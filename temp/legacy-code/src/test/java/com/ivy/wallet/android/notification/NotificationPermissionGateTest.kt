package com.ivy.wallet.android.notification

import io.kotest.matchers.shouldBe
import org.junit.Test

/**
 * Why the permission decision needs its own test.
 *
 * The whole bug this fix addresses was invisible: POST_NOTIFICATIONS was declared
 * in the manifest, never requested, and every notification was discarded on
 * Android 13+ with no symptom at all. The owner's own device runs Android 10,
 * where the permission does not exist, so nothing about the failure was
 * reproducible by hand.
 *
 * These cases pin the three-state machine that decides "ask again" versus "only
 * system settings can fix this", because that is the part which is easy to
 * write wrong and impossible to notice until users go quiet.
 */
class NotificationPermissionGateTest {

    private fun resolve(
        requiresRuntimeRequest: Boolean = true,
        isGranted: Boolean = false,
        shouldShowRationale: Boolean = false,
        hasAskedBefore: Boolean = false,
    ) = NotificationPermissionGate.resolveState(
        requiresRuntimeRequest = requiresRuntimeRequest,
        isGranted = isGranted,
        shouldShowRationale = shouldShowRationale,
        hasAskedBefore = hasAskedBefore,
    )

    @Test
    fun `below API 33 the permission does not exist, so nothing can block us`() {
        // Android 10, the owner's own device. Even "denied" is meaningless here:
        // there is no runtime permission to deny.
        resolve(requiresRuntimeRequest = false, isGranted = false) shouldBe
            NotificationPermissionState.NotRequired
    }

    @Test
    fun `granted means notifications will be delivered`() {
        resolve(isGranted = true) shouldBe NotificationPermissionState.Granted
    }

    @Test
    fun `a fresh install is askable, not permanently denied`() {
        // The regression that matters most. The rationale flag is FALSE on a
        // fresh install, exactly as it is after a permanent denial - so any code
        // that reads that flag alone concludes "permanently denied" and never
        // asks. On every new install the permission would then never be
        // requested, which is the silent bug all over again.
        resolve(
            shouldShowRationale = false,
            hasAskedBefore = false,
        ) shouldBe NotificationPermissionState.DeniedButAskable
    }

    @Test
    fun `denied once without dont-ask-again is still askable`() {
        resolve(
            shouldShowRationale = true,
            hasAskedBefore = true,
        ) shouldBe NotificationPermissionState.DeniedButAskable
    }

    @Test
    fun `asked and denied with no rationale means the system will not ask again`() {
        // Only the hasAskedBefore flag makes this distinguishable from the fresh
        // install above. Without it, these two states are indistinguishable and
        // the row either nags forever or gives up too early.
        resolve(
            shouldShowRationale = false,
            hasAskedBefore = true,
        ) shouldBe NotificationPermissionState.PermanentlyDenied
    }

    @Test
    fun `a granted permission wins over a stale asked flag`() {
        // The user can revoke and re-grant in system settings while the app is
        // backgrounded. A cached or "asked before" answer must not outvote the
        // live grant, or the row keeps claiming a problem that is fixed.
        resolve(
            isGranted = true,
            shouldShowRationale = true,
            hasAskedBefore = true,
        ) shouldBe NotificationPermissionState.Granted
    }

    @Test
    fun `a granted permission wins on API 33 and below alike`() {
        resolve(
            requiresRuntimeRequest = false,
            isGranted = false,
            hasAskedBefore = true,
        ) shouldBe NotificationPermissionState.NotRequired
    }
}
