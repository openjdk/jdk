/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package org.graalvm.compiler.options;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.UnmodifiableEconomicMap;
import jdk.internal.vm.compiler.collections.UnmodifiableMapCursor;

/**
 * A context for obtaining values for {@link OptionKey}s.
 */
public class OptionValues {

    private final UnmodifiableEconomicMap<OptionKey<?>, Object> values;

    protected boolean containsKey(OptionKey<?> key) {
        return values.containsKey(key);
    }

    public OptionValues(OptionValues initialValues, UnmodifiableEconomicMap<OptionKey<?>, Object> extraPairs) {
        EconomicMap<OptionKey<?>, Object> map = newOptionMap();
        if (initialValues != null) {
            map.putAll(initialValues.getMap());
        }
        initMap(map, extraPairs);
        this.values = map;
    }

    public OptionValues(OptionValues initialValues, OptionKey<?> key1, Object value1, Object... extraPairs) {
        this(initialValues, asMap(key1, value1, extraPairs));
    }

    /**
     * Creates a new map suitable for using {@link OptionKey}s as keys.
     */
    public static EconomicMap<OptionKey<?>, Object> newOptionMap() {
        return EconomicMap.create(Equivalence.IDENTITY);
    }

    /**
     * Gets an immutable view of the key/value pairs in this object. Values read from this view
     * should be {@linkplain #decodeNull(Object) decoded} before being used.
     */
    public UnmodifiableEconomicMap<OptionKey<?>, Object> getMap() {
        return values;
    }

    /**
     * @param key1 first key in map
     * @param value1 first value in map
     * @param extraPairs key/value pairs of the form {@code [key1, value1, key2, value2, ...]}
     * @return a map containing the key/value pairs as entries
     */
    public static EconomicMap<OptionKey<?>, Object> asMap(OptionKey<?> key1, Object value1, Object... extraPairs) {
        EconomicMap<OptionKey<?>, Object> map = newOptionMap();
        map.put(key1, value1);
        for (int i = 0; i < extraPairs.length; i += 2) {
            OptionKey<?> key = (OptionKey<?>) extraPairs[i];
            Object value = extraPairs[i + 1];
            map.put(key, value);
        }
        return map;
    }

    public OptionValues(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        EconomicMap<OptionKey<?>, Object> map = newOptionMap();
        initMap(map, values);
        this.values = map;
    }

    protected static void initMap(EconomicMap<OptionKey<?>, Object> map, UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        UnmodifiableMapCursor<OptionKey<?>, Object> cursor = values.getEntries();
        while (cursor.advance()) {
            map.put(cursor.getKey(), encodeNull(cursor.getValue()));
        }
    }

    protected <T> T get(OptionKey<T> key) {
        return get(values, key);
    }

    @SuppressWarnings("unchecked")
    protected static <T> T get(UnmodifiableEconomicMap<OptionKey<?>, Object> values, OptionKey<T> key) {
        Object value = values.get(key);
        if (value == null) {
            return key.getDefaultValue();
        }
        return (T) decodeNull(value);
    }

    private static final Object NULL = new Object();

    protected static Object encodeNull(Object value) {
        return value == null ? NULL : value;
    }

    /**
     * Decodes a value that may be the sentinel value for {@code null} in a map.
     */
    public static Object decodeNull(Object value) {
        return value == NULL ? null : value;
    }

    @Override
    public String toString() {
        return toString(getMap());
    }

    public static String toString(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
        Comparator<OptionKey<?>> comparator = new Comparator<OptionKey<?>>() {
            @Override
            public int compare(OptionKey<?> o1, OptionKey<?> o2) {
                return o1.getName().compareTo(o2.getName());
            }
        };
        SortedMap<OptionKey<?>, Object> sorted = new TreeMap<>(comparator);
        UnmodifiableMapCursor<OptionKey<?>, Object> cursor = values.getEntries();
        while (cursor.advance()) {
            sorted.put(cursor.getKey(), decodeNull(cursor.getValue()));
        }
        return sorted.toString();
    }

    private static final int PROPERTY_LINE_WIDTH = 80;
    private static final int PROPERTY_HELP_INDENT = 10;

    /**
     * Wraps some given text to one or more lines of a given maximum width.
     *
     * @param text text to wrap
     * @param width maximum width of an output line, exception for words in {@code text} longer than
     *            this value
     * @return {@code text} broken into lines
     */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text.length() > width) {
            String[] chunks = text.split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String chunk : chunks) {
                if (line.length() + chunk.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() != 0) {
                    line.append(' ');
                }
                line.append(chunk);
            }
            if (line.length() != 0) {
                lines.add(line.toString());
            }
        } else {
            lines.add(text);
        }
        return lines;
    }

    /**
     * Prints a help message to {@code out} describing all options available via {@code loader}. The
     * key/value for each option is separated by {@code :=} if the option has an entry in this
     * object otherwise {@code =} is used as the separator.
     *
     * @param loader
     * @param out
     * @param namePrefix
     */
    public void printHelp(Iterable<OptionDescriptors> loader, PrintStream out, String namePrefix) {
        SortedMap<String, OptionDescriptor> sortedOptions = new TreeMap<>();
        for (OptionDescriptors opts : loader) {
            for (OptionDescriptor desc : opts) {
                String name = desc.getName();
                OptionDescriptor existing = sortedOptions.put(name, desc);
                assert existing == null || existing == desc : "Option named \"" + name + "\" has multiple definitions: " + existing.getLocation() + " and " + desc.getLocation();
            }
        }
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            OptionDescriptor desc = e.getValue();
            Object value = desc.getOptionKey().getValue(this);
            if (value instanceof String) {
                value = '"' + String.valueOf(value) + '"';
            }

            String name = namePrefix + e.getKey();
            String assign = containsKey(desc.getOptionKey()) ? ":=" : "=";
            String typeName = desc.getOptionKey() instanceof EnumOptionKey ? "String" : desc.getOptionValueType().getSimpleName();
            String linePrefix = String.format("%s %s %s ", name, assign, value);
            int typeStartPos = PROPERTY_LINE_WIDTH - typeName.length();
            int linePad = typeStartPos - linePrefix.length();
            if (linePad > 0) {
                out.printf("%s%-" + linePad + "s[%s]%n", linePrefix, "", typeName);
            } else {
                out.printf("%s[%s]%n", linePrefix, typeName);
            }

            List<String> helpLines;
            String help = desc.getHelp();
            if (help.length() != 0) {
                helpLines = wrap(help, PROPERTY_LINE_WIDTH - PROPERTY_HELP_INDENT);
                helpLines.addAll(desc.getExtraHelp());
            } else {
                helpLines = desc.getExtraHelp();
            }
            for (String line : helpLines) {
                out.printf("%" + PROPERTY_HELP_INDENT + "s%s%n", "", line);
            }
        }
    }
}
