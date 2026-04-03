package com.rton.expensebucket.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.repository.ExpenseBucketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSummary(
    val transactionsImported: Int = 0,
    val categoriesImported: Int = 0,
    val paymentMethodsImported: Int = 0,
    val projectsImported: Int = 0,
    val categoriesSkipped: Int = 0,
    val paymentMethodsSkipped: Int = 0,
    val projectsSkipped: Int = 0
) {
    fun toUserMessage(): String {
        val parts = mutableListOf<String>()
        parts += "交易 $transactionsImported 筆"
        if (categoriesImported > 0 || categoriesSkipped > 0) {
            parts += "分類新增 $categoriesImported 筆"
            if (categoriesSkipped > 0) parts += "分類略過重複 $categoriesSkipped 筆"
        }
        if (paymentMethodsImported > 0 || paymentMethodsSkipped > 0) {
            parts += "支付工具新增 $paymentMethodsImported 筆"
            if (paymentMethodsSkipped > 0) parts += "支付工具略過重複 $paymentMethodsSkipped 筆"
        }
        if (projectsImported > 0 || projectsSkipped > 0) {
            parts += "專案新增 $projectsImported 筆"
            if (projectsSkipped > 0) parts += "專案略過重複 $projectsSkipped 筆"
        }
        return "成功匯入 ${parts.joinToString("，")}"
    }
}

@Singleton
class DataExportImportManager @Inject constructor(
    private val repository: ExpenseBucketRepository
) {
    private enum class ExportSection {
        CATEGORIES,
        PAYMENT_METHODS,
        PROJECTS,
        TRANSACTIONS
    }

    private data class CategoryRecord(
        val name: String,
        val parentName: String,
        val icon: String,
        val color: Long,
        val isExpense: Boolean,
        val sortOrder: Int
    )

    private data class PaymentMethodRecord(
        val name: String,
        val parentName: String,
        val icon: String,
        val color: Long,
        val type: String,
        val isDefault: Boolean,
        val sortOrder: Int,
        val billingCycleType: String,
        val billingCycleDay: Int?,
        val billingLimitType: String,
        val billingLimitAmount: Double?
    )

    private data class ProjectRecord(
        val name: String,
        val description: String,
        val defaultCurrency: String,
        val startDate: Long?,
        val endDate: Long?,
        val budget: Double?,
        val isActive: Boolean,
        val createdAt: Long,
        val updatedAt: Long
    )

    private data class TransactionRecord(
        val amount: Double,
        val personalAmount: Double?,
        val note: String,
        val categoryPath: String,
        val projectName: String,
        val projectCurrency: String,
        val projectStartDate: Long?,
        val projectEndDate: Long?,
        val paymentMethodPath: String,
        val date: Long,
        val currency: String,
        val exchangeRate: Double,
        val isExpense: Boolean,
        val source: String,
        val isDraft: Boolean,
        val createdAt: Long
    )

    private data class CategoryKey(
        val path: String,
        val isExpense: Boolean
    )

    private data class PaymentMethodKey(
        val path: String,
        val type: String
    )

    private data class ProjectKey(
        val name: String,
        val defaultCurrency: String,
        val startDate: Long?,
        val endDate: Long?
    )

    suspend fun exportToCsv(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val categories = repository.getAllCategories().first().sortedWith(
                compareBy<Category> { it.isExpense.not() }
                    .thenBy { it.sortOrder }
                    .thenBy { it.id }
            )
            val paymentMethods = repository.getAllPaymentMethods().first().sortedWith(
                compareBy<PaymentMethod> { it.sortOrder }.thenBy { it.id }
            )
            val projects = repository.getAllProjects().first().sortedBy { it.updatedAt }
            val transactions = repository.getAllConfirmedTransactions().first()

            val categoryById = categories.associateBy { it.id }
            val paymentMethodById = paymentMethods.associateBy { it.id }
            val projectById = projects.associateBy { it.id }

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    writer.write("# ExpenseBucket Export v2\n")
                    writer.write("# GeneratedAt:${System.currentTimeMillis()}\n\n")

                    writer.write("#SECTION:CATEGORIES\n")
                    writer.write("name,parentName,icon,color,isExpense,sortOrder\n")
                    categories.forEach { category ->
                        writer.write(
                            toCsvRow(
                                listOf(
                                    category.name,
                                    category.parentId?.let { categoryById[it]?.name }.orEmpty(),
                                    category.icon,
                                    category.color.toString(),
                                    category.isExpense.toString(),
                                    category.sortOrder.toString()
                                )
                            ) + "\n"
                        )
                    }
                    writer.write("\n")

                    writer.write("#SECTION:PAYMENT_METHODS\n")
                    writer.write("name,parentName,icon,color,type,isDefault,sortOrder,billingCycleType,billingCycleDay,billingLimitType,billingLimitAmount\n")
                    paymentMethods.forEach { method ->
                        writer.write(
                            toCsvRow(
                                listOf(
                                    method.name,
                                    method.parentId?.let { paymentMethodById[it]?.name }.orEmpty(),
                                    method.icon,
                                    method.color.toString(),
                                    method.type,
                                    method.isDefault.toString(),
                                    method.sortOrder.toString(),
                                    method.billingCycleType,
                                    method.billingCycleDay?.toString().orEmpty(),
                                    method.billingLimitType,
                                    method.billingLimitAmount?.toString().orEmpty()
                                )
                            ) + "\n"
                        )
                    }
                    writer.write("\n")

                    writer.write("#SECTION:PROJECTS\n")
                    writer.write("name,description,defaultCurrency,startDate,endDate,budget,isActive,createdAt,updatedAt\n")
                    projects.forEach { project ->
                        writer.write(
                            toCsvRow(
                                listOf(
                                    project.name,
                                    project.description,
                                    project.defaultCurrency,
                                    project.startDate?.toString().orEmpty(),
                                    project.endDate?.toString().orEmpty(),
                                    project.budget?.toString().orEmpty(),
                                    project.isActive.toString(),
                                    project.createdAt.toString(),
                                    project.updatedAt.toString()
                                )
                            ) + "\n"
                        )
                    }
                    writer.write("\n")

                    writer.write("#SECTION:TRANSACTIONS\n")
                    writer.write("amount,personalAmount,note,categoryPath,projectName,projectCurrency,projectStartDate,projectEndDate,paymentMethodPath,date,currency,exchangeRate,isExpense,source,isDraft,createdAt\n")
                    transactions.forEach { transaction ->
                        val categoryPath = transaction.categoryId
                            ?.let { categoryById[it] }
                            ?.let { buildCategoryPath(it, categoryById) }
                            .orEmpty()
                        val paymentMethodPath = transaction.paymentMethodId
                            ?.let { paymentMethodById[it] }
                            ?.let { buildPaymentMethodPath(it, paymentMethodById) }
                            .orEmpty()
                        val project = transaction.projectId?.let { projectById[it] }

                        writer.write(
                            toCsvRow(
                                listOf(
                                    transaction.amount.toString(),
                                    transaction.personalAmount?.toString().orEmpty(),
                                    transaction.note,
                                    categoryPath,
                                    project?.name.orEmpty(),
                                    project?.defaultCurrency.orEmpty(),
                                    project?.startDate?.toString().orEmpty(),
                                    project?.endDate?.toString().orEmpty(),
                                    paymentMethodPath,
                                    transaction.date.toString(),
                                    transaction.currency,
                                    transaction.exchangeRate.toString(),
                                    transaction.isExpense.toString(),
                                    transaction.source,
                                    transaction.isDraft.toString(),
                                    transaction.createdAt.toString()
                                )
                            ) + "\n"
                        )
                    }
                }
            } ?: return@withContext Result.failure(Exception("無法開啟匯出檔案"))

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DataExportImport", "Failed to export data", e)
            Result.failure(e)
        }
    }

    suspend fun importFromCsv(context: Context, uri: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    val firstRecord = reader.readCsvRecord()
                        ?: return@withContext Result.failure(Exception("Empty file"))

                    val summary = if (firstRecord.startsWith("# ExpenseBucket Export v2")) {
                        importSectionedCsv(reader)
                    } else {
                        importLegacyTransactions(reader, firstRecord)
                    }
                    return@withContext Result.success(summary)
                }
            } ?: Result.failure(Exception("無法開啟匯入檔案"))
        } catch (e: Exception) {
            Log.e("DataExportImport", "Failed to import data", e)
            Result.failure(e)
        }
    }

    private suspend fun importSectionedCsv(reader: BufferedReader): ImportSummary {
        var currentSection: ExportSection? = null
        var currentHeader: Map<String, Int>? = null

        val categoryRecords = mutableListOf<CategoryRecord>()
        val paymentMethodRecords = mutableListOf<PaymentMethodRecord>()
        val projectRecords = mutableListOf<ProjectRecord>()
        val transactionRecords = mutableListOf<TransactionRecord>()

        var record = reader.readCsvRecord()
        while (record != null) {
            when {
                record.isBlank() -> Unit
                record.startsWith("#SECTION:") -> {
                    currentSection = runCatching {
                        ExportSection.valueOf(record.removePrefix("#SECTION:").trim())
                    }.getOrNull()
                    currentHeader = null
                }
                record.startsWith("#") -> Unit
                currentSection == null -> Unit
                currentHeader == null -> {
                    currentHeader = parseCsvLine(record).withIndex().associate { it.value to it.index }
                }
                else -> {
                    val values = parseCsvLine(record)
                    when (currentSection) {
                        ExportSection.CATEGORIES -> categoryRecords += CategoryRecord(
                            name = valueAt(values, currentHeader, "name"),
                            parentName = valueAt(values, currentHeader, "parentName"),
                            icon = valueAt(values, currentHeader, "icon"),
                            color = valueAt(values, currentHeader, "color").toLongOrNull() ?: 0xFF6B7280,
                            isExpense = valueAt(values, currentHeader, "isExpense").toBooleanStrictOrNull() ?: true,
                            sortOrder = valueAt(values, currentHeader, "sortOrder").toIntOrNull() ?: 0
                        )
                        ExportSection.PAYMENT_METHODS -> paymentMethodRecords += PaymentMethodRecord(
                            name = valueAt(values, currentHeader, "name"),
                            parentName = valueAt(values, currentHeader, "parentName"),
                            icon = valueAt(values, currentHeader, "icon"),
                            color = valueAt(values, currentHeader, "color").toLongOrNull() ?: 0xFF6B7280,
                            type = valueAt(values, currentHeader, "type").ifBlank { "other" },
                            isDefault = valueAt(values, currentHeader, "isDefault").toBooleanStrictOrNull() ?: false,
                            sortOrder = valueAt(values, currentHeader, "sortOrder").toIntOrNull() ?: 0,
                            billingCycleType = valueAt(values, currentHeader, "billingCycleType"),
                            billingCycleDay = valueAt(values, currentHeader, "billingCycleDay").toIntOrNull(),
                            billingLimitType = valueAt(values, currentHeader, "billingLimitType"),
                            billingLimitAmount = valueAt(values, currentHeader, "billingLimitAmount").toDoubleOrNull()
                        )
                        ExportSection.PROJECTS -> projectRecords += ProjectRecord(
                            name = valueAt(values, currentHeader, "name"),
                            description = valueAt(values, currentHeader, "description"),
                            defaultCurrency = valueAt(values, currentHeader, "defaultCurrency").ifBlank { "TWD" },
                            startDate = valueAt(values, currentHeader, "startDate").toLongOrNull(),
                            endDate = valueAt(values, currentHeader, "endDate").toLongOrNull(),
                            budget = valueAt(values, currentHeader, "budget").toDoubleOrNull(),
                            isActive = valueAt(values, currentHeader, "isActive").toBooleanStrictOrNull() ?: true,
                            createdAt = valueAt(values, currentHeader, "createdAt").toLongOrNull() ?: System.currentTimeMillis(),
                            updatedAt = valueAt(values, currentHeader, "updatedAt").toLongOrNull() ?: System.currentTimeMillis()
                        )
                        ExportSection.TRANSACTIONS -> transactionRecords += TransactionRecord(
                            amount = valueAt(values, currentHeader, "amount").toDoubleOrNull() ?: 0.0,
                            personalAmount = valueAt(values, currentHeader, "personalAmount").toDoubleOrNull(),
                            note = valueAt(values, currentHeader, "note"),
                            categoryPath = valueAt(values, currentHeader, "categoryPath"),
                            projectName = valueAt(values, currentHeader, "projectName"),
                            projectCurrency = valueAt(values, currentHeader, "projectCurrency"),
                            projectStartDate = valueAt(values, currentHeader, "projectStartDate").toLongOrNull(),
                            projectEndDate = valueAt(values, currentHeader, "projectEndDate").toLongOrNull(),
                            paymentMethodPath = valueAt(values, currentHeader, "paymentMethodPath"),
                            date = parseDateValue(valueAt(values, currentHeader, "date")) ?: System.currentTimeMillis(),
                            currency = valueAt(values, currentHeader, "currency").ifBlank { "TWD" },
                            exchangeRate = valueAt(values, currentHeader, "exchangeRate").toDoubleOrNull() ?: 1.0,
                            isExpense = valueAt(values, currentHeader, "isExpense").toBooleanStrictOrNull() ?: true,
                            source = valueAt(values, currentHeader, "source").ifBlank { "import" },
                            isDraft = valueAt(values, currentHeader, "isDraft").toBooleanStrictOrNull() ?: false,
                            createdAt = parseDateValue(valueAt(values, currentHeader, "createdAt")) ?: System.currentTimeMillis()
                        )
                    }
                }
            }
            record = reader.readCsvRecord()
        }

        val categoryImport = importCategories(categoryRecords)
        val paymentMethodImport = importPaymentMethods(paymentMethodRecords)
        val projectImport = importProjects(projectRecords)
        val transactionCount = importTransactions(transactionRecords)

        return ImportSummary(
            transactionsImported = transactionCount,
            categoriesImported = categoryImport.first,
            paymentMethodsImported = paymentMethodImport.first,
            projectsImported = projectImport.first,
            categoriesSkipped = categoryImport.second,
            paymentMethodsSkipped = paymentMethodImport.second,
            projectsSkipped = projectImport.second
        )
    }

    private suspend fun importLegacyTransactions(
        reader: BufferedReader,
        headerLine: String
    ): ImportSummary {
        val header = parseCsvLine(headerLine).withIndex().associate { it.value to it.index }
        val transactionRecords = mutableListOf<TransactionRecord>()

        var record = reader.readCsvRecord()
        while (record != null) {
            if (record.isNotBlank()) {
                val values = parseCsvLine(record)
                transactionRecords += TransactionRecord(
                    amount = valueAt(values, header, "amount").toDoubleOrNull() ?: 0.0,
                    personalAmount = valueAt(values, header, "personalAmount").toDoubleOrNull(),
                    note = valueAt(values, header, "note"),
                    categoryPath = valueAt(values, header, "categoryName"),
                    projectName = valueAt(values, header, "projectName"),
                    projectCurrency = "",
                    projectStartDate = null,
                    projectEndDate = null,
                    paymentMethodPath = valueAt(values, header, "paymentMethodName"),
                    date = parseDateValue(valueAt(values, header, "date")) ?: System.currentTimeMillis(),
                    currency = valueAt(values, header, "currency").ifBlank { "TWD" },
                    exchangeRate = valueAt(values, header, "exchangeRate").toDoubleOrNull() ?: 1.0,
                    isExpense = valueAt(values, header, "isExpense").toBooleanStrictOrNull() ?: true,
                    source = valueAt(values, header, "source").ifBlank { "import" },
                    isDraft = valueAt(values, header, "isDraft").toBooleanStrictOrNull() ?: false,
                    createdAt = parseDateValue(valueAt(values, header, "createdAt")) ?: System.currentTimeMillis()
                )
            }
            record = reader.readCsvRecord()
        }

        return ImportSummary(transactionsImported = importTransactions(transactionRecords))
    }

    private suspend fun importCategories(records: List<CategoryRecord>): Pair<Int, Int> {
        if (records.isEmpty()) return 0 to 0

        val existingCategories = repository.getAllCategories().first()
        val categoryById = existingCategories.associateBy { it.id }.toMutableMap()
        val categoryIdByKey = existingCategories.associate { category ->
            CategoryKey(buildCategoryPath(category, categoryById), category.isExpense) to category.id
        }.toMutableMap()

        var imported = 0
        var skipped = 0

        records.sortedBy { if (it.parentName.isBlank()) 0 else 1 }.forEach { record ->
            val path = buildPath(record.parentName, record.name)
            val key = CategoryKey(path, record.isExpense)
            if (categoryIdByKey.containsKey(key)) {
                skipped++
                return@forEach
            }

            val parentId = if (record.parentName.isBlank()) {
                null
            } else {
                categoryIdByKey[CategoryKey(record.parentName, record.isExpense)]
            }
            if (record.parentName.isNotBlank() && parentId == null) {
                skipped++
                return@forEach
            }

            val insertedId = repository.insertCategory(
                Category(
                    name = record.name,
                    icon = record.icon,
                    color = record.color,
                    isExpense = record.isExpense,
                    parentId = parentId,
                    sortOrder = record.sortOrder
                )
            )
            categoryById[insertedId] = Category(
                id = insertedId,
                name = record.name,
                icon = record.icon,
                color = record.color,
                isExpense = record.isExpense,
                parentId = parentId,
                sortOrder = record.sortOrder
            )
            categoryIdByKey[key] = insertedId
            imported++
        }

        return imported to skipped
    }

    private suspend fun importPaymentMethods(records: List<PaymentMethodRecord>): Pair<Int, Int> {
        if (records.isEmpty()) return 0 to 0

        val existingMethods = repository.getAllPaymentMethods().first()
        val methodById = existingMethods.associateBy { it.id }.toMutableMap()
        val methodIdByKey = existingMethods.associate { method ->
            PaymentMethodKey(buildPaymentMethodPath(method, methodById), method.type) to method.id
        }.toMutableMap()

        var imported = 0
        var skipped = 0
        var defaultMethodId: Long? = null

        records.sortedBy { if (it.parentName.isBlank()) 0 else 1 }.forEach { record ->
            val path = buildPath(record.parentName, record.name)
            val key = PaymentMethodKey(path, record.type)
            val existingId = methodIdByKey[key]
            if (existingId != null) {
                skipped++
                if (record.isDefault) defaultMethodId = existingId
                return@forEach
            }

            val parentId = if (record.parentName.isBlank()) {
                null
            } else {
                methodIdByKey[PaymentMethodKey(record.parentName, record.type)]
                    ?: methodIdByKey.entries.firstOrNull { it.key.path == record.parentName }?.value
            }
            if (record.parentName.isNotBlank() && parentId == null) {
                skipped++
                return@forEach
            }

            val insertedId = repository.insertPaymentMethod(
                PaymentMethod(
                    name = record.name,
                    icon = record.icon,
                    color = record.color,
                    type = record.type,
                    isDefault = false,
                    parentId = parentId,
                    sortOrder = record.sortOrder,
                    billingCycleType = record.billingCycleType.ifBlank { "none" },
                    billingCycleDay = record.billingCycleDay,
                    billingLimitType = record.billingLimitType.ifBlank { "credit" },
                    billingLimitAmount = record.billingLimitAmount
                )
            )
            methodById[insertedId] = PaymentMethod(
                id = insertedId,
                name = record.name,
                icon = record.icon,
                color = record.color,
                type = record.type,
                isDefault = false,
                parentId = parentId,
                sortOrder = record.sortOrder,
                billingCycleType = record.billingCycleType.ifBlank { "none" },
                billingCycleDay = record.billingCycleDay,
                billingLimitType = record.billingLimitType.ifBlank { "credit" },
                billingLimitAmount = record.billingLimitAmount
            )
            methodIdByKey[key] = insertedId
            if (record.isDefault) defaultMethodId = insertedId
            imported++
        }

        if (defaultMethodId != null) {
            repository.setDefaultPaymentMethod(defaultMethodId!!)
        }

        return imported to skipped
    }

    private suspend fun importProjects(records: List<ProjectRecord>): Pair<Int, Int> {
        if (records.isEmpty()) return 0 to 0

        val existingProjects = repository.getAllProjects().first()
        val projectByKey = existingProjects.associateBy { it.toProjectKey() }.toMutableMap()

        var imported = 0
        var skipped = 0

        records.forEach { record ->
            val key = record.toProjectKey()
            if (projectByKey.containsKey(key)) {
                skipped++
                return@forEach
            }

            val insertedId = repository.insertProject(
                Project(
                    name = record.name,
                    description = record.description,
                    defaultCurrency = record.defaultCurrency,
                    startDate = record.startDate,
                    endDate = record.endDate,
                    budget = record.budget,
                    isActive = record.isActive,
                    createdAt = record.createdAt,
                    updatedAt = record.updatedAt
                )
            )
            projectByKey[key] = Project(
                id = insertedId,
                name = record.name,
                description = record.description,
                defaultCurrency = record.defaultCurrency,
                startDate = record.startDate,
                endDate = record.endDate,
                budget = record.budget,
                isActive = record.isActive,
                createdAt = record.createdAt,
                updatedAt = record.updatedAt
            )
            imported++
        }

        return imported to skipped
    }

    private suspend fun importTransactions(records: List<TransactionRecord>): Int {
        if (records.isEmpty()) return 0

        val categories = repository.getAllCategories().first()
        val categoryById = categories.associateBy { it.id }
        val categoryIdByKey = categories.associate { category ->
            CategoryKey(buildCategoryPath(category, categoryById), category.isExpense) to category.id
        }

        val paymentMethods = repository.getAllPaymentMethods().first()
        val paymentMethodById = paymentMethods.associateBy { it.id }
        val paymentMethodIdByKey = paymentMethods.associate { method ->
            PaymentMethodKey(buildPaymentMethodPath(method, paymentMethodById), method.type) to method.id
        }

        val projects = repository.getAllProjects().first()
        val projectByKey = projects.associateBy { it.toProjectKey() }
        val projectByName = projects.associateBy { normalizeKey(it.name) }

        val defaultExpenseCategoryId = repository.getCategoryByNameAndType("其他支出", true)?.id
        val defaultIncomeCategoryId = repository.getCategoryByNameAndType("其他收入", false)?.id
        val defaultPaymentMethodId = repository.getPaymentMethodByName("現金")?.id

        var imported = 0
        records.forEach { record ->
            val categoryId = if (record.categoryPath.isNotBlank()) {
                categoryIdByKey[CategoryKey(record.categoryPath, record.isExpense)]
                    ?: categoryIdByKey.entries.firstOrNull {
                        it.key.isExpense == record.isExpense && it.key.path == record.categoryPath
                    }?.value
                    ?: if (record.isExpense) defaultExpenseCategoryId else defaultIncomeCategoryId
            } else {
                null
            }

            val paymentMethodId = if (record.paymentMethodPath.isNotBlank()) {
                paymentMethodIdByKey.entries.firstOrNull { it.key.path == record.paymentMethodPath }?.value
                    ?: repository.getPaymentMethodByName(record.paymentMethodPath)?.id
                    ?: defaultPaymentMethodId
            } else {
                null
            }

            val projectId = when {
                record.projectName.isBlank() -> null
                record.projectCurrency.isNotBlank() || record.projectStartDate != null || record.projectEndDate != null -> {
                    projectByKey[
                        ProjectKey(
                            name = normalizeKey(record.projectName),
                            defaultCurrency = normalizeKey(record.projectCurrency.ifBlank { "TWD" }),
                            startDate = record.projectStartDate,
                            endDate = record.projectEndDate
                        )
                    ]?.id
                }
                else -> projectByName[normalizeKey(record.projectName)]?.id
            }

            repository.insertTransaction(
                Transaction(
                    amount = record.amount,
                    personalAmount = record.personalAmount?.takeIf { record.isExpense && it > 0 },
                    note = record.note,
                    categoryId = categoryId,
                    projectId = projectId,
                    paymentMethodId = paymentMethodId,
                    date = record.date,
                    currency = record.currency,
                    exchangeRate = record.exchangeRate,
                    isExpense = record.isExpense,
                    source = record.source,
                    isDraft = record.isDraft,
                    createdAt = record.createdAt
                )
            )
            imported++
        }

        return imported
    }

    private fun Project.toProjectKey(): ProjectKey = ProjectKey(
        name = normalizeKey(name),
        defaultCurrency = normalizeKey(defaultCurrency),
        startDate = startDate,
        endDate = endDate
    )

    private fun ProjectRecord.toProjectKey(): ProjectKey = ProjectKey(
        name = normalizeKey(name),
        defaultCurrency = normalizeKey(defaultCurrency),
        startDate = startDate,
        endDate = endDate
    )

    private fun buildCategoryPath(
        category: Category,
        categoryById: Map<Long, Category>
    ): String {
        val parentName = category.parentId?.let { categoryById[it]?.name }.orEmpty()
        return buildPath(parentName, category.name)
    }

    private fun buildPaymentMethodPath(
        method: PaymentMethod,
        paymentMethodById: Map<Long, PaymentMethod>
    ): String {
        val parentName = method.parentId?.let { paymentMethodById[it]?.name }.orEmpty()
        return buildPath(parentName, method.name)
    }

    private fun buildPath(parentName: String, name: String): String =
        if (parentName.isBlank()) name else "$parentName > $name"

    private fun toCsvRow(values: List<String>): String =
        values.joinToString(",") { it.escapeCsv() }

    private fun String.escapeCsv(): String =
        if (contains(",") || contains("\"") || contains("\n")) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val currentToken = StringBuilder()

        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        currentToken.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentToken.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        tokens += currentToken.toString()
                        currentToken.clear()
                    }
                    else -> currentToken.append(c)
                }
            }
            i++
        }
        tokens += currentToken.toString()
        return tokens
    }

    private fun BufferedReader.readCsvRecord(): String? {
        val firstLine = readLine() ?: return null
        var record = firstLine
        while (record.count { it == '"' } % 2 != 0) {
            val nextLine = readLine() ?: break
            record += "\n$nextLine"
        }
        return record
    }

    private fun valueAt(values: List<String>, header: Map<String, Int>, name: String): String =
        values.getOrNull(header[name] ?: -1).orEmpty()

    private fun parseDateValue(value: String): Long? {
        if (value.isBlank()) return null
        value.toLongOrNull()?.let { return it }
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return runCatching { dateFormat.parse(value)?.time }.getOrNull()
    }

    private fun normalizeKey(value: String): String =
        value.trim().lowercase(Locale.ROOT)
}
