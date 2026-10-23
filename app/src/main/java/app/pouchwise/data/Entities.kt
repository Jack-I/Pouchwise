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
    /** First-order nicotine release constant, per minute (see [Brands]); already includes [fastRelease]. */
    val releaseK: Double = Brands.averageK,
    /** User marked this line as fast-releasing (mini, dry-slim, strong tingle). */
    val fastRelease: Boolean = false,
    /** Price of a can in the user's currency; null until entered. */
    val pricePerCan: Double? = null,
    val pouchesPerCan: Int = 20,
)

/** What the pouch editor hands back. */
data class PouchEdit(
    val brand: String,
    val variant: String,
    val strengthMg: Double,
    val colorIndex: Int,
    val fastRelease: Boolean,
    val pricePerCan: Double?,
    val pouchesPerCan: Int,
)

/** What one pouch of this kind costs, or null without a price. */
fun PouchType.pricePerPouch(): Double? = pricePerCan?.takeIf { pouchesPerCan > 0 }?.div(pouchesPerCan)

/**
 * One pouch used. [timestamp] is when it went in; [endTimestamp] is when the user took it out,
 * or null when it was left in for its full pouch time. Strength, release constant, pouch time and
 * tracking day are copied at log time so later edits of the type or of the settings do not rewrite history.
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
    /** Full pouch time in minutes when the pouch went in; null only for rows from before schema v6 until backfilled. */
    val pouchMinutes: Int? = null,
    /**
     * Tracking day (epoch day) the pouch counts toward, fixed with the day-start hour and time zone in force
     * when it was logged; null only for rows from before schema v7 until backfilled.
     */
    val day: Long? = null,
    /** What the pouch cost when it was logged; null when its kind had no price yet. */
    val pricePerPouch: Double? = null,
    /**
     * The interval plan in force when it went in: the next pouch was due once nicotine dropped below this many mg.
     * Null with the plan off or still learning.
     */
    val planLevelMg: Double? = null,
    /** Schema v9 columns, superseded by [FeltMark]; always null from v10 on. */
    val felt: Int? = null,
    val feltAt: Long? = null,
)

/** How much a pouch was felt, in the owner's words; stored as [level] so that more means stronger. */
enum class Felt(val level: Int, val code: String) {
    NOTHING(0, "nothing"),
    A_BIT(1, "a_bit"),
    HIT(2, "hit"),
    OVERWHELM(3, "overwhelm");

    companion object {
        /** Strongest first, as the buttons show them. */
        val strongestFirst: List<Felt> = entries.reversed()

        fun of(level: Int?): Felt? = entries.firstOrNull { it.level == level }
        fun ofCode(code: String?): Felt? = entries.firstOrNull { it.code == code }
    }
}

/** One answer to how a pouch was felt, at the moment it was given; a pouch collects one per change of feeling. */
@Entity(tableName = "felt_marks", indices = [Index("entryId")])
data class FeltMark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    /** A [Felt] level. */
    val level: Int,
    val atMs: Long,
)

/** The limit plan from [fromDay] on, until the next version; past days are judged by the version of their day. */
@Entity(tableName = "plan_versions")
data class PlanVersion(
    @PrimaryKey val fromDay: Long,
    val limitMode: String,
    val dailyLimitCount: Int,
    val dailyLimitMg: Double,
    val taperEnabled: Boolean,
    val taperStartDay: Long,
    val taperStartValue: Double,
    val taperTargetDay: Long,
    val taperTargetValue: Double,
)
