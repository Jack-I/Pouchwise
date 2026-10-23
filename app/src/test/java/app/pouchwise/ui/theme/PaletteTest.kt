package app.pouchwise.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteTest {
    @Test
    fun thePickerShowsEveryColourOnce() {
        assertEquals(PouchPalette.indices.toList(), PaletteOrder.sorted())
    }
}
