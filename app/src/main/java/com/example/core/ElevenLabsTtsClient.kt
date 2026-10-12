package com.example.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps the user's API key encrypted on-device and outside Android backup. */
internal class ElevenLabsSecretStore(context: Context) {
    private val appContext = context.applicationContext
    private val encryptedKeyFile = File(appContext.noBackupFilesDir, "elevenlabs-api-key.enc")
    private val keyAlias = "mimi.elevenlabs.api-key.v1"
    private val androidKeyStore = "AndroidKeyStore"

    fun hasApiKey(): Boolean = getApiKey() != null

    fun saveApiKey(value: String): Boolean {
        val apiKey = value.trim()
        if (apiKey.isEmpty()) return false
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
            encryptedKeyFile.parentFile?.mkdirs()
            encryptedKeyFile.writeBytes(cipher.iv + encrypted)
            true
        }.getOrDefault(false)
    }

    fun getApiKey(): String? {
        if (!encryptedKeyFile.isFile) return null
        return runCatching {
            val data = encryptedKeyFile.readBytes()
            require(data.size > IV_LENGTH) { "Invalid encrypted key" }
            val iv = data.copyOfRange(0, IV_LENGTH)
            val encrypted = data.copyOfRange(IV_LENGTH, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrElse {
            // The file can outlive a restored/lost Keystore key. Remove the unusable ciphertext.
            clearApiKey()
            null
        }
    }

    fun clearApiKey() {
        runCatching { encryptedKeyFile.delete() }
        runCatching {
            val store = KeyStore.getInstance(androidKeyStore).apply { load(null) }
            if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(androidKeyStore).apply { load(null) }
        val existing = store.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}

/** Calls the official ElevenLabs text-to-speech endpoint and returns a temporary MP3 file. */
internal class ElevenLabsTtsClient(
    context: Context,
    private val prefs: Prefs
) {
    private val appContext = context.applicationContext
    private val secrets = ElevenLabsSecretStore(appContext)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    fun hasApiKey(): Boolean = secrets.hasApiKey()
    fun saveApiKey(value: String): Boolean = secrets.saveApiKey(value)
    fun clearApiKey() = secrets.clearApiKey()

    fun isConfigured(): Boolean {
        val voiceId = prefs.elevenLabsVoiceId.trim()
        return !secrets.getApiKey().isNullOrBlank() && voiceId.matches(VOICE_ID_PATTERN)
    }

    suspend fun synthesize(text: String, languageTag: String?, emotion: String): File = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Speech text is empty" }
        require(text.length <= MAX_TEXT_CHARS) { "Text is too long for a single cloud synthesis request" }

        val apiKey = secrets.getApiKey() ?: throw IOException("ElevenLabs API key is not configured")
        val voiceId = prefs.elevenLabsVoiceId.trim()
        if (!voiceId.matches(VOICE_ID_PATTERN)) throw IOException("ElevenLabs voice ID is not configured")

        val json = JsonObject().apply {
            addProperty("text", applyEmotionTag(text, emotion))
            addProperty("model_id", MODEL_ID)
            val resolved = SpeechLanguage.resolveTag(text, languageTag, prefs.voiceLanguage)
            val language = resolved.substringBefore('-').lowercase(Locale.ROOT)
            if (language.matches(LANGUAGE_PATTERN)) addProperty("language_code", language)
            add("voice_settings", JsonObject().apply {
                addProperty("stability", 0.38)
                addProperty("similarity_boost", 0.78)
                addProperty("style", 0.45)
                addProperty("use_speaker_boost", true)
            })
        }
        val request = Request.Builder()
            .url("https://api.elevenlabs.io/v1/text-to-speech/$voiceId?output_format=mp3_44100_128")
            .header("xi-api-key", apiKey)
            .header("Accept", "audio/mpeg")
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val call = http.newCall(request)
        val coroutineContext = currentCoroutineContext()
        val cancellation = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }
        var tempFile: File? = null
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("ElevenLabs returned HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("ElevenLabs returned an empty audio response")
                val file = File.createTempFile("mimi-elevenlabs-", ".mp3", appContext.cacheDir)
                tempFile = file
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_AUDIO_BYTES) throw IOException("ElevenLabs audio response is too large")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                if (file.length() == 0L) throw IOException("ElevenLabs returned empty audio")
                if (!coroutineContext.isActive) throw CancellationException("Speech request cancelled")
                file
            }
        } catch (error: Exception) {
            tempFile?.delete()
            throw error
        } finally {
            cancellation?.dispose()
        }
    }

    private fun applyEmotionTag(text: String, emotion: String): String {
        val tag = when (emotion.trim().lowercase(Locale.ROOT)) {
            "happy", "joyful", "excited" -> "[excited]"
            "sad", "hurt", "disappointed" -> "[sad]"
            "angry", "frustrated" -> "[angry]"
            "shy", "soft" -> "[whispers]"
            "thinking", "curious", "curiosity" -> "[curious]"
            else -> ""
        }
        return if (tag.isEmpty()) text else "$tag $text"
    }

    private companion object {
        const val MODEL_ID = "eleven_v4"
        const val MAX_TEXT_CHARS = 8000
        const val MAX_AUDIO_BYTES = 24L * 1024L * 1024L
        val VOICE_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")
        val LANGUAGE_PATTERN = Regex("^[a-z]{2}$")
    }
}
