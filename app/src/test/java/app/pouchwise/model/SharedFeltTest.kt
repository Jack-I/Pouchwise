package app.pouchwise.model

import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedFeltTest {
    private val min = 60_000L
    private val t0 = 1_763_200_000_000L
    private val s = Settings()

    /** In and out in minutes from [t0]. */
    private fun pouch(id: Long, inAt: Long, outAt: Long) = LogEntry(
        id = id, pouchTypeId = 1, timestamp = t0 + inAt * min, strengthMg = 6.0, endTimestamp = t0 + outAt * min, pouchMinutes = 60,
    )

    @Test
    fun anAnswerGoesToEveryPouchInTheMouth() {
        val a = pouch(1, 0, 30)
        val b = pouch(2, 5, 35)
        val added = SharedFelt.toAdd(listOf(a, b), emptyMap(), Felt.HIT, t0 + 10 * min)
        assertEquals(listOf(1L, 2L), added.map { it.entryId })
        assertEquals(setOf(Felt.HIT.level), added.map { it.level }.toSet())
        assertEquals(setOf(t0 + 10 * min), added.map { it.atMs }.toSet())
        // Once a is out, only b is in the mouth.
        assertEquals(listOf(2L), SharedFelt.toAdd(listOf(b), emptyMap(), Felt.A_BIT, t0 + 32 * min).map { it.entryId })
    }

    @Test
    fun aPouchAlreadyAtThatAnswerKeepsItsOwn() {
        val a = pouch(1, 0, 30)
        val b = pouch(2, 5, 35)
        val marks = mapOf(1L to listOf(FeltMark(id = 7, entryId = 1, level = Felt.HIT.level, atMs = t0 + 3 * min)))
        assertEquals(listOf(2L), SharedFelt.toAdd(listOf(a, b), marks, Felt.HIT, t0 + 10 * min).map { it.entryId })
    }

    @Test
    fun takingBackRemovesOnlyTheAnswerGivenTogether() {
        val a = pouch(1, 0, 30)
        val b = pouch(2, 5, 35)
        val at = t0 + 10 * min
        val together = mapOf(
            1L to listOf(FeltMark(id = 1, entryId = 1, level = Felt.HIT.level, atMs = at)),
            2L to listOf(FeltMark(id = 2, entryId = 2, level = Felt.HIT.level, atMs = at)),
        )
        assertEquals(listOf(1L, 2L), SharedFelt.toTakeBack(a, together).map { it.id })
        val apart = mapOf(
            1L to listOf(FeltMark(id = 1, entryId = 1, level = Felt.HIT.level, atMs = at)),
            2L to listOf(FeltMark(id = 2, entryId = 2, level = Felt.HIT.level, atMs = at - min)),
        )
        assertEquals(listOf(1L), SharedFelt.toTakeBack(a, apart).map { it.id })
        assertEquals(emptyList<FeltMark>(), SharedFelt.toTakeBack(b, emptyMap()))
    }

    @Test
    fun takingBackReachesAPartnerAlreadyOut() {
        // a and b got HIT together at 10; b came out at 15, and the answer is taken back on a at 16.
        val a = pouch(1, 0, 30)
        val marks = mapOf(
            1L to listOf(FeltMark(id = 1, entryId = 1, level = Felt.HIT.level, atMs = t0 + 10 * min)),
            2L to listOf(FeltMark(id = 2, entryId = 2, level = Felt.HIT.level, atMs = t0 + 10 * min)),
        )
        assertEquals(listOf(1L, 2L), SharedFelt.toTakeBack(a, marks).map { it.id })
    }

    @Test
    fun pouchesInTogetherFormOneGroupAndTouchingDoesNot() {
        // a, b and c each overlap the next; d goes in as c comes out.
        val a = pouch(1, 0, 30)
        val b = pouch(2, 20, 50)
        val c = pouch(3, 45, 75)
        val d = pouch(4, 75, 100)
        val e = pouch(5, 200, 230)
        val groups = SharedFelt.groups(listOf(e, d, c, b, a), s).map { g -> g.map { it.id } }
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(4L), listOf(5L)), groups)
    }

    @Test
    fun othersInMouthStopAtTheirPullOut() {
        val all = listOf(pouch(1, 0, 30), pouch(2, 20, 50))
        assertEquals(listOf(2L), SharedFelt.othersInMouth(1, t0 + 25 * min, all, s))
        assertEquals(listOf(1L), SharedFelt.othersInMouth(2, t0 + 20 * min, all, s))
        assertEquals(emptyList<Long>(), SharedFelt.othersInMouth(2, t0 + 30 * min, all, s))
    }
}
