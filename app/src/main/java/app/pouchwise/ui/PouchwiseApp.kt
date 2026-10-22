package app.pouchwise.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.ui.components.AppLogo
import app.pouchwise.ui.screens.BodyScreen
import app.pouchwise.ui.screens.HistoryScreen
import app.pouchwise.ui.screens.LevelsScreen
import app.pouchwise.ui.screens.OnboardingScreen
import app.pouchwise.ui.screens.PlanScreen
import app.pouchwise.ui.screens.PouchTypesScreen
import app.pouchwise.ui.screens.SettingsScreen
import app.pouchwise.ui.screens.TrackScreen

private enum class Tab(val labelRes: Int, val icon: ImageVector) {
    TRACK(R.string.tab_track, Icons.Filled.AddCircle),
    BODY(R.string.tab_body, Icons.Filled.Timeline),
    HISTORY(R.string.tab_history, Icons.Filled.CalendarMonth),
    PLAN(R.string.tab_plan, Icons.Filled.Flag),
}

private const val SCREEN_MAIN = 0
private const val SCREEN_SETTINGS = 1
private const val SCREEN_POUCHES = 2
private const val SCREEN_LEVELS = 3

@Composable
fun PouchwiseApp(vm: AppViewModel) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var screen by rememberSaveable { mutableIntStateOf(SCREEN_MAIN) }
    var pouchesOpenedFromSettings by rememberSaveable { mutableIntStateOf(0) }

    val settings by vm.settings.collectAsStateWithLifecycle()
    val settingsLoaded by vm.settingsLoaded.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val loadedTypes = types
    if (!settingsLoaded || loadedTypes == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    if (!settings.onboarded && loadedTypes.isEmpty()) {
        OnboardingScreen(vm)
        return
    }

    BackHandler(enabled = screen != SCREEN_MAIN) {
        screen = if (screen == SCREEN_POUCHES && pouchesOpenedFromSettings == 1) SCREEN_SETTINGS else SCREEN_MAIN
    }

    when (screen) {
        SCREEN_SETTINGS -> SettingsScreen(
            vm = vm,
            onBack = { screen = SCREEN_MAIN },
            onManagePouches = { pouchesOpenedFromSettings = 1; screen = SCREEN_POUCHES },
        )
        SCREEN_POUCHES -> PouchTypesScreen(
            vm = vm,
            onBack = { screen = if (pouchesOpenedFromSettings == 1) SCREEN_SETTINGS else SCREEN_MAIN },
        )
        SCREEN_LEVELS -> LevelsScreen(vm = vm, onBack = { screen = SCREEN_MAIN })
        else -> MainScaffold(
            vm = vm,
            tabIndex = tabIndex,
            onTab = { tabIndex = it },
            onOpenSettings = { screen = SCREEN_SETTINGS },
            onEditPouches = { pouchesOpenedFromSettings = 0; screen = SCREEN_POUCHES },
            onOpenLevels = { screen = SCREEN_LEVELS },
        )
    }
}

@Composable
private fun MainScaffold(
    vm: AppViewModel,
    tabIndex: Int,
    onTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onEditPouches: () -> Unit,
    onOpenLevels: () -> Unit,
) {
    val tabs = Tab.entries
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppLogo(size = 30.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = i == tabIndex,
                        onClick = { onTab(i) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (tabs[tabIndex]) {
                Tab.TRACK -> TrackScreen(vm = vm, onEditPouches = onEditPouches)
                Tab.BODY -> BodyScreen(vm = vm, onOpenLevels = onOpenLevels)
                Tab.HISTORY -> HistoryScreen(vm = vm, onEditPouches = onEditPouches)
                Tab.PLAN -> PlanScreen(vm = vm)
            }
        }
    }
}
