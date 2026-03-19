package com.rton.expensebucket.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a payment method (cash, credit card, e-wallet, etc.)
 */
enum class BillingCycleType(val value: String) {
    NONE("none"),
    MONTHLY_CLOSING_DAY("monthly_closing_day");

    companion object {
        fun fromValue(value: String?): BillingCycleType =
            entries.firstOrNull { it.value == value } ?: NONE
    }
}

enum class BillingLimitType(val value: String) {
    CREDIT("credit"),
    PROMO("promo");

    companion object {
        fun fromValue(value: String?): BillingLimitType =
            entries.firstOrNull { it.value == value } ?: CREDIT
    }
}

@Entity(tableName = "payment_methods")
data class PaymentMethod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                // e.g. "現金", "Richart JCB"
    val icon: String,                // icon key from PaymentIconMapper
    val color: Long = 0xFF6B7280,   // ARGB default gray
    val type: String = "cash",       // "cash", "credit", "epay", "other"
    val isDefault: Boolean = false,  // only one should be default
    val parentId: Long? = null,      // null = parent, non-null = child of that parent
    val sortOrder: Int = 0,
    val billingCycleType: String = BillingCycleType.NONE.value,
    val billingCycleDay: Int? = null,
    val billingLimitType: String = BillingLimitType.CREDIT.value,
    val billingLimitAmount: Double? = null
)

/**
 * Represents an expense/income category.
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String, // Material icon name, e.g. "Restaurant", "DirectionsBus"
    val color: Long, // ARGB color value
    val isExpense: Boolean = true, // true = expense, false = income
    val parentId: Long? = null, // null = parent category, non-null = child of that parent
    val sortOrder: Int = 0
)

/**
 * Represents a travel project for grouping expenses.
 */
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val defaultCurrency: String = "TWD",
    val startDate: Long? = null,       // epoch millis, null = 不限
    val endDate: Long? = null,         // epoch millis, null = 不限
    val budget: Double? = null,        // 預算金額, null = 不設預算
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Represents a single expense or income transaction.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PaymentMethod::class,
            parentColumns = ["id"],
            childColumns = ["paymentMethodId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["projectId"]),
        Index(value = ["paymentMethodId"]),
        Index(value = ["date"])
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val note: String = "",
    val categoryId: Long? = null,
    val projectId: Long? = null,
    val paymentMethodId: Long? = null,
    val date: Long = System.currentTimeMillis(), // epoch millis
    val currency: String = "TWD",
    val exchangeRate: Double = 1.0, // rate to convert to default currency
    val isExpense: Boolean = true,
    val source: String = "manual", // "manual", "ocr", "notification"
    val isDraft: Boolean = false, // true for auto-captured pending confirmation
    val createdAt: Long = System.currentTimeMillis()
)
