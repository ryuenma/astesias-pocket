package com.ivy.onboarding.viewmodel

import io.kotest.matchers.shouldBe
import org.junit.Test

class OnboardingCurrencyPolicyTest {

    private val usd = "USD"

    // --- preselectedCurrencyCode ---------------------------------------------

    /**
     * The bug this pins: the backup said IDR, the importer stored IDR, and the
     * currency step still opened on USD, so finishing onboarding replaced it.
     */
    @Test
    fun `the stored currency wins over the device default`() {
        preselectedCurrencyCode(
            storedCode = "IDR",
            deviceDefaultCode = usd,
        ) shouldBe "IDR"
    }

    @Test
    fun `a blank stored currency falls back to the device default`() {
        preselectedCurrencyCode(storedCode = "", deviceDefaultCode = usd) shouldBe usd
    }

    @Test
    fun `an absent stored currency falls back to the device default`() {
        preselectedCurrencyCode(storedCode = null, deviceDefaultCode = usd) shouldBe usd
    }

    @Test
    fun `a whitespace stored currency is treated as absent`() {
        preselectedCurrencyCode(storedCode = "   ", deviceDefaultCode = usd) shouldBe usd
    }

    @Test
    fun `a stored currency equal to the default still wins on its own terms`() {
        preselectedCurrencyCode(storedCode = "EUR", deviceDefaultCode = "EUR") shouldBe "EUR"
    }

    // --- shouldPersistCurrency ----------------------------------------------

    /**
     * The core no-overwrite rule: tapping "Set" without moving the picker must
     * not write, because the picker's value IS the imported currency.
     */
    @Test
    fun `confirming the unchanged imported currency does not write`() {
        shouldPersistCurrency(
            storedCode = "IDR",
            chosenCode = "IDR",
        ) shouldBe false
    }

    @Test
    fun `deliberately choosing a different currency writes`() {
        shouldPersistCurrency(
            storedCode = "IDR",
            chosenCode = "EUR",
        ) shouldBe true
    }

    @Test
    fun `a fresh install with nothing stored writes the first choice`() {
        shouldPersistCurrency(
            storedCode = null,
            chosenCode = "EUR",
        ) shouldBe true
    }

    @Test
    fun `a blank stored currency writes the first choice`() {
        shouldPersistCurrency(
            storedCode = "",
            chosenCode = "EUR",
        ) shouldBe true
    }
}