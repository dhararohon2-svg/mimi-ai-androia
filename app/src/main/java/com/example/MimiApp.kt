package com.example

import android.app.Application
import com.example.core.CharacterController
import com.example.core.CommandRouter
import com.example.core.GeminiClient
import com.example.core.MoodEngine
import com.example.core.Prefs
import com.example.core.RoutineManager
import com.example.core.TtsManager
import com.example.core.VoiceManager
import com.example.data.AppDatabase
import com.example.phone.PhoneController

class MimiApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var moodEngine: MoodEngine
        private set
    lateinit var ttsManager: TtsManager
        private set
    lateinit var voiceManager: VoiceManager
        private set
    lateinit var phoneController: PhoneController
        private set
    lateinit var characterController: CharacterController
        private set
    lateinit var geminiClient: GeminiClient
        private set
    lateinit var commandRouter: CommandRouter
        private set
    lateinit var routineManager: RoutineManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        prefs = Prefs(this)
        database = AppDatabase.getInstance(this)
        moodEngine = MoodEngine(prefs)
        ttsManager = TtsManager(this, prefs)
        voiceManager = VoiceManager(this, prefs)
        phoneController = PhoneController(this)
        characterController = CharacterController(this, prefs)
        geminiClient = GeminiClient(prefs)
        commandRouter = CommandRouter(this, phoneController, moodEngine, ttsManager, geminiClient, database)
        routineManager = RoutineManager(database, phoneController, ttsManager, moodEngine)
    }

    override fun onTerminate() {
        super.onTerminate()
        ttsManager.shutdown()
        voiceManager.stopListening()
    }

    companion object {
        lateinit var instance: MimiApp
            private set
    }
}
