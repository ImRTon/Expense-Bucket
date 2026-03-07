package com.rton.expensebucket.ocr

import java.util.regex.Pattern

/**
 * Rule-based transaction parser using regex patterns.
 * Supports common Taiwanese receipt, credit card, and e-payment formats.
 *
 * To add new patterns, append to the `extractAmount()` or `extractMerchant()` functions.
 */
class RuleBasedParser : TransactionParser {
    override val id = "rule_based"
    override val displayName = "規則比對"
    override val isAvailable = true

    override suspend fun parse(text: String): ParsedTransaction? {
        val amount = extractAmount(text) ?: return null
        val merchant = extractMerchant(text)
        val paymentHint = detectPaymentMethod(text)
        val isIncome = detectIncome(text)

        return ParsedTransaction(
            amount = amount,
            merchant = merchant,
            note = text.take(100), // keep first 100 chars as reference
            isExpense = !isIncome,
            paymentMethodHint = paymentHint,
            confidence = if (merchant.isNotBlank()) 0.8f else 0.5f
        )
    }

    // ─── Amount extraction ──────────────────────────────────────

    private fun extractAmount(text: String): Double? {
        // Ordered by specificity — most specific patterns first
        val patterns = listOf(
            // NT$1,234 or NT$ 1,234 or NT$1234.56
            Pattern.compile("""NT\$\s*([\d,]+(?:\.\d{1,2})?)""", Pattern.CASE_INSENSITIVE),
            // $1,234 or $ 1,234 (TWD context)
            Pattern.compile("""\$\s*([\d,]+(?:\.\d{1,2})?)"""),
            // 金額 1,234 or 金額：1234
            Pattern.compile("""金額[：:\s]*(\d[\d,]*(?:\.\d{1,2})?)"""),
            // 消費 1,234 元
            Pattern.compile("""消費[：:\s]*(\d[\d,]*(?:\.\d{1,2})?)"""),
            // 扣款 1,234 元
            Pattern.compile("""扣款[：:\s]*(\d[\d,]*(?:\.\d{1,2})?)"""),
            // 支付 1,234 元
            Pattern.compile("""支付[：:\s]*(\d[\d,]*(?:\.\d{1,2})?)"""),
            // 刷卡 1,234 元
            Pattern.compile("""刷卡[：:\s]*(\d[\d,]*(?:\.\d{1,2})?)"""),
            // 1,234 元
            Pattern.compile("""(\d[\d,]*(?:\.\d{1,2})?)\s*元"""),
            // 總計 or 合計 1,234
            Pattern.compile("""[總合]計[：:\s]*(\d[\d,]*(?:\.\d{1,2})?)"""),
            // 應付 1,234
            Pattern.compile("""應付[：:\s]*(\d[\d,]*(?:\.\d{1,2})?)"""),
            // Fallback: any number >= 10 that looks like money
            Pattern.compile("""(?:^|\s)(\d[\d,]*(?:\.\d{1,2})?)(?:\s|$)""")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val raw = matcher.group(1)?.replace(",", "") ?: continue
                val value = raw.toDoubleOrNull() ?: continue
                if (value >= 1.0 && value < 10_000_000) {
                    return value
                }
            }
        }
        return null
    }

    // ─── Merchant extraction ────────────────────────────────────

    private fun extractMerchant(text: String): String {
        val patterns = listOf(
            // 於 XXX 消費 / 在 XXX 消費
            Pattern.compile("""[於在]\s*(.{2,20}?)\s*(?:消費|刷卡|支付|扣款)"""),
            // 商店 XXX or 特約商店 XXX
            Pattern.compile("""(?:特約)?商店[：:\s]*(.{2,20})"""),
            // 商家 XXX
            Pattern.compile("""商家[：:\s]*(.{2,20})"""),
            // 交易對象
            Pattern.compile("""交易對象[：:\s]*(.{2,20})""")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim() ?: ""
            }
        }
        return ""
    }

    // ─── Payment method detection ───────────────────────────────

    private fun detectPaymentMethod(text: String): String? {
        val lowered = text.lowercase()
        return when {
            "line pay" in lowered || "linepay" in lowered -> "linepay"
            "街口" in lowered || "jkopay" in lowered -> "jko"
            "悠遊付" in lowered || "easycard" in lowered -> "easycard"
            "apple pay" in lowered || "applepay" in lowered -> "applepay"
            "google pay" in lowered || "googlepay" in lowered -> "googlepay"
            "pi 拍錢包" in lowered || "pi wallet" in lowered -> "pi"
            "全支付" in lowered -> "pay_full"
            "richart" in lowered || "台新" in lowered -> "richart"
            "玉山" in lowered || "esun" in lowered -> "esun"
            "國泰" in lowered || "cathay" in lowered -> "cathay"
            "信用卡" in lowered || "刷卡" in lowered -> "credit_card"
            else -> null
        }
    }

    // ─── Income detection ───────────────────────────────────────

    private fun detectIncome(text: String): Boolean {
        val incomeKeywords = listOf("入帳", "轉入", "收到", "薪資", "退款", "退費", "匯入")
        return incomeKeywords.any { it in text }
    }
}
