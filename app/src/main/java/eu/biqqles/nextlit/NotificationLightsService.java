
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
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public class NotificationLightsService extends NotificationListenerService {
    private LedControl ledcontrol;
    private BroadcastReceiver screenReceiver;
    private BroadcastReceiver dndReceiver;
    private NotificationManager manager;
    private SharedPreferences prefs;
    private SharedPreferences appsEnabled;  // preferences store each app's status and pattern,
    private SharedPreferences appsPatterns;  // using package name as a key

    private static boolean screenOn = true;

    @Override
    public void onCreate() {
        super.onCreate();

        final PatternProvider patternProvider = new PatternProvider(getApplicationContext());
        try {
            ledcontrol = new LedControl(patternProvider.patterns);
        } catch (IOException e) {
            // MainActivity will close if its LedControl can't initialise; how did you manage to get
            // here?
            System.exit(126);  // command not executable due to insufficient privileges
        }

        manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        appsEnabled = getSharedPreferences("apps_enabled", MODE_PRIVATE);
        appsPatterns = getSharedPreferences("apps_patterns", MODE_PRIVATE);

        // set up listener for screen on/off events
        final IntentFilter screenStateFilter = new IntentFilter();
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

        // and for changes in Do not Disturb state
        final IntentFilter dndFilter = new IntentFilter();
        dndFilter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);

        dndReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateState();
            }
        };
        registerReceiver(dndReceiver, dndFilter);
    }

    void updateState() {
        // Decides if, and how, the user needs to be notified and activates or deactivates the leds
        // accordingly. If notifying apps have conflicting patterns set, the notification with the
        // highest priority takes precedence and its pattern will be the one that is run.

        // service configuration options, listed in a rough order of priority
        final boolean enabled = prefs.getBoolean("service_enabled", false);
        final boolean obeyDnDPolicy = prefs.getBoolean("obey_dnd_policy", false);
        final boolean showWhenScreenOn = prefs.getBoolean("show_when_screen_on", false);
        final boolean obeyNotificationRules = prefs.getBoolean("obey_notif_light_rules", false);
        final boolean showForOngoing = prefs.getBoolean("show_for_ongoing", false);

        final boolean dndActive = manager.getCurrentInterruptionFilter() ==
                NotificationManager.INTERRUPTION_FILTER_ALARMS;  // "alarms only" mode

        String pattern = null;  // null represents disabled lights

        // get notifications in order of priority
        final StatusBarNotification[] notifications = getNotificationsByPriority();

        if (enabled && !(dndActive && obeyDnDPolicy) && (!screenOn || showWhenScreenOn)) {

            for (StatusBarNotification notification : notifications) {
                final String packageName = notification.getPackageName();
                final boolean appEnabled = appsEnabled.getBoolean(packageName, true);
                final String appPattern = appsPatterns.getString(packageName, null);

                /* If 'show for ongoing' is disabled, determine whether the notification shows the
                standard notification LED on the device so we can mirror that behaviour. On platforms
                earlier than Oreo, the only way to do this is to check whether the notification is
                clearable and whether DnD is currently enabled: with API 26 we can do one better and
                actually discover if a notification should enable the LED or not. */

                boolean notificationShowsLights = showForOngoing || notification.isClearable();

                if (obeyNotificationRules && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String channelId = notification.getNotification().getChannelId();
                    if (manager != null) {
                        NotificationChannel channel = manager.getNotificationChannel(channelId);
                        if (channel != null) {
                            notificationShowsLights = channel.shouldShowLights();
                        }
                    }
                }

                if (notificationShowsLights && appEnabled) {
                    // for appPattern, null represents the default pattern
                    pattern = appPattern != null ? appPattern : prefs.getString("pattern_name", null);
                    if (pattern != null) {
                        break;
                    }
                }
            }
        }
        ledcontrol.setPattern(pattern);
    }

    private StatusBarNotification[] getNotificationsByPriority() {
        // Get notifications sorted by priority in descending order, then by natural order.
        final StatusBarNotification[] notifications = getActiveNotifications();
        if (notifications == null) {  // service hasn't been initialised yet
            return new StatusBarNotification[0];
        }

        Arrays.sort(notifications, new Comparator<StatusBarNotification>() {
            public int compare(StatusBarNotification a, StatusBarNotification b) {
                // priority constants vary between -2 (PRIORITY_MIN) and 2 (PRIORITY_MAX)
                return Integer.compare(b.getNotification().priority, a.getNotification().priority);
            }
        });
        return notifications;
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
    public void onDestroy() {
        unregisterReceiver(screenReceiver);
        unregisterReceiver(dndReceiver);
        super.onDestroy();
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
