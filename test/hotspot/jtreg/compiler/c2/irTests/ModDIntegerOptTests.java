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

import jdk.test.lib.Asserts;
import compiler.lib.generators.Generator;
import compiler.lib.ir_framework.*;

import static compiler.lib.generators.Generators.G;

/*
 * @test
 * @bug 8309636
 * @key randomness
 * @summary Test ModDNode optimization: drem with integer-valued operands converted to ModL
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class ModDIntegerOptTests {

    public static void main(String[] args) {
        TestFramework.run();
    }

    static void assertDremEQ(double actual, double expected, String msg) {
        if (Double.isNaN(expected)) {
            Asserts.assertTrue(Double.isNaN(actual), msg);
        } else {
            Asserts.assertEQ(actual, expected, msg);
        }
    }

    @Run(test = {"staticConvI2D", "staticConvF2D", "staticSumOfInts", "staticSubD", "staticFloatSum", "staticSubF",
                 "staticFloatWithConst", "staticFloatLargeConst", "staticHugeConst",
                 "staticConvL2D", "staticNegD", "staticNegF", "staticAbsD", "staticAbsF",
                 "staticDivisorOne", "staticNegativeDivisor", "staticLargeDivisor",
                 "staticDepth4", "staticDepth5",
                 "speculativeDouble", "nonIntegerDivisor",
                 "divisorZero", "divisorNaN", "divisorInf", "divisorLarge"})
    public void runner() {
        int a = G.ints().next();
        int b = G.ints().next();
        int c = G.ints().next();

        // Static path: all int-based tests in one loop
        int[] intValues = { a, b, c, 0, 1, -1, 42, -42, Integer.MAX_VALUE, Integer.MAX_VALUE - 1, Integer.MIN_VALUE, Integer.MIN_VALUE + 1 };
        for (int v : intValues) {
            assertDremEQ(staticConvI2D(v), (double)v % 42.0d, "staticConvI2D(" + v + ")");
            assertDremEQ(staticConvF2D(v), (double)(float)v % 42.0d, "staticConvF2D(" + v + ")");
            assertDremEQ(staticDivisorOne(v), (double)v % 1.0d, "staticDivisorOne(" + v + ")");
            assertDremEQ(staticNegativeDivisor(v), (double)v % -7.0d, "staticNegDivisor(" + v + ")");
            assertDremEQ(staticLargeDivisor(v), (double)v % 1000000007.0d, "staticLargeDivisor(" + v + ")");
            assertDremEQ(staticNegD(v), (-(double)v) % 42.0d, "staticNegD(" + v + ")");
            assertDremEQ(staticNegF(v), (-(float)v) % 42.0d, "staticNegF(" + v + ")");
            assertDremEQ(staticAbsD(v), Math.abs((double)v) % 42.0d, "staticAbsD(" + v + ")");
            assertDremEQ(staticAbsF(v), Math.abs((float)v) % 42.0d, "staticAbsF(" + v + ")");
            assertDremEQ(staticConvL2D(v), (double)((long)v) % 42.0d, "staticConvL2D(" + v + ")");
            assertDremEQ(staticFloatSum(v, v, v),
                ((float)v + (float)v + (float)v) % 42.0d, "staticFloatSum(" + v + ")");
            assertDremEQ(staticFloatWithConst(v), ((float)v + 1.0f) % 42.0d,
                "staticFloatWithConst(" + v + ")");
            assertDremEQ(staticFloatLargeConst(v), ((float)v + 3e10f) % 42.0d,
                "staticFloatLargeConst(" + v + ")");
            assertDremEQ(staticHugeConst(v), ((double)v + (1L << 62)) % 42.0d,
                "staticHugeConst(" + v + ")");
            // Also exercise int values through speculative path
            assertDremEQ(speculativeDouble((double)v), (double)v % 42.0d, "speculative(int " + v + ")");
        }
        assertDremEQ(staticSumOfInts(a, b, c),
            ((double)a + (double)b + (double)c) % 7.0d,
            "staticSumOfInts(" + a + "," + b + "," + c + ")");
        assertDremEQ(staticSumOfInts(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
            ((double)Integer.MAX_VALUE + (double)Integer.MAX_VALUE + (double)Integer.MAX_VALUE) % 7.0d,
            "staticSumOfInts(MAX,MAX,MAX)");
        assertDremEQ(staticSubD(a, b), ((double)a - (double)b) % 42.0d, "staticSubD(" + a + "," + b + ")");
        assertDremEQ(staticSubF(a, b), ((float)a - (float)b) % 42.0d, "staticSubF(" + a + "," + b + ")");

        // Depth boundary: compare against reference fmod computation
        double depth4ref = (double)a;
        for (int i = 0; i < 4; i++) { depth4ref += (double)((i % 2 == 0) ? b : a); }
        assertDremEQ(staticDepth4(a, b), depth4ref % 42.0d, "staticDepth4(" + a + "," + b + ")");

        double depth5ref = (double)a;
        for (int i = 0; i < 5; i++) { depth5ref += (double)((i % 2 == 0) ? b : a); }
        assertDremEQ(staticDepth5(a, b), depth5ref % 42.0d, "staticDepth5(" + a + "," + b + ")");

        // Speculative path: edge-case doubles including 2^31, 2^53 and 2^63 boundaries
        final double TWO_31 = (double)(1L << 31);
        final double TWO_53 = (double)(1L << 53);   // double mantissa precision boundary
        final double TWO_59 = (double)(1L << 59);   // constant magnitude bound (63 - D)
        final double TWO_63 = (double)(1L << 62) * 2.0; // ConvD2L saturation boundary

        double[] values = {
            Double.MIN_VALUE, Double.MIN_NORMAL, -42.0d, -1.0d, -0.0d, +0.0d,
            0.5d, 1.0d, 2.0d, 42.0d, 100.0d, 1e18d, Double.MAX_VALUE,
            Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN,
            TWO_31 - 1, TWO_31, TWO_31 + 1,
            -(TWO_31 - 1), -TWO_31, -(TWO_31 + 1),
            // 2^53: double mantissa precision, integers above may lose precision
            Math.nextDown(TWO_53), TWO_53, Math.nextUp(TWO_53),
            -Math.nextUp(TWO_53), -TWO_53, -Math.nextDown(TWO_53),
            // 2^59: constant magnitude bound in is_integral_fp
            Math.nextDown(TWO_59), TWO_59, Math.nextUp(TWO_59),
            -Math.nextUp(TWO_59), -TWO_59, -Math.nextDown(TWO_59),
            // 2^63: ConvD2L saturation boundary
            Math.nextDown(TWO_63), TWO_63, Math.nextUp(TWO_63),
            -Math.nextUp(TWO_63), -TWO_63, -Math.nextDown(TWO_63),
            G.doubles().next(), G.anyBitsDouble().next(),
        };

        for (double x : values) {
            assertDremEQ(speculativeDouble(x), x % 42.0d, "speculative(" + x + ")");
            assertDremEQ(nonIntegerDivisor(x), x % 31.5d, "nonIntegerDivisor(" + x + ")");
            // Divisor bail-out correctness: optimization doesn't fire, fmod handles it
            assertDremEQ(divisorZero(x), x % 0.0d, "divisorZero(" + x + ")");
            assertDremEQ(divisorNaN(x), x % Double.NaN, "divisorNaN(" + x + ")");
            assertDremEQ(divisorInf(x), x % Double.POSITIVE_INFINITY, "divisorInf(" + x + ")");
            assertDremEQ(divisorLarge(x), x % 9007199254740993.0d, "divisorLarge(" + x + ")");
        }
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {IRNode.CONV_D2L})
    @IR(counts = {IRNode.CONV_I2L, "1"})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticConvI2D(int a) {
        return (double)a % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticConvF2D(int a) {
        return (double)(float)a % 42.0d;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.MOD_D})
    @IR(counts = {IRNode.CONV_D2L, "1"})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticSumOfInts(int a, int b, int c) {
        return ((double)a + (double)b + (double)c) % 7.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticSubD(int a, int b) {
        return ((double)a - (double)b) % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticSubF(int a, int b) {
        return ((float)a - (float)b) % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticFloatWithConst(int a) {
        return ((float)a + 1.0f) % 42.0d;
    }

    // Large float constant (> 2^31 but < 2^53), now accepted
    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticFloatLargeConst(int a) {
        return ((float)a + 3e10f) % 42.0d;
    }

    // Constant at 2^62 (depth-1 bound, strict <) rejected, falls to speculative
    @Test
    @IR(counts = {".*CallLeaf.*drem.*", "1"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticHugeConst(int a) {
        return ((double)a + (1L << 62)) % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticDivisorOne(int a) {
        return (double)a % 1.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticNegativeDivisor(int a) {
        return (double)a % -7.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticLargeDivisor(int a) {
        return (double)a % 1000000007.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticFloatSum(int a, int b, int c) {
        return ((float)a + (float)b + (float)c) % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticNegD(int a) {
        return (-(double)a) % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticNegF(int a) {
        return (-(float)a) % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticAbsD(int a) {
        return Math.abs((double)a) % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticAbsF(int a) {
        return Math.abs((float)a) % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.MOD_D})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticConvL2D(int a) {
        return (double)((long)a) % 42.0d;
    }

    // Depth 4: innermost ConvI2D at the limit, accepted by is_integral_fp
    @Test
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticDepth4(int a, int b) {
        double v = (double)a;
        v = v + (double)b;
        v = v + (double)a;
        v = v + (double)b;
        v = v + (double)a;
        return v % 42.0d;
    }

    // Depth 5: exceeds limit, falls to speculative (drem on slow path)
    @Test
    @IR(counts = {".*CallLeaf.*drem.*", "1"}, phase = CompilePhase.BEFORE_MATCHING)
    public double staticDepth5(int a, int b) {
        double v = (double)a;
        v = v + (double)b;
        v = v + (double)a;
        v = v + (double)b;
        v = v + (double)a;
        v = v + (double)b;
        return v % 42.0d;
    }

    // Speculative: two guards (roundtrip check + saturation check)
    @Test
    @IR(counts = {IRNode.MOD_D, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(failOn = {IRNode.MOD_D})
    @IR(counts = {IRNode.CONV_D2L, "1", IRNode.CMP_D, "1", IRNode.IF, "2"})
    @IR(counts = {".*CallLeaf.*drem.*", "1"}, phase = CompilePhase.BEFORE_MATCHING)
    public double speculativeDouble(double x) {
        return x % 42.0d;
    }

    @Test
    @IR(counts = {IRNode.MOD_D, "1"}, phase = CompilePhase.AFTER_PARSING)
    @IR(counts = {IRNode.MOD_D, "1"})
    @IR(failOn = {IRNode.CONV_D2L, IRNode.IF})
    @IR(counts = {".*CallLeaf.*drem.*", "1"}, phase = CompilePhase.BEFORE_MATCHING)
    public double nonIntegerDivisor(double x) {
        return x % 31.5d;
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42})
    @IR(failOn = {".*CallLeaf.*drem.*"}, phase = CompilePhase.BEFORE_MATCHING)
    public double foldedAfterLoopOpts(int input) {
        int a = 77;
        int b = 0;
        do {
            a--;
            b++;
        } while (a > 0);
        double x = (double) input + (b == 77 ? 0.0 : 0.5);
        return x % 42.0d;
    }

    @Test
    @IR(failOn = {IRNode.CONV_D2L, IRNode.IF})
    public double divisorZero(double x) {
        return x % 0.0d;
    }

    @Test
    @IR(failOn = {IRNode.CONV_D2L, IRNode.IF})
    public double divisorNaN(double x) {
        return x % Double.NaN;
    }

    @Test
    @IR(failOn = {IRNode.CONV_D2L, IRNode.IF})
    public double divisorInf(double x) {
        return x % Double.POSITIVE_INFINITY;
    }

    @Test
    @IR(failOn = {IRNode.CONV_D2L, IRNode.IF})
    public double divisorLarge(double x) {
        return x % 9007199254740993.0d;
    }
}
