package com.rton.expensebucket

import com.rton.expensebucket.data.model.NotificationCapture
import com.rton.expensebucket.ocr.NotificationDeduplicator
import org.junit.Assert.*
import org.junit.Test

class NotificationDeduplicatorTest {
    private val bank = NotificationCapture(
        transactionId = 1,
        packageName = "com.sinopac.DaCard",
        notificationKey = "bank-key",
        textHash = "bank-text",
        amount = 413.0,
        isExpense = true,
        merchant = "SAMPLE CAFE B",
        cardSuffix = "1234",
        transactionDate = 1_000_000L,
        capturedAt = 1_005_000L
    )
    private val wallet = bank.copy(
        transactionId = 0,
        packageName = "com.google.android.apps.walletnfcrel",
        notificationKey = "wallet-key",
        textHash = "wallet-text",
        merchant = "Sample Cafe B",
        transactionDate = null,
        capturedAt = 1_010_000L
    )

    @Test
    fun matchesBankAndWalletInEitherOrder() {
        assertEquals(1L, NotificationDeduplicator.findDuplicate(wallet, listOf(bank)))
        assertEquals(2L, NotificationDeduplicator.findDuplicate(bank, listOf(wallet.copy(transactionId = 2))))
    }

    @Test
    fun matchesDifferentMerchantLanguagesUsingCardSuffix() {
        assertEquals(1L, NotificationDeduplicator.findDuplicate(
            wallet.copy(merchant = "測試百貨"), listOf(bank)
        ))
    }

    @Test
    fun matchesTokenizedCardUsingMerchantEvenWhenSuffixDiffers() {
        assertEquals(1L, NotificationDeduplicator.findDuplicate(
            wallet.copy(cardSuffix = "5678"), listOf(bank)
        ))
    }

    @Test
    fun keepsTransactionsWithDifferentAmountDirectionCurrencyOrTime() {
        listOf(
            wallet.copy(amount = 414.0),
            wallet.copy(isExpense = false),
            wallet.copy(currency = "USD"),
            wallet.copy(capturedAt = bank.capturedAt + 120_001),
            wallet.copy(transactionDate = bank.transactionDate!! + 120_001),
            wallet.copy(merchant = "OTHER SHOP", cardSuffix = "5678")
        ).forEach { assertNull(NotificationDeduplicator.findDuplicate(it, listOf(bank))) }
    }

    @Test
    fun keepsSameAmountWithoutMerchantOrCardEvidence() {
        assertNull(NotificationDeduplicator.findDuplicate(
            wallet.copy(merchant = "", cardSuffix = null),
            listOf(bank.copy(merchant = "", cardSuffix = null))
        ))
    }

    @Test
    fun keepsSecondPurchaseFromSameSourceAfterPairWasMatched() {
        val firstWallet = wallet.copy(transactionId = 1)
        val secondWallet = wallet.copy(notificationKey = "wallet-key-2", textHash = "next-purchase")
        assertNull(NotificationDeduplicator.findDuplicate(secondWallet, listOf(bank, firstWallet)))
        assertNull(NotificationDeduplicator.findDuplicate(
            bank.copy(notificationKey = "bank-key-2", textHash = "next-purchase"),
            listOf(bank, firstWallet)
        ))
    }

    @Test
    fun ignoresRepostedSameNotificationButKeepsDifferentBankNotifications() {
        assertEquals(1L, NotificationDeduplicator.findDuplicate(bank, listOf(bank)))
        assertNull(NotificationDeduplicator.findDuplicate(
            bank.copy(notificationKey = "another-bank-key"), listOf(bank)
        ))
        assertNull(NotificationDeduplicator.findDuplicate(
            bank.copy(packageName = "com.fubon.mobilebank"), listOf(bank)
        ))
    }

    @Test
    fun keepsAmbiguousCrossAppMatches() {
        assertNull(NotificationDeduplicator.findDuplicate(wallet, listOf(bank, bank.copy(transactionId = 2))))
    }

    @Test
    fun extractsCardSuffixFromBankAndWalletMasks() {
        assertEquals("1234", NotificationDeduplicator.extractCardSuffix("信用卡末四碼1234刷卡通知"))
        assertEquals("1234", NotificationDeduplicator.extractCardSuffix("使用 永豐SPORT卡 **1234 付款"))
        assertEquals("1234", NotificationDeduplicator.extractCardSuffix("使用 永豐SPORT卡 ••1234 付款"))
        assertEquals("8888", NotificationDeduplicator.extractCardSuffix("中國信託 LINE Pay 卡 (尾號 8888)"))
        assertNull(NotificationDeduplicator.extractCardSuffix("末三碼123"))
    }

    @Test
    fun deduplicatesSinopacDawhoAndDaCardCompanionApps() {
        val dawho = bank.copy(
            transactionId = 1,
            packageName = "com.sinopac.dawho",
            notificationKey = "dawho-key",
            textHash = "dawho-text"
        )
        val daCard = bank.copy(
            transactionId = 0,
            packageName = "com.sinopac.DaCard",
            notificationKey = "dacard-key",
            textHash = "dacard-text",
            capturedAt = dawho.capturedAt + 2_000L
        )

        assertEquals(1L, NotificationDeduplicator.findDuplicate(daCard, listOf(dawho)))
        assertEquals(1L, NotificationDeduplicator.findDuplicate(
            dawho.copy(transactionId = 0),
            listOf(daCard.copy(transactionId = 1))
        ))
    }

    @Test
    fun deduplicatesLinePayAndBankCreditCardNotificationsIncludingThreeWay() {
        val linePay = NotificationCapture(
            transactionId = 1,
            packageName = "jp.naver.line.android",
            notificationKey = "line-key",
            textHash = "line-text",
            amount = 356.0,
            isExpense = true,
            merchant = "全聯",
            cardSuffix = null,
            transactionDate = null,
            capturedAt = 1_000_000L
        )
        val dawho = NotificationCapture(
            transactionId = 0,
            packageName = "com.sinopac.dawho",
            notificationKey = "dawho-key",
            textHash = "dawho-text",
            amount = 356.0,
            isExpense = true,
            merchant = "連加＊全聯福利中心",
            cardSuffix = "1234",
            transactionDate = 1_000_000L,
            capturedAt = 1_002_000L
        )
        val daCard = dawho.copy(
            packageName = "com.sinopac.DaCard",
            notificationKey = "dacard-key",
            textHash = "dacard-text",
            capturedAt = 1_003_000L
        )

        // LINE Pay -> 大戶 -> 永豐大咖
        assertEquals(1L, NotificationDeduplicator.findDuplicate(dawho, listOf(linePay)))
        val capturedDawho = dawho.copy(transactionId = 1)
        assertEquals(1L, NotificationDeduplicator.findDuplicate(daCard, listOf(capturedDawho, linePay)))

        // 刷卡通知只有英文店名或「LINE PAY」且 LINE Pay 無末四碼時仍應去重
        val bankEnglishMerchant = dawho.copy(merchant = "STARBUCKS")
        assertEquals(1L, NotificationDeduplicator.findDuplicate(
            bankEnglishMerchant,
            listOf(linePay.copy(merchant = "星巴克"))
        ))

        val bankGatewayMerchant = dawho.copy(merchant = "連加網路")
        assertEquals(1L, NotificationDeduplicator.findDuplicate(bankGatewayMerchant, listOf(linePay)))
    }

    @Test
    fun deduplicatesLineWalletAndLineCardSwipeNotifications() {
        val lineWallet = NotificationCapture(
            transactionId = 1,
            packageName = "jp.naver.line.android",
            notificationKey = "line-wallet-key",
            textHash = "line-wallet-text",
            amount = 280.0,
            isExpense = true,
            merchant = "星巴克",
            cardSuffix = null,
            transactionDate = null,
            capturedAt = 1_000_000L
        )
        val lineCardSwipe = NotificationCapture(
            transactionId = 0,
            packageName = "jp.naver.line.android#card",
            notificationKey = "line-card-key",
            textHash = "line-card-text",
            amount = 280.0,
            isExpense = true,
            merchant = "LINE Pay-星巴克",
            cardSuffix = "1234",
            transactionDate = 1_000_000L,
            capturedAt = 1_003_000L
        )

        assertEquals(1L, NotificationDeduplicator.findDuplicate(lineCardSwipe, listOf(lineWallet)))
    }
}
