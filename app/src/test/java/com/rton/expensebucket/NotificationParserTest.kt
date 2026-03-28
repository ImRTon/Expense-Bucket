package com.rton.expensebucket

import com.rton.expensebucket.ocr.NotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Calendar

class NotificationParserTest {

    private val parser = NotificationParser()

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
            text = "【刷卡消費通知】您的信用卡末四碼6773於03/28 13:09:10好市多中和店消費臺幣1,257元",
            packageName = "com.fubon.mobilebank"
        )

        assertNotNull(parsed)
        parsed!!
        assertEquals(1257.0, parsed.amount, 0.001)
        assertEquals("好市多中和店", parsed.merchant)
        assertEquals("credit_card", parsed.paymentMethodHint)

        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.date!! }
        assertEquals(3, calendar.get(Calendar.MONTH) + 1)
        assertEquals(28, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(13, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(9, calendar.get(Calendar.MINUTE))
    }
}
