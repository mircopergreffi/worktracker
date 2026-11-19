package com.example.workhours.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workhours.MainViewModel
import com.example.workhours.UiState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")
private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: MainViewModel, onBack: () -> Unit, onDay: (LocalDate) -> Unit) {
    val days by vm.history.collectAsStateWithLifecycle()
    val filter by vm.historyFilter.collectAsStateWithLifecycle()
    val clients by vm.clients.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    val total = days.sumOf { it.seconds }
    val max = maxOf(days.maxOfOrNull { it.seconds } ?: 0L, 1L)

    val client = clients.find { it.id == filter.clientId }
    val project = projects.find { it.id == filter.projectId }
    val clientsById = clients.associateBy { it.id }

    val clientOptions = listOf(Option(-1, "All clients", null)) +
        clients.map { Option(it.id, it.name, it.color) }
    // Without a client, every project is listed with its client name next to it.
    val projectOptions = listOf(Option(-1, "All projects", null)) + projects
        .filter { client == null || it.clientId == client.id }
        .map {
            if (client == null) Option(it.id, "${it.name} · ${clientsById[it.clientId]?.name ?: "?"}", clientsById[it.clientId]?.color)
            else Option(it.id, it.name, null)
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = { picking = true }) { Text("Pick a day") } }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 24.dp).fillMaxSize()) {
            Spacer(Modifier.height(8.dp))
            PeriodSelector(value = filter.period, onChange = vm::setPeriod, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Selector(
                    label = "All clients",
                    selected = client?.let { Option(it.id, it.name, it.color) },
                    options = clientOptions,
                    enabled = true,
                    addLabel = null,
                    onSelect = { id -> vm.setFilterClient(if (id >= 0) id else null) },
                    modifier = Modifier.weight(1f)
                )
                Selector(
                    label = "All projects",
                    selected = project?.let { Option(it.id, it.name, null) },
                    options = projectOptions,
                    enabled = true,
                    addLabel = null,
                    onSelect = { id -> vm.setFilterProject(if (id >= 0) id else null) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hide empty days", Modifier.weight(1f))
                Switch(checked = filter.hideEmpty, onCheckedChange = vm::setHideEmpty)
            }

            Spacer(Modifier.height(8.dp))
            Text(filter.period.label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
            Text(formatHm(total), fontSize = 40.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))

            if (days.isEmpty()) {
                Text("No days to show", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(days, key = { it.day.toString() }) { d ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onDay(d.day) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(d.day.format(shortDate), Modifier.width(100.dp), fontSize = 14.sp)
                        Box(
                            Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                Modifier.fillMaxHeight()
                                    .fillMaxWidth((d.seconds.toFloat() / max).coerceIn(0f, 1f))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        Text(
                            if (d.seconds > 0) formatHm(d.seconds) else "–",
                            Modifier.width(76.dp),
                            textAlign = TextAlign.End,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    if (picking) {
        val pickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        pickerState.selectedDateMillis?.let {
                            onDay(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                        }
                        picking = false
                    }
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(vm: MainViewModel, day: LocalDate, onBack: () -> Unit) {
    val state by remember(day) { vm.dayState(day) }.collectAsStateWithLifecycle(initialValue = UiState())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(day.format(longDate)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 24.dp).fillMaxSize()) {
            Spacer(Modifier.height(16.dp))
            Text("Worked", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
            Text(formatHms(state.workedSeconds), fontSize = 48.sp, fontWeight = FontWeight.Light, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(24.dp))
            if (state.entries.isEmpty()) {
                Text("No entries for this day", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.entries, key = { it.id }) { EntryRow(it) }
            }
        }
    }
}
