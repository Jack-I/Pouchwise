package app.pouchwise.data

import app.pouchwise.model.IntervalMode
import app.pouchwise.model.IntervalPlan
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings

/** The entry for a pouch going in, with its price and the interval plan in force fixed at that moment. */
object PouchLog {
    suspend fun entry(dao: PouchDao, type: PouchType, atMs: Long, s: Settings): LogEntry {
        val plan = if (s.intervalMode == IntervalMode.OFF) null
                   else IntervalPlan.threshold(dao.entriesBetween(atMs - IntervalPlan.DATA_MS, atMs + 1), s, atMs)
        return PouchTiming.newEntry(type.id, type.strengthMg, type.releaseK, atMs, s, type.pricePerPouch(), plan)
    }
}
