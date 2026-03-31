package helium314.keyboard.latin.utils

import android.content.SharedPreferences
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo

/**
 * Manages inline calculator state and integrates with the suggestion system.
 * 
 * When the user types a math expression (e.g., "2+3*4"), this manager:
 * 1. Detects the expression
 * 2. Evaluates it
 * 3. Creates SuggestedWordInfo entries to show in the suggestion strip
 */
class InlineCalculatorManager(private val prefs: SharedPreferences) {

    companion object {
        const val PREF_INLINE_CALCULATOR_ENABLED = "pref_inline_calculator_enabled"
        const val CALCULATOR_SUGGESTION_SOURCE = "calculator"

        // Score for calculator suggestions (high priority to appear first)
        const val CALCULATOR_RESULT_SCORE = Integer.MAX_VALUE
        const val CALCULATOR_APPEND_SCORE = Integer.MAX_VALUE - 1
    }

    private var lastExpression: String? = null
    private var lastResult: InlineCalculator.CalcResult? = null

    val isEnabled: Boolean
        get() = prefs.getBoolean(PREF_INLINE_CALCULATOR_ENABLED, true)

    /**
     * Process the current composing text / text before cursor and return
     * calculator suggestions if a math expression is detected.
     * 
     * @param textBeforeCursor The text before the cursor position
     * @param composingText The currently composing text (word being typed)
     * @return List of SuggestedWordInfo for calculator results, or empty list
     */
    fun getCalculatorSuggestions(
        textBeforeCursor: String?,
        composingText: String?
    ): List<SuggestedWordInfo> {
        if (!isEnabled) return emptyList()

        val textToEvaluate = composingText?.takeIf { it.isNotBlank() }
            ?: textBeforeCursor?.takeIf { it.isNotBlank() }
            ?: return emptyList()

        val calcResult = InlineCalculator.detect(textToEvaluate)
            ?: return emptyList()
        val formattedResult = InlineCalculator.formatResult(calcResult.result)

        // Cache for later use
        lastExpression = calcResult.expression
        lastResult = calcResult

        val suggestions = mutableListOf<SuggestedWordInfo>()

        // Primary suggestion: just the result (e.g., "14")
        suggestions.add(
            SuggestedWordInfo(
                formattedResult,                            // word
                "",                                          // prevWordsContext
                CALCULATOR_RESULT_SCORE,                     // score
                SuggestedWordInfo.KIND_TYPED,                // kind
                null,                                        // sourceDict
                SuggestedWordInfo.NOT_AN_INDEX,              // indexOfTouchPointOfSecondWord
                SuggestedWordInfo.NOT_A_CONFIDENCE            // autoCommitFirstWordConfidence
            )
        )

        // Secondary suggestion: expression = result (e.g., "2+3*4 = 14")
        suggestions.add(
            SuggestedWordInfo(
                "${calcResult.expression} = $formattedResult",
                "",
                CALCULATOR_APPEND_SCORE,
                SuggestedWordInfo.KIND_TYPED,
                null,
                SuggestedWordInfo.NOT_AN_INDEX,
                SuggestedWordInfo.NOT_A_CONFIDENCE
            )
        )

        return suggestions
    }

    /**
     * Get the last calculated result.
     */
    fun getLastResult(): InlineCalculator.CalcResult? = lastResult

    /**
     * Clear cached state.
     */
    fun reset() {
        lastExpression = null
        lastResult = null
    }
}
