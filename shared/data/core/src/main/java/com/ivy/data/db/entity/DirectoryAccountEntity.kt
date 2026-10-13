package com.ivy.data.db.entity

import androidx.room.Entity
import java.util.UUID

/**
 * One account belonging to another profile, published so a transfer can be
 * addressed to it.
 *
 * This is a directory, not a ledger: id, display name and currency only. No
 * balance and no transactions ever cross profiles - the sole exception is a
 * cross-profile transfer, whose second leg is created on its owner's device.
 *
 * ## Identity is (profileId, accountId), never accountId alone
 *
 * [accountId] is the owner's own account UUID, and it is only unique WITHIN one
 * profile. Two phones set up from the same backup, or two people who both bank
 * at the same place and imported the same list, publish the identical id - the
 * server's unique index is the composite `(profileId, localAccountId)` precisely
 * because that is the real uniqueness.
 *
 * While the key was [accountId] alone, a refresh that fetched both profiles'
 * rows inserted them into a table that can hold a given accountId ONCE, and
 * [com.ivy.data.db.dao.read.DirectoryAccountDao.insertAll] is
 * `OnConflictStrategy.REPLACE` - so whichever profile came later in the insert
 * list silently overwrote the other. Measured against a device whose server held
 * 30 rows (`qa_a` 21 + `qa_b` 9, with 9 ids shared), the table after one refresh
 * held 21 rows, all `qa_a`, and zero `qa_b`: the partner's accounts had been
 * REPLACEd away. [com.ivy.data.db.dao.read.DirectoryAccountDao.findOthers] then
 * returned nothing and the destination picker read "No other profiles yet" for
 * ever.
 *
 * The same key is what makes a re-sync update in place rather than accumulate
 * duplicates: re-publishing one account under one profile collides with that
 * profile's row and replaces it, which is how a rename propagates.
 *
 * ## No separate index on `profileId`
 *
 * The leading column of a composite primary key is already indexed, so
 * `findOthers` and `findByProfile` (`WHERE profileId = ?` / `!= ?`) are served
 * by the primary key index. The old `Index("profileId")` would have cost a
 * second write per insert and bought nothing once `profileId` leads the key.
 *
 * Deliberately NOT `@Serializable`. This table is a sync-derived local cache,
 * never part of the user's Ivy backup export, so carrying the UUID serializer
 * would only add a cross-module annotation that every injected consumer of the
 * DAO has to resolve.
 */
@Entity(
    tableName = "directory_accounts",
    primaryKeys = ["profileId", "accountId"],
)
data class DirectoryAccountEntity(
    val profileId: String,
    val accountId: UUID,
    val profileName: String,
    val name: String,
    val currencyCode: String? = null,
)
