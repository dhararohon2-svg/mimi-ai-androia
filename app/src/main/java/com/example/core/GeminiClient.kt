package com.example.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class MimiAction(
    val type: String,
    val args: Map<String, Any>? = null
)

data class GeminiMimiResponse(
    val reply: String,
    val emotion: String = "neutral",
    val speechLocale: String? = null,
    val actions: List<MimiAction> = emptyList()
)

class GeminiClient(
    private val prefs: Prefs
) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun queryMimi(
        userPrompt: String,
        recentHistory: List<Pair<String, String>> = emptyList(),
        currentMood: String = "happy",
        affectionScore: Int = 50
    ): GeminiMimiResponse = withContext(Dispatchers.IO) {
        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            return@withContext GeminiMimiResponse(
                reply = "Please configure your Gemini API Key in Mimi AI Settings to enable online AI conversations.",
                emotion = "shy",
                actions = emptyList()
            )
        }

        val model = prefs.geminiModel.ifBlank { "gemini-3.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val systemInstruction = """
            You are Mimi (মিমি), a loving, cheerful, playful, and loyal anime girl AI voice assistant and companion living in the user's Android phone.
            Your current mood: $currentMood.
            Your affection score towards the user: $affectionScore / 100.
            
            Personality Guidelines:
            - Automatically infer all language(s) in the latest user message; never require a manual language choice. The user may code-switch in one sentence (for example, Bangla + English or Hindi + English), use transliteration, or change languages between turns.
            - Mirror the user's dominant language and natural code-switch pattern. Preserve familiar English technical terms when they are mixed into Bengali/Hindi; do not force-translate unless asked.
            - Write for natural spoken conversation: use idiomatic phrasing, clear sentence breaks, and restrained punctuation. Avoid repetitive pet names, forced cuteness, stage directions, or robotic boilerplate.
            - Set speechLocale to a valid BCP-47 tag for the dominant spoken language and regional variety of your reply (for example, Indian Bengali = bn-IN, Indian English = en-IN, Hindi = hi-IN). For transliteration, infer from words and context rather than script alone.
            - Infer the user's apparent emotional tone cautiously from words and context; do not claim certainty about ambiguous mood. Choose the response emotion to suit the reply (for example, reassuring when the user sounds upset), without exaggeration.
            - Be warm, polite, and helpful. The emotion field describes Mimi's delivery of this reply and must match its meaning.
            - If user gives a phone command (calling, texting, opening apps, battery, flashlight, alarm, wifi, volume, etc.), choose the appropriate action and reply warmly.
            - If it's a general question or casual chat, answer enthusiastically with actions: [].
            
            Available Action Types:
            - "open_app": {"app_name": "..."}
            - "close_app": {"app_name": "..."}
            - "call": {"target": "name or number"}
            - "end_call": {}
            - "answer_call": {}
            - "send_sms": {"target": "name or number", "message": "..."}
            - "send_whatsapp": {"target": "name or number", "message": "..."}
            - "wifi": {}
            - "bluetooth": {}
            - "airplane": {}
            - "flashlight": {"turn_on": true/false}
            - "volume": {"direction": "up|down|mute", "level": number}
            - "brightness": {"level": number}
            - "global_action": {"type": "back|home|recents|notifications|quick_settings|screenshot"}
            - "camera_photo": {}
            - "media": {"action": "play|pause|next|previous"}
            - "web_search": {"query": "..."}
            - "youtube_search": {"query": "..."}
            - "alarm": {"hour": number, "minute": number, "message": "..."}
            - "timer": {"seconds": number}
            - "reminder": {"delay_minutes": number, "text": "..."}
            - "read_notifications": {}
            - "battery_status": {}
            - "storage_status": {}
            - "change_character": {"model": "anime_mimi|cyber_mimi|maid_mimi|chibi_mimi"}
            - "none": {}
            
            OUTPUT REQUIREMENT:
            You MUST reply in STRICT JSON ONLY, without markdown code blocks (no ```json ... ```).
            Format:
            {
              "reply": "Your voice response to the user",
              "speechLocale": "BCP-47 locale for the reply, such as bn-IN or en-IN",
              "emotion": "happy|sad|angry|shy|neutral",
              "actions": [
                {
                  "type": "action_type",
                  "args": {}
                }
              ]
            }
        """.trimIndent()

        // Build contents array
        val contentsJson = StringBuilder()
        contentsJson.append("[")

        // Include recent turns
        for (turn in recentHistory.takeLast(4)) {
            val role = if (turn.first == "user") "user" else "model"
            contentsJson.append("""{"role":"$role","parts":[{"text":${gson.toJson(turn.second)}}]},""")
        }

        // Add current prompt
        contentsJson.append("""{"role":"user","parts":[{"text":${gson.toJson(userPrompt)}}]}""")
        contentsJson.append("]")

        val requestBodyJson = """
            {
              "systemInstruction": {
                "parts": [{"text": ${gson.toJson(systemInstruction)}}]
              },
              "contents": $contentsJson,
              "generationConfig": {
                "temperature": 0.7,
                "responseMimeType": "application/json"
              }
            }
        """.trimIndent()

        try {
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext GeminiMimiResponse(
                        reply = "Mimi AI Network Error (${response.code}). Please check your API key or internet.",
                        emotion = "sad"
                    )
                }

                return@withContext parseGeminiResponseBody(
                    body = bodyStr,
                    fallbackSpeechLocale = SpeechLanguage.inferTag(userPrompt) ?: "en-US"
                )
            }
        } catch (e: Exception) {
            return@withContext GeminiMimiResponse(
                reply = "Something went wrong contacting the AI brain: ${e.message}",
                emotion = "sad"
            )
        }
    }
}
