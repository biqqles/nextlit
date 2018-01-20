
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
    // Control the LP5523 which drives the Robin's segmented LEDs, using sysfs.

    class Engine {
        /*
        The LP5523 has three "engines", which can each manage some of the four leds available.
        Each engine runs a sequence of microcode instructions, usually (but not necessarily) in an
        infinite loop. These instructions control the brightness of the leds managed by the engine over
        time. Multiple engines can run concurrently (at least with the "new" interface), and this can be
        used to create more complex visuals. For the purposes of this program, a "pattern" is an array
        of engines, working together in this way.
        */
        Engine(int number, String leds, String microcode) {
            /*
            the engine number, 1-3
            */
            this.number = number;
            /*
            only four of the possible nine channels are used: a mux config of 111100000 gives the
            engine control of ALL leds. Ordering is right-left, i.e. 100000000 would control only
            the rightmost led
            */
            this.leds = leds + "00000";
            /*
            based on <https://wiki.maemo.org/LED_patterns#Lysti_Format_Engine_Patterns_and_Commands>
            "Basic instruction set"
                98d0 -- start, load multiplexer register
                40xx -- set engine brightness (auto inc)
                xxyy -- change brightness over time: time (per step), number of steps. If xx is -ve, decrement
                9d0x -- select led to be controlled (where x is count of the led in mux)
            A maximum of 16 instructions (kernel limitation, hardware can store up to 96)

            This class allows microcode instructions to be separated with _ for readability
            */
            this.microcode = microcode.replace("_", "");
        }

        final int number;
        final String leds;
        final String microcode;
    }


    final Engine[][] customPatterns = {
            // loading
            {new Engine(2, "1111", "9d80" +
                    "9d04_04ff_9d03_04ff_9d02_04ff_9d01_04ff" +  // light sequentially
                    "05ff_9d02_05ff_9d03_05ff_9d04_03ff")},  // dim sequentially

            // breathing
            {new Engine(2, "1111", "9d80_10ff_11ff_0000")},

            // running multiple engines _works_, but the behaviour is very odd...
            {new Engine(3, "0110", "9d80_30ff_3100_0000"),
             new Engine(2, "1001", "9d80_4000_10ee_0000")}
    };


    private static final String DEVICE = "/sys/class/leds/lp5523:channel0/device/";
    private static final String PATTERN_FILE = "led_pattern";
    private Process su = acquireRoot();

    LedControl() throws IOException {
        // cd to LP5523 (so from now on all paths will be relative to it)
        execCommand(MessageFormat.format("cd {0}\n", DEVICE), false);
        // Check we have access to /sys (and therefore indirectly that we have root access),
        // otherwise throw an exception.
        String result = execCommand("ls\n", true);
        if (result == null) {
            throw new IOException();
        }
    }

    void clearAll() {
        // Clears all leds: disables any predefined pattern and resets all engines.
        clearPattern();
        disableAllEngines();
    }

    void setPattern(int pattern) {
        // Sets a predefined pattern (programmed by Nextbit in platform data).
        // For descriptions of the five patterns available, see arrays.xml.
        clearPattern();
        echoToFile(Integer.toString(pattern), PATTERN_FILE);
    }

    int getPattern() {
        // Gets the currently set pattern.
        String cmd = MessageFormat.format("cat {0}\n", PATTERN_FILE);
        try {
            return Integer.parseInt(execCommand(cmd, true));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void clearPattern() {
        // Clears current pattern and sets all leds to LOW.
        echoToFile("0", PATTERN_FILE);
    }

    void setCustomPattern(Engine[] engines) {
        // Write and run a pattern using the "legacy" interface (the only one available on the Robin,
        // though it seems to have some files related to the "new" interface.
        // Documentation: <https://github.com/torvalds/linux/blob/master/Documentation/leds/leds-lp5523.txt>
        disableAllEngines();
        for (Engine engine:engines) {
            String engine_mode = MessageFormat.format("engine{0}_mode", engine.number);
            String engine_load = MessageFormat.format("engine{0}_load", engine.number);
            String engine_leds = MessageFormat.format("engine{0}_leds", engine.number);

            // may be causing odd timing issues, but suggested in leds-lp55xx.txt
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            echoToFile("load", engine_mode); // freezes pwm and execution
            // datasheet suggests 1 ms pause here, unless driver does that
            echoToFile(engine.microcode, engine_load);
            echoToFile(engine.leds, engine_leds);
            echoToFile("run", engine_mode);  // or disabled here, then run in another loop
        }
    }

    private void disableAllEngines() {
        // Disables all engines.
        for (int i=1; i<4; i++) {
            echoToFile("disabled", MessageFormat.format("engine{0}_mode", i));
            // block for a bit, zooming through this seems to risk not resetting the engines fully
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private Process acquireRoot() {
        // Creates a Process with root privileges.
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
        } catch (IOException e) {
            Log.e("NEXTLIT", MessageFormat.format("Failed to execute {0} with error {1}",
                    command, e));
        }
        return null;
    }
}
