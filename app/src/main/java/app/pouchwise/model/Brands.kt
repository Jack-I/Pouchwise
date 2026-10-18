package app.pouchwise.model

import kotlin.math.exp
import kotlin.math.roundToInt

data class BrandInfo(val name: String, val kPerMin: Double)

/**
 * First-order nicotine release constants per brand, from in-vitro dissolution of commercial pouches
 * in artificial saliva (Sci Rep 2025, USP-4 flow-through, 60 min). Brands that were not measured
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
        BrandInfo("Loop", 0.021),
    )

    val averageK: Double = known.map { it.kPerMin }.average()

    /** Rate used for a pouch marked "fast release": ZYN-like, applied when the brand's own rate is lower. */
    const val FAST_K = 0.10

    fun find(brand: String): BrandInfo? = known.firstOrNull { it.name.equals(brand.trim(), ignoreCase = true) }

    fun isKnown(brand: String): Boolean = find(brand) != null

    fun kFor(brand: String): Double = find(brand)?.kPerMin ?: averageK

    fun effectiveK(brand: String, fastRelease: Boolean): Double =
        if (fastRelease) maxOf(kFor(brand), FAST_K) else kFor(brand)

    /** Share of the 60-minute release that is out after 20 minutes, in percent — a feel for the speed. */
    fun percentBy20Min(kPerMin: Double): Int =
        (100.0 * (1 - exp(-20.0 * kPerMin)) / (1 - exp(-60.0 * kPerMin))).roundToInt()

    fun displayName(brand: String, variant: String): String =
        if (variant.isBlank()) brand.trim() else "${brand.trim()} ${variant.trim()}"
}
