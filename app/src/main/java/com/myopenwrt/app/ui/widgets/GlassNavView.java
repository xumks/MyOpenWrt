package com.myopenwrt.app.ui.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.myopenwrt.app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 仿 iPhone 的悬浮毛玻璃底部导航：半透明胶囊 + 选中项胶囊高亮。
 */
public class GlassNavView extends LinearLayout {

    public interface OnItemSelectedListener {
        void onItemSelected(int index, int itemId);
    }

    private static final int[] ITEM_IDS = {
            R.id.nav_dashboard, R.id.nav_network, R.id.nav_devices,
            R.id.nav_wifi, R.id.nav_settings
    };
    private static final int[] ICONS = {
            R.drawable.ic_nav_dashboard, R.drawable.ic_nav_network,
            R.drawable.ic_nav_devices, R.drawable.ic_wifi, R.drawable.ic_service
    };
    private static final int[] LABELS = {
            R.string.nav_dashboard, R.string.nav_network, R.string.nav_devices,
            R.string.nav_wifi, R.string.nav_settings
    };

    private final List<LinearLayout> capsules = new ArrayList<>();
    private final List<ImageView> icons = new ArrayList<>();
    private final List<TextView> labels = new ArrayList<>();

    private int selectedIndex = 0;
    private OnItemSelectedListener listener;

    public GlassNavView(Context context) {
        this(context, null);
    }

    public GlassNavView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundResource(R.drawable.bg_glass_nav);
        setElevation(dp(14));
        int padH = dp(7);
        setPadding(padH, 0, padH, 0);

        int idleColor = ContextCompat.getColor(getContext(), R.color.text_secondary);
        TypedValue ripple = new TypedValue();
        getContext().getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, ripple, true);

        for (int i = 0; i < ITEM_IDS.length; i++) {
            final int index = i;

            LinearLayout cell = new LinearLayout(getContext());
            cell.setOrientation(HORIZONTAL);
            cell.setGravity(Gravity.CENTER);
            cell.setClickable(true);
            cell.setFocusable(true);
            if (ripple.resourceId != 0) {
                cell.setBackgroundResource(ripple.resourceId);
            }
            cell.setLayoutParams(new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            cell.setOnClickListener(v -> select(index));

            LinearLayout capsule = new LinearLayout(getContext());
            capsule.setOrientation(VERTICAL);
            capsule.setGravity(Gravity.CENTER);
            capsule.setPadding(dp(8), dp(7), dp(8), dp(7));
            capsule.setLayoutParams(new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            ImageView icon = new ImageView(getContext());
            icon.setLayoutParams(new LayoutParams(dp(21), dp(21)));
            icon.setImageResource(ICONS[i]);
            icon.setColorFilter(idleColor);

            TextView label = new TextView(getContext());
            label.setText(LABELS[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setIncludeFontPadding(false);
            label.setTextColor(idleColor);
            LayoutParams labelLp = new LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelLp.topMargin = dp(2);
            label.setLayoutParams(labelLp);

            capsule.addView(icon);
            capsule.addView(label);
            cell.addView(capsule);
            addView(cell);

            capsules.add(capsule);
            icons.add(icon);
            labels.add(label);
        }
        applySelection(0);
    }

    public void setOnItemSelectedListener(OnItemSelectedListener l) {
        this.listener = l;
    }

    /** 仅更新外观，不回调。 */
    public void setSelectedIndex(int index) {
        if (index < 0 || index >= ITEM_IDS.length) return;
        applySelection(index);
    }

    /** 通过菜单 id 选中某个 Tab（供 MainActivity.navigateTo 使用，会回调切换页面）。 */
    public void setSelectedItemId(int itemId) {
        for (int i = 0; i < ITEM_IDS.length; i++) {
            if (ITEM_IDS[i] == itemId) {
                select(i);
                return;
            }
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    private void select(int index) {
        if (index == selectedIndex) return;
        applySelection(index);
        if (listener != null) listener.onItemSelected(index, ITEM_IDS[index]);
    }

    private void applySelection(int index) {
        selectedIndex = index;
        int primary = ContextCompat.getColor(getContext(), R.color.primary);
        int idle = ContextCompat.getColor(getContext(), R.color.text_secondary);
        for (int i = 0; i < capsules.size(); i++) {
            boolean on = i == index;
            LinearLayout capsule = capsules.get(i);
            if (on) {
                capsule.setBackgroundResource(R.drawable.bg_nav_selected);
                capsule.setPadding(dp(13), dp(7), dp(13), dp(7));
            } else {
                capsule.setBackgroundResource(0);
                capsule.setPadding(dp(8), dp(7), dp(8), dp(7));
            }
            icons.get(i).setColorFilter(on ? primary : idle);
            labels.get(i).setTextColor(on ? primary : idle);
            labels.get(i).setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
