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
 * @bug 8304720
 * @summary Test some examples where non-vectorized memops also need to
 *          be reordered during SuperWord::schedule.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestScheduleReordersScalarMemops
 */

package compiler.loopopts.superword;

import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

public class TestScheduleReordersScalarMemops {
    static final int RANGE = 1024;
    static final int ITER  = 10_000;
    static Unsafe unsafe = Unsafe.getUnsafe();

    int[]   goldI0 = new int[RANGE];
    float[] goldF0 = new float[RANGE];
    int[]   goldI1 = new int[RANGE];
    float[] goldF1 = new float[RANGE];

    public static void main(String args[]) {
        TestFramework.runWithFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                                   "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestScheduleReordersScalarMemops::test*",
                                   "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestScheduleReordersScalarMemops::verify",
                                   "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestScheduleReordersScalarMemops::init",
                                   "-XX:-TieredCompilation", "-Xbatch",
                                   "-XX:+IgnoreUnrecognizedVMOptions", "-XX:LoopUnrollLimit=1000");
    }

    TestScheduleReordersScalarMemops() {
        // compute the gold standard in interpreter mode
        init(goldI0, goldF0);
        test0(goldI0, goldI0, goldF0, goldF0);
        init(goldI1, goldF1);
        test1(goldI1, goldI1, goldF1, goldF1);
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
    @IR(counts = {IRNode.MUL_VI, "> 0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    static void test0(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb) {
        for (int i = 0; i < RANGE; i+=2) {
            // We have dependency edges:
            //  A -> X
            //  Y -> B
            // Still, we can vectorize [X,Y].
            // We do not vectorize A and B, because they are not isomorphic (add vs mul).
            //
            // Imagine this is unrolled at least 2x.
            // We get order: A0 X0 Y0 B0 A1 X1 Y1 B1
            // Vectorized:      X0 Y0       X1 Y1
            // Scalar:       A0       B0 A1       B1
            //
            // However, since the As need to be before, and the Bs after the vector operations,
            // we need to have all As before all Bs. This means we need to reorder the scalar
            // operations, and not just the vectorized ones.
            //
            // A correct reordering would be: A0 A1 [X0, Y0, X1, Y1] B0 B1
            //
            dataFa[i + 0] = dataIa[i + 0] * 1.3f;     // A *1.3
            dataIb[i + 0] = (int)dataFb[i + 0] * 11;  // X *11
            dataIb[i + 1] = (int)dataFb[i + 1] * 11;  // Y *11
            dataFa[i + 1] = dataIa[i + 1] + 1.2f;     // B +1.2
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
    @IR(counts = {IRNode.MUL_VI, "> 0"},
        applyIfCPUFeatureOr = {"avx2", "true", "asimd", "true"})
    static void test1(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb) {
        for (int i = 0; i < RANGE; i+=2) {
            // Do the same as test0, but without int-float conversion.
            // This should reproduce on machines where conversion is not implemented.
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4 * i + 0, dataIa[i+0] + 1);  // A +1
            dataIb[i+0] = 11 * unsafe.getInt(dataFb, unsafe.ARRAY_INT_BASE_OFFSET + 4 * i + 0);  // X
            dataIb[i+1] = 11 * unsafe.getInt(dataFb, unsafe.ARRAY_INT_BASE_OFFSET + 4 * i + 4);  // Y
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4 * i + 4, dataIa[i+1] * 11); // B *11
        }
    }

    static void init(int[] dataI, float[] dataF) {
        for (int i = 0; i < RANGE; i++) {
            dataI[i] = i + 1;
            dataF[i] = i + 0.1f;
        }
    }

    static void verify(String name, int[] data, int[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    static void verify(String name, float[] data, float[] gold) {
        for (int i = 0; i < RANGE; i++) {
            int datav = unsafe.getInt(data, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4 * i);
            int goldv = unsafe.getInt(gold, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4 * i);
            if (datav != goldv) {
                throw new RuntimeException(" Invalid " + name + " result: dataF[" + i + "]: " + datav + " != " + goldv);
            }
        }
    }
}
