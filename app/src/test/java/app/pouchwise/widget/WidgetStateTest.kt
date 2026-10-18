package app.pouchwise.widget

import app.pouchwise.model.LimitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateTest {
    @Test
    fun ringFillsTowardsTheLimitAndFlagsOveruse() {
        val half = WidgetState(LimitMode.COUNT, count = 5, mg = 40.0, limit = 10, hasTypes = true)
        assertEquals(0.5f, half.ratio, 1e-6f)
        assertFalse(half.over)

        val exact = half.copy(count = 10)
        assertEquals(1f, exact.ratio, 1e-6f)
        assertFalse(exact.over)

        val over = half.copy(count = 12)
        assertEquals(1f, over.ratio, 1e-6f)
        assertTrue(over.over)
    }

    @Test
    fun mgModeUsesMilligramsAndZeroLimitCountsAnythingAsFull() {
        val mg = WidgetState(LimitMode.MG, count = 3, mg = 24.0, limit = 60, hasTypes = false)
        assertEquals(0.4f, mg.ratio, 1e-6f)
        assertFalse(mg.over)

        val noLimit = WidgetState(LimitMode.COUNT, count = 1, mg = 8.0, limit = 0, hasTypes = false)
        assertEquals(1f, noLimit.ratio, 1e-6f)
        assertTrue(noLimit.over)
        assertEquals(0f, noLimit.copy(count = 0, mg = 0.0).ratio, 1e-6f)
    }
}
