package com.rton.expensebucket

import com.rton.expensebucket.data.backup.BackupPreferences
import com.rton.expensebucket.data.backup.LedgerMerge
import com.rton.expensebucket.data.backup.LedgerSnapshot
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.NotificationCapture
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerMergeTest {
    private val empty = LedgerSnapshot(
        LedgerSnapshot.FORMAT_VERSION, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
        BackupPreferences("SYSTEM", "DEFAULT", "EXPENSE_INCOME", 2, 1000.0)
    )

    @Test
    fun merge_remapsConflictingIdsAndCanBeRepeated() {
        val local = empty.copy(
            categories = listOf(Category(id = 1, name = "Food", icon = "Restaurant", color = 0)),
            paymentMethods = listOf(PaymentMethod(id = 1, name = "Cash", icon = "Cash", isDefault = true)),
            projects = listOf(Project(id = 1, name = "Taipei")),
            transactions = listOf(Transaction(id = 1, amount = 50.0, categoryId = 1, createdAt = 1))
        )
        val remote = empty.copy(
            categories = listOf(
                Category(id = 1, name = "Travel", icon = "DirectionsBus", color = 0),
                Category(id = 2, name = "Train", icon = "Train", color = 0, parentId = 1)
            ),
            paymentMethods = listOf(PaymentMethod(id = 1, name = "Card", icon = "CreditCard", isDefault = true)),
            projects = listOf(Project(id = 1, name = "Tokyo")),
            transactions = listOf(
                Transaction(id = 1, amount = 600.0, categoryId = 2, paymentMethodId = 1, projectId = 1, createdAt = 2)
            ),
            notificationCaptures = listOf(
                NotificationCapture(
                    1, "bank", "key", "hash", 600.0, isExpense = true, merchant = "Train",
                    cardSuffix = null, transactionDate = null, capturedAt = 2
                )
            )
        )

        val merged = LedgerMerge.additions(local, remote)
        val transaction = merged.transactions.single()
        assertEquals(2L, transaction.id)
        assertEquals(merged.categories[0].id, merged.categories[1].parentId)
        assertEquals(merged.categories[1].id, transaction.categoryId)
        assertEquals(merged.paymentMethods.single().id, transaction.paymentMethodId)
        assertEquals(merged.projects.single().id, transaction.projectId)
        assertEquals(transaction.id, merged.notificationCaptures.single().transactionId)
        assertFalse(merged.paymentMethods.single().isDefault)

        val repeat = LedgerMerge.additions(
            local.copy(
                categories = local.categories + merged.categories,
                paymentMethods = local.paymentMethods + merged.paymentMethods,
                projects = local.projects + merged.projects,
                transactions = local.transactions + merged.transactions,
                notificationCaptures = merged.notificationCaptures
            ),
            remote
        )
        assertTrue(repeat.transactions.isEmpty())
        assertTrue(repeat.categories.isEmpty())
        assertTrue(repeat.paymentMethods.isEmpty())
        assertTrue(repeat.projects.isEmpty())
        assertTrue(repeat.notificationCaptures.isEmpty())
    }

    @Test
    fun merge_preservesLegitimateDuplicatesAndDeduplicatesTransitReferences() {
        val receipt = Transaction(id = 1, amount = 99.0, createdAt = 1)
        val nfc = receipt.copy(id = 2, nfcReference = "ride", note = "Local correction")
        val local = empty.copy(transactions = listOf(receipt, nfc))
        val remote = empty.copy(
            transactions = listOf(receipt, nfc.copy(note = "Original"), receipt.copy(id = 3))
        )

        val added = LedgerMerge.additions(local, remote).transactions.single()
        assertEquals(receipt.copy(id = 3), added)
    }
}
