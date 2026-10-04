package com.ivy.onboarding.viewmodel

/**
 * Which currency the onboarding "Set currency" step opens on, and when it is
 * allowed to write.
 *
 * These two rules exist because the step used to ignore the currency a backup
 * import had just written. The importer stored IDR correctly; the step still
 * opened pre-selected to the device default (USD), and finishing onboarding
 * replaced the imported value -- the owner's backup says IDR while the app
 * showed "USD 199.84" next to a bufferAmount of 15,000,000.
 *
 * Kept free of Android types so the rules are unit-testable off-device.
 */

/**
 * The stored currency wins over the device default.
 *
 * A blank or absent stored code means "nothing to adopt", so the device default
 * is the right answer for a genuinely fresh install.
 */
internal fun preselectedCurrencyCode(
    storedCode: String?,
    deviceDefaultCode: String,
): String = storedCode?.takeIf { it.isNotBlank() } ?: deviceDefaultCode

/**
 * Whether finishing the currency step should write to the settings row.
 *
 * Only when the user actually moved off the preselected currency, or when there
 * is no row to update yet. Persisting the unchanged preselection is precisely
 * how the imported currency got overwritten, so it must not be written.
 */
internal fun shouldPersistCurrency(
    storedCode: String?,
    chosenCode: String,
): Boolean = if (storedCode.isNullOrBlank()) {
    true
} else {
    storedCode != chosenCode
}