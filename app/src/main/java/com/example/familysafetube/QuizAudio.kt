package com.example.familysafetube

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Plays everything the quiz says: the phone's text-to-speech voice, the parent's recordings, the
 * bundled animal sounds, and the synthesised cheer / "oops". One step at a time, in order.
 *
 * Playing a new plan (or [stop]) cancels the previous one, so a quick tap never leaves two voices
 * talking over each other. Every failure (missing voice, bad file) just skips that step.
 * Create one per screen and call [release] when the screen closes.
 */
class QuizAudio(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ttsFailed = false
    private val whenTtsReady = ArrayList<(Boolean) -> Unit>()
    private val utteranceDone = HashMap<String, () -> Unit>()
    private var utteranceCounter = 0
    private var generation = 0
    private var player: MediaPlayer? = null
    private val tracks = ArrayList<AudioTrack>()

    private val mediaAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val utteranceListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) { finish(utteranceId) }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) { finish(utteranceId) }
        override fun onError(utteranceId: String?, errorCode: Int) { finish(utteranceId) }
        private fun finish(id: String?) {
            handler.post { id?.let { utteranceDone.remove(it) }?.invoke() }
        }
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            handler.post {
                ttsReady = status == TextToSpeech.SUCCESS
                ttsFailed = !ttsReady
                if (ttsReady) tts?.setOnUtteranceProgressListener(utteranceListener)
                val waiting = ArrayList(whenTtsReady)
                whenTtsReady.clear()
                waiting.forEach { it(ttsReady) }
            }
        }
    }

    /** False only when we know the phone has no voice for [lang]. (True while the engine is still starting.) */
    fun isTtsAvailable(lang: QuizLang): Boolean {
        if (ttsFailed) return false
        val engine = tts ?: return false
        if (!ttsReady) return true
        return engine.isLanguageAvailable(Locale.forLanguageTag(lang.localeTag)) >= TextToSpeech.LANG_AVAILABLE
    }

    // ── playing a plan ───────────────────────────────────────────────────────
    fun play(steps: List<AudioStep>, onDone: () -> Unit = {}) {
        stopPlayback()
        val gen = ++generation
        playFrom(steps, 0, gen, onDone)
    }

    fun stop() {
        generation++
        stopPlayback()
    }

    private fun playFrom(steps: List<AudioStep>, index: Int, gen: Int, onDone: () -> Unit) {
        if (gen != generation) return
        if (index >= steps.size) { onDone(); return }
        val next = { playFrom(steps, index + 1, gen, onDone) }
        when (val step = steps[index]) {
            is AudioStep.Tts -> speak(step, gen, next)
            is AudioStep.Effect -> playEffect(step, gen, next)
            is AudioStep.RecordedFile -> playFile(QuizRepository.audioFile(context, step.fileName), gen, next)
            is AudioStep.BuiltIn -> {
                val res = BuiltInSounds.resFor(step.key)
                if (res == null) next() else playRaw(res, gen, next)
            }
        }
    }

    private fun speak(step: AudioStep.Tts, gen: Int, done: () -> Unit) {
        val engine = tts
        if (engine == null || ttsFailed || step.text.isBlank()) { done(); return }
        val run = { ready: Boolean ->
            if (gen == generation) {
                if (!ready) {
                    done()
                } else {
                    val result = engine.setLanguage(Locale.forLanguageTag(step.lang.localeTag))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        done()                                    // no voice for this language: skip the step
                    } else {
                        engine.setPitch(step.pitch)                  // the celebration's voice style (1.0 = normal)
                        engine.setSpeechRate(step.rate)
                        val id = "quiz-${++utteranceCounter}"
                        utteranceDone[id] = { if (gen == generation) done() }
                        engine.speak(step.text, TextToSpeech.QUEUE_FLUSH, null, id)
                    }
                }
            }
        }
        if (ttsReady) run(true) else whenTtsReady.add(run)
    }

    private fun playFile(file: File, gen: Int, done: () -> Unit) {
        if (!file.exists() || file.length() == 0L) { done(); return }
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(mediaAttrs)
            mp.setDataSource(file.absolutePath)
            attach(mp, gen, done)
            mp.prepare()
            player = mp
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "Could not play ${file.name}: ${e.message}")
            releasePlayer(mp)
            done()
        }
    }

    private fun playRaw(resId: Int, gen: Int, done: () -> Unit) {
        val mp = MediaPlayer.create(context, resId)
        if (mp == null) { done(); return }
        try {
            attach(mp, gen, done)
            player = mp
            mp.start()
        } catch (e: Exception) {
            Log.w(TAG, "Could not play built-in sound: ${e.message}")
            releasePlayer(mp)
            done()
        }
    }

    private fun attach(mp: MediaPlayer, gen: Int, done: () -> Unit) {
        mp.setOnCompletionListener { releasePlayer(mp); handler.post { if (gen == generation) done() } }
        mp.setOnErrorListener { _, _, _ -> releasePlayer(mp); handler.post { if (gen == generation) done() }; true }
    }

    private fun releasePlayer(mp: MediaPlayer) {
        try { mp.release() } catch (_: Exception) {}
        if (player === mp) player = null
    }

    private fun stopPlayback() {
        utteranceDone.clear()
        try { tts?.stop() } catch (_: Exception) {}
        player?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            releasePlayer(mp)
        }
    }

    // ── feedback sounds (synthesised, no assets) ─────────────────────────────
    fun cheer() = playTone(ToneSynth.CHEER)
    fun oops() = playTone(ToneSynth.OOPS)

    private fun playTone(notes: List<Note>) = playPcm(ToneSynth.render(notes), ToneSynth.durationMs(notes))

    /** A celebration sound (cheer, fanfare, applause, sparkle) as a step of a plan: the next step starts when it has finished. */
    private fun playEffect(step: AudioStep.Effect, gen: Int, done: () -> Unit) {
        val ms = ToneSynth.effectDurationMs(step.effect, step.long)
        if (ms <= 0) { done(); return }
        playPcm(ToneSynth.renderEffect(step.effect, step.long), ms)
        handler.postDelayed({ if (gen == generation) done() }, ms.toLong())
    }

    private fun playPcm(pcm: ShortArray, durationMs: Int) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(ToneSynth.SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            tracks.add(track)
            handler.postDelayed({
                try { track.release() } catch (_: Exception) {}
                tracks.remove(track)
            }, durationMs + 300L)
        } catch (e: Exception) {
            Log.w(TAG, "Could not play feedback tone: ${e.message}")
        }
    }

    fun release() {
        generation++
        handler.removeCallbacksAndMessages(null)
        stopPlayback()
        whenTtsReady.clear()
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        tracks.forEach { try { it.release() } catch (_: Exception) {} }
        tracks.clear()
    }

    private companion object { const val TAG = "QuizAudio" }
}
