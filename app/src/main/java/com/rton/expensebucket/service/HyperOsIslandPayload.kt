package com.rton.expensebucket.service

import com.google.gson.Gson

/** Xiaomi OS3 template data. Compact-island tap gestures are owned by SystemUI. */
object HyperOsIslandPayload {
    const val OPEN_ACTION = "miui.focus.action_expense_edit"
    const val EDIT_ICON = "miui.focus.pic_expense_edit"
    const val ACTION_COLOR = "#B7F34A"
    const val ISLAND_TIMEOUT_SECONDS = 120

    data class Labels(
        val record: String = "記帳",
        val pendingType: (String) -> String = { "待確認$it" },
        val pendingRecord: (String, String) -> String = { type, amount -> "$type $amount · 待記帳" },
        val tapToEdit: String = "點擊後可編輯",
        val pending: String = "待確認"
    )

    fun build(amountText: String, typeText: String, merchant: String, labels: Labels = Labels()): String {
        val openAction = mapOf(
            "action" to OPEN_ACTION,
            "actionTitle" to labels.record,
            "actionIcon" to EDIT_ICON,
            "actionBgColor" to ACTION_COLOR,
            "actionBgColorDark" to ACTION_COLOR,
            "actionTitleColor" to "#172104",
            "actionTitleColorDark" to "#172104",
            "clickWithCollapse" to true
        )
        val details = merchant.ifBlank { labels.pendingType(typeText) }.take(80)
        val parameters = mapOf(
            "protocol" to 1,
            "business" to "expense_capture",
            "islandFirstFloat" to false,
            "enableFloat" to false,
            "updatable" to false,
            "filterWhenNoPermission" to false,
            "ticker" to "$typeText $amountText",
            "aodTitle" to labels.pendingRecord(typeText, amountText),
            "param_island" to mapOf(
                "islandProperty" to 2,
                "highlightColor" to ACTION_COLOR,
                "islandTimeout" to ISLAND_TIMEOUT_SECONDS,
                "bigIslandArea" to mapOf(
                    "imageTextInfoLeft" to mapOf(
                        "type" to 1,
                        "textInfo" to mapOf("title" to amountText, "narrowFont" to true)
                    ),
                    "imageTextInfoRight" to mapOf(
                        "type" to 2,
                        "picInfo" to mapOf("type" to 1, "pic" to EDIT_ICON),
                        "textInfo" to mapOf("title" to labels.record, "showHighlightColor" to true)
                    )
                ),
                "smallIslandArea" to mapOf("picInfo" to mapOf("type" to 1, "pic" to EDIT_ICON))
            ),
            "baseInfo" to mapOf("type" to 1, "title" to "$typeText $amountText", "content" to details),
            "picInfo" to mapOf("type" to 1),
            "hintInfo" to mapOf(
                "type" to 1,
                "title" to labels.tapToEdit,
                "content" to labels.pending,
                "actionInfo" to openAction
            )
        )
        return Gson().toJson(mapOf("param_v2" to parameters))
    }
}
