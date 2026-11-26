package com.myopenwrt.app.api.model;

/**
 * DHCP 租约（已连接设备）。
 */
public class DhcpLease {
    public String ip;
    public String mac;
    public String hostname;
    public long expires;     // 租约剩余秒数

    public String staticSid;   // /etc/config/dhcp 中 host 段的 section id，null 表示未固定
    public String staticIp;    // 已固定的 IP
    public boolean blocked;    // 是否已被防火墙拉黑
    public String blockSid;    // 对应防火墙规则的 section id

    public String getDisplayHostname() {
        if (hostname == null || hostname.trim().isEmpty() || hostname.equals("*")) {
            return "(未知)";
        }
        return hostname;
    }

    public String getExpiresDisplay() {
        if (expires <= 0) return "永久";
        long h = expires / 3600;
        long m = (expires % 3600) / 60;
        if (h > 0) return h + "小时" + m + "分";
        return m + "分钟";
    }
}
