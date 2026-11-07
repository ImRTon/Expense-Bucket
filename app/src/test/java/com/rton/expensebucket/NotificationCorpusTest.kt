package com.rton.expensebucket

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.rton.expensebucket.ocr.NotificationCapturePolicy
import com.rton.expensebucket.ocr.NotificationParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NotificationCorpusTest(private val caseId: String, private val case: JsonObject) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): Collection<Array<Any>> {
            val stream = requireNotNull(NotificationCorpusTest::class.java.getResourceAsStream(
                "/notifications/device-notifications.json"
            ))
            return stream.bufferedReader(Charsets.UTF_8).use { reader ->
                JsonParser.parseReader(reader).asJsonArray.map { element ->
                    val case = element.asJsonObject
                    arrayOf<Any>(case.get("id").asString, case)
                }
            }
        }
    }

    @Test
    fun replaysAnonymizedNotification() {
        val text = case.get("text").asString
        val packageName = case.get("packageName")?.asString
        val parsed = NotificationParser().parse(text, packageName)
        if (case.get("expectedAmount").isJsonNull) {
            assertNull(caseId, parsed)
            assertFalse(caseId, NotificationCapturePolicy.shouldKeepUnparsed(text))
        } else {
            assertNotNull(caseId, parsed)
            assertEquals(caseId, case.get("expectedAmount").asDouble, parsed!!.amount, 0.001)
            assertEquals(caseId, case.get("expectedIsExpense").asBoolean, parsed.isExpense)
            case.get("expectedMerchant")?.let { assertEquals(caseId, it.asString, parsed.merchant) }
            assertFalse(caseId, NotificationCapturePolicy.shouldIgnore(text))
        }
    }
}
