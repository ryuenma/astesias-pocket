package com.ivy.data.db.dao.fake

import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.entity.PlannedPaymentRuleEntity
import org.jetbrains.annotations.VisibleForTesting
import java.util.UUID

@VisibleForTesting
class FakePlannedPaymentDao : PlannedPaymentRuleDao, WritePlannedPaymentRuleDao {
    private val items = mutableListOf<PlannedPaymentRuleEntity>()

    override suspend fun findAll(): List<PlannedPaymentRuleEntity> {
        return items
    }

    override suspend fun findByIsSyncedAndIsDeleted(
        synced: Boolean,
        deleted: Boolean
    ): List<PlannedPaymentRuleEntity> {
        TODO("Not yet implemented")
    }

    override suspend fun findAllByOneTime(oneTime: Boolean): List<PlannedPaymentRuleEntity> {
        TODO("Not yet implemented")
    }

    override suspend fun findById(id: UUID): PlannedPaymentRuleEntity? {
        return items.find { it.id == id }
    }

    override suspend fun countPlannedPayments(): Long {
        TODO("Not yet implemented")
    }

    /**
     * Mirrors the SQL filter: a rule has a reminder only when its day is set.
     *
     * Implemented rather than `TODO` because the scheduler reads this to decide
     * what to arm, and a fake that threw there would make any test touching the
     * scheduler fail for a reason unrelated to what it is testing.
     */
    override suspend fun findAllWithBillReminder(): List<PlannedPaymentRuleEntity> =
        items.filter { !it.isDeleted && it.dueDayOfMonth != null }

    override suspend fun save(value: PlannedPaymentRuleEntity) {
        items.add(value)
    }

    override suspend fun saveMany(values: List<PlannedPaymentRuleEntity>) {
        values.forEach { save(it) }
    }

    override suspend fun deletedByAccountId(accountId: UUID) {
        items.removeIf { it.accountId == accountId }
    }

    override suspend fun deleteById(id: UUID) {
        items.removeIf { it.id == id }
    }

    override suspend fun deleteAll() {
        items.clear()
    }
}