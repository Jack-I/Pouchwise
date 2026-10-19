package app.pouchwise.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PouchType::class, LogEntry::class, PlanVersion::class], version = 7, exportSchema = false)
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

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "pouchwise.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
                .also { instance = it }
        }
    }
}
