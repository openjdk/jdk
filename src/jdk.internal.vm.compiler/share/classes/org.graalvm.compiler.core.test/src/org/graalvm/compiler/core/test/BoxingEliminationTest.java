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
import org.graalvm.compiler.loop.phases.LoopPeelingPhase;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant
 * 0. Then boxing elimination is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class BoxingEliminationTest extends GraalCompilerTest {

    private static final Short s = 2;

    @SuppressWarnings("all")
    public static short referenceSnippet1() {
        return 1;
    }

    @SuppressWarnings("all")
    public static short referenceSnippet2() {
        return 2;
    }

    public static Short boxedShort() {
        return 1;
    }

    public static Object boxedObjectShort() {
        return (short) 1;
    }

    public static Object boxedObjectInteger() {
        return 1;
    }

    public static Integer boxedInteger() {
        return 2;
    }

    public static Short constantBoxedShort() {
        return s;
    }

    @Test
    public void test1() {
        compareGraphs("test1Snippet", "referenceSnippet1");
    }

    @SuppressWarnings("all")
    public static short test1Snippet() {
        return boxedShort();
    }

    @Test
    public void test2() {
        compareGraphs("test2Snippet", "referenceSnippet1");
    }

    @SuppressWarnings("all")
    public static short test2Snippet() {
        return (Short) boxedObjectShort();
    }

    @Test
    public void test3() {
        compareGraphs("test3Snippet", "referenceSnippet1");
    }

    @SuppressWarnings("all")
    public static short test3Snippet() {
        short b = boxedShort();
        if (b < 0) {
            b = boxedShort();
        }
        return b;
    }

    @Test
    public void test4() {
        compareGraphs("test4Snippet", "referenceSnippet2");
    }

    @SuppressWarnings("all")
    public static short test4Snippet() {
        return constantBoxedShort();
    }

    @Ignore
    @Test
    public void testLoop() {
        compareGraphs("testLoopSnippet", "referenceLoopSnippet", false, true);
    }

    public static int testLoopSnippet(int n, int a) {
        Integer sum = a;
        for (Integer i = 0; i < n; i++) {
            sum += i;
        }
        return sum;
    }

    public static int referenceLoopSnippet(int n, int a) {
        int sum = a;
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        return sum;
    }

    @Test
    public void testLoop2() {
        compareGraphs("testLoop2Snippet", "referenceLoop2Snippet", true, true);
    }

    public static int testLoop2Snippet(int n, Integer a) {
        Integer sum = a;
        for (Integer i = 0; i < n; i++) {
            sum += i;
        }
        return sum;
    }

    public static int referenceLoop2Snippet(int n, Integer a) {
        Integer sum0;
        if (n <= 0) {
            sum0 = a;
        } else {
            int sum = a;
            for (int i = 1; i < n; i++) {
                sum += i;
            }
            sum0 = sum;
        }
        return sum0;
    }

    public static int referenceIfSnippet(int a) {
        int result;
        if (a < 0) {
            result = 2;
        } else {
            result = 1;
        }
        return result;
    }

    @Test
    public void testIf() {
        compareGraphs("testIfSnippet", "referenceIfSnippet");
    }

    public static int testIfSnippet(int a) {
        Integer result;
        if (a < 0) {
            result = boxedInteger();
        } else {
            result = (Integer) boxedObjectInteger();
        }
        return result;
    }

    @Test
    public void testComparison() {
        compareGraphs("testComparison1Snippet", "referenceComparisonSnippet");
        compareGraphs("testComparison2Snippet", "referenceComparisonSnippet");
    }

    @SuppressWarnings("cast")
    public static boolean testComparison1Snippet(int a, int b) {
        return ((Integer) a) == b;
    }

    public static boolean testComparison2Snippet(int a, int b) {
        Integer x = a;
        Integer y = b;
        return x == y;
    }

    public static boolean referenceComparisonSnippet(int a, int b) {
        return a == b;
    }

    @Test
    public void testLateCanonicalization() {
        compareGraphs("testLateCanonicalizationSnippet", "referenceLateCanonicalizationSnippet");
    }

    public static boolean testLateCanonicalizationSnippet(int a) {
        Integer x = a;
        Integer y = 1000;
        return x == y;
    }

    public static boolean referenceLateCanonicalizationSnippet(int a) {
        return a == 1000;
    }

    private StructuredGraph graph;

    public static Integer materializeReferenceSnippet(int a) {
        return Integer.valueOf(a);
    }

    public static Integer materializeTest1Snippet(int a) {
        Integer v = a;

        if (v == a) {
            return v;
        } else {
            return null;
        }
    }

    @Test
    public void materializeTest1() {
        test("materializeTest1Snippet", 1);
    }

    public static Float materializeTest2Snippet(float a) {
        Float v = a;

        if (v == a) {
            return v;
        } else {
            return null;
        }
    }

    @Test
    public void materializeTest2() {
        test("materializeTest2Snippet", 1f);
    }

    public static int intTest1Snippet() {
        return Integer.valueOf(1);
    }

    @Test
    public void intTest1() {
        ValueNode result = getResult("intTest1Snippet");
        Assert.assertTrue(result.isConstant());
        Assert.assertEquals(1, result.asJavaConstant().asInt());
    }

    public static int mergeTest1Snippet(boolean d, int a, int b) {
        Integer v;
        if (d) {
            v = a;
        } else {
            v = b;
        }
        return v;
    }

    @Test
    public void mergeTest1() {
        processMethod("mergeTest1Snippet");
    }

    public static boolean equalsTest1Snippet(int x, int y) {
        Integer a = x;
        Integer b = y;
        return a == b;
    }

    @Test
    public void equalsTest1() {
        processMethod("equalsTest1Snippet");
    }

    public static int loopTest1Snippet(int n, int v) {
        Integer sum = 0;
        for (int i = 0; i < n; i++) {
            sum += v;
        }
        return sum;
    }

    @Test
    public void loopTest1() {
        processMethod("loopTest1Snippet");

    }

    final ValueNode getResult(String snippet) {
        processMethod(snippet);
        assertDeepEquals(1, graph.getNodes(ReturnNode.TYPE).count());
        return graph.getNodes(ReturnNode.TYPE).first().result();
    }

    private void processMethod(final String snippet) {
        graph = parseEager(snippet, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        createInliningPhase().apply(graph, context);
        new PartialEscapePhase(false, createCanonicalizerPhase(), graph.getOptions()).apply(graph, context);
    }

    private void compareGraphs(final String snippet, final String referenceSnippet) {
        compareGraphs(snippet, referenceSnippet, false, false);
    }

    private void compareGraphs(final String snippet, final String referenceSnippet, final boolean loopPeeling, final boolean excludeVirtual) {
        graph = parseEager(snippet, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = this.createCanonicalizerPhase();
        canonicalizer.apply(graph, context);
        createInliningPhase().apply(graph, context);
        if (loopPeeling) {
            new LoopPeelingPhase(new DefaultLoopPolicies()).apply(graph, context);
        }
        new DeadCodeEliminationPhase().apply(graph);
        canonicalizer.apply(graph, context);
        new PartialEscapePhase(false, canonicalizer, graph.getOptions()).apply(graph, context);

        new DeadCodeEliminationPhase().apply(graph);
        canonicalizer.apply(graph, context);

        StructuredGraph referenceGraph = parseEager(referenceSnippet, AllowAssumptions.YES);
        createInliningPhase().apply(referenceGraph, context);
        new DeadCodeEliminationPhase().apply(referenceGraph);
        this.createCanonicalizerPhase().apply(referenceGraph, context);

        assertEquals(referenceGraph, graph, excludeVirtual, true);
    }
}
