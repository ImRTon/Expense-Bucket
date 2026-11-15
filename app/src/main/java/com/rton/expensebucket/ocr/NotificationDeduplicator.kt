package com.rton.expensebucket.ocr

import com.rton.expensebucket.data.model.NotificationCapture
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object NotificationDeduplicator {
    const val MATCH_WINDOW_MS = 2 * 60 * 1000L
    private const val GOOGLE_WALLET = "com.google.android.apps.walletnfcrel"
    private val gatewayPrefixRegex = Regex(
        """^(?:連加(?:網路(?:商業)?(?:股份有限公司)?)?|line\s*pay|街口(?:電子支付|電支)?|jkopay|jko|全支付|pxpay(?:plus)?|悠遊付|easycard|拍付(?:國際)?|pi\s*(?:拍)?錢包)[*＊\-_\s:：/]+""",
        RegexOption.IGNORE_CASE
    )
    private val gatewayOnlyMerchants = setOf(
        "連加", "連加網路", "連加網路商業", "連加股份有限公司", "linepay",
        "街口", "街口電支", "街口電子支付", "jkopay",
        "全支付", "pxpay", "悠遊付", "easycard", "拍付", "拍付國際", "pi錢包", "pi拍錢包"
    )

    fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.replace(Regex("""\s+"""), " ").trim().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun extractCardSuffix(text: String): String? =
        Regex("""(?:卡號末四碼|末四碼|卡號末碼|末碼|尾四碼|尾號|[•●*]{2,})\s*[:：]?\s*(\d{4})(?!\d)""")
            .find(text)?.groupValues?.get(1)

    fun findDuplicate(incoming: NotificationCapture, recent: List<NotificationCapture>): Long? {
        val sameSource = recent.firstOrNull {
            it.packageName == incoming.packageName &&
                it.notificationKey == incoming.notificationKey &&
                sameAmountAndTime(incoming, it) &&
                !hasConflictingEvidence(incoming, it)
        }
        if (sameSource != null) return sameSource.transactionId

        // Each transaction can absorb only one notification from a given source.
        // This preserves a second same-amount purchase even while the first pair is recent.
        val alreadyCapturedBySource = recent.filter { it.packageName == incoming.packageName }
            .map { it.transactionId }.toSet()
        val candidates = recent.filter {
            it.transactionId !in alreadyCapturedBySource &&
                isCompatibleCrossAppPair(incoming, it) &&
                sameAmountAndTime(incoming, it) &&
                samePurchaseEvidence(incoming, it)
        }
        val strongMatches = candidates.filter { hasDirectEvidenceMatch(incoming, it) }
            .map { it.transactionId }
            .distinct()
        if (strongMatches.size == 1) return strongMatches.single()

        val matches = candidates.map { it.transactionId }.distinct()
        // Ambiguous pairs are kept for review rather than silently dropping a real expense.
        return matches.singleOrNull()
    }

    private fun sameAmountAndTime(first: NotificationCapture, second: NotificationCapture): Boolean =
        first.amount == second.amount && first.currency == second.currency &&
            first.isExpense == second.isExpense &&
            abs(first.capturedAt - second.capturedAt) <= MATCH_WINDOW_MS &&
            (first.transactionDate == null || second.transactionDate == null ||
                abs(first.transactionDate - second.transactionDate) <= MATCH_WINDOW_MS)

    private fun isCompatibleCrossAppPair(first: NotificationCapture, second: NotificationCapture): Boolean {
        if (first.packageName == second.packageName) return false
        val firstWallet = walletGroup(first.packageName)
        val secondWallet = walletGroup(second.packageName)
        val firstBank = bankGroup(first.packageName)
        val secondBank = bankGroup(second.packageName)

        val isWalletAndBank = (firstWallet != null && secondBank != null) ||
            (secondWallet != null && firstBank != null)
        val isSameBank = firstBank != null && secondBank != null &&
            (firstBank == secondBank || firstBank == "line_card" || secondBank == "line_card")
        val isSameWallet = firstWallet != null && firstWallet == secondWallet
        val isEpayAndSystemWallet = (isEpayGroup(firstWallet) && secondWallet == "google_wallet") ||
            (isEpayGroup(secondWallet) && firstWallet == "google_wallet")

        return isWalletAndBank || isSameBank || isSameWallet || isEpayAndSystemWallet
    }

    private fun bankGroup(packageName: String): String? {
        val pkg = packageName.lowercase(Locale.ROOT)
        return when {
            pkg.endsWith("#card") -> "line_card"
            "sinopac" in pkg || "sionpac" in pkg || "dawho" in pkg || "dacard" in pkg -> "sinopac"
            "taishin" in pkg || "richart" in pkg -> "taishin"
            "fubon" in pkg -> "fubon"
            "cathay" in pkg -> "cathay"
            "ctbc" in pkg || "chinatrust" in pkg -> "ctbc"
            "esun" in pkg -> "esun"
            "wbank.ubot.com.tw" in pkg || "ubot" in pkg -> "ubot"
            "tw.gov.post.mpost" in pkg || "mpost" in pkg || "post" in pkg -> "post"
            else -> null
        }
    }

    private fun walletGroup(packageName: String): String? {
        val pkg = packageName.lowercase(Locale.ROOT)
        if (pkg.endsWith("#card")) return null
        return when {
            pkg == GOOGLE_WALLET || "walletnfcrel" in pkg || ("google" in pkg && "pay" in pkg) -> "google_wallet"
            pkg == "jp.naver.line.android" || pkg == "com.linepaytw.upay" || "linepay" in pkg -> "linepay"
            pkg == "com.jkos.app" || "jkos" in pkg || "jkopay" in pkg -> "jkos"
            pkg == "com.easycard.wallet" || "easycard" in pkg || "easygo" in pkg -> "easycard"
            pkg == "tw.com.pchome.android.pi" || "pchome.android.pi" in pkg -> "pi"
            pkg == "com.pxpayplus.app" || "pxpay" in pkg -> "pxpay"
            "applepay" in pkg || "samsungpay" in pkg -> "system_wallet"
            else -> null
        }
    }

    private fun isEpayGroup(group: String?): Boolean =
        group != null && group != "google_wallet" && group != "system_wallet"

    private fun hasDirectEvidenceMatch(first: NotificationCapture, second: NotificationCapture): Boolean {
        val sameMerchant = merchantsMatch(first.merchant, second.merchant)
        val sameCard = first.cardSuffix != null && first.cardSuffix == second.cardSuffix
        return sameMerchant || sameCard
    }

    private fun hasConflictingEvidence(first: NotificationCapture, second: NotificationCapture): Boolean {
        val sameMerchant = merchantsMatch(first.merchant, second.merchant)
        val sameCard = first.cardSuffix != null && first.cardSuffix == second.cardSuffix
        if (sameMerchant || sameCard) return false

        val cardsConflict = first.cardSuffix != null && second.cardSuffix != null &&
            first.cardSuffix != second.cardSuffix
        val firstMerchant = normalizeMerchant(first.merchant)
        val secondMerchant = normalizeMerchant(second.merchant)
        val merchantsConflict = firstMerchant.isNotBlank() && secondMerchant.isNotBlank() &&
            !merchantsMatch(first.merchant, second.merchant)

        return cardsConflict || merchantsConflict
    }

    private fun samePurchaseEvidence(first: NotificationCapture, second: NotificationCapture): Boolean {
        if (hasDirectEvidenceMatch(first, second)) return true

        val firstWallet = walletGroup(first.packageName)
        val secondWallet = walletGroup(second.packageName)
        val firstBank = bankGroup(first.packageName)
        val secondBank = bankGroup(second.packageName)

        val cardsConflict = first.cardSuffix != null && second.cardSuffix != null &&
            first.cardSuffix != second.cardSuffix

        // E-payment apps (LINE Pay, JKO, PX Pay, EasyCard, Pi) rarely expose the underlying
        // card's last 4 digits, and bank notifications often show the gateway name (e.g. 連加*)
        // or an English/legal merchant name.
        if (isEpayGroup(firstWallet) || isEpayGroup(secondWallet)) {
            return !cardsConflict
        }

        // Companion apps from the same bank (e.g. 永豐大戶 + 永豐大咖, Richart + Richart Life,
        // Fubon + Fubon aibank) reporting the same amount at the same time represent one swipe
        // as long as their extracted card or merchant fields do not conflict.
        val isSameBank = firstBank != null && secondBank != null &&
            (firstBank == secondBank || firstBank == "line_card" || secondBank == "line_card")
        if (isSameBank) {
            return !hasConflictingEvidence(first, second)
        }

        // For NFC wallet + bank pairs, allow matching when one side (e.g. Richart or Post)
        // does not provide a merchant or 4-digit card suffix, while the other side does.
        val firstHasEvidence = normalizeMerchant(first.merchant).isNotBlank() || first.cardSuffix != null
        val secondHasEvidence = normalizeMerchant(second.merchant).isNotBlank() || second.cardSuffix != null
        return (firstHasEvidence xor secondHasEvidence) && !cardsConflict
    }

    private fun merchantsMatch(firstRaw: String, secondRaw: String): Boolean {
        val first = normalizeMerchant(firstRaw)
        val second = normalizeMerchant(secondRaw)
        if (first.isBlank() || second.isBlank()) return false
        if (first == second) return true
        return minOf(first.length, second.length) >= 2 &&
            (first.contains(second) || second.contains(first))
    }

    private fun normalizeMerchant(merchant: String): String {
        val normalizedForm = Normalizer.normalize(merchant, Normalizer.Form.NFKC).trim()
        val strippedGateway = normalizedForm.replace(gatewayPrefixRegex, "").trim()
        val candidate = strippedGateway.ifBlank { normalizedForm }
        val compact = candidate.lowercase(Locale.ROOT).replace(Regex("""[^\p{L}\p{N}]"""), "")
        return if (compact in gatewayOnlyMerchants) "" else compact
    }
}
