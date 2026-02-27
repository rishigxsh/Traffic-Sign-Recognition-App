package com.example.tsrapp.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechHelper(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    
    fun initialize(callback: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.getDefault())
                isInitialized = result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                callback(isInitialized)
            } else {
                isInitialized = false
                callback(false)
            }
        }
    }
    
    fun speak(text: String) {
        if (isInitialized && tts != null && SettingsManager.isTtsEnabled(context)) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
    
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}

