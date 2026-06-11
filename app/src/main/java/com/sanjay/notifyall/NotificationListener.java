package com.sanjay.notifyall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationListener extends NotificationListenerService {

    private static final String TAG = "NotifyListener";
    private static final String CHANNEL_ID = "notifyall_service";
    private static final int FOREGROUND_NOTIF_ID = 1;
    private static final String CACHE_KEY_LAST_NOTIF = "last_notification_cache";

    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("NotifyAll", MODE_PRIVATE);
        Log.d(TAG, "NotificationListener created");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Listener connected");
        startForegroundWithNotification();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "Listener disconnected — requesting rebind");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(new android.content.ComponentName(this, NotificationListener.class));
        }
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "NotifyAll Service", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps NotifyAll running in the background");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openApp,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE : 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("NotifyAll is running")
                .setContentText("Monitoring selected apps for notifications")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(FOREGROUND_NOTIF_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FOREGROUND_NOTIF_ID, notification);
        }
        Log.d(TAG, "Foreground notification started");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        Log.d(TAG, "📩 Notification received from: " + packageName);

        // 1. BLOCK Android System overlay warning notification
        if (packageName.equals("android")) {
            Notification notif = sbn.getNotification();
            String title = safeString(notif.extras, Notification.EXTRA_TITLE);
            if (title != null && (title.contains("displaying over other apps") || title.contains("Donation Alert"))) {
                Log.d(TAG, "🚫 Blocked system overlay warning notification");
                return;
            }
        }

        if (!isAppSelected(packageName)) {
            Log.d(TAG, "App not selected, ignoring: " + packageName);
            return;
        }

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;

        // Debug: dump extras
        logAllExtras(packageName, extras);

        // Extract all possible text fields
        String title    = safeString(extras, Notification.EXTRA_TITLE);
        String titleBig = safeString(extras, Notification.EXTRA_TITLE_BIG);
        String text     = safeString(extras, Notification.EXTRA_TEXT);
        String bigText  = safeCharSequence(extras, Notification.EXTRA_BIG_TEXT);
        String subText  = safeString(extras, Notification.EXTRA_SUB_TEXT);
        String infoText = safeString(extras, Notification.EXTRA_INFO_TEXT);
        String summaryText = safeString(extras, Notification.EXTRA_SUMMARY_TEXT);

        List<String> inboxLines = extractInboxLines(extras);
        List<String> messagingLines = extractMessagingLines(extras);

        // Build the best title (use big if available)
        String bestTitle = titleBig.isEmpty() ? title : titleBig;

        // --- Build RAW TEXT (concatenate everything without parsing) ---
        StringBuilder rawBuilder = new StringBuilder();
        if (!bestTitle.isEmpty()) rawBuilder.append(bestTitle);
        if (!text.isEmpty()) {
            if (rawBuilder.length() > 0) rawBuilder.append("\n");
            rawBuilder.append(text);
        }
        if (!bigText.isEmpty()) {
            if (rawBuilder.length() > 0) rawBuilder.append("\n");
            rawBuilder.append(bigText);
        }
        for (String line : inboxLines) {
            if (rawBuilder.length() > 0) rawBuilder.append("\n");
            rawBuilder.append(line);
        }
        for (String line : messagingLines) {
            if (rawBuilder.length() > 0) rawBuilder.append("\n");
            rawBuilder.append(line);
        }
        if (!subText.isEmpty()) {
            if (rawBuilder.length() > 0) rawBuilder.append("\n");
            rawBuilder.append(subText);
        }
        if (!infoText.isEmpty()) {
            if (rawBuilder.length() > 0) rawBuilder.append("\n");
            rawBuilder.append(infoText);
        }
        if (!summaryText.isEmpty()) {
            if (rawBuilder.length() > 0) rawBuilder.append("\n");
            rawBuilder.append(summaryText);
        }
        String rawText = rawBuilder.toString().trim();
        Log.d(TAG, "📝 Raw text built (length " + rawText.length() + "): " +
                (rawText.length() > 100 ? rawText.substring(0, 100) + "..." : rawText));

        // --- Build parsed data for webhook ---
        String combinedForParsing = bestTitle + " " + rawText;
        NotificationData data = new NotificationData();
        data.appPackage = packageName;
        data.appName    = getAppName(packageName);
        data.title      = bestTitle;
        data.fullText   = rawText;
        data.rawText    = rawText;
        data.timestamp  = System.currentTimeMillis();

        // Check if this app has regex parsing enabled
        boolean useRegexForThisApp = isRegexEnabledForApp(packageName);
        if (useRegexForThisApp && isTransactionNotification(combinedForParsing)) {
            data.amount = extractAmount(combinedForParsing);
            data.name   = extractName(combinedForParsing);
            data.note   = extractNote(combinedForParsing);
            Log.d(TAG, "🔍 Regex applied → amount=" + data.amount + " name=" + data.name + " note=" + data.note);
        } else {
            data.amount = "";
            data.name = "";
            data.note = "";
            Log.d(TAG, "🚫 Regex skipped for app: " + packageName);
        }

        // Save to cache (for UI later)
        saveToCache(data.toJson());

        // Determine overlay mode (global setting: "raw" or "parsed")
        String overlayMode = prefs.getString("overlay_mode", "parsed");
        boolean showOverlay = prefs.getBoolean("show_overlay", true);
        if (showOverlay) {
            showOverlay(data, overlayMode, useRegexForThisApp);
        }

        sendWebhook(data);
        broadcastNotification(data);
    }

    private void saveToCache(String json) {
        prefs.edit().putString(CACHE_KEY_LAST_NOTIF, json).apply();
        Log.d(TAG, "💾 Notification cached");
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) { /* no-op */ }

    // --- Helper methods (same as before, kept for brevity) ---

    private boolean isRegexEnabledForApp(String packageName) {
        String saved = prefs.getString("regex_apps", "{}");
        try {
            JSONObject obj = new JSONObject(saved);
            return obj.optBoolean(packageName, false);
        } catch (Exception e) {
            return false;
        }
    }

    private String safeString(Bundle b, String key) {
        if (b == null) return "";
        String v = b.getString(key, "");
        return v != null ? v : "";
    }

    private String safeCharSequence(Bundle b, String key) {
        if (b == null) return "";
        CharSequence v = b.getCharSequence(key);
        return v != null ? v.toString() : "";
    }

    private List<String> extractInboxLines(Bundle extras) {
        List<String> lines = new ArrayList<>();
        if (extras == null) return lines;
        CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (textLines != null) {
            for (CharSequence cs : textLines) {
                if (cs != null && cs.length() > 0) lines.add(cs.toString());
            }
        }
        return lines;
    }

    private List<String> extractMessagingLines(Bundle extras) {
        List<String> lines = new ArrayList<>();
        if (extras == null) return lines;
        try {
            android.os.Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages == null) return lines;
            for (android.os.Parcelable p : messages) {
                if (p instanceof Bundle) {
                    Bundle msg = (Bundle) p;
                    CharSequence text = msg.getCharSequence("text");
                    if (text != null && text.length() > 0) {
                        lines.add(text.toString());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "MessagingStyle extraction failed", e);
        }
        return lines;
    }

    private void logAllExtras(String pkg, Bundle extras) {
        if (extras == null) return;
        Log.d(TAG, "=== Extras for " + pkg + " ===");
        for (String key : extras.keySet()) {
            Object val = extras.get(key);
            Log.d(TAG, "  " + key + " = " + (val != null ? val.toString() : "null"));
        }
    }

    private boolean isTransactionNotification(String text) {
        String lower = text.toLowerCase();
        return lower.contains("paid") || lower.contains("received") ||
                lower.contains("₹")   || lower.contains("rs.")      ||
                lower.contains("inr") || lower.contains("money")    ||
                lower.contains("payment") || lower.contains("transfer") ||
                lower.contains("credit")  || lower.contains("debit")    ||
                lower.contains("debited") || lower.contains("credited")  ||
                lower.contains("sent")    || lower.contains("upi")       ||
                lower.contains("bank")    || lower.contains("account")   ||
                lower.contains("txn")     || lower.contains("utr");
    }

    private String extractAmount(String text) {
        Pattern p1 = Pattern.compile("₹\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(text);
        if (m1.find()) return "₹" + m1.group(1).replace(",", "");

        Pattern p2 = Pattern.compile("(?:Rs\\.?|INR)\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) return "₹" + m2.group(1).replace(",", "");

        Pattern p3 = Pattern.compile("(\\d{1,7}[,\\d]*\\.\\d{2})");
        Matcher m3 = p3.matcher(text);
        if (m3.find()) return "₹" + m3.group(1).replace(",", "");

        return "";
    }

    private String extractName(String text) {
        Pattern vpa = Pattern.compile("(?:to|from)\\s+([\\w.]+@[\\w]+)", Pattern.CASE_INSENSITIVE);
        Matcher vpaM = vpa.matcher(text);
        if (vpaM.find()) return vpaM.group(1).trim();

        String[] patterns = {
                "you\\s+received\\s+[\\S]+\\s+from\\s+([A-Za-z][A-Za-z .]{1,38}?)(?:\\s*[.|,]|$)",
                "you\\s+paid\\s+[\\S]+\\s+to\\s+([A-Za-z][A-Za-z .]{1,38}?)(?:\\s*[.|,]|$)",
                "paid(?:\\s+(?:to|by))\\s+([A-Za-z][A-Za-z .]{1,38}?)(?:\\s*[|\\-,]|\\s+(?:using|via|on|for|Rs|₹)|$)",
                "received\\s+from\\s+([A-Za-z][A-Za-z .]{1,38}?)(?:\\s*[|\\-,]|\\s+(?:using|via|on|for|Rs|₹)|$)",
                "sent\\s+to\\s+([A-Za-z][A-Za-z .]{1,38}?)(?:\\s*[|\\-,]|\\s+(?:using|via|on|for|Rs|₹)|$)",
                "payment\\s+(?:to|from)\\s+([A-Za-z][A-Za-z .]{1,38}?)(?:\\s*[|\\-,]|\\s+(?:using|via|on|for|Rs|₹)|$)",
                "(?:to|for)\\s+([A-Za-z][A-Za-z .]{1,38}?)(?:\\s*[|\\-,]|\\s+(?:using|via|Rs|₹)|$)",
                "from\\s+([A-Za-z][A-Za-z .]{1,38}?)(?:\\s*[|\\-,]|\\s+(?:using|via|Rs|₹)|$)"
        };

        for (String pat : patterns) {
            Pattern p = Pattern.compile(pat, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(text);
            if (m.find()) {
                String name = m.group(1).trim();
                if (name.length() > 1 && name.length() < 40
                        && !name.matches(".*\\d.*")
                        && !name.equalsIgnoreCase("upi")
                        && !name.equalsIgnoreCase("bank")) {
                    return name;
                }
            }
        }
        return "";
    }

    private String extractNote(String text) {
        Pattern noteLabel = Pattern.compile("(?:note|remark|desc(?:ription)?|purpose|ref(?:erence)?)[\\s]*[:\\-]?\\s*([^\\n\\r|,]{1,60})", Pattern.CASE_INSENSITIVE);
        Matcher nm = noteLabel.matcher(text);
        if (nm.find()) return nm.group(1).trim();

        Pattern utrPat = Pattern.compile("(?:UTR|Txn(?:\\s*ID)?|Transaction\\s*(?:ID|No\\.?)?)[\\s:#]*([A-Z0-9]{8,25})", Pattern.CASE_INSENSITIVE);
        Matcher um = utrPat.matcher(text);
        if (um.find()) return "UTR: " + um.group(1).trim();

        return "";
    }

    private boolean isAppSelected(String packageName) {
        if (packageName.equals("android")) return true;
        String saved = prefs.getString("selected_apps", "[]");
        try {
            JSONArray arr = new JSONArray(saved);
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getString(i).equals(packageName)) return true;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    private String getAppName(String packageName) {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(packageName, 0);
            return getPackageManager().getApplicationLabel(ai).toString();
        } catch (Exception e) { return packageName; }
    }

    private void showOverlay(NotificationData data, String overlayMode, boolean useRegexForThisApp) {
        Intent intent = new Intent(this, OverlayService.class);
        intent.putExtra("notification_data", data.toJson());
        intent.putExtra("overlay_mode", overlayMode);
        intent.putExtra("use_regex", useRegexForThisApp);
        startService(intent);
        Log.d(TAG, "🖼️ Overlay triggered with mode=" + overlayMode + ", regex=" + useRegexForThisApp);
    }

    private void sendWebhook(NotificationData data) {
        boolean webhookEnabled = prefs.getBoolean("webhook_enabled", false);
        if (!webhookEnabled) return;

        String jwtToken   = prefs.getString("streamelements_jwt", "");
        String accountId  = prefs.getString("streamelements_account_id", "");
        if (jwtToken.isEmpty() || accountId.isEmpty()) return;

        Intent intent = new Intent(this, WebhookService.class);
        intent.putExtra("jwt_token", jwtToken);
        intent.putExtra("account_id", accountId);
        intent.putExtra("notification_data", data.toJson());
        startService(intent);
        Log.d(TAG, "🌐 Webhook sent for " + data.appName);
    }

    private void broadcastNotification(NotificationData data) {
        Intent intent = new Intent("NOTIFICATION_CAPTURED");
        intent.putExtra("data", data.toJson());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        Log.d(TAG, "📡 Broadcast sent to UI");
        // Force refresh UI via resend
        Intent resendIntent = new Intent("RESEND_LAST_NOTIFICATION");
        LocalBroadcastManager.getInstance(this).sendBroadcast(resendIntent);
    }

    // --- Data class ---
    public static class NotificationData {
        public String appPackage;
        public String appName;
        public String title;
        public String fullText;
        public String rawText;
        public String amount;
        public String name;
        public String note;
        public long   timestamp;

        public String toJson() {
            try {
                JSONObject json = new JSONObject();
                json.put("appPackage", appPackage);
                json.put("appName",    appName);
                json.put("title",      title);
                json.put("fullText",   fullText);
                json.put("rawText",    rawText);
                json.put("amount",     amount);
                json.put("name",       name);
                json.put("note",       note);
                json.put("timestamp",  timestamp);
                return json.toString();
            } catch (Exception e) { return "{}"; }
        }

        public static NotificationData fromJson(String json) {
            NotificationData data = new NotificationData();
            try {
                JSONObject obj = new JSONObject(json);
                data.appPackage = obj.optString("appPackage");
                data.appName    = obj.optString("appName");
                data.title      = obj.optString("title");
                data.fullText   = obj.optString("fullText");
                data.rawText    = obj.optString("rawText");
                data.amount     = obj.optString("amount");
                data.name       = obj.optString("name");
                data.note       = obj.optString("note");
                data.timestamp  = obj.optLong("timestamp");
            } catch (Exception e) { /* no-op */ }
            return data;
        }
    }
}