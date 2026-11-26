package com.myopenwrt.app.util;

/**
 * 通用格式化工具。
 */
public class FormatUtils {

    private FormatUtils() { }

    public static String formatUptime(long seconds) {
        if (seconds <= 0) return "—";
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("天");
        if (hours > 0 || days > 0) sb.append(hours).append("时");
        sb.append(minutes).append("分");
        return sb.toString();
    }

    public static String formatBytes(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static String formatMemory(long total, long used) {
        if (total <= 0) return "—";
        double totalMb = total / (1024.0 * 1024);
        double usedMb = used / (1024.0 * 1024);
        int percent = (int) (used * 100 / total);
        return String.format("%.0f / %.0f MB (%d%%)", usedMb, totalMb, percent);
    }

    public static String formatLoad(double load) {
        return String.format("%.2f", load);
    }

    /** 计算两次采样的速率，返回 "xx KB/s"。 */
    public static String formatRate(long bytesDiff, long msDiff) {
        if (msDiff <= 0 || bytesDiff < 0) return "0 B/s";
        double bps = bytesDiff * 1000.0 / msDiff;
        return formatBytes((long) bps) + "/s";
    }
}
