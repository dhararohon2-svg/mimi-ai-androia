package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeechLanguageTest {
    @Test fun normalizesIndianBengaliAndInfersBengaliScript() {
        assertEquals("bn-IN", SpeechLanguage.normalizeTag("bn-IN"))
        assertEquals("bn-IN", SpeechLanguage.inferTag("আজ কেমন আছো?"))
    }
    @Test fun latinScriptDoesNotGetGuessedAsBengali() {
        assertNull(SpeechLanguage.inferTag("Hello, how are you?"))
    }

    @Test fun infersHindiScriptAndStillDoesNotGuessLatinFromScriptAlone() {
        assertEquals("hi-IN", SpeechLanguage.inferTag("मुझे help चाहिए please"))
        assertNull(SpeechLanguage.inferTag("Good morning, please help"))
    }
}
