/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8285281
 * @key randomness
 * @summary To test various tansforms added for bit COMPRESS_BITS and EXPAND_BITS operations
 * @requires vm.compiler2.enabled
 * @requires vm.cpu.features ~= ".*bmi2.*"
 * @requires os.simpleArch == "x64"
 * @library /test/lib /
 * @run driver compiler.intrinsics.TestBitShuffleOpers
 */

package compiler.intrinsics;

import java.util.concurrent.Callable;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import java.util.Random;

public class TestBitShuffleOpers {
    int [] ri;
    int [] ai;
    int [] bi;

    long [] rl;
    long [] al;
    long [] bl;

    //===================== Compress Bits Transforms ================
    @Test
    @IR(counts = {"RShiftI", " > 0 ", "AndI", " > 0"})
    public void test1(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.compress(ai[i], 1 << bi[i]);
        }
    }

    @Run(test = {"test1"}, mode = RunMode.STANDALONE)
    public void kernel_test1() {
        for (int i = 0; i < 10000; i++) {
            test1(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {"URShiftI", " > 0 "})
    public void test2(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.compress(ai[i], -1 << bi[i]);
        }
    }

    @Run(test = {"test2"}, mode = RunMode.STANDALONE)
    public void kernel_test2() {
        for (int i = 0; i < 10000; i++) {
            test2(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {"CompressBits", " > 0 ", "AndI" , " > 0 "})
    public void test3(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.compress(Integer.expand(ai[i], bi[i]), bi[i]);
        }
    }

    @Run(test = {"test3"}, mode = RunMode.STANDALONE)
    public void kernel_test3() {
        for (int i = 0; i < 10000; i++) {
            test3(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {"RShiftL", " > 0 ", "AndL", " > 0"})
    public void test4(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.compress(al[i], 1L << bl[i]);
        }
    }

    @Run(test = {"test4"}, mode = RunMode.STANDALONE)
    public void kernel_test4() {
        for (int i = 0; i < 10000; i++) {
            test4(rl, al, bl);
        }
    }

    @Test
    @IR(counts = {"URShiftL", " > 0 "})
    public void test5(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.compress(al[i], -1L << bl[i]);
        }
    }

    @Run(test = {"test5"}, mode = RunMode.STANDALONE)
    public void kernel_test5() {
        for (int i = 0; i < 10000; i++) {
            test5(rl, al, bl);
        }
    }

    @Test
    @IR(counts = {"CompressBits", " > 0 ", "AndL" , " > 0 "})
    public void test6(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.compress(Long.expand(al[i], bl[i]), bl[i]);
        }
    }

    @Run(test = {"test6"}, mode = RunMode.STANDALONE)
    public void kernel_test6() {
        for (int i = 0; i < 10000; i++) {
            test6(rl, al, bl);
        }
    }
    //===================== Expand Bits Transforms ================
    @Test
    @IR(counts = {"LShiftI", " > 0 ", "AndI", " > 0"})
    public void test7(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.expand(ai[i], 1 << bi[i]);
        }
    }

    @Run(test = {"test7"}, mode = RunMode.STANDALONE)
    public void kernel_test7() {
        for (int i = 0; i < 10000; i++) {
            test7(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {"LShiftI", " > 0 "})
    public void test8(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.expand(ai[i], -1 << bi[i]);
        }
    }

    @Run(test = {"test8"}, mode = RunMode.STANDALONE)
    public void kernel_test8() {
        for (int i = 0; i < 10000; i++) {
            test8(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {"AndI" , " > 0 "})
    public void test9(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.expand(Integer.compress(ai[i], bi[i]), bi[i]);
        }
    }

    @Run(test = {"test9"}, mode = RunMode.STANDALONE)
    public void kernel_test9() {
        for (int i = 0; i < 10000; i++) {
            test9(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {"LShiftL", " > 0 ", "AndL", " > 0"})
    public void test10(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.expand(al[i], 1L << bl[i]);
        }
    }

    @Run(test = {"test10"}, mode = RunMode.STANDALONE)
    public void kernel_test10() {
        for (int i = 0; i < 10000; i++) {
            test10(rl, al, bl);
        }
    }

    @Test
    @IR(counts = {"LShiftL", " > 0 "})
    public void test11(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.expand(al[i], -1L << bl[i]);
        }
    }

    @Run(test = {"test11"}, mode = RunMode.STANDALONE)
    public void kernel_test11() {
        for (int i = 0; i < 10000; i++) {
            test11(rl, al, bl);
        }
    }

    @Test
    @IR(counts = {"AndL" , " > 0 "})
    public void test12(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.expand(Long.compress(al[i], bl[i]), bl[i]);
        }
    }

    @Run(test = {"test12"}, mode = RunMode.STANDALONE)
    public void kernel_test12() {
        for (int i = 0; i < 10000; i++) {
            test12(rl, al, bl);
        }
    }
    // ===================================================== //

    private static final Random R = Utils.getRandomInstance();

    static int[] fillIntRandom(Callable<int[]> factory) {
        try {
            int[] arr = factory.call();
            for (int i = 0; i < arr.length; i++) {
                arr[i] = R.nextInt();
            }
            return arr;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }
    static long[] fillLongRandom(Callable<long[]> factory) {
        try {
            long[] arr = factory.call();
            for (int i = 0; i < arr.length; i++) {
                arr[i] = R.nextLong();
            }
            return arr;
        } catch (Exception e) {
            throw new InternalError(e);
        }
    }

    // ===================================================== //

    static final int SIZE = 512;


    public TestBitShuffleOpers() {
        ri = new int[SIZE];
        ai = fillIntRandom(()-> new int[SIZE]);
        bi = fillIntRandom(()-> new int[SIZE]);

        rl = new long[SIZE];
        al = fillLongRandom(() -> new long[SIZE]);
        bl = fillLongRandom(() -> new long[SIZE]);

    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:UseAVX=3",
                                   "--add-modules=jdk.incubator.vector",
                                   "-XX:CompileThresholdScaling=0.3");
    }
}
