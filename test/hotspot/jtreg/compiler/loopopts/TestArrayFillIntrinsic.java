/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts;

import java.lang.foreign.*;
import java.util.*;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/**
 * @test
 * @bug 8351468
 * @summary Test replacement of array-filling loops with intrinsic calls in the
 *          face of matching and mismatching stores.
 * @library /test/lib /
 * @run driver compiler.loopopts.TestArrayFillIntrinsic
 */

public class TestArrayFillIntrinsic {

    public static void main(String[] args) {
        // Disabling unrolling is necessary for test robustness, otherwise the
        // compiler might decide to unroll the array-filling loop instead of
        // replacing it with an intrinsic call even if OptimizeFill is enabled.
        TestFramework framework = new TestFramework();
        framework.addScenarios(new Scenario(0),
                               new Scenario(1, "-XX:LoopUnrollLimit=0", "-XX:+OptimizeFill"));
        framework.start();
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfAnd = {"LoopUnrollLimit", "0", "OptimizeFill", "true"},
        counts = {IRNode.CALL_OF, "(arrayof_)?jbyte_fill", "1"})
    static void testFillBooleanArray(boolean[] array, boolean val) {
        for (int i = 0; i < array.length; i++) {
            array[i] = val;
        }
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfAnd = {"LoopUnrollLimit", "0", "OptimizeFill", "true"},
        counts = {IRNode.CALL_OF, "(arrayof_)?jbyte_fill", "1"})
    static void testFillByteArray(byte[] array, byte val) {
        for (int i = 0; i < array.length; i++) {
            array[i] = val;
        }
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfAnd = {"LoopUnrollLimit", "0", "OptimizeFill", "true"},
        counts = {IRNode.CALL_OF, "(arrayof_)?jshort_fill", "1"})
    static void testFillCharArray(char[] array, char val) {
        for (int i = 0; i < array.length; i++) {
            array[i] = val;
        }
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfAnd = {"LoopUnrollLimit", "0", "OptimizeFill", "true"},
        counts = {IRNode.CALL_OF, "(arrayof_)?jshort_fill", "1"})
    static void testFillShortArray(short[] array, short val) {
        for (int i = 0; i < array.length; i++) {
            array[i] = val;
        }
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfAnd = {"LoopUnrollLimit", "0", "OptimizeFill", "true"},
        counts = {IRNode.CALL_OF, "(arrayof_)?jint_fill", "1"})
    static void testFillIntArray(int[] array, int val) {
        for (int i = 0; i < array.length; i++) {
            array[i] = val;
        }
    }

    @Test
    @IR(applyIfPlatformOr = {"x64", "true", "aarch64", "true", "riscv64", "true"},
        applyIfAnd = {"LoopUnrollLimit", "0", "OptimizeFill", "true"},
        counts = {IRNode.CALL_OF, "(arrayof_)?jint_fill", "1"})
    static void testFillFloatArray(float[] array, float val) {
        for (int i = 0; i < array.length; i++) {
            array[i] = val;
        }
    }

    @Run(test = {"testFillByteArray",
                 "testFillBooleanArray",
                 "testFillCharArray",
                 "testFillShortArray",
                 "testFillIntArray",
                 "testFillFloatArray"})
    public void runPositiveTests() {
        Random r = RunInfo.getRandom();
        int N = r.ints(1, 1024).findFirst().getAsInt();
        {
            boolean[] array = new boolean[N];
            boolean val = r.nextBoolean();
            testFillBooleanArray(array, val);
            for (int i = 0; i < array.length; i++) {
                Asserts.assertEquals(val, array[i]);
            }
        }
        {
            byte[] array = new byte[N];
            byte val = (byte)r.nextInt();
            testFillByteArray(array, val);
            for (int i = 0; i < array.length; i++) {
                Asserts.assertEquals(val, array[i]);
            }
        }
        {
            char[] array = new char[N];
            char val = (char)r.nextInt();
            testFillCharArray(array, val);
            for (int i = 0; i < array.length; i++) {
                Asserts.assertEquals(val, array[i]);
            }
        }
        {
            short[] array = new short[N];
            short val = (short)r.nextInt();
            testFillShortArray(array, val);
            for (int i = 0; i < array.length; i++) {
                Asserts.assertEquals(val, array[i]);
            }
        }
        {
            int[] array = new int[N];
            int val = r.nextInt();
            testFillIntArray(array, val);
            for (int i = 0; i < array.length; i++) {
                Asserts.assertEquals(val, array[i]);
            }
        }
        {
            float[] array = new float[N];
            float val = r.nextFloat();
            testFillFloatArray(array, val);
            for (int i = 0; i < array.length; i++) {
                Asserts.assertEquals(val, array[i]);
            }
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF, "(arrayof_)?.*_fill"})
    static void testFillShortArrayWithByte(MemorySegment array, int n, byte val) {
        for (int i = 0; i < n; i++) {
            array.setAtIndex(ValueLayout.JAVA_BYTE, i, val);
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF, "(arrayof_)?.*_fill"})
    static void testFillIntArrayWithByte(MemorySegment array, int n, byte val) {
        for (int i = 0; i < n; i++) {
            array.setAtIndex(ValueLayout.JAVA_BYTE, i, val);
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF, "(arrayof_)?.*_fill"})
    static void testFillIntArrayWithShort(MemorySegment array, int n, short val) {
        for (int i = 0; i < n; i++) {
            array.setAtIndex(ValueLayout.JAVA_SHORT, i, val);
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF, "(arrayof_)?.*_fill"})
    static void testFillByteArrayWithBoolean(MemorySegment array, int n, boolean val) {
        for (int i = 0; i < n; i++) {
            array.setAtIndex(ValueLayout.JAVA_BOOLEAN, i, val);
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF, "(arrayof_)?.*_fill"})
    static void testFillShortArrayWithChar(MemorySegment array, int n, char val) {
        for (int i = 0; i < n; i++) {
            array.setAtIndex(ValueLayout.JAVA_CHAR, i, val);
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF, "(arrayof_)?.*_fill"})
    static void testFillCharArrayWithShort(MemorySegment array, int n, short val) {
        for (int i = 0; i < n; i++) {
            array.setAtIndex(ValueLayout.JAVA_SHORT, i, val);
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF, "(arrayof_)?.*_fill"})
    static void testFillIntArrayWithFloat(MemorySegment array, int n, float val) {
        for (int i = 0; i < n; i++) {
            array.setAtIndex(ValueLayout.JAVA_FLOAT, i, val);
        }
    }

    @Test
    @IR(failOn = {IRNode.CALL_OF, "(arrayof_)?.*_fill"})
    static void testFillFloatArrayWithInt(MemorySegment array, int n, int val) {
        for (int i = 0; i < n; i++) {
            array.setAtIndex(ValueLayout.JAVA_INT, i, val);
        }
    }

    @Run(test = {"testFillShortArrayWithByte",
                 "testFillIntArrayWithByte",
                 "testFillIntArrayWithShort",
                 "testFillByteArrayWithBoolean",
                 "testFillShortArrayWithChar",
                 "testFillCharArrayWithShort",
                 "testFillIntArrayWithFloat",
                 "testFillFloatArrayWithInt"})
    public void runTypeMismatchTests() {
        Random r = RunInfo.getRandom();
        int N = r.ints(1, 1024).findFirst().getAsInt();
        testFillShortArrayWithByte(MemorySegment.ofArray(new short[N]), N,
                                   (byte)r.nextInt());
        testFillIntArrayWithByte(MemorySegment.ofArray(new int[N]), N,
                                 (byte)r.nextInt());
        testFillIntArrayWithShort(MemorySegment.ofArray(new int[N]), N,
                                  (short)r.nextInt());
        testFillByteArrayWithBoolean(MemorySegment.ofArray(new byte[N]), N,
                                     r.nextBoolean());
        testFillShortArrayWithChar(MemorySegment.ofArray(new short[N]), N,
                                   (char)r.nextInt());
        testFillCharArrayWithShort(MemorySegment.ofArray(new char[N]), N,
                                   (short)r.nextInt());
        testFillIntArrayWithFloat(MemorySegment.ofArray(new int[N]), N,
                                  r.nextFloat());
        testFillFloatArrayWithInt(MemorySegment.ofArray(new float[N]), N,
                                  r.nextInt());
    }
}
