package com.rton.expanses.ocr

/**
 * Placeholder for cloud API-based parser.
 *
 * Future implementation could use:
 * - OpenAI GPT API
 * - Google Gemini API
 * - Custom backend API
 *
 * The API would receive OCR text and return structured transaction data.
 * API key should be stored in EncryptedSharedPreferences or injected via BuildConfig.
 */
class ApiParser : TransactionParser {
    override val id = "cloud_api"
    override val displayName = "雲端 AI (API)"
    override val isAvailable = false // disabled until API key is configured

    override suspend fun parse(text: String): ParsedTransaction? {
        // TODO: implement cloud API call
        // 1. Build request with OCR text
        // 2. Call API endpoint
        // 3. Parse response JSON to ParsedTransaction
        // 4. Handle errors / rate limits
        return null
    }
}
