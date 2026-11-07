package com.rton.expensebucket

import com.google.gson.JsonParser
import com.rton.expensebucket.service.HyperOsIslandPayload
import com.rton.expensebucket.service.CaptureNotificationContract
import org.junit.Assert.*
import org.junit.Test

class HyperOsIslandPayloadTest {
    @Test
    fun placesAmountOnLeftAndBookingLabelOnRight() {
        val params = JsonParser.parseString(HyperOsIslandPayload.build("$1,234.50", "消費", "測試商店"))
            .asJsonObject.getAsJsonObject("param_v2")
        val island = params.getAsJsonObject("param_island")
        val area = island.getAsJsonObject("bigIslandArea")
        assertEquals("$1,234.50", area.getAsJsonObject("imageTextInfoLeft").getAsJsonObject("textInfo")["title"].asString)
        val actionArea = area.getAsJsonObject("imageTextInfoRight")
        assertEquals("記帳", actionArea.getAsJsonObject("textInfo")["title"].asString)
        assertEquals(HyperOsIslandPayload.EDIT_ICON, actionArea.getAsJsonObject("picInfo")["pic"].asString)
        assertEquals(HyperOsIslandPayload.ISLAND_TIMEOUT_SECONDS, island["islandTimeout"].asInt)
        assertFalse(params["filterWhenNoPermission"].asBoolean)
        assertEquals(HyperOsIslandPayload.OPEN_ACTION, params.getAsJsonObject("hintInfo").getAsJsonObject("actionInfo")["action"].asString)
    }

    @Test
    fun escapesMerchantTextAndSupportsIncome() {
        val merchant = "測試\"商店\n分店"
        val params = JsonParser.parseString(HyperOsIslandPayload.build("$50", "收入", merchant))
            .asJsonObject.getAsJsonObject("param_v2")
        assertEquals(merchant, params.getAsJsonObject("baseInfo")["content"].asString)
        assertEquals("收入 $50", params.getAsJsonObject("baseInfo")["title"].asString)
    }

    @Test
    fun acceptsOnlyValidTransactionRoutes() {
        assertEquals(42L, CaptureNotificationContract.transactionId("edit_transaction/42"))
        listOf(null, "drafts", "edit_transaction/0", "edit_transaction/-1", "edit_transaction/42/extra", "edit_transaction/9999999999999999999999999")
            .forEach { assertNull(CaptureNotificationContract.transactionId(it)) }
    }
}
