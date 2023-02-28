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

/*
 * @test
 * @bug 8298935
 * @summary Test SuperWord vectorization with different access offsets
 *          and various SuperWordMaxVectorSize values, and +- AlignVector.
 *          Note: CompileCommand Option Vectorize is enabled.
 * @requires vm.compiler2.enabled
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestDependencyOffsets
 */

package compiler.loopopts.superword;
import compiler.lib.ir_framework.*;

/*
 * Note: this test is auto-generated. Please modify / generate with script:
 *       https://bugs.openjdk.org/browse/JDK-8298935
 *
 * Types: int, long, short, char, byte, float, double
 * Offsets: 0, -1, 1, -2, 2, -3, 3, -4, 4, -7, 7, -8, 8, -15, 15, -16, 16, -31, 31, -32, 32, -63, 63, -64, 64, -65, 65, -128, 128, -129, 129, -192, 192
 *
 * Checking if we should vectorize is a bit complicated. It depends on
 * Matcher::vector_width_in_bytes, of the respective platforms (eg. x86.ad)
 * This vector_width can be further constrained by SuperWordMaxVectorSize.
 *
 * Generally, we vectorize if:
 *  - Vectors have at least 4 bytes:    vector_width >= 4
 *  - Vectors hold at least 2 elements: vector_width >= 2 * sizeofop(velt_type)
 *  - No cyclic dependency:
 *    - Access: data[i + offset] = data[i] * fac;
 *    - byte_offset = offset * sizeofop(type)
 *    - Cyclic dependency if: 0 < byte_offset < vector_width
 *
 * Note: sizeofop(type) = sizeof(type), except sizeofop(char) = 2
 *
 * Different types can lead to different vector_width. This depends on
 * the CPU-features. Thus, we have a positive and negative IR rule per
 * CPU-feature for each test.
*/

public class TestDependencyOffsets {
    static final int RANGE = 512;

    static int[] goldIntP0 = new int[RANGE];
    static int[] goldIntM1 = new int[RANGE];
    static int[] goldIntP1 = new int[RANGE];
    static int[] goldIntM2 = new int[RANGE];
    static int[] goldIntP2 = new int[RANGE];
    static int[] goldIntM3 = new int[RANGE];
    static int[] goldIntP3 = new int[RANGE];
    static int[] goldIntM4 = new int[RANGE];
    static int[] goldIntP4 = new int[RANGE];
    static int[] goldIntM7 = new int[RANGE];
    static int[] goldIntP7 = new int[RANGE];
    static int[] goldIntM8 = new int[RANGE];
    static int[] goldIntP8 = new int[RANGE];
    static int[] goldIntM15 = new int[RANGE];
    static int[] goldIntP15 = new int[RANGE];
    static int[] goldIntM16 = new int[RANGE];
    static int[] goldIntP16 = new int[RANGE];
    static int[] goldIntM31 = new int[RANGE];
    static int[] goldIntP31 = new int[RANGE];
    static int[] goldIntM32 = new int[RANGE];
    static int[] goldIntP32 = new int[RANGE];
    static int[] goldIntM63 = new int[RANGE];
    static int[] goldIntP63 = new int[RANGE];
    static int[] goldIntM64 = new int[RANGE];
    static int[] goldIntP64 = new int[RANGE];
    static int[] goldIntM65 = new int[RANGE];
    static int[] goldIntP65 = new int[RANGE];
    static int[] goldIntM128 = new int[RANGE];
    static int[] goldIntP128 = new int[RANGE];
    static int[] goldIntM129 = new int[RANGE];
    static int[] goldIntP129 = new int[RANGE];
    static int[] goldIntM192 = new int[RANGE];
    static int[] goldIntP192 = new int[RANGE];
    static long[] goldLongP0 = new long[RANGE];
    static long[] goldLongM1 = new long[RANGE];
    static long[] goldLongP1 = new long[RANGE];
    static long[] goldLongM2 = new long[RANGE];
    static long[] goldLongP2 = new long[RANGE];
    static long[] goldLongM3 = new long[RANGE];
    static long[] goldLongP3 = new long[RANGE];
    static long[] goldLongM4 = new long[RANGE];
    static long[] goldLongP4 = new long[RANGE];
    static long[] goldLongM7 = new long[RANGE];
    static long[] goldLongP7 = new long[RANGE];
    static long[] goldLongM8 = new long[RANGE];
    static long[] goldLongP8 = new long[RANGE];
    static long[] goldLongM15 = new long[RANGE];
    static long[] goldLongP15 = new long[RANGE];
    static long[] goldLongM16 = new long[RANGE];
    static long[] goldLongP16 = new long[RANGE];
    static long[] goldLongM31 = new long[RANGE];
    static long[] goldLongP31 = new long[RANGE];
    static long[] goldLongM32 = new long[RANGE];
    static long[] goldLongP32 = new long[RANGE];
    static long[] goldLongM63 = new long[RANGE];
    static long[] goldLongP63 = new long[RANGE];
    static long[] goldLongM64 = new long[RANGE];
    static long[] goldLongP64 = new long[RANGE];
    static long[] goldLongM65 = new long[RANGE];
    static long[] goldLongP65 = new long[RANGE];
    static long[] goldLongM128 = new long[RANGE];
    static long[] goldLongP128 = new long[RANGE];
    static long[] goldLongM129 = new long[RANGE];
    static long[] goldLongP129 = new long[RANGE];
    static long[] goldLongM192 = new long[RANGE];
    static long[] goldLongP192 = new long[RANGE];
    static short[] goldShortP0 = new short[RANGE];
    static short[] goldShortM1 = new short[RANGE];
    static short[] goldShortP1 = new short[RANGE];
    static short[] goldShortM2 = new short[RANGE];
    static short[] goldShortP2 = new short[RANGE];
    static short[] goldShortM3 = new short[RANGE];
    static short[] goldShortP3 = new short[RANGE];
    static short[] goldShortM4 = new short[RANGE];
    static short[] goldShortP4 = new short[RANGE];
    static short[] goldShortM7 = new short[RANGE];
    static short[] goldShortP7 = new short[RANGE];
    static short[] goldShortM8 = new short[RANGE];
    static short[] goldShortP8 = new short[RANGE];
    static short[] goldShortM15 = new short[RANGE];
    static short[] goldShortP15 = new short[RANGE];
    static short[] goldShortM16 = new short[RANGE];
    static short[] goldShortP16 = new short[RANGE];
    static short[] goldShortM31 = new short[RANGE];
    static short[] goldShortP31 = new short[RANGE];
    static short[] goldShortM32 = new short[RANGE];
    static short[] goldShortP32 = new short[RANGE];
    static short[] goldShortM63 = new short[RANGE];
    static short[] goldShortP63 = new short[RANGE];
    static short[] goldShortM64 = new short[RANGE];
    static short[] goldShortP64 = new short[RANGE];
    static short[] goldShortM65 = new short[RANGE];
    static short[] goldShortP65 = new short[RANGE];
    static short[] goldShortM128 = new short[RANGE];
    static short[] goldShortP128 = new short[RANGE];
    static short[] goldShortM129 = new short[RANGE];
    static short[] goldShortP129 = new short[RANGE];
    static short[] goldShortM192 = new short[RANGE];
    static short[] goldShortP192 = new short[RANGE];
    static char[] goldCharP0 = new char[RANGE];
    static char[] goldCharM1 = new char[RANGE];
    static char[] goldCharP1 = new char[RANGE];
    static char[] goldCharM2 = new char[RANGE];
    static char[] goldCharP2 = new char[RANGE];
    static char[] goldCharM3 = new char[RANGE];
    static char[] goldCharP3 = new char[RANGE];
    static char[] goldCharM4 = new char[RANGE];
    static char[] goldCharP4 = new char[RANGE];
    static char[] goldCharM7 = new char[RANGE];
    static char[] goldCharP7 = new char[RANGE];
    static char[] goldCharM8 = new char[RANGE];
    static char[] goldCharP8 = new char[RANGE];
    static char[] goldCharM15 = new char[RANGE];
    static char[] goldCharP15 = new char[RANGE];
    static char[] goldCharM16 = new char[RANGE];
    static char[] goldCharP16 = new char[RANGE];
    static char[] goldCharM31 = new char[RANGE];
    static char[] goldCharP31 = new char[RANGE];
    static char[] goldCharM32 = new char[RANGE];
    static char[] goldCharP32 = new char[RANGE];
    static char[] goldCharM63 = new char[RANGE];
    static char[] goldCharP63 = new char[RANGE];
    static char[] goldCharM64 = new char[RANGE];
    static char[] goldCharP64 = new char[RANGE];
    static char[] goldCharM65 = new char[RANGE];
    static char[] goldCharP65 = new char[RANGE];
    static char[] goldCharM128 = new char[RANGE];
    static char[] goldCharP128 = new char[RANGE];
    static char[] goldCharM129 = new char[RANGE];
    static char[] goldCharP129 = new char[RANGE];
    static char[] goldCharM192 = new char[RANGE];
    static char[] goldCharP192 = new char[RANGE];
    static byte[] goldByteP0 = new byte[RANGE];
    static byte[] goldByteM1 = new byte[RANGE];
    static byte[] goldByteP1 = new byte[RANGE];
    static byte[] goldByteM2 = new byte[RANGE];
    static byte[] goldByteP2 = new byte[RANGE];
    static byte[] goldByteM3 = new byte[RANGE];
    static byte[] goldByteP3 = new byte[RANGE];
    static byte[] goldByteM4 = new byte[RANGE];
    static byte[] goldByteP4 = new byte[RANGE];
    static byte[] goldByteM7 = new byte[RANGE];
    static byte[] goldByteP7 = new byte[RANGE];
    static byte[] goldByteM8 = new byte[RANGE];
    static byte[] goldByteP8 = new byte[RANGE];
    static byte[] goldByteM15 = new byte[RANGE];
    static byte[] goldByteP15 = new byte[RANGE];
    static byte[] goldByteM16 = new byte[RANGE];
    static byte[] goldByteP16 = new byte[RANGE];
    static byte[] goldByteM31 = new byte[RANGE];
    static byte[] goldByteP31 = new byte[RANGE];
    static byte[] goldByteM32 = new byte[RANGE];
    static byte[] goldByteP32 = new byte[RANGE];
    static byte[] goldByteM63 = new byte[RANGE];
    static byte[] goldByteP63 = new byte[RANGE];
    static byte[] goldByteM64 = new byte[RANGE];
    static byte[] goldByteP64 = new byte[RANGE];
    static byte[] goldByteM65 = new byte[RANGE];
    static byte[] goldByteP65 = new byte[RANGE];
    static byte[] goldByteM128 = new byte[RANGE];
    static byte[] goldByteP128 = new byte[RANGE];
    static byte[] goldByteM129 = new byte[RANGE];
    static byte[] goldByteP129 = new byte[RANGE];
    static byte[] goldByteM192 = new byte[RANGE];
    static byte[] goldByteP192 = new byte[RANGE];
    static float[] goldFloatP0 = new float[RANGE];
    static float[] goldFloatM1 = new float[RANGE];
    static float[] goldFloatP1 = new float[RANGE];
    static float[] goldFloatM2 = new float[RANGE];
    static float[] goldFloatP2 = new float[RANGE];
    static float[] goldFloatM3 = new float[RANGE];
    static float[] goldFloatP3 = new float[RANGE];
    static float[] goldFloatM4 = new float[RANGE];
    static float[] goldFloatP4 = new float[RANGE];
    static float[] goldFloatM7 = new float[RANGE];
    static float[] goldFloatP7 = new float[RANGE];
    static float[] goldFloatM8 = new float[RANGE];
    static float[] goldFloatP8 = new float[RANGE];
    static float[] goldFloatM15 = new float[RANGE];
    static float[] goldFloatP15 = new float[RANGE];
    static float[] goldFloatM16 = new float[RANGE];
    static float[] goldFloatP16 = new float[RANGE];
    static float[] goldFloatM31 = new float[RANGE];
    static float[] goldFloatP31 = new float[RANGE];
    static float[] goldFloatM32 = new float[RANGE];
    static float[] goldFloatP32 = new float[RANGE];
    static float[] goldFloatM63 = new float[RANGE];
    static float[] goldFloatP63 = new float[RANGE];
    static float[] goldFloatM64 = new float[RANGE];
    static float[] goldFloatP64 = new float[RANGE];
    static float[] goldFloatM65 = new float[RANGE];
    static float[] goldFloatP65 = new float[RANGE];
    static float[] goldFloatM128 = new float[RANGE];
    static float[] goldFloatP128 = new float[RANGE];
    static float[] goldFloatM129 = new float[RANGE];
    static float[] goldFloatP129 = new float[RANGE];
    static float[] goldFloatM192 = new float[RANGE];
    static float[] goldFloatP192 = new float[RANGE];
    static double[] goldDoubleP0 = new double[RANGE];
    static double[] goldDoubleM1 = new double[RANGE];
    static double[] goldDoubleP1 = new double[RANGE];
    static double[] goldDoubleM2 = new double[RANGE];
    static double[] goldDoubleP2 = new double[RANGE];
    static double[] goldDoubleM3 = new double[RANGE];
    static double[] goldDoubleP3 = new double[RANGE];
    static double[] goldDoubleM4 = new double[RANGE];
    static double[] goldDoubleP4 = new double[RANGE];
    static double[] goldDoubleM7 = new double[RANGE];
    static double[] goldDoubleP7 = new double[RANGE];
    static double[] goldDoubleM8 = new double[RANGE];
    static double[] goldDoubleP8 = new double[RANGE];
    static double[] goldDoubleM15 = new double[RANGE];
    static double[] goldDoubleP15 = new double[RANGE];
    static double[] goldDoubleM16 = new double[RANGE];
    static double[] goldDoubleP16 = new double[RANGE];
    static double[] goldDoubleM31 = new double[RANGE];
    static double[] goldDoubleP31 = new double[RANGE];
    static double[] goldDoubleM32 = new double[RANGE];
    static double[] goldDoubleP32 = new double[RANGE];
    static double[] goldDoubleM63 = new double[RANGE];
    static double[] goldDoubleP63 = new double[RANGE];
    static double[] goldDoubleM64 = new double[RANGE];
    static double[] goldDoubleP64 = new double[RANGE];
    static double[] goldDoubleM65 = new double[RANGE];
    static double[] goldDoubleP65 = new double[RANGE];
    static double[] goldDoubleM128 = new double[RANGE];
    static double[] goldDoubleP128 = new double[RANGE];
    static double[] goldDoubleM129 = new double[RANGE];
    static double[] goldDoubleP129 = new double[RANGE];
    static double[] goldDoubleM192 = new double[RANGE];
    static double[] goldDoubleP192 = new double[RANGE];

    static {
        // compute the gold standard in interpreter mode
        init(goldIntP0);
        testIntP0(goldIntP0);
        init(goldIntM1);
        testIntM1(goldIntM1);
        init(goldIntP1);
        testIntP1(goldIntP1);
        init(goldIntM2);
        testIntM2(goldIntM2);
        init(goldIntP2);
        testIntP2(goldIntP2);
        init(goldIntM3);
        testIntM3(goldIntM3);
        init(goldIntP3);
        testIntP3(goldIntP3);
        init(goldIntM4);
        testIntM4(goldIntM4);
        init(goldIntP4);
        testIntP4(goldIntP4);
        init(goldIntM7);
        testIntM7(goldIntM7);
        init(goldIntP7);
        testIntP7(goldIntP7);
        init(goldIntM8);
        testIntM8(goldIntM8);
        init(goldIntP8);
        testIntP8(goldIntP8);
        init(goldIntM15);
        testIntM15(goldIntM15);
        init(goldIntP15);
        testIntP15(goldIntP15);
        init(goldIntM16);
        testIntM16(goldIntM16);
        init(goldIntP16);
        testIntP16(goldIntP16);
        init(goldIntM31);
        testIntM31(goldIntM31);
        init(goldIntP31);
        testIntP31(goldIntP31);
        init(goldIntM32);
        testIntM32(goldIntM32);
        init(goldIntP32);
        testIntP32(goldIntP32);
        init(goldIntM63);
        testIntM63(goldIntM63);
        init(goldIntP63);
        testIntP63(goldIntP63);
        init(goldIntM64);
        testIntM64(goldIntM64);
        init(goldIntP64);
        testIntP64(goldIntP64);
        init(goldIntM65);
        testIntM65(goldIntM65);
        init(goldIntP65);
        testIntP65(goldIntP65);
        init(goldIntM128);
        testIntM128(goldIntM128);
        init(goldIntP128);
        testIntP128(goldIntP128);
        init(goldIntM129);
        testIntM129(goldIntM129);
        init(goldIntP129);
        testIntP129(goldIntP129);
        init(goldIntM192);
        testIntM192(goldIntM192);
        init(goldIntP192);
        testIntP192(goldIntP192);
        init(goldLongP0);
        testLongP0(goldLongP0);
        init(goldLongM1);
        testLongM1(goldLongM1);
        init(goldLongP1);
        testLongP1(goldLongP1);
        init(goldLongM2);
        testLongM2(goldLongM2);
        init(goldLongP2);
        testLongP2(goldLongP2);
        init(goldLongM3);
        testLongM3(goldLongM3);
        init(goldLongP3);
        testLongP3(goldLongP3);
        init(goldLongM4);
        testLongM4(goldLongM4);
        init(goldLongP4);
        testLongP4(goldLongP4);
        init(goldLongM7);
        testLongM7(goldLongM7);
        init(goldLongP7);
        testLongP7(goldLongP7);
        init(goldLongM8);
        testLongM8(goldLongM8);
        init(goldLongP8);
        testLongP8(goldLongP8);
        init(goldLongM15);
        testLongM15(goldLongM15);
        init(goldLongP15);
        testLongP15(goldLongP15);
        init(goldLongM16);
        testLongM16(goldLongM16);
        init(goldLongP16);
        testLongP16(goldLongP16);
        init(goldLongM31);
        testLongM31(goldLongM31);
        init(goldLongP31);
        testLongP31(goldLongP31);
        init(goldLongM32);
        testLongM32(goldLongM32);
        init(goldLongP32);
        testLongP32(goldLongP32);
        init(goldLongM63);
        testLongM63(goldLongM63);
        init(goldLongP63);
        testLongP63(goldLongP63);
        init(goldLongM64);
        testLongM64(goldLongM64);
        init(goldLongP64);
        testLongP64(goldLongP64);
        init(goldLongM65);
        testLongM65(goldLongM65);
        init(goldLongP65);
        testLongP65(goldLongP65);
        init(goldLongM128);
        testLongM128(goldLongM128);
        init(goldLongP128);
        testLongP128(goldLongP128);
        init(goldLongM129);
        testLongM129(goldLongM129);
        init(goldLongP129);
        testLongP129(goldLongP129);
        init(goldLongM192);
        testLongM192(goldLongM192);
        init(goldLongP192);
        testLongP192(goldLongP192);
        init(goldShortP0);
        testShortP0(goldShortP0);
        init(goldShortM1);
        testShortM1(goldShortM1);
        init(goldShortP1);
        testShortP1(goldShortP1);
        init(goldShortM2);
        testShortM2(goldShortM2);
        init(goldShortP2);
        testShortP2(goldShortP2);
        init(goldShortM3);
        testShortM3(goldShortM3);
        init(goldShortP3);
        testShortP3(goldShortP3);
        init(goldShortM4);
        testShortM4(goldShortM4);
        init(goldShortP4);
        testShortP4(goldShortP4);
        init(goldShortM7);
        testShortM7(goldShortM7);
        init(goldShortP7);
        testShortP7(goldShortP7);
        init(goldShortM8);
        testShortM8(goldShortM8);
        init(goldShortP8);
        testShortP8(goldShortP8);
        init(goldShortM15);
        testShortM15(goldShortM15);
        init(goldShortP15);
        testShortP15(goldShortP15);
        init(goldShortM16);
        testShortM16(goldShortM16);
        init(goldShortP16);
        testShortP16(goldShortP16);
        init(goldShortM31);
        testShortM31(goldShortM31);
        init(goldShortP31);
        testShortP31(goldShortP31);
        init(goldShortM32);
        testShortM32(goldShortM32);
        init(goldShortP32);
        testShortP32(goldShortP32);
        init(goldShortM63);
        testShortM63(goldShortM63);
        init(goldShortP63);
        testShortP63(goldShortP63);
        init(goldShortM64);
        testShortM64(goldShortM64);
        init(goldShortP64);
        testShortP64(goldShortP64);
        init(goldShortM65);
        testShortM65(goldShortM65);
        init(goldShortP65);
        testShortP65(goldShortP65);
        init(goldShortM128);
        testShortM128(goldShortM128);
        init(goldShortP128);
        testShortP128(goldShortP128);
        init(goldShortM129);
        testShortM129(goldShortM129);
        init(goldShortP129);
        testShortP129(goldShortP129);
        init(goldShortM192);
        testShortM192(goldShortM192);
        init(goldShortP192);
        testShortP192(goldShortP192);
        init(goldCharP0);
        testCharP0(goldCharP0);
        init(goldCharM1);
        testCharM1(goldCharM1);
        init(goldCharP1);
        testCharP1(goldCharP1);
        init(goldCharM2);
        testCharM2(goldCharM2);
        init(goldCharP2);
        testCharP2(goldCharP2);
        init(goldCharM3);
        testCharM3(goldCharM3);
        init(goldCharP3);
        testCharP3(goldCharP3);
        init(goldCharM4);
        testCharM4(goldCharM4);
        init(goldCharP4);
        testCharP4(goldCharP4);
        init(goldCharM7);
        testCharM7(goldCharM7);
        init(goldCharP7);
        testCharP7(goldCharP7);
        init(goldCharM8);
        testCharM8(goldCharM8);
        init(goldCharP8);
        testCharP8(goldCharP8);
        init(goldCharM15);
        testCharM15(goldCharM15);
        init(goldCharP15);
        testCharP15(goldCharP15);
        init(goldCharM16);
        testCharM16(goldCharM16);
        init(goldCharP16);
        testCharP16(goldCharP16);
        init(goldCharM31);
        testCharM31(goldCharM31);
        init(goldCharP31);
        testCharP31(goldCharP31);
        init(goldCharM32);
        testCharM32(goldCharM32);
        init(goldCharP32);
        testCharP32(goldCharP32);
        init(goldCharM63);
        testCharM63(goldCharM63);
        init(goldCharP63);
        testCharP63(goldCharP63);
        init(goldCharM64);
        testCharM64(goldCharM64);
        init(goldCharP64);
        testCharP64(goldCharP64);
        init(goldCharM65);
        testCharM65(goldCharM65);
        init(goldCharP65);
        testCharP65(goldCharP65);
        init(goldCharM128);
        testCharM128(goldCharM128);
        init(goldCharP128);
        testCharP128(goldCharP128);
        init(goldCharM129);
        testCharM129(goldCharM129);
        init(goldCharP129);
        testCharP129(goldCharP129);
        init(goldCharM192);
        testCharM192(goldCharM192);
        init(goldCharP192);
        testCharP192(goldCharP192);
        init(goldByteP0);
        testByteP0(goldByteP0);
        init(goldByteM1);
        testByteM1(goldByteM1);
        init(goldByteP1);
        testByteP1(goldByteP1);
        init(goldByteM2);
        testByteM2(goldByteM2);
        init(goldByteP2);
        testByteP2(goldByteP2);
        init(goldByteM3);
        testByteM3(goldByteM3);
        init(goldByteP3);
        testByteP3(goldByteP3);
        init(goldByteM4);
        testByteM4(goldByteM4);
        init(goldByteP4);
        testByteP4(goldByteP4);
        init(goldByteM7);
        testByteM7(goldByteM7);
        init(goldByteP7);
        testByteP7(goldByteP7);
        init(goldByteM8);
        testByteM8(goldByteM8);
        init(goldByteP8);
        testByteP8(goldByteP8);
        init(goldByteM15);
        testByteM15(goldByteM15);
        init(goldByteP15);
        testByteP15(goldByteP15);
        init(goldByteM16);
        testByteM16(goldByteM16);
        init(goldByteP16);
        testByteP16(goldByteP16);
        init(goldByteM31);
        testByteM31(goldByteM31);
        init(goldByteP31);
        testByteP31(goldByteP31);
        init(goldByteM32);
        testByteM32(goldByteM32);
        init(goldByteP32);
        testByteP32(goldByteP32);
        init(goldByteM63);
        testByteM63(goldByteM63);
        init(goldByteP63);
        testByteP63(goldByteP63);
        init(goldByteM64);
        testByteM64(goldByteM64);
        init(goldByteP64);
        testByteP64(goldByteP64);
        init(goldByteM65);
        testByteM65(goldByteM65);
        init(goldByteP65);
        testByteP65(goldByteP65);
        init(goldByteM128);
        testByteM128(goldByteM128);
        init(goldByteP128);
        testByteP128(goldByteP128);
        init(goldByteM129);
        testByteM129(goldByteM129);
        init(goldByteP129);
        testByteP129(goldByteP129);
        init(goldByteM192);
        testByteM192(goldByteM192);
        init(goldByteP192);
        testByteP192(goldByteP192);
        init(goldFloatP0);
        testFloatP0(goldFloatP0);
        init(goldFloatM1);
        testFloatM1(goldFloatM1);
        init(goldFloatP1);
        testFloatP1(goldFloatP1);
        init(goldFloatM2);
        testFloatM2(goldFloatM2);
        init(goldFloatP2);
        testFloatP2(goldFloatP2);
        init(goldFloatM3);
        testFloatM3(goldFloatM3);
        init(goldFloatP3);
        testFloatP3(goldFloatP3);
        init(goldFloatM4);
        testFloatM4(goldFloatM4);
        init(goldFloatP4);
        testFloatP4(goldFloatP4);
        init(goldFloatM7);
        testFloatM7(goldFloatM7);
        init(goldFloatP7);
        testFloatP7(goldFloatP7);
        init(goldFloatM8);
        testFloatM8(goldFloatM8);
        init(goldFloatP8);
        testFloatP8(goldFloatP8);
        init(goldFloatM15);
        testFloatM15(goldFloatM15);
        init(goldFloatP15);
        testFloatP15(goldFloatP15);
        init(goldFloatM16);
        testFloatM16(goldFloatM16);
        init(goldFloatP16);
        testFloatP16(goldFloatP16);
        init(goldFloatM31);
        testFloatM31(goldFloatM31);
        init(goldFloatP31);
        testFloatP31(goldFloatP31);
        init(goldFloatM32);
        testFloatM32(goldFloatM32);
        init(goldFloatP32);
        testFloatP32(goldFloatP32);
        init(goldFloatM63);
        testFloatM63(goldFloatM63);
        init(goldFloatP63);
        testFloatP63(goldFloatP63);
        init(goldFloatM64);
        testFloatM64(goldFloatM64);
        init(goldFloatP64);
        testFloatP64(goldFloatP64);
        init(goldFloatM65);
        testFloatM65(goldFloatM65);
        init(goldFloatP65);
        testFloatP65(goldFloatP65);
        init(goldFloatM128);
        testFloatM128(goldFloatM128);
        init(goldFloatP128);
        testFloatP128(goldFloatP128);
        init(goldFloatM129);
        testFloatM129(goldFloatM129);
        init(goldFloatP129);
        testFloatP129(goldFloatP129);
        init(goldFloatM192);
        testFloatM192(goldFloatM192);
        init(goldFloatP192);
        testFloatP192(goldFloatP192);
        init(goldDoubleP0);
        testDoubleP0(goldDoubleP0);
        init(goldDoubleM1);
        testDoubleM1(goldDoubleM1);
        init(goldDoubleP1);
        testDoubleP1(goldDoubleP1);
        init(goldDoubleM2);
        testDoubleM2(goldDoubleM2);
        init(goldDoubleP2);
        testDoubleP2(goldDoubleP2);
        init(goldDoubleM3);
        testDoubleM3(goldDoubleM3);
        init(goldDoubleP3);
        testDoubleP3(goldDoubleP3);
        init(goldDoubleM4);
        testDoubleM4(goldDoubleM4);
        init(goldDoubleP4);
        testDoubleP4(goldDoubleP4);
        init(goldDoubleM7);
        testDoubleM7(goldDoubleM7);
        init(goldDoubleP7);
        testDoubleP7(goldDoubleP7);
        init(goldDoubleM8);
        testDoubleM8(goldDoubleM8);
        init(goldDoubleP8);
        testDoubleP8(goldDoubleP8);
        init(goldDoubleM15);
        testDoubleM15(goldDoubleM15);
        init(goldDoubleP15);
        testDoubleP15(goldDoubleP15);
        init(goldDoubleM16);
        testDoubleM16(goldDoubleM16);
        init(goldDoubleP16);
        testDoubleP16(goldDoubleP16);
        init(goldDoubleM31);
        testDoubleM31(goldDoubleM31);
        init(goldDoubleP31);
        testDoubleP31(goldDoubleP31);
        init(goldDoubleM32);
        testDoubleM32(goldDoubleM32);
        init(goldDoubleP32);
        testDoubleP32(goldDoubleP32);
        init(goldDoubleM63);
        testDoubleM63(goldDoubleM63);
        init(goldDoubleP63);
        testDoubleP63(goldDoubleP63);
        init(goldDoubleM64);
        testDoubleM64(goldDoubleM64);
        init(goldDoubleP64);
        testDoubleP64(goldDoubleP64);
        init(goldDoubleM65);
        testDoubleM65(goldDoubleM65);
        init(goldDoubleP65);
        testDoubleP65(goldDoubleP65);
        init(goldDoubleM128);
        testDoubleM128(goldDoubleM128);
        init(goldDoubleP128);
        testDoubleP128(goldDoubleP128);
        init(goldDoubleM129);
        testDoubleM129(goldDoubleM129);
        init(goldDoubleP129);
        testDoubleP129(goldDoubleP129);
        init(goldDoubleM192);
        testDoubleM192(goldDoubleM192);
        init(goldDoubleP192);
        testDoubleP192(goldDoubleP192);
    }

    public static void main(String args[]) {
        TestDependencyOffsets x = new TestDependencyOffsets();
        TestFramework framework = new TestFramework(TestDependencyOffsets.class);
        framework.addFlags("-XX:-TieredCompilation",
                           "-XX:CompileCommand=option,compiler.loopopts.superword.TestDependencyOffsets::test*,Vectorize",
                           "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestDependencyOffsets::init",
                           "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestDependencyOffsets::test*",
                           "-XX:CompileCommand=compileonly,compiler.loopopts.superword.TestDependencyOffsets::verify",
                           "-XX:LoopUnrollLimit=250");

        int i = 0;
        Scenario[] scenarios = new Scenario[8];
        for (int maxVectorSize : new int[] {1, 2, 4, 8, 16, 32, 64, 128}) {
            for (String alignVectorSign : new String[] {"-"}) { // TODO
                scenarios[i] = new Scenario(i, "-XX:" + alignVectorSign + "AlignVector",
                                               "-XX:SuperWordMaxVectorSize=" + maxVectorSize);
                i++;
            }
        }
        framework.addScenarios(scenarios);
        framework.start();
    }

    // ------------------- Tests -------------------

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP0(int[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j + 0] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP0")
    @Warmup(0)
    public static void runIntP0() {
        int[] data = new int[RANGE];
        init(data);
        testIntP0(data);
        verify("testIntP0", data, goldIntP0);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM1(int[] data) {
        for (int j = 1; j < RANGE; j++) {
            data[j + -1] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM1")
    @Warmup(0)
    public static void runIntM1() {
        int[] data = new int[RANGE];
        init(data);
        testIntM1(data);
        verify("testIntM1", data, goldIntM1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP1(int[] data) {
        for (int j = 0; j < RANGE - 1; j++) {
            data[j + 1] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP1")
    @Warmup(0)
    public static void runIntP1() {
        int[] data = new int[RANGE];
        init(data);
        testIntP1(data);
        verify("testIntP1", data, goldIntP1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM2(int[] data) {
        for (int j = 2; j < RANGE; j++) {
            data[j + -2] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM2")
    @Warmup(0)
    public static void runIntM2() {
        int[] data = new int[RANGE];
        init(data);
        testIntM2(data);
        verify("testIntM2", data, goldIntM2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP2(int[] data) {
        for (int j = 0; j < RANGE - 2; j++) {
            data[j + 2] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP2")
    @Warmup(0)
    public static void runIntP2() {
        int[] data = new int[RANGE];
        init(data);
        testIntP2(data);
        verify("testIntP2", data, goldIntP2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM3(int[] data) {
        for (int j = 3; j < RANGE; j++) {
            data[j + -3] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM3")
    @Warmup(0)
    public static void runIntM3() {
        int[] data = new int[RANGE];
        init(data);
        testIntM3(data);
        verify("testIntM3", data, goldIntM3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    // positive byte_offset 12 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 12"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 12"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 12 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 12"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 12"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 12 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 12"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 12"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 12 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 12"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 12"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP3(int[] data) {
        for (int j = 0; j < RANGE - 3; j++) {
            data[j + 3] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP3")
    @Warmup(0)
    public static void runIntP3() {
        int[] data = new int[RANGE];
        init(data);
        testIntP3(data);
        verify("testIntP3", data, goldIntP3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM4(int[] data) {
        for (int j = 4; j < RANGE; j++) {
            data[j + -4] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM4")
    @Warmup(0)
    public static void runIntM4() {
        int[] data = new int[RANGE];
        init(data);
        testIntM4(data);
        verify("testIntM4", data, goldIntM4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP4(int[] data) {
        for (int j = 0; j < RANGE - 4; j++) {
            data[j + 4] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP4")
    @Warmup(0)
    public static void runIntP4() {
        int[] data = new int[RANGE];
        init(data);
        testIntP4(data);
        verify("testIntP4", data, goldIntP4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM7(int[] data) {
        for (int j = 7; j < RANGE; j++) {
            data[j + -7] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM7")
    @Warmup(0)
    public static void runIntM7() {
        int[] data = new int[RANGE];
        init(data);
        testIntM7(data);
        verify("testIntM7", data, goldIntM7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 28 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 28"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 28"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 28 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 28"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 28"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 28 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 28"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 28"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP7(int[] data) {
        for (int j = 0; j < RANGE - 7; j++) {
            data[j + 7] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP7")
    @Warmup(0)
    public static void runIntP7() {
        int[] data = new int[RANGE];
        init(data);
        testIntP7(data);
        verify("testIntP7", data, goldIntP7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM8(int[] data) {
        for (int j = 8; j < RANGE; j++) {
            data[j + -8] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM8")
    @Warmup(0)
    public static void runIntM8() {
        int[] data = new int[RANGE];
        init(data);
        testIntM8(data);
        verify("testIntM8", data, goldIntM8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 32 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 32"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 32"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP8(int[] data) {
        for (int j = 0; j < RANGE - 8; j++) {
            data[j + 8] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP8")
    @Warmup(0)
    public static void runIntP8() {
        int[] data = new int[RANGE];
        init(data);
        testIntP8(data);
        verify("testIntP8", data, goldIntP8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM15(int[] data) {
        for (int j = 15; j < RANGE; j++) {
            data[j + -15] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM15")
    @Warmup(0)
    public static void runIntM15() {
        int[] data = new int[RANGE];
        init(data);
        testIntM15(data);
        verify("testIntM15", data, goldIntM15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 60 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 60"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 60"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP15(int[] data) {
        for (int j = 0; j < RANGE - 15; j++) {
            data[j + 15] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP15")
    @Warmup(0)
    public static void runIntP15() {
        int[] data = new int[RANGE];
        init(data);
        testIntP15(data);
        verify("testIntP15", data, goldIntP15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM16(int[] data) {
        for (int j = 16; j < RANGE; j++) {
            data[j + -16] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM16")
    @Warmup(0)
    public static void runIntM16() {
        int[] data = new int[RANGE];
        init(data);
        testIntM16(data);
        verify("testIntM16", data, goldIntM16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP16(int[] data) {
        for (int j = 0; j < RANGE - 16; j++) {
            data[j + 16] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP16")
    @Warmup(0)
    public static void runIntP16() {
        int[] data = new int[RANGE];
        init(data);
        testIntP16(data);
        verify("testIntP16", data, goldIntP16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM31(int[] data) {
        for (int j = 31; j < RANGE; j++) {
            data[j + -31] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM31")
    @Warmup(0)
    public static void runIntM31() {
        int[] data = new int[RANGE];
        init(data);
        testIntM31(data);
        verify("testIntM31", data, goldIntM31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP31(int[] data) {
        for (int j = 0; j < RANGE - 31; j++) {
            data[j + 31] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP31")
    @Warmup(0)
    public static void runIntP31() {
        int[] data = new int[RANGE];
        init(data);
        testIntP31(data);
        verify("testIntP31", data, goldIntP31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM32(int[] data) {
        for (int j = 32; j < RANGE; j++) {
            data[j + -32] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM32")
    @Warmup(0)
    public static void runIntM32() {
        int[] data = new int[RANGE];
        init(data);
        testIntM32(data);
        verify("testIntM32", data, goldIntM32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP32(int[] data) {
        for (int j = 0; j < RANGE - 32; j++) {
            data[j + 32] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP32")
    @Warmup(0)
    public static void runIntP32() {
        int[] data = new int[RANGE];
        init(data);
        testIntP32(data);
        verify("testIntP32", data, goldIntP32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM63(int[] data) {
        for (int j = 63; j < RANGE; j++) {
            data[j + -63] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM63")
    @Warmup(0)
    public static void runIntM63() {
        int[] data = new int[RANGE];
        init(data);
        testIntM63(data);
        verify("testIntM63", data, goldIntM63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP63(int[] data) {
        for (int j = 0; j < RANGE - 63; j++) {
            data[j + 63] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP63")
    @Warmup(0)
    public static void runIntP63() {
        int[] data = new int[RANGE];
        init(data);
        testIntP63(data);
        verify("testIntP63", data, goldIntP63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM64(int[] data) {
        for (int j = 64; j < RANGE; j++) {
            data[j + -64] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM64")
    @Warmup(0)
    public static void runIntM64() {
        int[] data = new int[RANGE];
        init(data);
        testIntM64(data);
        verify("testIntM64", data, goldIntM64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP64(int[] data) {
        for (int j = 0; j < RANGE - 64; j++) {
            data[j + 64] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP64")
    @Warmup(0)
    public static void runIntP64() {
        int[] data = new int[RANGE];
        init(data);
        testIntP64(data);
        verify("testIntP64", data, goldIntP64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM65(int[] data) {
        for (int j = 65; j < RANGE; j++) {
            data[j + -65] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM65")
    @Warmup(0)
    public static void runIntM65() {
        int[] data = new int[RANGE];
        init(data);
        testIntM65(data);
        verify("testIntM65", data, goldIntM65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP65(int[] data) {
        for (int j = 0; j < RANGE - 65; j++) {
            data[j + 65] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP65")
    @Warmup(0)
    public static void runIntP65() {
        int[] data = new int[RANGE];
        init(data);
        testIntP65(data);
        verify("testIntP65", data, goldIntP65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM128(int[] data) {
        for (int j = 128; j < RANGE; j++) {
            data[j + -128] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM128")
    @Warmup(0)
    public static void runIntM128() {
        int[] data = new int[RANGE];
        init(data);
        testIntM128(data);
        verify("testIntM128", data, goldIntM128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP128(int[] data) {
        for (int j = 0; j < RANGE - 128; j++) {
            data[j + 128] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP128")
    @Warmup(0)
    public static void runIntP128() {
        int[] data = new int[RANGE];
        init(data);
        testIntP128(data);
        verify("testIntP128", data, goldIntP128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM129(int[] data) {
        for (int j = 129; j < RANGE; j++) {
            data[j + -129] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM129")
    @Warmup(0)
    public static void runIntM129() {
        int[] data = new int[RANGE];
        init(data);
        testIntM129(data);
        verify("testIntM129", data, goldIntM129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP129(int[] data) {
        for (int j = 0; j < RANGE - 129; j++) {
            data[j + 129] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP129")
    @Warmup(0)
    public static void runIntP129() {
        int[] data = new int[RANGE];
        init(data);
        testIntP129(data);
        verify("testIntP129", data, goldIntP129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntM192(int[] data) {
        for (int j = 192; j < RANGE; j++) {
            data[j + -192] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntM192")
    @Warmup(0)
    public static void runIntM192() {
        int[] data = new int[RANGE];
        init(data);
        testIntM192(data);
        verify("testIntM192", data, goldIntM192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testIntP192(int[] data) {
        for (int j = 0; j < RANGE - 192; j++) {
            data[j + 192] = (int)(data[j] * (int)-11);
        }
    }

    @Run(test = "testIntP192")
    @Warmup(0)
    public static void runIntP192() {
        int[] data = new int[RANGE];
        init(data);
        testIntP192(data);
        verify("testIntP192", data, goldIntP192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP0(long[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j + 0] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP0")
    @Warmup(0)
    public static void runLongP0() {
        long[] data = new long[RANGE];
        init(data);
        testLongP0(data);
        verify("testLongP0", data, goldLongP0);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM1(long[] data) {
        for (int j = 1; j < RANGE; j++) {
            data[j + -1] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM1")
    @Warmup(0)
    public static void runLongM1() {
        long[] data = new long[RANGE];
        init(data);
        testLongM1(data);
        verify("testLongM1", data, goldLongM1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP1(long[] data) {
        for (int j = 0; j < RANGE - 1; j++) {
            data[j + 1] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP1")
    @Warmup(0)
    public static void runLongP1() {
        long[] data = new long[RANGE];
        init(data);
        testLongP1(data);
        verify("testLongP1", data, goldLongP1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM2(long[] data) {
        for (int j = 2; j < RANGE; j++) {
            data[j + -2] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM2")
    @Warmup(0)
    public static void runLongM2() {
        long[] data = new long[RANGE];
        init(data);
        testLongM2(data);
        verify("testLongM2", data, goldLongM2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP2(long[] data) {
        for (int j = 0; j < RANGE - 2; j++) {
            data[j + 2] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP2")
    @Warmup(0)
    public static void runLongP2() {
        long[] data = new long[RANGE];
        init(data);
        testLongP2(data);
        verify("testLongP2", data, goldLongP2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM3(long[] data) {
        for (int j = 3; j < RANGE; j++) {
            data[j + -3] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM3")
    @Warmup(0)
    public static void runLongM3() {
        long[] data = new long[RANGE];
        init(data);
        testLongM3(data);
        verify("testLongM3", data, goldLongM3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 24 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 24"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 24"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 24 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 24"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 24"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 24 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 24"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 24"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP3(long[] data) {
        for (int j = 0; j < RANGE - 3; j++) {
            data[j + 3] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP3")
    @Warmup(0)
    public static void runLongP3() {
        long[] data = new long[RANGE];
        init(data);
        testLongP3(data);
        verify("testLongP3", data, goldLongP3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM4(long[] data) {
        for (int j = 4; j < RANGE; j++) {
            data[j + -4] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM4")
    @Warmup(0)
    public static void runLongM4() {
        long[] data = new long[RANGE];
        init(data);
        testLongM4(data);
        verify("testLongM4", data, goldLongM4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 32 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 32"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 32"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP4(long[] data) {
        for (int j = 0; j < RANGE - 4; j++) {
            data[j + 4] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP4")
    @Warmup(0)
    public static void runLongP4() {
        long[] data = new long[RANGE];
        init(data);
        testLongP4(data);
        verify("testLongP4", data, goldLongP4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM7(long[] data) {
        for (int j = 7; j < RANGE; j++) {
            data[j + -7] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM7")
    @Warmup(0)
    public static void runLongM7() {
        long[] data = new long[RANGE];
        init(data);
        testLongM7(data);
        verify("testLongM7", data, goldLongM7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 56 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 56"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 56"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP7(long[] data) {
        for (int j = 0; j < RANGE - 7; j++) {
            data[j + 7] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP7")
    @Warmup(0)
    public static void runLongP7() {
        long[] data = new long[RANGE];
        init(data);
        testLongP7(data);
        verify("testLongP7", data, goldLongP7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM8(long[] data) {
        for (int j = 8; j < RANGE; j++) {
            data[j + -8] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM8")
    @Warmup(0)
    public static void runLongM8() {
        long[] data = new long[RANGE];
        init(data);
        testLongM8(data);
        verify("testLongM8", data, goldLongM8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP8(long[] data) {
        for (int j = 0; j < RANGE - 8; j++) {
            data[j + 8] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP8")
    @Warmup(0)
    public static void runLongP8() {
        long[] data = new long[RANGE];
        init(data);
        testLongP8(data);
        verify("testLongP8", data, goldLongP8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM15(long[] data) {
        for (int j = 15; j < RANGE; j++) {
            data[j + -15] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM15")
    @Warmup(0)
    public static void runLongM15() {
        long[] data = new long[RANGE];
        init(data);
        testLongM15(data);
        verify("testLongM15", data, goldLongM15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP15(long[] data) {
        for (int j = 0; j < RANGE - 15; j++) {
            data[j + 15] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP15")
    @Warmup(0)
    public static void runLongP15() {
        long[] data = new long[RANGE];
        init(data);
        testLongP15(data);
        verify("testLongP15", data, goldLongP15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM16(long[] data) {
        for (int j = 16; j < RANGE; j++) {
            data[j + -16] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM16")
    @Warmup(0)
    public static void runLongM16() {
        long[] data = new long[RANGE];
        init(data);
        testLongM16(data);
        verify("testLongM16", data, goldLongM16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP16(long[] data) {
        for (int j = 0; j < RANGE - 16; j++) {
            data[j + 16] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP16")
    @Warmup(0)
    public static void runLongP16() {
        long[] data = new long[RANGE];
        init(data);
        testLongP16(data);
        verify("testLongP16", data, goldLongP16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM31(long[] data) {
        for (int j = 31; j < RANGE; j++) {
            data[j + -31] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM31")
    @Warmup(0)
    public static void runLongM31() {
        long[] data = new long[RANGE];
        init(data);
        testLongM31(data);
        verify("testLongM31", data, goldLongM31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP31(long[] data) {
        for (int j = 0; j < RANGE - 31; j++) {
            data[j + 31] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP31")
    @Warmup(0)
    public static void runLongP31() {
        long[] data = new long[RANGE];
        init(data);
        testLongP31(data);
        verify("testLongP31", data, goldLongP31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM32(long[] data) {
        for (int j = 32; j < RANGE; j++) {
            data[j + -32] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM32")
    @Warmup(0)
    public static void runLongM32() {
        long[] data = new long[RANGE];
        init(data);
        testLongM32(data);
        verify("testLongM32", data, goldLongM32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP32(long[] data) {
        for (int j = 0; j < RANGE - 32; j++) {
            data[j + 32] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP32")
    @Warmup(0)
    public static void runLongP32() {
        long[] data = new long[RANGE];
        init(data);
        testLongP32(data);
        verify("testLongP32", data, goldLongP32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM63(long[] data) {
        for (int j = 63; j < RANGE; j++) {
            data[j + -63] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM63")
    @Warmup(0)
    public static void runLongM63() {
        long[] data = new long[RANGE];
        init(data);
        testLongM63(data);
        verify("testLongM63", data, goldLongM63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP63(long[] data) {
        for (int j = 0; j < RANGE - 63; j++) {
            data[j + 63] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP63")
    @Warmup(0)
    public static void runLongP63() {
        long[] data = new long[RANGE];
        init(data);
        testLongP63(data);
        verify("testLongP63", data, goldLongP63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM64(long[] data) {
        for (int j = 64; j < RANGE; j++) {
            data[j + -64] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM64")
    @Warmup(0)
    public static void runLongM64() {
        long[] data = new long[RANGE];
        init(data);
        testLongM64(data);
        verify("testLongM64", data, goldLongM64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP64(long[] data) {
        for (int j = 0; j < RANGE - 64; j++) {
            data[j + 64] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP64")
    @Warmup(0)
    public static void runLongP64() {
        long[] data = new long[RANGE];
        init(data);
        testLongP64(data);
        verify("testLongP64", data, goldLongP64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM65(long[] data) {
        for (int j = 65; j < RANGE; j++) {
            data[j + -65] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM65")
    @Warmup(0)
    public static void runLongM65() {
        long[] data = new long[RANGE];
        init(data);
        testLongM65(data);
        verify("testLongM65", data, goldLongM65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP65(long[] data) {
        for (int j = 0; j < RANGE - 65; j++) {
            data[j + 65] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP65")
    @Warmup(0)
    public static void runLongP65() {
        long[] data = new long[RANGE];
        init(data);
        testLongP65(data);
        verify("testLongP65", data, goldLongP65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM128(long[] data) {
        for (int j = 128; j < RANGE; j++) {
            data[j + -128] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM128")
    @Warmup(0)
    public static void runLongM128() {
        long[] data = new long[RANGE];
        init(data);
        testLongM128(data);
        verify("testLongM128", data, goldLongM128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP128(long[] data) {
        for (int j = 0; j < RANGE - 128; j++) {
            data[j + 128] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP128")
    @Warmup(0)
    public static void runLongP128() {
        long[] data = new long[RANGE];
        init(data);
        testLongP128(data);
        verify("testLongP128", data, goldLongP128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM129(long[] data) {
        for (int j = 129; j < RANGE; j++) {
            data[j + -129] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM129")
    @Warmup(0)
    public static void runLongM129() {
        long[] data = new long[RANGE];
        init(data);
        testLongM129(data);
        verify("testLongM129", data, goldLongM129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP129(long[] data) {
        for (int j = 0; j < RANGE - 129; j++) {
            data[j + 129] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP129")
    @Warmup(0)
    public static void runLongP129() {
        long[] data = new long[RANGE];
        init(data);
        testLongP129(data);
        verify("testLongP129", data, goldLongP129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongM192(long[] data) {
        for (int j = 192; j < RANGE; j++) {
            data[j + -192] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongM192")
    @Warmup(0)
    public static void runLongM192() {
        long[] data = new long[RANGE];
        init(data);
        testLongM192(data);
        verify("testLongM192", data, goldLongM192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testLongP192(long[] data) {
        for (int j = 0; j < RANGE - 192; j++) {
            data[j + 192] = (long)(data[j] * (long)-11);
        }
    }

    @Run(test = "testLongP192")
    @Warmup(0)
    public static void runLongP192() {
        long[] data = new long[RANGE];
        init(data);
        testLongP192(data);
        verify("testLongP192", data, goldLongP192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP0(short[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j + 0] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP0")
    @Warmup(0)
    public static void runShortP0() {
        short[] data = new short[RANGE];
        init(data);
        testShortP0(data);
        verify("testShortP0", data, goldShortP0);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM1(short[] data) {
        for (int j = 1; j < RANGE; j++) {
            data[j + -1] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM1")
    @Warmup(0)
    public static void runShortM1() {
        short[] data = new short[RANGE];
        init(data);
        testShortM1(data);
        verify("testShortM1", data, goldShortM1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP1(short[] data) {
        for (int j = 0; j < RANGE - 1; j++) {
            data[j + 1] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP1")
    @Warmup(0)
    public static void runShortP1() {
        short[] data = new short[RANGE];
        init(data);
        testShortP1(data);
        verify("testShortP1", data, goldShortP1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM2(short[] data) {
        for (int j = 2; j < RANGE; j++) {
            data[j + -2] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM2")
    @Warmup(0)
    public static void runShortM2() {
        short[] data = new short[RANGE];
        init(data);
        testShortM2(data);
        verify("testShortM2", data, goldShortM2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP2(short[] data) {
        for (int j = 0; j < RANGE - 2; j++) {
            data[j + 2] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP2")
    @Warmup(0)
    public static void runShortP2() {
        short[] data = new short[RANGE];
        init(data);
        testShortP2(data);
        verify("testShortP2", data, goldShortP2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM3(short[] data) {
        for (int j = 3; j < RANGE; j++) {
            data[j + -3] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM3")
    @Warmup(0)
    public static void runShortM3() {
        short[] data = new short[RANGE];
        init(data);
        testShortM3(data);
        verify("testShortM3", data, goldShortM3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 6 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 6"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 6"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 6 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 6"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 6"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 6 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 6"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 6"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 6 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 6"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 6"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP3(short[] data) {
        for (int j = 0; j < RANGE - 3; j++) {
            data[j + 3] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP3")
    @Warmup(0)
    public static void runShortP3() {
        short[] data = new short[RANGE];
        init(data);
        testShortP3(data);
        verify("testShortP3", data, goldShortP3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM4(short[] data) {
        for (int j = 4; j < RANGE; j++) {
            data[j + -4] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM4")
    @Warmup(0)
    public static void runShortM4() {
        short[] data = new short[RANGE];
        init(data);
        testShortM4(data);
        verify("testShortM4", data, goldShortM4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP4(short[] data) {
        for (int j = 0; j < RANGE - 4; j++) {
            data[j + 4] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP4")
    @Warmup(0)
    public static void runShortP4() {
        short[] data = new short[RANGE];
        init(data);
        testShortP4(data);
        verify("testShortP4", data, goldShortP4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM7(short[] data) {
        for (int j = 7; j < RANGE; j++) {
            data[j + -7] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM7")
    @Warmup(0)
    public static void runShortM7() {
        short[] data = new short[RANGE];
        init(data);
        testShortM7(data);
        verify("testShortM7", data, goldShortM7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 14 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 14"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 14"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 14 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 14"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 14"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 14 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 14"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 14"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 14 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 14"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 14"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP7(short[] data) {
        for (int j = 0; j < RANGE - 7; j++) {
            data[j + 7] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP7")
    @Warmup(0)
    public static void runShortP7() {
        short[] data = new short[RANGE];
        init(data);
        testShortP7(data);
        verify("testShortP7", data, goldShortP7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM8(short[] data) {
        for (int j = 8; j < RANGE; j++) {
            data[j + -8] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM8")
    @Warmup(0)
    public static void runShortM8() {
        short[] data = new short[RANGE];
        init(data);
        testShortM8(data);
        verify("testShortM8", data, goldShortM8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP8(short[] data) {
        for (int j = 0; j < RANGE - 8; j++) {
            data[j + 8] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP8")
    @Warmup(0)
    public static void runShortP8() {
        short[] data = new short[RANGE];
        init(data);
        testShortP8(data);
        verify("testShortP8", data, goldShortP8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM15(short[] data) {
        for (int j = 15; j < RANGE; j++) {
            data[j + -15] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM15")
    @Warmup(0)
    public static void runShortM15() {
        short[] data = new short[RANGE];
        init(data);
        testShortM15(data);
        verify("testShortM15", data, goldShortM15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 30 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 30"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 30"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 30 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 30"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 30"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 30 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 30"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 30"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP15(short[] data) {
        for (int j = 0; j < RANGE - 15; j++) {
            data[j + 15] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP15")
    @Warmup(0)
    public static void runShortP15() {
        short[] data = new short[RANGE];
        init(data);
        testShortP15(data);
        verify("testShortP15", data, goldShortP15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM16(short[] data) {
        for (int j = 16; j < RANGE; j++) {
            data[j + -16] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM16")
    @Warmup(0)
    public static void runShortM16() {
        short[] data = new short[RANGE];
        init(data);
        testShortM16(data);
        verify("testShortM16", data, goldShortM16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 32 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 32"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 32"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP16(short[] data) {
        for (int j = 0; j < RANGE - 16; j++) {
            data[j + 16] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP16")
    @Warmup(0)
    public static void runShortP16() {
        short[] data = new short[RANGE];
        init(data);
        testShortP16(data);
        verify("testShortP16", data, goldShortP16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM31(short[] data) {
        for (int j = 31; j < RANGE; j++) {
            data[j + -31] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM31")
    @Warmup(0)
    public static void runShortM31() {
        short[] data = new short[RANGE];
        init(data);
        testShortM31(data);
        verify("testShortM31", data, goldShortM31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 62 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 62"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 62"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP31(short[] data) {
        for (int j = 0; j < RANGE - 31; j++) {
            data[j + 31] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP31")
    @Warmup(0)
    public static void runShortP31() {
        short[] data = new short[RANGE];
        init(data);
        testShortP31(data);
        verify("testShortP31", data, goldShortP31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM32(short[] data) {
        for (int j = 32; j < RANGE; j++) {
            data[j + -32] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM32")
    @Warmup(0)
    public static void runShortM32() {
        short[] data = new short[RANGE];
        init(data);
        testShortM32(data);
        verify("testShortM32", data, goldShortM32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP32(short[] data) {
        for (int j = 0; j < RANGE - 32; j++) {
            data[j + 32] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP32")
    @Warmup(0)
    public static void runShortP32() {
        short[] data = new short[RANGE];
        init(data);
        testShortP32(data);
        verify("testShortP32", data, goldShortP32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM63(short[] data) {
        for (int j = 63; j < RANGE; j++) {
            data[j + -63] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM63")
    @Warmup(0)
    public static void runShortM63() {
        short[] data = new short[RANGE];
        init(data);
        testShortM63(data);
        verify("testShortM63", data, goldShortM63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP63(short[] data) {
        for (int j = 0; j < RANGE - 63; j++) {
            data[j + 63] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP63")
    @Warmup(0)
    public static void runShortP63() {
        short[] data = new short[RANGE];
        init(data);
        testShortP63(data);
        verify("testShortP63", data, goldShortP63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM64(short[] data) {
        for (int j = 64; j < RANGE; j++) {
            data[j + -64] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM64")
    @Warmup(0)
    public static void runShortM64() {
        short[] data = new short[RANGE];
        init(data);
        testShortM64(data);
        verify("testShortM64", data, goldShortM64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP64(short[] data) {
        for (int j = 0; j < RANGE - 64; j++) {
            data[j + 64] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP64")
    @Warmup(0)
    public static void runShortP64() {
        short[] data = new short[RANGE];
        init(data);
        testShortP64(data);
        verify("testShortP64", data, goldShortP64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM65(short[] data) {
        for (int j = 65; j < RANGE; j++) {
            data[j + -65] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM65")
    @Warmup(0)
    public static void runShortM65() {
        short[] data = new short[RANGE];
        init(data);
        testShortM65(data);
        verify("testShortM65", data, goldShortM65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP65(short[] data) {
        for (int j = 0; j < RANGE - 65; j++) {
            data[j + 65] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP65")
    @Warmup(0)
    public static void runShortP65() {
        short[] data = new short[RANGE];
        init(data);
        testShortP65(data);
        verify("testShortP65", data, goldShortP65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM128(short[] data) {
        for (int j = 128; j < RANGE; j++) {
            data[j + -128] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM128")
    @Warmup(0)
    public static void runShortM128() {
        short[] data = new short[RANGE];
        init(data);
        testShortM128(data);
        verify("testShortM128", data, goldShortM128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP128(short[] data) {
        for (int j = 0; j < RANGE - 128; j++) {
            data[j + 128] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP128")
    @Warmup(0)
    public static void runShortP128() {
        short[] data = new short[RANGE];
        init(data);
        testShortP128(data);
        verify("testShortP128", data, goldShortP128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM129(short[] data) {
        for (int j = 129; j < RANGE; j++) {
            data[j + -129] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM129")
    @Warmup(0)
    public static void runShortM129() {
        short[] data = new short[RANGE];
        init(data);
        testShortM129(data);
        verify("testShortM129", data, goldShortM129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP129(short[] data) {
        for (int j = 0; j < RANGE - 129; j++) {
            data[j + 129] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP129")
    @Warmup(0)
    public static void runShortP129() {
        short[] data = new short[RANGE];
        init(data);
        testShortP129(data);
        verify("testShortP129", data, goldShortP129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortM192(short[] data) {
        for (int j = 192; j < RANGE; j++) {
            data[j + -192] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortM192")
    @Warmup(0)
    public static void runShortM192() {
        short[] data = new short[RANGE];
        init(data);
        testShortM192(data);
        verify("testShortM192", data, goldShortM192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testShortP192(short[] data) {
        for (int j = 0; j < RANGE - 192; j++) {
            data[j + 192] = (short)(data[j] * (short)-11);
        }
    }

    @Run(test = "testShortP192")
    @Warmup(0)
    public static void runShortP192() {
        short[] data = new short[RANGE];
        init(data);
        testShortP192(data);
        verify("testShortP192", data, goldShortP192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP0(char[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j + 0] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP0")
    @Warmup(0)
    public static void runCharP0() {
        char[] data = new char[RANGE];
        init(data);
        testCharP0(data);
        verify("testCharP0", data, goldCharP0);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM1(char[] data) {
        for (int j = 1; j < RANGE; j++) {
            data[j + -1] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM1")
    @Warmup(0)
    public static void runCharM1() {
        char[] data = new char[RANGE];
        init(data);
        testCharM1(data);
        verify("testCharM1", data, goldCharM1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP1(char[] data) {
        for (int j = 0; j < RANGE - 1; j++) {
            data[j + 1] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP1")
    @Warmup(0)
    public static void runCharP1() {
        char[] data = new char[RANGE];
        init(data);
        testCharP1(data);
        verify("testCharP1", data, goldCharP1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM2(char[] data) {
        for (int j = 2; j < RANGE; j++) {
            data[j + -2] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM2")
    @Warmup(0)
    public static void runCharM2() {
        char[] data = new char[RANGE];
        init(data);
        testCharM2(data);
        verify("testCharM2", data, goldCharM2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP2(char[] data) {
        for (int j = 0; j < RANGE - 2; j++) {
            data[j + 2] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP2")
    @Warmup(0)
    public static void runCharP2() {
        char[] data = new char[RANGE];
        init(data);
        testCharP2(data);
        verify("testCharP2", data, goldCharP2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM3(char[] data) {
        for (int j = 3; j < RANGE; j++) {
            data[j + -3] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM3")
    @Warmup(0)
    public static void runCharM3() {
        char[] data = new char[RANGE];
        init(data);
        testCharM3(data);
        verify("testCharM3", data, goldCharM3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 6 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 6"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 6"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 6 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 6"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 6"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 6 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 6"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 6"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 6 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 6"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 6"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP3(char[] data) {
        for (int j = 0; j < RANGE - 3; j++) {
            data[j + 3] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP3")
    @Warmup(0)
    public static void runCharP3() {
        char[] data = new char[RANGE];
        init(data);
        testCharP3(data);
        verify("testCharP3", data, goldCharP3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM4(char[] data) {
        for (int j = 4; j < RANGE; j++) {
            data[j + -4] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM4")
    @Warmup(0)
    public static void runCharM4() {
        char[] data = new char[RANGE];
        init(data);
        testCharM4(data);
        verify("testCharM4", data, goldCharM4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP4(char[] data) {
        for (int j = 0; j < RANGE - 4; j++) {
            data[j + 4] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP4")
    @Warmup(0)
    public static void runCharP4() {
        char[] data = new char[RANGE];
        init(data);
        testCharP4(data);
        verify("testCharP4", data, goldCharP4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM7(char[] data) {
        for (int j = 7; j < RANGE; j++) {
            data[j + -7] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM7")
    @Warmup(0)
    public static void runCharM7() {
        char[] data = new char[RANGE];
        init(data);
        testCharM7(data);
        verify("testCharM7", data, goldCharM7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    // positive byte_offset 14 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 14"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 14"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 14 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 14"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 14"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 14 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 14"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 14"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 14 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 14"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 14"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP7(char[] data) {
        for (int j = 0; j < RANGE - 7; j++) {
            data[j + 7] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP7")
    @Warmup(0)
    public static void runCharP7() {
        char[] data = new char[RANGE];
        init(data);
        testCharP7(data);
        verify("testCharP7", data, goldCharP7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM8(char[] data) {
        for (int j = 8; j < RANGE; j++) {
            data[j + -8] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM8")
    @Warmup(0)
    public static void runCharM8() {
        char[] data = new char[RANGE];
        init(data);
        testCharM8(data);
        verify("testCharM8", data, goldCharM8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP8(char[] data) {
        for (int j = 0; j < RANGE - 8; j++) {
            data[j + 8] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP8")
    @Warmup(0)
    public static void runCharP8() {
        char[] data = new char[RANGE];
        init(data);
        testCharP8(data);
        verify("testCharP8", data, goldCharP8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM15(char[] data) {
        for (int j = 15; j < RANGE; j++) {
            data[j + -15] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM15")
    @Warmup(0)
    public static void runCharM15() {
        char[] data = new char[RANGE];
        init(data);
        testCharM15(data);
        verify("testCharM15", data, goldCharM15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 30 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 30"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 30"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 30 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 30"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 30"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    // positive byte_offset 30 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 30"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 30"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP15(char[] data) {
        for (int j = 0; j < RANGE - 15; j++) {
            data[j + 15] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP15")
    @Warmup(0)
    public static void runCharP15() {
        char[] data = new char[RANGE];
        init(data);
        testCharP15(data);
        verify("testCharP15", data, goldCharP15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM16(char[] data) {
        for (int j = 16; j < RANGE; j++) {
            data[j + -16] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM16")
    @Warmup(0)
    public static void runCharM16() {
        char[] data = new char[RANGE];
        init(data);
        testCharM16(data);
        verify("testCharM16", data, goldCharM16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 32 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 32"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 32"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP16(char[] data) {
        for (int j = 0; j < RANGE - 16; j++) {
            data[j + 16] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP16")
    @Warmup(0)
    public static void runCharP16() {
        char[] data = new char[RANGE];
        init(data);
        testCharP16(data);
        verify("testCharP16", data, goldCharP16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM31(char[] data) {
        for (int j = 31; j < RANGE; j++) {
            data[j + -31] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM31")
    @Warmup(0)
    public static void runCharM31() {
        char[] data = new char[RANGE];
        init(data);
        testCharM31(data);
        verify("testCharM31", data, goldCharM31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    // positive byte_offset 62 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 62"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 62"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP31(char[] data) {
        for (int j = 0; j < RANGE - 31; j++) {
            data[j + 31] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP31")
    @Warmup(0)
    public static void runCharP31() {
        char[] data = new char[RANGE];
        init(data);
        testCharP31(data);
        verify("testCharP31", data, goldCharP31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM32(char[] data) {
        for (int j = 32; j < RANGE; j++) {
            data[j + -32] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM32")
    @Warmup(0)
    public static void runCharM32() {
        char[] data = new char[RANGE];
        init(data);
        testCharM32(data);
        verify("testCharM32", data, goldCharM32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP32(char[] data) {
        for (int j = 0; j < RANGE - 32; j++) {
            data[j + 32] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP32")
    @Warmup(0)
    public static void runCharP32() {
        char[] data = new char[RANGE];
        init(data);
        testCharP32(data);
        verify("testCharP32", data, goldCharP32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM63(char[] data) {
        for (int j = 63; j < RANGE; j++) {
            data[j + -63] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM63")
    @Warmup(0)
    public static void runCharM63() {
        char[] data = new char[RANGE];
        init(data);
        testCharM63(data);
        verify("testCharM63", data, goldCharM63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP63(char[] data) {
        for (int j = 0; j < RANGE - 63; j++) {
            data[j + 63] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP63")
    @Warmup(0)
    public static void runCharP63() {
        char[] data = new char[RANGE];
        init(data);
        testCharP63(data);
        verify("testCharP63", data, goldCharP63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM64(char[] data) {
        for (int j = 64; j < RANGE; j++) {
            data[j + -64] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM64")
    @Warmup(0)
    public static void runCharM64() {
        char[] data = new char[RANGE];
        init(data);
        testCharM64(data);
        verify("testCharM64", data, goldCharM64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP64(char[] data) {
        for (int j = 0; j < RANGE - 64; j++) {
            data[j + 64] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP64")
    @Warmup(0)
    public static void runCharP64() {
        char[] data = new char[RANGE];
        init(data);
        testCharP64(data);
        verify("testCharP64", data, goldCharP64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM65(char[] data) {
        for (int j = 65; j < RANGE; j++) {
            data[j + -65] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM65")
    @Warmup(0)
    public static void runCharM65() {
        char[] data = new char[RANGE];
        init(data);
        testCharM65(data);
        verify("testCharM65", data, goldCharM65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP65(char[] data) {
        for (int j = 0; j < RANGE - 65; j++) {
            data[j + 65] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP65")
    @Warmup(0)
    public static void runCharP65() {
        char[] data = new char[RANGE];
        init(data);
        testCharP65(data);
        verify("testCharP65", data, goldCharP65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM128(char[] data) {
        for (int j = 128; j < RANGE; j++) {
            data[j + -128] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM128")
    @Warmup(0)
    public static void runCharM128() {
        char[] data = new char[RANGE];
        init(data);
        testCharM128(data);
        verify("testCharM128", data, goldCharM128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP128(char[] data) {
        for (int j = 0; j < RANGE - 128; j++) {
            data[j + 128] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP128")
    @Warmup(0)
    public static void runCharP128() {
        char[] data = new char[RANGE];
        init(data);
        testCharP128(data);
        verify("testCharP128", data, goldCharP128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM129(char[] data) {
        for (int j = 129; j < RANGE; j++) {
            data[j + -129] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM129")
    @Warmup(0)
    public static void runCharM129() {
        char[] data = new char[RANGE];
        init(data);
        testCharM129(data);
        verify("testCharM129", data, goldCharM129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP129(char[] data) {
        for (int j = 0; j < RANGE - 129; j++) {
            data[j + 129] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP129")
    @Warmup(0)
    public static void runCharP129() {
        char[] data = new char[RANGE];
        init(data);
        testCharP129(data);
        verify("testCharP129", data, goldCharP129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharM192(char[] data) {
        for (int j = 192; j < RANGE; j++) {
            data[j + -192] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharM192")
    @Warmup(0)
    public static void runCharM192() {
        char[] data = new char[RANGE];
        init(data);
        testCharM192(data);
        verify("testCharM192", data, goldCharM192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testCharP192(char[] data) {
        for (int j = 0; j < RANGE - 192; j++) {
            data[j + 192] = (char)(data[j] * (char)-11);
        }
    }

    @Run(test = "testCharP192")
    @Warmup(0)
    public static void runCharP192() {
        char[] data = new char[RANGE];
        init(data);
        testCharP192(data);
        verify("testCharP192", data, goldCharP192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP0(byte[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j + 0] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP0")
    @Warmup(0)
    public static void runByteP0() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP0(data);
        verify("testByteP0", data, goldByteP0);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM1(byte[] data) {
        for (int j = 1; j < RANGE; j++) {
            data[j + -1] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM1")
    @Warmup(0)
    public static void runByteM1() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM1(data);
        verify("testByteM1", data, goldByteM1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    // positive byte_offset 1 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 1"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 1"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 1 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 1"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 1"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 1 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 1"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 1"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 1 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 1"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 1"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP1(byte[] data) {
        for (int j = 0; j < RANGE - 1; j++) {
            data[j + 1] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP1")
    @Warmup(0)
    public static void runByteP1() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP1(data);
        verify("testByteP1", data, goldByteP1);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM2(byte[] data) {
        for (int j = 2; j < RANGE; j++) {
            data[j + -2] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM2")
    @Warmup(0)
    public static void runByteM2() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM2(data);
        verify("testByteM2", data, goldByteM2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 2 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 2"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 2"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP2(byte[] data) {
        for (int j = 0; j < RANGE - 2; j++) {
            data[j + 2] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP2")
    @Warmup(0)
    public static void runByteP2() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP2(data);
        verify("testByteP2", data, goldByteP2);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM3(byte[] data) {
        for (int j = 3; j < RANGE; j++) {
            data[j + -3] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM3")
    @Warmup(0)
    public static void runByteM3() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM3(data);
        verify("testByteM3", data, goldByteM3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    // positive byte_offset 3 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 3"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 3"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 3 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 3"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 3"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 3 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 3"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 3"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 3 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 3"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 3"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP3(byte[] data) {
        for (int j = 0; j < RANGE - 3; j++) {
            data[j + 3] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP3")
    @Warmup(0)
    public static void runByteP3() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP3(data);
        verify("testByteP3", data, goldByteP3);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM4(byte[] data) {
        for (int j = 4; j < RANGE; j++) {
            data[j + -4] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM4")
    @Warmup(0)
    public static void runByteM4() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM4(data);
        verify("testByteM4", data, goldByteM4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP4(byte[] data) {
        for (int j = 0; j < RANGE - 4; j++) {
            data[j + 4] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP4")
    @Warmup(0)
    public static void runByteP4() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP4(data);
        verify("testByteP4", data, goldByteP4);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM7(byte[] data) {
        for (int j = 7; j < RANGE; j++) {
            data[j + -7] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM7")
    @Warmup(0)
    public static void runByteM7() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM7(data);
        verify("testByteM7", data, goldByteM7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    // positive byte_offset 7 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 7"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 7"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 7 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 7"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 7"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 7 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 7"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 7"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 7 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 7"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 7"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP7(byte[] data) {
        for (int j = 0; j < RANGE - 7; j++) {
            data[j + 7] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP7")
    @Warmup(0)
    public static void runByteP7() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP7(data);
        verify("testByteP7", data, goldByteP7);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM8(byte[] data) {
        for (int j = 8; j < RANGE; j++) {
            data[j + -8] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM8")
    @Warmup(0)
    public static void runByteM8() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM8(data);
        verify("testByteM8", data, goldByteM8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP8(byte[] data) {
        for (int j = 0; j < RANGE - 8; j++) {
            data[j + 8] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP8")
    @Warmup(0)
    public static void runByteP8() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP8(data);
        verify("testByteP8", data, goldByteP8);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM15(byte[] data) {
        for (int j = 15; j < RANGE; j++) {
            data[j + -15] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM15")
    @Warmup(0)
    public static void runByteM15() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM15(data);
        verify("testByteM15", data, goldByteM15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    // positive byte_offset 15 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 15"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 15"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 15 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 15"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 15"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 15 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 15"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 15"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 15 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 15"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 15"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP15(byte[] data) {
        for (int j = 0; j < RANGE - 15; j++) {
            data[j + 15] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP15")
    @Warmup(0)
    public static void runByteP15() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP15(data);
        verify("testByteP15", data, goldByteP15);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM16(byte[] data) {
        for (int j = 16; j < RANGE; j++) {
            data[j + -16] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM16")
    @Warmup(0)
    public static void runByteM16() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM16(data);
        verify("testByteM16", data, goldByteM16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP16(byte[] data) {
        for (int j = 0; j < RANGE - 16; j++) {
            data[j + 16] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP16")
    @Warmup(0)
    public static void runByteP16() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP16(data);
        verify("testByteP16", data, goldByteP16);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM31(byte[] data) {
        for (int j = 31; j < RANGE; j++) {
            data[j + -31] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM31")
    @Warmup(0)
    public static void runByteM31() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM31(data);
        verify("testByteM31", data, goldByteM31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 31 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 31"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 31"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 31 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 31"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 31"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    // positive byte_offset 31 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 31"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 31"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP31(byte[] data) {
        for (int j = 0; j < RANGE - 31; j++) {
            data[j + 31] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP31")
    @Warmup(0)
    public static void runByteP31() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP31(data);
        verify("testByteP31", data, goldByteP31);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM32(byte[] data) {
        for (int j = 32; j < RANGE; j++) {
            data[j + -32] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM32")
    @Warmup(0)
    public static void runByteM32() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM32(data);
        verify("testByteM32", data, goldByteM32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 32 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 32"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 32"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP32(byte[] data) {
        for (int j = 0; j < RANGE - 32; j++) {
            data[j + 32] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP32")
    @Warmup(0)
    public static void runByteP32() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP32(data);
        verify("testByteP32", data, goldByteP32);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM63(byte[] data) {
        for (int j = 63; j < RANGE; j++) {
            data[j + -63] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM63")
    @Warmup(0)
    public static void runByteM63() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM63(data);
        verify("testByteM63", data, goldByteM63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    // positive byte_offset 63 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 4", "SuperWordMaxVectorSize", "<= 63"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 4", "SuperWordMaxVectorSize", "> 63"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP63(byte[] data) {
        for (int j = 0; j < RANGE - 63; j++) {
            data[j + 63] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP63")
    @Warmup(0)
    public static void runByteP63() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP63(data);
        verify("testByteP63", data, goldByteP63);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM64(byte[] data) {
        for (int j = 64; j < RANGE; j++) {
            data[j + -64] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM64")
    @Warmup(0)
    public static void runByteM64() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM64(data);
        verify("testByteM64", data, goldByteM64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP64(byte[] data) {
        for (int j = 0; j < RANGE - 64; j++) {
            data[j + 64] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP64")
    @Warmup(0)
    public static void runByteP64() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP64(data);
        verify("testByteP64", data, goldByteP64);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM65(byte[] data) {
        for (int j = 65; j < RANGE; j++) {
            data[j + -65] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM65")
    @Warmup(0)
    public static void runByteM65() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM65(data);
        verify("testByteM65", data, goldByteM65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP65(byte[] data) {
        for (int j = 0; j < RANGE - 65; j++) {
            data[j + 65] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP65")
    @Warmup(0)
    public static void runByteP65() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP65(data);
        verify("testByteP65", data, goldByteP65);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM128(byte[] data) {
        for (int j = 128; j < RANGE; j++) {
            data[j + -128] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM128")
    @Warmup(0)
    public static void runByteM128() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM128(data);
        verify("testByteM128", data, goldByteM128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP128(byte[] data) {
        for (int j = 0; j < RANGE - 128; j++) {
            data[j + 128] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP128")
    @Warmup(0)
    public static void runByteP128() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP128(data);
        verify("testByteP128", data, goldByteP128);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM129(byte[] data) {
        for (int j = 129; j < RANGE; j++) {
            data[j + -129] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM129")
    @Warmup(0)
    public static void runByteM129() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM129(data);
        verify("testByteM129", data, goldByteM129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP129(byte[] data) {
        for (int j = 0; j < RANGE - 129; j++) {
            data[j + 129] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP129")
    @Warmup(0)
    public static void runByteP129() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP129(data);
        verify("testByteP129", data, goldByteP129);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteM192(byte[] data) {
        for (int j = 192; j < RANGE; j++) {
            data[j + -192] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteM192")
    @Warmup(0)
    public static void runByteM192() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteM192(data);
        verify("testByteM192", data, goldByteM192);
    }

    @Test
    // cpu: sse4.1 to avx -> vector_width: 16 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx2", "false"})
    // cpu: avx2 to avx512 without avx512bw -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeatureAnd = {"avx2", "true", "avx512bw", "false"})
    // cpu: avx512bw -> vector_width: 64 -> elements in vector: 64
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"avx512bw", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 32
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testByteP192(byte[] data) {
        for (int j = 0; j < RANGE - 192; j++) {
            data[j + 192] = (byte)(data[j] * (byte)11);
        }
    }

    @Run(test = "testByteP192")
    @Warmup(0)
    public static void runByteP192() {
        byte[] data = new byte[RANGE];
        init(data);
        testByteP192(data);
        verify("testByteP192", data, goldByteP192);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP0(float[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j + 0] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP0")
    @Warmup(0)
    public static void runFloatP0() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP0(data);
        verify("testFloatP0", data, goldFloatP0);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM1(float[] data) {
        for (int j = 1; j < RANGE; j++) {
            data[j + -1] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM1")
    @Warmup(0)
    public static void runFloatM1() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM1(data);
        verify("testFloatM1", data, goldFloatM1);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 4 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 4"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 4"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP1(float[] data) {
        for (int j = 0; j < RANGE - 1; j++) {
            data[j + 1] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP1")
    @Warmup(0)
    public static void runFloatP1() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP1(data);
        verify("testFloatP1", data, goldFloatP1);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM2(float[] data) {
        for (int j = 2; j < RANGE; j++) {
            data[j + -2] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM2")
    @Warmup(0)
    public static void runFloatM2() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM2(data);
        verify("testFloatM2", data, goldFloatM2);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP2(float[] data) {
        for (int j = 0; j < RANGE - 2; j++) {
            data[j + 2] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP2")
    @Warmup(0)
    public static void runFloatP2() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP2(data);
        verify("testFloatP2", data, goldFloatP2);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM3(float[] data) {
        for (int j = 3; j < RANGE; j++) {
            data[j + -3] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM3")
    @Warmup(0)
    public static void runFloatM3() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM3(data);
        verify("testFloatM3", data, goldFloatM3);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    // positive byte_offset 12 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 12"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 12"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 12 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 12"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 12"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 12 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 12"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 12"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 12 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 12"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 12"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP3(float[] data) {
        for (int j = 0; j < RANGE - 3; j++) {
            data[j + 3] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP3")
    @Warmup(0)
    public static void runFloatP3() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP3(data);
        verify("testFloatP3", data, goldFloatP3);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM4(float[] data) {
        for (int j = 4; j < RANGE; j++) {
            data[j + -4] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM4")
    @Warmup(0)
    public static void runFloatM4() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM4(data);
        verify("testFloatM4", data, goldFloatM4);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP4(float[] data) {
        for (int j = 0; j < RANGE - 4; j++) {
            data[j + 4] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP4")
    @Warmup(0)
    public static void runFloatP4() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP4(data);
        verify("testFloatP4", data, goldFloatP4);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM7(float[] data) {
        for (int j = 7; j < RANGE; j++) {
            data[j + -7] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM7")
    @Warmup(0)
    public static void runFloatM7() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM7(data);
        verify("testFloatM7", data, goldFloatM7);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 28 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 28"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 28"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 28 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 28"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 28"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    // positive byte_offset 28 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 28"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 28"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP7(float[] data) {
        for (int j = 0; j < RANGE - 7; j++) {
            data[j + 7] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP7")
    @Warmup(0)
    public static void runFloatP7() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP7(data);
        verify("testFloatP7", data, goldFloatP7);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM8(float[] data) {
        for (int j = 8; j < RANGE; j++) {
            data[j + -8] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM8")
    @Warmup(0)
    public static void runFloatM8() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM8(data);
        verify("testFloatM8", data, goldFloatM8);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 32 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 32"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 32"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP8(float[] data) {
        for (int j = 0; j < RANGE - 8; j++) {
            data[j + 8] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP8")
    @Warmup(0)
    public static void runFloatP8() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP8(data);
        verify("testFloatP8", data, goldFloatP8);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM15(float[] data) {
        for (int j = 15; j < RANGE; j++) {
            data[j + -15] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM15")
    @Warmup(0)
    public static void runFloatM15() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM15(data);
        verify("testFloatM15", data, goldFloatM15);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    // positive byte_offset 60 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 8", "SuperWordMaxVectorSize", "<= 60"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 8", "SuperWordMaxVectorSize", "> 60"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP15(float[] data) {
        for (int j = 0; j < RANGE - 15; j++) {
            data[j + 15] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP15")
    @Warmup(0)
    public static void runFloatP15() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP15(data);
        verify("testFloatP15", data, goldFloatP15);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM16(float[] data) {
        for (int j = 16; j < RANGE; j++) {
            data[j + -16] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM16")
    @Warmup(0)
    public static void runFloatM16() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM16(data);
        verify("testFloatM16", data, goldFloatM16);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP16(float[] data) {
        for (int j = 0; j < RANGE - 16; j++) {
            data[j + 16] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP16")
    @Warmup(0)
    public static void runFloatP16() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP16(data);
        verify("testFloatP16", data, goldFloatP16);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM31(float[] data) {
        for (int j = 31; j < RANGE; j++) {
            data[j + -31] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM31")
    @Warmup(0)
    public static void runFloatM31() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM31(data);
        verify("testFloatM31", data, goldFloatM31);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP31(float[] data) {
        for (int j = 0; j < RANGE - 31; j++) {
            data[j + 31] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP31")
    @Warmup(0)
    public static void runFloatP31() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP31(data);
        verify("testFloatP31", data, goldFloatP31);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM32(float[] data) {
        for (int j = 32; j < RANGE; j++) {
            data[j + -32] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM32")
    @Warmup(0)
    public static void runFloatM32() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM32(data);
        verify("testFloatM32", data, goldFloatM32);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP32(float[] data) {
        for (int j = 0; j < RANGE - 32; j++) {
            data[j + 32] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP32")
    @Warmup(0)
    public static void runFloatP32() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP32(data);
        verify("testFloatP32", data, goldFloatP32);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM63(float[] data) {
        for (int j = 63; j < RANGE; j++) {
            data[j + -63] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM63")
    @Warmup(0)
    public static void runFloatM63() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM63(data);
        verify("testFloatM63", data, goldFloatM63);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP63(float[] data) {
        for (int j = 0; j < RANGE - 63; j++) {
            data[j + 63] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP63")
    @Warmup(0)
    public static void runFloatP63() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP63(data);
        verify("testFloatP63", data, goldFloatP63);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM64(float[] data) {
        for (int j = 64; j < RANGE; j++) {
            data[j + -64] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM64")
    @Warmup(0)
    public static void runFloatM64() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM64(data);
        verify("testFloatM64", data, goldFloatM64);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP64(float[] data) {
        for (int j = 0; j < RANGE - 64; j++) {
            data[j + 64] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP64")
    @Warmup(0)
    public static void runFloatP64() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP64(data);
        verify("testFloatP64", data, goldFloatP64);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM65(float[] data) {
        for (int j = 65; j < RANGE; j++) {
            data[j + -65] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM65")
    @Warmup(0)
    public static void runFloatM65() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM65(data);
        verify("testFloatM65", data, goldFloatM65);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP65(float[] data) {
        for (int j = 0; j < RANGE - 65; j++) {
            data[j + 65] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP65")
    @Warmup(0)
    public static void runFloatP65() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP65(data);
        verify("testFloatP65", data, goldFloatP65);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM128(float[] data) {
        for (int j = 128; j < RANGE; j++) {
            data[j + -128] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM128")
    @Warmup(0)
    public static void runFloatM128() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM128(data);
        verify("testFloatM128", data, goldFloatM128);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP128(float[] data) {
        for (int j = 0; j < RANGE - 128; j++) {
            data[j + 128] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP128")
    @Warmup(0)
    public static void runFloatP128() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP128(data);
        verify("testFloatP128", data, goldFloatP128);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM129(float[] data) {
        for (int j = 129; j < RANGE; j++) {
            data[j + -129] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM129")
    @Warmup(0)
    public static void runFloatM129() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM129(data);
        verify("testFloatM129", data, goldFloatM129);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP129(float[] data) {
        for (int j = 0; j < RANGE - 129; j++) {
            data[j + 129] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP129")
    @Warmup(0)
    public static void runFloatP129() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP129(data);
        verify("testFloatP129", data, goldFloatP129);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatM192(float[] data) {
        for (int j = 192; j < RANGE; j++) {
            data[j + -192] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatM192")
    @Warmup(0)
    public static void runFloatM192() {
        float[] data = new float[RANGE];
        init(data);
        testFloatM192(data);
        verify("testFloatM192", data, goldFloatM192);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 16
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testFloatP192(float[] data) {
        for (int j = 0; j < RANGE - 192; j++) {
            data[j + 192] = (float)(data[j] * (float)1.001f);
        }
    }

    @Run(test = "testFloatP192")
    @Warmup(0)
    public static void runFloatP192() {
        float[] data = new float[RANGE];
        init(data);
        testFloatP192(data);
        verify("testFloatP192", data, goldFloatP192);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP0(double[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j + 0] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP0")
    @Warmup(0)
    public static void runDoubleP0() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP0(data);
        verify("testDoubleP0", data, goldDoubleP0);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM1(double[] data) {
        for (int j = 1; j < RANGE; j++) {
            data[j + -1] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM1")
    @Warmup(0)
    public static void runDoubleM1() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM1(data);
        verify("testDoubleM1", data, goldDoubleM1);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 8 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 8"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 8"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP1(double[] data) {
        for (int j = 0; j < RANGE - 1; j++) {
            data[j + 1] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP1")
    @Warmup(0)
    public static void runDoubleP1() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP1(data);
        verify("testDoubleP1", data, goldDoubleP1);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM2(double[] data) {
        for (int j = 2; j < RANGE; j++) {
            data[j + -2] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM2")
    @Warmup(0)
    public static void runDoubleM2() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM2(data);
        verify("testDoubleM2", data, goldDoubleM2);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 16 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP2(double[] data) {
        for (int j = 0; j < RANGE - 2; j++) {
            data[j + 2] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP2")
    @Warmup(0)
    public static void runDoubleP2() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP2(data);
        verify("testDoubleP2", data, goldDoubleP2);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM3(double[] data) {
        for (int j = 3; j < RANGE; j++) {
            data[j + -3] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM3")
    @Warmup(0)
    public static void runDoubleM3() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM3(data);
        verify("testDoubleM3", data, goldDoubleM3);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 24 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 24"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 24"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 24 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 24"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 24"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    // positive byte_offset 24 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 24"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 24"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP3(double[] data) {
        for (int j = 0; j < RANGE - 3; j++) {
            data[j + 3] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP3")
    @Warmup(0)
    public static void runDoubleP3() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP3(data);
        verify("testDoubleP3", data, goldDoubleP3);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM4(double[] data) {
        for (int j = 4; j < RANGE; j++) {
            data[j + -4] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM4")
    @Warmup(0)
    public static void runDoubleM4() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM4(data);
        verify("testDoubleM4", data, goldDoubleM4);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 32 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 32"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 32"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP4(double[] data) {
        for (int j = 0; j < RANGE - 4; j++) {
            data[j + 4] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP4")
    @Warmup(0)
    public static void runDoubleP4() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP4(data);
        verify("testDoubleP4", data, goldDoubleP4);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM7(double[] data) {
        for (int j = 7; j < RANGE; j++) {
            data[j + -7] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM7")
    @Warmup(0)
    public static void runDoubleM7() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM7(data);
        verify("testDoubleM7", data, goldDoubleM7);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    // positive byte_offset 56 can lead to cyclic dependency
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIfAnd = {"SuperWordMaxVectorSize", ">= 16", "SuperWordMaxVectorSize", "<= 56"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIfOr = {"SuperWordMaxVectorSize", "< 16", "SuperWordMaxVectorSize", "> 56"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP7(double[] data) {
        for (int j = 0; j < RANGE - 7; j++) {
            data[j + 7] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP7")
    @Warmup(0)
    public static void runDoubleP7() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP7(data);
        verify("testDoubleP7", data, goldDoubleP7);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM8(double[] data) {
        for (int j = 8; j < RANGE; j++) {
            data[j + -8] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM8")
    @Warmup(0)
    public static void runDoubleM8() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM8(data);
        verify("testDoubleM8", data, goldDoubleM8);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP8(double[] data) {
        for (int j = 0; j < RANGE - 8; j++) {
            data[j + 8] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP8")
    @Warmup(0)
    public static void runDoubleP8() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP8(data);
        verify("testDoubleP8", data, goldDoubleP8);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM15(double[] data) {
        for (int j = 15; j < RANGE; j++) {
            data[j + -15] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM15")
    @Warmup(0)
    public static void runDoubleM15() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM15(data);
        verify("testDoubleM15", data, goldDoubleM15);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP15(double[] data) {
        for (int j = 0; j < RANGE - 15; j++) {
            data[j + 15] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP15")
    @Warmup(0)
    public static void runDoubleP15() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP15(data);
        verify("testDoubleP15", data, goldDoubleP15);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM16(double[] data) {
        for (int j = 16; j < RANGE; j++) {
            data[j + -16] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM16")
    @Warmup(0)
    public static void runDoubleM16() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM16(data);
        verify("testDoubleM16", data, goldDoubleM16);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP16(double[] data) {
        for (int j = 0; j < RANGE - 16; j++) {
            data[j + 16] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP16")
    @Warmup(0)
    public static void runDoubleP16() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP16(data);
        verify("testDoubleP16", data, goldDoubleP16);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM31(double[] data) {
        for (int j = 31; j < RANGE; j++) {
            data[j + -31] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM31")
    @Warmup(0)
    public static void runDoubleM31() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM31(data);
        verify("testDoubleM31", data, goldDoubleM31);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP31(double[] data) {
        for (int j = 0; j < RANGE - 31; j++) {
            data[j + 31] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP31")
    @Warmup(0)
    public static void runDoubleP31() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP31(data);
        verify("testDoubleP31", data, goldDoubleP31);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM32(double[] data) {
        for (int j = 32; j < RANGE; j++) {
            data[j + -32] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM32")
    @Warmup(0)
    public static void runDoubleM32() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM32(data);
        verify("testDoubleM32", data, goldDoubleM32);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP32(double[] data) {
        for (int j = 0; j < RANGE - 32; j++) {
            data[j + 32] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP32")
    @Warmup(0)
    public static void runDoubleP32() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP32(data);
        verify("testDoubleP32", data, goldDoubleP32);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM63(double[] data) {
        for (int j = 63; j < RANGE; j++) {
            data[j + -63] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM63")
    @Warmup(0)
    public static void runDoubleM63() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM63(data);
        verify("testDoubleM63", data, goldDoubleM63);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP63(double[] data) {
        for (int j = 0; j < RANGE - 63; j++) {
            data[j + 63] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP63")
    @Warmup(0)
    public static void runDoubleP63() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP63(data);
        verify("testDoubleP63", data, goldDoubleP63);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM64(double[] data) {
        for (int j = 64; j < RANGE; j++) {
            data[j + -64] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM64")
    @Warmup(0)
    public static void runDoubleM64() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM64(data);
        verify("testDoubleM64", data, goldDoubleM64);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP64(double[] data) {
        for (int j = 0; j < RANGE - 64; j++) {
            data[j + 64] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP64")
    @Warmup(0)
    public static void runDoubleP64() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP64(data);
        verify("testDoubleP64", data, goldDoubleP64);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM65(double[] data) {
        for (int j = 65; j < RANGE; j++) {
            data[j + -65] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM65")
    @Warmup(0)
    public static void runDoubleM65() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM65(data);
        verify("testDoubleM65", data, goldDoubleM65);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP65(double[] data) {
        for (int j = 0; j < RANGE - 65; j++) {
            data[j + 65] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP65")
    @Warmup(0)
    public static void runDoubleP65() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP65(data);
        verify("testDoubleP65", data, goldDoubleP65);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM128(double[] data) {
        for (int j = 128; j < RANGE; j++) {
            data[j + -128] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM128")
    @Warmup(0)
    public static void runDoubleM128() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM128(data);
        verify("testDoubleM128", data, goldDoubleM128);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP128(double[] data) {
        for (int j = 0; j < RANGE - 128; j++) {
            data[j + 128] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP128")
    @Warmup(0)
    public static void runDoubleP128() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP128(data);
        verify("testDoubleP128", data, goldDoubleP128);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM129(double[] data) {
        for (int j = 129; j < RANGE; j++) {
            data[j + -129] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM129")
    @Warmup(0)
    public static void runDoubleM129() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM129(data);
        verify("testDoubleM129", data, goldDoubleM129);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP129(double[] data) {
        for (int j = 0; j < RANGE - 129; j++) {
            data[j + 129] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP129")
    @Warmup(0)
    public static void runDoubleP129() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP129(data);
        verify("testDoubleP129", data, goldDoubleP129);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleM192(double[] data) {
        for (int j = 192; j < RANGE; j++) {
            data[j + -192] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleM192")
    @Warmup(0)
    public static void runDoubleM192() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleM192(data);
        verify("testDoubleM192", data, goldDoubleM192);
    }

    @Test
    // cpu: sse4.1 -> vector_width: 16 -> elements in vector: 2
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"sse4.1", "true", "avx", "false"})
    // cpu: avx and avx2 -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeatureAnd = {"avx", "true", "avx512", "false"})
    // cpu: avx512 -> vector_width: 64 -> elements in vector: 8
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"avx512", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"avx512", "true"})
    // cpu: asimd -> vector_width: 32 -> elements in vector: 4
    @IR(counts = {IRNode.LOAD_VECTOR, "> 0", IRNode.MUL_V, "> 0", IRNode.STORE_VECTOR, "> 0"},
        applyIf = {"SuperWordMaxVectorSize", ">= 16"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(failOn = {IRNode.LOAD_VECTOR, IRNode.MUL_V, IRNode.STORE_VECTOR},
        applyIf = {"SuperWordMaxVectorSize", "< 16"},
        applyIfCPUFeature = {"asimd", "true"})
    public static void testDoubleP192(double[] data) {
        for (int j = 0; j < RANGE - 192; j++) {
            data[j + 192] = (double)(data[j] * (double)1.001);
        }
    }

    @Run(test = "testDoubleP192")
    @Warmup(0)
    public static void runDoubleP192() {
        double[] data = new double[RANGE];
        init(data);
        testDoubleP192(data);
        verify("testDoubleP192", data, goldDoubleP192);
    }

    // ------------------- Initialization -------------------

    static void init(int[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j] = (int)j;
        }
    }

    static void init(long[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j] = (long)j;
        }
    }

    static void init(short[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j] = (short)j;
        }
    }

    static void init(char[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j] = (char)j;
        }
    }

    static void init(byte[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j] = (byte)j;
        }
    }

    static void init(float[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j] = (float)j;
        }
    }

    static void init(double[] data) {
        for (int j = 0; j < RANGE; j++) {
            data[j] = (double)j;
        }
    }

    // ------------------- Verification -------------------

    static void verify(String context, int[] data, int[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + context + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
    static void verify(String context, long[] data, long[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + context + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
    static void verify(String context, short[] data, short[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + context + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
    static void verify(String context, char[] data, char[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + context + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
    static void verify(String context, byte[] data, byte[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + context + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
    static void verify(String context, float[] data, float[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + context + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
    static void verify(String context, double[] data, double[] gold) {
        for (int i = 0; i < RANGE; i++) {
            if (data[i] != gold[i]) {
                throw new RuntimeException(" Invalid " + context + " result: data[" + i + "]: " + data[i] + " != " + gold[i]);
            }
        }
    }
}
