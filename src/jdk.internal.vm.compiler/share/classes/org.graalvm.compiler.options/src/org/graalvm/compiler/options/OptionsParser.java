/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.List;
import java.util.ServiceLoader;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.MapCursor;

/**
 * This class contains methods for parsing Graal options and matching them against a set of
 * {@link OptionDescriptors}. The {@link OptionDescriptors} are loaded via a {@link ServiceLoader}.
 */
public class OptionsParser {

    private static volatile List<OptionDescriptors> cachedOptionDescriptors;

    /**
     * Gets an iterable of available {@link OptionDescriptors}.
     */
    public static Iterable<OptionDescriptors> getOptionsLoader() {
        if (IS_IN_NATIVE_IMAGE || cachedOptionDescriptors != null) {
            return cachedOptionDescriptors;
        }
        boolean java8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;
        ClassLoader loader;
        if (java8OrEarlier) {
            // On JDK 8, Graal and its extensions are loaded by same class loader.
            loader = OptionDescriptors.class.getClassLoader();
        } else {
            /*
             * The Graal module (i.e., jdk.internal.vm.compiler) is loaded by the platform class
             * loader as of JDK 9. Modules that depend on and extend Graal are loaded by the app
             * class loader. As such, we need to start the provider search at the app class loader
             * instead of the platform class loader.
             */
            loader = ClassLoader.getSystemClassLoader();
        }
        Iterable<OptionDescriptors> result = ServiceLoader.load(OptionDescriptors.class, loader);
        if (IS_BUILDING_NATIVE_IMAGE) {
            ArrayList<OptionDescriptors> optionDescriptors = new ArrayList<>();
            for (OptionDescriptors descriptors : result) {
                optionDescriptors.add(descriptors);
            }
            OptionsParser.cachedOptionDescriptors = optionDescriptors;
        }
        return result;
    }

    /**
     * Parses a map representing assignments of values to options.
     *
     * @param optionSettings option settings (i.e., assignments of values to options)
     * @param values the object in which to store the parsed values
     * @param loader source of the available {@link OptionDescriptors}
     * @throws IllegalArgumentException if there's a problem parsing {@code option}
     */
    public static void parseOptions(EconomicMap<String, String> optionSettings, EconomicMap<OptionKey<?>, Object> values, Iterable<OptionDescriptors> loader) {
        if (optionSettings != null && !optionSettings.isEmpty()) {
            MapCursor<String, String> cursor = optionSettings.getEntries();
            while (cursor.advance()) {
                parseOption(cursor.getKey(), cursor.getValue(), values, loader);
            }
        }
    }

    /**
     * Parses a given option setting string and adds the parsed key and value {@code dst}.
     *
     * @param optionSetting a string matching the pattern {@code <name>=<value>}
     */
    public static void parseOptionSettingTo(String optionSetting, EconomicMap<String, String> dst) {
        int eqIndex = optionSetting.indexOf('=');
        if (eqIndex == -1) {
            throw new InternalError("Option setting has does not match the pattern <name>=<value>: " + optionSetting);
        }
        dst.put(optionSetting.substring(0, eqIndex), optionSetting.substring(eqIndex + 1));
    }

    /**
     * Looks up an {@link OptionDescriptor} based on a given name.
     *
     * @param loader source of the available {@link OptionDescriptors}
     * @param name the name of the option to look up
     * @return the {@link OptionDescriptor} whose name equals {@code name} or null if not such
     *         descriptor is available
     */
    private static OptionDescriptor lookup(Iterable<OptionDescriptors> loader, String name) {
        for (OptionDescriptors optionDescriptors : loader) {
            OptionDescriptor desc = optionDescriptors.get(name);
            if (desc != null) {
                return desc;
            }
        }
        return null;
    }

    /**
     * Parses a given option name and value.
     *
     * @param name the option name
     * @param uncheckedValue the unchecked value for the option
     * @param values the object in which to store the parsed option and value
     * @param loader source of the available {@link OptionDescriptors}
     * @throws IllegalArgumentException if there's a problem parsing {@code option}
     */
    public static void parseOption(String name, Object uncheckedValue, EconomicMap<OptionKey<?>, Object> values, Iterable<OptionDescriptors> loader) {

        OptionDescriptor desc = lookup(loader, name);
        if (desc == null) {
            List<OptionDescriptor> matches = fuzzyMatch(loader, name);
            Formatter msg = new Formatter();
            msg.format("Could not find option %s", name);
            if (!matches.isEmpty()) {
                msg.format("%nDid you mean one of the following?");
                for (OptionDescriptor match : matches) {
                    msg.format("%n    %s=<value>", match.getName());
                }
            }
            throw new IllegalArgumentException(msg.toString());
        }

        Class<?> optionType = desc.getOptionValueType();
        Object value;
        if (!(uncheckedValue instanceof String)) {
            if (optionType != uncheckedValue.getClass()) {
                String type = optionType.getSimpleName();
                throw new IllegalArgumentException(type + " option '" + name + "' must have " + type + " value, not " + uncheckedValue.getClass() + " [toString: " + uncheckedValue + "]");
            }
            value = uncheckedValue;
        } else {
            String valueString = (String) uncheckedValue;
            if (optionType == Boolean.class) {
                if ("true".equals(valueString)) {
                    value = Boolean.TRUE;
                } else if ("false".equals(valueString)) {
                    value = Boolean.FALSE;
                } else {
                    throw new IllegalArgumentException("Boolean option '" + name + "' must have value \"true\" or \"false\", not \"" + uncheckedValue + "\"");
                }
            } else if (optionType == String.class) {
                value = valueString;
            } else if (Enum.class.isAssignableFrom(optionType)) {
                value = ((EnumOptionKey<?>) desc.getOptionKey()).valueOf(valueString);
            } else {
                if (valueString.isEmpty()) {
                    throw new IllegalArgumentException("Non empty value required for option '" + name + "'");
                }
                try {
                    if (optionType == Float.class) {
                        value = Float.parseFloat(valueString);
                    } else if (optionType == Double.class) {
                        value = Double.parseDouble(valueString);
                    } else if (optionType == Integer.class) {
                        value = Integer.valueOf((int) parseLong(valueString));
                    } else if (optionType == Long.class) {
                        value = Long.valueOf(parseLong(valueString));
                    } else {
                        throw new IllegalArgumentException("Wrong value for option '" + name + "'");
                    }
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Value for option '" + name + "' has invalid number format: " + valueString);
                }
            }
        }

        desc.getOptionKey().update(values, value);
    }

    private static long parseLong(String v) {
        String valueString = v.toLowerCase();
        long scale = 1;
        if (valueString.endsWith("k")) {
            scale = 1024L;
        } else if (valueString.endsWith("m")) {
            scale = 1024L * 1024L;
        } else if (valueString.endsWith("g")) {
            scale = 1024L * 1024L * 1024L;
        } else if (valueString.endsWith("t")) {
            scale = 1024L * 1024L * 1024L * 1024L;
        }

        if (scale != 1) {
            /* Remove trailing scale character. */
            valueString = valueString.substring(0, valueString.length() - 1);
        }

        return Long.parseLong(valueString) * scale;
    }

    /**
     * Compute string similarity based on Dice's coefficient.
     *
     * Ported from str_similar() in globals.cpp.
     */
    static float stringSimiliarity(String str1, String str2) {
        int hit = 0;
        for (int i = 0; i < str1.length() - 1; ++i) {
            for (int j = 0; j < str2.length() - 1; ++j) {
                if ((str1.charAt(i) == str2.charAt(j)) && (str1.charAt(i + 1) == str2.charAt(j + 1))) {
                    ++hit;
                    break;
                }
            }
        }
        return 2.0f * hit / (str1.length() + str2.length());
    }

    private static final float FUZZY_MATCH_THRESHOLD = 0.7F;

    /**
     * Returns the set of options that fuzzy match a given option name.
     */
    private static List<OptionDescriptor> fuzzyMatch(Iterable<OptionDescriptors> loader, String optionName) {
        List<OptionDescriptor> matches = new ArrayList<>();
        for (OptionDescriptors options : loader) {
            collectFuzzyMatches(options, optionName, matches);
        }
        return matches;
    }

    /**
     * Collects the set of options that fuzzy match a given option name. String similarity for fuzzy
     * matching is based on Dice's coefficient.
     *
     * @param toSearch the set of option descriptors to search
     * @param name the option name to search for
     * @param matches the collection to which fuzzy matches of {@code name} will be added
     * @return whether any fuzzy matches were found
     */
    public static boolean collectFuzzyMatches(Iterable<OptionDescriptor> toSearch, String name, Collection<OptionDescriptor> matches) {
        boolean found = false;
        for (OptionDescriptor option : toSearch) {
            float score = stringSimiliarity(option.getName(), name);
            if (score >= FUZZY_MATCH_THRESHOLD) {
                found = true;
                matches.add(option);
            }
        }
        return found;
    }
}
