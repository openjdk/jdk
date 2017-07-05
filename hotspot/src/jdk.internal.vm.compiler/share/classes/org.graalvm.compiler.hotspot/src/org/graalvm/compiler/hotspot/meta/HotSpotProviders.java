/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.NodeCostProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.phases.tiers.SuitesProvider;
import org.graalvm.compiler.phases.util.Providers;

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
    private final SnippetReflectionProvider snippetReflection;
    private final HotSpotWordTypes wordTypes;
    private final Plugins graphBuilderPlugins;

    public HotSpotProviders(MetaAccessProvider metaAccess, HotSpotCodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantField,
                    HotSpotForeignCallsProvider foreignCalls, LoweringProvider lowerer, Replacements replacements, NodeCostProvider nodeCostProvider, SuitesProvider suites,
                    HotSpotRegistersProvider registers,
                    SnippetReflectionProvider snippetReflection, HotSpotWordTypes wordTypes, Plugins graphBuilderPlugins) {
        super(metaAccess, codeCache, constantReflection, constantField, foreignCalls, lowerer, replacements, new HotSpotStampProvider(), nodeCostProvider);
        this.suites = suites;
        this.registers = registers;
        this.snippetReflection = snippetReflection;
        this.wordTypes = wordTypes;
        this.graphBuilderPlugins = graphBuilderPlugins;
    }

    @Override
    public HotSpotCodeCacheProvider getCodeCache() {
        return (HotSpotCodeCacheProvider) super.getCodeCache();
    }

    @Override
    public HotSpotForeignCallsProvider getForeignCalls() {
        return (HotSpotForeignCallsProvider) super.getForeignCalls();
    }

    public SuitesProvider getSuites() {
        return suites;
    }

    public HotSpotRegistersProvider getRegisters() {
        return registers;
    }

    public SnippetReflectionProvider getSnippetReflection() {
        return snippetReflection;
    }

    public Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    public HotSpotWordTypes getWordTypes() {
        return wordTypes;
    }
}
