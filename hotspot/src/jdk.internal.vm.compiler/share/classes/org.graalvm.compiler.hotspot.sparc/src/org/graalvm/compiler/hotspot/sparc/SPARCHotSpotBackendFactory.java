/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.sparc;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.sparc.SPARCAddressLowering;
import org.graalvm.compiler.core.sparc.SPARCSuitesCreator;
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
import org.graalvm.compiler.hotspot.meta.HotSpotLoweringProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotRegisters;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSuitesProvider;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;
import org.graalvm.compiler.replacements.sparc.SPARCGraphBuilderPlugins;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.sparc.SPARC;

@ServiceProvider(HotSpotBackendFactory.class)
public class SPARCHotSpotBackendFactory implements HotSpotBackendFactory {

    @Override
    public String getName() {
        return "core";
    }

    @Override
    public Class<? extends Architecture> getArchitecture() {
        return SPARC.class;
    }

    @Override
    public HotSpotBackend createBackend(HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotBackend host) {
        assert host == null;

        GraalHotSpotVMConfig config = runtime.getVMConfig();
        JVMCIBackend jvmci = jvmciRuntime.getHostJVMCIBackend();
        HotSpotRegistersProvider registers = createRegisters();
        HotSpotMetaAccessProvider metaAccess = (HotSpotMetaAccessProvider) jvmci.getMetaAccess();
        HotSpotCodeCacheProvider codeCache = (HotSpotCodeCacheProvider) jvmci.getCodeCache();
        TargetDescription target = codeCache.getTarget();
        HotSpotConstantReflectionProvider constantReflection = (HotSpotConstantReflectionProvider) jvmci.getConstantReflection();
        HotSpotConstantFieldProvider constantFieldProvider = new HotSpotGraalConstantFieldProvider(config, metaAccess);
        Value[] nativeABICallerSaveRegisters = createNativeABICallerSaveRegisters(config, codeCache.getRegisterConfig());
        HotSpotWordTypes wordTypes = new HotSpotWordTypes(metaAccess, target.wordJavaKind);
        HotSpotForeignCallsProvider foreignCalls = new SPARCHotSpotForeignCallsProvider(jvmciRuntime, runtime, metaAccess, codeCache, wordTypes, nativeABICallerSaveRegisters);
        LoweringProvider lowerer = createLowerer(runtime, metaAccess, foreignCalls, registers, constantReflection, target);
        HotSpotStampProvider stampProvider = new HotSpotStampProvider();
        Providers p = new Providers(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, null, stampProvider);
        HotSpotSnippetReflectionProvider snippetReflection = new HotSpotSnippetReflectionProvider(runtime, constantReflection, wordTypes);
        BytecodeProvider bytecodeProvider = new ClassfileBytecodeProvider(metaAccess, snippetReflection);
        HotSpotReplacementsImpl replacements = new HotSpotReplacementsImpl(runtime.getOptions(), p, snippetReflection, bytecodeProvider, target);
        Plugins plugins = createGraphBuilderPlugins(compilerConfiguration, config, metaAccess, constantReflection, foreignCalls, stampProvider, snippetReflection, replacements, wordTypes);
        replacements.setGraphBuilderPlugins(plugins);
        HotSpotSuitesProvider suites = createSuites(config, runtime, compilerConfiguration, plugins, replacements);
        HotSpotProviders providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, suites, registers,
                        snippetReflection,
                        wordTypes, plugins);

        return createBackend(config, runtime, providers);
    }

    protected Plugins createGraphBuilderPlugins(CompilerConfiguration compilerConfiguration, GraalHotSpotVMConfig config, HotSpotMetaAccessProvider metaAccess,
                    HotSpotConstantReflectionProvider constantReflection, HotSpotForeignCallsProvider foreignCalls, HotSpotStampProvider stampProvider,
                    HotSpotSnippetReflectionProvider snippetReflection, HotSpotReplacementsImpl replacements, HotSpotWordTypes wordTypes) {
        Plugins plugins = HotSpotGraphBuilderPlugins.create(compilerConfiguration, config, wordTypes, metaAccess, constantReflection, snippetReflection, foreignCalls, stampProvider, replacements);
        SPARCGraphBuilderPlugins.register(plugins, replacements.getDefaultReplacementBytecodeProvider());
        return plugins;
    }

    /**
     * @param replacements
     */
    protected HotSpotSuitesProvider createSuites(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, Plugins plugins,
                    Replacements replacements) {
        return new AddressLoweringHotSpotSuitesProvider(new SPARCSuitesCreator(compilerConfiguration, plugins), config, runtime, new SPARCAddressLowering());
    }

    protected SPARCHotSpotBackend createBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new SPARCHotSpotBackend(config, runtime, providers);
    }

    protected HotSpotLoweringProvider createLowerer(HotSpotGraalRuntimeProvider runtime, HotSpotMetaAccessProvider metaAccess, HotSpotForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, TargetDescription target) {
        return new SPARCHotSpotLoweringProvider(runtime, metaAccess, foreignCalls, registers, constantReflection, target);
    }

    protected HotSpotRegistersProvider createRegisters() {
        return new HotSpotRegisters(SPARC.g2, SPARC.g6, SPARC.sp);
    }

    @SuppressWarnings("unused")
    private static Value[] createNativeABICallerSaveRegisters(GraalHotSpotVMConfig config, RegisterConfig regConfig) {
        Set<Register> callerSavedRegisters = new HashSet<>();
        SPARC.fpusRegisters.addTo(callerSavedRegisters);
        SPARC.fpudRegisters.addTo(callerSavedRegisters);
        callerSavedRegisters.add(SPARC.g1);
        callerSavedRegisters.add(SPARC.g4);
        callerSavedRegisters.add(SPARC.g5);
        Value[] nativeABICallerSaveRegisters = new Value[callerSavedRegisters.size()];
        int i = 0;
        for (Register reg : callerSavedRegisters) {
            nativeABICallerSaveRegisters[i] = reg.asValue();
            i++;
        }
        return nativeABICallerSaveRegisters;
    }

    @Override
    public String toString() {
        return "SPARC";
    }
}
