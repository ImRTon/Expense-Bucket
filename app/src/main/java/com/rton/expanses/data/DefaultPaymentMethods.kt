package com.rton.expanses.data

import com.rton.expanses.data.model.PaymentMethod

/**
 * Default payment methods seeded on first launch.
 *
 * Icon keys map to PaymentIconMapper. Types: "cash", "credit", "epay", "other"
 * To add new methods: add an entry here and a matching icon key in PaymentIconMapper.
 */
object DefaultPaymentMethods {

    fun get(): List<PaymentMethod> = listOf(
        // ─── Cash ──────────────────────────────────────────────
        PaymentMethod(
            name = "現金",
            icon = "cash",
            color = 0xFF4ADE80,
            type = "cash",
            isDefault = true,
            sortOrder = 0
        ),

        // ─── Credit Cards ───────────────────────────────────────
        PaymentMethod(
            name = "信用卡",
            icon = "credit_card",
            color = 0xFF818CF8,
            type = "credit",
            sortOrder = 10
        ),
        PaymentMethod(
            name = "Richart JCB",
            icon = "richart",
            color = 0xFF60A5FA,
            type = "credit",
            sortOrder = 11
        ),
        PaymentMethod(
            name = "玉山 Visa",
            icon = "esun",
            color = 0xFFFF8C00,
            type = "credit",
            sortOrder = 12
        ),
        PaymentMethod(
            name = "國泰世華",
            icon = "cathay",
            color = 0xFF10B981,
            type = "credit",
            sortOrder = 13
        ),

        // ─── E-Payment ─────────────────────────────────────────
        PaymentMethod(
            name = "街口支付",
            icon = "jko",
            color = 0xFFF59E0B,
            type = "epay",
            sortOrder = 20
        ),
        PaymentMethod(
            name = "LINE Pay",
            icon = "linepay",
            color = 0xFF22C55E,
            type = "epay",
            sortOrder = 21
        ),
        PaymentMethod(
            name = "悠遊付",
            icon = "easycard",
            color = 0xFF3B82F6,
            type = "epay",
            sortOrder = 22
        ),
        PaymentMethod(
            name = "Apple Pay",
            icon = "applepay",
            color = 0xFF1C1C1E,
            type = "epay",
            sortOrder = 23
        ),
        PaymentMethod(
            name = "Google Pay",
            icon = "googlepay",
            color = 0xFF4285F4,
            type = "epay",
            sortOrder = 24
        ),
        PaymentMethod(
            name = "Pi 拍錢包",
            icon = "pi",
            color = 0xFFEC4899,
            type = "epay",
            sortOrder = 25
        ),
        PaymentMethod(
            name = "全支付",
            icon = "pay_full",
            color = 0xFF0EA5E9,
            type = "epay",
            sortOrder = 26
        ),

        // ─── Other ─────────────────────────────────────────────
        PaymentMethod(
            name = "銀行轉帳",
            icon = "bank_transfer",
            color = 0xFF6B7280,
            type = "other",
            sortOrder = 30
        )
    )
}
