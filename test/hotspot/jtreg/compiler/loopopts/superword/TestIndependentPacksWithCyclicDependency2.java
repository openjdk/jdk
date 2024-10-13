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
 *          Before fix, this hit: "assert(!is_visited) failed: visit only once"
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.loopopts.superword.TestIndependentPacksWithCyclicDependency2::test
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:LoopUnrollLimit=250
 *                   compiler.loopopts.superword.TestIndependentPacksWithCyclicDependency2
 */

package compiler.loopopts.superword;

import jdk.test.lib.Asserts;
import jdk.internal.misc.Unsafe;

public class TestIndependentPacksWithCyclicDependency2 {
    static final int RANGE = 1024;
    static final int ITER  = 10_000;

    static Unsafe unsafe = Unsafe.getUnsafe();

    static void init(int[] dataI, float[] dataF, long[] dataL) {
        for (int i = 0; i < RANGE; i++) {
            dataI[i] = i + 1;
            dataF[i] = i + 0.1f;
            dataL[i] = (long)(i + 1);
        }
    }

    static void test(int[] dataIa, int[] dataIb, float[] dataFa, float[] dataFb,
                     long[] dataLa, long[] dataLb) {
        for (int i = 0; i < RANGE; i+=2) {
            // For explanation, see test 10 in TestIndependentPacksWithCyclicDependency.java
            int v00 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4 * i + 0) + 3;
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4 * i + 0, v00);
            int v10 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4 * i + 0) * 45;
            int v11 = unsafe.getInt(dataFb, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4 * i + 4) + 43;
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4 * i + 0, v10);
            unsafe.putInt(dataLa, unsafe.ARRAY_LONG_BASE_OFFSET + 4 * i + 4, v11);
            float v20 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4 * i + 0) + 0.55f;
            float v21 = unsafe.getFloat(dataLb, unsafe.ARRAY_LONG_BASE_OFFSET + 4 * i + 4) + 0.55f;
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4 * i + 0, v20);
            unsafe.putFloat(dataIb, unsafe.ARRAY_INT_BASE_OFFSET + 4 * i + 4, v21);
            int v01 = unsafe.getInt(dataIa, unsafe.ARRAY_INT_BASE_OFFSET + 4 * i + 4) + 3; // moved down
            unsafe.putInt(dataFa, unsafe.ARRAY_FLOAT_BASE_OFFSET + 4 * i + 4, v01);
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

    static void verify(String name, long[] data, long[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + name + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }

    public static void main(String[] args) {
        int[] dataI = new int[RANGE];
        int[] goldI = new int[RANGE];
        float[] dataF = new float[RANGE];
        float[] goldF = new float[RANGE];
        long[] dataL = new long[RANGE];
        long[] goldL = new long[RANGE];
        init(goldI, goldF, goldL);
        test(goldI, goldI, goldF, goldF, goldL, goldL);
        for (int i = 0; i < ITER; i++) {
            init(dataI, dataF, dataL);
            test(dataI, dataI, dataF, dataF, dataL, dataL);
        }
        verify("test", dataI, goldI);
        verify("test", dataF, goldF);
        verify("test", dataL, goldL);
    }
}

