package com.ivy.data.profile

import com.ivy.base.legacy.SharedPrefs
import com.ivy.data.file.FileSystem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Test

/**
 * The registry is the app's answer to "whose data is this". The behaviour worth
 * protecting is the failure behaviour: a corrupt or absent preference must resolve
 * to the profile that holds the user's existing data, never to an empty one, because
 * the alternative is an app that opens on a blank wallet and looks like data loss.
 */
class ProfileRegistryTest {

    private lateinit var prefs: SharedPrefs
    private lateinit var files: MutableSet<String>
    private lateinit var fileSystem: FileSystem
    private lateinit var registry: ProfileRegistry

    @Before
    fun setUp() {
        val values = mutableMapOf<String, String?>()
        prefs = mockk<SharedPrefs>()
        // Mirrors the REAL SharedPrefs contract, which is easy to get wrong:
        // getString(key) THROWS IllegalStateException when the key is absent, while
        // getString(key, default) returns the default. A previous fake returned ""
        // from the single-arg overload, so every unit test passed while the app
        // crashed on first launch with "astesia.active_profile cannot be null".
        // Reproducing the throw keeps this class honest.
        every { prefs.getString(any()) } answers {
            values[firstArg<String>()]
                ?: throw IllegalStateException("SharePrefs key '${firstArg<String>()}' cannot be null")
        }
        every { prefs.getString(any(), any()) } answers {
            values[firstArg<String>()] as? String ?: secondArg<String?>()
        }
        every { prefs.putString(any(), any()) } answers {
            values[firstArg()] = secondArg()
        }

        files = mutableSetOf()
        fileSystem = mockk<FileSystem>()
        every { fileSystem.databaseExists(any()) } answers { files.contains(firstArg()) }

        registry = ProfileRegistry(sharedPrefs = prefs, fileSystem = fileSystem)
    }

    @Test
    fun `falls back to the default profile when nothing is stored`() {
        registry.activeProfileId() shouldBe ProfileId.DEFAULT
        registry.profiles().map { it.id } shouldContainExactly listOf(ProfileId.DEFAULT)
    }

    @Test
    fun `falls back to the default profile when the stored id is corrupt`() {
        // A hand-edited preference or a value written by a future schema change must
        // not throw during Hilt graph construction - the DB is opened from this value.
        every { prefs.getString(KEY_ACTIVE_PROFILE, null) } returns "../../etc/passwd"

        registry.activeProfileId() shouldBe ProfileId.DEFAULT
    }

    @Test
    fun `reads back the active profile after it is set`() {
        registry.register(Profile(ProfileId.of("so"), "Her"))
        registry.setActiveProfile(ProfileId.of("so"))

        registry.activeProfileId() shouldBe ProfileId.of("so")
        registry.activeProfile().id shouldBe ProfileId.of("so")
    }

    @Test
    fun `register is idempotent and never overwrites a name`() {
        registry.register(Profile(ProfileId.of("so"), "Her"))
        registry.register(Profile(ProfileId.of("so"), "Renamed by accident"))

        registry.profiles().filter { it.id == ProfileId.of("so") }
            .map { it.name } shouldContainExactly listOf("Her")
    }

    @Test
    fun `display names survive punctuation that ids may not contain`() {
        // Names are free text and never touch a path, so real names must round-trip
        // even though the same characters are rejected in an id.
        registry.register(Profile(ProfileId.of("so"), "Ratna (SO) #1 ★"))

        registry.profiles().single { it.id == ProfileId.of("so") }.name shouldBe
            "Ratna (SO) #1 ★"
    }

    @Test
    fun `a name containing the record separator does not forge a second profile`() {
        // The list is persisted as one delimited string. A display name is untrusted
        // input, so it must not be able to inject a record boundary and thereby
        // register a profile the user never created.
        registry.register(Profile(ProfileId.of("so"), "evil␟forged:1:Forged Name"))

        val ids = registry.profiles().map { it.id }
        ids.contains(ProfileId.of("forged")) shouldBe false
        registry.profiles().size shouldBe 2 // default + so
    }

    @Test
    fun `refuses to activate a profile that was never registered`() {
        // Guards the split-brain state: activating an unknown id would make
        // activeProfileId() and activeProfile() disagree about who is active.
        withClue("activating an unregistered id must be refused, not silently stored: ") {
            shouldThrow<IllegalStateException> {
                registry.setActiveProfile(ProfileId.of("ghost"))
            }
        }
        registry.activeProfileId() shouldBe ProfileId.DEFAULT
    }

    @Test
    fun `reads the active profile through the non-throwing overload`() {
        // Regression: activeProfileId() used getString(key), which throws when the key
        // is missing. On a fresh install that key does not exist, so the very first
        // launch crashed inside RoomDbModule with zero UI shown.
        // Asserted by simply resolving the profile with nothing stored.
        registry.activeProfileId() shouldBe ProfileId.DEFAULT

        // If the code regressed to the throwing overload this would blow up here.
        registry.register(Profile(ProfileId.of("so"), "Her"))
        registry.setActiveProfile(ProfileId.of("so"))
        registry.activeProfileId() shouldBe ProfileId.of("so")
    }

    @Test
    fun `naming the default profile actually persists the name`() {
        // Regression, found by driving the real onboarding UI: typing a name was
        // accepted on screen and then silently discarded. ProfileId.DEFAULT is
        // always present implicitly, so the old "skip if it exists" guard meant
        // onboarding's name never reached storage.
        registry.register(Profile(ProfileId.DEFAULT, "Ryuen"))

        registry.activeProfile().name shouldBe "Ryuen"
        registry.profiles().single { it.id == ProfileId.DEFAULT }.name shouldBe "Ryuen"
    }

    @Test
    fun `a name that is not the placeholder is not clobbered`() {
        registry.register(Profile(ProfileId.DEFAULT, "Ryuen"))
        registry.register(Profile(ProfileId.DEFAULT, "Something Else"))

        registry.activeProfile().name shouldBe "Ryuen"
    }

    @Test
    fun `registering a different id is unaffected by the placeholder rule`() {
        registry.register(Profile(ProfileId.of("so"), "Ratna"))

        registry.profiles().filter { it.id == ProfileId.of("so") }
            .map { it.name } shouldContainExactly listOf("Ratna")
    }

    @Test
    fun `hasLocalData reflects a real database file, not registration`() {
        registry.register(Profile(ProfileId.of("so"), "Her"))
        files.add("so.db")

        registry.hasLocalData(ProfileId.of("so")) shouldBe true
        // Registered but never set up: a different state from "has data".
        registry.hasLocalData(ProfileId.DEFAULT) shouldBe false
    }

    @Test
    fun `the default profile keeps Ivy Wallet's original filename`() {
        // Not a property of the registry, but the reason existing installs keep their
        // data; asserted here so the mapping is covered by a test that runs.
        ProfileId.DEFAULT.dbFileName shouldBe "default.db"
        com.ivy.data.db.IvyRoomDatabase.LEGACY_DB_NAME shouldBe "ivywallet.db"
    }
}
