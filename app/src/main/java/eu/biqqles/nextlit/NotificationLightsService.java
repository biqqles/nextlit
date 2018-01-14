
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
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.io.IOException;

public class NotificationLightsService extends NotificationListenerService {
    SharedPreferences prefs;
    LedControl ledcontrol;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = this.getSharedPreferences("nextlit", MODE_PRIVATE);
        try {
            ledcontrol = new LedControl();
        } catch (IOException ioe) {
            // MainActivity will close if its LedControl can't initialise; how did you manage to get
            // here?
            System.exit(0);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn){
        if (getActiveNotifications().length > 0) {
            int pattern  = prefs.getInt("predef_pattern", 0);  // get selected pattern from preferences
            // if pattern isn't set already, set it
            if (ledcontrol.getPattern() != pattern) {
                ledcontrol.setPattern(pattern);
            }
        }
        else {
            ledcontrol.clearPattern();
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn){
        onNotificationPosted(sbn);  // just do the same thing
    }
}
