package com.rton.expensebucket.ui.screens

import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.amortizationEndYearMonth
import com.rton.expensebucket.data.model.amortizedShare
import com.rton.expensebucket.data.model.yearMonthFromMillis
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.ExpressionEvaluator
import java.util.Calendar
import java.util.Locale

data class AddTransactionFormDraft(
    val amountText: String = "",
    val personalAmountText: String = "",
    val selectedCategoryId: Long? = null,
    val isExpense: Boolean = true,
    val note: String = "",
    val selectedDateTime: Long = System.currentTimeMillis(),
    val selectedPaymentMethodId: Long? = null,
    val selectedProjectId: Long? = null,
    val selectedCurrency: String = "TWD",
    val exchangeRateText: String = "1",
    val amortizationEnabled: Boolean = false,
    val amortizationStartYearMonth: Int = yearMonthFromMillis(System.currentTimeMillis()),
    val amortizationMonthCount: Int = 6,
    val showNumpad: Boolean = true
)

object AddTransactionFormLogic {
    private const val OPERATORS = "+−×÷"
    private const val MAX_INPUT_Length = 20

    fun appendAmountKey(current: String, key: String): String {
        return when {
            key in OPERATORS -> {
                if (current.isBlank()) {
                    current
                } else if (current.last() in OPERATORS) {
                    current.dropLast(1) + key
                } else {
                    current + key
                }
            }
            key == "00" -> {
                val lastSegment = current.takeLastWhile { it !in OPERATORS }
                when {
                    current.length + 2 > MAX_INPUT_Length -> current
                    lastSegment.isEmpty() || lastSegment == "0" ->
                        if (lastSegment.isEmpty()) current + "0" else current
                    else -> current + "00"
                }
            }
            key == "0" -> {
                val lastSegment = current.takeLastWhile { it !in OPERATORS }
                when {
                    current.length >= MAX_INPUT_Length -> current
                    lastSegment == "0" -> current
                    else -> current + "0"
                }
            }
            else -> {
                if (current.length >= MAX_INPUT_Length) return current
                val lastSegment = current.takeLastWhile { it !in OPERATORS }
                if (lastSegment == "0") {
                    current.dropLast(1) + key
                } else {
                    current + key
                }
            }
        }
    }

    fun appendAmountDot(current: String): String {
        val lastSegment = current.takeLastWhile { it !in OPERATORS }
        return when {
            lastSegment.contains('.') -> current
            current.isEmpty() || current.last() in OPERATORS -> current + "0."
            else -> "$current."
        }
    }

    fun resolveAmount(amountText: String): Double? {
        val trimmed = amountText.trim().trimEnd { it in OPERATORS }
        if (trimmed.isBlank()) return null
        return if (trimmed.any { it in OPERATORS }) {
            ExpressionEvaluator.evaluate(trimmed)
        } else {
            trimmed.toDoubleOrNull()
        }
    }

    fun evaluateExpressionToText(amountText: String): String {
        if (amountText.isBlank()) return ""
        return resolveAmount(amountText)?.let { ExpressionEvaluator.formatResult(it) } ?: amountText
    }

    fun validatePersonalAmount(
        personalAmountText: String,
        isExpense: Boolean,
        resolvedAmount: Double?
    ): String? {
        if (!isExpense || personalAmountText.isBlank()) return null
        val parsed = resolveAmount(personalAmountText)
        return when {
            parsed == null -> "請輸入正確金額"
            parsed <= 0 -> "我的花費需大於 0"
            resolvedAmount != null && parsed > resolvedAmount -> "我的花費不能大於支出總額"
            else -> null
        }
    }

    fun canSave(
        resolvedAmount: Double?,
        parsedExchangeRate: Double?,
        personalAmountError: String?
    ): Boolean {
        return resolvedAmount != null &&
            resolvedAmount > 0 &&
            parsedExchangeRate != null &&
            parsedExchangeRate > 0 &&
            personalAmountError == null
    }

    fun hasUnsavedInput(
        amountText: String,
        selectedCategoryId: Long?,
        note: String,
        personalAmountText: String,
        isEditMode: Boolean
    ): Boolean {
        if (isEditMode) return false
        return amountText.isNotBlank() ||
            selectedCategoryId != null ||
            note.isNotBlank() ||
            personalAmountText.isNotBlank()
    }

    fun buildTransaction(
        existingTransaction: Transaction?,
        prefillSource: String?,
        draft: AddTransactionFormDraft,
        settlementCurrency: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Transaction? {
        val resolvedAmount = resolveAmount(draft.amountText) ?: return null
        val requiresExchangeRate = draft.selectedCurrency != settlementCurrency
        val exchangeRate = if (requiresExchangeRate) {
            draft.exchangeRateText.toDoubleOrNull()
        } else {
            1.0
        } ?: return null
        val personalError = validatePersonalAmount(
            personalAmountText = draft.personalAmountText,
            isExpense = draft.isExpense,
            resolvedAmount = resolvedAmount
        )
        if (!canSave(resolvedAmount, exchangeRate, personalError)) return null

        val parsedPersonalAmount = if (draft.isExpense && draft.personalAmountText.isNotBlank()) {
            resolveAmount(draft.personalAmountText)?.takeIf { it > 0 }
        } else {
            null
        }
        val activeAmortization = draft.isExpense &&
            draft.amortizationEnabled &&
            draft.amortizationMonthCount >= 2

        return Transaction(
            id = existingTransaction?.id ?: 0,
            amount = resolvedAmount,
            personalAmount = parsedPersonalAmount,
            note = draft.note.trim(),
            categoryId = draft.selectedCategoryId,
            isExpense = draft.isExpense,
            date = draft.selectedDateTime,
            source = existingTransaction?.source ?: prefillSource ?: "manual",
            projectId = draft.selectedProjectId,
            paymentMethodId = draft.selectedPaymentMethodId,
            currency = draft.selectedCurrency,
            exchangeRate = exchangeRate,
            isDraft = false,
            amortizationEnabled = activeAmortization,
            amortizationStartYearMonth = if (activeAmortization) draft.amortizationStartYearMonth else null,
            amortizationMonthCount = if (activeAmortization) draft.amortizationMonthCount else null,
            createdAt = existingTransaction?.createdAt ?: nowMillis
        )
    }

    fun resetForSaveAndAddAnother(draft: AddTransactionFormDraft): AddTransactionFormDraft {
        return draft.copy(
            amountText = "",
            personalAmountText = "",
            selectedCategoryId = null,
            note = "",
            amortizationEnabled = false,
            amortizationStartYearMonth = yearMonthFromMillis(draft.selectedDateTime),
            amortizationMonthCount = 6,
            showNumpad = true
        )
    }

    fun matchActiveProjectForDate(
        projects: List<Project>,
        targetDateTime: Long
    ): Project? {
        return projects
            .filter { project ->
                project.isActive &&
                    project.startDate != null &&
                    project.endDate != null &&
                    isWithinProjectDateRange(
                        targetDateTime = targetDateTime,
                        startDate = project.startDate,
                        endDate = project.endDate
                    )
            }
            .minByOrNull { project ->
                (project.endDate ?: Long.MAX_VALUE) - (project.startDate ?: Long.MIN_VALUE)
            }
    }

    fun isWithinProjectDateRange(
        targetDateTime: Long,
        startDate: Long?,
        endDate: Long?
    ): Boolean {
        if (startDate == null || endDate == null) return false
        val rangeStart = startOfDay(startDate)
        val rangeEnd = endOfDay(endDate)
        return targetDateTime in rangeStart..rangeEnd
    }

    fun formatAmortizationSummary(
        currency: String,
        amount: Double?,
        startYearMonth: Int,
        monthCount: Int
    ): String {
        val endYearMonth = amortizationEndYearMonth(startYearMonth, monthCount)
        val periodText = "${formatYearMonth(startYearMonth)} - ${formatYearMonth(endYearMonth)}"
        val amountText = amount?.let {
            " · 每月 ${CurrencyFormats.formatAmount(currency, amortizedShare(it, monthCount, 0))}"
        } ?: ""
        return "${periodText} (${monthCount}期)$amountText"
    }

    fun formatYearMonth(yearMonth: Int): String =
        "%04d/%02d".format(Locale.TAIWAN, yearMonth / 100, yearMonth % 100)

    fun formatEditableTransactionAmount(amount: Double): String =
        if (amount == amount.toLong().toDouble()) amount.toLong().toString() else amount.toString()

    fun startOfDay(timeMillis: Long): Long = Calendar.getInstance().run {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    fun endOfDay(timeMillis: Long): Long = Calendar.getInstance().run {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
        timeInMillis
    }
}
