package app.pouchwise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pouchwise.R
import app.pouchwise.data.Felt
import app.pouchwise.data.PouchType
import app.pouchwise.util.Fmt
import app.pouchwise.data.PouchEdit
import app.pouchwise.model.Brands
import app.pouchwise.model.DayKeys
import app.pouchwise.model.PouchTiming
import app.pouchwise.ui.theme.PouchPalette
import java.time.Instant
import java.time.ZoneId

/** Marker for "a brand that is not in the catalogue" in [BrandPicker]. */
const val OTHER_BRAND = ""

/** A typed number, or null for anything else, including "NaN" and "Infinity", which Kotlin would otherwise accept. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private fun String.removePrefixIgnoringCase(prefix: String): String =
    if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

/**
 * Brand chips from the catalogue plus "Other" with a free-text name, and a one-line note on how fast
 * the chosen brand releases nicotine. [selected] is a catalogue name or [OTHER_BRAND].
 */
@Composable
fun BrandPicker(
    selected: String,
    custom: String,
    onSelect: (String) -> Unit,
    onCustomChange: (String) -> Unit,
) {
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Brands.known.forEach { b ->
                FilterChip(selected = selected == b.name, onClick = { onSelect(b.name) }, label = { Text(b.name) })
            }
            FilterChip(
                selected = selected == OTHER_BRAND,
                onClick = { onSelect(OTHER_BRAND) },
                label = { Text(stringResource(R.string.brand_other)) },
            )
        }
        if (selected == OTHER_BRAND) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = custom,
                onValueChange = onCustomChange,
                label = { Text(stringResource(R.string.brand_custom)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(6.dp))
        val info = if (selected != OTHER_BRAND) {
            stringResource(R.string.brand_info_known, selected, Brands.percentBy20Min(Brands.kFor(selected)))
        } else {
            stringResource(R.string.brand_info_other, Brands.percentBy20Min(Brands.averageK))
        }
        Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The brand name a [BrandPicker] state resolves to, or blank when nothing usable is chosen. */
fun resolveBrand(selected: String, custom: String): String =
    if (selected != OTHER_BRAND) selected else custom.trim()

/** Above this labelled strength the dialog shows the tolerance warning. */
const val STRONG_POUCH_MG = 12.0

@Composable
fun PouchTypeDialog(
    initial: PouchType?,
    onDismiss: () -> Unit,
    currency: String,
    onSave: (PouchEdit) -> Unit,
) {
    val initialKnown = initial?.brand?.takeIf { Brands.isKnown(it) }?.let { Brands.find(it)!!.name }
    var brandSel by rememberSaveable { mutableStateOf(initialKnown ?: OTHER_BRAND) }
    var custom by rememberSaveable {
        mutableStateOf(if (initial == null || initialKnown != null) "" else initial.brand.ifBlank { initial.name })
    }
    var variant by rememberSaveable {
        mutableStateOf(
            when {
                initial == null -> ""
                initialKnown != null -> initial.name.removePrefixIgnoringCase(initial.brand.trim().ifEmpty { initialKnown }).trim()
                initial.brand.isNotBlank() -> initial.name.removePrefixIgnoringCase(initial.brand.trim()).trim()
                else -> ""
            },
        )
    }
    var strength by rememberSaveable { mutableStateOf(initial?.let { fmtInput(it.strengthMg) } ?: "") }
    var color by rememberSaveable { mutableIntStateOf(initial?.colorIndex ?: 0) }
    var fast by rememberSaveable { mutableStateOf(initial?.fastRelease ?: false) }
    var price by rememberSaveable { mutableStateOf(initial?.pricePerCan?.let { fmtInput(it) } ?: "") }
    var perCan by rememberSaveable { mutableStateOf((initial?.pouchesPerCan ?: 20).toString()) }
    val mg = parseDecimal(strength)
    val brand = resolveBrand(brandSel, custom)
    val priceValue = parseDecimal(price)
    val perCanValue = perCan.trim().toIntOrNull()
    val priceOk = price.isBlank() || (priceValue != null && priceValue >= 0 && priceValue < 1e7)
    val perCanOk = perCanValue != null && perCanValue in 1..1000
    val valid = brand.isNotBlank() && mg != null && mg > 0 && mg < 200 && priceOk && perCanOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.new_pouch else R.string.edit_pouch)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.brand), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                BrandPicker(
                    selected = brandSel,
                    custom = custom,
                    onSelect = { brandSel = it },
                    onCustomChange = { custom = it },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = variant,
                    onValueChange = { variant = it },
                    label = { Text(stringResource(R.string.variant)) },
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
                if (mg != null && mg > STRONG_POUCH_MG) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.strong_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = fast, onCheckedChange = { fast = it })
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.fast_release), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.fast_release_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text(stringResource(R.string.price_per_can)) },
                        suffix = { Text(currency) },
                        singleLine = true,
                        isError = !priceOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = perCan,
                        onValueChange = { perCan = it },
                        label = { Text(stringResource(R.string.pouches_per_can)) },
                        singleLine = true,
                        isError = !perCanOk,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (priceValue != null && perCanValue != null && perCanValue > 0) {
                    Text(
                        stringResource(R.string.price_per_pouch, Fmt.money(priceValue / perCanValue, currency)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
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
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(PouchEdit(brand, variant.trim(), mg ?: 0.0, color, fast, priceValue.takeIf { price.isNotBlank() }, perCanValue ?: 20))
                },
            ) {
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

/**
 * Corrects a logged pouch: when it went in and, once it is out, how many minutes it stayed. Moving the start
 * keeps the minutes. [durationMs] is null while the pouch is still in; [onSave] gets the same shape back.
 */
@Composable
fun EntryDialog(
    title: String,
    color: Color,
    startMs: Long,
    durationMs: Long?,
    felt: Felt?,
    onDismiss: () -> Unit,
    onSave: (startMs: Long, durationMs: Long?, felt: Felt?) -> Unit,
) {
    var start by rememberSaveable { mutableLongStateOf(startMs) }
    var feltLevel by rememberSaveable { mutableStateOf(felt?.level) }
    val initialMinutes = durationMs?.let { (it / 60_000L).toInt() }
    var minutesText by rememberSaveable { mutableStateOf(initialMinutes?.toString().orEmpty()) }
    var pickTime by rememberSaveable { mutableStateOf(false) }
    val minutes = minutesText.toIntOrNull()?.takeIf { it in 1..PouchTiming.MAX_POUCH_MINUTES }
    // Untouched minutes keep the exact time, seconds included.
    val newDurationMs = when {
        durationMs == null -> null
        minutes == initialMinutes -> durationMs
        else -> minutes?.let { it * 60_000L }
    }
    val endsAfterNow = newDurationMs != null && start + newDurationMs > System.currentTimeMillis()
    val valid = (durationMs == null || newDurationMs != null) && !endsAfterNow

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_entry)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(color, size = 10)
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.put_in_at), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    FilledTonalButton(onClick = { pickTime = true }) { Text(Fmt.time(start), style = MaterialTheme.typography.titleMedium) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.in_the_mouth), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (durationMs == null) {
                        Text(stringResource(R.string.still_in), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val shown = (minutesText.toIntOrNull() ?: initialMinutes ?: 1).coerceIn(1, PouchTiming.MAX_POUCH_MINUTES)
                        FilledTonalIconButton(onClick = { minutesText = (shown - 1).coerceAtLeast(1).toString() }) {
                            Icon(Icons.Filled.Remove, contentDescription = null)
                        }
                        // Not OutlinedTextField: its 16 dp side padding hides two digits at this width.
                        val frame = if (minutes == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        BasicTextField(
                            value = minutesText,
                            onValueChange = { v -> minutesText = v.filter { it.isDigit() }.take(2) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = if (minutes == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .width(44.dp)
                                .border(1.dp, frame, RoundedCornerShape(8.dp))
                                .padding(vertical = 8.dp),
                        )
                        Text(stringResource(R.string.unit_min), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        FilledTonalIconButton(onClick = { minutesText = (shown + 1).coerceAtMost(PouchTiming.MAX_POUCH_MINUTES).toString() }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    }
                }
                if (newDurationMs != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (endsAfterNow) stringResource(R.string.ends_after_now) else stringResource(R.string.taken_out_at, Fmt.time(start + newDurationMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (endsAfterNow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.felt_label), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
                FeltChips(Felt.of(feltLevel), { feltLevel = it?.level })
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(start, newDurationMs, Felt.of(feltLevel)) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (pickTime) {
        TimeDialog(
            title = stringResource(R.string.put_in_at),
            initialMs = start,
            onDismiss = { pickTime = false },
            onConfirm = { h, m ->
                start = DayKeys.nearestPastTimeMs(start, h, m, System.currentTimeMillis())
                pickTime = false
            },
        )
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
