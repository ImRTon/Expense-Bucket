package com.rton.expensebucket

import com.rton.expensebucket.nfc.TransitCardDetailType
import com.rton.expensebucket.nfc.TransitCardEntryType
import com.rton.expensebucket.nfc.TransitFelicaProtocol
import java.io.IOException
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class TransitFelicaProtocolTest {
    private val id = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
    private val system = byteArrayOf(0, 3)
    private val readAt = Instant.parse("2026-11-18T04:00:00Z").toEpochMilli()

    @Test
    fun read_decodesIndependentBalanceAndOnlyImportableDebits() {
        val blocks = listOf(
            historyBlock(0x46, sequence = 4, balance = 470),
            historyBlock(0x02, sequence = 3, balance = 600),
            historyBlock(0x01, sequence = 2, balance = 100),
            historyBlock(0x01, sequence = 1, balance = 280)
        )
        val scan = TransitFelicaProtocol.read(system, id, { command ->
            assertEquals(16, command[0].toInt())
            assertEquals(6, command[1].toInt())
            assertArrayEquals(id, command.copyOfRange(2, 10))
            when (command[11].toInt() and 0xff) {
                0x8b -> response(ByteArray(16).apply {
                    this[10] = 0x7f // The balance service uses a different offset from the history.
                    setLittleEndian(11, 470)
                })
                0x0f -> response(blocks.getOrElse(command.last().toInt()) { ByteArray(16) })
                else -> error("Unexpected service")
            }
        }, { readAt })

        assertEquals(470, scan.balance)
        assertEquals(64, scan.fingerprint.length)
        assertEquals(listOf(130, null, 180, null), scan.history.map { it.expenseAmount })
        assertEquals(500, scan.history[1].balanceChange)
        assertEquals(TransitCardEntryType.TOP_UP, scan.history[1].type)
        assertFalse(scan.historyIncomplete)
    }

    @Test
    fun read_pollsTheTransitSystemAndKeepsBalanceWhenHistoryIsInterrupted() {
        val scan = TransitFelicaProtocol.read(byteArrayOf(0xfe.toByte(), 0), id, { command ->
            if (command[1].toInt() == 0) {
                assertArrayEquals(byteArrayOf(6, 0, 0, 3, 0, 0), command)
                byteArrayOf(18, 1) + id + ByteArray(8)
            } else if ((command[11].toInt() and 0xff) == 0x8b) {
                response(ByteArray(16).apply { setLittleEndian(11, 150) })
            } else {
                throw IOException("Tag removed")
            }
        }, { readAt })

        assertEquals(150, scan.balance)
        assertTrue(scan.history.isEmpty())
        assertTrue(scan.historyIncomplete)
    }

    @Test
    fun read_computesAmountsAcrossSequenceGapsFromGateEntries() {
        val scan = TransitFelicaProtocol.read(system, id, { command ->
            if ((command[11].toInt() and 0xff) == 0x8b) {
                response(ByteArray(16).apply { setLittleEndian(11, 100) })
            } else {
                response(when (command.last().toInt()) {
                    0 -> historyBlock(0x01, sequence = 5, balance = 100)
                    1 -> historyBlock(0x01, sequence = 3, balance = 500)
                    else -> ByteArray(16)
                })
            }
        }, { readAt })

        assertEquals(listOf(400, null), scan.history.map { it.expenseAmount })
    }

    @Test
    fun read_leavesAmountUnknownWhenPrecedingBlockIsInvalidOrOutOfOrder() {
        val scan = TransitFelicaProtocol.read(system, id, { command ->
            if ((command[11].toInt() and 0xff) == 0x8b) {
                response(ByteArray(16).apply { setLittleEndian(11, 100) })
            } else {
                response(when (command.last().toInt()) {
                    0 -> historyBlock(0x01, sequence = 5, balance = 100)
                    1 -> historyBlock(0x01, sequence = 6, balance = 500)
                    2 -> historyBlock(0x01, sequence = 4, balance = 700).apply {
                        this[4] = 0
                        this[5] = 0 // Invalid date (month 0, day 0) drops this block.
                    }
                    else -> ByteArray(16)
                })
            }
        }, { readAt })

        assertEquals(listOf(null, null), scan.history.map { it.expenseAmount })
    }

    @Test(expected = IOException::class)
    fun read_rejectsMismatchedCardResponse() {
        TransitFelicaProtocol.read(system, id, {
            response(ByteArray(16)).apply { this[2] = 0x34 }
        }, { readAt })
    }

    @Test
    fun read_decodesStationsShoppingTimeAndBusDetails() {
        val kansaiRide = historyBlock(type = 0x01, sequence = 10, balance = 1000).apply {
            this[6] = 0x81.toByte()
            this[7] = 0x1c // 梅田 (region 2)
            this[8] = 0x81.toByte()
            this[9] = 0x21 // 本町 (region 2)
            this[15] = ((2 shl 6) or (2 shl 4)).toByte()
        }
        val vending = historyBlock(type = 0x46, sequence = 9, balance = 1230).apply {
            this[0] = 0xc8.toByte()
            val time = (14 shl 11) or (32 shl 5) or 10 // 14:32:20
            this[6] = (time shr 8).toByte()
            this[7] = time.toByte()
        }
        val bus = historyBlock(type = 13, sequence = 8, balance = 1380).apply {
            this[0] = 0x05
            this[6] = 0x0e
            this[7] = 0x09
            this[8] = 0x00
            this[9] = 0x3a
            this[15] = (2 shl 6).toByte()
        }
        val unmappedBus = historyBlock(type = 15, sequence = 7, balance = 1610).apply {
            this[0] = 0x05
            this[6] = 0x01
            this[7] = 0x01
        }
        val blocks = listOf(kansaiRide, vending, bus, unmappedBus)
        val scan = TransitFelicaProtocol.read(system, id, { command ->
            when (command[11].toInt() and 0xff) {
                0x8b -> response(ByteArray(16).apply { setLittleEndian(11, 1000) })
                0x0f -> response(blocks.getOrElse(command.last().toInt()) { ByteArray(16) })
                else -> error("Unexpected service")
            }
        }, { readAt })

        assertEquals("梅田", scan.history[0].entryStation?.name)
        assertEquals("本町", scan.history[0].exitStation?.name)
        assertEquals(TransitCardDetailType.GATE_EXIT, scan.history[0].detailType)

        assertEquals(LocalTime.of(14, 32, 20), scan.history[1].time)
        assertNull(scan.history[1].entryStation)
        assertEquals(TransitCardDetailType.VENDING_MACHINE, scan.history[1].detailType)

        assertNull(scan.history[2].entryStation)
        assertNull(scan.history[2].exitStation)
        assertEquals("京都市バス", scan.history[2].busOperator)
        assertEquals(TransitCardDetailType.BUS, scan.history[2].detailType)

        assertEquals("0101", scan.history[3].busOperator)
        assertEquals(TransitCardDetailType.BUS, scan.history[3].detailType)
    }

    private fun response(block: ByteArray): ByteArray = byteArrayOf(29, 7) + id + byteArrayOf(0, 0, 1) + block

    private fun historyBlock(type: Int, sequence: Int, balance: Int): ByteArray = ByteArray(16).apply {
        this[0] = 0x16
        this[1] = type.toByte()
        val date = (26 shl 9) or (11 shl 5) or 18
        this[4] = (date shr 8).toByte()
        this[5] = date.toByte()
        setLittleEndian(10, balance)
        this[13] = (sequence shr 8).toByte()
        this[14] = sequence.toByte()
    }

    private fun ByteArray.setLittleEndian(index: Int, value: Int) {
        this[index] = value.toByte()
        this[index + 1] = (value shr 8).toByte()
    }
}
