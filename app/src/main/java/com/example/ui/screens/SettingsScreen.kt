package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.CharacterController
import com.example.core.CommandRouter
import com.example.core.MoodEngine
import com.example.core.Prefs
import com.example.core.TtsManager
import com.example.data.AppDatabase
import com.example.service.WakeWordService
import com.example.ui.components.PermissionHelper
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.PrimaryPink
import com.example.ui.theme.SecondaryPink
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    prefs: Prefs,
    characterController: CharacterController,
    moodEngine: MoodEngine,
    ttsManager: TtsManager,
    database: AppDatabase,
    commandRouter: CommandRouter,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nameInput by remember { mutableStateOf(prefs.characterName) }
    var pitchSlider by remember { mutableFloatStateOf(prefs.voicePitch) }
    var speedSlider by remember { mutableFloatStateOf(prefs.voiceSpeed) }
    var apiKeyInput by remember { mutableStateOf(prefs.geminiApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(prefs.geminiModel) }
    var wakeWordEnabled by remember { mutableStateOf(prefs.isWakeWordEnabled) }
    var wakeWordPhrase by remember { mutableStateOf(prefs.wakeWord) }

    val availableModels by characterController.availableModels.collectAsState()
    val activeModelId by characterController.selectedModel.collectAsState()

    var historySearchQuery by remember { mutableStateOf("") }
    val historyList by if (historySearchQuery.isBlank()) {
        database.commandHistoryDao().getAllHistory().collectAsState(initial = emptyList())
    } else {
        database.commandHistoryDao().searchHistory(historySearchQuery).collectAsState(initial = emptyList())
    }

    val permissionsList = remember { PermissionHelper.checkAllPermissions(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Customization",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Configure 3D avatar, voice, AI reasoning, and permissions",
                color = SecondaryPink,
                fontSize = 12.sp
            )
        }

        // --- SECTION 1: 3D Character Appearance ---
        item {
            SettingsSectionHeader(title = "🎭 3D Character & Voice", icon = Icons.Default.Face)
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select 3D Skin / Costume:", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (model in availableModels) {
                            val isSelected = model.id == activeModelId
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) PrimaryPink else DarkSurface)
                                    .border(1.dp, if (isSelected) PrimaryPink else DarkBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        characterController.selectModel(model.id)
                                        moodEngine.updateModel(model.id)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = model.id.removeSuffix("_mimi").replaceFirstChar { it.uppercase() },
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            prefs.characterName = it
                            moodEngine.updateName(it)
                        },
                        label = { Text("Character Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )

                    // Voice Pitch
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Voice Pitch", color = TextPrimary, fontSize = 13.sp)
                            Text(String.format(Locale.getDefault(), "%.2fx", pitchSlider), color = SecondaryPink, fontSize = 13.sp)
                        }
                        Slider(
                            value = pitchSlider,
                            onValueChange = {
                                pitchSlider = it
                                prefs.voicePitch = it
                                ttsManager.setupVoiceAndLanguage()
                            },
                            valueRange = 0.8f..1.8f,
                            colors = SliderDefaults.colors(thumbColor = PrimaryPink, activeTrackColor = PrimaryPink)
                        )
                    }

                    // Voice Speed
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Voice Speed", color = TextPrimary, fontSize = 13.sp)
                            Text(String.format(Locale.getDefault(), "%.2fx", speedSlider), color = SecondaryPink, fontSize = 13.sp)
                        }
                        Slider(
                            value = speedSlider,
                            onValueChange = {
                                speedSlider = it
                                prefs.voiceSpeed = it
                                ttsManager.setupVoiceAndLanguage()
                            },
                            valueRange = 0.6f..1.6f,
                            colors = SliderDefaults.colors(thumbColor = PrimaryPink, activeTrackColor = PrimaryPink)
                        )
                    }

                    // Vocal response toggle
                    var vocalResponseEnabled by remember { mutableStateOf(prefs.isVocalResponseEnabled) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Vocal Responses", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Mimi speaks answers and command results aloud", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = vocalResponseEnabled,
                            onCheckedChange = { enabled ->
                                vocalResponseEnabled = enabled
                                prefs.isVocalResponseEnabled = enabled
                                if (!enabled) {
                                    ttsManager.stop()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryPink, checkedTrackColor = DarkSurface)
                        )
                    }

                    // TTS Engine Info
                    val currentEngine by ttsManager.currentEngineName.collectAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Engine:", color = TextSecondary, fontSize = 12.sp)
                        Text(currentEngine, color = SecondaryPink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                ttsManager.speak("হ্যালো! আমি মিমি, আপনার প্রিয় ৩ডি ভয়েস অ্যাসিস্ট্যান্ট!")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🇧🇩 শুনুন (বাংলা)", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                ttsManager.speak("Hello Master! I am Mimi, ready to assist your phone commands!")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryPink),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🇺🇸 Test (English)", fontSize = 12.sp, color = Color.Black)
                        }
                    }
                }
            }
        }

        // --- SECTION 2: Background Wake Word ---
        item {
            SettingsSectionHeader(title = "🎙️ Wake Word Detection", icon = Icons.Default.Mic)
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Wake Word", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Listens for 'Hey Mimi' or 'হেই মিমি' in background", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = wakeWordEnabled,
                            onCheckedChange = { enabled ->
                                wakeWordEnabled = enabled
                                prefs.isWakeWordEnabled = enabled
                                val intent = Intent(context, WakeWordService::class.java).apply {
                                    putExtra("WAKE_WORD", wakeWordPhrase)
                                }
                                if (enabled) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                } else {
                                    context.stopService(intent)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryPink, checkedTrackColor = DarkSurface)
                        )
                    }

                    OutlinedTextField(
                        value = wakeWordPhrase,
                        onValueChange = {
                            wakeWordPhrase = it
                            prefs.wakeWord = it
                        },
                        label = { Text("Custom Wake Phrase") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )

                    Text(
                        text = "⚠️ Battery Notice: Background listening consumes additional battery. A persistent notification is shown by Android.",
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // --- SECTION 3: Gemini AI Settings ---
        item {
            SettingsSectionHeader(title = "🤖 Gemini AI Brain", icon = Icons.Default.Key)
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = {
                            apiKeyInput = it
                            prefs.geminiApiKey = it
                        },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle visibility",
                                    tint = TextSecondary
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )

                    Text("Selected AI Model:", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    val modelOptions = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-flash-latest")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (m in modelOptions) {
                            val isSel = selectedModel == m
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSel) PrimaryPink else DarkSurface)
                                    .border(1.dp, if (isSel) PrimaryPink else DarkBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedModel = m
                                        prefs.geminiModel = m
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = m.replace("gemini-", "").take(7),
                                    color = if (isSel) Color.White else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 4: Live Permissions Status Dashboard ---
        item {
            SettingsSectionHeader(title = "🔒 System Permissions Dashboard", icon = Icons.Default.Security)
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Live status of all required device capabilities:",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    for (item in permissionsList) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurface)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (item.isGranted) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = "Status",
                                        tint = if (item.isGranted) StatusGreen else StatusRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(item.title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Text(item.description, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            }

                            if (!item.isGranted) {
                                Button(
                                    onClick = { PermissionHelper.openSpecialPermissionSettings(context, item.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Grant", fontSize = 11.sp)
                                }
                            } else {
                                Text("Granted", color = StatusGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 5: Command History ---
        item {
            SettingsSectionHeader(title = "📜 Command Execution History", icon = Icons.Default.History)
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = historySearchQuery,
                            onValueChange = { historySearchQuery = it },
                            placeholder = { Text("Search past commands...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                        )
                        IconButton(onClick = { scope.launch { database.commandHistoryDao().clearAll() } }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Clear History", tint = TextSecondary)
                        }
                    }

                    if (historyList.isEmpty()) {
                        Text("No commands in history yet.", color = TextSecondary, fontSize = 12.sp)
                    } else {
                        for (history in historyList.take(20)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(DarkSurface)
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(history.commandText, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = "${history.actionType} • ${if (history.success) "Success" else "Failed"} • ${SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault()).format(Date(history.timestamp))}",
                                        color = if (history.success) StatusGreen else StatusRed,
                                        fontSize = 11.sp
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            commandRouter.processCommand(history.commandText, source = "rerun")
                                        }
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Re-run", tint = PrimaryPink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = PrimaryPink, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
