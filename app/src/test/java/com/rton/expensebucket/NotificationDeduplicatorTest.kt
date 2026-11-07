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
        assertNull(NotificationDeduplicator.extractCardSuffix("末三碼123"))
    }
}
