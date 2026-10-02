package com.ivy.wallet.domain.deprecated.logic.csv

import com.ivy.base.legacy.Transaction
import com.ivy.base.model.TransactionType
import com.ivy.legacy.domain.deprecated.logic.csv.model.ImportType
import com.ivy.legacy.utils.toLowerCaseLocal
import com.ivy.wallet.domain.deprecated.logic.csv.model.JoinResult
import com.ivy.wallet.domain.deprecated.logic.csv.model.RowMapping
import javax.inject.Inject

class CSVMapper @Inject constructor() {

    @ExperimentalStdlibApi
    fun mapping(type: ImportType, headerRow: String?) = when (type) {
        ImportType.IVY -> {
            if (headerRow?.contains("Currency") == true) {
                ivyMappingV2()
            } else {
                ivyMappingV1()
            }
        }

    }

    private fun ivyMappingV1() = RowMapping(
        date = 0,
        title = 1,
        category = 2,
        account = 3,
        amount = 4,
        type = 5,
        transferAmount = 6,
        toAccount = 7,
        description = 8,
        dueDate = 9,

        id = null // Don't map because it fcks up the sync with Insufficient Permission error
    )

    private fun ivyMappingV2() = RowMapping(
        date = 0,
        title = 1,
        category = 2,
        account = 3,
        amount = 4,
        accountCurrency = 5,
        type = 6,
        transferAmount = 7,
        // Transfer Currency - 8
        toAccount = 9,
        toAmount = 10,
        toAccountCurrency = 11,
        // skip "Receive Currency"
        description = 12,
        dueDate = 13,

        id = null, // 14 - Don't map because it fcks up the sync with Insufficient Permission error

        accountColor = 15,
        accountOrderNum = 16,
        accountIcon = 21,
        categoryColor = 17,
        categoryOrderNum = 18,
        categoryIcon = 22,
        toAccountColor = 19,
        toAccountOrderNum = 20,
        toAccountIcon = 23
    )

    // Praseto - https://play.google.com/store/apps/details?id=com.realbyteapps.moneymanagerfree&hl=en&gl=US
}
