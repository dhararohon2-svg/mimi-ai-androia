package com.example.core

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiResponseParserTest {
    @Test fun malformedOrEmptyCandidatesReturnSafeFallback() {
        val malformed = parseGeminiResponseBody("not-json", "bn-IN")
        assertEquals("sad", malformed.emotion)
        assertEquals("bn-IN", malformed.speechLocale)
        assertNotNull(malformed.reply)
        assertTrue(malformed.actions.isEmpty())

        val empty = parseGeminiResponseBody("""{"candidates":[]}""", "hi-IN")
        assertEquals("hi-IN", empty.speechLocale)
        assertTrue(empty.reply.isNotBlank())
    }

    @Test fun validReplyLocaleEmotionAndPrimitiveActionArgumentsAreRead() {
        val inner = """{"reply":"নমস্কার","emotion":"happy","speechLocale":"bn-IN","actions":[{"type":"set_mode","args":{"mode":"night","enabled":true}}]}"""
        val outer = """{"candidates":[{"content":{"parts":[{"text":${Gson().toJson(inner)}}]}}]}"""
        val parsed = parseGeminiResponseBody(outer, "en-US")

        assertEquals("নমস্কার", parsed.reply)
        assertEquals("happy", parsed.emotion)
        assertEquals("bn-IN", parsed.speechLocale)
        assertEquals(1, parsed.actions.size)

        val args = requireNotNull(parsed.actions[0].args) {
            "Expected action args to be present"
        }
        assertEquals("night", args["mode"] as String)
        assertEquals(true, args["enabled"] as Boolean)
    }

    @Test fun unsupportedModelLocaleFallsBackToDetectedReplyScript() {
        val inner = """{"reply":"হ্যালো","emotion":"unknown","speechLocale":"not a locale"}"""
        val outer = """{"candidates":[{"content":{"parts":[{"text":${Gson().toJson(inner)}}]}}]}"""
        val parsed = parseGeminiResponseBody(outer, "en-US")

        assertEquals("bn-IN", parsed.speechLocale)
        assertEquals("neutral", parsed.emotion)
    }
}
