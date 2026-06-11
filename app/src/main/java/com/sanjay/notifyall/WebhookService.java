package com.sanjay.notifyall;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class WebhookService extends IntentService {

    private static final String TAG = "WebhookService";

    public WebhookService() {
        super("WebhookService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;

        String jwtToken = intent.getStringExtra("jwt_token");
        String accountId = intent.getStringExtra("account_id");
        String notificationData = intent.getStringExtra("notification_data");

        if (jwtToken == null || jwtToken.isEmpty()) {
            Log.e(TAG, "No JWT token provided");
            return;
        }
        if (accountId == null || accountId.isEmpty()) {
            Log.e(TAG, "No account ID provided");
            return;
        }
        if (notificationData == null) return;

        sendToStreamElements(jwtToken, accountId, notificationData);
    }

    private void sendToStreamElements(String jwtToken, String accountId, String notificationData) {
        android.util.Log.e("WEBHOOK_TEST", "=== WEBHOOK TRIGGERED ===");
        android.util.Log.e("WEBHOOK_TEST", "Account ID: " + accountId);
        android.util.Log.e("WEBHOOK_TEST", "JWT Length: " + (jwtToken != null ? jwtToken.length() : 0));
        android.util.Log.e("WEBHOOK_TEST", "Data: " + notificationData);
        HttpURLConnection connection = null;
        try {
            JSONObject notification = new JSONObject(notificationData);

            String amountStr = notification.optString("amount", "");
            String senderName = notification.optString("name", "");
            String noteText = notification.optString("note", "");
            String appName = notification.optString("appName", "");
            String title = notification.optString("title", "");

            double amountValue = parseAmount(amountStr);

            if (senderName.isEmpty()) senderName = "Anonymous";

            String message = noteText.isEmpty() ? title : noteText;

            JSONObject user = new JSONObject();
            user.put("username", senderName);
            user.put("userId", "notifyall-" + System.currentTimeMillis());
            user.put("email", "no@email.no");

            JSONObject body = new JSONObject();
            body.put("user", user);
            body.put("provider", appName.isEmpty() ? "NotifyAll" : appName);
            body.put("message", message);
            body.put("amount", amountValue);
            body.put("currency", "INR");
            body.put("imported", "true");

            URL url = new URL("https://api.streamelements.com/kappa/v2/tips/" + accountId);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            byte[] outputBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(outputBytes);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                Log.d(TAG, "StreamElements tip sent: " + senderName + " " + amountStr);
            } else {
                Log.e(TAG, "StreamElements error: " + responseCode);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private double parseAmount(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) return 0.0;
        try {
            String cleaned = amountStr.replaceAll("[₹,\\s]", "")
                    .replaceAll("(?i)rs\\.?", "")
                    .replaceAll("(?i)inr", "")
                    .trim();
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}