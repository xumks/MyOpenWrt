package com.myopenwrt.app.api.model;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenClash 插件全局配置 + 订阅列表。
 */
public class OpenClashConfig {
    public boolean enable;
    public String proxyMode;     // rule / global / direct
    public String operationMode; // fake-ip / redir-host 等
    public String configPath;    // /etc/openclash/config/c-01.yaml
    public String httpPort;
    public String socksPort;
    public String mixedPort;
    public String proxyPort;
    public String dnsPort;
    public String cnPort;        // 控制面板端口 9090
    public String coreType;
    public String dashboardType;
    public boolean running;      // 进程是否在运行（来自 service list）

    public List<OpenClashSubscribe> subscribes = new ArrayList<>();

    /** 当前配置文件名（不含路径与扩展名），用于匹配哪个订阅正在生效。 */
    public String currentConfigName() {
        if (configPath == null || configPath.isEmpty()) return null;
        int slash = configPath.lastIndexOf('/');
        String base = slash >= 0 ? configPath.substring(slash + 1) : configPath;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    public String getProxyModeDisplay() {
        if (proxyMode == null) return "—";
        switch (proxyMode) {
            case "rule": return "规则模式";
            case "global": return "全局模式";
            case "direct": return "直连模式";
            default: return proxyMode;
        }
    }
}
