package com.rton.expensebucket.data.backup

import com.rton.expensebucket.data.model.Transaction

internal data class BackupSequences(
    val categories: Long = 0,
    val methods: Long = 0,
    val projects: Long = 0,
    val transactions: Long = 0
)

internal object LedgerMerge {
    fun additions(
        local: LedgerSnapshot,
        remote: LedgerSnapshot,
        sequences: BackupSequences = BackupSequences()
    ): LedgerSnapshot {
        val categories = mapRows(
            local.categories, remote.categories, { it.id }, { it.parentId },
            sequences.categories,
            { item, parent -> Triple(item.name, item.isExpense, parent) },
            { item, id, parent -> item.copy(id = id, parentId = parent) }
        )
        val reservedBindings = local.paymentMethods.mapNotNull { it.nfcCardFingerprint }.toMutableSet()
        val methods = mapRows(
            local.paymentMethods, remote.paymentMethods, { it.id }, { it.parentId },
            sequences.methods,
            { item, parent -> Triple(item.name, item.type, parent) },
            { item, id, parent ->
                item.copy(
                    id = id,
                    parentId = parent,
                    isDefault = false,
                    nfcCardFingerprint = item.nfcCardFingerprint?.takeIf(reservedBindings::add)
                )
            }
        )
        val projects = mapRows(
            local.projects, remote.projects, { it.id }, { null },
            sequences.projects,
            { item, _ -> Triple(item.name, item.startDate, item.endDate) },
            { item, id, _ -> item.copy(id = id) }
        )
        val localByNfcReference = local.transactions.mapNotNull { item ->
            item.nfcReference?.let { it to item.id }
        }.toMap()
        val equivalent = local.transactions.groupBy { it.copy(id = 0, paymentMethodChangedAt = null) }
            .mapValues { (_, items) -> ArrayDeque(items.map(Transaction::id)) }
        var nextId = maxOf(sequences.transactions, local.transactions.maxOfOrNull(Transaction::id) ?: 0)
        val transactionIds = mutableMapOf<Long, Long>()
        val newTransactions = remote.transactions.mapNotNull { item ->
            val remapped = item.copy(
                id = 0,
                categoryId = categories.ids[item.categoryId],
                paymentMethodId = methods.ids[item.paymentMethodId],
                projectId = projects.ids[item.projectId]
            )
            val existing = if (item.nfcReference != null) {
                localByNfcReference[item.nfcReference]
            } else {
                equivalent[remapped.copy(paymentMethodChangedAt = null)]?.removeFirstOrNull()
            }
            val newId = existing ?: ++nextId
            transactionIds[item.id] = newId
            if (existing == null) remapped.copy(id = newId) else null
        }
        val captureKeys = local.notificationCaptures.mapTo(mutableSetOf()) {
            it.transactionId to it.packageName
        }
        val newCaptures = remote.notificationCaptures.mapNotNull { capture ->
            val transactionId = transactionIds.getValue(capture.transactionId)
            if (captureKeys.add(transactionId to capture.packageName)) {
                capture.copy(transactionId = transactionId)
            } else {
                null
            }
        }
        return local.copy(
            categories = categories.newRows,
            paymentMethods = methods.newRows,
            projects = projects.newRows,
            transactions = newTransactions,
            notificationCaptures = newCaptures
        )
    }

    private data class MappedRows<T>(val ids: Map<Long, Long>, val newRows: List<T>)

    private fun <T, K> mapRows(
        local: List<T>,
        remote: List<T>,
        id: (T) -> Long,
        parent: (T) -> Long?,
        sequence: Long,
        key: (T, Long?) -> K,
        withIds: (T, Long, Long?) -> T
    ): MappedRows<T> {
        val localByKey = local.groupBy { key(it, parent(it)) }
            .mapValues { (_, items) -> ArrayDeque(items.map(id)) }
        val remoteById = remote.associateBy(id)
        val ids = mutableMapOf<Long, Long>()
        val visiting = mutableSetOf<Long>()
        val newRows = mutableListOf<T>()
        var nextId = maxOf(sequence, local.maxOfOrNull(id) ?: 0)

        fun map(item: T): Long {
            val sourceId = id(item)
            ids[sourceId]?.let { return it }
            require(visiting.add(sourceId)) { "Cyclic backup hierarchy" }
            val targetParent = parent(item)?.let { remoteById[it]?.let(::map) }
            val existing = localByKey[key(item, targetParent)]?.removeFirstOrNull()
            val targetId = existing ?: ++nextId
            if (existing == null) newRows += withIds(item, targetId, targetParent)
            ids[sourceId] = targetId
            visiting.remove(sourceId)
            return targetId
        }

        remote.forEach(::map)
        return MappedRows(ids, newRows)
    }
}
