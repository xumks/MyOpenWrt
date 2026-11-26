package com.myopenwrt.app.ui;

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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
    private InterfaceAdapter adapter;
    private final List<NetworkInterface> data = new ArrayList<>();
    /** 上次采样：name -> [rxBytes, txBytes, timestampMs] */
    private final Map<String, long[]> lastSample = new HashMap<>();
    private Handler autoHandler;
    private Runnable autoRefresh;

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

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InterfaceAdapter();
        recycler.setAdapter(adapter);

        swipe.setOnRefreshListener(this::loadData);
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

    private void startAutoRefresh() {
        stopAutoRefresh();
        autoHandler = new Handler(Looper.getMainLooper());
        autoRefresh = new Runnable() {
            @Override
            public void run() {
                if (api.hasSession() && isResumed()) {
                    loadData();
                }
                autoHandler.postDelayed(this, 3000);
            }
        };
        autoHandler.postDelayed(autoRefresh, 3000);
    }

    private void stopAutoRefresh() {
        if (autoHandler != null && autoRefresh != null) {
            autoHandler.removeCallbacks(autoRefresh);
        }
    }

    private void loadData() {
        if (!api.hasSession()) {
            emptyText.setText("请先在「设置」中连接路由器");
            emptyText.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
            swipe.setRefreshing(false);
            return;
        }
        api.getNetworkInterfaces(new ApiCallback<List<NetworkInterface>>() {
            @Override
            public void onSuccess(List<NetworkInterface> result) {
                swipe.setRefreshing(false);
                data.clear();
                data.addAll(result);
                computeRates(result);
                if (result.isEmpty()) {
                    emptyText.setText("暂无网络接口数据");
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
            lastSample.put(ni.name, new long[]{ni.rxBytes, ni.txBytes, now});
        }
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
            h.name.setText(ni.name.toUpperCase(Locale.getDefault()) + " (" + ni.proto + ")");
            h.status.setText(ni.up ? "● 已连接" : "○ 未连接");
            h.status.setTextColor(ni.up ? 0xFF4CAF50 : 0xFF9E9E9E);
            h.device.setText("设备：" + ni.device);
            h.ipv4.setText("IPv4：" + (ni.ipv4Addr != null ? ni.ipv4Addr + "/" + ni.ipv4Mask : "—"));
            h.mac.setText("MAC：" + ni.mac);
            h.rx.setText("↓ " + ni.getRxFormatted() + (ni.rxRate != null ? "  (" + ni.rxRate + ")" : ""));
            h.tx.setText("↑ " + ni.getTxFormatted() + (ni.txRate != null ? "  (" + ni.txRate + ")" : ""));
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView name, status, device, ipv4, mac, rx, tx;
            VH(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.tv_name);
                status = v.findViewById(R.id.tv_status);
                device = v.findViewById(R.id.tv_device);
                ipv4 = v.findViewById(R.id.tv_ipv4);
                mac = v.findViewById(R.id.tv_mac);
                rx = v.findViewById(R.id.tv_rx);
                tx = v.findViewById(R.id.tv_tx);
            }
        }
    }
}
