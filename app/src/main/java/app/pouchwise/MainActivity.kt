package app.pouchwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pouchwise.model.ThemeMode
import app.pouchwise.ui.PouchwiseApp
import app.pouchwise.ui.theme.PouchwiseTheme
import app.pouchwise.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Also runs after a language switch recreates the activity, so the widgets follow it.
        lifecycleScope.launch { runCatching { WidgetUpdater.updateAll(applicationContext) } }
        setContent {
            val vm: AppViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            PouchwiseTheme(darkTheme = dark) {
                PouchwiseApp(vm)
            }
        }
    }
}
