package com.ivy.domain.usecase.xlsx

import android.net.Uri
import arrow.core.Either
import com.ivy.base.threading.DispatchersProvider
import com.ivy.data.file.FileSystem
import com.ivy.data.model.Transaction
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.usecase.csv.ExportCsvUseCase
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Writes the owner's transactions as a real .xlsx spreadsheet.
 *
 * Deliberately thin: the rows come from [ExportCsvUseCase.exportRows], the same
 * flattened rows the CSV export writes. Two exports that each build their own
 * columns agree on the day they are written and diverge the first time someone
 * reorders one of them.
 */
class ExportXlsxUseCase @Inject constructor(
    private val exportCsvUseCase: ExportCsvUseCase,
    private val transactionRepository: TransactionRepository,
    private val fileSystem: FileSystem,
    private val dispatchers: DispatchersProvider
) {

    suspend fun exportToFile(
        outputFile: Uri,
        // Same default as the CSV export, so both cover every transaction when
        // no scope is given.
        exportScope: suspend TransactionRepository.() -> List<Transaction> = {
            transactionRepository.findAll()
        }
    ): Either<FileSystem.Failure, Unit> = withContext(dispatchers.io) {
        val bytes = XlsxWriter.write(exportCsvUseCase.exportRows(exportScope))
        fileSystem.writeToFile(outputFile, bytes)
    }
}