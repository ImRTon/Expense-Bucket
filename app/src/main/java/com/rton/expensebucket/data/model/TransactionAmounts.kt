package com.rton.expensebucket.data.model

fun Transaction.effectiveAmount(): Double = personalAmount ?: amount

fun Transaction.effectiveConvertedAmount(): Double = effectiveAmount() * exchangeRate

fun Transaction.billedConvertedAmount(): Double = amount * exchangeRate
