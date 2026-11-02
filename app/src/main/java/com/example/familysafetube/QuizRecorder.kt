package com.example.familysafetube

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Records a short voice clip from the phone's microphone (AAC in an MPEG-4 container, mono).
 * Capped at [MAX_MS]; [onMaxReached] fires on the main thread when the cap stops the recording.
 */
class QuizRecorder(private val context: Context, private val onMaxReached: () -> Unit = {}) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    val isRecording: Boolean get() = recorder != null

    /** Starts recording into [target]. Returns false (and cleans up) if the microphone can't be used. */
    fun start(target: File): Boolean {
        cancel()
        return try {
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                    else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(32_000)
            r.setAudioEncodingBitRate(64_000)
            r.setMaxDuration(MAX_MS)
            r.setOutputFile(target.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached()
            }
            r.prepare()
            r.start()
            recorder = r
            file = target
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not start recording: ${e.message}")
            target.delete()
            recorder = null
            false
        }
    }

    /** Stops and keeps the clip. Returns false if nothing usable was recorded (the file is then deleted). */
    fun stop(): Boolean {
        val r = recorder ?: return false
        val f = file
        recorder = null
        file = null
        val ok = try { r.stop(); true } catch (e: RuntimeException) { false }   // stop() throws if nothing was captured
        try { r.release() } catch (_: Exception) {}
        if (!ok || f == null || f.length() < MIN_BYTES) { f?.delete(); return false }
        return true
    }

    /** Stops and throws the clip away (screen closed, permission lost…). */
    fun cancel() {
        val r = recorder ?: return
        val f = file
        recorder = null
        file = null
        try { r.stop() } catch (_: RuntimeException) {}
        try { r.release() } catch (_: Exception) {}
        f?.delete()
    }

    companion object {
        const val MAX_MS = 8_000
        private const val MIN_BYTES = 800L
        private const val TAG = "QuizRecorder"
    }
}
