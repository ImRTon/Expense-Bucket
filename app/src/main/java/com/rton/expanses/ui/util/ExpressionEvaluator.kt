package com.rton.expanses.ui.util

/**
 * Simple expression evaluator for basic arithmetic.
 * Supports: + - × ÷ (and * /)
 *
 * Examples:
 *   "200+600"     → 800.0
 *   "1000-350"    → 650.0
 *   "3×150"       → 450.0
 *   "100+50×2"    → 200.0 (left-to-right, no precedence — keeps it simple for expense entry)
 *
 * Note: This uses LEFT-TO-RIGHT evaluation (no operator precedence).
 * This matches how most calculator apps on phones work for quick expense calculations.
 */
object ExpressionEvaluator {

    /**
     * Evaluate a simple arithmetic expression.
     * Returns null if the expression is invalid.
     */
    fun evaluate(expression: String): Double? {
        if (expression.isBlank()) return null

        val cleaned = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")

        // Check if it's just a number
        cleaned.toDoubleOrNull()?.let { return it }

        // Tokenize: split into numbers and operators
        val numbers = mutableListOf<Double>()
        val operators = mutableListOf<Char>()

        var currentNum = StringBuilder()
        for (ch in cleaned) {
            when {
                ch in "+-*/" && currentNum.isNotEmpty() -> {
                    numbers.add(currentNum.toString().toDoubleOrNull() ?: return null)
                    currentNum = StringBuilder()
                    operators.add(ch)
                }
                ch.isDigit() || ch == '.' -> currentNum.append(ch)
                else -> return null // invalid character
            }
        }

        // Add the last number
        if (currentNum.isNotEmpty()) {
            numbers.add(currentNum.toString().toDoubleOrNull() ?: return null)
        }

        if (numbers.isEmpty()) return null
        if (numbers.size != operators.size + 1) return null

        // Left-to-right evaluation (no precedence)
        var result = numbers[0]
        for (i in operators.indices) {
            val right = numbers[i + 1]
            result = when (operators[i]) {
                '+' -> result + right
                '-' -> result - right
                '*' -> result * right
                '/' -> if (right != 0.0) result / right else return null // div by zero
                else -> return null
            }
        }

        return result
    }

    /**
     * Check if the expression contains any operator.
     */
    fun hasOperator(expression: String): Boolean {
        return expression.any { it in "+−×÷" || (it in "*/" && expression.length > 1) }
    }

    /**
     * Format the result: remove trailing .0 for whole numbers.
     */
    fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format("%.2f", value)
        }
    }
}
