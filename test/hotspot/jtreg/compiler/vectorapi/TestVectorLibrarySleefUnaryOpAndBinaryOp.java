/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.incubator.vector.*;
import jtreg.SkippedException;

import static jdk.incubator.vector.VectorOperators.ACOS;
import static jdk.incubator.vector.VectorOperators.ADD;
import static jdk.incubator.vector.VectorOperators.ASIN;
import static jdk.incubator.vector.VectorOperators.ATAN;
import static jdk.incubator.vector.VectorOperators.ATAN2;
import static jdk.incubator.vector.VectorOperators.CBRT;
import static jdk.incubator.vector.VectorOperators.COS;
import static jdk.incubator.vector.VectorOperators.COSH;
import static jdk.incubator.vector.VectorOperators.EXP;
import static jdk.incubator.vector.VectorOperators.EXPM1;
import static jdk.incubator.vector.VectorOperators.HYPOT;
import static jdk.incubator.vector.VectorOperators.LOG;
import static jdk.incubator.vector.VectorOperators.LOG10;
import static jdk.incubator.vector.VectorOperators.LOG1P;
import static jdk.incubator.vector.VectorOperators.POW;
import static jdk.incubator.vector.VectorOperators.SIN;
import static jdk.incubator.vector.VectorOperators.SINH;
import static jdk.incubator.vector.VectorOperators.TAN;

/**
 * @test
 * @bug 8376602
 * @library /test/lib /
 * @requires (os.arch == "aarch64" & vm.cpu.features ~= ".*asimd.*") |
 *           (os.arch == "riscv64" & vm.cpu.features ~= ".*rvv.*")
 * @summary VectorAPI: SLEEF unary and binary math library operations should be intrinsified.
 *          This test is run on SVML/SLEEF supported platforms only.
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.TestVectorLibrarySleefUnaryOpAndBinaryOp
 */

public class TestVectorLibrarySleefUnaryOpAndBinaryOp {
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_128;
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_128;

    // TANH is not included because VectorMathLibrary.SLEEF intentionally rejects it.
    private static final int SLEEF_UNARY_OP_COUNT = 14;
    private static final int SLEEF_BINARY_OP_COUNT = 3;

    @Test
    @IR(counts = { IRNode.CALL_LEAF_VECTOR, "= " + SLEEF_UNARY_OP_COUNT })
    public static float testFloatUnary() {
        FloatVector v = FloatVector.broadcast(F_SPECIES, 3.14f);
        FloatVector r = FloatVector.zero(F_SPECIES);

        r = r.add(v.lanewise(SIN));
        r = r.add(v.lanewise(COS));
        r = r.add(v.lanewise(TAN));
        r = r.add(v.lanewise(ASIN));
        r = r.add(v.lanewise(ACOS));
        r = r.add(v.lanewise(ATAN));
        r = r.add(v.lanewise(EXP));
        r = r.add(v.lanewise(LOG));
        r = r.add(v.lanewise(LOG10));
        r = r.add(v.lanewise(CBRT));
        r = r.add(v.lanewise(SINH));
        r = r.add(v.lanewise(COSH));
        r = r.add(v.lanewise(EXPM1));
        r = r.add(v.lanewise(LOG1P));

        return r.reduceLanes(ADD);
    }

    @Test
    @IR(counts = { IRNode.CALL_LEAF_VECTOR, "= " + SLEEF_UNARY_OP_COUNT })
    public static double testDoubleUnary() {
        DoubleVector v = DoubleVector.broadcast(D_SPECIES, 3.14d);
        DoubleVector r = DoubleVector.zero(D_SPECIES);

        r = r.add(v.lanewise(SIN));
        r = r.add(v.lanewise(COS));
        r = r.add(v.lanewise(TAN));
        r = r.add(v.lanewise(ASIN));
        r = r.add(v.lanewise(ACOS));
        r = r.add(v.lanewise(ATAN));
        r = r.add(v.lanewise(EXP));
        r = r.add(v.lanewise(LOG));
        r = r.add(v.lanewise(LOG10));
        r = r.add(v.lanewise(CBRT));
        r = r.add(v.lanewise(SINH));
        r = r.add(v.lanewise(COSH));
        r = r.add(v.lanewise(EXPM1));
        r = r.add(v.lanewise(LOG1P));

        return r.reduceLanes(ADD);
    }

    @Test
    @IR(counts = { IRNode.CALL_LEAF_VECTOR, "= " + SLEEF_BINARY_OP_COUNT })
    public static float testFloatBinary() {
        FloatVector v1 = FloatVector.broadcast(F_SPECIES, 3.14f);
        FloatVector v2 = FloatVector.broadcast(F_SPECIES, 0.5f);
        FloatVector r = FloatVector.zero(F_SPECIES);

        r = r.add(v1.lanewise(ATAN2, v2));
        r = r.add(v1.lanewise(POW, v2));
        r = r.add(v1.lanewise(HYPOT, v2));

        return r.reduceLanes(ADD);
    }

    @Test
    @IR(counts = { IRNode.CALL_LEAF_VECTOR, "= " + SLEEF_BINARY_OP_COUNT })
    public static double testDoubleBinary() {
        DoubleVector v1 = DoubleVector.broadcast(D_SPECIES, 3.14d);
        DoubleVector v2 = DoubleVector.broadcast(D_SPECIES, 0.5d);
        DoubleVector r = DoubleVector.zero(D_SPECIES);

        r = r.add(v1.lanewise(ATAN2, v2));
        r = r.add(v1.lanewise(POW, v2));
        r = r.add(v1.lanewise(HYPOT, v2));

        return r.reduceLanes(ADD);
    }

    private static void checkSleef() {
        try {
            System.loadLibrary("sleef");
        } catch (UnsatisfiedLinkError _) {
            throw new SkippedException("SLEEF not found");
        }
    }

    public static void main(String[] args) {
        checkSleef();

        TestFramework testFramework = new TestFramework();
        testFramework.addFlags("--add-modules=jdk.incubator.vector")
                     .start();
    }
}
