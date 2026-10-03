package com.ivy.data.model

import com.ivy.data.model.primitive.IconAsset

/**
 * The kind of account, which is what decides whether its money sits inside
 * Total Balance.
 *
 * Ivy has never had an account-type column: the icon chosen in the account
 * modal *was* the type, and the only thing the balance maths read was
 * [includeInBalance]. Savings is the first kind where those two disagree by
 * default - a piggy bank is not something you spend from - so the pairing
 * lives here instead of being re-derived at each call site.
 *
 * The icon is part of the type, not decoration: it is what an existing
 * account's icon resolves back to, so upgrading leaves old savings accounts
 * recognised without a data migration.
 */
enum class AccountType(
    val icon: IconAsset,
    val includeInBalance: Boolean,
) {
    /** Everyday money. Counted in Total Balance. */
    Cash(icon = IconAsset.unsafe("cash"), includeInBalance = true),

    /** A bank or card account. Counted in Total Balance. */
    Bank(icon = IconAsset.unsafe("bank"), includeInBalance = true),

    /**
     * Money set aside, not spendable.
     *
     * Excluded from Total Balance, which is exactly what makes it show up in
     * Total Savings - that figure is derived as the difference between the
     * all-accounts total and the includeInBalance-only total. Setting the flag
     * false is therefore the whole integration; there is no savings-specific
     * arithmetic anywhere in the app.
     */
    Savings(icon = IconAsset.unsafe("savings"), includeInBalance = false),
    ;

    companion object {
        /**
         * The type an account's stored icon means, or null when the icon is
         * not one this app knows - a custom icon, or an account predating
         * types. Callers must keep the account's own [Account.includeInBalance]
         * in that case rather than assume a type's default.
         */
        fun fromIcon(icon: IconAsset?): AccountType? = entries.firstOrNull { it.icon == icon }
    }
}

/**
 * This account's type, or null when its icon is not one the app knows.
 *
 * Null is the honest answer for a custom icon: the icon is real user data,
 * and inferring a type from it would invent a classification the owner never
 * made.
 */
val Account.type: AccountType?
    get() = AccountType.fromIcon(icon)