package com.example.workhours.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workhours.MainViewModel
import com.example.workhours.data.EntryView
import com.example.workhours.data.partnerOf
import java.time.LocalDate
import java.time.LocalTime

/**
 * Edit / delete dialog for one entry. [entries] is that day's sorted list, used to find the
 * IN/OUT partner: client and project changes apply to both, and deleting removes both.
 */
@Composable
fun EntryEditor(entry: EntryView, entries: List<EntryView>, vm: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clients by vm.clients.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val partner = remember(entries, entry.id) { partnerOf(entries, entry.id) }

    val t = remember(entry.id) { LocalTime.parse(entry.time) }
    var h by remember(entry.id) { mutableStateOf("%02d".format(t.hour)) }
    var m by remember(entry.id) { mutableStateOf("%02d".format(t.minute)) }
    var s by remember(entry.id) { mutableStateOf("%02d".format(t.second)) }
    var clientId by remember(entry.id) { mutableStateOf(entry.clientId) }
    var projectId by remember(entry.id) { mutableStateOf(entry.projectId) }
    var confirmDelete by remember { mutableStateOf(false) }

    val client = clients.find { it.id == clientId }
    val clientProjects = projects.filter { it.clientId == client?.id }
    val project = clientProjects.find { it.id == projectId }

    val hh = h.toIntOrNull()
    val mm = m.toIntOrNull()
    val ss = s.toIntOrNull()
    val time: String? =
        if (hh != null && mm != null && ss != null && hh in 0..23 && mm in 0..59 && ss in 0..59)
            "%02d:%02d:%02d".format(hh, mm, ss)
        else null
    // Old entries may have neither; otherwise both must be set.
    val selectionOk = (client == null && project == null) || (client != null && project != null)

    // The last entry of today is deleted on its own (see MainViewModel.deleteEntry).
    val isLastToday = entry.day == LocalDate.now().toString() && entries.lastOrNull()?.id == entry.id
    val infoText = buildString {
        if (partner != null) append("Client and project also change on the paired ${partner.inOut} (${partner.time}). ")
        when {
            isLastToday && entry.inOut == "OUT" -> append("Deleting this last OUT keeps the IN and restarts the live timer.")
            isLastToday -> append("The timer is running. Deleting this IN stops it.")
            partner != null -> append("Deleting removes both.")
            entry.inOut == "OUT" -> append("This OUT has no matching IN.")
        }
    }

    if (!confirmDelete) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit ${entry.inOut} entry") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TimeField(h, "h", { h = it }, Modifier.weight(1f))
                        Text(":")
                        TimeField(m, "m", { m = it }, Modifier.weight(1f))
                        Text(":")
                        TimeField(s, "s", { s = it }, Modifier.weight(1f))
                    }
                    Selector(
                        label = "Client",
                        selected = client?.let { Option(it.id, it.name, it.color) },
                        options = clients.map { Option(it.id, it.name, it.color) },
                        enabled = true,
                        addLabel = null,
                        onSelect = { id -> if (id != clientId) { clientId = id; projectId = null } }
                    )
                    Selector(
                        label = "Project",
                        selected = project?.let { Option(it.id, it.name, null) },
                        options = clientProjects.map { Option(it.id, it.name, null) },
                        enabled = client != null,
                        addLabel = null,
                        onSelect = { id -> projectId = id }
                    )
                    Text(
                        infoText,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = time != null && selectionOk,
                    onClick = {
                        time?.let { tm ->
                            vm.editEntry(entry.id, tm, client?.id, project?.id) { err ->
                                if (err == null) onDismiss()
                                else Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete entry?") },
            text = {
                Text(
                    when {
                        isLastToday && entry.inOut == "OUT" ->
                            "The OUT will be deleted and the live timer will start again. The paired IN stays."
                        isLastToday -> "The running timer will stop."
                        partner != null -> "The paired ${partner.inOut} at ${partner.time} will be deleted too."
                        else -> "This entry will be deleted."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.deleteEntry(entry.id) { onDismiss() } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TimeField(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}
