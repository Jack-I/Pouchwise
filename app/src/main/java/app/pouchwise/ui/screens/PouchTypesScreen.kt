package app.pouchwise.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.data.PouchType
import app.pouchwise.model.Brands
import app.pouchwise.ui.components.*
import app.pouchwise.ui.theme.pouchColor
import app.pouchwise.util.Fmt

@Composable
fun PouchTypesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val typesOrNull by vm.types.collectAsStateWithLifecycle()
    val types = typesOrNull.orEmpty()
    val active = types.filter { !it.archived }
    val archived = types.filter { it.archived }

    var editing by remember { mutableStateOf<PouchType?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<PouchType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_pouches), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_pouch_type)) },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (types.isEmpty()) {
                item { Text(stringResource(R.string.no_types_yet), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(active, key = { "active-${it.id}" }) { t ->
                TypeRow(
                    type = t,
                    trailing = {
                        IconButton(onClick = { editing = t }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_pouch)) }
                        IconButton(onClick = { removing = t }) { Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(R.string.delete)) }
                    },
                )
            }
            if (archived.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.archived), modifier = Modifier.padding(top = 12.dp)) }
                items(archived, key = { "archived-${it.id}" }) { t ->
                    TypeRow(
                        type = t,
                        trailing = {
                            TextButton(onClick = { vm.restoreType(t) }) { Text(stringResource(R.string.restore)) }
                        },
                    )
                }
            }
        }
    }

    if (adding) {
        PouchTypeDialog(
            initial = null,
            onDismiss = { adding = false },
            onSave = { brand, variant, mg, color -> vm.addType(brand, variant, mg, color); adding = false },
        )
    }
    editing?.let { t ->
        PouchTypeDialog(
            initial = t,
            onDismiss = { editing = null },
            onSave = { brand, variant, mg, color ->
                vm.updateType(t, brand, variant, mg, color)
                editing = null
            },
        )
    }
    removing?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.delete_type_q, "${t.name} ${Fmt.mg(t.strengthMg)} ${stringResource(R.string.unit_mg)}"),
            text = stringResource(R.string.delete_type_body),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { removing = null },
            onConfirm = { vm.removeType(t); removing = null },
            destructive = true,
        )
    }
}

@Composable
private fun TypeRow(type: PouchType, trailing: @Composable RowScope.() -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(pouchColor(type.colorIndex))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(type.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    stringResource(R.string.mg_value, Fmt.mg(type.strengthMg)) +
                        " · " + stringResource(R.string.brand_speed_short, Brands.percentBy20Min(type.releaseK)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing()
        }
    }
}
