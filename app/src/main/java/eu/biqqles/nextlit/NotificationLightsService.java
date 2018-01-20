
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.io.IOException;

public class NotificationLightsService extends NotificationListenerService {
    LedControl ledcontrol;
    SharedPreferences prefs;
    public static boolean enabled = false;
    private static boolean screenOn = true;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            ledcontrol = new LedControl();
        } catch (IOException e) {
            // MainActivity will close if its LedControl can't initialise; how did you manage to get
            // here?
            System.exit(0);
        }

        prefs = getSharedPreferences("nextlit", MODE_PRIVATE);

        // set up listener for screen on/off events
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);

        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action != null) {
                    if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                        screenOn = true;
                    } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                        screenOn = false;
                    }
                }
                updateState();
            }
        },
                screenStateFilter);
    }

    void updateState() {
        // Determines whether the lights should be enabled or disabled.
        boolean showWhenScreenOn = prefs.getBoolean("show_when_screen_on", false);
        if (getActiveNotifications().length > 0 && enabled && (!screenOn || showWhenScreenOn)) {
            int pattern = prefs.getInt("predef_pattern", 0);  // get selected pattern from preferences
            // if pattern isn't set already, set it
            if (ledcontrol.getPattern() != pattern) {
                ledcontrol.setPattern(pattern);
            }
        } else {
            ledcontrol.clearAll();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.hasCategory("restore_state")) {
                updateState();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        updateState();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        updateState();
    }
}
