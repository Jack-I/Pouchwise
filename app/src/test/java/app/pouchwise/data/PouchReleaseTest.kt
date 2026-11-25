package app.pouchwise.data

import app.pouchwise.model.Brands
import app.pouchwise.model.Release
import org.junit.Assert.assertEquals
import org.junit.Test

class PouchReleaseTest {
    private val pouch = PouchType(name = "Camo zesty orange", strengthMg = 6.0, brand = "Camo")

    @Test
    fun theChoiceMadeInTheDialogIsTheOneReadBack() {
        for (r in Release.entries) {
            assertEquals(r, pouch.edited(PouchEdit("Camo", "zesty orange", 6.0, 0, r, null, 20)).release())
        }
    }

    @Test
    fun aPouchSetSlowOrFastBeforeTheChoiceWasStoredReadsBackAsSuch() {
        assertEquals(Release.SLOW, pouch.copy(releaseK = 0.030).release())
        assertEquals(Release.FAST, pouch.copy(releaseK = 0.095).release())
        assertEquals(Release.NORMAL, pouch.copy(releaseK = Brands.averageK).release())
    }
}
