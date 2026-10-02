package com.ivy.wallet.domain.deprecated.logic.csv

import com.ivy.legacy.domain.deprecated.logic.csv.model.ImportType
import javax.inject.Inject

class CSVNormalizer @Inject constructor() {

    /**
     * Ivy's own CSV export needs no normalisation. The one adapter that did
     * (Wallet by BudgetBakers) was removed by owner decision.
     */
    @Suppress("UNUSED_PARAMETER")
    fun normalize(rawCSV: String, importType: ImportType): String = rawCSV
}
