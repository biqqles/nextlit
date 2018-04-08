
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
        Each engine runs a sequence of instructions, usually (but not necessarily) in an infinite
        loop. These instructions control the brightness of the leds managed by the engine over time.
        Multiple engines can run concurrently (at least with the "new" interface), and this can be
        used to create more complex visuals. For the purposes of this program, a "pattern" is an
        array of engines, working together in this way.
        */
        Engine(int number, String leds, String instructions) {
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
            Reference: <www.farnell.com/datasheets/1814222.pdf>, Section 4: Instruction Set Details.
            <https://wiki.maemo.org/LED_patterns#Lysti_Format_Engine_Patterns_and_Commands> may also
            be useful.
            "Basic instruction set"
                98d0 -- start, load multiplexer register
                40xx -- set brightness of all LEDs controlled by the engine
                xxyy -- change brightness over time: time (per step), number of steps. If xx is -ve,
                        decrement
                xx00 -- wait (variation of xxyy)
                9d0x -- select led to be controlled (where x is count of the led in mux)
                0000 -- reset program counter and restart execution
            A maximum of 16 instructions (kernel limitation, hardware can store up to 96)

            This class allows instructions to be separated with _ for readability
            */
            this.instructions = instructions.replace("_", "");
        }

        final int number;
        final String leds;
        final String instructions;
    }

    // define some custom patterns
    private final Engine[][] customPatterns = {
            // loading
            {new Engine(2, "1111", "9d80_9d04_04ff_9d03_04ff_9d02_04ff_9d01_04ff" +
                                                            "05ff_9d02_05ff_9d03_05ff_9d04_03ff")},

            // breathing
            {new Engine(2, "1111", "9d80_10ff_11ff_0000")},

            // pulse
            {new Engine(2, "1111", "9d80_12ff_13dc_0000")},

            // bounce
            {new Engine(2, "1111", "9d80_16ff_05cc_40ff_05cc_0000")}
    };


    private static final String DEVICE = "/sys/class/leds/lp5523:channel0/device/";
    private static final String PATTERN_FILE = "led_pattern";
    private Process su;

    LedControl() throws IOException {
        su = acquireRoot();
        // cd to device (so from now on all paths will be relative to it)
        execCommand(MessageFormat.format("cd {0}\n", DEVICE), false);
        // Check we have access to /sys (and therefore also that we have root access), otherwise
        // throw an exception
        String result = execCommand("ls\n", true);
        if (result == null) {
            throw new IOException();
        }
    }

    void setPattern(int pattern) {
        // Sets (executes) a given pattern.
        // A pattern between 0 and 5 inclusive represents a predefined pattern held in platform data.
        // A pattern above 5 represents a "custom" pattern defined by the app, held in customPatterns.
        clearAll();
        if (pattern < 6) {
            setPredefPattern(pattern);
        } else {
            setCustomPattern(customPatterns[pattern - 6]);
        }
    }

    boolean isEngineRunning() {
        // Check if an engine is currently running.
        String cat = "cat {0}\n";

        for (int i=1; i<4; i++) {
            String engine_mode = MessageFormat.format("engine{0}_mode", i);
            String contents = execCommand(MessageFormat.format(cat, engine_mode), true);
            if (contents.equals("run")) {
                return true;
            }
        }
        return false;
    }

    void clearAll() {
        // Disables the running pattern, resets all engines and sets all pwm channels to 0.
        echoToFile("0", PATTERN_FILE);  // end predefined pattern execution
        // todo: force all pwm channels to 0 and reset mapping table
        disableAllEngines();  // end engine execution
    }

    int getPredefPattern() {
        // Gets the currently set predefined pattern.
        String cmd = MessageFormat.format("cat {0}\n", PATTERN_FILE);
        try {
            return Integer.parseInt(execCommand(cmd, true));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setPredefPattern(int pattern) {
        // Sets a predefined pattern (programmed by Nextbit in platform data).
        // For descriptions of the five patterns available, see arrays.xml.
        echoToFile(Integer.toString(pattern), PATTERN_FILE);
    }

    private void setCustomPattern(Engine[] engines) {
        // Writes and runs a pattern using the "legacy" interface (the only one available on the
        // Robin, though it seems to have some files related to the "new" interface.
        // Documentation: <https://github.com/torvalds/linux/blob/master/Documentation/leds/leds-lp5523.txt>

        // currently restricted to single engine mode:
        Engine engine = engines[0];

        String engine_mode = MessageFormat.format("engine{0}_mode", engine.number);
        String engine_load = MessageFormat.format("engine{0}_load", engine.number);
        String engine_leds = MessageFormat.format("engine{0}_leds", engine.number);

        echoToFile("load", engine_mode);  // freezes pwm and execution
        echoToFile(engine.instructions, engine_load);
        echoToFile(engine.leds, engine_leds);
        echoToFile("run", engine_mode);
    }

    private void disableAllEngines() {
        // Disables (stops) all engines.
        for (int i=1; i<4; i++) {
            echoToFile("disabled", MessageFormat.format("engine{0}_mode", i));
        }
    }

    private Process acquireRoot() throws IOException {
        // Creates a Process with root privileges.
        // It's worth noting that writing & reading files with root privileges can only be achieved
        // through shell commands, and not Java itself.
        return Runtime.getRuntime().exec("su");
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
