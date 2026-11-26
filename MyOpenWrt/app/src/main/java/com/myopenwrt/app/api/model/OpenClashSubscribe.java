package com.myopenwrt.app.api.model;

/**
 * 单个 OpenClash 订阅配置（UCI config_subscribe section）。
 */
public class OpenClashSubscribe {
    public String sid;        // UCI section id，如 cfg29b6bc
    public String name;       // 订阅名称，如 c-01
    public String address;    // 订阅地址
    public boolean enabled;   // 是否启用
    public String subUa;      // User-Agent，如 clash.meta
    public String subConvert; // 是否开启订阅转换
    public boolean isCurrent; // 是否为当前生效的配置（config_path 指向它）

    public String getDisplayAddress() {
        return (address == null || address.isEmpty()) ? "(未设置)" : address;
    }

    public String getDisplayUa() {
        return (subUa == null || subUa.isEmpty()) ? "—" : subUa;
    }
}
