package com.rton.expensebucket.data.repository

import androidx.room.withTransaction
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.HistoricalCashRateProvider
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.nfc.TransitCardEntryType
import com.rton.expensebucket.nfc.TransitCardScan
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class TransitCardImportPreview(
    val imported: Set<String>,
    val possibleDuplicates: Set<String>
)

enum class TransitCardImportStatus { SAVED, RATE_REQUIRED, WRONG_CARD, STALE_SCAN }

data class TransitCardImportResult(
    val status: TransitCardImportStatus,
    val importedCount: Int = 0
)

class TransitCardImportRepository @Inject constructor(private val database: ExpenseBucketDatabase) {
    private val methods = database.paymentMethodDao()
    private val transactions = database.transactionDao()

    suspend fun preview(methodId: Long, scan: TransitCardScan): TransitCardImportPreview? {
        if (!matchesCard(methodId, scan)) return null
        val expenses = scan.history.filter { it.expenseAmount != null }
        if (expenses.isEmpty()) return TransitCardImportPreview(emptySet(), emptySet())
        val zone = ZoneId.systemDefault()
        val imported = transactions.getImportedNfcReferences(expenses.map(scan::reference)).toSet()
        val candidates = transactions.getNfcImportCandidates(
            methodId, expenses.minOf { it.accountingDate(zone, scan.readAt) }
        ).filter { it.nfcReference == null }
        val possibleDuplicates = expenses.filter { entry ->
            candidates.any {
                it.amount == entry.expenseAmount?.toDouble() &&
                    Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() == entry.date
            }
        }.map(scan::reference).toSet()
        return TransitCardImportPreview(imported, possibleDuplicates)
    }

    suspend fun save(
        methodId: Long,
        scan: TransitCardScan,
        selectedNotes: Map<String, String>,
        manualRate: Double?
    ): TransitCardImportResult {
        if (!matchesCard(methodId, scan)) return TransitCardImportResult(TransitCardImportStatus.WRONG_CARD)
        val entries = scan.history.filter { it.expenseAmount != null && scan.reference(it) in selectedNotes }
        val imported = transactions.getImportedNfcReferences(entries.map(scan::reference)).toSet()
        val pending = entries.filter { scan.reference(it) !in imported }
        val zone = ZoneId.systemDefault()
        val ratesByDate = mutableMapOf<Long, Double>()
        for (entry in pending) {
            val date = entry.accountingDate(zone, scan.readAt)
            if (date in ratesByDate) continue
            val rate = manualRate ?: HistoricalCashRateProvider.getExchangeRate("JPY", "TWD", date).getOrNull()
            currentCoroutineContext().ensureActive()
            if (rate == null || !rate.isFinite() || rate <= 0) {
                return TransitCardImportResult(TransitCardImportStatus.RATE_REQUIRED)
            }
            ratesByDate[date] = rate
        }

        return database.withTransaction {
            if (!matchesCard(methodId, scan)) {
                return@withTransaction TransitCardImportResult(TransitCardImportStatus.WRONG_CARD)
            }
            val anchor = methods.getPaymentMethodById(methodId)?.storedValueBalance
            if (anchor != null && anchor.asOf > scan.readAt) {
                return@withTransaction TransitCardImportResult(TransitCardImportStatus.STALE_SCAN)
            }
            val categories = database.categoryDao()
            val transport = categories.getCategoryByNameAndType("交通", true)?.id
            val purchase = categories.getCategoryByNameAndType("購物", true)?.id
            val records = pending.map { entry ->
                val reference = scan.reference(entry)
                val date = entry.accountingDate(zone, scan.readAt)
                Transaction(
                    amount = requireNotNull(entry.expenseAmount).toDouble(),
                    note = selectedNotes.getValue(reference),
                    categoryId = if (entry.type == TransitCardEntryType.TRANSPORT) transport else purchase,
                    paymentMethodId = methodId,
                    date = date,
                    currency = "JPY",
                    exchangeRate = ratesByDate.getValue(date),
                    source = "nfc",
                    nfcReference = reference
                )
            }
            val importedCount = if (records.isEmpty()) 0 else transactions.insertNfcTransactions(records).count { it != -1L }
            methods.setNfcCardBalance(methodId, scan.balance.toDouble(), scan.readAt, scan.fingerprint)
            TransitCardImportResult(TransitCardImportStatus.SAVED, importedCount)
        }
    }

    private suspend fun matchesCard(methodId: Long, scan: TransitCardScan): Boolean {
        val method = methods.getPaymentMethodById(methodId) ?: return false
        if (method.icon !in setOf("suica", "icoca")) return false
        if (method.nfcCardFingerprint != null && method.nfcCardFingerprint != scan.fingerprint) return false
        val owner = methods.findNfcCardOwner(scan.fingerprint)
        return owner == null || owner == method.id
    }
}
