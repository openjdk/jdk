/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import static jdk.vm.ci.common.InitTimer.timer;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotBackendFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.meta.AddressLoweringHotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraalConstantFieldProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegisters;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.AddressLoweringPhase;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.amd64.AMD64GraphBuilderPlugins;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCIBackend;

@ServiceProvider(HotSpotBackendFactory.class)
public class AMD64HotSpotBackendFactory extends HotSpotBackendFactory {

    @Override
    public String getName() {
        return "community";
    }

    @Override
    public Class<? extends Architecture> getArchitecture() {
        return AMD64.class;
    }

    @Override
    @SuppressWarnings("try")
    public HotSpotBackend createBackend(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntime jvmciRuntime, HotSpotBackend host) {
        assert host == null;

        OptionValues options = graalRuntime.getOptions();
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        GraalHotSpotVMConfig config = graalRuntime.getVMConfig();
        HotSpotProviders providers;
        HotSpotRegistersProvider registers;
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmci.getCodeCache();
        TargetDescription target = codeCache.getTarget();
        HotSpotHostForeignCallsProvider foreignCalls;
        Value[] nativeABICallerSaveRegisters;
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();
        ConstantFieldProvider constantFieldProvider = new HotSpotGraalConstantFieldProvider(config, metaAccess);
        HotSpotLoweringProvider lowerer;
        HotSpotStampProvider stampProvider;
        HotSpotPlatformConfigurationProvider platformConfigurationProvider;
        HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider;
        HotSpotSnippetReflectionProvider snippetReflection;
        HotSpotReplacementsImpl replacements;
        HotSpotSuitesProvider suites;
        HotSpotWordTypes wordTypes;
        Plugins plugins;
        BytecodeProvider bytecodeProvider;
        try (InitTimer t = timer("create providers")) {
            try (InitTimer rt = timer("create HotSpotRegisters provider")) {
                registers = createRegisters();
            }
            try (InitTimer rt = timer("create NativeABICallerSaveRegisters")) {
                nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(config, codeCache.getRegisterConfig());
            }
            try (InitTimer rt = timer("create WordTypes")) {
                wordTypes = createWordTypes(metaAccess, target);
            }
            try (InitTimer rt = timer("create ForeignCalls provider")) {
                foreignCalls = createForeignCalls(jvmciRuntime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
            }
            try (InitTimer rt = timer("create platform configuration provider")) {
                platformConfigurationProvider = createConfigInfoProvider(config, metaAccess);
            }
            try (InitTimer rt = timer("create MetaAccessExtensionProvider")) {
                metaAccessExtensionProvider = createMetaAccessExtensionProvider();
            }
            try (InitTimer rt = timer("create stamp provider")) {
                stampProvider = createStampProvider();
            }
            try (InitTimer rt = timer("create Lowerer provider")) {
                lowerer = createLowerer(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, platformConfigurationProvider, metaAccessExtensionProvider, target);
            }

            Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider, platformConfigurationProvider,
                            metaAccessExtensionProvider);

            try (InitTimer rt = timer("create SnippetReflection provider")) {
                snippetReflection = createSnippetReflection(graalRuntime, constantReflection, wordTypes);
            }
            try (InitTimer rt = timer("create Bytecode provider")) {
                bytecodeProvider = createBytecodeProvider(metaAccess, snippetReflection);
            }
            try (InitTimer rt = timer("create Replacements provider")) {
                replacements = createReplacements(target, p, snippetReflection, bytecodeProvider);
            }
            try (InitTimer rt = timer("create GraphBuilderPhase plugins")) {
                plugins = createGraphBuilderPlugins(graalRuntime, compilerConfiguration, config, target, constantReflection, foreignCalls, metaAccess, snippetReflection, replacements, wordTypes,
                                options);
                replacements.setGraphBuilderPlugins(plugins);
            }
            try (InitTimer rt = timer("create Suites provider")) {
                suites = createSuites(config, graalRuntime, compilerConfiguration, plugins, registers, replacements, options);
            }
            providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, suites, registers,
                            snippetReflection, wordTypes, plugins, platformConfigurationProvider, metaAccessExtensionProvider);
            replacements.setProviders(providers);
            replacements.maybeInitializeEncoder(options);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(config, graalRuntime, providers);
        }
    }

    protected Plugins createGraphBuilderPlugins(HotSpotGraalRuntimeProvider graalRuntime,
                    CompilerConfiguration compilerConfiguration,
                    GraalHotSpotVMConfig config,
                    TargetDescription target,
                    HotSpotConstantReflectionProvider constantReflection,
                    HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotMetaAccessProvider metaAccess,
                    HotSpotSnippetReflectionProvider snippetReflection,
                    HotSpotReplacementsImpl replacements,
                    HotSpotWordTypes wordTypes,
                    OptionValues options) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(
                        graalRuntime,
                        compilerConfiguration,
                        config,
                        wordTypes,
                        metaAccess,
                        constantReflection,
                        snippetReflection,
                        foreignCalls,
                        replacements,
                        options,
                        target);
        AMD64GraphBuilderPlugins.register(plugins, replacements, (AMD64) target.arch, false, JavaVersionUtil.JAVA_SPEC >= 9, config.useFMAIntrinsics);
        return plugins;
    }

    protected AMD64HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new AMD64HotSpotBackend(config, runtime, providers);
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(AMD64.r15, AMD64.r12, AMD64.rsp);
    }

    protected AMD64HotSpotForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, WordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        return new AMD64HotSpotForeignCallsProvider(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
    }

    /**
     * @param replacements
     */
    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins,
                    HotSpotRegistersProvider registers, Replacements replacements, OptionValues options) {
        return new AddressLoweringHotSpotSuitesProvider(new AMD64HotSpotSuitesCreator(compilerConfiguration, plugins), config, runtime,
                        new AddressLoweringPhase(new AMD64HotSpotAddressLowering(config, registers.getHeapBaseRegister(), options)));
    }

    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess, HotSpotForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, PlatformConfigurationProvider platformConfig,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target) {
        return new AMD64HotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, constantReflection, platformConfig, metaAccessExtensionProvider, target);
    }

    protected Value[] createNativeABICallerSaveRegisters(GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        List<Register> callerSave = new ArrayList<>(regConfig.getAllocatableRegisters().asList());
        if (config.osName.equals("windows")) {
            // http://msdn.microsoft.com/en-us/library/9z1stfyw.aspx
            callerSave.remove(AMD64.rdi);
            callerSave.remove(AMD64.rsi);
            callerSave.remove(AMD64.rbx);
            callerSave.remove(AMD64.rbp);
            callerSave.remove(AMD64.rsp);
            callerSave.remove(AMD64.r12);
            callerSave.remove(AMD64.r13);
            callerSave.remove(AMD64.r14);
            callerSave.remove(AMD64.r15);
            callerSave.remove(AMD64.xmm6);
            callerSave.remove(AMD64.xmm7);
            callerSave.remove(AMD64.xmm8);
            callerSave.remove(AMD64.xmm9);
            callerSave.remove(AMD64.xmm10);
            callerSave.remove(AMD64.xmm11);
            callerSave.remove(AMD64.xmm12);
            callerSave.remove(AMD64.xmm13);
            callerSave.remove(AMD64.xmm14);
            callerSave.remove(AMD64.xmm15);
        } else {
            /*
             * System V Application Binary Interface, AMD64 Architecture Processor Supplement
             *
             * Draft Version 0.96
             *
             * http://www.uclibc.org/docs/psABI-x86_64.pdf
             *
             * 3.2.1
             *
             * ...
             *
             * This subsection discusses usage of each register. Registers %rbp, %rbx and %r12
             * through %r15 "belong" to the calling function and the called function is required to
             * preserve their values. In other words, a called function must preserve these
             * registers' values for its caller. Remaining registers "belong" to the called
             * function. If a calling function wants to preserve such a register value across a
             * function call, it must save the value in its local stack frame.
             */
            callerSave.remove(AMD64.rbp);
            callerSave.remove(AMD64.rbx);
            callerSave.remove(AMD64.r12);
            callerSave.remove(AMD64.r13);
            callerSave.remove(AMD64.r14);
            callerSave.remove(AMD64.r15);
        }
        Value[] nativeABICallerSaveRegisters = new Value[callerSave.size()];
        for (int i = 0; i < callerSave.size(); i++) {
            nativeABICallerSaveRegisters[i] = callerSave.get(i).asValue();
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "AMD64";
    }
}
