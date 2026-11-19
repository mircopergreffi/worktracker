package com.example.workhours.ui

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workhours.MainViewModel
import com.example.workhours.data.Client
import com.example.workhours.data.Project

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(vm: MainViewModel, onBack: () -> Unit) {
    val clients by vm.clients.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editClient by remember { mutableStateOf<Client?>(null) }
    var editProject by remember { mutableStateOf<Project?>(null) }
    var newClient by remember { mutableStateOf(false) }
    var newProjectFor by remember { mutableStateOf<Client?>(null) }

    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    fun inUse(what: String) =
        Toast.makeText(context, "$what has entries and can't be deleted. Rename it instead.", Toast.LENGTH_LONG).show()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Clients & projects") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = { newClient = true }) { Text("Add client") } }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 24.dp).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (clients.isEmpty()) item { Text("No clients yet", color = muted) }
            items(clients, key = { it.id }) { c ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .clickable { editClient = c }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorDot(c.color, 14.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(c.name, Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 18.sp)
                        Text("Edit", color = muted, fontSize = 14.sp)
                    }
                    projects.filter { it.clientId == c.id }.forEach { p ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .clickable { editProject = p }
                                .padding(start = 24.dp).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.name, Modifier.weight(1f), fontSize = 16.sp)
                            Text("Edit", color = muted, fontSize = 14.sp)
                        }
                    }
                    TextButton(onClick = { newProjectFor = c }, Modifier.padding(start = 12.dp)) {
                        Text("+ Add project")
                    }
                }
            }
        }
    }

    editClient?.let { c ->
        NameColorDialog(
            title = "Edit client",
            label = "Client name",
            withColor = true,
            initialName = c.name,
            initialColor = c.color,
            onDelete = {
                vm.deleteClient(c) { ok -> if (!ok) inUse("Client") }
                editClient = null
            },
            onConfirm = { name, color -> vm.updateClient(c.copy(name = name, color = color)); editClient = null },
            onDismiss = { editClient = null }
        )
    }
    editProject?.let { p ->
        NameColorDialog(
            title = "Edit project",
            label = "Project name",
            withColor = false,
            initialName = p.name,
            onDelete = {
                vm.deleteProject(p) { ok -> if (!ok) inUse("Project") }
                editProject = null
            },
            onConfirm = { name, _ -> vm.updateProject(p.copy(name = name)); editProject = null },
            onDismiss = { editProject = null }
        )
    }
    if (newClient) {
        NameColorDialog(
            title = "New client",
            label = "Client name",
            withColor = true,
            initialColor = Palette[clients.size % Palette.size],
            onConfirm = { name, color -> vm.addClient(name, color, false); newClient = false },
            onDismiss = { newClient = false }
        )
    }
    newProjectFor?.let { c ->
        NameColorDialog(
            title = "New project for ${c.name}",
            label = "Project name",
            withColor = false,
            onConfirm = { name, _ -> vm.addProject(name, c.id, false); newProjectFor = null },
            onDismiss = { newProjectFor = null }
        )
    }
}
