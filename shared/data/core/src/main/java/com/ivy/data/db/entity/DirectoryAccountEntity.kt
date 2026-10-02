package com.ivy.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One account belonging to another profile, published so a transfer can be
 * addressed to it.
 *
 * This is a directory, not a ledger: id, display name and currency only. No
 * balance and no transactions ever cross profiles - the sole exception is a
 * cross-profile transfer, whose second leg is created on its owner's device.
 *
 * [accountId] is the owner's own UUID and is the primary key, so an account
 * updates in place as she renames it rather than accumulating duplicates.
 *
 * Deliberately NOT `@Serializable`. This table is a sync-derived local cache,
 * never part of the user's Ivy backup export, so carrying the UUID serializer
 * would only add a cross-module annotation that every injected consumer of the
 * DAO has to resolve.
 */
@Entity(
    tableName = "directory_accounts",
    indices = [Index("profileId")],
)
data class DirectoryAccountEntity(
    @PrimaryKey
    val accountId: UUID,
    val profileId: String,
    val profileName: String,
    val name: String,
    val currencyCode: String? = null,
)
