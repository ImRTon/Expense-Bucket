package com.rton.expensebucket

import com.rton.expensebucket.ocr.NotificationParser
import com.rton.expensebucket.ocr.NotificationCapturePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationParserTest {

    private val parser = NotificationParser()

    @Test
    fun rejectsSettlementBillingAndCampaignNotifications() {
        val texts = listOf(
            "交割款通知 2026/11/06 21:44:41 您於測試分公司的證券交割戶，將於11/10早上10:00前從交割戶扣款12,345元，請確保銀行餘額足夠。",
            "交割款提醒 您於測試分公司的證券交割戶，將於11/05收到款項12,345元。",
            "【週年慶贈最高21%】到指定分店，刷台新信用卡，消費滿5,000元贈最高1,050元等值回饋！",
            "感謝您!台新銀行已於115年10月05日 收到信用卡帳單繳款金額:NT$12,345",
            "消費扣款最高2.5%🔥 好評再延長！只要每月任7個營業日帳戶餘額存放新臺幣5萬元以上，次月享消費扣款最高2.5%回饋",
            "消費通知 週年慶贈最高1,050元，刷卡滿5,000元享回饋",
            "您上個月SPORT卡運動累積已達標且一般消費1234元，設定帳戶自動扣繳信用卡帳款，即可享有一般消費最高2%豐點回饋",
            "提醒您已收到信用卡帳單，本期應繳NT$12,345",
            "本行已收到您信用卡 2026/10/02 台幣繳款 12,345 元",
            "刷卡失敗，消費NT$500，未完成授權"
        )
        texts.forEach { text ->
            assertNull(text, parser.parse(text, "com.sinopac.DaCard"))
            assertFalse(text, NotificationCapturePolicy.shouldKeepUnparsed(text))
        }
    }

    @Test
    fun parsesWalletMerchantWithoutPaymentCardText() {
        listOf("••", "**").forEach { mask ->
            val parsed = parser.parse(
                "Sample Cafe B $413.00 (使用 示範現金回饋信用卡 ${mask}2468 付款)",
                "com.google.android.apps.walletnfcrel"
            )
            assertNotNull(parsed)
            assertEquals(413.0, parsed!!.amount, 0.001)
            assertEquals("Sample Cafe B", parsed.merchant)
            assertEquals("googlepay", parsed.paymentMethodHint)
            assertTrue(parsed.isExpense)
        }
    }

    @Test
    fun keepsActualCardPurchaseWithBillingDisclaimerAndPromotionFooter() {
        assertNotNull(parser.parse(
            "永豐信用卡末四碼1234刷卡通知1150321_17:59金額台幣240元，商店名稱:TEST，資訊以帳單為準，指定通路最高回饋2%",
            "com.sinopac.DaCard"
        ))
        assertNotNull(parser.parse("帳戶入帳通知 金額TWD$2,000元，自他行匯入您的新臺幣帳戶。2026/11/06 10:50:02"))
        assertNotNull(parser.parse("退款成功 NT$500"))
    }

    @Test
    fun rejectsPromotionalAmountWithoutCompletedTransaction() {
        assertNull(parser.parse(
            "限時優惠！信用卡消費滿 NT$1,000 最高回饋 100 元，立即參加活動",
            "com.esunbank"
        ))
        assertFalse(NotificationCapturePolicy.shouldKeepUnparsed(
            "刷卡滿 1,000 元享優惠，立即參加活動"
        ))
    }

    @Test
    fun keepsCompletedCardNotificationWithPointsOffer() {
        assertNotNull(parser.parse(
            "【刷卡通知】金額NT$1340元卡號末四碼1234於2026/03/08 20:52在商店名稱AAAA刷卡。立即以點數折抵",
            "com.cathaybk.mymobibank"
        ))
    }

    @Test
    fun lineNotificationsRequireLinePayMarker() {
        assertFalse(NotificationCapturePolicy.shouldInspect("jp.naver.line.android", "朋友：明天消費 500 元"))
        assertTrue(NotificationCapturePolicy.shouldInspect("jp.naver.line.android", "LINE Pay消費通知 在 全聯 支付 $356"))
    }

    @Test
    fun unparsedInboxKeepsOnlyPlausiblePayments() {
        assertTrue(NotificationCapturePolicy.shouldKeepUnparsed("您的信用卡於 09/29 授權 NT$560"))
        assertFalse(NotificationCapturePolicy.shouldKeepUnparsed("本月帳單繳款截止日提醒"))
    }

    @Test
    fun parsesSinopacLegacyNotification() {
        val parsed = parser.parse(
            text = "永豐貴賓您好，末四碼1234感謝03/15 12:05刷卡台幣123元，商店名稱:STORE_NAME，實際商店名稱請以店家收據為準",
            packageName = "com.sinopac.DaCard"
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(123.0, parsed.amount, 0.001)
        assertEquals("STORE_NAME", parsed.merchant)
        assertEquals("sinopac", parsed.paymentMethodHint)

        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.date!! }
        assertEquals(3, calendar.get(Calendar.MONTH) + 1)
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun parsesSinopacForeignCurrencyNotificationUsingApproxTwd() {
        val parsed = parser.parse(
            text = "永豐信用卡末四碼1234刷卡通知1150321_17:59金額日圓JPY$1,181.00，約當台幣$ 240元，商店名稱:XXXX，實際商店名稱ACTUAL_STORE",
            packageName = "com.sinopac.DaCard"
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(240.0, parsed.amount, 0.001)
        assertEquals("XXXX", parsed.merchant)
        assertEquals("sinopac", parsed.paymentMethodHint)

        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.date!! }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(3, calendar.get(Calendar.MONTH) + 1)
        assertEquals(21, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(17, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun parsesRichartNotificationWithTraditionalTaiCharacter() {
        val parsed = parser.parse(
            text = "【信用卡消費通知】您的Richart卡(末三碼123)於01/30-18:06網路刷卡約新臺幣3,019元，實際消費XXXX",
            packageName = "com.taishinbank.richart"
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(3019.0, parsed.amount, 0.001)
        assertEquals("richart", parsed.paymentMethodHint)

        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.date!! }
        assertEquals(1, calendar.get(Calendar.MONTH) + 1)
        assertEquals(30, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(18, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(6, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun parsesCreditCardNotificationsAcrossAllTwdVariants() {
        val variants = listOf("台幣", "臺幣", "新臺幣", "新台幣")

        variants.forEach { currency ->
            val richart = parser.parse(
                text = "【信用卡消費通知】您的Richart卡(末三碼123)於01/30-18:06網路刷卡約${currency}3,019元，實際消費XXXX",
                packageName = "com.taishinbank.richart"
            )
            assertNotNull("Richart should parse $currency", richart)
            assertEquals(3019.0, richart!!.amount, 0.001)

            val genericCard = parser.parse(
                text = "消費通知 卡號末四碼1234 於 星巴克 消費 ${currency}165",
                packageName = "com.ctbcbank.mobile"
            )
            assertNotNull("Generic credit card should parse $currency", genericCard)
            assertEquals("星巴克", genericCard!!.merchant)

            val bankSms = parser.parse(
                text = "台新銀行通知：刷卡消費${currency}1,234元 商店：7-ELEVEN",
                packageName = "com.taishinbank.richart"
            )
            assertNotNull("Bank SMS should parse $currency", bankSms)
            assertEquals(1234.0, bankSms!!.amount, 0.001)

            val cathay = parser.parse(
                text = "【刷卡通知】金額${currency}1340元卡號末四碼1234於2026/03/08 20:52在商店名稱AAAA刷卡。立即以點數折抵",
                packageName = "com.cathaybk.mymobibank"
            )
            assertNotNull("Cathay should parse $currency", cathay)
            assertEquals("AAAA", cathay!!.merchant)

            val fubon = parser.parse(
                text = "【刷卡消費通知】您的信用卡末四碼1234於02/23 12:37:26商店名稱消費${currency}1,234元",
                packageName = "com.fubon"
            )
            assertNotNull("Fubon should parse $currency", fubon)
            assertEquals(1234.0, fubon!!.amount, 0.001)

            val sinopacLegacy = parser.parse(
                text = "永豐貴賓您好，末四碼1234感謝03/15 12:05刷卡${currency}123元，商店名稱:STORE_NAME，實際商店名稱請以店家收據為準",
                packageName = "com.sinopac.DaCard"
            )
            assertNotNull("Sinopac legacy should parse $currency", sinopacLegacy)
            assertEquals("STORE_NAME", sinopacLegacy!!.merchant)

            val sinopacNew = parser.parse(
                text = "永豐信用卡末四碼1234刷卡通知1150321_17:59金額日圓JPY$1,181.00，約當${currency}$ 240元，商店名稱:XXXX，實際商店名稱ACTUAL_STORE",
                packageName = "com.sinopac.DaCard"
            )
            assertNotNull("Sinopac foreign currency should parse $currency", sinopacNew)
            assertEquals(240.0, sinopacNew!!.amount, 0.001)

            val post = parser.parse(
                text = "您的郵政VISA金融卡於114/01/13 21:22:05消費${currency}8,521元(國外交易TEST)，如有疑慮請致電客服",
                packageName = "tw.post.mobile"
            )
            assertNotNull("Post card should parse $currency", post)
            assertEquals(8521.0, post!!.amount, 0.001)
        }
    }

    @Test
    fun parsesRealWorldFubonNotification() {
        val parsed = parser.parse(
            text = "【刷卡消費通知】您的信用卡末四碼2468於04/09 14:25:36示範量販甲店消費臺幣1,642元",
            packageName = "com.fubon.mobilebank"
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(1642.0, parsed.amount, 0.001)
        assertEquals("示範量販甲店", parsed.merchant)
        assertEquals("credit_card", parsed.paymentMethodHint)

        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.date!! }
        assertEquals(4, calendar.get(Calendar.MONTH) + 1)
        assertEquals(9, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(25, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun parsesCreditCardNotificationWhenBankCopyChangesOrder() {
        val parsed = parser.parse(
            text = "刷卡通知：卡號末四碼2468 04/09 14:25:36 示範量販甲店 臺幣1,642 已授權",
            packageName = "com.fubon.mobilebank"
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(1642.0, parsed.amount, 0.001)
        assertEquals("示範量販甲店", parsed.merchant)
        assertEquals("credit_card", parsed.paymentMethodHint)

        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.date!! }
        assertEquals(4, calendar.get(Calendar.MONTH) + 1)
        assertEquals(9, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(25, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun parsesWalletNotificationByFieldsInsteadOfFullSentenceTemplate() {
        val parsed = parser.parse(
            text = "全支付交易通知：付款成功 金額$356 商店 全聯福利中心",
            packageName = "com.pxpayplus.app"
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(356.0, parsed.amount, 0.001)
        assertEquals("全聯福利中心", parsed.merchant)
    }

    @Test
    fun parsesTruncatedAndFullCombinedNotificationCorrectly() {
        // Simulate a scenario where extraction combines a truncated EXTRA_TEXT and a full EXTRA_BIG_TEXT
        // Truncated version matched generic credit card pattern #10, full version matched #8. 
        // The parser should select the higher confidence one (the one with date and merchant)
        val combinedText = "永豐大咖 末四碼1234感謝刷卡123元 永豐貴賓您好，末四碼1234感謝03/15 12:05刷卡台幣123元，商店名稱:STORE，實際商店名稱請以店家收據為準"
        
        val parsed = parser.parse(
            text = combinedText,
            packageName = "com.sinopac.DaCard"
        )

        assertNotNull("Should correctly parse combined notification text", parsed)
        parsed!!
        assertEquals(123.0, parsed.amount, 0.001)
        assertEquals("STORE", parsed.merchant)
        assertEquals("sinopac", parsed.paymentMethodHint)

        // Verifying it successfully fell back to Pattern #8 and not general generic patterns
        assertNotNull(parsed.date)
    }
}
