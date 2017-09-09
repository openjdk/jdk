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
package org.graalvm.compiler.core.test;

import static org.graalvm.compiler.core.GraalCompiler.compileGraph;
import static org.graalvm.compiler.core.common.GraalOptions.OptAssumptions;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test that infopoints in {@link CompilationResult}s have correctly assigned reasons.
 */
public class InfopointReasonTest extends GraalCompilerTest {

    public static final String[] STRINGS = new String[]{"world", "everyone", "you"};

    public String testMethod() {
        StringBuilder sb = new StringBuilder("Hello ");
        for (String s : STRINGS) {
            sb.append(s).append(", ");
        }
        sb.replace(sb.length() - 2, sb.length(), "!");
        return sb.toString();
    }

    @Test
    public void callInfopoints() {
        final ResolvedJavaMethod method = getResolvedJavaMethod("testMethod");
        final StructuredGraph graph = parseEager(method, AllowAssumptions.YES);
        final CompilationResult cr = compileGraph(graph, graph.method(), getProviders(), getBackend(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL, graph.getProfilingInfo(),
                        createSuites(graph.getOptions()), createLIRSuites(graph.getOptions()), new CompilationResult(graph.compilationId()), CompilationResultBuilderFactory.Default);
        for (Infopoint sp : cr.getInfopoints()) {
            assertNotNull(sp.reason);
            if (sp instanceof Call) {
                assertDeepEquals(InfopointReason.CALL, sp.reason);
            }
        }
    }

    @Test
    public void lineInfopoints() {
        final ResolvedJavaMethod method = getResolvedJavaMethod("testMethod");
        final StructuredGraph graph = parse(builder(method, AllowAssumptions.ifTrue(OptAssumptions.getValue(getInitialOptions()))), getDebugGraphBuilderSuite());
        int graphLineSPs = 0;
        for (FullInfopointNode ipn : graph.getNodes().filter(FullInfopointNode.class)) {
            if (ipn.getReason() == InfopointReason.BYTECODE_POSITION) {
                ++graphLineSPs;
            }
        }
        assertTrue(graphLineSPs > 0);
        PhaseSuite<HighTierContext> graphBuilderSuite = getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withFullInfopoints(true));
        final CompilationResult cr = compileGraph(graph, graph.method(), getProviders(), getBackend(), graphBuilderSuite, OptimisticOptimizations.ALL, graph.getProfilingInfo(),
                        createSuites(graph.getOptions()), createLIRSuites(graph.getOptions()), new CompilationResult(graph.compilationId()), CompilationResultBuilderFactory.Default);
        int lineSPs = 0;
        for (Infopoint sp : cr.getInfopoints()) {
            assertNotNull(sp.reason);
            if (sp.reason == InfopointReason.BYTECODE_POSITION) {
                ++lineSPs;
            }
        }
        assertTrue(lineSPs > 0);
    }

}
