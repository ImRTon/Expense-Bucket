package com.rton.expensebucket

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.repository.TransitCardImportRepository
import com.rton.expensebucket.data.repository.TransitCardImportStatus
import com.rton.expensebucket.nfc.TransitCardEntry
import com.rton.expensebucket.nfc.TransitCardEntryType
import com.rton.expensebucket.nfc.TransitCardScan
import com.rton.expensebucket.util.StoredValueCalculator
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TransitCardImportTest {
    @Test
    fun save_reconcilesAndImportsOnceWhileLeavingDepositsOutOfTheLedger() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, ExpenseBucketDatabase::class.java).build()
        try {
            val methods = database.paymentMethodDao()
            val methodId = methods.insertPaymentMethod(PaymentMethod(name = "Suica", icon = "suica"))
            val date = LocalDate.of(2026, 11, 18)
            val at = date.atTime(15, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val purchase = TransitCardEntry(date, 15, TransitCardEntryType.PURCHASE, -130, 470)
            val topUp = TransitCardEntry(date, 14, TransitCardEntryType.TOP_UP, 500, 600)
            val scan = TransitCardScan("a".repeat(64), at, 470, listOf(purchase, topUp), false)
            val repo = TransitCardImportRepository(database)
            val selected = mapOf(scan.reference(purchase) to "Suica purchase", scan.reference(topUp) to "Top up")

            assertTrue(repo.preview(methodId, scan)!!.imported.isEmpty())
            assertEquals(1, repo.save(methodId, scan, selected, 0.21).importedCount)
            assertEquals(0, repo.save(methodId, scan, selected, 0.21).importedCount)
            assertEquals(setOf(scan.reference(purchase)), repo.preview(methodId, scan)!!.imported)
            assertNull(repo.preview(methodId, scan.copy(fingerprint = "b".repeat(64))))

            val card = methods.getPaymentMethodById(methodId)!!
            val records = database.transactionDao().getAllConfirmedTransactions().first()
            assertEquals(130.0, records.single().amount, 0.0)
            assertEquals(0.21, records.single().exchangeRate, 0.0)
            assertEquals(470.0, StoredValueCalculator.currentBalance(card, records)!!, 0.0)
            val manual = Transaction(amount = 60.0, paymentMethodId = methodId, currency = "JPY", date = at + 1)
            assertEquals(410.0, StoredValueCalculator.currentBalance(card, records + manual)!!, 0.0)
            assertEquals(470.0, StoredValueCalculator.currentBalance(card, listOf(records.single().copy(date = at + 1)))!!, 0.0)

            methods.setStoredValueBalance(methodId, 700.0, at + 1, "JPY", false)
            assertEquals(TransitCardImportStatus.STALE_SCAN, repo.save(methodId, scan, emptyMap(), null).status)
            assertEquals(700.0, methods.getPaymentMethodById(methodId)!!.storedValueBalance!!.amount, 0.0)
        } finally {
            database.close()
        }
    }
}
