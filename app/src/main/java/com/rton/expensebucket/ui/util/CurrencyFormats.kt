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

    /**
     * Compact amount format for list views: uses "$" for TWD (or matching settlement currency)
     * to avoid repeating "TWD" on every line, and prefixes currency code only for foreign currencies.
     */
    fun formatCompactAmount(
        currency: String,
        amount: Double,
        settlementCurrency: String = "TWD"
    ): String {
        val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = fractionDigitsFor(currency)
        }
        val formattedNumber = numberFormat.format(amount)
        return if (currency == settlementCurrency && currency == "TWD") {
            "$$formattedNumber"
        } else {
            "$currency $formattedNumber"
        }
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
