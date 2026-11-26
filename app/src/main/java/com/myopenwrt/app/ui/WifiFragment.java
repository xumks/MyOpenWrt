package com.myopenwrt.app.ui;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import com.myopenwrt.app.R;
import com.myopenwrt.app.api.ApiCallback;
import com.myopenwrt.app.api.OpenWrtApi;
import com.myopenwrt.app.api.model.WifiInfo;
import com.myopenwrt.app.api.model.WirelessConfig;

import java.util.ArrayList;
import java.util.List;

public class WifiFragment extends Fragment {

    private OpenWrtApi api;
    private SwipeRefreshLayout swipe;
    private RecyclerView recycler;
    private TextView emptyText;
    private TextView tvSubtitle;
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
        tvSubtitle = view.findViewById(R.id.tv_subtitle);

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WifiAdapter();
        recycler.setAdapter(adapter);
        swipe.setColorSchemeResources(R.color.primary);
        swipe.setOnRefreshListener(this::loadData);
        view.findViewById(R.id.btn_refresh).setOnClickListener(v -> loadData());
        view.findViewById(R.id.btn_settings).setOnClickListener(v -> showWifiConfigChooser());
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
        api.getWifiInfo(new ApiCallback<List<WifiInfo>>() {
            @Override
            public void onSuccess(List<WifiInfo> result) {
                if (!isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                data.clear();
                data.addAll(result);
                int clients = 0;
                for (WifiInfo w : result) {
                    if (w.clients != null) clients += w.clients.size();
                }
                if (tvSubtitle != null) {
                    tvSubtitle.setText(result.isEmpty()
                            ? getString(R.string.wifi_subtitle)
                            : getString(R.string.wifi_subtitle_count, result.size(), clients));
                }
                if (result.isEmpty()) {
                    emptyText.setText(R.string.wifi_empty);
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
                if (!isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                Toast.makeText(getContext(), "加载失败：" + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ============================ WiFi 名称 / 密码 ============================

    private void showWifiConfigChooser() {
        if (!api.hasSession()) {
            Toast.makeText(getContext(), "请先在「设置」中连接路由器", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), "正在读取无线配置…", Toast.LENGTH_SHORT).show();
        api.getWirelessConfigs(new ApiCallback<List<WirelessConfig>>() {
            @Override
            public void onSuccess(List<WirelessConfig> result) {
                if (!isAdded() || getView() == null) return;
                if (result.isEmpty()) {
                    Toast.makeText(getContext(), R.string.wifi_no_config, Toast.LENGTH_LONG).show();
                } else {
                    showChooserDialog(result);
                }
            }

            @Override
            public void onFailure(String message, Exception e) {
                if (!isAdded() || getView() == null) return;
                Toast.makeText(getContext(), "读取无线配置失败：" + message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showChooserDialog(List<WirelessConfig> list) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(8), dp(2), dp(8), dp(2));

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.wifi_settings_title)
                .setNegativeButton(R.string.oc_cancel, null)
                .create();

        for (final WirelessConfig cfg : list) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setClickable(true);
            TypedValue ripple = new TypedValue();
            requireContext().getTheme().resolveAttribute(
                    android.R.attr.selectableItemBackground, ripple, true);
            if (ripple.resourceId != 0) row.setBackgroundResource(ripple.resourceId);

            LinearLayout texts = new LinearLayout(requireContext());
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(requireContext());
            title.setText(cfg.getTitle());
            title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
            title.setTextSize(15);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            TextView sub = new TextView(requireContext());
            sub.setText(cfg.getSubtitle());
            sub.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            sub.setTextSize(12);
            sub.setPadding(0, dp(2), 0, 0);
            texts.addView(title);
            texts.addView(sub);
            row.addView(texts, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView chevron = new ImageView(requireContext());
            chevron.setImageResource(R.drawable.ic_chevron_right);
            chevron.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            row.addView(chevron, new LinearLayout.LayoutParams(dp(20), dp(20)));

            row.setOnClickListener(v -> {
                dialog.dismiss();
                showEditDialog(cfg);
            });
            container.addView(row);
        }

        ScrollView sv = new ScrollView(requireContext());
        sv.addView(container);
        dialog.setView(sv);
        dialog.show();
    }

    private void showEditDialog(final WirelessConfig cfg) {
        if (!isAdded() || getView() == null) return;
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_wifi_edit, null, false);
        final EditText etSsid = content.findViewById(R.id.et_ssid);
        final EditText etKey = content.findViewById(R.id.et_key);
        TextView tvTip = content.findViewById(R.id.tv_tip);
        etSsid.setText(cfg.ssid == null ? "" : cfg.ssid);
        etSsid.setSelection(etSsid.getText().length());
        tvTip.setText(getString(R.string.wifi_tip) + "\n当前加密方式："
                + (cfg.encryption == null || cfg.encryption.isEmpty() ? "none" : cfg.encryption));

        final AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.wifi_settings_title)
                .setView(content)
                .setNegativeButton(R.string.oc_cancel, null)
                .setPositiveButton(R.string.wifi_save_apply, null)
                .create();

        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    final String ssid = etSsid.getText().toString().trim();
                    final String key = etKey.getText().toString();
                    if (ssid.isEmpty()) {
                        Toast.makeText(getContext(), R.string.wifi_ssid_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (ssid.length() > 32) {
                        Toast.makeText(getContext(), R.string.wifi_ssid_too_long, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean keyValid = key.isEmpty()
                            || (key.length() >= 8 && key.length() <= 63)
                            || (key.length() == 64 && key.matches("[0-9A-Fa-f]{64}"));
                    if (!keyValid) {
                        Toast.makeText(getContext(), R.string.wifi_key_invalid, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    final android.widget.Button positive =
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    positive.setEnabled(false);
                    Toast.makeText(getContext(), R.string.wifi_saving, Toast.LENGTH_SHORT).show();
                    api.updateWirelessConfig(cfg.sid, ssid, key, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            if (!isAdded() || getView() == null) return;
                            Toast.makeText(getContext(), R.string.wifi_saved, Toast.LENGTH_LONG).show();
                            dialog.dismiss();
                            loadData();
                        }

                        @Override
                        public void onFailure(String message, Exception e) {
                            if (!isAdded() || getView() == null) return;
                            positive.setEnabled(true);
                            Toast.makeText(getContext(), "保存失败：" + message,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                }));
        dialog.show();
    }

    private int signalColor(int signal) {
        if (signal == 0) return ContextCompat.getColor(requireContext(), R.color.status_off);
        if (signal >= -60) return ContextCompat.getColor(requireContext(), R.color.status_on);
        if (signal >= -75) return ContextCompat.getColor(requireContext(), R.color.warning);
        return ContextCompat.getColor(requireContext(), R.color.danger);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String safe(String s) {
        return s == null || s.isEmpty() ? "—" : s;
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

            String ssid = w.ssid == null || w.ssid.isEmpty() || "—".equals(w.ssid)
                    ? getString(R.string.wifi_hidden_ssid) : w.ssid;
            h.ssid.setText(ssid);
            h.detail.setText(safe(w.ifname) + " · " + safe(w.mode));

            h.signal.setText(w.getSignalDisplay());
            h.signal.setTextColor(signalColor(w.signal));
            h.channel.setText(getString(R.string.wifi_channel, safe(w.channel)));

            h.state.setText(w.up ? R.string.wifi_enabled : R.string.wifi_disabled);
            h.state.setBackgroundResource(w.up
                    ? R.drawable.bg_chip_success : R.drawable.bg_chip_neutral);
            h.state.setTextColor(ContextCompat.getColor(requireContext(),
                    w.up ? R.color.status_on : R.color.text_secondary));

            h.encryption.setText(safe(w.encryption));
            int count = w.clients == null ? 0 : w.clients.size();
            h.clients.setText(getString(R.string.wifi_clients, count));

            h.clientContainer.removeAllViews();
            if (count == 0) {
                TextView tip = new TextView(h.clientContainer.getContext());
                tip.setText(R.string.wifi_no_clients);
                tip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
                tip.setTextSize(12);
                tip.setPadding(0, dp(8), 0, dp(4));
                h.clientContainer.addView(tip);
            } else {
                for (WifiInfo.WifiClient c : w.clients) {
                    LinearLayout row = new LinearLayout(h.clientContainer.getContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(5), 0, dp(5));

                    TextView name = new TextView(row.getContext());
                    String host = c.hostname == null || c.hostname.isEmpty()
                            ? c.mac : c.hostname + "  " + c.mac;
                    name.setText(host);
                    name.setTextSize(12);
                    name.setSingleLine(true);
                    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    name.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
                    row.addView(name, new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                    TextView sig = new TextView(row.getContext());
                    sig.setText(c.signal == 0 ? "—" : c.signal + " dBm");
                    sig.setTextSize(11.5f);
                    sig.setTextColor(signalColor(c.signal));
                    sig.setTypeface(null, android.graphics.Typeface.BOLD);
                    row.addView(sig);

                    h.clientContainer.addView(row);
                }
            }
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView ssid, detail, signal, channel, state, encryption, clients;
            LinearLayout clientContainer;

            VH(@NonNull View v) {
                super(v);
                ssid = v.findViewById(R.id.tv_ssid);
                detail = v.findViewById(R.id.tv_detail);
                signal = v.findViewById(R.id.tv_signal);
                channel = v.findViewById(R.id.tv_channel);
                state = v.findViewById(R.id.tv_state);
                encryption = v.findViewById(R.id.tv_encryption);
                clients = v.findViewById(R.id.tv_clients);
                clientContainer = v.findViewById(R.id.client_container);
            }
        }
    }
}
