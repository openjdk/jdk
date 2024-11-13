/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @bug 8304042
 * @summary Test some examples with independent packs with cyclic dependency
 *          between the packs.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestIndependentPacksWithCyclicDependency
 */

package compiler.loopopts.superword;

import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

public class TestIndependentPacksWithCyclicDependency {
    static final int RANGE = 1024;
    static final int ITER  = 10_000;
    static Unsafe unsafe = Unsafe.getUnsafe();

    int[]   goldI0 = new int[RANGE];
    float[] goldF0 = new float[RANGE];
    int[]   goldI1 = new int[RANGE];
    float[] goldF1 = new float[RANGE];
    int[]   goldI2 = new int[RANGE];
    float[] goldF2 = new float[RANGE];
    int[]   goldI3 = new int[RANGE];
    float[] goldF3 = new float[RANGE];
    int[]   goldI4 = new int[RANGE];
    float[] goldF4 = new float[RANGE];
    int[]   goldI5 = new int[RANGE];
    float[] goldF5 = new float[RANGE];
    int[]   goldI6 = new int[RANGE];
    float[] goldF6 = new float[RANGE];
    long[]  goldL6 = new long[RANGE];
    int[]   goldI7 = new int[RANGE];
    float[] goldF7 = new float[RANGE];
    long[]  goldL7 = new long[RANGE];
    int[]   goldI8 = new int[RANGE];
    float[] goldF8 = new float[RANGE];
    long[]  goldL8 = new long[RANGE];
    int[]   goldI9 = new int[RANGE];
    float[] goldF9 = new float[RANGE];
    long[]  goldL9 = new long[RANGE];
    int[]   goldI10 = new int[RANGE];
    float[] goldF10 = new float[RANGE];
    long[]  goldL10 = new long[RANGE];

    public static void main(String args[]) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                                   "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestIndependentPacksWithCyclicDependency::test*",
                                   "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestIndependentPacksWithCyclicDependency::verify",
                                   "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestIndependentPacksWithCyclicDependency::init",
                                   "-XX:+IgnoreUnrecognizedVMOptions", "-XX:LoopUnrollLimit=1000");
    }

    TestIndependentPacksWithCyclicDependency() {
        // compute the gold standard in interpreter mode
        init(goldI0, goldF0);
        test0(goldI0, goldI0, goldF0, goldF0);
        init(goldI1, goldF1);
        test1(goldI1, goldI1, goldF1, goldF1);
        init(goldI2, goldF2);
        test2(goldI2, goldI2, goldF2, goldF2);
        init(goldI3, goldF3);
        test3(goldI3, goldI3, goldF3, goldF3);
        init(goldI4, goldF4);
        test4(goldI4, goldI4, goldF4, goldF4);
        init(goldI5, goldF5);
        test5(goldI5, goldI5, goldF5, goldF5);
        init(goldI6, goldF6, goldL6);
        test6(goldI6, goldI6, goldF6, goldF6, goldL6, goldL6);
        init(goldI7, goldF7, goldL7);
        test7(goldI7, goldI7, goldF7, goldF7, goldL7, goldL7);
        init(goldI8, goldF8, goldL8);
        test8(goldI8, goldI8, goldF8, goldF8, goldL8, goldL8);
        init(goldI9, goldF9, goldL9);
        test9(goldI9, goldI9, goldF9, goldF9, goldL9, goldL9);
        init(goldI10, goldF10, goldL10);
        test10(goldI10, goldI10, goldF10, goldF10, goldL10, goldL10);
    }

    @Run(test = "test0")
    @Warmup(100)
    public void runTest0() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test0(dataI, dataI, dataF, dataF);
        verify("test0", dataI, goldI0);
        verify("test0", dataF, goldF0);
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0", IRNode.MUL_VF, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test0(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb) {
        for (int i = 0; i < RANGE; i+=2) {
            // Hand-unrolled 2x. Int and Float slice are completely separate.
            dataIb[i+0] = dataIa[i+0] + 3;
            dataIb[i+1] = dataIa[i+1] + 3;
            dataFb[i+0] = dataFa[i+0] * 1.3f;
            dataFb[i+1] = dataFa[i+1] * 1.3f;
        }
    }

    @Run(test = "test1")
    @Warmup(100)
    public void runTest1() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test1(dataI, dataI, dataF, dataF);
        verify("test1", dataI, goldI1);
        verify("test1", dataF, goldF1);
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0", IRNode.MUL_VF, "> 0", IRNode.VECTOR_CAST_F2I, "> 0", IRNode.VECTOR_CAST_I2F, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    static void test1(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb) {
        for (int i = 0; i < RANGE; i+=2) {
            // Hand-unrolled 2x. Converst to and from. StoreF -> LoadF dependency.
            dataFa[i+0] = dataIa[i+0] + 3;
            dataFa[i+1] = dataIa[i+1] + 3;
            dataIb[i+0] = (int)(dataFb[i+0] * 1.3f);
            dataIb[i+1] = (int)(dataFb[i+1] * 1.3f);
        }
    }

    @Run(test = "test2")
    public void runTest2() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test2(dataI, dataI, dataF, dataF);
        verify("test2", dataI, goldI2);
        verify("test2", dataF, goldF2);
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0", IRNode.MUL_VI, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test2(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb) {
        for (int i = 0; i < RANGE; i+=2) {
            // int and float arrays are two slices. But we pretend both are of type int.
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0, dataIa[i+0] + 1);
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4, dataIa[i+1] + 1);
            dataIb[i+0] = 11 * unsafe.getInt(dataFb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0);
            dataIb[i+1] = 11 * unsafe.getInt(dataFb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4);
        }
    }

    @Run(test = "test3")
    @Warmup(100)
    public void runTest3() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test3(dataI, dataI, dataF, dataF);
        verify("test3", dataI, goldI3);
        verify("test3", dataF, goldF3);
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0", IRNode.MUL_VF, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test3(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb) {
        for (int i = 0; i < RANGE; i+=2) {
            // Inversion of orders. But because we operate on separate slices, this should
            // safely vectorize. It should detect that each line is independent, so it can
            // reorder them.
            dataIb[i+0] = dataIa[i+0] + 3;
            dataFb[i+1] = dataFa[i+1] * 1.3f;
            dataFb[i+0] = dataFa[i+0] * 1.3f;
            dataIb[i+1] = dataIa[i+1] + 3;
        }
    }

    @Run(test = "test4")
    @Warmup(100)
    public void runTest4() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test4(dataI, dataI, dataF, dataF);
        verify("test4", dataI, goldI4);
        verify("test4", dataF, goldF4);
    }

    @Test
    static void test4(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb) {
        for (int i = 0; i < RANGE; i+=2) {
            // same as test1, except that reordering leads to different semantics
            // [A,B] and [X,Y] are both packs that are internally independent
            // But we have dependencies A -> X (StoreF -> LoadF)
            //                      and Y -> B (StoreI -> LoadI)
            // Hence the two packs have a cyclic dependency, we cannot schedule
            // one before the other.
            dataFa[i+0] = dataIa[i+0] + 3;            // A
            dataIb[i+0] = (int)(dataFb[i+0] * 1.3f);  // X
            dataIb[i+1] = (int)(dataFb[i+1] * 1.3f);  // Y
            dataFa[i+1] = dataIa[i+1] + 3;            // B
        }
    }

    @Run(test = "test5")
    public void runTest5() {
        int[] dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        init(dataI, dataF);
        test5(dataI, dataI, dataF, dataF);
        verify("test5", dataI, goldI5);
        verify("test5", dataF, goldF5);
    }

    @Test
    static void test5(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb) {
        for (int i = 0; i < RANGE; i+=2) {
            // same as test2, except that reordering leads to different semantics
            // explanation analogue to test4
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0, dataIa[i+0] + 1); // A
            dataIb[i+0] = 11 * unsafe.getInt(dataFb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0); // X
            dataIb[i+1] = 11 * unsafe.getInt(dataFb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4); // Y
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4, dataIa[i+1] + 1); // B
        }
    }

    @Run(test = "test6")
    public void runTest6() {
        int[]   dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        long[]  dataL = new long[RANGE];
        init(dataI, dataF, dataL);
        test6(dataI, dataI, dataF, dataF, dataL, dataL);
        verify("test6", dataI, goldI6);
        verify("test6", dataF, goldF6);
        verify("test6", dataL, goldL6);
    }

    @Test
    @IR(counts = {IRNode.ADD_VI, "> 0", IRNode.MUL_VI, "> 0", IRNode.ADD_VF, "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test6(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb,
                      long[] dataLa, long[] dataLb) {
        for (int i = 0; i < RANGE; i+=2) {
            // Chain of parallelizable op and conversion
            int v00 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0) + 3;
            int v01 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4) + 3;
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0, v00);
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4, v01);
            int v10 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0) * 45;
            int v11 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4) * 45;
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0, v10);
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4, v11);
            float v20 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0) + 0.55f;
            float v21 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4) + 0.55f;
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0, v20);
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4, v21);
        }
    }

    @Run(test = "test7")
    public void runTest7() {
        int[]   dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        long[]  dataL = new long[RANGE];
        init(dataI, dataF, dataL);
        test7(dataI, dataI, dataF, dataF, dataL, dataL);
        verify("test7", dataI, goldI7);
        verify("test7", dataF, goldF7);
        verify("test7", dataL, goldL7);
    }

    @Test
    static void test7(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb,
                      long[] dataLa, long[] dataLb) {
        for (int i = 0; i < RANGE; i+=2) {
            // Cycle involving 3 memory slices
            int v00 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0) + 3;
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0, v00);
            int v10 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0) * 45;
            int v11 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4) * 45;
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0, v10);
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4, v11);
            float v20 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0) + 0.55f;
            float v21 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4) + 0.55f;
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0, v20);
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4, v21);
            int v01 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4) + 3; // moved down
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4, v01);
        }
    }


    @Run(test = "test8")
    public void runTest8() {
        int[]   dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        long[]  dataL = new long[RANGE];
        init(dataI, dataF, dataL);
        test8(dataI, dataI, dataF, dataF, dataL, dataL);
        verify("test8", dataI, goldI8);
        verify("test8", dataF, goldF8);
        verify("test8", dataL, goldL8);
    }

    @Test
    static void test8(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb,
                      long[] dataLa, long[] dataLb) {
        for (int i = 0; i < RANGE; i+=2) {
            // 2-cycle, with more ops after
            int v00 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0) + 3;
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0, v00);
            int v10 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0) * 45;
            int v11 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4) * 45;
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0, v10);
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4, v11);
            int v01 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4) + 3;
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4, v01);
            // more stuff after
            float v20 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0) + 0.55f;
            float v21 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4) + 0.55f;
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0, v20);
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4, v21);
        }
    }

    @Run(test = "test9")
    public void runTest9() {
        int[]   dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        long[]  dataL = new long[RANGE];
        init(dataI, dataF, dataL);
        test9(dataI, dataI, dataF, dataF, dataL, dataL);
        verify("test9", dataI, goldI9);
        verify("test9", dataF, goldF9);
        verify("test9", dataL, goldL9);
    }

    @Test
    static void test9(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb,
                      long[] dataLa, long[] dataLb) {
        for (int i = 0; i < RANGE; i+=2) {
            // 2-cycle, with more stuff before
            float v20 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0) + 0.55f;
            float v21 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4) + 0.55f;
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0, v20);
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4, v21);
            // 2-cycle
            int v00 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0) + 3;
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0, v00);
            int v10 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0) * 45;
            int v11 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4) * 45;
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0, v10);
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4, v11);
            int v01 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4) + 3;
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4, v01);
        }
    }

    @Run(test = "test10")
    public void runTest10() {
        int[]   dataI = new int[RANGE];
        float[] dataF = new float[RANGE];
        long[]  dataL = new long[RANGE];
        init(dataI, dataF, dataL);
        test10(dataI, dataI, dataF, dataF, dataL, dataL);
        verify("test10", dataI, goldI10);
        verify("test10", dataF, goldF10);
        verify("test10", dataL, goldL10);
    }

    @Test
    static void test10(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb,
                      long[] dataLa, long[] dataLb) {
        for (int i = 0; i < RANGE; i+=2) {
            // This creates the following graph before SuperWord:
            //
            // A -> R -> U
            //      S -> V -> B
            //
            // SuperWord analyzes the graph, and sees that [A,B] and [U,V]
            // are adjacent, isomorphic and independent packs. However,
            // [R,S] are not isomorphic (R mul, S add).
            // So it vectorizes [A,B] and [U,V] this gives us this graph:
            //
            //        -> R
            //  [A,B]      -> [U,V] -+
            //    ^   -> S           |
            //    |                  |
            //    +------------------+
            //
            // The cycle thus does not only go via packs, but also scalar ops.
            //
            int v00 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0) + 3; // A
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0, v00);
            int v10 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 0) * 45; // R: constant mismatch
            int v11 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4) + 43; // S
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0, v10);
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4, v11);
            float v20 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 0) + 0.55f; // U
            float v21 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4L * i + 4) + 0.55f; // V
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 0, v20);
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4, v21);
            int v01 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4L * i + 4) + 3; // B: moved down
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i + 4, v01);
        }
    }

    static void init(int[] dataI, float[] dataF) {
        for (int i = 0; i < RANGE; i++) {
            dataI[i] = i + 1;
            dataF[i] = i + 0.1f;
        }
    }

    static void init(int[] dataI, float[] dataF, long[] dataL) {
        for (int i = 0; i < RANGE; i++) {
            dataI[i] = i + 1;
            dataF[i] = i + 0.1f;
            dataL[i] = i + 1;
        }
    }

    static void verify(String name, int[] data, int[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: dataI[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, float[] data, float[] gold) {
        for (int i = 0; i < RANGE; i++) {
            int datav = unsafe.getInt(data, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i);
            int goldv = unsafe.getInt(gold, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4L * i);
            if (datav != goldv) {
                throw new RuntimeException(" Invalid " + name + " result: dataF[" + i + "]: " + datav + " != " + goldv);
            }
        }
    }

    static void verify(String name, long[] data, long[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: dataL[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
}

