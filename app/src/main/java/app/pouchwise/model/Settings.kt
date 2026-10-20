package app.pouchwise.model

enum class LimitMode { COUNT, MG }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Spacing between pouches by nicotine level, see [IntervalPlan]. */
enum class IntervalMode { OFF, KEEP, LOWER }

data class Settings(
    val limitMode: LimitMode = LimitMode.COUNT,
    val dailyLimitCount: Int = 10,
    val dailyLimitMg: Double = 60.0,
    val taperEnabled: Boolean = false,
    /** Epoch days (days since 1970-01-01, in the user's local calendar). */
    val taperStartDay: Long = 0L,
    val taperStartValue: Double = 10.0,
    val taperTargetDay: Long = 0L,
    val taperTargetValue: Double = 5.0,
    /** The taper values in the other unit from before the last unit switch, so switching back restores them. */
    val taperStartAlt: Double? = null,
    val taperTargetAlt: Double? = null,
    /** Hour (0-23) at which a new tracking day begins. */
    val dayStartHour: Int = 0,
    val bodyWeightKg: Double = 80.0,
    /** Only [PkParams.absorptionMinutes] is user-editable; the rest are model constants. */
    val pk: PkParams = PkParams(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** True once the first-launch questionnaire was completed or skipped. */
    val onboarded: Boolean = false,
    /** Time zone the running day was counted in; empty until first seen. */
    val zoneId: String = "",
    val intervalMode: IntervalMode = IntervalMode.OFF,
    /** Pouches a day before Pouchwise, the yardstick for savings; null until known. */
    val baselinePerDay: Int? = null,
    /** Currency symbol for prices; empty means the one of the phone's region. */
    val currency: String = "",
)
