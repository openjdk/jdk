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
 */

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;

/**
* @test
* @bug 8287835
* @summary Test vector slice intrinsics
* @modules jdk.incubator.vector
* @requires vm.compiler2.enabled
* @library /test/lib /
* @run driver compiler.vectorapi.TestVectorSlice
*/
public class TestVectorSlice {
    @Run(test = {"testB64", "testB128", "testB256", "testB512"})
    static void testBytes() {
        int maxSize = 64;
        int diff = 70;
        byte[][] dst = new byte[maxSize + 1][maxSize];
        byte[] src1 = new byte[maxSize];
        byte[] src2 = new byte[maxSize];
        for (int i = 0; i < src1.length; i++) {
            src1[i] = (byte)i;
            src2[i] = (byte)(i + diff);
        }

        int length = 8;
        testB64(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                byte expected;
                if (offset < length) {
                    expected = (byte)offset;
                } else {
                    expected = (byte)(offset + diff - length);
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 16;
        testB128(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                byte expected;
                if (offset < length) {
                    expected = (byte)offset;
                } else {
                    expected = (byte)(offset + diff - length);
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 32;
        testB256(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                byte expected;
                if (offset < length) {
                    expected = (byte)offset;
                } else {
                    expected = (byte)(offset + diff - length);
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 64;
        testB512(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                byte expected;
                if (offset < length) {
                    expected = (byte)offset;
                } else {
                    expected = (byte)(offset + diff - length);
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }
    }

    @Run(test = {"testS64", "testS128", "testS256", "testS512"})
    static void testShorts() {
        int maxSize = 32;
        int diff = 1234;
        short[][] dst = new short[maxSize + 1][maxSize];
        short[] src1 = new short[maxSize];
        short[] src2 = new short[maxSize];
        for (int i = 0; i < src1.length; i++) {
            src1[i] = (short)i;
            src2[i] = (short)(i + diff);
        }

        int length = 4;
        testS64(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                short expected;
                if (offset < length) {
                    expected = (short)offset;
                } else {
                    expected = (short)(offset + diff - length);
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 8;
        testS128(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                short expected;
                if (offset < length) {
                    expected = (short)offset;
                } else {
                    expected = (short)(offset + diff - length);
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 16;
        testS256(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                short expected;
                if (offset < length) {
                    expected = (short)offset;
                } else {
                    expected = (short)(offset + diff - length);
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 32;
        testS512(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                short expected;
                if (offset < length) {
                    expected = (short)offset;
                } else {
                    expected = (short)(offset + diff - length);
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }
    }

    @Run(test = {"testI64", "testI128", "testI256", "testI512"})
    static void testInts() {
        int maxSize = 16;
        int diff = 70;
        int[][] dst = new int[maxSize + 1][maxSize];
        int[] src1 = new int[maxSize];
        int[] src2 = new int[maxSize];
        for (int i = 0; i < src1.length; i++) {
            src1[i] = i;
            src2[i] = i + diff;
        }

        int length = 2;
        testI64(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                int expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 4;
        testI128(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                int expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 8;
        testI256(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                int expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 16;
        testI512(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                int expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }
    }

    @Run(test = {"testL128", "testL256", "testL512"})
    static void testLongs() {
        int maxSize = 8;
        int diff = 70;
        long[][] dst = new long[maxSize + 1][maxSize];
        long[] src1 = new long[maxSize];
        long[] src2 = new long[maxSize];
        for (int i = 0; i < src1.length; i++) {
            src1[i] = i;
            src2[i] = i + diff;
        }

        int length = 2;
        testL128(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                long expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 4;
        testL256(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                long expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 8;
        testL512(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                long expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }
    }

    @Run(test = {"testF64", "testF128", "testF256", "testF512"})
    static void testFloats() {
        int maxSize = 64;
        int diff = 70;
        float[][] dst = new float[maxSize + 1][maxSize];
        float[] src1 = new float[maxSize];
        float[] src2 = new float[maxSize];
        for (int i = 0; i < src1.length; i++) {
            src1[i] = i;
            src2[i] = i + diff;
        }

        int length = 2;
        testF64(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                float expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 4;
        testF128(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                float expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 8;
        testF256(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                float expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 16;
        testF512(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                float expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }
    }

    @Run(test = {"testD128", "testD256", "testD512"})
    static void testDoubles() {
        int maxSize = 64;
        int diff = 70;
        double[][] dst = new double[maxSize + 1][maxSize];
        double[] src1 = new double[maxSize];
        double[] src2 = new double[maxSize];
        for (int i = 0; i < src1.length; i++) {
            src1[i] = i;
            src2[i] = i + diff;
        }

        int length = 2;
        testD128(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                double expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 4;
        testD256(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                double expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }

        length = 8;
        testD512(dst, src1, src2);
        for (int i = 0; i <= length; i++) {
            for (int j = 0; j < length; j++) {
                int offset = i + j;
                double expected;
                if (offset < length) {
                    expected = offset;
                } else {
                    expected = offset + diff - length;
                }
                Asserts.assertEquals(expected, dst[i][j]);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "9"})
    static void testB64(byte[][] dst, byte[] src1, byte[] src2) {
        var species = ByteVector.SPECIES_64;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "17"})
    static void testB128(byte[][] dst, byte[] src1, byte[] src2) {
        var species = ByteVector.SPECIES_128;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
        helper(species, dst[9], src1, src2, 9);
        helper(species, dst[10], src1, src2, 10);
        helper(species, dst[11], src1, src2, 11);
        helper(species, dst[12], src1, src2, 12);
        helper(species, dst[13], src1, src2, 13);
        helper(species, dst[14], src1, src2, 14);
        helper(species, dst[15], src1, src2, 15);
        helper(species, dst[16], src1, src2, 16);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "33"}, applyIfCPUFeature = {"avx2", "true"})
    static void testB256(byte[][] dst, byte[] src1, byte[] src2) {
        var species = ByteVector.SPECIES_256;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
        helper(species, dst[9], src1, src2, 9);
        helper(species, dst[10], src1, src2, 10);
        helper(species, dst[11], src1, src2, 11);
        helper(species, dst[12], src1, src2, 12);
        helper(species, dst[13], src1, src2, 13);
        helper(species, dst[14], src1, src2, 14);
        helper(species, dst[15], src1, src2, 15);
        helper(species, dst[16], src1, src2, 16);
        helper(species, dst[17], src1, src2, 17);
        helper(species, dst[18], src1, src2, 18);
        helper(species, dst[19], src1, src2, 19);
        helper(species, dst[20], src1, src2, 20);
        helper(species, dst[21], src1, src2, 21);
        helper(species, dst[22], src1, src2, 22);
        helper(species, dst[23], src1, src2, 23);
        helper(species, dst[24], src1, src2, 24);
        helper(species, dst[25], src1, src2, 25);
        helper(species, dst[26], src1, src2, 26);
        helper(species, dst[27], src1, src2, 27);
        helper(species, dst[28], src1, src2, 28);
        helper(species, dst[29], src1, src2, 29);
        helper(species, dst[30], src1, src2, 30);
        helper(species, dst[31], src1, src2, 31);
        helper(species, dst[32], src1, src2, 32);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "65"}, applyIfCPUFeature = {"avx512bw", "true"})
    static void testB512(byte[][] dst, byte[] src1, byte[] src2) {
        var species = ByteVector.SPECIES_512;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
        helper(species, dst[9], src1, src2, 9);
        helper(species, dst[10], src1, src2, 10);
        helper(species, dst[11], src1, src2, 11);
        helper(species, dst[12], src1, src2, 12);
        helper(species, dst[13], src1, src2, 13);
        helper(species, dst[14], src1, src2, 14);
        helper(species, dst[15], src1, src2, 15);
        helper(species, dst[16], src1, src2, 16);
        helper(species, dst[17], src1, src2, 17);
        helper(species, dst[18], src1, src2, 18);
        helper(species, dst[19], src1, src2, 19);
        helper(species, dst[20], src1, src2, 20);
        helper(species, dst[21], src1, src2, 21);
        helper(species, dst[22], src1, src2, 22);
        helper(species, dst[23], src1, src2, 23);
        helper(species, dst[24], src1, src2, 24);
        helper(species, dst[25], src1, src2, 25);
        helper(species, dst[26], src1, src2, 26);
        helper(species, dst[27], src1, src2, 27);
        helper(species, dst[28], src1, src2, 28);
        helper(species, dst[29], src1, src2, 29);
        helper(species, dst[30], src1, src2, 30);
        helper(species, dst[31], src1, src2, 31);
        helper(species, dst[32], src1, src2, 32);
        helper(species, dst[33], src1, src2, 33);
        helper(species, dst[34], src1, src2, 34);
        helper(species, dst[35], src1, src2, 35);
        helper(species, dst[36], src1, src2, 36);
        helper(species, dst[37], src1, src2, 37);
        helper(species, dst[38], src1, src2, 38);
        helper(species, dst[39], src1, src2, 39);
        helper(species, dst[40], src1, src2, 40);
        helper(species, dst[41], src1, src2, 41);
        helper(species, dst[42], src1, src2, 42);
        helper(species, dst[43], src1, src2, 43);
        helper(species, dst[44], src1, src2, 44);
        helper(species, dst[45], src1, src2, 45);
        helper(species, dst[46], src1, src2, 46);
        helper(species, dst[47], src1, src2, 47);
        helper(species, dst[48], src1, src2, 48);
        helper(species, dst[49], src1, src2, 49);
        helper(species, dst[50], src1, src2, 50);
        helper(species, dst[51], src1, src2, 51);
        helper(species, dst[52], src1, src2, 52);
        helper(species, dst[53], src1, src2, 53);
        helper(species, dst[54], src1, src2, 54);
        helper(species, dst[55], src1, src2, 55);
        helper(species, dst[56], src1, src2, 56);
        helper(species, dst[57], src1, src2, 57);
        helper(species, dst[58], src1, src2, 58);
        helper(species, dst[59], src1, src2, 59);
        helper(species, dst[60], src1, src2, 60);
        helper(species, dst[61], src1, src2, 61);
        helper(species, dst[62], src1, src2, 62);
        helper(species, dst[63], src1, src2, 63);
        helper(species, dst[64], src1, src2, 64);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "5"})
    static void testS64(short[][] dst, short[] src1, short[] src2) {
        var species = ShortVector.SPECIES_64;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "9"})
    static void testS128(short[][] dst, short[] src1, short[] src2) {
        var species = ShortVector.SPECIES_128;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "17"}, applyIfCPUFeature = {"avx2", "true"})
    static void testS256(short[][] dst, short[] src1, short[] src2) {
        var species = ShortVector.SPECIES_256;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
        helper(species, dst[9], src1, src2, 9);
        helper(species, dst[10], src1, src2, 10);
        helper(species, dst[11], src1, src2, 11);
        helper(species, dst[12], src1, src2, 12);
        helper(species, dst[13], src1, src2, 13);
        helper(species, dst[14], src1, src2, 14);
        helper(species, dst[15], src1, src2, 15);
        helper(species, dst[16], src1, src2, 16);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "33"}, applyIfCPUFeature = {"avx512bw", "true"})
    static void testS512(short[][] dst, short[] src1, short[] src2) {
        var species = ShortVector.SPECIES_512;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
        helper(species, dst[9], src1, src2, 9);
        helper(species, dst[10], src1, src2, 10);
        helper(species, dst[11], src1, src2, 11);
        helper(species, dst[12], src1, src2, 12);
        helper(species, dst[13], src1, src2, 13);
        helper(species, dst[14], src1, src2, 14);
        helper(species, dst[15], src1, src2, 15);
        helper(species, dst[16], src1, src2, 16);
        helper(species, dst[17], src1, src2, 17);
        helper(species, dst[18], src1, src2, 18);
        helper(species, dst[19], src1, src2, 19);
        helper(species, dst[20], src1, src2, 20);
        helper(species, dst[21], src1, src2, 21);
        helper(species, dst[22], src1, src2, 22);
        helper(species, dst[23], src1, src2, 23);
        helper(species, dst[24], src1, src2, 24);
        helper(species, dst[25], src1, src2, 25);
        helper(species, dst[26], src1, src2, 26);
        helper(species, dst[27], src1, src2, 27);
        helper(species, dst[28], src1, src2, 28);
        helper(species, dst[29], src1, src2, 29);
        helper(species, dst[30], src1, src2, 30);
        helper(species, dst[31], src1, src2, 31);
        helper(species, dst[32], src1, src2, 32);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "3"})
    static void testI64(int[][] dst, int[] src1, int[] src2) {
        var species = IntVector.SPECIES_64;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "5"})
    static void testI128(int[][] dst, int[] src1, int[] src2) {
        var species = IntVector.SPECIES_128;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "9"}, applyIfCPUFeature = {"avx2", "true"})
    static void testI256(int[][] dst, int[] src1, int[] src2) {
        var species = IntVector.SPECIES_256;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "17"}, applyIfCPUFeature = {"avx512f", "true"})
    static void testI512(int[][] dst, int[] src1, int[] src2) {
        var species = IntVector.SPECIES_512;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
        helper(species, dst[9], src1, src2, 9);
        helper(species, dst[10], src1, src2, 10);
        helper(species, dst[11], src1, src2, 11);
        helper(species, dst[12], src1, src2, 12);
        helper(species, dst[13], src1, src2, 13);
        helper(species, dst[14], src1, src2, 14);
        helper(species, dst[15], src1, src2, 15);
        helper(species, dst[16], src1, src2, 16);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "3"})
    static void testL128(long[][] dst, long[] src1, long[] src2) {
        var species = LongVector.SPECIES_128;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "5"}, applyIfCPUFeature = {"avx2", "true"})
    static void testL256(long[][] dst, long[] src1, long[] src2) {
        var species = LongVector.SPECIES_256;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "9"}, applyIfCPUFeature = {"avx512f", "true"})
    static void testL512(long[][] dst, long[] src1, long[] src2) {
        var species = LongVector.SPECIES_512;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "3"})
    static void testF64(float[][] dst, float[] src1, float[] src2) {
        var species = FloatVector.SPECIES_64;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "5"})
    static void testF128(float[][] dst, float[] src1, float[] src2) {
        var species = FloatVector.SPECIES_128;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "9"}, applyIfCPUFeature = {"avx", "true"})
    static void testF256(float[][] dst, float[] src1, float[] src2) {
        var species = FloatVector.SPECIES_256;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "17"}, applyIfCPUFeature = {"avx512f", "true"})
    static void testF512(float[][] dst, float[] src1, float[] src2) {
        var species = FloatVector.SPECIES_512;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
        helper(species, dst[9], src1, src2, 9);
        helper(species, dst[10], src1, src2, 10);
        helper(species, dst[11], src1, src2, 11);
        helper(species, dst[12], src1, src2, 12);
        helper(species, dst[13], src1, src2, 13);
        helper(species, dst[14], src1, src2, 14);
        helper(species, dst[15], src1, src2, 15);
        helper(species, dst[16], src1, src2, 16);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "3"})
    static void testD128(double[][] dst, double[] src1, double[] src2) {
        var species = DoubleVector.SPECIES_128;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "5"}, applyIfCPUFeature = {"avx", "true"})
    static void testD256(double[][] dst, double[] src1, double[] src2) {
        var species = DoubleVector.SPECIES_256;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
    }

    @Test
    @IR(counts = {IRNode.VECTOR_SLICE, "9"}, applyIfCPUFeature = {"avx512f", "true"})
    static void testD512(double[][] dst, double[] src1, double[] src2) {
        var species = DoubleVector.SPECIES_512;
        helper(species, dst[0], src1, src2, 0);
        helper(species, dst[1], src1, src2, 1);
        helper(species, dst[2], src1, src2, 2);
        helper(species, dst[3], src1, src2, 3);
        helper(species, dst[4], src1, src2, 4);
        helper(species, dst[5], src1, src2, 5);
        helper(species, dst[6], src1, src2, 6);
        helper(species, dst[7], src1, src2, 7);
        helper(species, dst[8], src1, src2, 8);
    }

    @ForceInline
    static void helper(VectorSpecies<Byte> species, byte[] dst, byte[] src1, byte[] src2, int origin) {
        var v1 = ByteVector.fromArray(species, src1, 0);
        var v2 = ByteVector.fromArray(species, src2, 0);
        v1.slice(origin, v2).intoArray(dst, 0);
    }

    @ForceInline
    static void helper(VectorSpecies<Short> species, short[] dst, short[] src1, short[] src2, int origin) {
        var v1 = ShortVector.fromArray(species, src1, 0);
        var v2 = ShortVector.fromArray(species, src2, 0);
        v1.slice(origin, v2).intoArray(dst, 0);
    }

    @ForceInline
    static void helper(VectorSpecies<Integer> species, int[] dst, int[] src1, int[] src2, int origin) {
        var v1 = IntVector.fromArray(species, src1, 0);
        var v2 = IntVector.fromArray(species, src2, 0);
        v1.slice(origin, v2).intoArray(dst, 0);
    }

    @ForceInline
    static void helper(VectorSpecies<Long> species, long[] dst, long[] src1, long[] src2, int origin) {
        var v1 = LongVector.fromArray(species, src1, 0);
        var v2 = LongVector.fromArray(species, src2, 0);
        v1.slice(origin, v2).intoArray(dst, 0);
    }

    @ForceInline
    static void helper(VectorSpecies<Float> species, float[] dst, float[] src1, float[] src2, int origin) {
        var v1 = FloatVector.fromArray(species, src1, 0);
        var v2 = FloatVector.fromArray(species, src2, 0);
        v1.slice(origin, v2).intoArray(dst, 0);
    }

    @ForceInline
    static void helper(VectorSpecies<Double> species, double[] dst, double[] src1, double[] src2, int origin) {
        var v1 = DoubleVector.fromArray(species, src1, 0);
        var v2 = DoubleVector.fromArray(species, src2, 0);
        v1.slice(origin, v2).intoArray(dst, 0);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
