
/*
 * Copyright © 2018 biqqles.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package eu.biqqles.nextlit;

import android.content.Context;
import android.util.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;

class PatternProvider {
    // Provides access to the LP5523 patterns used throughout the application.
    // At some point will probably have to be "upgraded" to an Adapter.
    private static final String PATTERN_STORE = "patterns.json";
    static LinkedHashMap<String, LedControl.Pattern> patterns;  // maps pattern name to Pattern

    PatternProvider(Context context) {
        patterns = new LinkedHashMap<>();
        patterns.putAll(readPredefPatterns(context));
        try {
            patterns.putAll(readCustomPatterns(context));
        } catch (IOException ignored) {
        }
    }

    ArrayList<String> getNames() {
        // Returns a list of strings representing patterns, in insertion order.
        return new ArrayList<>(patterns.keySet());
    }

    int indexOf(String name) {
        // Returns the index of a pattern by its name.
        if (name == null) {
            return 0;
        }
        return getNames().indexOf(name);
    }

    private static LinkedHashMap<String, LedControl.Pattern> readPredefPatterns(Context context) {
        // Reads an array of descriptions for the patterns programmed into the LP5523 by Nextbit
        // from resources, and maps them sequentially to their integer keys, returning a dictionary
        // of Pattern objects.
        final LinkedHashMap<String, LedControl.Pattern> patterns = new LinkedHashMap<>();

        final List<String> names = Arrays.asList(
                context.getResources().getStringArray(R.array.predefinedPatternNames));
        final ListIterator<String> iterator = names.listIterator();

        while (iterator.hasNext()) {
            final String name = iterator.next();
            final int key = iterator.previousIndex();
            patterns.put(name, new LedControl.Pattern(name, key));
        }
        return patterns;
    }

    private static LinkedHashMap<String, LedControl.Pattern> readCustomPatterns(Context context)
            throws IOException {
        // Reads the json file specified at PATTERN_STORE containing patterns defined by this
        // application, and returns a dictionary of Pattern objects.
        final LinkedHashMap<String, LedControl.Pattern> patterns = new LinkedHashMap<>();

        final InputStream input = context.getAssets().open(PATTERN_STORE);
        final JsonReader reader = new JsonReader(new InputStreamReader(input, "UTF-8"));

        reader.beginArray();
        while (reader.hasNext()) {  // for each pattern
            LedControl.Pattern pattern = jsonReadPattern(reader);
            patterns.put(pattern.name, pattern);
        }
        reader.endArray();
        return patterns;
    }

    private static LedControl.Pattern jsonReadPattern(JsonReader reader) throws IOException {
        // Reads a pattern object from pattern store.
        reader.beginObject();

        String name = "";
        final ArrayList<LedControl.Engine> engines = new ArrayList<>();

        while (reader.hasNext()) {
            String key = reader.nextName();

            if (key.equals("name")) {
                name = reader.nextString();
            } else if (key.equals("engines")) {
                reader.beginArray();
                while (reader.hasNext()) {  // for each engine
                    engines.add(jsonReadEngine(reader));
                }
                reader.endArray();
            }
        }

        reader.endObject();
        return new LedControl.Pattern(name, engines);
    }

    private static LedControl.Engine jsonReadEngine(JsonReader reader) throws IOException {
        // Reads an engine object from pattern store.
        byte number = 0;
        String leds = "";
        String instructions = "";

        reader.beginObject();

        while (reader.hasNext()) {  // for each engine parameter
            final String key = reader.nextName();
            switch (key) {
                case "number":
                    number = (byte) reader.nextInt();
                    break;
                case "leds":
                    leds = reader.nextString();
                    break;
                case "instructions":
                    instructions = reader.nextString();
                    break;
            }
        }

        reader.endObject();
        return new LedControl.Engine(number, leds, instructions);
    }
}
