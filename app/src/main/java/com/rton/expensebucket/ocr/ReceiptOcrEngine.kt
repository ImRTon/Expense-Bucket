package com.rton.expensebucket.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.content.Context
import android.net.Uri
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
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
 * Receipt OCR engine with a fundamentally robust amount extraction pipeline:
 *
 *   1. Multi-recognizer OCR → merged blocks → flat rawText
 *   2. ML Kit Entity Extraction on FULL rawText (not per-block) so the model
 *      sees full context like "金額 → ¥18,352"
 *   3. Regex-based extraction with currency symbol priority as backup
 *   4. Smart merging: entity candidates + regex candidates → best amount
 *   5. Currency detection (JPY / TWD / USD) from text signals
 *
 * Entity Extraction runs with multiple language models in parallel
 * (ja, en, zh) to avoid depending on language detection being correct.
 */
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
    private val entityExtractorMutex = Mutex()
    private val entityExtractors = mutableMapOf<String, EntityExtractor>()
    private val entityModelReady = mutableMapOf<String, Boolean>()
    private var entityExtractionDisabled = false

    // Language models to try for entity extraction — covers most receipt scenarios
    private val entityLanguageTags = listOf("ja", "en", "zh")

    // ═══════════════════════════════════════════════════════════════
    //  Public API
    // ═══════════════════════════════════════════════════════════════

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
            translatedText = translatedText,
            entityMoneySnippets = emptyList()
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Core pipeline
    // ═══════════════════════════════════════════════════════════════

    private suspend fun processImage(
        image: InputImage,
        includeJapaneseRecognizer: Boolean
    ): ReceiptOcrResult = coroutineScope {
        val chineseResult = recognizeFullText(chineseRecognizer, image)
        val latinResult = recognizeFullText(latinRecognizer, image)
        val japaneseResult = if (includeJapaneseRecognizer) {
            recognizeFullText(japaneseRecognizer, image)
        } else null

        // Step 2: Merge overlapping blocks → ordered raw text
        val allBlockCandidates = buildList<ReceiptBlockCandidate> {
            chineseResult.textBlocks.forEach { add(it.toBlockCandidate(RecognizerHint.CHINESE)) }
            japaneseResult?.textBlocks?.forEach { add(it.toBlockCandidate(RecognizerHint.JAPANESE)) }
            latinResult.textBlocks.forEach { add(it.toBlockCandidate(RecognizerHint.LATIN)) }
        }
        val mergedBlocks = mergeBlocks(allBlockCandidates)
            .sortedWith(compareBy<MergedBlock>({ it.top }, { it.left }))

        val rawText = mergedBlocks.joinToString("\n") { it.text }.ifBlank {
            val recognizerResults = buildList<Pair<Text, RecognizerHint>> {
                add(chineseResult to RecognizerHint.CHINESE)
                if (japaneseResult != null) add(japaneseResult to RecognizerHint.JAPANESE)
                add(latinResult to RecognizerHint.LATIN)
            }
            recognizerResults.maxByOrNull { (t, h) -> scoreRecognizerText(t.text, h) }
                ?.first?.text?.trim().orEmpty()
        }

        // Step 3: Identify language
        val detectedLanguage = identifyLanguage(rawText)

        // Step 4: Entity Extraction on FULL rawText with multiple language models
        val entityMoneySnippets = extractMoneyEntitiesFromFullText(rawText)

        // Step 5: Build result (regex + entity → best amount)
        buildResult(
            rawText = rawText,
            detectedLanguageTag = detectedLanguage,
            translatedText = null,
            entityMoneySnippets = entityMoneySnippets
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Entity Extraction on FULL text — the key improvement
    // ═══════════════════════════════════════════════════════════════

    /**
     * Run Entity Extraction on the entire flattened rawText with multiple
     * language models in parallel. Returns all money entity snippets found.
     *
     * Running on the full text gives ML Kit full context — e.g. it can see
     * "金額" near "¥18,352" and understand it's the total, even across lines.
     *
     * NOTE: Entity Extraction is disabled due to native library loading issues
     * on some devices. Regex-based extraction is sufficient for most receipts.
     */
    private suspend fun extractMoneyEntitiesFromFullText(
        rawText: String
    ): List<EntityMoneySnippet> {
        if (rawText.isBlank()) return emptyList()
        
        return try {
            coroutineScope {
                val results = entityLanguageTags.map { langTag ->
                    async {
                        try {
                            val modelId = EntityExtractorOptions.fromLanguageTag(langTag)
                            val extractor = getOrCreateEntityExtractor(modelId) ?: return@async emptyList()
                            val ready = ensureEntityModelReady(modelId, extractor)
                            if (!ready) return@async emptyList()
                            extractMoneyAnnotations(extractor, rawText, langTag)
                        } catch (e: Throwable) {
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()

                results
                    .groupBy { it.amount }
                    .mapNotNull { (_, group) -> group.maxByOrNull { it.score } }
            }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    private suspend fun extractMoneyAnnotations(
        extractor: EntityExtractor,
        text: String,
        languageTag: String
    ): List<EntityMoneySnippet> {
        val annotations = suspendCancellableCoroutine<List<EntityAnnotation>> { continuation ->
            try {
                extractor.annotate(text)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(emptyList()) }
            } catch (e: Throwable) {
                continuation.resume(emptyList())
            }
        }

        return annotations.mapNotNull { annotation ->
            val hasMoney = annotation.entities.any { it.type == Entity.TYPE_MONEY }
            if (!hasMoney) return@mapNotNull null

            val snippet = text.substring(annotation.start, annotation.end)
            val amount = normalizeMoneyValue(snippet) ?: return@mapNotNull null
            if (amount !in 1.0..10_000_000.0) return@mapNotNull null

            // Score based on context around the annotation
            val contextStart = maxOf(0, annotation.start - 40)
            val contextEnd = minOf(text.length, annotation.end + 20)
            val context = text.substring(contextStart, contextEnd).lowercase()

            var score = 20 // base entity extraction bonus
            if (ReceiptTextParser.totalKeywords.any { it in context }) score += 15
            if (ReceiptTextParser.amountKeywords.any { it in context }) score += 10
            if (snippet.contains('¥') || snippet.contains('￥') || snippet.contains('$')) score += 8
            if (ReceiptTextParser.metadataNoiseKeywords.any { it in context }) score -= 20
            if (looksLikeDateOrPhone(context)) score -= 15
            // Position bonus: amounts later in text are more likely to be totals
            score += (annotation.start * 10 / maxOf(1, text.length))
            if (amount >= 100) score += 3

            EntityMoneySnippet(
                amount = amount,
                snippet = snippet,
                score = score,
                startIndex = annotation.start,
                languageTag = languageTag
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  OCR recognizer helpers
    // ═══════════════════════════════════════════════════════════════

    private suspend fun recognizeFullText(
        recognizer: TextRecognizer,
        image: InputImage
    ): Text = suspendCancellableCoroutine { continuation ->
        try {
            recognizer.process(image)
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        } catch (e: Throwable) {
            continuation.resumeWithException(e)
        }
    }

    private suspend fun identifyLanguage(text: String): String? {
        if (text.isBlank()) return null
        return suspendCancellableCoroutine { continuation ->
            try {
                languageIdentifier.identifyLanguage(text)
                    .addOnSuccessListener { code ->
                        continuation.resume(code.takeUnless { it == "und" })
                    }
                    .addOnFailureListener { continuation.resume(null) }
            } catch (e: Throwable) {
                continuation.resume(null)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Block merging
    // ═══════════════════════════════════════════════════════════════

    private fun mergeBlocks(candidates: List<ReceiptBlockCandidate>): List<MergedBlock> {
        if (candidates.isEmpty()) return emptyList()
        val clusters = mutableListOf<MutableList<ReceiptBlockCandidate>>()
        for (candidate in candidates.sortedByDescending { it.score }) {
            val cluster = clusters.firstOrNull { existing ->
                existing.any { matchesSameRegion(it.boundingBox, candidate.boundingBox) }
            }
            if (cluster == null) clusters += mutableListOf(candidate)
            else cluster += candidate
        }
        return clusters.mapNotNull { cluster ->
            val best = cluster.maxByOrNull { it.score } ?: return@mapNotNull null
            MergedBlock(text = best.text, boundingBox = best.boundingBox)
        }
    }

    private fun matchesSameRegion(a: Rect?, b: Rect?): Boolean {
        if (a == null || b == null) return false
        val left = max(a.left, b.left); val top = max(a.top, b.top)
        val right = min(a.right, b.right); val bottom = min(a.bottom, b.bottom)
        if (right <= left || bottom <= top) return false
        val intersection = (right - left) * (bottom - top)
        val union = max(1, a.width() * a.height()) + max(1, b.width() * b.height()) - intersection
        return intersection.toFloat() / union.toFloat() >= 0.45f
    }

    // ═══════════════════════════════════════════════════════════════
    //  Entity extractor management
    // ═══════════════════════════════════════════════════════════════

    private suspend fun getOrCreateEntityExtractor(modelId: String): EntityExtractor? {
        if (entityExtractionDisabled) return null
        entityExtractors[modelId]?.let { return it }
        return try {
            entityExtractorMutex.withLock {
                entityExtractors[modelId] ?: run {
                    val created = EntityExtraction.getClient(
                        EntityExtractorOptions.Builder(modelId).build()
                    )
                    entityExtractors[modelId] = created
                    created
                }
            }
        } catch (e: Throwable) {
            entityExtractionDisabled = true
            null
        }
    }

    private suspend fun ensureEntityModelReady(modelId: String, extractor: EntityExtractor): Boolean {
        entityModelReady[modelId]?.let { if (it) return true }
        return entityExtractorMutex.withLock {
            entityModelReady[modelId]?.let { if (it) return@withLock true }
            val ok = suspendCancellableCoroutine<Boolean> { c ->
                extractor.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener { c.resume(true) }
                    .addOnFailureListener { c.resume(false) }
            }
            entityModelReady[modelId] = ok
            ok
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Translation helpers
    // ═══════════════════════════════════════════════════════════════

    private suspend fun translatePreservingLines(
        translator: com.google.mlkit.nl.translate.Translator,
        text: String
    ): String? {
        val lines = text.split('\n')
        if (lines.isEmpty()) return null
        val translated = mutableListOf<String>()
        for (line in lines) {
            if (line.isBlank()) { translated += ""; continue }
            val result = suspendCancellableCoroutine<String?> { c ->
                translator.translate(line)
                    .addOnSuccessListener { c.resume(it) }
                    .addOnFailureListener { c.resume(null) }
            } ?: line
            translated += result
        }
        return translated.joinToString("\n")
    }

    // ═══════════════════════════════════════════════════════════════
    //  Result building
    // ═══════════════════════════════════════════════════════════════

    private fun buildResult(
        rawText: String,
        detectedLanguageTag: String?,
        translatedText: String?,
        entityMoneySnippets: List<EntityMoneySnippet>
    ): ReceiptOcrResult {
        val analysisText = translatedText ?: rawText
        val parsedReceipt = ReceiptTextParser.parse(
            sourceText = analysisText,
            fallbackAmountText = rawText,
            fallbackLineItemText = if (translatedText != null) analysisText else rawText,
            entityMoneySnippets = entityMoneySnippets
        )
        return ReceiptOcrResult(
            rawText = rawText,
            detectedLanguageTag = detectedLanguageTag,
            translatedText = translatedText,
            displayText = analysisText,
            totalAmount = parsedReceipt.totalAmount,
            lineItems = parsedReceipt.lineItems,
            note = parsedReceipt.note,
            wasTranslated = translatedText != null,
            detectedCurrency = parsedReceipt.detectedCurrency
        )
    }

    // ═══════════════════════════════════════════════════════════════
    //  Utility
    // ═══════════════════════════════════════════════════════════════

    private fun scoreRecognizerText(text: String, hint: RecognizerHint): Int {
        val t = text.trim()
        if (t.isEmpty()) return Int.MIN_VALUE
        val visible = t.count { !it.isWhitespace() }
        val cjk = t.count { it.code in 0x4E00..0x9FFF }
        val kana = t.count { it.code in 0x3040..0x30FF }
        val bonus = when (hint) {
            RecognizerHint.CHINESE -> cjk * 2
            RecognizerHint.JAPANESE -> cjk * 2 + kana * 4
            RecognizerHint.LATIN -> if (cjk == 0 && kana == 0) 24 else 0
        }
        return visible + bonus
    }

    companion object {
        internal fun normalizeMoneyValue(text: String): Double? {
            val cleaned = text
                .replace("NT$", "", ignoreCase = true)
                .replace("TWD", "", ignoreCase = true)
                .replace("NTD", "", ignoreCase = true)
                .replace("JPY", "", ignoreCase = true)
                .replace("USD", "", ignoreCase = true)
                .replace("¥", "").replace("￥", "")
                .replace("円", "").replace("元", "")
                .replace("$", "")
                .replace(Regex("[,\\s]+"), "") // "18, 352" → "18352"
                .trim()
            return Regex("""\d+(?:\.\d{1,2})?""").find(cleaned)?.value?.toDoubleOrNull()
        }

        internal fun looksLikeDateOrPhone(text: String): Boolean {
            return dateSlashRegex.containsMatchIn(text) || phoneRegex.containsMatchIn(text)
        }

        private val dateSlashRegex = Regex("""\d{1,4}[/-]\d{1,2}[/-]\d{1,4}""")
        private val phoneRegex = Regex("""(?:\+?\d{2,4}[- ]?)(?:\d{2,4}[- ]?){2,3}\d{2,4}""")
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Internal types
// ═══════════════════════════════════════════════════════════════════

private enum class RecognizerHint { CHINESE, JAPANESE, LATIN }

private data class ReceiptBlockCandidate(
    val text: String,
    val boundingBox: Rect?,
    val score: Int
)

private data class MergedBlock(
    val text: String,
    val boundingBox: Rect?
) {
    val top: Int get() = boundingBox?.top ?: Int.MAX_VALUE
    val left: Int get() = boundingBox?.left ?: Int.MAX_VALUE
}

internal data class EntityMoneySnippet(
    val amount: Double,
    val snippet: String,
    val score: Int,
    val startIndex: Int,
    val languageTag: String
)

private fun Text.TextBlock.toBlockCandidate(hint: RecognizerHint): ReceiptBlockCandidate {
    val normalized = text.replace("\\s+".toRegex(), " ").trim()
    val visible = normalized.count { !it.isWhitespace() }
    val cjk = normalized.count { it.code in 0x4E00..0x9FFF }
    val kana = normalized.count { it.code in 0x3040..0x30FF }
    val bonus = when (hint) {
        RecognizerHint.CHINESE -> cjk * 2
        RecognizerHint.JAPANESE -> cjk * 2 + kana * 4
        RecognizerHint.LATIN -> if (cjk == 0 && kana == 0) 24 else 0
    }
    return ReceiptBlockCandidate(text = normalized, boundingBox = boundingBox, score = visible + bonus)
}

// ═══════════════════════════════════════════════════════════════════
//  Public result types
// ═══════════════════════════════════════════════════════════════════

data class ReceiptOcrResult(
    val rawText: String,
    val detectedLanguageTag: String?,
    val translatedText: String?,
    val displayText: String,
    val totalAmount: Double?,
    val lineItems: List<String>,
    val note: String,
    val wasTranslated: Boolean,
    val detectedCurrency: String? = null
)

// ═══════════════════════════════════════════════════════════════════
//  ReceiptTextParser — regex + entity fusion
// ═══════════════════════════════════════════════════════════════════

internal data class ParsedReceiptDetails(
    val totalAmount: Double?,
    val lineItems: List<String>,
    val note: String,
    val detectedCurrency: String?
)

internal object ReceiptTextParser {

    val totalKeywords = listOf(
        "總計", "合計", "合計金額", "總額", "應付", "實付",
        "お会計", "お支払", "ご請求額", "領収金額", "現計", "税込",
        "売上", "ご利用金額", "お買い上げ", "請求額",
        "total", "amount due", "grand total", "subtotal", "amount"
    )

    val amountKeywords = listOf(
        "金額", "付款", "支付", "刷卡", "消費",
        "支払", "お支払い", "請求",
        "paid", "charge", "debit"
    )

    val metadataNoiseKeywords = listOf(
        "發票", "統編", "電話", "地址", "交易", "卡號", "信用卡", "簽帳", "找零", "折扣",
        "領収書", "レシート", "住所", "取引", "カード", "クレジット", "税率",
        "内税", "外税", "値引", "返品", "担当", "登録番号", "日時",
        "端末番号", "会員番号", "処理通番", "伝票番号", "承認番号", "有効期限",
        "加盟店", "MERCHANT", "TERM", "TRAN NO", "APP CODE",
        "AID", "ATC", "ARC", "SLIP NO", "APPROVAL",
        "invoice", "tax", "change", "payment", "card", "receipt",
        "tel", "phone", "store", "clerk", "customer copy"
    )

    /** Currency-prefixed amount — highest confidence regex. */
    private val currencyAmountRegex = Regex(
        """(?:NT\$|NTD|TWD|JPY|USD|[¥￥$])\s*(\d[\d,\s]*(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /** Bare numeric amount — fallback. */
    private val bareAmountRegex = Regex("""(\d[\d,]*(?:\.\d{1,2})?)""")

    /** Serial numbers: leading zeros + long digit strings. */
    private val serialNumberRegex = Regex("""^0\d{4,}$""")

    private val datePattern = Regex("""\d{1,4}[/-]\d{1,2}[/-]\d{1,4}""")

    fun parse(
        sourceText: String,
        fallbackAmountText: String,
        fallbackLineItemText: String,
        entityMoneySnippets: List<EntityMoneySnippet> = emptyList()
    ): ParsedReceiptDetails {
        val normalizedLines = normalizeLines(sourceText)
        val fallbackAmountLines = normalizeLines(fallbackAmountText)
        val fallbackLineItemLines = normalizeLines(fallbackLineItemText)
        val allLines = (normalizedLines + fallbackAmountLines).distinct()

        // ── Amount extraction: 3-tier priority ──────────────────
        //
        // Tier 1: Entity Extraction money candidates (ML Kit understood context)
        //         → only use if score is high enough (i.e. near keywords, has currency symbol)
        //
        // Tier 2: Regex with currency symbol (¥18,352 / NT$1,230 etc.)
        //         → very reliable because currency symbol is strong signal
        //
        // Tier 3: Regex keyword match (金額: 1230, total 1230 etc.)
        //         → keyword on same line as number
        //
        // Tier 4: Fallback — largest reasonable number in lower half of receipt

        val entityAmount = pickEntityAmount(entityMoneySnippets)
        val regexCurrencyAmount = extractCurrencySymbolAmount(allLines)
        val regexKeywordAmount = extractKeywordAmount(allLines)
        val regexFallbackAmount = extractFallbackAmount(allLines)

        val totalAmount = selectBestAmount(
            entityAmount, regexCurrencyAmount, regexKeywordAmount, regexFallbackAmount
        )

        val detectedCurrency = detectCurrency(allLines)

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
            note = note,
            detectedCurrency = detectedCurrency
        )
    }

    // ── Tier 1: Entity Extraction ─────────────────────────────

    private fun pickEntityAmount(snippets: List<EntityMoneySnippet>): AmountCandidate? {
        val best = snippets.maxByOrNull { it.score } ?: return null
        // Only trust entity extraction if it has a decent score
        // (i.e., found near keywords or has currency symbol)
        if (best.score < 15) return null
        return AmountCandidate(best.amount, best.score, "entity")
    }

    // ── Tier 2: Currency symbol amounts ───────────────────────

    private fun extractCurrencySymbolAmount(lines: List<String>): AmountCandidate? {
        val matches = lines.flatMapIndexed { index, line ->
            currencyAmountRegex.findAll(line).mapNotNull { match ->
                val amount = parseAmountString(match.groupValues[1]) ?: return@mapNotNull null
                if (amount !in 1.0..1_000_000.0) return@mapNotNull null

                var score = 30 // currency symbol is strong signal
                val lower = line.lowercase()
                if (totalKeywords.any { it in lower }) score += 15
                if (amountKeywords.any { it in lower }) score += 10
                if (metadataNoiseKeywords.any { it in lower }) score -= 25
                // Position bonus: later in receipt = more likely total
                score += index
                if (amount >= 100) score += 3

                AmountCandidate(amount, score, "currency_regex")
            }
        }
        return matches.maxByOrNull { it.score }
    }

    // ── Tier 3: Keyword + bare regex ──────────────────────────

    private fun extractKeywordAmount(lines: List<String>): AmountCandidate? {
        // Find lines with keywords, then look for amounts on same or adjacent lines
        val candidates = mutableListOf<AmountCandidate>()

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()
            val hasTotal = totalKeywords.any { it in lower }
            val hasAmount = amountKeywords.any { it in lower }
            if (!hasTotal && !hasAmount) continue

            // Look for amount on this line
            val sameLineAmount = findBestBareAmount(line)
            if (sameLineAmount != null) {
                var score = 20
                if (hasTotal) score += 10
                if (hasAmount) score += 5
                candidates += AmountCandidate(sameLineAmount, score, "keyword_same_line")
            }

            // Also look on immediately adjacent lines (±1)
            // This handles "金額\n¥18,352" where keyword and amount are separated
            for (offset in intArrayOf(1, -1, 2)) {
                val adjIndex = i + offset
                if (adjIndex !in lines.indices) continue
                val adjLine = lines[adjIndex]
                val adjLower = adjLine.lowercase()
                // Skip if adjacent line is also a metadata line
                if (metadataNoiseKeywords.any { it in adjLower }) continue

                // Prefer currency regex on adjacent line
                val adjCurrencyAmount = currencyAmountRegex.findAll(adjLine)
                    .mapNotNull { parseAmountString(it.groupValues[1]) }
                    .filter { it in 1.0..1_000_000.0 }
                    .maxOrNull()

                if (adjCurrencyAmount != null) {
                    var score = 28 // keyword nearby + currency symbol
                    if (hasTotal) score += 10
                    candidates += AmountCandidate(adjCurrencyAmount, score, "keyword_adj_currency")
                } else {
                    val adjBareAmount = findBestBareAmount(adjLine)
                    if (adjBareAmount != null) {
                        candidates += AmountCandidate(adjBareAmount, 15, "keyword_adj_bare")
                    }
                }
            }
        }

        return candidates.maxByOrNull { it.score }
    }

    // ── Tier 4: Fallback ──────────────────────────────────────

    private fun extractFallbackAmount(lines: List<String>): AmountCandidate? {
        val lowerHalf = lines.takeLast(maxOf(1, lines.size / 2))
        val amount = lowerHalf
            .filter { line ->
                val lower = line.lowercase()
                metadataNoiseKeywords.none { it in lower } && !datePattern.containsMatchIn(line)
            }
            .flatMap { line ->
                bareAmountRegex.findAll(line).mapNotNull { match ->
                    val raw = match.groupValues[1]
                    if (serialNumberRegex.matches(raw)) return@mapNotNull null
                    if (raw.replace(",", "").length > 6) return@mapNotNull null
                    parseAmountString(raw)
                }.filter { it in 10.0..500_000.0 }
            }
            .maxOrNull() ?: return null

        return AmountCandidate(amount, 5, "fallback")
    }

    // ── Amount selection: merge all tiers ─────────────────────

    private fun selectBestAmount(vararg candidates: AmountCandidate?): Double? {
        val valid = candidates.filterNotNull()
        if (valid.isEmpty()) return null

        // If entity and currency regex agree on the same amount → very high confidence
        val entityAmt = valid.firstOrNull { it.source == "entity" }
        val currencyAmt = valid.firstOrNull { it.source == "currency_regex" }
        if (entityAmt != null && currencyAmt != null && entityAmt.amount == currencyAmt.amount) {
            return entityAmt.amount
        }

        // If currency regex found something, it's usually correct
        // (currency symbol is very reliable signal)
        if (currencyAmt != null && currencyAmt.score >= 25) {
            return currencyAmt.amount
        }

        // If entity had very high score, trust it
        if (entityAmt != null && entityAmt.score >= 30) {
            return entityAmt.amount
        }

        // Keyword adjacent + currency is also very reliable
        val keywordAdj = valid.firstOrNull { it.source == "keyword_adj_currency" }
        if (keywordAdj != null) return keywordAdj.amount

        // Otherwise pick highest score
        return valid.maxByOrNull { it.score }?.amount
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun normalizeLines(text: String): List<String> =
        text.lines().map { it.replace("\\s+".toRegex(), " ").trim() }.filter { it.isNotBlank() }

    private fun findBestBareAmount(line: String): Double? {
        val lower = line.lowercase()
        if (metadataNoiseKeywords.any { it in lower }) return null
        return bareAmountRegex.findAll(line)
            .mapNotNull { match ->
                val raw = match.groupValues[1]
                if (serialNumberRegex.matches(raw)) return@mapNotNull null
                if (raw.replace(",", "").length > 6) return@mapNotNull null
                parseAmountString(raw)
            }
            .filter { it in 1.0..1_000_000.0 }
            .maxOrNull()
    }

    /** "18, 352" → 18352.0 — tolerant of OCR spaces after commas. */
    private fun parseAmountString(raw: String): Double? =
        raw.replace(Regex("[,\\s]+"), "").toDoubleOrNull()

    private fun detectCurrency(lines: List<String>): String? {
        val text = lines.joinToString(" ")
        val lower = text.lowercase()

        var jpyScore = 0; var twdScore = 0; var usdScore = 0

        if (text.contains('¥') || text.contains('￥')) jpyScore += 3
        if (text.contains('円')) jpyScore += 5
        if ("jpy" in lower) jpyScore += 5
        if (text.any { it.code in 0x3040..0x30FF }) jpyScore += 3
        if (listOf("売上", "お支払", "税込", "クレジット", "お会計", "ご利用").any { it in text }) jpyScore += 4

        if ("nt$" in lower || "ntd" in lower || "twd" in lower) twdScore += 5
        if (text.contains('元') && jpyScore == 0) twdScore += 3
        if (listOf("總計", "發票", "統編", "合計", "應付", "實付").any { it in text }) twdScore += 4

        if ("usd" in lower) usdScore += 5

        val max = maxOf(jpyScore, twdScore, usdScore)
        if (max == 0) return null

        return when (max) {
            jpyScore -> if (jpyScore > twdScore + 1) "JPY" else null
            twdScore -> if (twdScore > jpyScore + 1) "TWD" else null
            usdScore -> if (usdScore > maxOf(jpyScore, twdScore) + 1) "USD" else null
            else -> null
        }
    }

    private fun extractLineItems(lines: List<String>, totalAmount: Double?): List<String> {
        val totalText = totalAmount?.let { "%.2f".format(it) }
        return lines
            .filter { line ->
                val lower = line.lowercase()
                metadataNoiseKeywords.none { it in lower } &&
                    totalKeywords.none { it in lower } &&
                    line.any { c -> c.isLetter() || c.code in 0x4E00..0x9FFF } &&
                    line.length in 2..48
            }
            .filterNot { line ->
                totalText != null && line.contains(totalText.removeSuffix(".00"))
            }
            .take(8)
    }
}

private data class AmountCandidate(
    val amount: Double,
    val score: Int,
    val source: String
)
