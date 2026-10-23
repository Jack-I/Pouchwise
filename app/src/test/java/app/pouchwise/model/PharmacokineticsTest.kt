package app.pouchwise.model

import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
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
        // The pouch releases at a falling rate while it is in, scaled so that 30 minutes give the absorbed share.
        val ka = Brands.averageK * Pharmacokinetics.IN_MOUTH_RELEASE_FACTOR * 60.0
        val r0 = 10.0 * p.absorptionFraction * ka / (1 - exp(-ka * 0.5))
        val yieldMass = p.cotinineYield * 176.22 / 162.23
        val kc = ln(2.0) / p.cotinineHalfLifeHours
        val kn = ln(2.0) / p.nicotineHalfLifeHours
        val dt = 1.0 / 3600.0
        val tOut = p.absorptionMinutes / 60.0
        var n = 0.0
        var c = 0.0
        var time = 0.0
        var nAt10Min = 0.0
        while (time < 10.0) {
            val input = if (time < tOut) r0 * exp(-ka * (time + dt / 2)) else 0.0
            val dn = input - kn * n
            val dc = yieldMass * kn * n - kc * c
            n += dn * dt; c += dc * dt; time += dt
            if (abs(time - 10.0 / 60.0) < dt / 2) nAt10Min = n
        }
        assertEquals(nAt10Min, Pharmacokinetics.levelsAt(10 * min, listOf(dose), p).nicotineMg, 1e-3)
        val l = Pharmacokinetics.levelsAt(10 * h, listOf(dose), p)
        assertEquals(n, l.nicotineMg, 1e-3)
        assertEquals(c, l.cotinineMg, 1e-3)
        assertTrue(l.cotinineMg < yieldMass * 10.0 * p.absorptionFraction)
    }

    @Test
    fun releaseFollowsTheBrandsInMouthRate() {
        val k = Brands.averageK
        fun released(minutes: Double, k: Double) = Pharmacokinetics.released(minutes, k)
        assertEquals(0.0, released(0.0, k), 1e-12)
        assertEquals(0.374, released(30.0, k), 0.001)
        // In the mouth release is slow enough that time matters: 15 min give little over half of 30, 60 min ~1.6×.
        assertEquals(0.558, released(15.0, k) / released(30.0, k), 0.001)
        assertEquals(1.626, released(60.0, k) / released(30.0, k), 0.001)
        // 30 minutes: Loop has given up 15 % of its nicotine, ZYN 54 %
        assertEquals(0.146, released(30.0, Brands.kFor("Loop")), 0.001)
        assertEquals(0.538, released(30.0, Brands.kFor("ZYN")), 0.001)
        assertEquals(0.0, released(15.0, 0.0), 1e-12)
    }

    @Test
    fun theBrandSetsTheDoseAndAnAveragePouchStaysAtThirtyPercent() {
        fun absorbed(k: Double) = 10.0 * Pharmacokinetics.uptake(p) * Pharmacokinetics.released(30.0, k)
        assertEquals(3.0, absorbed(Brands.averageK), 1e-9)
        assertEquals(4.32, absorbed(Brands.kFor("ZYN")), 0.01)
        assertEquals(1.17, absorbed(Brands.kFor("Loop")), 0.01)
        // Four fifths of what leaves the pouch, so never more than has left it.
        assertEquals(0.80, Pharmacokinetics.uptake(p), 0.005)
    }

    @Test
    fun aShorterPouchTimeGivesLessNicotineAndTheSameMinutesGiveTheSame() {
        val noElimination = p.copy(nicotineHalfLifeHours = 1e9, cotinineHalfLifeHours = 2e9)
        fun absorbed(dose: Dose) = Pharmacokinetics.levelsAt(3 * h, listOf(dose), noElimination).nicotineMg
        val full20 = absorbed(Dose(0L, 10.0, 20 * min, fullMinutes = 20))
        val full30 = absorbed(Dose(0L, 10.0, 30 * min, fullMinutes = 30))
        val full40 = absorbed(Dose(0L, 10.0, 40 * min, fullMinutes = 40))
        assertTrue(full20 < full30 && full30 < full40)
        assertEquals(10.0 * p.absorptionFraction, full30, 1e-6)
        // 15 minutes in are 15 minutes in, whatever the pouch time
        assertEquals(absorbed(Dose(0L, 10.0, 15 * min, fullMinutes = 20)), absorbed(Dose(0L, 10.0, 15 * min, fullMinutes = 40)), 1e-9)
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
        assertEquals(10.0 * Pharmacokinetics.uptake(p) * Pharmacokinetics.released(15.0, Brands.averageK), halfN, 1e-6)
        assertEquals(1.67, halfN, 0.02)

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
    fun aDoseKeepsItsOwnFullTime() {
        val dose = Dose(0L, 10.0, 20 * min, fullMinutes = 30)
        val asLogged = Pharmacokinetics.levelsAt(2 * h, listOf(dose), p).nicotineMg
        val afterSettingChange = Pharmacokinetics.levelsAt(2 * h, listOf(dose), p.copy(absorptionMinutes = 60)).nicotineMg
        assertEquals(asLogged, afterSettingChange, 1e-12)
    }

    @Test
    fun bodyAgreesWithTrackWhileThePouchIsIn() {
        val noElimination = p.copy(nicotineHalfLifeHours = 1e9, cotinineHalfLifeHours = 2e9)
        val s = Settings(pk = noElimination)
        val entry = LogEntry(pouchTypeId = 1, timestamp = 0L, strengthMg = 6.0, releaseK = Brands.kFor("ZYN"), pouchMinutes = 30)
        for (m in listOf(1L, 5L, 10L, 20L, 29L)) {
            val body = Pharmacokinetics.levelsAt(m * min, listOf(PouchTiming.toDose(entry, s)), noElimination).nicotineMg
            assertEquals(PouchTiming.absorbedMg(entry, s, m * min), body, 1e-6)
        }
    }

    @Test
    fun takingAPouchOutKeepsTheCurveSoFar() {
        val s = Settings()
        val entry = LogEntry(pouchTypeId = 1, timestamp = 0L, strengthMg = 8.0, pouchMinutes = 30)
        val removed = PouchTiming.finish(entry, s, 12 * min)
        for (m in 0L..12L) {
            val stillIn = Pharmacokinetics.levelsAt(m * min, listOf(PouchTiming.toDose(entry, s)), p)
            val out = Pharmacokinetics.levelsAt(m * min, listOf(PouchTiming.toDose(removed, s)), p)
            assertEquals(stillIn.nicotineMg, out.nicotineMg, 1e-9)
            assertEquals(stillIn.cotinineMg, out.cotinineMg, 1e-9)
        }
    }

    @Test
    fun mostlyGoneCountsFromThePeakStillAhead() {
        // A pouch put in a minute ago after a long break: hardly anything in yet, but the curve is about to rise.
        val now = 10 * h
        val gone = Pharmacokinetics.mostlyGone(now, listOf(Dose(now - min, 6.0, full)), p)
        assertTrue(gone.nicotineClearMs!! > now + 8 * h)
        assertTrue(gone.cotinineClearMs!! > now + 2 * 24 * h)
        // Nothing taken: both have cleared.
        val none = Pharmacokinetics.mostlyGone(now, emptyList(), p)
        assertNull(none.nicotineClearMs)
        assertNull(none.cotinineClearMs)
    }

    @Test
    fun mostlyGoneIsATwentiethOfTheLevelEvenWhenItIsLow() {
        // A day of pouches that ended a day ago: nicotine is gone, cotinine is a few mg and falling.
        val now = 48 * h
        val doses = (0 until 8).map { Dose(now - 24 * h - (2 * it) * h - 30 * min, 8.0, full) }
        val levels = Pharmacokinetics.levelsAt(now, doses, p)
        assertTrue(levels.nicotineMg < Pharmacokinetics.NICOTINE_CLEARED_MG)
        assertTrue(levels.cotinineMg > 1.0)
        val gone = Pharmacokinetics.mostlyGone(now, doses, p)
        assertNull(gone.nicotineClearMs)
        // log2(20) ≈ 4.32 cotinine half-lives ≈ 69 h, not the earlier moment it drops below 0.5 mg.
        assertEquals(69.1, (gone.cotinineClearMs!! - now) / h.toDouble(), 0.5)
    }

    @Test
    fun ngPerMlConversion() {
        // 1 mg nicotine in an 80 kg person: 1000 µg / (80*2.6 L) ≈ 4.8 ng/mL
        assertEquals(4.8077, Pharmacokinetics.toNgPerMl(1.0, 80.0, Pharmacokinetics.NICOTINE_VD_L_PER_KG), 1e-3)
    }
}
