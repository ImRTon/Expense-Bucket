package com.rton.expanses.ocr

import java.util.regex.Pattern

/**
 * Parses notification text from banking/payment apps into transaction data.
 *
 * Supported apps (by package name patterns in ExpansesNotificationService):
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
        val isIncome: Boolean = false
    )

    private val patterns = listOf(
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

                return ParsedTransaction(
                    amount = amount,
                    merchant = merchant,
                    note = text.take(100),
                    isExpense = !p.isIncome,
                    paymentMethodHint = hint,
                    confidence = if (merchant.isNotBlank()) 0.9f else 0.6f
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
            "apple" in packageName -> "applepay"
            "google" in packageName && "pay" in packageName -> "googlepay"
            else -> null
        }
    }
}
