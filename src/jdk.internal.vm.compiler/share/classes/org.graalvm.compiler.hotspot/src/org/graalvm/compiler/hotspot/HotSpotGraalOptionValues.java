/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import static jdk.vm.ci.common.InitTimer.timer;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import jdk.internal.vm.compiler.collections.EconomicMap;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.services.Services;

/**
 * The {@link #defaultOptions()} method returns the options values initialized in a HotSpot VM. The
 * values are set via system properties with the {@value #GRAAL_OPTION_PROPERTY_PREFIX} prefix.
 */
public class HotSpotGraalOptionValues {

    /**
     * The name of the system property specifying a file containing extra Graal option settings.
     */
    private static final String GRAAL_OPTIONS_FILE_PROPERTY_NAME = "graal.options.file";

    /**
     * The name of the system property specifying the Graal version.
     */
    private static final String GRAAL_VERSION_PROPERTY_NAME = "graal.version";

    /**
     * The prefix for system properties that correspond to {@link Option} annotated fields. A field
     * named {@code MyOption} will have its value set from a system property with the name
     * {@code GRAAL_OPTION_PROPERTY_PREFIX + "MyOption"}.
     */
    public static final String GRAAL_OPTION_PROPERTY_PREFIX = "graal.";

    /**
     * Gets the system property assignment that would set the current value for a given option.
     */
    public static String asSystemPropertySetting(OptionValues options, OptionKey<?> value) {
        return GRAAL_OPTION_PROPERTY_PREFIX + value.getName() + "=" + value.getValue(options);
    }

    @NativeImageReinitialize private static volatile OptionValues hotspotOptions;

    public static OptionValues defaultOptions() {
        OptionValues res = hotspotOptions;
        if (res == null) {
            synchronized (HotSpotGraalOptionValues.class) {
                res = hotspotOptions;
                if (res == null) {
                    res = initializeOptions();
                    hotspotOptions = res;
                }
            }
        }
        return res;
    }

    /**
     * Gets and parses options based on {@linkplain Services#getSavedProperties() saved system
     * properties}. The values for these options are initialized by parsing the file denoted by the
     * {@value #GRAAL_OPTIONS_FILE_PROPERTY_NAME} property followed by parsing the options encoded
     * in properties whose names start with {@value #GRAAL_OPTION_PROPERTY_PREFIX}. Key/value pairs
     * are parsed from the aforementioned file with {@link Properties#load(java.io.Reader)}.
     */
    @SuppressWarnings("try")
    public static EconomicMap<OptionKey<?>, Object> parseOptions() {
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        try (InitTimer t = timer("InitializeOptions")) {

            Iterable<OptionDescriptors> loader = OptionsParser.getOptionsLoader();
            Map<String, String> savedProps = jdk.vm.ci.services.Services.getSavedProperties();
            String optionsFile = savedProps.get(GRAAL_OPTIONS_FILE_PROPERTY_NAME);

            if (optionsFile != null) {
                File graalOptions = new File(optionsFile);
                if (graalOptions.exists()) {
                    try (FileReader fr = new FileReader(graalOptions)) {
                        Properties props = new Properties();
                        props.load(fr);
                        EconomicMap<String, String> optionSettings = EconomicMap.create();
                        for (Map.Entry<Object, Object> e : props.entrySet()) {
                            optionSettings.put((String) e.getKey(), (String) e.getValue());
                        }
                        try {
                            OptionsParser.parseOptions(optionSettings, values, loader);
                        } catch (Throwable e) {
                            throw new InternalError("Error parsing an option from " + graalOptions, e);
                        }
                    } catch (IOException e) {
                        throw new InternalError("Error reading " + graalOptions, e);
                    }
                }
            }

            EconomicMap<String, String> optionSettings = EconomicMap.create();
            for (Map.Entry<String, String> e : savedProps.entrySet()) {
                String name = e.getKey();
                if (name.startsWith(GRAAL_OPTION_PROPERTY_PREFIX)) {
                    if (name.equals("graal.PrintFlags") || name.equals("graal.ShowFlags")) {
                        System.err.println("The " + name + " option has been removed and will be ignored. Use -XX:+JVMCIPrintProperties instead.");
                    } else if (name.equals(GRAAL_OPTIONS_FILE_PROPERTY_NAME) || name.equals(GRAAL_VERSION_PROPERTY_NAME)) {
                        // Ignore well known properties that do not denote an option
                    } else {
                        String value = e.getValue();
                        optionSettings.put(name.substring(GRAAL_OPTION_PROPERTY_PREFIX.length()), value);
                    }
                }
            }

            OptionsParser.parseOptions(optionSettings, values, loader);
            return values;
        }
    }

    /**
     * Substituted by
     * {@code com.oracle.svm.graal.hotspot.libgraal.Target_org_graalvm_compiler_hotspot_HotSpotGraalOptionValues}
     * to update {@code com.oracle.svm.core.option.RuntimeOptionValues.singleton()} instead of
     * creating a new {@link OptionValues} object.
     */
    private static OptionValues initializeOptions() {
        return new OptionValues(parseOptions());
    }
}
