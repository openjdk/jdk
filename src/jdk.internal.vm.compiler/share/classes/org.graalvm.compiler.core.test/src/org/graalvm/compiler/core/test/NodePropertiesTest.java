/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.SimplifierTool;
import org.graalvm.compiler.java.ComputeLoopFrequenciesClosure;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import org.graalvm.compiler.phases.contract.NodeCostUtil;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.junit.Assert;
import org.junit.Test;

public class NodePropertiesTest extends GraalCompilerTest {

    public static Object sideEffect;

    public static Object[] array = new Object[]{new Object(), new Object(), new Object()};

    public static int test1Snippet(int a) {
        int x = 0;
        if (a > 0) {
            x = 1;
            sideEffect = null;
        } else {
            x = 2;
            sideEffect = null;
        }
        int b = 4;
        sideEffect = null;
        int c = b % 5;
        // can shift
        return a * x * c;
    }

    public static int test2Snippet(int a) {
        int x = 0;
        if (a > 0) {
            x = 1;
            sideEffect = null;
        } else {
            x = 2;
            sideEffect = null;
        }
        sideEffect = null;
        // cannot shift
        return a * x * 41;
    }

    public static final int ITERATIONS_LOOP_1 = 128;
    public static final int ITERATIONS_LOOP_2 = 256;

    public static int testLoop01(int a) {
        int res = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(ITERATIONS_LOOP_1, i < a); i++) {
            res += i;
        }
        return res;
    }

    public static int testLoop02(int a) {
        int res = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(ITERATIONS_LOOP_2, i < a); i++) {
            res += i;
        }
        return res;
    }

    public static int testLoop03(int a) {
        int res = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(ITERATIONS_LOOP_1, i < a); i++) {
            res *= i;
        }
        return res;
    }

    public static int testLoop04(int a) {
        int res = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(ITERATIONS_LOOP_1 * ITERATIONS_LOOP_2, i < a); i++) {
            res += i;
        }
        return res;
    }

    public static int testLoop05(int a) {
        int res = 0;
        for (int i = 0; GraalDirectives.injectIterationCount(ITERATIONS_LOOP_1, i < a); i++) {
            res += i;
            for (int j = 0; GraalDirectives.injectIterationCount(ITERATIONS_LOOP_2, j < ITERATIONS_LOOP_2); j++) {
                res += i;
            }
        }
        return res;
    }

    public static int dontInline(int a) {
        int res = 1;
        for (int i = 0; i < a; i++) {
            for (int j = 0; j < i; j++) {
                for (int k = 0; k < j; k++) {
                    res += i + j + k;
                }
            }
        }
        sideEffect = res;
        return (Integer) sideEffect;
    }

    public static int untrused01(int a) {
        return dontInline(a);
    }

    public static int arrayLoadTest(int a) {
        return array[a].hashCode();
    }

    public static int arrayStoreTest(int a) {
        array[2] = a;
        return a;
    }

    public static int fieldLoad(int a) {
        return sideEffect.hashCode() * a;
    }

    public static int fieldStore(int a) {
        sideEffect = a;
        return a;
    }

    @Test
    public void testCanonicalizationExample() {
        HighTierContext htc = getDefaultHighTierContext();
        ImprovementSavingCanonicalizer c1 = new ImprovementSavingCanonicalizer();
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("test1Snippet"));
        new CanonicalizerPhase(c1).apply(g1, htc);
        ImprovementSavingCanonicalizer c2 = new ImprovementSavingCanonicalizer();
        StructuredGraph g2 = parseForCompile(getResolvedJavaMethod("test2Snippet"));
        new CanonicalizerPhase(c2).apply(g2, htc);
        Assert.assertEquals(0, c1.savedCycles);
        Assert.assertEquals(0, c2.savedCycles);
    }

    private static void prepareGraphForLoopFrequencies(StructuredGraph g, HighTierContext htc) {
        // let canonicalizer work away branch probability nodes
        new CanonicalizerPhase().apply(g, htc);
        // recompute the loop frequencies
        ComputeLoopFrequenciesClosure.compute(g);
    }

    private static void assertFrequency(StructuredGraph g, int iterations) {
        NodeIterable<LoopBeginNode> loopBeginNodes = g.getNodes(LoopBeginNode.TYPE);
        LoopBeginNode loopBeginNode = loopBeginNodes.first();
        Assert.assertEquals("loop frequency of " + loopBeginNode, iterations, loopBeginNode.loopFrequency(), 0);
    }

    @Test
    public void testDifferentLoopFaster() {
        HighTierContext htc = getDefaultHighTierContext();
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("testLoop01"));
        StructuredGraph g2 = parseForCompile(getResolvedJavaMethod("testLoop03"));
        prepareGraphForLoopFrequencies(g1, htc);
        prepareGraphForLoopFrequencies(g2, htc);
        assertFrequency(g1, ITERATIONS_LOOP_1);
        assertFrequency(g2, ITERATIONS_LOOP_1);
        GraphCostPhase gc1 = new GraphCostPhase();
        GraphCostPhase gc2 = new GraphCostPhase();
        gc1.apply(g1, htc);
        gc2.apply(g2, htc);
        g1.getDebug().log("Test testDifferentLoopFaster --> 1.Graph cycles:%f size:%f vs. 2.Graph cycles:%f size:%f\n", gc1.finalCycles, gc1.finalSize, gc2.finalCycles, gc2.finalSize);
        Assert.assertTrue(gc2.finalCycles > gc1.finalCycles);
        Assert.assertTrue(gc2.finalSize == gc1.finalSize);
    }

    @Test
    public void testSameLoopMoreIterationsCostlier() {
        HighTierContext htc = getDefaultHighTierContext();
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("testLoop01"));
        StructuredGraph g2 = parseForCompile(getResolvedJavaMethod("testLoop02"));
        prepareGraphForLoopFrequencies(g1, htc);
        prepareGraphForLoopFrequencies(g2, htc);
        assertFrequency(g1, ITERATIONS_LOOP_1);
        assertFrequency(g2, ITERATIONS_LOOP_2);
        GraphCostPhase gc1 = new GraphCostPhase();
        GraphCostPhase gc2 = new GraphCostPhase();
        gc1.apply(g1, htc);
        gc2.apply(g2, htc);
        g1.getDebug().log("Test testSameLoopMoreIterationsCostlier --> 1.Graph cycles:%f size:%f vs. 2.Graph cycles:%f size:%f\n", gc1.finalCycles, gc1.finalSize, gc2.finalCycles, gc2.finalSize);
        Assert.assertTrue(gc2.finalCycles > gc1.finalCycles);
        Assert.assertTrue(gc2.finalSize == gc1.finalSize);
    }

    @Test
    public void testDifferentLoopsInnerOuter() {
        HighTierContext htc = getDefaultHighTierContext();
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("testLoop04"));
        StructuredGraph g2 = parseForCompile(getResolvedJavaMethod("testLoop05"));
        prepareGraphForLoopFrequencies(g1, htc);
        prepareGraphForLoopFrequencies(g2, htc);
        assertFrequency(g1, ITERATIONS_LOOP_1 * ITERATIONS_LOOP_2);
        GraphCostPhase gc1 = new GraphCostPhase();
        GraphCostPhase gc2 = new GraphCostPhase();
        gc1.apply(g1, htc);
        gc2.apply(g2, htc);
        g1.getDebug().log("Test testDifferentLoopsInnerOuter --> 1.Graph cycles:%f size:%f vs. 2.Graph cycles:%f size:%f\n", gc1.finalCycles, gc1.finalSize, gc2.finalCycles, gc2.finalSize);
        Assert.assertTrue(gc2.finalSize > gc1.finalSize);
    }

    @Test
    public void testGraphCost() {
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("test1Snippet"));
        StructuredGraph g2 = parseForCompile(getResolvedJavaMethod("test2Snippet"));
        HighTierContext htc = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(g1, htc);
        new CanonicalizerPhase().apply(g2, htc);
        GraphCostPhase gc1 = new GraphCostPhase();
        GraphCostPhase gc2 = new GraphCostPhase();
        gc1.apply(g1, htc);
        gc2.apply(g2, htc);
        g1.getDebug().log("Test Graph Cost --> 1.Graph cost:%f vs. 2.Graph cost:%f\n", gc1.finalCycles, gc2.finalCycles);
        Assert.assertTrue(gc2.finalCycles > gc1.finalCycles);
        Assert.assertTrue(gc2.finalSize == gc1.finalSize);
    }

    @Test
    public void testExpectUntrusted() {
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("untrused01"));
        HighTierContext htc = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(g1, htc);
        GraphCostPhase gc1 = new GraphCostPhase();
        gc1.apply(g1, htc);
    }

    @Test
    public void testArrayLoad() {
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("arrayLoadTest"));
        HighTierContext htc = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(g1, htc);
        GraphCostPhase gc1 = new GraphCostPhase();
        gc1.apply(g1, htc);
        Assert.assertEquals(15, gc1.finalCycles, 25);
    }

    @Test
    public void testArrayStore() {
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("arrayStoreTest"));
        HighTierContext htc = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(g1, htc);
        GraphCostPhase gc1 = new GraphCostPhase();
        gc1.apply(g1, htc);
        Assert.assertEquals(15, gc1.finalCycles, 25);
    }

    @Test
    public void testFieldLoad() {
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("fieldLoad"));
        HighTierContext htc = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(g1, htc);
        GraphCostPhase gc1 = new GraphCostPhase();
        gc1.apply(g1, htc);
        Assert.assertEquals(15, gc1.finalCycles, 25);
    }

    @Test
    public void testFieldStore() {
        StructuredGraph g1 = parseForCompile(getResolvedJavaMethod("fieldStore"));
        HighTierContext htc = getDefaultHighTierContext();
        new CanonicalizerPhase().apply(g1, htc);
        GraphCostPhase gc1 = new GraphCostPhase();
        gc1.apply(g1, htc);
        Assert.assertEquals(15, gc1.finalCycles, 25);
    }

    static class ImprovementSavingCanonicalizer extends CustomCanonicalizer {
        private int savedCycles;

        @Override
        public void simplify(Node node, SimplifierTool tool) {
            if (node instanceof Canonicalizable.Binary<?>) {
                @SuppressWarnings("unchecked")
                Canonicalizable.Binary<ValueNode> bc = (Canonicalizable.Binary<ValueNode>) node;
                Node canonicalized = bc.canonical(tool, bc.getX(), bc.getY());
                if (canonicalized != node) {
                    savedCycles += node.estimatedNodeCycles().value - canonicalized.estimatedNodeCycles().value;
                }
            }
        }
    }

    private static class GraphCostPhase extends BasePhase<PhaseContext> {
        private double finalCycles;
        private double finalSize;

        @Override
        protected void run(StructuredGraph graph, PhaseContext context) {
            finalCycles = NodeCostUtil.computeGraphCycles(graph, true);
            finalSize = NodeCostUtil.computeGraphSize(graph);
        }

    }

}
