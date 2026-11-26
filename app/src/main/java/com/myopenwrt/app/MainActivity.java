package com.myopenwrt.app;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.myopenwrt.app.ui.DashboardFragment;
import com.myopenwrt.app.ui.DevicesFragment;
import com.myopenwrt.app.ui.NetworkFragment;
import com.myopenwrt.app.ui.OpenClashFragment;
import com.myopenwrt.app.ui.ServicesFragment;
import com.myopenwrt.app.ui.SettingsFragment;
import com.myopenwrt.app.ui.WifiFragment;
import com.myopenwrt.app.ui.widgets.GlassNavView;

public class MainActivity extends AppCompatActivity {

    private GlassNavView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener((index, itemId) -> {
            if (itemId == R.id.nav_dashboard) {
                switchFragment(new DashboardFragment());
            } else if (itemId == R.id.nav_network) {
                switchFragment(new NetworkFragment());
            } else if (itemId == R.id.nav_devices) {
                switchFragment(new DevicesFragment());
            } else if (itemId == R.id.nav_wifi) {
                switchFragment(new WifiFragment());
            } else if (itemId == R.id.nav_settings) {
                switchFragment(new SettingsFragment());
            }
        });

        applyWindowInsets();

        if (savedInstanceState == null) {
            bottomNav.setSelectedIndex(0);
            switchFragment(new DashboardFragment());
        }
    }

    /** 处理全面屏/状态栏与手势导航避让。 */
    private void applyWindowInsets() {
        final int baseBottom = dp(14);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            android.view.View container = findViewById(R.id.fragment_container);
            container.setPadding(0, bars.top, 0, 0);

            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) bottomNav.getLayoutParams();
            if (lp != null) {
                lp.bottomMargin = baseBottom + bars.bottom;
                bottomNav.setLayoutParams(lp);
            }
            return insets;
        });
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void switchFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    public void navigateTo(int itemId) {
        if (bottomNav != null) bottomNav.setSelectedItemId(itemId);
    }

    /** 打开服务与插件管理页（加入回退栈，按返回键回到概览）。 */
    public void openServices() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new ServicesFragment())
                .addToBackStack(null)
                .commit();
    }

    /** 打开 OpenClash 订阅配置页（加入回退栈，按返回键回到概览）。 */
    public void openOpenClash() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new OpenClashFragment())
                .addToBackStack(null)
                .commit();
    }
}
