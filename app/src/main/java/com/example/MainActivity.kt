package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MimiTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MimiApp

        handleLaunchIntent(intent, app)

        setContent {
            MimiTheme {
                MainScreen(
                    prefs = app.prefs,
                    characterController = app.characterController,
                    moodEngine = app.moodEngine,
                    voiceManager = app.voiceManager,
                    ttsManager = app.ttsManager,
                    commandRouter = app.commandRouter,
                    routineManager = app.routineManager,
                    database = app.database
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as MimiApp
        handleLaunchIntent(intent, app)
    }

    private fun handleLaunchIntent(intent: Intent?, app: MimiApp) {
        if (intent == null) return

        if (intent.getBooleanExtra("TRIGGER_VOICE_LISTENING", false)) {
            // Wake word detected, trigger listening
            app.voiceManager.startListening { spoken ->
                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    app.commandRouter.processCommand(spoken, source = "wake_word")
                }
            }
        }

        val spokenAlert = intent.getStringExtra("SPOKEN_ALERT")
        if (!spokenAlert.isNullOrBlank()) {
            app.ttsManager.speak(spokenAlert)
        }
    }
}
