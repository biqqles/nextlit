
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public class NotificationLightsService extends NotificationListenerService {
    private LedControl ledcontrol;
    private AudioManager audio;
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

        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
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
                    screenOn = intent.getAction().equals(Intent.ACTION_SCREEN_ON);
                }
                updateState();
            }
        };
        registerReceiver(screenReceiver, screenStateFilter);

        // and for changes in interruption filter state
        final IntentFilter dndFilter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            dndFilter.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
        }

        dndReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateState();
            }
        };
        registerReceiver(dndReceiver, dndFilter);
    }

    @Override
    public void onDestroy() {
        // Unregister receivers.
        unregisterReceiver(screenReceiver);
        unregisterReceiver(dndReceiver);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Handle instructions in the form of Intent categories passed with startService().
        // This is the only way to communicate with (i.e. call methods from) the service from the
        // app activity.
        if (intent != null) {
            if (intent.hasCategory("restore_state")) {
                // used to restore the "proper" state of the leds after it has been manually
                // changed, e.g. after previewing a pattern
                updateState();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        updateState();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        updateState();
    }

    void updateState() {
        // Decides if, and how, the user needs to be notified and activates or deactivates the leds
        // accordingly. If notifying apps have conflicting patterns set, the notification with the
        // highest priority takes precedence and its pattern will be the one that is run.

        // service configuration options, listed in a rough order of priority
        final boolean enabled = prefs.getBoolean("service_enabled", false);
        final boolean obeyDnDPolicy = prefs.getBoolean("obey_dnd_policy", false);
        final boolean showWhenScreenOn = prefs.getBoolean("show_when_screen_on", false);
        final boolean showForAll = prefs.getBoolean("show_for_ongoing", false);
        final boolean mimicStandard = prefs.getBoolean("mimic_standard_behaviour", false);

        final boolean dndActive = interruptionFilterActive();
        final boolean notificationLightActive = ledcontrol.isNotificationActive();

        String pattern = null;  // null represents disabled lights

        // get notifications in order of priority
        final StatusBarNotification[] notifications = getNotificationsByPriority();

        if (enabled && !(dndActive && obeyDnDPolicy)) {
            for (StatusBarNotification sbn : notifications) {
                final String packageName = sbn.getPackageName();
                final boolean appEnabled = appsEnabled.getBoolean(packageName, true);
                final String appPattern = appsPatterns.getString(packageName, null);

                if (!appEnabled) continue;

                final Notification notification = sbn.getNotification();
                final boolean fullscreenOverride = notificationIsFullscreen(notification);
                final boolean showsLights = showForAll || notificationShowsLights(notification);

                if (!fullscreenOverride) {
                    if (screenOn && !showWhenScreenOn) continue;
                    if (mimicStandard && !notificationLightActive) continue;
                }

                if (showsLights || fullscreenOverride) {
                    // for appPattern, null represents the default pattern
                    pattern = appPattern != null ? appPattern : prefs.getString("pattern_name",
                            null);
                    if (pattern != null) break;
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

    private boolean interruptionFilterActive() {
        // Returns true if "alarms only" or "total silence" is active.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        final int filter = manager.getCurrentInterruptionFilter();
        return filter == NotificationManager.INTERRUPTION_FILTER_ALARMS ||
               filter == NotificationManager.INTERRUPTION_FILTER_NONE;
    }

    private boolean notificationShowsLights(Notification notification) {
        // Returns true if the given notification should ordinarily activate the notification light.
        // This doesn't necessarily mean that the notification /will/ show lights, because Android
        // will decide not to flash the lights if it decides the notification has been read.

        boolean guessedByFlags = (notification.ledOnMS > 0 &&
                checkFlagPresent(Notification.FLAG_SHOW_LIGHTS, notification.flags)) ||
                checkFlagPresent(Notification.DEFAULT_ALL, notification.defaults) ||
                checkFlagPresent(Notification.DEFAULT_LIGHTS, notification.defaults);

        /* In my testing on 8.1, a couple of apps produce notifications which display lights yet do
        not set these flags. I have little idea why this is the case when the vast majority of apps
        set the flags as expected (i.e. backwards compatibility seems to work in most cases). We can
        only access channels owned by our app (attempting to bypass this by directly accessing
        INotificationManager leads to a SecurityException without being a system app, so we cannot
        use NotificationChannel.shouldShowLights without quite some hassle).
        See: NotificationManager.getNotificationChannel, INotificationManager.Stub.asInterface,
        NotificationManagerService.checkCallerIsSystemOrSameApp, ServiceManager.getService.

        The best alternative is to assume that communication should always merit a notification
        light. Synchronous communication is handled differently: see notificationIsFullscreen. */

        boolean guessedByCategory = notification.category != null &&
                (notification.category.equals(Notification.CATEGORY_EMAIL) ||
                 notification.category.equals(Notification.CATEGORY_MESSAGE));

        return guessedByFlags || guessedByCategory;
    }

    private boolean notificationIsFullscreen(Notification notification) {
        // Returns true if the notification displays fullscreen, on top of other apps. This is used
        // by notifications that demand the user's immediate action, such as alarms and incoming
        // calls. Because these notifications turn on the display they generally do not request the
        // activation of the standard notification light.

        boolean guessedByIntent = notification.fullScreenIntent != null;

        boolean guessedByCategory = notification.category != null &&
                (notification.category.equals(Notification.CATEGORY_ALARM) ||
                 notification.category.equals(Notification.CATEGORY_CALL));

        /* In my testing on Lineage 15.1, com.android.dialer has a strange bug whereby its "incoming
        call" notification's channel will flip between `phone_incoming_call`, which contains the
        fullscreen intent, and `phone_ongoing_call`, which does not. This breaks lights for incoming
        calls. This workaround checks that a call really is in progress when the latter channel is
        in use. If it isn't, we know that it is set erroneously. */

        boolean incomingCallFix = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                ("phone_ongoing_call".equals(notification.getChannelId()) &&
                 audio.getMode() != AudioManager.MODE_IN_CALL);

        return guessedByIntent || guessedByCategory || incomingCallFix;
    }

    private boolean checkFlagPresent(int toCheck, int flags) {
        // Check that the given flag is present in, i.e. bitwise ORed onto, an integer representing
        // flags set.
        return (flags & toCheck) == toCheck;
    }
}
