package com.example.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.security.KeyStore
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Direct, user-keyed Google Cloud Text-to-Speech integration; no app backend is required. */
internal class GoogleChirpTtsClient(context: Context, private val prefs: Prefs) {
    private val app = context.applicationContext
    private val secrets = GoogleChirpSecretStore(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS).callTimeout(65, TimeUnit.SECONDS).build()
    private data class Voice(val name: String, val languageCode: String)
    private data class Cached(val voice: Voice, val time: Long)
    private val voiceCache = ConcurrentHashMap<String, Cached>()

    fun hasApiKey() = secrets.hasApiKey()
    fun saveApiKey(value: String) = secrets.saveApiKey(value)
    fun clearApiKey() = secrets.clearApiKey()
    fun isConfigured() = !secrets.getApiKey().isNullOrBlank()

    suspend fun synthesize(text: String, languageTag: String?, emotion: String): File = withContext(Dispatchers.IO) {
        require(text.isNotBlank()) { "Speech text is empty" }
        require(text.length <= MAX_TEXT_CHARS) { "Text is too long for one Google Cloud TTS request" }
        val key = secrets.getApiKey() ?: throw IOException("Google Cloud TTS API key is not configured")
        val locale = googleLocale(SpeechLanguage.resolveTag(text, languageTag, prefs.voiceLanguage))
        val voice = resolveVoice(key, locale)
        val payload = JSONObject()
            .put("input", JSONObject().put("text", text))
            .put("voice", JSONObject().put("languageCode", voice.languageCode).put("name", voice.name))
            .put("audioConfig", JSONObject().put("audioEncoding", "MP3")
                .put("speakingRate", rateFor(emotion)))
        val request = Request.Builder()
            .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=${enc(key)}")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val audio = http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("Google Cloud synthesis failed (HTTP ${response.code}); check API enablement, key, and billing.")
            JSONObject(body).optString("audioContent").takeIf { it.isNotBlank() }
                ?: throw IOException("Google Cloud returned no audioContent")
        }
        val bytes = try { Base64.decode(audio, Base64.DEFAULT) }
        catch (e: IllegalArgumentException) { throw IOException("Google Cloud returned invalid audio", e) }
        if (bytes.isEmpty()) throw IOException("Google Cloud returned empty audio")
        File.createTempFile("mimi_chirp_", ".mp3", app.cacheDir).also { f ->
            try { FileOutputStream(f).use { it.write(bytes) } }
            catch (e: Exception) { f.delete(); throw e }
        }
    }

    private fun resolveVoice(key: String, locale: String): Voice {
        voiceCache[locale]?.let { if (System.currentTimeMillis() - it.time < VOICE_CACHE_MS) return it.voice }
        for (query in listOf(locale, locale.substringBefore('-')).distinct()) {
            val url = "https://texttospeech.googleapis.com/v1/voices?languageCode=${enc(query)}&key=${enc(key)}"
            val data = http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("Google Cloud voice lookup failed (HTTP ${response.code}); check API enablement, key, and billing.")
                JSONObject(body)
            }
            val voices = data.optJSONArray("voices") ?: continue
            val candidates = mutableListOf<Pair<Voice, String>>()
            for (i in 0 until voices.length()) {
                val item = voices.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (!name.contains("Chirp3-HD", ignoreCase = true)) continue
                val codes = item.optJSONArray("languageCodes") ?: continue
                for (j in 0 until codes.length()) {
                    val code = normalized(codes.optString(j)) ?: continue
                    if (code.equals(locale, true) || Locale.forLanguageTag(code).language == Locale.forLanguageTag(locale).language) {
                        candidates += Voice(name, code) to item.optString("ssmlGender")
                    }
                }
            }
            val selected = candidates.sortedWith(compareBy<Pair<Voice, String>>(
                { if (it.first.languageCode.equals(locale, true)) 0 else 1 },
                { if (it.second.equals("FEMALE", true)) 0 else 1 },
                { it.first.name }
            )).firstOrNull()?.first
            if (selected != null) {
                voiceCache[locale] = Cached(selected, System.currentTimeMillis())
                return selected
            }
        }
        throw IOException("No Google Chirp 3 HD voice is available for $locale")
    }

    private fun googleLocale(tag: String): String {
        val normalized = normalized(tag) ?: Locale.getDefault().toLanguageTag()
        return when (Locale.forLanguageTag(normalized).language.lowercase(Locale.ROOT)) {
            "bn" -> "bn-IN"; "hi" -> "hi-IN"; "pa" -> "pa-IN"; "gu" -> "gu-IN"
            "or" -> "or-IN"; "ta" -> "ta-IN"; "te" -> "te-IN"; "kn" -> "kn-IN"; "ml" -> "ml-IN"
            else -> normalized
        }
    }
    private fun normalized(tag: String?): String? {
        val locale = tag?.let { Locale.forLanguageTag(it) } ?: return null
        return locale.toLanguageTag().takeIf { locale.language.isNotBlank() && locale.language != "und" }
    }
    // Chirp 3 HD pace adjustment is only a subtle cue; it cannot guarantee expressive emotion.
    private fun rateFor(emotion: String) = when (emotion.lowercase(Locale.ROOT)) {
        "happy", "excited" -> 1.04; "sad" -> 0.94; "thinking" -> 0.97; "angry" -> 1.02; else -> 1.0
    }
    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
    private companion object { const val MAX_TEXT_CHARS = 4500; const val VOICE_CACHE_MS = 6 * 60 * 60 * 1000L }
}

/** AES-GCM at-rest storage. A mobile-app API key can still be extracted from a compromised device. */
private class GoogleChirpSecretStore(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, "google_chirp_tts_api_key.bin")
    fun hasApiKey() = !getApiKey().isNullOrBlank()
    fun saveApiKey(value: String) = runCatching {
        val keyText = value.trim(); require(keyText.isNotEmpty())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keyMaterial())
        val encrypted = cipher.doFinal(keyText.toByteArray(Charsets.UTF_8))
        file.parentFile?.mkdirs(); file.writeBytes(cipher.iv + encrypted)
    }.isSuccess
    fun getApiKey(): String? {
        if (!file.exists()) return null
        return runCatching {
            val data = file.readBytes(); require(data.size > IV_BYTES)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, keyMaterial(), GCMParameterSpec(TAG_BITS, data.copyOfRange(0, IV_BYTES)))
            String(cipher.doFinal(data.copyOfRange(IV_BYTES, data.size)), Charsets.UTF_8).takeIf { it.isNotBlank() }
        }.getOrElse { clearApiKey(); null }
    }
    fun clearApiKey() {
        runCatching { file.delete() }
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }
    private fun keyMaterial(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    private companion object { const val KEYSTORE="AndroidKeyStore"; const val ALIAS="mimi_google_chirp_tts_api_key"; const val IV_BYTES=12; const val TAG_BITS=128 }
}
