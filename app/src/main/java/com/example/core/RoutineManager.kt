package com.example.core

import com.example.data.AppDatabase
import com.example.data.RoutineEntity
import com.example.phone.PhoneController
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class RoutineManager(
    private val database: AppDatabase,
    private val phoneController: PhoneController,
    private val ttsManager: TtsManager,
    private val moodEngine: MoodEngine
) {

    private val gson = Gson()

    suspend fun executeRoutine(routine: RoutineEntity): String = withContext(Dispatchers.IO) {
        val actionsArray = try {
            JsonParser.parseString(routine.actionsJson).asJsonArray
        } catch (e: Exception) {
            JsonArray()
        }

        val results = mutableListOf<String>()
        moodEngine.setMood("happy")

        withContext(Dispatchers.Main) {
            ttsManager.speak("Starting routine: ${routine.title}")
        }
        delay(1500)

        for (elem in actionsArray) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            val type = obj.get("type")?.asString ?: continue

            val res = when (type) {
                "battery_status" -> phoneController.getBatteryReport()
                "storage_status" -> phoneController.getStorageReport()
                "read_notifications" -> phoneController.readNotifications()
                "wifi" -> phoneController.openWifiSettings()
                "bluetooth" -> phoneController.openBluetoothSettings()
                "open_app" -> {
                    val app = obj.getAsJsonObject("args")?.get("app_name")?.asString ?: "YouTube"
                    phoneController.openApp(app)
                }
                "media" -> {
                    val act = obj.getAsJsonObject("args")?.get("action")?.asString ?: "play"
                    phoneController.mediaControl(act)
                }
                else -> PhoneController.ActionResult(true, "Completed step: $type")
            }

            results.add(res.message)
            withContext(Dispatchers.Main) {
                ttsManager.speak(res.message)
            }
            delay(2000)
        }

        return@withContext "Routine '${routine.title}' finished! Executed ${results.size} step(s)."
    }

    suspend fun checkAndExecuteTrigger(spokenPhrase: String): Boolean {
        val routine = database.routineDao().findRoutineByTrigger(spokenPhrase.trim())
        if (routine != null) {
            executeRoutine(routine)
            return true
        }
        return false
    }
}
