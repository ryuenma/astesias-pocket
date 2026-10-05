package com.ivy.domain.usecase.transfer

import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.entity.PendingTransferEntity
import com.ivy.data.sync.DirectorySyncApi
import com.ivy.data.sync.SyncConfig
import com.ivy.data.sync.TransferOutboxRepository
import com.ivy.data.sync.TransferSyncApi
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The inbound half of mirroring: what the counterpart's phone wrote, turned
 * into rows in HER inbox.
 *
 * ## Why this lives in `shared/domain` and not in `data/core`
 *
 * Because it needs `com.ivy.domain.*`. `data/core` is the lower module and
 * cannot see it, so the transport that applies `TransferInbox`'s rules to
 * `PendingTransferEntity` has to sit above. `data/core` keeps the wire and the
 * queue; this applies the decision.
 *
 * ## What it will NOT do
 *
 * It will not create a TRANSFER row. Not one event, not automatically, ever.
 * An arriving event becomes a PENDING inbox item and nothing more, because the
 * money leaving his account is a fact and her accepting it into her ledger is a
 * separate decision she has to make. A transport that "helpfully" wrote the
 * mirrored leg on delivery would be reporting money into an account nobody
 * agreed to receive it in. See [PendingTransferRepository] for the accept path.
 *
 * ## Never throws
 *
 * The counterpart's inbox is the least valuable thing on the device to lose in
 * an exception, and every method returns a description of what happened instead.
 */
@Singleton
class TransferEventSync @Inject constructor(
    private val api: TransferSyncApi,
    private val profilesApi: DirectorySyncApi,
    private val outbox: TransferOutboxRepository,
    private val pendingTransfers: PendingTransferRepository,
    private val accounts: AccountDao,
    private val config: SyncConfig,
) {

    /**
     * Pulls the counterpart's transfers and offers them to HER inbox.
     *
     * @return what arrived, for a caller that wants to say something honest
     *   rather than just refresh silently.
     */
    suspend fun pullEvents(): PullResult {
        if (!config.isConfigured) {
            Timber.i("Sync not configured; her inbox is left as-is")
            return PullResult.NotConfigured
        }

        // `fetchEvents` is specified never to throw, and the sync does not depend
        // on that promise being kept: a socket that dies mid-read is an ordinary
        // event on a phone, not a programming error, and it must read as
        // "unreachable" rather than taking down whatever hosted this coroutine.
        val envelope = runCatching { api.fetchEvents() }.getOrElse { error ->
            Timber.e(error, "Could not read transfer events")
            null
        } ?: return PullResult.Unreachable

        val names = runCatching { profileNames() }.getOrElse { error ->
            // Names are cosmetic. Losing them costs the display name, not the
            // transfer, so the pull carries on with ids instead of abandoning it.
            Timber.e(error, "Could not resolve profile names")
            emptyMap()
        }

        var offered = 0
        var ignored = 0
        var undeliverable = 0

        for (event in envelope.items) {
            // Per-event isolation, and around the WHOLE body rather than just the
            // resolution: the insert is the part most likely to fail (a locked
            // database, a disk full), and an exception there would otherwise
            // abandon the transfers queued behind it. One row this device cannot
            // process must not cost her the other four that were perfectly fine.
            val outcome = runCatching {
                when (val delivery = deliver(event, names)) {
                    is Delivery.Offered ->
                        when (pendingTransfers.onEventArrived(delivery.item)) {
                            is TransferInbox.Outcome.Create -> Outcome.OFFERED
                            // Every other outcome is a replay, and each of them
                            // means the row is already in her inbox in the right
                            // state - including AlreadyAccepted and
                            // AlreadyDeclined, which are why a redelivered event
                            // must never resurrect a decision.
                            else -> Outcome.IGNORED
                        }

                    is Delivery.Skipped -> Outcome.IGNORED
                    is Delivery.Undeliverable -> Outcome.UNDELIVERABLE
                }
            }.getOrElse { error ->
                Timber.e(error, "Could not process an arriving transfer event")
                Outcome.UNDELIVERABLE
            }

            when (outcome) {
                Outcome.OFFERED -> offered++
                Outcome.IGNORED -> ignored++
                Outcome.UNDELIVERABLE -> undeliverable++
            }
        }

        Timber.i(
            "Pulled events: $offered new, $ignored already known, " +
                "$undeliverable could not be placed"
        )
        return PullResult.Pulled(
            newItems = offered,
            alreadyKnown = ignored,
            undeliverable = undeliverable,
        )
    }

    /**
     * Pulls her answers back, for an owner who wants to know whether the money
     * she sent actually arrived on the other phone.
     *
     * The owner's ledger is NOT rewritten by anything here. Her leg stands as
     * recorded regardless of what she decides, because the money already left -
     * see [TransferInbox.Decision.touchesLedger] for the full argument. What
     * this returns is knowledge, not a write.
     */
    suspend fun pullConfirmations(): List<TransferSyncApi.RemoteConfirmation> {
        if (!config.isConfigured) return emptyList()
        return runCatching { api.fetchConfirmations() }
            .getOrElse { error ->
                Timber.e(error, "Could not read transfer confirmations")
                null
            }
            ?.items
            .orEmpty()
    }

    /**
     * One remote event, resolved against this device and offered to the inbox.
     *
     * Every rejection here is a deliberate refusal to guess. The alternative -
     * inventing an account id, a date or a name so the row fits - produces an
     * inbox entry that looks real and is not, which is the worst outcome
     * available: she would accept money into an account the event never named.
     */
    private suspend fun deliver(
        event: TransferSyncApi.RemoteTransferEvent,
        names: Map<String, String>,
    ): Delivery {
        // Every guard below is a refusal to guess, expressed as one value rather
        // than as a chain of early returns: the rules read in order, and a
        // function with eight `return`s is one where a later rule can quietly
        // skip an earlier one in a refactor.
        val correlationId = runCatching { UUID.fromString(event.correlationId) }.getOrNull()
        if (correlationId == null) {
            Timber.w("Dropping an event whose correlationId will not parse")
            return Delivery.Undeliverable("unparseable correlationId")
        }

        if (event.recordedBy.isBlank()) {
            return Delivery.Undeliverable("no recordedBy")
        }

        // Not addressed to us, or already ours. Both profiles may read every
        // event, so this device necessarily sees what it published, and without
        // this check our own event would land in HER inbox as a second, mirrored
        // copy of a transfer already in HER ledger - the duplicate the inbox's
        // primary key exists to prevent, arriving through a different door.
        //
        // Two independent checks because either alone has a hole: `recordedBy`
        // catches it before any row exists, and the outbox catches it afterwards,
        // which is the one that has to keep working - clearing the queue on
        // sign-out must not resurrect a transfer into our own inbox.
        val isMine = event.recordedBy == config.profileId || outbox.isOwnEvent(correlationId)
        if (isMine) {
            Timber.i("Event $correlationId is ours; not offering it to our own inbox")
            return Delivery.Skipped("own event")
        }

        // The destination must be one of HER accounts. `toAccountName` is the
        // sender's name for it, and his database is the only place that name
        // exists - so it is matched against her own accounts here. No match
        // means the event stays on the server, and offering it anyway would let
        // her accept money into an account the event never named.
        val destination = event.toAccountName?.takeIf { it.isNotBlank() }
            ?: return Delivery.Undeliverable("no destination account name")
        val intoAccount = accounts.findByName(destination)
        if (intoAccount == null) {
            Timber.w(
                "Cannot place transfer $correlationId: no local account named " +
                    "'$destination'. It stays on the server."
            )
            return Delivery.Undeliverable("no destination account")
        }

        // The sender's side is a name precisely because it does not exist here.
        // An empty string would render as a blank "from" and imply the money came
        // from nowhere, so it is dropped rather than prettified.
        val fromAccountName = event.fromAccountName?.takeIf { it.isNotBlank() }
            ?: return Delivery.Undeliverable("no source account name")

        val occurredOn = parseDate(event.occurredOn)
            ?: return Delivery.Undeliverable("unparseable occurredOn")

        return Delivery.Offered(
            PendingTransferEntity(
                correlationId = correlationId,
                recordedByProfileId = event.recordedBy,
                // The id when the name is unknown, never blank: a blank owner
                // would read as a transfer from nobody.
                recordedByProfileName = names[event.recordedBy] ?: event.recordedBy,
                intoAccountId = intoAccount.id,
                fromAccountName = fromAccountName,
                amount = event.amount,
                currency = event.currency,
                occurredOn = occurredOn,
                state = PendingTransferState.PENDING.name,
            )
        )
    }

    /**
     * The wire carries a calendar date, so the instant is reconstructed at UTC
     * midnight.
     *
     * UTC rather than the local zone on purpose, and it is a real limitation:
     * the owner enters a date, not a time, and reconstructing it in the local
     * zone would put the transfer on a different day for a reader east or west
     * of Greenwich. UTC midnight is the one reading both phones agree on, and
     * the date the owner chose is preserved exactly.
     */
    private fun parseDate(raw: String): Instant? = runCatching {
        LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant()
    }.getOrNull()

    /** Profile id -> display name, so an item reads before anything is decided. */
    private suspend fun profileNames(): Map<String, String> =
        profilesApi.fetchProfiles()
            ?.items
            ?.associate { it.profileId to it.displayName }
            .orEmpty()

    /**
     * How one event's processing ended.
     *
     * A named enum rather than reusing [Delivery] because the count that matters
     * to the caller is "what happened", including the case where processing
     * threw - which is an undeliverable outcome but not one any [Delivery]
     * variant describes.
     */
    private enum class Outcome { OFFERED, IGNORED, UNDELIVERABLE }

    /** What became of one remote event. */
    private sealed interface Delivery {
        /** Resolved to a concrete local account and ready for her inbox. */
        data class Offered(val item: PendingTransferEntity) : Delivery

        /** Recognised and deliberately not offered. Not an error. */
        data class Skipped(val why: String) : Delivery

        /** Cannot be acted on. Left on the server for a later attempt. */
        data class Undeliverable(val why: String) : Delivery
    }

    /** What one pull achieved. */
    sealed interface PullResult {
        /** Nothing was attempted: this device has no server. */
        data object NotConfigured : PullResult

        /** The server could not be read. Her inbox is untouched. */
        data object Unreachable : PullResult

        data class Pulled(
            val newItems: Int,
            val alreadyKnown: Int,
            val undeliverable: Int,
        ) : PullResult
    }
}
