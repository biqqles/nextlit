
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;


class LedControl {
    // Various methods to control the LP5523 which drives the Robin's segmented LEDs, using sysfs.
    private static final String PATTERN_FILE = "/sys/class/leds/lp5523:channel0/device/led_pattern";
    private Process su = acquireRoot();

    LedControl() throws IOException {
        // Check we have access to /sys (and therefore indirectly that we have root access),
        // otherwise throw an exception.
        String result = execCommand("ls /sys\n", true);
        if (result == null) {
            throw new IOException();
        }
    }

    void setPattern(int pattern) {
        // Sets a predefined pattern programmed by Nextbit.
        // For descriptions of the six patterns available (with 0 being clear), see arrays.xml.
        clearPattern();
        echoToFile(Integer.toString(pattern), PATTERN_FILE);
    }

    int getPattern() {
        // Gets the currently set pattern.
        String cmd = MessageFormat.format("cat {0}\n", PATTERN_FILE);
        try {
            return Integer.parseInt(execCommand(cmd, true));
        } catch (NumberFormatException ex) {
            return 0;
        }
     }

    void clearPattern() {
        // Clears current pattern and set all leds to LOW.
        echoToFile("0", PATTERN_FILE);
    }

    private Process acquireRoot() {
        // Create a Process with root privileges.
        // It's worth noting that writing / reading from files with root privileges can only be
        // done through shell commands, and not Java itself.
        try {
            return Runtime.getRuntime().exec("su");
        } catch (IOException e) {
            return null;
        }
    }

    private void echoToFile(String data, String path) {
        // Writes <data> to a file at <path>.
        String cmd = MessageFormat.format("echo \"{0}\" > {1}\n", data, path);
        execCommand(cmd, false);
    }

    private String execCommand(String command, boolean getOutput) {
        // Executes a shell command, with root privileges, and optionally returns the result.
        try {
            DataOutputStream dos = new DataOutputStream(su.getOutputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(su.getInputStream()));
            dos.writeBytes(command);
            dos.flush();
            if (getOutput) {
                return br.readLine();
            }
        } catch (IOException ioe) {
            Log.e("NEXTLIT", MessageFormat.format("Failed to execute {} with error {}",
                    command, ioe));
        }
        return null;
    }
}
