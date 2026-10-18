package app.pouchwise.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.pouchwise.R
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.ReleaseSpeed
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

    fun csv(entries: List<LogEntry>, types: List<PouchType>, settings: Settings): String {
        val byId = types.associateBy { it.id }
        val zone = ZoneId.systemDefault()
        val sb = StringBuilder("id,timestamp_iso,epoch_ms,end_epoch_ms,duration_min,pouch,strength_mg,release_speed\n")
        for (e in entries) {
            val iso = isoFormatter.format(Instant.ofEpochMilli(e.timestamp).atZone(zone))
            val name = byId[e.pouchTypeId]?.name ?: ""
            val durationMin = PouchTiming.effectiveDurationMs(e, settings) / 60_000.0
            sb.append(e.id).append(',')
                .append(iso).append(',')
                .append(e.timestamp).append(',')
                .append(e.endTimestamp?.toString() ?: "").append(',')
                .append(String.format(Locale.ROOT, "%.1f", durationMin)).append(',')
                .append(quote(name)).append(',')
                .append(String.format(Locale.ROOT, "%.2f", e.strengthMg)).append(',')
                .append(ReleaseSpeed.fromInt(e.releaseSpeed).name.lowercase()).append('\n')
        }
        return sb.toString()
    }

    fun json(entries: List<LogEntry>, types: List<PouchType>, settings: Settings): String {
        val root = JSONObject()
        root.put("exportedAt", isoFormatter.format(Instant.now().atZone(ZoneId.systemDefault())))
        root.put("types", JSONArray().apply {
            types.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id); put("name", t.name); put("strengthMg", t.strengthMg)
                    put("colorIndex", t.colorIndex); put("archived", t.archived); put("createdAt", t.createdAt)
                    put("releaseSpeed", ReleaseSpeed.fromInt(t.releaseSpeed).name.lowercase())
                })
            }
        })
        root.put("entries", JSONArray().apply {
            entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id); put("pouchTypeId", e.pouchTypeId)
                    put("timestamp", e.timestamp); put("strengthMg", e.strengthMg)
                    put("endTimestamp", e.endTimestamp ?: JSONObject.NULL)
                    put("releaseSpeed", ReleaseSpeed.fromInt(e.releaseSpeed).name.lowercase())
                })
            }
        })
        root.put("settings", JSONObject().apply {
            put("limitMode", settings.limitMode.name)
            put("dailyLimitCount", settings.dailyLimitCount)
            put("dailyLimitMg", settings.dailyLimitMg)
            put("taperEnabled", settings.taperEnabled)
            put("taperStartDay", settings.taperStartDay)
            put("taperStartValue", settings.taperStartValue)
            put("taperTargetDay", settings.taperTargetDay)
            put("taperTargetValue", settings.taperTargetValue)
            put("dayStartHour", settings.dayStartHour)
            put("bodyWeightKg", settings.bodyWeightKg)
            put("pk", JSONObject().apply {
                put("absorptionFraction", settings.pk.absorptionFraction)
                put("absorptionMinutes", settings.pk.absorptionMinutes)
                put("nicotineHalfLifeHours", settings.pk.nicotineHalfLifeHours)
                put("cotinineHalfLifeHours", settings.pk.cotinineHalfLifeHours)
                put("cotinineYield", settings.pk.cotinineYield)
            })
        })
        return root.toString(2)
    }

    fun shareCsv(context: Context, entries: List<LogEntry>, types: List<PouchType>, settings: Settings) =
        share(context, "pouchwise-${stamp()}.csv", "text/csv", csv(entries, types, settings))

    fun shareJson(context: Context, entries: List<LogEntry>, types: List<PouchType>, settings: Settings) =
        share(context, "pouchwise-${stamp()}.json", "application/json", json(entries, types, settings))

    private fun share(context: Context, fileName: String, mime: String, content: String) {
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
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_export)))
    }

    private fun stamp(): String =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT).format(Instant.now().atZone(ZoneId.systemDefault()))

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
