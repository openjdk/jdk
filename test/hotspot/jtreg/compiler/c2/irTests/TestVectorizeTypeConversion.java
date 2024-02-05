/*
 * Copyright (c) 2022, 2023, Arm Limited. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8283091
 * @summary Auto-vectorization enhancement for type conversion between different data sizes.
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx2.*") | os.arch=="aarch64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestVectorizeTypeConversion
 */

public class TestVectorizeTypeConversion {

    final private static int SIZE = 3000;

    private static double[] doublea = new double[SIZE];
    private static double[] doubleb = new double[SIZE];
    private static long[] longa = new long[SIZE];
    private static long[] longb = new long[SIZE];
    private static int[] inta = new int[SIZE];
    private static int[] intb = new int[SIZE];
    private static float[] floata = new float[SIZE];
    private static float[] floatb = new float[SIZE];

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:+IgnoreUnrecognizedVMOptions", "-XX:+SuperWordRTDepCheck");
    }

    @Test
    // Mixing types of different sizes has the effect that some vectors are shorter than the type allows.
    @IR(counts = {IRNode.LOAD_VECTOR_I,   IRNode.VECTOR_SIZE + "min(max_int, max_double)", ">0",
                  IRNode.VECTOR_CAST_I2D, IRNode.VECTOR_SIZE + "min(max_int, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"})
    private static void testConvI2D(double[] d, int[] a) {
        for(int i = 0; i < d.length; i++) {
            d[i] = (double) (a[i]);
        }
    }

    @Test
    // Mixing types of different sizes has the effect that some vectors are shorter than the type allows.
    @IR(counts = {IRNode.LOAD_VECTOR_L,   IRNode.VECTOR_SIZE + "min(max_int, max_long)", ">0",
                  IRNode.LOAD_VECTOR_I,   IRNode.VECTOR_SIZE + "min(max_int, max_long)", ">0",
                  IRNode.VECTOR_CAST_I2L, IRNode.VECTOR_SIZE + "min(max_int, max_long)", ">0",
                  IRNode.VECTOR_CAST_L2I, IRNode.VECTOR_SIZE + "min(max_int, max_long)", ">0",
                  IRNode.STORE_VECTOR, ">0"})
    private static void testConvI2L(int[] d1, int d2[], long[] a1, long[] a2) {
        for(int i = 0; i < d1.length; i++) {
            d1[i] = (int) (a1[i]);
            a2[i] = (long) (d2[i]);
        }
    }

    @Test
    // Mixing types of different sizes has the effect that some vectors are shorter than the type allows.
    @IR(counts = {IRNode.LOAD_VECTOR_F,   IRNode.VECTOR_SIZE + "min(max_float, max_double)", ">0",
                  IRNode.LOAD_VECTOR_D,   IRNode.VECTOR_SIZE + "min(max_float, max_double)", ">0",
                  IRNode.VECTOR_CAST_D2F, IRNode.VECTOR_SIZE + "min(max_float, max_double)", ">0",
                  IRNode.VECTOR_CAST_F2D, IRNode.VECTOR_SIZE + "min(max_float, max_double)", ">0",
                  IRNode.STORE_VECTOR, ">0"})
    private static void testConvF2D(double[] d1, double[] d2, float[] a1, float[] a2) {
        for(int i = 0; i < d1.length; i++) {
            d1[i] = (double) (a1[i]);
            a2[i] = (float) (d2[i]);
        }
    }

    @Run(test = {"testConvI2D", "testConvI2L", "testConvF2D"})
    private void test_runner() {
        testConvI2D(doublea, inta);
        testConvI2L(inta, intb, longa, longb);
        testConvF2D(doublea, doubleb, floata, floatb);
    }
}
