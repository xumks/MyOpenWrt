package com.myopenwrt.app.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.myopenwrt.app.R;
import com.myopenwrt.app.api.ApiCallback;
import com.myopenwrt.app.api.OpenWrtApi;
import com.myopenwrt.app.api.model.ServiceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ServicesFragment extends Fragment {

    private OpenWrtApi api;
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView emptyText;
    private TextView tvSubtitle;
    private EditText etSearch;
    private TextView chipAll, chipRunning, chipEnabled;
    private ServiceAdapter adapter;
    private final List<ServiceInfo> allData = new ArrayList<>();
    private final List<ServiceInfo> filtered = new ArrayList<>();
    private String filter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_services, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = OpenWrtApi.getInstance(requireContext());
        swipe = view.findViewById(R.id.swipe);
        recycler = view.findViewById(R.id.recycler);
        emptyText = view.findViewById(R.id.empty_text);
        tvSubtitle = view.findViewById(R.id.tv_subtitle);
        etSearch = view.findViewById(R.id.et_search);
        chipAll = view.findViewById(R.id.chip_all);
        chipRunning = view.findViewById(R.id.chip_running);
        chipEnabled = view.findViewById(R.id.chip_enabled);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ServiceAdapter();
        recycler.setAdapter(adapter);

        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::loadData);
        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> loadData());
        chipAll.setOnClickListener(v -> setFilter("all"));
        chipRunning.setOnClickListener(v -> setFilter("running"));
        chipEnabled.setOnClickListener(v -> setFilter("enabled"));
        updateChips();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { applyFilter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
    }

    private void setFilter(String value) {
        filter = value;
        updateChips();
        applyFilter(etSearch.getText().toString());
    }

    private void updateChips() {
        styleChip(chipAll, "all".equals(filter));
        styleChip(chipRunning, "running".equals(filter));
        styleChip(chipEnabled, "enabled".equals(filter));
    }

    private void styleChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected
                ? R.drawable.bg_filter_chip_selected : R.drawable.bg_filter_chip);
        chip.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.white : R.color.text_primary));
        chip.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void loadData() {
        if (!api.hasSession()) {
            emptyText.setText("请先在「设置」中连接路由器");
            emptyText.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            swipe.setRefreshing(false);
            if (tvSubtitle != null) tvSubtitle.setText(R.string.state_disconnected);
            return;
        }
        api.getServices(new ApiCallback<List<ServiceInfo>>() {
            @Override
            public void onSuccess(List<ServiceInfo> result) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                allData.clear();
                allData.addAll(result);
                if (tvSubtitle != null) {
                    tvSubtitle.setText(allData.isEmpty()
                            ? getString(R.string.services_subtitle)
                            : "共 " + allData.size() + " 个服务");
                }
                applyFilter(etSearch.getText().toString());
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                Toast.makeText(getContext(), "加载失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilter(String kw) {
        filtered.clear();
        String key = kw == null ? "" : kw.toLowerCase(Locale.getDefault()).trim();
        for (ServiceInfo s : allData) {
            if (!key.isEmpty()
                    && (s.name == null || !s.name.toLowerCase(Locale.getDefault()).contains(key))) {
                continue;
            }
            if ("running".equals(filter) && !s.running) continue;
            if ("enabled".equals(filter) && !s.enabled) continue;
            filtered.add(s);
        }
        if (filtered.isEmpty()) {
            emptyText.setText(allData.isEmpty() ? R.string.svc_empty : R.string.svc_no_match);
            emptyText.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    // ============================ 服务控制 ============================

    private void onToggle(ServiceInfo s) {
        if (!s.hasInit) { toastNoInit(s.name); return; }
        if (s.running) {
            confirm("停止 " + s.name + " ？期间依赖它的功能将不可用。", s.name, "stop");
        } else {
            runAction(s.name, "start", true);
        }
    }

    private void onRestart(ServiceInfo s) {
        if (!s.hasInit) { toastNoInit(s.name); return; }
        confirm("重启 " + s.name + " ？期间服务可能短暂中断。", s.name, "restart");
    }

    private void onStatus(ServiceInfo s) {
        if (!s.hasInit) { toastNoInit(s.name); return; }
        runAction(s.name, "status", false);
    }

    private void onAutostart(ServiceInfo s) {
        if (!s.hasInit) { toastNoInit(s.name); return; }
        runAction(s.name, s.enabled ? "disable" : "enable", true);
    }

    private void confirm(String msg, final String name, final String action) {
        new AlertDialog.Builder(requireContext())
                .setTitle("确认操作")
                .setMessage(msg)
                .setNegativeButton(R.string.oc_cancel, null)
                .setPositiveButton("确定", (d, w) -> runAction(name, action, true))
                .show();
    }

    private void runAction(final String name, final String action, final boolean refresh) {
        api.serviceAction(name, action, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                if (!isAdded()) return;
                if ("status".equals(action)) {
                    showStatusDialog(name, result);
                } else {
                    Toast.makeText(getContext(), name + " 已执行 " + action, Toast.LENGTH_SHORT).show();
                    if (refresh) loadData();
                }
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), name + " 操作失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showStatusDialog(String name, String text) {
        if (!isAdded()) return;
        ScrollView sv = new ScrollView(requireContext());
        TextView tv = new TextView(requireContext());
        tv.setText(text == null || text.isEmpty() ? "(无输出)" : text);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);
        tv.setPadding(pad, pad, pad, pad);
        sv.addView(tv);
        new AlertDialog.Builder(requireContext())
                .setTitle(name + " - 运行状态")
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void toastNoInit(String name) {
        Toast.makeText(getContext(), name + " 没有 init 脚本，无法控制", Toast.LENGTH_SHORT).show();
    }

    // ============================ Adapter ============================

    private class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_service, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            ServiceInfo s = filtered.get(position);
            h.name.setText(s.name);

            h.running.setText(s.getRunningText());
            h.running.setBackgroundResource(s.running
                    ? R.drawable.bg_chip_success : R.drawable.bg_chip_neutral);
            h.running.setTextColor(ContextCompat.getColor(requireContext(),
                    s.running ? R.color.status_on : R.color.text_secondary));

            StringBuilder subtitle = new StringBuilder();
            subtitle.append(s.getRunningText()).append(" · PID ").append(s.getPidDisplay());
            if (s.hasInit) subtitle.append(" · ").append(s.getStartOrderDisplay());
            if (s.instanceCount > 1) subtitle.append(" · ").append(s.instanceCount).append(" 实例");
            h.subtitle.setText(subtitle.toString());

            h.badgeNoInit.setVisibility(s.hasInit ? View.GONE : View.VISIBLE);

            h.enabled.setText(s.hasInit
                    ? getString(s.enabled ? R.string.svc_enabled : R.string.svc_disabled)
                    : s.getEnabledText());
            h.enabled.setTextColor(ContextCompat.getColor(requireContext(),
                    s.hasInit && s.enabled ? R.color.status_on : R.color.text_secondary));

            boolean controllable = s.hasInit;
            h.swAutostart.setOnCheckedChangeListener(null);
            h.swAutostart.setChecked(s.enabled);
            h.swAutostart.setEnabled(controllable);
            h.swAutostart.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked != s.enabled) onAutostart(s);
            });

            h.dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(),
                            s.running ? R.color.status_on : R.color.status_off)));

            h.toggle.setEnabled(controllable);
            h.restart.setEnabled(controllable);
            h.status.setEnabled(controllable);
            float alpha = controllable ? 1f : 0.4f;
            h.toggle.setAlpha(alpha);
            h.restart.setAlpha(alpha);
            h.status.setAlpha(alpha);

            h.toggle.setText(s.running ? R.string.svc_stop : R.string.svc_start);

            h.toggle.setOnClickListener(v -> onToggle(s));
            h.restart.setOnClickListener(v -> onRestart(s));
            h.status.setOnClickListener(v -> onStatus(s));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView name, subtitle, running, badgeNoInit, enabled;
            TextView toggle, restart, status;
            SwitchMaterial swAutostart;
            View dot;

            VH(@NonNull View v) {
                super(v);
                dot = v.findViewById(R.id.dot_status);
                name = v.findViewById(R.id.tv_name);
                subtitle = v.findViewById(R.id.tv_subtitle);
                running = v.findViewById(R.id.tv_running);
                badgeNoInit = v.findViewById(R.id.tv_badge_noinit);
                enabled = v.findViewById(R.id.tv_enabled);
                swAutostart = v.findViewById(R.id.sw_autostart);
                toggle = v.findViewById(R.id.btn_toggle);
                restart = v.findViewById(R.id.btn_restart);
                status = v.findViewById(R.id.btn_status);
            }
        }
    }
}
