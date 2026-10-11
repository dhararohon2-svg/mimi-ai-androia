package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class TtsManager(
    private val context: Context,
    private val prefs: Prefs
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Lip sync mouth openness (0.0f closed to 1.0f wide open)
    private val _mouthOpenLevel = MutableStateFlow(0f)
    val mouthOpenLevel: StateFlow<Float> = _mouthOpenLevel.asStateFlow()

    // Active engine info for display in settings
    private val _currentEngineName = MutableStateFlow("Initializing...")
    val currentEngineName: StateFlow<String> = _currentEngineName.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main)
    private var lipSyncJob: Job? = null
    private val handler = Handler(Looper.getMainLooper())

    // Pending speech queue for requests made before initialization completes
    private val pendingSpeechQueue = mutableListOf<Pair<String, (() -> Unit)?>>()

    // Utterance callback map
    private val utteranceCallbacks = ConcurrentHashMap<String, () -> Unit>()

    init {
        initTtsEngine()
    }

    private fun initTtsEngine() {
        val googleTtsPackage = "com.google.android.tts"
        try {
            // Check if Google TTS engine is installed
            val pm = context.packageManager
            val info = pm.getPackageInfo(googleTtsPackage, 0)
            if (info != null) {
                tts = TextToSpeech(context.applicationContext, this, googleTtsPackage)
            } else {
                tts = TextToSpeech(context.applicationContext, this)
            }
        } catch (e: Exception) {
            // Fallback to default engine
            tts = TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupVoiceAndLanguage()
            setupAudioAttributes()

            tts?.let { t ->
                _currentEngineName.value = t.defaultEngine ?: "System Default"
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    handler.post {
                        _isSpeaking.value = true
                        startLipSync()
                    }
                }

                override fun onDone(utteranceId: String?) {
                    handler.post {
                        _isSpeaking.value = false
                        stopLipSync()
                        if (utteranceId != null) {
                            utteranceCallbacks.remove(utteranceId)?.invoke()
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    handler.post {
                        _isSpeaking.value = false
                        stopLipSync()
                        if (utteranceId != null) {
                            utteranceCallbacks.remove(utteranceId)
                        }
                    }
                }
            })

            // Process any speech requests queued while engine was initializing
            synchronized(pendingSpeechQueue) {
                while (pendingSpeechQueue.isNotEmpty()) {
                    val (text, callback) = pendingSpeechQueue.removeAt(0)
                    speak(text, callback)
                }
            }
        } else {
            _currentEngineName.value = "Failed to initialize TTS"
        }
    }

    private fun setupAudioAttributes() {
        val t = tts ?: return
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .build()
            t.setAudioAttributes(audioAttributes)
        } catch (ignored: Exception) {}
    }

    fun setupVoiceAndLanguage() {
        val t = tts ?: return
        if (!isInitialized) return

        val langCode = prefs.voiceLanguage
        val targetLocale = if (langCode.startsWith("bn")) {
            Locale("bn", "BD")
        } else {
            Locale.US
        }

        applyLocaleSafely(t, targetLocale)
        applyFemaleVoice(t, targetLocale)

        t.setPitch(prefs.voicePitch)
        t.setSpeechRate(prefs.voiceSpeed)
    }

    private fun applyLocaleSafely(t: TextToSpeech, targetLocale: Locale) {
        val avail = t.isLanguageAvailable(targetLocale)
        if (avail >= TextToSpeech.LANG_AVAILABLE) {
            t.language = targetLocale
        } else {
            // If specific country variant not available, try language only
            val langOnly = Locale(targetLocale.language)
            val langAvail = t.isLanguageAvailable(langOnly)
            if (langAvail >= TextToSpeech.LANG_AVAILABLE) {
                t.language = langOnly
            } else {
                t.language = Locale.US
            }
        }
    }

    private fun applyFemaleVoice(t: TextToSpeech, locale: Locale) {
        try {
            val voices = t.voices ?: return
            // Prefer the requested language and installed high-quality voices. Voice names do
            // not reliably encode gender, so do not assume that a voice is female by its name.
            val bestVoice = voices
                .asSequence()
                .filter { it.locale.language == locale.language }
                .filterNot { it.isNetworkConnectionRequired }
                .sortedWith(
                    compareByDescending<Voice> { it.quality }
                        .thenBy { it.latency }
                        .thenBy { it.name }
                )
                .firstOrNull()
            if (bestVoice != null) t.voice = bestVoice
        } catch (ignored: Exception) {}
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!prefs.isVocalResponseEnabled) {
            onDone?.invoke()
            return
        }

        val cleanedText = cleanTextForSpeech(text)
        if (cleanedText.isBlank()) {
            onDone?.invoke()
            return
        }

        if (!isInitialized) {
            synchronized(pendingSpeechQueue) {
                pendingSpeechQueue.add(Pair(cleanedText, onDone))
            }
            return
        }

        val t = tts ?: run {
            onDone?.invoke()
            return
        }

        // Auto detect if text contains Bengali characters
        val containsBengali = cleanedText.any { it.code in 0x0980..0x09FF }
        val targetLocale = if (containsBengali) Locale("bn", "BD") else Locale.US

        applyLocaleSafely(t, targetLocale)
        applyFemaleVoice(t, targetLocale)

        t.setPitch(prefs.voicePitch)
        t.setSpeechRate(prefs.voiceSpeed)

        val utteranceId = "mimi_tts_" + System.currentTimeMillis()
        if (onDone != null) {
            utteranceCallbacks[utteranceId] = onDone
        }

        t.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        stopLipSync()
        utteranceCallbacks.clear()
    }

    private fun cleanTextForSpeech(text: String): String {
        return text
            // Strip markdown asterisks, hashes, backticks, brackets
            .replace(Regex("[*#_`~>|\\[\\]()]"), " ")
            // Convert percent symbol to text for natural reading
            .replace("%", " percent")
            // Remove common emoji / symbols that TTS engines stumble on
            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun startLipSync() {
        lipSyncJob?.cancel()
        lipSyncJob = scope.launch {
            while (isActive && _isSpeaking.value) {
                val targetOpen = Random.nextFloat() * 0.9f + 0.1f
                _mouthOpenLevel.value = targetOpen
                delay(80)
                _mouthOpenLevel.value = targetOpen * 0.35f
                delay(60)
            }
            _mouthOpenLevel.value = 0f
        }
    }

    private fun stopLipSync() {
        lipSyncJob?.cancel()
        lipSyncJob = null
        _mouthOpenLevel.value = 0f
    }

    fun shutdown() {
        stop()
        synchronized(pendingSpeechQueue) { pendingSpeechQueue.clear() }
        utteranceCallbacks.clear()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
