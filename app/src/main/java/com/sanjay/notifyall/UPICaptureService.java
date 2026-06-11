package com.sanjay.notifyall;

import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.app.Notification;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class UPICaptureService extends NotificationListenerService {

    private static final String TAG = "UPICapture";

    // Comprehensive list of UPI and payment apps
    private static final Set<String> UPI_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.nbu.paisa.user",     // Google Pay
            "com.phonepe.app",                            // PhonePe
            "com.paytm",                                  // Paytm
            "com.amazon.mShop.android.shopping",          // Amazon Pay
            "in.amazon.mShop.android.shopping",           // Amazon India
            "com.whatsapp",                               // WhatsApp Payments
            "com.whatsapp.w4b",                           // WhatsApp Business
            "com.truecaller",                             // Truecaller Pay
            "com.microsoft.skydrive",                     // Microsoft Pay (BharatQR)
            "com.sbi.SBIFreedom",                         // SBI Pay
            "com.hdfc.hdfcbankpay",                       // HDFC Pay
            "com.icici.bank.icicibank",                   // ICICI Pay
            "com.axis.mobile",                            // Axis Pay
            "net.one97.paytm",                            // PayTM old
            "com.npci.rupay",                             // RuPay
            "com.bhim",                                   // BHIM UPI
            "com.bhim.upi",                               // BHIM
            "com.bankofbaroda.mobilebanking",             // BOB UPI
            "com.csam.icici.bank.imobile",                // ICICI iMobile
            "com.hdfcbank.mobile",                        // HDFC Bank
            "com.sbi",                                    // SBI Anywhere
            "com.pnb.netbanking",                         // PNB
            "com.yono.sbi",                               // SBI YONO
            "com.airtel.bank",                            // Airtel Payments Bank
            "com.jio.payments",                           // Jio Payments
            "com.freecharge.android",                     // FreeCharge
            "com.mobikwik_new",                           // Mobikwik
            "com.mobikwik",                               // Mobikwik
            "com.olacabs.olamoney",                       // Ola Money
            "com.ubercab",                                // Uber (has payments)
            "com.zomato",                                 // Zomato Payments
            "com.swiggy",                                 // Swiggy Money
            "com.flipkart.android",                       // Flipkart Pay
            "com.phonepe",                                // PhonePe variations
            "com.google.android.apps.nbu.paisa"          // Google Pay variations
    ));

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Check if this is a UPI app
        if (!isUPIApp(packageName)) {
            return;
        }

        Log.e(TAG, "═══════════════════════════════════════════════");
        Log.e(TAG, "📱 UPI APP DETECTED: " + packageName);
        Log.e(TAG, "═══════════════════════════════════════════════");

        captureAllNotificationData(sbn);
    }

    private boolean isUPIApp(String packageName) {
        for (String upiPackage : UPI_APPS) {
            if (packageName.equals(upiPackage) || packageName.contains(upiPackage)) {
                return true;
            }
        }
        // Also check for payment-related package names
        String lowerPackage = packageName.toLowerCase();
        return lowerPackage.contains("pay") ||
                lowerPackage.contains("upi") ||
                lowerPackage.contains("phonepe") ||
                lowerPackage.contains("gpay") ||
                lowerPackage.contains("tez") ||
                lowerPackage.contains("bhim") ||
                lowerPackage.contains("bank") ||
                lowerPackage.contains("money") ||
                lowerPackage.contains("wallet");
    }

    private void captureAllNotificationData(StatusBarNotification sbn) {
        try {
            Notification notification = sbn.getNotification();
            Bundle extras = notification.extras;

            // Basic information
            logBasicInfo(sbn);

            // All extras keys and values
            logAllExtras(extras);

            // Try to extract all possible text fields
            logAllTextFields(extras);

            // Try reflection to get hidden fields
            tryReflectionExtraction(notification);

            // Try to get raw content view (if possible)
            tryGetRawContent(notification);

            // Try alternative extraction methods
            tryAlternativeExtraction(extras);

            // Try to get notification actions
            logNotificationActions(notification);

            // Try to get large icon and content view
            logVisualContent(notification);

            // Try to get the actual displayed text using different approaches
            exhaustivelyExtractText(extras);

            Log.e(TAG, "═══════════════════════════════════════════════");
            Log.e(TAG, "✅ UPI Notification Capture Complete");
            Log.e(TAG, "═══════════════════════════════════════════════\n");

        } catch (Exception e) {
            Log.e(TAG, "Error capturing notification: " + e.getMessage(), e);
        }
    }

    private void logBasicInfo(StatusBarNotification sbn) {
        Log.e(TAG, "📋 BASIC INFORMATION:");
        Log.e(TAG, "  • Package: " + sbn.getPackageName());
        Log.e(TAG, "  • Tag: " + sbn.getTag());
        Log.e(TAG, "  • ID: " + sbn.getId());
        Log.e(TAG, "  • Key: " + sbn.getKey());  // This is the correct method
        Log.e(TAG, "  • Group Key: " + sbn.getGroupKey());
        Log.e(TAG, "  • Post Time: " + sbn.getPostTime());
        Log.e(TAG, "  • Is Clearable: " + sbn.isClearable());
        Log.e(TAG, "  • Is Ongoing: " + sbn.isOngoing());
        Log.e(TAG, "  • User ID: " + sbn.getUser());
        Log.e(TAG, "  • Notification ID: " + sbn.getId());
        Log.e(TAG, "  • OpPkg: " + sbn.getOpPkg());
    }

    private void logAllExtras(Bundle extras) {
        Log.e(TAG, "📦 ALL EXTRAS KEYS & VALUES:");
        if (extras == null) {
            Log.e(TAG, "  ❌ Extras bundle is null!");
            return;
        }

        Set<String> keys = extras.keySet();
        Log.e(TAG, "  Total keys: " + keys.size());

        for (String key : keys) {
            Object value = extras.get(key);
            String valueStr = (value != null) ? value.toString() : "null";

            // Truncate long values but log them fully later
            if (valueStr.length() > 500) {
                Log.e(TAG, "  📌 " + key + " = " + valueStr.substring(0, 500) + "... (length: " + valueStr.length() + ")");
                // Log full value in separate call
                if (valueStr.length() > 1000) {
                    Log.e(TAG, "  🔍 FULL " + key + ":\n" + valueStr);
                } else {
                    Log.e(TAG, "  🔍 FULL " + key + ": " + valueStr);
                }
            } else {
                Log.e(TAG, "  📌 " + key + " = " + valueStr);
            }
        }
    }

    private void logAllTextFields(Bundle extras) {
        Log.e(TAG, "📝 ALL TEXT FIELDS:");

        // Standard notification fields
        String[] textFields = {
                Notification.EXTRA_TITLE,
                Notification.EXTRA_TITLE_BIG,
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_INFO_TEXT,
                Notification.EXTRA_SUMMARY_TEXT,
                "android.title",
                "android.title.big",
                "android.text",
                "android.subText",
                "android.infoText",
                "android.summaryText",
                "android.largeIcon",
                "android.progress",
                "android.progressMax",
                "android.progressIndeterminate",
                "android.showChronometer",
                "android.showWhen",
                "android.usesChronometer",
                "android.template",
                "android.reduced.images",
                "android.background",
                "android.remoteInputHistory",
                "android.messagingStyleUser",
                "android.messagingStyleMessages",
                "android.conversationTitle",
                "android.largeIcon",
                "android.largeIconBig",
                "android.picture",
                "android.people",
                "android.carExtender",
                "android.wearableExtender"
        };

        for (String field : textFields) {
            String value = getBundleString(extras, field);
            if (value != null && !value.isEmpty()) {
                Log.e(TAG, "  ✏️ " + field + " = " + value);
            }
        }

        // Try to get CharSequence arrays
        try {
            CharSequence[] textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (textLines != null && textLines.length > 0) {
                Log.e(TAG, "  📄 EXTRA_TEXT_LINES (" + textLines.length + " lines):");
                for (int i = 0; i < textLines.length; i++) {
                    Log.e(TAG, "    Line " + i + ": " + (textLines[i] != null ? textLines[i].toString() : "null"));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "  Could not get EXTRA_TEXT_LINES: " + e.getMessage());
        }

        // Try to get remote input history
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                CharSequence[] remoteHistory = extras.getCharSequenceArray(Notification.EXTRA_REMOTE_INPUT_HISTORY);
                if (remoteHistory != null && remoteHistory.length > 0) {
                    Log.e(TAG, "  🔄 REMOTE INPUT HISTORY:");
                    for (int i = 0; i < remoteHistory.length; i++) {
                        Log.e(TAG, "    History " + i + ": " + remoteHistory[i]);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "  Could not get remote input history: " + e.getMessage());
        }
    }

    private void tryReflectionExtraction(Notification notification) {
        Log.e(TAG, "🪞 REFLECTION EXTRACTION:");
        try {
            Field[] fields = notification.getClass().getDeclaredFields();
            Log.e(TAG, "  Total fields: " + fields.length);

            for (Field field : fields) {
                field.setAccessible(true);
                try {
                    Object value = field.get(notification);
                    if (value != null) {
                        String fieldName = field.getName();
                        if (fieldName.toLowerCase().contains("text") ||
                                fieldName.toLowerCase().contains("title") ||
                                fieldName.toLowerCase().contains("message") ||
                                fieldName.toLowerCase().contains("content") ||
                                fieldName.toLowerCase().contains("amount") ||
                                fieldName.toLowerCase().contains("transaction") ||
                                fieldName.toLowerCase().contains("money") ||
                                fieldName.toLowerCase().contains("price")) {

                            String valueStr = value.toString();
                            if (valueStr.length() > 200) {
                                valueStr = valueStr.substring(0, 200) + "...";
                            }
                            Log.e(TAG, "  🔍 " + fieldName + " = " + valueStr);
                        }
                    }
                } catch (IllegalAccessException e) {
                    // Skip inaccessible fields silently
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "  Reflection error: " + e.getMessage());
        }
    }

    private void tryGetRawContent(Notification notification) {
        Log.e(TAG, "🎨 RAW CONTENT VIEW:");
        try {
            if (notification.contentView != null) {
                Log.e(TAG, "  ContentView exists: " + notification.contentView.toString());
                Log.e(TAG, "  ContentView package: " + notification.contentView.getPackage());
            } else {
                Log.e(TAG, "  ContentView is null");
            }

            if (notification.bigContentView != null) {
                Log.e(TAG, "  BigContentView exists: " + notification.bigContentView.toString());
            } else {
                Log.e(TAG, "  BigContentView is null");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (notification.headsUpContentView != null) {
                    Log.e(TAG, "  HeadsUpContentView exists: " + notification.headsUpContentView.toString());
                } else {
                    Log.e(TAG, "  HeadsUpContentView is null");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "  Error getting content view: " + e.getMessage());
        }
    }

    private void tryAlternativeExtraction(Bundle extras) {
        Log.e(TAG, "🔄 ALTERNATIVE EXTRACTION:");

        // Try to get data as different types
        String[] keys = {
                "amount", "Amount", "AMOUNT", "txn_amount", "txnAmount", "transaction_amount",
                "price", "Price", "PRICE", "value", "Value", "total", "Total",
                "sender", "Sender", "SENDER", "from", "From", "FROM", "payer", "Payer",
                "receiver", "Receiver", "RECEIVER", "to", "To", "TO", "payee", "Payee",
                "note", "Note", "NOTE", "remark", "Remark", "REMARK", "description", "Description",
                "upi", "UPI", "vpa", "VPA", "virtual_payment_address",
                "ref", "Ref", "REF", "reference", "Reference", "txn_id", "txnId", "transaction_id",
                "status", "Status", "STATUS", "success", "Success", "failed", "Failed",
                "bank", "Bank", "BANK", "account", "Account", "balance", "Balance"
        };

        for (String key : keys) {
            // Try String
            String stringValue = getBundleString(extras, key);
            if (stringValue != null && !stringValue.isEmpty()) {
                Log.e(TAG, "  💰 " + key + " (String) = " + stringValue);
            }

            // Try Integer
            try {
                int intValue = extras.getInt(key, -999);
                if (intValue != -999) {
                    Log.e(TAG, "  🔢 " + key + " (Int) = " + intValue);
                }
            } catch (Exception e) {}

            // Try Double
            try {
                double doubleValue = extras.getDouble(key, -999.0);
                if (doubleValue != -999.0) {
                    Log.e(TAG, "  🔢 " + key + " (Double) = " + doubleValue);
                }
            } catch (Exception e) {}
        }

        // Try to get Parcelable arrays (like MessagingStyle)
        try {
            android.os.Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages != null && messages.length > 0) {
                Log.e(TAG, "  💬 MESSAGES (" + messages.length + " messages):");
                for (int i = 0; i < messages.length; i++) {
                    if (messages[i] instanceof Bundle) {
                        Bundle msg = (Bundle) messages[i];
                        String sender = msg.getString("sender");
                        String text = msg.getString("text");
                        Log.e(TAG, "    Message " + i + " - Sender: " + sender + ", Text: " + text);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "  Could not get messages: " + e.getMessage());
        }
    }

    private void logNotificationActions(Notification notification) {
        Log.e(TAG, "⚡ NOTIFICATION ACTIONS:");
        try {
            if (notification.actions != null && notification.actions.length > 0) {
                Log.e(TAG, "  Total actions: " + notification.actions.length);
                for (int i = 0; i < notification.actions.length; i++) {
                    Notification.Action action = notification.actions[i];
                    Log.e(TAG, "    Action " + i + ": " + action.title);
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        if (action.getRemoteInputs() != null) {
                            Log.e(TAG, "      Remote inputs: " + action.getRemoteInputs().length);
                        }
                    }
                }
            } else {
                Log.e(TAG, "  No actions available");
            }
        } catch (Exception e) {
            Log.e(TAG, "  Error getting actions: " + e.getMessage());
        }
    }

    private void logVisualContent(Notification notification) {
        Log.e(TAG, "🖼️ VISUAL CONTENT:");
        try {
            if (notification.largeIcon != null) {
                Log.e(TAG, "  Large icon available: " + notification.largeIcon.getByteCount() + " bytes");
            } else {
                Log.e(TAG, "  No large icon");
            }
            Log.e(TAG, "  Icon resource ID: " + notification.icon);  // This exists on Notification class
        } catch (Exception e) {
            Log.e(TAG, "  Error getting icons: " + e.getMessage());
        }
    }

    private void exhaustivelyExtractText(Bundle extras) {
        Log.e(TAG, "🔍 EXHAUSTIVE TEXT EXTRACTION:");

        StringBuilder allText = new StringBuilder();

        // Extract everything that could possibly contain text
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                if (value != null) {
                    String valueStr = value.toString();
                    // Check if it looks like text content
                    if (valueStr.length() > 3 &&
                            (valueStr.matches(".*[a-zA-Z0-9].*") || valueStr.matches(".*[₹0-9].*"))) {
                        allText.append(valueStr).append("\n");
                        if (valueStr.length() < 200) {
                            Log.e(TAG, "  🔎 " + key + " (text candidate): " + valueStr);
                        }
                    }
                }
            }
        }

        if (allText.length() > 0) {
            Log.e(TAG, "  📄 COMBINED TEXT EXTRACTION:\n" + allText.toString());

            // Try to extract specific patterns
            extractAmountsAndNames(allText.toString());
        }
    }

    private void extractAmountsAndNames(String fullText) {
        Log.e(TAG, "💰 EXTRACTED PATTERNS:");

        // Amount patterns
        java.util.regex.Pattern amountPattern = java.util.regex.Pattern.compile(
                "[₹Rs\\s]*([0-9,]+(?:\\.[0-9]{2})?)",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher amountMatcher = amountPattern.matcher(fullText);
        while (amountMatcher.find()) {
            String amount = amountMatcher.group(1);
            if (amount != null && !amount.isEmpty() && !amount.equals("0")) {
                Log.e(TAG, "  💵 Found amount: " + amount);
            }
        }

        // UPI VPA patterns
        java.util.regex.Pattern vpaPattern = java.util.regex.Pattern.compile(
                "[a-zA-Z0-9._-]+@[a-zA-Z0-9]+",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher vpaMatcher = vpaPattern.matcher(fullText);
        while (vpaMatcher.find()) {
            Log.e(TAG, "  📧 Found VPA: " + vpaMatcher.group());
        }

        // Name patterns
        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile(
                "(?:to|from|paid to|received from|pay to|beneficiary)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher nameMatcher = namePattern.matcher(fullText);
        while (nameMatcher.find()) {
            Log.e(TAG, "  👤 Found name: " + nameMatcher.group(1));
        }

        // Transaction ID patterns
        java.util.regex.Pattern txnPattern = java.util.regex.Pattern.compile(
                "(?:Txn ID|Transaction ID|UTR|Ref No)[:\\s]*([A-Z0-9]{8,25})",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher txnMatcher = txnPattern.matcher(fullText);
        while (txnMatcher.find()) {
            Log.e(TAG, "  🆔 Found Transaction ID: " + txnMatcher.group(1));
        }
    }

    private String getBundleString(Bundle bundle, String key) {
        try {
            String value = bundle.getString(key);
            return (value != null && !value.isEmpty()) ? value : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        if (isUPIApp(packageName)) {
            Log.e(TAG, "🗑️ UPI Notification Removed: " + packageName);
        }
    }
}