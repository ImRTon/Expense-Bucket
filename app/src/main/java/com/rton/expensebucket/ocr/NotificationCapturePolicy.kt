package com.rton.expensebucket.ocr

/** Keeps chat messages and marketing copy out of the notification capture flow. */
object NotificationCapturePolicy {
    private val linePayMarker = Regex("""LINE\s*Pay""", RegexOption.IGNORE_CASE)
    private val amountMarker = Regex("""(?:(?:NTD|TWD|NT\$|[新]?[台臺]幣|[$＄])\s*\d[\d,]*|\d[\d,]*\s*元)""")
    private val transactionMarker = Regex("""(刷卡|消費|付款|支付|扣款|交易|入帳|轉入|收到|退款|請款|授權)""")
    private val completedMarker = Regex("""(付款成功|支付成功|交易成功|交易完成|扣款成功|已授權|轉入成功|退款成功)""")
    private val promotionMarker = Regex("""(優惠|折扣|限時|活動|抽獎|領券|滿額|滿千|滿百|最高|加碼|立即申辦|立即參加|點我|指定通路|指定分店|週年慶|周年慶|回饋|禮券|(?:消費|刷卡|付款|支付|扣款)滿|累積已達標|帳單分期|消費分期)""")
    private val settlementMarker = Regex("""(交割款|交割戶|證券交割)""")
    private val billingMarker = Regex("""((?:信用卡|電子|本期|本月)帳單(?:繳款|通知|已|寄|送|出|金額)|收到.{0,60}(?:帳單|帳款|信用卡.{0,40}繳款)|(?:帳單|帳款).{0,16}(?:繳款金額|應繳|最低應繳|繳款截止|繳款提醒|扣繳成功)|(?:代繳|扣繳).{0,12}(?:信用卡|卡費)|(?:信用卡|卡費).{0,12}(?:繳款成功|繳款收訖|扣繳成功))""")
    private val unsuccessfulMarker = Regex("""((?:交易|刷卡|付款|支付|扣款|授權)(?:失敗|未完成|遭拒|被拒)|(?:將於|預計於|預定於).{0,40}(?:扣款|付款|收到款項))""")
    private val cardMarker = Regex("""(?:末[三四]碼|卡號末|[•●*]{2,})\s*\d{3,4}""")
    private val dateMarker = Regex("""(?:\d{1,4}[/-])?\d{1,2}[/-]\d{1,2}[\s-]\d{1,2}:\d{2}|\d{7,8}_\d{1,2}:\d{2}""")
    private val merchantMarker = Regex("""(?:商店名稱|特約商店|商店|店家)[：:\s]+\S+|[於在]\s*\S+\s*(?:付款|支付|消費|刷卡)""")

    fun shouldInspect(packageName: String, text: String): Boolean =
        packageName != "jp.naver.line.android" || linePayMarker.containsMatchIn(text)

    fun isLikelyPromotion(text: String): Boolean {
        // A title such as 「消費通知」 is not proof of an individual purchase.
        val hasIndividualTransaction = NotificationAmountExtractor.hasTransactionAmount(text) ||
            (NotificationAmountExtractor.bestAmount(text) != null &&
                (completedMarker.containsMatchIn(text) ||
                    (cardMarker.containsMatchIn(text) && dateMarker.containsMatchIn(text)) ||
                    (merchantMarker.containsMatchIn(text) && dateMarker.containsMatchIn(text))))
        return (promotionMarker.containsMatchIn(text) ||
            NotificationAmountExtractor.hasOnlyNonTransactionAmounts(text)) && !hasIndividualTransaction
    }

    fun shouldIgnore(text: String): Boolean =
        settlementMarker.containsMatchIn(text) || billingMarker.containsMatchIn(text) ||
            unsuccessfulMarker.containsMatchIn(text) || isLikelyPromotion(text)

    fun shouldKeepUnparsed(text: String): Boolean =
        !shouldIgnore(text) && transactionMarker.containsMatchIn(text) && amountMarker.containsMatchIn(text)
}
