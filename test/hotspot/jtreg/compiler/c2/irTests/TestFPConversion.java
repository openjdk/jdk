/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8345219
 * @summary Test that code generation for FP conversion works as intended
 * @library /test/lib /
 * @requires os.arch != "x86" & os.arch != "i386"
 * @run driver compiler.c2.irTests.TestFPConversion
 */
public class TestFPConversion {
    static final double[] DOUBLES = new double[] {
            Double.NEGATIVE_INFINITY,
            -Double.MAX_VALUE,
            -1.0,
            -Double.MIN_VALUE,
            -0.0,
            0.0,
            Double.MIN_VALUE,
            1.0,
            Double.MAX_VALUE,
            Double.POSITIVE_INFINITY,
            Double.NaN,
    };

    static final float[] FLOATS = new float[] {
            Float.NEGATIVE_INFINITY,
            -Float.MAX_VALUE,
            -1.0F,
            -Float.MIN_VALUE,
            -0.0F,
            0.0F,
            Float.MIN_VALUE,
            1.0F,
            Float.MAX_VALUE,
            Float.POSITIVE_INFINITY,
            Float.NaN,
    };

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.MOV_D2L, "1"})
    public long doubleToRawLongBits(double x) {
        return Double.doubleToRawLongBits(x);
    }

    @Test
    @IR(counts = {IRNode.MOV_D2L, "1"})
    public long doubleToLongBits(double x) {
        return Double.doubleToLongBits(x);
    }

    @Test
    @IR(counts = {IRNode.MOV_L2D, "1"})
    public double longBitsToDouble(long x) {
        return Double.longBitsToDouble(x);
    }

    @Test
    @IR(counts = {IRNode.MOV_F2I, "1"})
    public int floatToRawIntBits(float x) {
        return Float.floatToRawIntBits(x);
    }

    @Test
    @IR(counts = {IRNode.MOV_F2I, "1"})
    public int floatToIntBits(float x) {
        return Float.floatToIntBits(x);
    }

    @Test
    @IR(counts = {IRNode.MOV_I2F, "1"})
    public float intBitsToFloat(int x) {
        return Float.intBitsToFloat(x);
    }

    @Run(test = {"doubleToRawLongBits", "doubleToLongBits", "longBitsToDouble",
                 "floatToRawIntBits", "floatToIntBits", "intBitsToFloat"})
    public void runTests() {
        for (int i = 0; i < DOUBLES.length; i++) {
            double d = DOUBLES[i];
            long l1 = doubleToRawLongBits(d);
            long l2 = doubleToLongBits(d);
            double d1 = longBitsToDouble(l1);
            double d2 = longBitsToDouble(l2);
            Asserts.assertEquals(d, d1);
            Asserts.assertEquals(d, d2);
        }
        for (int i = 0; i < FLOATS.length; i++) {
            float f = FLOATS[i];
            int i1 = floatToRawIntBits(f);
            int i2 = floatToIntBits(f);
            float f1 = intBitsToFloat(i1);
            float f2 = intBitsToFloat(i2);
            Asserts.assertEquals(f, f1);
            Asserts.assertEquals(f, f2);
        }
    }
}
