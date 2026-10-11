package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.core.MoodEngine
import com.example.core.Prefs
import com.example.core.TtsManager
import com.example.ui.components.PermissionHelper
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.PrimaryPink
import com.example.ui.theme.SecondaryPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun OnboardingDialog(
    prefs: Prefs,
    ttsManager: TtsManager,
    moodEngine: MoodEngine,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(1) }

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // Character voice explanation per step
    LaunchedEffect(currentStep) {
        val message = when (currentStep) {
            1 -> "হ্যালো! আমি মিমি, আপনার পার্সোনাল ৩ডি ভয়েস অ্যাসিস্ট্যান্ট ও ফোন সঙ্গী। আপনাকে সাহায্য করতে পেরে আমি আনন্দিত!"
            2 -> "আমাকে আপনার কথা শুনতে মাইক্রোফোন পারমিশন দিন, যেন আপনি হেই মিমি বললেই আমি সাড়া দিতে পারি!"
            3 -> "আপনার ফোন থেকে কল করা, এসএমএস পাঠানো ও কন্টাক্ট খুঁজতে ফোন পারমিশন প্রয়োজন।"
            4 -> "স্ক্রিন নেভিগেশন ও নোটিফিকেশন পড়ার জন্য অ্যাক্সেসিবিলিটি সার্ভিস চালু করুন।"
            else -> "দারুণ! সব সেটআপ শেষ। চলুন শুরু করা যাক!"
        }
        ttsManager.speak(message)
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            color = DarkCard,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryPink)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Step indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    for (i in 1..5) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (i == currentStep) 24.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (i == currentStep) PrimaryPink else DarkBorder)
                        )
                    }
                }

                // Step content
                AnimatedContent(targetState = currentStep, label = "onboarding_step") { step ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        when (step) {
                            1 -> {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(PrimaryPink.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Favorite, contentDescription = null, tint = PrimaryPink, modifier = Modifier.size(50.dp))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Meet Mimi AI", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Your interactive 3D anime voice assistant, phone automation manager, and companion.",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            2 -> {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(SecondaryPink.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Mic, contentDescription = null, tint = SecondaryPink, modifier = Modifier.size(50.dp))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Voice & Speech Access", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Mimi needs microphone permission to hear your voice in Bengali and English, and trigger wake commands.",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        runtimePermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                                ) {
                                    Text("Grant Microphone")
                                }
                            }
                            3 -> {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF26A69A).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Color(0xFF26A69A), modifier = Modifier.size(50.dp))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Phone Automation", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Allow Mimi to call contacts, send SMS messages, take photos, and report battery health hands-free.",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val perms = mutableListOf(
                                            Manifest.permission.CALL_PHONE,
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.SEND_SMS,
                                            Manifest.permission.CAMERA
                                        )
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        runtimePermissionsLauncher.launch(perms.toTypedArray())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink)
                                ) {
                                    Text("Grant Phone Permissions")
                                }
                            }
                            4 -> {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFAB47BC).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = Color(0xFFAB47BC), modifier = Modifier.size(50.dp))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Special Services", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Enable Accessibility to allow Mimi to tap, scroll, and screenshot, and Notification Listener to read your alerts.",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { PermissionHelper.openSpecialPermissionSettings(context, "ACCESSIBILITY") }
                                    ) {
                                        Text("Accessibility", fontSize = 12.sp, color = SecondaryPink)
                                    }
                                    OutlinedButton(
                                        onClick = { PermissionHelper.openSpecialPermissionSettings(context, "NOTIFICATION_LISTENER") }
                                    ) {
                                        Text("Notifications", fontSize = 12.sp, color = SecondaryPink)
                                    }
                                }
                            }
                            5 -> {
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(50.dp))
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("You're All Set!", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Mimi is ready to help you manage your phone, converse, and keep you company!",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Bottom Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStep > 1) {
                        OutlinedButton(
                            onClick = { currentStep-- },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Back", color = TextSecondary)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = {
                            if (currentStep < 5) {
                                currentStep++
                            } else {
                                prefs.isOnboardingCompleted = true
                                onComplete()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPink),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (currentStep < 5) "Continue" else "Get Started")
                    }
                }
            }
        }
    }
}
