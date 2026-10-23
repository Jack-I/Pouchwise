package app.pouchwise.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.pouchwise.R
import app.pouchwise.data.Backup
import app.pouchwise.data.Felt
import app.pouchwise.data.FeltMark
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PlanVersion
import app.pouchwise.data.PouchType
import app.pouchwise.model.DayKeys
import app.pouchwise.model.IntervalPlan
import app.pouchwise.model.Levels
import app.pouchwise.model.Pharmacokinetics
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Export {
    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun csv(entries: List<LogEntry>, types: List<PouchType>, settings: Settings, marks: List<FeltMark> = emptyList()): String {
        val byId = types.associateBy { it.id }
        val latest = marks.groupBy { it.entryId }.mapValues { (_, own) -> own.maxWith(compareBy({ it.atMs }, { it.id })) }
        val now = System.currentTimeMillis()
        val header = "id,timestamp_iso,epoch_ms,end_epoch_ms,duration_min,pouch,brand,strength_mg,release_k,absorbed_mg,pouch_min,day," +
            "price,plan_level_mg,felt,felt_after_min,felt_recalled"
        return table(header, entries.map { e ->
            val type = byId[e.pouchTypeId]
            val last = latest[e.id]
            listOf(
                e.id, iso(e.timestamp), e.timestamp, e.endTimestamp ?: "", dec(PouchTiming.effectiveDurationMs(e, settings) / 60_000.0, 1),
                quote(type?.name ?: ""), quote(type?.brand ?: ""), dec(e.strengthMg, 2), dec(e.releaseK, 4),
                dec(PouchTiming.absorbedMg(e, settings, now), 3), PouchTiming.pouchMinutes(e, settings),
                DayKeys.localDate(DayKeys.of(e, settings.dayStartHour)),
                e.pricePerPouch?.let { dec(it, 2) } ?: "", e.planLevelMg?.let { dec(it, 3) } ?: "",
                last?.let { Felt.of(it.level)?.code } ?: "", last?.let { minutesAfter(e.timestamp, it.atMs) } ?: "",
                last?.let { if (it.recalled) 1 else 0 } ?: "",
            )
        })
    }

    /** One row per felt answer, with the model's levels at that moment, when the pouch went in and when it came out. */
    fun feltCsv(entries: List<LogEntry>, types: List<PouchType>, settings: Settings, marks: List<FeltMark>): String {
        val byId = types.associateBy { it.id }
        val sorted = entries.sortedWith(compareBy({ it.timestamp }, { it.id }))
        val entryById = sorted.associateBy { it.id }
        val previous = sorted.zipWithNext().associate { (before, e) -> e.id to before }
        val firsts = IntervalPlan.firstOfDayIds(sorted, settings)
        val doses = sorted.map { PouchTiming.toDose(it, settings) }
        fun ng(mg: Double, vd: Double) = Pharmacokinetics.toNgPerMl(mg, settings.bodyWeightKg, vd)
        fun nicotineAt(ms: Long) = ng(Pharmacokinetics.levelsAt(ms, doses, settings.pk).nicotineMg, Pharmacokinetics.NICOTINE_VD_L_PER_KG)
        fun freePct(nicotineNg: Double) = dec(100.0 * (1.0 - Levels.receptorsBusy(nicotineNg)), 0)
        val header = "entry_id,pouch,brand,strength_mg,release_k,toss_in_iso,mark_iso,min_after_toss_in,in_mouth,felt,recalled," +
            "nicotine_ng_ml,cotinine_ng_ml,receptors_free_pct,nicotine_at_toss_in_ng_ml,receptors_free_at_toss_in_pct," +
            "nicotine_at_pull_out_ng_ml,receptors_free_at_pull_out_pct,min_since_prev_pouch,first_of_day"
        return table(header, marks.sortedWith(compareBy({ it.atMs }, { it.id })).mapNotNull { m ->
            val e = entryById[m.entryId] ?: return@mapNotNull null
            val felt = Felt.of(m.level) ?: return@mapNotNull null
            val type = byId[e.pouchTypeId]
            val atMark = Pharmacokinetics.levelsAt(m.atMs, doses, settings.pk)
            val nicotine = ng(atMark.nicotineMg, Pharmacokinetics.NICOTINE_VD_L_PER_KG)
            val atStart = nicotineAt(e.timestamp)
            val out = PouchTiming.outMs(e, settings)
            val atOut = nicotineAt(out)
            listOf(
                e.id, quote(type?.name ?: ""), quote(type?.brand ?: ""), dec(e.strengthMg, 2), dec(e.releaseK, 4),
                iso(e.timestamp), iso(m.atMs), minutesAfter(e.timestamp, m.atMs), if (m.atMs < out) 1 else 0,
                felt.code, if (m.recalled) 1 else 0,
                dec(nicotine, 1), dec(ng(atMark.cotinineMg, Pharmacokinetics.COTININE_VD_L_PER_KG), 1), freePct(nicotine),
                dec(atStart, 1), freePct(atStart), dec(atOut, 1), freePct(atOut),
                previous[e.id]?.let { minutesAfter(it.timestamp, e.timestamp) } ?: "", if (e.id in firsts) 1 else 0,
            )
        })
    }

    private fun table(header: String, rows: List<List<Any>>): String =
        (listOf(header) + rows.map { it.joinToString(",") }).joinToString("\n", postfix = "\n")

    private fun dec(v: Double, places: Int): String = String.format(Locale.ROOT, "%.${places}f", v)

    private fun iso(ms: Long): String = isoFormatter.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    private fun minutesAfter(fromMs: Long, atMs: Long): Long = Math.round((atMs - fromMs) / 60_000.0)

    /** Everything needed to restore the app on another phone; read back by [Backup.parse]. */
    fun json(
        entries: List<LogEntry>,
        types: List<PouchType>,
        settings: Settings,
        appVersion: String = "",
        plans: List<PlanVersion> = emptyList(),
        marks: List<FeltMark> = emptyList(),
    ): String {
        val root = JSONObject()
        root.put("app", "Pouchwise")
        root.put("format", Backup.FORMAT)
        if (appVersion.isNotEmpty()) root.put("appVersion", appVersion)
        root.put("exportedAt", isoFormatter.format(Instant.now().atZone(ZoneId.systemDefault())))
        root.put("types", JSONArray().apply {
            types.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("name", t.name); put("strengthMg", t.strengthMg)
                    put("colorIndex", t.colorIndex); put("sortOrder", t.sortOrder); put("archived", t.archived)
                    put("createdAt", t.createdAt); put("brand", t.brand); put("releaseK", t.releaseK)
                    put("fastRelease", t.fastRelease)
                    put("pricePerCan", t.pricePerCan ?: JSONObject.NULL); put("pouchesPerCan", t.pouchesPerCan)
                })
            }
        })
        root.put("entries", JSONArray().apply {
            entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("pouchTypeId", e.pouchTypeId)
                    put("timestamp", e.timestamp); put("strengthMg", e.strengthMg)
                    put("endTimestamp", e.endTimestamp ?: JSONObject.NULL)
                    put("releaseK", e.releaseK)
                    put("pouchMinutes", e.pouchMinutes ?: JSONObject.NULL)
                    put("day", e.day ?: JSONObject.NULL)
                    put("pricePerPouch", e.pricePerPouch ?: JSONObject.NULL)
                    put("planLevelMg", e.planLevelMg ?: JSONObject.NULL)
                })
            }
        })
        root.put("feltMarks", JSONArray().apply {
            marks.forEach { m ->
                val felt = Felt.of(m.level) ?: return@forEach
                put(JSONObject().apply { put("entryId", m.entryId); put("felt", felt.code); put("atMs", m.atMs); put("recalled", m.recalled) })
            }
        })
        root.put("planHistory", JSONArray().apply {
            plans.forEach { p ->
                put(JSONObject().apply {
                    put("fromDay", p.fromDay); put("limitMode", p.limitMode)
                    put("dailyLimitCount", p.dailyLimitCount); put("dailyLimitMg", finite(p.dailyLimitMg))
                    put("taperEnabled", p.taperEnabled)
                    put("taperStartDay", p.taperStartDay); put("taperStartValue", finite(p.taperStartValue))
                    put("taperTargetDay", p.taperTargetDay); put("taperTargetValue", finite(p.taperTargetValue))
                })
            }
        })
        root.put("settings", JSONObject().apply {
            put("limitMode", settings.limitMode.name)
            put("dailyLimitCount", settings.dailyLimitCount)
            put("dailyLimitMg", finite(settings.dailyLimitMg))
            put("taperEnabled", settings.taperEnabled)
            put("taperStartDay", settings.taperStartDay)
            put("taperStartValue", finite(settings.taperStartValue))
            put("taperTargetDay", settings.taperTargetDay)
            put("taperTargetValue", finite(settings.taperTargetValue))
            put("dayStartHour", settings.dayStartHour)
            put("bodyWeightKg", finite(settings.bodyWeightKg))
            put("pk", JSONObject().apply {
                put("absorptionFraction", settings.pk.absorptionFraction)
                put("absorptionMinutes", settings.pk.absorptionMinutes)
                put("nicotineHalfLifeHours", settings.pk.nicotineHalfLifeHours)
                put("cotinineHalfLifeHours", settings.pk.cotinineHalfLifeHours)
                put("cotinineYield", settings.pk.cotinineYield)
            })
            put("themeMode", settings.themeMode.name)
            put("intervalMode", settings.intervalMode.name)
            put("baselinePerDay", settings.baselinePerDay ?: JSONObject.NULL)
            put("currency", settings.currency)
        })
        return root.toString(2)
    }

    // Each builds the file and returns the share sheet for it: call them off the main thread, start the sheet on it.

    fun csvChooser(context: Context, entries: List<LogEntry>, types: List<PouchType>, settings: Settings, marks: List<FeltMark>): Intent =
        chooser(context, "pouchwise-${stamp()}.csv", "text/csv", csv(entries, types, settings, marks))

    fun feltCsvChooser(context: Context, entries: List<LogEntry>, types: List<PouchType>, settings: Settings, marks: List<FeltMark>): Intent =
        chooser(context, "pouchwise-felt-${stamp()}.csv", "text/csv", feltCsv(entries, types, settings, marks))

    fun jsonChooser(
        context: Context, entries: List<LogEntry>, types: List<PouchType>, settings: Settings, plans: List<PlanVersion>, marks: List<FeltMark>,
    ): Intent {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }.getOrDefault("")
        return chooser(context, "pouchwise-${stamp()}.json", "application/json", json(entries, types, settings, version, plans, marks))
    }

    /** org.json refuses infinite numbers; settings saved by older versions could hold one. */
    private fun finite(v: Double): Double = if (v.isFinite()) v else 0.0

    private fun chooser(context: Context, fileName: String, mime: String, content: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, context.getString(R.string.share_export))
    }

    private fun stamp(): String =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT).format(Instant.now().atZone(ZoneId.systemDefault()))

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
