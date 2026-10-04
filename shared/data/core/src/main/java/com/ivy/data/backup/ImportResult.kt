package com.ivy.data.backup

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
    /**
     * Names the import could not safely reconcile against the database.
     *
     * Fork: `getReplacementPairs` used to pair ids by name and drop any name
     * group that did not fit, so a name appearing three or more times was
     * ignored with no signal at all - the import reported success having
     * silently reconciled nothing. Duplicated names are now reported here
     * instead.
     *
     * NOT an [errorMessage]: the import itself succeeded and the user's data
     * is intact. What happened is that this category may now exist twice, which
     * is worth saying out loud rather than hiding.
     */
    val duplicateNameWarnings: ImmutableList<String> = persistentListOf(),
) {
    val isSuccess: Boolean get() = errorMessage == null
}
