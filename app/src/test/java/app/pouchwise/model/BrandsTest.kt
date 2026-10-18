package app.pouchwise.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandsTest {
    @Test
    fun catalogueMatchesTheStudy() {
        assertEquals(7, Brands.known.size)
        assertEquals(0.103, Brands.kFor("ZYN"), 1e-12)
        assertEquals(0.021, Brands.kFor("Loop"), 1e-12)
        assertEquals(0.0624, Brands.averageK, 5e-4)
    }

    @Test
    fun lookupIsCaseInsensitiveAndFallsBackToAverage() {
        assertEquals(Brands.kFor("ZYN"), Brands.kFor(" zyn "), 1e-12)
        assertTrue(Brands.isKnown("velo"))
        assertFalse(Brands.isKnown("Zeus"))
        assertEquals(Brands.averageK, Brands.kFor("Zeus"), 1e-12)
        assertEquals(Brands.averageK, Brands.kFor(""), 1e-12)
    }

    @Test
    fun speedSummaryAndNames() {
        assertEquals(87, Brands.percentBy20Min(Brands.kFor("ZYN")))
        assertEquals(73, Brands.percentBy20Min(Brands.averageK))
        assertEquals(48, Brands.percentBy20Min(Brands.kFor("Loop")))
        assertEquals("Velo", Brands.displayName("Velo", ""))
        assertEquals("Velo Ice Cool", Brands.displayName(" Velo ", " Ice Cool "))
    }
}
