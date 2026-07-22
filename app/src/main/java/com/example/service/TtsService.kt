package com.example.service

import android.content.Context
import android.os.Bundle
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsService(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        try {
            // Use applicationContext to prevent leaking the activity/context reference
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Throwable) {
            Log.e("TtsService", "Failed to initialize TextToSpeech engine: ${e.localizedMessage}")
            tts = null
            isInitialized = false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                val ttsEngine = tts
                if (ttsEngine != null) {
                    val localesToTry = listOf(Locale.US, Locale.ENGLISH, Locale.UK, Locale.getDefault())
                    var success = false
                    for (locale in localesToTry) {
                        val result = ttsEngine.setLanguage(locale)
                        if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                            success = true
                            Log.d("TtsService", "Successfully set TTS language to: $locale")
                            break
                        }
                    }
                    // Explicitly set standard speech parameters
                    ttsEngine.setPitch(1.0f)
                    ttsEngine.setSpeechRate(1.0f)
                    
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    ttsEngine.setAudioAttributes(attrs)
                    
                    // Mark as initialized so speech is enabled
                    isInitialized = true
                    if (!success) {
                        Log.w("TtsService", "Preferred languages (US, ENG, UK, Default) returned warning status, but engine is marked ready for fallback.")
                    }
                } else {
                    isInitialized = false
                }
            } catch (e: Throwable) {
                Log.e("TtsService", "Error during TextToSpeech setLanguage: ${e.localizedMessage}")
                isInitialized = true // Fallback to let the system voice try
            }
        } else {
            Log.e("TtsService", "TextToSpeech Initialization Failed with status: $status")
            isInitialized = false
        }
    }

    fun isLanguageInstalled(locale: Locale): Boolean {
        return try {
            val result = tts?.isLanguageAvailable(locale)
            result == TextToSpeech.LANG_AVAILABLE || 
            result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
            result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    fun promptInstallTtsData(context: Context) {
        try {
            val intent = android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("TtsService", "Error launching TTS install intent: ${e.localizedMessage}")
        }
    }

    fun speak(text: String, volumeMultiplier: Float = 1.0f, groupLanguage: String = "en") {
        if (isInitialized && tts != null) {
            try {
                val locale = if (groupLanguage == "zh") Locale.CHINA else Locale.US
                val currentLocale = tts?.voice?.locale
                if (currentLocale?.language != locale.language) {
                    tts?.setLanguage(locale)
                }

                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeMultiplier.coerceIn(0f, 1f))
                }
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "VocabularyStudyTts")
            } catch (e: Throwable) {
                Log.e("TtsService", "TextToSpeech speech error: ${e.localizedMessage}")
            }
        } else {
            Log.w("TtsService", "Cannot speak, TextToSpeech is not initialized or failed to load.")
        }
    }

    fun stop() {
        try {
            if (isInitialized && tts != null) {
                tts?.stop()
            }
        } catch (e: Throwable) {
            Log.e("TtsService", "TextToSpeech stop error: ${e.localizedMessage}")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Throwable) {
            Log.e("TtsService", "TextToSpeech shutdown error: ${e.localizedMessage}")
        } finally {
            tts = null
            isInitialized = false
        }
    }
}
