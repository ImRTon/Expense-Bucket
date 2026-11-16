package com.rton.expensebucket

import com.rton.expensebucket.data.model.TransactionItem
import com.rton.expensebucket.data.model.TransactionItemConverter
import com.rton.expensebucket.data.model.TransactionItemLogic
import com.rton.expensebucket.ui.screens.AddTransactionFormDraft
import com.rton.expensebucket.ui.screens.AddTransactionFormLogic
import org.junit.Assert.*
import org.junit.Test

class TransactionItemTest {
    @Test
    fun create_trimsNameAndRejectsInvalidAmounts() {
        assertEquals(TransactionItem("豆腐", 20.5), TransactionItemLogic.create(" 豆腐 ", "20.50"))
        assertEquals(TransactionItem("贈品", 0.0), TransactionItemLogic.create("贈品", "0"))
        assertNull(TransactionItemLogic.create("  ", "20"))
        listOf("", ".", "-1", "NaN", "Infinity", "1.2.3").forEach {
            assertNull(TransactionItemLogic.create("豆腐", it))
        }
    }

    @Test
    fun total_addsDecimalAmountsWithoutFloatingPointDrift() {
        assertEquals(0.3, TransactionItemLogic.total(listOf(TransactionItem("A", 0.1), TransactionItem("B", 0.2))), 0.0)
        assertEquals(0.0, TransactionItemLogic.total(emptyList()), 0.0)
    }

    @Test
    fun converter_roundTripsUnicodeDuplicatesAndCsvSpecialCharacters() {
        val items = listOf(TransactionItem("豆腐, \"嫩\"\n小份", 20.5), TransactionItem("豆干", 25.0), TransactionItem("豆干", 0.0))
        val converter = TransactionItemConverter()
        assertEquals(items, converter.decode(converter.encode(items)))
        assertEquals(emptyList<TransactionItem>(), converter.decode(""))
        assertEquals(emptyList<TransactionItem>(), converter.decode("[]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun converter_rejectsInvalidDetailsInsteadOfLosingThemSilently() {
        TransactionItemConverter().decode("""[{"name":"豆腐","amount":-1}]""")
    }

    @Test
    fun buildTransaction_keepsItemTotalIndependentAndClearsItemsForNextEntry() {
        val items = listOf(TransactionItem("豆腐", 20.0), TransactionItem("豆干", 25.0))
        val draft = AddTransactionFormDraft(amountText = "40", note = "購物\n折扣 5 元", items = items)
        val transaction = AddTransactionFormLogic.buildTransaction(null, null, draft, "TWD")!!
        assertEquals(40.0, transaction.amount, 0.0)
        assertEquals(45.0, TransactionItemLogic.total(transaction.items), 0.0)
        assertEquals("購物\n折扣 5 元", transaction.note)
        val edited = AddTransactionFormLogic.buildTransaction(transaction.copy(id = 12), null, draft, "TWD")!!
        assertEquals(12L, edited.id)
        assertEquals(items, edited.items)
        assertTrue(AddTransactionFormLogic.resetForSaveAndAddAnother(draft).items.isEmpty())
        assertTrue(AddTransactionFormLogic.hasUnsavedInput("", null, "", "", false, items))
    }
}
