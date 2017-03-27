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
import static org.graalvm.compiler.hotspot.HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX;

import java.io.PrintStream;

import org.graalvm.compiler.debug.MethodFilter;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;

import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotJVMCICompilerFactory;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.vm.ci.services.Services;

public final class HotSpotGraalCompilerFactory extends HotSpotJVMCICompilerFactory {

    private static MethodFilter[] graalCompileOnlyFilter;

    private final HotSpotGraalJVMCIServiceLocator locator;

    HotSpotGraalCompilerFactory(HotSpotGraalJVMCIServiceLocator locator) {
        this.locator = locator;
    }

    @Override
    public String getCompilerName() {
        return "graal";
    }

    /**
     * Initialized when this factory is {@linkplain #onSelection() selected}.
     */
    private OptionValues options;

    @Override
    public void onSelection() {
        JVMCIVersionCheck.check(false);
        assert options == null : "cannot select " + getClass() + " service more than once";
        options = HotSpotGraalOptionValues.HOTSPOT_OPTIONS;
        initializeGraalCompileOnlyFilter(options);
        if (graalCompileOnlyFilter != null || !Options.UseTrivialPrefixes.getValue(options)) {
            /*
             * Exercise this code path early to encourage loading now. This doesn't solve problem of
             * deadlock during class loading but seems to eliminate it in practice.
             */
            adjustCompilationLevelInternal(Object.class, "hashCode", "()I", CompilationLevel.FullOptimization);
            adjustCompilationLevelInternal(Object.class, "hashCode", "()I", CompilationLevel.Simple);
        }
    }

    private static void initializeGraalCompileOnlyFilter(OptionValues options) {
        String optionValue = Options.GraalCompileOnly.getValue(options);
        if (optionValue != null) {
            MethodFilter[] filter = MethodFilter.parse(optionValue);
            if (filter.length == 0) {
                filter = null;
            }
            graalCompileOnlyFilter = filter;
        }
    }

    @Override
    public void printProperties(PrintStream out) {
        out.println("[Graal properties]");
        options.printHelp(OptionsParser.getOptionsLoader(), out, GRAAL_OPTION_PROPERTY_PREFIX);
    }

    static class Options {

        // @formatter:off
        @Option(help = "In tiered mode compile Graal and JVMCI using optimized first tier code.", type = OptionType.Expert)
        public static final OptionKey<Boolean> CompileGraalWithC1Only = new OptionKey<>(true);

        @Option(help = "Hook into VM-level mechanism for denoting compilations to be performed in first tier.", type = OptionType.Expert)
        public static final OptionKey<Boolean> UseTrivialPrefixes = new OptionKey<>(false);

        @Option(help = "A method filter selecting what should be compiled by Graal.  All other requests will be reduced to CompilationLevel.Simple.", type = OptionType.Expert)
        public static final OptionKey<String> GraalCompileOnly = new OptionKey<>(null);
        // @formatter:on

    }

    @Override
    public HotSpotGraalCompiler createCompiler(JVMCIRuntime runtime) {
        HotSpotGraalCompiler compiler = createCompiler(runtime, options, CompilerConfigurationFactory.selectFactory(null, options));
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
    public static HotSpotGraalCompiler createCompiler(JVMCIRuntime runtime, OptionValues options, CompilerConfigurationFactory compilerConfigurationFactory) {
        HotSpotJVMCIRuntime jvmciRuntime = (HotSpotJVMCIRuntime) runtime;
        try (InitTimer t = timer("HotSpotGraalRuntime.<init>")) {
            HotSpotGraalRuntime graalRuntime = new HotSpotGraalRuntime(jvmciRuntime, compilerConfigurationFactory, options);
            return new HotSpotGraalCompiler(jvmciRuntime, graalRuntime);
        }
    }

    @Override
    public String[] getTrivialPrefixes() {
        if (Options.UseTrivialPrefixes.getValue(options)) {
            if (Options.CompileGraalWithC1Only.getValue(options)) {
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
        if (!Options.UseTrivialPrefixes.getValue(options)) {
            if (Options.CompileGraalWithC1Only.getValue(options)) {
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

    static {
        // Fail-fast detection for package renaming to guard use of package
        // prefixes in adjustCompilationLevelInternal.
        assert Services.class.getName().equals("jdk.vm.ci.services.Services");
        assert HotSpotGraalCompilerFactory.class.getName().equals("org.graalvm.compiler.hotspot.HotSpotGraalCompilerFactory");
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
