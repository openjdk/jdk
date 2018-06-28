/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.jtt.JTTTest;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This has been converted to JUnit from the jtreg test test/java/lang/Math/Log10Tests.java in JDK8.
 */
@RunWith(Parameterized.class)
public final class Math_log10 extends JTTTest {

    static final double LN_10 = StrictMath.log(10.0);

    @Parameter(value = 0) public double input;
    @Parameter(value = 1) public Number input2;
    @Parameter(value = 2) public Number result;
    @Parameter(value = 3) public Condition condition;
    public Double computedResult;

    enum Condition {
        EQUALS,
        THREE_ULPS,
        MONOTONICITY
    }

    public static double log10(double v) {
        return Math.log10(v);
    }

    public static boolean log10Monotonicity(double v, double v2) {
        return Math.log10(v) < Math.log(v2);
    }

    @Test
    public void testLog10() {
        if (condition == Condition.MONOTONICITY) {
            runTest("log10Monotonicity", input, input2.doubleValue());
        } else {
            runTest("log10", input);
        }
    }

    public static double strictLog10(double v) {
        return StrictMath.log10(v);
    }

    public static boolean strictLog10Monotonicity(double v, double v2) {
        return StrictMath.log10(v) < StrictMath.log(v2);
    }

    @Test
    public void testStrictLog10() {
        if (condition == Condition.MONOTONICITY) {
            runTest("strictLog10Monotonicity", input, input2.doubleValue());
        } else {
            runTest("strictLog10", input);
        }
    }

    @Before
    public void before() {
        computedResult = null;
    }

    private static boolean checkFor3ulps(double expected, double result) {
        return Math.abs(result - expected) / Math.ulp(expected) <= 3;
    }

    @Override
    protected void assertDeepEquals(Object expected, Object actual) {
        if (this.condition == Condition.THREE_ULPS) {
            double actualValue = ((Number) actual).doubleValue();
            assertTrue("differs by more than 3 ulps: " + result.doubleValue() + "," + actualValue, checkFor3ulps(result.doubleValue(), actualValue));
            if (computedResult != null && actualValue != computedResult) {
                /*
                 * This test detects difference in the actual result between the built in
                 * implementation and what Graal does. If it reaches this test then the value was
                 * within 3 ulps but differs in the exact amount.
                 *
                 * System.err.println("value for " + input + " is within 3 ulps but differs from
                 * computed value: " + computedResult + " " + actualValue);
                 */
            }
        } else {
            super.assertDeepEquals(expected, actual);
        }
    }

    @Override
    protected Result executeExpected(ResolvedJavaMethod method, Object receiver, Object... args) {
        Result actual = super.executeExpected(method, receiver, args);
        if (actual.returnValue instanceof Number) {
            computedResult = ((Number) actual.returnValue).doubleValue();
            assertDeepEquals(computedResult, actual.returnValue);
        }
        return actual;
    }

    static void addEqualityTest(List<Object[]> tests, double input, double expected) {
        tests.add(new Object[]{input, null, expected, Condition.EQUALS});
    }

    static void add3UlpTest(List<Object[]> tests, double input, double expected) {
        tests.add(new Object[]{input, null, expected, Condition.THREE_ULPS});
    }

    static void addMonotonicityTest(List<Object[]> tests, double input, double input2) {
        tests.add(new Object[]{input, input2, null, Condition.MONOTONICITY});
    }

    @Parameters(name = "{index}")
    public static Collection<Object[]> data() {
        List<Object[]> tests = new ArrayList<>();

        addEqualityTest(tests, Double.NaN, Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0x7FF0000000000001L), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0xFFF0000000000001L), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0x7FF8555555555555L), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0xFFF8555555555555L), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0xFFFFFFFFFFFFFFFFL), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0x7FFDeadBeef00000L), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0xFFFDeadBeef00000L), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0x7FFCafeBabe00000L), Double.NaN);
        addEqualityTest(tests, Double.longBitsToDouble(0xFFFCafeBabe00000L), Double.NaN);
        addEqualityTest(tests, Double.NEGATIVE_INFINITY, Double.NaN);
        addEqualityTest(tests, -8.0, Double.NaN);
        addEqualityTest(tests, -1.0, Double.NaN);
        addEqualityTest(tests, -Double.MIN_NORMAL, Double.NaN);
        addEqualityTest(tests, -Double.MIN_VALUE, Double.NaN);
        addEqualityTest(tests, -0.0, -Double.POSITIVE_INFINITY);
        addEqualityTest(tests, +0.0, -Double.POSITIVE_INFINITY);
        addEqualityTest(tests, +1.0, 0.0);
        addEqualityTest(tests, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

        // Test log10(10^n) == n for integer n; 10^n, n < 0 is not
        // exactly representable as a floating-point value -- up to
        // 10^22 can be represented exactly
        double testCase = 1.0;
        for (int i = 0; i < 23; i++) {
            addEqualityTest(tests, testCase, i);
            testCase *= 10.0;
        }

        // Test for gross inaccuracy by comparing to log; should be
        // within a few ulps of log(x)/log(10)
        Random rand = new java.util.Random(0L);
        for (int i = 0; i < 10000; i++) {
            double input = Double.longBitsToDouble(rand.nextLong());
            if (!Double.isFinite(input)) {
                continue; // avoid testing NaN and infinite values
            } else {
                input = Math.abs(input);

                double expected = StrictMath.log(input) / LN_10;
                if (!Double.isFinite(expected)) {
                    continue; // if log(input) overflowed, try again
                } else {
                    add3UlpTest(tests, input, expected);
                }
            }
        }

        double z = Double.NaN;
        // Test inputs greater than 1.0.
        double[] input = new double[40];
        int half = input.length / 2;
        // Initialize input to the 40 consecutive double values
        // "centered" at 1.0.
        double up = Double.NaN;
        double down = Double.NaN;
        for (int i = 0; i < half; i++) {
            if (i == 0) {
                input[half] = 1.0;
                up = Math.nextUp(1.0);
                down = Math.nextDown(1.0);
            } else {
                input[half + i] = up;
                input[half - i] = down;
                up = Math.nextUp(up);
                down = Math.nextDown(down);
            }
        }
        input[0] = Math.nextDown(input[1]);
        for (int i = 0; i < input.length; i++) {
            // Test accuracy.
            z = input[i] - 1.0;
            double expected = (z - (z * z) * 0.5) / LN_10;
            add3UlpTest(tests, input[i], expected);

            // Test monotonicity
            if (i > 0) {
                addMonotonicityTest(tests, input[i - 1], input[i]);
            }
        }

        return tests;
    }

}
