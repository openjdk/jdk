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

/*
 * @test
 * @bug 8301833
 * @build Tests
 * @build FdlibmTranslit
 * @build ExhaustingTests
 * @run main/manual ExhaustingTests
 * @summary Compare StrictMath.foo and FdlibmTranslit.foo for many inputs.
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

    /**
     * Test the unary (one-argument) StrictMath methods from FDLIBM.
     */
    private static long testUnaryMethods() {
        long failures = 0;
        UnaryTestCase[] testCases = {
         // new UnaryTestCase("sqrt",  FdlibmTranslit::sqrt,  StrictMath::sqrt),
            new UnaryTestCase("cbrt",  FdlibmTranslit::cbrt,  StrictMath::cbrt),

         // new UnaryTestCase("log",   FdlibmTranslit::log,   StrictMath::log),
            new UnaryTestCase("log10", FdlibmTranslit::log10, StrictMath::log10),
            new UnaryTestCase("log1p", FdlibmTranslit::log1p, StrictMath::log1p),

            new UnaryTestCase("exp",   FdlibmTranslit::exp,   StrictMath::exp),
            new UnaryTestCase("expm1", FdlibmTranslit::expm1, StrictMath::expm1),

         // new UnaryTestCase("sinh",  FdlibmTranslit::sinh,  StrictMath::sinh),
         // new UnaryTestCase("cosh",  FdlibmTranslit::cosh,  StrictMath::cosh),
         // new UnaryTestCase("tanh",  FdlibmTranslit::tanh,  StrictMath::tanh),

         // new UnaryTestCase("sin",   FdlibmTranslit::sin,   StrictMath::sin),
         // new UnaryTestCase("cos",   FdlibmTranslit::cos,   StrictMath::cos),
         // new UnaryTestCase("tan",   FdlibmTranslit::tan,   StrictMath::tan),

         // new UnaryTestCase("asin",  FdlibmTranslit::asin,  StrictMath::asin),
         // new UnaryTestCase("acos",  FdlibmTranslit::acos,  StrictMath::acos),
         // new UnaryTestCase("atan",  FdlibmTranslit::atan,  StrictMath::atan),
        };

        for (var testCase : testCases) {
            System.out.println("Testing " + testCase.name());
            System.out.flush();
            int i = Integer.MAX_VALUE; // overflow to Integer.MIN_VALUE at start of loop
            do {
                i++;
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
                                        DoubleUnaryOperator strictMath) {}

    /**
     * Test the binary (two-argument) StrictMath methods from FDLIBM.
     */
    private static long testBinaryMethods() {
        long failures = 0;
        // Note: pow does _not_ have translit a port
        BinaryTestCase[] testCases = {
            new BinaryTestCase("hypot", FdlibmTranslit::hypot, StrictMath::hypot),
         // new BinaryTestCase("atan2", FdlibmTranslit::atan2, StrictMath::atan2),
        };

        // to get 2^32 probes for a binary method, sample every 2^16 float values.
        for (var testCase : testCases) {
            System.out.println("Testing " + testCase.name());
            System.out.flush();

            for (long i = Integer.MIN_VALUE; i <= Integer.MAX_VALUE; i += 65_536) {
                for (long j = Integer.MIN_VALUE; j <= Integer.MAX_VALUE; j += 65_536) {
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
                                         DoubleBinaryOperator strictMath) {}
}
