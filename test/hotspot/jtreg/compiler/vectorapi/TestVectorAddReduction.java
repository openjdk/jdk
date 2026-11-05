/*
 * Copyright 2026 Arm Limited and/or its affiliates.
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

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8391066 8392077
 * @library /test/lib /
 * @summary Verify the identity value (+0.0) is returned when performing ADD
 *          reduction operations with all mask lanes unset.
 * @modules jdk.incubator.vector
 * @run driver compiler.vectorapi.TestVectorAddReduction
 */

public class TestVectorAddReduction {
    private static final int SIZE = 128;

    private static float[] fa;
    private static double[] da;

    static {
        fa = new float[SIZE];
        da = new double[SIZE];
        for (int i = 0; i < SIZE; i++) {
            fa[i] = -0.0f;
            da[i] = -0.0;
        }
    }

    // Test masked add reductions for floats
    @ForceInline
    public static float testFloatAddKernel_masked(VectorSpecies<Float> SPECIES, float[] f) {
        var mask = SPECIES.maskAll(false);
        var av = FloatVector.fromArray(SPECIES, f, 0);
        return av.reduceLanes(VectorOperators.ADD, mask);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true"},
        applyIf = {"MaxVectorSize", ">=8"},
        phase = CompilePhase.PRINT_IDEAL)
    public static float testFloatReduceZero_64_masked() {
        return testFloatAddKernel_masked(FloatVector.SPECIES_64, fa);
    }

    @Check(test = "testFloatReduceZero_64_masked")
    static void checkFloatZero_64_masked(float result) {
        Asserts.assertEquals(+0.0f, result);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
        phase = CompilePhase.PRINT_IDEAL)
    public static float testFloatReduceZero_128_masked() {
        return testFloatAddKernel_masked(FloatVector.SPECIES_128, fa);
    }

    @Check(test = "testFloatReduceZero_128_masked")
    static void checkFloatZero_128_masked(float result) {
        Asserts.assertEquals(+0.0f, result);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    public static float testFloatReduceZero_preferred_masked() {
        return testFloatAddKernel_masked(FloatVector.SPECIES_PREFERRED, fa);
    }

    @Check(test = "testFloatReduceZero_preferred_masked")
    static void checkFloatZero_preferred_masked(float result) {
        Asserts.assertEquals(+0.0f, result);
    }

    // Test masked add reductions for doubles
    @ForceInline
    public static double testDoubleAddKernel_masked(VectorSpecies<Double> SPECIES, double[] d) {
        var mask = SPECIES.maskAll(false);
        var av = DoubleVector.fromArray(SPECIES, d, 0);
        return av.reduceLanes(VectorOperators.ADD, mask);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VD, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
        phase = CompilePhase.PRINT_IDEAL)
    public static double testDoubleReduceZero_128_masked() {
        return testDoubleAddKernel_masked(DoubleVector.SPECIES_128, da);
    }

    @Check(test = "testDoubleReduceZero_128_masked")
    static void checkDoubleZero_128_masked(double result) {
        Asserts.assertEquals(+0.0, result);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VD, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    public static double testDoubleReduceZero_preferred_masked() {
        return testDoubleAddKernel_masked(DoubleVector.SPECIES_PREFERRED, da);
    }

    @Check(test = "testDoubleReduceZero_preferred_masked")
    static void checkDoubleZero_preferred_masked(double result) {
        Asserts.assertEquals(+0.0, result);
    }


    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
