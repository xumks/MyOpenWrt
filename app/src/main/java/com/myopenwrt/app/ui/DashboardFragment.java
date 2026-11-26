package com.myopenwrt.app.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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
    private Button btnGoSettings;
    private TextView tvSubtitle, tvHeroHostname, tvHeroModel, tvHeroFirmware, tvHeroKernel, tvHeroUptime;
    private TextView tvLoad1, tvLoad5, tvLoad15, tvMemory;
    private ProgressBar progressMemory;

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
        tvSubtitle = view.findViewById(R.id.tv_subtitle);
        tvHeroHostname = view.findViewById(R.id.tv_hero_hostname);
        tvHeroModel = view.findViewById(R.id.tv_hero_model);
        tvHeroFirmware = view.findViewById(R.id.tv_hero_firmware);
        tvHeroKernel = view.findViewById(R.id.tv_hero_kernel);
        tvHeroUptime = view.findViewById(R.id.tv_hero_uptime);
        tvLoad1 = view.findViewById(R.id.tv_load1);
        tvLoad5 = view.findViewById(R.id.tv_load5);
        tvLoad15 = view.findViewById(R.id.tv_load15);
        tvMemory = view.findViewById(R.id.tv_memory);
        progressMemory = view.findViewById(R.id.progress_memory);

        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::loadData);
        btnGoSettings.setOnClickListener(v -> openSettings());
        view.findViewById(R.id.btn_go_settings_quick).setOnClickListener(v -> openSettings());
        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> loadData());
        view.findViewById(R.id.btn_reboot).setOnClickListener(v -> confirmReboot());
        view.findViewById(R.id.btn_services).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openServices();
            }
        });
        view.findViewById(R.id.btn_openclash).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openOpenClash();
            }
        });
    }

    private void openSettings() {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateTo(R.id.nav_settings);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateSubtitle();
        loadData();
    }

    private void updateSubtitle() {
        if (tvSubtitle == null) return;
        tvSubtitle.setText(api.hasSession()
                ? api.getConfig().getDisplayName()
                : "尚未连接路由器，请先在设置中配置");
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
                if (!isAdded()) return;
                progress.setVisibility(View.GONE);
                swipe.setRefreshing(false);
                bind(info);
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded()) return;
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
        setRow(R.id.row_swap, getString(R.string.label_swap),
                FormatUtils.formatMemory(info.swapTotal, info.swapTotal - info.swapFree));

        tvHeroHostname.setText(info.hostname != null ? info.hostname : "OpenWrt");
        tvHeroModel.setText(info.model != null ? info.model : "—");
        tvHeroFirmware.setText(info.release != null && !info.release.isEmpty() ? info.release : "—");
        tvHeroKernel.setText(info.kernel != null && !info.kernel.isEmpty() ? info.kernel : "—");
        tvHeroUptime.setText(FormatUtils.formatUptime(info.uptime));

        tvLoad1.setText(FormatUtils.formatLoad(info.load1));
        tvLoad5.setText(FormatUtils.formatLoad(info.load5));
        tvLoad15.setText(FormatUtils.formatLoad(info.load15));

        long used = info.getMemoryUsed();
        tvMemory.setText(FormatUtils.formatMemory(info.memoryTotal, used));
        int percent = info.memoryTotal > 0
                ? (int) Math.min(100, used * 100 / info.memoryTotal)
                : 0;
        progressMemory.setProgress(percent);
    }

    private void setRow(int rowId, String label, String value) {
        if (rootView == null) return;
        View row = rootView.findViewById(rowId);
        if (row == null) return;
        TextView tvLabel = row.findViewById(R.id.info_label);
        TextView tvValue = row.findViewById(R.id.info_value);
        if (tvLabel != null) tvLabel.setText(label);
        if (tvValue != null) tvValue.setText(value != null && !value.isEmpty() ? value : "—");
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
