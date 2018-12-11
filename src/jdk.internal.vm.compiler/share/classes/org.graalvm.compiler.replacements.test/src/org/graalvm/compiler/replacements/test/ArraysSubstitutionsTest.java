/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.test;

import static org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;

import java.util.Arrays;

import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assert;
import org.junit.Test;

public class ArraysSubstitutionsTest extends ArraysSubstitutionsTestBase {

    private static final int N = 10;

    private void testEquals(String methodName, Class<?>[] parameterTypes, ArrayBuilder builder) {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;
        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = builder.newArray(i, 0, 1);
            args2[n] = builder.newArray(i, 0, 1);
        }
        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = builder.newArray(i, 0, 1);
            args2[n] = builder.newArray(i, 1, 1);
        }
        testSubstitution(methodName, ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, false, args1, args2);
    }

    @Test
    public void testEqualsBoolean() {
        testEquals("arraysEqualsBoolean", new Class<?>[]{boolean[].class, boolean[].class}, ArraysSubstitutionsTestBase::booleanArray);
    }

    @Test
    public void testEqualsByte() {
        testEquals("arraysEqualsByte", new Class<?>[]{byte[].class, byte[].class}, ArraysSubstitutionsTestBase::byteArray);
    }

    @Test
    public void testEqualsChar() {
        testEquals("arraysEqualsChar", new Class<?>[]{char[].class, char[].class}, ArraysSubstitutionsTestBase::charArray);
    }

    @Test
    public void testEqualsShort() {
        testEquals("arraysEqualsShort", new Class<?>[]{short[].class, short[].class}, ArraysSubstitutionsTestBase::shortArray);
    }

    @Test
    public void testEqualsInt() {
        testEquals("arraysEqualsInt", new Class<?>[]{int[].class, int[].class}, ArraysSubstitutionsTestBase::intArray);
    }

    @Test
    public void testEqualsLong() {
        testEquals("arraysEqualsLong", new Class<?>[]{long[].class, long[].class}, ArraysSubstitutionsTestBase::longArray);
    }

    @Test
    public void testEqualsNodeGVN() {
        test("testEqualsNodeGVNSnippet", true);
    }

    public static int[] intArrayCompare = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
    public static int[] intArray;

    public static boolean testEqualsNodeGVNSnippet(boolean b) {
        int[] newIntArray = new int[]{0, 2, 3, 4, 5, 6, 7, 8, 9};
        intArray = newIntArray;

        if (b) {
            newIntArray[0] = 1;
            return Arrays.equals(newIntArray, intArrayCompare);
        } else {
            newIntArray[0] = 1;
            return Arrays.equals(newIntArray, intArrayCompare);
        }
    }

    @Test
    public void testConstants() {
        testGraph("testConstantsSnippet");
    }

    public static final int[] constantArray1 = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
    public static final int[] constantArray2 = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};

    public static boolean testConstantsSnippet() {
        constantArray2[0] = 10;
        try {
            return Arrays.equals(constantArray1, constantArray2);
        } finally {
            constantArray2[0] = 1;
        }
    }

    @Test
    public void testCanonicalLength() {
        StructuredGraph graph = parseEager("testCanonicalLengthSnippet", AllowAssumptions.NO);
        HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        createInliningPhase().apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));

        Assert.assertTrue(graph.getNodes(ReturnNode.TYPE).first().result().asJavaConstant().asLong() == 0);
    }

    public static final int[] constantArray3 = new int[]{1, 2, 3};

    public static boolean testCanonicalLengthSnippet() {
        return Arrays.equals(constantArray1, constantArray3);
    }

    @Test
    public void testCanonicalEqual() {
        StructuredGraph graph = parseEager("testCanonicalEqualSnippet", AllowAssumptions.NO);
        HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        createInliningPhase().apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));

        Assert.assertTrue(graph.getNodes(ReturnNode.TYPE).first().result().asJavaConstant().asLong() == 1);
    }

    public static boolean testCanonicalEqualSnippet() {
        return Arrays.equals(constantArray1, constantArray1);
    }

    @Test
    public void testVirtualEqual() {
        StructuredGraph graph = parseEager("testVirtualEqualSnippet", AllowAssumptions.NO);
        HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        createInliningPhase().apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new PartialEscapePhase(false, new CanonicalizerPhase(), graph.getOptions()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));

        Assert.assertTrue(graph.getNodes(ReturnNode.TYPE).first().result().asJavaConstant().asLong() == 1);
    }

    public static boolean testVirtualEqualSnippet() {
        int[] array1 = new int[]{1, 2, 3, 4};
        int[] array2 = new int[]{1, 2, 3, 4};
        return Arrays.equals(array1, array2);
    }

    @Test
    public void testVirtualNotEqual() {
        StructuredGraph graph = parseEager("testVirtualNotEqualSnippet", AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        createInliningPhase().apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new PartialEscapePhase(false, new CanonicalizerPhase(), graph.getOptions()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));

        Assert.assertTrue(graph.getNodes(ReturnNode.TYPE).first().result().asJavaConstant().asLong() == 0);
    }

    public static boolean testVirtualNotEqualSnippet(int x) {
        int[] array1 = new int[]{1, 2, 100, x};
        int[] array2 = new int[]{1, 2, 3, 4};
        return Arrays.equals(array1, array2);
    }
}
