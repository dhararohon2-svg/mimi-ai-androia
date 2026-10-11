package com.example.core

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.CommandHistoryEntity
import com.example.phone.PhoneController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CommandResult(
    val replyText: String,
    val emotion: String,
    val actionType: String,
    val success: Boolean
)

class CommandRouter(
    private val context: Context,
    private val phoneController: PhoneController,
    private val moodEngine: MoodEngine,
    private val ttsManager: TtsManager,
    private val geminiClient: GeminiClient,
    private val database: AppDatabase
) {

    suspend fun processCommand(
        input: String,
        source: String = "voice",
        onProgress: ((String) -> Unit)? = null
    ): CommandResult = withContext(Dispatchers.IO) {
        val clean = input.trim()
        if (clean.isBlank()) {
            return@withContext CommandResult("I didn't catch that. Please speak again!", "neutral", "none", false)
        }

        // Save user message to chat DB
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(sender = "user", message = clean)
        )

        // 1. Check Offline Rule-Based Match
        val offlineAction = tryOfflineParse(clean)

        val result: CommandResult = if (offlineAction != null) {
            executeAction(offlineAction, clean, isOffline = true)
        } else {
            // 2. Query Gemini AI with context
            onProgress?.invoke("Thinking with AI...")
            val recentTurns = database.chatMessageDao().getRecentMessages(6).reversed().map {
                Pair(it.sender, it.message)
            }
            val currentState = moodEngine.characterState.value
            val aiResponse = geminiClient.queryMimi(
                userPrompt = clean,
                recentHistory = recentTurns,
                currentMood = currentState.mood,
                affectionScore = currentState.affection
            )

            moodEngine.setMood(aiResponse.emotion)

            if (aiResponse.actions.isNotEmpty()) {
                var allSuccess = true
                var executedType = "none"
                for (action in aiResponse.actions) {
                    executedType = action.type
                    val actRes = runPhoneAction(action.type, action.args ?: emptyMap())
                    if (!actRes.success) allSuccess = false
                }
                CommandResult(
                    replyText = aiResponse.reply,
                    emotion = aiResponse.emotion,
                    actionType = executedType,
                    success = allSuccess
                )
            } else {
                CommandResult(
                    replyText = aiResponse.reply,
                    emotion = aiResponse.emotion,
                    actionType = "chat",
                    success = true
                )
            }
        }

        // 3. Save Mimi's message to chat DB
        database.chatMessageDao().insertMessage(
            ChatMessageEntity(
                sender = "mimi",
                message = result.replyText,
                emotion = result.emotion
            )
        )

        // 4. Record to Command History DB
        database.commandHistoryDao().insertHistory(
            CommandHistoryEntity(
                commandText = clean,
                source = source,
                actionType = result.actionType,
                success = result.success,
                resultMessage = result.replyText
            )
        )

        // 5. Update Affection
        if (result.success) {
            moodEngine.addAffection(1)
        }

        // 6. Speak aloud through TTS
        withContext(Dispatchers.Main) {
            ttsManager.speak(result.replyText)
        }

        return@withContext result
    }

    private fun tryOfflineParse(input: String): MimiAction? {
        val lower = input.lowercase()

        // Battery
        if (lower.contains("ব্যাটারি") || lower.contains("battery")) {
            return MimiAction("battery_status")
        }

        // Storage / RAM
        if (lower.contains("স্টোরেজ") || lower.contains("storage") || lower.contains("মেমোরি") || lower.contains("ram")) {
            return MimiAction("storage_status")
        }

        // Flashlight ON
        if (lower.contains("ফ্ল্যাশলাইট জ্বালাও") || lower.contains("ফ্ল্যাশ অন") || lower.contains("টর্চ জ্বালাও") ||
            lower.contains("turn on flashlight") || lower.contains("flashlight on") || lower.contains("torch on")) {
            return MimiAction("flashlight", mapOf("turn_on" to true))
        }

        // Flashlight OFF
        if (lower.contains("ফ্ল্যাশলাইট নিভাও") || lower.contains("ফ্ল্যাশলাইট বন্ধ") || lower.contains("টর্চ বন্ধ") ||
            lower.contains("turn off flashlight") || lower.contains("flashlight off") || lower.contains("torch off")) {
            return MimiAction("flashlight", mapOf("turn_on" to false))
        }

        // Wi-Fi
        if (lower.contains("ওয়াইফাই") || lower.contains("wifi") || lower.contains("wi-fi")) {
            return MimiAction("wifi")
        }

        // Bluetooth
        if (lower.contains("ব্লুটুথ") || lower.contains("bluetooth")) {
            return MimiAction("bluetooth")
        }

        // Airplane
        if (lower.contains("এরোপ্লেন") || lower.contains("বিমান মোড") || lower.contains("airplane")) {
            return MimiAction("airplane")
        }

        // Volume UP
        if (lower.contains("ভলিউম বাড়াও") || lower.contains("আওয়াজ বাড়াও") || lower.contains("volume up") || lower.contains("increase volume")) {
            return MimiAction("volume", mapOf("direction" to "up"))
        }

        // Volume DOWN
        if (lower.contains("ভলিউম কমাও") || lower.contains("আওয়াজ কমাও") || lower.contains("volume down") || lower.contains("decrease volume")) {
            return MimiAction("volume", mapOf("direction" to "down"))
        }

        // Mute
        if (lower.contains("মিউট করো") || lower.contains("চুপ করো") || lower.contains("mute audio") || lower.contains("mute")) {
            return MimiAction("volume", mapOf("direction" to "mute"))
        }

        // Screenshot
        if (lower.contains("স্ক্রিনশট") || lower.contains("screenshot") || lower.contains("take screenshot")) {
            return MimiAction("global_action", mapOf("type" to "screenshot"))
        }

        // Back / Home / Recents / Notifications
        if (lower.contains("হোমে যাও") || lower == "home" || lower.contains("go home")) {
            return MimiAction("global_action", mapOf("type" to "home"))
        }
        if (lower.contains("পিছনে যাও") || lower.contains("ব্যাক করো") || lower == "back" || lower.contains("go back")) {
            return MimiAction("global_action", mapOf("type" to "back"))
        }
        if (lower.contains("রিসেন্টস") || lower.contains("recent apps") || lower.contains("টাস্ক খোলো")) {
            return MimiAction("global_action", mapOf("type" to "recents"))
        }
        if (lower.contains("নোটিফিকেশন বার") || lower.contains("নোটিফিকেশন নামাও") || lower.contains("pull notifications")) {
            return MimiAction("global_action", mapOf("type" to "notifications"))
        }

        // Read notifications
        if (lower.contains("নোটিফিকেশন পড়ো") || lower.contains("read notifications") || lower.contains("check notifications")) {
            return MimiAction("read_notifications")
        }

        // Call End / Answer
        if (lower.contains("কল কাটো") || lower.contains("কল কেটে দাও") || lower.contains("end call") || lower.contains("hang up")) {
            return MimiAction("end_call")
        }
        if (lower.contains("কল ধরো") || lower.contains("কল রিসিভ") || lower.contains("answer call") || lower.contains("pick up call")) {
            return MimiAction("answer_call")
        }

        // Make Call: "কল করো [নাম/নাম্বার]" or "call [name]"
        if (lower.startsWith("কল করো ") || lower.startsWith("call ")) {
            val target = lower.removePrefix("কল করো ").removePrefix("call ").trim()
            if (target.isNotBlank()) {
                return MimiAction("call", mapOf("target" to target))
            }
        }

        // Open App: "খোলো [অ্যাপ]" or "[অ্যাপ] খোলো" or "open [app]"
        if (lower.endsWith(" খোলো") || lower.endsWith(" চালাও")) {
            val app = lower.removeSuffix(" খোলো").removeSuffix(" চালাও").trim()
            if (app.isNotBlank()) {
                return MimiAction("open_app", mapOf("app_name" to app))
            }
        }
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val app = lower.removePrefix("open ").removePrefix("launch ").trim()
            if (app.isNotBlank()) {
                return MimiAction("open_app", mapOf("app_name" to app))
            }
        }

        // YouTube Search: "ইউটিউবে ... খোঁজো" or "search youtube for ..."
        if (lower.contains("ইউটিউবে") && lower.contains("খোঁজো")) {
            val query = lower.replace("ইউটিউবে", "").replace("খোঁজো", "").trim()
            return MimiAction("youtube_search", mapOf("query" to query))
        }
        if (lower.startsWith("search youtube for ") || lower.startsWith("youtube ")) {
            val query = lower.removePrefix("search youtube for ").removePrefix("youtube ").trim()
            return MimiAction("youtube_search", mapOf("query" to query))
        }

        // Google Search: "গুগলে ... খোঁজো" or "search for ..."
        if (lower.startsWith("search for ") || lower.startsWith("google ")) {
            val query = lower.removePrefix("search for ").removePrefix("google ").trim()
            return MimiAction("web_search", mapOf("query" to query))
        }

        // Camera
        if (lower.contains("ক্যামেরা খোলো") || lower.contains("ছবি তোলো") || lower.contains("take photo") || lower.contains("open camera")) {
            return MimiAction("camera_photo")
        }

        // Media controls
        if (lower.contains("গান বাজাও") || lower.contains("play music") || lower.contains("play song")) {
            return MimiAction("media", mapOf("action" to "play"))
        }
        if (lower.contains("গান থামাও") || lower.contains("pause music") || lower.contains("stop music")) {
            return MimiAction("media", mapOf("action" to "pause"))
        }
        if (lower.contains("পরের গান") || lower.contains("next song") || lower.contains("next track")) {
            return MimiAction("media", mapOf("action" to "next"))
        }

        return null
    }

    private fun executeAction(action: MimiAction, originalText: String, isOffline: Boolean): CommandResult {
        val result = runPhoneAction(action.type, action.args ?: emptyMap())
        val emotion = if (result.success) "happy" else "sad"
        moodEngine.setMood(emotion)
        return CommandResult(
            replyText = result.message,
            emotion = emotion,
            actionType = action.type,
            success = result.success
        )
    }

    private fun runPhoneAction(type: String, args: Map<String, Any>): PhoneController.ActionResult {
        return when (type) {
            "open_app" -> {
                val app = args["app_name"]?.toString() ?: ""
                phoneController.openApp(app)
            }
            "close_app" -> {
                val app = args["app_name"]?.toString() ?: ""
                phoneController.closeApp(app)
            }
            "call" -> {
                val target = args["target"]?.toString() ?: ""
                phoneController.makeCall(target)
            }
            "end_call" -> phoneController.endCall()
            "answer_call" -> phoneController.answerCall()
            "send_sms" -> {
                val target = args["target"]?.toString() ?: ""
                val msg = args["message"]?.toString() ?: "Hello from Mimi AI!"
                phoneController.sendSms(target, msg)
            }
            "send_whatsapp" -> {
                val target = args["target"]?.toString() ?: ""
                val msg = args["message"]?.toString() ?: "Hello!"
                phoneController.sendWhatsApp(target, msg)
            }
            "wifi" -> phoneController.openWifiSettings()
            "bluetooth" -> phoneController.openBluetoothSettings()
            "airplane" -> phoneController.openAirplaneSettings()
            "flashlight" -> {
                val turnOn = args["turn_on"] as? Boolean
                phoneController.toggleFlashlight(turnOn)
            }
            "volume" -> {
                val dir = args["direction"]?.toString() ?: "up"
                val level = (args["level"] as? Number)?.toInt()
                phoneController.adjustVolume(dir, level)
            }
            "global_action" -> {
                val gType = args["type"]?.toString() ?: "back"
                phoneController.performGlobalAction(gType)
            }
            "camera_photo" -> phoneController.openCamera()
            "media" -> {
                val act = args["action"]?.toString() ?: "play"
                phoneController.mediaControl(act)
            }
            "web_search" -> {
                val q = args["query"]?.toString() ?: ""
                phoneController.webSearch(q)
            }
            "youtube_search" -> {
                val q = args["query"]?.toString() ?: ""
                phoneController.youtubeSearch(q)
            }
            "battery_status" -> phoneController.getBatteryReport()
            "storage_status" -> phoneController.getStorageReport()
            "read_notifications" -> phoneController.readNotifications()
            "alarm" -> {
                val h = (args["hour"] as? Number)?.toInt() ?: 7
                val m = (args["minute"] as? Number)?.toInt() ?: 0
                val msg = args["message"]?.toString() ?: "Mimi Alarm"
                phoneController.setAlarm(h, m, msg)
            }
            "reminder" -> {
                val delay = (args["delay_minutes"] as? Number)?.toInt() ?: 5
                val text = args["text"]?.toString() ?: "Reminder from Mimi!"
                phoneController.setReminder(delay, text)
            }
            else -> PhoneController.ActionResult(true, "Action processed successfully.")
        }
    }
}
