package app.pouchwise.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class FmtTest {
    private fun inUk(block: () -> Unit) {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.UK)
        try {
            block()
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun hoursAndMinutesNeverReadNowOrDays() = inUk {
        val h = "%1\$d h %2\$02d"
        val m = "%1\$d min"
        assertEquals("0 min", Fmt.hoursMinutes(0, h, m))
        assertEquals("0 min", Fmt.hoursMinutes(59_000, h, m))
        assertEquals("36 min", Fmt.hoursMinutes(36 * 60_000L, h, m))
        assertEquals("2 h 36", Fmt.hoursMinutes((2 * 60 + 36) * 60_000L, h, m))
        assertEquals("24 h 00", Fmt.hoursMinutes(24 * 3_600_000L, h, m))
    }

    @Test
    fun theCurrencySymbolNeverWrapsAwayFromItsNumber() = inUk {
        assertEquals("1.58 €", Fmt.money(1.58, "€"))
        assertEquals("2,480 ₽", Fmt.money(2480.0, "₽"))
        assertEquals("0.2", Fmt.money(0.2, ""))
    }

    @Test
    fun aTimeAWeekAwayCarriesItsDate() = inUk {
        val zone = ZoneId.of("Europe/Rome")
        fun at(day: Int) = LocalDateTime.of(2026, 10, day, 9, 5).atZone(zone).toInstant().toEpochMilli()
        val now = at(23) + 3 * 3_600_000L // Friday noon
        assertEquals("09:05", Fmt.timeOrDayTime(at(23), now, zone))
        assertEquals("Sat 09:05", Fmt.timeOrDayTime(at(17), now, zone))
        assertEquals("16 Oct 09:05", Fmt.timeOrDayTime(at(16), now, zone))
    }
}
