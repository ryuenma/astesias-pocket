package com.ivy.data.profile

import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.file.FileSystem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared-preferences key holding the active profile id.
 *
 * Top-level and internal so `ProfileSwitcher` writes the same key `ProfileRegistry`
 * reads. Two private copies of this string would drift and break the switch
 * silently: the app would appear to switch and then reopen the old profile.
 */
internal const val KEY_ACTIVE_PROFILE = "astesia.active_profile"

/**
 * Which profile the app is currently showing, and which exist locally.
 *
 * **Deliberately synchronous.** The active profile is read during Hilt graph
 * construction to decide which database file to open (see `RoomDbModule`). A suspend
 * or Flow-based API there would mean opening a database before its name was known,
 * so this is a plain SharedPrefs read. The cost is a blocking read on first
 * composition only, which is the same read Ivy Wallet already does for its own
 * settings.
 *
 * ## Why one database file per profile
 *
 * Each profile owns a separate `.db` file, so isolation is a property of the
 * filesystem rather than a `WHERE profile_id = ?` clause that every future query
 * would have to remember. Listing the database directory is a complete audit of
 * "whose data is on this phone".
 *
 * The consequence is spelled out in [switchActive]: the database is a `@Singleton`
 * injected into roughly twenty unscoped DAOs, and those DAOs capture the instance
 * they were handed. Handing out a new database object at runtime would leave every
 * existing DAO pointing at the closed old file - a class of bug that is silent
 * (reads succeed against stale data, or throw on a closed handle) and very hard to
 * see in review. So switching closes the store and restarts the process, which
 * re-resolves the whole graph against the new file. See [switchActive].
 */
@Singleton
class ProfileRegistry @Inject constructor(
    private val sharedPrefs: SharedPrefs,
    private val fileSystem: FileSystem,
) {

    /**
     * The profile to open. Falls back to [ProfileId.DEFAULT] rather than throwing:
     * an unreadable preference must not brick the app on launch, and the default
     * profile is the one that holds the user's existing data.
     */
    // NOTE: the single-argument SharedPrefs.getString(key) THROWS IllegalStateException
    // when the key is absent, so the two-argument overload is required here. Reading
    // it the other way crashes the very first launch, before onboarding.
    fun activeProfileId(): ProfileId =
        ProfileId.ofOrNull(sharedPrefs.getString(KEY_ACTIVE_PROFILE, null))
            ?: ProfileId.DEFAULT

    fun activeProfile(): Profile = profiles().firstOrNull { it.id == activeProfileId() }
        ?: Profile(ProfileId.DEFAULT, DEFAULT_PROFILE_NAME)

    fun profiles(): List<Profile> {
        val raw = sharedPrefs.getString(KEY_KNOWN, null) ?: return listOf(defaultProfile())
        val parsed = raw.split(SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(NAME_SEPARATOR, limit = 2)
                val id = ProfileId.ofOrNull(parts.firstOrNull()) ?: return@mapNotNull null
                Profile(id, parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultNameFor(id))
            }
        return parsed.ifEmpty { listOf(defaultProfile()) }
    }

    /**
     * Registers [profile], or updates its name if it is still an unset placeholder.
     *
     * The placeholder rule matters: [ProfileId.DEFAULT] is always present
     * implicitly, so a plain "skip if it exists" guard meant naming your own
     * profile during onboarding silently did nothing. The owner typed a name, it
     * was accepted, and it was never written.
     *
     * A name that is not the placeholder is treated as chosen and is left alone,
     * so a later register() cannot clobber it.
     */
    fun register(profile: Profile) {
        val existing = profiles()
        val current = existing.firstOrNull { it.id == profile.id }
        when {
            current == null -> persist(existing + profile)
            isPlaceholder(current.name, profile.id) && current.name != profile.name ->
                persist(existing.map { if (it.id == profile.id) profile else it })
            else -> Unit
        }
    }

    /**
     * True while [name] is still the auto-generated one, i.e. the user has not
     * chosen anything yet.
     */
    private fun isPlaceholder(name: String, id: ProfileId): Boolean =
        name.isBlank() || name == defaultNameFor(id)

    fun exists(id: ProfileId): Boolean = profiles().any { it.id == id }

    /**
     * True when [id] owns a database file on disk. Used to tell "profile created but
     * never set up" apart from "profile with data in it" without opening the database.
     */
    fun hasLocalData(id: ProfileId): Boolean = fileSystem.databaseExists(id.dbFileName)

    private fun defaultProfile() = Profile(ProfileId.DEFAULT, DEFAULT_PROFILE_NAME)

    private fun defaultNameFor(id: ProfileId): String =
        if (id == ProfileId.DEFAULT) DEFAULT_PROFILE_NAME else id.value

    /**
     * Points the app at [id]. The profile must already be registered.
     *
     * Enforced rather than left to callers because the two read paths disagree if it
     * is not: `activeProfileId` would return [id] while `activeProfile` - which
     * resolves the *name* through the profile list - would fall back to the default.
     * That split-brain state is exactly what makes a switch look like it worked and
     * then show the wrong person's name.
     */
    internal fun setActiveProfile(id: ProfileId) {
        check(profiles().any { it.id == id }) {
            "cannot activate unregistered profile '${id.value}'; call register first"
        }
        sharedPrefs.putString(KEY_ACTIVE_PROFILE, id.value)
    }

    private fun persist(profiles: List<Profile>) {
        sharedPrefs.putString(
            KEY_KNOWN,
            profiles.joinToString(SEPARATOR) { "${it.id.value}$NAME_SEPARATOR${it.name}" },
        )
    }

    private companion object {
        const val KEY_KNOWN = "astesia.known_profiles"
        const val SEPARATOR = "␟"
        const val NAME_SEPARATOR = "␞"
        const val DEFAULT_PROFILE_NAME = "My profile"
    }
}

/**
 * Raised when a caller asks to switch to a profile this device does not have.
 *
 * Distinct from an IO error on purpose: the caller can offer "create this profile"
 * rather than reporting a crash.
 */
class UnknownProfileException(id: ProfileId) :
    IllegalStateException("No such profile: ${id.value}")
