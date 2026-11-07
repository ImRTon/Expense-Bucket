package com.rton.expensebucket.ocr

/** Classifies monetary fields by their nearby wording, without requiring a merchant or card. */
object NotificationAmountExtractor {
    private const val NUMBER = """\d+(?:,\d{3})*(?:\.\d+)?"""
    private const val CURRENCY = """(?:NTD|TWD|NT\$?|N\.?T\.?\$?|(?:新)?[台臺]幣|[$＄])"""
    private val money = Regex(
        """(?:$CURRENCY\s*\$?\s*($NUMBER)([萬千百])?(?:\s*元)?|($NUMBER)([萬千百])?\s*元)(?![\d%％萬千百]|[.,]\d)""",
        RegexOption.IGNORE_CASE
    )
    private val clauseBoundary = Regex("""[，,](?!\d)|(?<!\d)[，,]|[。；;！!？?\n]""")
    private val actionBeforeAmount = Regex(
        """(?:刷卡(?:消費)?|消費|付款|支付|扣款|交易|入帳|轉入|收到|退款|請款|授權|實付)(?:成功|完成|通知|了)?\s*(?:金額)?\s*(?:為|共|約)?\s*[:：]?\s*$"""
    )
    private val actionAfterAmount = Regex(
        """^\s*(?:已|業已|已經)?(?:付款|支付|扣款|入帳|轉入|退款|消費|刷卡|授權)(?:成功|完成)?"""
    )
    private val creditedAfterAmount = Regex("""^\s*(?:已|業已|已經)(?:入帳|轉入|退款)""")
    private val walletPayment = Regex("""^\s*\(?使用\s+.+?[•●*]{2,}\s*\d{4}\s*付款""")
    private val nonTransactionPrefix = Regex(
        """(?:滿|達到|至少|最低|每滿|最高(?:可享|享|回饋|贈|送)?|回饋(?:上限)?|紅利|點數|禮券|折扣|折抵|優惠券?|餘額|可用額度|應繳|最低應繳|贈|送|抽)\s*(?:金額)?\s*(?:為|達|共|約)?\s*[:：]?\s*$"""
    )
    private val summaryPrefix = Regex(
        """(?:上個月|上月|本月|每月|累積|累計|總計).{0,32}(?:消費|刷卡|付款|支付|扣款)(?:金額)?\s*[:：]?\s*$"""
    )
    // A bare 「享回饋」 can be a footer on a real purchase. Require conditional
    // wording such as 「即可／就送」 before treating the payment amount as a target.
    private val conditionalSuffix = Regex("""^\s*(?:以上|以下|起|即可|就(?:能|送|贈|抽|享)|可享)""")
    private val completedBeforeAmount = Regex("""(?:已(?:經)?(?:刷卡|消費|付款|支付|扣款)|成功|完成)""")
    private val convertedPrefix = Regex("""約當\s*$""")
    private val amountLabel = Regex("""(?:金額|實付)\s*[:：]?\s*$""")

    private data class AmountEvidence(val amount: Double, val isTransaction: Boolean, val priority: Int)

    fun bestAmount(text: String): Double? = amounts(text).maxByOrNull { it.priority }?.amount

    fun hasTransactionAmount(text: String): Boolean = amounts(text).any { it.isTransaction }

    fun hasOnlyNonTransactionAmounts(text: String): Boolean =
        money.containsMatchIn(text) && amounts(text).isEmpty()

    private fun amounts(text: String): List<AmountEvidence> = money.findAll(text).mapNotNull { match ->
        val number = match.groupValues[1].ifBlank { match.groupValues[3] }
        val unit = match.groupValues[2].ifBlank { match.groupValues[4] }
        val multiplier = when (unit) {
            "萬" -> 10_000
            "千" -> 1_000
            "百" -> 100
            else -> 1
        }
        val amount = (number.replace(",", "").toDoubleOrNull() ?: return@mapNotNull null) * multiplier
        if (amount !in 1.0..10_000_000.0) return@mapNotNull null

        val preceding = text.substring((match.range.first - 80).coerceAtLeast(0), match.range.first)
        val boundary = clauseBoundary.findAll(preceding).lastOrNull()?.range?.last?.plus(1) ?: 0
        val prefix = preceding.substring(boundary)
        val suffix = text.substring(match.range.last + 1).take(100)

        // These amounts describe a target, a reward, a balance, or a monthly summary.
        // An actual credited reward (「回饋...元已入帳」) remains a valid income.
        if (summaryPrefix.containsMatchIn(prefix) ||
            (nonTransactionPrefix.containsMatchIn(prefix) && !creditedAfterAmount.containsMatchIn(suffix)) ||
            (conditionalSuffix.containsMatchIn(suffix) && !completedBeforeAmount.containsMatchIn(prefix))) {
            return@mapNotNull null
        }

        val isTransaction = actionBeforeAmount.containsMatchIn(prefix) ||
            actionAfterAmount.containsMatchIn(suffix) || walletPayment.containsMatchIn(suffix)
        val priority = when {
            convertedPrefix.containsMatchIn(prefix) -> 120 // Bank's TWD equivalent of a foreign purchase.
            isTransaction -> 100
            amountLabel.containsMatchIn(prefix) -> 85
            else -> 65
        }
        AmountEvidence(amount, isTransaction, priority)
    }.toList()
}
