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
 * @run main/othervm compiler.vectorapi.TestVectorShiftImm
 */

public class TestVectorShiftImm {
    private static final int LARGE_LEN = 128;
    private static final int NUM_ITERS = 200000;

    private static final int NUM_OPS          = 5;
    private static final int MAX_TESTS_PER_OP = 6;

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

    static String[] opNames = {"LSHL", "ASHR", "LSHR", "ASHRACC", "LSHRACC"};

    public static void main(String args[]) {
        test_init();

        for (int i = 0; i < NUM_ITERS; i++) {
            test_vector_api_bytes();
            test_vector_api_shorts();
            test_vector_api_integers();
            test_vector_api_longs();
        }

        System.out.println("Test PASSED");
    }

    static void test_vector_api_bytes() {
         shift_bytes(tBytes, true);
    }

    static void shift_bytes(byte arrBytes[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 8; i++) {
            int op = 0;
            ByteVector vba = ByteVector.fromArray(byte64SPECIES, bytesA, 8 * i);
            ByteVector vbb = ByteVector.fromArray(byte64SPECIES, bytesB, 8 * i);

            vbb.lanewise(VectorOperators.LSHL, 1).intoArray(arrBytes[op], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 8).intoArray(arrBytes[op  + 1], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 13).intoArray(arrBytes[op + 2], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 16).intoArray(arrBytes[op + 3], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 19).intoArray(arrBytes[op + 4], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 24).intoArray(arrBytes[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.ASHR, 1).intoArray(arrBytes[op], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 8).intoArray(arrBytes[op  + 1], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 13).intoArray(arrBytes[op + 2], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 16).intoArray(arrBytes[op + 3], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 19).intoArray(arrBytes[op + 4], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 24).intoArray(arrBytes[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.LSHR, 1).intoArray(arrBytes[op], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 8).intoArray(arrBytes[op  + 1], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 13).intoArray(arrBytes[op + 2], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 16).intoArray(arrBytes[op + 3], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 19).intoArray(arrBytes[op + 4], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 24).intoArray(arrBytes[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.ASHR, 1)).intoArray(arrBytes[op], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 8)).intoArray(arrBytes[op  + 1], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 13)).intoArray(arrBytes[op + 2], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 16)).intoArray(arrBytes[op + 3], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 19)).intoArray(arrBytes[op + 4], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 24)).intoArray(arrBytes[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.LSHR, 1)).intoArray(arrBytes[op], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 8)).intoArray(arrBytes[op  + 1], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 13)).intoArray(arrBytes[op + 2], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 16)).intoArray(arrBytes[op + 3], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 19)).intoArray(arrBytes[op + 4], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 24)).intoArray(arrBytes[op + 5], 8 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tBytes[i], gBytes[i]), i);
            }
        }

        for (int i = 0; i < LARGE_LEN / 16; i++) {
            int op = 0;
            ByteVector vba = ByteVector.fromArray(byte128SPECIES, bytesA, 16 * i);
            ByteVector vbb = ByteVector.fromArray(byte128SPECIES, bytesB, 16 * i);

            vbb.lanewise(VectorOperators.LSHL, 1).intoArray(arrBytes[op], 16 * i);
            vbb.lanewise(VectorOperators.LSHL, 8).intoArray(arrBytes[op  + 1], 16 * i);
            vbb.lanewise(VectorOperators.LSHL, 13).intoArray(arrBytes[op + 2], 16 * i);
            vbb.lanewise(VectorOperators.LSHL, 16).intoArray(arrBytes[op + 3], 16 * i);
            vbb.lanewise(VectorOperators.LSHL, 19).intoArray(arrBytes[op + 4], 16 * i);
            vbb.lanewise(VectorOperators.LSHL, 24).intoArray(arrBytes[op + 5], 16 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.ASHR, 1).intoArray(arrBytes[op], 16 * i);
            vbb.lanewise(VectorOperators.ASHR, 8).intoArray(arrBytes[op  + 1], 16 * i);
            vbb.lanewise(VectorOperators.ASHR, 13).intoArray(arrBytes[op + 2], 16 * i);
            vbb.lanewise(VectorOperators.ASHR, 16).intoArray(arrBytes[op + 3], 16 * i);
            vbb.lanewise(VectorOperators.ASHR, 19).intoArray(arrBytes[op + 4], 16 * i);
            vbb.lanewise(VectorOperators.ASHR, 24).intoArray(arrBytes[op + 5], 16 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.LSHR, 1).intoArray(arrBytes[op], 16 * i);
            vbb.lanewise(VectorOperators.LSHR, 8).intoArray(arrBytes[op  + 1], 16 * i);
            vbb.lanewise(VectorOperators.LSHR, 13).intoArray(arrBytes[op + 2], 16 * i);
            vbb.lanewise(VectorOperators.LSHR, 16).intoArray(arrBytes[op + 3], 16 * i);
            vbb.lanewise(VectorOperators.LSHR, 19).intoArray(arrBytes[op + 4], 16 * i);
            vbb.lanewise(VectorOperators.LSHR, 24).intoArray(arrBytes[op + 5], 16 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.ASHR, 1)).intoArray(arrBytes[op], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 8)).intoArray(arrBytes[op  + 1], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 13)).intoArray(arrBytes[op + 2], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 16)).intoArray(arrBytes[op + 3], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 19)).intoArray(arrBytes[op + 4], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 24)).intoArray(arrBytes[op + 5], 16 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.LSHR, 1)).intoArray(arrBytes[op], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 8)).intoArray(arrBytes[op  + 1], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 13)).intoArray(arrBytes[op + 2], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 16)).intoArray(arrBytes[op + 3], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 19)).intoArray(arrBytes[op + 4], 16 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 24)).intoArray(arrBytes[op + 5], 16 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tBytes[i], gBytes[i]), i);
            }
        }
    }

    static void test_vector_api_shorts() {
         shift_shorts(tShorts, true);
    }

    static void shift_shorts(short arrShorts[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 4; i++) {
            int op = 0;
            ShortVector vba = ShortVector.fromArray(short64SPECIES, shortsA, 4 * i);
            ShortVector vbb = ShortVector.fromArray(short64SPECIES, shortsB, 4 * i);

            vbb.lanewise(VectorOperators.LSHL, 9).intoArray(arrShorts[op], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 16).intoArray(arrShorts[op + 1], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 27).intoArray(arrShorts[op + 2], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 32).intoArray(arrShorts[op + 3], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 35).intoArray(arrShorts[op + 4], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 48).intoArray(arrShorts[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.ASHR, 9).intoArray(arrShorts[op], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 16).intoArray(arrShorts[op + 1], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 27).intoArray(arrShorts[op + 2], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 32).intoArray(arrShorts[op + 3], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 35).intoArray(arrShorts[op + 4], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 48).intoArray(arrShorts[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.LSHR, 9).intoArray(arrShorts[op], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 16).intoArray(arrShorts[op + 1], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 27).intoArray(arrShorts[op + 2], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 32).intoArray(arrShorts[op + 3], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 35).intoArray(arrShorts[op + 4], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 48).intoArray(arrShorts[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.ASHR, 9)).intoArray(arrShorts[op], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 16)).intoArray(arrShorts[op + 1], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 27)).intoArray(arrShorts[op + 2], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 32)).intoArray(arrShorts[op + 3], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 35)).intoArray(arrShorts[op + 4], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 48)).intoArray(arrShorts[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.LSHR, 9)).intoArray(arrShorts[op], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 16)).intoArray(arrShorts[op + 1], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 27)).intoArray(arrShorts[op + 2], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 32)).intoArray(arrShorts[op + 3], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 35)).intoArray(arrShorts[op + 4], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 48)).intoArray(arrShorts[op + 5], 4 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tShorts[i], gShorts[i]), i);
            }
        }

        for (int i = 0; i < LARGE_LEN / 8; i++) {
            int op = 0;
            ShortVector vba = ShortVector.fromArray(short128SPECIES, shortsA, 8 * i);
            ShortVector vbb = ShortVector.fromArray(short128SPECIES, shortsB, 8 * i);

            vbb.lanewise(VectorOperators.LSHL, 9).intoArray(arrShorts[op], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 16).intoArray(arrShorts[op + 1], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 27).intoArray(arrShorts[op + 2], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 32).intoArray(arrShorts[op + 3], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 35).intoArray(arrShorts[op + 4], 8 * i);
            vbb.lanewise(VectorOperators.LSHL, 48).intoArray(arrShorts[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.ASHR, 9).intoArray(arrShorts[op], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 16).intoArray(arrShorts[op + 1], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 27).intoArray(arrShorts[op + 2], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 32).intoArray(arrShorts[op + 3], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 35).intoArray(arrShorts[op + 4], 8 * i);
            vbb.lanewise(VectorOperators.ASHR, 48).intoArray(arrShorts[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.LSHR, 9).intoArray(arrShorts[op], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 16).intoArray(arrShorts[op + 1], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 27).intoArray(arrShorts[op + 2], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 32).intoArray(arrShorts[op + 3], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 35).intoArray(arrShorts[op + 4], 8 * i);
            vbb.lanewise(VectorOperators.LSHR, 48).intoArray(arrShorts[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.ASHR, 9)).intoArray(arrShorts[op], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 16)).intoArray(arrShorts[op + 1], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 27)).intoArray(arrShorts[op + 2], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 32)).intoArray(arrShorts[op + 3], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 35)).intoArray(arrShorts[op + 4], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 48)).intoArray(arrShorts[op + 5], 8 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.LSHR, 9)).intoArray(arrShorts[op], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 16)).intoArray(arrShorts[op + 1], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 27)).intoArray(arrShorts[op + 2], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 32)).intoArray(arrShorts[op + 3], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 35)).intoArray(arrShorts[op + 4], 8 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 48)).intoArray(arrShorts[op + 5], 8 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tShorts[i], gShorts[i]), i);
            }
        }
    }

    static void test_vector_api_integers() {
         shift_integers(tIntegers, true);
    }

    static void shift_integers(int arrIntegers[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 2; i++) {
            int op = 0;
            IntVector vba = IntVector.fromArray(integer64SPECIES, integersA, 2 * i);
            IntVector vbb = IntVector.fromArray(integer64SPECIES, integersB, 2 * i);

            vbb.lanewise(VectorOperators.LSHL, 9).intoArray(arrIntegers[op], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 16).intoArray(arrIntegers[op + 1], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 27).intoArray(arrIntegers[op + 2], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 32).intoArray(arrIntegers[op + 3], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 35).intoArray(arrIntegers[op + 4], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 48).intoArray(arrIntegers[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.ASHR, 9).intoArray(arrIntegers[op], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 16).intoArray(arrIntegers[op + 1], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 27).intoArray(arrIntegers[op + 2], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 32).intoArray(arrIntegers[op + 3], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 35).intoArray(arrIntegers[op + 4], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 48).intoArray(arrIntegers[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.LSHR, 9).intoArray(arrIntegers[op], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 16).intoArray(arrIntegers[op + 1], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 27).intoArray(arrIntegers[op + 2], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 32).intoArray(arrIntegers[op + 3], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 35).intoArray(arrIntegers[op + 4], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 48).intoArray(arrIntegers[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.ASHR, 9)).intoArray(arrIntegers[op], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 16)).intoArray(arrIntegers[op + 1], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 27)).intoArray(arrIntegers[op + 2], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 32)).intoArray(arrIntegers[op + 3], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 35)).intoArray(arrIntegers[op + 4], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 48)).intoArray(arrIntegers[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.LSHR, 9)).intoArray(arrIntegers[op], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 16)).intoArray(arrIntegers[op + 1], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 27)).intoArray(arrIntegers[op + 2], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 32)).intoArray(arrIntegers[op + 3], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 35)).intoArray(arrIntegers[op + 4], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 48)).intoArray(arrIntegers[op + 5], 2 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tIntegers[i], gIntegers[i]), i);
            }
        }

        for (int i = 0; i < LARGE_LEN / 4; i++) {
            int op = 0;
            IntVector vba = IntVector.fromArray(integer128SPECIES, integersA, 4 * i);
            IntVector vbb = IntVector.fromArray(integer128SPECIES, integersB, 4 * i);

            vbb.lanewise(VectorOperators.LSHL, 9).intoArray(arrIntegers[op], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 16).intoArray(arrIntegers[op + 1], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 27).intoArray(arrIntegers[op + 2], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 32).intoArray(arrIntegers[op + 3], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 35).intoArray(arrIntegers[op + 4], 4 * i);
            vbb.lanewise(VectorOperators.LSHL, 48).intoArray(arrIntegers[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.ASHR, 9).intoArray(arrIntegers[op], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 16).intoArray(arrIntegers[op + 1], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 27).intoArray(arrIntegers[op + 2], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 32).intoArray(arrIntegers[op + 3], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 35).intoArray(arrIntegers[op + 4], 4 * i);
            vbb.lanewise(VectorOperators.ASHR, 48).intoArray(arrIntegers[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.LSHR, 9).intoArray(arrIntegers[op], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 16).intoArray(arrIntegers[op + 1], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 27).intoArray(arrIntegers[op + 2], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 32).intoArray(arrIntegers[op + 3], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 35).intoArray(arrIntegers[op + 4], 4 * i);
            vbb.lanewise(VectorOperators.LSHR, 48).intoArray(arrIntegers[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.ASHR, 9)).intoArray(arrIntegers[op], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 16)).intoArray(arrIntegers[op + 1], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 27)).intoArray(arrIntegers[op + 2], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 32)).intoArray(arrIntegers[op + 3], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 35)).intoArray(arrIntegers[op + 4], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 48)).intoArray(arrIntegers[op + 5], 4 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.LSHR, 9)).intoArray(arrIntegers[op], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 16)).intoArray(arrIntegers[op + 1], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 27)).intoArray(arrIntegers[op + 2], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 32)).intoArray(arrIntegers[op + 3], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 35)).intoArray(arrIntegers[op + 4], 4 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 48)).intoArray(arrIntegers[op + 5], 4 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tIntegers[i], gIntegers[i]), i);
            }
        }
    }

    static void test_vector_api_longs() {
         shift_longs(tLongs, true);
    }

    static void shift_longs(long arrLongs[][], boolean verify) {
        for (int i = 0; i < LARGE_LEN / 2; i++) {
            int op = 0;
            LongVector vba = LongVector.fromArray(long128SPECIES, longsA, 2 * i);
            LongVector vbb = LongVector.fromArray(long128SPECIES, longsB, 2 * i);

            vbb.lanewise(VectorOperators.LSHL, 37).intoArray(arrLongs[op], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 64).intoArray(arrLongs[op  + 1], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 99).intoArray(arrLongs[op  + 2], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 128).intoArray(arrLongs[op + 3], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 157).intoArray(arrLongs[op + 4], 2 * i);
            vbb.lanewise(VectorOperators.LSHL, 192).intoArray(arrLongs[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.ASHR, 37).intoArray(arrLongs[op], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 64).intoArray(arrLongs[op  + 1], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 99).intoArray(arrLongs[op  + 2], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 128).intoArray(arrLongs[op + 3], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 157).intoArray(arrLongs[op + 4], 2 * i);
            vbb.lanewise(VectorOperators.ASHR, 192).intoArray(arrLongs[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vbb.lanewise(VectorOperators.LSHR, 37).intoArray(arrLongs[op], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 64).intoArray(arrLongs[op  + 1], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 99).intoArray(arrLongs[op  + 2], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 128).intoArray(arrLongs[op + 3], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 157).intoArray(arrLongs[op + 4], 2 * i);
            vbb.lanewise(VectorOperators.LSHR, 192).intoArray(arrLongs[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.ASHR, 37)).intoArray(arrLongs[op], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 64)).intoArray(arrLongs[op  + 1], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 99)).intoArray(arrLongs[op  + 2], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 128)).intoArray(arrLongs[op + 3], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 157)).intoArray(arrLongs[op + 4], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.ASHR, 192)).intoArray(arrLongs[op + 5], 2 * i);
            op += MAX_TESTS_PER_OP;

            vba.add(vbb.lanewise(VectorOperators.LSHR, 37)).intoArray(arrLongs[op], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 64)).intoArray(arrLongs[op  + 1], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 99)).intoArray(arrLongs[op  + 2], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 128)).intoArray(arrLongs[op + 3], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 157)).intoArray(arrLongs[op + 4], 2 * i);
            vba.add(vbb.lanewise(VectorOperators.LSHR, 192)).intoArray(arrLongs[op + 5], 2 * i);
        }

        if (verify) {
            for (int i = 0; i < NUM_OPS * MAX_TESTS_PER_OP; i++) {
                assertTrue(Arrays.equals(tLongs[i], gLongs[i]), i);
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

        tBytes    = new byte[MAX_TESTS_PER_OP * NUM_OPS][count];
        tShorts   = new short[MAX_TESTS_PER_OP * NUM_OPS][count];
        tIntegers = new int[MAX_TESTS_PER_OP * NUM_OPS][count];
        tLongs    = new long[MAX_TESTS_PER_OP * NUM_OPS][count];

        gBytes    = new byte[MAX_TESTS_PER_OP * NUM_OPS][count];
        gShorts   = new short[MAX_TESTS_PER_OP * NUM_OPS][count];
        gIntegers = new int[MAX_TESTS_PER_OP * NUM_OPS][count];
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

        shift_bytes(gBytes,   false);
        shift_shorts(gShorts, false);
        shift_integers(gIntegers, false);
        shift_longs(gLongs, false);
    }

    static void assertTrue(boolean okay, int i) {
        if (!okay) {
            throw new RuntimeException("Test Failed, verify index " + i + ", shift operation " + opNames[i / MAX_TESTS_PER_OP]);
        }
    }
}
