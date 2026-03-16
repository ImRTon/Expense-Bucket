package com.rton.expensebucket

import com.rton.expensebucket.ocr.InvoiceQrPayload
import com.rton.expensebucket.ocr.TaiwanInvoiceOcrMetadataParser
import com.rton.expensebucket.ocr.TaiwanInvoiceQrParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Base64

class TaiwanInvoiceQrParserTest {

    @Test
    fun parsesUtf8InvoiceAcrossTwoQrs() {
        val fixed = buildFixedPrefix(
            invoiceNumber = "AB12345678",
            rocDate = "1140316",
            randomCode = "1234",
            salesAmountHex = "0000003C",
            totalAmountHex = "0000003C",
            buyerIdentifier = "00000000",
            sellerIdentifier = "12345678",
            encryptedPayload = "ABCDEFGHIJKLMNOPQRSTUVWX"
        )
        val left = InvoiceQrPayload(
            text = fixed + ":ABCD:2:2:1:可樂:1:30:餅"
        )
        val right = InvoiceQrPayload(
            text = "**乾:2:15"
        )

        val result = TaiwanInvoiceQrParser.parse(left, right)

        assertEquals("AB12345678", result.invoiceNumber)
        assertEquals("2025-03-16", result.invoiceDate.toString())
        assertEquals("1234", result.randomCode)
        assertEquals("60", result.totalAmount.toPlainString())
        assertEquals(2, result.items.size)
        assertEquals("可樂 x1 @ 30", result.items[0].displayText)
        assertEquals("餅乾 x2 @ 15", result.items[1].displayText)
        assertEquals("12345678", result.sellerIdentifier)
    }

    @Test
    fun parsesBase64InvoicePayloadAndSupplement() {
        val fixed = buildFixedPrefix(
            invoiceNumber = "CD87654321",
            rocDate = "1140316",
            randomCode = "5678",
            salesAmountHex = "000000D2",
            totalAmountHex = "000000D2",
            buyerIdentifier = "00000000",
            sellerIdentifier = "87654321",
            encryptedPayload = "ZYXWVUTSRQPONMLKJIHGFEDC"
        )
        val payload = "Latte:1:120:Bagel:2:45:第二件折扣"
        val encodedPayload = Base64.getEncoder().encodeToString(payload.toByteArray(Charsets.UTF_8))
        val splitIndex = encodedPayload.length / 2
        val left = InvoiceQrPayload(
            text = fixed + ":EFGH:2:2:2:" + encodedPayload.substring(0, splitIndex)
        )
        val right = InvoiceQrPayload(
            text = "**" + encodedPayload.substring(splitIndex)
        )

        val result = TaiwanInvoiceQrParser.parse(left, right)

        assertEquals("210", result.totalAmount.toPlainString())
        assertEquals(2, result.items.size)
        assertEquals("Latte x1 @ 120", result.items[0].displayText)
        assertEquals("Bagel x2 @ 45", result.items[1].displayText)
        assertEquals("第二件折扣", result.supplement)
    }

    @Test
    fun parsesOcrMetadataForMerchantAndDateTime() {
        val metadata = TaiwanInvoiceOcrMetadataParser.parse(
            rawText = """
                電子發票證明聯
                全家便利商店
                AB12345678
                2025/03/16 08:45:12
                總計 60
            """.trimIndent(),
            fallbackDate = java.time.LocalDate.of(2025, 3, 16)
        )

        assertEquals("全家便利商店", metadata.merchantName)
        assertNotNull(metadata.invoiceDateTime)
        assertEquals("2025-03-16T08:45:12", metadata.invoiceDateTime.toString())
    }

    @Test
    fun detectsRightPayloadFromRawBytesOrPosition() {
        val rightByRawBytes = InvoiceQrPayload(
            text = "",
            rawBytes = "**RIGHT".toByteArray(),
            centerX = 1200f
        )
        val rightByPosition = InvoiceQrPayload(
            text = "SOMETHING",
            rawBytes = "SOMETHING".toByteArray(),
            centerX = 1200f
        )
        val leftByPosition = InvoiceQrPayload(
            text = "LEFT",
            rawBytes = "LEFT".toByteArray(),
            centerX = 300f
        )

        assertTrue(TaiwanInvoiceQrParser.isLikelyRightPayload(rightByRawBytes, frameCenterX = 540f))
        assertTrue(TaiwanInvoiceQrParser.isLikelyRightPayload(rightByPosition, frameCenterX = 540f))
        assertFalse(TaiwanInvoiceQrParser.isLikelyRightPayload(leftByPosition, frameCenterX = 540f))
        assertTrue(TaiwanInvoiceQrParser.isLikelyLeftPayload(leftByPosition, frameCenterX = 540f))
        assertFalse(TaiwanInvoiceQrParser.isLikelyLeftPayload(rightByRawBytes, frameCenterX = 540f))
    }

    private fun buildFixedPrefix(
        invoiceNumber: String,
        rocDate: String,
        randomCode: String,
        salesAmountHex: String,
        totalAmountHex: String,
        buyerIdentifier: String,
        sellerIdentifier: String,
        encryptedPayload: String
    ): String {
        return invoiceNumber +
            rocDate +
            randomCode +
            salesAmountHex +
            totalAmountHex +
            buyerIdentifier +
            sellerIdentifier +
            encryptedPayload
    }
}
