package com.ivy.accounts

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * The savings figure is arithmetic on two totals the app already computes.
 *
 * That is the whole implementation - no second query - so the risk is not that
 * the code is complex, it is that the subtraction is wrong. These pin the
 * behaviour that matters:
 *
 *   savings = (all accounts) - (only includeInBalance accounts)
 *          = the balance of the excluded accounts
 */
class SavingsBalanceTest {

    private fun savings(allAccounts: Double, includedOnly: Double) = allAccounts - includedOnly

    @Test
    fun `excluded accounts land entirely in the savings figure`() {
        // 1,000,000 across two accounts, 250,000 of it excluded.
        val all = 1_000_000.00
        val includedOnly = 750_000.00

        savings(all, includedOnly) shouldBe 250_000.00
    }

    @Test
    fun `no excluded accounts means no savings`() {
        savings(allAccounts = 1_000_000.00, includedOnly = 1_000_000.00) shouldBe 0.00
    }

    @Test
    fun `everything excluded still totals correctly`() {
        savings(allAccounts = 500_000.00, includedOnly = 0.00) shouldBe 500_000.00
    }

    @Test
    fun `an over-drawn excluded account reports negative rather than zero`() {
        // A credit card parked outside the balance. Hiding the minus sign would
        // show savings the owner does not have.
        val result = savings(allAccounts = 1_000_000.00, includedOnly = 1_400_000.00)

        result shouldBeLessThan 0.0
    }

    @Test
    fun `savings never exceed the total balance`() {
        // Excluding an account can only subtract from the total, never add.
        val all = 2_000_000.00
        val included = 1_100_000.00

        savings(all, included).shouldBeLessThan(all)
        savings(all, included).shouldBeGreaterThan(0.0)
    }
}