package app.pouchwise.model

enum class LimitMode { COUNT, MG }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

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
    /** Hour (0-23) at which a new tracking day begins. */
    val dayStartHour: Int = 0,
    val bodyWeightKg: Double = 80.0,
    val pk: PkParams = PkParams(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)
