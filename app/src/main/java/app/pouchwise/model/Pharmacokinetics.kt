package app.pouchwise.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

data class PkParams(
    /** Share of the labelled nicotine that reaches the bloodstream when a pouch stays in for the full time. */
    val absorptionFraction: Double = 0.30,
    /** Default (and maximum) time a pouch stays in, minutes. Also the auto-removal time on the Track screen. */
    val absorptionMinutes: Int = 30,
    val nicotineHalfLifeHours: Double = 2.0,
    val cotinineHalfLifeHours: Double = 16.0,
    /** Share of eliminated nicotine that is metabolised to cotinine (molar). */
    val cotinineYield: Double = 0.75,
)

/**
 * How quickly a pouch gives up its nicotine, as a first-order rate constant per minute.
 * Values follow in-vitro dissolution of commercial pouches in artificial saliva
 * (Sci Rep 2025, USP-4): ZYN / on! around 0.08–0.10, Velo / Dryft / Volt around 0.04–0.06,
 * wet pouches such as Loop around 0.02.
 */
enum class ReleaseSpeed(val kPerMin: Double) {
    SLOW(0.030),
    NORMAL(0.060),
    FAST(0.095);

    companion object {
        fun fromInt(i: Int): ReleaseSpeed = entries.getOrElse(i) { NORMAL }
    }
}

/** One pouch: when it went in, its labelled strength, how long it stayed in and its release rate. */
data class Dose(val timeMs: Long, val labelledMg: Double, val durationMs: Long, val kPerMin: Double = ReleaseSpeed.NORMAL.kPerMin)

data class BodyLevels(val nicotineMg: Double, val cotinineMg: Double)

data class Clearance(val nicotineClearMs: Long?, val cotinineClearMs: Long?)

/**
 * One-compartment nicotine with first-order elimination feeding a cotinine compartment
 * (Bateman cascade). Each pouch is a constant-rate input over the time it stayed in; the total
 * absorbed amount follows first-order release from the pouch ([releaseFraction]).
 * Closed form, so any instant can be evaluated directly.
 */
object Pharmacokinetics {
    private const val NICOTINE_MW = 162.23
    private const val COTININE_MW = 176.22
    private const val HOUR_MS = 3_600_000.0
    private const val MIN_DURATION_MS = 60_000L

    /** Below these amounts the substance counts as "cleared" (roughly below usual detection in blood). */
    const val NICOTINE_CLEARED_MG = 0.1
    const val COTININE_CLEARED_MG = 0.5

    /** Apparent volumes of distribution, litres per kg body weight. */
    const val NICOTINE_VD_L_PER_KG = 2.6
    const val COTININE_VD_L_PER_KG = 0.9

    /** Doses older than this contribute nothing measurable and are skipped. */
    const val LOOKBACK_MS = 7L * 24 * 3_600_000L
    const val MAX_FORECAST_MS = 14L * 24 * 3_600_000L

    /**
     * Share of the full-time dose that a pouch has given up after [minutesIn] minutes, with the
     * full pouch time [fullMinutes] counting as 100 %: first-order release normalised to the full time.
     */
    fun releaseFraction(minutesIn: Double, fullMinutes: Double, kPerMin: Double): Double {
        val t = minutesIn.coerceIn(0.0, fullMinutes)
        if (kPerMin <= 1e-9) return t / fullMinutes
        return (1 - exp(-kPerMin * t)) / (1 - exp(-kPerMin * fullMinutes))
    }

    fun levelsAt(timeMs: Long, doses: List<Dose>, p: PkParams): BodyLevels {
        val kn = ln(2.0) / p.nicotineHalfLifeHours.coerceAtLeast(0.05)
        var kc = ln(2.0) / p.cotinineHalfLifeHours.coerceAtLeast(0.05)
        if (abs(kc - kn) < 1e-9) kc *= 1.0001
        val fullMs = max(p.absorptionMinutes, 1) * 60_000L
        val fullMin = fullMs / 60_000.0
        val yieldMass = p.cotinineYield * COTININE_MW / NICOTINE_MW

        var n = 0.0
        var c = 0.0
        for (d in doses) {
            if (d.timeMs > timeMs) continue
            val ageMs = timeMs - d.timeMs
            if (ageMs > LOOKBACK_MS) continue
            val durMs = d.durationMs.coerceIn(MIN_DURATION_MS, fullMs)
            val tInf = durMs / HOUR_MS
            val absorbed = d.labelledMg * p.absorptionFraction * releaseFraction(durMs / 60_000.0, fullMin, d.kPerMin)
            val tau = ageMs / HOUR_MS
            val rate = absorbed / tInf
            val a = yieldMass * rate
            if (tau <= tInf) {
                n += (rate / kn) * (1 - exp(-kn * tau))
                c += (a / kc) * (1 - exp(-kc * tau)) - a * (exp(-kn * tau) - exp(-kc * tau)) / (kc - kn)
            } else {
                val nEnd = (rate / kn) * (1 - exp(-kn * tInf))
                val cEnd = (a / kc) * (1 - exp(-kc * tInf)) - a * (exp(-kn * tInf) - exp(-kc * tInf)) / (kc - kn)
                val s = tau - tInf
                n += nEnd * exp(-kn * s)
                c += cEnd * exp(-kc * s) + yieldMass * kn * nEnd * (exp(-kn * s) - exp(-kc * s)) / (kc - kn)
            }
        }
        return BodyLevels(n, c)
    }

    fun series(fromMs: Long, toMs: Long, stepMs: Long, doses: List<Dose>, p: PkParams): List<Pair<Long, BodyLevels>> {
        if (toMs <= fromMs || stepMs <= 0) return emptyList()
        val out = ArrayList<Pair<Long, BodyLevels>>()
        var t = fromMs
        while (t <= toMs) {
            out.add(t to levelsAt(t, doses, p))
            t += stepMs
        }
        if (out.last().first != toMs) out.add(toMs to levelsAt(toMs, doses, p))
        return out
    }

    /**
     * When, assuming no further doses, each substance drops below its threshold for good.
     * Null means it is already below the threshold and stays there.
     */
    fun clearance(nowMs: Long, doses: List<Dose>, p: PkParams, stepMs: Long = 5 * 60_000L): Clearance {
        var lastNicAbove: Long? = null
        var lastCotAbove: Long? = null
        var t = nowMs
        val end = nowMs + MAX_FORECAST_MS
        val settle = nowMs + 2 * HOUR_MS.toLong()
        while (t <= end) {
            val l = levelsAt(t, doses, p)
            if (l.nicotineMg >= NICOTINE_CLEARED_MG) lastNicAbove = t
            if (l.cotinineMg >= COTININE_CLEARED_MG) lastCotAbove = t
            if (t > settle && l.nicotineMg < NICOTINE_CLEARED_MG && l.cotinineMg < COTININE_CLEARED_MG) break
            t += stepMs
        }
        return Clearance(lastNicAbove?.plus(stepMs), lastCotAbove?.plus(stepMs))
    }

    /** mg in the body to an approximate plasma concentration in ng/mL. */
    fun toNgPerMl(mg: Double, weightKg: Double, vdLPerKg: Double): Double {
        val litres = weightKg.coerceAtLeast(30.0) * vdLPerKg
        return mg * 1000.0 / litres
    }
}
