package com.example.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Safely extracts Gemini's candidates envelope and JSON payload returned as candidate text. */
internal fun parseGeminiResponseBody(
    body: String,
    fallbackSpeechLocale: String? = null
): GeminiMimiResponse {
    val fallbackLocale = SpeechLanguage.normalizeTag(fallbackSpeechLocale) ?: "en-US"
    fun fallback() = GeminiMimiResponse(
        reply = "I couldn't get a clear answer just now. Please try again.",
        emotion = "sad",
        speechLocale = fallbackLocale
    )
    val root = runCatching { JsonParser.parseString(body) }.getOrNull()
        ?.takeIf { it.isJsonObject }?.asJsonObject ?: return fallback()
    val candidates = root.get("candidates")?.takeIf { it.isJsonArray }?.asJsonArray ?: return fallback()
    val candidate = candidates.firstOrNull { it.isJsonObject }?.asJsonObject ?: return fallback()
    val content = candidate.get("content")?.takeIf { it.isJsonObject }?.asJsonObject ?: return fallback()
    val parts = content.get("parts")?.takeIf { it.isJsonArray }?.asJsonArray ?: return fallback()
    val rawText = parts.firstNotNullOfOrNull partLoop@ { part ->
        if (!part.isJsonObject) return@partLoop null
        part.asJsonObject.stringValue("text")
    } ?: return fallback()
    val inner = runCatching { JsonParser.parseString(rawText.trim()) }.getOrNull()
        ?.takeIf { it.isJsonObject }?.asJsonObject ?: return fallback()
    val reply = inner.stringValue("reply")?.trim()?.takeIf { it.isNotEmpty() } ?: return fallback()
    val allowedEmotions = setOf("neutral", "happy", "sad", "shy", "angry", "thinking")
    val emotion = inner.stringValue("emotion")?.lowercase()
        ?.takeIf { it in allowedEmotions } ?: "neutral"
    val locale = SpeechLanguage.normalizeTag(inner.stringValue("speechLocale"))
        ?: SpeechLanguage.inferTag(reply) ?: fallbackLocale
    val actions = mutableListOf<MimiAction>()
    val actionArray = inner.get("actions")?.takeIf { it.isJsonArray }?.asJsonArray
    actionArray?.forEach actionLoop@ { element ->
        if (!element.isJsonObject) return@actionLoop
        val obj = element.asJsonObject
        val type = obj.stringValue("type")?.takeIf { it.isNotBlank() } ?: "none"
        val args = mutableMapOf<String, Any>()
        val argsObj = obj.get("args")?.takeIf { it.isJsonObject }?.asJsonObject
        argsObj?.entrySet()?.forEach argsLoop@ { entry ->
            val value = entry.value
            if (!value.isJsonPrimitive) return@argsLoop
            val primitive = value.asJsonPrimitive
            when {
                primitive.isBoolean -> args[entry.key] = primitive.asBoolean
                primitive.isNumber -> args[entry.key] = primitive.asNumber
                primitive.isString -> args[entry.key] = primitive.asString
            }
        }
        actions += MimiAction(type, args)
    }
    return GeminiMimiResponse(reply = reply, emotion = emotion, actions = actions, speechLocale = locale)
}

private fun JsonObject.stringValue(key: String): String? {
    val value = get(key) ?: return null
    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
    return value.asString
}
