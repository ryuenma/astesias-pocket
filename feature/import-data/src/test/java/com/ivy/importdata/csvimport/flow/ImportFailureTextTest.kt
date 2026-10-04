package com.ivy.importdata.csvimport.flow

import com.ivy.data.backup.ImportResult
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

/**
 * Pins what the import result screen says when an import does not fully succeed.
 *
 * The device showed the real defect: a failed ZIP backup rendered "0 rows from
 * the CSV file were not recognized". That names a CSV input the user never chose,
 * counts nothing meaningful, and hides the actual reason the importer recorded.
 */
class ImportFailureTextTest {

    private fun result(
        rowsFound: Int,
        imported: Int,
        errorMessage: String? = null,
    ) = ImportResult(
        rowsFound = rowsFound,
        transactionsImported = imported,
        accountsImported = 0,
        categoriesImported = 0,
        failedRows = persistentListOf(),
        errorMessage = errorMessage,
    )

    /** The reported case: a ZIP that could not be read at all. */
    @Test
    fun `a failed backup reports the importer's reason, not a CSV row count`() {
        failedRowsMessage(
            result(
                rowsFound = 0,
                imported = 0,
                errorMessage = "Could not read the backup. As a plain file: " +
                    "the content was unreadable or empty.",
            )
        ) shouldBe "Could not read the backup. As a plain file: the content was unreadable or empty."
    }

    @Test
    fun `an empty backup reports its own reason`() {
        failedRowsMessage(
            result(rowsFound = 0, imported = 0, errorMessage = "The backup file is empty.")
        ) shouldBe "The backup file is empty."
    }

    @Test
    fun `a partial import reports how many rows were dropped`() {
        failedRowsMessage(result(rowsFound = 356, imported = 320)) shouldBe "36"
    }

    /**
     * The other half of the wording bug: on a clean import the old code still
     * printed "0 rows ... were not recognized", which reads like a defect.
     */
    @Test
    fun `a fully successful import says nothing about dropped rows`() {
        failedRowsMessage(result(rowsFound = 6538, imported = 6538)) shouldBe null
    }

    @Test
    fun `an all-zero result with no recorded reason stays silent rather than lying`() {
        failedRowsMessage(result(rowsFound = 0, imported = 0)) shouldBe null
    }

    @Test
    fun `a recorded reason wins over the row count`() {
        failedRowsMessage(
            result(
                rowsFound = 100,
                imported = 40,
                errorMessage = "The backup file is empty.",
            )
        ) shouldBe "The backup file is empty."
    }
}