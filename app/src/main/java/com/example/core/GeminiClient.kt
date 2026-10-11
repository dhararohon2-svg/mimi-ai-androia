package com.example.core

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
            - Respond in the language the user speaks: if user speaks Bengali, answer in cute, natural Bengali. If English, answer in sweet, friendly English.
            - Always be polite, affectionate, and helpful. Use cute speech quirks naturally.
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

                val jsonRoot = JsonParser.parseString(bodyStr).asJsonObject
                val candidates = jsonRoot.getAsJsonArray("candidates")
                val firstCandidate = candidates?.get(0)?.asJsonObject
                val content = firstCandidate?.getAsJsonObject("content")
                val parts = content?.getAsJsonArray("parts")
                val rawText = parts?.get(0)?.asJsonObject?.get("text")?.asString ?: "{}"

                // Parse inner JSON from text
                val parsed = JsonParser.parseString(rawText.trim()).asJsonObject
                val replyText = parsed.get("reply")?.asString ?: "I'm here!"
                val emotion = parsed.get("emotion")?.asString ?: "neutral"

                val actionsList = mutableListOf<MimiAction>()
                if (parsed.has("actions") && parsed.get("actions").isJsonArray) {
                    val actionsArray = parsed.getAsJsonArray("actions")
                    for (actElem in actionsArray) {
                        if (actElem.isJsonObject) {
                            val actObj = actElem.asJsonObject
                            val type = actObj.get("type")?.asString ?: "none"
                            val argsMap = mutableMapOf<String, Any>()
                            if (actObj.has("args") && actObj.get("args").isJsonObject) {
                                val aObj = actObj.getAsJsonObject("args")
                                for (key in aObj.keySet()) {
                                    val elem = aObj.get(key)
                                    if (elem.isJsonPrimitive) {
                                        val prim = elem.asJsonPrimitive
                                        if (prim.isBoolean) argsMap[key] = prim.asBoolean
                                        else if (prim.isNumber) argsMap[key] = prim.asNumber
                                        else argsMap[key] = prim.asString
                                    }
                                }
                            }
                            actionsList.add(MimiAction(type, argsMap))
                        }
                    }
                }

                return@withContext GeminiMimiResponse(
                    reply = replyText,
                    emotion = emotion,
                    actions = actionsList
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
