package app.pouchwise.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.pouchwise.MainActivity
import app.pouchwise.R
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchLog
import app.pouchwise.data.PouchType
import app.pouchwise.data.SettingsRepository
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import app.pouchwise.ui.components.ColorDot
import app.pouchwise.ui.theme.PouchwiseTheme
import app.pouchwise.ui.theme.pouchColor
import app.pouchwise.util.Fmt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent activity behind the wide widget's buttons, shown over the home screen.
 * "+" asks which pouch goes in (logs straight away with a single pouch, opens the app with none);
 * "✓" takes the pouch out (asks which one when several are in).
 */
class QuickLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val removing = intent?.action == ACTION_REMOVE
        val settingsFlow = SettingsRepository(applicationContext).settings
        setContent {
            val settings by settingsFlow.collectAsState(initial = null)
            val mode = settings?.themeMode ?: return@setContent
            PouchwiseTheme(mode) {
                if (removing) RemoveChooser() else LogChooser()
            }
        }
    }

    /** Closes only once [work] is done: an open activity keeps the process from being frozen halfway through a write. */
    private fun finishAfter(work: suspend () -> Unit) {
        lifecycleScope.launch {
            withContext(NonCancellable + Dispatchers.IO) { work() }
            finish()
        }
    }

    @Composable
    private fun LogChooser() {
        var types by remember { mutableStateOf<List<PouchType>?>(null) }
        LaunchedEffect(Unit) {
            val list = withContext(Dispatchers.IO) { AppDatabase.get(applicationContext).dao().activeTypes() }
            when (list.size) {
                0 -> {
                    startActivity(Intent(this@QuickLogActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    finish()
                }
                1 -> finishAfter { QuickLog.log(applicationContext, list.first()) }
                else -> types = list
            }
        }
        val list = types ?: return
        Chooser(stringResource(R.string.quick_log_title)) {
            list.forEach { type ->
                ChooserRow(type.colorIndex, type.name, stringResource(R.string.mg_value, Fmt.mg(type.strengthMg))) {
                    finishAfter { QuickLog.log(applicationContext, type) }
                }
            }
        }
    }

    @Composable
    private fun RemoveChooser() {
        var loaded by remember { mutableStateOf<Pair<Settings, List<Pair<LogEntry, PouchType?>>>?>(null) }
        LaunchedEffect(Unit) {
            val settings = SettingsRepository(applicationContext).settings.first()
            val inNow = withContext(Dispatchers.IO) {
                WidgetData.pouchesInNow(applicationContext, settings, System.currentTimeMillis())
            }
            when (inNow.size) {
                0 -> finishAfter { QuickLog.nothingIn(applicationContext) }
                1 -> finishAfter { QuickLog.remove(applicationContext, inNow[0].first, inNow[0].second, settings) }
                else -> loaded = settings to inNow
            }
        }
        val (settings, list) = loaded ?: return
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1_000)
                now = System.currentTimeMillis()
            }
        }
        Chooser(stringResource(R.string.quick_remove_title)) {
            list.forEach { (entry, type) ->
                val detail = stringResource(R.string.mg_value, Fmt.mg(entry.strengthMg)) + " · " + Fmt.mmss(now - entry.timestamp)
                ChooserRow(type?.colorIndex ?: 0, type?.name ?: "?", detail) {
                    finishAfter { QuickLog.remove(applicationContext, entry, type, settings) }
                }
            }
        }
    }

    @Composable
    private fun Chooser(title: String, rows: @Composable ColumnScope.() -> Unit) {
        AlertDialog(
            onDismissRequest = { finish() },
            title = { Text(title) },
            text = { Column(content = rows) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    @Composable
    private fun ChooserRow(colorIndex: Int, name: String, detail: String, onClick: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(pouchColor(colorIndex))
            Spacer(Modifier.width(12.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }

    companion object {
        const val ACTION_LOG = "app.pouchwise.action.QUICK_LOG"
        const val ACTION_REMOVE = "app.pouchwise.action.QUICK_REMOVE"
    }
}

/** Logs or takes out a pouch from outside the app and refreshes the widgets; a failure says so. */
object QuickLog {
    /** A second tap on "+" within this time is taken as a double tap, not as a second pouch. */
    private const val DOUBLE_TAP_MS = 500L
    /** On the elapsed-realtime clock, which a change of the wall clock cannot move. */
    private var lastLogAt = -DOUBLE_TAP_MS

    suspend fun log(context: Context, type: PouchType) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!acceptTap()) return
        guarded(app) { insert(app, type, now) }
    }

    /** For a broadcast from the cover-screen widget: done before the receiver lets the process go. */
    suspend fun logById(context: Context, typeId: Long) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        if (!acceptTap()) return
        guarded(app) {
            val type = AppDatabase.get(app).dao().typesByIds(listOf(typeId)).firstOrNull()
            // A button for a pouch deleted since the widget was drawn: drawing it again drops the button.
            if (type == null) WidgetUpdater.updateAll(app) else insert(app, type, now)
        }
    }

    private fun acceptTap(): Boolean = synchronized(this) {
        val tap = SystemClock.elapsedRealtime()
        if (tap - lastLogAt < DOUBLE_TAP_MS) return false
        lastLogAt = tap
        true
    }

    private suspend fun insert(app: Context, type: PouchType, atMs: Long) {
        val settings = SettingsRepository(app).settings.first()
        val dao = AppDatabase.get(app).dao()
        dao.insertEntry(PouchLog.entry(dao, type, atMs, settings))
        // A slow first log (a cold start) must not let the second tap of a double tap through.
        synchronized(this) { lastLogAt = SystemClock.elapsedRealtime() }
        toast(app, app.getString(R.string.widget_logged, type.name))
        runCatching { WidgetUpdater.updateAll(app) }
    }

    suspend fun remove(context: Context, entry: LogEntry, type: PouchType?, settings: Settings) {
        val app = context.applicationContext
        guarded(app) {
            PouchTiming.finish(entry, settings, System.currentTimeMillis()).endTimestamp?.let { AppDatabase.get(app).dao().setOut(entry.id, it) }
            toast(app, app.getString(R.string.widget_removed, type?.name ?: "?"))
            runCatching { WidgetUpdater.updateAll(app) }
        }
    }

    /** The widget still showed a pouch that has meanwhile come out by itself. */
    suspend fun nothingIn(context: Context) {
        val app = context.applicationContext
        guarded(app) {
            toast(app, app.getString(R.string.widget_nothing_in))
            runCatching { WidgetUpdater.updateAll(app) }
        }
    }

    private suspend fun guarded(app: Context, work: suspend () -> Unit) {
        try {
            work()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            toast(app, app.getString(R.string.widget_failed))
        }
    }

    private suspend fun toast(app: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
    }
}
