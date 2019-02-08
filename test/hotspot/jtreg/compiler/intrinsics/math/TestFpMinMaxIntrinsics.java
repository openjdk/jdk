/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Arm Limited. All rights reserved.
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
 * @bug 8212043
 * @summary Test compiler intrinsics of floating-point Math.min/max
 *
 * @run main/othervm -Xint compiler.intrinsics.math.TestFpMinMaxIntrinsics
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -Xcomp -XX:TieredStopAtLevel=1
 *                   -XX:CompileOnly=java/lang/Math
 *                   compiler.intrinsics.math.TestFpMinMaxIntrinsics
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions
 *                   -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileOnly=java/lang/Math
 *                   compiler.intrinsics.math.TestFpMinMaxIntrinsics
 */

package compiler.intrinsics.math;

import java.util.Arrays;

public class TestFpMinMaxIntrinsics {

    private static final float fPos     =  15280.0f;
    private static final float fNeg     = -55555.5f;
    private static final float fPosZero =      0.0f;
    private static final float fNegZero =     -0.0f;
    private static final float fPosInf  = Float.POSITIVE_INFINITY;
    private static final float fNegInf  = Float.NEGATIVE_INFINITY;
    private static final float fNaN     = Float.NaN;

    private static final double dPos     =  482390926662501720.0;
    private static final double dNeg     = -333333333333333333.3;
    private static final double dPosZero =                   0.0;
    private static final double dNegZero =                  -0.0;
    private static final double dPosInf  = Double.POSITIVE_INFINITY;
    private static final double dNegInf  = Double.NEGATIVE_INFINITY;
    private static final double dNaN     = Double.NaN;

    private static final float[][] f_cases = {
        //     a         b         min       max
        {     fPos,     fPos,     fPos,     fPos },
        {     fPos,     fNeg,     fNeg,     fPos },
        { fPosZero, fNegZero, fNegZero, fPosZero },
        { fNegZero, fNegZero, fNegZero, fNegZero },
        {     fPos,  fPosInf,     fPos,  fPosInf },
        {     fNeg,  fNegInf,  fNegInf,     fNeg },
        {     fPos,     fNaN,     fNaN,     fNaN },
        {  fNegInf,     fNaN,     fNaN,     fNaN },
    };

    private static final double[][] d_cases = {
        //     a         b         min       max
        {     dPos,     dPos,     dPos,     dPos },
        {     dPos,     dNeg,     dNeg,     dPos },
        { dPosZero, dNegZero, dNegZero, dPosZero },
        { dNegZero, dNegZero, dNegZero, dNegZero },
        {     dPos,  dPosInf,     dPos,  dPosInf },
        {     dNeg,  dNegInf,  dNegInf,     dNeg },
        {     dPos,     dNaN,     dNaN,     dNaN },
        {  dNegInf,     dNaN,     dNaN,     dNaN },
    };

    private static void fTest(float[] row) {
        float min = Math.min(row[0], row[1]);
        float max = Math.max(row[0], row[1]);
        if (Float.isNaN(min) && Float.isNaN(max)
                && Float.isNaN(row[2]) && Float.isNaN(row[3])) {
            // Return if all of them are NaN
            return;
        }
        if (min != row[2] || max != row[3]) {
            throw new AssertionError("Unexpected result of float min/max: " +
                    "a = " + row[0] + ", b = " + row[1] + ", " +
                    "result = (" + min + ", " + max + "), " +
                    "expected = (" + row[2] + ", " + row[3] + ")");
        }
    }

    private static void dTest(double[] row) {
        double min = Math.min(row[0], row[1]);
        double max = Math.max(row[0], row[1]);
        if (Double.isNaN(min) && Double.isNaN(max)
                && Double.isNaN(row[2]) && Double.isNaN(row[3])) {
            // Return if all of them are NaN
            return;
        }
        if (min != row[2] || max != row[3]) {
            throw new AssertionError("Unexpected result of double min/max" +
                    "a = " + row[0] + ", b = " + row[1] + ", " +
                    "result = (" + min + ", " + max + "), " +
                    "expected = (" + row[2] + ", " + row[3] + ")");
        }
    }

    public static void main(String[] args) {
        Arrays.stream(f_cases).forEach(TestFpMinMaxIntrinsics::fTest);
        Arrays.stream(d_cases).forEach(TestFpMinMaxIntrinsics::dTest);
        System.out.println("PASS");
    }
}

