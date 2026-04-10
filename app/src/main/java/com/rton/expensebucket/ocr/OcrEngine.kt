package com.rton.expensebucket.ocr

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/**
 * Unified OCR engine:
 *   Image -> ML Kit (multi-recognizer OCR) -> merged blocks/lines -> parser chain -> ParsedTransaction
 *
 * Screenshot bookkeeping now prefers block-level OCR candidates and uses ML Kit entity extraction
 * as an additional money signal before finalizing the amount.
 */
@Singleton
class OcrEngine @Inject constructor() {

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
    private val entityExtractor: EntityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
    )
    private val entityModelMutex = Mutex()
    @Volatile
    private var entityModelReady = false

    /** Parser chain — ordered by priority. First non-null result wins. */
    private val parsers: List<TransactionParser> = listOf(
        RuleBasedParser(),
        LlmParser(),
        ApiParser()
    )

    suspend fun processImage(context: Context, imageUri: Uri): OcrResult = coroutineScope {
        val image = InputImage.fromFilePath(context, imageUri)
        val document = recognizeDocument(image)
        val amountCandidates = extractAmountCandidates(document)
        val parsed = mergeAmountCandidate(
            parseText(document.rawText),
            amountCandidates,
            document.rawText
        )
        OcrResult(
            rawText = document.rawText,
            parsed = parsed,
            document = document,
            amountCandidates = amountCandidates
        )
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

    /** All registered parsers (for settings UI visibility) */
    fun getAvailableParsers(): List<TransactionParser> = parsers.filter { it.isAvailable }

    private suspend fun recognizeDocument(image: InputImage): OcrTextDocument = coroutineScope {
        val chineseDeferred = async { recognizeText(chineseRecognizer, image, ScreenshotRecognizerHint.CHINESE) }
        val latinDeferred = async { recognizeText(latinRecognizer, image, ScreenshotRecognizerHint.LATIN) }
        val japaneseDeferred = async { recognizeText(japaneseRecognizer, image, ScreenshotRecognizerHint.JAPANESE) }

        val recognized = listOf(chineseDeferred, latinDeferred, japaneseDeferred).awaitAll()
        val mergedBlocks = mergeBlocks(recognized.flatMap { result ->
            result.textBlocks.map { block -> block.toCandidate(result.hint) }
        })

        val orderedBlocks = mergedBlocks
            .sortedWith(compareBy<OcrTextBlock>({ it.top }, { it.left }))
            .map { block ->
                async {
                    block.copy(
                        languageTag = identifyLanguage(block.text)
                    )
                }
            }
            .awaitAll()
        val fallbackText = recognized
            .map { candidate -> candidate.fullText to scoreRecognizerText(candidate.fullText, candidate.hint) }
            .maxByOrNull { it.second }
            ?.first
            ?.trim()
            .orEmpty()
        val rawText = orderedBlocks.joinToString("\n") { it.text }.ifBlank { fallbackText }
        val detectedLanguageTag = identifyLanguage(rawText)

        OcrTextDocument(
            rawText = rawText,
            detectedLanguageTag = detectedLanguageTag,
            blocks = orderedBlocks
        )
    }

    private suspend fun recognizeText(
        recognizer: TextRecognizer,
        image: InputImage,
        hint: ScreenshotRecognizerHint
    ): RecognizedDocument = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(
                    RecognizedDocument(
                        hint = hint,
                        fullText = visionText.text,
                        textBlocks = visionText.textBlocks
                    )
                )
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
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

    private fun mergeBlocks(candidates: List<BlockCandidate>): List<OcrTextBlock> {
        if (candidates.isEmpty()) return emptyList()

        val clusters = mutableListOf<MutableList<BlockCandidate>>()
        for (candidate in candidates.sortedByDescending { it.score }) {
            val cluster = clusters.firstOrNull { existing ->
                existing.any { matchesSameRegion(it.boundingBox, candidate.boundingBox) }
            }
            if (cluster == null) {
                clusters += mutableListOf(candidate)
            } else {
                cluster += candidate
            }
        }

        return clusters.mapNotNull { cluster ->
            val best = cluster.maxByOrNull { it.score } ?: return@mapNotNull null
            OcrTextBlock(
                text = best.text,
                lines = best.lines,
                boundingBox = best.boundingBox,
                recognizerHint = best.recognizerHint,
                languageTag = null
            )
        }
    }

    private fun matchesSameRegion(first: Rect?, second: Rect?): Boolean {
        if (first == null || second == null) return false

        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)
        if (right <= left || bottom <= top) return false

        val intersection = (right - left) * (bottom - top)
        val firstArea = max(1, first.width() * first.height())
        val secondArea = max(1, second.width() * second.height())
        val union = firstArea + secondArea - intersection
        return intersection.toFloat() / union.toFloat() >= 0.45f
    }

    private suspend fun extractAmountCandidates(document: OcrTextDocument): List<OcrAmountCandidate> = coroutineScope {
        val entitySupported = ensureEntityModelReady()
        val units = buildList {
            document.blocks.forEachIndexed { blockIndex, block ->
                add(TextUnit(block.text, blockIndex, null, block.boundingBox, block.recognizerHint))
                block.lines.forEachIndexed { lineIndex, line ->
                    add(TextUnit(line.text, blockIndex, lineIndex, line.boundingBox, block.recognizerHint))
                }
            }
        }

        val entityCandidates = if (entitySupported) {
            units.map { unit ->
                async { extractEntityCandidates(unit, document.blocks.size) }
            }.awaitAll().flatten()
        } else {
            emptyList()
        }

        val regexCandidates = units.flatMap { unit ->
            extractRegexCandidates(unit, document.blocks.size)
        }

        (entityCandidates + regexCandidates)
            .groupBy { AmountCandidateKey(it.amount, it.blockIndex, it.lineIndex) }
            .map { (_, grouped) ->
                grouped.maxByOrNull { it.score }!!
            }
            .sortedByDescending { it.score }
    }

    private suspend fun ensureEntityModelReady(): Boolean {
        if (entityModelReady) return true
        return entityModelMutex.withLock {
            if (entityModelReady) return@withLock true
            val downloaded = suspendCancellableCoroutine<Boolean> { continuation ->
                entityExtractor.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener { continuation.resume(true) }
                    .addOnFailureListener { continuation.resume(false) }
            }
            entityModelReady = downloaded
            downloaded
        }
    }

    private suspend fun extractEntityCandidates(
        unit: TextUnit,
        blockCount: Int
    ): List<OcrAmountCandidate> {
        if (!looksLikeMoneyText(unit.text)) return emptyList()

        val annotations = suspendCancellableCoroutine<List<EntityAnnotation>> { continuation ->
            entityExtractor.annotate(unit.text)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }

        return annotations.flatMap { annotation ->
            val containsMoney = annotation.entities.any { entity -> entity.type == Entity.TYPE_MONEY }
            if (!containsMoney) {
                emptyList()
            } else {
                val snippet = unit.text.substring(annotation.start, annotation.end)
                buildMoneyCandidates(
                    text = unit.text,
                    candidateText = snippet,
                    unit = unit,
                    blockCount = blockCount,
                    source = "entity",
                    sourceBonus = 7
                )
            }
        }
    }

    private fun extractRegexCandidates(unit: TextUnit, blockCount: Int): List<OcrAmountCandidate> {
        if (!looksLikeMoneyText(unit.text)) return emptyList()

        val matches = amountRegex.findAll(unit.text).toList()
        return matches.flatMap { match ->
            buildMoneyCandidates(
                text = unit.text,
                candidateText = match.value,
                unit = unit,
                blockCount = blockCount,
                source = "regex",
                sourceBonus = 0
            )
        }
    }

    private fun buildMoneyCandidates(
        text: String,
        candidateText: String,
        unit: TextUnit,
        blockCount: Int,
        source: String,
        sourceBonus: Int
    ): List<OcrAmountCandidate> {
        val amount = normalizeMoneyValue(candidateText) ?: return emptyList()
        if (amount !in 1.0..10_000_000.0) return emptyList()

        val lower = text.lowercase()
        var score = 10 + sourceBonus

        if (totalKeywords.any { it in lower }) score += 16
        if (amountKeywords.any { it in lower }) score += 8
        if (currencyKeywords.any { it in lower }) score += 5
        if (negativeKeywords.any { it in lower }) score -= 10
        if (metadataNoiseKeywords.any { it in lower }) score -= 14
        if (looksLikeDateOrPhoneContext(text, candidateText)) score -= 18
        if (unit.lineIndex != null) score += 4

        val blockPosition = unit.blockIndex + 1
        score += blockPosition * 2
        if (blockCount > 0 && unit.blockIndex >= blockCount - 2) score += 6
        if (candidateText.contains('$') || candidateText.contains("NT")) score += 4
        if (candidateText.contains(',')) score += 2
        if (amount >= 100) score += 2

        return listOf(
            OcrAmountCandidate(
                amount = amount,
                snippet = candidateText,
                source = source,
                score = score,
                blockIndex = unit.blockIndex,
                lineIndex = unit.lineIndex
            )
        )
    }

    private fun mergeAmountCandidate(
        parsed: ParsedTransaction?,
        candidates: List<OcrAmountCandidate>,
        rawText: String
    ): ParsedTransaction? {
        val best = candidates.firstOrNull()
        if (parsed == null && best == null) return null

        if (parsed == null && best != null) {
            return ParsedTransaction(
                amount = best.amount,
                note = rawText.take(100),
                isExpense = true,
                confidence = 0.55f
            )
        }

        if (best == null || parsed == null) return parsed

        val bestConfidence = when {
            best.source == "entity" && best.score >= 30 -> 0.92f
            best.source == "entity" -> 0.82f
            best.score >= 28 -> 0.86f
            else -> 0.72f
        }
        return parsed.copy(
            amount = best.amount,
            confidence = max(parsed.confidence, bestConfidence)
        )
    }

    private fun normalizeMoneyValue(text: String): Double? {
        val normalized = text
            .replace("NT$", "", ignoreCase = true)
            .replace("TWD", "", ignoreCase = true)
            .replace("NTD", "", ignoreCase = true)
            .replace("$", "")
            .replace(",", "")
            .trim()

        val regexMatch = Regex("""\d+(?:\.\d{1,2})?""").find(normalized)?.value ?: return null
        return regexMatch.toDoubleOrNull()
    }

    private fun looksLikeMoneyText(text: String): Boolean {
        return text.any { it.isDigit() } &&
            (currencyKeywords.any { keyword -> keyword in text.lowercase() } ||
                amountKeywords.any { keyword -> keyword in text.lowercase() } ||
                totalKeywords.any { keyword -> keyword in text.lowercase() } ||
                text.contains('$') ||
                text.contains('元'))
    }

    private fun looksLikeDateOrPhoneContext(text: String, candidateText: String): Boolean {
        if (dateSlashRegex.containsMatchIn(text)) return true
        if (phoneRegex.containsMatchIn(text)) return true
        if (text.contains(':') && candidateText.length <= 4) return true
        return false
    }
}

data class OcrResult(
    val rawText: String,
    val parsed: ParsedTransaction?,
    val document: OcrTextDocument? = null,
    val amountCandidates: List<OcrAmountCandidate> = emptyList()
)

data class OcrTextDocument(
    val rawText: String,
    val detectedLanguageTag: String?,
    val blocks: List<OcrTextBlock>
)

data class OcrTextBlock(
    val text: String,
    val lines: List<OcrTextLine>,
    val boundingBox: Rect?,
    val recognizerHint: ScreenshotRecognizerHint,
    val languageTag: String? = null
) {
    val top: Int get() = boundingBox?.top ?: Int.MAX_VALUE
    val left: Int get() = boundingBox?.left ?: Int.MAX_VALUE
}

data class OcrTextLine(
    val text: String,
    val boundingBox: Rect?
)

data class OcrAmountCandidate(
    val amount: Double,
    val snippet: String,
    val source: String,
    val score: Int,
    val blockIndex: Int,
    val lineIndex: Int?
)

private data class RecognizedDocument(
    val hint: ScreenshotRecognizerHint,
    val fullText: String,
    val textBlocks: List<Text.TextBlock>
)

private data class BlockCandidate(
    val text: String,
    val lines: List<OcrTextLine>,
    val boundingBox: Rect?,
    val recognizerHint: ScreenshotRecognizerHint,
    val score: Int
)

private data class TextUnit(
    val text: String,
    val blockIndex: Int,
    val lineIndex: Int?,
    val boundingBox: Rect?,
    val recognizerHint: ScreenshotRecognizerHint
)

private data class AmountCandidateKey(
    val amount: Double,
    val blockIndex: Int,
    val lineIndex: Int?
)

enum class ScreenshotRecognizerHint {
    CHINESE,
    JAPANESE,
    LATIN
}

private fun Text.TextBlock.toCandidate(hint: ScreenshotRecognizerHint): BlockCandidate {
    val text = text.replace("\\s+".toRegex(), " ").trim()
    val lines = lines
        .mapNotNull { line ->
            val normalized = line.text.replace("\\s+".toRegex(), " ").trim()
            normalized.takeIf { it.isNotBlank() }?.let {
                OcrTextLine(
                    text = it,
                    boundingBox = line.boundingBox
                )
            }
        }
    return BlockCandidate(
        text = text,
        lines = lines,
        boundingBox = boundingBox,
        recognizerHint = hint,
        score = scoreRecognizerText(text, hint)
    )
}

private fun scoreRecognizerText(text: String, hint: ScreenshotRecognizerHint): Int {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Int.MIN_VALUE

    val visibleChars = trimmed.count { !it.isWhitespace() }
    val digits = trimmed.count { it.isDigit() }
    val cjkChars = trimmed.count { it.code in 0x4E00..0x9FFF }
    val japaneseKana = trimmed.count { it.code in 0x3040..0x30FF }

    val recognizerBonus = when (hint) {
        ScreenshotRecognizerHint.CHINESE -> cjkChars * 2
        ScreenshotRecognizerHint.JAPANESE -> (cjkChars * 2) + (japaneseKana * 4)
        ScreenshotRecognizerHint.LATIN -> if (cjkChars == 0 && japaneseKana == 0) 24 else 0
    }

    return visibleChars + (digits / 2) + recognizerBonus
}

private val amountRegex = Regex("""(?:NT\$|NTD|TWD|\$)?\s*\d[\d,]*(?:\.\d{1,2})?""", RegexOption.IGNORE_CASE)
private val totalKeywords = listOf("總計", "合計", "總額", "應付", "實付", "total", "grand total", "amount due")
private val amountKeywords = listOf("金額", "付款", "支付", "刷卡", "消費", "paid", "charge", "debit")
private val currencyKeywords = listOf("nt$", "twd", "ntd", "jpy", "usd", "$", "元", "圓")
private val negativeKeywords = listOf("折扣", "找零", "change", "discount", "tax", "服務費")
private val metadataNoiseKeywords = listOf("電話", "統編", "地址", "卡號", "invoice", "tel", "receipt")
private val dateSlashRegex = Regex("""\d{1,4}[/-]\d{1,2}[/-]\d{1,4}""")
private val phoneRegex = Regex("""(?:\+?\d{2,4}[- ]?)?(?:\d{2,4}[- ]?){2,3}\d{2,4}""")
