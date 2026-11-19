package com.example.workhours

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.workhours.data.AppDatabase
import com.example.workhours.data.Entry
import com.example.workhours.data.workedSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class DaySummary(val day: LocalDate, val seconds: Long, val count: Int)

data class UiState(
    val entries: List<Entry> = emptyList(),
    val workedSeconds: Long = 0,
    val isIn: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).entryDao()
    private val prefs = app.getSharedPreferences("prefs", 0)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    // null = follow system until the user picks something
    private val _dark = MutableStateFlow(if (prefs.contains("dark")) prefs.getBoolean("dark", false) else null)
    val dark: StateFlow<Boolean?> = _dark

    fun setDark(value: Boolean) {
        prefs.edit().putBoolean("dark", value).apply()
        _dark.value = value
    }

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

    fun mark(inOut: String) {
        viewModelScope.launch {
            val now = LocalDateTime.now()
            dao.insert(Entry(day = now.toLocalDate().toString(), inOut = inOut, time = now.format(timeFmt)))
        }
    }

    fun deleteToday() = viewModelScope.launch { dao.deleteDay(java.time.LocalDate.now().toString()) }
    fun deleteAll() = viewModelScope.launch { dao.deleteAll() }

    /** Writes the whole table as plain text to the chosen document. */
    fun export(uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = buildString {
                        appendLine("id;day;in_out;time")
                        dao.getAll().forEach { appendLine("${it.id};${it.day};${it.inOut};${it.time}") }
                    }
                    getApplication<Application>().contentResolver.openOutputStream(uri)!!
                        .use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            onDone(ok)
        }
    }
}
