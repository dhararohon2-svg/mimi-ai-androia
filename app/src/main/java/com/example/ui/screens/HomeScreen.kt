package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.core.CharacterController
import com.example.core.CommandRouter
import com.example.core.MoodEngine
import com.example.core.Prefs
import com.example.core.TtsManager
import com.example.core.VoiceManager
import com.example.ui.components.Character3DCanvas
import com.example.ui.components.MicPulseRing
import com.example.ui.components.VoiceWaveAnimation
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MoodAngry
import com.example.ui.theme.MoodHappy
import com.example.ui.theme.MoodSad
import com.example.ui.theme.MoodShy
import com.example.ui.theme.PrimaryPink
import com.example.ui.theme.SecondaryPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    prefs: Prefs,
    moodEngine: MoodEngine,
    characterController: CharacterController,
    voiceManager: VoiceManager,
    ttsManager: TtsManager,
    commandRouter: CommandRouter,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val charState by moodEngine.characterState.collectAsState()
    val isListening by voiceManager.isListening.collectAsState()
    val partialVoiceText by voiceManager.partialText.collectAsState()
    val rmsLevel by voiceManager.rmsLevel.collectAsState()
    val isSpeaking by ttsManager.isSpeaking.collectAsState()
    val mouthOpenLevel by ttsManager.mouthOpenLevel.collectAsState()

    var statusMessage by remember { mutableStateOf("Mimi is listening... Tap the mic or character!") }
    var isProcessing by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
    var textCommandInput by remember { mutableStateOf("") }

    // Audio Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceManager.startListening { spoken ->
                scope.launch {
                    isProcessing = true
                    statusMessage = "Executing: \"$spoken\""
                    val res = commandRouter.processCommand(spoken, source = "voice")
                    statusMessage = res.replyText
                    isProcessing = false
                }
            }
        } else {
            statusMessage = "Microphone permission is required to listen."
        }
    }

    fun handleMicToggle() {
        if (ttsManager.isSpeaking.value) {
            ttsManager.stop()
        }
        if (isListening) {
            voiceManager.stopListening()
        } else {
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasMic) {
                voiceManager.startListening { spoken ->
                    scope.launch {
                        isProcessing = true
                        statusMessage = "Executing: \"$spoken\""
                        val res = commandRouter.processCommand(spoken, source = "voice")
                        statusMessage = res.replyText
                        isProcessing = false
                    }
                }
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun sendQuickCommand(cmd: String) {
        if (ttsManager.isSpeaking.value) {
            ttsManager.stop()
        }
        scope.launch {
            isProcessing = true
            statusMessage = "Executing: $cmd"
            val res = commandRouter.processCommand(cmd, source = "quick_chip")
            statusMessage = res.replyText
            isProcessing = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Top Header: Affection & Mood Badge ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Affection progress
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "💖 Affection",
                        color = SecondaryPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${charState.affection}/100",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { charState.affection / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = PrimaryPink,
                    trackColor = DarkBorder,
                )
            }

            // Mood chip
            val moodColor = when (charState.mood) {
                "happy" -> MoodHappy
                "shy" -> MoodShy
                "sad" -> MoodSad
                "angry" -> MoodAngry
                else -> Color(0xFFB0BEC5)
            }
            val moodLabel = when (charState.mood) {
                "happy" -> "😊 Happy"
                "shy" -> "😳 Shy"
                "sad" -> "😢 Sad"
                "angry" -> "😡 Angry"
                else -> "😌 Neutral"
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(moodColor.copy(alpha = 0.2f))
                    .border(1.dp, moodColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = moodLabel,
                    color = moodColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- 2. Interactive 3D Character Canvas Area ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(DarkCard)
                .border(1.dp, DarkBorder, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Character3DCanvas(
                modelId = charState.modelType,
                mood = charState.mood,
                mouthOpenLevel = mouthOpenLevel,
                isSpeaking = isSpeaking,
                onAvatarTap = {
                    val dialogue = moodEngine.onAvatarTapped()
                    statusMessage = dialogue
                    ttsManager.speak(dialogue)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Hint overlay on canvas
            Text(
                text = "Drag to rotate • Pinch to zoom • Tap to interact",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. Speech Transcript Bubble ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, if (isListening) PrimaryPink else DarkBorder, RoundedCornerShape(18.dp)),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Default.GraphicEq else Icons.Default.Notifications,
                        contentDescription = "Status",
                        tint = if (isSpeaking) PrimaryPink else SecondaryPink,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isListening) "Listening..." else if (isProcessing) "Thinking..." else if (isSpeaking) "Speaking..." else charState.name,
                        color = PrimaryPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isProcessing) {
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = PrimaryPink,
                            strokeWidth = 1.5.dp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isListening && partialVoiceText.isNotBlank()) partialVoiceText else statusMessage,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 3
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- 4. Quick Action Chips ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickChip("🔋 ব্যাটারি", Icons.Default.BatteryFull) { sendQuickCommand("ব্যাটারি কত") }
            QuickChip("🔦 ফ্ল্যাশ", Icons.Default.FlashlightOn) { sendQuickCommand("ফ্ল্যাশলাইট জ্বালাও") }
            QuickChip("🎬 ইউটিউব", Icons.Default.PlayArrow) { sendQuickCommand("ইউটিউব খোলো") }
            QuickChip("💾 স্টোরেজ", Icons.Default.Storage) { sendQuickCommand("স্টোরেজ কত") }
            QuickChip("📸 ক্যামেরা", Icons.Default.CameraAlt) { sendQuickCommand("ক্যামেরা খোলো") }
            QuickChip("🔔 নোটিফিকেশন", Icons.Default.Notifications) { sendQuickCommand("নোটিফিকেশন পড়ো") }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- 5. Voice Wave Animation (when listening) ---
        AnimatedVisibility(
            visible = isListening,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            VoiceWaveAnimation(rmsLevel = rmsLevel, modifier = Modifier.padding(bottom = 6.dp))
        }

        // --- 6. Text input drawer or mic dock ---
        if (showTextInput) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = textCommandInput,
                    onValueChange = { textCommandInput = it },
                    placeholder = { Text("Ask Mimi anything...", color = TextSecondary) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface,
                        focusedBorderColor = PrimaryPink,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val txt = textCommandInput.trim()
                        if (txt.isNotBlank()) {
                            textCommandInput = ""
                            showTextInput = false
                            sendQuickCommand(txt)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(PrimaryPink)
                ) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        } else {
            // Main Bottom Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language Switch Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, RoundedCornerShape(20.dp))
                        .clickable {
                            val newLang = if (prefs.voiceLanguage.startsWith("bn")) "en-US" else "bn-BD"
                            prefs.voiceLanguage = newLang
                            ttsManager.setupVoiceAndLanguage()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (prefs.voiceLanguage.startsWith("bn")) "🇧🇩 বাংলা" else "🇺🇸 English",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Central Large Mic Button with Pulse Ring
                Box(contentAlignment = Alignment.Center) {
                    MicPulseRing(isListening = isListening)
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isListening) listOf(Color(0xFFFF1744), PrimaryPink) else listOf(PrimaryPink, Color(0xFFD81B60))
                                )
                            )
                            .clickable { handleMicToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Microphone",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Keyboard Toggle Button
                IconButton(
                    onClick = { showTextInput = true },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DarkCard)
                        .border(1.dp, DarkBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Type Command",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, DarkBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = SecondaryPink, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(text = label, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
