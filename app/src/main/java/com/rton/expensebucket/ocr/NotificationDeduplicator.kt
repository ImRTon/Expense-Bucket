package com.rton.expensebucket.ocr

import com.rton.expensebucket.data.model.NotificationCapture
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object NotificationDeduplicator {
    const val MATCH_WINDOW_MS = 2 * 60 * 1000L
    private const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
    private val bankPackages = listOf(
        "taishin", "richart", "esun", "cathay", "ctbc", "fubon", "sinopac",
        "wbank.ubot.com.tw", "tw.gov.post.mpost"
    )

    fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.replace(Regex("""\s+"""), " ").trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun extractCardSuffix(text: String): String? =
        Regex("""(?:末四碼|卡號末四碼|[•●*]{2,})\s*(\d{4})(?!\d)""")
            .find(text)?.groupValues?.get(1)

    fun findDuplicate(incoming: NotificationCapture, recent: List<NotificationCapture>): Long? {
        val sameSource = recent.firstOrNull {
            it.packageName == incoming.packageName &&
                it.notificationKey == incoming.notificationKey && it.textHash == incoming.textHash &&
                sameAmountAndTime(incoming, it)
        }
        if (sameSource != null) return sameSource.transactionId

        // Each transaction can absorb only one notification from a given source.
        // This preserves a second same-amount purchase even while the first pair is recent.
        val alreadyCapturedBySource = recent.filter { it.packageName == incoming.packageName }
            .map { it.transactionId }.toSet()
        val matches = recent.filter {
            it.transactionId !in alreadyCapturedBySource && isWalletBankPair(incoming, it) &&
                sameAmountAndTime(incoming, it) && samePurchaseEvidence(incoming, it)
        }.map { it.transactionId }.distinct()
        // Ambiguous pairs are kept for review rather than silently dropping a real expense.
        return matches.singleOrNull()
    }

    private fun sameAmountAndTime(first: NotificationCapture, second: NotificationCapture): Boolean =
        first.amount == second.amount && first.currency == second.currency &&
            first.isExpense == second.isExpense &&
            abs(first.capturedAt - second.capturedAt) <= MATCH_WINDOW_MS &&
            (first.transactionDate == null || second.transactionDate == null ||
                abs(first.transactionDate - second.transactionDate) <= MATCH_WINDOW_MS)

    private fun isWalletBankPair(first: NotificationCapture, second: NotificationCapture): Boolean =
        (first.packageName == GOOGLE_WALLET && isBank(second.packageName)) ||
            (second.packageName == GOOGLE_WALLET && isBank(first.packageName))

    private fun isBank(packageName: String): Boolean =
        bankPackages.any { packageName.lowercase(Locale.ROOT).contains(it) }

    private fun samePurchaseEvidence(first: NotificationCapture, second: NotificationCapture): Boolean {
        val firstMerchant = normalizeMerchant(first.merchant)
        val secondMerchant = normalizeMerchant(second.merchant)
        val sameMerchant = firstMerchant.isNotBlank() && firstMerchant == secondMerchant
        val sameCard = first.cardSuffix != null && first.cardSuffix == second.cardSuffix
        // Wallet and bank merchant names can be in different languages; matching card
        // suffixes provide an alternative. Wallet token suffixes can differ from the card,
        // so a matching merchant is sufficient even when those suffixes differ.
        return sameMerchant || sameCard
    }

    private fun normalizeMerchant(merchant: String): String =
        Normalizer.normalize(merchant, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]"""), "")
}
