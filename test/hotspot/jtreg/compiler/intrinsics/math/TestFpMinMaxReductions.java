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

/**
 * @test
 * @bug 8287087
 * @summary Test that floating-point min/max x64 operations are implemented
 *          differently depending on whether they are part of a reduction. These
 *          tests complement those in TestFpMinMaxIntrinsics, which focus more
 *          on correctness aspects.
 * @library /test/lib /
 * @requires os.simpleArch == "x64" & vm.cpu.features ~= ".*avx.*"
 * @run driver compiler.intrinsics.math.TestFpMinMaxReductions
 */

package compiler.intrinsics.math;

import compiler.lib.ir_framework.*;

public class TestFpMinMaxReductions {

    private static float acc;
    private static float floatInput1;
    private static float floatInput2;
    private static float[] floatArray = new float[1000];

    private static double doubleInput1;
    private static double doubleInput2;
    private static double[] doubleArray = new double[1000];

    private static int stride = 1;

    public static void main(String[] args) throws Exception {
        TestFramework.run();
    }

    @Test
    @IR(counts = {IRNode.MIN_F_REG, "1"},
        failOn = {IRNode.MIN_F_REDUCTION_REG})
    private static float testFloatMin() {
        return Math.min(floatInput1, floatInput2);
    }

    @Test
    @IR(counts = {IRNode.MAX_F_REG, "1"},
        failOn = {IRNode.MAX_F_REDUCTION_REG})
    private static float testFloatMax() {
        return Math.max(floatInput1, floatInput2);
    }

    @Test
    @IR(counts = {IRNode.MIN_F_REDUCTION_REG, ">= 1"})
    private static float testFloatMinReduction() {
        float fmin = Float.POSITIVE_INFINITY;
        for (int i = 0; i < floatArray.length; i++) {
            fmin = Math.min(fmin, floatArray[i]);
        }
        return fmin;
    }

    @Test
    @IR(counts = {IRNode.MIN_F_REDUCTION_REG, ">= 1"})
    private static float testFloatMinReductionPartiallyUnrolled() {
        float fmin = Float.POSITIVE_INFINITY;
        for (int i = 0; i < floatArray.length / 2; i++) {
            fmin = Math.min(fmin, floatArray[2*i]);
            fmin = Math.min(fmin, floatArray[2*i + 1]);
        }
        return fmin;
    }

    @Test
    @IR(counts = {IRNode.MIN_F_REDUCTION_REG, ">= 1"})
    private static float testFloatMinReductionNonCounted() {
        float fmin = Float.POSITIVE_INFINITY;
        for (int i = 0; i < floatArray.length; i += stride) {
            fmin = Math.min(fmin, floatArray[i]);
        }
        return fmin;
    }

    @Test
    @IR(counts = {IRNode.MIN_F_REDUCTION_REG, ">= 1"})
    private static float testFloatMinReductionGlobalAccumulator() {
        acc = Float.POSITIVE_INFINITY;
        for (int i = 0; i < floatArray.length; i++) {
            acc = Math.min(acc, floatArray[i]);
        }
        return acc;
    }

    @Test
    @IR(counts = {IRNode.MIN_F_REDUCTION_REG, ">= 1"})
    private static float testFloatMinReductionInOuterLoop() {
        float fmin = Float.POSITIVE_INFINITY;
        int count = 0;
        for (int i = 0; i < floatArray.length; i++) {
            fmin = Math.min(fmin, floatArray[i]);
            for (int j = 0; j < 10; j += stride) {
                count++;
            }
        }
        return fmin + count;
    }

    @Test
    @IR(counts = {IRNode.MAX_F_REDUCTION_REG, ">= 1"})
    private static float testFloatMaxReduction() {
        float fmax = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < floatArray.length; i++) {
            fmax = Math.max(fmax, floatArray[i]);
        }
        return fmax;
    }

    @Test
    @IR(counts = {IRNode.MIN_D_REG, "1"},
        failOn = {IRNode.MIN_D_REDUCTION_REG})
    private static double testDoubleMin() {
        return Math.min(doubleInput1, doubleInput2);
    }

    @Test
    @IR(counts = {IRNode.MAX_D_REG, "1"},
        failOn = {IRNode.MAX_D_REDUCTION_REG})
    private static double testDoubleMax() {
        return Math.max(doubleInput1, doubleInput2);
    }

    @Test
    @IR(counts = {IRNode.MIN_D_REDUCTION_REG, ">= 1"})
    private static double testDoubleMinReduction() {
        double fmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < doubleArray.length; i++) {
            fmin = Math.min(fmin, doubleArray[i]);
        }
        return fmin;
    }

    @Test
    @IR(counts = {IRNode.MAX_D_REDUCTION_REG, ">= 1"})
    private static double testDoubleMaxReduction() {
        double fmax = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < doubleArray.length; i++) {
            fmax = Math.max(fmax, doubleArray[i]);
        }
        return fmax;
    }

}
