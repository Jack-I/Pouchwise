package app.pouchwise

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.data.SettingsRepository
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import app.pouchwise.util.Export
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val settingsRepo = SettingsRepository(app)

    /** Null until the first read from the database completes. */
    val types: StateFlow<List<PouchType>?> = dao.observeTypes()
        .map<List<PouchType>, List<PouchType>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<LogEntry>> = dao.observeEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Settings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    /** Ticks every half minute so "ago" texts and the body curve stay current. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    fun addType(name: String, strengthMg: Double, colorIndex: Int) = viewModelScope.launch {
        val order = dao.maxSortOrder() + 1
        dao.insertType(PouchType(name = name.trim(), strengthMg = strengthMg, colorIndex = colorIndex, sortOrder = order))
    }

    fun updateType(type: PouchType) = viewModelScope.launch { dao.updateType(type) }

    /** Types with history are hidden instead of deleted so old entries keep their name. */
    fun removeType(type: PouchType) = viewModelScope.launch {
        if (dao.countEntriesForType(type.id) > 0) dao.updateType(type.copy(archived = true))
        else dao.deleteType(type.id)
    }

    fun restoreType(type: PouchType) = viewModelScope.launch { dao.updateType(type.copy(archived = false)) }

    /** Puts a pouch in: it stays "active" until removed or until the default pouch time passes. */
    fun logPouch(type: PouchType, atMs: Long = System.currentTimeMillis()) = viewModelScope.launch {
        dao.insertEntry(LogEntry(pouchTypeId = type.id, timestamp = atMs, strengthMg = type.strengthMg))
    }

    /** Takes the pouch out now; absorption stops here. */
    fun finishPouch(entry: LogEntry) = viewModelScope.launch {
        dao.updateEntry(PouchTiming.finish(entry, settings.value, System.currentTimeMillis()))
    }

    fun deleteEntry(entry: LogEntry) = viewModelScope.launch { dao.deleteEntry(entry) }

    /** Moves the whole pouch (start and end) to a new start time. */
    fun moveEntry(entry: LogEntry, newTimestamp: Long) = viewModelScope.launch {
        val delta = newTimestamp - entry.timestamp
        dao.updateEntry(entry.copy(timestamp = newTimestamp, endTimestamp = entry.endTimestamp?.plus(delta)))
    }

    fun updateSettings(transform: (Settings) -> Settings) = viewModelScope.launch { settingsRepo.update(transform) }

    fun exportCsv(context: Context) {
        Export.shareCsv(context, entries.value, types.value.orEmpty(), settings.value)
    }

    fun exportJson(context: Context) {
        Export.shareJson(context, entries.value, types.value.orEmpty(), settings.value)
    }
}
