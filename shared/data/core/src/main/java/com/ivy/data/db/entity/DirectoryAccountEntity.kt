package com.ivy.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One account belonging to another profile, published so a transfer can be
 * addressed to it.
 *
 * This is a directory, not a ledger: id, display name and currency only. No
 * balance and no transactions ever cross profiles - the sole exception is a
 * cross-profile transfer, whose second leg is created explicitly on its owner's
 * device.
 *
 * [accountId] is the owner's own UUID and is the primary key, so the same account
 * updates in place as she renames it rather than accumulating duplicates.
 */
@Serializable
@Entity(
    tableName = "directory_accounts",
    indices = [Index("profileId")],
)
data class DirectoryAccountEntity(
    @PrimaryKey
    @SerialName("accountId")
    @Serializable(with = com.ivy.base.kotlinxserilzation.KSerializerUUID::class)
    val accountId: UUID,

    @SerialName("profileId")
    val profileId: String,

    @SerialName("profileName")
    val profileName: String,

    @SerialName("name")
    val name: String,

    @SerialName("currencyCode")
    val currencyCode: String? = null,
)
