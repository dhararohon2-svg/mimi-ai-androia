package com.example.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.example.MainActivity;
import com.example.R;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_REMINDER_TEXT = "extra_reminder_text";
    private static final String CHANNEL_ID = "mimi_reminder_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String reminderText = "Time for your scheduled reminder!";
        if (intent != null && intent.hasExtra(EXTRA_REMINDER_TEXT)) {
            String text = intent.getStringExtra(EXTRA_REMINDER_TEXT);
            if (text != null && !text.isEmpty()) {
                reminderText = text;
            }
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.reminder_notification_channel),
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }

            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.putExtra("SPOKEN_ALERT", reminderText);
            PendingIntent pi = PendingIntent.getActivity(
                    context,
                    (int) System.currentTimeMillis(),
                    openIntent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Mimi AI Reminder")
                    .setContentText(reminderText)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pi);

            nm.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
