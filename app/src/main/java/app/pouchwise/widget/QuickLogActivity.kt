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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pouchwise.MainActivity
import app.pouchwise.R
import app.pouchwise.data.AppDatabase
import app.pouchwise.data.LogEntry
import app.pouchwise.data.PouchType
import app.pouchwise.ui.components.ColorDot
import app.pouchwise.ui.theme.PouchwiseTheme
import app.pouchwise.ui.theme.pouchColor
import app.pouchwise.util.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Transparent activity behind the widget's "+": shows the pouch chooser over the home screen.
 * With a single pouch it logs straight away; with none it opens the app.
 */
class QuickLogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PouchwiseTheme(darkTheme = isSystemInDarkTheme()) {
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
                val list = types
                if (list != null) {
                    AlertDialog(
                        onDismissRequest = { finish() },
                        title = { Text(stringResource(R.string.quick_log_title)) },
                        text = {
                            Column {
                                list.forEach { type ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                QuickLog.log(applicationContext, type)
                                                finish()
                                            }
                                            .padding(horizontal = 8.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ColorDot(pouchColor(type.colorIndex))
                                        Spacer(Modifier.width(12.dp))
                                        Text(type.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.mg_value, Fmt.mg(type.strengthMg)),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.cancel)) } },
                    )
                }
            }
        }
    }
}

/** Logs a pouch from outside the app and refreshes the widgets; outlives the activity that asked. */
object QuickLog {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun log(context: Context, type: PouchType) {
        val app = context.applicationContext
        scope.launch {
            AppDatabase.get(app).dao().insertEntry(
                LogEntry(
                    pouchTypeId = type.id,
                    timestamp = System.currentTimeMillis(),
                    strengthMg = type.strengthMg,
                    releaseK = type.releaseK,
                ),
            )
            withContext(Dispatchers.Main) {
                Toast.makeText(app, app.getString(R.string.widget_logged, type.name), Toast.LENGTH_SHORT).show()
            }
            runCatching { WidgetUpdater.updateAll(app) }
        }
    }
}
