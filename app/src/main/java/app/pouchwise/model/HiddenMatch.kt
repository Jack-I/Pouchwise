package app.pouchwise.model

import app.pouchwise.data.PouchEdit
import app.pouchwise.data.PouchType
import app.pouchwise.data.variant
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The hidden pouch one being added most likely is: same brand and strength, the same flavour but for typos. */
object HiddenMatch {
    fun find(edit: PouchEdit, types: List<PouchType>): PouchType? {
        val brand = fold(edit.brand)
        val flavour = fold(edit.variant)
        return types
            .filter { it.archived && fold(it.brand.ifBlank { it.name }) == brand && abs(it.strengthMg - edit.strengthMg) < 0.001 }
            .mapNotNull { t ->
                val other = fold(t.variant())
                val typos = distance(flavour, other)
                if (typos <= allowedTypos(max(flavour.length, other.length))) t to typos else null
            }
            .minWithOrNull(compareBy<Pair<PouchType, Int>> { it.second }.thenByDescending { it.first.createdAt })
            ?.first
    }

    private fun allowedTypos(letters: Int): Int = when {
        letters < 4 -> 0
        letters < 8 -> 1
        letters < 16 -> 2
        else -> 3
    }

    private fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }

    /** Optimal string alignment: a missing, extra or wrong letter, or two neighbours swapped, is one. */
    private fun distance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = min(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }
}
