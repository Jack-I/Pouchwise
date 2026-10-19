package app.pouchwise.data

import app.pouchwise.model.Brands
import app.pouchwise.model.LimitMode
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import app.pouchwise.model.ThemeMode
import org.json.JSONException
import org.json.JSONObject

/** Reads a JSON export back. A file is accepted only when it parses completely; nothing is half-read. */
object Backup {
    /** Layout version written by the JSON export. Files without it come from 0.7.0 and earlier. */
    const val FORMAT = 1

    class NotAnExport(message: String) : Exception(message)
    class NewerFormat(val format: Int) : Exception("format $format")

    class Contents(
        val types: List<PouchType>,
        val entries: List<LogEntry>,
        /** ISO date-time as written in the file, or null when the file lacks it. */
        val exportedAt: String?,
        private val settings: JSONObject?,
    ) {
        /** The file's settings over [current]; anything the file lacks keeps its current value. */
        fun settingsOver(current: Settings): Settings {
            val s = settings ?: return current
            val pk = s.optJSONObject("pk")
            return current.copy(
                limitMode = LimitMode.entries.firstOrNull { it.name == s.optString("limitMode") } ?: current.limitMode,
                dailyLimitCount = s.optInt("dailyLimitCount", current.dailyLimitCount).coerceAtLeast(0),
                dailyLimitMg = s.optDouble("dailyLimitMg", current.dailyLimitMg).coerceAtLeast(0.0),
                taperEnabled = s.optBoolean("taperEnabled", current.taperEnabled),
                taperStartDay = s.optLong("taperStartDay", current.taperStartDay),
                taperStartValue = s.optDouble("taperStartValue", current.taperStartValue),
                taperTargetDay = s.optLong("taperTargetDay", current.taperTargetDay),
                taperTargetValue = s.optDouble("taperTargetValue", current.taperTargetValue),
                dayStartHour = s.optInt("dayStartHour", current.dayStartHour).coerceIn(0, 12),
                bodyWeightKg = s.optDouble("bodyWeightKg", current.bodyWeightKg).coerceIn(30.0, 250.0),
                pk = current.pk.copy(
                    absorptionMinutes = (pk?.optInt("absorptionMinutes", current.pk.absorptionMinutes) ?: current.pk.absorptionMinutes)
                        .coerceIn(5, 90),
                ),
                themeMode = ThemeMode.entries.firstOrNull { it.name == s.optString("themeMode") } ?: current.themeMode,
            )
        }
    }

    fun parse(text: String): Contents {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw NotAnExport("not a JSON object")
        }
        val app = root.optString("app")
        if (app.isNotEmpty() && app != "Pouchwise") throw NotAnExport("made by $app")
        val format = root.optInt("format", 0)
        if (format > FORMAT) throw NewerFormat(format)
        val typesJson = root.optJSONArray("types") ?: throw NotAnExport("no pouches")
        val entriesJson = root.optJSONArray("entries") ?: throw NotAnExport("no entries")
        try {
            val types = (0 until typesJson.length()).map { i -> type(typesJson.getJSONObject(i), i) }
            val entries = (0 until entriesJson.length()).map { i -> entry(entriesJson.getJSONObject(i)) }
            if (types.distinctBy { it.id }.size != types.size) throw NotAnExport("repeated pouch ids")
            if (entries.distinctBy { it.id }.size != entries.size) throw NotAnExport("repeated entry ids")
            return Contents(types, entries, root.optString("exportedAt").ifEmpty { null }, root.optJSONObject("settings"))
        } catch (e: JSONException) {
            throw NotAnExport(e.message ?: "unreadable field")
        }
    }

    private fun type(o: JSONObject, index: Int): PouchType {
        val brand = o.optString("brand", "")
        val releaseK = o.optDouble("releaseK", Brands.averageK)
        return PouchType(
            id = o.getLong("id"),
            name = o.getString("name"),
            strengthMg = o.getDouble("strengthMg"),
            colorIndex = o.optInt("colorIndex", 0),
            sortOrder = o.optInt("sortOrder", index),
            archived = o.optBoolean("archived", false),
            createdAt = o.optLong("createdAt", 0L),
            brand = brand,
            releaseK = releaseK,
            // Exports from 0.7.0 lack the tick; the release constant gives it away.
            fastRelease = if (o.has("fastRelease")) o.getBoolean("fastRelease")
                          else releaseK >= Brands.FAST_K - 1e-9 && Brands.kFor(brand) < Brands.FAST_K,
        )
    }

    private fun entry(o: JSONObject): LogEntry = LogEntry(
        id = o.getLong("id"),
        pouchTypeId = o.getLong("pouchTypeId"),
        timestamp = o.getLong("timestamp"),
        strengthMg = o.getDouble("strengthMg"),
        endTimestamp = if (o.isNull("endTimestamp")) null else o.getLong("endTimestamp"),
        releaseK = o.optDouble("releaseK", Brands.averageK),
        pouchMinutes = if (o.isNull("pouchMinutes")) null else o.getInt("pouchMinutes").coerceIn(1, PouchTiming.MAX_POUCH_MINUTES),
    )
}
