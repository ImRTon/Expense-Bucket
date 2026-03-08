package com.rton.expensebucket.data

import com.rton.expensebucket.data.model.PaymentMethod

/**
 * Default payment methods seeded on first launch.
 *
 * Icon keys map to PaymentIconMapper. Types: "cash", "credit", "epay", "other"
 * To add new methods: add an entry here and a matching icon key in PaymentIconMapper.
 */
object DefaultPaymentMethods {

    data class PaymentMethodSeed(
        val name: String,
        val icon: String,
        val color: Long,
        val type: String,
        val isDefault: Boolean = false,
        val sortOrder: Int = 0,
        val parentName: String? = null // null = parent
    )

    fun getSeeds(): List<PaymentMethodSeed> = listOf(
        // ─── Parents ──────────────────────────────────────────────
        PaymentMethodSeed("現金", "Payments", 0xFF4CAF50, "cash", isDefault = true, sortOrder = 0),
        PaymentMethodSeed("信用卡", "CreditCard", 0xFF2196F3, "credit", sortOrder = 1),
        PaymentMethodSeed("電子支付", "PhoneIphone", 0xFFFF9800, "epay", sortOrder = 2),
        PaymentMethodSeed("其他", "Toll", 0xFF9E9E9E, "other", sortOrder = 3),

        // ─── Children ─────────────────────────────────────────────
        // Cash (If we want a default child for Cash, but usually standard money isn't subcategorized default, left empty for now)

        // Credit Cards
        PaymentMethodSeed("Visa", "visa", 0xFF1A1F71, "credit", sortOrder = 10, parentName = "信用卡"),
        PaymentMethodSeed("Mastercard", "mastercard", 0xFFEB001B, "credit", sortOrder = 11, parentName = "信用卡"),
        PaymentMethodSeed("JCB", "jcb", 0xFF0B4EA2, "credit", sortOrder = 12, parentName = "信用卡"),

        // E-Payment
        PaymentMethodSeed("街口支付", "jko", 0xFFE60912, "epay", sortOrder = 20, parentName = "電子支付"),
        PaymentMethodSeed("LINE Pay", "linepay", 0xFF07B53B, "epay", sortOrder = 21, parentName = "電子支付"),
        PaymentMethodSeed("悠遊付", "easycard", 0xFF3B82F6, "epay", sortOrder = 22, parentName = "電子支付"),
        PaymentMethodSeed("Apple Pay", "applepay", 0xFF000000, "epay", sortOrder = 23, parentName = "電子支付"),
        PaymentMethodSeed("Google Pay", "googlepay", 0xFF4285F4, "epay", sortOrder = 24, parentName = "電子支付"),
        PaymentMethodSeed("Samsung Pay", "samsungpay", 0xFF1428A0, "epay", sortOrder = 25, parentName = "電子支付"),
        PaymentMethodSeed("PayPal", "paypal", 0xFF00457C, "epay", sortOrder = 26, parentName = "電子支付"),
        PaymentMethodSeed("Pi 拍錢包", "pi", 0xFFEC4899, "epay", sortOrder = 27, parentName = "電子支付"),
        PaymentMethodSeed("全支付", "pay_full", 0xFFFFD100, "epay", sortOrder = 28, parentName = "電子支付"),
        PaymentMethodSeed("臺灣 Pay", "tpay", 0xFFE3007F, "epay", sortOrder = 29, parentName = "電子支付"),

        // Other
        PaymentMethodSeed("銀行轉帳", "bank_transfer", 0xFF6B7280, "other", sortOrder = 30, parentName = "其他")
    )

    fun get(): List<PaymentMethod> = getSeeds()
        .filter { it.parentName == null }
        .map { seed ->
            PaymentMethod(
                name = seed.name,
                icon = seed.icon,
                color = seed.color,
                type = seed.type,
                isDefault = seed.isDefault,
                sortOrder = seed.sortOrder,
                parentId = null
            )
        }
}
