package com.ivy.accounts

import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * The savings figure is arithmetic on two totals the app already computes.
 *
 * The implementation is one subtraction, so it lives in `savingsBalance` next to
 * AccountsViewModel rather than being copied here - a test that re-implements the
 * expression it claims to cover stays green if production inverts it. These call
 * the real function:
 *
 *   savings = (all accounts) - (only includeInBalance accounts)
 *          = the balance of the excluded accounts
 */
class SavingsBalanceTest {

    @Test
    fun `excluded accounts land entirely in the savings figure`() {
        // 1,000,000 across two accounts, 250,000 of it excluded.
        val all = 1_000_000.00
        val includedOnly = 750_000.00

        savingsBalance(all, includedOnly) shouldBe 250_000.00
    }

    @Test
    fun `no excluded accounts means no savings`() {
        savingsBalance(allAccounts = 1_000_000.00, includedOnly = 1_000_000.00) shouldBe 0.00
    }

    @Test
    fun `everything excluded still totals correctly`() {
        savingsBalance(allAccounts = 500_000.00, includedOnly = 0.00) shouldBe 500_000.00
    }

    @Test
    fun `an over-drawn excluded account reports negative rather than zero`() {
        // A credit card parked outside the balance. Hiding the minus sign would
        // show savings the owner does not have.
        val result = savingsBalance(allAccounts = 1_000_000.00, includedOnly = 1_400_000.00)

        result shouldBeLessThan 0.0
    }

    @Test
    fun `savings never exceed the total balance`() {
        // Excluding an account can only subtract from the total, never add.
        val all = 2_000_000.00
        val included = 1_100_000.00

        savingsBalance(all, included).shouldBeLessThan(all)
        savingsBalance(all, included).shouldBeGreaterThan(0.0)
    }
}