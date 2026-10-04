package com.ivy.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.legacy.unzip
import com.ivy.base.legacy.zip
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.DataObserver
import com.ivy.data.DataWriteEvent
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.BudgetDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.db.dao.read.LoanDao
import com.ivy.data.db.dao.read.LoanRecordDao
import com.ivy.data.db.dao.read.PlannedPaymentRuleDao
import com.ivy.data.db.dao.read.SettingsDao
import com.ivy.data.db.dao.read.TagAssociationDao
import com.ivy.data.db.dao.read.TagDao
import com.ivy.data.db.dao.read.TransactionDao
import com.ivy.data.db.dao.write.WriteBudgetDao
import com.ivy.data.db.dao.write.WriteCategoryDao
import com.ivy.data.db.dao.write.WriteLoanDao
import com.ivy.data.db.dao.write.WriteLoanRecordDao
import com.ivy.data.db.dao.write.WritePlannedPaymentRuleDao
import com.ivy.data.db.dao.write.WriteSettingsDao
import com.ivy.data.db.dao.write.WriteTagAssociationDao
import com.ivy.data.db.dao.write.WriteTagDao
import com.ivy.data.db.dao.write.WriteTransactionDao
import com.ivy.data.file.FileSystem
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.mapper.AccountMapper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject

// TODO: Legacy code, needs improvements
class BackupDataUseCase @Inject constructor(
    private val accountDao: AccountDao,
    private val budgetDao: BudgetDao,
    private val categoryDao: CategoryDao,
    private val loanRecordDao: LoanRecordDao,
    private val loanDao: LoanDao,
    private val plannedPaymentRuleDao: PlannedPaymentRuleDao,
    private val settingsDao: SettingsDao,
    private val transactionDao: TransactionDao,
    private val transactionWriter: WriteTransactionDao,
    private val sharedPrefs: SharedPrefs,
    private val accountRepository: AccountRepository,
    private val accountMapper: AccountMapper,
    private val categoryWriter: WriteCategoryDao,
    private val settingsWriter: WriteSettingsDao,
    private val budgetWriter: WriteBudgetDao,
    private val loanWriter: WriteLoanDao,
    private val loanRecordWriter: WriteLoanRecordDao,
    private val plannedPaymentRuleWriter: WritePlannedPaymentRuleDao,
    @ApplicationContext
    private val context: Context,
    private val json: Json,
    private val dispatchersProvider: DispatchersProvider,
    private val fileSystem: FileSystem,
    private val dataObserver: DataObserver,
    private val tagsReader: TagDao,
    private val tagAssociationReader: TagAssociationDao,
    private val tagsWriter: WriteTagDao,
    private val tagAssociationWriter: WriteTagAssociationDao
) {
    suspend fun exportToFile(
        zipFileUri: Uri
    ) {
        val jsonString = generateJsonBackup()
        val file = createJsonDataFile(jsonString)
        zip(context = context, zipFileUri, listOf(file))
        clearCacheDir()
    }

    private fun createJsonDataFile(jsonString: String): File {
        val fileNamePrefix = "data"
        val fileNameSuffix = ".json"
        val outputDir = context.cacheDir

        val file = File.createTempFile(fileNamePrefix, fileNameSuffix, outputDir)
        FileOutputStream(file).use { it.write(jsonString.toByteArray(Charsets.UTF_16)) }

        return file
    }

    suspend fun generateJsonBackup(): String {
        return withContext(dispatchersProvider.io) {
            val accounts = async { accountDao.findAll() }
            val budgets = async { budgetDao.findAll() }
            val categories = async { categoryDao.findAll() }
            val loanRecords = async { loanRecordDao.findAll() }
            val loans = async { loanDao.findAll() }
            val plannedPaymentRules =
                async { plannedPaymentRuleDao.findAll() }
            val settings = async { settingsDao.findAll() }
            val transactions = async { transactionDao.findAll() }
            val sharedPrefs = async { getSharedPrefsData() }
            val tags = async { tagsReader.findAll() }
            val tagAssociations = async { tagAssociationReader.findAll() }

            val completeData = IvyWalletCompleteData(
                accounts = accounts.await(),
                budgets = budgets.await(),
                categories = categories.await(),
                loanRecords = loanRecords.await(),
                loans = loans.await(),
                plannedPaymentRules = plannedPaymentRules.await(),
                settings = settings.await(),
                transactions = transactions.await(),
                sharedPrefs = sharedPrefs.await(),
                tags = tags.await(),
                tagAssociations = tagAssociations.await()
            )

            json.encodeToString(completeData)
        }
    }

    private fun getSharedPrefsData(): HashMap<String, String> {
        val hashmap = HashMap<String, String>()
        hashmap[SharedPrefs.SHOW_NOTIFICATIONS] =
            sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true).toString()

        hashmap[SharedPrefs.APP_LOCK_ENABLED] =
            sharedPrefs.getBoolean(SharedPrefs.APP_LOCK_ENABLED, false).toString()

        hashmap[SharedPrefs.HIDE_CURRENT_BALANCE] =
            sharedPrefs.getBoolean(SharedPrefs.HIDE_CURRENT_BALANCE, false).toString()

        hashmap[SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE] =
            sharedPrefs.getBoolean(SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE, false).toString()

        return hashmap
    }

    suspend fun importBackupFile(
        backupFileUri: Uri,
        onProgress: suspend (progressPercent: Double) -> Unit
    ): ImportResult = withContext(dispatchersProvider.io) {
        return@withContext try {
            // Prefer the zip. If that fails the file may be a bare .json, so fall
            // back to reading it directly -- but decode by BOM, not a forced
            // UTF_16. The old fallback decoded raw ZIP bytes as UTF-16 text, which
            // is guaranteed garbage and then fed to importJson.
            var readFailure: String? = null
            val jsonString: String? = try {
                extractAndReadBackupZip(backupFileUri, onProgress)
            } catch (zipError: Exception) {
                Timber.w("Not a readable zip, trying as a raw backup file: $zipError")
                fileSystem.read(backupFileUri).getOrNull().also {
                    if (it == null) {
                        readFailure = "Could not read the backup. As a zip: " +
                            "${zipError.message ?: zipError::class.java.simpleName}. " +
                            "As a plain file: the content was unreadable or empty."
                    }
                }
            }

            if (readFailure != null) {
                ImportResult(
                    rowsFound = 0,
                    transactionsImported = 0,
                    accountsImported = 0,
                    categoriesImported = 0,
                    failedRows = persistentListOf(),
                    errorMessage = readFailure
                )
            } else if (jsonString.isNullOrBlank()) {
                ImportResult(
                    rowsFound = 0,
                    transactionsImported = 0,
                    accountsImported = 0,
                    categoriesImported = 0,
                    failedRows = persistentListOf(),
                    errorMessage = "The backup file is empty."
                )
            } else {
                importJson(jsonString, onProgress, clearCacheDir = true)
            }
        } catch (e: Exception) {
            // Previously swallowed into an all-zero result, so a corrupt or
            // wrongly-encoded backup reported "0 imported" with no reason given.
            Timber.e("Import error: $e")
            ImportResult(
                rowsFound = 0,
                transactionsImported = 0,
                accountsImported = 0,
                categoriesImported = 0,
                failedRows = persistentListOf(),
                errorMessage = e.message ?: e::class.java.simpleName
            )
        } finally {
            dataObserver.post(DataWriteEvent.AllDataChange)
        }
    }

    private suspend fun extractAndReadBackupZip(
        backupFileUri: Uri,
        onProgress: suspend (progressPercent: Double) -> Unit
    ): String? {
        val folderName = "backup" + System.currentTimeMillis()
        val cacheFolderPath = File(context.cacheDir, folderName)

        unzip(context, backupFileUri, cacheFolderPath)

        val filesArray = cacheFolderPath.listFiles()

        onProgress(0.05)

        if (filesArray == null || filesArray.isEmpty()) {
            error("Couldn't unzip")
        }

        val filesList = filesArray.toList().filter {
            hasJsonExtension(it)
        }

        onProgress(0.1)

        if (filesList.size != 1) {
            error("Didn't unzip exactly one file.")
        }

        // No forced charset: Ivy wrote UTF-16, but a UTF-8 backup must work too.
        return fileSystem.read(filesList[0].toUri()).getOrNull()
    }

    suspend fun importJson(
        jsonString: String,
        onProgress: suspend (Double) -> Unit = {},
        clearCacheDir: Boolean = false,
    ): ImportResult {
        val accommodated = accommodateExistingAccountsAndCategories(jsonString)
        val ivyWalletCompleteData = accommodated?.let {
            json.decodeFromString<IvyWalletCompleteData>(it.json)
        } ?: error("Failed to parse backup JSON.")

        onProgress(0.4)
        insertDataToDb(completeData = ivyWalletCompleteData, onProgress = onProgress)
        onProgress(1.0)

        if (clearCacheDir) {
            clearCacheDir()
        }

        return ImportResult(
            rowsFound = ivyWalletCompleteData.transactions.size,
            transactionsImported = ivyWalletCompleteData.transactions.size,
            accountsImported = ivyWalletCompleteData.accounts.size,
            categoriesImported = ivyWalletCompleteData.categories.size,
            failedRows = persistentListOf(),
            duplicateNameWarnings = accommodated.ambiguousNames.toImmutableList()
        )
    }

    private suspend fun accommodateExistingAccountsAndCategories(
        jsonString: String?
    ): AccommodatedBackup? {
        if (jsonString == null) return null

        val ivyWalletCompleteData = json.decodeFromString<IvyWalletCompleteData>(jsonString)
        val plan = getReplacementPairs(ivyWalletCompleteData)

        // Typed explicitly rather than inferred from the `String?` parameter:
        // assigning inside a lambda invalidates the smart cast, and `!!` on
        // every iteration would just be that cast written louder.
        var modifiedString: String = jsonString
        plan.pairs.forEach {
            modifiedString = modifiedString.replace(it.first.toString(), it.second.toString())
        }

        return AccommodatedBackup(
            json = modifiedString,
            ambiguousNames = plan.ambiguousNames,
        )
    }

    private suspend fun insertDataToDb(
        completeData: IvyWalletCompleteData,
        onProgress: suspend (progressPercent: Double) -> Unit = {}
    ) {
        withContext(dispatchersProvider.io) {
            transactionWriter.saveMany(completeData.transactions)
            onProgress(0.6)

            val accounts = async {
                val domainAccounts = with(accountMapper) {
                    completeData.accounts.mapNotNull { entity ->
                        entity.toDomain().getOrNull()
                    }
                }
                accountRepository.saveMany(domainAccounts)
            }
            val budgets = async { budgetWriter.saveMany(completeData.budgets) }
            val categories =
                async { categoryWriter.saveMany(completeData.categories) }
            accounts.await()
            budgets.await()
            categories.await()

            onProgress(0.7)

            val loans = async { loanWriter.saveMany(completeData.loans) }
            val loanRecords =
                async { loanRecordWriter.saveMany(completeData.loanRecords) }

            loans.await()
            loanRecords.await()

            onProgress(0.8)

            val tags = async { tagsWriter.save(completeData.tags) }
            val tagAssociations = async { tagAssociationWriter.save(completeData.tagAssociations) }

            val plannedPayments =
                async { plannedPaymentRuleWriter.saveMany(completeData.plannedPaymentRules) }
            val settings = async {
                settingsWriter.deleteAll()
                settingsWriter.saveMany(completeData.settings)
            }

            sharedPrefs.putBoolean(
                SharedPrefs.SHOW_NOTIFICATIONS,
                (completeData.sharedPrefs[SharedPrefs.SHOW_NOTIFICATIONS] ?: "true").toBoolean()
            )

            sharedPrefs.putBoolean(
                SharedPrefs.APP_LOCK_ENABLED,
                (completeData.sharedPrefs[SharedPrefs.APP_LOCK_ENABLED] ?: "false").toBoolean()
            )

            sharedPrefs.putBoolean(
                SharedPrefs.HIDE_CURRENT_BALANCE,
                (completeData.sharedPrefs[SharedPrefs.HIDE_CURRENT_BALANCE] ?: "false").toBoolean()
            )

            sharedPrefs.putBoolean(
                SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE,
                (
                        completeData.sharedPrefs[SharedPrefs.TRANSFERS_AS_INCOME_EXPENSE]
                            ?: "false"
                        ).toBoolean()
            )

            plannedPayments.await()
            settings.await()
            tags.await()
            tagAssociations.await()

            onProgress(0.9)
        }
    }

    /**
     * One row on its way in, tagged with where it came from.
     *
     * The tag is the point: the old code inferred "is this from the backup?"
     * with `list.contains(row)`, a data-class equality check that answers
     * wrongly whenever an existing row happens to be identical to a backup row.
     * Carrying the origin alongside the row cannot be wrong.
     */
    private data class Sourced<T>(
        val row: T,
        val fromBackup: Boolean,
    )

    /**
     * The rewritten backup JSON, plus the names whose ids could not be
     * reconciled.
     *
     * [ambiguousNames] is empty in the healthy case. When it is not, the import
     * still proceeds - refusing to restore somebody's ledger over a naming
     * collision is its own kind of data loss - but the collisions are reported
     * to the user instead of being resolved by guessing.
     */
    private data class AccommodatedBackup(
        val json: String,
        val ambiguousNames: List<String>,
    )

    /**
     * Ids to rewrite in the incoming backup.
     *
     * [ambiguousNames] names the groups no pair was emitted for; see
     * [replacementPlan] for why those have no single correct answer.
     */
    private data class ReplacementPlan(
        val pairs: List<Pair<UUID, UUID>>,
        val ambiguousNames: List<String>,
    )

    /**
     * Pair up backup Ids with existing Ids so an import does not duplicate rows.
     *
     * **The rule is "exactly one existing and exactly one backup", not "the name
     * appears twice".** Those differ, and the difference is the bug: this used to
     * accept any name group of size 2, which silently produced two different
     * wrong answers.
     *
     *  - 2 existing, 0 backup (a name duplicated on the device). The old code
     *    paired the two EXISTING ids to each other and rewrote every reference
     *    from one to the other across the whole backup. That is not
     *    deduplication, it is corruption of ids that were already correct, and
     *    it was silent.
     *  - 3 or more of anything. The old code dropped these via
     *    `filter { it.value.size == 2 }` and said nothing, so the import
     *    reported success while reconciling nothing.
     *
     * Pairing is only well-defined when there is exactly one candidate on each
     * side; that is the sole case where "A must become B" has a single answer.
     * Every other composition is ambiguous, so it is reported and no pair is
     * emitted. Guessing is never right here: the backup's transaction rows
     * point at specific category ids, so picking the wrong one silently files a
     * user's spending under the wrong category.
     *
     * Neither duplicate-name fixture in this repo's test resources
     * (`450-150.json`, the owner's real sample) contains a repeated name, so
     * this changes nothing for any known-good backup.
     */
    private fun <T> replacementPlan(
        existing: List<T>,
        fromBackup: List<T>,
        name: (T) -> String,
        id: (T) -> UUID,
    ): ReplacementPlan {
        val sourced = existing.map { Sourced(it, fromBackup = false) } +
            fromBackup.map { Sourced(it, fromBackup = true) }

        val pairs = mutableListOf<Pair<UUID, UUID>>()
        val ambiguous = mutableListOf<String>()

        for ((groupName, group) in sourced.groupBy { name(it.row) }) {
            val existingRows = group.filter { !it.fromBackup }.map { it.row }
            val backupRows = group.filter { it.fromBackup }.map { it.row }

            if (existingRows.size == 1 && backupRows.size == 1) {
                // The one unambiguous case: one of ours, one of theirs.
                // Backup id -> existing id.
                pairs += Pair(id(backupRows[0]), id(existingRows[0]))
            } else if (existingRows.size > 1 || backupRows.size > 1) {
                // Duplicates on one side or on both: no single defensible pair.
                Timber.w(
                    "Backup import: '$groupName' appears ${existingRows.size}x in the " +
                        "database and ${backupRows.size}x in the backup. Ids for it " +
                        "cannot be reconciled without guessing, so no replacement " +
                        "was made - expect this category to be duplicated."
                )
                ambiguous += groupName
            }
            // A name only the backup has, appearing exactly once, needs no
            // reconciliation and is not worth reporting.
        }

        return ReplacementPlan(pairs = pairs, ambiguousNames = ambiguous.distinct().sorted())
    }

    /**
     * Replace account & category Ids in backup data with existing Ids.
     *
     * Ambiguous names are returned rather than resolved; see [replacementPlan].
     */
    private suspend fun getReplacementPairs(
        completeData: IvyWalletCompleteData
    ): ReplacementPlan {
        return withContext(dispatchersProvider.io) {
            val existingAccountsList = accountDao.findAll()
            val existingCategoryList = categoryDao.findAll()

            val backupAccountsList = completeData.accounts
            val backupCategoryList = completeData.categories

            if (existingAccountsList.isEmpty() && existingCategoryList.isEmpty()) {
                return@withContext ReplacementPlan(pairs = emptyList(), ambiguousNames = emptyList())
            }

            val accounts = async {
                replacementPlan(
                    existing = existingAccountsList,
                    fromBackup = backupAccountsList,
                    name = { it.name },
                    id = { it.id },
                )
            }
            val categories = async {
                replacementPlan(
                    existing = existingCategoryList,
                    fromBackup = backupCategoryList,
                    name = { it.name },
                    id = { it.id },
                )
            }

            val accountsPlan = accounts.await()
            val categoriesPlan = categories.await()

            ReplacementPlan(
                pairs = accountsPlan.pairs + categoriesPlan.pairs,
                ambiguousNames = accountsPlan.ambiguousNames + categoriesPlan.ambiguousNames,
            )
        }
    }

    private fun hasJsonExtension(file: File): Boolean {
        val name = file.name
        val lastIndexOf = name.lastIndexOf(".")
        if (lastIndexOf == -1) {
            return false
        }

        return (name.substring(lastIndexOf).equals(".json", true))
    }

    private fun clearCacheDir() {
        context.cacheDir.deleteRecursively()
    }
}
