package com.rton.expensebucket.ui.model

data class TransactionPrefill(
    val amount: Double? = null,
    val note: String = "",
    val isExpense: Boolean = true,
    val date: Long = System.currentTimeMillis(),
    val source: String = "ocr",
    val currency: String = "TWD",
    val exchangeRate: Double = 1.0
)
