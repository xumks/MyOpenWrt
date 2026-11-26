package com.myopenwrt.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.myopenwrt.app.MainActivity;
import com.myopenwrt.app.R;
import com.myopenwrt.app.api.ApiCallback;
import com.myopenwrt.app.api.OpenWrtApi;
import com.myopenwrt.app.api.model.SystemInfo;
import com.myopenwrt.app.util.FormatUtils;

public class DashboardFragment extends Fragment {

    private OpenWrtApi api;
    private SwipeRefreshLayout swipe;
    private ProgressBar progress;
    private View rootView;
    private View emptyView;
    private Button btnGoSettings, btnReboot, btnServices, btnOpenClash;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = OpenWrtApi.getInstance(requireContext());
        rootView = view;

        swipe = view.findViewById(R.id.swipe);
        progress = view.findViewById(R.id.progress);
        emptyView = view.findViewById(R.id.empty_view);
        btnGoSettings = view.findViewById(R.id.btn_go_settings);
        btnReboot = view.findViewById(R.id.btn_reboot);
        btnServices = view.findViewById(R.id.btn_services);
        btnOpenClash = view.findViewById(R.id.btn_openclash);

        swipe.setOnRefreshListener(this::loadData);
        btnGoSettings.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateTo(R.id.nav_settings);
            }
        });
        btnReboot.setOnClickListener(v -> confirmReboot());
        btnServices.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openServices();
            }
        });
        btnOpenClash.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openOpenClash();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        if (!api.hasSession()) {
            showEmpty(true);
            swipe.setRefreshing(false);
            return;
        }
        showEmpty(false);
        progress.setVisibility(View.VISIBLE);
        api.getSystemInfo(new ApiCallback<SystemInfo>() {
            @Override
            public void onSuccess(SystemInfo info) {
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                bind(info);
            }

            @Override
            public void onFailure(String message, Exception e) {
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                Toast.makeText(getContext(), "加载失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void bind(SystemInfo info) {
        setRow(R.id.row_model, getString(R.string.label_model), info.model);
        setRow(R.id.row_firmware, getString(R.string.label_firmware),
                (info.release != null ? info.release : "—")
                        + (info.revision != null && !info.revision.isEmpty() ? " (" + info.revision + ")" : ""));
        setRow(R.id.row_kernel, getString(R.string.label_kernel), info.kernel);
        setRow(R.id.row_hostname, getString(R.string.label_hostname), info.hostname);
        setRow(R.id.row_uptime, getString(R.string.label_uptime), FormatUtils.formatUptime(info.uptime));
        setRow(R.id.row_load1, getString(R.string.label_load1), FormatUtils.formatLoad(info.load1));
        setRow(R.id.row_load5, getString(R.string.label_load5), FormatUtils.formatLoad(info.load5));
        setRow(R.id.row_load15, getString(R.string.label_load15), FormatUtils.formatLoad(info.load15));
        setRow(R.id.row_memory, getString(R.string.label_memory),
                FormatUtils.formatMemory(info.memoryTotal, info.getMemoryUsed()));
        setRow(R.id.row_swap, getString(R.string.label_swap),
                FormatUtils.formatMemory(info.swapTotal, info.swapTotal - info.swapFree));
    }

    private void setRow(int rowId, String label, String value) {
        if (rootView == null) return;
        View row = rootView.findViewById(rowId);
        if (row == null) return;
        TextView tvLabel = row.findViewById(R.id.info_label);
        TextView tvValue = row.findViewById(R.id.info_value);
        if (tvLabel != null) tvLabel.setText(label);
        if (tvValue != null) tvValue.setText(value != null ? value : "—");
    }

    private void confirmReboot() {
        new AlertDialog.Builder(requireContext())
                .setTitle("重启路由器")
                .setMessage("确定要重启路由器吗？重启期间网络将中断约 1-2 分钟。")
                .setPositiveButton("重启", (d, w) -> doReboot())
                .setNegativeButton("取消", null)
                .show();
    }

    private void doReboot() {
        Toast.makeText(getContext(), "正在重启路由器…", Toast.LENGTH_SHORT).show();
        api.reboot(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(getContext(), "重启命令已发送，请等待路由器重新上线", Toast.LENGTH_LONG).show();
            }
            @Override
            public void onFailure(String message, Exception e) {
                Toast.makeText(getContext(), "重启失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showEmpty(boolean show) {
        emptyView.setVisibility(show ? View.VISIBLE : View.GONE);
        swipe.setVisibility(show ? View.GONE : View.VISIBLE);
    }
}
