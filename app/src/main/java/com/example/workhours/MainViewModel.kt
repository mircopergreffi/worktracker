package com.example.workhours

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.workhours.data.AppDatabase
import com.example.workhours.data.Entry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

    /** Sums IN→OUT pairs; an open IN counts up to "now". */
    private fun workedSeconds(entries: List<Entry>, now: LocalTime): Long {
        var total = 0L
        var start: LocalTime? = null
        for (e in entries) {
            val t = LocalTime.parse(e.time)
            if (e.inOut == "IN") {
                if (start == null) start = t
            } else if (start != null) {
                total += Duration.between(start, t).seconds
                start = null
            }
        }
        if (start != null) total += maxOf(0, Duration.between(start, now).seconds)
        return total
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
