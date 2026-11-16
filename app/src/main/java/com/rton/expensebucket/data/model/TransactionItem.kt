package com.rton.expensebucket.data.model

import androidx.room.TypeConverter
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal

/** Additional receipt details, independent of the transaction's accounting amount. */
data class TransactionItem(val name: String, val amount: Double)

object TransactionItemLogic {
    fun create(name: String, amountText: String): TransactionItem? {
        val amount = amountText.toDoubleOrNull() ?: return null
        if (name.isBlank() || !amount.isFinite() || amount < 0) return null
        return TransactionItem(name.trim(), amount)
    }

    fun total(items: List<TransactionItem>): Double = items.fold(BigDecimal.ZERO) { sum, item ->
        sum + BigDecimal.valueOf(item.amount)
    }.toDouble()
}

/** Explicit JSON keys also keep CSV backups stable in minified release builds. */
class TransactionItemConverter {
    @TypeConverter
    fun encode(items: List<TransactionItem>): String = JsonArray().apply {
        items.forEach { item ->
            add(JsonObject().apply {
                addProperty("name", item.name)
                addProperty("amount", item.amount)
            })
        }
    }.toString()

    @TypeConverter
    fun decode(json: String): List<TransactionItem> {
        if (json.isBlank()) return emptyList() // CSV backups predating item details.
        return JsonParser.parseString(json).asJsonArray.map { element ->
            val item = element.asJsonObject
            requireNotNull(TransactionItemLogic.create(item.get("name").asString, item.get("amount").asString)) {
                "品項名稱或金額無效"
            }
        }
    }
}
