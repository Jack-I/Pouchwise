package app.pouchwise.data

import app.pouchwise.model.Brands
import app.pouchwise.model.IntervalMode
import app.pouchwise.model.LimitMode
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import app.pouchwise.model.ThemeMode
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs

/** Reads a JSON export back. A file is accepted only when it parses completely; nothing is half-read. */
object Backup {
    /** Layout version written by the JSON export. Files without it come from 0.7.0 and earlier. */
    const val FORMAT = 1

    private const val MAX_STRENGTH_MG = 200.0
    private const val DAY_MS = 86_400_000L

    class NotAnExport(message: String) : Exception(message)
    class NewerFormat(val format: Int) : Exception("format $format")

    class Contents(
        val types: List<PouchType>,
        val entries: List<LogEntry>,
        /** Recorded plan versions; empty for files from before 0.7.7. */
        val plans: List<PlanVersion>,
        /** ISO date-time as written in the file, or null when the file lacks it. */
        val exportedAt: String?,
        private val settings: JSONObject?,
    ) {
        /** The file's settings over [current]; anything the file lacks, or that is not a sane number, keeps its current value. */
        fun settingsOver(current: Settings): Settings {
            val s = settings ?: return current
            val pk = s.optJSONObject("pk")
            return current.copy(
                limitMode = LimitMode.entries.firstOrNull { it.name == s.optString("limitMode") } ?: current.limitMode,
                dailyLimitCount = s.optInt("dailyLimitCount", current.dailyLimitCount).coerceIn(0, 1_000),
                dailyLimitMg = s.finite("dailyLimitMg", current.dailyLimitMg).coerceIn(0.0, 10_000.0),
                taperEnabled = s.optBoolean("taperEnabled", current.taperEnabled),
                taperStartDay = s.optLong("taperStartDay", current.taperStartDay),
                taperStartValue = s.finite("taperStartValue", current.taperStartValue).coerceIn(0.0, 10_000.0),
                taperTargetDay = s.optLong("taperTargetDay", current.taperTargetDay),
                taperTargetValue = s.finite("taperTargetValue", current.taperTargetValue).coerceIn(0.0, 10_000.0),
                dayStartHour = s.optInt("dayStartHour", current.dayStartHour).coerceIn(0, 12),
                bodyWeightKg = s.finite("bodyWeightKg", current.bodyWeightKg).coerceIn(30.0, 250.0),
                pk = current.pk.copy(
                    absorptionMinutes = (pk?.optInt("absorptionMinutes", current.pk.absorptionMinutes) ?: current.pk.absorptionMinutes)
                        .coerceIn(5, PouchTiming.MAX_POUCH_MINUTES),
                ),
                themeMode = ThemeMode.entries.firstOrNull { it.name == s.optString("themeMode") } ?: current.themeMode,
                intervalMode = IntervalMode.entries.firstOrNull { it.name == s.optString("intervalMode") } ?: current.intervalMode,
                baselinePerDay = if (s.has("baselinePerDay") && !s.isNull("baselinePerDay")) s.optInt("baselinePerDay").coerceIn(0, 200)
                                 else current.baselinePerDay,
                currency = if (s.has("currency")) s.optString("currency").take(8) else current.currency,
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
        val plansJson = root.optJSONArray("planHistory")
        try {
            val types = (0 until typesJson.length()).map { i -> type(typesJson.getJSONObject(i), i) }
            val entries = (0 until entriesJson.length()).map { i -> entry(entriesJson.getJSONObject(i)) }
            val plans = if (plansJson == null) emptyList() else (0 until plansJson.length()).map { i -> plan(plansJson.getJSONObject(i)) }
            if (types.distinctBy { it.id }.size != types.size) throw NotAnExport("repeated pouch ids")
            if (entries.distinctBy { it.id }.size != entries.size) throw NotAnExport("repeated entry ids")
            if (plans.distinctBy { it.fromDay }.size != plans.size) throw NotAnExport("repeated plan days")
            return Contents(types, entries, plans.sortedBy { it.fromDay }, root.optString("exportedAt").ifEmpty { null }, root.optJSONObject("settings"))
        } catch (e: JSONException) {
            throw NotAnExport(e.message ?: "unreadable field")
        }
    }

    private fun type(o: JSONObject, index: Int): PouchType {
        val brand = o.optString("brand", "")
        val releaseK = o.finite("releaseK", Brands.kFor(brand)).takeIf { it > 0.0 && it <= 1.0 } ?: Brands.kFor(brand)
        return PouchType(
            id = o.getLong("id"),
            name = o.getString("name"),
            strengthMg = strength(o),
            colorIndex = o.optInt("colorIndex", 0),
            sortOrder = o.optInt("sortOrder", index),
            archived = o.optBoolean("archived", false),
            createdAt = o.optLong("createdAt", 0L),
            brand = brand,
            releaseK = releaseK,
            // Exports from 0.7.0 lack the tick; the release constant gives it away.
            fastRelease = if (o.has("fastRelease")) o.getBoolean("fastRelease")
                          else releaseK >= Brands.FAST_K - 1e-9 && Brands.kFor(brand) < Brands.FAST_K,
            pricePerCan = o.amount("pricePerCan"),
            pouchesPerCan = o.optInt("pouchesPerCan", 20).coerceIn(1, 1_000),
        )
    }

    private fun entry(o: JSONObject): LogEntry {
        val start = o.getLong("timestamp")
        val end = if (o.isNull("endTimestamp")) null else o.getLong("endTimestamp")
        if (start <= 0L || (end != null && end < start)) throw NotAnExport("bad times")
        val felt = Felt.ofCode(o.optString("felt"))
        return LogEntry(
            id = o.getLong("id"),
            pouchTypeId = o.getLong("pouchTypeId"),
            timestamp = start,
            strengthMg = strength(o),
            endTimestamp = end,
            releaseK = o.finite("releaseK", Brands.averageK).takeIf { it > 0.0 && it <= 1.0 } ?: Brands.averageK,
            pouchMinutes = if (o.isNull("pouchMinutes")) null else o.getInt("pouchMinutes").coerceIn(1, PouchTiming.MAX_POUCH_MINUTES),
            // A day far from the timestamp is corrupt; null makes the import recompute it.
            day = if (o.isNull("day")) null else o.getLong("day").takeIf { abs(it - Math.floorDiv(start, DAY_MS)) <= 2 },
            pricePerPouch = o.amount("pricePerPouch"),
            planLevelMg = o.amount("planLevelMg")?.takeIf { it <= 100.0 },
            felt = felt?.level,
            feltAt = if (felt == null || o.isNull("feltAt")) null else o.optLong("feltAt").takeIf { it > 0L },
        )
    }

    private fun plan(o: JSONObject): PlanVersion {
        val mode = o.getString("limitMode")
        if (LimitMode.entries.none { it.name == mode }) throw NotAnExport("bad limit mode")
        fun value(name: String) = o.getDouble(name).also { if (!it.isFinite() || it < 0) throw NotAnExport("bad $name") }
        return PlanVersion(
            fromDay = o.getLong("fromDay"),
            limitMode = mode,
            dailyLimitCount = o.getInt("dailyLimitCount").coerceAtLeast(0),
            dailyLimitMg = value("dailyLimitMg"),
            taperEnabled = o.getBoolean("taperEnabled"),
            taperStartDay = o.getLong("taperStartDay"),
            taperStartValue = value("taperStartValue"),
            taperTargetDay = o.getLong("taperTargetDay"),
            taperTargetValue = value("taperTargetValue"),
        )
    }

    private fun strength(o: JSONObject): Double {
        val mg = o.getDouble("strengthMg")
        if (!mg.isFinite() || mg <= 0.0 || mg > MAX_STRENGTH_MG) throw NotAnExport("bad strength $mg")
        return mg
    }

    /** An optional non-negative amount; missing, null or not a sane number gives null. */
    private fun JSONObject.amount(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { it.isFinite() && it >= 0.0 && it < 1e9 }

    /** A number field, or [fallback] when it is missing or not finite (org.json reads "NaN" and "Infinity" strings). */
    private fun JSONObject.finite(name: String, fallback: Double): Double =
        optDouble(name, fallback).takeIf { it.isFinite() } ?: fallback
}
