package com.myopenwrt.app;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.myopenwrt.app.ui.DashboardFragment;
import com.myopenwrt.app.ui.DevicesFragment;
import com.myopenwrt.app.ui.NetworkFragment;
import com.myopenwrt.app.ui.OpenClashFragment;
import com.myopenwrt.app.ui.ServicesFragment;
import com.myopenwrt.app.ui.SettingsFragment;
import com.myopenwrt.app.ui.WifiFragment;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_dashboard) {
                switchFragment(new DashboardFragment());
                return true;
            } else if (id == R.id.nav_network) {
                switchFragment(new NetworkFragment());
                return true;
            } else if (id == R.id.nav_devices) {
                switchFragment(new DevicesFragment());
                return true;
            } else if (id == R.id.nav_wifi) {
                switchFragment(new WifiFragment());
                return true;
            } else if (id == R.id.nav_settings) {
                switchFragment(new SettingsFragment());
                return true;
            }
            return false;
        });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }
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
