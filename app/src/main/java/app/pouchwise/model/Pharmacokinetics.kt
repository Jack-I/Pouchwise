package app.pouchwise.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

data class PkParams(
    /** Share of the labelled nicotine that reaches the bloodstream from a pouch of average release speed kept in for 30 minutes. */
    val absorptionFraction: Double = 0.30,
    /** Pouch time for pouches put in from now on, minutes; each logged pouch keeps the value it was logged with. */
    val absorptionMinutes: Int = 30,
    val nicotineHalfLifeHours: Double = 2.0,
    val cotinineHalfLifeHours: Double = 16.0,
    /** Share of eliminated nicotine that is metabolised to cotinine (molar). */
    val cotinineYield: Double = 0.75,
)

/**
 * One pouch: when it went in, its labelled strength, how long it stayed in, its release rate (see [Brands])
 * and its full pouch time in minutes (null = [PkParams.absorptionMinutes]).
 */
data class Dose(
    val timeMs: Long,
    val labelledMg: Double,
    val durationMs: Long,
    val kPerMin: Double = Brands.averageK,
    val fullMinutes: Int? = null,
)

data class BodyLevels(val nicotineMg: Double, val cotinineMg: Double)

data class Clearance(val nicotineClearMs: Long?, val cotinineClearMs: Long?)

/**
 * One-compartment nicotine with first-order elimination feeding a cotinine compartment
 * (Bateman cascade). While a pouch is in, nicotine comes in as the pouch releases it ([released]),
 * fast at first and slowing down; taking it out stops the input. Closed form, so any instant can be
 * evaluated directly.
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

    /** [PkParams.absorptionFraction] is the absorbed share for an average pouch kept in this long. */
    const val REFERENCE_MINUTES = 30.0

    /**
     * In the mouth a pouch gives up its nicotine about four times more slowly than in the lab dissolution
     * behind [Brands]; fitted to blood levels after pouches and snus kept in for different times.
     */
    const val IN_MOUTH_RELEASE_FACTOR = 0.25

    private const val GONE_SHARE = 0.05
    /** Cotinine peaks about 7 h after nicotine stops coming in; the peaks are searched this far past the last pouch. */
    private const val PEAK_SEARCH_MS = 10L * 3_600_000L
    private const val SCAN_STEP_MS = 5 * 60_000L

    /**
     * Share of a pouch's nicotine that has left it after [minutesIn] minutes in the mouth: first-order release,
     * so a slow brand or a short time gives less. It depends only on the minutes actually in, not on the pouch-time setting.
     */
    fun released(minutesIn: Double, kPerMin: Double): Double =
        1 - exp(-kPerMin * IN_MOUTH_RELEASE_FACTOR * minutesIn.coerceAtLeast(0.0))

    /**
     * Share of the nicotine out of the pouch that reaches the bloodstream, the same for every brand; set so that an
     * average pouch kept in for [REFERENCE_MINUTES] gives [PkParams.absorptionFraction] of its label.
     */
    fun uptake(p: PkParams): Double = p.absorptionFraction / released(REFERENCE_MINUTES, Brands.averageK)

    fun levelsAt(timeMs: Long, doses: List<Dose>, p: PkParams): BodyLevels {
        val kn = ln(2.0) / p.nicotineHalfLifeHours.coerceAtLeast(0.05)
        var kc = ln(2.0) / p.cotinineHalfLifeHours.coerceAtLeast(0.05)
        if (abs(kc - kn) < 1e-9) kc *= 1.0001
        val y = p.cotinineYield * COTININE_MW / NICOTINE_MW
        val u = uptake(p)

        var n = 0.0
        var c = 0.0
        for (d in doses) {
            if (d.timeMs > timeMs) continue
            val ageMs = timeMs - d.timeMs
            if (ageMs > LOOKBACK_MS) continue
            val tau = ageMs / HOUR_MS
            val tOut = durationMs(d, p) / HOUR_MS
            val t = min(tau, tOut)
            val en = exp(-kn * t)
            val ec = exp(-kc * t)
            // Levels at t while the pouch is in.
            var ka = d.kPerMin * IN_MOUTH_RELEASE_FACTOR * 60.0
            if (abs(ka - kn) < 1e-6 * kn || abs(ka - kc) < 1e-6 * kc) ka *= 1.001
            val r0 = d.labelledMg * u * ka
            val ea = exp(-ka * t)
            val nIn = r0 / (kn - ka) * (ea - en)
            val cIn = y * kn * r0 / (kn - ka) * ((ea - ec) / (kc - ka) - (en - ec) / (kc - kn))
            if (tau <= tOut) {
                n += nIn
                c += cIn
            } else {
                val s = tau - tOut
                n += nIn * exp(-kn * s)
                c += cIn * exp(-kc * s) + y * kn * nIn * (exp(-kn * s) - exp(-kc * s)) / (kc - kn)
            }
        }
        return BodyLevels(n, c)
    }

    private fun durationMs(d: Dose, p: PkParams): Long {
        val fullMs = max(d.fullMinutes ?: p.absorptionMinutes, 1) * 60_000L
        return d.durationMs.coerceIn(MIN_DURATION_MS, fullMs)
    }

    /**
     * Levels from [fromMs] to [toMs]: every [stepMs] on the clock, plus each moment a pouch went in or came
     * out, so peaks are drawn where they are and stay put as time passes.
     */
    fun series(fromMs: Long, toMs: Long, stepMs: Long, doses: List<Dose>, p: PkParams): List<Pair<Long, BodyLevels>> {
        if (toMs <= fromMs || stepMs <= 0) return emptyList()
        val times = sortedSetOf(fromMs, toMs)
        var t = Math.floorDiv(fromMs, stepMs) * stepMs + stepMs
        while (t < toMs) {
            times.add(t)
            t += stepMs
        }
        for (d in doses) {
            if (d.timeMs in fromMs..toMs) times.add(d.timeMs)
            val outMs = d.timeMs + durationMs(d, p)
            if (outMs in fromMs..toMs) times.add(outMs)
        }
        // Times only grow, so each point needs just the doses of the week before it.
        val sorted = doses.sortedBy { it.timeMs }
        var lo = 0
        var hi = 0
        return times.map { at ->
            while (hi < sorted.size && sorted[hi].timeMs <= at) hi++
            while (lo < hi && at - sorted[lo].timeMs > LOOKBACK_MS) lo++
            at to levelsAt(at, sorted.subList(lo, hi), p)
        }
    }

    /**
     * When, assuming no further doses, each substance drops below its threshold for good.
     * Null means it is already below the threshold and stays there. The defaults are the absolute
     * "cleared" amounts; pass a share of the current level to get e.g. the "90 % gone" moment.
     */
    fun clearance(
        nowMs: Long,
        doses: List<Dose>,
        p: PkParams,
        nicotineThreshold: Double = NICOTINE_CLEARED_MG,
        cotinineThreshold: Double = COTININE_CLEARED_MG,
        stepMs: Long = 5 * 60_000L,
    ): Clearance {
        val recent = doses.filter { nowMs - it.timeMs <= LOOKBACK_MS }
        var lastNicAbove: Long? = null
        var lastCotAbove: Long? = null
        var t = nowMs
        val end = nowMs + MAX_FORECAST_MS
        val settle = nowMs + 2 * HOUR_MS.toLong()
        while (t <= end) {
            val l = levelsAt(t, recent, p)
            if (l.nicotineMg >= nicotineThreshold) lastNicAbove = t
            if (l.cotinineMg >= cotinineThreshold) lastCotAbove = t
            if (t > settle && l.nicotineMg < nicotineThreshold && l.cotinineMg < cotinineThreshold) break
            t += stepMs
        }
        return Clearance(lastNicAbove?.plus(stepMs), lastCotAbove?.plus(stepMs))
    }

    /**
     * When each substance will be 95 % gone if nothing more is taken: the moment it stays below a twentieth
     * of the highest level still ahead (now, or the peak that a pouch still in or cotinine still forming
     * will bring). Null when even that level is below the "cleared" amount.
     */
    fun mostlyGone(nowMs: Long, doses: List<Dose>, p: PkParams): Clearance {
        val recent = doses.filter { nowMs - it.timeMs <= LOOKBACK_MS }
        val lastOut = recent.maxOfOrNull { it.timeMs + durationMs(it, p) } ?: nowMs
        var nPeak = 0.0
        var cPeak = 0.0
        var t = nowMs
        val until = max(nowMs, lastOut) + PEAK_SEARCH_MS
        while (t <= until) {
            val l = levelsAt(t, recent, p)
            nPeak = max(nPeak, l.nicotineMg)
            cPeak = max(cPeak, l.cotinineMg)
            t += SCAN_STEP_MS
        }
        val nicotine = nPeak >= NICOTINE_CLEARED_MG
        val cotinine = cPeak >= COTININE_CLEARED_MG
        if (!nicotine && !cotinine) return Clearance(null, null)
        val gone = clearance(
            nowMs, recent, p,
            nicotineThreshold = if (nicotine) GONE_SHARE * nPeak else Double.POSITIVE_INFINITY,
            cotinineThreshold = if (cotinine) GONE_SHARE * cPeak else Double.POSITIVE_INFINITY,
        )
        return Clearance(gone.nicotineClearMs, gone.cotinineClearMs)
    }

    /** mg in the body to an approximate plasma concentration in ng/mL. */
    fun toNgPerMl(mg: Double, weightKg: Double, vdLPerKg: Double): Double {
        val litres = weightKg.coerceAtLeast(30.0) * vdLPerKg
        return mg * 1000.0 / litres
    }

    /** The other way round: a plasma concentration in ng/mL to mg in the body. */
    fun toMg(ngPerMl: Double, weightKg: Double, vdLPerKg: Double): Double =
        ngPerMl * weightKg.coerceAtLeast(30.0) * vdLPerKg / 1000.0
}
