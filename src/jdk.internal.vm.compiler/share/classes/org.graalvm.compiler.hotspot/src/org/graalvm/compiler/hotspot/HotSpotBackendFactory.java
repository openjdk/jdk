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


package org.graalvm.compiler.hotspot;

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotGraalConstantFieldProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotSnippetReflectionProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotStampProvider;
import org.graalvm.compiler.hotspot.word.HotSpotWordTypes;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.classfile.ClassfileBytecodeProvider;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotMetaAccessProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public abstract class HotSpotBackendFactory {

    protected HotSpotGraalConstantFieldProvider createConstantFieldProvider(GraalHotSpotVMConfig config, HotSpotMetaAccessProvider metaAccess) {
        return new HotSpotGraalConstantFieldProvider(config, metaAccess);
    }

    protected HotSpotWordTypes createWordTypes(HotSpotMetaAccessProvider metaAccess, TargetDescription target) {
        return new HotSpotWordTypes(metaAccess, target.wordJavaKind);
    }

    protected HotSpotStampProvider createStampProvider() {
        return new HotSpotStampProvider();
    }

    protected HotSpotPlatformConfigurationProvider createConfigInfoProvider(GraalHotSpotVMConfig config, MetaAccessProvider metaAccess) {
        return new HotSpotPlatformConfigurationProvider(config, metaAccess);
    }

    protected HotSpotMetaAccessExtensionProvider createMetaAccessExtensionProvider() {
        return new HotSpotMetaAccessExtensionProvider();
    }

    protected HotSpotReplacementsImpl createReplacements(TargetDescription target, Providers p, HotSpotSnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider) {
        return new HotSpotReplacementsImpl(p, snippetReflection, bytecodeProvider, target);
    }

    protected ClassfileBytecodeProvider createBytecodeProvider(HotSpotMetaAccessProvider metaAccess, HotSpotSnippetReflectionProvider snippetReflection) {
        return new ClassfileBytecodeProvider(metaAccess, snippetReflection);
    }

    protected HotSpotSnippetReflectionProvider createSnippetReflection(HotSpotGraalRuntimeProvider runtime, HotSpotConstantReflectionProvider constantReflection, HotSpotWordTypes wordTypes) {
        return new HotSpotSnippetReflectionProvider(runtime, constantReflection, wordTypes);
    }

    /**
     * Gets the name of this backend factory. This should not include the {@link #getArchitecture()
     * architecture}. The {@link CompilerConfigurationFactory} can select alternative backends based
     * on this name.
     */
    public abstract String getName();

    /**
     * Gets the class describing the architecture the backend created by this factory is associated
     * with.
     */
    public abstract Class<? extends Architecture> getArchitecture();

    public abstract HotSpotBackend createBackend(HotSpotGraalRuntimeProvider runtime, CompilerConfiguration compilerConfiguration, HotSpotJVMCIRuntime jvmciRuntime, HotSpotBackend host);
}
