package com.example.workhours.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workhours.MainViewModel
import com.example.workhours.data.DAY_SECONDS
import com.example.workhours.data.EntryView
import com.example.workhours.data.formatSeconds
import com.example.workhours.data.parseSeconds
import java.time.LocalDate

@Composable
fun AppRoot(vm: MainViewModel, isDark: Boolean) {
    var screen by rememberSaveable { mutableStateOf("main") }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(screen != "main") {
        screen = when (screen) {
            "day" -> "history"
            "lists" -> "settings"
            else -> "main"
        }
    }

    when (screen) {
        "settings" -> SettingsScreen(vm, onBack = { screen = "main" }, onManage = { screen = "lists" })
        "lists" -> ManageScreen(vm) { screen = "settings" }
        "history" -> HistoryScreen(vm, onBack = { screen = "main" }) {
            selectedDay = it.toString(); screen = "day"
        }
        "day" -> DayScreen(vm, LocalDate.parse(selectedDay!!)) { screen = "history" }
        else -> MainScreen(vm, isDark, onSettings = { screen = "settings" }, onHistory = { screen = "history" })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, isDark: Boolean, onSettings: () -> Unit, onHistory: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val clients by vm.clients.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val sel by vm.selection.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var addClient by remember { mutableStateOf(false) }
    var addProject by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EntryView?>(null) }

    // While clocked in, the selectors show (and lock to) the active entry's client / project.
    val active = state.entries.lastOrNull()
    val clientId = if (state.isIn) active?.clientId else sel.clientId
    val projectId = if (state.isIn) active?.projectId else sel.projectId
    val client = clients.find { it.id == clientId }
    val clientProjects = projects.filter { it.clientId == client?.id }
    val project = clientProjects.find { it.id == projectId }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) vm.export(uri) { ok ->
            Toast.makeText(context, if (ok) "Exported" else "Export failed", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Work Hours") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (isDark) "Light theme" else "Dark theme") },
                            onClick = { menu = false; vm.setDark(!isDark) }
                        )
                        DropdownMenuItem(
                            text = { Text("History") },
                            onClick = { menu = false; onHistory() }
                        )
                        DropdownMenuItem(
                            text = { Text("Export database (txt)") },
                            onClick = { menu = false; exportLauncher.launch("workhours_export.txt") }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { menu = false; onSettings() }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 24.dp).fillMaxSize()
        ) {
            Spacer(Modifier.height(24.dp))
            Text(if (state.carriedOver) "Worked (session since yesterday)" else "Worked today", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
            Text(
                formatHms(state.workedSeconds),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Selector(
                    label = "Client",
                    selected = client?.let { Option(it.id, it.name, it.color) },
                    options = clients.map { Option(it.id, it.name, it.color) },
                    enabled = !state.isIn,
                    addLabel = "+ New client",
                    onSelect = vm::selectClient,
                    onAdd = { addClient = true },
                    modifier = Modifier.weight(1f)
                )
                Selector(
                    label = "Project",
                    selected = project?.let { Option(it.id, it.name, null) },
                    options = clientProjects.map { Option(it.id, it.name, null) },
                    enabled = !state.isIn && client != null,
                    addLabel = "+ New project",
                    onSelect = vm::selectProject,
                    onAdd = { addProject = true },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { if (client != null && project != null) vm.clockIn(client.id, project.id) },
                    enabled = !state.isIn && client != null && project != null,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("IN", fontSize = 18.sp, fontWeight = FontWeight.Medium) }
                Button(
                    onClick = { vm.clockOut() },
                    enabled = state.isIn,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) { Text("OUT", fontSize = 18.sp, fontWeight = FontWeight.Medium) }
            }
            Spacer(Modifier.height(32.dp))
            Text(if (state.carriedOver) "Yesterday" else "Today", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            if (state.entries.isEmpty()) {
                Text("No entries yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.entries, key = { it.id }) { e -> EntryRow(e, onClick = { editing = e }) }
            }
        }
    }

    editing?.let { EntryEditor(it, state.entries, vm) { editing = null } }

    if (addClient) {
        NameColorDialog(
            title = "New client",
            label = "Client name",
            withColor = true,
            initialColor = Palette[clients.size % Palette.size],
            onConfirm = { name, color -> vm.addClient(name, color, true); addClient = false },
            onDismiss = { addClient = false }
        )
    }
    if (addProject && client != null) {
        NameColorDialog(
            title = "New project for ${client.name}",
            label = "Project name",
            withColor = false,
            onConfirm = { name, _ -> vm.addProject(name, client.id, true); addProject = false },
            onDismiss = { addProject = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit, onManage: () -> Unit) {
    var confirm by remember { mutableStateOf<String?>(null) }
    val defaultPeriod by vm.defaultPeriod.collectAsStateWithLifecycle()
    val roundIn by vm.roundIn.collectAsStateWithLifecycle()
    val roundOut by vm.roundOut.collectAsStateWithLifecycle()
    val notifyEnabled by vm.notifyEnabled.collectAsStateWithLifecycle()
    val notifyMinutes by vm.notifyMinutes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val appVersion = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) {
            Toast.makeText(context, "Notifications are blocked for this app in the system settings", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Lists", fontWeight = FontWeight.Medium)
            OutlinedButton(onClick = onManage, Modifier.fillMaxWidth()) { Text("Clients & projects") }
            Spacer(Modifier.height(12.dp))
            Text("Notification", fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notify when today's worked time is reached", Modifier.weight(1f))
                Switch(
                    checked = notifyEnabled,
                    onCheckedChange = { on ->
                        vm.setNotifyEnabled(on)
                        if (on && Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
            if (notifyEnabled) {
                NotifyTimeFields(minutes = notifyMinutes, onChange = vm::setNotifyMinutes)
                val exactAllowed = Build.VERSION.SDK_INT < 31 ||
                    context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
                if (!exactAllowed) {
                    Text(
                        "Without exact alarms Android may delay the notification by a few minutes.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                            )
                        },
                        Modifier.fillMaxWidth()
                    ) { Text("Allow exact timing") }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Rounding", fontWeight = FontWeight.Medium)
            Text(
                "Applied to the time saved when you press the button.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
            Text("IN button", fontSize = 14.sp)
            RoundingSelector(value = roundIn, onChange = vm::setRoundIn)
            Text("OUT button", fontSize = 14.sp)
            RoundingSelector(value = roundOut, onChange = vm::setRoundOut)
            Spacer(Modifier.height(12.dp))
            Text("History", fontWeight = FontWeight.Medium)
            Text(
                "Default period",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
            PeriodSelector(value = defaultPeriod, onChange = vm::setDefaultPeriod)
            Spacer(Modifier.height(12.dp))
            Text("Data", fontWeight = FontWeight.Medium)
            OutlinedButton(onClick = { confirm = "today" }, Modifier.fillMaxWidth()) { Text("Delete today's entries") }
            OutlinedButton(onClick = { confirm = "all" }, Modifier.fillMaxWidth()) { Text("Delete all entries") }
            Spacer(Modifier.height(12.dp))
            Text(
                "Version $appVersion",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
        }
    }

    confirm?.let { what ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Are you sure?") },
            text = { Text(if (what == "all") "All entries will be deleted." else "Today's entries will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    if (what == "all") vm.deleteAll() else vm.deleteToday()
                    confirm = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } }
        )
    }
}

fun formatHms(total: Long): String =
    "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)

fun formatHm(total: Long): String = "%dh %02dm".format(total / 3600, (total % 3600) / 60)

/** Order: client, project, in/out, time. */
@Composable
fun EntryRow(e: EntryView, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            e.clientColor?.let { ColorDot(it); Spacer(Modifier.width(6.dp)) }
            Text(
                e.clientName ?: "—",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            e.projectName ?: "—",
            Modifier.weight(1f),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            e.inOut,
            Modifier.width(36.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (e.inOut == "IN") MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
        )
        val secs = parseSeconds(e.time)
        Text(formatSeconds(secs % DAY_SECONDS), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        if (secs >= DAY_SECONDS) Text("+1", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
    }
}

/** Hours and minutes of worked time after which the notification is shown. Saved when valid (1 min to 23:59). */
@Composable
private fun NotifyTimeFields(minutes: Int, onChange: (Int) -> Unit) {
    var h by remember { mutableStateOf((minutes / 60).toString()) }
    var m by remember { mutableStateOf((minutes % 60).toString()) }

    fun push(newH: String, newM: String) {
        val hh = newH.toIntOrNull()
        val mm = newM.toIntOrNull()
        if (hh != null && mm != null && hh in 0..23 && mm in 0..59 && hh * 60 + mm > 0) onChange(hh * 60 + mm)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = h,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { h = it; push(it, m) } },
            label = { Text("Hours") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = m,
            onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) { m = it; push(h, it) } },
            label = { Text("Minutes") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f)
        )
    }
}
