package com.myopenwrt.app.data;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 使用 SharedPreferences 持久化路由器连接配置。
 */
public class PrefsManager {
    private static final String PREFS_NAME = "openwrt_prefs";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_USE_HTTPS = "use_https";
    private static final String KEY_SESSION = "session_id";
    private static final String KEY_SESSION_TIME = "session_time";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveConfig(RouterConfig config) {
        prefs.edit()
                .putString(KEY_HOST, config.getHost())
                .putInt(KEY_PORT, config.getPort())
                .putString(KEY_USERNAME, config.getUsername())
                .putString(KEY_PASSWORD, config.getPassword())
                .putBoolean(KEY_USE_HTTPS, config.isUseHttps())
                .apply();
    }

    public RouterConfig loadConfig() {
        if (!prefs.contains(KEY_HOST)) {
            // 默认值：连接当前局域网的网关
            return new RouterConfig("192.168.1.1", 80, "root", "", false);
        }
        return new RouterConfig(
                prefs.getString(KEY_HOST, "192.168.1.1"),
                prefs.getInt(KEY_PORT, 80),
                prefs.getString(KEY_USERNAME, "root"),
                prefs.getString(KEY_PASSWORD, ""),
                prefs.getBoolean(KEY_USE_HTTPS, false)
        );
    }

    public boolean hasConfig() {
        return prefs.contains(KEY_HOST);
    }

    public void clearConfig() {
        prefs.edit().clear().apply();
    }

    public void saveSession(String sessionId) {
        prefs.edit()
                .putString(KEY_SESSION, sessionId)
                .apply();
    }

    public String getSession() {
        return prefs.getString(KEY_SESSION, null);
    }

    public void clearSession() {
        prefs.edit().remove(KEY_SESSION).remove(KEY_SESSION_TIME).apply();
    }
}
