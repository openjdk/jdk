/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289551
 * @summary Verify conversion between float and 16-bit formats
 * @library ../Math
 * @build FloatConsts
 * @run main SixteenBitFormats
 */

import static java.lang.Float.*;

public class SixteenBitFormats {
    public static void main(String... argv) {
        int errors = 0;
        errors += binary16RoundTrip();
        // Note that helper methods do sign-symmetric testing
        errors += binary16CardinalValues();
        errors += roundFloatToBinary16();
        errors += roundFloatToBinary16HalfWayCases();

        if (errors > 0)
            throw new RuntimeException(errors + " errors");
    }

    /*
     * Put all 16-bit values through a conversion loop and make sure
     * the values are preserved. (NaN bit patterns notwithstanding.)
     */
    private static int binary16RoundTrip() {
        int errors = 0;
        for (int i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
            short s = (short)i;
            float f =  Float.binary16AsShortBitsToFloat(s);
            short s2 = Float.floatToBinary16AsShortBits(f);

            if (Binary16.compare(s, s2) != 0) {
                errors++;
                System.out.println("Roundtrip failure on " +
                                   Integer.toHexString((int)s) +
                                   "\t got back " + Integer.toHexString((int)s2));
            }
        }
        return errors;
    }

    private static int binary16CardinalValues() {
        int errors = 0;
        // Encode short value for different binary16 cardinal values as an
        // integer-valued float.
        float[][] testCases = {
            {Binary16.POSITIVE_INFINITY,      Float.POSITIVE_INFINITY},
            {Binary16.POSITIVE_ZERO,         +0.0f},
            {Binary16.MIN_VALUE,              0x1.0p-24f},
            {Binary16.MAX_SUBNORMAL,          0x1.ff8p-15f},
            {Binary16.MIN_NORMAL,             0x1.0p-14f},
            {Binary16.ONE,                    1.0f},
            {Binary16.MAX_VALUE,              65504.0f},
        };

        // Check conversions in both directions

        // short -> float
        for (var testCase : testCases) {
            errors += compareAndReportError((short)testCase[0],
                                            testCase[1]);
        }

        // float -> short
        for (var testCase : testCases) {
            errors += compareAndReportError(testCase[1],
                                            (short)testCase[0]);
        }

        return errors;
    };


    private static int roundFloatToBinary16() {
        int errors = 0;

        float[][] testCases = {
            // Test all combinations of LSB, round, and sticky bit

            // LSB = 0, test combination of round and sticky
            {0x1.ff8p-1f,                (short)0x3bfe}, // round = 0, sticky = 0
            {0x1.ff801p-1f,              (short)0x3bfe}, // round = 0, sticky = 1
            {0x1.ffap-1f,                (short)0x3bfe}, // round = 1, sticky = 0
            {0x1.ffa01p-1f,              (short)0x3bff}, // round = 1, sticky = 1 => ++

            // LSB = 1, test combination of round and sticky
            // (short)0x3bff is the largest binary16 less than one
            {0x1.ffcp-1f,                (short)0x3bff}, // round = 0, sticky = 0
            {0x1.ffc01p-1f,              (short)0x3bff}, // round = 0, sticky = 1
            {0x1.ffep-1f,                (short)0x3c00}, // round = 1, sticky = 0 => ++
            {0x1.ffe01p-1f,              (short)0x3c00}, // round = 1, sticky = 1 => ++

            // Test subnormal rounding
            // Largest subnormal binary16 0x03ff => 0x1.ff8p-15f; LSB = 1
            {0x1.ff8p-15f,               (short)0x03ff}, // round = 0, sticky = 0
            {0x1.ff801p-15f,             (short)0x03ff}, // round = 0, sticky = 1
            {0x1.ffcp-15f,               (short)0x0400}, // round = 1, sticky = 0 => ++
            {0x1.ffc01p-15f,             (short)0x0400}, // round = 1, sticky = 1 => ++

            // Test rounding near binary16 MIN_VALUE
            // Smallest in magnitude subnormal binary16 value 0x0001 => 0x1.0p-24f
            // Half-way case and small should round down to zero
            {0x1.fffffep-26f,             (short)0x0000}, // nextDown in float
            {0x1.0p-25f,                  (short)0x0000},
            {0x1.000002p-25f,             (short)0x0001}, // nextUp in float
            {0x1.1p-25f,                  (short)0x0001},

            // Test rounding near overflow threshold
            // Largest normal binary16 number 0x7bff => 0x1.ffcp15f; LSB = 1
            {0x1.ffcp15f,                (short)0x7bff}, // round = 0, sticky = 0
            {0x1.ffc01p15f,              (short)0x7bff}, // round = 0, sticky = 1
            {0x1.ffep15f,                (short)0x7c00}, // round = 1, sticky = 0 => ++
            {0x1.ffe01p15f,              (short)0x7c00}, // round = 1, sticky = 1 => ++
        };

        for (var testCase : testCases) {
            errors += compareAndReportError(testCase[0],
                                            (short)testCase[1]);
        }
        return errors;
    }

    private static int roundFloatToBinary16HalfWayCases() {
        int errors = 0;

        // Test rounding of exact half-way cases between each pair of
        // finite exactly-representable binary16 numbers. Also test
        // rounding of half-way +/- ulp of the *float* value.
        // Additionally, test +/- float ulp of the endpoints. (Other
        // tests in this file make sure all short values round-trip so
        // that doesn't need to be tested here.)

        for (int i = 0;   // binary16 0.0
             i < 0x7bff ; // Largest normal binary16 value
             i += 2) {    // Check every even/odd pair once
            short lower = (short)i;
            short upper = (short)(i+1);

            float lowerFloat = Float.binary16AsShortBitsToFloat(lower);
            float upperFloat = Float.binary16AsShortBitsToFloat(upper);
            assert lowerFloat < upperFloat;

            float midway = (lowerFloat + upperFloat) * 0.5f; // Exact midpoint

            errors += compareAndReportError(Math.nextUp(lowerFloat),   lower);
            errors += compareAndReportError(Math.nextDown(midway),     lower);

            // Under round to nearest even, the midway point will
            // round *down* to the (even) lower endpoint.
            errors += compareAndReportError(midway, lower);

            errors += compareAndReportError(Math.nextUp(midway),       upper);
            errors += compareAndReportError(Math.nextDown(upperFloat), upper);
        }

        return errors;
    }

    private static int compareAndReportError0(float input,
                                              short expected) {
        short actual = Float.floatToBinary16AsShortBits(input);
        if (Binary16.compare(actual, expected) != 0) {
            System.out.println("Unexpected result of converting " +
                               Float.toHexString(input) +
                               " to short. Expected 0x" + Integer.toHexString(expected) +
                               " got 0x" + Integer.toHexString(actual));
            return 1;
            }
        return 0;
    }

    private static int compareAndReportError(float input,
                                             short expected) {
        // Round to nearest even is sign symmetric
        return compareAndReportError0( input, expected) +
               compareAndReportError0(-input, Binary16.negate(expected));
    }

    private static int compareAndReportError0(short input,
                                              float expected) {
        float actual = Float.binary16AsShortBitsToFloat(input);
        if (Float.compare(actual, expected) != 0) {
            System.out.println("Unexpected result of converting " +
                               Integer.toHexString(input & 0xFFFF) +
                               " to float. Expected " + Float.toHexString(expected) +
                               " got " + Float.toHexString(actual));
            return 1;
            }
        return 0;
    }

    private static int compareAndReportError(short input,
                                             float expected) {
        // Round to nearest even is sign symmetric
        return compareAndReportError0(                input,   expected) +
               compareAndReportError0(Binary16.negate(input), -expected);
    }

    public static class Binary16 {
        public static final short POSITIVE_INFINITY = (short)0x7c00;
        public static final short NEGATIVE_INFINITY = (short)0xfc00;
        public static final short MAX_VALUE = 0x7bff;
        public static final short ONE = 0x3c00;
        public static final short MIN_NORMAL = 0x0400;
        public static final short MIN_VALUE = 0x0001;
        public static final short NEGATIVE_ZERO = (short)0x8000;
        public static final short POSITIVE_ZERO = 0x0000;
        public static final short MAX_SUBNORMAL = (short)0x03ff;

        public static boolean isNaN(short binary16) {
            return ((binary16 & 0x7c00) == 0x7c00) // Max exponent and...
                && ((binary16 & 0x03ff) != 0 );    // significand nonzero.
        }

        public static short negate(short binary16) {
            return (short)(((binary16 & 0x8000) ^ 0x8000) | // Isolate and flip sign bit
                           (binary16 & 0x7fff));
        }

        public static int compare(short bin16_1, short bin16_2) {
            if (bin16_1 == bin16_2) {
                return 0;
            } else {
                if (isNaN(bin16_1)) {
                    return isNaN(bin16_2) ? 0 : 1;
                } else {
                    if (isNaN(bin16_2)) {
                        return -1;
                    }
                    return Integer.compare((int)(bin16_1 & 0xFFFF),
                                           (int)(bin16_2 & 0xFFFF));
                }
            }
        }
    }
}
