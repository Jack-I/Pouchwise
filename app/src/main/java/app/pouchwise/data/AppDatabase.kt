package app.pouchwise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.pouchwise.model.PouchTiming

@Database(entities = [PouchType::class, LogEntry::class, PlanVersion::class, FeltMark::class], version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PouchDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE log_entries ADD COLUMN endTimestamp INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pouch_types ADD COLUMN releaseSpeed INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE log_entries ADD COLUMN releaseSpeed INTEGER NOT NULL DEFAULT 1")
            }
        }

        /** Per-brand release constants replace the slow / normal / fast choice. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pouch_types ADD COLUMN brand TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pouch_types ADD COLUMN releaseK REAL NOT NULL DEFAULT 0.0624")
                db.execSQL("ALTER TABLE log_entries ADD COLUMN releaseK REAL NOT NULL DEFAULT 0.0624")
                val remap = "CASE releaseSpeed WHEN 0 THEN 0.030 WHEN 2 THEN 0.095 ELSE 0.0624 END"
                db.execSQL("UPDATE pouch_types SET releaseK = $remap")
                db.execSQL("UPDATE log_entries SET releaseK = $remap")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pouch_types ADD COLUMN fastRelease INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Existing rows stay NULL here; the app fills them from the settings on its next start. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE log_entries ADD COLUMN pouchMinutes INTEGER")
            }
        }

        /** Entry days stay NULL here and the plan table empty; the app fills both from the settings on its next start. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE log_entries ADD COLUMN day INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `plan_versions` (`fromDay` INTEGER NOT NULL, `limitMode` TEXT NOT NULL, " +
                        "`dailyLimitCount` INTEGER NOT NULL, `dailyLimitMg` REAL NOT NULL, `taperEnabled` INTEGER NOT NULL, " +
                        "`taperStartDay` INTEGER NOT NULL, `taperStartValue` REAL NOT NULL, `taperTargetDay` INTEGER NOT NULL, " +
                        "`taperTargetValue` REAL NOT NULL, PRIMARY KEY(`fromDay`))",
                )
            }
        }

        /** Prices and the interval plan; existing rows stay NULL and pouches hold 20 a can until edited. */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pouch_types ADD COLUMN pricePerCan REAL")
                db.execSQL("ALTER TABLE pouch_types ADD COLUMN pouchesPerCan INTEGER NOT NULL DEFAULT 20")
                db.execSQL("ALTER TABLE log_entries ADD COLUMN pricePerPouch REAL")
                db.execSQL("ALTER TABLE log_entries ADD COLUMN planLevelMg REAL")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE log_entries ADD COLUMN felt INTEGER")
                db.execSQL("ALTER TABLE log_entries ADD COLUMN feltAt INTEGER")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `felt_marks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entryId` INTEGER NOT NULL, `level` INTEGER NOT NULL, `atMs` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_felt_marks_entryId` ON `felt_marks` (`entryId`)")
                db.execSQL("INSERT INTO felt_marks (entryId, level, atMs) SELECT id, felt, COALESCE(feltAt, timestamp) FROM log_entries WHERE felt IS NOT NULL")
                db.execSQL("UPDATE log_entries SET felt = NULL, feltAt = NULL")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE felt_marks ADD COLUMN recalled INTEGER NOT NULL DEFAULT 0")
                // Answers already given long after their pouch came out move to its pull-out, as new ones do.
                val out = "(SELECT MIN(MAX(COALESCE(e.endTimestamp, e.timestamp + COALESCE(e.pouchMinutes, 30) * 60000), e.timestamp + 60000), " +
                    "e.timestamp + COALESCE(e.pouchMinutes, 30) * 60000) FROM log_entries e WHERE e.id = felt_marks.entryId)"
                db.execSQL("UPDATE felt_marks SET recalled = 1, atMs = $out WHERE atMs > $out + ${PouchTiming.RECALL_AFTER_MS}")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pouchwise.db")
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10, MIGRATION_10_11,
                )
                .build()
                .also { instance = it }
        }
    }
}
