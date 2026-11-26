package com.myopenwrt.app.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.myopenwrt.app.R;
import com.myopenwrt.app.api.ApiCallback;
import com.myopenwrt.app.api.OpenWrtApi;
import com.myopenwrt.app.api.model.NetworkInterface;
import com.myopenwrt.app.util.FormatUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NetworkFragment extends Fragment {

    private OpenWrtApi api;
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView emptyText;
    private TextView tvSubtitle;
    private InterfaceAdapter adapter;
    private final List<NetworkInterface> data = new ArrayList<>();
    /** 上次采样：name -> [rxBytes, txBytes, timestampMs] */
    private final Map<String, long[]> lastSample = new HashMap<>();
    private Handler autoHandler;
    private Runnable autoRefresh;
    private boolean loading;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_network, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = OpenWrtApi.getInstance(requireContext());
        swipe = view.findViewById(R.id.swipe);
        recycler = view.findViewById(R.id.recycler);
        emptyText = view.findViewById(R.id.empty_text);
        tvSubtitle = view.findViewById(R.id.tv_subtitle);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InterfaceAdapter();
        recycler.setAdapter(adapter);

        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::loadData);
        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> loadData());
    }

    @Override
    public void onResume() {
        super.onResume();
        loadData();
        startAutoRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopAutoRefresh();
    }

    @Override
    public void onDestroyView() {
        stopAutoRefresh();
        autoHandler = null;
        autoRefresh = null;
        super.onDestroyView();
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        autoHandler = new Handler(Looper.getMainLooper());
        autoRefresh = new Runnable() {
            @Override
            public void run() {
                if (api.hasSession() && isResumed()) {
                    loadData();
                }
                autoHandler.postDelayed(this, 4000);
            }
        };
        autoHandler.postDelayed(autoRefresh, 4000);
    }

    private void stopAutoRefresh() {
        if (autoHandler != null && autoRefresh != null) {
            autoHandler.removeCallbacks(autoRefresh);
        }
    }

    private void loadData() {
        if (loading) return;
        if (!api.hasSession()) {
            emptyText.setText("请先在「设置」中连接路由器");
            emptyText.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            swipe.setRefreshing(false);
            if (tvSubtitle != null) tvSubtitle.setText(R.string.state_disconnected);
            return;
        }
        loading = true;
        api.getNetworkInterfaces(new ApiCallback<List<NetworkInterface>>() {
            @Override
            public void onSuccess(List<NetworkInterface> result) {
                loading = false;
                if (!isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                data.clear();
                data.addAll(result);
                computeRates(result);
                if (tvSubtitle != null) {
                    tvSubtitle.setText(result.isEmpty()
                            ? getString(R.string.network_title)
                            : "共 " + result.size() + " 个网络接口");
                }
                if (result.isEmpty()) {
                    emptyText.setText(R.string.network_title);
                    emptyText.setVisibility(View.VISIBLE);
                    recycler.setVisibility(View.GONE);
                } else {
                    emptyText.setVisibility(View.GONE);
                    recycler.setVisibility(View.VISIBLE);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onFailure(String message, Exception e) {
                loading = false;
                if (!isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                Toast.makeText(getContext(), "加载失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void computeRates(List<NetworkInterface> list) {
        long now = System.currentTimeMillis();
        for (NetworkInterface ni : list) {
            long[] prev = lastSample.get(ni.name);
            if (prev != null) {
                long dt = now - prev[2];
                if (dt > 0) {
                    ni.rxRate = FormatUtils.formatRate(ni.rxBytes - prev[0], dt);
                    ni.txRate = FormatUtils.formatRate(ni.txBytes - prev[1], dt);
                }
            }
            if (ni.rxRate == null) ni.rxRate = "0 B/s";
            if (ni.txRate == null) ni.txRate = "0 B/s";
            lastSample.put(ni.name, new long[]{ni.rxBytes, ni.txBytes, now});
        }
    }

    private String protocolText(String proto) {
        if (proto == null) return "—";
        switch (proto) {
            case "dhcp":
                return "DHCP";
            case "static":
                return "静态";
            case "pppoe":
                return "PPPoE";
            default:
                return proto.toUpperCase(Locale.getDefault());
        }
    }

    // ============================ 接口详情 / 重启 ============================

    private void showDetail(NetworkInterface ni) {
        if (!isAdded() || getView() == null) return;
        View sheet = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_interface_detail, null, false);

        View dot = sheet.findViewById(R.id.dot_status);
        int color = ContextCompat.getColor(requireContext(),
                ni.up ? R.color.status_on : R.color.status_off);
        dot.setBackgroundTintList(ColorStateList.valueOf(color));

        TextView name = sheet.findViewById(R.id.tv_name);
        name.setText(ni.name == null ? "—" : ni.name.toUpperCase(Locale.getDefault()));
        TextView state = sheet.findViewById(R.id.tv_state);
        state.setText(ni.up ? R.string.net_online : R.string.net_offline);
        state.setBackgroundResource(ni.up ? R.drawable.bg_chip_success : R.drawable.bg_chip_neutral);
        state.setTextColor(ContextCompat.getColor(requireContext(),
                ni.up ? R.color.status_on : R.color.text_secondary));

        setRow(sheet, R.id.row_phys, getString(R.string.net_detail_physical), safe(ni.device));
        setRow(sheet, R.id.row_proto, getString(R.string.net_detail_proto), protocolText(ni.proto));
        setRow(sheet, R.id.row_ipv4, getString(R.string.net_detail_ipv4), safe(ni.ipv4Addr));
        setRow(sheet, R.id.row_mask, getString(R.string.net_detail_mask), safe(ni.ipv4Mask));
        setRow(sheet, R.id.row_ipv6, getString(R.string.net_detail_ipv6), safe(ni.ipv6Addr));
        setRow(sheet, R.id.row_mac, getString(R.string.net_detail_mac), safe(ni.mac));
        setRow(sheet, R.id.row_rxpkt, getString(R.string.net_detail_rxpkt), String.valueOf(ni.rxPackets));
        setRow(sheet, R.id.row_txpkt, getString(R.string.net_detail_txpkt), String.valueOf(ni.txPackets));
        setRow(sheet, R.id.row_rx, getString(R.string.net_detail_rx), FormatUtils.formatBytes(ni.rxBytes));
        setRow(sheet, R.id.row_tx, getString(R.string.net_detail_tx), FormatUtils.formatBytes(ni.txBytes));

        final BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(sheet);

        android.widget.Button btn = sheet.findViewById(R.id.btn_restart_interface);
        btn.setText(getString(R.string.net_restart_iface, ni.name));
        btn.setOnClickListener(v -> {
            dialog.dismiss();
            confirmRestart(ni.name);
        });
        dialog.show();
    }

    private void setRow(View root, int rowId, String label, String value) {
        View row = root.findViewById(rowId);
        if (row == null) return;
        TextView tvLabel = row.findViewById(R.id.info_label);
        TextView tvValue = row.findViewById(R.id.info_value);
        if (tvLabel != null) tvLabel.setText(label);
        if (tvValue != null) tvValue.setText(value == null || value.isEmpty() ? "—" : value);
    }

    private void confirmRestart(String name) {
        if (!isAdded() || getView() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.net_restart_title)
                .setMessage(getString(R.string.net_restart_msg, name))
                .setNegativeButton(R.string.oc_cancel, null)
                .setPositiveButton(R.string.svc_restart, (d, w) -> restart(name))
                .show();
    }

    private void restart(String name) {
        Toast.makeText(getContext(), getString(R.string.net_restart_doing, name),
                Toast.LENGTH_SHORT).show();
        api.restartInterface(name, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                if (!isAdded() || getView() == null) return;
                Toast.makeText(getContext(), getString(R.string.net_restart_done, name),
                        Toast.LENGTH_SHORT).show();
                loadData();
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded() || getView() == null) return;
                Toast.makeText(getContext(), "重启失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static String safe(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private class InterfaceAdapter extends RecyclerView.Adapter<InterfaceAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_interface, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            NetworkInterface ni = data.get(position);
            h.name.setText(ni.name == null ? "—" : ni.name.toUpperCase(Locale.getDefault()));
            h.proto.setText(protocolText(ni.proto));
            h.status.setText(ni.up ? R.string.net_online : R.string.net_offline);
            h.status.setTextColor(ContextCompat.getColor(requireContext(),
                    ni.up ? R.color.status_on : R.color.text_secondary));
            h.dot.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(),
                            ni.up ? R.color.status_on : R.color.status_off)));
            h.device.setText(ni.device);
            h.ipv4.setText(ni.ipv4Addr == null || ni.ipv4Addr.isEmpty()
                    ? getString(R.string.net_no_ipv4)
                    : getString(R.string.net_ipv4_value, ni.ipv4Addr));
            h.rx.setText(ni.getRxFormatted());
            h.tx.setText(ni.getTxFormatted());
            h.rxRate.setText(ni.rxRate);
            h.txRate.setText(ni.txRate);
            h.itemView.setOnClickListener(v -> showDetail(ni));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView name, proto, status, device, ipv4, rx, tx, rxRate, txRate;
            View dot;

            VH(@NonNull View v) {
                super(v);
                dot = v.findViewById(R.id.dot_status);
                name = v.findViewById(R.id.tv_name);
                proto = v.findViewById(R.id.tv_proto);
                status = v.findViewById(R.id.tv_status);
                device = v.findViewById(R.id.tv_device);
                ipv4 = v.findViewById(R.id.tv_ipv4);
                rx = v.findViewById(R.id.tv_rx);
                tx = v.findViewById(R.id.tv_tx);
                rxRate = v.findViewById(R.id.tv_rx_rate);
                txRate = v.findViewById(R.id.tv_tx_rate);
            }
        }
    }
}
