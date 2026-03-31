// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.utils.InlineCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InlineCalculatorTest {
    @Test fun detectsSimpleDivision() {
        val result = InlineCalculator.detect("12/10")
        assertNotNull(result)
        assertEquals("12/10", result.expression)
        assertEquals("1.2", InlineCalculator.formatResult(result.result))
    }

    @Test fun supportsUnicodeOperatorsAndParens() {
        val result = InlineCalculator.detect("((4×5)−10)÷2")
        assertNotNull(result)
        assertEquals("5", InlineCalculator.formatResult(result.result))
    }

    @Test fun supportsModAndPowerAliases() {
        val power = InlineCalculator.detect("2**4")
        assertNotNull(power)
        assertEquals("16", InlineCalculator.formatResult(power.result))

        val mod = InlineCalculator.detect("20 mod 6")
        assertNotNull(mod)
        assertEquals("2", InlineCalculator.formatResult(mod.result))
    }

    @Test fun ignoresIncompleteOrNonMathInput() {
        assertNull(InlineCalculator.detect("hello"))
        assertNull(InlineCalculator.detect("1+"))
        assertNull(InlineCalculator.detect("123"))
    }
}
