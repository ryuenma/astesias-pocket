package com.ivy.legacy.android.power

import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Proves the rule that decides whether this device can be trusted to run the
 * background transfer sync.
 *
 * The failure this guards is not "the app crashes": it is the app quietly not
 * syncing for hours because the OS is deferring its work, while every screen
 * still looks healthy. Measured on the POCOPHONE F1 (MIUI, Android 10): the
 * periodic worker's JobScheduler job sat 28 minutes past due with the standby
 * bucket on RARE and the package not exempt, and no transfer notification was
 * posted for over five hours. The app cannot run that work on the user's behalf,
 * but it can say so - and saying so requires this decision to be right.
 */
class BatteryOptimisationTest {

    @Test
    fun `below API 23 there is no battery optimisation to escape`() {
        // Doze and app standby arrived in API 23. Asking a 5.x device to be
        // exempted is meaningless, and offering it a settings screen that does
        // not exist is worse than saying nothing.
        batteryOptimisationState(sdkInt = 22, isIgnoringOptimisations = false) shouldBe
            BatteryOptimisationState.NotRequired
    }

    @Test
    fun `an exempted app is the only state that promises background work`() {
        batteryOptimisationState(sdkInt = 29, isIgnoringOptimisations = true) shouldBe
            BatteryOptimisationState.Exempt
    }

    @Test
    fun `not exempted means the OS may defer the sync indefinitely`() {
        // THE case that cost this session an afternoon. Not an error state and
        // not visible anywhere else in the app: jobs still exist, they just do
        // not run, so the only honest thing to do is surface it.
        batteryOptimisationState(sdkInt = 29, isIgnoringOptimisations = false) shouldBe
            BatteryOptimisationState.Restricted
    }

    @Test
    fun `a modern API with no exemption is still restricted`() {
        batteryOptimisationState(sdkInt = 35, isIgnoringOptimisations = false) shouldBe
            BatteryOptimisationState.Restricted
    }

    @Test
    fun `the exemption flag decides above the threshold, never the sdk alone`() {
        // Same API, opposite answers: the flag is the input that matters once the
        // platform has the concept at all.
        batteryOptimisationState(33, true) shouldBe BatteryOptimisationState.Exempt
        batteryOptimisationState(33, false) shouldBe BatteryOptimisationState.Restricted
    }
}
