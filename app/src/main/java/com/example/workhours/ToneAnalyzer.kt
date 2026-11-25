package com.example.workhours

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin

/** A detected (or saved) tone: frequency in Hz and the tone's level in dB relative to full scale (0 dB = loudest). */
data class ToneReading(val freq: Float, val levelDb: Float)

/** Finds the dominant tone of a block of samples with a windowed FFT. */
class ToneAnalyzer(private val n: Int = 4096, private val sampleRate: Int = 44100) {
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / (n - 1))).toFloat() }
    private val cosT = FloatArray(n / 2) { cos(2.0 * PI * it / n).toFloat() }
    private val sinT = FloatArray(n / 2) { (-sin(2.0 * PI * it / n)).toFloat() }
    private val rev = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }
    private val re = FloatArray(n)
    private val im = FloatArray(n)
    private val mag = FloatArray(n / 2 + 1)

    /** [samples] holds [n] values in -1..1. Returns null for silence or noise without a clear tone. */
    fun analyze(samples: FloatArray): ToneReading? {
        for (i in 0 until n) {
            re[i] = samples[i] * window[i]
            im[i] = 0f
        }
        fft()

        val minBin = maxOf(2, (MIN_HZ * n / sampleRate).toInt())
        val maxBin = minOf(n / 2 - 2, (MAX_HZ * n / sampleRate).toInt())
        var peak = minBin
        var sum = 0.0
        for (k in minBin..maxBin) {
            val m = hypot(re[k], im[k])
            mag[k] = m
            sum += m
            if (m > mag[peak]) peak = k
        }
        val mean = sum / (maxBin - minBin + 1)
        if (mag[peak] < mean * TONAL_RATIO) return null // noise, not a tone

        // Parabolic interpolation on the log magnitude gives frequency and amplitude between the bins.
        val a = ln(mag[peak - 1] + 1e-12)
        val b = ln(mag[peak] + 1e-12)
        val c = ln(mag[peak + 1] + 1e-12)
        val denom = a - 2 * b + c
        val p = if (denom == 0.0) 0.0 else 0.5 * (a - c) / denom
        val freq = (peak + p) * sampleRate / n
        val amplitude = 4.0 * exp(b - 0.25 * (a - c) * p) / n // Hann window: amplitude = 4|X| / n
        val db = 20.0 * log10(amplitude)
        if (db < MIN_DB) return null
        return ToneReading(freq.toFloat(), db.toFloat())
    }

    private fun fft() {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosT[k]
                    val wi = sinT[k]
                    val xr = re[j + half] * wr - im[j + half] * wi
                    val xi = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - xr
                    im[j + half] = im[j] - xi
                    re[j] += xr
                    im[j] += xi
                    k += step
                }
                start += size
            }
            size *= 2
        }
    }

    companion object {
        const val MIN_HZ = 80
        const val MAX_HZ = 8000
        const val TONAL_RATIO = 10f // the peak must be at least 10x the average of the band
        const val MIN_DB = -65.0    // quieter tones are ignored
    }
}

data class NoteInfo(val name: String, val octave: Int, val cents: Float)

object Notes {
    private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** Nearest note (A4 = 440 Hz) and how many cents the frequency is above or below it. */
    fun info(freq: Float): NoteInfo {
        val midi = 69.0 + 12.0 * log2(freq / 440.0)
        val nearest = midi.roundToInt()
        return NoteInfo(
            name = NAMES[((nearest % 12) + 12) % 12],
            octave = floor(nearest / 12.0).toInt() - 1,
            cents = ((midi - nearest) * 100).toFloat()
        )
    }
}
