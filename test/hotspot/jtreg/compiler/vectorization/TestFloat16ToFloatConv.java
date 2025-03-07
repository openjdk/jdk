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
* @bug 8350835
* @summary Test bug fix for JDK-8350835 discovered through Template Framework
* @requires vm.compiler2.enabled
* @library /test/lib /
* @run main/othervm -XX:-TieredCompilation -XX:CompileOnly=compiler.vectorization.TestFloat16ToFloatConv::test* compiler.vectorization.TestFloat16ToFloatConv
*/

package compiler.vectorization;

import compiler.lib.ir_framework.*;
import java.util.Random; 

public class TestFloat16ToFloatConv {
    public static Random RANDOM = new Random();
    public static byte[] aB = new byte[1000];
    public static short[] aS = new short[1000];
    public static int[] aI = new int[1000];
    public static long[] aL = new long[1000];
    public static float[] goldB, goldS, goldI, goldL;

    static {
        for (int i = 0; i < aB.length; i++) {
            aB[i] = (byte)RANDOM.nextInt();
            aS[i] = (short)RANDOM.nextInt();
            aI[i] = RANDOM.nextInt();
            aL[i] = RANDOM.nextLong();
        }
        goldB = testByteKernel(aB);
        goldS = testShortKernel(aS);
        goldI = testIntKernel(aI);
        goldL = testLongKernel(aL);
    }
    
    @Test
    public static float[] testByteKernel(byte[] barr) {
        float[] res = new float[barr.length];
        for (int i = 0; i < barr.length; i++) {
            res[i] = Float.float16ToFloat(barr[i]);
        }
        return res;
    }

    @Test
    public static float[] testShortKernel(short[] sarr) {
        float[] res = new float[sarr.length];
        for (int i = 0; i < sarr.length; i++) {
            res[i] = Float.float16ToFloat(sarr[i]);
        }
        return res;
    }

    @Test
    public static float[] testIntKernel(int[] iarr) {
        float[] res = new float[iarr.length];
        for (int i = 0; i < iarr.length; i++) {
            res[i] = Float.float16ToFloat((short)iarr[i]);
        }
        return res;
    }

    @Test
    public static float[] testLongKernel(long[] larr) {
        float[] res = new float[larr.length];
        for (int i = 0; i < larr.length; i++) {
            res[i] = Float.float16ToFloat((short)larr[i]);
        }
        return res;
    }

    public static void checkResult(float[] res, float[] gold) {
        for (int i = 0; i < res.length; i++) {
            if (Float.floatToIntBits(res[i]) != Float.floatToIntBits(gold[i])) {
                throw new RuntimeException("wrong value: " + Float.floatToRawIntBits(res[i]) + " " + Float.floatToRawIntBits(gold[i]));
            }
        }
    }

    @Run(test = {"testByteKernel"})
    public static void testByte() {
        float[] farr = testByteKernel(aB);
        checkResult(farr, goldB);
    }

    @Run(test = {"testShortKernel"})
    public static void testShort() {
        float[] farr = testShortKernel(aS);
        checkResult(farr, goldS);
    }

    @Run(test = {"testIntKernel"})
    public static void testInt() {
        float[] farr = testIntKernel(aI);
        checkResult(farr, goldI);
    }

    @Run(test = {"testLongKernel"})
    public static void testLong() {
        float[] farr = testLongKernel(aL);
        checkResult(farr, goldL);
    }

    public static void main(String [] args) {
        TestFramework.run(TestFloat16ToFloatConv.class);
    }
}

