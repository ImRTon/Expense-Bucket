package com.rton.expensebucket.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.repository.ExpenseBucketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExportImportManager @Inject constructor(
    private val repository: ExpenseBucketRepository
) {
    // Escapes a string to be valid in CSV
    private fun String.escapeCsv(): String {
        return if (this.contains(",") || this.contains("\"") || this.contains("\n")) {
            "\"${this.replace("\"", "\"\"")}\""
        } else {
            this
        }
    }

    suspend fun exportToCsv(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val transactions = repository.getAllTransactions().first()
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, "UTF-8").use { writer ->
                    // Write header
                    writer.write("amount,note,categoryName,projectName,paymentMethodName,date,currency,exchangeRate,isExpense,source,isDraft,createdAt\n")
                    
                    for (t in transactions) {
                        val categoryName = t.categoryId?.let { repository.getCategoryById(it)?.name } ?: ""
                        val projectName = t.projectId?.let { repository.getProjectById(it)?.name } ?: ""
                        val paymentMethodName = t.paymentMethodId?.let { repository.getPaymentMethodById(it)?.name } ?: ""
                        
                        val row = listOf(
                            t.amount.toString(),
                            t.note.escapeCsv(),
                            categoryName.escapeCsv(),
                            projectName.escapeCsv(),
                            paymentMethodName.escapeCsv(),
                            dateFormat.format(Date(t.date)).escapeCsv(),
                            t.currency.escapeCsv(),
                            t.exchangeRate.toString(),
                            t.isExpense.toString(),
                            t.source.escapeCsv(),
                            t.isDraft.toString(),
                            dateFormat.format(Date(t.createdAt)).escapeCsv()
                        ).joinToString(",")
                        writer.write("$row\n")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("DataExportImport", "Failed to export data", e)
            Result.failure(e)
        }
    }

    suspend fun importFromCsv(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var importedCount = 0
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    Log.d("DataExportImport", "Reading CSV lines...")
                    val header = reader.readLine() // Skip header
                    if (header == null) return@withContext Result.failure(Exception("Empty file"))
                    
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if (line.isBlank()) {
                            line = reader.readLine()
                            continue
                        }
                        var currentLine = line
                        // Handle newlines inside quotes
                        while (currentLine.count { it == '"' } % 2 != 0) {
                            val nextLine = reader.readLine() ?: break
                            currentLine += "\n$nextLine"
                        }
                        
                        val tokens = parseCsvLine(currentLine)
                        if (tokens.size >= 12) {
                            val amount = tokens[0].toDoubleOrNull() ?: 0.0
                            val note = tokens[1]
                            val categoryName = tokens[2]
                            val projectName = tokens[3]
                            val paymentMethodName = tokens[4]
                            
                            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val date = try {
                                dateFormat.parse(tokens[5])?.time ?: tokens[5].toLongOrNull() ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                tokens[5].toLongOrNull() ?: System.currentTimeMillis()
                            }

                            val currency = tokens[6].ifEmpty { "TWD" }
                            val exchangeRate = tokens[7].toDoubleOrNull() ?: 1.0
                            val isExpense = tokens[8].toBooleanStrictOrNull() ?: true
                            val source = tokens[9].ifEmpty { "import" }
                            val isDraft = tokens[10].toBooleanStrictOrNull() ?: false

                            val createdAt = try {
                                dateFormat.parse(tokens[11])?.time ?: tokens[11].toLongOrNull() ?: System.currentTimeMillis()
                            } catch (e: Exception) {
                                tokens[11].toLongOrNull() ?: System.currentTimeMillis()
                            }
                            
                            val categoryId = if (categoryName.isNotEmpty()) {
                                repository.getCategoryByNameAndType(categoryName, isExpense)?.id 
                                    ?: repository.getCategoryByNameAndType(if (isExpense) "其他支出" else "其他收入", isExpense)?.id
                            } else null

                            
                            val paymentMethodId = if (paymentMethodName.isNotEmpty()) {
                                repository.getPaymentMethodByName(paymentMethodName)?.id
                                    ?: repository.getPaymentMethodByName("現金")?.id
                            } else null

                            val projectId = if (projectName.isNotEmpty()) {
                                repository.getProjectByName(projectName)?.id
                            } else null
                            
                            val transaction = Transaction(
                                amount = amount,
                                note = note,
                                categoryId = categoryId,
                                projectId = projectId,
                                paymentMethodId = paymentMethodId,
                                date = date,
                                currency = currency,
                                exchangeRate = exchangeRate,
                                isExpense = isExpense,
                                source = source,
                                isDraft = isDraft,
                                createdAt = createdAt
                            )
                            repository.insertTransaction(transaction)
                            importedCount++
                        }
                        
                        line = reader.readLine()
                    }
                }
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            Log.e("DataExportImport", "Failed to import data", e)
            Result.failure(e)
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val currentToken = java.lang.StringBuilder()
        
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        currentToken.append('"')
                        i++ // Skip the escaped quote
                    } else {
                        inQuotes = false
                    }
                } else {
                    currentToken.append(c)
                }
            } else {
                if (c == '"') {
                    inQuotes = true
                } else if (c == ',') {
                    tokens.add(currentToken.toString())
                    currentToken.clear()
                } else {
                    currentToken.append(c)
                }
            }
            i++
        }
        tokens.add(currentToken.toString())
        return tokens
    }
}
