/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8301833 8302026 8301444 8302028 8302040 8302027 8304028
 * @build Tests
 * @build FdlibmTranslit
 * @build ExhaustingTests
 * @run main ExhaustingTests
 * @summary Compare StrictMath.foo and FdlibmTranslit.foo for many inputs.
 */

/*
 * Note on usage: for more exhaustive testing to help validate changes
 * to StrictMath, the DEFAULT_SHIFT setting should be set to 0. This
 * will test all float values against the unary methods. Running all
 * the float values for a single method takes on the order of a minute
 * or two. The default setting is a shift of 10, meaning every 1024th
 * float value is tested and the overall test runs within the typical
 * time expectations of a tier 1 test.
 */

import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleUnaryOperator;

public class ExhaustingTests {
    public static void main(String... args) {
        long failures = 0;

        failures += testUnaryMethods();
        failures += testBinaryMethods();

        if (failures > 0) {
            System.err.println("Comparing StrictMath and FdlibmTranslit"
                               + " incurred " + failures + " failures.");
            throw new RuntimeException();
        }
    }

    private static final int DEFAULT_SHIFT = 10;

    /**
     * Test the unary (one-argument) StrictMath methods from FDLIBM.
     */
    private static long testUnaryMethods() {
        long failures = 0;
        UnaryTestCase[] testCases = {
            // Since sqrt is correctly rounded and thus for each input
            // there is one well-defined correct result, additional
            // comparison of the transliteration sqrt or StrictMath
            // sqrt could be made against Math::sqrt.
            new UnaryTestCase("sqrt",  FdlibmTranslit::sqrt,  StrictMath::sqrt,  DEFAULT_SHIFT),
            new UnaryTestCase("cbrt",  FdlibmTranslit::cbrt,  StrictMath::cbrt,  DEFAULT_SHIFT),

            new UnaryTestCase("log",   FdlibmTranslit::log,   StrictMath::log,   DEFAULT_SHIFT),
            new UnaryTestCase("log10", FdlibmTranslit::log10, StrictMath::log10, DEFAULT_SHIFT),
            new UnaryTestCase("log1p", FdlibmTranslit::log1p, StrictMath::log1p, DEFAULT_SHIFT),

            new UnaryTestCase("exp",   FdlibmTranslit::exp,   StrictMath::exp,   DEFAULT_SHIFT),
            new UnaryTestCase("expm1", FdlibmTranslit::expm1, StrictMath::expm1, DEFAULT_SHIFT),

            new UnaryTestCase("sinh",  FdlibmTranslit::sinh,  StrictMath::sinh,  DEFAULT_SHIFT),
            new UnaryTestCase("cosh",  FdlibmTranslit::cosh,  StrictMath::cosh,  DEFAULT_SHIFT),
            new UnaryTestCase("tanh",  FdlibmTranslit::tanh,  StrictMath::tanh,  DEFAULT_SHIFT),

            new UnaryTestCase("sin",   FdlibmTranslit::sin,   StrictMath::sin,   DEFAULT_SHIFT),
            new UnaryTestCase("cos",   FdlibmTranslit::cos,   StrictMath::cos,   DEFAULT_SHIFT),
            new UnaryTestCase("tan",   FdlibmTranslit::tan,   StrictMath::tan,   DEFAULT_SHIFT),

            new UnaryTestCase("asin",  FdlibmTranslit::asin,  StrictMath::asin,  DEFAULT_SHIFT),
            new UnaryTestCase("acos",  FdlibmTranslit::acos,  StrictMath::acos,  DEFAULT_SHIFT),
            new UnaryTestCase("atan",  FdlibmTranslit::atan,  StrictMath::atan,  DEFAULT_SHIFT),

            new UnaryTestCase("asinh", FdlibmTranslit::asinh, StrictMath::asinh, DEFAULT_SHIFT),
            new UnaryTestCase("acosh", FdlibmTranslit::acosh, StrictMath::acosh, DEFAULT_SHIFT),
        };

        for (var testCase : testCases) {
            System.out.println("Testing " + testCase.name());
            System.out.flush();
            int i = Integer.MAX_VALUE; // overflow to Integer.MIN_VALUE at start of loop
            int increment = 1 << testCase.shiftDistance;
            do {
                i += increment;
                double input = (double)Float.intBitsToFloat(i);
                failures += Tests.test(testCase.name(),
                                       input,
                                       testCase.strictMath,
                                       testCase.translit.applyAsDouble(input));
            } while (i != Integer.MAX_VALUE);
        }
        return failures;
    }

    private static record UnaryTestCase(String name,
                                        DoubleUnaryOperator translit,
                                        DoubleUnaryOperator strictMath,
                                        int shiftDistance) {
        UnaryTestCase {
            if (shiftDistance < 0 || shiftDistance >= 31) {
                throw new IllegalArgumentException("Shift out of range");
            }
        }
    }

    /**
     * Test the binary (two-argument) StrictMath methods from FDLIBM.
     */
    private static long testBinaryMethods() {
        long failures = 0;
        // Note: pow does _not_ have a transliteration port.

        // Shift of 16 for a binary method gives comparable running
        // time to exhaustive testing of a unary method (testing every
        // 2^16 floating point values over two arguments is 2^32
        // probes).
        BinaryTestCase[] testCases = {
            new BinaryTestCase("hypot", FdlibmTranslit::hypot, StrictMath::hypot, 20, 20),
            new BinaryTestCase("atan2", FdlibmTranslit::atan2, StrictMath::atan2, 20, 20),
            new BinaryTestCase("IEEEremainder", FdlibmTranslit::IEEEremainder, StrictMath::IEEEremainder, 20, 20),
        };

        for (var testCase : testCases) {
            System.out.println("Testing " + testCase.name());
            System.out.flush();

            int iIncrement = 1 << testCase.xShift;
            int jIncrement = 1 << testCase.yShift;

            for (long i = Integer.MIN_VALUE; i <= Integer.MAX_VALUE; i += iIncrement) {
                for (long j = Integer.MIN_VALUE; j <= Integer.MAX_VALUE; j += jIncrement) {
                    double input1 = (double)Float.intBitsToFloat((int)i);
                    double input2 = (double)Float.intBitsToFloat((int)j);
                    failures += Tests.test(testCase.name(),
                                           input1, input2,
                                           testCase.strictMath,
                                           testCase.translit.applyAsDouble(input1, input2));
                }
            }
        }
        return failures;
    }

    private static record BinaryTestCase(String name,
                                         DoubleBinaryOperator translit,
                                         DoubleBinaryOperator strictMath,
                                         int xShift,
                                         int yShift) {
        BinaryTestCase {
            if (xShift < 0 || xShift >= 31 ||
                yShift < 0 || yShift >= 31 ) {
                throw new IllegalArgumentException("Shift out of range");
            }
        }
    }
}
