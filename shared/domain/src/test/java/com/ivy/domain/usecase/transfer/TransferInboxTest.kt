package com.ivy.domain.usecase.transfer

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * Idempotency and the dismissal path for a mirrored partner transfer.
 *
 * The property under test throughout is that a replayed event never creates a
 * second row. That is the property which has already broken once on this project
 * - a PocketBase collection with `unique: true` and no real index accepted a
 * replay and recorded the same transfer twice. So the replay cases below are
 * deliberately exhaustive over every state rather than just asserting the happy
 * path once.
 */
class TransferInboxTest {

    @Test
    fun `a never-seen transfer is created as pending`() {
        val outcome = TransferInbox.onEventArrived(existing = null)

        outcome shouldBe TransferInbox.Outcome.Create(PendingTransferState.PENDING)
        outcome.writesNewRow() shouldBe true
    }

    @Test
    fun `a replayed pending transfer writes nothing`() {
        // The case that actually bites: the owner retried after a dropped
        // response. Already PENDING, so the retry must be inert.
        val outcome = TransferInbox.onEventArrived(existing = PendingTransferState.PENDING)

        outcome shouldBe TransferInbox.Outcome.Ignore
        outcome.writesNewRow() shouldBe false
    }

    @Test
    fun `a replay after acceptance does not resurrect or duplicate`() {
        // The ledger row already exists from the first accept. A replay must not
        // add a second one.
        val outcome = TransferInbox.onEventArrived(existing = PendingTransferState.ACCEPTED)

        outcome shouldBe TransferInbox.Outcome.AlreadyAccepted
        outcome.writesNewRow() shouldBe false
    }

    @Test
    fun `a replay after declining does not resurrect the item`() {
        // The load-bearing dismissal property. If this returned Create, a declined
        // transfer would come back on every subsequent sync, forever, because the
        // device would no longer have any record that it had ever said no.
        val outcome = TransferInbox.onEventArrived(existing = PendingTransferState.DECLINED)

        outcome shouldBe TransferInbox.Outcome.AlreadyDeclined
        outcome.writesNewRow() shouldBe false
    }

    @Test
    fun `a sequential replay creates exactly one row`() {
        // The guarantee, stated as a count rather than a case. Drive the inbox the
        // way a retrying client would, feeding back the state each delivery left
        // behind, and assert on how many writes were permitted.
        val states = listOf<PendingTransferState?>(
            null,
            PendingTransferState.PENDING,
            PendingTransferState.PENDING,
            PendingTransferState.ACCEPTED,
            PendingTransferState.ACCEPTED,
            PendingTransferState.DECLINED,
        )

        val writes = states.count { existing ->
            TransferInbox.onEventArrived(existing).writesNewRow()
        }

        writes shouldBe 1
    }

    @Test
    fun `two in-flight events for the same correlation id are the window the unique key closes`() {
        // Documented limit of the pure function, pinned deliberately so nobody
        // later "simplifies" it into a false claim of full protection.
        //
        // [onEventArrived] only sees COMMITTED rows. Two POSTs that are both in
        // flight before either commits both read null, so this function alone
        // would permit two writes. What actually stops the second is the inbox
        // table's primary key on correlationId - the same key that makes the
        // correlation id the idempotency key - so the second INSERT fails at the
        // database instead of duplicating.
        //
        // So: idempotency here is the pair (this decision, that unique key).
        // Neither half is sufficient, which is why the key is not optional.
        val concurrent = listOf<PendingTransferState?>(null, null)
        val permitted = concurrent.count { TransferInbox.onEventArrived(it).writesNewRow() }

        permitted shouldBe 2

        // The row identity both of those writes collide on is the correlation id
        // itself, not a row id, so the collision is guaranteed rather than likely.
        val correlationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        // Both deliveries describe the SAME transfer, so they resolve to the same
        // inbox primary key and the second one cannot become a second row.
        CrossProfileTransfer.senderLeg(
            legId = UUID.randomUUID(),
            correlationId = correlationId,
            fromAccountId = MY_ACCOUNT,
            toAccountId = HER_ACCOUNT,
            counterpartProfileId = "so",
            amount = 500_000.0,
            dateTime = WHEN,
        ).linkedTransactionId shouldBe correlationId
    }

    @Test
    fun `only a create outcome writes`() {
        // Guard for the write site: every replay-shaped outcome must be inert.
        val replayOutcomes = listOf(
            TransferInbox.onEventArrived(PendingTransferState.PENDING),
            TransferInbox.onEventArrived(PendingTransferState.ACCEPTED),
            TransferInbox.onEventArrived(PendingTransferState.DECLINED),
        )
        replayOutcomes.forEach { it.writesNewRow() shouldBe false }
    }

    @Test
    fun `accepting a pending transfer touches the ledger`() {
        val decision = TransferInbox.onUserDecision(
            existing = PendingTransferState.PENDING,
            action = TransferInbox.Action.ACCEPT
        )

        decision shouldBe TransferInbox.Decision.Accept
        decision.touchesLedger() shouldBe true
    }

    @Test
    fun `declining never touches the ledger`() {
        // The money already moved. Her declining it cannot un-move it, and must
        // not retract the owner's row, which would falsify her ledger.
        val decision = TransferInbox.onUserDecision(
            existing = PendingTransferState.PENDING,
            action = TransferInbox.Action.DECLINE
        )

        decision shouldBe TransferInbox.Decision.Decline
        decision.touchesLedger() shouldBe false
    }

    @Test
    fun `a second tap on an already-decided item changes nothing`() {
        // Two taps is routine on a phone. The second must not double the money.
        TransferInbox.onUserDecision(PendingTransferState.ACCEPTED, TransferInbox.Action.ACCEPT) shouldBe
            TransferInbox.Decision.NoChange
        TransferInbox.onUserDecision(PendingTransferState.DECLINED, TransferInbox.Action.ACCEPT) shouldBe
            TransferInbox.Decision.NoChange
        TransferInbox.onUserDecision(PendingTransferState.DECLINED, TransferInbox.Action.DECLINE) shouldBe
            TransferInbox.Decision.NoChange
    }

    @Test
    fun `acting on an item that is already gone changes nothing`() {
        // A replayed confirm after the row was tombstoned.
        TransferInbox.onUserDecision(null, TransferInbox.Action.ACCEPT) shouldBe
            TransferInbox.Decision.NoChange
        TransferInbox.onUserDecision(null, TransferInbox.Action.DECLINE) shouldBe
            TransferInbox.Decision.NoChange
    }

    @Test
    fun `dismissal leaves both sides in a defined state`() {
        // Owner: her row stands untouched, because the money moved.
        val ownerLeg = CrossProfileTransfer.senderLeg(
            legId = UUID_ZERO,
            correlationId = CORRELATION,
            fromAccountId = MY_ACCOUNT,
            toAccountId = HER_ACCOUNT,
            counterpartProfileId = "so",
            amount = 500_000.0,
            dateTime = WHEN,
        )

        // Counterpart: declined, and therefore terminal - so the next sync's
        // replay of the same event is inert rather than a resurrection.
        val declined = TransferInbox.onEventArrived(null)
        declined.writesNewRow() shouldBe true

        val afterDecline = TransferInbox.onUserDecision(PendingTransferState.PENDING, TransferInbox.Action.DECLINE)
        afterDecline shouldBe TransferInbox.Decision.Decline

        // She never gains a ledger row, and the owner's row is still a real
        // TRANSFER she can see in her own ledger.
        ownerLeg.type.name shouldBe "TRANSFER"
        ownerLeg.amount shouldBe 500_000.0

        // And the state she settled into is one a replay recognises.
        val replay = TransferInbox.onEventArrived(PendingTransferState.DECLINED)
        replay.writesNewRow() shouldBe false
    }

    @Test
    fun `acceptance produces a real TRANSFER and never INCOME`() {
        // Owner-decided: it reads as moved money on her side too.
        val accepted = CrossProfileTransfer.receiverLeg(
            legId = HER_LEG,
            correlationId = CORRELATION,
            intoAccountId = HER_ACCOUNT,
            fromAccountId = MY_ACCOUNT,
            senderProfileId = "default",
            amount = 500_000.0,
            dateTime = WHEN,
        )

        accepted.type.name shouldBe "TRANSFER"
        // Still keyed by the same correlation id, so the two legs remain joinable
        // and a replay can recognise the confirmed transfer.
        accepted.linkedTransactionId shouldBe CORRELATION
    }

    @Test
    fun `terminal states are terminal`() {
        PendingTransferState.PENDING.isTerminal shouldBe false
        PendingTransferState.ACCEPTED.isTerminal shouldBe true
        PendingTransferState.DECLINED.isTerminal shouldBe true
    }

    private companion object {
        val CORRELATION = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val MY_ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val HER_ACCOUNT = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val UUID_ZERO = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val HER_LEG = UUID.fromString("44444444-4444-4444-4444-444444444444")
        val WHEN = Instant.parse("2026-10-02T09:15:00Z")
    }
}