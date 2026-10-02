package com.ivy.data.profile

import java.util.Locale

/**
 * Identifies one person's financial data within the app.
 *
 * A [ProfileId] is used verbatim as part of a database filename, which makes it a
 * filesystem boundary: a value of `../../databases/other` would open a different
 * database than intended. Parsing is therefore strict allow-list validation rather
 * than sanitisation - anything outside `[a-z0-9_]` is rejected outright instead of
 * being stripped, so a malformed id can never quietly collapse onto another profile.
 *
 * Display names (a couple's names, however spelled or capitalised) are stored
 * separately in [Profile] and never reach a path.
 */
@JvmInline
value class ProfileId private constructor(val value: String) {

    val dbFileName: String get() = "$value.db"

    companion object {
        private val VALID = Regex("^[a-z0-9_]{1,32}$")

        /**
         * The profile used by a single-person install. Ivy Wallet's original database
         * was a single fixed file; this profile owns it so an existing install keeps
         * its data across the upgrade.
         */
        val DEFAULT = of("default")

        fun of(raw: String): ProfileId {
            val normalised = raw.trim().lowercase(Locale.ROOT)
            require(normalised.matches(VALID)) {
                "ProfileId must match ${VALID.pattern} (a-z, 0-9, underscore; 1-32 chars) " +
                    "but was '$raw'"
            }
            return ProfileId(normalised)
        }

        fun ofOrNull(raw: String?): ProfileId? =
            raw?.let { runCatching { of(it) }.getOrNull() }
    }
}

/**
 * A profile plus the human-facing name shown in the switcher. [name] is free text and
 * is never interpolated into a file path - see [ProfileId].
 */
data class Profile(
    val id: ProfileId,
    val name: String,
)
