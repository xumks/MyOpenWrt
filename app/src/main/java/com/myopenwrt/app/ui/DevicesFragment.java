package com.myopenwrt.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.myopenwrt.app.R;
import com.myopenwrt.app.api.ApiCallback;
import com.myopenwrt.app.api.OpenWrtApi;
import com.myopenwrt.app.api.model.DhcpLease;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DevicesFragment extends Fragment {

    private OpenWrtApi api;
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView emptyText;
    private EditText etSearch;
    private TextView tvSubtitle;
    private DeviceAdapter adapter;
    private final List<DhcpLease> allData = new ArrayList<>();
    private final List<DhcpLease> filtered = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_devices, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = OpenWrtApi.getInstance(requireContext());
        swipe = view.findViewById(R.id.swipe);
        recycler = view.findViewById(R.id.recycler);
        emptyText = view.findViewById(R.id.empty_text);
        etSearch = view.findViewById(R.id.et_search);
        tvSubtitle = view.findViewById(R.id.tv_subtitle);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new DeviceAdapter();
        recycler.setAdapter(adapter);

        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::loadData);
        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> loadData());
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
            if (tvSubtitle != null) tvSubtitle.setText(R.string.state_disconnected);
            return;
        }
        api.getDhcpLeases(new ApiCallback<List<DhcpLease>>() {
            @Override
            public void onSuccess(List<DhcpLease> result) {
                if (!isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                allData.clear();
                allData.addAll(result);
                if (tvSubtitle != null) {
                    tvSubtitle.setText(getString(R.string.device_count, allData.size()));
                }
                applyFilter(etSearch.getText().toString());
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                Toast.makeText(getContext(), "加载失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void applyFilter(String kw) {
        filtered.clear();
        String key = kw == null ? "" : kw.toLowerCase(Locale.getDefault()).trim();
        for (DhcpLease l : allData) {
            if (key.isEmpty()
                    || (l.hostname != null && l.hostname.toLowerCase(Locale.getDefault()).contains(key))
                    || (l.ip != null && l.ip.contains(key))
                    || (l.mac != null && l.mac.toLowerCase(Locale.getDefault()).contains(key))) {
                filtered.add(l);
            }
        }
        if (filtered.isEmpty()) {
            emptyText.setText(allData.isEmpty() ? R.string.device_empty : R.string.device_no_match);
            emptyText.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    // ============================ 固定 IP / 拉黑 ============================

    private void onStaticClick(DhcpLease d) {
        if (d.staticSid != null) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.device_unfix_title)
                    .setMessage(R.string.device_unfix_msg)
                    .setNegativeButton(R.string.oc_cancel, null)
                    .setPositiveButton(R.string.device_unfix_ip, (dialog, which) ->
                            api.removeStaticLease(d.staticSid, actionCallback("已取消固定 IP")))
                    .show();
        } else {
            showStaticDialog(d);
        }
    }

    private void showStaticDialog(DhcpLease d) {
        if (!isAdded() || getView() == null) return;
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_static_ip, null, false);
        TextView tip = content.findViewById(R.id.tv_tip);
        final EditText etIp = content.findViewById(R.id.et_ip);
        tip.setText(getString(R.string.device_static_tip, d.getDisplayHostname(), d.mac));
        if (d.ip != null && !d.ip.isEmpty()) {
            etIp.setText(d.ip);
            etIp.setSelection(etIp.getText().length());
        }

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.device_static_title)
                .setView(content)
                .setNegativeButton(R.string.oc_cancel, null)
                .setPositiveButton(R.string.oc_save, null)
                .create();
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String ip = etIp.getText().toString().trim();
                    if (!isValidIpv4(ip)) {
                        Toast.makeText(getContext(), "请输入正确的 IP 地址，如 192.168.10.100",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    dialog.dismiss();
                    api.setStaticLease(d, ip, actionCallback("已设置固定 IP，设备重新连接后生效"));
                }));
        dialog.show();
    }

    private void onBlockClick(DhcpLease d) {
        if (d.blocked) {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.device_unblock_title)
                    .setMessage(R.string.device_unblock_msg)
                    .setNegativeButton(R.string.oc_cancel, null)
                    .setPositiveButton(R.string.device_unblock, (dialog, which) ->
                            api.unblockDevice(d.blockSid, actionCallback("已解除拉黑")))
                    .show();
        } else {
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.device_block_title)
                    .setMessage(getString(R.string.device_block_msg, d.getDisplayHostname()))
                    .setNegativeButton(R.string.oc_cancel, null)
                    .setPositiveButton(R.string.device_block, (dialog, which) ->
                            api.blockDevice(d.mac, actionCallback("已拉黑，该设备将无法访问网络")))
                    .show();
        }
    }

    private ApiCallback<Void> actionCallback(final String successMsg) {
        return new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded() || getView() == null) return;
                Toast.makeText(getContext(), successMsg, Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded() || getView() == null) return;
                Toast.makeText(getContext(), "操作失败：" + message, Toast.LENGTH_LONG).show();
            }
        };
    }

    private boolean isValidIpv4(String ip) {
        if (ip == null) return false;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return false;
        for (String part : parts) {
            try {
                int v = Integer.parseInt(part);
                if (v < 0 || v > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_device, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            DhcpLease d = filtered.get(position);
            String name = d.getDisplayHostname();
            h.avatar.setText("(未知)".equals(name) || name.isEmpty()
                    ? "?"
                    : name.substring(0, 1).toUpperCase(Locale.getDefault()));
            h.avatar.setBackgroundResource(d.blocked
                    ? R.drawable.bg_avatar_blocked : R.drawable.bg_avatar);
            h.hostname.setText(name);
            h.ip.setText(d.ip == null || d.ip.isEmpty() ? "—" : d.ip);
            h.mac.setText(d.mac);
            h.expires.setText(d.expires <= 0
                    ? getString(R.string.device_expire_forever)
                    : getString(R.string.device_expire_left, d.getExpiresDisplay()));

            boolean fixed = d.staticSid != null;
            h.staticBadge.setVisibility(fixed ? View.VISIBLE : View.GONE);
            if (fixed) {
                h.staticBadge.setText(getString(R.string.device_static_badge,
                        d.staticIp == null ? "—" : d.staticIp));
            }
            h.blockedBadge.setVisibility(d.blocked ? View.VISIBLE : View.GONE);

            h.btnStatic.setText(fixed ? R.string.device_unfix_ip : R.string.device_fix_ip);
            h.btnBlock.setText(d.blocked ? R.string.device_unblock : R.string.device_block);
            h.btnBlock.setBackgroundResource(d.blocked
                    ? R.drawable.bg_button_success : R.drawable.bg_button_danger);
            h.btnBlock.setTextColor(d.blocked
                    ? getResources().getColor(R.color.status_on)
                    : getResources().getColor(R.color.danger));

            h.btnStatic.setOnClickListener(v -> onStaticClick(d));
            h.btnBlock.setOnClickListener(v -> onBlockClick(d));
        }

        @Override
        public int getItemCount() {
            return filtered.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView avatar, hostname, ip, mac, expires, staticBadge, blockedBadge, btnStatic, btnBlock;
            VH(@NonNull View v) {
                super(v);
                avatar = v.findViewById(R.id.tv_avatar);
                hostname = v.findViewById(R.id.tv_hostname);
                ip = v.findViewById(R.id.tv_ip);
                mac = v.findViewById(R.id.tv_mac);
                expires = v.findViewById(R.id.tv_expires);
                staticBadge = v.findViewById(R.id.tv_static_badge);
                blockedBadge = v.findViewById(R.id.tv_blocked_badge);
                btnStatic = v.findViewById(R.id.btn_static);
                btnBlock = v.findViewById(R.id.btn_block);
            }
        }
    }
}
