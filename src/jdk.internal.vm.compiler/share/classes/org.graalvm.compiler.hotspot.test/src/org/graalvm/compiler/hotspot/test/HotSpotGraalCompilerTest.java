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


package org.graalvm.compiler.hotspot.test;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotGraalCompiler;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;

/**
 * A Graal compiler test that needs access to the {@link HotSpotGraalRuntimeProvider}.
 */
public abstract class HotSpotGraalCompilerTest extends GraalCompilerTest {

    /**
     * Gets the {@link HotSpotGraalRuntimeProvider}.
     */
    protected HotSpotGraalRuntimeProvider runtime() {
        return ((HotSpotBackend) getBackend()).getRuntime();
    }

    protected InstalledCode compileAndInstallSubstitution(Class<?> c, String methodName) {
        ResolvedJavaMethod method = getMetaAccess().lookupJavaMethod(getMethod(c, methodName));
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) JVMCI.getRuntime().getCompiler();
        HotSpotGraalRuntimeProvider rt = (HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class);
        HotSpotProviders providers = rt.getHostBackend().getProviders();
        CompilationIdentifier compilationId = runtime().getHostBackend().getCompilationIdentifier(method);
        OptionValues options = getInitialOptions();
        StructuredGraph graph = compiler.getIntrinsicGraph(method, providers, compilationId, options, getDebugContext(options));
        if (graph != null) {
            return getCode(method, graph, true, true, graph.getOptions());
        }
        return null;
    }
}
