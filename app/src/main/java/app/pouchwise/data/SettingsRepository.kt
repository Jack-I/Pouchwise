package app.pouchwise.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pouchwise.model.IntervalMode
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
        val POUCH_MINUTES = intPreferencesKey("pk_absorption_min")
        val THEME = stringPreferencesKey("theme")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val TAPER_START_ALT = doublePreferencesKey("taper_start_alt")
        val TAPER_TARGET_ALT = doublePreferencesKey("taper_target_alt")
        val ZONE = stringPreferencesKey("zone_id")
        val INTERVAL_MODE = stringPreferencesKey("interval_mode")
        val BASELINE = intPreferencesKey("baseline_per_day")
        val CURRENCY = stringPreferencesKey("currency")
    }

    val settings: Flow<Settings> = context.settingsStore.data.map { read(it) }

    /** Applies [transform] atomically and returns the settings before and after it. */
    suspend fun update(transform: (Settings) -> Settings): Pair<Settings, Settings> {
        var before = Settings()
        var after = Settings()
        context.settingsStore.edit { prefs ->
            before = read(prefs)
            after = transform(before)
            write(prefs, after)
        }
        return before to after
    }

    /** Limits are whole pouches or whole mg; older versions could store 32.5. */
    private fun read(p: Preferences): Settings {
        val d = Settings()
        fun whole(v: Double) = Math.round(v).toDouble()
        return Settings(
            limitMode = p[K.LIMIT_MODE]?.let { runCatching { LimitMode.valueOf(it) }.getOrNull() } ?: d.limitMode,
            dailyLimitCount = p[K.LIMIT_COUNT] ?: d.dailyLimitCount,
            dailyLimitMg = whole(p[K.LIMIT_MG] ?: d.dailyLimitMg),
            taperEnabled = p[K.TAPER_ENABLED] ?: d.taperEnabled,
            taperStartDay = p[K.TAPER_START_DAY] ?: d.taperStartDay,
            taperStartValue = whole(p[K.TAPER_START_VALUE] ?: d.taperStartValue),
            taperTargetDay = p[K.TAPER_TARGET_DAY] ?: d.taperTargetDay,
            taperTargetValue = whole(p[K.TAPER_TARGET_VALUE] ?: d.taperTargetValue),
            taperStartAlt = p[K.TAPER_START_ALT],
            taperTargetAlt = p[K.TAPER_TARGET_ALT],
            dayStartHour = p[K.DAY_START_HOUR] ?: d.dayStartHour,
            bodyWeightKg = p[K.WEIGHT] ?: d.bodyWeightKg,
            pk = PkParams(absorptionMinutes = p[K.POUCH_MINUTES] ?: PkParams().absorptionMinutes),
            themeMode = p[K.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            onboarded = p[K.ONBOARDED] ?: d.onboarded,
            zoneId = p[K.ZONE] ?: d.zoneId,
            intervalMode = p[K.INTERVAL_MODE]?.let { runCatching { IntervalMode.valueOf(it) }.getOrNull() } ?: d.intervalMode,
            baselinePerDay = p[K.BASELINE],
            currency = p[K.CURRENCY] ?: d.currency,
        )
    }

    private fun write(p: MutablePreferences, s: Settings) {
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
        p[K.POUCH_MINUTES] = s.pk.absorptionMinutes
        p[K.THEME] = s.themeMode.name
        p[K.ONBOARDED] = s.onboarded
        if (s.taperStartAlt != null) p[K.TAPER_START_ALT] = s.taperStartAlt else p.remove(K.TAPER_START_ALT)
        if (s.taperTargetAlt != null) p[K.TAPER_TARGET_ALT] = s.taperTargetAlt else p.remove(K.TAPER_TARGET_ALT)
        p[K.ZONE] = s.zoneId
        p[K.INTERVAL_MODE] = s.intervalMode.name
        if (s.baselinePerDay != null) p[K.BASELINE] = s.baselinePerDay else p.remove(K.BASELINE)
        p[K.CURRENCY] = s.currency
    }
}
