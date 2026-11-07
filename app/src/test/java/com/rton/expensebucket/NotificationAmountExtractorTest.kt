package com.rton.expensebucket

import com.rton.expensebucket.ocr.NotificationAmountExtractor
import com.rton.expensebucket.ocr.NotificationCapturePolicy
import com.rton.expensebucket.ocr.NotificationParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Synthetic boundary cases supplement the separately documented device-note corpus. */
@RunWith(Parameterized::class)
class NotificationAmountExtractorTest(
    private val description: String,
    private val text: String,
    private val expectedAmount: Double?,
    private val expectedIsExpense: Boolean
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any?>> = listOf(
            arrayOf("bare expense without merchant card or date", "消費 NT$619", 619.0, true),
            arrayOf("debit with promotion footer and no metadata", "扣款NT$638，指定通路最高回饋5%", 638.0, true),
            arrayOf("successful payment and amount only", "付款成功 金額$742，活動回饋另計", 742.0, true),
            arrayOf("completed transaction in separate clause", "交易成功，金額NT$863，限時回饋另計", 863.0, true),
            arrayOf("authorization without merchant or card", "金額NT$961，已授權，限時優惠另計", 961.0, true),
            arrayOf("actual expense after advertised amounts", "限時回饋：滿NT$4,800贈NT$360。您已消費NT$743", 743.0, true),
            arrayOf("balance is not the debit amount", "扣款成功NT$684，餘額NT$18,500，最高回饋3%", 684.0, true),
            arrayOf("cashback cap is not the payment amount", "付款NT$725 回饋上限NT$120", 725.0, true),
            arrayOf("date without merchant or card", "2025/04/09 14:25 消費金額NT$853，最高回饋4%", 853.0, true),
            arrayOf("promotion word inside merchant name", "在 優惠雜貨舖 消費NT$573", 573.0, true),
            arrayOf("cashback card without suffix or merchant", "您的現金回饋信用卡消費NT$562", 562.0, true),
            arrayOf("reward footer without metadata or completed wording", "消費NT$547元享最高回饋3%", 547.0, true),
            arrayOf("completed purchase with conditional offer footer", "您已消費NT$549元即可參加優惠活動", 549.0, true),
            arrayOf("notification title and amount only", "刷卡通知 金額NT$798", 798.0, true),
            arrayOf("incoming transfer without merchant or card", "入帳NT$1,357，帳戶餘額NT$50,000", 1357.0, false),
            arrayOf("incoming transfer with ten thousand unit", "入帳新臺幣3萬元，帳戶餘額新臺幣7萬元", 30000.0, false),
            arrayOf("payment with thousand unit", "扣款2千元，最高回饋5%", 2000.0, true),
            arrayOf("refund with promotion word", "退款成功NT$137，優惠取消退回", 137.0, false),
            arrayOf("amount before debit action", "NT$531 已扣款，最高回饋5%", 531.0, true),
            arrayOf("amount before completed transaction", "NT$546交易成功，指定通路優惠另計", 546.0, true),
            arrayOf("decimal expense and percentage reward", "消費NT$529.75，指定通路回饋2.5%", 529.75, true),
            arrayOf("credited cashback income", "現金回饋$58元已入帳", 58.0, false),
            arrayOf("plain yuan amount without currency prefix", "扣款638元，最高回饋5%", 638.0, true),
            arrayOf("actual expense before spending threshold", "消費NT$581，刷卡滿NT$4,800贈NT$360", 581.0, true),
            arrayOf("template must not select reward instead of payment", "在 優惠雜貨舖 支付NT$577。回饋NT$80，商店:示範商店", 577.0, true),
            arrayOf("card and date cannot turn thresholds into a purchase", "刷卡通知 末四碼2468 2025/04/09 14:25，消費滿NT$4,800贈最高NT$360", null, true),
            arrayOf("completed wording in prize advertisement", "交易成功就送NT$800禮券", null, true),
            arrayOf("conditional payment amount is not an actual payment", "刷卡NT$900就送NT$90回饋", null, true),
            arrayOf("monthly spending summary is not one purchase", "您上個月累積一般消費NT$2,718，最高回饋5%", null, true),
            arrayOf("summary without advertising words", "本月一般消費NT$2,718", null, true),
            arrayOf("percentage alone is not a monetary amount", "消費扣款最高3.5%回饋", null, true),
            arrayOf("spending minimum without other promotion words", "刷卡滿NT$4,800", null, true)
        )
    }

    @Test
    fun identifiesActualAmountWithoutRequiringMerchantOrCard() {
        val parsed = NotificationParser().parse(text, "com.sinopac.DaCard")
        if (expectedAmount == null) {
            assertNull(description, parsed)
            assertFalse(description, NotificationAmountExtractor.hasTransactionAmount(text))
            assertFalse(description, NotificationCapturePolicy.shouldKeepUnparsed(text))
        } else {
            assertNotNull(description, parsed)
            assertEquals(description, expectedAmount, parsed!!.amount, 0.001)
            assertEquals(description, expectedIsExpense, parsed.isExpense)
            assertEquals(description, expectedAmount, NotificationAmountExtractor.bestAmount(text)!!, 0.001)
            assertFalse(description, NotificationCapturePolicy.shouldIgnore(text))
        }
    }
}
