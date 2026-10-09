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

/**
 * @test
 * @bug 8393682
 * @summary Tests of BigDecimal.{toBigInteger, toBigIntegerExact}
 */

import java.math.*;

public class ToBigIntegerTests {
    public static void main(String... args) {
        int failures = 0;

        failures += zeroTestCases();
        failures += toBigIntegerTests();
        failures += toBigIntegerExactTests();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures +
                                       " failures while testing toBigInteger/toBigIntegerExact.");
        }
    }

    private static record TbiTestCase(BigDecimal input, BigInteger expected) {
        static TbiTestCase of(BigDecimal input, BigInteger expected) {
            return new TbiTestCase(input, expected);
        }

        static TbiTestCase of(String input, BigInteger expected) {
            return new TbiTestCase(new BigDecimal(input), expected);
        }
    }

    private static TbiTestCase zeroTestCases[] = {
        TbiTestCase.of(new BigDecimal(BigInteger.ZERO,
                                      Integer.MIN_VALUE),   BigInteger.ZERO),
        TbiTestCase.of(new BigDecimal(BigInteger.ZERO, -1), BigInteger.ZERO),
        TbiTestCase.of(BigDecimal.ZERO,                     BigInteger.ZERO),
        TbiTestCase.of(new BigDecimal(BigInteger.ZERO, +1), BigInteger.ZERO),
        TbiTestCase.of(new BigDecimal(BigInteger.ZERO,
                                      Integer.MAX_VALUE),   BigInteger.ZERO),
    };

    private static int zeroTestCases() {
        int failures = 0;

        for(var zeroTestCase : zeroTestCases) {
            BigDecimal input    = zeroTestCase.input;
            BigInteger expected = zeroTestCase.expected;

            if (!input.toBigInteger().equals(expected)) {
                failures++;
                System.out.println(input + " did not have expected toBigInteger result of " + expected);
            }

            if (!input.toBigIntegerExact().equals(expected)) {
                failures++;
                System.out.println(input + " did not have toBigIntegerExact result of " + expected);
            }
        }

        return failures;
    }

    private static TbiTestCase tbiTestCases[] = {
        // nonzero constant values in the API
        TbiTestCase.of(BigDecimal.ONE, BigInteger.ONE),
        TbiTestCase.of(BigDecimal.TWO, BigInteger.TWO),
        TbiTestCase.of(BigDecimal.TEN, BigInteger.TEN),

        // Negative scale values with (precision == 1)
        TbiTestCase.of( "1e1", BigInteger.TEN),
        TbiTestCase.of( "1e2", BigInteger.valueOf(      100)),
        TbiTestCase.of( "1e3", BigInteger.valueOf(    1_000)),
        TbiTestCase.of( "1e6", BigInteger.valueOf(1_000_000)),

        // Scale == 0 values, varying precision
        TbiTestCase.of(BigDecimal.valueOf(  12, 0), BigInteger.valueOf(  12)),
        TbiTestCase.of(BigDecimal.valueOf( 123, 0), BigInteger.valueOf( 123)),
        TbiTestCase.of(BigDecimal.valueOf(1234, 0), BigInteger.valueOf(1234)),

        // Positive scale, varying precision
        TbiTestCase.of(BigDecimal.valueOf(  12, 1), BigInteger.valueOf(  1)),
        TbiTestCase.of(BigDecimal.valueOf( 123, 1), BigInteger.valueOf( 12)),
        TbiTestCase.of(BigDecimal.valueOf(1234, 1), BigInteger.valueOf(123)),

        TbiTestCase.of(BigDecimal.valueOf(  12, 2), BigInteger.valueOf( 0)),
        TbiTestCase.of(BigDecimal.valueOf( 123, 2), BigInteger.valueOf( 1)),
        TbiTestCase.of(BigDecimal.valueOf(1234, 2), BigInteger.valueOf(12)),

        TbiTestCase.of(BigDecimal.valueOf(  12, 3), BigInteger.valueOf(0)),
        TbiTestCase.of(BigDecimal.valueOf( 123, 3), BigInteger.valueOf(0)),
        TbiTestCase.of(BigDecimal.valueOf(1234, 3), BigInteger.valueOf(1)),
        
        // More fractional values
        TbiTestCase.of("-0.5", BigInteger.ZERO),
        TbiTestCase.of( "0.5", BigInteger.ZERO),

        TbiTestCase.of("-0.5001", BigInteger.ZERO),
        TbiTestCase.of( "0.5001", BigInteger.ZERO),

        TbiTestCase.of("-0.9999", BigInteger.ZERO),
        TbiTestCase.of( "0.9999", BigInteger.ZERO),

        TbiTestCase.of( "0.0001", BigInteger.ZERO),
        TbiTestCase.of("-0.0001", BigInteger.ZERO),

        TbiTestCase.of( "1e-15", BigInteger.ZERO),
        TbiTestCase.of("-1e-15", BigInteger.ZERO),

        // Test rounding to 1
        TbiTestCase.of("-1.5", BigInteger.ONE.negate()),
        TbiTestCase.of( "1.5", BigInteger.ONE),

        TbiTestCase.of("-1.5001", BigInteger.ONE.negate()),
        TbiTestCase.of( "1.5001", BigInteger.ONE),

        TbiTestCase.of("-1.9999", BigInteger.ONE.negate()),
        TbiTestCase.of( "1.9999", BigInteger.ONE),

        TbiTestCase.of( "1.0001", BigInteger.ONE),
        TbiTestCase.of("-1.0001", BigInteger.ONE.negate()),
    };

    private static int toBigIntegerTests() {
        int failures = 0;

        for(var tbiTestCase : tbiTestCases) {
            BigDecimal input    = tbiTestCase.input;
            BigInteger expected = tbiTestCase.expected;

            if (!input.toBigInteger().equals(expected)) {
                failures++;
                System.out.println(input + " did not have expected toBigInteger of result of " + expected);
            }
        }

        return failures;
    }

    private static TbiTestCase tbiExactTestCases[] = {
        // nonzero constant value in the API
        TbiTestCase.of(BigDecimal.ONE, BigInteger.ONE),
        TbiTestCase.of(BigDecimal.TWO, BigInteger.TWO),
        TbiTestCase.of(BigDecimal.TEN, BigInteger.TEN),

        // Negative scale values with (precision == 1)
        TbiTestCase.of( "1e1", BigInteger.TEN),
        TbiTestCase.of( "1e2", BigInteger.valueOf(      100)),
        TbiTestCase.of( "1e3", BigInteger.valueOf(    1_000)),
        TbiTestCase.of( "1e6", BigInteger.valueOf(1_000_000)),

        // Scale == 0 values, varying precision
        TbiTestCase.of(BigDecimal.valueOf(  12, 0), BigInteger.valueOf(  12)),
        TbiTestCase.of(BigDecimal.valueOf( 123, 0), BigInteger.valueOf( 123)),
        TbiTestCase.of(BigDecimal.valueOf(1234, 0), BigInteger.valueOf(1234)),

        // Positive scale, varying precision
        TbiTestCase.of(BigDecimal.valueOf(  12, 1), null),
        TbiTestCase.of(BigDecimal.valueOf( 123, 1), null),
        TbiTestCase.of(BigDecimal.valueOf(1234, 1), null),

        TbiTestCase.of(BigDecimal.valueOf(  12, 2), null),
        TbiTestCase.of(BigDecimal.valueOf( 123, 2), null),
        TbiTestCase.of(BigDecimal.valueOf(1234, 2), null),

        TbiTestCase.of(BigDecimal.valueOf(  12, 3), null),
        TbiTestCase.of(BigDecimal.valueOf( 123, 3), null),
        TbiTestCase.of(BigDecimal.valueOf(1234, 3), null),
        
        // More fractional values
        TbiTestCase.of("-0.9999", null),
        TbiTestCase.of( "0.9999", null),

        TbiTestCase.of( "0.0001", null),
        TbiTestCase.of("-0.0001", null),


        TbiTestCase.of( "1e-15", null),
        TbiTestCase.of("-1e-15", null),
    };

    private static int toBigIntegerExactTests() {
        int failures = 0;

        for(var tbiTestCase : tbiExactTestCases) {
            BigDecimal input    = tbiTestCase.input;
            BigInteger expected = tbiTestCase.expected;
            BigInteger result = null;

            if (expected == null) { // ArithmeticException expected
                try {
                    result = input.toBigIntegerExact();
                    failures++;
                    System.out.println("Got unexpected non-exceptionsl result of toBigIntegerExact on " + input);
                } catch(ArithmeticException ae) {
                    ; // Expected
                }
            } else {
                try {
                    result = input.toBigIntegerExact();
                    if (!result.equals(expected)) {
                        failures++;
                        System.out.println(input + " did not have expected toBigIntegerExact of " + expected);
                    }
                } catch(ArithmeticException ae) {
                    failures++;
                    System.out.println("Unexpected exception computing toBigIntegerExact of " + input);
                }
            }
        }
        return failures;
    }
}
