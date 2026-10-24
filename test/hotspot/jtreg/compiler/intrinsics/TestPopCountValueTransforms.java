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

/**
 * @test
 * @bug 8365205
 * @summary C2: Optimize popcount value computation using knownbits
 * @library /test/lib /
 * @run driver compiler.intrinsics.TestPopCountValueTransforms
 */
package compiler.intrinsics;

import compiler.lib.ir_framework.*;
import compiler.lib.generators.*;
import compiler.lib.verify.*;
import static compiler.lib.generators.Generators.*;

public class TestPopCountValueTransforms {
    int  [] inI1;
    int  [] inI2;
    long [] inL1;
    long [] inL2;

    static final int SIZE = 4096;

    static final int rand_bndI1 = G.ints().next();
    static final int rand_bndI2 = G.ints().next();
    static final int rand_popcI1 = G.uniformInts(0, 32).next();
    static final int rand_popcI2 = G.uniformInts(0, 32).next();

    static final long rand_bndL1 = G.longs().next();
    static final long rand_bndL2 = G.longs().next();
    static final long rand_popcL1 = G.uniformLongs(0, 64).next();
    static final long rand_popcL2 = G.uniformLongs(0, 64).next();

    @Test
    @IR(counts = {IRNode.POPCOUNT_L, " 0 "})
    public long testPopCountElisionLong1(long num) {
        num = Math.clamp(num, 0xF000F000L, 0xF000F0FFL);
        // PopCount ValueRange = {lo:8, hi:16}
        if (Long.bitCount(num) < 8 || Long.bitCount(num) > 16) {
            return 0;
        }
        return 1;
    }

    @Run(test = {"testPopCountElisionLong1"})
    public void runPopCountElisionLong1() {
        long res = 1;
        for (int i = 0; i < inL1.length; i++) {
            res &= testPopCountElisionLong1(inL1[i]);
        }
        Verify.checkEQ(res, 1L);
    }

    @Test
    @IR(counts = {IRNode.POPCOUNT_L, " 1 "})
    public long testPopCountElisionLong2(long num) {
        num = Math.clamp(num, 0x3L, 0xFFFFL);
        // PopCount ValueRange = {lo:0, hi:16}
        if (Long.bitCount(num) >= 0 && Long.bitCount(num) <= 11) {
            return 0;
        }
        return 1;
    }

    @Run(test = {"testPopCountElisionLong2"})
    public void runPopCountElisionLong2() {
        long res = 0;
        for (int i = 0; i < inL2.length; i++) {
            res |= testPopCountElisionLong2(inL2[i]);
        }
        Verify.checkEQ(res, 0L);
    }

    @Test
    @IR(counts = {IRNode.POPCOUNT_I, " 0 "})
    public int testPopCountElisionInt1(int num) {
        // PopCount ValueRange = {lo:11, hi:15}
        num = Math.clamp(num, 0xFE00F000, 0xFE00F00F);
        if (Integer.bitCount(num) < 11 || Integer.bitCount(num) > 15) {
            return 0;
        }
        return 1;
    }

    @Run(test = {"testPopCountElisionInt1"})
    public void runPopCountElisionInt1() {
        int res = 1;
        for (int i = 0; i < inI1.length; i++) {
            res &= testPopCountElisionInt1(inI1[i]);
        }
        Verify.checkEQ(res, 1);
    }

    @Test
    @IR(counts = {IRNode.POPCOUNT_I, " 1 "})
    public int testPopCountElisionInt2(int num) {
        // PopCount ValueRange = {lo:0, hi:8}
        num = Math.clamp(num, 0x3, 0xFF);
        if (Integer.bitCount(num) >= 0 && Integer.bitCount(num) <= 5) {
            return 0;
        }
        return 1;
    }

    @Run(test = {"testPopCountElisionInt2"})
    public void runPopCountElisionInt2() {
        int res = 0;
        for (int i = 0; i < inI2.length; i++) {
            res |= testPopCountElisionInt2(inI2[i]);
        }
        Verify.checkEQ(res, 0);
    }

    @Test
    public void testPopCountRandomInt(int rand_numI) {
        int res = 0;
        int num = Math.clamp(rand_numI, Math.min(rand_bndI1, rand_bndI2), Math.max(rand_bndI1, rand_bndI2));
        if (Integer.bitCount(num) >= rand_popcI1 && Integer.bitCount(num) < rand_popcI2) {
            res = 1;
        } else {
            res = -1;
        }
        checkPopCountRandomInt(rand_numI, res);
    }

    public void checkPopCountRandomInt(int rand_numI, int res) {
        int exp = 0;
        int num = Math.clamp(rand_numI, Math.min(rand_bndI1, rand_bndI2), Math.max(rand_bndI1, rand_bndI2));
        while(num != 0) {
            num &= (num - 1);
            exp++;
        }
        Verify.checkEQ(exp >= rand_popcI1 && exp < rand_popcI2 ? 1 : -1, res);
    }

    @Run(test="testPopCountRandomInt")
    public void runPopCountRandomInt() {
        testPopCountRandomInt(G.ints().next());
    }


    @Test
    public void testPopCountRandomLong(long rand_numL) {
        long res = 0;
        long num = Math.clamp(rand_numL, Math.min(rand_bndL1, rand_bndL2), Math.max(rand_bndL1, rand_bndL2));
        if (Long.bitCount(num) >= rand_popcL1 && Long.bitCount(num) < rand_popcL2) {
            res = 1L;
        } else {
            res = -1L;
        }
        checkPopCountRandomLong(rand_numL, res);
    }

    public void checkPopCountRandomLong(long rand_numL, long res) {
        int exp = 0;
        long num = Math.clamp(rand_numL, Math.min(rand_bndL1, rand_bndL2), Math.max(rand_bndL1, rand_bndL2));
        while(num != 0) {
            num &= (num - 1L);
            exp++;
        }
        Verify.checkEQ(exp >= rand_popcL1 && exp < rand_popcL2 ? 1L : -1L, res);
    }

    @Run(test="testPopCountRandomLong")
    public void runPopCountRandomLong() {
        testPopCountRandomLong(G.longs().next());
    }

    public TestPopCountValueTransforms() {
        inL1  = new long[SIZE];
        G.fill(G.longs(), inL1);

        inL2  = new long[SIZE];
        Generator<Long> genL = G.uniformLongs(0x3L, 0xFFCL);
        for (int i = 0; i < SIZE; i++) {
            inL2[i] = genL.next();
        }

        inI1  = new int[SIZE];
        G.fill(G.ints(), inI1);

        inI2  = new int[SIZE];
        Generator<Integer> genI = G.uniformInts(0x3, 0x1F);
        for (int i = 0; i < SIZE; i++) {
            inI2[i] = genI.next();
        }
    }

    public static void main(String[] args) {
        TestFramework.run();
    }
}
