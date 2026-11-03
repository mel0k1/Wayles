package com.wayles.app.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

class VoiceTake(val file: File, val seconds: Int, val amps: List<Int>)

class VoiceRecorder(private val ctx: Context) {

    private var rec: MediaRecorder? = null
    private var job: Job? = null
    private var file: File? = null
    private var startedAt = 0L
    private val amps = CopyOnWriteArrayList<Int>()

    fun start(scope: CoroutineScope): Boolean {
        val f = File(
            ctx.cacheDir,
            "voice_${System.currentTimeMillis()}.${if (Build.VERSION.SDK_INT >= 29) "ogg" else "m4a"}"
        )
        val r = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(ctx)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        val ok = runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (Build.VERSION.SDK_INT >= 29) {
                r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            } else {
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            r.setAudioSamplingRate(48000)
            r.setAudioEncodingBitRate(96000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
        }.isSuccess
        if (!ok) {
            runCatching { r.release() }
            f.delete()
            return false
        }
        rec = r
        file = f
        amps.clear()
        startedAt = System.currentTimeMillis()
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val a = runCatching { r.maxAmplitude }.getOrNull()
                if (a != null && a > 0) {
                    amps.add(a)
                }
                delay(80)
            }
        }
        return true
    }

    fun stop(): VoiceTake? {
        job?.cancel()
        job = null
        val f = file ?: return null
        val seconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(1L).toInt()
        val stopped = runCatching { rec?.stop() }.isSuccess
        runCatching { rec?.release() }
        rec = null
        file = null
        val taken = amps.toList()
        amps.clear()
        if (!stopped) {
            f.delete()
            return null
        }
        return VoiceTake(f, seconds, taken)
    }

    fun cancel() {
        job?.cancel()
        job = null
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        rec = null
        file?.delete()
        file = null
        amps.clear()
    }
}

fun waveformOf(samples: List<Int>): ByteArray {
    val n = samples.size
    if (n == 0) {
        return ByteArray(1) { 8 }
    }
    val target = minOf(63, n)
    return ByteArray(target) { i ->
        val a = i * n / target
        val b = (((i + 1) * n / target).coerceAtLeast(a + 1)).coerceAtMost(n)
        val avg = samples.subList(a, b).average()
        ((avg / 1050.0).toInt().coerceIn(1, 31)).toByte()
    }
}
