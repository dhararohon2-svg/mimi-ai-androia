package com.example.phone;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.util.Locale;

public class SystemStatus {

    public static class BatteryInfo {
        public int level;
        public boolean isCharging;
        public float temperatureCelsius;
        public String statusDescription;
    }

    public static class StorageInfo {
        public long totalBytes;
        public long availableBytes;
        public long usedBytes;
        public String formattedReport;
    }

    public static class NetworkInfo {
        public boolean isConnected;
        public boolean isWifi;
        public boolean isCellular;
        public String statusDescription;
    }

    public static BatteryInfo getBatteryInfo(Context context) {
        BatteryInfo info = new BatteryInfo();
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            info.level = (int) ((level / (float) scale) * 100);

            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            info.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            int temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
            info.temperatureCelsius = temp / 10.0f;

            info.statusDescription = String.format(Locale.getDefault(),
                    "Battery is at %d%%. %s. Temperature is %.1f°C.",
                    info.level,
                    info.isCharging ? "Charging right now" : "Not charging",
                    info.temperatureCelsius);
        } else {
            info.level = 0;
            info.isCharging = false;
            info.temperatureCelsius = 25.0f;
            info.statusDescription = "Battery info unavailable";
        }
        return info;
    }

    public static StorageInfo getStorageInfo() {
        StorageInfo info = new StorageInfo();
        try {
            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long availableBlocks = stat.getAvailableBlocksLong();

            info.totalBytes = totalBlocks * blockSize;
            info.availableBytes = availableBlocks * blockSize;
            info.usedBytes = info.totalBytes - info.availableBytes;

            double totalGb = info.totalBytes / (1024.0 * 1024.0 * 1024.0);
            double freeGb = info.availableBytes / (1024.0 * 1024.0 * 1024.0);
            double usedGb = info.usedBytes / (1024.0 * 1024.0 * 1024.0);

            info.formattedReport = String.format(Locale.getDefault(),
                    "Storage: %.1f GB free out of %.1f GB total (%.1f GB used).",
                    freeGb, totalGb, usedGb);
        } catch (Exception e) {
            info.formattedReport = "Storage details could not be read.";
        }
        return info;
    }

    public static NetworkInfo getNetworkInfo(Context context) {
        NetworkInfo info = new NetworkInfo();
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                android.net.Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    if (caps != null) {
                        info.isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
                        info.isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                        info.isCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
                    }
                }
            }
        } catch (Exception ignored) {}

        if (info.isWifi) {
            info.statusDescription = "Connected via Wi-Fi network.";
        } else if (info.isCellular) {
            info.statusDescription = "Connected via Mobile Data.";
        } else {
            info.statusDescription = "No active internet connection.";
        }
        return info;
    }

    public static String getRamInfo(Context context) {
        try {
            ActivityManager actManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
            if (actManager != null) {
                actManager.getMemoryInfo(memInfo);
                double totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0);
                double availRamGb = memInfo.availMem / (1024.0 * 1024.0 * 1024.0);
                return String.format(Locale.getDefault(),
                        "RAM: %.1f GB available out of %.1f GB.",
                        availRamGb, totalRamGb);
            }
        } catch (Exception ignored) {}
        return "RAM info unavailable.";
    }
}
