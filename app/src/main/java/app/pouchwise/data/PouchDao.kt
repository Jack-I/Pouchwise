package app.pouchwise.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TypeUsage(val pouchTypeId: Long, val uses: Int)

@Dao
interface PouchDao {
    @Query("SELECT * FROM pouch_types ORDER BY sortOrder, id")
    fun observeTypes(): Flow<List<PouchType>>

    @Insert
    suspend fun insertType(type: PouchType): Long

    @Update
    suspend fun updateType(type: PouchType)

    @Query("DELETE FROM pouch_types WHERE id = :id")
    suspend fun deleteType(id: Long)

    @Query("SELECT COUNT(*) FROM log_entries WHERE pouchTypeId = :typeId")
    suspend fun countEntriesForType(typeId: Long): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM pouch_types")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM log_entries ORDER BY timestamp")
    fun observeEntries(): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE timestamp >= :fromMs AND timestamp < :toMs ORDER BY timestamp")
    suspend fun entriesBetween(fromMs: Long, toMs: Long): List<LogEntry>

    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestEntry(): LogEntry?

    @Query("SELECT * FROM pouch_types WHERE archived = 0 ORDER BY sortOrder, id")
    suspend fun activeTypes(): List<PouchType>

    @Query("SELECT * FROM pouch_types WHERE id IN (:ids)")
    suspend fun typesByIds(ids: List<Long>): List<PouchType>

    @Insert
    suspend fun insertEntry(entry: LogEntry): Long

    @Insert
    suspend fun insertTypes(types: List<PouchType>)

    @Insert
    suspend fun insertEntries(entries: List<LogEntry>)

    @Query("DELETE FROM log_entries")
    suspend fun deleteAllEntries()

    /** Freezes the pouch time of entries that have none yet (rows from before schema v6, old imports). */
    @Query("UPDATE log_entries SET pouchMinutes = :minutes WHERE pouchMinutes IS NULL")
    suspend fun fillMissingPouchMinutes(minutes: Int)

    @Query("SELECT * FROM log_entries WHERE day IS NULL")
    suspend fun entriesWithoutDay(): List<LogEntry>

    /** Touches only the day, and only if none was stored meanwhile, so a parallel edit of the row survives. */
    @Query("UPDATE log_entries SET day = :day WHERE id = :id AND day IS NULL")
    suspend fun setDayIfMissing(id: Long, day: Long)

    @Query("UPDATE log_entries SET day = :day WHERE id = :id")
    suspend fun setDay(id: Long, day: Long)

    @Query("SELECT * FROM log_entries WHERE day >= :day")
    suspend fun entriesFromDay(day: Long): List<LogEntry>

    @Query("SELECT * FROM log_entries ORDER BY timestamp")
    suspend fun allEntries(): List<LogEntry>

    @Query("SELECT * FROM pouch_types ORDER BY sortOrder, id")
    suspend fun allTypes(): List<PouchType>

    @Query("SELECT * FROM plan_versions ORDER BY fromDay")
    suspend fun allPlans(): List<PlanVersion>

    /** Pouches logged before their kind had a price take the first one entered. */
    @Query("UPDATE log_entries SET pricePerPouch = :price WHERE pouchTypeId = :typeId AND pricePerPouch IS NULL")
    suspend fun fillMissingPrices(typeId: Long, price: Double)

    @Query("SELECT pouchTypeId, COUNT(*) AS uses FROM log_entries WHERE timestamp >= :fromMs GROUP BY pouchTypeId")
    suspend fun usageSince(fromMs: Long): List<TypeUsage>

    /** One tracking day; rows without a stored day fall back to the time range of that day. */
    @Query("SELECT * FROM log_entries WHERE day = :day OR (day IS NULL AND timestamp >= :fromMs AND timestamp < :toMs) ORDER BY timestamp")
    suspend fun entriesOfDay(day: Long, fromMs: Long, toMs: Long): List<LogEntry>

    @Query("SELECT * FROM plan_versions ORDER BY fromDay")
    fun observePlans(): Flow<List<PlanVersion>>

    @Query("SELECT COUNT(*) FROM plan_versions")
    suspend fun planCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: PlanVersion)

    @Insert
    suspend fun insertPlans(plans: List<PlanVersion>)

    @Query("DELETE FROM plan_versions")
    suspend fun deleteAllPlans()

    @Query("DELETE FROM pouch_types")
    suspend fun deleteAllTypes()

    @Update
    suspend fun updateEntry(entry: LogEntry)

    @Delete
    suspend fun deleteEntry(entry: LogEntry)

    @Query("SELECT * FROM felt_marks ORDER BY atMs, id")
    fun observeFeltMarks(): Flow<List<FeltMark>>

    @Query("SELECT * FROM felt_marks ORDER BY atMs, id")
    suspend fun allFeltMarks(): List<FeltMark>

    @Insert
    suspend fun insertFeltMark(mark: FeltMark): Long

    @Insert
    suspend fun insertFeltMarks(marks: List<FeltMark>)

    /** Takes back the pouch's latest answer. */
    @Query("DELETE FROM felt_marks WHERE id = (SELECT id FROM felt_marks WHERE entryId = :entryId ORDER BY atMs DESC, id DESC LIMIT 1)")
    suspend fun deleteLatestFeltMark(entryId: Long)

    @Query("DELETE FROM felt_marks WHERE entryId = :entryId")
    suspend fun deleteFeltMarksOf(entryId: Long)

    @Query("DELETE FROM felt_marks")
    suspend fun deleteAllFeltMarks()
}
