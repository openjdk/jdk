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
package compiler.c2.irTests;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8375633
 * @key randomness
 * @summary Test that ConvD2F::Ideal optimization is not missed with incremental inlining.
 *          AlwaysIncrementalInline is not required but deterministically defers even
 *          small methods, making this test reliable.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class ConvD2FIdealizationStress {

    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.runWithFlags("-XX:-TieredCompilation",
                                   "-XX:+IgnoreUnrecognizedVMOptions",
                                   "-XX:+AlwaysIncrementalInline",
                                   "-XX:VerifyIterativeGVN=1110");
    }

    // Deferred by AlwaysIncrementalInline; ConvF2D appears only after inlining.
    static double toDouble(float x) {
        return (double) x;
    }

    // ConvD2F(SqrtD(ConvF2D(x))) => SqrtF(x)
    // Math.sqrt (intrinsic) is expanded at parse time; toDouble is deferred.
    @Test
    @IR(counts = {IRNode.SQRT_F, ">=1"},
        failOn = {IRNode.CONV_D2F, IRNode.SQRT_D, IRNode.CONV_F2D})
    public static float testSqrtConversion(float x) {
        return (float) Math.sqrt(toDouble(x));
    }

    @Run(test = "testSqrtConversion")
    public void runSqrtConversion() {
        float input = RANDOM.nextFloat();
        checkSqrtConversion(input, testSqrtConversion(input));
    }

    @DontCompile
    public void checkSqrtConversion(float input, float result) {
        Asserts.assertEQ((float) Math.sqrt(input), result);
    }
}
