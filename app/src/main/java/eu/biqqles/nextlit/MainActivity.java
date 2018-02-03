
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;


public class MainActivity extends AppCompatActivity {
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
            Toast.makeText(this, "App requires root access, closing", Toast.LENGTH_LONG).show();
            // prevent the service from constantly trying to restart itself if already enabled
            prefs.edit().putBoolean("service_enabled", false).apply();
            finish();
        }

        final Spinner patternSpinner = findViewById(R.id.patternSpinner);
        final ToggleButton previewButton = findViewById(R.id.previewButton);
        final Switch showWhenScreenOn = findViewById(R.id.showWhenScreenOn);
        final Switch showForOngoing = findViewById(R.id.showForOngoing);

        patternSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView adapterView, View view, int i, long l) {
                previewButton.setChecked(false);
                restoreLightsState();
                // update preferences (spinner index starts at 0 but patterns start at 1 (0 = clear))
                prefs.edit().putInt("pattern", i + 1).apply();
            }

            public void onNothingSelected(AdapterView<?> adapterView) { }
        });

        patternSpinner.setSelection(prefs.getInt("pattern", 1) - 1);
        previewButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (checked) {
                    String patternName = patternSpinner.getSelectedItem().toString();
                    int patternNumber = patternSpinner.getSelectedItemPosition();

                    ledcontrol.clearAll();
                    ledcontrol.setPattern(patternNumber + 1);

                    View view = findViewById(R.id.mainLayout);
                    Snackbar.make(view, "Previewing " + patternName, Snackbar.LENGTH_LONG).show();
                } else {
                    restoreLightsState();
                }
            }
        });

        // when selected, this option will activate the lights while the screen is on
        showWhenScreenOn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean("show_when_screen_on", checked).apply();
                restoreLightsState();
            }
        });

        showWhenScreenOn.setChecked(prefs.getBoolean("show_when_screen_on", false));

        // when selected, this option will activate the lights for ongoing (persistent) notifications
        showForOngoing.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                prefs.edit().putBoolean("show_for_ongoing", checked).apply();
                restoreLightsState();
            }
        });

        showForOngoing.setChecked(prefs.getBoolean("show_for_ongoing", false));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // add listener to start/stop service when switch toggled
        final MenuItem item = menu.findItem(R.id.switch_item);
        item.setActionView(R.layout.actionbar_switch_layout);
        Switch serviceSwitch = item.getActionView().findViewById(R.id.serviceSwitch);

        serviceSwitch.setChecked(prefs.getBoolean("service_enabled", false));

        // this switch enables and disables the notification service
        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
           @Override
            public void onCheckedChanged(CompoundButton button, boolean checked) {
               if (checked) {
                   // take user to Notification access if they haven't enabled the service already
                   if (!serviceBound()) {
                       startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                       Toast.makeText(MainActivity.this, "Enable Nextlit", Toast.LENGTH_SHORT).show();
                   }

                   /*
                   Normal procedure here would be to call start/stopService, but for whatever reason
                   Android ignores these calls for NotificationListenerServices, and so effectively
                   the only thing that governs whether a service is enabled or not is if it's enabled
                   in Notification access. Instead, we accept that the service will always run and
                   just modify a static flag that says if the lights should be enabled or not.
                   */
                   Toast.makeText(MainActivity.this, "Service started", Toast.LENGTH_SHORT).show();
               } else {
                   Toast.makeText(MainActivity.this, "Service stopped", Toast.LENGTH_SHORT).show();
               }

               prefs.edit().putBoolean("service_enabled", checked).apply();
               restoreLightsState();
           }
        });
       return super.onCreateOptionsMenu(menu);
    }

    void restoreLightsState() {
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
}





