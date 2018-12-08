/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.runtime.GraalRuntime;
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

public final class HotSpotGraalCompilerFactory extends HotSpotJVMCICompilerFactory {

    private static MethodFilter[] graalCompileOnlyFilter;
    private static boolean compileGraalWithC1Only;

    private IsGraalPredicate isGraalPredicate;

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
        initializeGraalCompilePolicyFields(options);
        isGraalPredicate = compileGraalWithC1Only ? new IsGraalPredicate() : null;
        /*
         * Exercise this code path early to encourage loading now. This doesn't solve problem of
         * deadlock during class loading but seems to eliminate it in practice.
         */
        adjustCompilationLevelInternal(Object.class, "hashCode", "()I", CompilationLevel.FullOptimization);
        adjustCompilationLevelInternal(Object.class, "hashCode", "()I", CompilationLevel.Simple);
    }

    private static void initializeGraalCompilePolicyFields(OptionValues options) {
        compileGraalWithC1Only = Options.CompileGraalWithC1Only.getValue(options);
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

        @Option(help = "A filter applied to a method the VM has selected for compilation by Graal. " +
                       "A method not matching the filter is redirected to a lower tier compiler. " +
                       "The filter format is the same as for the MethodFilter option.", type = OptionType.Expert)
        public static final OptionKey<String> GraalCompileOnly = new OptionKey<>(null);
        // @formatter:on

    }

    @Override
    public HotSpotGraalCompiler createCompiler(JVMCIRuntime runtime) {
        CompilerConfigurationFactory factory = CompilerConfigurationFactory.selectFactory(null, options);
        if (isGraalPredicate != null) {
            isGraalPredicate.onCompilerConfigurationFactorySelection(factory);
        }
        HotSpotGraalCompiler compiler = createCompiler("VM", runtime, options, factory);
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
     * @param runtimeNameQualifier a qualifier to be added to the {@linkplain GraalRuntime#getName()
     *            name} of the {@linkplain HotSpotGraalCompiler#getGraalRuntime() runtime} created
     *            by this method
     * @param runtime the JVMCI runtime on which the {@link HotSpotGraalRuntime} is built
     * @param compilerConfigurationFactory factory for the {@link CompilerConfiguration}
     */
    @SuppressWarnings("try")
    public static HotSpotGraalCompiler createCompiler(String runtimeNameQualifier, JVMCIRuntime runtime, OptionValues options, CompilerConfigurationFactory compilerConfigurationFactory) {
        HotSpotJVMCIRuntime jvmciRuntime = (HotSpotJVMCIRuntime) runtime;
        try (InitTimer t = timer("HotSpotGraalRuntime.<init>")) {
            HotSpotGraalRuntime graalRuntime = new HotSpotGraalRuntime(runtimeNameQualifier, jvmciRuntime, compilerConfigurationFactory, options);
            return new HotSpotGraalCompiler(jvmciRuntime, graalRuntime, graalRuntime.getOptions());
        }
    }

    @Override
    public CompilationLevelAdjustment getCompilationLevelAdjustment() {
        if (graalCompileOnlyFilter != null) {
            return CompilationLevelAdjustment.ByFullSignature;
        }
        if (compileGraalWithC1Only) {
            // We only decide using the class declaring the method
            // so no need to have the method name and signature
            // symbols converted to a String.
            return CompilationLevelAdjustment.ByHolder;
        }
        return CompilationLevelAdjustment.None;
    }

    @Override
    public CompilationLevel adjustCompilationLevel(Object declaringClassObject, String name, String signature, boolean isOsr, CompilationLevel level) {
        if (declaringClassObject instanceof String) {
            // This must be SVM mode in which case only GraalCompileC1Only matters since Graal and
            // JVMCI are already compiled.
            return checkGraalCompileOnlyFilter((String) declaringClassObject, name, signature, level);
        }
        Class<?> declaringClass = (Class<?>) declaringClassObject;
        return adjustCompilationLevelInternal(declaringClass, name, signature, level);
    }

    static {
        // Fail-fast detection for package renaming to guard use of package
        // prefixes in adjustCompilationLevelInternal.
        assert jdk.vm.ci.services.Services.class.getName().equals("jdk.vm.ci.services.Services");
        assert HotSpotGraalCompilerFactory.class.getName().equals("org.graalvm.compiler.hotspot.HotSpotGraalCompilerFactory");
    }

    private CompilationLevel adjustCompilationLevelInternal(Class<?> declaringClass, String name, String signature, CompilationLevel level) {
        if (compileGraalWithC1Only) {
            if (level.ordinal() > CompilationLevel.Simple.ordinal()) {
                if (isGraalPredicate.apply(declaringClass)) {
                    return CompilationLevel.Simple;
                }
            }
        }
        return checkGraalCompileOnlyFilter(declaringClass.getName(), name, signature, level);
    }

    public static CompilationLevel checkGraalCompileOnlyFilter(String declaringClassName, String name, String signature, CompilationLevel level) {
        if (graalCompileOnlyFilter != null) {
            if (level == CompilationLevel.FullOptimization) {
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
        return level;
    }
}
