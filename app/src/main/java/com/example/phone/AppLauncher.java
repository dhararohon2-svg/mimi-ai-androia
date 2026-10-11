package com.example.phone;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppLauncher {

    private static final Map<String, String> BENGALI_APP_MAP = new HashMap<>();

    static {
        BENGALI_APP_MAP.put("ইউটিউব", "youtube");
        BENGALI_APP_MAP.put("ফেসবুক", "facebook");
        BENGALI_APP_MAP.put("হোয়াটসঅ্যাপ", "whatsapp");
        BENGALI_APP_MAP.put("ক্যালকুলেটর", "calculator");
        BENGALI_APP_MAP.put("ক্যালেন্ডার", "calendar");
        BENGALI_APP_MAP.put("ক্যামেরা", "camera");
        BENGALI_APP_MAP.put("সেটিংস", "settings");
        BENGALI_APP_MAP.put("ঘড়ি", "clock");
        BENGALI_APP_MAP.put("গ্যালারি", "gallery");
        BENGALI_APP_MAP.put("ছবি", "photos");
        BENGALI_APP_MAP.put("ক্রোম", "chrome");
        BENGALI_APP_MAP.put("ম্যাপ", "maps");
        BENGALI_APP_MAP.put("মেসেজ", "messages");
        BENGALI_APP_MAP.put("ফোন", "phone");
        BENGALI_APP_MAP.put("ডায়ালার", "dialer");
        BENGALI_APP_MAP.put("প্লে স্টোর", "play store");
        BENGALI_APP_MAP.put("ড্রাইভ", "drive");
        BENGALI_APP_MAP.put("জিমেইল", "gmail");
        BENGALI_APP_MAP.put("ফাইল", "files");
        BENGALI_APP_MAP.put("টেলিগ্রাম", "telegram");
        BENGALI_APP_MAP.put("টুইটার", "twitter");
        BENGALI_APP_MAP.put("ইনস্টাগ্রাম", "instagram");
        BENGALI_APP_MAP.put("মিউজিক", "music");
    }

    public static class LaunchResult {
        public boolean success;
        public String appName;
        public String packageName;
        public String message;

        public LaunchResult(boolean success, String appName, String packageName, String message) {
            this.success = success;
            this.appName = appName;
            this.packageName = packageName;
            this.message = message;
        }
    }

    public static LaunchResult openApp(Context context, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new LaunchResult(false, "", "", "No app name specified");
        }

        String search = query.trim().toLowerCase();
        for (Map.Entry<String, String> entry : BENGALI_APP_MAP.entrySet()) {
            if (search.contains(entry.getKey())) {
                search = entry.getValue();
                break;
            }
        }

        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        ResolveInfo bestMatch = null;
        String bestLabel = "";

        // First pass: exact or prefix match
        for (ResolveInfo info : apps) {
            CharSequence labelSeq = info.loadLabel(pm);
            String label = labelSeq != null ? labelSeq.toString().toLowerCase() : "";
            String pkg = info.activityInfo.packageName.toLowerCase();

            if (label.equals(search) || pkg.endsWith("." + search) || label.startsWith(search)) {
                bestMatch = info;
                bestLabel = labelSeq != null ? labelSeq.toString() : pkg;
                break;
            }
        }

        // Second pass: contains match
        if (bestMatch == null) {
            for (ResolveInfo info : apps) {
                CharSequence labelSeq = info.loadLabel(pm);
                String label = labelSeq != null ? labelSeq.toString().toLowerCase() : "";
                String pkg = info.activityInfo.packageName.toLowerCase();

                if (label.contains(search) || pkg.contains(search)) {
                    bestMatch = info;
                    bestLabel = labelSeq != null ? labelSeq.toString() : pkg;
                    break;
                }
            }
        }

        if (bestMatch != null) {
            String pkgName = bestMatch.activityInfo.packageName;
            Intent launchIntent = pm.getLaunchIntentForPackage(pkgName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                return new LaunchResult(true, bestLabel, pkgName, "Opened " + bestLabel);
            }
        }

        return new LaunchResult(false, query, "", "App not found for \"" + query + "\"");
    }
}
