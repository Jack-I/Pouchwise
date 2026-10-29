package app.pouchwise

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.Backup
import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PlanVersion
import app.pouchwise.data.PouchEdit
import app.pouchwise.data.PouchLog
import app.pouchwise.data.PouchType
import app.pouchwise.data.pricePerPouch
import app.pouchwise.data.RunningDay
import app.pouchwise.data.SettingsRepository
import app.pouchwise.data.edited
import app.pouchwise.model.DayKeys
import app.pouchwise.model.LimitMode
import app.pouchwise.model.PlanHistory
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import app.pouchwise.util.Export
import app.pouchwise.widget.WidgetUpdater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    /** Felt answers by entry id, oldest first. */
    val feltMarks: StateFlow<Map<Long, List<FeltMark>>> = dao.observeFeltMarks()
        .map { marks -> marks.groupBy { it.entryId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
        insertType(edit)
        refreshWidgets()
    }

    private suspend fun insertType(edit: PouchEdit) {
        dao.insertType(PouchType(name = "", strengthMg = edit.strengthMg, sortOrder = dao.maxSortOrder() + 1).edited(edit))
    }

    fun updateType(type: PouchType, edit: PouchEdit) =
        viewModelScope.launch {
            val updated = type.edited(edit)
            dao.updateType(updated)
            // Pouches logged before the kind had any price take its first one; logged prices never change.
            if (type.pricePerCan == null) updated.pricePerPouch()?.let { dao.fillMissingPrices(type.id, it) }
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

    /** The shown pouches in the order of [activeIds]; hidden ones go after them, so a restored one comes back last. */
    fun reorderTypes(activeIds: List<Long>) = viewModelScope.launch {
        db.withTransaction {
            val hidden = dao.allTypes().filter { it.archived && it.id !in activeIds }.map { it.id }
            (activeIds + hidden).forEachIndexed { i, id -> dao.setSortOrder(id, i) }
        }
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
        PouchTiming.finish(entry, settingsRepo.settings.first(), System.currentTimeMillis()).endTimestamp?.let { dao.setOut(entry.id, it) }
        refreshWidgets()
    }

    fun deleteEntry(entry: LogEntry) = viewModelScope.launch {
        db.withTransaction {
            dao.deleteEntry(entry)
            dao.deleteFeltMarksOf(entry.id)
        }
        refreshWidgets()
    }

    /**
     * A hand correction: when the pouch went in, how long it stayed ([durationMs] is null while it is still in)
     * and its felt answers, which replace the stored ones.
     */
    fun editEntry(entry: LogEntry, newStartMs: Long, durationMs: Long?, marks: List<FeltMark>) = viewModelScope.launch {
        val s = settingsRepo.settings.first()
        db.withTransaction {
            // The row as it is now: the pouch may have been pulled out from a widget while the dialog was open.
            val current = dao.entryById(entry.id) ?: return@withTransaction
            val moved = if (durationMs == null) PouchTiming.withNewStart(current, newStartMs, s)
                        else PouchTiming.withTimes(current, newStartMs, durationMs, s)
            dao.updateEntry(moved)
            dao.deleteFeltMarksOf(entry.id)
            dao.insertFeltMarks(marks.map { PouchTiming.placeAnswer(it.copy(id = 0, entryId = entry.id), moved, s) })
        }
        refreshWidgets()
    }

    /** Records how the pouch feels now. */
    fun addFelt(entry: LogEntry, felt: Felt) = viewModelScope.launch {
        dao.insertFeltMark(FeltMark(entryId = entry.id, level = felt.level, atMs = System.currentTimeMillis()))
    }

    fun takeBackFelt(entry: LogEntry) = viewModelScope.launch {
        dao.deleteLatestFeltMark(entry.id)
    }

    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch {
        // Once saved, the pouches and the plan record must follow even if the screen is left at that moment.
        withContext(NonCancellable) {
            val (before, after) = settingsRepo.update(transform)
            if (before.dayStartHour != after.dayStartHour) {
                val zone = ZoneId.systemDefault()
                RunningDay.refile(appContext, before.dayStartHour, zone, after.dayStartHour, zone)
            }
            recordPlan(before, after)
        }
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
            insertType(PouchEdit(brand, variant = "", strengthNow, colorIndex = 0, fastRelease = false, pricePerCan = null, pouchesPerCan = 20))
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

    fun exportCsv(context: Context) = export(context) { d, s -> Export.csvChooser(appContext, d.entries, d.types, s, d.marks) }

    fun exportFeltCsv(context: Context) = export(context) { d, s -> Export.feltCsvChooser(appContext, d.entries, d.types, s, d.marks) }

    fun exportJson(context: Context) = export(context) { d, s -> Export.jsonChooser(appContext, d.entries, d.types, s, d.plans, d.marks) }

    private class Everything(val entries: List<LogEntry>, val types: List<PouchType>, val plans: List<PlanVersion>, val marks: List<FeltMark>)

    /** Read straight from the database in one go: the screen-bound flows can be stale or empty on the Settings screen. */
    private fun export(context: Context, build: (Everything, Settings) -> Intent) = viewModelScope.launch {
        try {
            val data = db.withTransaction { Everything(dao.allEntries(), dao.allTypes(), dao.allPlans(), dao.allFeltMarks()) }
            val settings = settingsRepo.settings.first()
            context.startActivity(withContext(Dispatchers.Default) { build(data, settings) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Toast.makeText(appContext, R.string.export_failed, Toast.LENGTH_LONG).show()
        }
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
        val entriesById = entries.associateBy { it.id }
        val marks = contents.feltMarks.map { PouchTiming.placeAnswer(it, entriesById.getValue(it.entryId), imported) }
        val ok = runCatching {
            db.withTransaction {
                dao.deleteAllFeltMarks()
                dao.deleteAllEntries()
                dao.deleteAllTypes()
                dao.deleteAllPlans()
                dao.insertTypes(contents.types)
                dao.insertEntries(entries)
                dao.insertPlans(plans)
                dao.insertFeltMarks(marks)
            }
        }.isSuccess
        if (ok) {
            runCatching { settingsRepo.update { contents.settingsOver(it).copy(onboarded = true, taperStartAlt = null, taperTargetAlt = null) } }
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
