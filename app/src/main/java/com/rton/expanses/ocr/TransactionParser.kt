package com.rton.expanses.ocr

/**
 * Parsed result from OCR or notification text.
 */
data class ParsedTransaction(
    val amount: Double,
    val merchant: String = "",       // 商家名稱
    val note: String = "",           // 備註 / 原始文字摘要
    val isExpense: Boolean = true,
    val date: Long? = null,          // null = 使用當前時間
    val paymentMethodHint: String? = null, // e.g. "linepay", "jko" — match PaymentMethod.icon key
    val confidence: Float = 0f       // 0..1  解析信心度
)

/**
 * Strategy interface for parsing text into a transaction.
 * Implementations: RuleBasedParser, LlmParser, ApiParser
 */
interface TransactionParser {
    /** Unique identifier for this parser */
    val id: String

    /** Human-readable name */
    val displayName: String

    /** Whether this parser is currently available (has model/API key/etc.) */
    val isAvailable: Boolean

    /**
     * Attempt to parse the raw text into a ParsedTransaction.
     * Returns null if the parser cannot extract meaningful data.
     */
    suspend fun parse(text: String): ParsedTransaction?
}
