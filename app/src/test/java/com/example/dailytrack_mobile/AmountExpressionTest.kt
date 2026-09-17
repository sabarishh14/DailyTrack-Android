package com.example.dailytrack_mobile

import com.example.dailytrack_mobile.presentation.util.AmountExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountExpressionTest {

    @Test
    fun `plain numbers evaluate to themselves`() {
        assertEquals(120.0, AmountExpression.evaluate("120"))
        assertEquals(120.5, AmountExpression.evaluate("120.50"))
    }

    @Test
    fun `addition chain sums every term`() {
        assertEquals(175.0, AmountExpression.evaluate("120+40+15"))
    }

    @Test
    fun `multiplication binds tighter than addition`() {
        assertEquals(110.0, AmountExpression.evaluate("10+20*5"))
    }

    @Test
    fun `brackets override precedence`() {
        assertEquals(150.0, AmountExpression.evaluate("(10+20)*5"))
    }

    @Test
    fun `spaces are ignored`() {
        assertEquals(175.0, AmountExpression.evaluate(" 120 + 40 + 15 "))
    }

    @Test
    fun `results round to two decimals`() {
        assertEquals(33.33, AmountExpression.evaluate("100/3"))
    }

    @Test
    fun `half-typed expressions yield nothing rather than a wrong total`() {
        assertNull(AmountExpression.evaluate("120+"))
        assertNull(AmountExpression.evaluate("(120+40"))
        assertNull(AmountExpression.evaluate("120++40"))
        assertNull(AmountExpression.evaluate("120--40"))
        assertNull(AmountExpression.evaluate(""))
    }

    @Test
    fun `a leading minus inside brackets still parses`() {
        assertEquals(15.0, AmountExpression.evaluate("(-5+20)"))
    }

    @Test
    fun `division by zero is rejected`() {
        assertNull(AmountExpression.evaluate("120/0"))
    }

    @Test
    fun `negative results are rejected because an amount cannot be negative`() {
        assertNull(AmountExpression.evaluate("40-120"))
    }

    @Test
    fun `letters are rejected outright`() {
        assertFalse(AmountExpression.isAllowedInput("12a"))
        assertNull(AmountExpression.evaluate("12a"))
    }

    @Test
    fun `preview is offered only for expressions`() {
        assertNull(AmountExpression.previewFor("120"))
        assertNull(AmountExpression.previewFor(""))
        assertEquals("175", AmountExpression.previewFor("120+40+15"))
        assertEquals("33.33", AmountExpression.previewFor("100/3"))
    }

    @Test
    fun `whole results drop the decimal tail`() {
        assertEquals("200", AmountExpression.formatAmount(200.0))
        assertEquals("200.25", AmountExpression.formatAmount(200.25))
    }

    @Test
    fun `allowed input covers the operator keys the form offers`() {
        assertTrue(AmountExpression.isAllowedInput("120+40-15*2/3()"))
    }
}
