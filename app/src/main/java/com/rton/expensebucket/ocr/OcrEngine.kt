package com.rton.expanses.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Unified OCR engine:
 *   Image → ML Kit (Chinese text recognition) → raw text → parser chain → ParsedTransaction
 *
 * Parser chain priority:  RuleBasedParser → LlmParser → ApiParser
 * Falls through to next parser if current returns null.
 */
@Singleton
class OcrEngine @Inject constructor() {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    /** Parser chain — ordered by priority. First non-null result wins. */
    private val parsers: List<TransactionParser> = listOf(
        RuleBasedParser(),
        LlmParser(),
        ApiParser()
    )

    /**
     * Run OCR on an image URI and return parsed transaction data.
     * @return pair of (rawOcrText, parsedResult) — parsedResult may be null if no parser succeeded
     */
    suspend fun processImage(context: Context, imageUri: Uri): OcrResult {
        val rawText = recognizeText(context, imageUri)
        val parsed = parseText(rawText)
        return OcrResult(rawText = rawText, parsed = parsed)
    }

    /**
     * Parse already-extracted text through the parser chain.
     * Useful for notification text that doesn't need OCR.
     */
    suspend fun parseText(text: String): ParsedTransaction? {
        for (parser in parsers) {
            if (!parser.isAvailable) continue
            val result = parser.parse(text)
            if (result != null) return result
        }
        return null
    }

    /**
     * Run ML Kit text recognition on an image.
     */
    private suspend fun recognizeText(context: Context, imageUri: Uri): String {
        val image = InputImage.fromFilePath(context, imageUri)
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }
    }

    /** All registered parsers (for settings UI visibility) */
    fun getAvailableParsers(): List<TransactionParser> = parsers.filter { it.isAvailable }
}

/**
 * Result from the OCR engine containing both raw text and parsed data.
 */
data class OcrResult(
    val rawText: String,
    val parsed: ParsedTransaction?
)
