package app.pouchwise.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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

    @Insert
    suspend fun insertEntry(entry: LogEntry): Long

    @Update
    suspend fun updateEntry(entry: LogEntry)

    @Delete
    suspend fun deleteEntry(entry: LogEntry)
}
