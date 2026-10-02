package com.ivy.data.backup

import kotlinx.collections.immutable.ImmutableList

data class ImportResult(
    val rowsFound: Int,
    val transactionsImported: Int,
    val accountsImported: Int,
    val categoriesImported: Int,
    val failedRows: ImmutableList<CSVRow>,
    /**
     * Why the import failed, when it did.
     *
     * Fork: this did not exist, so every failure path returned an all-zero
     * [ImportResult] and the UI reported "0 imported" with no explanation - a
     * corrupted or wrongly-encoded backup looked identical to an empty one. Callers
     * should surface this rather than reporting a silent success.
     */
    val errorMessage: String? = null,
) {
    val isSuccess: Boolean get() = errorMessage == null
}
