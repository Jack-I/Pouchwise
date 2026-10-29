package app.pouchwise.ui.screens

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.data.PouchType
import app.pouchwise.model.Brands
import app.pouchwise.ui.components.*
import app.pouchwise.ui.theme.pouchColor
import app.pouchwise.util.Fmt
import app.pouchwise.util.Money

@Composable
fun PouchTypesScreen(vm: AppViewModel, onBack: () -> Unit) {
    val typesOrNull by vm.types.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val types = typesOrNull.orEmpty()
    val active = types.filter { !it.archived }
    val archived = types.filter { it.archived }

    var editing by remember { mutableStateOf<PouchType?>(null) }
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<PouchType?>(null) }

    // The order being dragged, shown until the saved one comes back from the database.
    val listState = rememberLazyListState()
    var order by remember { mutableStateOf<List<Long>?>(null) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    val activeIds = active.map { it.id }
    LaunchedEffect(activeIds) { if (draggedId == null) order = null }
    val shown = order?.let { ids -> ids.mapNotNull { id -> active.firstOrNull { it.id == id } } + active.filter { it.id !in ids } } ?: active
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)

    fun rowInfo(id: Long) = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "active-$id" }
    fun moved(ids: List<Long>, from: Int, to: Int) = ids.toMutableList().apply { add(to, removeAt(from)) }
    fun save(ids: List<Long>) {
        if (ids == activeIds) order = null else vm.reorderTypes(ids)
    }

    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.manage_pouches), onBack) },
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
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (types.isEmpty()) {
                item { Text(stringResource(R.string.no_types_yet), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(shown, key = { "active-${it.id}" }) { t ->
                val dragged = draggedId == t.id
                val ids = shown.map { it.id }
                val index = ids.indexOf(t.id)
                TypeRow(
                    type = t,
                    lifted = dragged,
                    modifier = Modifier
                        .zIndex(if (dragged) 1f else 0f)
                        .then(
                            if (dragged) Modifier.graphicsLayer { translationY = dragTop - (rowInfo(t.id)?.offset ?: 0) }
                            else Modifier.animateItem()
                        )
                        .semantics {
                            customActions = listOfNotNull(
                                if (index > 0) CustomAccessibilityAction(moveUp) { save(moved(ids, index, index - 1)); true } else null,
                                if (index < ids.lastIndex) CustomAccessibilityAction(moveDown) { save(moved(ids, index, index + 1)); true } else null,
                            )
                        },
                    trailing = {
                        IconButton(onClick = { editing = t }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_pouch)) }
                        IconButton(onClick = { removing = t }) { Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(R.string.hide)) }
                        Icon(
                            Icons.Filled.DragHandle,
                            contentDescription = stringResource(R.string.drag_to_reorder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .draggable(
                                    orientation = Orientation.Vertical,
                                    state = rememberDraggableState { dy ->
                                        val current = order ?: return@rememberDraggableState
                                        dragTop += dy
                                        val i = current.indexOf(t.id)
                                        val me = rowInfo(t.id) ?: return@rememberDraggableState
                                        val center = dragTop + me.size / 2f
                                        val next = current.getOrNull(i + 1)?.let { rowInfo(it) }
                                        val prev = current.getOrNull(i - 1)?.let { rowInfo(it) }
                                        val to = when {
                                            next != null && center > next.offset + next.size / 2f -> i + 1
                                            prev != null && center < prev.offset + prev.size / 2f -> i - 1
                                            else -> return@rememberDraggableState
                                        }
                                        // Keeps the list in place; it would otherwise follow a first row that moved.
                                        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                                        order = moved(current, i, to)
                                    },
                                    onDragStarted = {
                                        order = shown.map { it.id }
                                        draggedId = t.id
                                        dragTop = rowInfo(t.id)?.offset?.toFloat() ?: 0f
                                    },
                                    onDragStopped = {
                                        draggedId = null
                                        order?.let { save(it) }
                                    },
                                )
                                .padding(12.dp),
                        )
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
            currency = Money.symbol(settings, context),
            onSave = { vm.addType(it); adding = false },
        )
    }
    editing?.let { t ->
        PouchTypeDialog(
            initial = t,
            onDismiss = { editing = null },
            currency = Money.symbol(settings, context),
            onSave = {
                vm.updateType(t, it)
                editing = null
            },
        )
    }
    removing?.let { t ->
        // A pouch never logged is deleted outright rather than hidden, so the dialog must not promise a way back.
        val logged by produceState(initialValue = true, t.id) { value = vm.typeHasEntries(t.id) }
        val name = "${t.name} ${stringResource(R.string.mg_value, Fmt.mg(t.strengthMg))}"
        ConfirmDialog(
            title = stringResource(if (logged) R.string.hide_type_q else R.string.delete_type_q, name),
            text = stringResource(if (logged) R.string.delete_type_body else R.string.delete_type_body_unused),
            confirmLabel = stringResource(if (logged) R.string.hide else R.string.delete),
            onDismiss = { removing = null },
            onConfirm = { vm.removeType(t); removing = null },
            destructive = !logged,
        )
    }
}

@Composable
private fun TypeRow(
    type: PouchType,
    modifier: Modifier = Modifier,
    lifted: Boolean = false,
    trailing: @Composable RowScope.() -> Unit,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (lifted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (lifted) 8.dp else 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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
