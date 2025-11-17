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

/*
 * @test 8308776
 * @summary Validate monotonicity of Math.log over an ascending sequence of positive double values
 * @run main TestLogMonotonicity
 */
public class TestLogMonotonicity {
    public static void main(String[] args) {
        double[] values = buildAscendingValues();
        double prev = Double.NEGATIVE_INFINITY;
        for (double v : values) {
            double lv = Math.log(v);
            if (Double.isNaN(lv)) {
                throw new AssertionError("Unexpected NaN for Math.log(" + v + ")");
            }
            if (!(lv > prev)) {
                throw new AssertionError("Math.log not strictly increasing: prev=" + prev + " current=" + lv + " value=" + v);
            }
            prev = lv;
        }
    }

    private static double[] buildAscendingValues() {
        java.util.ArrayList<Double> list = new java.util.ArrayList<>();
        // Subnormal range doubling until normal threshold
        double v = Double.MIN_VALUE;
        while (v < Double.MIN_NORMAL) {
            list.add(v);
            double nv = v * 2.0;
            if (nv == v)
                break;
            v = nv;
        }
        // Transition normals
        list.add(Double.MIN_NORMAL);
        list.add(Double.MIN_NORMAL * 2);
        // Powers of two 2^1 .. 2^16
        for (int i = 1; i <= 16; i++) {
            list.add(Math.pow(2.0, i));
        }
        // Some arbitrary increasing magnitudes
        list.add(Double.MAX_VALUE / 2.0); // large but avoid overflow
        list.add(Double.MAX_VALUE);
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}
