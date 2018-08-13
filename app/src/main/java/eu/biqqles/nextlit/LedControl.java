
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
import java.util.ArrayList;
import java.util.HashMap;

class LedControl {
    // Control the LP5523 which drives the Robin's segmented LEDs, using sysfs.
    static final byte LED_COUNT = 4;
    static final byte LED_COUNT_MAX = 9;
    private static final byte ENGINE_COUNT = 3;
    private static final String DEVICE = "/sys/class/leds/lp5523:channel0/device/";
    private static final String PATTERN_FILE = "led_pattern";
    private final Process su;
    private final HashMap<String, Pattern> patternStore;

    static class Engine {
        /*
        The LP5523 has three "engines", which can each manage some of the four leds available.
        Each engine runs a sequence of instructions, usually (but not necessarily) in an infinite
        loop. These instructions control the brightness of the leds managed by the engine over time.

        Note: engine 1 doesn't seem to be very reliable, presumably because it is involved in
        running the predefined patterns (one of which, on most ROMs, runs at boot). Thus I try to
        avoid using it.
        */
        final byte number;
        final String leds;
        final String instructions;

        Engine(byte number, String leds, String instructions) {
            /*
            the engine number, 1-3
            */
            this.number = number;
            /*
            only four of the possible nine channels are used: a mux config of 111100000 gives the
            engine control of ALL leds. Ordering is right-left, i.e. 100000000 would control only
            the rightmost led
            */
            this.leds = leds + new String(new char[LED_COUNT_MAX - LED_COUNT]).replace('\0', '0');
            /*
            Reference: <www.farnell.com/datasheets/1814222.pdf>, Section 4: Instruction Set Details.
            <wiki.maemo.org/LED_patterns#Lysti_Format_Engine_Patterns_and_Commands> may also be
            useful. The following is the basic instruction subset used in this program.

                98d0 -- start, load multiplexer register
                9d0x -- select one led to be controlled (where x is count of the led in mux)
                9d00 -- clear engine to output mapping
                40xx -- set brightness of all LEDs controlled by the engine
                xxyy -- change brightness over time: time (per step), number of steps. If xx is odd,
                        i.e. its LSB is 1, decrement
                xx00 -- wait (variation of xxyy) - maximum value for xx is 7f
                0000 -- reset program counter and restart execution - if missed, messes up switching

            A maximum of 16 instructions (driver limitation, hardware can store up to 96), but since
            98d0 and 0000 are required for the pattern to function properly, we really only have a
            pithy 14 instructions per engine. Additionally and *inexplicably*, instruction sequences
            which do not contain the mux_sel (9d0x) instruction refuse to work at all if run at any
            point after predefined pattern 5 unless they contain mux_clr (9d00), reducing the number
            of actual instructions yet further to 13 in some cases.

            This class allows instructions to be separated with _ for readability.
            */
            this.instructions = instructions.replace("_", "");
        }
    }

    static class Pattern {
        /*
        For the purposes of this program, a "pattern" consists of _either_ a predefined key (0 to
        5 ins.), or a list of engines. The LP5523 allows multiple engines to be run concurrently (at
        least with the "new" interface), and this can be used to create more complex visuals.
        */
        final String name;
        int predefinedKey;
        ArrayList<Engine> customEngines;

        Pattern(String name, int predefinedKey) {
            this.name = name;
            this.predefinedKey = predefinedKey;
        }

        Pattern(String name, ArrayList<Engine> customEngines) {
            this.name = name;
            this.customEngines = customEngines;
        }
    }

    LedControl(HashMap<String, Pattern> patterns) throws IOException {
        patternStore = patterns;
        su = acquireRoot();
        // cd to device (so from now on all paths will be relative to it)
        execCommand(MessageFormat.format("cd {0}\n", DEVICE), false);
        // Check we have access to /sys (and therefore also that we have root access), otherwise
        // throw an exception
        final String result = execCommand("ls\n", true);
        if (result == null) {
            throw new IOException();
        }
    }

    void setPattern(Pattern pattern) {
        // Sets (activates) a given pattern.
        if (pattern != null) {
            if (pattern.customEngines == null) {
                // If the pattern is already running, don't write it again as this will cause it to
                // restart which looks a little unpolished. This isn't possible for custom patterns
                // as there's no way to get the current running pattern from an engine
                if (pattern.predefinedKey != getPredefPattern()) {
                    setPredefPattern(pattern);
                }
            } else {
                setCustomPattern(pattern);
            }
        } else {
            clearAll();
        }
    }

    void setPattern(String patternName) {
        // Sets (activates) a given pattern (convenience overload).
        final Pattern pattern = patternStore.get(patternName);
        setPattern(pattern);
    }

    void clearAll() {
        // Disables the running pattern, resets all engines and sets all pwm channels to 0.
        // sets all outputs to 0, yet (curiously) retains engine mode and mapping
        echoToFile("0", PATTERN_FILE);
        // disable the now inactive engines (for neatness' sake more than anything else)
        disableAllEngines();
    }

    private boolean isEngineRunning() {
        // Check if an engine is currently running.
        final String cat = "cat {0}\n";

        for (int i=1; i<ENGINE_COUNT+1; i++) {
            final String engine_mode = MessageFormat.format("engine{0}_mode", i);
            final String contents = execCommand(MessageFormat.format(cat, engine_mode), true);
            if (contents != null && contents.equals("run")) {
                return true;
            }
        }
        return false;
    }

    private int getPredefPattern() {
        // Gets the currently set predefined pattern.
        final String cmd = MessageFormat.format("cat {0}\n", PATTERN_FILE);
        try {
            return Integer.parseInt(execCommand(cmd, true));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void setPredefPattern(Pattern pattern) {
        // Sets a predefined pattern (programmed by Nextbit in platform data).
        // For descriptions of the five patterns available, see arrays.xml.
        clearAll();  // without resetting the engines, behaviour is undefined
        echoToFile(Integer.toString(pattern.predefinedKey), PATTERN_FILE);
    }

    private void setCustomPattern(Pattern pattern) {
        // Writes and runs a pattern using the "legacy" interface (the only one available on the
        // Robin, though strangely it seems to have some files related to the "new" interface.
        // <https://github.com/torvalds/linux/blob/master/Documentation/leds/leds-lp5523.txt>
        clearAll();  // load mode must be entered from disabled mode (datasheet page 29)
        for (Engine engine: pattern.customEngines) {
            final String engine_mode = MessageFormat.format("engine{0}_mode", engine.number);
            final String engine_load = MessageFormat.format("engine{0}_load", engine.number);
            final String engine_leds = MessageFormat.format("engine{0}_leds", engine.number);

            echoToFile("load", engine_mode);
            echoToFile(engine.instructions, engine_load);
            echoToFile(engine.leds, engine_leds);
            echoToFile("run", engine_mode);
        }
    }

    private void disableAllEngines() {
        // Disables (stops) all engines.
        for (int i=1; i<ENGINE_COUNT+1; i++) {
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
        final String cmd = MessageFormat.format("echo \"{0}\" > {1}\n", data, path);
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
