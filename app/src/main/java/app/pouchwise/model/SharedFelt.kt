package app.pouchwise.model

import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import kotlin.math.max

/** One feeling for all pouches in the mouth at once: a double-decker is answered, and counted, as one. */
object SharedFelt {
    /** What a tap on an answer on a pouch card changes. */
    data class Tap(val add: List<FeltMark> = emptyList(), val remove: List<FeltMark> = emptyList())

    /**
     * A tap on [felt] on [entry]'s card, which showed [shown] chosen: taking it back when that was [felt] and it is still
     * the latest answer, otherwise giving [felt] to every pouch [inMouth]. A second tap before the card changes does nothing.
     */
    fun tap(entry: LogEntry, inMouth: List<LogEntry>, marks: Map<Long, List<FeltMark>>, felt: Felt, atMs: Long, shown: Felt?): Tap =
        when {
            shown != felt -> Tap(add = toAdd(inMouth, marks, felt, atMs))
            latest(marks, entry.id)?.level == felt.level -> Tap(remove = toTakeBack(entry, marks))
            else -> Tap()
        }

    /** [entry], whose card was tapped, and the other pouches in the mouth at [nowMs]. */
    fun inMouthWith(entry: LogEntry, entries: List<LogEntry>, s: Settings, nowMs: Long): List<LogEntry> =
        listOf(entry) + entries.filter { it.id != entry.id && PouchTiming.isActive(it, s, nowMs) }

    /** [felt] at [atMs] for each pouch of [inMouth] whose latest answer is not [felt] already. */
    fun toAdd(inMouth: List<LogEntry>, marks: Map<Long, List<FeltMark>>, felt: Felt, atMs: Long): List<FeltMark> =
        inMouth.filter { latest(marks, it.id)?.level != felt.level }
            .map { FeltMark(entryId = it.id, level = felt.level, atMs = atMs) }

    /** [entry]'s latest answer, and the same answer given with it to other pouches, still in the mouth or not. */
    fun toTakeBack(entry: LogEntry, marks: Map<Long, List<FeltMark>>): List<FeltMark> {
        val mine = latest(marks, entry.id) ?: return emptyList()
        val together = marks.keys.filter { it != entry.id }
            .mapNotNull { latest(marks, it) }
            .filter { it.atMs == mine.atMs && it.level == mine.level }
        return listOf(mine) + together
    }

    /** Pouches whose time in the mouth overlaps, in time order; a lone pouch is a group of its own. */
    fun groups(entries: List<LogEntry>, s: Settings): List<List<LogEntry>> {
        val groups = mutableListOf<MutableList<LogEntry>>()
        var end = Long.MIN_VALUE
        for (e in entries.sortedWith(compareBy({ it.timestamp }, { it.id }))) {
            if (groups.isNotEmpty() && e.timestamp < end) {
                groups.last().add(e)
                end = max(end, PouchTiming.outMs(e, s))
            } else {
                groups.add(mutableListOf(e))
                end = PouchTiming.outMs(e, s)
            }
        }
        return groups
    }

    /** The pouches other than [entryId] in the mouth at [atMs]. */
    fun othersInMouth(entryId: Long, atMs: Long, entries: List<LogEntry>, s: Settings): List<Long> =
        entries.filter { it.id != entryId && it.timestamp <= atMs && atMs < PouchTiming.outMs(it, s) }.map { it.id }

    private fun latest(marks: Map<Long, List<FeltMark>>, entryId: Long): FeltMark? =
        marks[entryId]?.maxWithOrNull(compareBy({ it.atMs }, { it.id }))
}
