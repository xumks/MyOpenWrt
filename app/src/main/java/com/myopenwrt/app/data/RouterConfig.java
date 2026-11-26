package com.myopenwrt.app.data;

/**
 * 路由器连接配置。
 */
public class RouterConfig {
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean useHttps;

    public RouterConfig() {
    }

    public RouterConfig(String host, int port, String username, String password, boolean useHttps) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.useHttps = useHttps;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isUseHttps() {
        return useHttps;
    }

    public void setUseHttps(boolean useHttps) {
        this.useHttps = useHttps;
    }

    /** 构造基础 URL，例如 http://192.168.1.1:80 */
    public String buildBaseUrl() {
        String scheme = useHttps ? "https" : "http";
        return scheme + "://" + host + ":" + port;
    }

    public String getDisplayName() {
        return (username == null || username.isEmpty() ? "root" : username) + "@" + host;
    }
}
