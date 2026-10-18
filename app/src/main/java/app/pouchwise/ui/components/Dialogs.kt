package app.pouchwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pouchwise.R
import app.pouchwise.data.PouchType
import app.pouchwise.ui.theme.PouchPalette
import java.time.Instant
import java.time.ZoneId

fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

@Composable
fun PouchTypeDialog(
    initial: PouchType?,
    onDismiss: () -> Unit,
    onSave: (name: String, strengthMg: Double, colorIndex: Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var strength by rememberSaveable { mutableStateOf(initial?.let { fmtInput(it.strengthMg) } ?: "") }
    var color by rememberSaveable { mutableIntStateOf(initial?.colorIndex ?: 0) }
    val mg = parseDecimal(strength)
    val valid = name.isNotBlank() && mg != null && mg > 0 && mg < 200

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_pouch else R.string.edit_pouch)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.pouch_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = strength,
                    onValueChange = { strength = it },
                    label = { Text(stringResource(R.string.pouch_strength)) },
                    singleLine = true,
                    isError = strength.isNotBlank() && (mg == null || mg <= 0),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.pouch_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PouchPalette.forEachIndexed { i, c ->
                        ColorChoice(color = c, selected = i == color, onClick = { color = i })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(name.trim(), mg ?: 0.0, color) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ColorChoice(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
    }
}

private fun fmtInput(v: Double): String =
    if (v == Math.rint(v)) v.toLong().toString() else v.toString()

/** Hour/minute picker in a dialog that fits narrow screens. */
@Composable
fun TimeDialog(
    title: String,
    initialMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val zdt = remember(initialMs) { Instant.ofEpochMilli(initialMs).atZone(ZoneId.systemDefault()) }
    val state = rememberTimePickerState(initialHour = zdt.hour, initialMinute = zdt.minute, is24Hour = true)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp),
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text(stringResource(R.string.ok)) }
                }
            }
        }
    }
}

@Composable
fun NumberDialog(
    title: String,
    initial: String,
    decimal: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    suffix: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val value = parseDecimal(text)
    val valid = value != null && value >= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                suffix = if (suffix != null) ({ Text(suffix) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(value ?: 0.0) }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = if (text != null) ({ Text(text) }) else null,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                         else ButtonDefaults.textButtonColors(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun DateDialog(
    initialEpochDay: Long,
    onDismiss: () -> Unit,
    onConfirm: (epochDay: Long) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialEpochDay * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = state.selectedDateMillis
                if (ms != null) onConfirm(Math.floorDiv(ms, 86_400_000L))
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
