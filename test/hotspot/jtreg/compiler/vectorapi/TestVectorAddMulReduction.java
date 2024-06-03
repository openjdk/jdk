/*
 * Copyright (c) 2024, Arm Limited. All rights reserved.
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

import java.util.Random;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8320725
 * @library /test/lib /
 * @summary Verify non-strictly ordered AddReductionVF/VD and MulReductionVF/VD
 *          nodes are generated in VectorAPI
 * @modules jdk.incubator.vector
 * @run driver compiler.vectorapi.TestVectorAddMulReduction
 */

public class TestVectorAddMulReduction {

    private static final int SIZE = 1024;
    private static final Random RD = Utils.getRandomInstance();

    private static float[] fa;
    private static float fres;
    private static double[] da;
    private static double dres;

    static {
        fa = new float[SIZE];
        da = new double[SIZE];
        fres = 1;
        dres = 1;
        for (int i = 0; i < SIZE; i++) {
            fa[i] = RD.nextFloat();
            da[i] = RD.nextDouble();
        }
    }

    // Test add reduction operation for floats
    @ForceInline
    public static void testFloatAddKernel(VectorSpecies SPECIES, float[] f) {
        for (int i = 0; i < SPECIES.loopBound(f.length); i += SPECIES.length()) {
            var av = FloatVector.fromArray(SPECIES, f, i);
            fres += av.reduceLanes(VectorOperators.ADD);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=8"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testFloatAdd_64() {
        testFloatAddKernel(FloatVector.SPECIES_64, fa);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testFloatAdd_128() {
        testFloatAddKernel(FloatVector.SPECIES_128, fa);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=32"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testFloatAdd_256() {
        testFloatAddKernel(FloatVector.SPECIES_256, fa);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=64"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testFloatAdd_512() {
        testFloatAddKernel(FloatVector.SPECIES_512, fa);
    }

    // Test add reduction operation for doubles
    @ForceInline
    public static void testDoubleAddKernel(VectorSpecies SPECIES, double[] d) {
        for (int i = 0; i < SPECIES.loopBound(d.length); i += SPECIES.length()) {
            var av = DoubleVector.fromArray(SPECIES, d, i);
            dres += av.reduceLanes(VectorOperators.ADD);
        }
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VD, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testDoubleAdd_128() {
        testDoubleAddKernel(DoubleVector.SPECIES_128, da);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VD, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=32"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testDoubleAdd_256() {
        testDoubleAddKernel(DoubleVector.SPECIES_256, da);
    }

    @Test
    @IR(counts = {IRNode.ADD_REDUCTION_VD, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=64"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testDoubleAdd_512() {
        testDoubleAddKernel(DoubleVector.SPECIES_512, da);
    }

    // Test mul reduction operation for floats
    // On aarch64, there are no direct vector mul reduction instructions for float/double mul reduction
    // and scalar instructions are emitted for 64-bit/128-bit vectors. Thus MulReductionVF/VD nodes are generated
    // only for vector length of 8B/16B on vectorAPI.
    @ForceInline
    public static void testFloatMulKernel(VectorSpecies SPECIES, float[] f) {
        for (int i = 0; i < SPECIES.loopBound(f.length); i += SPECIES.length()) {
            var av = FloatVector.fromArray(SPECIES, f, i);
            fres += av.reduceLanes(VectorOperators.MUL);
        }
    }

    @Test
    @IR(counts = {IRNode.MUL_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=8"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testFloatMul_64() {
        testFloatMulKernel(FloatVector.SPECIES_64, fa);
    }

    @Test
    @IR(counts = {IRNode.MUL_REDUCTION_VF, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testFloatMul_128() {
        testFloatMulKernel(FloatVector.SPECIES_128, fa);
    }

    // Test mul reduction operation for doubles
    @ForceInline
    public static void testDoubleMulKernel(VectorSpecies SPECIES, double[] d) {
        for (int i = 0; i < SPECIES.loopBound(d.length); i += SPECIES.length()) {
            var av = DoubleVector.fromArray(SPECIES, d, i);
            dres += av.reduceLanes(VectorOperators.MUL);
        }
    }

    @Test
    @IR(counts = {IRNode.MUL_REDUCTION_VD, ">=1", "no_strict_order", ">=1"},
        failOn = {"requires_strict_order"},
        applyIfCPUFeatureOr = {"asimd", "true", "sse2", "true"},
        applyIf = {"MaxVectorSize", ">=16"},
        phase = CompilePhase.PRINT_IDEAL)
    public static void testDoubleMul_128() {
        testDoubleMulKernel(DoubleVector.SPECIES_128, da);
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
