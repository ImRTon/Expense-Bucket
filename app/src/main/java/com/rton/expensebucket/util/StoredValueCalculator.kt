package com.rton.expensebucket.util

import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Transaction

fun PaymentMethod.isTransitCardGroup(): Boolean =
    parentId == null && (icon == "transit_card" || name == "電子票證")

fun PaymentMethod.storedValueCurrency(parent: PaymentMethod?): String? =
    storedValueBalance?.currency ?: when {
        isTransitCardGroup() -> null
        icon == "suica" || icon == "icoca" -> "JPY"
        icon in setOf("easycard_card", "ipass_card", "icash") -> "TWD"
        parent?.isTransitCardGroup() == true -> "TWD"
        else -> null
    }

object StoredValueCalculator {
    fun currentBalance(method: PaymentMethod, transactions: List<Transaction>): Double? {
        val balance = method.storedValueBalance ?: return null
        val importedFromCard = method.nfcCardFingerprint?.let { "$it:" }
        // A payment method correction can assign an older expense to this card after reconciliation.
        val spending = transactions.asSequence()
            .filter { it.paymentMethodId == method.id && it.isExpense && !it.isDraft }
            .filterNot { importedFromCard != null && it.nfcReference?.startsWith(importedFromCard) == true }
            .filter { it.currency.equals(balance.currency, ignoreCase = true) }
            .filter { maxOf(it.date, it.paymentMethodChangedAt ?: it.date) > balance.asOf }
            .sumOf { it.amount }
        return balance.amount - spending
    }
}
