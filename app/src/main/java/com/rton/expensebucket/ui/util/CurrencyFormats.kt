package com.rton.expensebucket.ui.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormats {
    val supportedCurrencies: List<String> = listOf(
        "TWD", "JPY", "USD", "EUR", "KRW", "GBP", "THB", "VND"
    )

    fun formatAmount(currency: String, amount: Double): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = fractionDigitsFor(currency)
        }
        return "$currency ${numberFormat.format(amount)}"
    }

    fun formatRate(rate: Double): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 4
        }
        return numberFormat.format(rate)
    }

    private fun fractionDigitsFor(currency: String): Int {
        return when (currency) {
            "JPY", "KRW", "VND" -> 0
            else -> 2
        }
    }
}
