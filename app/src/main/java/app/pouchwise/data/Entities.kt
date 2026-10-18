package app.pouchwise.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.pouchwise.model.Brands

/** A kind of pouch the user buys: brand plus nicotine strength. */
@Entity(tableName = "pouch_types")
data class PouchType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Display name: brand plus optional flavour / line. */
    val name: String,
    val strengthMg: Double,
    val colorIndex: Int = 0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Schema v3 column, superseded by [releaseK]; kept because SQLite cannot drop it in place. */
    val releaseSpeed: Int = 1,
    /** Brand as picked from the catalogue or typed by the user; empty for rows created before 0.4. */
    val brand: String = "",
    /** First-order nicotine release constant, per minute (see [Brands]). */
    val releaseK: Double = Brands.averageK,
)

/**
 * One pouch used. [timestamp] is when it went in; [endTimestamp] is when the user took it out,
 * or null when it was left in for the default pouch time. Strength and release constant are copied
 * at log time so later edits of the type do not rewrite history.
 */
@Entity(tableName = "log_entries", indices = [Index("timestamp"), Index("pouchTypeId")])
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pouchTypeId: Long,
    val timestamp: Long,
    val strengthMg: Double,
    val endTimestamp: Long? = null,
    /** Schema v3 column, superseded by [releaseK]. */
    val releaseSpeed: Int = 1,
    val releaseK: Double = Brands.averageK,
)
