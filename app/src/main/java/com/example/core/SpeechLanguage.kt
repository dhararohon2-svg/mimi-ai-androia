package com.example.core

import java.util.Locale

/**
 * Best-effort local script detection. For Latin-script languages the AI response's BCP-47
 * speechLocale is authoritative; Unicode script alone cannot distinguish English, Spanish, etc.
 */
internal object SpeechLanguage {
    fun normalizeTag(tag: String?): String? {
        val value = tag?.trim()?.takeIf {
            it.matches(Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$"))
        } ?: return null
        val locale = Locale.forLanguageTag(value)
        return locale.toLanguageTag().takeIf { locale.language.isNotBlank() && locale.language != "und" }
    }

    fun inferTag(text: String): String? {
        val scores = mutableMapOf<String, Int>()
        text.forEach { character ->
            val code = character.code
            val tag = when (code) {
                in 0x0980..0x09FF -> "bn-IN"
                in 0x0900..0x097F -> "hi-IN"
                in 0x0A00..0x0A7F -> "pa-IN"
                in 0x0A80..0x0AFF -> "gu-IN"
                in 0x0B00..0x0B7F -> "or-IN"
                in 0x0B80..0x0BFF -> "ta-IN"
                in 0x0C00..0x0C7F -> "te-IN"
                in 0x0C80..0x0CFF -> "kn-IN"
                in 0x0D00..0x0D7F -> "ml-IN"
                in 0x0600..0x06FF -> "ar"
                in 0x0590..0x05FF -> "he"
                in 0x0E00..0x0E7F -> "th-TH"
                in 0x0400..0x052F -> "ru"
                in 0x3040..0x30FF -> "ja-JP"
                in 0xAC00..0xD7AF -> "ko-KR"
                in 0x4E00..0x9FFF -> "zh"
                in 0x0530..0x058F -> "hy"
                in 0x10A0..0x10FF -> "ka"
                in 0x1000..0x109F -> "my"
                else -> null
            }
            if (tag != null) scores[tag] = (scores[tag] ?: 0) + 1
        }
        return scores.maxByOrNull { it.value }?.key
    }

    fun resolveTag(text: String, modelTag: String?, preferredTag: String?): String {
        return normalizeTag(modelTag)
            ?: inferTag(text)
            ?: normalizeTag(preferredTag)
            ?: Locale.getDefault().toLanguageTag().takeIf { it != "und" }
            ?: "en-US"
    }
}
