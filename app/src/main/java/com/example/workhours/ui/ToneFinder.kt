package com.example.workhours.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workhours.MainViewModel
import com.example.workhours.Notes
import com.example.workhours.ToneReading
import kotlin.math.abs

fun micGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/** Works like a guitar tuner: shows the note, frequency, cents and level of the tone it hears. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToneFinderScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasMic by remember { mutableStateOf(micGranted(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMic = it }

    // The microphone is only used while this screen is open and permitted; the screen stays on.
    DisposableEffect(hasMic) {
        if (hasMic) vm.setFinderActive(true)
        onDispose { vm.setFinderActive(false) }
    }
    val view = LocalView.current
    DisposableEffect(hasMic) {
        view.keepScreenOn = hasMic
        onDispose { view.keepScreenOn = false }
    }

    val reading by vm.toneReading.collectAsStateWithLifecycle()
    val last by vm.lastTone.collectAsStateWithLifecycle()
    val toneIn by vm.toneIn.collectAsStateWithLifecycle()
    val toneOut by vm.toneOut.collectAsStateWithLifecycle()
    val note = reading?.let { Notes.info(it.freq) }
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tone finder") },
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
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!hasMic) {
                Text("The microphone is needed to find a tone. It is only used while this screen or tone listening is open.")
                Button(onClick = { permission.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
                return@Column
            }

            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    note?.let { "${it.name}${it.octave}" } ?: "--",
                    fontSize = 72.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    reading?.let { "%.1f Hz".format(it.freq) } ?: "Play the tone near the microphone",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    color = if (reading == null) muted else MaterialTheme.colorScheme.onSurface
                )
            }
            CentsGauge(note?.cents)
            Text(
                note?.let { "%+.0f cents".format(it.cents) } ?: " ",
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = muted,
                fontSize = 14.sp
            )

            Text("Level", color = muted, fontSize = 14.sp)
            LevelMeter(reading?.levelDb)
            Text(
                reading?.let { "%.1f dB".format(it.levelDb) } ?: "-- dB",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Text("Last detected tone", fontWeight = FontWeight.Medium)
            Text(
                last?.let { "%.1f Hz, %.1f dB".format(it.freq, it.levelDb) } ?: "None yet",
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { last?.let { vm.saveTone(true, it); Toast.makeText(context, "IN tone saved", Toast.LENGTH_SHORT).show() } },
                    enabled = last != null,
                    modifier = Modifier.weight(1f)
                ) { Text("Use for IN") }
                Button(
                    onClick = { last?.let { vm.saveTone(false, it); Toast.makeText(context, "OUT tone saved", Toast.LENGTH_SHORT).show() } },
                    enabled = last != null,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) { Text("Use for OUT") }
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            Text("Saved tones", fontWeight = FontWeight.Medium)
            SavedToneRow("IN tone", toneIn) { vm.clearTone(true) }
            SavedToneRow("OUT tone", toneOut) { vm.clearTone(false) }

            Text(
                "On the main screen, \"Listen for tones\" presses IN or OUT when it hears a saved tone: " +
                    "same frequency (within 3%, at least 15 Hz), at most 8 dB quieter than saved, for 0.3 seconds. " +
                    "Tones are not acted on while this screen is open.",
                color = muted,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SavedToneRow(label: String, tone: ToneReading?, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(
                tone?.let { "%.1f Hz, %.1f dB".format(it.freq, it.levelDb) } ?: "Not set",
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (tone != null) TextButton(onClick = onClear) { Text("Clear") }
    }
}

/** Tuner needle: the dot sits in the middle when the tone is exactly on the note; ticks every 10 cents. */
@Composable
private fun CentsGauge(cents: Float?) {
    val line = MaterialTheme.colorScheme.outline
    val good = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(48.dp)) {
        val w = size.width
        val cy = size.height / 2
        val stroke = 2.dp.toPx()
        drawLine(line, Offset(0f, cy), Offset(w, cy), stroke)
        for (i in -5..5) {
            val x = w / 2 + i * (w / 10)
            val half = if (i == 0) size.height / 2 else size.height / 5
            drawLine(line, Offset(x, cy - half), Offset(x, cy + half), stroke)
        }
        if (cents != null) {
            val x = w / 2 + cents.coerceIn(-50f, 50f) / 50f * (w / 2)
            drawCircle(if (abs(cents) < 5f) good else off, 10.dp.toPx(), Offset(x, cy))
        }
    }
}

/** -80 dB (left) to 0 dB (right). */
@Composable
private fun LevelMeter(db: Float?) {
    val fraction = if (db == null) 0f else ((db + 80f) / 80f).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(fraction)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}
