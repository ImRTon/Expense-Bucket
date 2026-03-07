package com.rton.expanses.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps payment method icon keys to Material Icons or branded fallbacks.
 *
 * To add a new payment method icon:
 * 1. Add a new entry in the `iconMap` below with a unique string key
 * 2. Map it to the closest Material icon OR a custom branded icon
 * 3. Reference the same key in DefaultPaymentMethods.kt
 */
object PaymentIconMapper {

    /**
     * Icon library: key → ImageVector
     * Feel free to extend this map with new branded icons.
     */
    private val iconMap: Map<String, ImageVector> = mapOf(
        // ─── Cash ──────────────────────────────────────────────
        "cash"          to Icons.Filled.Payments,

        // ─── Generic Credit Card ────────────────────────────────
        "credit_card"   to Icons.Filled.CreditCard,

        // ─── Taiwan Bank Cards (use CreditCard as base) ─────────
        "richart"       to Icons.Filled.CreditCard,   // Richart JCB (Taishin)
        "esun"          to Icons.Filled.CreditCard,   // 玉山 Visa
        "cathay"        to Icons.Filled.CreditCard,   // 國泰世華
        "ctbc"          to Icons.Filled.CreditCard,   // 中國信託
        "fubon"         to Icons.Filled.CreditCard,   // 富邦
        "ubot"          to Icons.Filled.CreditCard,   // 聯邦銀行

        // ─── E-Payment ─────────────────────────────────────────
        "jko"           to Icons.Filled.QrCode,       // 街口支付
        "linepay"       to Icons.Filled.Chat,          // LINE Pay
        "easycard"      to Icons.Filled.DirectionsBus, // 悠遊付
        "applepay"      to Icons.Filled.PhoneIphone,   // Apple Pay
        "googlepay"     to Icons.Filled.PhoneAndroid,  // Google Pay
        "pi"            to Icons.Filled.AccountBalanceWallet, // Pi 拍錢包
        "pay_full"      to Icons.Filled.AccountBalanceWallet, // 全支付
        "jcshopping"    to Icons.Filled.ShoppingCart,  // 全聯 PX Pay

        // ─── Other ─────────────────────────────────────────────
        "bank_transfer" to Icons.Filled.AccountBalance,
        "check"         to Icons.Filled.Receipt,
        "other"         to Icons.Filled.MoreHoriz,
    )

    /**
     * Returns the icon for the given key, falling back to a wallet icon.
     */
    fun getIcon(key: String): ImageVector =
        iconMap[key] ?: Icons.Filled.AccountBalanceWallet

    /**
     * Returns all available icon keys for the icon picker UI.
     */
    val allKeys: List<String> get() = iconMap.keys.toList()

    /**
     * Friendly display name for each key (for the icon picker UI).
     */
    fun displayName(key: String): String = when (key) {
        "cash"          -> "現金"
        "credit_card"   -> "信用卡"
        "richart"       -> "Richart"
        "esun"          -> "玉山"
        "cathay"        -> "國泰"
        "ctbc"          -> "中信"
        "fubon"         -> "富邦"
        "ubot"          -> "聯邦"
        "jko"           -> "街口"
        "linepay"       -> "LINE Pay"
        "easycard"      -> "悠遊"
        "applepay"      -> "Apple Pay"
        "googlepay"     -> "Google Pay"
        "pi"            -> "Pi 錢包"
        "pay_full"      -> "全支付"
        "jcshopping"    -> "PX Pay"
        "bank_transfer" -> "轉帳"
        "check"         -> "支票"
        else            -> "其他"
    }
}
