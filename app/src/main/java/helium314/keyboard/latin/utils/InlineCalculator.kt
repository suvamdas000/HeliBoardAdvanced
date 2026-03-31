package helium314.keyboard.latin.utils

import java.util.Stack
import kotlin.math.*

/**
 * InlineCalculator — Evaluates mathematical expressions typed inline.
 * Supports: +, -, *, /, ^, %, parentheses, sqrt(), sin(), cos(), tan(), log(), ln(), abs(), pi, e
 * 
 * Usage:
 *   val result = InlineCalculator.evaluate("2+3*4")  // returns "14"
 *   val result = InlineCalculator.evaluate("sqrt(16)") // returns "4"
 */
object InlineCalculator {

    // Regex to detect if text contains a math expression
    // Matches patterns like: "2+3", "100/5", "sqrt(9)", "(2+3)*4", "3^2", "15%3", etc.
    private val MATH_EXPRESSION_PATTERN = Regex(
        """^[\s]*[-]?[\d]*\.?[\d]+[\s]*([+\-*/^%][\s]*[-]?[\d]*\.?[\d]+[\s]*)+$|""" +  // simple: 2+3*4
        """^[\s]*\(.*\)[\s]*([+\-*/^%].*)?$|""" +  // parenthesized: (2+3)*4
        """^[\s]*(sqrt|sin|cos|tan|log|ln|abs)\s*\(.*\)[\s]*([+\-*/^%].*)?$|""" +  // functions: sqrt(16)
        """^[\s]*[-]?[\d]*\.?[\d]+[\s]*[+\-*/^%].*$"""  // general: anything with operator
    )

    // Simpler detection: does the string look like a math expression?
    private val CONTAINS_OPERATOR = Regex(""".*\d+\s*[+\-*/^%]\s*\d+.*""")
    private val CONTAINS_FUNCTION = Regex("""(sqrt|sin|cos|tan|log|ln|abs)\s*\(""")
    private val JUST_A_NUMBER = Regex("""^[\s]*[-]?[\d]+\.?[\d]*[\s]*$""")

    /**
     * Check if the given text looks like a math expression worth evaluating.
     */
    fun isMathExpression(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (JUST_A_NUMBER.matches(trimmed)) return false // Don't evaluate plain numbers

        return CONTAINS_OPERATOR.containsMatchIn(trimmed) ||
               CONTAINS_FUNCTION.containsMatchIn(trimmed)
    }

    /**
     * Evaluate a math expression string and return the result as a formatted string.
     * Returns null if the expression is invalid or cannot be evaluated.
     */
    fun evaluate(expression: String): String? {
        return try {
            val sanitized = sanitize(expression)
            if (sanitized.isBlank()) return null

            val tokens = tokenize(sanitized)
            if (tokens.isEmpty()) return null

            val result = parseExpression(tokens, 0).first

            if (result.isNaN() || result.isInfinite()) return null

            formatResult(result)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Try to extract a math expression from the end of the typed text.
     * Returns a Pair of (expression, result) or null.
     */
    fun findAndEvaluate(text: String): CalculatorResult? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        // Strategy 1: Try the entire text as an expression
        if (isMathExpression(trimmed)) {
            val result = evaluate(trimmed)
            if (result != null) {
                return CalculatorResult(
                    expression = trimmed,
                    result = result,
                    startIndex = 0,
                    fullText = text
                )
            }
        }

        // Strategy 2: Try to find expression at the end of text (after last space/newline)
        val lastSeparator = maxOf(
            trimmed.lastIndexOf(' '),
            trimmed.lastIndexOf('\n'),
            trimmed.lastIndexOf('\t')
        )
        if (lastSeparator >= 0 && lastSeparator < trimmed.length - 1) {
            val candidate = trimmed.substring(lastSeparator + 1)
            if (isMathExpression(candidate)) {
                val result = evaluate(candidate)
                if (result != null) {
                    return CalculatorResult(
                        expression = candidate,
                        result = result,
                        startIndex = lastSeparator + 1,
                        fullText = text
                    )
                }
            }
        }

        // Strategy 3: Try to find expression starting with '=' trigger
        // e.g., user types "=2+3" → evaluate "2+3"
        if (trimmed.startsWith("=") && trimmed.length > 1) {
            val expr = trimmed.substring(1)
            if (isMathExpression(expr)) {
                val result = evaluate(expr)
                if (result != null) {
                    return CalculatorResult(
                        expression = trimmed,
                        result = result,
                        startIndex = 0,
                        fullText = text
                    )
                }
            }
        }

        return null
    }

    // ========================
    // TOKENIZER
    // ========================

    private sealed class Token {
        data class Number(val value: Double) : Token()
        data class Op(val op: Char) : Token()
        data class Func(val name: String) : Token()
        object LParen : Token()
        object RParen : Token()
    }

    private fun sanitize(expr: String): String {
        return expr.trim()
            .replace("×", "*")
            .replace("÷", "/")
            .replace("−", "-")
            .replace(",", ".")
            .replace("π", "pi")
            .lowercase()
    }

    private fun tokenize(expr: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0

        while (i < expr.length) {
            val c = expr[i]

            when {
                c.isWhitespace() -> i++

                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                    val numStr = expr.substring(start, i)
                    tokens.add(Token.Number(numStr.toDouble()))
                }

                c.isLetter() -> {
                    val start = i
                    while (i < expr.length && expr[i].isLetter()) i++
                    val word = expr.substring(start, i)
                    when (word) {
                        "pi" -> tokens.add(Token.Number(Math.PI))
                        "e" -> tokens.add(Token.Number(Math.E))
                        "sqrt", "sin", "cos", "tan", "log", "ln", "abs",
                        "asin", "acos", "atan", "ceil", "floor", "round" -> {
                            tokens.add(Token.Func(word))
                        }
                        else -> throw IllegalArgumentException("Unknown identifier: $word")
                    }
                }

                c == '(' -> { tokens.add(Token.LParen); i++ }
                c == ')' -> { tokens.add(Token.RParen); i++ }

                c == '+' || c == '-' -> {
                    // Handle unary minus/plus
                    if (tokens.isEmpty() || tokens.last() is Token.LParen || tokens.last() is Token.Op) {
                        // Unary: read the number
                        i++
                        if (i < expr.length && (expr[i].isDigit() || expr[i] == '.' || expr[i].isLetter())) {
                            if (expr[i].isDigit() || expr[i] == '.') {
                                val start = i
                                while (i < expr.length && (expr[i].isDigit() || expr[i] == '.')) i++
                                val numStr = expr.substring(start, i)
                                val value = numStr.toDouble()
                                tokens.add(Token.Number(if (c == '-') -value else value))
                            } else {
                                // Unary before function or constant — insert 0 and operator
                                tokens.add(Token.Number(0.0))
                                tokens.add(Token.Op(c))
                            }
                        } else {
                            tokens.add(Token.Number(0.0))
                            tokens.add(Token.Op(c))
                        }
                    } else {
                        tokens.add(Token.Op(c)); i++
                    }
                }

                c == '*' || c == '/' || c == '^' || c == '%' -> {
                    tokens.add(Token.Op(c)); i++
                }

                else -> throw IllegalArgumentException("Unexpected character: $c")
            }
        }

        return tokens
    }

    // ========================
    // RECURSIVE DESCENT PARSER
    // ========================

    // Grammar:
    // expression = term (('+' | '-') term)*
    // term       = power (('*' | '/' | '%') power)*
    // power      = unary ('^' unary)*
    // unary      = ('-')? factor
    // factor     = NUMBER | '(' expression ')' | FUNC '(' expression ')'

    private fun parseExpression(tokens: List<Token>, pos: Int): Pair<Double, Int> {
        var (left, nextPos) = parseTerm(tokens, pos)

        while (nextPos < tokens.size) {
            val token = tokens[nextPos]
            if (token is Token.Op && (token.op == '+' || token.op == '-')) {
                val (right, newPos) = parseTerm(tokens, nextPos + 1)
                left = if (token.op == '+') left + right else left - right
                nextPos = newPos
            } else {
                break
            }
        }

        return Pair(left, nextPos)
    }

    private fun parseTerm(tokens: List<Token>, pos: Int): Pair<Double, Int> {
        var (left, nextPos) = parsePower(tokens, pos)

        while (nextPos < tokens.size) {
            val token = tokens[nextPos]
            if (token is Token.Op && (token.op == '*' || token.op == '/' || token.op == '%')) {
                val (right, newPos) = parsePower(tokens, nextPos + 1)
                left = when (token.op) {
                    '*' -> left * right
                    '/' -> if (right != 0.0) left / right else Double.NaN
                    '%' -> left % right
                    else -> left
                }
                nextPos = newPos
            } else {
                break
            }
        }

        return Pair(left, nextPos)
    }

    private fun parsePower(tokens: List<Token>, pos: Int): Pair<Double, Int> {
        var (base, nextPos) = parseFactor(tokens, pos)

        while (nextPos < tokens.size) {
            val token = tokens[nextPos]
            if (token is Token.Op && token.op == '^') {
                val (exponent, newPos) = parseFactor(tokens, nextPos + 1)
                base = base.pow(exponent)
                nextPos = newPos
            } else {
                break
            }
        }

        return Pair(base, nextPos)
    }

    private fun parseFactor(tokens: List<Token>, pos: Int): Pair<Double, Int> {
        if (pos >= tokens.size) throw IllegalArgumentException("Unexpected end of expression")

        return when (val token = tokens[pos]) {
            is Token.Number -> Pair(token.value, pos + 1)

            is Token.LParen -> {
                val (value, nextPos) = parseExpression(tokens, pos + 1)
                if (nextPos < tokens.size && tokens[nextPos] is Token.RParen) {
                    Pair(value, nextPos + 1)
                } else {
                    throw IllegalArgumentException("Missing closing parenthesis")
                }
            }

            is Token.Func -> {
                // Expect '(' after function name
                if (pos + 1 < tokens.size && tokens[pos + 1] is Token.LParen) {
                    val (argValue, nextPos) = parseExpression(tokens, pos + 2)
                    if (nextPos < tokens.size && tokens[nextPos] is Token.RParen) {
                        val result = applyFunction(token.name, argValue)
                        Pair(result, nextPos + 1)
                    } else {
                        throw IllegalArgumentException("Missing closing parenthesis for function ${token.name}")
                    }
                } else {
                    throw IllegalArgumentException("Expected '(' after function ${token.name}")
                }
            }

            is Token.Op -> {
                if (token.op == '-') {
                    val (value, nextPos) = parseFactor(tokens, pos + 1)
                    Pair(-value, nextPos)
                } else if (token.op == '+') {
                    parseFactor(tokens, pos + 1)
                } else {
                    throw IllegalArgumentException("Unexpected operator: ${token.op}")
                }
            }

            else -> throw IllegalArgumentException("Unexpected token: $token")
        }
    }

    private fun applyFunction(name: String, value: Double): Double {
        return when (name) {
            "sqrt" -> sqrt(value)
            "sin" -> sin(Math.toRadians(value))
            "cos" -> cos(Math.toRadians(value))
            "tan" -> tan(Math.toRadians(value))
            "asin" -> Math.toDegrees(asin(value))
            "acos" -> Math.toDegrees(acos(value))
            "atan" -> Math.toDegrees(atan(value))
            "log" -> log10(value)
            "ln" -> ln(value)
            "abs" -> abs(value)
            "ceil" -> ceil(value)
            "floor" -> floor(value)
            "round" -> round(value).toDouble()
            else -> throw IllegalArgumentException("Unknown function: $name")
        }
    }

    // ========================
    // RESULT FORMATTING
    // ========================

    private fun formatResult(value: Double): String {
        // If the result is effectively an integer, show without decimal
        return if (value == value.toLong().toDouble() && !value.isInfinite() && abs(value) < 1e15) {
            value.toLong().toString()
        } else {
            // Show up to 10 significant digits, remove trailing zeros
            val formatted = "%.10g".format(value)
            if (formatted.contains('.')) {
                formatted.trimEnd('0').trimEnd('.')
            } else {
                formatted
            }
        }
    }

    /**
     * Data class holding the result of an inline calculation.
     */
    data class CalculatorResult(
        val expression: String,   // The detected math expression
        val result: String,       // The computed result as a string
        val startIndex: Int,      // Where the expression starts in the full text
        val fullText: String      // The original full text
    ) {
        /**
         * Returns the text with the expression replaced by the result.
         */
        fun getReplacedText(): String {
            return fullText.substring(0, startIndex) + result
        }

        /**
         * Returns the text with "= result" appended after the expression.
         */
        fun getAppendedText(): String {
            return "$fullText = $result"
        }
    }
}
