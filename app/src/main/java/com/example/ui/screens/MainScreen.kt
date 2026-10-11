package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.CharacterController
import com.example.core.CommandRouter
import com.example.core.MoodEngine
import com.example.core.Prefs
import com.example.core.RoutineManager
import com.example.core.TtsManager
import com.example.core.VoiceManager
import com.example.data.AppDatabase
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.DarkCard
import com.example.ui.theme.PrimaryPink
import com.example.ui.theme.SecondaryPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class MimiTab {
    HOME, CHAT, ROUTINES, SETTINGS
}

@Composable
fun MainScreen(
    prefs: Prefs,
    characterController: CharacterController,
    moodEngine: MoodEngine,
    voiceManager: VoiceManager,
    ttsManager: TtsManager,
    commandRouter: CommandRouter,
    routineManager: RoutineManager,
    database: AppDatabase
) {
    var selectedTab by remember { mutableStateOf(MimiTab.HOME) }
    var showOnboarding by remember { mutableStateOf(!prefs.isOnboardingCompleted) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = AmoledBlack,
        bottomBar = {
            NavigationBar(
                containerColor = DarkCard,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == MimiTab.HOME,
                    onClick = { selectedTab = MimiTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = PrimaryPink,
                        indicatorColor = PrimaryPink,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MimiTab.CHAT,
                    onClick = { selectedTab = MimiTab.CHAT },
                    icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Chat") },
                    label = { Text("Chat", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = PrimaryPink,
                        indicatorColor = PrimaryPink,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MimiTab.ROUTINES,
                    onClick = { selectedTab = MimiTab.ROUTINES },
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "Routines") },
                    label = { Text("Routines", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = PrimaryPink,
                        indicatorColor = PrimaryPink,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == MimiTab.SETTINGS,
                    onClick = { selectedTab = MimiTab.SETTINGS },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = PrimaryPink,
                        indicatorColor = PrimaryPink,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(AmoledBlack)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tab_switch"
            ) { tab ->
                when (tab) {
                    MimiTab.HOME -> HomeScreen(
                        prefs = prefs,
                        moodEngine = moodEngine,
                        characterController = characterController,
                        voiceManager = voiceManager,
                        ttsManager = ttsManager,
                        commandRouter = commandRouter
                    )
                    MimiTab.CHAT -> ChatScreen(
                        database = database,
                        commandRouter = commandRouter,
                        voiceManager = voiceManager,
                        moodEngine = moodEngine,
                        ttsManager = ttsManager
                    )
                    MimiTab.ROUTINES -> RoutinesScreen(
                        database = database,
                        routineManager = routineManager
                    )
                    MimiTab.SETTINGS -> SettingsScreen(
                        prefs = prefs,
                        characterController = characterController,
                        moodEngine = moodEngine,
                        ttsManager = ttsManager,
                        database = database,
                        commandRouter = commandRouter
                    )
                }
            }

            if (showOnboarding) {
                OnboardingDialog(
                    prefs = prefs,
                    ttsManager = ttsManager,
                    moodEngine = moodEngine,
                    onComplete = {
                        showOnboarding = false
                    }
                )
            }
        }
    }
}
