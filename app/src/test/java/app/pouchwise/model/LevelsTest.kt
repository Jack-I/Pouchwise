package app.pouchwise.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {
    private val pk = PkParams()

    @Test
    fun receptorsAreHalfBusyAtBrodysLevel() {
        assertEquals(0.5, Levels.receptorsBusy(0.87), 1e-9)
        assertEquals(0.95, Levels.receptorsBusy(16.0), 0.01)
        assertEquals(0.0, Levels.receptorsBusy(0.0), 0.0)
    }

    @Test
    fun aNonUsersCotinineIsAFifthOfAMilligramAt72Kg() {
        // 3 ng/mL × 72 kg × 0.9 L/kg
        assertEquals(0.194, Levels.nonUserCotinineMg(72.0), 0.001)
    }

    @Test
    fun cotinineTakesAboutFourDaysToReachANonUsersLevelAfterRegularUse() {
        val start = 1_000_000_000_000L
        val hour = 3_600_000L
        // A week of 6 mg pouches every two hours from 08:00 to 22:00.
        val doses = (0 until 7).flatMap { d -> (8..22 step 2).map { h -> Dose(start + (d * 24 + h) * hour, 6.0, 30 * 60_000L) } }
        val last = start + (6 * 24 + 22) * hour
        val now = last + hour
        val at = Levels.nonUserAtMs(now, doses, pk, 72.0)!!
        val hoursAfterLast = (at - last) / hour.toDouble()
        assertTrue("$hoursAfterLast h", hoursAfterLast in 70.0..110.0)
        // Nothing taken: already there.
        assertNull(Levels.nonUserAtMs(now, emptyList(), pk, 72.0))
    }
}
