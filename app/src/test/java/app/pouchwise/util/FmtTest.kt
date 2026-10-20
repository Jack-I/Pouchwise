package app.pouchwise.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FmtTest {
    @Test
    fun theCurrencySymbolNeverWrapsAwayFromItsNumber() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            assertEquals("1.58 €", Fmt.money(1.58, "€"))
            assertEquals("2,480 ₽", Fmt.money(2480.0, "₽"))
            assertEquals("0.2", Fmt.money(0.2, ""))
        } finally {
            Locale.setDefault(before)
        }
    }
}
