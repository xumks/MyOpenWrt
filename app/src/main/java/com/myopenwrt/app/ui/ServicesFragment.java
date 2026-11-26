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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
    private EditText etSearch;
    private ServiceAdapter adapter;
    private final List<ServiceInfo> allData = new ArrayList<>();
    private final List<ServiceInfo> filtered = new ArrayList<>();

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
        etSearch = view.findViewById(R.id.et_search);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ServiceAdapter();
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(this::loadData);
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

    private void loadData() {
        if (!api.hasSession()) {
            emptyText.setText("请先在「设置」中连接路由器");
            emptyText.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            swipe.setRefreshing(false);
            return;
        }
        api.getServices(new ApiCallback<List<ServiceInfo>>() {
            @Override
            public void onSuccess(List<ServiceInfo> result) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                allData.clear();
                allData.addAll(result);
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
            if (key.isEmpty() || (s.name != null && s.name.toLowerCase(Locale.getDefault()).contains(key))) {
                filtered.add(s);
            }
        }
        if (filtered.isEmpty()) {
            emptyText.setText(allData.isEmpty() ? "暂无服务数据" : "未找到匹配服务");
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
            confirm("停止 " + s.name + " ？", s.name, "stop");
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
                .setNegativeButton("取消", null)
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
            h.running.setTextColor(getResources().getColor(
                    s.running ? R.color.status_on : R.color.status_off));

            h.enabled.setText(s.getEnabledText());
            h.enabled.setTextColor(getResources().getColor(
                    s.hasInit && s.enabled ? R.color.status_on : R.color.text_secondary));

            h.pid.setText("PID: " + s.getPidDisplay());
            h.order.setText(s.hasInit ? ("启动顺序: " + s.getStartOrderDisplay()) : "");
            h.command.setText(s.command == null || s.command.isEmpty() ? "—" : s.command);

            boolean controllable = s.hasInit;
            h.toggle.setEnabled(controllable);
            h.restart.setEnabled(controllable);
            h.status.setEnabled(controllable);
            h.autostart.setEnabled(controllable);
            float alpha = controllable ? 1f : 0.4f;
            h.toggle.setAlpha(alpha);
            h.restart.setAlpha(alpha);
            h.status.setAlpha(alpha);
            h.autostart.setAlpha(alpha);

            h.toggle.setText(s.running ? "停止" : "启动");
            h.autostart.setText(controllable ? (s.enabled ? "自启:开" : "自启:关") : "自启:—");

            h.toggle.setOnClickListener(v -> onToggle(s));
            h.restart.setOnClickListener(v -> onRestart(s));
            h.status.setOnClickListener(v -> onStatus(s));
            h.autostart.setOnClickListener(v -> onAutostart(s));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView name, running, enabled, pid, order, command;
            TextView toggle, restart, status, autostart;

            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.tv_name);
                running = v.findViewById(R.id.tv_running);
                enabled = v.findViewById(R.id.tv_enabled);
                pid = v.findViewById(R.id.tv_pid);
                order = v.findViewById(R.id.tv_order);
                command = v.findViewById(R.id.tv_command);
                toggle = v.findViewById(R.id.btn_toggle);
                restart = v.findViewById(R.id.btn_restart);
                status = v.findViewById(R.id.btn_status);
                autostart = v.findViewById(R.id.btn_autostart);
            }
        }
    }
}
