package app.pouchwise.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.data.Backup
import app.pouchwise.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_FILE_BYTES = 20 * 1024 * 1024

/**
 * Picking a JSON export, checking it and asking before it replaces what is on the phone.
 * Returns the function that opens the file picker.
 */
@Composable
fun rememberImportFlow(vm: AppViewModel): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val types by vm.types.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var pending by remember { mutableStateOf<Backup.Contents?>(null) }
    var problem by remember { mutableStateOf<Int?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { Backup.parse(readText(context, uri)) } }
                    .onSuccess { pending = it }
                    .onFailure { problem = if (it is Backup.NewerFormat) R.string.import_newer else R.string.import_not_export }
            }
        }
    }

    pending?.let { contents ->
        val phoneTypes = types.orEmpty()
        val phoneEmpty = phoneTypes.isEmpty() && entries.isEmpty()
        val fileCounts = counts(contents.types.count { !it.archived }, contents.entries.size)
        val exported = contents.exportedAt?.let { Fmt.isoDateTime(it) }
        val fileLine = if (exported != null) stringResource(R.string.import_in_file_dated, fileCounts, exported)
                       else stringResource(R.string.import_in_file, fileCounts)
        val text = if (phoneEmpty) {
            fileLine
        } else {
            stringResource(R.string.import_on_phone, counts(phoneTypes.count { !it.archived }, entries.size)) + "\n" +
                fileLine + "\n\n" + stringResource(R.string.import_warning)
        }
        ConfirmDialog(
            title = stringResource(if (phoneEmpty) R.string.import_title_empty else R.string.import_title),
            text = text,
            confirmLabel = stringResource(if (phoneEmpty) R.string.import_restore else R.string.import_replace),
            onDismiss = { pending = null },
            onConfirm = {
                vm.importBackup(contents)
                pending = null
            },
            destructive = !phoneEmpty,
        )
    }

    problem?.let { res ->
        AlertDialog(
            onDismissRequest = { problem = null },
            title = { Text(stringResource(R.string.import_cant)) },
            text = { Text(stringResource(res)) },
            confirmButton = { TextButton(onClick = { problem = null }) { Text(stringResource(R.string.ok)) } },
        )
    }

    // Some file managers and messengers label JSON as plain text or as unknown binary.
    return { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
}

@Composable
private fun counts(pouches: Int, entries: Int): String =
    pluralStringResource(R.plurals.count_pouches, pouches, pouches) + ", " +
        pluralStringResource(R.plurals.count_entries, entries, entries)

private fun readText(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: throw Backup.NotAnExport("cannot open")
    return input.use { stream ->
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_FILE_BYTES) throw Backup.NotAnExport("too large")
        }
        out.toString(Charsets.UTF_8.name())
    }
}
