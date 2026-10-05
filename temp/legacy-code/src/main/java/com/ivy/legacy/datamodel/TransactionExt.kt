package com.ivy.legacy.datamodel

import com.ivy.base.legacy.Transaction
import com.ivy.data.db.entity.TransactionEntity

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    accountId = accountId,
    type = type,
    amount = amount.toDouble(),
    toAccountId = toAccountId,
    toAmount = toAmount.toDouble(),
    title = title,
    description = description,
    dateTime = dateTime,
    categoryId = categoryId,
    dueDate = dueDate,
    recurringRuleId = recurringRuleId,
    paidForDateTime = paidFor,
    attachmentUrl = attachmentUrl,
    loanId = loanId,
    loanRecordId = loanRecordId,
    id = id,
    isSynced = isSynced,
    isDeleted = isDeleted,
    // Carried so TransactionMapper can tell "this transfer points at an account in
    // somebody ELSE's ledger, and here is who owns it" apart from "this transfer
    // points at an account that does not exist". Those two are indistinguishable
    // without it, and the second must stay an error.
    counterpartProfileId = counterpartProfileId,
)
