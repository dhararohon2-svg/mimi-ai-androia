package com.example.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import com.example.service.WakeWordService;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            // Must be the same file name that com.example.core.Prefs uses (it was "mimi_prefs" before,
            // so the wake-word setting was never found and the service never restarted after boot).
            SharedPreferences prefs = context.getSharedPreferences("mimi_assistant_prefs", Context.MODE_PRIVATE);
            boolean wakeWordEnabled = prefs.getBoolean("wake_word_enabled", false);
            if (!wakeWordEnabled) return;

            // Android 15 (API 35) and newer do not allow a microphone foreground service to be started
            // from BOOT_COMPLETED. The wake word starts again the next time the app is opened.
            if (Build.VERSION.SDK_INT >= 35) {
                Log.i(TAG, "Wake word service cannot auto-start at boot on this Android version");
                return;
            }

            try {
                Intent serviceIntent = new Intent(context, WakeWordService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not start wake word service at boot", e);
            }
        }
    }
}
