/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.replacements.ArraysSubstitutions;
import org.graalvm.compiler.replacements.nodes.ArrayEqualsNode;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;

/**
 * Tests {@link ArraysSubstitutions}.
 */
public class ArraysSubstitutionsTest extends MethodSubstitutionTest {

    private static final int N = 10;

    @Test
    public void testEqualsBoolean() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new boolean[i];
            args2[n] = new boolean[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            boolean[] a2 = new boolean[i];
            if (i > 0) {
                a2[i - 1] = true;
            }
            args1[n] = new boolean[i];
            args2[n] = a2;
        }
        Class<?>[] parameterTypes = new Class<?>[]{boolean[].class, boolean[].class};
        testSubstitution("arraysEqualsBoolean", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsBoolean(boolean[] a, boolean[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsByte() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new byte[i];
            args2[n] = new byte[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            byte[] a2 = new byte[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new byte[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{byte[].class, byte[].class};
        testSubstitution("arraysEqualsByte", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsByte(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsChar() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new char[i];
            args2[n] = new char[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            char[] a2 = new char[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new char[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{char[].class, char[].class};
        testSubstitution("arraysEqualsChar", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsChar(char[] a, char[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsShort() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new short[i];
            args2[n] = new short[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            short[] a2 = new short[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new short[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{short[].class, short[].class};
        testSubstitution("arraysEqualsShort", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsShort(short[] a, short[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsInt() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new int[i];
            args2[n] = new int[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            int[] a2 = new int[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new int[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{int[].class, int[].class};
        testSubstitution("arraysEqualsInt", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsInt(int[] a, int[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsLong() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new long[i];
            args2[n] = new long[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            long[] a2 = new long[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new long[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{long[].class, long[].class};
        testSubstitution("arraysEqualsLong", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsLong(long[] a, long[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsFloat() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new float[i];
            args2[n] = new float[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            float[] a2 = new float[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new float[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{float[].class, float[].class};
        testSubstitution("arraysEqualsFloat", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsFloat(float[] a, float[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsDouble() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new double[i];
            args2[n] = new double[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            double[] a2 = new double[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new double[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{double[].class, double[].class};
        testSubstitution("arraysEqualsDouble", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsDouble(double[] a, double[] b) {
        return Arrays.equals(a, b);
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
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
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
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
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
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new PartialEscapePhase(false, new CanonicalizerPhase()).apply(graph, context);
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
        new InliningPhase(new CanonicalizerPhase()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));
        new PartialEscapePhase(false, new CanonicalizerPhase()).apply(graph, context);
        new CanonicalizerPhase().apply(graph, new PhaseContext(getProviders()));

        Assert.assertTrue(graph.getNodes(ReturnNode.TYPE).first().result().asJavaConstant().asLong() == 0);
    }

    public static boolean testVirtualNotEqualSnippet(int x) {
        int[] array1 = new int[]{1, 2, 100, x};
        int[] array2 = new int[]{1, 2, 3, 4};
        return Arrays.equals(array1, array2);
    }
}
