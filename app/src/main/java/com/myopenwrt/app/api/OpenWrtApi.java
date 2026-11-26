package com.myopenwrt.app.api;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.myopenwrt.app.api.model.DhcpLease;
import com.myopenwrt.app.api.model.NetworkInterface;
import com.myopenwrt.app.api.model.OpenClashConfig;
import com.myopenwrt.app.api.model.OpenClashSubscribe;
import com.myopenwrt.app.api.model.ServiceInfo;
import com.myopenwrt.app.api.model.SystemInfo;
import com.myopenwrt.app.api.model.WifiInfo;
import com.myopenwrt.app.api.model.WirelessConfig;
import com.myopenwrt.app.data.PrefsManager;
import com.myopenwrt.app.data.RouterConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OpenWrt ubus over HTTP (JSON-RPC 2.0) 客户端。
 *
 * 通信流程：
 *  1. POST /ubus 调用 session.login 换取 session id；
 *  2. 后续所有调用携带 session id，格式为
 *     {"jsonrpc":"2.0","id":N,"method":"call","params":["<sid>","<obj>","<m>",{...}]}；
 *  3. 若返回错误码 6（权限/会话失效），自动重新登录一次后重试。
 *
 * 注意：为兼容路由器自签名 HTTPS 证书，本客户端信任所有证书，仅建议在可信局域网内使用。
 */
public class OpenWrtApi {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String UBUS_PATH = "/ubus";
    private static final String EMPTY_SESSION = "00000000000000000000000000000000";
    private static final int ERR_ACCESS_DENIED = 6;

    private static volatile OpenWrtApi instance;

    private final OkHttpClient client;
    private final OkHttpClient longClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final PrefsManager prefs;

    private RouterConfig config;
    private String sessionId;
    private int callId = 1;

    private OpenWrtApi(Context context) {
        this.prefs = new PrefsManager(context.getApplicationContext());
        this.client = createUnsafeClient();
        this.longClient = createUnsafeClient(60, 60, 60);
        this.config = prefs.loadConfig();
        this.sessionId = prefs.getSession();
    }

    public static OpenWrtApi getInstance(Context context) {
        if (instance == null) {
            synchronized (OpenWrtApi.class) {
                if (instance == null) {
                    instance = new OpenWrtApi(context);
                }
            }
        }
        return instance;
    }

    public RouterConfig getConfig() {
        return config;
    }

    public void setConfig(RouterConfig config) {
        this.config = config;
        this.sessionId = null;
        prefs.saveConfig(config);
        prefs.clearSession();
    }

    public boolean hasSession() {
        return sessionId != null && !sessionId.isEmpty();
    }

    public void logout() {
        sessionId = null;
        prefs.clearSession();
    }

    // ============================ 底层调用 ============================

    /** 执行一次 ubus 调用，自动处理会话失效重登。返回 result 数组中的数据对象。 */
    private JSONObject callUbus(String object, String method, JSONObject params) throws Exception {
        return callUbus(client, object, method, params);
    }

    /** 使用长超时客户端调用（用于重启等耗时命令）。 */
    private JSONObject callUbusLong(String object, String method, JSONObject params) throws Exception {
        return callUbus(longClient, object, method, params);
    }

    private JSONObject callUbus(OkHttpClient httpClient, String object, String method, JSONObject params) throws Exception {
        if (config == null) throw new IOException("未配置路由器连接信息");
        ensureSession();
        return doCall(httpClient, sessionId, object, method, params, false);
    }

    private synchronized void ensureSession() throws Exception {
        if (sessionId == null || sessionId.isEmpty()) {
            doLogin();
        }
    }

    private JSONObject doCall(OkHttpClient httpClient, String sid, String object, String method,
                              JSONObject params, boolean retried) throws Exception {
        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        body.put("id", callId++);
        body.put("method", "call");
        JSONArray arr = new JSONArray();
        arr.put(sid);
        arr.put(object);
        arr.put(method);
        arr.put(params == null ? new JSONObject() : params);
        body.put("params", arr);

        Request request = new Request.Builder()
                .url(config.buildBaseUrl() + UBUS_PATH)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            String respText = readBody(response);
            JSONObject json = new JSONObject(respText);

            if (json.has("error")) {
                JSONObject err = json.optJSONObject("error");
                throw new IOException("ubus 错误: " + (err != null ? err.optString("message", "未知") : respText));
            }
            if (!json.has("result")) {
                throw new IOException("响应缺少 result 字段: " + respText);
            }
            JSONArray result = json.getJSONArray("result");
            if (result.length() == 0) throw new IOException("空响应");

            Object first = result.get(0);
            if (first instanceof Integer) {
                int code = (Integer) first;
                if (code == ERR_ACCESS_DENIED) {
                    if (retried) throw new IOException("ubus 权限不足（错误码 6）");
                    synchronized (this) {
                        if (sid != null && sid.equals(sessionId)) {
                            sessionId = null;
                            prefs.clearSession();
                            doLogin();
                        } else if (sessionId == null || sessionId.isEmpty()) {
                            doLogin();
                        }
                    }
                    // 若其他线程已刷新会话则直接复用；每次调用最多重试一次。
                    return doCall(httpClient, sessionId, object, method, params, true);
                }
                if (code != 0) {
                    throw new IOException("ubus 返回错误码 " + code + ": " +
                            (result.length() > 1 ? result.optString(1, "") : ""));
                }
                return result.length() > 1 ? result.optJSONObject(1) : new JSONObject();
            }
            // first 是字符串（登录返回 session id）
            return result.optJSONObject(1);
        }
    }

    private void doLogin() throws Exception {
        JSONObject params = new JSONObject();
        params.put("username", config.getUsername() == null ? "root" : config.getUsername());
        params.put("password", config.getPassword() == null ? "" : config.getPassword());
        params.put("timeout", 3600);

        JSONObject body = new JSONObject();
        body.put("jsonrpc", "2.0");
        body.put("id", callId++);
        body.put("method", "call");
        JSONArray arr = new JSONArray();
        arr.put(EMPTY_SESSION);
        arr.put("session");
        arr.put("login");
        arr.put(params);
        body.put("params", arr);

        Request request = new Request.Builder()
                .url(config.buildBaseUrl() + UBUS_PATH)
                .post(RequestBody.create(body.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + "（请确认地址/端口正确，且路由器已启用 ubus over HTTP）");
            }
            String respText = readBody(response);
            JSONObject json = new JSONObject(respText);
            if (json.has("error")) {
                JSONObject err = json.optJSONObject("error");
                throw new IOException("登录失败: " + (err != null ? err.optString("message") : respText));
            }
            JSONArray result = json.optJSONArray("result");
            if (result == null || result.length() == 0) {
                throw new IOException("登录响应格式错误: " + respText);
            }

            // 兼容两种 ubus 登录返回格式：
            //   旧版: ["<session_id>", {...}]          —— result[0] 即 session 字符串
            //   新版: [0, {"ubus_rpc_session":"..."}]  —— result[0] 为状态码 0，session 在 data 中
            Object first = result.get(0);
            String sid = null;
            if (first instanceof String) {
                sid = (String) first;
            } else if (first instanceof Integer) {
                int code = (Integer) first;
                if (code == ERR_ACCESS_DENIED) {
                    throw new IOException("用户名或密码错误");
                }
                if (code == 0) {
                    JSONObject data = result.optJSONObject(1);
                    if (data != null && data.has("ubus_rpc_session")) {
                        sid = data.optString("ubus_rpc_session", null);
                    }
                }
                if (sid == null) {
                    throw new IOException("登录失败，错误码 " + code + ": " +
                            (result.length() > 1 ? result.optString(1, "") : ""));
                }
            }
            if (sid == null || sid.isEmpty()) {
                throw new IOException("登录响应缺少 session 信息: " + respText);
            }
            this.sessionId = sid;
            prefs.saveSession(sessionId);
        }
    }

    private String readBody(Response response) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body().byteStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static OkHttpClient createUnsafeClient() {
        return createUnsafeClient(8, 20, 20);
    }

    private static OkHttpClient createUnsafeClient(int connect, int read, int write) {
        try {
            TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext ssl = SSLContext.getInstance("TLS");
            ssl.init(null, trustAll, new SecureRandom());
            return new OkHttpClient.Builder()
                    .sslSocketFactory(ssl.getSocketFactory(), (X509TrustManager) trustAll[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(connect, TimeUnit.SECONDS)
                    .readTimeout(read, TimeUnit.SECONDS)
                    .writeTimeout(write, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            return new OkHttpClient.Builder()
                    .connectTimeout(connect, TimeUnit.SECONDS)
                    .readTimeout(read, TimeUnit.SECONDS)
                    .build();
        }
    }

    // ============================ 异步封装 ============================

    private <T> void runAsync(AsyncTask<T> task, ApiCallback<T> callback) {
        executor.execute(() -> {
            try {
                T result = task.execute();
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(e.getMessage() != null ? e.getMessage() : e.toString(), e));
            }
        });
    }

    private interface AsyncTask<T> {
        T execute() throws Exception;
    }

    // ============================ 对外方法 ============================

    /** 测试登录连通性。 */
    public void login(ApiCallback<String> callback) {
        runAsync(() -> {
            doLogin();
            return sessionId;
        }, callback);
    }

    /** 获取系统信息（合并 system info 与 system board）。 */
    public void getSystemInfo(ApiCallback<SystemInfo> callback) {
        runAsync(() -> {
            SystemInfo info = new SystemInfo();
            JSONObject sysInfo = callUbus("system", "info", null);
            info.uptime = sysInfo.optLong("uptime");
            JSONArray load = sysInfo.optJSONArray("load");
            if (load != null && load.length() >= 3) {
                info.load1 = load.optDouble(0) / 65535.0;
                info.load5 = load.optDouble(1) / 65535.0;
                info.load15 = load.optDouble(2) / 65535.0;
            }
            JSONObject mem = sysInfo.optJSONObject("memory");
            if (mem != null) {
                info.memoryTotal = mem.optLong("total");
                info.memoryFree = mem.optLong("free");
                info.memoryShared = mem.optLong("shared");
                info.memoryBuffered = mem.optLong("buffered");
                info.memoryAvailable = mem.optLong("available", info.memoryFree + info.memoryBuffered);
            }
            JSONObject swap = sysInfo.optJSONObject("swap");
            if (swap != null) {
                info.swapTotal = swap.optLong("total");
                info.swapFree = swap.optLong("free");
            }

            try {
                JSONObject board = callUbus("system", "board", null);
                info.model = board.optString("model", "—");
                info.boardName = board.optString("board_name", "—");
                info.hostname = board.optString("hostname", "OpenWrt");
                info.kernel = board.optString("kernel", "—");
                JSONObject release = board.optJSONObject("release");
                if (release != null) {
                    info.release = release.optString("version", "—");
                    info.revision = release.optString("revision", "—");
                }
            } catch (Exception ignored) { /* board 可选 */ }
            return info;
        }, callback);
    }

    /** 获取网络接口状态。 */
    public void getNetworkInterfaces(ApiCallback<List<NetworkInterface>> callback) {
        runAsync(() -> {
            List<NetworkInterface> list = new ArrayList<>();
            JSONObject data = callUbus("network.interface", "dump", null);
            JSONArray interfaces = data.optJSONArray("interface");
            if (interfaces != null) {
                for (int i = 0; i < interfaces.length(); i++) {
                    JSONObject item = interfaces.getJSONObject(i);
                    NetworkInterface ni = new NetworkInterface();
                    ni.name = item.optString("interface", item.optString("name", "?"));
                    ni.proto = item.optString("proto", "—");
                    ni.device = item.optString("device", "—");
                    ni.up = item.optBoolean("up", false);
                    ni.mac = item.optString("mac", "—");
                    JSONArray v4 = item.optJSONArray("ipv4-address");
                    if (v4 != null && v4.length() > 0) {
                        JSONObject a = v4.getJSONObject(0);
                        ni.ipv4Addr = a.optString("address");
                        ni.ipv4Mask = a.optString("mask");
                    }
                    JSONArray v6 = item.optJSONArray("ipv6-address");
                    if (v6 != null && v6.length() > 0) {
                        ni.ipv6Addr = v6.getJSONObject(0).optString("address");
                    }
                    JSONObject stats = item.optJSONObject("stats");
                    if (stats != null) {
                        ni.rxBytes = stats.optLong("rx_bytes");
                        ni.txBytes = stats.optLong("tx_bytes");
                        ni.rxPackets = stats.optLong("rx_packets");
                        ni.txPackets = stats.optLong("tx_packets");
                    }
                    list.add(ni);
                }
            }
            // 用 /proc/net/dev 补全流量统计（部分固件 network.interface dump 不含 stats）
            try {
                JSONObject dp = new JSONObject();
                dp.put("path", "/proc/net/dev");
                String devContent = callUbus("file", "read", dp).optString("data", "");
                Map<String, long[]> devStats = parseProcNetDev(devContent);
                for (NetworkInterface ni : list) {
                    long[] st = devStats.get(ni.device);
                    if (st == null) st = devStats.get(ni.name);
                    if (st != null) {
                        ni.rxBytes = st[0];
                        ni.txBytes = st[1];
                    }
                }
            } catch (Exception ignored) { }
            return list;
        }, callback);
    }

    /** 解析 /proc/net/dev，返回 device -> [rxBytes, txBytes]。 */
    private Map<String, long[]> parseProcNetDev(String content) {
        Map<String, long[]> map = new HashMap<>();
        if (content == null) return map;
        String[] lines = content.split("\n");
        for (int i = 2; i < lines.length; i++) {  // 跳过两行表头
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            int colon = line.indexOf(':');
            if (colon < 0) continue;
            String name = line.substring(0, colon).trim();
            String rest = line.substring(colon + 1).trim();
            String[] tokens = rest.split("\\s+");
            if (tokens.length < 9) continue;
            try {
                long rx = Long.parseLong(tokens[0]);
                long tx = Long.parseLong(tokens[8]);
                map.put(name, new long[]{rx, tx});
            } catch (Exception ignored) { }
        }
        return map;
    }

    /** 获取 DHCP 租约（已连接设备），并合并静态租约与拉黑状态。 */
    public void getDhcpLeases(ApiCallback<List<DhcpLease>> callback) {
        runAsync(() -> {
            List<DhcpLease> list = new ArrayList<>();
            JSONObject params = new JSONObject();
            params.put("path", "/tmp/dhcp.leases");
            JSONObject data = callUbus("file", "read", params);
            String content = data.optString("data", "");
            Map<String, DhcpLease> byMac = new HashMap<>();
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 4) continue;
                DhcpLease lease = new DhcpLease();
                try {
                    // dhcp.leases 第一列是租约到期时间戳（0 表示永久）
                    long expiry = Long.parseLong(parts[0]);
                    lease.expires = expiry <= 0 ? 0
                            : Math.max(0, expiry - System.currentTimeMillis() / 1000);
                } catch (Exception ignored) {}
                lease.mac = parts[1];
                lease.ip = parts[2];
                lease.hostname = parts.length > 3 ? parts[3] : "";
                list.add(lease);
                byMac.put(lease.mac.toLowerCase(), lease);
            }

            // 静态租约（/etc/config/dhcp 中的 host 段），离线设备也会列出，便于取消固定
            try {
                LinkedHashMap<String, LinkedHashMap<String, String>> hosts = readUciConfig("dhcp");
                for (Map.Entry<String, LinkedHashMap<String, String>> e : hosts.entrySet()) {
                    LinkedHashMap<String, String> sec = e.getValue();
                    if (!"host".equals(sec.get(".type"))) continue;
                    String mac = sec.get("mac");
                    String ip = sec.get("ip");
                    if (mac == null || mac.trim().isEmpty()) continue;
                    String key = mac.trim().toLowerCase();
                    DhcpLease lease = byMac.get(key);
                    if (lease == null) {
                        lease = new DhcpLease();
                        lease.mac = mac.trim();
                        lease.ip = ip;
                        lease.hostname = sec.get("name");
                        lease.expires = 0;
                        list.add(lease);
                        byMac.put(key, lease);
                    }
                    lease.staticSid = e.getKey();
                    lease.staticIp = ip;
                    if (lease.ip == null || lease.ip.isEmpty()) lease.ip = ip;
                }
            } catch (Exception ignored) { }

            // 防火墙拉黑规则（src_mac + DROP/REJECT）
            try {
                LinkedHashMap<String, LinkedHashMap<String, String>> rules = readUciConfig("firewall");
                for (Map.Entry<String, LinkedHashMap<String, String>> e : rules.entrySet()) {
                    LinkedHashMap<String, String> sec = e.getValue();
                    if (!"rule".equals(sec.get(".type"))) continue;
                    String target = sec.get("target");
                    if (target == null
                            || !(target.equalsIgnoreCase("DROP") || target.equalsIgnoreCase("REJECT"))) {
                        continue;
                    }
                    String macs = sec.get("src_mac");
                    if (macs == null) continue;
                    for (String m : macs.trim().split("\\s+")) {
                        if (m.isEmpty()) continue;
                        String key = m.toLowerCase();
                        DhcpLease lease = byMac.get(key);
                        if (lease == null) {
                            lease = new DhcpLease();
                            lease.mac = m;
                            lease.expires = 0;
                            list.add(lease);
                            byMac.put(key, lease);
                        }
                        lease.blocked = true;
                        lease.blockSid = e.getKey();
                    }
                }
            } catch (Exception ignored) { }
            return list;
        }, callback);
    }

    /** 获取 WiFi 状态与关联客户端。 */
    public void getWifiInfo(ApiCallback<List<WifiInfo>> callback) {
        runAsync(() -> {
            List<WifiInfo> list = new ArrayList<>();
            boolean got = false;
            // 优先使用 iwinfo ubus 对象
            try {
                JSONObject devicesObj = callUbus("iwinfo", "devices", null);
                if (devicesObj != null) {
                    Iterator<String> keys = devicesObj.keys();
                    while (keys.hasNext()) {
                        String dev = keys.next();
                        WifiInfo wifi = new WifiInfo();
                        wifi.ifname = dev;
                        try {
                            JSONObject p = new JSONObject();
                            p.put("device", dev);
                            JSONObject info = callUbus("iwinfo", "info", p);
                            if (info == null) throw new IOException("iwinfo info 返回空响应");
                            got = true;
                            wifi.ssid = info.optString("ssid", "—");
                            wifi.mode = info.optString("mode", "—");
                            wifi.channel = info.optString("channel", "—");
                            wifi.country = info.optString("country", "—");
                            wifi.up = info.optInt("channel", 0) != 0 || !wifi.ssid.equals("—");
                            JSONObject enc = info.optJSONObject("encryption");
                            if (enc != null) {
                                wifi.encryption = enc.optString("encryption",
                                        enc.optString("description", "—"));
                            } else {
                                wifi.encryption = "—";
                            }
                            wifi.signal = info.optInt("signal", 0);
                        } catch (Exception ignored) {}
                        try {
                            JSONObject p = new JSONObject();
                            p.put("device", dev);
                            JSONObject assoc = callUbus("iwinfo", "assoclist", p);
                            JSONObject results = assoc.optJSONObject("results");
                            if (results != null) {
                                Iterator<String> macs = results.keys();
                                while (macs.hasNext()) {
                                    String mac = macs.next();
                                    JSONObject c = results.getJSONObject(mac);
                                    WifiInfo.WifiClient wc = new WifiInfo.WifiClient();
                                    wc.mac = mac;
                                    wc.signal = c.optInt("signal", 0);
                                    wc.connectedTime = c.optString("connected_time", "—");
                                    wc.hostname = c.optString("hostname", "");
                                    wifi.clients.add(wc);
                                }
                            }
                        } catch (Exception ignored) {}
                        list.add(wifi);
                    }
                }
            } catch (Exception ignored) { }
            // 设备列表为空或所有射频 info 读取失败时，改用命令输出解析。
            if (!got) {
                list = parseWifiViaExec();
            }
            return list;
        }, callback);
    }

    // ============================ WiFi 名称 / 密码 ============================

    /** 读取所有 AP 模式的无线配置（/etc/config/wireless）。 */
    public void getWirelessConfigs(ApiCallback<List<WirelessConfig>> callback) {
        runAsync(() -> {
            List<WirelessConfig> list = new ArrayList<>();
            LinkedHashMap<String, LinkedHashMap<String, String>> sections = readUciConfig("wireless");
            for (Map.Entry<String, LinkedHashMap<String, String>> e : sections.entrySet()) {
                LinkedHashMap<String, String> sec = e.getValue();
                if (!"wifi-iface".equals(sec.get(".type"))) continue;
                String mode = sec.get("mode");
                if (mode != null && !mode.trim().isEmpty() && !"ap".equalsIgnoreCase(mode)) continue;

                WirelessConfig c = new WirelessConfig();
                c.sid = e.getKey();
                c.ssid = sec.get("ssid");
                c.key = sec.get("key");
                c.encryption = firstNonEmpty(sec.get("encryption"), "none");
                c.device = firstNonEmpty(sec.get("device"), sec.get("ifname"), "—");
                c.disabled = truthy(sec.get("disabled"));
                list.add(c);
            }
            return list;
        }, callback);
    }

    /** 修改 AP 的 SSID 与密码（key 为空表示保持原密码），随后异步重载无线配置。 */
    public void updateWirelessConfig(String sid, String ssid, String key, ApiCallback<Void> callback) {
        runAsync(() -> {
            if (sid == null || sid.isEmpty()) throw new IOException("无线配置无效");
            final String newSsid = ssid == null ? "" : ssid.trim();
            if (newSsid.isEmpty()) throw new IOException("请输入 WiFi 名称");
            if (newSsid.length() > 32) throw new IOException("WiFi 名称不能超过 32 个字符");

            final String newKey = key == null ? "" : key;
            if (!newKey.isEmpty() && !isValidWifiKey(newKey)) {
                throw new IOException("密码需为 8-63 位字符，或 64 位十六进制");
            }

            String encryption = null;
            try {
                LinkedHashMap<String, LinkedHashMap<String, String>> sections = readUciConfig("wireless");
                LinkedHashMap<String, String> sec = sections.get(sid);
                if (sec != null) encryption = sec.get("encryption");
            } catch (Exception ignored) { }

            execUci("set", "wireless." + sid + ".ssid=" + newSsid);
            if (!newKey.isEmpty()) {
                execUci("set", "wireless." + sid + ".key=" + newKey);
                if (encryption == null || encryption.isEmpty()
                        || "none".equalsIgnoreCase(encryption)) {
                    execUci("set", "wireless." + sid + ".encryption=psk2");
                }
            }
            execUci("commit", "wireless");
            // 异步重载，避免本机正好连着该 WiFi 时请求被中断而误报失败
            execShell("(/sbin/wifi reload || /etc/init.d/network reload) >/dev/null 2>&1 &");
            return null;
        }, callback);
    }

    private static boolean isValidWifiKey(String key) {
        if (key.length() >= 8 && key.length() <= 63) return true;
        return key.length() == 64 && key.matches("[0-9A-Fa-f]{64}");
    }

    /** 通过执行 iwinfo 命令并解析文本获取 WiFi 信息（兼容 ubus iwinfo 不可用的固件）。 */
    private List<WifiInfo> parseWifiViaExec() throws Exception {
        String text = execIwinfo(new String[]{});
        List<WifiInfo> list = parseIwinfoText(text);
        for (WifiInfo w : list) {
            try {
                w.clients = parseAssoclist(execIwinfo(new String[]{w.ifname, "assoclist"}));
            } catch (Exception ignored) { }
        }
        return list;
    }

    private String execIwinfo(String[] args) throws Exception {
        JSONObject p = new JSONObject();
        p.put("command", "/usr/bin/iwinfo");
        JSONArray arr = new JSONArray();
        for (String a : args) arr.put(a);
        p.put("params", arr);
        return callUbus("file", "exec", p).optString("stdout", "");
    }

    private static final Pattern WIFI_HEADER = Pattern.compile("^(\\S+)\\s+ESSID:\\s*\"?([^\"]*)\"?");
    private static final Pattern WIFI_CHANNEL = Pattern.compile("Channel:\\s*(\\d+)");
    private static final Pattern WIFI_SIGNAL = Pattern.compile("Signal:\\s*(-?\\d+)\\s*dBm");
    private static final Pattern WIFI_ENC = Pattern.compile("(?m)Encryption:\\s*(.+)$");
    private static final Pattern WIFI_MODE = Pattern.compile("Mode:\\s*(\\w+)");

    private List<WifiInfo> parseIwinfoText(String text) {
        List<WifiInfo> list = new ArrayList<>();
        if (text == null || text.isEmpty()) return list;
        String[] blocks = text.split("\\n\\s*\\n");
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;
            Matcher mh = WIFI_HEADER.matcher(block);
            if (!mh.find()) continue;
            WifiInfo w = new WifiInfo();
            w.ifname = mh.group(1);
            w.ssid = mh.group(2) == null ? "" : mh.group(2);
            Matcher mc = WIFI_CHANNEL.matcher(block);
            if (mc.find()) w.channel = mc.group(1);
            Matcher ms = WIFI_SIGNAL.matcher(block);
            if (ms.find()) { try { w.signal = Integer.parseInt(ms.group(1)); } catch (Exception ignored) {} }
            Matcher me = WIFI_ENC.matcher(block);
            if (me.find()) w.encryption = me.group(1).trim();
            Matcher mm = WIFI_MODE.matcher(block);
            if (mm.find()) w.mode = mm.group(1);
            w.up = (w.channel != null && !w.channel.isEmpty()) || !w.ssid.isEmpty();
            list.add(w);
        }
        return list;
    }

    private static final Pattern ASSOC_MAC = Pattern.compile("(?m)^([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})\\s+(-?\\d+)\\s*dBm");

    private List<WifiInfo.WifiClient> parseAssoclist(String text) {
        List<WifiInfo.WifiClient> clients = new ArrayList<>();
        if (text == null || text.isEmpty()) return clients;
        Matcher m = ASSOC_MAC.matcher(text);
        while (m.find()) {
            WifiInfo.WifiClient c = new WifiInfo.WifiClient();
            c.mac = m.group(1);
            try { c.signal = Integer.parseInt(m.group(2)); } catch (Exception ignored) {}
            clients.add(c);
        }
        return clients;
    }

    /** 重启路由器。 */
    public void reboot(ApiCallback<Void> callback) {
        runAsync(() -> {
            callUbus("system", "reboot", null);
            logout();
            return null;
        }, callback);
    }

    /** 重启指定网络接口。 */
    public void restartInterface(String iface, ApiCallback<Void> callback) {
        runAsync(() -> {
            JSONObject p = new JSONObject();
            p.put("interface", iface);
            callUbus("network.interface." + iface, "restart", p);
            return null;
        }, callback);
    }

    /** 获取所有服务 / 插件状态（合并 ubus service list 与 rc list）。 */
    public void getServices(ApiCallback<List<ServiceInfo>> callback) {
        runAsync(() -> {
            List<ServiceInfo> list = new ArrayList<>();
            JSONObject services = callUbus("service", "list", null);
            JSONObject rc = callUbus("rc", "list", null);

            // rc list 中的为可管理 init 脚本，service list 中可能多出运行中的子进程（如 openclash-watchdog）
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            if (rc != null) { Iterator<String> it = rc.keys(); while (it.hasNext()) names.add(it.next()); }
            if (services != null) { Iterator<String> it = services.keys(); while (it.hasNext()) names.add(it.next()); }

            for (String name : names) {
                ServiceInfo s = new ServiceInfo();
                s.name = name;

                JSONObject r = rc != null ? rc.optJSONObject(name) : null;
                if (r != null) {
                    s.hasInit = true;
                    s.enabled = r.optBoolean("enabled", false);
                    s.startOrder = r.optInt("start", 0);
                    s.running = r.optBoolean("running", false);
                }

                JSONObject svc = services != null ? services.optJSONObject(name) : null;
                if (svc != null) {
                    JSONObject instances = svc.optJSONObject("instances");
                    if (instances != null) {
                        Iterator<String> ik = instances.keys();
                        boolean anyRunning = false;
                        while (ik.hasNext()) {
                            s.instanceCount++;
                            JSONObject inst = instances.optJSONObject(ik.next());
                            if (inst == null) continue;
                            if (inst.optBoolean("running", false)) anyRunning = true;
                            if (s.pid == 0 && inst.has("pid")) s.pid = inst.optInt("pid", 0);
                            if (s.command == null) {
                                JSONArray cmd = inst.optJSONArray("command");
                                if (cmd != null && cmd.length() > 0) {
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 0; i < cmd.length(); i++) {
                                        if (i > 0) sb.append(' ');
                                        sb.append(cmd.optString(i, ""));
                                    }
                                    s.command = sb.toString();
                                }
                            }
                        }
                        if (s.instanceCount > 0) s.running = anyRunning;
                    }
                }
                list.add(s);
            }
            return list;
        }, callback);
    }

    /** 对服务执行 init 动作：start / stop / restart / enable / disable / status。返回命令 stdout。 */
    public void serviceAction(String name, String action, ApiCallback<String> callback) {
        runAsync(() -> execInit(name, action), callback);
    }

    private String execInit(String name, String action) throws Exception {
        JSONObject p = new JSONObject();
        p.put("command", "/etc/init.d/" + name);
        JSONArray arr = new JSONArray();
        arr.put(action);
        p.put("params", arr);
        JSONObject data = callUbusLong("file", "exec", p);
        int code = data.optInt("code", -1);
        String stdout = data.optString("stdout", "");
        String stderr = data.optString("stderr", "");
        if (code != 0) {
            String msg = (stderr == null || stderr.isEmpty()) ? stdout : stderr;
            throw new IOException("执行失败 (code=" + code + ")" + (msg.isEmpty() ? "" : ": " + msg));
        }
        return stdout;
    }

    // ============================ 静态 IP / 设备拉黑 ============================

    private static final Pattern MAC_PATTERN =
            Pattern.compile("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    /** 为设备设置固定 IP（/etc/config/dhcp 静态租约），完成后重启 dnsmasq。 */
    public void setStaticLease(DhcpLease lease, String ip, ApiCallback<Void> callback) {
        runAsync(() -> {
            if (lease == null || lease.mac == null || !MAC_PATTERN.matcher(lease.mac).matches()) {
                throw new IOException("设备 MAC 地址无效");
            }
            if (ip == null || !IPV4_PATTERN.matcher(ip.trim()).matches()) {
                throw new IOException("IP 地址格式不正确");
            }
            final String fixedIp = ip.trim();
            String name = (lease.hostname == null || lease.hostname.trim().isEmpty()
                    || lease.hostname.equals("*"))
                    ? "device_" + lease.mac.replace(":", "")
                    : lease.hostname.trim();

            String sid = lease.staticSid;
            LinkedHashMap<String, LinkedHashMap<String, String>> sections = readUciConfig("dhcp");
            for (Map.Entry<String, LinkedHashMap<String, String>> e : sections.entrySet()) {
                if (!"host".equals(e.getValue().get(".type"))) continue;
                if (sid != null && sid.equals(e.getKey())) continue;
                if (fixedIp.equals(e.getValue().get("ip"))) {
                    String usedMac = e.getValue().get("mac");
                    throw new IOException("IP " + fixedIp + " 已被"
                            + (usedMac == null ? "其他设备" : "设备 " + usedMac) + "固定");
                }
            }
            if (sid == null) {
                sid = execUci("add", "dhcp", "host").trim();
                if (sid.isEmpty()) throw new IOException("创建静态租约失败");
            }
            execUci("set", "dhcp." + sid + ".mac=" + lease.mac);
            execUci("set", "dhcp." + sid + ".ip=" + fixedIp);
            execUci("set", "dhcp." + sid + ".name=" + name);
            execUci("commit", "dhcp");

            // 清除该 MAC 的旧动态租约，使其立即按固定地址重新获取
            execShell("grep -iv \" " + lease.mac.toLowerCase()
                    + " \" /tmp/dhcp.leases > /tmp/dhcp.leases.tmp 2>/dev/null && "
                    + "mv /tmp/dhcp.leases.tmp /tmp/dhcp.leases");
            execInit("dnsmasq", "restart");
            return null;
        }, callback);
    }

    /** 取消设备的固定 IP。 */
    public void removeStaticLease(String sid, ApiCallback<Void> callback) {
        runAsync(() -> {
            if (sid == null || sid.isEmpty()) throw new IOException("未找到静态租约配置");
            execUci("delete", "dhcp." + sid);
            execUci("commit", "dhcp");
            execInit("dnsmasq", "restart");
            return null;
        }, callback);
    }

    /** 拉黑设备：新增防火墙规则，丢弃该 MAC 的所有流量。 */
    public void blockDevice(String mac, ApiCallback<Void> callback) {
        runAsync(() -> {
            if (mac == null || !MAC_PATTERN.matcher(mac).matches()) {
                throw new IOException("设备 MAC 地址无效");
            }
            String sid = execUci("add", "firewall", "rule").trim();
            if (sid.isEmpty()) throw new IOException("创建防火墙规则失败");
            execUci("set", "firewall." + sid + ".name=Block-" + mac);
            execUci("set", "firewall." + sid + ".src=lan");
            execUci("set", "firewall." + sid + ".src_mac=" + mac);
            execUci("set", "firewall." + sid + ".dest=*");
            execUci("set", "firewall." + sid + ".proto=all");
            execUci("set", "firewall." + sid + ".target=DROP");
            execUci("set", "firewall." + sid + ".enabled=1");
            execUci("commit", "firewall");
            execInit("firewall", "reload");
            return null;
        }, callback);
    }

    /** 解除拉黑：删除对应防火墙规则。 */
    public void unblockDevice(String sid, ApiCallback<Void> callback) {
        runAsync(() -> {
            if (sid == null || sid.isEmpty()) throw new IOException("未找到拉黑规则");
            execUci("delete", "firewall." + sid);
            execUci("commit", "firewall");
            execInit("firewall", "reload");
            return null;
        }, callback);
    }

    // ============================ OpenClash ============================

    /** 读取 OpenClash 配置（/etc/config/openclash）及其订阅列表。 */
    public void getOpenClash(ApiCallback<OpenClashConfig> callback) {
        runAsync(() -> {
            OpenClashConfig oc = loadOpenClash();
            if (oc == null) throw new IOException("未检测到 OpenClash（未找到 /etc/config/openclash）");
            markCurrentSubscribe(oc);
            try {
                JSONObject rc = callUbus("rc", "list", null);
                JSONObject entry = rc.optJSONObject("openclash");
                if (entry != null) oc.running = entry.optBoolean("running", false);
            } catch (Exception ignored) { }
            return oc;
        }, callback);
    }

    /** 新增订阅，enabled 控制是否启用下载。 */
    public void addOpenClashSubscribe(String name, String address, boolean enabled, ApiCallback<Void> callback) {
        runAsync(() -> {
            String sid = execUci("add", "openclash", "config_subscribe").trim();
            if (sid.isEmpty()) throw new IOException("创建订阅配置失败");
            execUci("set", "openclash." + sid + ".name=" + name);
            execUci("set", "openclash." + sid + ".address=" + address);
            execUci("set", "openclash." + sid + ".enabled=" + (enabled ? "1" : "0"));
            execUci("commit", "openclash");
            return null;
        }, callback);
    }

    /** 修改订阅的名称 / 地址 / 启用状态（为 null 的字段不修改）。 */
    public void updateOpenClashSubscribe(String sid, String name, String address, boolean enabled,
                                         ApiCallback<Void> callback) {
        runAsync(() -> {
            if (name != null) execUci("set", "openclash." + sid + ".name=" + name);
            if (address != null) execUci("set", "openclash." + sid + ".address=" + address);
            execUci("set", "openclash." + sid + ".enabled=" + (enabled ? "1" : "0"));
            execUci("commit", "openclash");
            return null;
        }, callback);
    }

    /** 删除订阅。 */
    public void deleteOpenClashSubscribe(String sid, ApiCallback<Void> callback) {
        runAsync(() -> {
            execUci("delete", "openclash." + sid);
            execUci("commit", "openclash");
            return null;
        }, callback);
    }

    /** 后台下载订阅配置并应用（调用 openclash.sh，与 LuCI 的「更新配置」一致）。 */
    public void updateOpenClashSubscriptions(ApiCallback<String> callback) {
        runAsync(() -> {
            String script = "/usr/share/openclash/openclash.sh";
            String cmd = "if [ -x " + script + " ]; then " + script
                    + " >/tmp/openclash_app_update.log 2>&1 & echo __STARTED__; else echo __MISSING__; fi";
            JSONObject p = new JSONObject();
            p.put("command", "/bin/sh");
            JSONArray arr = new JSONArray();
            arr.put("-c");
            arr.put(cmd);
            p.put("params", arr);
            String out = callUbusLong("file", "exec", p).optString("stdout", "");
            if (out.contains("__MISSING__")) {
                throw new IOException("未找到 " + script + "，请确认 OpenClash 已正确安装");
            }
            return "已开始更新订阅，OpenClash 会自动重启生效";
        }, callback);
    }

    /**
     * 读取 UCI 配置，返回 sectionId -> 选项映射（含 ".type"）。
     * 优先使用 ubus uci get，失败时回退 uci show 命令解析。
     */
    private LinkedHashMap<String, LinkedHashMap<String, String>> readUciConfig(String config) throws Exception {
        LinkedHashMap<String, LinkedHashMap<String, String>> result = new LinkedHashMap<>();
        try {
            JSONObject params = new JSONObject();
            params.put("config", config);
            JSONObject values = callUbus("uci", "get", params).optJSONObject("values");
            if (values != null && values.length() > 0) {
                List<String> keys = new ArrayList<>();
                for (Iterator<String> it = values.keys(); it.hasNext(); ) keys.add(it.next());
                Collections.sort(keys, (a, b) -> {
                    JSONObject ja = values.optJSONObject(a);
                    JSONObject jb = values.optJSONObject(b);
                    return (ja == null ? 0 : ja.optInt(".index", 0)) - (jb == null ? 0 : jb.optInt(".index", 0));
                });
                for (String key : keys) {
                    JSONObject sec = values.optJSONObject(key);
                    if (sec == null) continue;
                    LinkedHashMap<String, String> map = new LinkedHashMap<>();
                    for (Iterator<String> it = sec.keys(); it.hasNext(); ) {
                        String k = it.next();
                        Object v = sec.opt(k);
                        if (v == null || v == JSONObject.NULL) continue;
                        map.put(k, String.valueOf(v));
                    }
                    result.put(key, map);
                }
                if (!result.isEmpty()) return result;
            }
        } catch (Exception ignored) { }
        // 回退：部分固件没有 ubus uci 对象
        String prefix = config + ".";
        String out = execShell("uci show " + config + " 2>/dev/null");
        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (!line.startsWith(prefix)) continue;
            String rest = line.substring(prefix.length());
            int eq = rest.indexOf('=');
            if (eq <= 0) continue;
            String path = rest.substring(0, eq);
            String value = unquoteUci(rest.substring(eq + 1));
            int dot = path.indexOf('.');
            String secRef = dot < 0 ? path : path.substring(0, dot);
            String opt = dot < 0 ? ".type" : path.substring(dot + 1);
            LinkedHashMap<String, String> sec = result.get(secRef);
            if (sec == null) {
                sec = new LinkedHashMap<>();
                result.put(secRef, sec);
            }
            sec.put(opt, value);
        }
        return result;
    }

    private String execShell(String cmd) throws Exception {
        JSONObject p = new JSONObject();
        p.put("command", "/bin/sh");
        JSONArray arr = new JSONArray();
        arr.put("-c");
        arr.put(cmd);
        p.put("params", arr);
        return callUbus("file", "exec", p).optString("stdout", "");
    }

    private OpenClashConfig loadOpenClash() throws Exception {
        LinkedHashMap<String, LinkedHashMap<String, String>> sections = readUciConfig("openclash");
        if (sections.isEmpty()) return null;

        OpenClashConfig oc = new OpenClashConfig();
        LinkedHashMap<String, String> global = null;
        LinkedHashMap<String, String> fallbackGlobal = null;
        for (Map.Entry<String, LinkedHashMap<String, String>> e : sections.entrySet()) {
            LinkedHashMap<String, String> sec = e.getValue();
            String type = sec.get(".type");
            if ("config_subscribe".equals(type)) {
                OpenClashSubscribe s = new OpenClashSubscribe();
                s.sid = e.getKey();
                s.name = emptyToNull(sec.get("name"));
                s.address = firstNonEmpty(sec.get("address"), sec.get("url"));
                s.enabled = !sec.containsKey("enabled") || truthy(sec.get("enabled"));
                s.subUa = emptyToNull(sec.get("sub_ua"));
                s.subConvert = emptyToNull(sec.get("sub_convert"));
                oc.subscribes.add(s);
            } else if ("openclash".equals(type) && global == null) {
                global = sec;
            } else if (fallbackGlobal == null) {
                fallbackGlobal = sec;
            }
        }
        if (global == null) global = fallbackGlobal;
        if (global == null) return null;
        fillGlobal(oc, global);
        return oc;
    }

    private void fillGlobal(OpenClashConfig oc, Map<String, String> g) {
        oc.enable = truthy(g.get("enable"));
        oc.proxyMode = g.get("proxy_mode");
        oc.operationMode = firstNonEmpty(g.get("en_mode"), g.get("operation_mode"));
        oc.configPath = g.get("config_path");
        oc.httpPort = g.get("http_port");
        oc.socksPort = g.get("socks_port");
        oc.mixedPort = g.get("mixed_port");
        oc.proxyPort = firstNonEmpty(g.get("proxy_port"), g.get("redir_port"), g.get("tproxy_port"));
        oc.dnsPort = g.get("dns_port");
        oc.cnPort = g.get("cn_port");
        oc.coreType = g.get("core_type");
        oc.dashboardType = g.get("dashboard_type");
    }

    private void markCurrentSubscribe(OpenClashConfig oc) {
        String current = oc.currentConfigName();
        for (OpenClashSubscribe s : oc.subscribes) {
            s.isCurrent = current != null && s.name != null && current.equalsIgnoreCase(s.name);
        }
    }

    /** 执行 uci 命令（参数逐个传递，避免 shell 引号问题）。 */
    private String execUci(String... args) throws Exception {
        JSONObject p = new JSONObject();
        p.put("command", "/sbin/uci");
        JSONArray arr = new JSONArray();
        for (String a : args) arr.put(a);
        p.put("params", arr);
        JSONObject data = callUbusLong("file", "exec", p);
        int code = data.optInt("code", -1);
        String stdout = data.optString("stdout", "");
        if (code != 0) {
            String err = data.optString("stderr", "");
            if (err.trim().isEmpty()) err = stdout;
            throw new IOException("UCI 操作失败" + (err.trim().isEmpty() ? "" : "：" + err.trim()));
        }
        return stdout;
    }

    private static boolean truthy(String v) {
        if (v == null) return false;
        String s = v.trim().toLowerCase();
        return s.equals("1") || s.equals("true") || s.equals("on") || s.equals("yes") || s.equals("enabled");
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return null;
    }

    private static String emptyToNull(String v) {
        return (v == null || v.isEmpty()) ? null : v;
    }

    private static String unquoteUci(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.length() >= 2 && s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'') {
            s = s.substring(1, s.length() - 1).replace("'\\''", "'");
        }
        return s;
    }

    /** 简单 ping 测试路由器是否可达。 */
    public void ping(ApiCallback<Boolean> callback) {
        executor.execute(() -> {
            boolean ok = false;
            try {
                InetAddress addr = InetAddress.getByName(config.getHost());
                ok = addr.isReachable(3000);
            } catch (Exception ignored) { }
            final boolean result = ok;
            mainHandler.post(() -> callback.onSuccess(result));
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
