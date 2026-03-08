package com.rton.expensebucket.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.rton.expensebucket.R

/**
 * Maps payment method icon keys to Material Icons or branded fallbacks.
 *
 * To add a new payment method icon:
 * 1. Add a new key to `allKeys`
 * 2. Map it in `getIcon` inside the `when` expression
 * 3. Update `displayName` if needed
 */
object PaymentIconMapper {

    /**
     * Returns all available icon keys for the icon picker UI.
     */
    val allKeys: List<String> = listOf(
        "cash", "credit_card", "visa", "mastercard", "jcb",
        "applepay", "googlepay", "samsungpay", "linepay", "paypal", "tpay",
        "jko", "easycard", "pi", "pay_full", "jcshopping", "bank_transfer", "check", "other"
    )

    /**
     * Returns the icon for the given key, falling back to a wallet icon.
     */
    @Composable
    fun getIcon(key: String): ImageVector = when (key) {
        // ─── Cash ──────────────────────────────────────────────
        "cash"          -> Icons.Filled.Payments

        // ─── Generic Credit Card ────────────────────────────────
        "credit_card"   -> Icons.Filled.CreditCard
        "visa"          -> ImageVector.vectorResource(id = R.drawable.ic_visa)
        "mastercard"    -> ImageVector.vectorResource(id = R.drawable.ic_mastercard)
        "jcb"           -> ImageVector.vectorResource(id = R.drawable.ic_jcb)

        // ─── Taiwan Bank Cards (Keep for backward compatibility of old data)
        "richart"       -> ImageVector.vectorResource(id = R.drawable.ic_jcb)
        "esun"          -> ImageVector.vectorResource(id = R.drawable.ic_visa)
        "cathay"        -> ImageVector.vectorResource(id = R.drawable.ic_mastercard)
        "ctbc"          -> Icons.Filled.CreditCard
        "fubon"         -> Icons.Filled.CreditCard
        "ubot"          -> Icons.Filled.CreditCard

        // ─── Brand Payment Options ─────────────────────────────
        "applepay"      -> ImageVector.vectorResource(id = R.drawable.ic_applepay)
        "googlepay"     -> ImageVector.vectorResource(id = R.drawable.ic_googlepay)
        "samsungpay"    -> ImageVector.vectorResource(id = R.drawable.ic_samsungpay)
        "linepay"       -> ImageVector.vectorResource(id = R.drawable.ic_line)
        "paypal"        -> ImageVector.vectorResource(id = R.drawable.ic_paypal)

        // ─── Localized E-Payment ───────────────────────────────
        "jko"           -> Icons.Filled.QrCode       // 街口支付
        "easycard"      -> Icons.Filled.DirectionsBus // 悠遊付
        "pi"            -> Icons.Filled.AccountBalanceWallet // Pi 拍錢包
        "pay_full"      -> Icons.Filled.AccountBalanceWallet // 全支付
        "tpay"          -> Icons.Filled.QrCodeScanner // 臺灣 Pay
        "jcshopping"    -> Icons.Filled.ShoppingCart  // 全聯 PX Pay

        // ─── Other ─────────────────────────────────────────────
        "bank_transfer" -> Icons.Filled.AccountBalance
        "check"         -> Icons.Filled.Receipt
        "other"         -> Icons.Filled.MoreHoriz
        else            -> Icons.Filled.AccountBalanceWallet
    }

    /**
     * Friendly display name for each key (for the icon picker UI).
     */
    fun displayName(key: String): String = when (key) {
        "cash"          -> "現金"
        "credit_card"   -> "信用卡"
        "visa"          -> "Visa"
        "mastercard"    -> "Mastercard"
        "jcb"           -> "JCB"
        // (Backward compat)
        "richart"       -> "Richart"
        "esun"          -> "玉山"
        "cathay"        -> "國泰"
        "ctbc"          -> "中信"
        "fubon"         -> "富邦"
        "ubot"          -> "聯邦"

        "applepay"      -> "Apple Pay"
        "googlepay"     -> "Google Pay"
        "samsungpay"    -> "Samsung Pay"
        "linepay"       -> "LINE Pay"
        "paypal"        -> "PayPal"

        "jko"           -> "街口"
        "easycard"      -> "悠遊"
        "pi"            -> "Pi 錢包"
        "pay_full"      -> "全支付"
        "tpay"          -> "臺灣 Pay"
        "jcshopping"    -> "PX Pay"

        "bank_transfer" -> "轉帳"
        "check"         -> "支票"
        else            -> "其他"
    }
}
