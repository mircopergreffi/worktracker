package com.example.workhours

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Reads the microphone and reports the dominant tone about every 46 ms.
 * Only runs while [start] has been called and the app is open; [stop] releases the microphone.
 */
class ToneEngine(private val scope: CoroutineScope) {
    private val analyzer = ToneAnalyzer(WINDOW, SAMPLE_RATE)
    private val _reading = MutableStateFlow<ToneReading?>(null)

    /** Current tone, or null when there is silence or only noise. */
    val reading: StateFlow<ToneReading?> = _reading

    private var job: Job? = null

    @SuppressLint("MissingPermission") // the caller checks RECORD_AUDIO before starting
    fun start(onFrame: (ToneReading?) -> Unit, onError: (String) -> Unit) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var rec: AudioRecord? = null
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val r = AudioRecord(
                    MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuf, HOP * 8)
                )
                rec = r
                if (r.state != AudioRecord.STATE_INITIALIZED) {
                    onError("Could not open the microphone")
                    return@launch
                }
                r.startRecording()

                val window = FloatArray(WINDOW)
                var filled = 0
                val chunk = ShortArray(HOP)
                while (isActive) {
                    val read = r.read(chunk, 0, HOP)
                    if (read < 0) {
                        onError("Microphone error")
                        break
                    }
                    if (read == 0) continue
                    // slide the window: drop the oldest samples, append the new ones
                    if (filled + read > WINDOW) {
                        val shift = filled + read - WINDOW
                        System.arraycopy(window, shift, window, 0, filled - shift)
                        filled -= shift
                    }
                    for (i in 0 until read) window[filled + i] = chunk[i] / 32768f
                    filled += read
                    if (filled == WINDOW) {
                        val tone = analyzer.analyze(window)
                        _reading.value = tone
                        onFrame(tone)
                    }
                }
            } catch (e: SecurityException) {
                onError("Microphone permission denied")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onError("Microphone error")
            } finally {
                rec?.let {
                    runCatching { it.stop() }
                    it.release()
                }
                _reading.value = null
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val WINDOW = 4096 // ~93 ms; frequency resolution ~11 Hz, refined by interpolation
        const val HOP = 2048    // a new analysis every ~46 ms
    }
}
