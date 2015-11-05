/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.options;

import static jdk.vm.ci.inittimer.InitTimer.*;

import java.io.*;
import java.util.*;

import jdk.vm.ci.inittimer.*;

/**
 * This class contains methods for parsing JVMCI options and matching them against a set of
 * {@link OptionDescriptors}. The {@link OptionDescriptors} are loaded from JVMCI jars, either
 * {@linkplain JVMCIJarsOptionDescriptorsProvider directly} or via a {@link ServiceLoader}.
 */
public class OptionsParser {

    private static final OptionValue<Boolean> PrintFlags = new OptionValue<>(false);

    /**
     * A service for looking up {@link OptionDescriptor}s.
     */
    public interface OptionDescriptorsProvider {
        /**
         * Gets the {@link OptionDescriptor} matching a given option {@linkplain Option#name() name}
         * or null if no option of that name is provided by this object.
         */
        OptionDescriptor get(String name);
    }

    public interface OptionConsumer {
        void set(OptionDescriptor desc, Object value);
    }

    /**
     * Parses the options in {@code <jre>/lib/jvmci/options} if {@code parseOptionsFile == true} and
     * the file exists followed by the JVMCI options in {@code options} if {@code options != null}.
     *
     * Called from VM. This method has an object return type to allow it to be called with a VM
     * utility function used to call other static initialization methods.
     *
     * @param options JVMCI options as serialized (name, value) pairs
     * @param parseOptionsFile specifies whether to look for and parse
     *            {@code <jre>/lib/jvmci/options}
     */
    @SuppressWarnings("try")
    public static Boolean parseOptionsFromVM(String[] options, boolean parseOptionsFile) {
        try (InitTimer t = timer("ParseOptions")) {
            JVMCIJarsOptionDescriptorsProvider odp = new JVMCIJarsOptionDescriptorsProvider();

            if (parseOptionsFile) {
                File javaHome = new File(System.getProperty("java.home"));
                File lib = new File(javaHome, "lib");
                File jvmci = new File(lib, "jvmci");
                File jvmciOptions = new File(jvmci, "options");
                if (jvmciOptions.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(jvmciOptions))) {
                        String optionSetting = null;
                        int lineNo = 1;
                        while ((optionSetting = br.readLine()) != null) {
                            if (!optionSetting.isEmpty() && optionSetting.charAt(0) != '#') {
                                try {
                                    parseOptionSetting(optionSetting, null, odp);
                                } catch (Throwable e) {
                                    throw new InternalError("Error parsing " + jvmciOptions + ", line " + lineNo, e);
                                }
                            }
                            lineNo++;
                        }
                    } catch (IOException e) {
                        throw new InternalError("Error reading " + jvmciOptions, e);
                    }
                }
            }

            if (options != null) {
                assert options.length % 2 == 0;
                for (int i = 0; i < options.length / 2; i++) {
                    String name = options[i * 2];
                    String value = options[i * 2 + 1];
                    parseOption(OptionsLoader.options, name, value, null, odp);
                }
            }
        }
        return Boolean.TRUE;
    }

    /**
     * Parses a given option setting.
     *
     * @param optionSetting a string matching the pattern {@code <name>=<value>}
     * @param setter the object to notify of the parsed option and value
     */
    public static void parseOptionSetting(String optionSetting, OptionConsumer setter, OptionDescriptorsProvider odp) {
        int eqIndex = optionSetting.indexOf('=');
        if (eqIndex == -1) {
            throw new InternalError("Option setting has does not match the pattern <name>=<value>: " + optionSetting);
        }
        String name = optionSetting.substring(0, eqIndex);
        String value = optionSetting.substring(eqIndex + 1);
        parseOption(OptionsLoader.options, name, value, setter, odp);
    }

    /**
     * Parses a given option name and value.
     *
     * @param options
     * @param name the option name
     * @param valueString the option value as a string
     * @param setter the object to notify of the parsed option and value
     * @param odp
     *
     * @throws IllegalArgumentException if there's a problem parsing {@code option}
     */
    public static void parseOption(SortedMap<String, OptionDescriptor> options, String name, String valueString, OptionConsumer setter, OptionDescriptorsProvider odp) {
        OptionDescriptor desc = options.get(name);
        if (desc == null && odp != null) {
            desc = odp.get(name);
        }
        if (desc == null && name.equals("PrintFlags")) {
            desc = OptionDescriptor.create("PrintFlags", Boolean.class, "Prints all JVMCI flags and exits", OptionsParser.class, "PrintFlags", PrintFlags);
        }
        if (desc == null) {
            List<OptionDescriptor> matches = fuzzyMatch(options, name);
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

        Class<?> optionType = desc.getType();
        Object value;
        if (optionType == Boolean.class) {
            if ("true".equals(valueString)) {
                value = Boolean.TRUE;
            } else if ("false".equals(valueString)) {
                value = Boolean.FALSE;
            } else {
                throw new IllegalArgumentException("Boolean option '" + name + "' must have value \"true\" or \"false\", not \"" + valueString + "\"");
            }
        } else if (optionType == Float.class) {
            value = Float.parseFloat(valueString);
        } else if (optionType == Double.class) {
            value = Double.parseDouble(valueString);
        } else if (optionType == Integer.class) {
            value = Integer.valueOf((int) parseLong(valueString));
        } else if (optionType == Long.class) {
            value = Long.valueOf(parseLong(valueString));
        } else if (optionType == String.class) {
            value = valueString;
        } else {
            throw new IllegalArgumentException("Wrong value for option '" + name + "'");
        }
        if (setter == null) {
            desc.getOptionValue().setValue(value);
        } else {
            setter.set(desc, value);
        }

        if (PrintFlags.getValue()) {
            printFlags(options, "JVMCI", System.out);
            System.exit(0);
        }
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
     * Wraps some given text to one or more lines of a given maximum width.
     *
     * @param text text to wrap
     * @param width maximum width of an output line, exception for words in {@code text} longer than
     *            this value
     * @return {@code text} broken into lines
     */
    private static List<String> wrap(String text, int width) {
        List<String> lines = Collections.singletonList(text);
        if (text.length() > width) {
            String[] chunks = text.split("\\s+");
            lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String chunk : chunks) {
                if (line.length() + chunk.length() > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                if (line.length() != 0) {
                    line.append(' ');
                }
                String[] embeddedLines = chunk.split("%n", -2);
                if (embeddedLines.length == 1) {
                    line.append(chunk);
                } else {
                    for (int i = 0; i < embeddedLines.length; i++) {
                        line.append(embeddedLines[i]);
                        if (i < embeddedLines.length - 1) {
                            lines.add(line.toString());
                            line.setLength(0);
                        }
                    }
                }
            }
            if (line.length() != 0) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    public static void printFlags(SortedMap<String, OptionDescriptor> sortedOptions, String prefix, PrintStream out) {
        out.println("[List of " + prefix + " options]");
        for (Map.Entry<String, OptionDescriptor> e : sortedOptions.entrySet()) {
            e.getKey();
            OptionDescriptor desc = e.getValue();
            Object value = desc.getOptionValue().getValue();
            List<String> helpLines = wrap(desc.getHelp(), 70);
            out.println(String.format("%9s %-40s = %-14s %s", desc.getType().getSimpleName(), e.getKey(), value, helpLines.get(0)));
            for (int i = 1; i < helpLines.size(); i++) {
                out.println(String.format("%67s %s", " ", helpLines.get(i)));
            }
        }
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
    private static List<OptionDescriptor> fuzzyMatch(SortedMap<String, OptionDescriptor> options, String optionName) {
        List<OptionDescriptor> matches = new ArrayList<>();
        for (Map.Entry<String, OptionDescriptor> e : options.entrySet()) {
            float score = stringSimiliarity(e.getKey(), optionName);
            if (score >= FUZZY_MATCH_THRESHOLD) {
                matches.add(e.getValue());
            }
        }
        return matches;
    }
}
