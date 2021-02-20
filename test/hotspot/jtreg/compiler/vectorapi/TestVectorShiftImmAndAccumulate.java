/*
 * Copyright (c) 2021, Huawei Technologies Co., Ltd. All rights reserved.
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

package compiler.vectorapi;

import java.util.Random;
import java.util.Arrays;

import jdk.incubator.vector.*;

/**
 * @test
 * @bug 8261142
 * @summary AArch64: Incorrect instruction encoding when right-shifting vectors with shift amount equals to the element width
 * @modules jdk.incubator.vector
 *
 * @run main/othervm compiler.vectorapi.TestVectorShiftImmAndAccumulate
 */

public class TestVectorShiftImmAndAccumulate {
    private static final int LARGE_LEN = 128;
    private static final int NUM_ITERS = 200000;

    private static final int NUM_OPS          = 2;
    private static final int MAX_TESTS_PER_OP = 6;
    private static final int VLENS            = 2;

    private static byte[]  bytesA,    bytesB;
    private static short[] shortsA,   shortsB;
    private static int[]   integersA, integersB;
    private static long[]  longsA,    longsB;

    private static byte  tBytes[][],    gBytes[][];
    private static short tShorts[][],   gShorts[][];
    private static int   tIntegers[][], gIntegers[][];
    private static long  tLongs[][],    gLongs[][];

    private static Random r = new Random(32781);

    static final VectorSpecies<Byte> byte64SPECIES  = ByteVector.SPECIES_64;
    static final VectorSpecies<Byte> byte128SPECIES = ByteVector.SPECIES_128;

    static final VectorSpecies<Short> short64SPECIES  = ShortVector.SPECIES_64;
    static final VectorSpecies<Short> short128SPECIES = ShortVector.SPECIES_128;

    static final VectorSpecies<Integer> integer64SPECIES  = IntVector.SPECIES_64;
    static final VectorSpecies<Integer> integer128SPECIES = IntVector.SPECIES_128;

    static final VectorSpecies<Long> long128SPECIES = LongVector.SPECIES_128;

    static String[] opNames = {"ASHR_AND_ACCUMULATE", "LSHR_AND_ACCUMULATE"};

    public static void main(String args[]) {
        test_init();

        test_shift_and_accumulte_vlen64();
        test_shift_and_accumulte_vlen128();

        System.out.println("Test PASSED");
    }

    static void test_shift_and_accumulte_vlen64() {
        for (int i = 0; i < NUM_ITERS; i++) {
            shift_bytes64(tBytes,        true);
            shift_shorts64(tShorts,      true);
            shift_integers64(tIntegers,  true);
        }
    }

    static void test_shift_and_accumulte_vlen128() {
        for (int i = 0; i < NUM_ITERS; i++) {
            shift_bytes128(tBytes,        true);
            shift_shorts128(tShorts,      true);
            shift_integers128(tIntegers,  true);
            shift_longs128(tLongs,        true);
        }
    }

    static void shift_bytes64(byte arrBytes[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 8; i++) {
            int op = 0;
            ByteVector vba64 = ByteVector.fromArray(byte64SPECIES, bytesA, 8 * i);
            ByteVector vbb64 = ByteVector.fromArray(byte64SPECIES, bytesB, 8 * i);

            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 1)).intoArray(arrBytes[op], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 8)).intoArray(arrBytes[op  + 1], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 13)).intoArray(arrBytes[op + 2], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 16)).intoArray(arrBytes[op + 3], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 19)).intoArray(arrBytes[op + 4], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 24)).intoArray(arrBytes[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 1)).intoArray(arrBytes[op], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 8)).intoArray(arrBytes[op  + 1], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 13)).intoArray(arrBytes[op + 2], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 16)).intoArray(arrBytes[op + 3], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 19)).intoArray(arrBytes[op + 4], 8 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 24)).intoArray(arrBytes[op + 5], 8 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tBytes[i], gBytes[i]), i, 64);
            }
        }
    }

    static void shift_bytes128(byte arrBytes[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 16; i++) {
            int op = NUM_OPS * MAX_TESTS_PER_OP;
            ByteVector vba128 = ByteVector.fromArray(byte128SPECIES, bytesA, 16 * i);
            ByteVector vbb128 = ByteVector.fromArray(byte128SPECIES, bytesB, 16 * i);

            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 1)).intoArray(arrBytes[op], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 8)).intoArray(arrBytes[op  + 1], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 13)).intoArray(arrBytes[op + 2], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 16)).intoArray(arrBytes[op + 3], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 19)).intoArray(arrBytes[op + 4], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 24)).intoArray(arrBytes[op + 5], 16 * i);
            op += MAX_TESTS_PER_OP;

            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 1)).intoArray(arrBytes[op], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 8)).intoArray(arrBytes[op  + 1], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 13)).intoArray(arrBytes[op + 2], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 16)).intoArray(arrBytes[op + 3], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 19)).intoArray(arrBytes[op + 4], 16 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 24)).intoArray(arrBytes[op + 5], 16 * i);
        }

        if (verify) {     
            for (int i = NUM_OPS * MAX_TESTS_PER_OP; i < VLENS * NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tBytes[i], gBytes[i]), i, 128);
            }
        }
    }

    static void shift_shorts64(short arrShorts[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 4; i++) {
            int op = 0;
            ShortVector vba64 = ShortVector.fromArray(short64SPECIES, shortsA, 4 * i);
            ShortVector vbb64 = ShortVector.fromArray(short64SPECIES, shortsB, 4 * i);

            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 9)).intoArray(arrShorts[op], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 16)).intoArray(arrShorts[op + 1], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 27)).intoArray(arrShorts[op + 2], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 32)).intoArray(arrShorts[op + 3], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 35)).intoArray(arrShorts[op + 4], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 48)).intoArray(arrShorts[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 9)).intoArray(arrShorts[op], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 16)).intoArray(arrShorts[op + 1], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 27)).intoArray(arrShorts[op + 2], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 32)).intoArray(arrShorts[op + 3], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 35)).intoArray(arrShorts[op + 4], 4 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 48)).intoArray(arrShorts[op + 5], 4 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tShorts[i], gShorts[i]), i, 64);
            }
        }
    }

    static void shift_shorts128(short arrShorts[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 8; i++) {
            int op = NUM_OPS * MAX_TESTS_PER_OP;
            ShortVector vba128 = ShortVector.fromArray(short128SPECIES, shortsA, 8 * i);
            ShortVector vbb128 = ShortVector.fromArray(short128SPECIES, shortsB, 8 * i);

            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 9)).intoArray(arrShorts[op], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 16)).intoArray(arrShorts[op + 1], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 27)).intoArray(arrShorts[op + 2], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 32)).intoArray(arrShorts[op + 3], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 35)).intoArray(arrShorts[op + 4], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 48)).intoArray(arrShorts[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 9)).intoArray(arrShorts[op], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 16)).intoArray(arrShorts[op + 1], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 27)).intoArray(arrShorts[op + 2], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 32)).intoArray(arrShorts[op + 3], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 35)).intoArray(arrShorts[op + 4], 8 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 48)).intoArray(arrShorts[op + 5], 8 * i);
        }

        if (verify) {
            for (int i = NUM_OPS * MAX_TESTS_PER_OP; i < VLENS * NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tShorts[i], gShorts[i]), i, 128);
            }
        }
    }

    static void shift_integers64(int arrIntegers[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 2; i++) {
            int op = 0;
            IntVector vba64 = IntVector.fromArray(integer64SPECIES, integersA, 2 * i);
            IntVector vbb64 = IntVector.fromArray(integer64SPECIES, integersB, 2 * i);

            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 9)).intoArray(arrIntegers[op], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 16)).intoArray(arrIntegers[op + 1], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 27)).intoArray(arrIntegers[op + 2], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 32)).intoArray(arrIntegers[op + 3], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 35)).intoArray(arrIntegers[op + 4], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.ASHR, 48)).intoArray(arrIntegers[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 9)).intoArray(arrIntegers[op], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 16)).intoArray(arrIntegers[op + 1], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 27)).intoArray(arrIntegers[op + 2], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 32)).intoArray(arrIntegers[op + 3], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 35)).intoArray(arrIntegers[op + 4], 2 * i);
            vba64.add(vbb64.lanewise(VectorOperators.LSHR, 48)).intoArray(arrIntegers[op + 5], 2 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tIntegers[i], gIntegers[i]), i, 64);
            }
        }
    }

    static void shift_integers128(int arrIntegers[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 4; i++) {
            int op = NUM_OPS * MAX_TESTS_PER_OP;
            IntVector vba128 = IntVector.fromArray(integer128SPECIES, integersA, 4 * i);
            IntVector vbb128 = IntVector.fromArray(integer128SPECIES, integersB, 4 * i);

            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 9)).intoArray(arrIntegers[op], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 16)).intoArray(arrIntegers[op + 1], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 27)).intoArray(arrIntegers[op + 2], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 32)).intoArray(arrIntegers[op + 3], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 35)).intoArray(arrIntegers[op + 4], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 48)).intoArray(arrIntegers[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 9)).intoArray(arrIntegers[op], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 16)).intoArray(arrIntegers[op + 1], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 27)).intoArray(arrIntegers[op + 2], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 32)).intoArray(arrIntegers[op + 3], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 35)).intoArray(arrIntegers[op + 4], 4 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 48)).intoArray(arrIntegers[op + 5], 4 * i);
        }

        if (verify) {
            for (int i = NUM_OPS * MAX_TESTS_PER_OP; i < VLENS * NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tIntegers[i], gIntegers[i]), i, 128);
            }
        }
    }

    static void shift_longs128(long arrLongs[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 2; i++) {
            int op = 0;
            LongVector vba128 = LongVector.fromArray(long128SPECIES, longsA, 2 * i);
            LongVector vbb128 = LongVector.fromArray(long128SPECIES, longsB, 2 * i);

            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 37)).intoArray(arrLongs[op], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 64)).intoArray(arrLongs[op  + 1], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 99)).intoArray(arrLongs[op  + 2], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 128)).intoArray(arrLongs[op + 3], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 157)).intoArray(arrLongs[op + 4], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.ASHR, 192)).intoArray(arrLongs[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 37)).intoArray(arrLongs[op], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 64)).intoArray(arrLongs[op  + 1], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 99)).intoArray(arrLongs[op  + 2], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 128)).intoArray(arrLongs[op + 3], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 157)).intoArray(arrLongs[op + 4], 2 * i);
            vba128.add(vbb128.lanewise(VectorOperators.LSHR, 192)).intoArray(arrLongs[op + 5], 2 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tLongs[i], gLongs[i]), i, 128);
            }
        }
    }

    static void test_init() {
        int count = LARGE_LEN;

        bytesA    = new byte[count];
        shortsA   = new short[count];
        integersA = new int[count];
        longsA    = new long[count];

        bytesB    = new byte[count];
        shortsB   = new short[count];
        integersB = new int[count];
        longsB    = new long[count];

        tBytes    = new byte[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        tShorts   = new short[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        tIntegers = new int[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        tLongs    = new long[MAX_TESTS_PER_OP * NUM_OPS][count];

        gBytes    = new byte[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        gShorts   = new short[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        gIntegers = new int[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        gLongs    = new long[MAX_TESTS_PER_OP * NUM_OPS][count];

        for (int i = 0; i < count; i++) {
            bytesA[i]    = (byte) r.nextInt();
            shortsA[i]   = (short) r.nextInt();
            integersA[i] = r.nextInt();
            longsA[i]    = r.nextLong();

            bytesB[i]    = (byte) r.nextInt();
            shortsB[i]   = (short) r.nextInt();
            integersB[i] = r.nextInt();
            longsB[i]    = r.nextLong();
        }

        shift_bytes64(gBytes,        false);
        shift_bytes128(gBytes,       false);
        shift_shorts64(gShorts,      false);
        shift_shorts128(gShorts,     false);
        shift_integers64(gIntegers,  false);
        shift_integers128(gIntegers, false);
        shift_longs128(gLongs,       false);
    }

    static void assertTrue(boolean okay, int i, int vlen) {
        if (!okay) {
            throw new RuntimeException("Test Failed, verify index " + i + ", operation " + opNames[i / MAX_TESTS_PER_OP / (vlen / 64)] + ", vector length "+ vlen);
        }
    }
}
