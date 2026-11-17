package com.rton.expensebucket.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import com.rton.expensebucket.R
import com.rton.expensebucket.data.DefaultPaymentMethods

/**
 * Maps payment method icon keys to Material Icons or monochrome brand silhouettes.
 *
 * To add a new payment method icon:
 * 1. Add a new key to `allKeys`
 * 2. Map it in `getPainter` or `getVectorIcon`
 * 3. Update `displayName` if needed
 */
object PaymentIconMapper {

    private val defaultColors = DefaultPaymentMethods.getSeeds().associate { it.icon to it.color }

    fun defaultColor(key: String): Long? = defaultColors[key]

    @Composable
    fun getPainter(key: String): Painter {
        val resource = when (key) {
            "linepay" -> R.drawable.ic_linepay
            "jko" -> R.drawable.ic_jkopay
            "pay_full" -> R.drawable.ic_pxpay_plus
            "tpay" -> R.drawable.ic_taiwanpay
            "ipass_money" -> R.drawable.ic_ipass_money
            "icash_pay" -> R.drawable.ic_icash_pay
            "taishin_pay" -> R.drawable.ic_taishin_pay
            "easycard_card" -> R.drawable.ic_easycard
            "easycard" -> R.drawable.ic_easycard
            "icash" -> R.drawable.ic_icash
            "ipass_card" -> R.drawable.ic_ipass
            "suica" -> R.drawable.ic_suica
            "icoca" -> R.drawable.ic_icoca
            else -> null
        }
        return resource?.let { painterResource(it) } ?: rememberVectorPainter(getVectorIcon(key))
    }

    /**
     * Returns all available icon keys for the icon picker UI.
     */
    val allKeys: List<String> = listOf(
        "cash", "credit_card", "visa", "mastercard", "jcb",
        "applepay", "googlepay", "samsungpay", "linepay", "paypal", "tpay",
        "jko", "easycard", "pi", "pay_full", "ipass_money", "icash_pay", "taishin_pay",
        "easycard_card", "icash", "ipass_card", "suica", "icoca",
        "transit_card", "jcshopping", "bank_transfer", "check", "other"
    )

    /**
     * Returns the icon for the given key, falling back to a wallet icon.
     */
    @Composable
    private fun getVectorIcon(key: String): ImageVector = when (key) {
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
        "paypal"        -> ImageVector.vectorResource(id = R.drawable.ic_paypal)

        // ─── Localized E-Payment ───────────────────────────────
        "pi"            -> Icons.Filled.AccountBalanceWallet // Pi 拍錢包
        "jcshopping"    -> Icons.Filled.ShoppingCart  // 全聯 PX Pay

        // ─── Other ─────────────────────────────────────────────
        "transit_card"  -> Icons.Filled.Contactless
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

        "jko"           -> "街口支付"
        "easycard"      -> "悠遊付"
        "pi"            -> "Pi 錢包"
        "pay_full"      -> "全支付"
        "tpay"          -> "臺灣 Pay"
        "ipass_money"   -> "iPASS MONEY"
        "icash_pay"     -> "icash Pay"
        "taishin_pay"   -> "台新 Pay"
        "easycard_card" -> "悠遊卡"
        "icash"         -> "icash"
        "ipass_card"    -> "一卡通"
        "suica"         -> "Suica 西瓜卡"
        "icoca"         -> "ICOCA"
        "jcshopping"    -> "PX Pay"

        "transit_card"  -> "電子票證"
        "bank_transfer" -> "轉帳"
        "check"         -> "支票"
        else            -> "其他"
    }
}
