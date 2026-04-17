package com.example.tsrapp.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.*
import java.util.concurrent.PriorityBlockingQueue

class TextToSpeechHelper(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var focusRetryRunnable: Runnable? = null
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { }

    // --- Speech Queue System ---
    private data class SpeechItem(
        val label: String,
        val phrase: String,
        val priority: Int, // 1 = Critical, 2 = Speed, 3 = Info
        val timestamp: Long = System.currentTimeMillis()
    ) : Comparable<SpeechItem> {
        override fun compareTo(other: SpeechItem): Int {
            if (this.priority != other.priority) return this.priority.compareTo(other.priority)
            return this.timestamp.compareTo(other.timestamp)
        }
    }

    private val speechQueue = PriorityBlockingQueue<SpeechItem>()
    private var isProcessingSpeech = false
    private var lastSpokenTime = 0L
    private val spokenHistory = mutableMapOf<String, Long>()

    companion object {
        private const val SAME_SIGN_COOLDOWN_MS = 15_000L
        private const val GROUPING_WINDOW_MS = 6_000L // If signs are within 6s, skip "Sign ahead" prefix
        private const val TAG = "TextToSpeechHelper"
    }

    fun initialize(callback: (Boolean) -> Unit) {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupEngine()
                isInitialized = true
                applyLanguageWithFallbacks()
                mainHandler.post { callback(true) }
            } else {
                mainHandler.post { callback(false) }
            }
        }
    }

    private fun setupEngine() {
        tts?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
            }
            setSpeechRate(0.95f)
            setPitch(1.0f)
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isProcessingSpeech = true
                }
                override fun onDone(utteranceId: String?) {
                    onSpeechFinished()
                }
                override fun onError(utteranceId: String?) {
                    onSpeechFinished()
                }
            })
        }
    }

    private fun onSpeechFinished() {
        isProcessingSpeech = false
        abandonAudioFocus()
        lastSpokenTime = System.currentTimeMillis()
        // Wait a short moment (natural pause) before next sign
        mainHandler.postDelayed({ processNextInQueue() }, 1000L)
    }

    /**
     * Entry point for driving sign alerts. Now handles queuing and deduplication.
     */
    fun speakDrivingSign(label: String) {
        val mode = SettingsManager.getVoiceMode(appContext)
        if (mode == SettingsManager.VOICE_MUTED) return
        
        val isCritical = SignLabelToSpeech.isCriticalRoadAlert(label)
        if (mode == SettingsManager.VOICE_ALERTS && !isCritical) return

        // 1. Deduplication (Cooldown)
        val now = System.currentTimeMillis()
        val lastTime = spokenHistory[label] ?: 0L
        if (now - lastTime < SAME_SIGN_COOLDOWN_MS) return

        // 2. Priority Calculation
        val priority = when {
            isCritical -> 1
            label.contains("speed-limit") -> 2
            else -> 3
        }

        val phrase = SignLabelToSpeech.toSpokenPhrase(label)
        val item = SpeechItem(label, phrase, priority)

        // 3. Add to Queue
        if (!speechQueue.any { it.label == label }) {
            speechQueue.add(item)
            if (!isProcessingSpeech) {
                processNextInQueue()
            }
        }
    }

    private fun processNextInQueue() {
        if (isProcessingSpeech || !isInitialized || tts == null) return
        
        val item = speechQueue.poll() ?: return
        
        // Final check: is sign still valid (optional expiration logic could go here)
        
        speakItem(item)
    }

    /**
     * General purpose speech (e.g., for settings tests).
     * Bypasses the sign queue and cooldowns.
     */
    fun speak(text: String) {
        if (!isInitialized || tts == null) return
        val id = "tsr_gen_${UUID.randomUUID()}"
        val params = android.os.Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        
        val vol = when(SettingsManager.getVolumeLevel(appContext)) {
            SettingsManager.VOL_LOUDER -> 1.0f
            SettingsManager.VOL_SOFTER -> 0.45f
            else -> 0.82f
        }
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol)
        
        requestAudioFocusForSpeech()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    private fun speakItem(item: SpeechItem) {
        val now = System.currentTimeMillis()
        val usePrefix = (now - lastSpokenTime > GROUPING_WINDOW_MS)
        
        val finalPhrase = if (usePrefix) "Sign ahead ${item.phrase}" else item.phrase
        val id = "tsr_${UUID.randomUUID()}"
        val params = android.os.Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        
        // Volume context
        val vol = when(SettingsManager.getVolumeLevel(appContext)) {
            SettingsManager.VOL_LOUDER -> 1.0f
            SettingsManager.VOL_SOFTER -> 0.45f
            else -> 0.82f
        }
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, vol)

        spokenHistory[item.label] = now
        requestAudioFocusForSpeech()
        
        Log.i(TAG, "Speaking: $finalPhrase (Priority: ${item.priority})")
        tts?.speak(finalPhrase, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    private fun applyLanguageWithFallbacks(): Boolean {
        val engine = tts ?: return false
        val candidates = listOf(Locale.getDefault(), Locale.US, Locale.UK)
        for (loc in candidates) {
            val result = engine.setLanguage(loc)
            if (result >= TextToSpeech.LANG_AVAILABLE) return true
        }
        return false
    }

    private fun requestAudioFocusForSpeech() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .build()
            audioFocusRequest = req
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
    }

    fun shutdown() {
        speechQueue.clear()
        spokenHistory.clear()
        abandonAudioFocus()
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

