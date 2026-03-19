package com.rton.expensebucket

import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.effectiveAmount
import com.rton.expensebucket.data.model.effectiveConvertedAmount
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionAmountsTest {

    @Test
    fun usesPersonalAmountWhenPresent() {
        val transaction = Transaction(
            amount = 1200.0,
            personalAmount = 300.0,
            exchangeRate = 1.0
        )

        assertEquals(300.0, transaction.effectiveAmount(), 0.001)
        assertEquals(300.0, transaction.effectiveConvertedAmount(), 0.001)
    }

    @Test
    fun fallsBackToAmountWhenPersonalAmountMissing() {
        val transaction = Transaction(
            amount = 1200.0,
            personalAmount = null,
            exchangeRate = 1.0
        )

        assertEquals(1200.0, transaction.effectiveAmount(), 0.001)
        assertEquals(1200.0, transaction.effectiveConvertedAmount(), 0.001)
    }
}
