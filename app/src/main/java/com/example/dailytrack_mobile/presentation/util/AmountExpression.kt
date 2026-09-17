package com.example.dailytrack_mobile.presentation.util

import kotlin.math.abs
import kotlin.math.round

/**
 * Arithmetic for amount fields, so a total can be typed the way it is counted —
 * `120+40+15` for three things on one bill — instead of being added up first.
 *
 * Deliberately a hand-written recursive-descent parser over `+ - * / ( )` only:
 * a general expression engine would accept far more than an amount field ever
 * needs, and there is nothing here worth the extra surface.
 */
object AmountExpression {

    /** Characters an amount field may contain. Anything else is rejected on input. */
    private val ALLOWED = Regex("^[0-9+\\-*/(). ]*$")

    /** True when [input] is just a plain number, needing no evaluation preview. */
    fun isPlainNumber(input: String): Boolean =
        input.isBlank() || input.trim().toDoubleOrNull() != null

    fun isAllowedInput(input: String): Boolean = ALLOWED.matches(input)

    /**
     * Evaluates [input], returning null when it is empty, malformed, or produces
     * a value an amount cannot hold (negative, infinite, NaN).
     *
     * Results are rounded to 2 decimal places — the precision the field itself
     * accepts — so `100/3` reads as `33.33` rather than a repeating fraction.
     */
    fun evaluate(input: String): Double? {
        val cleaned = input.replace(" ", "")
        if (cleaned.isEmpty()) return null
        if (!ALLOWED.matches(cleaned)) return null

        val value = try {
            Parser(cleaned).parse()
        } catch (_: IllegalArgumentException) {
            null
        } ?: return null

        if (value.isNaN() || value.isInfinite() || value < 0.0) return null
        return round(value * 100.0) / 100.0
    }

    /**
     * The evaluated result formatted for display, or null when there is nothing
     * useful to show — a plain number needs no echo, and a half-typed
     * expression like `120+` should not flash an error at the user.
     */
    fun previewFor(input: String): String? {
        if (isPlainNumber(input)) return null
        val value = evaluate(input) ?: return null
        return formatAmount(value)
    }

    /** Trailing `.0` dropped; two decimals kept only when they carry a value. */
    fun formatAmount(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        return if (abs(rounded - rounded.toLong()) < 0.005) {
            rounded.toLong().toString()
        } else {
            String.format("%.2f", rounded)
        }
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun parse(): Double? {
            val value = parseExpression()
            // A trailing operator or stray bracket means the user is mid-type.
            if (pos != text.length) return null
            return value
        }

        /** expression := term (('+' | '-') term)* */
        private fun parseExpression(): Double? {
            var left = parseTerm() ?: return null
            while (pos < text.length && (text[pos] == '+' || text[pos] == '-')) {
                val op = text[pos]
                pos++
                val right = parseTerm() ?: return null
                left = if (op == '+') left + right else left - right
            }
            return left
        }

        /** term := factor (('*' | '/') factor)* */
        private fun parseTerm(): Double? {
            var left = parseFactor() ?: return null
            while (pos < text.length && (text[pos] == '*' || text[pos] == '/')) {
                val op = text[pos]
                pos++
                val right = parseFactor() ?: return null
                if (op == '/') {
                    if (right == 0.0) return null
                    left /= right
                } else {
                    left *= right
                }
            }
            return left
        }

        /**
         * factor := '-'? (number | '(' expression ')')
         *
         * The leading minus is only honoured at the start of an expression or
         * straight after an opening bracket. That keeps "(-5+20)" working while
         * "120++40" and "120--40" stay errors rather than quietly becoming 160 —
         * a typo in an amount field should stop the save, not change it.
         */
        private fun parseFactor(): Double? {
            if (pos >= text.length) return null

            if (text[pos] == '-' && (pos == 0 || text[pos - 1] == '(')) {
                pos++
                return parseFactor()?.let { -it }
            }

            if (text[pos] == '(') {
                pos++
                val inner = parseExpression() ?: return null
                if (pos >= text.length || text[pos] != ')') return null
                pos++
                return inner
            }

            val start = pos
            var seenDot = false
            while (pos < text.length && (text[pos].isDigit() || (text[pos] == '.' && !seenDot))) {
                if (text[pos] == '.') seenDot = true
                pos++
            }
            if (pos == start) return null
            return text.substring(start, pos).toDoubleOrNull()
        }
    }
}
