package com.example.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.service.MimiAccessibilityService
import com.example.service.MimiNotificationListener

data class PermissionStatusItem(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean,
    val isSpecial: Boolean = false
)

object PermissionHelper {

    fun checkAllPermissions(context: Context): List<PermissionStatusItem> {
        val list = mutableListOf<PermissionStatusItem>()

        // 1. Microphone
        list.add(
            PermissionStatusItem(
                id = Manifest.permission.RECORD_AUDIO,
                title = "Microphone (RECORD_AUDIO)",
                description = "Required for listening to your voice commands and wake word.",
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 2. Phone Calls
        list.add(
            PermissionStatusItem(
                id = Manifest.permission.CALL_PHONE,
                title = "Phone Calls (CALL_PHONE)",
                description = "Required to make hands-free calls via voice commands.",
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 3. SMS
        list.add(
            PermissionStatusItem(
                id = Manifest.permission.SEND_SMS,
                title = "Send SMS (SEND_SMS)",
                description = "Required to send text messages directly through Mimi.",
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 4. Contacts
        list.add(
            PermissionStatusItem(
                id = Manifest.permission.READ_CONTACTS,
                title = "Contacts (READ_CONTACTS)",
                description = "Required to lookup contacts by name when you say 'Call Mom'.",
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 5. Camera
        list.add(
            PermissionStatusItem(
                id = Manifest.permission.CAMERA,
                title = "Camera (CAMERA)",
                description = "Required for taking photos and operating the flashlight torch.",
                isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            )
        )

        // 6. Notifications (Android 13+)
        val postNotifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        list.add(
            PermissionStatusItem(
                id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "NOTIF",
                title = "Notifications (POST_NOTIFICATIONS)",
                description = "Required for reminder alerts and background service notifications.",
                isGranted = postNotifGranted
            )
        )

        // 7. Special: Accessibility Service
        list.add(
            PermissionStatusItem(
                id = "ACCESSIBILITY",
                title = "Accessibility Service",
                description = "Required for automated navigation, tapping, scrolling, and taking screenshots.",
                isGranted = MimiAccessibilityService.isRunning(),
                isSpecial = true
            )
        )

        // 8. Special: Notification Listener
        list.add(
            PermissionStatusItem(
                id = "NOTIFICATION_LISTENER",
                title = "Notification Listener",
                description = "Required for Mimi to read aloud your incoming notifications.",
                isGranted = MimiNotificationListener.isPermissionGranted(context),
                isSpecial = true
            )
        )

        // 9. Special: Modify System Settings (Write Settings)
        val writeSettingsGranted = Settings.System.canWrite(context)
        list.add(
            PermissionStatusItem(
                id = "WRITE_SETTINGS",
                title = "Modify System Settings",
                description = "Required for adjusting display brightness directly.",
                isGranted = writeSettingsGranted,
                isSpecial = true
            )
        )

        // 10. Special: Exact Alarms
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }
        list.add(
            PermissionStatusItem(
                id = "EXACT_ALARM",
                title = "Schedule Exact Alarms",
                description = "Required for precision timer and reminder execution.",
                isGranted = exactAlarmGranted,
                isSpecial = true
            )
        )

        // 11. Battery Optimization Ignore
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        list.add(
            PermissionStatusItem(
                id = "BATTERY_OPT",
                title = "Ignore Battery Optimization",
                description = "Allows continuous background wake word listening without being killed.",
                isGranted = batteryIgnored,
                isSpecial = true
            )
        )

        return list
    }

    fun openSpecialPermissionSettings(context: Context, id: String) {
        try {
            when (id) {
                "ACCESSIBILITY" -> {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                "NOTIFICATION_LISTENER" -> {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                "WRITE_SETTINGS" -> {
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:" + context.packageName)
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                "EXACT_ALARM" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:" + context.packageName)
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }
                "BATTERY_OPT" -> {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:" + context.packageName)
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
                else -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:" + context.packageName)
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:" + context.packageName)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
