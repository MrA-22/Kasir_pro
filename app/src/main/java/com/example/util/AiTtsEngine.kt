package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class AiTtsEngine(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("id", "ID"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
                android.util.Log.d("AiTtsEngine", "TTS initialized successfully")
            } else {
                android.util.Log.e("AiTtsEngine", "Language not supported or data missing")
            }
        } else {
            android.util.Log.e("AiTtsEngine", "TTS initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        android.util.Log.d("AiTtsEngine", "Attempting to speak: $text, isInitialized: $isInitialized")
        if (isInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            android.util.Log.w("AiTtsEngine", "TTS not initialized, cannot speak")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
