package com.rton.expensebucket.data

import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds brand choices once, preserving existing method IDs and user settings. */
internal object PaymentMethodDefaultsMigration {
    fun migrate(database: SupportSQLiteDatabase) {
        database.query("SELECT COUNT(*) FROM payment_methods").use {
            if (it.moveToFirst() && it.getLong(0) == 0L) return
        }
        val seeds = DefaultPaymentMethods.getSeeds()
        val parents = mutableMapOf<String, Long>()

        fun find(seed: DefaultPaymentMethods.PaymentMethodSeed): Long? {
            val aliases = when (seed.icon) {
                "suica" -> listOf(seed.name, "Suica", "西瓜卡")
                "ipass_money" -> listOf(seed.name, "一卡通MONEY", "一卡通 MONEY")
                "taishin_pay" -> listOf(seed.name, "台新Pay")
                else -> listOf(seed.name)
            }
            val names = aliases.joinToString(" OR ") { "name = ? COLLATE NOCASE" }
            val parentConstraint = if (seed.parentName == null) " AND parentId IS NULL" else ""
            database.query(
                "SELECT id FROM payment_methods WHERE (icon = ? OR $names)$parentConstraint ORDER BY id LIMIT 1",
                (listOf(seed.icon) + aliases).toTypedArray()
            ).use { return if (it.moveToFirst()) it.getLong(0) else null }
        }

        fun insert(seed: DefaultPaymentMethods.PaymentMethodSeed, parentId: Long?): Long {
            val preferredId = database.query(
                "SELECT id FROM payment_methods WHERE id = ?", arrayOf(seed.id)
            ).use { if (it.moveToFirst()) null else seed.id }
            // A preferred ID may belong to a custom method on another installation.
            // NULL lets SQLite allocate a free ID instead of replacing that method.
            database.execSQL(
                """INSERT INTO payment_methods
                    (id, name, icon, color, type, isDefault, parentId, sortOrder,
                     billingCycleType, billingCycleDay, billingLimitType, billingLimitAmount)
                    VALUES (?, ?, ?, ?, ?, 0, ?, ?, 'none', NULL, 'credit', NULL)""",
                arrayOf(preferredId, seed.name, seed.icon, seed.color, seed.type, parentId, seed.sortOrder)
            )
            return database.query("SELECT last_insert_rowid()").use {
                check(it.moveToFirst())
                it.getLong(0)
            }
        }

        for (parentName in listOf("電子支付", "電子票證")) {
            val seed = seeds.single { it.name == parentName && it.parentName == null }
            parents[parentName] = find(seed) ?: insert(seed, null)
        }
        for (seed in seeds.filter { it.parentName != null && it.icon in DefaultPaymentMethods.addedIconKeys }) {
            val parentId = parents.getValue(seed.parentName!!)
            val existingId = find(seed)
            if (existingId == null) {
                insert(seed, parentId)
            } else {
                database.execSQL(
                    "UPDATE payment_methods SET icon = ?, parentId = ? WHERE id = ?",
                    arrayOf(seed.icon, parentId, existingId)
                )
            }
        }
    }
}
