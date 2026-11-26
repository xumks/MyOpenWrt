package com.myopenwrt.app.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * WiFi 设备与关联客户端信息。
 */
public class WifiInfo {
    public String ifname;        // wlan0
    public String ssid;
    public String encryption;
    public boolean up;
    public String channel;
    public int signal;           // dBm
    public String mode;          // Master 等
    public String country;
    public List<WifiClient> clients = new ArrayList<>();

    public static class WifiClient {
        public String mac;
        public String hostname;
        public int signal;        // dBm
        public String connectedTime;
    }

    public String getSignalDisplay() {
        if (signal == 0) return "—";
        return signal + " dBm";
    }
}
