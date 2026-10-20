package app.pouchwise

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.Backup
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PlanVersion
import app.pouchwise.data.PouchEdit
import app.pouchwise.data.PouchLog
import app.pouchwise.data.PouchType
import app.pouchwise.data.pricePerPouch
import app.pouchwise.data.RunningDay
import app.pouchwise.data.SettingsRepository
import app.pouchwise.model.Brands
import app.pouchwise.model.DayKeys
import app.pouchwise.model.LimitMode
import app.pouchwise.model.PlanHistory
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import app.pouchwise.util.Export
import app.pouchwise.util.Money
import app.pouchwise.widget.WidgetUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.math.abs

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext: Context = app
    private val db = AppDatabase.get(app)
    private val dao = db.dao()
    private val settingsRepo = SettingsRepository(app)

    /** Home-screen widgets mirror today's state; refresh them after anything that changes it. */
    private suspend fun refreshWidgets() {
        runCatching { WidgetUpdater.updateAll(appContext) }
    }

    init {
        // Rows from before 0.7.6 / 0.7.7 take the pouch time, day and plan in force now, once, and keep them.
        // A failure here is retried on the next start rather than crashing this one.
        viewModelScope.launch {
            runCatching {
                val s = settingsRepo.settings.first()
                dao.fillMissingPouchMinutes(PouchTiming.newPouchMinutes(s))
                db.withTransaction {
                    for (e in dao.entriesWithoutDay()) dao.setDayIfMissing(e.id, DayKeys.epochDay(e.timestamp, s.dayStartHour))
                }
                if (dao.planCount() == 0) dao.upsertPlan(PlanHistory.versionOf(s, fromDay = 0L))
                if (s.currency.isEmpty()) {
                    Money.localSymbol(appContext)?.let { sym -> settingsRepo.update { it.copy(currency = it.currency.ifEmpty { sym }) } }
                }
                if (s.onboarded && s.baselinePerDay == null) {
                    PlanHistory.baselinePerDay(dao.allPlans().firstOrNull(), dao.allTypes().minByOrNull { it.id })
                        ?.let { base -> settingsRepo.update { it.copy(baselinePerDay = it.baselinePerDay ?: base) } }
                }
            }
        }
    }

    /** Null until the first read from the database completes. */
    val types: StateFlow<List<PouchType>?> = dao.observeTypes()
        .map<List<PouchType>, List<PouchType>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<LogEntry>> = dao.observeEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recorded plan versions, oldest first; past days are judged by these. */
    val plans: StateFlow<List<PlanVersion>> = dao.observePlans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** False until the stored settings have been read once (so defaults are not mistaken for real values). */
    val settingsLoaded: StateFlow<Boolean> = settingsRepo.settings
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Ticks every half minute so "ago" texts and the body curve stay current. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    fun addType(edit: PouchEdit) = viewModelScope.launch {
        val order = dao.maxSortOrder() + 1
        dao.insertType(
            PouchType(
                name = Brands.displayName(edit.brand, edit.variant), strengthMg = edit.strengthMg, colorIndex = edit.colorIndex,
                sortOrder = order, brand = edit.brand.trim(), releaseK = Brands.effectiveK(edit.brand, edit.fastRelease),
                fastRelease = edit.fastRelease, pricePerCan = edit.pricePerCan, pouchesPerCan = edit.pouchesPerCan,
            ),
        )
        refreshWidgets()
    }

    fun updateType(type: PouchType, edit: PouchEdit) =
        viewModelScope.launch {
            val updated = type.copy(
                name = Brands.displayName(edit.brand, edit.variant), strengthMg = edit.strengthMg, colorIndex = edit.colorIndex,
                brand = edit.brand.trim(), releaseK = Brands.effectiveK(edit.brand, edit.fastRelease), fastRelease = edit.fastRelease,
                pricePerCan = edit.pricePerCan, pouchesPerCan = edit.pouchesPerCan,
            )
            dao.updateType(updated)
            // Pouches logged before the kind had a price take this one; logged prices never change.
            updated.pricePerPouch()?.let { dao.fillMissingPrices(type.id, it) }
            refreshWidgets()
        }

    suspend fun typeHasEntries(typeId: Long): Boolean = dao.countEntriesForType(typeId) > 0

    /** Types with history are hidden instead of deleted so old entries keep their name. */
    fun removeType(type: PouchType) = viewModelScope.launch {
        if (dao.countEntriesForType(type.id) > 0) dao.updateType(type.copy(archived = true))
        else dao.deleteType(type.id)
        refreshWidgets()
    }

    fun restoreType(type: PouchType) = viewModelScope.launch {
        dao.updateType(type.copy(archived = false))
        refreshWidgets()
    }

    /** Puts a pouch in: it stays "active" until removed or until its pouch time passes. */
    fun logPouch(type: PouchType, atMs: Long = System.currentTimeMillis()) = viewModelScope.launch {
        val s = settingsRepo.settings.first()
        dao.insertEntry(PouchLog.entry(dao, type, atMs, s))
        refreshWidgets()
    }

    /** Takes the pouch out now; absorption stops here. */
    fun finishPouch(entry: LogEntry) = viewModelScope.launch {
        dao.updateEntry(PouchTiming.finish(entry, settingsRepo.settings.first(), System.currentTimeMillis()))
        refreshWidgets()
    }

    fun deleteEntry(entry: LogEntry) = viewModelScope.launch {
        dao.deleteEntry(entry)
        refreshWidgets()
    }

    /** Corrects when a pouch went in; a recorded removal time stays where it was. */
    fun moveEntry(entry: LogEntry, newTimestamp: Long) = viewModelScope.launch {
        dao.updateEntry(PouchTiming.withNewStart(entry, newTimestamp, settingsRepo.settings.first()))
        refreshWidgets()
    }

    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch {
        val (before, after) = settingsRepo.update(transform)
        if (before.dayStartHour != after.dayStartHour) {
            val zone = ZoneId.systemDefault()
            RunningDay.refile(appContext, before.dayStartHour, zone, after.dayStartHour, zone)
        }
        recordPlan(before, after)
        refreshWidgets()
    }

    /** A plan change applies from today on; the plan so far stays on record for the days before. */
    private suspend fun recordPlan(before: Settings, after: Settings) {
        if (PlanHistory.samePlan(before, after)) return
        if (dao.planCount() == 0) dao.upsertPlan(PlanHistory.versionOf(before, fromDay = 0L))
        val today = DayKeys.epochDay(System.currentTimeMillis(), after.dayStartHour)
        dao.upsertPlan(PlanHistory.versionOf(after, fromDay = today))
    }

    /**
     * First-launch setup: creates the usual pouch and a two-month plan. The limit is counted in pouches
     * when the strength stays the same, otherwise in mg so that a switch to weaker pouches is captured.
     */
    fun completeOnboarding(brand: String, strengthNow: Double, perDayNow: Int, perDayTarget: Int, strengthTarget: Double) =
        viewModelScope.launch {
            val order = dao.maxSortOrder() + 1
            dao.insertType(
                PouchType(
                    name = brand.trim(), strengthMg = strengthNow, colorIndex = 0, sortOrder = order,
                    brand = brand.trim(), releaseK = Brands.kFor(brand),
                ),
            )
            val (before, after) = settingsRepo.update { s ->
                val today = DayKeys.epochDay(System.currentTimeMillis(), s.dayStartHour)
                val targetDay = DayKeys.localDate(today).plusMonths(2).toEpochDay()
                val sameStrength = abs(strengthNow - strengthTarget) < 1e-9
                // Limits are whole mg: 5 × 6.5 mg starts at 33, as the headline would show it anyway.
                val startValue = if (sameStrength) perDayNow.toDouble() else Math.round(perDayNow * strengthNow).toDouble()
                val targetValue = if (sameStrength) perDayTarget.toDouble() else Math.round(perDayTarget * strengthTarget).toDouble()
                s.copy(
                    limitMode = if (sameStrength) LimitMode.COUNT else LimitMode.MG,
                    dailyLimitCount = if (sameStrength) perDayNow else s.dailyLimitCount,
                    dailyLimitMg = if (sameStrength) s.dailyLimitMg else startValue,
                    taperEnabled = targetValue < startValue - 1e-9,
                    taperStartDay = today,
                    taperStartValue = startValue,
                    taperTargetDay = targetDay,
                    taperTargetValue = targetValue,
                    onboarded = true,
                    baselinePerDay = perDayNow,
                )
            }
            recordPlan(before, after)
            refreshWidgets()
        }

    fun skipOnboarding() = updateSettings { it.copy(onboarded = true) }

    /** Read straight from the database: the screen-bound flows can be stale or empty on the Settings screen. */
    fun exportCsv(context: Context) = viewModelScope.launch {
        Export.shareCsv(context, dao.allEntries(), dao.allTypes(), settingsRepo.settings.first())
    }

    fun exportJson(context: Context) = viewModelScope.launch {
        Export.shareJson(context, dao.allEntries(), dao.allTypes(), settingsRepo.settings.first(), dao.allPlans())
    }

    /** Replaces pouches, entries and plan history with the file's in one transaction, then takes over its settings. */
    fun importBackup(contents: Backup.Contents) = viewModelScope.launch {
        val imported = contents.settingsOver(settingsRepo.settings.first())
            .copy(onboarded = true, taperStartAlt = null, taperTargetAlt = null)
        // Older files lack per-entry pouch time and day, or the plan history; their own settings are the best guess.
        val minutes = PouchTiming.newPouchMinutes(imported)
        val entries = contents.entries.map {
            it.copy(
                pouchMinutes = it.pouchMinutes ?: minutes,
                day = it.day ?: DayKeys.epochDay(it.timestamp, imported.dayStartHour),
            )
        }
        val plans = contents.plans.ifEmpty { listOf(PlanHistory.versionOf(imported, fromDay = 0L)) }
        val ok = runCatching {
            db.withTransaction {
                dao.deleteAllEntries()
                dao.deleteAllTypes()
                dao.deleteAllPlans()
                dao.insertTypes(contents.types)
                dao.insertEntries(entries)
                dao.insertPlans(plans)
            }
        }.isSuccess
        if (ok) {
            settingsRepo.update { imported }
            refreshWidgets()
        }
        val res = appContext.resources
        val text = if (ok) {
            res.getString(
                R.string.import_done,
                res.getQuantityString(R.plurals.count_pouches, contents.types.count { !it.archived }, contents.types.count { !it.archived }),
                res.getQuantityString(R.plurals.count_entries, contents.entries.size, contents.entries.size),
            )
        } else {
            res.getString(R.string.import_failed)
        }
        Toast.makeText(appContext, text, Toast.LENGTH_LONG).show()
    }
}
