package app.pouchwise.util

import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.model.Brands
import app.pouchwise.model.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportTest {
    private val min = 60_000L
    private val t0 = 1_761_307_200_000L  // 2025-10-24 12:00 UTC
    private val types = listOf(
        PouchType(id = 1, name = "Loop", strengthMg = 7.0, brand = "Loop", releaseK = Brands.kFor("Loop")),
        PouchType(id = 2, name = "ZYN", strengthMg = 6.5, brand = "ZYN", releaseK = Brands.kFor("ZYN")),
    )
    private val entries = listOf(
        LogEntry(id = 1, pouchTypeId = 1, timestamp = t0, strengthMg = 7.0, endTimestamp = t0 + 30 * min, releaseK = Brands.kFor("Loop"), pouchMinutes = 30),
        LogEntry(id = 2, pouchTypeId = 2, timestamp = t0 + 65 * min, strengthMg = 6.5, releaseK = Brands.kFor("ZYN"), pouchMinutes = 30),
    )
    private val marks = listOf(
        FeltMark(entryId = 1, level = Felt.NOTHING.level, atMs = t0 + 12 * min),
        FeltMark(entryId = 2, level = Felt.HIT.level, atMs = t0 + 68 * min),
        FeltMark(entryId = 2, level = Felt.OVERWHELM.level, atMs = t0 + 85 * min),
        FeltMark(entryId = 1, level = Felt.A_BIT.level, atMs = t0 + 40 * min),
    )

    @Test
    fun feltAnswersCarryTheModelLevelsAndTheirPlaceInTheDay() {
        val rows = Export.feltCsv(entries, types, Settings(bodyWeightKg = 72.0), marks).trim().lines()
        val names = rows[0].split(',')
        fun cell(row: Int, name: String) = rows[row].split(',')[names.indexOf(name)]

        assertEquals(5, rows.size)
        assertEquals(listOf("nothing", "a_bit", "hit", "overwhelm"), (1..4).map { cell(it, "felt") })
        assertEquals(listOf("12", "40", "3", "20"), (1..4).map { cell(it, "min_after_toss_in") })
        assertEquals(listOf("1", "0", "1", "1"), (1..4).map { cell(it, "in_mouth") })
        assertEquals(listOf("1", "1", "0", "0"), (1..4).map { cell(it, "first_of_day") })
        assertEquals("", cell(1, "min_since_prev_pouch"))
        assertEquals("65", cell(3, "min_since_prev_pouch"))
        // The first pouch is still in the blood when the second goes in, and the second keeps raising the level.
        assertTrue(cell(3, "nicotine_at_toss_in_ng_ml").toDouble() > 0.5)
        assertTrue(cell(4, "nicotine_ng_ml").toDouble() > cell(3, "nicotine_ng_ml").toDouble())
        assertTrue(cell(4, "receptors_free_pct").toInt() < cell(3, "receptors_free_pct").toInt())
    }

    @Test
    fun thePouchCsvShowsTheLatestAnswer() {
        val rows = Export.csv(entries, types, Settings(), marks).trim().lines()
        val names = rows[0].split(',')
        fun cell(row: Int, name: String) = rows[row].split(',')[names.indexOf(name)]
        assertEquals("a_bit", cell(1, "felt"))
        assertEquals("40", cell(1, "felt_after_min"))
        assertEquals("overwhelm", cell(2, "felt"))
        assertEquals("20", cell(2, "felt_after_min"))
    }
}
