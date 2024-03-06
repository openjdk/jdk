/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8283894
 * @key randomness
 * @summary To test various transforms added for bit COMPRESS_BITS and EXPAND_BITS operations
 * @requires vm.compiler2.enabled
 * @requires (((os.arch=="x86" | os.arch=="amd64" | os.arch=="x86_64") &
 *            (vm.cpu.features ~= ".*bmi2.*" & vm.cpu.features ~= ".*bmi1.*" &
 *             vm.cpu.features ~= ".*sse2.*")) |
 *            (os.arch=="aarch64" & vm.cpu.features ~= ".*svebitperm.*") |
 *            (os.arch=="riscv64" & vm.cpu.features ~= ".*v,.*"))
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
    @IR(counts = {IRNode.RSHIFT_I, " > 0 ", IRNode.AND_I, " > 0"})
    public void test1(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.compress(ai[i], 1 << bi[i]);
        }
    }

    @Run(test = {"test1"}, mode = RunMode.STANDALONE)
    public void kernel_test1() {
        for (int i = 0; i < 5000; i++) {
            test1(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {IRNode.URSHIFT_I, " > 0 "})
    public void test2(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.compress(ai[i], -1 << bi[i]);
        }
    }

    @Run(test = {"test2"}, mode = RunMode.STANDALONE)
    public void kernel_test2() {
        for (int i = 0; i < 5000; i++) {
            test2(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, " > 0 ", IRNode.AND_I , " > 0 "})
    public void test3(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.compress(Integer.expand(ai[i], bi[i]), bi[i]);
        }
    }

    @Run(test = {"test3"}, mode = RunMode.STANDALONE)
    public void kernel_test3() {
        for (int i = 0; i < 5000; i++) {
            test3(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {IRNode.RSHIFT_L, " > 0 ", IRNode.AND_L, " > 0"})
    public void test4(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.compress(al[i], 1L << bl[i]);
        }
    }

    @Run(test = {"test4"}, mode = RunMode.STANDALONE)
    public void kernel_test4() {
        for (int i = 0; i < 5000; i++) {
            test4(rl, al, bl);
        }
    }

    @Test
    @IR(counts = {IRNode.URSHIFT_L, " > 0 "})
    public void test5(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.compress(al[i], -1L << bl[i]);
        }
    }

    @Run(test = {"test5"}, mode = RunMode.STANDALONE)
    public void kernel_test5() {
        for (int i = 0; i < 5000; i++) {
            test5(rl, al, bl);
        }
    }

    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, " > 0 ", IRNode.AND_L , " > 0 "})
    public void test6(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.compress(Long.expand(al[i], bl[i]), bl[i]);
        }
    }

    @Run(test = {"test6"}, mode = RunMode.STANDALONE)
    public void kernel_test6() {
        for (int i = 0; i < 5000; i++) {
            test6(rl, al, bl);
        }
    }
    //===================== Expand Bits Transforms ================
    @Test
    @IR(counts = {IRNode.LSHIFT_I, " > 0 ", IRNode.AND_I, " > 0"})
    public void test7(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.expand(ai[i], 1 << bi[i]);
        }
    }

    @Run(test = {"test7"}, mode = RunMode.STANDALONE)
    public void kernel_test7() {
        for (int i = 0; i < 5000; i++) {
            test7(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {IRNode.LSHIFT_I, " > 0 "})
    public void test8(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.expand(ai[i], -1 << bi[i]);
        }
    }

    @Run(test = {"test8"}, mode = RunMode.STANDALONE)
    public void kernel_test8() {
        for (int i = 0; i < 5000; i++) {
            test8(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {IRNode.AND_I , " > 0 "})
    public void test9(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.expand(Integer.compress(ai[i], bi[i]), bi[i]);
        }
    }

    @Run(test = {"test9"}, mode = RunMode.STANDALONE)
    public void kernel_test9() {
        for (int i = 0; i < 5000; i++) {
            test9(ri, ai, bi);
        }
    }

    @Test
    @IR(counts = {IRNode.LSHIFT_L, " > 0 ", IRNode.AND_L, " > 0"})
    public void test10(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.expand(al[i], 1L << bl[i]);
        }
    }

    @Run(test = {"test10"}, mode = RunMode.STANDALONE)
    public void kernel_test10() {
        for (int i = 0; i < 5000; i++) {
            test10(rl, al, bl);
        }
    }

    @Test
    @IR(counts = {IRNode.LSHIFT_L, " > 0 "})
    public void test11(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.expand(al[i], -1L << bl[i]);
        }
    }

    @Run(test = {"test11"}, mode = RunMode.STANDALONE)
    public void kernel_test11() {
        for (int i = 0; i < 5000; i++) {
            test11(rl, al, bl);
        }
    }

    @Test
    @IR(counts = {IRNode.AND_L , " > 0 "})
    public void test12(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.expand(Long.compress(al[i], bl[i]), bl[i]);
        }
    }

    @Run(test = {"test12"}, mode = RunMode.STANDALONE)
    public void kernel_test12() {
        for (int i = 0; i < 5000; i++) {
            test12(rl, al, bl);
        }
    }

    // ================ Compress/ExpandBits Vanilla ================= //

    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, " > 0 "})
    public void test13(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.compress(ai[i], bi[i]);
        }
    }

    @Run(test = {"test13"}, mode = RunMode.STANDALONE)
    public void kernel_test13() {
        for (int i = 0; i < 5000; i++) {
            test13(ri, ai, bi);
        }
        verifyCompressInts(ri, ai, bi);
    }

    @Test
    @IR(counts = {IRNode.COMPRESS_BITS, " > 0 "})
    public void test14(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.compress(al[i], bl[i]);
        }
    }

    @Run(test = {"test14"}, mode = RunMode.STANDALONE)
    public void kernel_test14() {
        for (int i = 0; i < 5000; i++) {
            test14(rl, al, bl);
        }
        verifyCompressLongs(rl, al, bl);
    }

    @Test
    @IR(counts = {IRNode.EXPAND_BITS, " > 0 "})
    public void test15(int[] ri, int[] ai, int[] bi) {
        for (int i = 0; i < ri.length; i++) {
           ri[i] = Integer.expand(ai[i], bi[i]);
        }
    }

    @Run(test = {"test15"}, mode = RunMode.STANDALONE)
    public void kernel_test15() {
        for (int i = 0; i < 5000; i++) {
            test15(ri, ai, bi);
        }
        verifyExpandInts(ri, ai, bi);
    }

    @Test
    @IR(counts = {IRNode.EXPAND_BITS, " > 0 "})
    public void test16(long[] rl, long[] al, long[] bl) {
        for (int i = 0; i < rl.length; i++) {
           rl[i] = Long.expand(al[i], bl[i]);
        }
    }

    @Run(test = {"test16"}, mode = RunMode.STANDALONE)
    public void kernel_test16() {
        for (int i = 0; i < 5000; i++) {
            test16(rl, al, bl);
        }
        verifyExpandLongs(rl, al, bl);
    }

    @Test
    public void test17() {
        int resI = 0;
        long resL = 0L;
        for (int i = 0; i < 5000; i++) {
           resI = Integer.expand(-1, -1);
           verifyExpandInt(resI, -1, -1);
           resI = Integer.compress(-1, -1);
           verifyCompressInt(resI, -1, -1);

           resI = Integer.expand(ai[i&(SIZE-1)], -1);
           verifyExpandInt(resI, ai[i&(SIZE-1)], -1);
           resI = Integer.expand(ai[i&(SIZE-1)], -2);
           verifyExpandInt(resI, ai[i&(SIZE-1)], -2);
           resI = Integer.expand(ai[i&(SIZE-1)],  5);
           verifyExpandInt(resI, ai[i&(SIZE-1)],  5);
           resI = Integer.compress(ai[i&(SIZE-1)], -1);
           verifyCompressInt(resI, ai[i&(SIZE-1)], -1);
           resI = Integer.compress(ai[i&(SIZE-1)], -2);
           verifyCompressInt(resI, ai[i&(SIZE-1)], -2);
           resI = Integer.compress(ai[i&(SIZE-1)],  5);
           verifyCompressInt(resI, ai[i&(SIZE-1)],  5);

           resI = Integer.expand(ai[i&(SIZE-1)], bi[i&(SIZE-1)] & ~(0x10000000));
           verifyExpandInt(resI, ai[i&(SIZE-1)], bi[i&(SIZE-1)] & ~(0x10000000));
           resI = Integer.expand(ai[i&(SIZE-1)], bi[i&(SIZE-1)] | (0x10000000));
           verifyExpandInt(resI, ai[i&(SIZE-1)], bi[i&(SIZE-1)] | (0x10000000));
           resI = Integer.compress(ai[i&(SIZE-1)], bi[i&(SIZE-1)] & ~(0x10000000));
           verifyCompressInt(resI, ai[i&(SIZE-1)], bi[i&(SIZE-1)] & ~(0x10000000));
           resI = Integer.compress(ai[i&(SIZE-1)], bi[i&(SIZE-1)] | (0x10000000));
           verifyCompressInt(resI, ai[i&(SIZE-1)], bi[i&(SIZE-1)] | (0x10000000));

           resI = Integer.compress(0x12123434, 0xFF00FF00);
           verifyCompressInt(resI, 0x12123434, 0xFF00FF00);
           resI = Integer.expand(0x12123434, 0xFF00FF00);
           verifyExpandInt(resI, 0x12123434, 0xFF00FF00);

           resL = Long.expand(-1L, -1L);
           verifyExpandLong(resL, -1L, -1L);
           resL = Long.compress(-1L, -1L);
           verifyCompressLong(resL, -1L, -1L);

           resL = Long.compress(0x1212343412123434L, 0xFF00FF00FF00FF00L);
           verifyCompressLong(resL, 0x1212343412123434L, 0xFF00FF00FF00FF00L);
           resL = Long.expand(0x1212343412123434L, 0xFF00FF00FF00FF00L);
           verifyExpandLong(resL, 0x1212343412123434L, 0xFF00FF00FF00FF00L);

           resL = Long.expand(al[i&(SIZE-1)], -1);
           verifyExpandLong(resL, al[i&(SIZE-1)], -1);
           resL = Long.expand(al[i&(SIZE-1)], -2);
           verifyExpandLong(resL, al[i&(SIZE-1)], -2);
           resL = Long.expand(al[i&(SIZE-1)],  5);
           verifyExpandLong(resL, al[i&(SIZE-1)],  5);
           resL = Long.compress(al[i&(SIZE-1)], -1);
           verifyCompressLong(resL, al[i&(SIZE-1)], -1);
           resL = Long.compress(al[i&(SIZE-1)], -2);
           verifyCompressLong(resL, al[i&(SIZE-1)], -2);
           resL = Long.compress(al[i&(SIZE-1)],  5);
           verifyCompressLong(resL, al[i&(SIZE-1)],  5);

           resL = Long.expand(al[i&(SIZE-1)], bl[i&(SIZE-1)] & ~(0x10000000));
           verifyExpandLong(resL, al[i&(SIZE-1)], bl[i&(SIZE-1)] & ~(0x10000000));
           resL = Long.expand(al[i&(SIZE-1)], bl[i&(SIZE-1)] | (0x10000000));
           verifyExpandLong(resL, al[i&(SIZE-1)], bl[i&(SIZE-1)] | (0x10000000));
           resL = Long.compress(al[i&(SIZE-1)], bl[i&(SIZE-1)] & ~(0x10000000));
           verifyCompressLong(resL, al[i&(SIZE-1)], bl[i&(SIZE-1)] & ~(0x10000000));
           resL = Long.compress(al[i&(SIZE-1)], bl[i&(SIZE-1)] | (0x10000000));
           verifyCompressLong(resL, al[i&(SIZE-1)], bl[i&(SIZE-1)] | (0x10000000));
        }
    }

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

    static void verifyExpandInt(int actual, int src, int mask) {
        int exp = 0;
        for(int j = 0, k = 0; j < Integer.SIZE; j++) {
            if ((mask & 0x1) == 1) {
                exp |= (src & 0x1) <<  j;
                src >>= 1;
            }
            mask >>= 1;
        }
        if (actual != exp) {
            throw new Error("expand_int: src = " + src + " mask = " + mask +
                            " acutal = " + actual + " expected = " + exp);
        }
    }

    static void verifyExpandInts(int [] actual_res, int [] inp_arr, int [] mask_arr) {
        assert inp_arr.length == mask_arr.length && inp_arr.length == actual_res.length;
        for(int i = 0; i < actual_res.length; i++) {
            verifyExpandInt(actual_res[i], inp_arr[i], mask_arr[i]);
        }
    }

    static void verifyExpandLong(long actual, long src, long mask) {
        long exp = 0;
        for(int j = 0, k = 0; j < Long.SIZE; j++) {
            if ((mask & 0x1) == 1) {
                exp |= (src & 0x1) <<  j;
                src >>= 1;
            }
            mask >>= 1;
        }
        if (actual != exp) {
            throw new Error("expand_long: src = " + src + " mask = " + mask +
                            " acutal = " + actual + " expected = " + exp);
        }
    }

    static void verifyExpandLongs(long [] actual_res, long [] inp_arr, long [] mask_arr) {
        assert inp_arr.length == mask_arr.length && inp_arr.length == actual_res.length;
        for(int i = 0; i < actual_res.length; i++) {
            verifyExpandLong(actual_res[i], inp_arr[i], mask_arr[i]);
        }
    }

    static void verifyCompressInt(int actual, int src, int mask) {
        int exp = 0;
        for(int j = 0, k = 0; j < Integer.SIZE; j++) {
            if ((mask & 0x1) == 1) {
                exp |= (src & 0x1) <<  k++;
            }
            mask >>= 1;
            src >>= 1;
        }
        if (actual != exp) {
            throw new Error("compress_int: src = " + src + " mask = " + mask +
                            " acutal = " + actual + " expected = " + exp);
        }
    }

    static void verifyCompressInts(int [] actual_res, int [] inp_arr, int [] mask_arr) {
        assert inp_arr.length == mask_arr.length && inp_arr.length == actual_res.length;
        for(int i = 0; i < actual_res.length; i++) {
            verifyCompressInt(actual_res[i], inp_arr[i], mask_arr[i]);
        }
    }

    static void verifyCompressLong(long actual, long src, long mask) {
        long exp = 0;
        for(int j = 0, k = 0; j < Long.SIZE; j++) {
            if ((mask & 0x1) == 1) {
                exp |= (src & 0x1) <<  k++;
            }
            mask >>= 1;
            src >>= 1;
        }
        if (actual != exp) {
            throw new Error("compress_long: src = " + src + " mask = " + mask +
                            " acutal = " + actual + " expected = " + exp);
        }
    }

    static void verifyCompressLongs(long [] actual_res, long [] inp_arr, long [] mask_arr) {
        assert inp_arr.length == mask_arr.length && inp_arr.length == actual_res.length;
        for(int i = 0; i < actual_res.length; i++) {
            verifyCompressLong(actual_res[i], inp_arr[i], mask_arr[i]);
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
                                   "-XX:CompileThresholdScaling=0.3");
    }
}
