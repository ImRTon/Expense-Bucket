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
            regex = Pattern.compile("""Richart卡.*?[於在]\s*(\d{2}/\d{2}-\d{2}:\d{2})\s*(?:網路)?刷卡約新台幣\s*([\d,]+(?:\.\d+)?)"""),
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
        // 信用卡通知：「消費通知 卡號末四碼1234 於 星巴克 消費 NT$165」
        NotifPattern(
            regex = Pattern.compile("""[於在]\s*(.{2,20}?)\s*消費\s*NT?\$?\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 2,
            merchantGroup = 1,
            paymentHint = "credit_card"
        ),
        // 銀行簡訊格式：「台新銀行通知：刷卡消費NT$1,234 商店：7-ELEVEN」
        NotifPattern(
            regex = Pattern.compile("""刷卡消費\s*NT?\$?\s*([\d,]+(?:\.\d+)?)\s*.*?(?:商店|特約商店)[：:\s]*(.{2,20})"""),
            amountGroup = 1,
            merchantGroup = 2,
            paymentHint = "credit_card"
        ),
        // 國泰世華：「【刷卡通知】金額NT$1340元卡號末四碼xxxx於2026/03/08 20:52在商店名稱AAAA刷卡。立即以點數xxxxxx」
        NotifPattern(
            regex = Pattern.compile("""金額\s*NT?\$?\s*([\d,]+(?:\.\d+)?)\s*元.*?[於在]\s*(\d{4}/\d{1,2}/\d{1,2}\s+\d{1,2}:\d{1,2})\s*[於在]\s*(.+?)\s*刷卡"""),
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
        // 富邦：「【刷卡消費通知】您的信用卡末四碼xxxx於02/23 12:37:26商店名稱消費台幣1,234元」
        NotifPattern(
            regex = Pattern.compile("""信用卡.*?[於在]\s*(\d{2}[/-]\d{2}\s+\d{1,2}:\d{1,2}(?::\d{1,2})?)\s*(.*?)\s*消費台幣\s*([\d,]+(?:\.\d+)?)\s*元"""),
            amountGroup = 3,
            merchantGroup = 2,
            paymentHint = "credit_card",
            dateGroup = 1
        ),
        // 永豐大咖/大戶：「xxxx感謝03/06 20:29刷卡台幣539元，商店名稱:悠遊付，實際xxxxx」
        NotifPattern(
            regex = Pattern.compile("""刷卡台幣\s*([\d,]+(?:\.\d+)?)\s*元.*?(?:商店名稱|商店)[：:\s]*([^，,]+)"""),
            amountGroup = 1,
            merchantGroup = 2,
            paymentHint = "sinopac"
        ),
        // 郵局：「您的郵政VISA金融卡於114/01/13 21:22:05消費新台幣8,521元(國外交易blablabla)，如有疑慮blablabla」
        NotifPattern(
            regex = Pattern.compile("""郵政.*?於\s*((?:\d{3,4})[/-]\d{1,2}[/-]\d{1,2}\s+\d{1,2}:\d{1,2}(?::\d{1,2})?)\s*消費新台幣\s*([\d,]+(?:\.\d+)?)"""),
            amountGroup = 2,
            merchantGroup = -1,
            paymentHint = "post",
            dateGroup = 1
        ),
        // 通用格式：「消費 NT$1,234」 or 「扣款 $567」
        NotifPattern(
            regex = Pattern.compile("""(?:消費|扣款|支付|付款)\s*NT?\$?\s*([\d,]+(?:\.\d+)?)"""),
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
        for (p in patterns) {
            val matcher = p.regex.matcher(text)
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

                return ParsedTransaction(
                    amount = amount,
                    merchant = merchant,
                    note = text.take(100),
                    isExpense = !p.isIncome,
                    date = parsedDate,
                    paymentMethodHint = paymentMethodHint,
                    confidence = if (merchant.isNotBlank() || parsedDate != null) 0.9f else 0.6f
                )
            }
        }
        return null
    }

    /**
     * Map known package names to payment method icon keys.
     */
    private fun detectPaymentHintFromPackage(packageName: String?): String? {
        if (packageName == null) return null
        return when {
            "jkopay" in packageName || "jko" in packageName -> "jko"
            "linepay" in packageName || "line" in packageName -> "linepay"
            "easycard" in packageName || "easygo" in packageName -> "easycard"
            "taishin" in packageName || "richart" in packageName -> "richart"
            "esunbank" in packageName -> "esun"
            "cathaybk" in packageName -> "cathay"
            "ctbcbank" in packageName -> "credit_card"
            "fubon" in packageName -> "credit_card"
            "sinopac" in packageName -> "sinopac"
            "post" in packageName -> "post"
            "apple" in packageName -> "applepay"
            "google" in packageName && "pay" in packageName -> "googlepay"
            else -> null
        }
    }

    /**
     * Parse date string like "01/30-18:06" to a Long timestamp in milliseconds.
     */
    private fun parseNotificationDate(dateStr: String?): Long? {
        if (dateStr == null) return null
        return try {
            val regex = """(?:(\d{3,4})[/-])?(\d{1,2})[/-](\d{1,2})[\s-](\d{1,2}):(\d{1,2})(?::\d{1,2})?""".toRegex()
            val match = regex.find(dateStr) ?: return null

            val (yearStr, month, day, hour, minute) = match.destructured

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
