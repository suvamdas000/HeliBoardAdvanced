package helium314.keyboard.latin.utils

import kotlin.math.pow

/**
 * Inline calculator: detects and evaluates math expressions from text before cursor.
 * Supports: + - * / ^ ( ) decimals, percentages, negative numbers.
 */
object InlineCalculator {

    // Grab expression-like tail at the end of the string, optionally followed by '='.
    private val GRAB_TAIL = Regex("""([0-9(][0-9+\-*/^().%\s×÷−]*[0-9)%])\s*=?\s*$""")

    data class CalcResult(
        val expression: String,    // the matched expression text
        val result: Double,        // evaluated result
        val expressionLength: Int  // chars to delete (including trailing =)
    )

    /**
     * Try to find and evaluate a math expression at the end of [textBeforeCursor].
     * Returns null if no valid expression is found.
     */
    fun detect(textBeforeCursor: String): CalcResult? {
        if (textBeforeCursor.isBlank()) return null

        val match = GRAB_TAIL.find(textBeforeCursor) ?: return null
        val exprText = match.groupValues[1].trim()

        val normalizedExpr = normalizeExpression(exprText)

        // Must contain at least one operator
        if (!normalizedExpr.any { it in "+-*/^%" } ||
            !exprText.any { it.isDigit() }) return null

        // Don't trigger on very short things like "1+"
        val cleanExpr = normalizedExpr.replace("\\s".toRegex(), "")
        if (cleanExpr.length < 3) return null

        return try {
            val result = evaluate(cleanExpr)
            if (result.isNaN() || result.isInfinite()) return null

            // Calculate how many chars to delete from cursor backwards
            val fullMatchEnd = match.range.last + 1
            val fullMatchStart = match.range.first
            // If the original text had trailing = or spaces after expression
            val deleteLength = textBeforeCursor.length - fullMatchStart

            CalcResult(exprText, result, deleteLength)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Format the result for display. Strips trailing zeros for clean output.
     */
    fun formatResult(value: Double): String {
        return if (value == value.toLong().toDouble() &&
            !value.isInfinite() &&
            kotlin.math.abs(value) < 1e15
        ) {
            value.toLong().toString()
        } else {
            // Up to 10 decimal places, strip trailing zeros
            val formatted = "%.10f".format(value).trimEnd('0').trimEnd('.')
            formatted
        }
    }

    // ========================
    // Recursive Descent Parser
    // ========================

    private class Parser(private val input: String) {
        private var pos = 0

        fun parse(): Double {
            val result = parseExpression()
            if (pos < input.length) throw IllegalArgumentException("Unexpected char: ${input[pos]}")
            return result
        }

        // expression = term (('+' | '-') term)*
        private fun parseExpression(): Double {
            var result = parseTerm()
            while (pos < input.length) {
                when (input[pos]) {
                    '+' -> { pos++; result += parseTerm() }
                    '-' -> { pos++; result -= parseTerm() }
                    else -> break
                }
            }
            return result
        }

        // term = power (('*' | '/') power)*
        private fun parseTerm(): Double {
            var result = parsePower()
            while (pos < input.length) {
                when (input[pos]) {
                    '*' -> { pos++; result *= parsePower() }
                    '/' -> {
                        pos++
                        val divisor = parsePower()
                        result /= divisor
                    }
                    else -> break
                }
            }
            return result
        }

        // power = unary ('^' unary)*
        private fun parsePower(): Double {
            var result = parseUnary()
            while (pos < input.length && input[pos] == '^') {
                pos++
                val exponent = parseUnary()
                result = result.pow(exponent)
            }
            return result
        }

        // unary = ('-' | '+') unary | postfix
        private fun parseUnary(): Double {
            if (pos < input.length) {
                when (input[pos]) {
                    '-' -> { pos++; return -parseUnary() }
                    '+' -> { pos++; return parseUnary() }
                }
            }
            return parsePostfix()
        }

        // postfix = primary '%'?
        private fun parsePostfix(): Double {
            var result = parsePrimary()
            if (pos < input.length && input[pos] == '%') {
                pos++
                result /= 100.0
            }
            return result
        }

        // primary = NUMBER | '(' expression ')'
        private fun parsePrimary(): Double {
            if (pos < input.length && input[pos] == '(') {
                pos++ // skip '('
                val result = parseExpression()
                if (pos < input.length && input[pos] == ')') {
                    pos++ // skip ')'
                } else {
                    throw IllegalArgumentException("Missing closing parenthesis")
                }
                return result
            }
            return parseNumber()
        }

        private fun parseNumber(): Double {
            val start = pos
            // digits and decimal point
            while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) {
                pos++
            }
            if (pos == start) throw IllegalArgumentException(
                "Expected number at position $pos"
            )
            return input.substring(start, pos).toDouble()
        }
    }

    fun evaluate(expression: String): Double {
        val cleaned = normalizeExpression(expression).replace(" ", "")
        if (cleaned.isEmpty()) throw IllegalArgumentException("Empty expression")
        return Parser(cleaned).parse()
    }

    private fun normalizeExpression(expression: String): String {
        return expression
            .replace('×', '*')
            .replace('÷', '/')
            .replace('−', '-')
            .replace("**", "^")
            .replace(Regex("""(?i)\bmod\b"""), "%")
    }
}
