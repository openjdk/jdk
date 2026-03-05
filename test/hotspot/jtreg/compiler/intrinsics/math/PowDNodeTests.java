/*
 * Copyright (c) 2026, IBM and/or its affiliates. All rights reserved.
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
 *
 */
package compiler.intrinsics.math;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import compiler.lib.ir_framework.*;

import java.util.Random;

/*
 * @test
 * @bug 8378713
 * @key randomness
 * @summary Math.pow(base, exp) should constant propagate
 * @library /test/lib /
 * @run driver compiler.intrinsics.math.PowDNodeTests
 */
public class PowDNodeTests {
    public static final Random RNG = Utils.getRandomInstance();

    public static final double B = RNG.nextDouble() * 1000.0d;
    public static final double E = RNG.nextDouble() * 1000.0d + 3.0d; // e >= 3 to avoid strength reduction code

    public static void main(String[] args) {
        TestFramework.run();

        testCorrectness();
    }

    // Test 1: pow(2.0, 10.0) -> 1024.0
    @Test
    @IR(failOn = {IRNode.POW_D})
    @IR(counts = {IRNode.CON_D, "1"})
    public static double constantLiteralFolding() {
        return Math.pow(2.0, 10.0);  // should fold to 1024.0
    }

    // Test 2: pow(final B, final E) -> B^E
    @Test
    @IR(failOn = {IRNode.POW_D})
    @IR(counts = {IRNode.CON_D, "1"})
    public static double constantStaticFolding() {
        return Math.pow(B, E);  // should fold to B^E
    }

    // Test 3: pow(b, 0.0) -> 1.0
    @Test
    @IR(failOn = {IRNode.POW_D})
    @IR(counts = {IRNode.CON_D, "1"})
    @Arguments(values = {Argument.RANDOM_EACH})
    public static double expZero(double b) {
        return Math.pow(b, 0.0);
    }

    // Test 4: pow(b, 1.0) -> b (identity)
    @Test
    @IR(failOn = {IRNode.POW_D, IRNode.CON_D})
    @Arguments(values = {Argument.RANDOM_EACH})
    public static double expOne(double b) {
        return Math.pow(b, 1.0);
    }

    // Test 5: pow(b, 2.0) -> b * b
    // More tests in TestPow2Opt.java
    @Test
    @IR(failOn = {IRNode.POW_D, IRNode.CON_D})
    @IR(counts = {IRNode.MUL_D, "1"})
    @Arguments(values = {Argument.RANDOM_EACH})
    public static double expTwo(double b) {
        return Math.pow(b, 2.0);
    }

    // Test 6: pow(b, 0.5) -> b < 0.0 ? pow(b, 0.5) : sqrt(b)
    // More tests in TestPow0Dot5Opt.java
    @Test
    @IR(counts = {IRNode.IF, "1"})
    @IR(counts = {IRNode.SQRT_D, "1"})
    @IR(counts = {IRNode.POW_D, "1"})
    @Arguments(values = {Argument.RANDOM_EACH})
    public static double expDot5(double b) {
        return Math.pow(b, 0.5);
    }

    // Test 7: non-constant exponent stays as call
    @Test
    @IR(counts = {IRNode.POW_D, "1"})
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public static double nonConstant(double b, double e) {
        return Math.pow(b, e);
    }

    // Test 8: late constant discovery on base (after loop opts)
    @Test
    @IR(counts = {IRNode.POW_D, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.POW_D})
    public static double lateBaseConstant() {
        double base = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                base = B;
            }
        }
        // After loop opts, base == B (constant), so pow(B, E) folds
        return Math.pow(base, E);
    }

    // Test 9: late constant discovery on exp (after loop opts)
    @Test
    @IR(counts = {IRNode.POW_D, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.POW_D})
    public static double lateExpConstant() {
        double exp = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                exp = E;
            }
        }
        // After loop opts, exp == E (constant), so pow(B, E) folds
        return Math.pow(B, exp);
    }

    // Test 10: late constant discoveries on both base and exp (after loop opts)
    @Test
    @IR(counts = {IRNode.POW_D, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.POW_D})
    public static double lateBothConstant() {
        double base = 0, exp = 0;
        for (int i = 0; i < 4; i++) {
            if ((i % 2) == 0) {
                base = B;
                exp = E;
            }
        }
        // After loop opts, base = B, exp == E, so pow(B, E) folds
        return Math.pow(base, exp);
    }

    private static void assertEQWithinOneUlp(double expected, double observed) {
        // Math.pow() requires result must be within 1 ulp of the respective magnitude
        double ulp = Math.max(Math.ulp(expected), Math.ulp(Math.ulp(observed)));
        if (Math.abs(expected - observed) > ulp) {
            throw new AssertionError(String.format(
                    "expect = %x, observed = %x, ulp = %x",
                    Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(observed), Double.doubleToRawLongBits(ulp)
            ));
        }
    }

    private static void testCorrectness() {
        // No need to warm up for intrinsics
        Asserts.assertEQ(1024.0d, constantLiteralFolding());

        double BE = StrictMath.pow(B, E);
        assertEQWithinOneUlp(BE, constantStaticFolding());
        assertEQWithinOneUlp(BE, lateExpConstant());
        assertEQWithinOneUlp(BE, lateBothConstant());

        double[] values = {
                Double.NEGATIVE_INFINITY, -42.0d, -1.0d, -0.0d, +0.0d, 0.5d, 2.0d, 123, Double.POSITIVE_INFINITY, Double.NaN,
                // some sufficiently large magnitudes
                (double) RNG.nextLong(Integer.MAX_VALUE, Long.MAX_VALUE), // >=  2^31
                (double) RNG.nextLong(Long.MIN_VALUE, Integer.MIN_VALUE), // <= -2^31
        };

        for (double b : values) {
            // Strength reduced, so we know the bits matches exactly
            Asserts.assertEQ(1.0d, expZero(b));
            Asserts.assertEQ(b, expOne(b));
            Asserts.assertEQ(b * b, expTwo(b));

            // Runtime calls, so make sure the result is within 1 ulp
            assertEQWithinOneUlp(StrictMath.pow(b, 0.5d), expDot5(b));

            for (double e : values) {
                assertEQWithinOneUlp(StrictMath.pow(b, e), nonConstant(b, e));
            }
        }
    }
}