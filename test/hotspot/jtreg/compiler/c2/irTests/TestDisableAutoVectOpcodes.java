/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

/*
 * @test
 * @bug 8275275
 * @summary Fix performance regression after auto-vectorization on aarch64 NEON.
 * @requires os.arch=="aarch64"
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestDisableAutoVectOpcodes
 */

public class TestDisableAutoVectOpcodes {

    final private static int SIZE = 3000;

    private static double[] doublea = new double[SIZE];
    private static double[] doubleb = new double[SIZE];
    private static long[] longa = new long[SIZE];
    private static long[] longb = new long[SIZE];
    private static int[] inta = new int[SIZE];
    private static float[] floata = new float[SIZE];
    private static float[] floatb = new float[SIZE];
    private static float fresult;
    private static double dresult;

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:UseSVE=0");
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_CAST_D2I})
    private static void testConvD2I() {
        for(int i = 0; i < SIZE; i++) {
            inta[i] = (int) (doublea[i]);
        }
    }

    @Test
    @IR(failOn = {IRNode.VECTOR_CAST_L2F})
    private static void testConvL2F() {
        for(int i = 0; i < SIZE; i++) {
            floata[i] = (float) (longa[i]);
        }
    }

    @Test
    @IR(failOn = {IRNode.MUL_VL})
    private static void testMulVL() {
        for(int i = 0; i < SIZE; i++) {
            longa[i] = longa[i] * longb[i];
        }
    }

    @Test
    @IR(failOn = {IRNode.ADD_REDUCTION_VF})
    private static void testAddReductionVF() {
        float result = 1;
        for(int i = 0; i < SIZE; i++) {
            result += (floata[i] + floatb[i]);
        }
        fresult += result;
    }

    @Test
    @IR(failOn = {IRNode.ADD_REDUCTION_VD})
    private static void testAddReductionVD() {
        double result = 1;
        for(int i = 0; i < SIZE; i++) {
            result += (doublea[i] + doubleb[i]);
        }
        dresult += result;
    }

    @Test
    @IR(failOn = {IRNode.MUL_REDUCTION_VF})
    private static void testMulReductionVF() {
        float result = 1;
        for(int i = 0; i < SIZE; i++) {
            result *= (floata[i] + floatb[i]);
        }
        fresult += result;
    }

    @Test
    @IR(failOn = {IRNode.MUL_REDUCTION_VD})
    private static void testMulReductionVD() {
        double result = 1;
        for(int i = 0; i < SIZE; i++) {
            result *= (doublea[i] + doubleb[i]);
        }
        dresult += result;
    }

    @Test
    @IR(failOn = {IRNode.COUNTTRAILINGZEROS_VL})
    public void testNumberOfTrailingZeros() {
        for (int i = 0; i < SIZE; ++i) {
            inta[i] = Long.numberOfTrailingZeros(longa[i]);
        }
    }

    @Test
    @IR(failOn = {IRNode.COUNTLEADINGZEROS_VL})
    public void testNumberOfLeadingZeros() {
        for (int i = 0; i < SIZE; ++i) {
            inta[i] = Long.numberOfLeadingZeros(longa[i]);
        }
    }

}
