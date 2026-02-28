package com.rton.expanses.ocr

/**
 * Placeholder for local LLM-based parser (<1B parameters).
 *
 * Future implementation could use:
 * - ONNX Runtime Mobile
 * - TensorFlow Lite
 * - MediaPipe LLM Inference API
 *
 * The model would receive OCR text and output structured JSON:
 * { "amount": 1234, "merchant": "7-11", "type": "expense" }
 */
class LlmParser : TransactionParser {
    override val id = "local_llm"
    override val displayName = "本地 AI (LLM)"
    override val isAvailable = false // disabled until model is integrated

    override suspend fun parse(text: String): ParsedTransaction? {
        // TODO: integrate local LLM inference
        // 1. Load ONNX/TFLite model from assets
        // 2. Tokenize input text
        // 3. Run inference
        // 4. Parse output JSON to ParsedTransaction
        return null
    }
}
