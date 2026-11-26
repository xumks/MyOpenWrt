package com.myopenwrt.app.api.model;

/**
 * 路由器服务 / 插件信息（合并自 ubus service list 与 rc list）。
 */
public class ServiceInfo {

    public String name;          // 服务名（如 openclash / adblock）
    public boolean running;      // 是否正在运行
    public boolean hasInit;      // 是否存在 /etc/init.d 脚本（来自 rc list）
    public boolean enabled;      // 是否开机自启（仅 hasInit 时有意义）
    public int pid;              // 主进程 PID（0 表示无）
    public String command;       // 首个实例的启动命令
    public int startOrder;       // rc 启动顺序（如 99）
    public int instanceCount;    // 实例数量

    public String getPidDisplay() {
        return pid > 0 ? String.valueOf(pid) : "—";
    }

    public String getStartOrderDisplay() {
        return hasInit ? ("S" + startOrder) : "—";
    }

    public String getRunningText() {
        return running ? "运行中" : "已停止";
    }

    public String getEnabledText() {
        if (!hasInit) return "—";
        return enabled ? "自启:开" : "自启:关";
    }
}
