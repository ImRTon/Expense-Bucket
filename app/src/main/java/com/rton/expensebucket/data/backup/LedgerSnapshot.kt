package com.rton.expensebucket.data.backup

import com.rton.expensebucket.data.dao.BackupTransactionSummary
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.NotificationCapture
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction

internal data class BackupPreferences(
    val theme: String,
    val palette: String,
    val compareMode: String,
    val firstDayOfWeek: Int,
    val monthlyBudget: Double
)

internal data class LedgerSnapshot(
    val formatVersion: Int,
    val categories: List<Category>,
    val paymentMethods: List<PaymentMethod>,
    val projects: List<Project>,
    val transactions: List<Transaction>,
    val notificationCaptures: List<NotificationCapture>,
    val preferences: BackupPreferences
) {
    companion object {
        const val FORMAT_VERSION = 1
    }
}

internal data class BackupPreview(
    val time: Long,
    val remote: BackupTransactionSummary,
    val local: BackupTransactionSummary
)
