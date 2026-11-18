package com.rton.expensebucket.nfc

import java.io.IOException
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Reads the public stored-fare services of the Japanese transit IC system. */
internal object TransitFelicaProtocol {
    private const val BALANCE_SERVICE = 0x008b
    private const val HISTORY_SERVICE = 0x090f
    private const val BLOCK_SIZE = 16
    private const val MAX_HISTORY = 20
    private val transitSystem = byteArrayOf(0x00, 0x03)

    fun read(
        detectedSystem: ByteArray?,
        detectedId: ByteArray,
        transceive: (ByteArray) -> ByteArray,
        now: () -> Long = System::currentTimeMillis
    ): TransitCardScan {
        val id = if (detectedSystem?.contentEquals(transitSystem) == true && detectedId.size == 8) {
            detectedId
        } else {
            val polling = transceive(byteArrayOf(6, 0, 0, 3, 0, 0))
            if (polling.size != 18 || polling.u8(0) != polling.size || polling.u8(1) != 1) invalidResponse()
            polling.copyOfRange(2, 10)
        }
        val attributes = readBlock(id, BALANCE_SERVICE, 0, transceive) ?: invalidResponse()
        val balance = attributes.le16(11)
        if (balance !in 0..20_000) invalidResponse()
        val readAt = now()
        val blocks = mutableListOf<ByteArray>()
        var incomplete = false
        for (index in 0 until MAX_HISTORY) {
            val block = try {
                readBlock(id, HISTORY_SERVICE, index, transceive)
            } catch (_: IOException) {
                null
            }
            if (block == null) {
                incomplete = true
                break
            }
            if (block.all { it == 0.toByte() }) break
            blocks += block
        }
        return TransitCardScan(
            fingerprint = MessageDigest.getInstance("SHA-256").digest(id).joinToString("") { "%02x".format(it) },
            readAt = readAt,
            balance = balance,
            history = parseHistory(blocks, readAt),
            historyIncomplete = incomplete
        )
    }

    private fun readBlock(
        id: ByteArray,
        service: Int,
        index: Int,
        transceive: (ByteArray) -> ByteArray
    ): ByteArray? {
        val request = byteArrayOf(16, 0x06) + id +
            byteArrayOf(1, service.toByte(), (service shr 8).toByte(), 1, 0x80.toByte(), index.toByte())
        val response = transceive(request)
        if (response.size < 12 || response.u8(0) != response.size || response.u8(1) != 0x07 ||
            !response.copyOfRange(2, 10).contentEquals(id)
        ) invalidResponse()
        if (response.u8(10) != 0 || response.u8(11) != 0) return null
        if (response.size != 13 + BLOCK_SIZE || response.u8(12) != 1) invalidResponse()
        return response.copyOfRange(13, response.size)
    }

    private fun parseHistory(blocks: List<ByteArray>, readAt: Long): List<TransitCardEntry> {
        val tapDate = Instant.ofEpochMilli(readAt).atZone(ZoneId.of("Asia/Tokyo")).toLocalDate()
        val entries = blocks.map { block ->
            val dateBits = (block.u8(4) shl 8) or block.u8(5)
            val date = try {
                LocalDate.of(2000 + (dateBits shr 9), (dateBits shr 5) and 15, dateBits and 31)
            } catch (_: DateTimeException) {
                null
            }
            val balance = block.le16(10)
            if (date == null || date.isAfter(tapDate) || balance !in 0..20_000) {
                null
            } else {
                val terminal = block.u8(0)
                val proc = block.u8(1) and 0x7f
                val isShopping = terminal in 0xc7..0xc8 || proc in setOf(70, 73, 74, 75)
                val isBus = terminal == 0x05 || proc in setOf(13, 15, 31)
                val time = if (isShopping) parseShoppingTime(block) else null
                val (entryStation, exitStation) = if (!isShopping && !isBus) {
                    val regionByte = block.u8(15)
                    val entryRegion = (regionByte shr 6) and 0x03
                    val exitRegion = (regionByte shr 4) and 0x03
                    TransitStationLookup.find(entryRegion, block.u8(6), block.u8(7)) to
                        TransitStationLookup.find(exitRegion, block.u8(8), block.u8(9))
                } else {
                    null to null
                }
                val busOperator = if (isBus && proc != 31) {
                    val region = (block.u8(15) shr 6) and 0x03
                    TransitBusOperatorLookup.resolve(region, block.u8(6), block.u8(7))
                } else {
                    null
                }
                TransitCardEntry(
                    date = date,
                    sequence = (block.u8(13) shl 8) or block.u8(14),
                    type = when (proc) {
                        1, 3, 4, 5, 6, 8, 13, 15, 19, 35 ->
                            if (terminal in 0xc7..0xc8) TransitCardEntryType.PURCHASE
                            else TransitCardEntryType.TRANSPORT
                        70, 75 -> TransitCardEntryType.PURCHASE
                        2, 20, 21, 31, 72, 73 -> TransitCardEntryType.TOP_UP
                        74 -> TransitCardEntryType.REFUND
                        else -> TransitCardEntryType.OTHER
                    },
                    balanceChange = null,
                    balanceAfter = balance,
                    time = time,
                    entryStation = entryStation,
                    exitStation = exitStation,
                    busOperator = busOperator,
                    detailType = parseDetailType(terminal, proc, isBus)
                )
            }
        }
        // The oldest entry and invalid preceding blocks have no verified balance to subtract.
        // Gate entries increment the card sequence without writing a 0x090f row, so settled
        // history entries commonly advance sequence by more than 1 (e.g. 2 per train ride).
        return entries.mapIndexedNotNull { index, entry ->
            entry ?: return@mapIndexedNotNull null
            val older = entries.getOrNull(index + 1)?.takeIf {
                ((entry.sequence - it.sequence) and 0xffff) in 1..0x7fff
            }
            entry.copy(balanceChange = older?.let { entry.balanceAfter - it.balanceAfter })
        }.distinctBy { it.date to it.sequence }
    }

    private fun parseShoppingTime(block: ByteArray): LocalTime? {
        val timeBits = (block.u8(6) shl 8) or block.u8(7)
        if (timeBits == 0) return null
        val hour = (timeBits shr 11) and 0x1f
        val minute = (timeBits shr 5) and 0x3f
        val second = (timeBits and 0x1f) * 2
        return if (hour in 0..23 && minute in 0..59 && second in 0..59) {
            LocalTime.of(hour, minute, second)
        } else {
            null
        }
    }

    private fun parseDetailType(terminal: Int, proc: Int, isBus: Boolean): TransitCardDetailType? = when {
        terminal == 0xc8 -> TransitCardDetailType.VENDING_MACHINE
        proc == 74 -> TransitCardDetailType.REFUND
        terminal == 0xc7 || proc in setOf(70, 75) -> TransitCardDetailType.RETAIL
        isBus -> if (proc == 31) TransitCardDetailType.CASH_TOP_UP else TransitCardDetailType.BUS
        proc in setOf(20, 21) -> TransitCardDetailType.AUTO_TOP_UP
        proc in setOf(2, 72, 73) -> TransitCardDetailType.CASH_TOP_UP
        proc == 1 -> TransitCardDetailType.GATE_EXIT
        proc in setOf(3, 35) -> TransitCardDetailType.TICKET_PURCHASE
        proc in setOf(4, 5) -> TransitCardDetailType.FARE_ADJUSTMENT
        proc in setOf(6, 8) -> TransitCardDetailType.COUNTER_EXIT
        proc == 19 -> TransitCardDetailType.SHINKANSEN
        else -> null
    }

    private fun invalidResponse(): Nothing = throw IOException("Invalid transit-card response")
    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xff
    private fun ByteArray.le16(index: Int): Int = u8(index) or (u8(index + 1) shl 8)
}
