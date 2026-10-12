package com.example.phone;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.KeyEvent;
import com.example.receiver.ReminderReceiver;
import com.example.service.MimiAccessibilityService;
import com.example.service.MimiNotificationListener;

import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

public class PhoneController {

    private final Context mContext;
    private static boolean sTorchOn = false;

    public PhoneController(Context context) {
        this.mContext = context.getApplicationContext();
    }

    public static class ActionResult {
        public final boolean success;
        public final String message;

        public ActionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    // 1. Open App
    public ActionResult openApp(String appName) {
        AppLauncher.LaunchResult res = AppLauncher.openApp(mContext, appName);
        return new ActionResult(res.success, res.message);
    }

    // 2. Close App (using Accessibility)
    public ActionResult closeApp(String appName) {
        MimiAccessibilityService service = MimiAccessibilityService.getInstance();
        if (service != null) {
            service.doGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
            return new ActionResult(true, "Showing recent apps to close " + (appName != null ? appName : ""));
        } else {
            return new ActionResult(false, "Accessibility Service is not enabled. Please enable it in Settings.");
        }
    }

    // 3. Phone Calls
    public ActionResult makeCall(String target) {
        if (target == null || target.trim().isEmpty()) {
            return new ActionResult(false, "No contact or phone number provided");
        }

        // Check if digits or contact name
        if (target.matches(".*\\d{3,}.*")) {
            boolean ok = CallHelper.makeCall(mContext, target);
            return new ActionResult(ok, ok ? "Calling " + target : "Failed to initiate call to " + target);
        } else {
            List<ContactHelper.ContactItem> contacts = ContactHelper.findContactsByName(mContext, target);
            if (!contacts.isEmpty()) {
                ContactHelper.ContactItem first = contacts.get(0);
                boolean ok = CallHelper.makeCall(mContext, first.number);
                return new ActionResult(ok, ok ? "Calling " + first.name + " (" + first.number + ")" : "Failed to call " + first.name);
            } else {
                return new ActionResult(false, "No contact found matching \"" + target + "\". Check Contacts permission.");
            }
        }
    }

    public ActionResult endCall() {
        boolean ok = CallHelper.endCall(mContext);
        return new ActionResult(ok, ok ? "Call ended." : "Unable to end call.");
    }

    public ActionResult answerCall() {
        boolean ok = CallHelper.answerCall(mContext);
        return new ActionResult(ok, ok ? "Call answered." : "Unable to answer call.");
    }

    // 4. SMS & WhatsApp
    public ActionResult sendSms(String target, String message) {
        String number = target;
        if (!target.matches(".*\\d{3,}.*")) {
            List<ContactHelper.ContactItem> contacts = ContactHelper.findContactsByName(mContext, target);
            if (!contacts.isEmpty()) {
                number = contacts.get(0).number;
            } else {
                return new ActionResult(false, "Contact not found for SMS: " + target);
            }
        }

        try {
            SmsManager smsManager = mContext.getSystemService(SmsManager.class);
            if (smsManager == null) {
                smsManager = SmsManager.getDefault();
            }
            smsManager.sendTextMessage(number, null, message, null, null);
            return new ActionResult(true, "SMS sent to " + target + ": \"" + message + "\"");
        } catch (Exception e) {
            // Fallback via intent
            try {
                Intent smsIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("sms:" + number));
                smsIntent.putExtra("sms_body", message);
                smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(smsIntent);
                return new ActionResult(true, "Opening SMS app with draft for " + target);
            } catch (Exception ex) {
                return new ActionResult(false, "Failed to send SMS: " + e.getMessage());
            }
        }
    }

    public ActionResult sendWhatsApp(String phoneOrName, String message) {
        String number = phoneOrName;
        if (!phoneOrName.matches(".*\\d{3,}.*")) {
            List<ContactHelper.ContactItem> contacts = ContactHelper.findContactsByName(mContext, phoneOrName);
            if (!contacts.isEmpty()) {
                number = contacts.get(0).number;
            }
        }
        number = number.replaceAll("[^0-9]", "");

        try {
            String url = "https://api.whatsapp.com/send?phone=" + number + "&text=" + URLEncoder.encode(message, "UTF-8");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.setPackage("com.whatsapp");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(i);

            // Attempt accessibility auto-click send if available
            MimiAccessibilityService service = MimiAccessibilityService.getInstance();
            if (service != null) {
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    service.clickText("Send");
                }, 1500);
            }
            return new ActionResult(true, "Sending WhatsApp message to " + phoneOrName);
        } catch (Exception e) {
            return new ActionResult(false, "WhatsApp is not installed or error occurred: " + e.getMessage());
        }
    }

    // 5. Connectivity Toggles (Settings Panel in Android 10+)
    public ActionResult openWifiSettings() {
        try {
            Intent panelIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
            } else {
                panelIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
            }
            panelIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(panelIntent);
            return new ActionResult(true, "Opening Wi-Fi settings panel.");
        } catch (Exception e) {
            return new ActionResult(false, "Could not open Wi-Fi settings.");
        }
    }

    public ActionResult openBluetoothSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return new ActionResult(true, "Opening Bluetooth settings.");
        } catch (Exception e) {
            return new ActionResult(false, "Could not open Bluetooth settings.");
        }
    }

    public ActionResult openAirplaneSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return new ActionResult(true, "Opening Airplane Mode settings.");
        } catch (Exception e) {
            return new ActionResult(false, "Could not open Airplane Mode settings.");
        }
    }

    // 6. Flashlight
    public ActionResult toggleFlashlight(Boolean turnOn) {
        CameraManager cm = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) return new ActionResult(false, "Camera hardware unavailable.");

        try {
            String[] ids = cm.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics chars = cm.getCameraCharacteristics(id);
                Boolean hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) {
                    boolean targetState = turnOn != null ? turnOn : !sTorchOn;
                    cm.setTorchMode(id, targetState);
                    sTorchOn = targetState;
                    return new ActionResult(true, targetState ? "Flashlight turned on." : "Flashlight turned off.");
                }
            }
        } catch (CameraAccessException | IllegalArgumentException e) {
            return new ActionResult(false, "Unable to switch flashlight: " + e.getMessage());
        }
        return new ActionResult(false, "No camera with flashlight found.");
    }

    // 7. Volume Adjustment
    public ActionResult adjustVolume(String direction, Integer level) {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return new ActionResult(false, "Audio service unavailable");

        int stream = AudioManager.STREAM_MUSIC;
        if (level != null) {
            int max = am.getStreamMaxVolume(stream);
            int clamped = Math.max(0, Math.min(level, max));
            am.setStreamVolume(stream, clamped, AudioManager.FLAG_SHOW_UI);
            return new ActionResult(true, "Volume set to " + clamped + " / " + max);
        }

        if ("up".equalsIgnoreCase(direction)) {
            am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
            return new ActionResult(true, "Volume increased.");
        } else if ("down".equalsIgnoreCase(direction)) {
            am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
            return new ActionResult(true, "Volume decreased.");
        } else if ("mute".equalsIgnoreCase(direction)) {
            am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
            return new ActionResult(true, "Audio muted.");
        }
        return new ActionResult(false, "Unknown volume direction");
    }

    // 8. Media Controls
    public ActionResult mediaControl(String action) {
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return new ActionResult(false, "Audio service unavailable");

        int key;
        if ("play".equalsIgnoreCase(action) || "pause".equalsIgnoreCase(action) || "toggle".equalsIgnoreCase(action)) {
            key = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        } else if ("next".equalsIgnoreCase(action)) {
            key = KeyEvent.KEYCODE_MEDIA_NEXT;
        } else if ("previous".equalsIgnoreCase(action) || "prev".equalsIgnoreCase(action)) {
            key = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        } else {
            return new ActionResult(false, "Unknown media action");
        }

        long eventTime = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, key, 0);
        KeyEvent up = new KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, key, 0);
        am.dispatchMediaKeyEvent(down);
        am.dispatchMediaKeyEvent(up);
        return new ActionResult(true, "Media action executed: " + action);
    }

    // 9. Global Accessibility Actions
    public ActionResult performGlobalAction(String type) {
        MimiAccessibilityService service = MimiAccessibilityService.getInstance();
        if (service == null) {
            return new ActionResult(false, "Mimi Accessibility Service is not active. Enable it in Android Settings.");
        }

        int action;
        switch (type.toLowerCase()) {
            case "back":
                action = AccessibilityService.GLOBAL_ACTION_BACK;
                break;
            case "home":
                action = AccessibilityService.GLOBAL_ACTION_HOME;
                break;
            case "recents":
                action = AccessibilityService.GLOBAL_ACTION_RECENTS;
                break;
            case "notifications":
                action = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS;
                break;
            case "quick_settings":
                action = AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS;
                break;
            case "screenshot":
                action = AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT;
                break;
            default:
                return new ActionResult(false, "Unknown global action: " + type);
        }

        boolean ok = service.doGlobalAction(action);
        return new ActionResult(ok, ok ? "Executed " + type : "Failed to execute " + type);
    }

    // 10. Web & YouTube Search
    public ActionResult webSearch(String query) {
        try {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, query);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return new ActionResult(true, "Searching Google for: " + query);
        } catch (Exception e) {
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8")));
                browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(browser);
                return new ActionResult(true, "Searching for: " + query);
            } catch (Exception ex) {
                return new ActionResult(false, "Could not open browser search.");
            }
        }
    }

    public ActionResult youtubeSearch(String query) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEARCH);
            intent.setPackage("com.google.android.youtube");
            intent.putExtra("query", query);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return new ActionResult(true, "Searching YouTube for: " + query);
        } catch (Exception e) {
            return webSearch(query + " on youtube");
        }
    }

    // 11. Alarm & Reminder
    public ActionResult setAlarm(int hour, int minute, String message) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, message != null ? message : "Mimi AI Alarm");
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return new ActionResult(true, String.format("Alarm set for %02d:%02d.", hour, minute));
        } catch (Exception e) {
            return new ActionResult(false, "Unable to set alarm: " + e.getMessage());
        }
    }

    public ActionResult setReminder(int delayMinutes, String reminderText) {
        try {
            AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(mContext, ReminderReceiver.class);
            intent.putExtra(ReminderReceiver.EXTRA_REMINDER_TEXT, reminderText);
            PendingIntent pi = PendingIntent.getBroadcast(
                    mContext,
                    (int) System.currentTimeMillis(),
                    intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            long triggerAt = System.currentTimeMillis() + (delayMinutes * 60L * 1000L);
            boolean exact = true;
            if (am != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                    // Android 12+ may deny exact alarms; fall back to an alarm that is slightly less precise
                    exact = false;
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            }
            return new ActionResult(true, "Reminder scheduled in " + delayMinutes + " minute(s)" + (exact ? "" : " (approximate time)") + ": \"" + reminderText + "\"");
        } catch (Exception e) {
            return new ActionResult(false, "Could not schedule reminder: " + e.getMessage());
        }
    }

    // 12. Camera photo capture
    public ActionResult openCamera() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return new ActionResult(true, "Opening camera to take photo.");
        } catch (Exception e) {
            return new ActionResult(false, "Failed to open camera: " + e.getMessage());
        }
    }

    // 13. System Status reports
    public ActionResult getBatteryReport() {
        SystemStatus.BatteryInfo b = SystemStatus.getBatteryInfo(mContext);
        return new ActionResult(true, b.statusDescription);
    }

    public ActionResult getStorageReport() {
        SystemStatus.StorageInfo s = SystemStatus.getStorageInfo();
        return new ActionResult(true, s.formattedReport);
    }

    public ActionResult getNetworkReport() {
        SystemStatus.NetworkInfo n = SystemStatus.getNetworkInfo(mContext);
        return new ActionResult(true, n.statusDescription);
    }

    public ActionResult readNotifications() {
        if (!MimiNotificationListener.isPermissionGranted(mContext)) {
            return new ActionResult(false, "Notification Listener permission is not granted. Please enable it in Settings.");
        }
        String summary = MimiNotificationListener.getReadableSummary();
        return new ActionResult(true, summary);
    }
}
