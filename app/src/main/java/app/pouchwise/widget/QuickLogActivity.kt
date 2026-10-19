package app.pouchwise.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import app.pouchwise.MainActivity
import app.pouchwise.R
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.data.SettingsRepository
import app.pouchwise.model.PouchTiming
import app.pouchwise.model.Settings
import app.pouchwise.model.ThemeMode
import app.pouchwise.ui.components.ColorDot
import app.pouchwise.ui.theme.PouchwiseTheme
import app.pouchwise.ui.theme.pouchColor
import app.pouchwise.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
            // Follow the app's own theme setting, like the app does.
            val settings by settingsFlow.collectAsState(initial = null)
            val mode = settings?.themeMode ?: return@setContent
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PouchwiseTheme(darkTheme = dark) {
                if (removing) RemoveChooser() else LogChooser()
            }
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
                1 -> {
                    QuickLog.log(applicationContext, list.first())
                    finish()
                }
                else -> types = list
            }
        }
        val list = types ?: return
        Chooser(stringResource(R.string.quick_log_title)) {
            list.forEach { type ->
                ChooserRow(type.colorIndex, type.name, stringResource(R.string.mg_value, Fmt.mg(type.strengthMg))) {
                    QuickLog.log(applicationContext, type)
                    finish()
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
                0 -> {
                    QuickLog.nothingIn(applicationContext)
                    finish()
                }
                1 -> {
                    QuickLog.remove(applicationContext, inNow[0].first, inNow[0].second, settings)
                    finish()
                }
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
                    QuickLog.remove(applicationContext, entry, type, settings)
                    finish()
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

/** Logs or takes out a pouch from outside the app and refreshes the widgets; outlives the activity that asked. */
object QuickLog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** A second tap on "+" within this time is taken as a double tap, not as a second pouch. */
    private const val DOUBLE_TAP_MS = 500L
    @Volatile private var lastLogAt = 0L

    fun log(context: Context, type: PouchType) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (now - lastLogAt < DOUBLE_TAP_MS) return
            lastLogAt = now
        }
        scope.launch {
            val settings = SettingsRepository(app).settings.first()
            AppDatabase.get(app).dao().insertEntry(PouchTiming.newEntry(type.id, type.strengthMg, type.releaseK, now, settings))
            toast(app, app.getString(R.string.widget_logged, type.name))
            runCatching { WidgetUpdater.updateAll(app) }
        }
    }

    fun remove(context: Context, entry: LogEntry, type: PouchType?, settings: Settings) {
        val app = context.applicationContext
        scope.launch {
            AppDatabase.get(app).dao().updateEntry(PouchTiming.finish(entry, settings, System.currentTimeMillis()))
            toast(app, app.getString(R.string.widget_removed, type?.name ?: "?"))
            runCatching { WidgetUpdater.updateAll(app) }
        }
    }

    /** The widget still showed a pouch that has meanwhile come out by itself. */
    fun nothingIn(context: Context) {
        val app = context.applicationContext
        scope.launch {
            toast(app, app.getString(R.string.widget_nothing_in))
            runCatching { WidgetUpdater.updateAll(app) }
        }
    }

    private suspend fun toast(app: Context, text: String) = withContext(Dispatchers.Main) {
        Toast.makeText(app, text, Toast.LENGTH_SHORT).show()
    }
}
