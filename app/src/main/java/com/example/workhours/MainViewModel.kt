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
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

data class DaySummary(val day: LocalDate, val seconds: Long, val count: Int)

data class UiState(
    val entries: List<EntryView> = emptyList(),
    val workedSeconds: Long = 0,
    val isIn: Boolean = false,
    /** True when the main screen shows yesterday's session that is still running past midnight. */
    val carriedOver: Boolean = false,
    /** Unfiltered entries of the day (dayState only), used to find IN/OUT partners. */
    val allEntries: List<EntryView> = emptyList()
)

data class HistoryFilter(
    val period: HistoryPeriod,
    val clientId: Long? = null,
    val projectId: Long? = null,
    val hideEmpty: Boolean = false
)

data class Selection(val clientId: Long? = null, val projectId: Long? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).entryDao()
    private val prefs = app.getSharedPreferences("prefs", 0)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    // ---------- theme ----------
    // The light theme is the default.
    private val _dark = MutableStateFlow(prefs.getBoolean("dark", false))
    val dark: StateFlow<Boolean> = _dark

    fun setDark(value: Boolean) {
        prefs.edit().putBoolean("dark", value).apply()
        _dark.value = value
    }

    // ---------- rounding of IN / OUT times ----------
    private fun loadRounding(key: String): TimeRounding =
        runCatching { TimeRounding.valueOf(prefs.getString(key, "") ?: "") }.getOrDefault(TimeRounding.NONE)

    private val _roundIn = MutableStateFlow(loadRounding("round_in"))
    private val _roundOut = MutableStateFlow(loadRounding("round_out"))
    val roundIn: StateFlow<TimeRounding> = _roundIn
    val roundOut: StateFlow<TimeRounding> = _roundOut

    fun setRoundIn(r: TimeRounding) {
        prefs.edit().putString("round_in", r.name).apply()
        _roundIn.value = r
    }

    fun setRoundOut(r: TimeRounding) {
        prefs.edit().putString("round_out", r.name).apply()
        _roundOut.value = r
    }

    // ---------- worked-time notification ----------
    private val _notifyEnabled = MutableStateFlow(prefs.getBoolean("notify_enabled", false))
    private val _notifyMinutes = MutableStateFlow(prefs.getInt("notify_minutes", 8 * 60))
    val notifyEnabled: StateFlow<Boolean> = _notifyEnabled
    val notifyMinutes: StateFlow<Int> = _notifyMinutes

    fun setNotifyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notify_enabled", enabled).apply()
        _notifyEnabled.value = enabled
    }

    fun setNotifyMinutes(totalMinutes: Int) {
        prefs.edit().putInt("notify_minutes", totalMinutes).apply()
        _notifyMinutes.value = totalMinutes
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
        if (_filter.value.clientId == client.id) _filter.update { it.copy(clientId = null, projectId = null) }
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
        if (_filter.value.projectId == project.id) _filter.update { it.copy(projectId = null) }
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

    /** The overall last entry's id; the editor uses it to know if an entry is the very last one. */
    val lastEntryId: StateFlow<Long?> = dao.observeLast().map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Day shown on the main screen: today, or yesterday while a session started yesterday
     * (an IN with no OUT yet) is still running through midnight.
     */
    private val workingDay: Flow<LocalDate> = clock
        .map { it.toLocalDate() }
        .distinctUntilChanged()
        .flatMapLatest { today ->
            dao.observeLast().map { last ->
                if (last != null && last.inOut == "IN" && last.day == today.minusDays(1).toString()) today.minusDays(1)
                else today
            }
        }
        .distinctUntilChanged()

    val state: StateFlow<UiState> = workingDay
        .flatMapLatest { day ->
            dao.observeDay(day.toString()).combine(clock) { entries, now ->
                UiState(
                    entries = entries,
                    workedSeconds = workedSeconds(entries, nowOn(day, now)),
                    isIn = entries.lastOrNull()?.inOut == "IN",
                    carriedOver = day != now.toLocalDate()
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    // ---------- history filter ----------
    private fun loadPeriod(): HistoryPeriod =
        runCatching { HistoryPeriod.valueOf(prefs.getString("default_period", "") ?: "") }
            .getOrDefault(HistoryPeriod.LAST_30)

    private val _defaultPeriod = MutableStateFlow(loadPeriod())
    val defaultPeriod: StateFlow<HistoryPeriod> = _defaultPeriod

    private val _filter = MutableStateFlow(HistoryFilter(_defaultPeriod.value))
    val historyFilter: StateFlow<HistoryFilter> = _filter

    fun setDefaultPeriod(period: HistoryPeriod) {
        prefs.edit().putString("default_period", period.name).apply()
        _defaultPeriod.value = period
        _filter.update { it.copy(period = period) }
    }

    fun setPeriod(period: HistoryPeriod) = _filter.update { it.copy(period = period) }
    fun setHideEmpty(hide: Boolean) = _filter.update { it.copy(hideEmpty = hide) }
    fun setFilterClient(id: Long?) = _filter.update { it.copy(clientId = id, projectId = null) }

    /** Picking a project also selects its client, so the two filters never disagree. */
    fun setFilterProject(id: Long?) = _filter.update { f ->
        val owner = id?.let { pid -> projects.value.find { it.id == pid }?.clientId } ?: f.clientId
        f.copy(clientId = owner, projectId = id)
    }

    init {
        // Keeps the alarm in sync with the entries and the notification settings.
        viewModelScope.launch {
            combine(
                workingDay.flatMapLatest { dao.observeDay(it.toString()) },
                _notifyEnabled,
                _notifyMinutes
            ) { _, _, _ -> }.collect { ReminderScheduler.reschedule(getApplication<Application>()) }
        }
    }

    /** One row per day of the selected period (newest first), for the selected client / project. */
    val history: StateFlow<List<DaySummary>> = _filter
        .flatMapLatest { f ->
            clock.map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { today ->
                val (from, to) = f.period.range(today)
                dao.observeRange(from.toString(), to.toString(), f.clientId, f.projectId)
                    .combine(clock) { all, now ->
                        val byDay = all.groupBy { it.day }
                        generateSequence(to) { d -> d.minusDays(1).takeIf { !it.isBefore(from) } }
                            .map { d ->
                                val list = byDay[d.toString()].orEmpty()
                                DaySummary(d, workedSeconds(list, nowOn(d, now)), list.size)
                            }
                            .filter { !f.hideEmpty || it.count > 0 }
                            .toList()
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Entries and worked time for one day, optionally only for a client and/or project. */
    fun dayState(day: LocalDate, clientId: Long? = null, projectId: Long? = null): Flow<UiState> =
        dao.observeDay(day.toString()).combine(clock) { all, now ->
            val entries = all.filter {
                (clientId == null || it.clientId == clientId) && (projectId == null || it.projectId == projectId)
            }
            UiState(
                entries = entries,
                workedSeconds = workedSeconds(entries, nowOn(day, now)),
                isIn = entries.lastOrNull()?.inOut == "IN",
                allEntries = all
            )
        }

    // ---------- entries ----------
    /**
     * Saves an IN/OUT with the (rounded) current time. It is never earlier than the previous entry.
     * An OUT that closes a session started the day before is stored on the IN's day with hours 24+
     * (01:30 becomes 25:30:00), so working through midnight stays one pair on one day.
     */
    private suspend fun insertEntry(inOut: String, clientId: Long?, projectId: Long?) {
        val now = LocalDateTime.now()
        val rounding = if (inOut == "IN") _roundIn.value else _roundOut.value
        var at = rounding.apply(now)
        // Rounding never pushes an IN into the next day.
        if (inOut == "IN" && at.toLocalDate() != now.toLocalDate()) at = now.toLocalDate().atTime(23, 59, 59)

        var abs = absSeconds(at.toLocalDate(), at.toLocalTime().toSecondOfDay())
        val last = dao.lastEntry()
        if (last != null) abs = maxOf(abs, absSeconds(LocalDate.parse(last.day), parseSeconds(last.time)))

        var day = LocalDate.ofEpochDay(abs / DAY_SECONDS)
        var secs = (abs % DAY_SECONDS).toInt()
        if (inOut == "OUT" && last != null && last.inOut == "IN") {
            val inDay = LocalDate.parse(last.day)
            if (ChronoUnit.DAYS.between(inDay, day) == 1L) {
                day = inDay
                secs += DAY_SECONDS
            }
        }

        dao.insert(
            Entry(
                day = day.toString(),
                inOut = inOut,
                time = formatSeconds(secs),
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

    /**
     * Deletes the entry together with its IN/OUT partner. Exception: the last entry of today is deleted
     * alone, so removing a last OUT restarts the live timer and removing a running IN stops it.
     */
    fun deleteEntry(id: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        val entry = dao.getEntry(id)
        if (entry != null) {
            val list = dao.entriesOfDay(entry.day)
            // The very last entry is deleted alone: a last OUT leaves its IN open, so the live timer restarts.
            val alone = deletesAlone(entry.day, entry.time, dao.lastEntry()?.id == id, LocalDate.now())
            val partner = if (alone) null else partnerOf(list, id)
            dao.deleteEntries(listOfNotNull(id, partner?.id))
        }
        onDone()
    }

    /**
     * Changes this entry's time, and client/project on it and its IN/OUT partner.
     * [onResult] gets null on success or an error message.
     */
    fun editEntry(id: Long, time: String, clientId: Long?, projectId: Long?, onResult: (String?) -> Unit) =
        viewModelScope.launch {
            val entry = dao.getEntry(id)
            if (entry == null) {
                onResult("Entry not found")
                return@launch
            }
            val list = dao.entriesOfDay(entry.day)
            val i = list.indexOfFirst { it.id == id }

            if (time != entry.time) {
                val newSecs = parseSeconds(time)
                val maxSecs = if (entry.inOut == "IN") DAY_SECONDS else 2 * DAY_SECONDS
                if (newSecs >= maxSecs) {
                    onResult("Time out of range")
                    return@launch
                }
                val prev = list.getOrNull(i - 1)?.time
                val next = list.getOrNull(i + 1)?.time
                if ((prev != null && newSecs <= parseSeconds(prev)) || (next != null && newSecs >= parseSeconds(next))) {
                    val lo = prev?.let { prettyTime(it) } ?: "00:00:00"
                    val hi = next?.let { prettyTime(it) } ?: if (entry.inOut == "IN") "23:59:59" else "23:59:59 (+1)"
                    onResult("Time must be between $lo and $hi")
                    return@launch
                }
                val now = LocalDateTime.now()
                if (absSeconds(LocalDate.parse(entry.day), newSecs) > absSeconds(now.toLocalDate(), now.toLocalTime().toSecondOfDay())) {
                    onResult("Time can't be in the future")
                    return@launch
                }
                dao.setTime(id, time)
            }
            val partner = partnerOf(list, id)
            dao.setClientProject(listOfNotNull(id, partner?.id), clientId, projectId)
            onResult(null)
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
