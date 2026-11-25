package app.pouchwise.model

import app.pouchwise.data.PouchEdit
import app.pouchwise.data.PouchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HiddenMatchTest {
    private fun hidden(id: Long, brand: String, flavour: String, mg: Double, createdAt: Long = 0, archived: Boolean = true) =
        PouchType(id = id, name = Brands.displayName(brand, flavour), brand = brand, strengthMg = mg, archived = archived, createdAt = createdAt)

    private fun edit(brand: String, flavour: String, mg: Double) =
        PouchEdit(brand, flavour, mg, colorIndex = 0, release = Release.NORMAL, pricePerCan = null, pouchesPerCan = 20)

    @Test
    fun aTypoInTheFlavourStillFindsIt() {
        val mango = hidden(1, "Velo", "tropical mango", 6.0)
        val cassis = hidden(2, "Skruf", "Purple cassice", 6.0)
        val mint = hidden(3, "Loop", "mint", 7.1)
        val all = listOf(mango, cassis, mint)
        assertEquals(mango, HiddenMatch.find(edit("Velo", "tropicl mango", 6.0), all))
        assertEquals(cassis, HiddenMatch.find(edit("Skruf", "Purple cassis", 6.0), all))
        // Two letters swapped are one typo, which a four-letter flavour is allowed.
        assertEquals(mint, HiddenMatch.find(edit("Loop", "mnit", 7.1), all))
    }

    @Test
    fun caseAccentsSpacesAndPunctuationDoNotCount() {
        val limoncello = hidden(1, "Après", "Limoncello", 5.5)
        val watermelon = hidden(2, "Übbs", "watermelon", 6.0)
        val creme = hidden(3, "Velo", "Crème brûlée", 6.0)
        val iceMint = hidden(4, "Ёрш", "Ледяная мята", 6.0)
        val all = listOf(limoncello, watermelon, creme, iceMint)
        assertEquals(limoncello, HiddenMatch.find(edit("apres", "LIMONCELLO", 5.5), all))
        assertEquals(watermelon, HiddenMatch.find(edit("Ubbs", "water-melon", 6.0), all))
        assertEquals(creme, HiddenMatch.find(edit("velo ", "creme brulee", 6.0), all))
        assertEquals(iceMint, HiddenMatch.find(edit("ерш", "ЛЕДЯНАЯ МЯТА", 6.0), all))
    }

    @Test
    fun anotherStrengthOrBrandIsAnotherPouch() {
        val all = listOf(hidden(1, "ZYN", "lemon spritz", 6.5))
        assertNull(HiddenMatch.find(edit("ZYN", "lemon spritz", 6.0), all))
        assertNull(HiddenMatch.find(edit("Velo", "lemon spritz", 6.5), all))
    }

    @Test
    fun aLongerLineOrAnotherFlavourIsAnotherPouch() {
        val all = listOf(hidden(1, "ZYN", "black cherry mini", 3.0), hidden(2, "ZYN", "lemon spritz", 6.5), hidden(3, "Après", "Cactus lime", 4.4))
        assertNull(HiddenMatch.find(edit("ZYN", "black cherry", 3.0), all))
        assertNull(HiddenMatch.find(edit("ZYN", "lime spritz", 6.5), all))
        assertNull(HiddenMatch.find(edit("Après", "Cactus lemon", 4.4), all))
    }

    @Test
    fun onlyHiddenPouchesCount() {
        val shown = hidden(1, "Camo", "zesty orange", 6.0, archived = false)
        assertNull(HiddenMatch.find(edit("Camo", "zesty orange", 6.0), listOf(shown)))
    }

    @Test
    fun noFlavourMatchesOnlyNoFlavour() {
        val plain = hidden(1, "ZYN", "", 6.0)
        assertEquals(plain, HiddenMatch.find(edit("ZYN", "", 6.0), listOf(plain)))
        assertNull(HiddenMatch.find(edit("ZYN", "mint", 6.0), listOf(plain)))
        assertNull(HiddenMatch.find(edit("ZYN", "", 6.0), listOf(hidden(2, "ZYN", "mint", 6.0))))
    }

    @Test
    fun aPouchFromBeforeBrandsMatchesByItsName() {
        val old = PouchType(id = 1, name = "Siberia", brand = "", strengthMg = 43.0, archived = true)
        assertEquals(old, HiddenMatch.find(edit("Siberia", "", 43.0), listOf(old)))
    }

    @Test
    fun theClosestFlavourWinsThenTheNewest() {
        val near = hidden(1, "Velo", "tropical mang", 6.0)
        val exact = hidden(2, "Velo", "tropical mango", 6.0)
        assertEquals(exact, HiddenMatch.find(edit("Velo", "tropical mango", 6.0), listOf(near, exact)))
        val older = hidden(3, "Velo", "tropical mango", 6.0, createdAt = 1)
        val newer = hidden(4, "Velo", "tropical mango", 6.0, createdAt = 2)
        assertEquals(newer, HiddenMatch.find(edit("Velo", "tropical mango", 6.0), listOf(older, newer)))
    }

    @Test
    fun shortIAndYiAreLettersOfTheirOwn() {
        val moi = hidden(1, "Мои", "мята", 6.0)
        val izhak = hidden(2, "Іжак", "м'ята", 6.0)
        val moy = hidden(3, "Мой", "мята", 6.0)
        assertNull(HiddenMatch.find(edit("Мой", "мята", 6.0), listOf(moi)))
        assertNull(HiddenMatch.find(edit("Їжак", "м'ята", 6.0), listOf(izhak)))
        assertEquals(moy, HiddenMatch.find(edit("МОЙ", "мята", 6.0), listOf(moy)))
    }

    @Test
    fun aBrandWithNoLettersMatchesOnlyItself() {
        val fire = hidden(1, "🔥", "", 6.0)
        assertNull(HiddenMatch.find(edit("💧", "", 6.0), listOf(fire)))
        assertNull(HiddenMatch.find(edit("!!!", "", 6.0), listOf(fire)))
        assertEquals(fire, HiddenMatch.find(edit("🔥", "", 6.0), listOf(fire)))
    }

    @Test
    fun anotherNumberIsAnotherPouch() {
        val mint17 = hidden(1, "Loop", "Mint 17", 6.0)
        assertNull(HiddenMatch.find(edit("Loop", "Mint 11", 6.0), listOf(mint17)))
        assertNull(HiddenMatch.find(edit("Loop", "Mint 7", 6.0), listOf(mint17)))
        assertEquals(mint17, HiddenMatch.find(edit("Loop", "Mnit 17", 6.0), listOf(mint17)))
    }

    @Test
    fun theTyposAllowedGrowAtEightAndSixteenLetters() {
        fun check(flavour: String, ok: String, tooMany: String) {
            val t = hidden(1, "Velo", flavour, 6.0)
            assertEquals(flavour, t, HiddenMatch.find(edit("Velo", ok, 6.0), listOf(t)))
            assertNull(flavour, HiddenMatch.find(edit("Velo", tooMany, 6.0), listOf(t)))
        }
        check("abcdefg", ok = "abcdefx", tooMany = "abcdexx")
        check("abcdefgh", ok = "abcdefxx", tooMany = "abcdexxx")
        check("abcdefghijklmno", ok = "abcdefghijklmxx", tooMany = "abcdefghijklxxx")
        check("abcdefghijklmnop", ok = "abcdefghijklmxxx", tooMany = "abcdefghijklxxxx")
    }

    @Test
    fun aPouchFromBeforeBrandsDoesNotMatchAFlavourOrAnotherBrand() {
        val old = PouchType(id = 1, name = "Siberia", brand = "", strengthMg = 43.0, archived = true)
        assertNull(HiddenMatch.find(edit("Siberia", "white dry", 43.0), listOf(old)))
        assertNull(HiddenMatch.find(edit("Odens", "", 43.0), listOf(old)))
    }
}
