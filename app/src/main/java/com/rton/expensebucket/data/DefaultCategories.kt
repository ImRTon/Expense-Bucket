package com.rton.expanses.data

import com.rton.expanses.data.model.Category

/**
 * Default categories with parent-child hierarchy.
 * Parent categories use negative temp IDs which will be replaced by real IDs during seeding.
 *
 * The seeding logic in MainViewModel handles inserting parents first,
 * then updating children with the real parentId.
 */
object DefaultCategories {

    /**
     * Returns flat list of all categories (parents + children).
     * Children have parentId set, parents have parentId = null.
     *
     * IMPORTANT: Since auto-generated IDs are unknown at compile time,
     * the ViewModel must seed parents first, then map children by matching parentName.
     */
    data class CategorySeed(
        val name: String,
        val icon: String,
        val color: Long,
        val isExpense: Boolean,
        val sortOrder: Int,
        val parentName: String? = null // null = parent category
    )

    fun getSeeds(): List<CategorySeed> = listOf(
        // ─── Expense parents ─────────────────────────────
        CategorySeed("食",     "Restaurant",     0xFFF97316, isExpense = true, sortOrder = 0),
        CategorySeed("行",     "DirectionsBus",  0xFF3B82F6, isExpense = true, sortOrder = 1),
        CategorySeed("樂",     "SportsEsports",  0xFF8B5CF6, isExpense = true, sortOrder = 2),
        CategorySeed("住",     "Home",           0xFF14B8A6, isExpense = true, sortOrder = 3),
        CategorySeed("醫",     "LocalHospital",  0xFF10B981, isExpense = true, sortOrder = 4),
        CategorySeed("學",     "School",         0xFF06B6D4, isExpense = true, sortOrder = 5),
        CategorySeed("其他支出","MoreHoriz",      0xFF6B7280, isExpense = true, sortOrder = 6),

        // ─── Expense children ────────────────────────────
        CategorySeed("飲食",   "Restaurant",     0xFFF97316, isExpense = true, sortOrder = 0, parentName = "食"),
        CategorySeed("食材",   "ShoppingCart",   0xFF84CC16, isExpense = true, sortOrder = 1, parentName = "食"),
        CategorySeed("飲料",   "LocalCafe",      0xFFA16207, isExpense = true, sortOrder = 2, parentName = "食"),
        CategorySeed("外送",   "DeliveryDining", 0xFFEA580C, isExpense = true, sortOrder = 3, parentName = "食"),

        CategorySeed("交通",   "DirectionsBus",  0xFF3B82F6, isExpense = true, sortOrder = 0, parentName = "行"),
        CategorySeed("旅行",   "Flight",         0xFFF59E0B, isExpense = true, sortOrder = 1, parentName = "行"),
        CategorySeed("加油",   "LocalGasStation", 0xFF1E40AF, isExpense = true, sortOrder = 2, parentName = "行"),

        CategorySeed("娛樂",   "SportsEsports",  0xFF8B5CF6, isExpense = true, sortOrder = 0, parentName = "樂"),
        CategorySeed("購物",   "ShoppingBag",    0xFFEC4899, isExpense = true, sortOrder = 1, parentName = "樂"),
        CategorySeed("服飾",   "Checkroom",      0xFFE879F9, isExpense = true, sortOrder = 2, parentName = "樂"),

        CategorySeed("房租",   "Home",           0xFF14B8A6, isExpense = true, sortOrder = 0, parentName = "住"),
        CategorySeed("帳單",   "Receipt",        0xFFEF4444, isExpense = true, sortOrder = 1, parentName = "住"),
        CategorySeed("日用品", "CleaningServices",0xFF0D9488, isExpense = true, sortOrder = 2, parentName = "住"),

        CategorySeed("醫療",   "LocalHospital",  0xFF10B981, isExpense = true, sortOrder = 0, parentName = "醫"),
        CategorySeed("美容",   "Spa",            0xFFF472B6, isExpense = true, sortOrder = 1, parentName = "醫"),

        CategorySeed("教育",   "School",         0xFF06B6D4, isExpense = true, sortOrder = 0, parentName = "學"),
        CategorySeed("書籍",   "MenuBook",       0xFF0891B2, isExpense = true, sortOrder = 1, parentName = "學"),

        // ─── Income parents ──────────────────────────────
        CategorySeed("薪資",   "AccountBalance", 0xFF10B981, isExpense = false, sortOrder = 0),
        CategorySeed("獎金",   "EmojiEvents",    0xFFF59E0B, isExpense = false, sortOrder = 1),
        CategorySeed("投資",   "TrendingUp",     0xFF3B82F6, isExpense = false, sortOrder = 2),
        CategorySeed("其他收入","Wallet",         0xFF6B7280, isExpense = false, sortOrder = 3),
    )

    /**
     * Legacy flat list for backward compatibility.
     * Used if the seed logic detects existing flat categories.
     */
    fun get(): List<Category> = getSeeds()
        .filter { it.parentName == null }
        .map { seed ->
            Category(
                name = seed.name,
                icon = seed.icon,
                color = seed.color,
                isExpense = seed.isExpense,
                sortOrder = seed.sortOrder
            )
        }
}
