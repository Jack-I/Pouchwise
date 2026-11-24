package app.pouchwise.model

import kotlin.math.exp
import kotlin.math.roundToInt

data class BrandInfo(val name: String, val kPerMin: Double, val inStudy: Boolean = true)

/** How fast a pouch line gives up nicotine next to its brand, as the user sets it on the pouch. */
enum class Release { SLOW, NORMAL, FAST }

/**
 * First-order nicotine release constants per brand, from in-vitro dissolution of commercial pouches
 * in artificial saliva (Platt et al., Sci Rep 2026, USP-4 flow-through, 60 min). Brands that were not measured
 * use the average of the measured ones.
 */
object Brands {
    val known: List<BrandInfo> = listOf(
        BrandInfo("ZYN", 0.103),
        BrandInfo("on!", 0.080),
        BrandInfo("Rogue", 0.078),
        BrandInfo("Velo", 0.057),
        BrandInfo("Volt", 0.056),
        BrandInfo("Dryft", 0.042),
        // Not in that study: the rate that fits its blood levels next to four other pouches (McEwan et al. 2022).
        BrandInfo("ZYN Wet", 0.032, inStudy = false),
        BrandInfo("Loop", 0.021),
    )

    val averageK: Double = known.filter { it.inStudy }.map { it.kPerMin }.average()

    /** Rate used for a pouch marked "fast release": ZYN-like, applied when the brand's own rate is lower. */
    const val FAST_K = 0.10

    /** Rate used for a pouch marked "slow release": ZYN Wet's, applied when the brand's own rate is higher. */
    val SLOW_K: Double = known.first { it.name == "ZYN Wet" }.kPerMin

    fun find(brand: String): BrandInfo? = known.firstOrNull { it.name.equals(brand.trim(), ignoreCase = true) }

    fun kFor(brand: String): Double = find(brand)?.kPerMin ?: averageK

    fun effectiveK(brand: String, release: Release): Double = when (release) {
        Release.SLOW -> minOf(kFor(brand), SLOW_K)
        Release.NORMAL -> kFor(brand)
        Release.FAST -> maxOf(kFor(brand), FAST_K)
    }

    /** Share of the 60-minute release that is out after 20 minutes, in percent — a feel for the speed. */
    fun percentBy20Min(kPerMin: Double): Int =
        (100.0 * (1 - exp(-20.0 * kPerMin)) / (1 - exp(-60.0 * kPerMin))).roundToInt()

    fun displayName(brand: String, variant: String): String =
        if (variant.isBlank()) brand.trim() else "${brand.trim()} ${variant.trim()}"
}
