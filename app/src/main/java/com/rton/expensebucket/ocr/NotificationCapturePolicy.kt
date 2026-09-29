package com.rton.expensebucket.ocr

/** Keeps chat messages and marketing copy out of the notification capture flow. */
object NotificationCapturePolicy {
    private val linePayMarker = Regex("""LINE\s*Pay""", RegexOption.IGNORE_CASE)
    private val amountMarker = Regex("""(?:(?:NTD|TWD|NT\$|[新]?[台臺]幣|[$＄])\s*\d[\d,]*|\d[\d,]*\s*元)""")
    private val transactionMarker = Regex("""(刷卡|消費|付款|支付|扣款|交易|入帳|轉入|收到|退款|請款|授權)""")
    private val completedMarker = Regex("""(刷卡通知|消費通知|刷卡消費|付款成功|支付成功|交易成功|交易完成|扣款成功|已授權|入帳通知|轉入成功|退款成功)""")
    private val promotionMarker = Regex("""(優惠|優惠券|折扣|限時|活動|抽獎|領券|滿額|滿千|滿百|最高回饋|加碼|立即申辦|立即參加|點我|指定通路)""")

    fun shouldInspect(packageName: String, text: String): Boolean =
        packageName != "jp.naver.line.android" || linePayMarker.containsMatchIn(text)

    fun isLikelyPromotion(text: String): Boolean =
        promotionMarker.containsMatchIn(text) && !completedMarker.containsMatchIn(text)

    fun shouldKeepUnparsed(text: String): Boolean =
        !isLikelyPromotion(text) && transactionMarker.containsMatchIn(text) && amountMarker.containsMatchIn(text)
}
