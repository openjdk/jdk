/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.junit.Test;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.java.RawMonitorEnterNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.LockEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.common.ValueAnchorCleanupPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public class LockEliminationTest extends GraalCompilerTest {

    static class A {

        int value;

        public synchronized int getValue() {
            return value;
        }
    }

    static int field1;
    static int field2;

    public static void testSynchronizedSnippet(A x, A y) {
        synchronized (x) {
            field1 = x.value;
        }
        synchronized (x) {
            field2 = y.value;
        }
    }

    @Test
    public void testLock() {
        test("testSynchronizedSnippet", new A(), new A());

        StructuredGraph graph = getGraph("testSynchronizedSnippet");
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(RawMonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    public static void testSynchronizedMethodSnippet(A x) {
        int value1 = x.getValue();
        int value2 = x.getValue();
        field1 = value1;
        field2 = value2;
    }

    @Test
    public void testSynchronizedMethod() {
        test("testSynchronizedMethodSnippet", new A());

        StructuredGraph graph = getGraph("testSynchronizedMethodSnippet");
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(RawMonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    private StructuredGraph getGraph(String snippet) {
        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        StructuredGraph graph = parseEager(method, AllowAssumptions.YES);
        HighTierContext context = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(graph, context);
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        new LoweringPhase(new CanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        new ValueAnchorCleanupPhase().apply(graph);
        new LockEliminationPhase().apply(graph);
        return graph;
    }

}
