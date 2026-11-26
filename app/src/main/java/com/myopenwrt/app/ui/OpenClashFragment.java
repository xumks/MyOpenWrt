package com.myopenwrt.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.myopenwrt.app.R;
import com.myopenwrt.app.api.ApiCallback;
import com.myopenwrt.app.api.OpenWrtApi;
import com.myopenwrt.app.api.model.OpenClashConfig;
import com.myopenwrt.app.api.model.OpenClashSubscribe;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenClash 订阅管理页：读取 /etc/config/openclash 中的订阅配置，
 * 支持新增、编辑、启用/禁用、删除订阅，并可触发订阅更新。
 */
public class OpenClashFragment extends Fragment {

    private OpenWrtApi api;
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView emptyText;
    private TextView tvSubtitle;
    private View rootView;
    private SubAdapter adapter;
    private final List<OpenClashSubscribe> subs = new ArrayList<>();
    private boolean loaded;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_openclash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = OpenWrtApi.getInstance(requireContext());
        rootView = view;
        swipe = view.findViewById(R.id.swipe);
        recycler = view.findViewById(R.id.recycler);
        emptyText = view.findViewById(R.id.empty_text);
        tvSubtitle = view.findViewById(R.id.tv_subtitle);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new SubAdapter();
        recycler.setAdapter(adapter);

        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::loadData);
        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> loadData());
        view.findViewById(R.id.btn_add).setOnClickListener(v -> showEditDialog(null));
        view.findViewById(R.id.btn_update).setOnClickListener(v -> updateSubscriptions());
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
        swipe.setRefreshing(true);
        api.getOpenClash(new ApiCallback<OpenClashConfig>() {
            @Override
            public void onSuccess(OpenClashConfig result) {
                if (!isAdded()) return;
                loaded = true;
                swipe.setRefreshing(false);
                bindStatus(result);
                if (tvSubtitle != null) {
                    tvSubtitle.setText("共 " + result.subscribes.size() + " 个订阅");
                }
                subs.clear();
                subs.addAll(result.subscribes);
                adapter.notifyDataSetChanged();
                boolean empty = subs.isEmpty();
                emptyText.setText(R.string.oc_empty);
                emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
                recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                loaded = false;
                subs.clear();
                adapter.notifyDataSetChanged();
                emptyText.setText("加载 OpenClash 配置失败：" + message);
                emptyText.setVisibility(View.VISIBLE);
                recycler.setVisibility(View.GONE);
            }
        });
    }

    // ============================ 状态展示 ============================

    private void bindStatus(OpenClashConfig oc) {
        setRow(R.id.row_oc_running, getString(R.string.oc_label_running), oc.running ? "运行中" : "已停止");
        TextView running = getRowValue(R.id.row_oc_running);
        if (running != null) {
            running.setTextColor(getResources().getColor(
                    oc.running ? R.color.status_on : R.color.status_off));
        }
        setRow(R.id.row_oc_mode, getString(R.string.oc_label_mode), oc.getProxyModeDisplay());
        String cfg = oc.currentConfigName();
        setRow(R.id.row_oc_current, getString(R.string.oc_label_current), cfg == null ? "—" : cfg + ".yaml");
        setRow(R.id.row_oc_count, getString(R.string.oc_label_count), String.valueOf(oc.subscribes.size()));
    }

    private void setRow(int rowId, String label, String value) {
        if (rootView == null) return;
        View row = rootView.findViewById(rowId);
        if (row == null) return;
        TextView tvLabel = row.findViewById(R.id.info_label);
        TextView tvValue = row.findViewById(R.id.info_value);
        if (tvLabel != null) tvLabel.setText(label);
        if (tvValue != null) tvValue.setText(value == null || value.isEmpty() ? "—" : value);
    }

    private TextView getRowValue(int rowId) {
        if (rootView == null) return null;
        View row = rootView.findViewById(rowId);
        return row == null ? null : row.findViewById(R.id.info_value);
    }

    // ============================ 订阅编辑 ============================

    private void showEditDialog(@Nullable final OpenClashSubscribe sub) {
        if (!isAdded()) return;
        final boolean isNew = sub == null;
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_openclash_sub, null, false);
        final EditText etName = content.findViewById(R.id.et_name);
        final EditText etAddress = content.findViewById(R.id.et_address);
        final SwitchMaterial swEnabled = content.findViewById(R.id.switch_enabled);

        if (isNew) {
            etName.setText("sub" + (subs.size() + 1));
            swEnabled.setChecked(true);
        } else {
            etName.setText(sub.name);
            etAddress.setText(sub.address);
            swEnabled.setChecked(sub.enabled);
        }
        etName.setSelection(etName.getText().length());

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(isNew ? R.string.oc_new_title : R.string.oc_edit_title)
                .setView(content)
                .setNegativeButton(R.string.oc_cancel, null)
                .setPositiveButton(R.string.oc_save, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = etName.getText().toString().trim();
                    String address = etAddress.getText().toString().trim();
                    if (name.isEmpty()) { toast("请输入订阅名称"); return; }
                    if (name.contains("/") || name.contains("\\")) {
                        toast("订阅名称不能包含斜杠"); return;
                    }
                    if (address.isEmpty()) { toast("请输入订阅地址"); return; }
                    if (!address.startsWith("http://") && !address.startsWith("https://")) {
                        toast("订阅地址需以 http:// 或 https:// 开头"); return;
                    }
                    if (nameExists(name, sub)) { toast("订阅名称已存在"); return; }
                    dialog.dismiss();
                    saveSubscribe(sub, name, address, swEnabled.isChecked());
                }));
        dialog.show();
    }

    private boolean nameExists(String name, @Nullable OpenClashSubscribe self) {
        for (OpenClashSubscribe s : subs) {
            if (self != null && self.sid != null && self.sid.equals(s.sid)) continue;
            if (s.name != null && s.name.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private void saveSubscribe(@Nullable OpenClashSubscribe sub, String name, String address, boolean enabled) {
        ApiCallback<Void> callback = new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "已保存，点击「更新订阅」生效", Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "保存失败：" + message, Toast.LENGTH_LONG).show();
            }
        };
        if (sub == null) {
            api.addOpenClashSubscribe(name, address, enabled, callback);
        } else {
            api.updateOpenClashSubscribe(sub.sid, name, address, enabled, callback);
        }
    }

    private void toggleSubscribe(OpenClashSubscribe sub) {
        api.updateOpenClashSubscribe(sub.sid, null, null, !sub.enabled, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), sub.enabled ? "已禁用该订阅" : "已启用该订阅",
                        Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "操作失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDelete(OpenClashSubscribe sub) {
        String name = sub.name == null || sub.name.isEmpty() ? "该订阅" : sub.name;
        new AlertDialog.Builder(requireContext())
                .setTitle("删除订阅")
                .setMessage("确定删除「" + name + "」？仅移除订阅配置，已下载的配置文件不受影响。")
                .setNegativeButton(R.string.oc_cancel, null)
                .setPositiveButton(R.string.oc_delete, (d, w) -> api.deleteOpenClashSubscribe(sub.sid,
                        new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "已删除", Toast.LENGTH_SHORT).show();
                                loadData();
                            }

                            @Override
                            public void onFailure(String message, Exception e) {
                                if (!isAdded()) return;
                                Toast.makeText(getContext(), "删除失败：" + message, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    private void updateSubscriptions() {
        if (!loaded) {
            toast("请先成功加载 OpenClash 配置");
            return;
        }
        Toast.makeText(getContext(), "正在启动订阅更新…", Toast.LENGTH_SHORT).show();
        api.updateOpenClashSubscriptions(new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), result, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "更新失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    // ============================ Adapter ============================

    private class SubAdapter extends RecyclerView.Adapter<SubAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_openclash_sub, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            OpenClashSubscribe s = subs.get(position);
            h.name.setText(s.name == null || s.name.isEmpty() ? "config" : s.name);
            h.address.setText(s.getDisplayAddress());
            h.current.setVisibility(s.isCurrent ? View.VISIBLE : View.GONE);
            h.state.setText(s.enabled ? R.string.oc_state_on : R.string.oc_state_off);
            h.state.setBackgroundResource(s.enabled
                    ? R.drawable.bg_chip_success : R.drawable.bg_chip_neutral);
            h.state.setTextColor(getResources().getColor(
                    s.enabled ? R.color.status_on : R.color.text_secondary));
            h.toggle.setText(s.enabled ? R.string.oc_disable_action : R.string.oc_enable_action);

            h.edit.setOnClickListener(v -> showEditDialog(s));
            h.toggle.setOnClickListener(v -> toggleSubscribe(s));
            h.delete.setOnClickListener(v -> confirmDelete(s));
            h.itemView.setOnClickListener(v -> showEditDialog(s));
        }

        @Override
        public int getItemCount() {
            return subs.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView name, address, current, state, edit, toggle, delete;

            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.tv_name);
                address = v.findViewById(R.id.tv_address);
                current = v.findViewById(R.id.tv_current);
                state = v.findViewById(R.id.tv_state);
                edit = v.findViewById(R.id.btn_edit);
                toggle = v.findViewById(R.id.btn_toggle);
                delete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
