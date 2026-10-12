package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsProviderPlanTest {
    @Test fun cloudOrderIsElevenLabsThenGoogle() {
        assertEquals(listOf(TtsProvider.ELEVENLABS, TtsProvider.GOOGLE_CHIRP), ttsProviderPlan(true, true, true, true))
    }
    @Test fun disabledOrUnconfiguredProvidersAreSkipped() {
        assertEquals(listOf(TtsProvider.GOOGLE_CHIRP), ttsProviderPlan(true, false, true, true))
        assertEquals(emptyList<TtsProvider>(), ttsProviderPlan(false, true, false, true))
    }
}
