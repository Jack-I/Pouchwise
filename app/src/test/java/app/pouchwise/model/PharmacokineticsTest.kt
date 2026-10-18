package app.pouchwise.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class PharmacokineticsTest {
    private val p = PkParams()
    private val h = 3_600_000L
    private val full = 30 * 60_000L

    @Test
    fun nothingBeforeFirstDose() {
        val l = Pharmacokinetics.levelsAt(0L, listOf(Dose(h, 8.0, full)), p)
        assertEquals(0.0, l.nicotineMg, 1e-12)
        assertEquals(0.0, l.cotinineMg, 1e-12)
    }

    @Test
    fun absorbedDoseIsBoundedByFractionTimesLabel() {
        val dose = Dose(0L, 10.0, full)
        var peak = 0.0
        for (m in 0..180) {
            val l = Pharmacokinetics.levelsAt(m * 60_000L, listOf(dose), p)
            if (l.nicotineMg > peak) peak = l.nicotineMg
        }
        assertTrue("peak $peak", peak > 0.5 && peak < 10.0 * p.absorptionFraction)
    }

    @Test
    fun nicotineHalvesEveryHalfLifeAfterAbsorption() {
        val dose = Dose(0L, 10.0, full)
        val t0 = 60 * 60_000L
        val n0 = Pharmacokinetics.levelsAt(t0, listOf(dose), p).nicotineMg
        val n1 = Pharmacokinetics.levelsAt(t0 + (p.nicotineHalfLifeHours * h).toLong(), listOf(dose), p).nicotineMg
        assertEquals(0.5, n1 / n0, 1e-6)
    }

    @Test
    fun closedFormMatchesNumericalIntegration() {
        val dose = Dose(0L, 10.0, full)
        val absorbed = 10.0 * p.absorptionFraction
        val yieldMass = p.cotinineYield * 176.22 / 162.23
        val kc = ln(2.0) / p.cotinineHalfLifeHours
        val kn = ln(2.0) / p.nicotineHalfLifeHours
        val dt = 1.0 / 3600.0
        var n = 0.0
        var c = 0.0
        val tInf = p.absorptionMinutes / 60.0
        var time = 0.0
        while (time < 10.0) {
            val input = if (time < tInf) absorbed / tInf else 0.0
            val dn = input - kn * n
            val dc = yieldMass * kn * n - kc * c
            n += dn * dt; c += dc * dt; time += dt
        }
        val l = Pharmacokinetics.levelsAt(10 * h, listOf(dose), p)
        assertEquals(n, l.nicotineMg, 1e-3)
        assertEquals(c, l.cotinineMg, 1e-3)
        assertTrue(l.cotinineMg < yieldMass * absorbed)
    }

    @Test
    fun shorterPouchTimeAbsorbsLessButMoreThanProportionally() {
        assertEquals(0.0, Pharmacokinetics.releaseFraction(0.0), 1e-12)
        assertEquals(1.0, Pharmacokinetics.releaseFraction(1.0), 1e-12)
        assertEquals(0.731, Pharmacokinetics.releaseFraction(0.5), 1e-3)

        val fullDose = Dose(0L, 10.0, full)
        val halfDose = Dose(0L, 10.0, full / 2)
        val t = 3 * h
        val nFull = Pharmacokinetics.levelsAt(t, listOf(fullDose), p).nicotineMg
        val nHalf = Pharmacokinetics.levelsAt(t, listOf(halfDose), p).nicotineMg
        assertTrue("half $nHalf < full $nFull", nHalf < nFull)
        assertTrue("half $nHalf > 50% of full $nFull", nHalf > 0.5 * nFull)

        // Longer than the default pouch time is capped at the default.
        val longDose = Dose(0L, 10.0, 3 * full)
        assertEquals(nFull, Pharmacokinetics.levelsAt(t, listOf(longDose), p).nicotineMg, 1e-12)
    }

    @Test
    fun clearanceIsAfterNowAndNullWhenNothingTaken() {
        val now = 100 * h
        val none = Pharmacokinetics.clearance(now, emptyList(), p)
        assertNull(none.nicotineClearMs)
        assertNull(none.cotinineClearMs)

        val some = Pharmacokinetics.clearance(now, listOf(Dose(now - h, 8.0, full)), p)
        assertNotNull(some.nicotineClearMs)
        assertNotNull(some.cotinineClearMs)
        assertTrue(some.nicotineClearMs!! > now)
        assertTrue(some.cotinineClearMs!! > some.nicotineClearMs!!)
    }

    @Test
    fun ngPerMlConversion() {
        // 1 mg nicotine in an 80 kg person: 1000 µg / (80*2.6 L) ≈ 4.8 ng/mL
        assertEquals(4.8077, Pharmacokinetics.toNgPerMl(1.0, 80.0, Pharmacokinetics.NICOTINE_VD_L_PER_KG), 1e-3)
    }
}
