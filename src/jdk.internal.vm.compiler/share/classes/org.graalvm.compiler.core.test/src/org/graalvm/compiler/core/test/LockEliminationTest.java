/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.loop.DefaultLoopPolicies;
import org.graalvm.compiler.loop.phases.LoopFullUnrollPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.java.MonitorExitNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.LockEliminationPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;

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

        StructuredGraph graph = getGraph("testSynchronizedSnippet", false);
        createCanonicalizerPhase().apply(graph, getProviders());
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(MonitorEnterNode.class).count());
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

        StructuredGraph graph = getGraph("testSynchronizedMethodSnippet", false);
        createCanonicalizerPhase().apply(graph, getProviders());
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(MonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    public void testUnrolledSyncSnippet(Object a) {
        for (int i = 0; i < 3; i++) {
            synchronized (a) {

            }
        }
    }

    @Test
    public void testUnrolledSync() {
        StructuredGraph graph = getGraph("testUnrolledSyncSnippet", false);
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        canonicalizer.apply(graph, getProviders());
        HighTierContext context = getDefaultHighTierContext();
        new LoopFullUnrollPhase(canonicalizer, new DefaultLoopPolicies()).apply(graph, context);
        new LockEliminationPhase().apply(graph);
        assertDeepEquals(1, graph.getNodes().filter(MonitorEnterNode.class).count());
        assertDeepEquals(1, graph.getNodes().filter(MonitorExitNode.class).count());
    }

    private StructuredGraph getGraph(String snippet, boolean doEscapeAnalysis) {
        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        StructuredGraph graph = parseEager(method, AllowAssumptions.YES);
        HighTierContext context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        canonicalizer.apply(graph, context);
        createInliningPhase().apply(graph, context);
        createCanonicalizerPhase().apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        if (doEscapeAnalysis) {
            new PartialEscapePhase(true, canonicalizer, graph.getOptions()).apply(graph, context);
        }
        new LoweringPhase(createCanonicalizerPhase(), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        return graph;
    }

    public void testEscapeAnalysisSnippet(A a) {
        A newA = new A();
        synchronized (newA) {
            synchronized (a) {
                field1 = a.value;
            }
        }
        /*
         * Escape analysis removes the synchronization on newA. But lock elimination still must not
         * combine the two synchronizations on the parameter a because they have a different lock
         * depth.
         */
        synchronized (a) {
            field2 = a.value;
        }
        /*
         * Lock elimination can combine these synchronizations, since they are both on parameter a
         * with the same lock depth.
         */
        synchronized (a) {
            field1 = a.value;
        }
    }

    @Test
    public void testEscapeAnalysis() {
        StructuredGraph graph = getGraph("testEscapeAnalysisSnippet", true);

        assertDeepEquals(3, graph.getNodes().filter(MonitorEnterNode.class).count());
        assertDeepEquals(3, graph.getNodes().filter(MonitorExitNode.class).count());

        new LockEliminationPhase().apply(graph);

        assertDeepEquals(2, graph.getNodes().filter(MonitorEnterNode.class).count());
        assertDeepEquals(2, graph.getNodes().filter(MonitorExitNode.class).count());
    }
}
