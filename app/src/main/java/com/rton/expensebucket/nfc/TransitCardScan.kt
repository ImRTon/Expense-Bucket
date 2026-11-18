package com.rton.expensebucket.nfc

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TransitCardScan(
    val fingerprint: String,
    val readAt: Long,
    val balance: Int,
    val history: List<TransitCardEntry>,
    val historyIncomplete: Boolean
) {
    fun reference(entry: TransitCardEntry): String =
        "$fingerprint:${entry.date}:${entry.sequence}"
}

data class TransitCardEntry(
    val date: LocalDate,
    val sequence: Int,
    val type: TransitCardEntryType,
    val balanceChange: Int?,
    val balanceAfter: Int,
    val time: LocalTime? = null,
    val entryStation: TransitStation? = null,
    val exitStation: TransitStation? = null,
    val busOperator: String? = null,
    val detailType: TransitCardDetailType? = null
) {
    val expenseAmount: Int?
        get() = balanceChange?.takeIf {
            it < 0 && (type == TransitCardEntryType.TRANSPORT || type == TransitCardEntryType.PURCHASE)
        }?.let { -it }

    fun accountingDate(zone: ZoneId, readAt: Long): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli().coerceAtMost(readAt)
}

enum class TransitCardEntryType { TRANSPORT, PURCHASE, TOP_UP, REFUND, OTHER }

enum class TransitCardDetailType {
    GATE_EXIT,
    TICKET_PURCHASE,
    FARE_ADJUSTMENT,
    COUNTER_EXIT,
    SHINKANSEN,
    BUS,
    VENDING_MACHINE,
    RETAIL,
    AUTO_TOP_UP,
    CASH_TOP_UP,
    REFUND
}
