package com.rton.expensebucket.ocr

import java.util.Calendar
import java.util.regex.Pattern

/**
 * Parses notification text from banking/payment apps into transaction data.
 *
 * Supported apps (by package name patterns in ExpenseBucketNotificationService):
 * - LINE Pay, 街口支付, 悠遊付, Apple Pay/Google Pay
 * - 台新 Richart, 玉山, 國泰世華, 中國信託, 富邦 etc.
 */
class NotificationParser {

    private val twdCurrencyToken = """(?:NTD|TWD|NT\$?|N\.?T\.?\$?|(?:新)?[台臺]幣)"""
    private val genericAmountCurrencyToken = """(?:$twdCurrencyToken|\$|＄)"""
    private val amountNumber = """[\d,]+(?:\.\d+)?"""
    private val transactionKeywordRegex = Regex("""(刷卡|消費|付款|支付|扣款|交易|入帳|轉入|收到|退款|請款|授權)""")
    private val incomeKeywordRegex = Regex("""(入帳|轉入|收到|退款)""")
    private val balanceNoiseRegex = Regex("""(餘額|可用額度|帳單|最低應繳|紅利|點數|優惠|驗證碼)""")

    /**
     * Known notification patterns.
     * Each entry: regex pattern → group indices for (amount, merchant).
     */
    private data class NotifPattern(
        val regex: Pattern,
        val amountGroup: Int = 1,
        val merchantGroup: Int = 2,
        val paymentHint: String? = null,
        val isIncome: Boolean = false,
        val dateGroup: Int = -1
    )

    private val patterns = listOf(
        // 台新 Richart：「【信用卡消費通知】您的Richart卡(末三碼xxxx)於01/30-18:06網路刷卡約新台幣3,019元，實際消費xxxx」
        // 或是「【信用卡消費通知】您的Richart卡(末三碼xxxx)於01/30-18:06刷卡約新台幣3,019元，實際消費xxxx」
        NotifPattern(
            regex = Pattern.compile("""Richart卡.*?[於在]\s*(\d{2}/\d{2}-\d{2}:\d{2})\s*(?:網路)?刷卡約\s*$twdCurrencyToken\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 2,
            merchantGroup = -1,
            paymentHint = "richart",
            dateGroup = 1
        ),
        // 街口支付：「您已於 7-ELEVEN 付款 NT$89」
        NotifPattern(
            regex = Pattern.compile("""[於在]\s*(.{2,20}?)\s*(?:付款|消費|支付)\s*NT?\$?\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 2,
            merchantGroup = 1,
            paymentHint = "jko"
        ),
        // LINE Pay：「LINE Pay消費通知 在 全聯 支付 $356」
        NotifPattern(
            regex = Pattern.compile("""(?:LINE\s*Pay).*[在於]\s*(.{2,20}?)\s*(?:支付|消費)\s*\$?\s*([\d,]+(?:\.\d+)?)""", Pattern.CASE_INSENSITIVE),
            amountGroup = 2,
            merchantGroup = 1,
            paymentHint = "linepay"
        ),
        // 銀行簡訊格式：「台新銀行通知：刷卡消費NT$1,234 商店：7-ELEVEN」
        NotifPattern(
            regex = Pattern.compile("""刷卡消費\s*$twdCurrencyToken\s*([\d,]+(?:\.\d+)?)\s*.*?(?:商店|特約商店)[：:\s]*(.{2,20})"""),
            amountGroup = 1,
            merchantGroup = 2,
            paymentHint = "credit_card"
        ),
        // 國泰世華：「【刷卡通知】金額NT$1340元卡號末四碼xxxx於2026/03/08 20:52在商店名稱AAAA刷卡。立即以點數xxxxxx」
        NotifPattern(
            regex = Pattern.compile("""金額\s*$twdCurrencyToken\s*([\d,]+(?:\.\d+)?)\s*元.*?[於在]\s*(\d{4}/\d{1,2}/\d{1,2}\s+\d{1,2}:\d{1,2})\s*[於在]\s*(?:商店名稱)?\s*(.+?)\s*刷卡"""),
            amountGroup = 1,
            merchantGroup = 3,
            paymentHint = "cathay",
            dateGroup = 2
        ),
        // 悠遊付：「悠遊付交易通知 消費金額 $50」
        NotifPattern(
            regex = Pattern.compile("""悠遊付.*消費金額\s*\$?\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 1,
            merchantGroup = -1,
            paymentHint = "easycard"
        ),
        // Apple Pay / Google Pay：「Apple Pay: 在 全聯 以 NT$289 消費」
        NotifPattern(
            regex = Pattern.compile("""(?:Apple|Google)\s*Pay.*[在於]\s*(.{2,20}?).*NT?\$?\s*([\d,]+(?:\.\d+)?)""", Pattern.CASE_INSENSITIVE),
            amountGroup = 2,
            merchantGroup = 1,
            paymentHint = "applepay" // will be overridden by package name
        ),
        // 富邦：「【刷卡消費通知】您的信用卡末四碼xxxx於02/23 12:37:26商店名稱消費臺幣1,234元」
        NotifPattern(
            regex = Pattern.compile("""信用卡.*?[於在]\s*(\d{2}[/-]\d{2}\s+\d{1,2}:\d{1,2}(?::\d{1,2})?)\s*(.*?)\s*消費\s*$twdCurrencyToken\s*([\d,]+(?:\.\d+)?)\s*元"""),
            amountGroup = 3,
            merchantGroup = 2,
            paymentHint = "credit_card",
            dateGroup = 1
        ),
        // 永豐大咖/大戶：「永豐貴賓您好，末四碼xxxx感謝03/15 12:05刷卡台幣123元，商店名稱:STORE_NAME，實際商店名稱請以xxxxxx」
        NotifPattern(
            regex = Pattern.compile("""感謝\s*(\d{1,2}/\d{1,2}\s+\d{1,2}:\d{1,2})\s*刷卡\s*$twdCurrencyToken\s*([\d,]+(?:\.\d+)?)\s*元.*?(?:商店名稱|商店)[：:\s]*([^，,]+)"""),
            amountGroup = 2,
            merchantGroup = 3,
            paymentHint = "sinopac",
            dateGroup = 1
        ),
        // 永豐信用卡新版：「永豐信用卡末四碼xxxx刷卡通知1150321_17:59金額日圓JPY$1,181.00，約當台幣$ 240元，商店名稱:XXXX，實際商店名稱......」
        NotifPattern(
            regex = Pattern.compile("""永豐信用卡末四碼\d{4}刷卡通知\s*((?:\d{3,4})(?:\d{2}){2}_\d{1,2}:\d{1,2}).*?約當\s*$twdCurrencyToken\s*\$?\s*([\d,]+(?:\.\d+)?)\s*元.*?商店名稱[：:\s]*([^，,\n]+)"""),
            amountGroup = 2,
            merchantGroup = 3,
            paymentHint = "sinopac",
            dateGroup = 1
        ),
        // 郵局：「您的郵政VISA金融卡於114/01/13 21:22:05消費新台幣8,521元(國外交易blablabla)，如有疑慮blablabla」
        NotifPattern(
            regex = Pattern.compile("""郵政.*?於\s*((?:\d{3,4})[/-]\d{1,2}[/-]\d{1,2}\s+\d{1,2}:\d{1,2}(?::\d{1,2})?)\s*消費\s*$twdCurrencyToken\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 2,
            merchantGroup = -1,
            paymentHint = "post",
            dateGroup = 1
        ),
        // 信用卡通知：「消費通知 卡號末四碼1234 於 星巴克 消費 NT$165」
        NotifPattern(
            regex = Pattern.compile("""[於在]\s*(.{2,20}?)\s*消費\s*$twdCurrencyToken\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 2,
            merchantGroup = 1,
            paymentHint = "credit_card"
        ),
        // 通用格式：「消費 NT$1,234」 or 「扣款 $567」
        NotifPattern(
            regex = Pattern.compile("""(?:消費|扣款|支付|付款)\s*$genericAmountCurrencyToken\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 1,
            merchantGroup = -1
        ),
        // 入帳通知
        NotifPattern(
            regex = Pattern.compile("""(?:入帳|轉入|收到|退款)\s*NT?\$?\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 1,
            merchantGroup = -1,
            isIncome = true
        )
    )

    /**
     * Parse notification text into structured transaction data.
     * @param text The notification title + text combined
     * @param packageName Source app package name (for payment method hinting)
     */
    fun parse(text: String, packageName: String? = null): ParsedTransaction? {
        val normalizedText = normalizeText(text)
        var bestMatch: ParsedTransaction? = null

        for (p in patterns) {
            val matcher = p.regex.matcher(normalizedText)
            if (matcher.find()) {
                val amountStr = matcher.group(p.amountGroup)?.replace(",", "") ?: continue
                val amount = amountStr.toDoubleOrNull() ?: continue
                if (amount < 1.0 || amount > 10_000_000) continue

                val merchant = if (p.merchantGroup > 0) {
                    matcher.group(p.merchantGroup)?.trim() ?: ""
                } else ""

                val hint = detectPaymentHintFromPackage(packageName) ?: p.paymentHint

                val paymentMethodHint = if (hint == "post") "credit_card" else hint

                val dateStr = if (p.dateGroup > 0) matcher.group(p.dateGroup) else null
                val parsedDate = parseNotificationDate(dateStr)

                val candidate = ParsedTransaction(
                    amount = amount,
                    merchant = merchant,
                    note = normalizedText.take(100),
                    isExpense = !p.isIncome,
                    date = parsedDate,
                    paymentMethodHint = paymentMethodHint,
                    confidence = if (merchant.isNotBlank() || parsedDate != null) 0.9f else 0.6f
                )
                
                if (bestMatch == null || candidate.confidence > bestMatch!!.confidence) {
                    bestMatch = candidate
                }
            }
        }

        parseByFields(normalizedText, packageName)?.let { candidate ->
            if (bestMatch == null || candidate.confidence > bestMatch!!.confidence) {
                bestMatch = candidate
            }
        }
        return bestMatch
    }

    /**
     * A semantic fallback for notification copy drift.
     *
     * Bank/e-wallet apps regularly tweak sentences, but the useful fields stay stable:
     * amount, date/time, merchant, and payment source. This parser extracts those fields
     * independently instead of depending on one whole-sentence regex per vendor.
     */
    private fun parseByFields(text: String, packageName: String?): ParsedTransaction? {
        if (!looksLikeTransactionNotification(text, packageName)) return null

        val amount = extractBestAmount(text) ?: return null
        if (amount < 1.0 || amount > 10_000_000) return null

        val parsedDate = parseNotificationDate(extractDateText(text))
        val merchant = extractMerchant(text)
        val hint = detectPaymentHintFromPackage(packageName)
        val isIncome = incomeKeywordRegex.containsMatchIn(text) && !Regex("""(刷卡|消費|付款|支付|扣款)""").containsMatchIn(text)

        var confidence = 0.62f
        if (merchant.isNotBlank()) confidence += 0.12f
        if (parsedDate != null) confidence += 0.10f
        if (hint != null) confidence += 0.04f
        if (Regex("""($genericAmountCurrencyToken|元)""").containsMatchIn(text)) confidence += 0.04f

        return ParsedTransaction(
            amount = amount,
            merchant = merchant,
            note = text.take(100),
            isExpense = !isIncome,
            date = parsedDate,
            paymentMethodHint = if (hint == "post") "credit_card" else hint,
            confidence = confidence.coerceAtMost(0.86f)
        )
    }

    private data class AmountCandidate(
        val amount: Double,
        val score: Int,
        val index: Int
    )

    private fun extractBestAmount(text: String): Double? {
        val candidates = mutableListOf<AmountCandidate>()

        fun addMatches(regex: Regex, score: Int) {
            regex.findAll(text).forEach { match ->
                val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return@forEach
                val contextStart = (match.range.first - 16).coerceAtLeast(0)
                val contextEnd = (match.range.last + 17).coerceAtMost(text.length)
                val context = text.substring(contextStart, contextEnd)
                val adjustedScore = score +
                    (if (transactionKeywordRegex.containsMatchIn(context)) 12 else 0) -
                    (if (balanceNoiseRegex.containsMatchIn(context)) 35 else 0)
                candidates += AmountCandidate(amount, adjustedScore, match.range.first)
            }
        }

        addMatches(Regex("""約當\s*$twdCurrencyToken\s*\$?\s*($amountNumber)\s*元?"""), 120)
        addMatches(Regex("""(?:金額|消費金額|交易金額|請款金額|實付|刷卡|消費|扣款|支付|付款)\s*(?:約)?\s*(?:$genericAmountCurrencyToken\s*)?($amountNumber)\s*元?"""), 95)
        addMatches(Regex("""$genericAmountCurrencyToken\s*($amountNumber)\s*元?"""), 85)
        addMatches(Regex("""($amountNumber)\s*元"""), 65)

        return candidates
            .filter { it.amount in 1.0..10_000_000.0 }
            .maxWithOrNull(compareBy<AmountCandidate> { it.score }.thenByDescending { -it.index })
            ?.amount
    }

    private fun extractDateText(text: String): String? {
        val datePatterns = listOf(
            Regex("""\d{3,4}\d{2}\d{2}_\d{1,2}:\d{1,2}"""),
            Regex("""(?:\d{3,4}[/-])?\d{1,2}[/-]\d{1,2}[\s-]\d{1,2}:\d{1,2}(?::\d{1,2})?""")
        )
        return datePatterns.firstNotNullOfOrNull { it.find(text)?.value }
    }

    private fun extractMerchant(text: String): String {
        val dateThenMerchant =
            """(?:\d{1,4}[/-])?\d{1,2}[/-]\d{1,2}[\s-]\d{1,2}:\d{1,2}(?::\d{1,2})?"""
        val merchantPatterns = listOf(
            Regex("""(?:商店名稱|特約商店|商店|店家)[：:\s]*([^,。；;\n]{2,40})"""),
            Regex("""(?:實際消費|實際商店名稱)[：:\s]*([^,。；;\n]{2,40})"""),
            Regex("""[於在]\s*([^,。；;\n]{2,40}?)\s*(?:付款|支付|消費|刷卡)"""),
            Regex("""$dateThenMerchant\s*([^,。；;\n]{2,40}?)\s*(?:消費|刷卡|$genericAmountCurrencyToken|$amountNumber\s*元)""")
        )

        return merchantPatterns
            .asSequence()
            .mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
            .map(::cleanMerchant)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun cleanMerchant(raw: String): String {
        val cleaned = raw
            .trim()
            .trim(',', '，', '.', '。', ':', '：', ' ')
            .replace(Regex("""^(商店名稱|特約商店|商店|店家|名稱)[：:\s]*"""), "")
            .replace(Regex("""(刷卡|消費|付款|支付|扣款|立即.*|如有.*|請以.*)$"""), "")
            .trim()

        if (cleaned.length < 2) return ""
        if (Regex("""^\d{1,4}[/-]\d{1,2}.*""").matches(cleaned)) return ""
        if (Regex("""^(末四碼|卡號|金額|台幣|臺幣|新台幣|新臺幣|NT|TWD)""").containsMatchIn(cleaned)) return ""
        return cleaned.take(40)
    }

    private fun looksLikeTransactionNotification(text: String, packageName: String?): Boolean {
        val hasTransactionKeyword = transactionKeywordRegex.containsMatchIn(text)
        val hasAmountSignal = Regex("""($genericAmountCurrencyToken|元)""").containsMatchIn(text)
        val hasKnownPackage = detectPaymentHintFromPackage(packageName) != null
        val hasStrongNoise = balanceNoiseRegex.containsMatchIn(text) && !hasTransactionKeyword

        return !hasStrongNoise && hasTransactionKeyword && (hasAmountSignal || hasKnownPackage)
    }

    private fun normalizeText(text: String): String =
        text
            .replace('\u00A0', ' ')
            .replace('　', ' ')
            .replace('＄', '$')
            .replace('：', ':')
            .replace('，', ',')
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Map known package names to payment method icon keys.
     */
    private fun detectPaymentHintFromPackage(packageName: String?): String? {
        if (packageName == null) return null
        val normalizedPackage = packageName.lowercase()
        return when {
            "jkopay" in normalizedPackage || "jko" in normalizedPackage -> "jko"
            "linepay" in normalizedPackage || "line" in normalizedPackage -> "linepay"
            "easycard" in normalizedPackage || "easygo" in normalizedPackage -> "easycard"
            "taishin" in normalizedPackage || "richart" in normalizedPackage -> "richart"
            "esunbank" in normalizedPackage -> "esun"
            "cathaybk" in normalizedPackage -> "cathay"
            "ctbcbank" in normalizedPackage -> "credit_card"
            "fubon" in normalizedPackage -> "credit_card"
            "sinopac" in normalizedPackage -> "sinopac"
            "post" in normalizedPackage -> "post"
            "apple" in normalizedPackage -> "applepay"
            "google" in normalizedPackage && "pay" in normalizedPackage -> "googlepay"
            else -> null
        }
    }

    /**
     * Parse date string like "01/30-18:06" to a Long timestamp in milliseconds.
     */
    private fun parseNotificationDate(dateStr: String?): Long? {
        if (dateStr == null) return null
        return try {
            val separatedRegex =
                """(?:(\d{3,4})[/-])?(\d{1,2})[/-](\d{1,2})[\s-](\d{1,2}):(\d{1,2})(?::\d{1,2})?""".toRegex()
            val compactRegex =
                """(\d{3,4})(\d{2})(\d{2})_(\d{1,2}):(\d{1,2})""".toRegex()

            val match = separatedRegex.find(dateStr)
            val (yearStr, month, day, hour, minute) = when {
                match != null -> match.destructured
                else -> {
                    val compactMatch = compactRegex.find(dateStr) ?: return null
                    compactMatch.destructured
                }
            }

            val calendar = Calendar.getInstance()
            if (yearStr.isNotBlank()) {
                if (yearStr.length == 3) {
                    calendar.set(Calendar.YEAR, yearStr.toInt() + 1911)
                } else {
                    calendar.set(Calendar.YEAR, yearStr.toInt())
                }
            }
            calendar.set(Calendar.MONTH, month.toInt() - 1) // 0-based month
            calendar.set(Calendar.DAY_OF_MONTH, day.toInt())
            calendar.set(Calendar.HOUR_OF_DAY, hour.toInt())
            calendar.set(Calendar.MINUTE, minute.toInt())
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            // Make sure the parsed time doesn't significantly exceed current time 
            // e.g. crossing a year boundary from December to January
            val now = Calendar.getInstance()
            if (yearStr.isBlank() && calendar.timeInMillis > now.timeInMillis + 7 * 24 * 3600 * 1000L) {
                calendar.add(Calendar.YEAR, -1)
            }

            calendar.timeInMillis
        } catch (e: Exception) {
            null
        }
    }
}
