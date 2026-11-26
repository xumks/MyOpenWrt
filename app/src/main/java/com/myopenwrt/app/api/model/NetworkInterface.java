package com.myopenwrt.app.api.model;

/**
 * 网络接口信息（对应 ubus network.interface dump）。
 */
public class NetworkInterface {
    public String name;          // wan / lan / wwan 等
    public String device;        // 物理设备名
    public boolean up;
    public String ipv4Addr;
    public String ipv4Mask;
    public String ipv6Addr;
    public String mac;
    public long rxBytes;
    public long txBytes;
    public long rxPackets;
    public long txPackets;
    public String uptime;
    public String proto;         // dhcp / static / pppoe 等
    public String rxRate;        // 实时下行速率，运行时计算
    public String txRate;        // 实时上行速率，运行时计算

    public String getRxFormatted() {
        return formatBytes(rxBytes);
    }

    public String getTxFormatted() {
        return formatBytes(txBytes);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
