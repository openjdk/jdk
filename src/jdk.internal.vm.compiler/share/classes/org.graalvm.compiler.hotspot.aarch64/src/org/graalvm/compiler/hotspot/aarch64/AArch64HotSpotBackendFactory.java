/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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


package org.graalvm.compiler.hotspot.aarch64;

import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.common.InitTimer.timer;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.aarch64.AArch64AddressLoweringByUse;
import org.graalvm.compiler.core.aarch64.AArch64LIRKindTool;
import org.graalvm.compiler.core.aarch64.AArch64SuitesCreator;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotBackendFactory;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotReplacementsImpl;
import org.graalvm.compiler.hotspot.meta.AddressLoweringHotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotConstantFieldProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraalConstantFieldProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraphBuilderPlugins;
import org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegisters;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.common.AddressLoweringByUsePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.aarch64.AArch64GraphBuilderPlugins;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.compiler.word.WordTypes;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCIBackend;

@ServiceProvider(HotSpotBackendFactory.class)
public class AArch64HotSpotBackendFactory implements HotSpotBackendFactory {

    @Override
    public String getName() {
        return "community";
    }

    @Override
    public Class<? extends Architecture> getArchitecture() {
        return AArch64.class;
    }

    @Override
    @SuppressWarnings("try")
    public HotSpotBackend createBackend(HotSpotGraalRuntimeProvider graalRuntime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntime jvmciRuntime, HotSpotBackend host) {
        assert host == null;

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
        HotSpotConstantFieldProvider constantFieldProvider = new HotSpotGraalConstantFieldProvider(config, metaAccess);
        HotSpotLoweringProvider lowerer;
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
                wordTypes = new HotSpotWordTypes(metaAccess, target.wordJavaKind);
            }
            try (InitTimer rt = timer("create ForeignCalls provider")) {
                foreignCalls = createForeignCalls(jvmciRuntime, graalRuntime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
            }
            try (InitTimer rt = timer("create Lowerer provider")) {
                lowerer = createLowerer(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, target);
            }
            HotSpotStampProvider stampProvider = new HotSpotStampProvider();
            Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider);

            try (InitTimer rt = timer("create SnippetReflection provider")) {
                snippetReflection = createSnippetReflection(graalRuntime, constantReflection, wordTypes);
            }
            try (InitTimer rt = timer("create Bytecode provider")) {
                bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
            }
            try (InitTimer rt = timer("create Replacements provider")) {
                replacements = createReplacements(graalRuntime.getOptions(), p, snippetReflection, bytecodeProvider);
            }
            try (InitTimer rt = timer("create GraphBuilderPhase plugins")) {
                plugins = createGraphBuilderPlugins(compilerConfiguration, config, constantReflection, foreignCalls, metaAccess, snippetReflection, replacements, wordTypes);
                replacements.setGraphBuilderPlugins(plugins);
            }
            try (InitTimer rt = timer("create Suites provider")) {
                suites = createSuites(config, graalRuntime, compilerConfiguration, plugins, replacements);
            }
            providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, suites, registers,
                            snippetReflection, wordTypes,
                            plugins);
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(config, graalRuntime, providers);
        }
    }

    protected Plugins createGraphBuilderPlugins(CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config, HotSpotConstantReflectionProvider constantReflection,
                    HotSpotHostForeignCallsProvider foreignCalls, HotSpotMetaAccessProvider metaAccess, HotSpotSnippetReflectionProvider snippetReflection,
                    HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(compilerConfiguration, config, wordTypes, metaAccess, constantReflection, snippetReflection, foreignCalls, replacements);
        AArch64GraphBuilderPlugins.register(plugins, replacements.getDefaultReplacementBytecodeProvider(), false);
        return plugins;
    }

    protected AArch64HotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new AArch64HotSpotBackend(config, runtime, providers);
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(AArch64HotSpotRegisterConfig.threadRegister, AArch64HotSpotRegisterConfig.heapBaseRegister, sp);
    }

    protected HotSpotReplacementsImpl createReplacements(OptionValues options, Providers p, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider) {
        return new HotSpotReplacementsImpl(options, p, snippetReflection, bytecodeProvider, p.getCodeCache().getTarget());
    }

    protected HotSpotHostForeignCallsProvider createForeignCalls(HotSpotJVMCIRuntime jvmciRuntime, HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache, WordTypes wordTypes, Value[] nativeABICallerSaveRegisters) {
        return new AArch64HotSpotForeignCallsProvider(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
    }

    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins,
                    @SuppressWarnings("unused") Replacements replacements) {
        AArch64SuitesCreator suitesCreator = new AArch64SuitesCreator(compilerConfiguration, plugins, SchedulePhase.class);
        Phase addressLoweringPhase = new AddressLoweringByUsePhase(new AArch64AddressLoweringByUse(new AArch64LIRKindTool()));
        return new AddressLoweringHotSpotSuitesProvider(suitesCreator, config, runtime, addressLoweringPhase);
    }

    protected HotSpotSnippetReflectionProvider createSnippetReflection(HotSpotGraalRuntimeProvider runtime, HotSpotConstantReflectionProvider constantReflection, WordTypes wordTypes) {
        return new HotSpotSnippetReflectionProvider(runtime, constantReflection, wordTypes);
    }

    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess, HotSpotForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, TargetDescription target) {
        return new AArch64HotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, constantReflection, target);
    }

    protected static Value[] createNativeABICallerSaveRegisters(@SuppressWarnings("unused") GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        List<Register> callerSave = new ArrayList<>(regConfig.getAllocatableRegisters().asList());
        callerSave.remove(AArch64.r19);
        callerSave.remove(AArch64.r20);
        callerSave.remove(AArch64.r21);
        callerSave.remove(AArch64.r22);
        callerSave.remove(AArch64.r23);
        callerSave.remove(AArch64.r24);
        callerSave.remove(AArch64.r25);
        callerSave.remove(AArch64.r26);
        callerSave.remove(AArch64.r27);
        callerSave.remove(AArch64.r28);
        Value[] nativeABICallerSaveRegisters = new Value[callerSave.size()];
        for (int i = 0; i < callerSave.size(); i++) {
            nativeABICallerSaveRegisters[i] = callerSave.get(i).asValue();
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "AArch64";
    }
}
