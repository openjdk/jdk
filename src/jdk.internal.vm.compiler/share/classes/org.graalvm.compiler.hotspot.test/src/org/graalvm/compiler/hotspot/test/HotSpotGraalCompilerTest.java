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


package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigAccess;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A compiler test that needs access to the {@link HotSpotGraalRuntimeProvider}.
 */
public abstract class HotSpotGraalCompilerTest extends GraalCompilerTest {

    /**
     * Gets the {@link HotSpotGraalRuntimeProvider}.
     */
    protected HotSpotGraalRuntimeProvider runtime() {
        return ((HotSpotBackend) getBackend()).getRuntime();
    }

    /**
     * Checks that the {@code UseJVMCICompiler} flag is false.
     *
     * @param message describes the reason the test should be ignored when Graal is the JIT
     * @throws AssumptionViolatedException if {@code UseJVMCICompiler == true}
     */
    public static void assumeGraalIsNotJIT(String message) {
        HotSpotVMConfigStore configStore = HotSpotJVMCIRuntime.runtime().getConfigStore();
        HotSpotVMConfigAccess access = new HotSpotVMConfigAccess(configStore);
        boolean useJVMCICompiler = access.getFlag("UseJVMCICompiler", Boolean.class);
        Assume.assumeFalse(message, useJVMCICompiler);
    }

    protected InstalledCode compileAndInstallSubstitution(Class<?> c, String methodName) {
        ResolvedJavaMethod method = getMetaAccess().lookupJavaMethod(getMethod(c, methodName));
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        HotSpotProviders providers = rt.getHostBackend().getProviders();
        CompilationIdentifier compilationId = runtime().getHostBackend().getCompilationIdentifier(method);
        OptionValues options = getInitialOptions();
        StructuredGraph graph = providers.getReplacements().getIntrinsicGraph(method, compilationId, getDebugContext(options), AllowAssumptions.YES, null);
        if (graph != null) {
            return getCode(method, graph, true, true, graph.getOptions());
        }
        return null;
    }
}
