package com.example.core

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class TtsManager(
    private val context: Context,
    private val prefs: Prefs
) : TextToSpeech.OnInitListener {

    private data class PendingSpeech(
        val text: String,
        val languageTag: String?,
        val emotion: String,
        val onDone: (() -> Unit)?
    )

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val elevenLabsClient = ElevenLabsTtsClient(context.applicationContext, prefs)
    private val googleChirpClient = GoogleChirpTtsClient(context.applicationContext, prefs)
    private var nativeEngineName = "Android TTS"
    private var cloudSpeechJob: Job? = null
    private var cloudPlayer: MediaPlayer? = null
    private var cloudAudioFile: File? = null

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
    private val pendingSpeechQueue = mutableListOf<PendingSpeech>()

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
                nativeEngineName = t.defaultEngine ?: "Android TTS"
                if (cloudPlayer == null) _currentEngineName.value = nativeEngineName
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
                    val pending = pendingSpeechQueue.removeAt(0)
                    speak(
                        pending.text,
                        onDone = pending.onDone,
                        languageTag = pending.languageTag,
                        emotion = pending.emotion
                    )
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

        val targetLocale = Locale.forLanguageTag(prefs.voiceLanguage)
        applyLocaleSafely(t, targetLocale)
        applyProsody(t, "neutral")
    }

    private fun applyLocaleSafely(t: TextToSpeech, targetLocale: Locale) {
        try {
            val candidates = listOf(
                targetLocale,
                Locale(targetLocale.language),
                Locale.forLanguageTag(prefs.voiceLanguage),
                Locale.getDefault()
            ).distinctBy { it.toLanguageTag() }
            val supported = candidates.firstOrNull {
                it.language.isNotBlank() && t.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE
            }
            if (supported != null) t.language = supported

            // Prefer a high-quality locally installed voice for this language. If none exists,
            // leave the engine's supported-language fallback in place rather than forcing English.
            val voiceLocale = supported ?: targetLocale
            val bestVoice = t.voices
                ?.asSequence()
                ?.filter { it.locale.language.equals(voiceLocale.language, ignoreCase = true) }
                ?.filterNot { it.isNetworkConnectionRequired }
                ?.sortedWith(
                    compareByDescending<Voice> { it.locale.country.equals(voiceLocale.country, ignoreCase = true) }
                        .thenByDescending { it.quality }
                        .thenBy { it.latency }
                        .thenBy { it.name }
                )
                ?.firstOrNull()
            if (bestVoice != null) t.voice = bestVoice
        } catch (ignored: Exception) {}
    }

    private fun applyProsody(t: TextToSpeech, emotion: String) {
        val (pitchFactor, speedFactor) = when (emotion.lowercase()) {
            "happy" -> 1.02f to 1.03f
            "sad" -> 0.98f to 0.94f
            "shy" -> 1.01f to 0.97f
            "angry" -> 0.99f to 1.01f
            else -> 1f to 1f
        }
        t.setPitch((prefs.voicePitch * pitchFactor).coerceIn(0.8f, 1.8f))
        t.setSpeechRate((prefs.voiceSpeed * speedFactor).coerceIn(0.6f, 1.6f))
    }

    fun speak(
        text: String,
        onDone: (() -> Unit)? = null,
        languageTag: String? = null,
        emotion: String = "neutral"
    ) {
        val cleanedText = cleanTextForSpeech(text)
        if (cleanedText.isBlank()) {
            onDone?.invoke()
            return
        }

        // A new request replaces any in-flight speech, matching QUEUE_FLUSH behavior.
        cloudSpeechJob?.cancel()
        cloudSpeechJob = null
        releaseCloudPlayback()
        tts?.stop()
        _isSpeaking.value = false
        stopLipSync()
        utteranceCallbacks.clear()

        val providers = ttsProviderPlan(
            elevenLabsEnabled = prefs.isElevenLabsEnabled,
            elevenLabsConfigured = elevenLabsClient.isConfigured(),
            googleChirpEnabled = prefs.isGoogleChirpEnabled,
            googleChirpConfigured = googleChirpClient.isConfigured()
        )
        if (providers.isEmpty()) {
            _currentEngineName.value = nativeEngineName
            speakWithAndroid(cleanedText, languageTag, emotion, onDone)
        } else {
            speakWithCloudProviders(cleanedText, languageTag, emotion, onDone, providers, 0)
        }
    }

    private fun speakWithAndroid(
        cleanedText: String,
        languageTag: String?,
        emotion: String,
        onDone: (() -> Unit)?
    ) {
        if (!isInitialized) {
            synchronized(pendingSpeechQueue) {
                pendingSpeechQueue.add(PendingSpeech(cleanedText, languageTag, emotion, onDone))
            }
            return
        }

        val t = tts ?: run {
            onDone?.invoke()
            return
        }
        _currentEngineName.value = nativeEngineName
        val resolvedTag = SpeechLanguage.resolveTag(cleanedText, languageTag, prefs.voiceLanguage)
        applyLocaleSafely(t, Locale.forLanguageTag(resolvedTag))
        applyProsody(t, emotion)

        val utteranceId = "mimi_tts_" + System.currentTimeMillis()
        if (onDone != null) utteranceCallbacks[utteranceId] = onDone
        t.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun speakWithCloudProviders(
        cleanedText: String, languageTag: String?, emotion: String, onDone: (() -> Unit)?,
        providers: List<TtsProvider>, providerIndex: Int
    ) {
        if (providerIndex >= providers.size) {
            _currentEngineName.value = nativeEngineName
            speakWithAndroid(cleanedText, languageTag, emotion, onDone)
            return
        }
        val provider = providers[providerIndex]
        val label = when (provider) {
            TtsProvider.ELEVENLABS -> "ElevenLabs"
            TtsProvider.GOOGLE_CHIRP -> "Google Chirp 3 HD"
        }
        _currentEngineName.value = label
        cloudSpeechJob = scope.launch {
            var file: File? = null
            try {
                file = when (provider) {
                    TtsProvider.ELEVENLABS -> elevenLabsClient.synthesize(cleanedText, languageTag, emotion)
                    TtsProvider.GOOGLE_CHIRP -> googleChirpClient.synthesize(cleanedText, languageTag, emotion)
                }
                if (!isActive) { file.delete(); return@launch }
                startCloudPlayback(file, label, onDone) {
                    speakWithCloudProviders(cleanedText, languageTag, emotion, onDone, providers, providerIndex + 1)
                }
            } catch (cancelled: CancellationException) {
                file?.delete(); throw cancelled
            } catch (_: Exception) {
                file?.delete()
                if (isActive) speakWithCloudProviders(cleanedText, languageTag, emotion, onDone, providers, providerIndex + 1)
            }
        }
    }

    private fun startCloudPlayback(audioFile: File, providerName: String, onDone: (() -> Unit)?, onFailure: () -> Unit) {
        val player = MediaPlayer(); cloudPlayer = player; cloudAudioFile = audioFile
        try {
            player.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            player.setDataSource(audioFile.absolutePath)
            player.setOnPreparedListener { ready ->
                if (cloudPlayer === ready) {
                    _currentEngineName.value = providerName; _isSpeaking.value = true; startLipSync(); ready.start()
                }
            }
            player.setOnCompletionListener { done ->
                if (cloudPlayer === done) {
                    releaseCloudPlayback(done); _isSpeaking.value = false; stopLipSync(); onDone?.invoke()
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                if (cloudPlayer === failed) {
                    releaseCloudPlayback(failed); _isSpeaking.value = false; stopLipSync(); onFailure()
                }
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            releaseCloudPlayback(player); _isSpeaking.value = false; stopLipSync(); onFailure()
        }
    }

    private fun releaseCloudPlayback(expectedPlayer: MediaPlayer? = null) {
        val player = cloudPlayer
        if (expectedPlayer != null && player !== expectedPlayer) return
        cloudPlayer = null
        if (player != null) {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            runCatching { player.release() }
        }
        cloudAudioFile?.let { runCatching { it.delete() } }
        cloudAudioFile = null
    }

    fun hasGoogleChirpApiKey(): Boolean = googleChirpClient.hasApiKey()

    fun saveGoogleChirpApiKey(value: String): Boolean = googleChirpClient.saveApiKey(value)

    fun clearGoogleChirpApiKey() {
        googleChirpClient.clearApiKey()
        prefs.isGoogleChirpEnabled = false
        _isSpeaking.value = false
        stopLipSync()
    }

    fun hasElevenLabsApiKey(): Boolean = elevenLabsClient.hasApiKey()

    fun saveElevenLabsApiKey(value: String): Boolean = elevenLabsClient.saveApiKey(value)

    fun clearElevenLabsApiKey() {
        elevenLabsClient.clearApiKey()
        prefs.isElevenLabsEnabled = false
        _isSpeaking.value = false
        stopLipSync()
    }

    fun stop() {
        cloudSpeechJob?.cancel()
        cloudSpeechJob = null
        releaseCloudPlayback()
        tts?.stop()
        _currentEngineName.value = nativeEngineName
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
        scope.cancel()
    }
}
