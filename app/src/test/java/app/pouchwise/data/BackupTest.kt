package app.pouchwise.data

import app.pouchwise.model.LimitMode
import app.pouchwise.model.PkParams
import app.pouchwise.model.Settings
import app.pouchwise.model.ThemeMode
import app.pouchwise.util.Export
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    private val types = listOf(
        PouchType(
            id = 3, name = "Velo Freeze", strengthMg = 10.0, colorIndex = 2, sortOrder = 1,
            createdAt = 1_700_000_000_000L, brand = "Velo", releaseK = 0.10, fastRelease = true,
        ),
        PouchType(
            id = 7, name = "Vont", strengthMg = 7.0, colorIndex = 5, sortOrder = 0, archived = true,
            createdAt = 1_700_000_100_000L, brand = "Vont", releaseK = 0.0624,
        ),
    )
    private val entries = listOf(
        LogEntry(id = 10, pouchTypeId = 3, timestamp = 1_760_000_000_000L, strengthMg = 10.0, endTimestamp = 1_760_000_900_000L, releaseK = 0.10),
        LogEntry(id = 11, pouchTypeId = 7, timestamp = 1_760_001_000_000L, strengthMg = 7.0, releaseK = 0.0624),
    )
    private val settings = Settings(
        limitMode = LimitMode.MG, dailyLimitCount = 8, dailyLimitMg = 45.0, taperEnabled = true,
        taperStartDay = 20_300L, taperStartValue = 60.0, taperTargetDay = 20_360L, taperTargetValue = 30.0,
        dayStartHour = 4, bodyWeightKg = 72.0, pk = PkParams(absorptionMinutes = 25), themeMode = ThemeMode.DARK,
    )

    @Test
    fun exportReadsBackUnchanged() {
        val contents = Backup.parse(Export.json(entries, types, settings, appVersion = "0.7.5"))
        assertEquals(types, contents.types)
        assertEquals(entries, contents.entries)
        assertEquals(settings, contents.settingsOver(Settings()))
        assertNotNull(contents.exportedAt)
    }

    @Test
    fun exportsFrom070StillRead() {
        val old = """
            {"exportedAt":"2026-10-18T21:00:00+03:00",
             "types":[{"id":1,"name":"Velo Shift","strengthMg":11,"colorIndex":0,"archived":false,"createdAt":5,"brand":"Velo","releaseK":0.1},
                      {"id":2,"name":"Loop","strengthMg":6,"colorIndex":1,"archived":false,"createdAt":6,"brand":"Loop","releaseK":0.021}],
             "entries":[{"id":1,"pouchTypeId":1,"timestamp":1000,"strengthMg":11,"endTimestamp":null,"releaseK":0.1}],
             "settings":{"limitMode":"COUNT","dailyLimitCount":9,"pk":{"absorptionMinutes":30}}}
        """.trimIndent()
        val contents = Backup.parse(old)
        assertTrue(contents.types[0].fastRelease) // Velo's own rate is below the fast-release one
        assertFalse(contents.types[1].fastRelease)
        assertEquals(listOf(0, 1), contents.types.map { it.sortOrder })
        assertNull(contents.entries[0].endTimestamp)

        val restored = contents.settingsOver(Settings(themeMode = ThemeMode.LIGHT, bodyWeightKg = 72.0))
        assertEquals(9, restored.dailyLimitCount)
        assertEquals(ThemeMode.LIGHT, restored.themeMode) // not in the old file, so kept
        assertEquals(72.0, restored.bodyWeightKg, 0.0)
    }

    @Test
    fun otherFilesAreRefused() {
        assertRefused("hello")
        assertRefused("{}")
        assertRefused("""{"app":"Other","types":[],"entries":[]}""")
        assertRefused("""{"types":[{"id":1,"name":"A","strengthMg":4},{"id":1,"name":"B","strengthMg":6}],"entries":[]}""")
        assertRefused("""{"types":[{"id":1,"strengthMg":4}],"entries":[]}""")
        try {
            Backup.parse("""{"app":"Pouchwise","format":99,"types":[],"entries":[]}""")
            fail("a newer format must be refused")
        } catch (e: Backup.NewerFormat) {
            assertEquals(99, e.format)
        }
    }

    private fun assertRefused(text: String) {
        try {
            Backup.parse(text)
            fail("should not parse: $text")
        } catch (expected: Backup.NotAnExport) {
            assertNotNull(expected.message)
        }
    }
}
