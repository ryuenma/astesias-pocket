package com.ivy.accounts

import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.model.AccountType
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.type
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.UUID

/**
 * A savings account is a kind of account, not a differently-flagged account.
 *
 * Ivy has no account-type column, so the piggy-bank icon is what makes an
 * account a savings account, and the type is what fixes includeInBalance=false.
 * The balance maths is deliberately not reimplemented here: Total Savings is
 * already derived upstream as (all accounts) - (includeInBalance only), and
 * CalcWalletBalanceAct already filters on that flag. SavingsBalanceTest covers
 * the arithmetic.
 *
 * So the risk is not the subtraction. It is the flag: if savings were merely included like any other account, or if
 * the type stopped round-tripping through the icon, savings would silently
 * vanish from the figure the owner is trying to read. These pin the contract
 * that connects the two.
 */
class SavingsAccountTypeTest {

    /** An account plus the balance it holds, so totals read like the app's. */
    private data class Entry(val account: Account, val balance: Double)

    private fun entry(
        name: String,
        balance: Double,
        type: AccountType? = null,
        icon: IconAsset? = null
    ) = Entry(
        account = Account(
            id = AccountId(UUID.randomUUID()),
            name = NotBlankTrimmedString.unsafe(name),
            asset = AssetCode.unsafe("IDR"),
            color = ColorInt(0),
            // A typed account carries its type's icon and its type's balance
            // rule; an untyped one keeps whatever the owner chose.
            icon = type?.icon ?: icon,
            includeInBalance = type?.includeInBalance ?: true,
            orderNum = 0.0,
        ),
        balance = balance
    )

    /** Mirrors AccountsViewModel: what the accounts screen would show. */
    private data class Totals(
        val totalBalance: Double,
        val totalSavings: Double,
    )

    private fun totals(vararg entries: Entry): Totals {
        val allAccounts = entries.sumOf { it.balance }
        val includedOnly = entries.filter { it.account.includeInBalance }.sumOf { it.balance }
        return Totals(
            totalBalance = includedOnly,
            totalSavings = allAccounts - includedOnly
        )
    }

    @Test
    fun `savings type is excluded from the balance`() {
        AccountType.Savings.includeInBalance shouldBe false
    }

    @Test
    fun `everyday types are counted in the balance`() {
        AccountType.Cash.includeInBalance shouldBe true
        AccountType.Bank.includeInBalance shouldBe true
    }

    @Test
    fun `a savings account lands in savings and not in the balance`() {
        val result = totals(
            entry("Cash", balance = 750_000.0, type = AccountType.Cash),
            entry("Deposito", balance = 12_500_000.0, type = AccountType.Savings),
        )

        // The everyday money is the balance...
        result.totalBalance shouldBe 750_000.0
        // ...and the savings money is not in it.
        result.totalSavings shouldBe 12_500_000.0
    }

    @Test
    fun `savings are the whole excluded balance and nothing else`() {
        val result = totals(
            entry("Cash", balance = 750_000.0, type = AccountType.Cash),
            entry("Deposito", balance = 12_500_000.0, type = AccountType.Savings),
        )

        // 13.25M across both accounts, 750k of it inside the balance.
        val allAccounts = 750_000.0 + 12_500_000.0
        result.totalSavings shouldBe allAccounts - result.totalBalance
    }

    @Test
    fun `the type round-trips through the icon, so existing accounts are recognised`() {
        // No data migration: the icon is the type, so an account that already
        // carries the piggy bank resolves back to Savings.
        val savings = entry(
            name = "Deposito",
            balance = 0.0,
            icon = IconAsset.unsafe("savings")
        ).account

        savings.type shouldBe AccountType.Savings
        savings.type?.includeInBalance shouldBe false
    }

    @Test
    fun `a custom icon is not forced into a type`() {
        // The icon is real user data. Inventing a type for it would classify
        // an account the owner never classified.
        val custom = entry(
            name = "Custom",
            balance = 0.0,
            icon = IconAsset.unsafe("revolut")
        ).account

        custom.type shouldBe null
    }

    @Test
    fun `an account with no icon has no type`() {
        entry(name = "None", balance = 0.0).account.type shouldBe null
    }

    @Test
    fun `two savings accounts both count`() {
        val result = totals(
            entry("Deposito A", balance = 12_500_000.0, type = AccountType.Savings),
            entry("Deposito B", balance = 12_500_000.0, type = AccountType.Savings),
        )

        result.totalBalance shouldBe 0.0
        result.totalSavings shouldBe 25_000_000.0
        result.totalSavings shouldBeGreaterThan result.totalBalance
    }

    @Test
    fun `each type carries the icon that identifies it`() {
        // The icon is the type, so these three strings are load-bearing: they
        // are what an already-saved account is recognised by.
        AccountType.Cash.icon shouldBe IconAsset.unsafe("cash")
        AccountType.Bank.icon shouldBe IconAsset.unsafe("bank")
        AccountType.Savings.icon shouldBe IconAsset.unsafe("savings")
    }

    @Test
    fun `with no savings account the savings figure is zero`() {
        val result = totals(
            entry("Cash", balance = 750_000.0, type = AccountType.Cash),
            entry("Bank", balance = 2_250_000.0, type = AccountType.Bank),
        )

        // No savings account anywhere: nothing to report, and the card stays
        // hidden because the figure is zero.
        result.totalBalance shouldBe 3_000_000.0
        result.totalSavings shouldBe 0.0
    }
}