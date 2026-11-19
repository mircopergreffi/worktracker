package com.example.workhours

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.workhours.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class DaySummary(val day: LocalDate, val seconds: Long, val count: Int)

data class UiState(
    val entries: List<EntryView> = emptyList(),
    val workedSeconds: Long = 0,
    val isIn: Boolean = false
)

data class Selection(val clientId: Long? = null, val projectId: Long? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).entryDao()
    private val prefs = app.getSharedPreferences("prefs", 0)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    // ---------- theme ----------
    // null = follow system until the user picks something
    private val _dark = MutableStateFlow(if (prefs.contains("dark")) prefs.getBoolean("dark", false) else null)
    val dark: StateFlow<Boolean?> = _dark

    fun setDark(value: Boolean) {
        prefs.edit().putBoolean("dark", value).apply()
        _dark.value = value
    }

    // ---------- clients / projects ----------
    val clients: StateFlow<List<Client>> =
        dao.observeClients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val projects: StateFlow<List<Project>> =
        dao.observeProjects().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selection = MutableStateFlow(
        Selection(
            prefs.getLong("sel_client", -1).takeIf { it >= 0 },
            prefs.getLong("sel_project", -1).takeIf { it >= 0 }
        )
    )
    val selection: StateFlow<Selection> = _selection

    private fun setSelection(clientId: Long?, projectId: Long?) {
        prefs.edit().putLong("sel_client", clientId ?: -1).putLong("sel_project", projectId ?: -1).apply()
        _selection.value = Selection(clientId, projectId)
    }

    fun selectClient(id: Long) {
        if (_selection.value.clientId != id) setSelection(id, null)
    }

    fun selectProject(id: Long) = setSelection(_selection.value.clientId, id)

    fun addClient(name: String, color: Int, select: Boolean) = viewModelScope.launch {
        val id = dao.insertClient(Client(name = name, color = color))
        if (select) setSelection(id, null)
    }

    fun updateClient(client: Client) = viewModelScope.launch { dao.updateClient(client) }

    /** Fails (false) when entries reference the client; rename it instead. */
    fun deleteClient(client: Client, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        if (dao.entriesForClient(client.id) > 0) {
            onResult(false)
            return@launch
        }
        dao.deleteProjectsOfClient(client.id)
        dao.deleteClient(client.id)
        if (_selection.value.clientId == client.id) setSelection(null, null)
        onResult(true)
    }

    fun addProject(name: String, clientId: Long, select: Boolean) = viewModelScope.launch {
        val id = dao.insertProject(Project(clientId = clientId, name = name))
        if (select) setSelection(clientId, id)
    }

    fun updateProject(project: Project) = viewModelScope.launch { dao.updateProject(project) }

    fun deleteProject(project: Project, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        if (dao.entriesForProject(project.id) > 0) {
            onResult(false)
            return@launch
        }
        dao.deleteProject(project.id)
        if (_selection.value.projectId == project.id) setSelection(_selection.value.clientId, null)
        onResult(true)
    }

    // ---------- clock / state ----------
    /** Emits the current date-time once per second. */
    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(1000 - (System.currentTimeMillis() % 1000))
        }
    }

    val state: StateFlow<UiState> = clock
        .map { it.toLocalDate().toString() }
        .distinctUntilChanged()
        .flatMapLatest { dao.observeDay(it) }
        .combine(clock) { entries, now ->
            UiState(
                entries = entries,
                workedSeconds = workedSeconds(entries, now.toLocalTime()),
                isIn = entries.lastOrNull()?.inOut == "IN"
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    /** Last 30 days (today first) with worked seconds per day, including empty days. */
    val history: StateFlow<List<DaySummary>> = clock
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { today ->
            dao.observeSince(today.minusDays(29).toString()).combine(clock) { all, now ->
                val byDay = all.groupBy { it.day }
                (0..29).map { i ->
                    val d = today.minusDays(i.toLong())
                    val list = byDay[d.toString()].orEmpty()
                    DaySummary(d, workedSeconds(list, if (i == 0) now.toLocalTime() else null), list.size)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Entries and worked time for any single day. */
    fun dayState(day: LocalDate): Flow<UiState> =
        dao.observeDay(day.toString()).combine(clock) { entries, now ->
            UiState(
                entries = entries,
                workedSeconds = workedSeconds(entries, if (day == now.toLocalDate()) now.toLocalTime() else null),
                isIn = entries.lastOrNull()?.inOut == "IN"
            )
        }

    // ---------- entries ----------
    private suspend fun insertEntry(inOut: String, clientId: Long?, projectId: Long?) {
        val now = LocalDateTime.now()
        dao.insert(
            Entry(
                day = now.toLocalDate().toString(),
                inOut = inOut,
                time = now.format(timeFmt),
                clientId = clientId,
                projectId = projectId
            )
        )
    }

    fun clockIn(clientId: Long, projectId: Long) = viewModelScope.launch {
        insertEntry("IN", clientId, projectId)
    }

    /** OUT inherits client and project from the last entry (the active IN). */
    fun clockOut() = viewModelScope.launch {
        val last = dao.lastEntry()
        insertEntry("OUT", last?.clientId, last?.projectId)
    }

    fun deleteToday() = viewModelScope.launch { dao.deleteDay(LocalDate.now().toString()) }
    fun deleteAll() = viewModelScope.launch { dao.deleteAll() }

    /** Writes the whole entries table (with current names) as plain text to the chosen document. */
    fun export(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    fun clean(s: String?) = (s ?: "").replace(';', ',')
                    val text = buildString {
                        appendLine("id;day;in_out;time;client_id;project_id;client;project")
                        dao.getAllViews().forEach {
                            appendLine(
                                "${it.id};${it.day};${it.inOut};${it.time};" +
                                    "${it.clientId ?: ""};${it.projectId ?: ""};" +
                                    "${clean(it.clientName)};${clean(it.projectName)}"
                            )
                        }
                    }
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!
                        .use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            onDone(ok)
        }
    }
}
