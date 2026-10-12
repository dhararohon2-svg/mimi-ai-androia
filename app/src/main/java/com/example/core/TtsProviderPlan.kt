package com.example.core

/** Cloud order; Android TTS is always the final local fallback after these providers fail. */
internal enum class TtsProvider { ELEVENLABS, GOOGLE_CHIRP }

internal fun ttsProviderPlan(
    elevenLabsEnabled: Boolean,
    elevenLabsConfigured: Boolean,
    googleChirpEnabled: Boolean,
    googleChirpConfigured: Boolean
): List<TtsProvider> = listOfNotNull(
    TtsProvider.ELEVENLABS.takeIf { elevenLabsEnabled && elevenLabsConfigured },
    TtsProvider.GOOGLE_CHIRP.takeIf { googleChirpEnabled && googleChirpConfigured }
)
