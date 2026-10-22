package app.pouchwise.model

/** Blood levels from studies that put the model's numbers in context. */
object Levels {
    /** Serum cotinine below this marks someone who uses no nicotine at all (SRNT 2020; Benowitz et al. 2009). */
    const val NON_USER_COTININE_NG_ML = 3.0

    fun nonUserCotinineMg(weightKg: Double): Double =
        Pharmacokinetics.toMg(NON_USER_COTININE_NG_ML, weightKg, Pharmacokinetics.COTININE_VD_L_PER_KG)

    /** When cotinine is down to a non-user's level for good if nothing more is taken; null if it is there already. */
    fun nonUserAtMs(nowMs: Long, doses: List<Dose>, p: PkParams, weightKg: Double): Long? =
        Pharmacokinetics.clearance(
            nowMs, doses, p,
            nicotineThreshold = Double.POSITIVE_INFINITY,
            cotinineThreshold = nonUserCotinineMg(weightKg),
        ).cotinineClearMs

    /** Share of α4β2 receptors taken at [nicotineNgMl]: one-site binding through Brody's half-point. */
    fun receptorsBusy(nicotineNgMl: Double): Double =
        if (nicotineNgMl <= 0.0) 0.0 else nicotineNgMl / (nicotineNgMl + IntervalPlan.RECEPTOR_HALF_NG_ML)
}
