/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.meta;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Extends {@link Providers} to include a number of extra capabilities used by the HotSpot parts of
 * the compiler.
 */
public class HotSpotProviders extends Providers {

    private final SuitesProvider suites;
    private final HotSpotRegistersProvider registers;
    private final Plugins graphBuilderPlugins;
    private final GraalHotSpotVMConfig config;

    public HotSpotProviders(MetaAccessProvider metaAccess,
                    HotSpotCodeCacheProvider codeCache,
                    ConstantReflectionProvider constantReflection,
                    ConstantFieldProvider constantField,
                    HotSpotHostForeignCallsProvider foreignCalls,
                    LoweringProvider lowerer,
                    Replacements replacements,
                    SuitesProvider suites,
                    HotSpotRegistersProvider registers,
                    SnippetReflectionProvider snippetReflection,
                    HotSpotWordTypes wordTypes,
                    Plugins graphBuilderPlugins,
                    PlatformConfigurationProvider platformConfigurationProvider,
                    MetaAccessExtensionProvider metaAccessExtensionProvider,
                    GraalHotSpotVMConfig config) {
        super(metaAccess, codeCache, constantReflection, constantField, foreignCalls, lowerer, replacements, new HotSpotStampProvider(), platformConfigurationProvider, metaAccessExtensionProvider,
                        snippetReflection, wordTypes);
        this.suites = suites;
        this.registers = registers;
        this.graphBuilderPlugins = graphBuilderPlugins;
        this.config = config;
    }

    public HotSpotProviders(MetaAccessProvider metaAccess, CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantField,
                    ForeignCallsProvider foreignCalls, LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, PlatformConfigurationProvider platformConfigurationProvider,
                    MetaAccessExtensionProvider metaAccessExtensionProvider) {
        super(metaAccess, codeCache, constantReflection, constantField, foreignCalls, lowerer, replacements, stampProvider, platformConfigurationProvider, metaAccessExtensionProvider, null, null);
        this.suites = null;
        this.registers = null;
        this.graphBuilderPlugins = null;
        this.config = null;
    }

    @Override
    public HotSpotCodeCacheProvider getCodeCache() {
        return (HotSpotCodeCacheProvider) super.getCodeCache();
    }

    @Override
    public HotSpotHostForeignCallsProvider getForeignCalls() {
        return (HotSpotHostForeignCallsProvider) super.getForeignCalls();
    }

    public SuitesProvider getSuites() {
        return suites;
    }

    public HotSpotRegistersProvider getRegisters() {
        return registers;
    }

    public Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    @Override
    public HotSpotWordTypes getWordTypes() {
        return (HotSpotWordTypes) super.getWordTypes();
    }

    public GraalHotSpotVMConfig getConfig() {
        return config;
    }

    @Override
    public HotSpotPlatformConfigurationProvider getPlatformConfigurationProvider() {
        return (HotSpotPlatformConfigurationProvider) platformConfigurationProvider;
    }

    @Override
    public HotSpotProviders copyWith(ConstantReflectionProvider substitution) {
        return new HotSpotProviders(getMetaAccess(), getCodeCache(), substitution, getConstantFieldProvider(), getForeignCalls(), getLowerer(), getReplacements(), getSuites(),
                        getRegisters(), getSnippetReflection(), getWordTypes(), getGraphBuilderPlugins(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider(), config);
    }

    @Override
    public HotSpotProviders copyWith(ConstantFieldProvider substitution) {
        return new HotSpotProviders(getMetaAccess(), getCodeCache(), getConstantReflection(), substitution, getForeignCalls(), getLowerer(), getReplacements(),
                        getSuites(),
                        getRegisters(), getSnippetReflection(), getWordTypes(), getGraphBuilderPlugins(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider(), config);
    }

    @Override
    public HotSpotProviders copyWith(Replacements substitution) {
        return new HotSpotProviders(getMetaAccess(), getCodeCache(), getConstantReflection(), getConstantFieldProvider(), getForeignCalls(), getLowerer(), substitution,
                        getSuites(), getRegisters(), getSnippetReflection(), getWordTypes(), getGraphBuilderPlugins(), getPlatformConfigurationProvider(), getMetaAccessExtensionProvider(), config);
    }

    public HotSpotProviders copyWith(Plugins substitution) {
        return new HotSpotProviders(getMetaAccess(), getCodeCache(), getConstantReflection(), getConstantFieldProvider(), getForeignCalls(), getLowerer(), getReplacements(),
                        getSuites(), getRegisters(), getSnippetReflection(), getWordTypes(), substitution, getPlatformConfigurationProvider(), getMetaAccessExtensionProvider(), config);
    }

}
