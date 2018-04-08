
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.io.IOException;

public class NotificationLightsService extends NotificationListenerService {
    private LedControl ledcontrol;
    private BroadcastReceiver screenReceiver;
    private NotificationManager manager;
    private SharedPreferences prefs;

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

        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        prefs = getSharedPreferences("nextlit", MODE_PRIVATE);

        // set up listener for screen on/off events
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);

        screenReceiver = new BroadcastReceiver() {
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
        };

        registerReceiver(screenReceiver, screenStateFilter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenReceiver);
        super.onDestroy();
    }

    void updateState() {
        // Decides whether the user needs to be notified and activates or deactivates the LEDs
        // accordingly.
        boolean enabled = prefs.getBoolean("service_enabled", false);
        boolean showWhenScreenOn = prefs.getBoolean("show_when_screen_on", false);
        boolean showForOngoing = prefs.getBoolean("show_for_ongoing", false);

        boolean notifyRequired = false;

        StatusBarNotification[] notifications = getActiveNotifications();
        if (notifications == null) {
            return;  // service hasn't been initialised yet
        }

        if (showForOngoing) {
            notifyRequired = (notifications.length > 0);
        } else {
            // by default, only "clearable" notifications should activate the lights: with Oreo
            // (API 26) we can do one better and actually discover if a notification should activate
            // the standard notification LED, and mirror that behaviour
            for(StatusBarNotification notification:notifications) {
                boolean notificationShowsLights = notification.isClearable();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String channelId = notification.getNotification().getChannelId();
                    if (manager != null) {
                        NotificationChannel channel = manager.getNotificationChannel(channelId);
                        if (channel != null) {
                            notificationShowsLights = channel.shouldShowLights();
                        }
                    }
                }

                if (notificationShowsLights) {
                    notifyRequired = true;
                    break;
                }
            }
        }

        if (notifyRequired && enabled && (!screenOn || showWhenScreenOn)) {
            // activate the lights
            int pattern = prefs.getInt("pattern", 0);  // get selected pattern from preferences
            // if a pattern isn't running, start one
            if (pattern > 5 || ledcontrol.getPredefPattern() != pattern) {
                ledcontrol.setPattern(pattern);
            }
        } else {
            ledcontrol.clearAll();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle instructions in the form of Intent categories passed with startService().
        // This is the only way to communicate with (i.e. call methods from) the service from the app
        // activity.
        if (intent != null) {
            if (intent.hasCategory("restore_state")) {
                // used to restore the "proper" state of the leds after it has been manually changed,
                // e.g. after previewing a pattern
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
