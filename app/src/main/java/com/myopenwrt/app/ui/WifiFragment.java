package com.myopenwrt.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
import com.myopenwrt.app.api.model.WifiInfo;

import java.util.ArrayList;
import java.util.List;

public class WifiFragment extends Fragment {

    private OpenWrtApi api;
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView emptyText;
    private WifiAdapter adapter;
    private final List<WifiInfo> data = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_wifi, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        api = OpenWrtApi.getInstance(requireContext());
        swipe = view.findViewById(R.id.swipe);
        recycler = view.findViewById(R.id.recycler);
        emptyText = view.findViewById(R.id.empty_text);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WifiAdapter();
        recycler.setAdapter(adapter);
        swipe.setOnRefreshListener(this::loadData);
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
        api.getWifiInfo(new ApiCallback<List<WifiInfo>>() {
            @Override
            public void onSuccess(List<WifiInfo> result) {
                swipe.setRefreshing(false);
                data.clear();
                data.addAll(result);
                if (result.isEmpty()) {
                    emptyText.setText("未获取到 WiFi 信息（可能缺少 iwinfo 支持）");
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

    private class WifiAdapter extends RecyclerView.Adapter<WifiAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_wifi, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WifiInfo w = data.get(position);
            h.ifname.setText(w.ifname);
            h.ssid.setText(w.ssid == null || w.ssid.isEmpty() ? "(隐藏 SSID)" : w.ssid);
            h.detail.setText(String.format("频道 %s · 加密 %s · 模式 %s",
                    safe(w.channel), safe(w.encryption), safe(w.mode)));
            h.status.setText(w.up ? "● 启用" : "○ 停用");
            h.status.setTextColor(w.up ? 0xFF4CAF50 : 0xFF9E9E9E);

            h.clientContainer.removeAllViews();
            if (w.clients == null || w.clients.isEmpty()) {
                TextView t = new TextView(h.clientContainer.getContext());
                t.setText("  暂无关联客户端");
                t.setTextColor(0xFF9E9E9E);
                h.clientContainer.addView(t);
            } else {
                TextView label = new TextView(h.clientContainer.getContext());
                label.setText("关联客户端 (" + w.clients.size() + ")：");
                label.setTextColor(0xFF666666);
                label.setPadding(0, 8, 0, 4);
                h.clientContainer.addView(label);
                for (WifiInfo.WifiClient c : w.clients) {
                    TextView t = new TextView(h.clientContainer.getContext());
                    String host = (c.hostname == null || c.hostname.isEmpty()) ? "—" : c.hostname;
                    t.setText(String.format("  %s  %s  信号 %d dBm", c.mac, host, c.signal));
                    t.setTextColor(0xFF333333);
                    t.setPadding(0, 2, 0, 2);
                    h.clientContainer.addView(t);
                }
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView ifname, ssid, detail, status;
            LinearLayout clientContainer;
            VH(@NonNull View v) {
                super(v);
                ifname = v.findViewById(R.id.tv_ifname);
                ssid = v.findViewById(R.id.tv_ssid);
                detail = v.findViewById(R.id.tv_detail);
                status = v.findViewById(R.id.tv_status);
                clientContainer = v.findViewById(R.id.client_container);
            }
        }
    }

    private static String safe(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }
}
