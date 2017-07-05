/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.options.OptionValue.PROFILE_OPTIONVALUE_PROPERTY_NAME;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.ServiceLoader;

import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotJVMCICompilerFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.vm.ci.services.Services;

public final class HotSpotGraalCompilerFactory extends HotSpotJVMCICompilerFactory {

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

    private static MethodFilter[] graalCompileOnlyFilter;

    /**
     * Gets the system property assignment that would set the current value for a given option.
     */
    public static String asSystemPropertySetting(OptionValue<?> value) {
        return GRAAL_OPTION_PROPERTY_PREFIX + value.getName() + "=" + value.getValue();
    }

    private final HotSpotGraalJVMCIServiceLocator locator;

    HotSpotGraalCompilerFactory(HotSpotGraalJVMCIServiceLocator locator) {
        this.locator = locator;
    }

    @Override
    public String getCompilerName() {
        return "graal";
    }

    @Override
    public void onSelection() {
        initializeOptions();
        JVMCIVersionCheck.check(false);
    }

    @Override
    public void printProperties(PrintStream out) {
        ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
        out.println("[Graal properties]");
        OptionsParser.printFlags(loader, out, allOptionsSettings.keySet(), GRAAL_OPTION_PROPERTY_PREFIX);
    }

    static class Options {

        // @formatter:off
        @Option(help = "In tiered mode compile Graal and JVMCI using optimized first tier code.", type = OptionType.Expert)
        public static final OptionValue<Boolean> CompileGraalWithC1Only = new OptionValue<>(true);

        @Option(help = "Hook into VM-level mechanism for denoting compilations to be performed in first tier.", type = OptionType.Expert)
        public static final OptionValue<Boolean> UseTrivialPrefixes = new OptionValue<>(false);

        @Option(help = "A method filter selecting what should be compiled by Graal.  All other requests will be reduced to CompilationLevel.Simple.", type = OptionType.Expert)
        public static final OptionValue<String> GraalCompileOnly = new OptionValue<>(null);
        // @formatter:on

    }

    private static Map<String, String> allOptionsSettings;

    /**
     * Initializes options if they haven't already been initialized.
     *
     * Initialization means first parsing the options in the file denoted by the
     * {@code VM.getSavedProperty(String) saved} system property named
     * {@value HotSpotGraalCompilerFactory#GRAAL_OPTIONS_FILE_PROPERTY_NAME} if the file exists
     * followed by parsing the options encoded in saved system properties whose names start with
     * {@value #GRAAL_OPTION_PROPERTY_PREFIX}. Key/value pairs are parsed from the aforementioned
     * file with {@link Properties#load(java.io.Reader)}.
     */
    @SuppressWarnings("try")
    private static synchronized void initializeOptions() {
        if (allOptionsSettings == null) {
            try (InitTimer t = timer("InitializeOptions")) {
                ServiceLoader<OptionDescriptors> loader = ServiceLoader.load(OptionDescriptors.class, OptionDescriptors.class.getClassLoader());
                Map<String, String> savedProps = Services.getSavedProperties();
                String optionsFile = savedProps.get(GRAAL_OPTIONS_FILE_PROPERTY_NAME);

                if (optionsFile != null) {
                    File graalOptions = new File(optionsFile);
                    if (graalOptions.exists()) {
                        try (FileReader fr = new FileReader(graalOptions)) {
                            Properties props = new Properties();
                            props.load(fr);
                            Map<String, String> optionSettings = new HashMap<>();
                            for (Map.Entry<Object, Object> e : props.entrySet()) {
                                optionSettings.put((String) e.getKey(), (String) e.getValue());
                            }
                            try {
                                OptionsParser.parseOptions(optionSettings, null, loader);
                                if (allOptionsSettings == null) {
                                    allOptionsSettings = new HashMap<>(optionSettings);
                                } else {
                                    allOptionsSettings.putAll(optionSettings);
                                }
                            } catch (Throwable e) {
                                throw new InternalError("Error parsing an option from " + graalOptions, e);
                            }
                        } catch (IOException e) {
                            throw new InternalError("Error reading " + graalOptions, e);
                        }
                    }
                }

                Map<String, String> optionSettings = new HashMap<>();
                for (Entry<String, String> e : savedProps.entrySet()) {
                    String name = e.getKey();
                    if (name.startsWith(GRAAL_OPTION_PROPERTY_PREFIX)) {
                        if (name.equals("graal.PrintFlags") || name.equals("graal.ShowFlags")) {
                            System.err.println("The " + name + " option has been removed and will be ignored. Use -XX:+JVMCIPrintProperties instead.");
                        } else if (name.equals(GRAAL_OPTIONS_FILE_PROPERTY_NAME) || name.equals(GRAAL_VERSION_PROPERTY_NAME) || name.equals(PROFILE_OPTIONVALUE_PROPERTY_NAME)) {
                            // Ignore well known properties that do not denote an option
                        } else {
                            String value = e.getValue();
                            optionSettings.put(name.substring(GRAAL_OPTION_PROPERTY_PREFIX.length()), value);
                        }
                    }
                }

                OptionsParser.parseOptions(optionSettings, null, loader);

                if (allOptionsSettings == null) {
                    allOptionsSettings = optionSettings;
                } else {
                    allOptionsSettings.putAll(optionSettings);
                }

                if (Options.GraalCompileOnly.getValue() != null) {
                    graalCompileOnlyFilter = MethodFilter.parse(Options.GraalCompileOnly.getValue());
                    if (graalCompileOnlyFilter.length == 0) {
                        graalCompileOnlyFilter = null;
                    }
                }
                if (graalCompileOnlyFilter != null || !Options.UseTrivialPrefixes.getValue()) {
                    /*
                     * Exercise this code path early to encourage loading now. This doesn't solve
                     * problem of deadlock during class loading but seems to eliminate it in
                     * practice.
                     */
                    adjustCompilationLevelInternal(Object.class, "hashCode", "()I", CompilationLevel.FullOptimization);
                    adjustCompilationLevelInternal(Object.class, "hashCode", "()I", CompilationLevel.Simple);
                }
            }
        }
    }

    @Override
    public HotSpotGraalCompiler createCompiler(JVMCIRuntime runtime) {
        HotSpotGraalCompiler compiler = createCompiler(runtime, CompilerConfigurationFactory.selectFactory(null));
        // Only the HotSpotGraalRuntime associated with the compiler created via
        // jdk.vm.ci.runtime.JVMCIRuntime.getCompiler() is registered for receiving
        // VM events.
        locator.onCompilerCreation(compiler);
        return compiler;
    }

    /**
     * Creates a new {@link HotSpotGraalRuntime} object and a new {@link HotSpotGraalCompiler} and
     * returns the latter.
     *
     * @param runtime the JVMCI runtime on which the {@link HotSpotGraalRuntime} is built
     * @param compilerConfigurationFactory factory for the {@link CompilerConfiguration}
     */
    @SuppressWarnings("try")
    public static HotSpotGraalCompiler createCompiler(JVMCIRuntime runtime, CompilerConfigurationFactory compilerConfigurationFactory) {
        HotSpotJVMCIRuntime jvmciRuntime = (HotSpotJVMCIRuntime) runtime;
        try (InitTimer t = timer("HotSpotGraalRuntime.<init>")) {
            HotSpotGraalRuntime graalRuntime = new HotSpotGraalRuntime(jvmciRuntime, compilerConfigurationFactory);
            return new HotSpotGraalCompiler(jvmciRuntime, graalRuntime);
        }
    }

    @Override
    public String[] getTrivialPrefixes() {
        if (Options.UseTrivialPrefixes.getValue()) {
            if (Options.CompileGraalWithC1Only.getValue()) {
                return new String[]{"jdk/vm/ci", "org/graalvm/compiler", "com/oracle/graal"};
            }
        }
        return null;
    }

    @Override
    public CompilationLevelAdjustment getCompilationLevelAdjustment() {
        if (graalCompileOnlyFilter != null) {
            return CompilationLevelAdjustment.ByFullSignature;
        }
        if (!Options.UseTrivialPrefixes.getValue()) {
            if (Options.CompileGraalWithC1Only.getValue()) {
                // We only decide using the class declaring the method
                // so no need to have the method name and signature
                // symbols converted to a String.
                return CompilationLevelAdjustment.ByHolder;
            }
        }
        return CompilationLevelAdjustment.None;
    }

    @Override
    public CompilationLevel adjustCompilationLevel(Class<?> declaringClass, String name, String signature, boolean isOsr, CompilationLevel level) {
        return adjustCompilationLevelInternal(declaringClass, name, signature, level);
    }

    /*
     * This method is static so it can be exercised during initialization.
     */
    private static CompilationLevel adjustCompilationLevelInternal(Class<?> declaringClass, String name, String signature, CompilationLevel level) {
        if (graalCompileOnlyFilter != null) {
            if (level == CompilationLevel.FullOptimization) {
                String declaringClassName = declaringClass.getName();
                HotSpotSignature sig = null;
                for (MethodFilter filter : graalCompileOnlyFilter) {
                    if (filter.hasSignature() && sig == null) {
                        sig = new HotSpotSignature(HotSpotJVMCIRuntime.runtime(), signature);
                    }
                    if (filter.matches(declaringClassName, name, sig)) {
                        return level;
                    }
                }
                return CompilationLevel.Simple;
            }
        }
        if (level.ordinal() > CompilationLevel.Simple.ordinal()) {
            String declaringClassName = declaringClass.getName();
            if (declaringClassName.startsWith("jdk.vm.ci") || declaringClassName.startsWith("org.graalvm.compiler") || declaringClassName.startsWith("com.oracle.graal")) {
                return CompilationLevel.Simple;
            }
        }
        return level;
    }
}
