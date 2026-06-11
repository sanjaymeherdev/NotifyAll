package com.sanjay.notifyall;

import android.os.Handler;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private WebView webView;
    private static final String CACHE_KEY_LAST_NOTIF = "last_notification_cache";

    private final BroadcastReceiver notificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String data = intent.getStringExtra("data");
            Log.d(TAG, "Broadcast received, data length: " + (data != null ? data.length() : 0));
            if (data != null && webView != null) {
                // Proper JSON string escaping for JavaScript
                String escaped = data.replace("\\", "\\\\")
                        .replace("'", "\\'")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
                final String jsCode = "javascript:onNotificationCaptured('" + escaped + "')";
                webView.post(() -> {
                    webView.loadUrl(jsCode);
                    Log.d(TAG, "Executed JS: " + jsCode.substring(0, Math.min(100, jsCode.length())));
                });
            } else {
                Log.w(TAG, "Broadcast received but webView is null or data missing");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                sendStatusToWebView();
                // Also send the last cached notification if any
                sendCachedNotificationToWebView();
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void sendCachedNotificationToWebView() {
        String cached = getSharedPreferences("NotifyAll", MODE_PRIVATE).getString(CACHE_KEY_LAST_NOTIF, null);
        if (cached != null && !cached.isEmpty()) {
            Log.d(TAG, "Sending cached notification to WebView");
            webView.post(() -> {
                String escaped = cached.replace("\\", "\\\\").replace("'", "\\'");
                webView.loadUrl("javascript:onNotificationCaptured('" + escaped + "')");
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sendStatusToWebView();
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationReceiver, new IntentFilter("NOTIFICATION_CAPTURED"));

        // Auto refresh UI with cached notification when app comes to foreground
        new Handler().postDelayed(() -> {
            if (webView != null) {
                webView.loadUrl("javascript:resendLastNotification()");
            }
        }, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resendReceiver);
    }

    private void sendStatusToWebView() {
        boolean hasOverlay = Settings.canDrawOverlays(this);
        boolean hasNotification = isNotificationAccessGranted();
        webView.post(() -> {
            webView.loadUrl("javascript:setPermissions(" + hasOverlay + ", " + hasNotification + ")");
            webView.loadUrl("javascript:updateServiceStatus(" + hasNotification + ")");
            String overlayMode = getSharedPreferences("NotifyAll", MODE_PRIVATE).getString("overlay_mode", "parsed");
            webView.loadUrl("javascript:setOverlayMode('" + overlayMode + "')");
            String regexAppsJson = getSharedPreferences("NotifyAll", MODE_PRIVATE).getString("regex_apps", "{}");
            webView.loadUrl("javascript:setRegexApps('" + regexAppsJson.replace("'", "\\'") + "')");
        });
    }
    private final BroadcastReceiver resendReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            runOnUiThread(() -> {
                if (webView != null) {
                    webView.loadUrl("javascript:resendLastNotification()");
                }
            });
        }
    };
    private boolean isNotificationAccessGranted() {
        String packageName = getPackageName();
        try {
            String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return enabled != null && enabled.contains(packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private void updateServiceStatusInWebView(boolean isActive) {
        webView.loadUrl("javascript:updateServiceStatus(" + isActive + ")");
    }

    public class WebAppInterface {
        @JavascriptInterface public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public String getInstalledApps() {
            List<ApplicationInfo> packages = getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
            JSONArray appsArray = new JSONArray();

            // UPI app package names to include even if system apps
            String[] upiApps = {"com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "com.amazon.mShop.android.shopping",
                    "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "com.amazon.mShop.android.shopping",
                    "in.amazon.mShop.android.shopping", "com.whatsapp", "com.whatsapp.w4b", "com.truecaller", "com.paytm",
                    "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "com.amazon.mShop.android.shopping",
                    "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "com.amazon.mShop.android.shopping"};

            for (ApplicationInfo appInfo : packages) {
                String packageName = appInfo.packageName;
                boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                boolean isUpiApp = false;

                // Check if this is a known UPI app
                for (String upi : upiApps) {
                    if (packageName.contains(upi) || packageName.toLowerCase().contains("pay") ||
                            packageName.toLowerCase().contains("upi") || packageName.toLowerCase().contains("phonepe") ||
                            packageName.toLowerCase().contains("gpay") || packageName.toLowerCase().contains("tez")) {
                        isUpiApp = true;
                        break;
                    }
                }

                // Include: user installed apps OR UPI system apps
                if (!isSystemApp || isUpiApp) {
                    try {
                        JSONObject app = new JSONObject();
                        app.put("name", getPackageManager().getApplicationLabel(appInfo).toString());
                        app.put("packageName", packageName);
                        appsArray.put(app);
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
            return appsArray.toString();
        }

        @JavascriptInterface public void requestOverlayPermission() {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName())));
                }
            });
        }

        @JavascriptInterface public void requestNotificationAccess() {
            runOnUiThread(() -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        }

        @JavascriptInterface public void startNotificationService() {
            runOnUiThread(() -> {
                if (!isNotificationAccessGranted()) {
                    Toast.makeText(MainActivity.this, "Please grant notification access first", Toast.LENGTH_LONG).show();
                    requestNotificationAccess();
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        NotificationListener.requestRebind(new android.content.ComponentName(MainActivity.this, NotificationListener.class));
                    }
                    Toast.makeText(MainActivity.this, "Notification service active", Toast.LENGTH_SHORT).show();
                    updateServiceStatusInWebView(true);
                }
            });
        }

        @JavascriptInterface public void stopNotificationService() {
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "To stop: revoke Notification Access in Settings", Toast.LENGTH_LONG).show();
                updateServiceStatusInWebView(isNotificationAccessGranted());
            });
        }

        @JavascriptInterface public void saveSettings(String key, String value) {
            getSharedPreferences("NotifyAll", MODE_PRIVATE).edit().putString(key, value).apply();
        }

        @JavascriptInterface public String getSettings(String key) {
            return getSharedPreferences("NotifyAll", MODE_PRIVATE).getString(key, "");
        }

        @JavascriptInterface public void setOverlayMode(String mode) {
            getSharedPreferences("NotifyAll", MODE_PRIVATE).edit().putString("overlay_mode", mode).apply();
        }

        @JavascriptInterface public String getOverlayMode() {
            return getSharedPreferences("NotifyAll", MODE_PRIVATE).getString("overlay_mode", "parsed");
        }

        @JavascriptInterface public void setRegexForApp(String packageName, boolean enabled) {
            SharedPreferences prefs = getSharedPreferences("NotifyAll", MODE_PRIVATE);
            String currentJson = prefs.getString("regex_apps", "{}");
            try {
                JSONObject obj = new JSONObject(currentJson);
                obj.put(packageName, enabled);
                prefs.edit().putString("regex_apps", obj.toString()).apply();
            } catch (Exception e) { e.printStackTrace(); }
        }

        @JavascriptInterface public boolean getRegexForApp(String packageName) {
            SharedPreferences prefs = getSharedPreferences("NotifyAll", MODE_PRIVATE);
            String currentJson = prefs.getString("regex_apps", "{}");
            try {
                JSONObject obj = new JSONObject(currentJson);
                return obj.optBoolean(packageName, false);
            } catch (Exception e) { return false; }
        }

        @JavascriptInterface public String getRegexApps() {
            return getSharedPreferences("NotifyAll", MODE_PRIVATE).getString("regex_apps", "{}");
        }

        @JavascriptInterface public void setShowOverlay(boolean show) {
            getSharedPreferences("NotifyAll", MODE_PRIVATE).edit().putBoolean("show_overlay", show).apply();
        }

        @JavascriptInterface public String getShowOverlay() {
            return String.valueOf(getSharedPreferences("NotifyAll", MODE_PRIVATE).getBoolean("show_overlay", true));
        }

        @JavascriptInterface public void setWebhookEnabled(boolean enabled) {
            getSharedPreferences("NotifyAll", MODE_PRIVATE).edit().putBoolean("webhook_enabled", enabled).apply();
        }

        @JavascriptInterface public String getWebhookEnabled() {
            return String.valueOf(getSharedPreferences("NotifyAll", MODE_PRIVATE).getBoolean("webhook_enabled", false));
        }

        @JavascriptInterface public void testWebhookOnly(String jsonData) {
            SharedPreferences prefs = getSharedPreferences("NotifyAll", MODE_PRIVATE);
            String jwtToken = prefs.getString("streamelements_jwt", "");
            String accountId = prefs.getString("streamelements_account_id", "");
            if (jwtToken.isEmpty() || accountId.isEmpty()) return;
            Intent intent = new Intent(MainActivity.this, WebhookService.class);
            intent.putExtra("jwt_token", jwtToken);
            intent.putExtra("account_id", accountId);
            intent.putExtra("notification_data", jsonData);
            startService(intent);
        }

        @JavascriptInterface
        public void triggerTestNotification(String jsonData) {
            runOnUiThread(() -> {
                SharedPreferences prefs = getSharedPreferences("NotifyAll", MODE_PRIVATE);
                boolean webhookEnabled = prefs.getBoolean("webhook_enabled", false);
                if (webhookEnabled) {
                    String jwtToken = prefs.getString("streamelements_jwt", "");
                    String accountId = prefs.getString("streamelements_account_id", "");
                    if (!jwtToken.isEmpty() && !accountId.isEmpty()) {
                        Intent webhookIntent = new Intent(MainActivity.this, WebhookService.class);
                        webhookIntent.putExtra("jwt_token", jwtToken);
                        webhookIntent.putExtra("account_id", accountId);
                        webhookIntent.putExtra("notification_data", jsonData);
                        startService(webhookIntent);
                    }
                }

                boolean showOverlay = prefs.getBoolean("show_overlay", true);
                if (showOverlay) {
                    // For test, we avoid overlay to prevent system warning
                    // Only broadcast to UI log
                    Intent broadcastIntent = new Intent("NOTIFICATION_CAPTURED");
                    broadcastIntent.putExtra("data", jsonData);
                    LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(broadcastIntent);
                    Toast.makeText(MainActivity.this, "Test notification added to log", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(MainActivity.this, "Overlay disabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        @JavascriptInterface
        public void resendLastNotification() {
            String cached = getSharedPreferences("NotifyAll", MODE_PRIVATE).getString(CACHE_KEY_LAST_NOTIF, null);
            if (cached == null || cached.isEmpty()) {
                showToast("No cached notification");
                return;
            }
            // Resend to UI log
            Intent broadcastIntent = new Intent("NOTIFICATION_CAPTURED");
            broadcastIntent.putExtra("data", cached);
            LocalBroadcastManager.getInstance(MainActivity.this).sendBroadcast(broadcastIntent);

            // Optionally show overlay again? (optional - but may cause warning if app in foreground)
            boolean showOverlay = getSharedPreferences("NotifyAll", MODE_PRIVATE).getBoolean("show_overlay", true);
            if (showOverlay) {
                // Use overlay service but with a delay and only if app is backgrounded?
                // For simplicity, we just log it.
                showToast("Last notification resent to log");
            } else {
                showToast("Last notification resent to log (overlay off)");
            }
        }

        @JavascriptInterface
        public void notifyWebViewReady() {
            Log.d(TAG, "WebView reported ready");
            sendCachedNotificationToWebView();
        }

        public void onBackPressed() {
            if (webView.canGoBack()) webView.goBack();
            else getOnBackPressedDispatcher().onBackPressed();
        }
    }
}