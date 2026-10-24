package com.ivy.legacy.utils

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.util.Locale

class AmountFormattingTest {

    @Test
    fun `shortenAmount formats thousands, millions, billions, and trillions with Indonesian suffixes`() {
        val prev = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            shortenAmount(500.0) shouldBe "500.0"
            shortenAmount(13_000.0) shouldBe "13rb"
            shortenAmount(13_500.0) shouldBe "13.50rb"
            shortenAmount(1_250_000.0) shouldBe "1.25jt"
            shortenAmount(-4_332_830_000.0) shouldBe "-4.33M"
            shortenAmount(2_000_000_000.0) shouldBe "2M"
            shortenAmount(2_500_000_000_000.0) shouldBe "2.50T"
        } finally {
            Locale.setDefault(prev)
        }
    }
}
