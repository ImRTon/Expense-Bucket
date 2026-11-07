package com.rton.expensebucket.ocr

import com.rton.expensebucket.data.model.PaymentMethod
import java.text.Normalizer
import java.util.Locale

object NotificationPaymentMethodMatcher {
    fun findPaymentMethodId(text: String, paymentMethods: List<PaymentMethod>): Long? {
        val cardName = Regex("""使用\s+(.+?)\s*[•●*]{2,}\s*\d{4}\s*付款""")
            .find(text)?.groupValues?.get(1) ?: return null
        val normalizedName = normalizeCardName(cardName)
        if (normalizedName.isBlank()) return null
        return paymentMethods.filter {
            normalizeCardName(it.name) == normalizedName
        }.singleOrNull()?.id
    }

    private fun normalizeCardName(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace("信用卡", "").replace("卡", "")
            .replace(Regex("""[^\p{L}\p{N}]"""), "")
            .replace(Regex("""sports$"""), "sport")
}
