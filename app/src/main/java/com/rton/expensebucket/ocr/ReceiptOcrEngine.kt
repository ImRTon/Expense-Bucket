package com.rton.expensebucket.ocr

import android.graphics.Bitmap
import android.content.Context
import android.net.Uri
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class ReceiptOcrEngine @Inject constructor() {

    private val chineseRecognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )
    private val japaneseRecognizer = TextRecognition.getClient(
        JapaneseTextRecognizerOptions.Builder().build()
    )
    private val latinRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )
    private val languageIdentifier = LanguageIdentification.getClient()

    suspend fun processReceipt(context: Context, imageUri: Uri): ReceiptOcrResult = coroutineScope {
        processImage(
            image = InputImage.fromFilePath(context, imageUri),
            includeJapaneseRecognizer = true
        )
    }

    suspend fun processBitmap(
        bitmap: Bitmap,
        includeJapaneseRecognizer: Boolean = true
    ): ReceiptOcrResult = coroutineScope {
        processImage(
            image = InputImage.fromBitmap(bitmap, 0),
            includeJapaneseRecognizer = includeJapaneseRecognizer
        )
    }

    suspend fun translateToLanguage(
        text: String,
        sourceLanguageTag: String?,
        targetLanguageTag: String
    ): String? {
        if (text.isBlank() || sourceLanguageTag.isNullOrBlank()) return null

        val normalizedSource = sourceLanguageTag.lowercase()
        val normalizedTarget = targetLanguageTag.lowercase()
        if (normalizedSource.startsWith(normalizedTarget.substringBefore('-'))) return null
        if (normalizedSource.startsWith("zh") && normalizedTarget.startsWith("zh")) return null

        val sourceLanguage = TranslateLanguage.fromLanguageTag(sourceLanguageTag) ?: return null
        val targetLanguage = TranslateLanguage.fromLanguageTag(targetLanguageTag)
            ?: TranslateLanguage.CHINESE

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()
        )

        return try {
            val modelReady = suspendCancellableCoroutine<Boolean> { continuation ->
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener { continuation.resume(true) }
                    .addOnFailureListener { continuation.resume(false) }
            }
            if (!modelReady) return null

            translatePreservingLines(translator, text)
        } finally {
            translator.close()
        }
    }

    fun applyTranslation(
        rawText: String,
        detectedLanguageTag: String?,
        translatedText: String?
    ): ReceiptOcrResult {
        return buildResult(
            rawText = rawText,
            detectedLanguageTag = detectedLanguageTag,
            translatedText = translatedText
        )
    }

    private fun buildResult(
        rawText: String,
        detectedLanguageTag: String?,
        translatedText: String?
    ): ReceiptOcrResult {
        val analysisText = translatedText ?: rawText
        val parsedReceipt = ReceiptTextParser.parse(
            sourceText = analysisText,
            fallbackAmountText = rawText,
            fallbackLineItemText = if (translatedText != null) analysisText else rawText
        )

        return ReceiptOcrResult(
            rawText = rawText,
            detectedLanguageTag = detectedLanguageTag,
            translatedText = translatedText,
            displayText = analysisText,
            totalAmount = parsedReceipt.totalAmount,
            lineItems = parsedReceipt.lineItems,
            note = parsedReceipt.note,
            wasTranslated = translatedText != null
        )
    }

    private suspend fun recognizeText(
        recognizer: TextRecognizer,
        image: InputImage
    ): String {
        return suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(visionText.text)
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }
    }

    private suspend fun translatePreservingLines(
        translator: com.google.mlkit.nl.translate.Translator,
        text: String
    ): String? {
        val lines = text.split('\n')
        if (lines.isEmpty()) return null

        val translatedLines = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) {
                translatedLines += ""
                continue
            }

            val translatedLine = suspendCancellableCoroutine<String?> { continuation ->
                translator.translate(line)
                    .addOnSuccessListener { translated -> continuation.resume(translated) }
                    .addOnFailureListener { continuation.resume(null) }
            } ?: line

            translatedLines += translatedLine
        }

        return translatedLines.joinToString("\n")
    }

    private fun selectBestText(vararg candidates: RecognizedTextCandidate): String {
        return candidates
            .maxByOrNull { candidate -> scoreCandidate(candidate) }
            ?.text
            ?.trim()
            .orEmpty()
    }

    private fun scoreCandidate(candidate: RecognizedTextCandidate): Int {
        val text = candidate.text.trim()
        if (text.isEmpty()) return Int.MIN_VALUE

        val visibleChars = text.count { !it.isWhitespace() }
        val digits = text.count { it.isDigit() }
        val cjkChars = text.count { it.code in 0x4E00..0x9FFF }
        val japaneseKana = text.count { it.code in 0x3040..0x30FF }

        val recognizerBonus = when (candidate.hint) {
            RecognizerHint.CHINESE -> cjkChars * 2
            RecognizerHint.JAPANESE -> (cjkChars * 2) + (japaneseKana * 4)
            RecognizerHint.LATIN -> if (cjkChars == 0 && japaneseKana == 0) 24 else 0
        }

        return visibleChars + (digits / 2) + recognizerBonus
    }

    private suspend fun identifyLanguage(text: String): String? {
        if (text.isBlank()) return null
        return suspendCancellableCoroutine { continuation ->
            languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    continuation.resume(languageCode.takeUnless { it == "und" })
                }
                .addOnFailureListener {
                    continuation.resume(null)
            }
        }
    }

    private suspend fun processImage(
        image: InputImage,
        includeJapaneseRecognizer: Boolean
    ): ReceiptOcrResult = coroutineScope {
        val chineseTextDeferred = async { recognizeText(chineseRecognizer, image) }
        val latinTextDeferred = async { recognizeText(latinRecognizer, image) }
        val japaneseTextDeferred = if (includeJapaneseRecognizer) {
            async { recognizeText(japaneseRecognizer, image) }
        } else {
            null
        }

        val chineseText = chineseTextDeferred.await()
        val latinText = latinTextDeferred.await()
        val candidates = buildList {
            add(RecognizedTextCandidate(chineseText, RecognizerHint.CHINESE))
            japaneseTextDeferred?.await()?.let { add(RecognizedTextCandidate(it, RecognizerHint.JAPANESE)) }
            add(RecognizedTextCandidate(latinText, RecognizerHint.LATIN))
        }
        val rawText = selectBestText(
            *candidates.toTypedArray()
        )
        val detectedLanguage = identifyLanguage(rawText)
        buildResult(
            rawText = rawText,
            detectedLanguageTag = detectedLanguage,
            translatedText = null
        )
    }
}

private data class RecognizedTextCandidate(
    val text: String,
    val hint: RecognizerHint
)

private enum class RecognizerHint {
    CHINESE,
    JAPANESE,
    LATIN
}

data class ReceiptOcrResult(
    val rawText: String,
    val detectedLanguageTag: String?,
    val translatedText: String?,
    val displayText: String,
    val totalAmount: Double?,
    val lineItems: List<String>,
    val note: String,
    val wasTranslated: Boolean
)

private data class ParsedReceiptDetails(
    val totalAmount: Double?,
    val lineItems: List<String>,
    val note: String
)

private object ReceiptTextParser {
    private val totalKeywords = listOf(
        "總計", "合計", "合計金額", "總額", "應付", "實付",
        "お会計", "お支払", "ご請求額", "領収金額", "現計", "税込",
        "total", "amount due", "grand total", "subtotal"
    )
    private val amountRegex = Regex("""(\d[\d,]*(?:\.\d{1,2})?)""")
    private val metadataKeywords = listOf(
        "發票", "統編", "電話", "地址", "交易", "卡號", "信用卡", "簽帳", "付款", "找零", "折扣", "稅",
        "領収書", "レシート", "電話", "住所", "取引", "カード", "クレジット", "税率", "小計",
        "内税", "外税", "値引", "返品", "担当", "登録番号", "日時",
        "invoice", "tax", "change", "payment", "card", "receipt", "tel", "phone", "store"
    )

    fun parse(
        sourceText: String,
        fallbackAmountText: String,
        fallbackLineItemText: String
    ): ParsedReceiptDetails {
        val normalizedLines = normalizeLines(sourceText)
        val fallbackAmountLines = normalizeLines(fallbackAmountText)
        val fallbackLineItemLines = normalizeLines(fallbackLineItemText)
        val totalAmount = extractTotal(normalizedLines) ?: extractTotal(fallbackAmountLines)
        val lineItems = extractLineItems(normalizedLines, totalAmount).ifEmpty {
            extractLineItems(fallbackLineItemLines, totalAmount)
        }
        val note = lineItems.joinToString("\n").ifBlank {
            normalizedLines.take(6).joinToString("\n").ifBlank {
                fallbackLineItemLines.take(6).joinToString("\n")
            }
        }

        return ParsedReceiptDetails(
            totalAmount = totalAmount,
            lineItems = lineItems,
            note = note
        )
    }

    private fun normalizeLines(text: String): List<String> {
        return text.lines()
            .map { it.replace("\\s+".toRegex(), " ").trim() }
            .filter { it.isNotBlank() }
    }

    private fun extractTotal(lines: List<String>): Double? {
        val keywordMatches = lines.mapIndexedNotNull { index, line ->
            val lowerLine = line.lowercase()
            if (totalKeywords.none { it in lowerLine }) return@mapIndexedNotNull null
            amountRegex.findAll(line)
                .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
                .filter { it in 1.0..1_000_000.0 }
                .maxOrNull()
                ?.let { Triple(index, line, it) }
        }

        if (keywordMatches.isNotEmpty()) {
            return keywordMatches.maxByOrNull { it.first }?.third
        }

        return lines.takeLast(8)
            .flatMap { line ->
                amountRegex.findAll(line)
                    .mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }
                    .filter { it in 1.0..1_000_000.0 }
                    .toList()
            }
            .maxOrNull()
    }

    private fun extractLineItems(lines: List<String>, totalAmount: Double?): List<String> {
        val totalText = totalAmount?.let { "%.2f".format(it) }
        return lines
            .filter { line ->
                val lowerLine = line.lowercase()
                metadataKeywords.none { it in lowerLine } &&
                    totalKeywords.none { it in lowerLine } &&
                    line.any { char -> char.isLetter() || char.code in 0x4E00..0x9FFF } &&
                    line.length in 2..48
            }
            .filterNot { line ->
                totalText != null && line.contains(totalText.removeSuffix(".00"))
            }
            .take(8)
    }
}
