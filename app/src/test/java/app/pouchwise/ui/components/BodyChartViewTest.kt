package app.pouchwise.ui.components

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyChartViewTest {
    private fun view() = BodyChartView(8f, 2f / 3f, 2f..168f)

    @Test
    fun pinchingKeepsTheTimeUnderTheFingers() = runBlocking {
        val v = view()
        v.reachH = -720f..48f
        val atFocus = v.endH - v.windowH + 0.25f * v.windowH
        v.pinch(focus = 0.25f, zoom = 2f, pan = 0f)
        assertEquals(4f, v.windowH, 1e-4f)
        assertEquals(atFocus, v.endH - v.windowH + 0.25f * v.windowH, 1e-4f)
        // No closer than 2 h, no wider than 7 days.
        v.pinch(0.5f, 100f, 0f)
        assertEquals(2f, v.windowH, 1e-4f)
        v.pinch(0.5f, 0.001f, 0f)
        assertEquals(168f, v.windowH, 1e-4f)
    }

    @Test
    fun theWindowStaysWithinTheData() = runBlocking {
        val v = view()
        v.reachH = -10f..5f
        v.dragBy(1000f)
        assertEquals(5f, v.endH, 1e-4f)
        v.dragBy(-1000f)
        assertEquals(-10f + 8f, v.endH, 1e-4f)
    }
}
