package com.sanjay.notifyall;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.content.SharedPreferences;
import org.json.JSONObject;
import android.util.Log;

public class OverlayService extends Service {

    private static final String TAG = "OverlayService";
    private WindowManager windowManager;
    private LinearLayout overlayView;
    private WebView webView;
    private Handler autoHideHandler;
    private Runnable autoHideRunnable;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        autoHideHandler = new Handler();
        Log.d(TAG, "OverlayService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e(TAG, "No overlay permission, stopping");
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra("notification_data")) {
            String notificationJson = intent.getStringExtra("notification_data");
            String overlayMode = intent.getStringExtra("overlay_mode");
            boolean useRegex = intent.getBooleanExtra("use_regex", false);
            if (overlayMode == null) {
                SharedPreferences prefs = getSharedPreferences("NotifyAll", MODE_PRIVATE);
                overlayMode = prefs.getString("overlay_mode", "parsed");
            }
            Log.d(TAG, "Showing overlay - mode=" + overlayMode + ", useRegex=" + useRegex);
            showNotificationOverlay(notificationJson, overlayMode, useRegex);
        } else {
            Log.w(TAG, "Intent has no notification_data");
        }
        return START_NOT_STICKY;
    }

    private void showNotificationOverlay(String notificationJson, String overlayMode, boolean useRegex) {
        if (overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception e) {}
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        overlayView = (LinearLayout) inflater.inflate(R.layout.overlay_notification, null);
        webView = overlayView.findViewById(R.id.webview_notification);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        // Parse notification data
        NotificationListener.NotificationData data = NotificationListener.NotificationData.fromJson(notificationJson);
        String html;
        if ("raw".equals(overlayMode) || !useRegex) {
            // RAW MODE: show raw text (full untouched notification content)
            html = getRawNotificationHtml(data);
            Log.d(TAG, "Using RAW overlay mode");
        } else {
            // PARSED MODE: use formatted amount/name/note if available
            html = getParsedNotificationHtml(data);
            Log.d(TAG, "Using PARSED overlay mode");
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 16;
        params.y = 80;

        try {
            windowManager.addView(overlayView, params);
            Log.d(TAG, "Overlay view added to window");
        } catch (Exception e) {
            Log.e(TAG, "Failed to add overlay", e);
            stopSelf();
            return;
        }

        if (autoHideRunnable != null) autoHideHandler.removeCallbacks(autoHideRunnable);
        autoHideRunnable = () -> {
            if (overlayView != null) {
                try { windowManager.removeView(overlayView); } catch (Exception e) {}
                overlayView = null;
            }
            stopSelf();
        };
        autoHideHandler.postDelayed(autoHideRunnable, 4000);
    }

    private String getRawNotificationHtml(NotificationListener.NotificationData data) {
        SharedPreferences prefs = getSharedPreferences("NotifyAll", MODE_PRIVATE);
        String savedDesign = prefs.getString("design", "");
        String bgColor = "#2C2C2C", textColor = "#FFFFFF", borderRadius = "12";
        if (!savedDesign.isEmpty()) {
            try {
                JSONObject design = new JSONObject(savedDesign);
                bgColor = design.optString("bgColor", "#2C2C2C");
                textColor = design.optString("textColor", "#FFFFFF");
                borderRadius = design.optString("borderRadius", "12");
            } catch (Exception e) {}
        }

        String displayText = (data.rawText != null && !data.rawText.isEmpty()) ? data.rawText : data.fullText;
        if (displayText == null || displayText.isEmpty()) {
            displayText = data.title;
        }
        if (displayText == null || displayText.isEmpty()) {
            displayText = "No content";
        }

        String html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>body{font-family:sans-serif;background:" + bgColor + ";color:" + textColor + ";" +
                "border-radius:" + borderRadius + "px;padding:12px 16px;margin:0;max-width:320px;}" +
                ".app{font-size:11px;color:#aaa;margin-bottom:6px;}" +
                ".text{font-size:14px;line-height:1.4;white-space:pre-wrap;word-break:break-word;}" +
                "</style></head><body>" +
                "<div class='app'>" + escapeHtml(data.appName) + "</div>" +
                "<div class='text'>" + escapeHtml(displayText) + "</div>" +
                "</body></html>";
        return html;
    }

    private String getParsedNotificationHtml(NotificationListener.NotificationData data) {
        SharedPreferences prefs = getSharedPreferences("NotifyAll", MODE_PRIVATE);
        String savedDesign = prefs.getString("design", "");
        String bgColor = "#2C2C2C", textColor = "#FFFFFF", amountColor = "#4CAF50", borderRadius = "12";
        if (!savedDesign.isEmpty()) {
            try {
                JSONObject design = new JSONObject(savedDesign);
                bgColor = design.optString("bgColor", "#2C2C2C");
                textColor = design.optString("textColor", "#FFFFFF");
                amountColor = design.optString("amountColor", "#4CAF50");
                borderRadius = design.optString("borderRadius", "12");
            } catch (Exception e) {}
        }

        boolean hasTransaction = (data.amount != null && !data.amount.isEmpty()) ||
                (data.name != null && !data.name.isEmpty()) ||
                (data.note != null && !data.note.isEmpty());
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<style>body{font-family:sans-serif;background:").append(bgColor).append(";color:").append(textColor);
        html.append(";border-radius:").append(borderRadius).append("px;padding:12px 16px;margin:0;max-width:320px;}");
        html.append(".app{font-size:11px;color:#aaa;margin-bottom:4px;}");
        html.append(".amount{font-size:22px;font-weight:bold;color:").append(amountColor).append(";margin-bottom:4px;}");
        html.append(".name{font-size:14px;font-weight:500;margin-bottom:4px;}");
        html.append(".note{font-size:11px;color:#aaa;}");
        html.append(".raw{font-size:13px;line-height:1.4;white-space:pre-wrap;}</style></head><body>");
        html.append("<div class='app'>").append(escapeHtml(data.appName)).append("</div>");

        if (hasTransaction) {
            if (data.amount != null && !data.amount.isEmpty()) html.append("<div class='amount'>").append(escapeHtml(data.amount)).append("</div>");
            if (data.name != null && !data.name.isEmpty()) html.append("<div class='name'>").append(escapeHtml(data.name)).append("</div>");
            if (data.note != null && !data.note.isEmpty()) html.append("<div class='note'>📝 ").append(escapeHtml(data.note)).append("</div>");
        } else {
            String raw = (data.rawText != null && !data.rawText.isEmpty()) ? data.rawText : data.fullText;
            if (raw == null || raw.isEmpty()) raw = data.title;
            if (raw == null) raw = "";
            html.append("<div class='raw'>").append(escapeHtml(raw)).append("</div>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (autoHideRunnable != null) autoHideHandler.removeCallbacks(autoHideRunnable);
        if (overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Exception e) {}
        }
        Log.d(TAG, "OverlayService destroyed");
    }
}