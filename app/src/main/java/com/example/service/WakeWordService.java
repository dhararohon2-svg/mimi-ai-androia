package com.example.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.core.app.NotificationCompat;
import com.example.MainActivity;
import com.example.R;

import java.util.ArrayList;
import java.util.Locale;

public class WakeWordService extends Service {

    public static final String ACTION_WAKE_WORD_DETECTED = "com.example.mimi.WAKE_WORD_DETECTED";
    private static final String CHANNEL_ID = "mimi_wake_word_channel";
    private static final int NOTIF_ID = 1001;

    private SpeechRecognizer mSpeechRecognizer;
    private Intent mRecognizerIntent;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mIsListening = false;
    private String mWakeWord = "hey mimi";
    private static boolean sIsRunning = false;

    public static boolean isRunning() {
        return sIsRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sIsRunning = true;
        createNotificationChannel();
        startForeground(NOTIF_ID, buildForegroundNotification());
        initSpeechRecognizer();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Mimi AI Wake Word Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Background voice service for wake word detection");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildForegroundNotification() {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, notifIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.wake_word_notification_title))
                .setContentText(getString(R.string.wake_word_notification_text))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return;
        }

        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                mIsListening = true;
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                mIsListening = false;
            }

            @Override
            public void onError(int error) {
                mIsListening = false;
                // Restart listening after short delay
                mHandler.postDelayed(WakeWordService.this::startListeningSafely, 1000);
            }

            @Override
            public void onResults(Bundle results) {
                mIsListening = false;
                checkMatches(results);
                mHandler.postDelayed(WakeWordService.this::startListeningSafely, 500);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                checkMatches(partialResults);
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        startListeningSafely();
    }

    private void checkMatches(Bundle bundle) {
        if (bundle == null) return;
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null) {
            for (String phrase : matches) {
                String clean = phrase.toLowerCase(Locale.getDefault());
                if (clean.contains("hey mimi") || clean.contains("হেই মিমি") ||
                    clean.contains("mimi") || clean.contains("মিমি") ||
                    clean.contains(mWakeWord.toLowerCase(Locale.getDefault()))) {
                    triggerWakeAction();
                    break;
                }
            }
        }
    }

    private void triggerWakeAction() {
        Intent intent = new Intent(ACTION_WAKE_WORD_DETECTED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);

        // Bring MainActivity to front
        Intent launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        launch.putExtra("TRIGGER_VOICE_LISTENING", true);
        startActivity(launch);
    }

    private void startListeningSafely() {
        if (mSpeechRecognizer != null && !mIsListening) {
            try {
                mSpeechRecognizer.startListening(mRecognizerIntent);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("WAKE_WORD")) {
            mWakeWord = intent.getStringExtra("WAKE_WORD");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sIsRunning = false;
        if (mSpeechRecognizer != null) {
            try {
                mSpeechRecognizer.destroy();
            } catch (Exception ignored) {}
            mSpeechRecognizer = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
