package com.ivy.data.profile

import com.ivy.data.db.dao.read.DirectoryAccountDao
import com.ivy.data.db.entity.DirectoryAccountEntity
import com.ivy.data.sync.SyncConfig
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

/**
 * The destination picker must never offer this device its own accounts.
 *
 * A regression test with a specific history, which is why it asserts on the
 * STRING that reaches SQL rather than only on the shape of the result. The
 * exclusion used to be a `ProfileId` parameter and callers passed
 * `ProfileId.DEFAULT`, whose value is `"default"`, while this table is keyed by
 * sync profile ids. Every row was compared against `"default"`, nothing matched,
 * and the picker listed the device's own accounts as destination choices.
 *
 * Both id spaces are non-empty strings, so the wrong one yields a perfectly
 * plausible list of the wrong rows. Asserting the argument is what makes that
 * difference visible; asserting only the returned rows would let the bug live on.
 */
class DirectoryRepositoryTest {

    private val dao: DirectoryAccountDao = mockk()
    private val config: SyncConfig = mockk()

    private val mine = row(
        accountId = "11111111-1111-4111-8111-111111111111",
        profileId = "qa_a",
        name = "Dompet Uji Belanja",
    )

    private val theirs = row(
        accountId = "22222222-2222-4222-8222-222222222222",
        profileId = "qa_b",
        name = "Dompet Dia",
    )

    private fun row(accountId: String, profileId: String, name: String) = DirectoryAccountEntity(
        accountId = UUID.fromString(accountId),
        profileId = profileId,
        profileName = profileId,
        name = name,
    )

    /**
     * A DAO that behaves like the real one: [DirectoryAccountDao.findOthers] is
     * `WHERE profileId != :ownProfileId`, so a wrong id produces a wrong list
     * here too. A stub returning a fixed list would hide the bug this test exists
     * to catch.
     */
    private fun daoHolding(rows: List<DirectoryAccountEntity>) {
        coEvery { dao.findOthers(any()) } answers {
            rows.filter { it.profileId != firstArg<String>() }
        }
    }

    @Test
    fun `excludes my own accounts, asked for by this device sync profile id`() = runTest {
        every { config.profileId } returns "qa_a"
        daoHolding(listOf(mine, theirs))

        val shown = DirectoryRepository(dao, config).others()

        // The literal "qa_a" here IS the assertion. The id this used to send,
        // "default", matches no row in this table, so the DAO's exclusion
        // silently became a no-op and MY OWN row came back in the picker.
        coVerify(exactly = 1) { dao.findOthers("qa_a") }
        shown shouldContainExactly listOf(theirs)
    }

    @Test
    fun `offers the other profile accounts, whoever that profile is`() = runTest {
        // The direction must not be hardcoded: the partner's phone has the
        // mirror-image view and must see exactly what this one hides.
        every { config.profileId } returns "qa_b"
        daoHolding(listOf(mine, theirs))

        val shown = DirectoryRepository(dao, config).others()

        coVerify(exactly = 1) { dao.findOthers("qa_b") }
        shown shouldContainExactly listOf(mine)
    }

    @Test
    fun `an unpaired device offers nothing, and does not query with a junk id`() = runTest {
        every { config.profileId } returns null

        val shown = DirectoryRepository(dao, config).others()

        shown shouldBe emptyList<DirectoryAccountEntity>()

        // Not merely an empty result: the query must not run at all, because
        // there is no id to exclude and an unfiltered query would list every row.
        coVerify(exactly = 0) { dao.findOthers(any()) }
    }
}
