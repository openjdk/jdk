/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class FloatArraysEqualsTest extends GraalCompilerTest {

    public static boolean testFloatArraySnippet(float[] a, float[] b) {
        return Arrays.equals(a, b);
    }

    private void testEqualInFloatArray(int arraySize, int index, float f1, float f2) {
        if (arraySize > 0 && index >= 0 && index < arraySize) {
            float[] a1 = new float[arraySize];
            float[] a2 = new float[arraySize];
            a1[index] = f1;
            a2[index] = f2;
            test("testFloatArraySnippet", a1, a2);
        }
    }

    public static final int FLOAT_TAIL = 1;
    public static final int FLOAT_8BYTE_ALIGNED = 2;
    public static final int FLOAT_8BYTE_UNALIGNED = 3;
    public static final int FLOAT_VECTOR_ALIGNED = 8;
    public static final int FLOAT_VECTOR_UNALIGNED = 9;

    @Test
    public void testFloatArray() {
        for (int size : new int[]{FLOAT_TAIL, FLOAT_8BYTE_ALIGNED, FLOAT_8BYTE_UNALIGNED, FLOAT_VECTOR_ALIGNED, FLOAT_VECTOR_UNALIGNED}) {
            for (int index : new int[]{0, size - 1}) {
                testEqualInFloatArray(size, index, 1024, 1024);
                testEqualInFloatArray(size, index, 0.0f, -0.0f);
                testEqualInFloatArray(size, index, Float.intBitsToFloat(0x7fc00000), Float.intBitsToFloat(0x7fc00000));
                testEqualInFloatArray(size, index, Float.intBitsToFloat(0x7fc00000), Float.intBitsToFloat(0x7f800001));
                testEqualInFloatArray(size, index, Float.intBitsToFloat(0x7fc00000), 1024);
            }
        }
    }

    public static boolean testDoubleArraySnippet(double[] a, double[] b) {
        return Arrays.equals(a, b);
    }

    public static final int DOUBLE_8BYTE_ALIGNED = 1;
    public static final int DOUBLE_VECTOR_ALIGNED = 4;
    public static final int DOUBLE_VECTOR_UNALIGNED = 5;

    private void testEqualInDoubleArray(int arraySize, int index, double d1, double d2) {
        if (arraySize > 0 && index >= 0 && index < arraySize) {
            double[] a1 = new double[arraySize];
            double[] a2 = new double[arraySize];
            a1[index] = d1;
            a2[index] = d2;
            test("testDoubleArraySnippet", a1, a2);
        }
    }

    @Test
    public void testDoubleArrayOrdinary() {
        for (int size : new int[]{DOUBLE_8BYTE_ALIGNED, DOUBLE_VECTOR_ALIGNED, DOUBLE_VECTOR_UNALIGNED}) {
            for (int index : new int[]{0, size - 1}) {
                testEqualInDoubleArray(size, index, 1024, 1024);
                testEqualInDoubleArray(size, index, 0.0d, -0.0d);
                testEqualInDoubleArray(size, index, Double.longBitsToDouble(0x7ff8000000000000L), Double.longBitsToDouble(0x7ff8000000000000L));
                testEqualInDoubleArray(size, index, Double.longBitsToDouble(0x7ff8000000000000L), Double.longBitsToDouble(0x7ff0000000000001L));
                testEqualInDoubleArray(size, index, Double.longBitsToDouble(0x7ff8000000000000L), 1024);
            }
        }
    }

    public static boolean testFloatArrayWithPEASnippet0() {
        return Arrays.equals(new float[]{0.0f}, new float[]{-0.0f});
    }

    public static boolean testFloatArrayWithPEASnippet1() {
        return Arrays.equals(new float[]{Float.intBitsToFloat(0x7fc00000)}, new float[]{Float.intBitsToFloat(0x7fc00000)});
    }

    public static boolean testFloatArrayWithPEASnippet2() {
        return Arrays.equals(new float[]{Float.intBitsToFloat(0x7fc00000)}, new float[]{Float.intBitsToFloat(0x7f800001)});

    }

    @Test
    public void testFloatArrayWithPEA() {
        test("testFloatArrayWithPEASnippet0");
        test("testFloatArrayWithPEASnippet1");
        test("testFloatArrayWithPEASnippet2");
    }

    public static boolean testDoubleArrayWithPEASnippet0() {
        return Arrays.equals(new double[]{0.0d}, new double[]{-0.0d});
    }

    public static boolean testDoubleArrayWithPEASnippet1() {
        return Arrays.equals(new double[]{Double.longBitsToDouble(0x7ff8000000000000L)}, new double[]{Double.longBitsToDouble(0x7ff8000000000000L)});
    }

    public static boolean testDoubleArrayWithPEASnippet2() {
        return Arrays.equals(new double[]{Double.longBitsToDouble(0x7ff8000000000000L)}, new double[]{Double.longBitsToDouble(0x7ff0000000000001L)});
    }

    @Test
    public void testDoubleArrayWithPEA() {
        test("testDoubleArrayWithPEASnippet0");
        test("testDoubleArrayWithPEASnippet1");
        test("testDoubleArrayWithPEASnippet2");
    }

    public static final float[] FLOAT_ARRAY1 = new float[]{0.0f};
    public static final float[] FLOAT_ARRAY2 = new float[]{-0.0f};
    public static final float[] FLOAT_ARRAY3 = new float[]{Float.intBitsToFloat(0x7fc00000)};
    public static final float[] FLOAT_ARRAY4 = new float[]{Float.intBitsToFloat(0x7f800001)};

    public static final double[] DOUBLE_ARRAY1 = new double[]{0.0d};
    public static final double[] DOUBLE_ARRAY2 = new double[]{-0.0d};
    public static final double[] DOUBLE_ARRAY3 = new double[]{Double.longBitsToDouble(0x7ff8000000000000L)};
    public static final double[] DOUBLE_ARRAY4 = new double[]{Double.longBitsToDouble(0x7ff0000000000001L)};

    public static boolean testStableFloatArraySnippet0() {
        return Arrays.equals(FLOAT_ARRAY1, FLOAT_ARRAY2);
    }

    public static boolean testStableFloatArraySnippet1() {
        return Arrays.equals(FLOAT_ARRAY1, FLOAT_ARRAY2);
    }

    public static boolean testStableDoubleArraySnippet0() {
        return Arrays.equals(DOUBLE_ARRAY1, DOUBLE_ARRAY2);
    }

    public static boolean testStableDoubleArraySnippet1() {
        return Arrays.equals(DOUBLE_ARRAY3, DOUBLE_ARRAY4);
    }

    public void testStableArray(String methodName) {
        ResolvedJavaMethod method = getResolvedJavaMethod(methodName);
        Result expected = executeExpected(method, null);

        StructuredGraph graph = parseEager(method, AllowAssumptions.YES);

        for (ConstantNode constantNode : graph.getNodes().filter(ConstantNode.class).snapshot()) {
            if (getConstantReflection().readArrayLength(constantNode.asJavaConstant()) != null) {
                ConstantNode newConstantNode = ConstantNode.forConstant(constantNode.asJavaConstant(), 1, true, getMetaAccess());
                newConstantNode = graph.unique(newConstantNode);
                constantNode.replaceAndDelete(newConstantNode);
            }
        }

        CompilationResult result = compile(method, graph);
        InstalledCode code = addMethod(graph.getDebug(), method, result);

        Result actual;

        try {
            actual = new Result(code.executeVarargs(), null);
        } catch (Exception e) {
            actual = new Result(null, e);
        }

        assertEquals(expected, actual);
    }

    @Test
    public void testStableArray() {
        testStableArray("testStableFloatArraySnippet0");
        testStableArray("testStableFloatArraySnippet1");
        testStableArray("testStableDoubleArraySnippet0");
        testStableArray("testStableDoubleArraySnippet1");
    }

}
