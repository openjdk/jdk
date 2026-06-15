/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import compiler.lib.ir_framework.*;

import java.util.Random;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test that constant-count vector rotates on AArch64 NEON emit
 *          RotateLeftV / RotateRightV IR nodes instead of decomposing them.
 * @requires vm.compiler2.enabled
 * @requires os.arch == "aarch64" & vm.cpu.features ~= ".*simd.*"
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestVectorRotateConstantAArch64
 */
public class TestVectorRotateConstantAArch64 {

    static final VectorSpecies<Long> L_SPECIES_128 = LongVector.SPECIES_128;
    static final VectorSpecies<Integer> I_SPECIES_128 = IntVector.SPECIES_128;
    static final VectorSpecies<Short> S_SPECIES_128 = ShortVector.SPECIES_128;
    static final VectorSpecies<Byte> B_SPECIES_128 = ByteVector.SPECIES_128;

    static final int SIZE = 256;
    static final int INT_INCR = I_SPECIES_128.length();
    static final int BYTE_INCR = B_SPECIES_128.length();
    static final int LONG_INCR = L_SPECIES_128.length();
    static final int SHORT_INCR = S_SPECIES_128.length();
    static final int INT_BOUND = I_SPECIES_128.loopBound(SIZE);
    static final int BYTE_BOUND = B_SPECIES_128.loopBound(SIZE);
    static final int LONG_BOUND = L_SPECIES_128.loopBound(SIZE);
    static final int SHORT_BOUND = S_SPECIES_128.loopBound(SIZE);

    static int[] iinp = new int[SIZE];
    static int[] iout = new int[SIZE];
    static byte[] binp = new byte[SIZE];
    static byte[] bout = new byte[SIZE];
    static long[] linp = new long[SIZE];
    static long[] lout = new long[SIZE];
    static short[] sinp = new short[SIZE];
    static short[] sout = new short[SIZE];

    static {
        Random r = new Random(42);
        for (int i = 0; i < SIZE; i++) {
            iinp[i] = r.nextInt();
            linp[i] = r.nextLong();
            binp[i] = (byte) r.nextInt();
            sinp[i] = (short) r.nextInt();
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftInt_shift0() {
        for (int i = 0; i < INT_BOUND; i += INT_INCR) {
            IntVector.fromArray(I_SPECIES_128, iinp, i)
                     .lanewise(VectorOperators.ROL, 0)
                     .intoArray(iout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftInt_shift31() {
        for (int i = 0; i < INT_BOUND; i += INT_INCR) {
            IntVector.fromArray(I_SPECIES_128, iinp, i)
                     .lanewise(VectorOperators.ROL, 31)
                     .intoArray(iout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftInt_shift37() {
        for (int i = 0; i < INT_BOUND; i += INT_INCR) {
            IntVector.fromArray(I_SPECIES_128, iinp, i)
                     .lanewise(VectorOperators.ROL, 37)
                     .intoArray(iout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_RIGHT_V, "> 0"})
    public static void testRotateRightInt_shift13() {
        for (int i = 0; i < INT_BOUND; i += INT_INCR) {
            IntVector.fromArray(I_SPECIES_128, iinp, i)
                     .lanewise(VectorOperators.ROR, 13)
                     .intoArray(iout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_RIGHT_V, "> 0"})
    public static void testRotateRightInt_shift31() {
        for (int i = 0; i < INT_BOUND; i += INT_INCR) {
            IntVector.fromArray(I_SPECIES_128, iinp, i)
                     .lanewise(VectorOperators.ROR, 31)
                     .intoArray(iout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftLong_shift0() {
        for (int i = 0; i < LONG_BOUND; i += LONG_INCR) {
            LongVector.fromArray(L_SPECIES_128, linp, i)
                      .lanewise(VectorOperators.ROL, 0)
                      .intoArray(lout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftLong_shift63() {
        for (int i = 0; i < LONG_BOUND; i += LONG_INCR) {
            LongVector.fromArray(L_SPECIES_128, linp, i)
                      .lanewise(VectorOperators.ROL, 63)
                      .intoArray(lout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftLong_shift67() {
        for (int i = 0; i < LONG_BOUND; i += LONG_INCR) {
            LongVector.fromArray(L_SPECIES_128, linp, i)
                      .lanewise(VectorOperators.ROL, 67)
                      .intoArray(lout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_RIGHT_V, "> 0"})
    public static void testRotateRightLong_shift13() {
        for (int i = 0; i < LONG_BOUND; i += LONG_INCR) {
            LongVector.fromArray(L_SPECIES_128, linp, i)
                      .lanewise(VectorOperators.ROR, 13)
                      .intoArray(lout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_RIGHT_V, "> 0"})
    public static void testRotateRightLong_shift63() {
        for (int i = 0; i < LONG_BOUND; i += LONG_INCR) {
            LongVector.fromArray(L_SPECIES_128, linp, i)
                      .lanewise(VectorOperators.ROR, 63)
                      .intoArray(lout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftShort_shift0() {
        for (int i = 0; i < SHORT_BOUND; i += SHORT_INCR) {
            ShortVector.fromArray(S_SPECIES_128, sinp, i)
                       .lanewise(VectorOperators.ROL, 0)
                       .intoArray(sout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftShort_shift7() {
        for (int i = 0; i < SHORT_BOUND; i += SHORT_INCR) {
            ShortVector.fromArray(S_SPECIES_128, sinp, i)
                       .lanewise(VectorOperators.ROL, 7)
                       .intoArray(sout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftShort_shift15() {
        for (int i = 0; i < SHORT_BOUND; i += SHORT_INCR) {
            ShortVector.fromArray(S_SPECIES_128, sinp, i)
                       .lanewise(VectorOperators.ROL, 15)
                       .intoArray(sout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftShort_shift16() {
        for (int i = 0; i < SHORT_BOUND; i += SHORT_INCR) {
            ShortVector.fromArray(S_SPECIES_128, sinp, i)
                       .lanewise(VectorOperators.ROL, 16)
                       .intoArray(sout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftShort_shift19() {
        for (int i = 0; i < SHORT_BOUND; i += SHORT_INCR) {
            ShortVector.fromArray(S_SPECIES_128, sinp, i)
                       .lanewise(VectorOperators.ROL, 19)
                       .intoArray(sout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_RIGHT_V, "> 0"})
    public static void testRotateRightShort_shift5() {
        for (int i = 0; i < SHORT_BOUND; i += SHORT_INCR) {
            ShortVector.fromArray(S_SPECIES_128, sinp, i)
                       .lanewise(VectorOperators.ROR, 5)
                       .intoArray(sout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftByte_shift0() {
        for (int i = 0; i < BYTE_BOUND; i += BYTE_INCR) {
            ByteVector.fromArray(B_SPECIES_128, binp, i)
                      .lanewise(VectorOperators.ROL, 0)
                      .intoArray(bout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftByte_shift4() {
        for (int i = 0; i < BYTE_BOUND; i += BYTE_INCR) {
            ByteVector.fromArray(B_SPECIES_128, binp, i)
                      .lanewise(VectorOperators.ROL, 4)
                      .intoArray(bout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftByte_shift7() {
        for (int i = 0; i < BYTE_BOUND; i += BYTE_INCR) {
            ByteVector.fromArray(B_SPECIES_128, binp, i)
                      .lanewise(VectorOperators.ROL, 7)
                      .intoArray(bout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftByte_shift8() {
        for (int i = 0; i < BYTE_BOUND; i += BYTE_INCR) {
            ByteVector.fromArray(B_SPECIES_128, binp, i)
                      .lanewise(VectorOperators.ROL, 8)
                      .intoArray(bout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_LEFT_V, "> 0"})
    public static void testRotateLeftByte_shift11() {
        for (int i = 0; i < BYTE_BOUND; i += BYTE_INCR) {
            ByteVector.fromArray(B_SPECIES_128, binp, i)
                      .lanewise(VectorOperators.ROL, 11)
                      .intoArray(bout, i);
        }
    }

    @Test
    @IR(counts = {IRNode.ROTATE_RIGHT_V, "> 0"})
    public static void testRotateRightByte_shift3() {
        for (int i = 0; i < BYTE_BOUND; i += BYTE_INCR) {
            ByteVector.fromArray(B_SPECIES_128, binp, i)
                      .lanewise(VectorOperators.ROR, 3)
                      .intoArray(bout, i);
        }
    }

    @Run(test = {
                 // Int tests
                 "testRotateLeftInt_shift0", "testRotateLeftInt_shift31",
                 "testRotateLeftInt_shift37", "testRotateRightInt_shift13",
                 "testRotateRightInt_shift31",

                 // Long tests
                 "testRotateLeftLong_shift0", "testRotateLeftLong_shift63",
                 "testRotateLeftLong_shift67", "testRotateRightLong_shift13",
                 "testRotateRightLong_shift63",

                 // Short tests
                 "testRotateLeftShort_shift0", "testRotateLeftShort_shift7",
                 "testRotateLeftShort_shift15", "testRotateLeftShort_shift16",
                 "testRotateLeftShort_shift19", "testRotateRightShort_shift5",

                 // Byte tests
                 "testRotateLeftByte_shift0", "testRotateLeftByte_shift4",
                 "testRotateLeftByte_shift7", "testRotateLeftByte_shift8",
                 "testRotateLeftByte_shift11", "testRotateRightByte_shift3",
    })
    public void verifyAllRotates() {
        testRotateLeftInt_shift0();
        verifyInt(iout, iinp, 0, true);

        testRotateLeftInt_shift31();
        verifyInt(iout, iinp, 31, true);

        testRotateLeftInt_shift37();
        verifyInt(iout, iinp, 37, true);

        testRotateRightInt_shift13();
        verifyInt(iout, iinp, 13, false);

        testRotateRightInt_shift31();
        verifyInt(iout, iinp, 31, false);

        testRotateLeftLong_shift0();
        verifyLong(lout, linp, 0, true);

        testRotateLeftLong_shift63();
        verifyLong(lout, linp, 63, true);

        testRotateLeftLong_shift67();
        verifyLong(lout, linp, 67, true);

        testRotateRightLong_shift13();
        verifyLong(lout, linp, 13, false);

        testRotateRightLong_shift63();
        verifyLong(lout, linp, 63, false);

        testRotateLeftShort_shift0();
        verifyShort(sout, sinp, 0, true);

        testRotateLeftShort_shift7();
        verifyShort(sout, sinp, 7, true);

        testRotateLeftShort_shift15();
        verifyShort(sout, sinp, 15, true);

        testRotateLeftShort_shift16();
        verifyShort(sout, sinp, 16, true);

        testRotateLeftShort_shift19();
        verifyShort(sout, sinp, 19, true);

        testRotateRightShort_shift5();
        verifyShort(sout, sinp, 5, false);

        testRotateLeftByte_shift0();
        verifyByte(bout, binp, 0, true);

        testRotateLeftByte_shift4();
        verifyByte(bout, binp, 4, true);

        testRotateLeftByte_shift7();
        verifyByte(bout, binp, 7, true);

        testRotateLeftByte_shift8();
        verifyByte(bout, binp, 8, true);

        testRotateLeftByte_shift11();
        verifyByte(bout, binp, 11, true);

        testRotateRightByte_shift3();
        verifyByte(bout, binp, 3, false);
    }

    static void verifyInt(int[] dst, int[] src, int shift, boolean left) {
        int bound = I_SPECIES_128.loopBound(src.length);
        for (int i = 0; i < bound; i++) {
            int expected = left ? Integer.rotateLeft(src[i], shift)
                                : Integer.rotateRight(src[i], shift);
            Asserts.assertEquals(dst[i], expected,
                "int rotate" + (left ? "Left" : "Right") + " failed at index " + i +
                ": src=" + Integer.toHexString(src[i]) + " shift=" + shift);
        }
    }

    static void verifyLong(long[] dst, long[] src, int shift, boolean left) {
        int bound = L_SPECIES_128.loopBound(src.length);
        for (int i = 0; i < bound; i++) {
            long expected = left ? Long.rotateLeft(src[i], shift)
                                 : Long.rotateRight(src[i], shift);
            Asserts.assertEquals(dst[i], expected,
                "long rotate" + (left ? "Left" : "Right") + " failed at index " + i +
                ": src=" + Long.toHexString(src[i]) + " shift=" + shift);
        }
    }

    static short rotateShort(short val, int shift, boolean left) {
        int n = left ? (shift & 15) : ((-shift) & 15);
        int unsigned = val & 0xFFFF;
        return (short) ((unsigned << n) | (unsigned >>> (16 - n)));
    }

    static void verifyShort(short[] dst, short[] src, int shift, boolean left) {
        int bound = S_SPECIES_128.loopBound(src.length);
        for (int i = 0; i < bound; i++) {
            short expected = rotateShort(src[i], shift, left);
            Asserts.assertEquals(dst[i], expected,
                "short rotate" + (left ? "Left" : "Right") + " failed at index " + i +
                ": src=" + Integer.toHexString(src[i] & 0xFFFF) + " shift=" + shift);
        }
    }

    static byte rotateByte(byte val, int shift, boolean left) {
        int n = left ? (shift & 7) : ((-shift) & 7);
        int unsigned = val & 0xFF;
        return (byte) ((unsigned << n) | (unsigned >>> (8 - n)));
    }

    static void verifyByte(byte[] dst, byte[] src, int shift, boolean left) {
        int bound = B_SPECIES_128.loopBound(src.length);
        for (int i = 0; i < bound; i++) {
            byte expected = rotateByte(src[i], shift, left);
            Asserts.assertEquals(dst[i], expected,
                "byte rotate" + (left ? "Left" : "Right") + " failed at index " + i +
                ": src=" + Integer.toHexString(src[i] & 0xFF) + " shift=" + shift);
        }
    }
}
