package com.myopenwrt.app.api.model;

/**
 * /etc/config/wireless 中的一个 wifi-iface（AP）配置。
 */
public class WirelessConfig {
    public String sid;
    public String ssid;
    public String key;
    public String encryption;
    public String device;
    public boolean disabled;

    public String getTitle() {
        return ssid == null || ssid.trim().isEmpty() ? "(未设置名称)" : ssid;
    }

    public String getSubtitle() {
        StringBuilder sb = new StringBuilder();
        sb.append(device == null || device.isEmpty() ? "—" : device);
        sb.append(" · ");
        sb.append(encryption == null || encryption.isEmpty() ? "none" : encryption);
        if (disabled) sb.append(" · 已禁用");
        return sb.toString();
    }
}
