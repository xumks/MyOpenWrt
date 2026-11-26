package com.myopenwrt.app.api.model;

/**
 * 路由器系统信息（对应 ubus system info / system board）。
 */
public class SystemInfo {
    public String model;
    public String boardName;
    public String release;        // 固件版本，如 "23.05.5"
    public String revision;
    public String kernel;         // 内核版本
    public String hostname;
    public long uptime;           // 秒
    public double load1;
    public double load5;
    public double load15;
    public long memoryTotal;      // 字节
    public long memoryFree;
    public long memoryShared;
    public long memoryBuffered;
    public long memoryAvailable;
    public long swapTotal;
    public long swapFree;

    public long getMemoryUsed() {
        return memoryTotal - memoryAvailable;
    }
}
