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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import compiler.lib.generators.*;
import jdk.test.lib.Utils;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;

import jdk.internal.math.RelaxedMath;

/*
 * @test
 * @bug 8343597
 * @summary Test RelaxedMath with auto-vectorization.
 * @modules java.base/jdk.internal.math
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestRelaxedMath
 */

// Note: The corresponding benchmark is
//       test/micro/org/openjdk/bench/vm/compiler/VectorRelaxedMath.java
//
//       Please make sure to verify that changes to the IR rules are justified
//       also by the benchmark results.

public class TestRelaxedMath {
    private static int SIZE = 10_000;

    private static Generator<Float>  generatorF = Generators.G.floats();
    private static Generator<Double> generatorD = Generators.G.doubles();

    private static float[] aF = new float[SIZE];
    private static float[] bF = new float[SIZE];

    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestRelaxedMath.class);
        framework.addFlags("--add-modules", "java.base", "--add-exports", "java.base/jdk.internal.math=ALL-UNNAMED");
        framework.start();
    }

    public TestRelaxedMath() {
    }

    @Setup
    public static Object[] setupFloat() {
        Generators.G.fill(generatorF, aF);
        return new Object[] { aF };
    }

    @Setup
    public static Object[] setupFloat2() {
        Generators.G.fill(generatorF, aF);
        Generators.G.fill(generatorF, bF);
        return new Object[] { aF, bF };
    }

    @Test
    @Arguments(setup = "setupFloat")
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "= 0",
                  IRNode.ADD_REDUCTION_VF, "= 0",
                  "requires_strict_order", "= 0"},
        failOn = {"no_strict_order"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    // FAILS: simple sum does not vectorize (see JDK-8307516).
    static float testStrictReductionFloatAdd(float[] a) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float val = a[i];
            sum = sum + val;
        }
        return sum;
    }

    @Test
    @Arguments(setup = "setupFloat")
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "= 0",
                  IRNode.ADD_REDUCTION_VF, "= 0",
                  "requires_strict_order", "= 0"},
        failOn = {"no_strict_order"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    static float testDefaultReductionFloatAdd(float[] a) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float val = a[i];
            sum = RelaxedMath.add(sum, val, /* default: no reordering */ 0);
        }
        return sum;
    }

    @Test
    @Arguments(setup = "setupFloat")
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "= 0",
                  IRNode.ADD_REDUCTION_VF, "= 0",
                  "no_strict_order",       "= 0"},
        failOn = {"requires_strict_order"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    static float testReorderedReductionFloatAdd(float[] a) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float val = a[i];
            sum = RelaxedMath.add(sum, val, /* allow reduction reordering */ 1);
        }
        return sum;
    }

    @Test
    @Arguments(setup = "setupFloat2")
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "> 0",
                  IRNode.MUL_VF,           "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0",
                  "requires_strict_order", "> 0"},
        failOn = {"no_strict_order"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    static float testStrictReductionFloatAddDotProduct(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float val = a[i] * b[i];
            sum = sum + val;
        }
        return sum;
    }

    @Test
    @Arguments(setup = "setupFloat2")
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "> 0",
                  IRNode.MUL_VF,           "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0",
                  "requires_strict_order", "> 0"},
        failOn = {"no_strict_order"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    static float testDefaultReductionFloatAddDotProduct(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float val = a[i] * b[i];
            sum = RelaxedMath.add(sum, val, /* default: no reordering */ 0);
        }
        return sum;
    }

    @Test
    @Arguments(setup = "setupFloat2")
    @IR(counts = {IRNode.LOAD_VECTOR_F,    "> 0",
                  IRNode.MUL_VF,           "> 0",
                  IRNode.ADD_REDUCTION_VF, "> 0",
                  "no_strict_order",       "> 0"},
        failOn = {"requires_strict_order"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        phase = CompilePhase.PRINT_IDEAL)
    static float testReorderedReductionFloatAddDotProduct(float[] a, float[] b) {
        float sum = 0;
        for (int i = 0; i < a.length; i++) {
            float val = a[i] * b[i];
            sum = RelaxedMath.add(sum, val, /* allow reduction reordering */ 1);
        }
        return sum;
    }
}
