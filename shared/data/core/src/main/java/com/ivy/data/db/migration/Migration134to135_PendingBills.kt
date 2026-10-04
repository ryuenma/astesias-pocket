package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The inbox of monthly bills that have been reminded about but not yet paid.
 *
 * A brand-new table, so nothing existing is touched: no `transactions` row is
 * altered, and an owner who has set no reminder gets an empty inbox rather than
 * a fabricated one.
 *
 * ## Why this is a separate table and not a `transactions` row with a null date
 *
 * This is the load-bearing decision of the whole feature, and it is the same
 * reasoning that put `pending_transfers` in its own table rather than in
 * `transactions` (see `Migration132to133_PendingTransfers`).
 *
 * An unpaid bill must never move a balance. Writing it as a `transactions` row
 * with `dateTime = NULL` and `dueDate` set - which is exactly what Ivy's existing
 * "planned payment, not yet paid" rows look like - does NOT make that safe. It
 * makes it safe only for the queries that happen to filter on `dateTime`:
 *
 *  - `TransactionDao.findAllBetween` and the other range queries compare
 *    `dateTime >= :start AND dateTime <= :end`, and SQL's `NULL >= x` is NULL,
 *    not true - so those do exclude an unpaid row. That exclusion is real, but it
 *    is a property of those specific WHERE clauses, not of the data model.
 *  - `TransactionDao.findAll()` has no date filter at all, and it feeds
 *    `AccountBalanceUseCase` -> `AccountStatsUseCase.calculate`, which sums every
 *    `Expense`/`Income`/`Transfer` it is handed with no `settled` check. An unpaid
 *    row placed there is counted into an account balance.
 *  - `ExportCsvUseCase` and `ExportXlsxUseCase` both default to
 *    `transactionRepository.findAll()`. An unpaid row would appear in the owner's
 *    exported ledger as a row with a blank date and a due date - and "blank date"
 *    is not something a spreadsheet or a CSV reader distinguishes from corrupt
 *    data.
 *
 * So an unpaid bill in `transactions` would be excluded from the home screen's
 * balance and included in the domain-layer account balance and in both exports.
 * Keeping it here makes that impossible rather than a rule each new query has to
 * remember: every total, report and export in this codebase reads `transactions`,
 * and nothing in this table is ever in it.
 *
 * It is still fully visible in-app - see `BillReminderInbox` - because "visible"
 * and "counted" are independent properties, and only one of them needed the
 * ledger.
 *
 * ## `occurrenceKey` is the idempotency key, and it is the PRIMARY KEY
 *
 * One bill must never produce two pending rows, whether the scheduler re-runs, a
 * WorkManager retry fires, the device reboots mid-write, or a sync replays. That
 * guarantee cannot be left to application logic: two workers can both read "not
 * seen yet" before either commits, so a check-then-insert in Kotlin has a window
 * that no ordering of the two statements closes.
 *
 * The key is derived, not random: it is the rule id plus the clamped occurrence
 * date, formatted into a stable string. That makes the identity of "the reminder
 * for rule R in month M" computable without reading anything, so a retry
 * recomputes the same key and SQLite refuses the second INSERT on the primary
 * key. Impossible, not unlikely.
 *
 * This is the property that has already broken once on this project: a
 * PocketBase collection declared `unique: true` on a field with no real index
 * behind it, so a replayed POST returned 200 and the same transfer was recorded
 * twice. A real PRIMARY KEY is the difference.
 *
 * ## A paid bill is RETAINED, not deleted
 *
 * `state` moves to PAID and the row stays, for the same reason
 * `Migration132to133_PendingTransfers` keeps DECLINED rows. If paying deleted the
 * row, the next reminder for the same occurrence would find nothing and insert a
 * fresh PENDING bill - the owner would be asked for a bill he had already paid,
 * once per replay, with nothing left recording that he had said yes. Retaining it
 * means the replay collides on the primary key and is dropped.
 *
 * The row is also what stops the same month's bill being re-fabricated after the
 * owner deletes it: `state` DISMISSED is likewise a tombstone, not a deletion.
 *
 * ## No index beyond the primary key
 *
 * The only queries are "everything not yet decided" (the inbox, bounded by the
 * number of bills in flight - single digits) and the primary-key lookup the
 * writer itself does. Both are cheap on a table this small.
 *
 * Deliberately NOT `@Serializable`-shaped into the owner's backup: see
 * `PendingBillEntity`, which documents that choice.
 *
 * @see com.ivy.data.db.entity.PendingBillEntity
 */
class Migration134to135_PendingBills : Migration(134, 135) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_bills` (
                `occurrenceKey` TEXT NOT NULL,
                `ruleId` TEXT NOT NULL,
                `dueOn` INTEGER NOT NULL,
                `amount` REAL NOT NULL,
                `title` TEXT,
                `description` TEXT,
                `accountId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                PRIMARY KEY(`occurrenceKey`)
            )
            """.trimIndent()
        )
    }
}