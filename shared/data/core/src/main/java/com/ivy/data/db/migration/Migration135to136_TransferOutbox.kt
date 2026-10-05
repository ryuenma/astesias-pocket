package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The queue of transfers this device still owes the sync server.
 *
 * A brand-new table, so nothing existing is touched: no `transactions` row is
 * altered, no account is touched, and a device that has never recorded a
 * cross-profile transfer gets an empty queue rather than a fabricated one.
 *
 * ## Why this is a separate table and not a column on `transactions`
 *
 * This is the load-bearing decision of the whole transport, and it is the same
 * reasoning that put `pending_transfers` in its own table (see
 * `Migration132to133_PendingTransfers`) and `pending_bills` in its own (see
 * `Migration134to135_PendingBills`).
 *
 * A row that is waiting to be published is not a transaction. It is a note that
 * a transaction exists and the other phone has not been told yet. Parking it in
 * `transactions` would make that distinction something every reader has to
 * remember, and the readers are numerous and mechanical:
 *
 *  - `TransactionDao.findAll()` has no date filter and feeds
 *    `AccountBalanceUseCase` -> `AccountStatsUseCase.calculate`, which sums
 *    every row it is handed. An unsent-transfer row placed there is counted into
 *    a balance.
 *  - `ExportCsvUseCase` and `ExportXlsxUseCase` both default to
 *    `transactionRepository.findAll()`, so an unsent row would appear in the
 *    owner's exported ledger.
 *
 * Every balance, report and export in this codebase reads `transactions`, and
 * nothing in `transfer_outbox` is ever in it. That makes "an unsent transfer can
 * never move a balance" structural instead of a rule.
 *
 * ## `correlationId` is the PRIMARY KEY, and that is what stops a double POST
 *
 * The id is simultaneously the cross-profile join key and the idempotency key.
 * One transfer must be published to the server exactly once no matter how many
 * times the local write is retried, and the server enforces the same key with a
 * real unique index. This database refusing the second INSERT is what keeps the
 * two ends consistent.
 *
 * The property has already failed once on this project: a PocketBase collection
 * declared `unique: true` on a field with no real index behind it, so a replayed
 * POST returned 200 and recorded the same transfer twice. A real PRIMARY KEY is
 * the difference between impossible and unlikely.
 *
 * ## A SENT row is retained, not deleted
 *
 * Deleting on success would let the next enqueue - a retried save, a re-sync -
 * insert a fresh row and POST the same transfer again. The server would then
 * reject it as a duplicate while this device kept retrying a row it had already
 * satisfied. Same reasoning that keeps DECLINED transfers and PAID bills.
 *
 * ## No index beyond the primary key
 *
 * The only queries are the primary-key lookup the writer does, and "oldest N
 * pending rows", bounded by [com.ivy.data.db.dao.read.TransferOutboxDao]'s cap.
 * Both are cheap on a table that holds one row per transfer ever made, so an
 * index would cost a write per transfer and buy nothing measurable.
 *
 * @see com.ivy.data.db.entity.TransferOutboxEntity
 */
class Migration135to136_TransferOutbox : Migration(135, 136) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `transfer_outbox` (
                `correlationId` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `recordedBy` TEXT NOT NULL,
                `amount` REAL,
                `currency` TEXT,
                `occurredOn` TEXT,
                `fromAccountName` TEXT,
                `toAccountName` TEXT,
                `decision` TEXT,
                `state` TEXT NOT NULL,
                `attempts` INTEGER NOT NULL,
                `lastError` TEXT,
                `enqueuedAt` INTEGER NOT NULL,
                `sentAt` INTEGER,
                PRIMARY KEY(`correlationId`)
            )
            """.trimIndent()
        )
    }
}