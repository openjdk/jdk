/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.*;

/*
 * @test
 * @bug 8241374
 * @summary Test abs and absExact for Math and StrictMath
 */
public class AbsTests {
    private static int errors = 0;

    public static void main(String... args) {
        errors += testInRangeIntAbs();
        errors += testIntMinValue();
        errors += testInRangeLongAbs();
        errors += testLongMinValue();

        if (errors > 0) {
            throw new RuntimeException(errors + " errors found testing abs.");
        }
    }

    private static int testInRangeIntAbs() {
        int errors = 0;
        int[][] testCases  = {
            // Argument to abs, expected result
            {+0, 0},
            {+1, 1},
            {-1, 1},
            {-2, 2},
            {+2, 2},
            {-Integer.MAX_VALUE, Integer.MAX_VALUE},
            {+Integer.MAX_VALUE, Integer.MAX_VALUE}
        };

        for(var testCase : testCases) {
            errors += testIntAbs(Math::abs,      testCase[0], testCase[1]);
            errors += testIntAbs(Math::absExact, testCase[0], testCase[1]);
        }
        return errors;
    }

    private static int testIntMinValue() {
        int errors = 0;
        // Strange but true
        errors += testIntAbs(Math::abs, Integer.MIN_VALUE, Integer.MIN_VALUE);

        // Test exceptional behavior for absExact
        try {
            int result = Math.absExact(Integer.MIN_VALUE);
            System.err.printf("Bad return value %d from Math.absExact(MIN_VALUE)%n",
                              result);
            errors++;
        } catch (ArithmeticException ae) {
            ; // Expected
        }
        return errors;
    }

    private static int testIntAbs(IntUnaryOperator absFunc,
                           int argument, int expected) {
        int result = absFunc.applyAsInt(argument);
        if (result != expected) {
            System.err.printf("Unexpected int abs result %d for argument %d%n",
                                result, argument);
            return 1;
        } else {
            return 0;
        }
    }

    // --------------------------------------------------------------------

    private static long testInRangeLongAbs() {
        int errors = 0;
        long[][] testCases  = {
            // Argument to abs, expected result
            {+0L, 0L},
            {+1L, 1L},
            {-1L, 1L},
            {-2L, 2L},
            {+2L, 2L},
            {-Integer.MAX_VALUE, Integer.MAX_VALUE},
            {+Integer.MAX_VALUE, Integer.MAX_VALUE},
            { Integer.MIN_VALUE, -((long)Integer.MIN_VALUE)},
            {-Long.MAX_VALUE, Long.MAX_VALUE},
        };

        for(var testCase : testCases) {
            errors += testLongAbs(Math::abs,      testCase[0], testCase[1]);
            errors += testLongAbs(Math::absExact, testCase[0], testCase[1]);
        }
        return errors;
    }

    private static int testLongMinValue() {
        int errors = 0;
        // Strange but true
        errors += testLongAbs(Math::abs, Long.MIN_VALUE, Long.MIN_VALUE);

        // Test exceptional behavior for absExact
        try {
            long result = Math.absExact(Long.MIN_VALUE);
            System.err.printf("Bad return value %d from Math.absExact(MIN_VALUE)%n",
                              result);
            errors++;
        } catch (ArithmeticException ae) {
            ; // Expected
        }
        return errors;
    }

    private static int testLongAbs(LongUnaryOperator absFunc,
                           long argument, long expected) {
        long result = absFunc.applyAsLong(argument);
        if (result != expected) {
            System.err.printf("Unexpected long abs result %d for argument %d%n",
                                result, argument);
            return 1;
        } else {
            return 0;
        }
    }
}
