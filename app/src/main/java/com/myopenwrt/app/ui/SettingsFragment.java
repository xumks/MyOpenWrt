package com.myopenwrt.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.myopenwrt.app.R;
import com.myopenwrt.app.api.ApiCallback;
import com.myopenwrt.app.api.OpenWrtApi;
import com.myopenwrt.app.data.PrefsManager;
import com.myopenwrt.app.data.RouterConfig;

public class SettingsFragment extends Fragment {

    private EditText etHost, etPort, etUsername, etPassword;
    private Switch swHttps;
    private Button btnSave, btnTest;
    private ProgressBar progress;
    private TextView tvStatus;
    private OpenWrtApi api;
    private PrefsManager prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = OpenWrtApi.getInstance(requireContext());
        prefs = new PrefsManager(requireContext());

        etHost = view.findViewById(R.id.et_host);
        etPort = view.findViewById(R.id.et_port);
        etUsername = view.findViewById(R.id.et_username);
        etPassword = view.findViewById(R.id.et_password);
        swHttps = view.findViewById(R.id.sw_https);
        btnSave = view.findViewById(R.id.btn_save);
        btnTest = view.findViewById(R.id.btn_test);
        progress = view.findViewById(R.id.progress);
        tvStatus = view.findViewById(R.id.tv_status);

        loadExisting();

        btnSave.setOnClickListener(v -> saveAndConnect());
        btnTest.setOnClickListener(v -> testConnection());
    }

    private void loadExisting() {
        RouterConfig cfg = prefs.loadConfig();
        etHost.setText(cfg.getHost());
        etPort.setText(String.valueOf(cfg.getPort()));
        etUsername.setText(cfg.getUsername());
        etPassword.setText(cfg.getPassword());
        swHttps.setChecked(cfg.isUseHttps());
        updateStatus();
    }

    private RouterConfig readConfig() {
        String host = etHost.getText().toString().trim();
        String portStr = etPort.getText().toString().trim();
        String user = etUsername.getText().toString().trim();
        String pass = etPassword.getText().toString();
        if (TextUtils.isEmpty(host)) {
            etHost.setError("请输入路由器地址");
            return null;
        }
        int port = 80;
        try { port = Integer.parseInt(portStr); } catch (Exception ignored) { }
        if (port <= 0 || port > 65535) port = swHttps.isChecked() ? 443 : 80;
        return new RouterConfig(host, port, user.isEmpty() ? "root" : user, pass, swHttps.isChecked());
    }

    private void saveAndConnect() {
        RouterConfig cfg = readConfig();
        if (cfg == null) return;
        api.setConfig(cfg);
        setLoading(true);
        tvStatus.setText("正在连接并登录…");
        api.login(new ApiCallback<String>() {
            @Override
            public void onSuccess(String sessionId) {
                setLoading(false);
                tvStatus.setText("已连接：" + cfg.getDisplayName());
                Toast.makeText(getContext(), "连接成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String message, Exception e) {
                setLoading(false);
                tvStatus.setText("连接失败：" + message);
                Toast.makeText(getContext(), "连接失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void testConnection() {
        RouterConfig cfg = readConfig();
        if (cfg == null) return;
        api.setConfig(cfg);
        setLoading(true);
        tvStatus.setText("正在测试连通性…");
        api.ping(new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean reachable) {
                if (reachable) {
                    tvStatus.setText("路由器可达，正在验证登录…");
                    api.login(new ApiCallback<String>() {
                        @Override
                        public void onSuccess(String sessionId) {
                            setLoading(false);
                            tvStatus.setText("测试通过：登录成功 ✓");
                        }
                        @Override
                        public void onFailure(String message, Exception e) {
                            setLoading(false);
                            tvStatus.setText("可达但登录失败：" + message);
                        }
                    });
                } else {
                    setLoading(false);
                    tvStatus.setText("路由器不可达，请检查地址/网络");
                }
            }
            @Override
            public void onFailure(String message, Exception e) {
                setLoading(false);
                tvStatus.setText("测试失败：" + message);
            }
        });
    }

    private void updateStatus() {
        if (api.hasSession()) {
            tvStatus.setText("已连接：" + api.getConfig().getDisplayName());
        } else if (prefs.hasConfig()) {
            tvStatus.setText("已保存配置，尚未连接");
        } else {
            tvStatus.setText("尚未配置路由器");
        }
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!loading);
        btnTest.setEnabled(!loading);
    }
}
