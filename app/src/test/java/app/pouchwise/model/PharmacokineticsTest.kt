package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

class PharmacokineticsTest {
    private val p = PkParams()
    private val h = 3_600_000L
    private val min = 60_000L
    private val full = 30 * min

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
            val l = Pharmacokinetics.levelsAt(m * min, listOf(dose), p)
            if (l.nicotineMg > peak) peak = l.nicotineMg
        }
        assertTrue("peak $peak", peak > 0.5 && peak < 10.0 * p.absorptionFraction)
    }

    @Test
    fun nicotineHalvesEveryHalfLifeAfterAbsorption() {
        val dose = Dose(0L, 10.0, full)
        val t0 = 60 * min
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
    fun releaseFractionFollowsFirstOrderKinetics() {
        val k = Brands.averageK
        assertEquals(0.0, Pharmacokinetics.releaseFraction(0.0, 30.0, k), 1e-12)
        assertEquals(1.0, Pharmacokinetics.releaseFraction(30.0, 30.0, k), 1e-12)
        assertEquals(1.0, Pharmacokinetics.releaseFraction(45.0, 30.0, k), 1e-12) // capped at the full time
        // 15 of 30 minutes: Loop < average < ZYN, all above the linear 50 %
        val loop = Pharmacokinetics.releaseFraction(15.0, 30.0, Brands.kFor("Loop"))
        val average = Pharmacokinetics.releaseFraction(15.0, 30.0, k)
        val zyn = Pharmacokinetics.releaseFraction(15.0, 30.0, Brands.kFor("ZYN"))
        assertEquals(0.58, loop, 0.01)
        assertEquals(0.72, average, 0.01)
        assertEquals(0.82, zyn, 0.01)
        assertTrue(loop < average && average < zyn)
        // k = 0 degrades to even release
        assertEquals(0.5, Pharmacokinetics.releaseFraction(15.0, 30.0, 0.0), 1e-12)
    }

    @Test
    fun earlyRemovalCountsOnlyWhatWasReleased() {
        // Switch elimination off (huge half-lives) so the amount in the body equals the absorbed amount.
        val noElimination = p.copy(nicotineHalfLifeHours = 1e9, cotinineHalfLifeHours = 2e9)
        val fullDose = Dose(0L, 10.0, full)
        val halfDose = Dose(0L, 10.0, 15 * min)
        val fullN = Pharmacokinetics.levelsAt(2 * h, listOf(fullDose), noElimination).nicotineMg
        val halfN = Pharmacokinetics.levelsAt(2 * h, listOf(halfDose), noElimination).nicotineMg
        assertEquals(10.0 * p.absorptionFraction, fullN, 1e-6)
        assertEquals(10.0 * p.absorptionFraction * Pharmacokinetics.releaseFraction(15.0, 30.0, Brands.averageK), halfN, 1e-6)
        assertEquals(2.15, halfN, 0.02)

        // Same thing through a logged pouch that was taken out after 15 minutes.
        val s = Settings()
        val entry = LogEntry(pouchTypeId = 1, timestamp = 0L, strengthMg = 10.0)
        val removed = PouchTiming.finish(entry, s, 15 * min)
        val viaEntry = Pharmacokinetics.levelsAt(2 * h, listOf(PouchTiming.toDose(removed, s)), noElimination).nicotineMg
        assertEquals(halfN, viaEntry, 1e-9)
        // ...and the "absorbed" figure shown in the UI agrees with the model.
        assertEquals(halfN, PouchTiming.absorbedMg(removed, s, 2 * h), 1e-5)

        // A ZYN taken out at the same time has given up more of its nicotine.
        val zynEntry = PouchTiming.finish(entry.copy(releaseK = Brands.kFor("ZYN")), s, 15 * min)
        val zynN = Pharmacokinetics.levelsAt(2 * h, listOf(PouchTiming.toDose(zynEntry, s)), noElimination).nicotineMg
        assertTrue(zynN > viaEntry)
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
    fun ninetyFivePercentGoneTakesAboutFourAndAThirdHalfLives() {
        // A day's worth of pouches, last one two hours ago: nicotine is a few mg now.
        val now = 24 * h
        val doses = (0 until 8).map { Dose(now - (2 + it) * h - 30 * min, 8.0, full) }
        val levels = Pharmacokinetics.levelsAt(now, doses, p)
        assertTrue(levels.nicotineMg > 1.0)
        val gone95 = Pharmacokinetics.clearance(now, doses, p, nicotineThreshold = levels.nicotineMg * 0.05)
        val gone95H = (gone95.nicotineClearMs!! - now) / h.toDouble()
        // 95 % gone = log2(20) ≈ 4.32 half-lives ≈ 8.6 h with a 2 h half-life, whatever the body weight.
        assertEquals(8.64, gone95H, 0.4)
        // The absolute 0.1 mg line comes later and depends on the starting level.
        val clearH = (Pharmacokinetics.clearance(now, doses, p).nicotineClearMs!! - now) / h.toDouble()
        assertTrue("clear $clearH > 95% $gone95H", clearH > gone95H)
    }

    @Test
    fun ngPerMlConversion() {
        // 1 mg nicotine in an 80 kg person: 1000 µg / (80*2.6 L) ≈ 4.8 ng/mL
        assertEquals(4.8077, Pharmacokinetics.toNgPerMl(1.0, 80.0, Pharmacokinetics.NICOTINE_VD_L_PER_KG), 1e-3)
    }
}
