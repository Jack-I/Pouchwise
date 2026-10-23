package app.pouchwise.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Runs the app's Room migrations on a real SQLite and checks the result the way Room checks the database when the
 * app opens it: every table of the exported schema with the same columns (type, NOT NULL, primary key, a default
 * where the entity declares one) and the same indices. A mismatch would crash the app on its first start after an
 * update.
 */
class MigrationTest {
    private val schema = JSONObject(File(SCHEMA).readText()).getJSONObject("database")
    private val entities = schema.getJSONArray("entities").objects()
    private val current = schema.getInt("version")

    @Test
    fun everyOlderVersionMigratesToTheCurrentSchema() {
        for (version in 1 until current) {
            sqlite().use { db ->
                createAt(db, version)
                migrate(db, from = version)
                assertSchemaLikeRoom(db, "from v$version")
            }
        }
    }

    @Test
    fun aFirstVersionDatabaseKeepsItsPouches() {
        sqlite().use { db ->
            createAt(db, 1)
            exec(db, "INSERT INTO pouch_types (id, name, strengthMg, colorIndex, sortOrder, archived, createdAt) VALUES (1, 'Velo', 6, 0, 0, 0, 5)")
            exec(db, "INSERT INTO log_entries (id, pouchTypeId, timestamp, strengthMg) VALUES (7, 1, 1000, 6)")
            migrate(db, from = 1)
            val entry = rows(db, "SELECT pouchTypeId, timestamp, strengthMg, releaseK, endTimestamp FROM log_entries").single()
            assertEquals(listOf<Any?>(1L, 1000L, 6.0, 0.0624, null), entry)
            assertEquals(listOf<Any?>(20L, 0L, ""), rows(db, "SELECT pouchesPerCan, fastRelease, brand FROM pouch_types").single())
            assertEquals(0, rows(db, "SELECT * FROM felt_marks").size)
        }
    }

    @Test
    fun feltAnswersMoveToTheirTableAndLateOnesToThePullOut() {
        val min = 60_000L
        sqlite().use { db ->
            createAt(db, 9)
            exec(
                db,
                "INSERT INTO pouch_types (id, name, strengthMg, colorIndex, sortOrder, archived, createdAt, releaseSpeed, brand, releaseK, " +
                    "fastRelease, pouchesPerCan) VALUES (1, 'ZYN', 6.5, 0, 0, 0, 0, 1, 'ZYN', 0.103, 0, 20)",
            )
            // In 38 minutes, answered at minute 20; in by itself for 30 minutes, answered the next day; answered without a time.
            entry(db, id = 1, at = 0L, end = 38 * min, minutes = 45, felt = 2, feltAt = 20 * min)
            entry(db, id = 2, at = 1_000 * min, end = null, minutes = 30, felt = 3, feltAt = 1_000 * min + 26 * 60 * min)
            entry(db, id = 3, at = 3_000 * min, end = null, minutes = 30, felt = 0, feltAt = null)
            migrate(db, from = 9)
            assertSchemaLikeRoom(db, "from v9 with answers")
            assertEquals(
                listOf(
                    listOf<Any?>(1L, 2L, 20 * min, 0L),
                    listOf<Any?>(2L, 3L, 1_030 * min, 1L),
                    listOf<Any?>(3L, 0L, 3_000 * min, 0L),
                ),
                rows(db, "SELECT entryId, level, atMs, recalled FROM felt_marks ORDER BY entryId"),
            )
            assertEquals(setOf(listOf<Any?>(null, null)), rows(db, "SELECT felt, feltAt FROM log_entries").toSet())
        }
    }

    // ---- schema at an older version ------------------------------------------------------------------------------

    /** Creates the tables as a fresh install of [version] had them: today's definitions without what came later. */
    private fun createAt(db: Connection, version: Int) {
        for (e in entities) {
            val table = e.getString("tableName")
            if ((TABLE_SINCE[table] ?: 1) > version) continue
            val later = ADDED.filterKeys { it > version }.values.mapNotNull { it[table] }.flatten().toSet()
            val fields = e.getJSONArray("fields").objects().filter { it.getString("columnName") !in later }
            val pk = e.getJSONObject("primaryKey")
            val keys = pk.getJSONArray("columnNames").strings()
            val auto = pk.optBoolean("autoGenerate")
            val columns = fields.joinToString(", ") { f ->
                val name = f.getString("columnName")
                "`$name` ${f.getString("affinity")}" +
                    (if (auto && keys == listOf(name)) " PRIMARY KEY AUTOINCREMENT" else "") +
                    (if (f.optBoolean("notNull")) " NOT NULL" else "") +
                    (if (f.has("defaultValue")) " DEFAULT ${f.getString("defaultValue")}" else "")
            }
            val primary = if (auto) "" else ", PRIMARY KEY(${keys.joinToString { "`$it`" }})"
            exec(db, "CREATE TABLE `$table` ($columns$primary)")
            e.optJSONArray("indices")?.objects()?.forEach { exec(db, indexSql(table, it)) }
        }
    }

    private fun migrate(db: Connection, from: Int) {
        val support = support(db)
        AppDatabase.MIGRATIONS.filter { it.startVersion >= from }.sortedBy { it.startVersion }.forEach { it.migrate(support) }
    }

    // ---- Room's check ---------------------------------------------------------------------------------------------

    private class Column(val type: String, val notNull: Int, val pk: Int, val default: String?)

    private fun assertSchemaLikeRoom(db: Connection, case: String) {
        sqlite().use { expected ->
            for (e in entities) {
                val table = e.getString("tableName")
                exec(expected, e.getString("createSql").replace("\${TABLE_NAME}", table))
                e.optJSONArray("indices")?.objects()?.forEach { exec(expected, indexSql(table, it)) }
            }
            for (e in entities) {
                val table = e.getString("tableName")
                val want = columns(expected, table)
                val got = columns(db, table)
                assertEquals("$case: columns of $table", want.keys, got.keys)
                for ((name, w) in want) {
                    val g = got.getValue(name)
                    assertEquals("$case: type of $table.$name", w.type, g.type)
                    assertEquals("$case: NOT NULL of $table.$name", w.notNull, g.notNull)
                    assertEquals("$case: key of $table.$name", w.pk, g.pk)
                    // Room compares defaults only where the entity declares one.
                    if (w.default != null) assertEquals("$case: default of $table.$name", w.default, g.default)
                }
                assertEquals("$case: indices of $table", indices(expected, table), indices(db, table))
            }
        }
    }

    private fun columns(db: Connection, table: String): Map<String, Column> =
        query(db, "PRAGMA table_info(`$table`)") {
            it.getString("name") to Column(it.getString("type").uppercase(), it.getInt("notnull"), it.getInt("pk"), it.getString("dflt_value"))
        }.toMap()

    /** Indices made by CREATE INDEX, as Room reads them: name, unique, columns. */
    private fun indices(db: Connection, table: String): Set<Triple<String, Int, List<String>>> =
        query(db, "PRAGMA index_list(`$table`)") { if (it.getString("origin") == "c") it.getString("name") to it.getInt("unique") else null }
            .filterNotNull()
            .map { (name, unique) -> Triple(name, unique, query(db, "PRAGMA index_info(`$name`)") { it.getString("name") }) }
            .toSet()

    // ---- plumbing -------------------------------------------------------------------------------------------------

    private fun sqlite(): Connection = DriverManager.getConnection("jdbc:sqlite::memory:")

    /** Migrations only run statements, so that is all this stand-in for Room's database does. */
    private fun support(db: Connection): SupportSQLiteDatabase =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            when (method.name) {
                "execSQL" -> db.prepareStatement(args[0] as String).use { st ->
                    (args.getOrNull(1) as Array<*>?)?.forEachIndexed { i, v -> st.setObject(i + 1, v) }
                    st.execute()
                    null
                }
                "toString" -> "sqlite for migrations"
                else -> throw UnsupportedOperationException(method.name)
            }
        } as SupportSQLiteDatabase

    private fun exec(db: Connection, sql: String) {
        db.createStatement().use { it.execute(sql) }
    }

    private fun <T> query(db: Connection, sql: String, row: (ResultSet) -> T): List<T> =
        db.createStatement().use { st -> st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(row(rs)) } } }

    private fun rows(db: Connection, sql: String): List<List<Any?>> =
        query(db, sql) { rs -> (1..rs.metaData.columnCount).map { i -> rs.getObject(i)?.let { v -> if (v is Int) v.toLong() else v } } }

    private fun entry(db: Connection, id: Long, at: Long, end: Long?, minutes: Int, felt: Int, feltAt: Long?) =
        exec(
            db,
            "INSERT INTO log_entries (id, pouchTypeId, timestamp, strengthMg, endTimestamp, releaseSpeed, releaseK, pouchMinutes, felt, feltAt) " +
                "VALUES ($id, 1, $at, 6.5, ${end ?: "NULL"}, 1, 0.103, $minutes, $felt, ${feltAt ?: "NULL"})",
        )

    private fun indexSql(table: String, index: JSONObject) =
        index.getString("createSql").replace("\${TABLE_NAME}", table).replace("\${INDEX_NAME}", index.getString("name"))

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private companion object {
        const val SCHEMA = "schemas/app.pouchwise.data.AppDatabase/11.json"

        /** Columns each migration added, by the version it leads to. */
        val ADDED = mapOf(
            2 to mapOf("log_entries" to setOf("endTimestamp")),
            3 to mapOf("pouch_types" to setOf("releaseSpeed"), "log_entries" to setOf("releaseSpeed")),
            4 to mapOf("pouch_types" to setOf("brand", "releaseK"), "log_entries" to setOf("releaseK")),
            5 to mapOf("pouch_types" to setOf("fastRelease")),
            6 to mapOf("log_entries" to setOf("pouchMinutes")),
            7 to mapOf("log_entries" to setOf("day")),
            8 to mapOf("pouch_types" to setOf("pricePerCan", "pouchesPerCan"), "log_entries" to setOf("pricePerPouch", "planLevelMg")),
            9 to mapOf("log_entries" to setOf("felt", "feltAt")),
            11 to mapOf("felt_marks" to setOf("recalled")),
        )

        /** Tables that a later version brought in. */
        val TABLE_SINCE = mapOf("plan_versions" to 7, "felt_marks" to 10)
    }
}
