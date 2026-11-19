package com.example.workhours.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workhours.MainViewModel

@Composable
fun AppRoot(vm: MainViewModel, isDark: Boolean) {
    var showSettings by remember { mutableStateOf(false) }
    BackHandler(showSettings) { showSettings = false }
    if (showSettings) SettingsScreen(vm) { showSettings = false }
    else MainScreen(vm, isDark) { showSettings = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel, isDark: Boolean, onSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }

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
            Text("Worked today", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = 14.sp)
            Text(
                formatHms(state.workedSeconds),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { vm.mark("IN") },
                    enabled = !state.isIn,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("IN", fontSize = 18.sp, fontWeight = FontWeight.Medium) }
                Button(
                    onClick = { vm.mark("OUT") },
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
            Text("Today", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            if (state.entries.isEmpty()) {
                Text("No entries yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.entries, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            e.inOut,
                            Modifier.width(56.dp),
                            fontWeight = FontWeight.SemiBold,
                            color = if (e.inOut == "IN") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                        )
                        Text(e.time, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    var confirm by remember { mutableStateOf<String?>(null) }

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
        Column(Modifier.padding(padding).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Data", fontWeight = FontWeight.Medium)
            OutlinedButton(onClick = { confirm = "today" }, Modifier.fillMaxWidth()) { Text("Delete today's entries") }
            OutlinedButton(onClick = { confirm = "all" }, Modifier.fillMaxWidth()) { Text("Delete all data") }
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

private fun formatHms(total: Long): String =
    "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
