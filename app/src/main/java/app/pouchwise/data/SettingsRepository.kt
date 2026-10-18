package app.pouchwise.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pouchwise.model.LimitMode
import app.pouchwise.model.PkParams
import app.pouchwise.model.Settings
import app.pouchwise.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object K {
        val LIMIT_MODE = stringPreferencesKey("limit_mode")
        val LIMIT_COUNT = intPreferencesKey("limit_count")
        val LIMIT_MG = doublePreferencesKey("limit_mg")
        val TAPER_ENABLED = booleanPreferencesKey("taper_enabled")
        val TAPER_START_DAY = longPreferencesKey("taper_start_day")
        val TAPER_START_VALUE = doublePreferencesKey("taper_start_value")
        val TAPER_TARGET_DAY = longPreferencesKey("taper_target_day")
        val TAPER_TARGET_VALUE = doublePreferencesKey("taper_target_value")
        val DAY_START_HOUR = intPreferencesKey("day_start_hour")
        val WEIGHT = doublePreferencesKey("weight_kg")
        val ABSORPTION = doublePreferencesKey("pk_absorption")
        val ABSORPTION_MIN = intPreferencesKey("pk_absorption_min")
        val NIC_HALF = doublePreferencesKey("pk_nic_half_life")
        val COT_HALF = doublePreferencesKey("pk_cot_half_life")
        val COT_YIELD = doublePreferencesKey("pk_cot_yield")
        val THEME = stringPreferencesKey("theme")
    }

    val settings: Flow<Settings> = context.settingsStore.data.map { read(it) }

    suspend fun update(transform: (Settings) -> Settings) {
        context.settingsStore.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        val pk = PkParams()
        return Settings(
            limitMode = p[K.LIMIT_MODE]?.let { runCatching { LimitMode.valueOf(it) }.getOrNull() } ?: d.limitMode,
            dailyLimitCount = p[K.LIMIT_COUNT] ?: d.dailyLimitCount,
            dailyLimitMg = p[K.LIMIT_MG] ?: d.dailyLimitMg,
            taperEnabled = p[K.TAPER_ENABLED] ?: d.taperEnabled,
            taperStartDay = p[K.TAPER_START_DAY] ?: d.taperStartDay,
            taperStartValue = p[K.TAPER_START_VALUE] ?: d.taperStartValue,
            taperTargetDay = p[K.TAPER_TARGET_DAY] ?: d.taperTargetDay,
            taperTargetValue = p[K.TAPER_TARGET_VALUE] ?: d.taperTargetValue,
            dayStartHour = p[K.DAY_START_HOUR] ?: d.dayStartHour,
            bodyWeightKg = p[K.WEIGHT] ?: d.bodyWeightKg,
            pk = PkParams(
                absorptionFraction = p[K.ABSORPTION] ?: pk.absorptionFraction,
                absorptionMinutes = p[K.ABSORPTION_MIN] ?: pk.absorptionMinutes,
                nicotineHalfLifeHours = p[K.NIC_HALF] ?: pk.nicotineHalfLifeHours,
                cotinineHalfLifeHours = p[K.COT_HALF] ?: pk.cotinineHalfLifeHours,
                cotinineYield = p[K.COT_YIELD] ?: pk.cotinineYield,
            ),
            themeMode = p[K.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: Settings) {
        p[K.LIMIT_MODE] = s.limitMode.name
        p[K.LIMIT_COUNT] = s.dailyLimitCount
        p[K.LIMIT_MG] = s.dailyLimitMg
        p[K.TAPER_ENABLED] = s.taperEnabled
        p[K.TAPER_START_DAY] = s.taperStartDay
        p[K.TAPER_START_VALUE] = s.taperStartValue
        p[K.TAPER_TARGET_DAY] = s.taperTargetDay
        p[K.TAPER_TARGET_VALUE] = s.taperTargetValue
        p[K.DAY_START_HOUR] = s.dayStartHour
        p[K.WEIGHT] = s.bodyWeightKg
        p[K.ABSORPTION] = s.pk.absorptionFraction
        p[K.ABSORPTION_MIN] = s.pk.absorptionMinutes
        p[K.NIC_HALF] = s.pk.nicotineHalfLifeHours
        p[K.COT_HALF] = s.pk.cotinineHalfLifeHours
        p[K.COT_YIELD] = s.pk.cotinineYield
        p[K.THEME] = s.themeMode.name
    }
}
