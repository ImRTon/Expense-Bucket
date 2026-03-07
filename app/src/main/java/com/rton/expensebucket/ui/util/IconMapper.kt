package com.rton.expanses.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps stored icon name strings to Material Icons.
 */
object IconMapper {
    private val iconMap = mapOf(
        "Restaurant" to Icons.Filled.Restaurant,
        "DirectionsBus" to Icons.Filled.DirectionsBus,
        "ShoppingBag" to Icons.Filled.ShoppingBag,
        "SportsEsports" to Icons.Filled.SportsEsports,
        "LocalHospital" to Icons.Filled.LocalHospital,
        "School" to Icons.Filled.School,
        "ShoppingCart" to Icons.Filled.ShoppingCart,
        "Receipt" to Icons.Filled.Receipt,
        "Flight" to Icons.Filled.Flight,
        "Home" to Icons.Filled.Home,
        "Checkroom" to Icons.Filled.Checkroom,
        "MoreHoriz" to Icons.Filled.MoreHoriz,
        "AccountBalance" to Icons.Filled.AccountBalance,
        "EmojiEvents" to Icons.Filled.EmojiEvents,
        "TrendingUp" to Icons.Filled.TrendingUp,
        "Wallet" to Icons.Filled.Wallet,
        "Star" to Icons.Filled.Star,
        "Favorite" to Icons.Filled.Favorite,
        "Work" to Icons.Filled.Work,
        "Pets" to Icons.Filled.Pets,
        "LocalCafe" to Icons.Filled.LocalCafe,
        "FitnessCenter" to Icons.Filled.FitnessCenter,
    )

    fun getIcon(name: String): ImageVector {
        return iconMap[name] ?: Icons.Filled.MoreHoriz
    }
}
