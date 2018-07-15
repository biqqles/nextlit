
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private LedControl ledcontrol;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        prefs = getSharedPreferences("nextlit", MODE_PRIVATE);

        try {
            ledcontrol = new LedControl();
        } catch (IOException e) {
            // root access denied/unavailable
            rootDenied();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // add listener to start/stop service when switch toggled
        final View header = navigationView.getHeaderView(0);
        final Switch serviceSwitch = header.findViewById(R.id.serviceSwitch);
        final Spinner patternSpinner = header.findViewById(R.id.patternSpinner);
        final ToggleButton previewButton = header.findViewById(R.id.previewButton);
        final TextView subtitle = header.findViewById(R.id.nav_header_subtitle);

        // this switch enables and disables the notification service
        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean checked) {
            int statusText;

            if (checked) {
                // take user to Notification access if they haven't enabled the service already
                if (!serviceBound()) {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    Toast.makeText(MainActivity.this,
                                   getResources().getString(R.string.enable_service),
                                   Toast.LENGTH_SHORT).show();
                }

                /*
                Normal procedure here would be to call start/stopService, but for whatever reason
                Android ignores these calls for NotificationListenerServices, and so effectively
                the only thing that governs whether a service is enabled or not is if it's enabled
                in Notification access. Instead, we accept that the service will always run and
                just modify a static flag that says if the lights should be enabled or not.
                */
                statusText = R.string.service_enabled;
            } else {
                statusText = R.string.service_disabled;
            }

            Toast.makeText(MainActivity.this, statusText, Toast.LENGTH_SHORT).show();
            subtitle.setText(statusText);

            prefs.edit().putBoolean("service_enabled", checked).apply();
            restoreLightsState();
            }
        });

        serviceSwitch.setChecked(prefs.getBoolean("service_enabled", false));

        patternSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView adapterView, View view, int i, long l) {
                // halt preview and update preferences
                previewButton.setChecked(false);
                restoreLightsState();
                prefs.edit().putInt("pattern", i).apply();
            }

            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        patternSpinner.setSelection(prefs.getInt("pattern", 0));
        previewButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
            if (checked) {
                String patternName = patternSpinner.getSelectedItem().toString();
                int patternNumber = patternSpinner.getSelectedItemPosition();

                ledcontrol.clearAll();
                ledcontrol.setPattern(patternNumber);

                View view = findViewById(R.id.mainLayout);
                Snackbar.make(view, "Previewing " + patternName, Snackbar.LENGTH_LONG).show();
            } else {
                restoreLightsState();
            }
            }
        });

        // display default fragment
        onNavigationItemSelected(navigationView.getMenu().findItem(R.id.nav_per_app));
    }

    @Override
    public void onBackPressed() {
        // Handle back button pressed.
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks.
        int id = item.getItemId();

        if (id == R.id.settings) {
            // settings activities work differently
        } else {
            Fragment fragment = new Fragment();

            switch (id) {
                case R.id.nav_per_app:
                    fragment = new PerAppFragment();
                    break;
            }

            getSupportFragmentManager()
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .replace(R.id.content_frame, fragment)
                    .commitNow();
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    private void restoreLightsState() {
        // Restores the "proper" state of the leds. Should be called after any setting which might
        // require a change in their current visibility has been modified.
        if (serviceBound()) {
            Intent serviceIntent = new Intent(this, NotificationLightsService.class);
            serviceIntent.addCategory("restore_state");
            startService(serviceIntent);
            stopService(serviceIntent);
        } else {
            ledcontrol.clearAll();
        }
    }

    boolean serviceBound() {
        // Reports whether the notification service has been bound (i.e. activated).
        String enabledNotificationListeners = Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");

        return !(enabledNotificationListeners == null ||
                !enabledNotificationListeners.contains(getPackageName()));
    }

    void rootDenied() {
        // Handle root access unavailable or denied.
        Toast.makeText(this, "App requires root access, closing", Toast.LENGTH_LONG).show();
        // prevent the service from constantly trying to restart itself if already enabled
        prefs.edit().putBoolean("service_enabled", false).apply();
        finish();
    }
}
