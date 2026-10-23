package app.pouchwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pouchwise.data.RunningDay
import app.pouchwise.ui.PouchwiseApp
import app.pouchwise.ui.theme.PouchwiseTheme
import app.pouchwise.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            PouchwiseTheme(settings.themeMode) {
                PouchwiseApp(vm)
            }
        }
    }

    /**
     * Every return to the app, including after a language switch recreates it, catches up with a new time
     * zone and brings the widgets up to date.
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            runCatching { RunningDay.checkZone(applicationContext) }
            runCatching { WidgetUpdater.updateAll(applicationContext) }
        }
    }
}
