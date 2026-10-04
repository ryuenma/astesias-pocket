package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds a rule-level "remind me on day N of the month" to planned payment rules.
 *
 * `planned_payment_rules` has no due-day column. Today a rule carries a
 * `startDate`, and the only day-of-month it implies is the day buried in that
 * instant's time-of-day component - which cannot express "the 25th", because a
 * date-time has no day-of-month field you can read without also pinning a month
 * and a year. So a monthly bill could be scheduled, but not *reminded* on a
 * chosen day.
 *
 * `dueDayOfMonth` is nullable and that null is exactly what every existing row
 * means today: this rule has no monthly reminder. So the ALTER alone is the whole
 * migration - no backfill, no rewrite of the table, and every existing rule keeps
 * behaving identically. A rule that has never been given a day never starts
 * reminding anyone.
 *
 * Deliberately does NOT backfill a day derived from `startDate`. Inferring "the
 * 25th" from a rule whose startDate happens to fall on the 25th would silently
 * switch on reminders the owner never asked for, and would do it to every rule
 * already in the database at once. A missing capability that stays missing until
 * configured is a much smaller failure than a new notification arriving on a date
 * nobody chose.
 *
 * Deliberately does NOT index the column on its own. Every read of it goes
 * through [Migration133to134_BillReminderDueDay]'s consumer, which asks for
 * "rules that have a day set" - a filter over a table bounded by the number of
 * bills one person has (single digits, low tens). An index would cost a write on
 * every rule save for no measurable read win.
 *
 * Deliberately does NOT add a foreign key or cascade to the eventual pending
 * items. That relationship is resolved by application code on purpose, so that
 * deleting a rule cannot take a row the owner may still be looking at with it,
 * silently.
 *
 * `dueDayOfMonth` is validated as 1..31 by the writer, not by a CHECK
 * constraint: SQLite cannot express a CHECK that Room's generated schema
 * validation would also agree with on a column that must accept NULL, and a
 * constraint that fires during a migration is a crash on someone's real ledger
 * rather than a rejected value.
 *
 * @see com.ivy.data.db.entity.PlannedPaymentRuleEntity
 */
class Migration133to134_BillReminderDueDay : Migration(133, 134) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE planned_payment_rules ADD COLUMN dueDayOfMonth INTEGER")

        // v133 rows all read NULL here, which means "no monthly reminder" - the
        // same thing every one of them already meant.
    }
}