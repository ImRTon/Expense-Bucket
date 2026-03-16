package com.rton.expensebucket.ocr

import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64

data class InvoiceQrPayload(
    val text: String,
    val rawBytes: ByteArray? = null,
    val centerX: Float? = null
)

data class TaiwanInvoiceItem(
    val name: String,
    val quantityText: String,
    val unitPriceText: String
) {
    val displayText: String
        get() = buildString {
            append(name)
            if (quantityText.isNotBlank()) append(" x").append(quantityText)
            if (unitPriceText.isNotBlank()) append(" @ ").append(unitPriceText)
        }
}

data class TaiwanInvoiceOcrMetadata(
    val merchantName: String? = null,
    val invoiceDateTime: LocalDateTime? = null
)

data class TaiwanInvoiceQrResult(
    val invoiceNumber: String,
    val invoiceDate: LocalDate,
    val randomCode: String,
    val salesAmount: BigDecimal,
    val totalAmount: BigDecimal,
    val buyerIdentifier: String,
    val sellerIdentifier: String,
    val encryptedPayload: String,
    val sellerCustomArea: String,
    val encodedItemCount: Int,
    val totalItemCount: Int,
    val encoding: String,
    val items: List<TaiwanInvoiceItem>,
    val supplement: String?,
    val rawLeftText: String,
    val rawRightText: String,
    val metadata: TaiwanInvoiceOcrMetadata? = null
) {
    val merchantLabel: String
        get() = metadata?.merchantName?.takeUnless { it.isBlank() } ?: "賣方統編 $sellerIdentifier"

    val resolvedDateTime: LocalDateTime
        get() = metadata?.invoiceDateTime ?: invoiceDate.atStartOfDay()

    val resolvedDateMillis: Long
        get() = resolvedDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    val noteText: String
        get() = buildString {
            append("發票 ").append(invoiceNumber)
            append('\n').append(merchantLabel)
            append('\n').append("隨機碼 ").append(randomCode)
            append('\n').append("總額 ").append(totalAmount.stripTrailingZeros().toPlainString())
            if (items.isNotEmpty()) {
                append('\n').append('\n').append("品項")
                items.forEach { item ->
                    append('\n').append("- ").append(item.displayText)
                }
            }
            supplement?.takeIf { it.isNotBlank() }?.let {
                append('\n').append('\n').append("補充資訊")
                append('\n').append(it)
            }
        }
}

object TaiwanInvoiceQrParser {
    private const val FIXED_PREFIX_LENGTH = 77
    private val big5Charset: Charset = Charset.forName("Big5")

    fun findPair(payloads: List<InvoiceQrPayload>): Pair<InvoiceQrPayload, InvoiceQrPayload>? {
        val candidates = payloads.filter { it.text.isNotBlank() || it.rawBytes?.isNotEmpty() == true }
        if (candidates.size < 2) return null

        val right = candidates.firstOrNull { hasRightPrefix(it) }
        val left = candidates.firstOrNull { it !== right && !hasRightPrefix(it) }
        if (left != null && right != null) return left to right

        val sorted = candidates.sortedBy { it.centerX ?: Float.MAX_VALUE }
        return sorted.getOrNull(0)?.let { first ->
            sorted.getOrNull(1)?.let { second -> first to second }
        }
    }

    fun isLikelyLeftPayload(payload: InvoiceQrPayload, frameCenterX: Float? = null): Boolean {
        if (hasRightPrefix(payload)) return false
        val centerX = payload.centerX
        return when {
            centerX == null || frameCenterX == null -> payload.text.isNotBlank() || payload.rawBytes?.isNotEmpty() == true
            else -> centerX <= frameCenterX
        }
    }

    fun isLikelyRightPayload(payload: InvoiceQrPayload, frameCenterX: Float? = null): Boolean {
        if (hasRightPrefix(payload)) return true
        val centerX = payload.centerX
        return when {
            centerX == null || frameCenterX == null -> false
            else -> centerX > frameCenterX
        }
    }

    fun parse(left: InvoiceQrPayload, right: InvoiceQrPayload): TaiwanInvoiceQrResult {
        val leftBytes = left.rawBytes ?: left.text.toByteArray(Charsets.UTF_8)
        val rightBytes = right.rawBytes ?: right.text.toByteArray(Charsets.UTF_8)
        val combinedBytes = combineQrPayload(leftBytes, rightBytes)
        require(combinedBytes.size >= FIXED_PREFIX_LENGTH) { "發票 QR 內容不足，無法解析" }

        val fixed = combinedBytes.copyOfRange(0, FIXED_PREFIX_LENGTH).toString(Charsets.US_ASCII)
        val extensionBytes = combinedBytes.copyOfRange(FIXED_PREFIX_LENGTH, combinedBytes.size)
        val extension = parseExtension(extensionBytes)

        val itemsPayload = decodeItemsPayload(
            rawPayload = extension.remainingPayload,
            encoding = extension.encoding
        )
        val itemTokens = itemsPayload
            .split(':')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val parsedItems = parseItems(itemTokens, extension.encodedItemCount)
        val supplement = itemTokens
            .drop(parsedItems.size * 3)
            .joinToString(":")
            .trim()
            .ifBlank { null }

        return TaiwanInvoiceQrResult(
            invoiceNumber = fixed.substring(0, 10),
            invoiceDate = parseRocDate(fixed.substring(10, 17)),
            randomCode = fixed.substring(17, 21),
            salesAmount = parseHexAmount(fixed.substring(21, 29)),
            totalAmount = parseHexAmount(fixed.substring(29, 37)),
            buyerIdentifier = fixed.substring(37, 45),
            sellerIdentifier = fixed.substring(45, 53),
            encryptedPayload = fixed.substring(53, 77),
            sellerCustomArea = extension.sellerCustomArea,
            encodedItemCount = extension.encodedItemCount,
            totalItemCount = extension.totalItemCount,
            encoding = extension.encoding,
            items = parsedItems,
            supplement = supplement,
            rawLeftText = left.text,
            rawRightText = right.text
        )
    }

    private fun combineQrPayload(leftBytes: ByteArray, rightBytes: ByteArray): ByteArray {
        val actualLeft = if (leftBytes.startsWithDoubleAsterisk()) rightBytes else leftBytes
        val actualRight = if (leftBytes.startsWithDoubleAsterisk()) leftBytes else rightBytes
        val rightContent = if (actualRight.startsWithDoubleAsterisk()) {
            actualRight.copyOfRange(2, actualRight.size)
        } else {
            actualRight
        }
        return actualLeft + rightContent
    }

    private fun ByteArray.startsWithDoubleAsterisk(): Boolean {
        return size >= 2 && this[0] == '*'.code.toByte() && this[1] == '*'.code.toByte()
    }

    private fun hasRightPrefix(payload: InvoiceQrPayload): Boolean {
        return payload.text.startsWith("**") || payload.rawBytes?.startsWithDoubleAsterisk() == true
    }

    private fun parseRocDate(raw: String): LocalDate {
        val rocYear = raw.substring(0, 3).toInt()
        val month = raw.substring(3, 5).toInt()
        val day = raw.substring(5, 7).toInt()
        return LocalDate.of(rocYear + 1911, month, day)
    }

    private fun parseHexAmount(raw: String): BigDecimal {
        return raw.toLong(16).toBigDecimal()
    }

    private data class ExtensionParseResult(
        val sellerCustomArea: String,
        val encodedItemCount: Int,
        val totalItemCount: Int,
        val encoding: String,
        val remainingPayload: ByteArray
    )

    private fun parseExtension(bytes: ByteArray): ExtensionParseResult {
        var index = 0

        fun readField(): String {
            require(index < bytes.size && bytes[index] == ':'.code.toByte()) { "發票 QR 延伸欄位格式不正確" }
            index += 1
            val start = index
            while (index < bytes.size && bytes[index] != ':'.code.toByte()) {
                index += 1
            }
            return bytes.copyOfRange(start, index).toString(Charsets.US_ASCII)
        }

        val sellerCustomArea = readField()
        val encodedItemCount = readField().toIntOrNull() ?: 0
        val totalItemCount = readField().toIntOrNull() ?: encodedItemCount
        val encoding = readField().ifBlank { "0" }
        val remainingPayload = if (index < bytes.size) {
            bytes.copyOfRange(index + 1, bytes.size)
        } else {
            ByteArray(0)
        }

        return ExtensionParseResult(
            sellerCustomArea = sellerCustomArea,
            encodedItemCount = encodedItemCount,
            totalItemCount = totalItemCount,
            encoding = encoding,
            remainingPayload = remainingPayload
        )
    }

    private fun decodeItemsPayload(rawPayload: ByteArray, encoding: String): String {
        if (rawPayload.isEmpty()) return ""
        return when (encoding) {
            "0" -> rawPayload.toString(big5Charset)
            "1" -> rawPayload.toString(Charsets.UTF_8)
            "2" -> decodeBase64Payload(rawPayload)
            else -> rawPayload.toString(Charsets.UTF_8)
        }
    }

    private fun decodeBase64Payload(rawPayload: ByteArray): String {
        val base64Text = rawPayload.toString(Charsets.US_ASCII).trim()
        val decoded = Base64.getDecoder().decode(base64Text)
        val utf8 = decoded.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            decoded.toString(big5Charset)
        } else {
            utf8
        }
    }

    private fun parseItems(tokens: List<String>, encodedItemCount: Int): List<TaiwanInvoiceItem> {
        if (tokens.isEmpty() || encodedItemCount <= 0) return emptyList()

        val maxTokenCount = minOf(tokens.size, encodedItemCount * 3)
        val itemTokens = tokens.take(maxTokenCount)
        return itemTokens.chunked(3)
            .mapNotNull { chunk ->
                if (chunk.size < 3) return@mapNotNull null
                TaiwanInvoiceItem(
                    name = chunk[0],
                    quantityText = chunk[1],
                    unitPriceText = chunk[2]
                )
            }
    }
}

object TaiwanInvoiceOcrMetadataParser {
    private val invoiceNumberRegex = Regex("""^[A-Z]{2}\d{8}$""")
    private val gregorianDateTimeRegex = Regex(
        """(20\d{2})[/-](\d{1,2})[/-](\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )
    private val rocDateTimeRegex = Regex(
        """(\d{3})[./-](\d{1,2})[./-](\d{1,2})\s+(\d{1,2}):(\d{2})(?::(\d{2}))?"""
    )
    private val merchantNoiseKeywords = listOf(
        "電子發票", "證明聯", "統一發票", "隨機碼", "總計", "合計", "課稅", "未稅",
        "買方", "賣方", "營業人", "交易明細", "列印", "發票", "金額", "時間", "日期"
    )

    fun parse(rawText: String, fallbackDate: LocalDate): TaiwanInvoiceOcrMetadata {
        val normalizedLines = rawText.lines()
            .map { it.replace("\\s+".toRegex(), " ").trim() }
            .filter { it.isNotBlank() }

        val merchantName = normalizedLines
            .take(8)
            .firstOrNull { isMerchantCandidate(it) }

        val invoiceDateTime = parseDateTime(rawText) ?: parseDateTimeFromLines(normalizedLines, fallbackDate)

        return TaiwanInvoiceOcrMetadata(
            merchantName = merchantName,
            invoiceDateTime = invoiceDateTime
        )
    }

    private fun parseDateTime(rawText: String): LocalDateTime? {
        gregorianDateTimeRegex.find(rawText)?.let { match ->
            return buildDateTime(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
                hour = match.groupValues[4].toInt(),
                minute = match.groupValues[5].toInt(),
                second = match.groupValues[6].ifBlank { "0" }.toInt()
            )
        }

        rocDateTimeRegex.find(rawText)?.let { match ->
            return buildDateTime(
                year = match.groupValues[1].toInt() + 1911,
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
                hour = match.groupValues[4].toInt(),
                minute = match.groupValues[5].toInt(),
                second = match.groupValues[6].ifBlank { "0" }.toInt()
            )
        }

        return null
    }

    private fun parseDateTimeFromLines(lines: List<String>, fallbackDate: LocalDate): LocalDateTime? {
        val timeRegex = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""")
        return lines.firstNotNullOfOrNull { line ->
            val match = timeRegex.find(line) ?: return@firstNotNullOfOrNull null
            buildDateTime(
                year = fallbackDate.year,
                month = fallbackDate.monthValue,
                day = fallbackDate.dayOfMonth,
                hour = match.groupValues[1].toInt(),
                minute = match.groupValues[2].toInt(),
                second = match.groupValues[3].ifBlank { "0" }.toInt()
            )
        }
    }

    private fun buildDateTime(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int
    ): LocalDateTime? {
        return runCatching {
            LocalDateTime.of(
                LocalDate.of(year, month, day),
                LocalTime.of(hour, minute, second)
            )
        }.getOrNull()
    }

    private fun isMerchantCandidate(line: String): Boolean {
        if (line.length !in 2..32) return false
        if (invoiceNumberRegex.matches(line)) return false
        if (line.all { it.isDigit() || it == '-' || it == ':' || it == '/' }) return false
        if (merchantNoiseKeywords.any { it in line }) return false
        return line.any { it.isLetter() || it.code in 0x4E00..0x9FFF }
    }
}
