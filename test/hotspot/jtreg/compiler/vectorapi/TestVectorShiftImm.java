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
 * @run main/othervm -XX:CompileThreshold=1000 -XX:-TieredCompilation
 *                   -XX:CompileCommand=print,compiler/vectorapi/TestVectorShiftImm.shift_*
 *                   -Dvlen=64 compiler.vectorapi.TestVectorShiftImm
 * @run main/othervm -XX:CompileThreshold=1000 -XX:-TieredCompilation
 *                   -XX:CompileCommand=print,compiler/vectorapi/TestVectorShiftImm.shift_*
 *                   -Dvlen=128 compiler.vectorapi.TestVectorShiftImm
 */

public class TestVectorShiftImm {
    private static final int LARGE_LEN = 256;
    private static final int NUM_ITERS = 50000;

    private static final int NUM_OPS          = 5;
    private static final int ACCUMULATE_OP_S  = 3;
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

    static String[] opNames = {"LSHL", "ASHR", "LSHR", "ASHR_AND_ACCUMULATE", "LSHR_AND_ACCUMULATE"};

    static boolean allTestsPassed = true;
    static StringBuilder errMsg = new StringBuilder();

    public static void main(String args[]) {

        int vlen = Integer.parseInt(System.getProperty("vlen", ""));

        test_init();

        if (vlen == 64) {
            test_shift_and_accumulate_vlen64();
            test_shift_immediate_vlen64();
        }

        if (vlen == 128) {
            test_shift_and_accumulate_vlen128();
            test_shift_immediate_vlen128();
        }

        if (allTestsPassed) {
            System.out.println("Test PASSED");
        } else {
            throw new RuntimeException("Test Failed, failed tests:\n" + errMsg.toString());
        }
    }

    static void test_shift_and_accumulate_vlen64() {
        for (int i = 0; i < NUM_ITERS; i++) {
            shift_and_accumulate_bytes64(tBytes,        true);
            shift_and_accumulate_shorts64(tShorts,      true);
            shift_and_accumulate_integers64(tIntegers,  true);
        }
    }

    static void test_shift_and_accumulate_vlen128() {
        for (int i = 0; i < NUM_ITERS; i++) {
            shift_and_accumulate_bytes128(tBytes,        true);
            shift_and_accumulate_shorts128(tShorts,      true);
            shift_and_accumulate_integers128(tIntegers,  true);
            shift_and_accumulate_longs128(tLongs,        true);
        }
    }

    static void test_shift_immediate_vlen64() {
        for (int i = 0; i < NUM_ITERS; i++) {
            shift_bytes64(tBytes,        true);
            shift_shorts64(tShorts,      true);
            shift_integers64(tIntegers,  true);
        }
    }

    static void test_shift_immediate_vlen128() {
        for (int i = 0; i < NUM_ITERS; i++) {
            shift_bytes128(tBytes,        true);
            shift_shorts128(tShorts,      true);
            shift_integers128(tIntegers,  true);
            shift_longs128(tLongs,        true);
        }
    }

    static int shift_op_byte_LSHL(ByteVector vbb, byte arrBytes[][], int end, int ind) {
        vbb.lanewise(VectorOperators.LSHL, 1).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 8).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 13).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 16).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 19).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 24).intoArray(arrBytes[end++], ind);
        return end;
    }

    static int shift_op_byte_ASHR(ByteVector vbb, byte arrBytes[][], int end, int ind) {
        vbb.lanewise(VectorOperators.ASHR, 1).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 8).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 13).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 16).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 19).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 24).intoArray(arrBytes[end++], ind);
        return end;
    }

    static int shift_op_byte_LSHR(ByteVector vbb, byte arrBytes[][], int end, int ind) {
        vbb.lanewise(VectorOperators.LSHR, 1).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 8).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 13).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 16).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 19).intoArray(arrBytes[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 24).intoArray(arrBytes[end++], ind);
        return end;
    }

    static int shift_op_byte_ASHR_and_ADD(ByteVector vba, ByteVector vbb, byte arrBytes[][], int end, int ind) {
        vba.add(vbb.lanewise(VectorOperators.ASHR, 1)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 8)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 13)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 16)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 19)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 24)).intoArray(arrBytes[end++], ind);
        return end;
    }

    static int shift_op_byte_LSHR_and_ADD(ByteVector vba, ByteVector vbb, byte arrBytes[][], int end, int ind) {
        vba.add(vbb.lanewise(VectorOperators.LSHR, 1)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 8)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 13)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 16)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 19)).intoArray(arrBytes[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 24)).intoArray(arrBytes[end++], ind);
        return end;
    }

    static void shift_bytes64(byte arrBytes[][], boolean verify) {
        int start = 0, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 16) {
            end = start;
            ByteVector vbb64 = ByteVector.fromArray(byte64SPECIES, bytesB, i);
            end = shift_op_byte_LSHL(vbb64, arrBytes, end, i);
            end = shift_op_byte_ASHR(vbb64, arrBytes, end, i);
            end = shift_op_byte_LSHR(vbb64, arrBytes, end, i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("BYTE", Arrays.equals(tBytes[i], gBytes[i]), i, 64);
            }
        }
    }

    static void shift_and_accumulate_bytes64(byte arrBytes[][], boolean verify) {
        int start = ACCUMULATE_OP_S * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 16) {
            end = start;
            ByteVector vba64 = ByteVector.fromArray(byte64SPECIES, bytesA, i);
            ByteVector vbb64 = ByteVector.fromArray(byte64SPECIES, bytesB, i);
            end = shift_op_byte_ASHR_and_ADD(vba64, vbb64, arrBytes, end,  i);
            end = shift_op_byte_LSHR_and_ADD(vba64, vbb64, arrBytes, end,  i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("BYTE", Arrays.equals(tBytes[i], gBytes[i]), i, 64);
            }
        }
    }

    static void shift_bytes128(byte arrBytes[][], boolean verify) {
        int start = NUM_OPS * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 32) {
            end = start;
            ByteVector vbb128 = ByteVector.fromArray(byte128SPECIES, bytesB, i);
            end = shift_op_byte_LSHL(vbb128, arrBytes, end, i);
            end = shift_op_byte_ASHR(vbb128, arrBytes, end, i);
            end = shift_op_byte_LSHR(vbb128, arrBytes, end, i);

        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("BYTE", Arrays.equals(tBytes[i], gBytes[i]), i, 128);
            }
        }
    }

    static void shift_and_accumulate_bytes128(byte arrBytes[][], boolean verify) {
        int start = (NUM_OPS + ACCUMULATE_OP_S) * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 32) {
            end = start;
            ByteVector vba128 = ByteVector.fromArray(byte128SPECIES, bytesA, i);
            ByteVector vbb128 = ByteVector.fromArray(byte128SPECIES, bytesB, i);
            end = shift_op_byte_ASHR_and_ADD(vba128, vbb128, arrBytes, end,  i);
            end = shift_op_byte_LSHR_and_ADD(vba128, vbb128, arrBytes, end,  i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("BYTE", Arrays.equals(tBytes[i], gBytes[i]), i, 128);
            }
        }
    }

    static int shift_op_short_LSHL(ShortVector vbb, short arrShorts[][], int end, int ind) {
        vbb.lanewise(VectorOperators.LSHL, 9).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 16).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 27).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 32).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 43).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 48).intoArray(arrShorts[end++], ind);
        return end;
    }

    static int shift_op_short_ASHR(ShortVector vbb, short arrShorts[][], int end, int ind) {
        vbb.lanewise(VectorOperators.ASHR, 9).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 16).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 27).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 32).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 43).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 48).intoArray(arrShorts[end++], ind);
        return end;
    }

    static int shift_op_short_LSHR(ShortVector vbb, short arrShorts[][], int end, int ind) {
        vbb.lanewise(VectorOperators.LSHR, 9).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 16).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 27).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 32).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 43).intoArray(arrShorts[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 48).intoArray(arrShorts[end++], ind);
        return end;
    }

    static int shift_op_short_ASHR_and_ADD(ShortVector vba, ShortVector vbb, short arrShorts[][], int end, int ind) {
        vba.add(vbb.lanewise(VectorOperators.ASHR, 9)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 16)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 27)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 32)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 43)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 48)).intoArray(arrShorts[end++], ind);
        return end;
    }

    static int shift_op_short_LSHR_and_ADD(ShortVector vba, ShortVector vbb, short arrShorts[][], int end, int ind) {
        vba.add(vbb.lanewise(VectorOperators.LSHR, 9)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 16)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 27)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 32)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 43)).intoArray(arrShorts[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 48)).intoArray(arrShorts[end++], ind);
        return end;
    }

    static void shift_shorts64(short arrShorts[][], boolean verify) {
        int start = 0, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 8) {
            end = start;
            ShortVector vbb64 = ShortVector.fromArray(short64SPECIES, shortsB, i);
            end = shift_op_short_LSHL(vbb64, arrShorts, end, i);
            end = shift_op_short_ASHR(vbb64, arrShorts, end, i);
            end = shift_op_short_LSHR(vbb64, arrShorts, end, i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("SHORT", Arrays.equals(tShorts[i], gShorts[i]), i, 64);
            }
        }
    }

    static void shift_and_accumulate_shorts64(short arrShorts[][], boolean verify) {
        int start = ACCUMULATE_OP_S * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 8) {
            end = start;
            ShortVector vba64 = ShortVector.fromArray(short64SPECIES, shortsA, i);
            ShortVector vbb64 = ShortVector.fromArray(short64SPECIES, shortsB, i);
            end = shift_op_short_ASHR_and_ADD(vba64, vbb64, arrShorts, end,    i);
            end = shift_op_short_LSHR_and_ADD(vba64, vbb64, arrShorts, end,    i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("SHORT", Arrays.equals(tShorts[i], gShorts[i]), i, 64);
            }
        }
    }

    static void shift_shorts128(short arrShorts[][], boolean verify) {
        int start = NUM_OPS * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 16) {
            end = start;
            ShortVector vbb128 = ShortVector.fromArray(short128SPECIES, shortsB, i);
            end = shift_op_short_LSHL(vbb128, arrShorts, end, i);
            end = shift_op_short_ASHR(vbb128, arrShorts, end, i);
            end = shift_op_short_LSHR(vbb128, arrShorts, end, i);

        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("SHORT", Arrays.equals(tShorts[i], gShorts[i]), i, 128);
            }
        }
    }

    static void shift_and_accumulate_shorts128(short arrShorts[][], boolean verify) {
        int start = (NUM_OPS + ACCUMULATE_OP_S) * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 16) {
            end = start;
            ShortVector vba128 = ShortVector.fromArray(short128SPECIES, shortsA, i);
            ShortVector vbb128 = ShortVector.fromArray(short128SPECIES, shortsB, i);
            end = shift_op_short_ASHR_and_ADD(vba128, vbb128, arrShorts, end,    i);
            end = shift_op_short_LSHR_and_ADD(vba128, vbb128, arrShorts, end,    i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("SHORT", Arrays.equals(tShorts[i], gShorts[i]), i, 128);
            }
        }
    }

    static int shift_op_integer_LSHL(IntVector vbb, int arrIntegers[][], int end, int ind) {
        vbb.lanewise(VectorOperators.LSHL, 17).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 32).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 53).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 64).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 76).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 96).intoArray(arrIntegers[end++], ind);
        return end;
    }

    static int shift_op_integer_ASHR(IntVector vbb, int arrIntegers[][], int end, int ind) {
        vbb.lanewise(VectorOperators.ASHR, 17).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 32).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 53).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 64).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 76).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 96).intoArray(arrIntegers[end++], ind);
        return end;
    }

    static int shift_op_integer_LSHR(IntVector vbb, int arrIntegers[][], int end, int ind) {
        vbb.lanewise(VectorOperators.LSHR, 17).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 32).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 53).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 64).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 76).intoArray(arrIntegers[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 96).intoArray(arrIntegers[end++], ind);
        return end;
    }

    static int shift_op_integer_ASHR_and_ADD(IntVector vba, IntVector vbb, int arrIntegers[][], int end, int ind) {
        vba.add(vbb.lanewise(VectorOperators.ASHR, 17)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 32)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 53)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 64)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 76)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 96)).intoArray(arrIntegers[end++], ind);
        return end;
    }

    static int shift_op_integer_LSHR_and_ADD(IntVector vba, IntVector vbb, int arrIntegers[][], int end, int ind) {
        vba.add(vbb.lanewise(VectorOperators.LSHR, 17)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 32)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 53)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 64)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 76)).intoArray(arrIntegers[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 96)).intoArray(arrIntegers[end++], ind);
        return end;
    }

    static void shift_integers64(int arrIntegers[][], boolean verify) {
        int start = 0, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 4) {
            end = start;
            IntVector vbb64 = IntVector.fromArray(integer64SPECIES, integersB, i);
            end = shift_op_integer_LSHL(vbb64, arrIntegers, end, i);
            end = shift_op_integer_ASHR(vbb64, arrIntegers, end, i);
            end = shift_op_integer_LSHR(vbb64, arrIntegers, end, i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("INTEGER", Arrays.equals(tIntegers[i], gIntegers[i]), i, 64);
            }
        }
    }

    static void shift_and_accumulate_integers64(int arrIntegers[][], boolean verify) {
        int start = ACCUMULATE_OP_S * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 4) {
            end = start;
            IntVector vba64 = IntVector.fromArray(integer64SPECIES, integersA,  i);
            IntVector vbb64 = IntVector.fromArray(integer64SPECIES, integersB,  i);
            end = shift_op_integer_ASHR_and_ADD(vba64, vbb64, arrIntegers, end, i);
            end = shift_op_integer_LSHR_and_ADD(vba64, vbb64, arrIntegers, end, i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("INTEGER", Arrays.equals(tIntegers[i], gIntegers[i]), i, 64);
            }
        }
    }

    static void shift_integers128(int arrIntegers[][], boolean verify) {
        int start = NUM_OPS * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 8) {
            end = start;
            IntVector vbb128 = IntVector.fromArray(integer128SPECIES, integersB, i);
            end = shift_op_integer_LSHL(vbb128, arrIntegers, end, i);
            end = shift_op_integer_ASHR(vbb128, arrIntegers, end, i);
            end = shift_op_integer_LSHR(vbb128, arrIntegers, end, i);

        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("INTEGER", Arrays.equals(tIntegers[i], gIntegers[i]), i, 128);
            }
        }
    }

    static void shift_and_accumulate_integers128(int arrIntegers[][], boolean verify) {
        int start = (NUM_OPS + ACCUMULATE_OP_S) * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 8) {
            end = start;
            IntVector vba128 = IntVector.fromArray(integer128SPECIES, integersA,  i);
            IntVector vbb128 = IntVector.fromArray(integer128SPECIES, integersB,  i);
            end = shift_op_integer_ASHR_and_ADD(vba128, vbb128, arrIntegers, end, i);
            end = shift_op_integer_LSHR_and_ADD(vba128, vbb128, arrIntegers, end, i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("INTEGER", Arrays.equals(tIntegers[i], gIntegers[i]), i, 128);
            }
        }
    }

    static int shift_op_long_LSHL(LongVector vbb, long arrLongs[][], int end, int ind) {
        vbb.lanewise(VectorOperators.LSHL, 37).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 64).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 99).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 128).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 157).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHL, 192).intoArray(arrLongs[end++], ind);
        return end;
    }

    static int shift_op_long_ASHR(LongVector vbb, long arrLongs[][], int end, int ind) {
        vbb.lanewise(VectorOperators.ASHR, 37).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 64).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 99).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 128).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 157).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.ASHR, 192).intoArray(arrLongs[end++], ind);
        return end;
    }

    static int shift_op_long_LSHR(LongVector vbb, long arrLongs[][], int end, int ind) {
        vbb.lanewise(VectorOperators.LSHR, 37).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 64).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 99).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 128).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 157).intoArray(arrLongs[end++], ind);
        vbb.lanewise(VectorOperators.LSHR, 192).intoArray(arrLongs[end++], ind);
        return end;
    }

    static int shift_op_long_ASHR_and_ADD(LongVector vba, LongVector vbb, long arrLongs[][], int end, int ind) {
        vba.add(vbb.lanewise(VectorOperators.ASHR, 37)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 64)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 99)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 128)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 157)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.ASHR, 192)).intoArray(arrLongs[end++], ind);
        return end;
    }

    static int shift_op_long_LSHR_and_ADD(LongVector vba, LongVector vbb, long arrLongs[][], int end, int ind) {
        vba.add(vbb.lanewise(VectorOperators.LSHR, 37)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 64)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 99)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 128)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 157)).intoArray(arrLongs[end++], ind);
        vba.add(vbb.lanewise(VectorOperators.LSHR, 192)).intoArray(arrLongs[end++], ind);
        return end;
    }

    static void shift_longs128(long arrLongs[][], boolean verify) {
        int start = NUM_OPS * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 2) {
            end = start;
            LongVector vbb128 = LongVector.fromArray(long128SPECIES, longsB, i);
            end = shift_op_long_LSHL(vbb128, arrLongs, end, i);
            end = shift_op_long_ASHR(vbb128, arrLongs, end, i);
            end = shift_op_long_LSHR(vbb128, arrLongs, end, i);

        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("LONG", Arrays.equals(tLongs[i], gLongs[i]), i, 128);
            }
        }
    }

    static void shift_and_accumulate_longs128(long arrLongs[][], boolean verify) {
        int start = (NUM_OPS + ACCUMULATE_OP_S) * MAX_TESTS_PER_OP, end = 0;

        for (int i = 0; i < LARGE_LEN; i += 2) {
            end = start;
            LongVector vba128 = LongVector.fromArray(long128SPECIES, longsA, i);
            LongVector vbb128 = LongVector.fromArray(long128SPECIES, longsB, i);
            end = shift_op_long_ASHR_and_ADD(vba128, vbb128, arrLongs, end,  i);
            end = shift_op_long_LSHR_and_ADD(vba128, vbb128, arrLongs, end,  i);
        }

        if (verify) {
            for (int i = start; i < end; i++) {
                assertTrue("LONG", Arrays.equals(tLongs[i], gLongs[i]), i, 128);
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
        tLongs    = new long[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];

        gBytes    = new byte[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        gShorts   = new short[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        gIntegers = new int[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];
        gLongs    = new long[VLENS * MAX_TESTS_PER_OP * NUM_OPS][count];

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

        shift_and_accumulate_bytes64(gBytes,        false);
        shift_and_accumulate_bytes128(gBytes,       false);
        shift_and_accumulate_shorts64(gShorts,      false);
        shift_and_accumulate_shorts128(gShorts,     false);
        shift_and_accumulate_integers64(gIntegers,  false);
        shift_and_accumulate_integers128(gIntegers, false);
        shift_and_accumulate_longs128(gLongs,       false);
    }

    static void assertTrue(String type, boolean okay, int i, int vlen) {
        int op = i % (MAX_TESTS_PER_OP * NUM_OPS) / MAX_TESTS_PER_OP;
        if (!okay) {
            allTestsPassed = false;
            if (!errMsg.toString().contains("type " + type + " index " + i)) {
                errMsg.append("type " + type + " index " + i + ", operation " + opNames[op] + ", vector length "+ vlen + ".\n");
            }
        }
    }
}
