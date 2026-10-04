package com.ivy.importdata.csvimport.flow

import com.ivy.data.backup.ImportResult

/**
 * What the result screen says about the rows that did not make it in.
 *
 * Two jobs, both forced by real behaviour on the device:
 *
 *  - A failed ZIP backup has no CSV rows at all. The old fixed wording read
 *    "0 rows from the CSV file were not recognized", which named an input the
 *    user never chose and gave no reason. If the importer recorded a reason, show
 *    that instead.
 *  - When nothing failed, there is no row count worth printing. "0 rows were not
 *    recognized" under a Success heading is noise that reads like a defect.
 *
 * Kept free of Compose so both rules are unit-testable off-device.
 */
internal fun failedRowsMessage(result: ImportResult): String? {
    result.errorMessage?.let { return it }
    val notImported = result.rowsFound - result.transactionsImported
    return if (notImported > 0) "$notImported" else null
}