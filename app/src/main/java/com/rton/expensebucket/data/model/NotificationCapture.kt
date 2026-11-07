package com.rton.expensebucket.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** Immutable notification evidence, independent of edits to the draft or confirmation. */
@Entity(
    tableName = "notification_captures",
    primaryKeys = ["transactionId", "packageName"],
    foreignKeys = [ForeignKey(
        entity = Transaction::class,
        parentColumns = ["id"],
        childColumns = ["transactionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["capturedAt"])]
)
data class NotificationCapture(
    val transactionId: Long = 0,
    val packageName: String,
    val notificationKey: String,
    val textHash: String,
    val amount: Double,
    val currency: String = "TWD",
    val isExpense: Boolean,
    val merchant: String,
    val cardSuffix: String?,
    val transactionDate: Long?,
    val capturedAt: Long
)
