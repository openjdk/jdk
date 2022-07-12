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

// TODO: add sign-symmetric testing from positive test cases

public class SixteenBitFormats {
    public static void main(String... argv) {
        int errors = 0;
        errors += binary16RoundTrip();
        errors += binary16CardinalValues();
        errors += roundFloatToBinary16();
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
            float f = Float.binary16AsShortBitsToFloat(s);
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
            {Binary16.POSITIVE_INFINITY, Float.POSITIVE_INFINITY},
            {Binary16.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY},
            {Binary16.POSITIVE_ZERO,     +0.0f},
            {Binary16.NEGATIVE_ZERO,     -0.0f},
            {Binary16.MIN_VALUE,         0x1.0p-24f},
            {Binary16.MIN_NORMAL,        0x1.0p-14f},
            {Binary16.MAX_VALUE,         65504.0f},
            {Binary16.ONE,               1.0f},
            {Binary16.ONE,               1.0f},
            {Binary16.MAX_SUBNORMAL,     0x1.ff8p-15f},
        };

        // Check conversions in both directions

        // short -> float
        for (var testCase : testCases) {
            short input    = (short)testCase[0];
            float expected = testCase[1];
            float actual   = Float.binary16AsShortBitsToFloat(input);

            // TODO: extract into separate method
            if (Float.compare(actual, expected) != 0) {
                errors++;
                System.out.println("Unexpected result of converting 0x" +
                                   Integer.toHexString(0xFFFF & input) +
                                   " to float. Expected " + expected +
                                   " got " + actual);
            }
        }

        // float -> short
        for (var testCase : testCases) {
            float input    = testCase[1];
            short expected = (short)testCase[0];
            short actual   = Float.floatToBinary16AsShortBits(input);

            // TODO: extract into separate method
            if (Binary16.compare(actual, expected) != 0) {
                errors++;
                System.out.println("Unexpected result of converting " +
                                   Float.toHexString(input) +
                                   " to short. Expected " + expected +
                                   " got " + actual);
            }
        }

        return errors;
    };

    private static int roundFloatToBinary16() {
        int errors = 0;

        float[][] testCases = {
            // Test all combinations of LSB, round, and sticky bit

           // LSB = 0, test combination of round and sticky
            {0x1.ff8p-1f,                (short)0x3bfe}, // guard = 0, sticky = 0
            {0x1.ff801p-1f,              (short)0x3bfe}, // guard = 0, sticky = 1
            {0x1.ffap-1f,                (short)0x3bfe}, // guard = 1, sticky = 0
            {0x1.ffa01p-1f,              (short)0x3bff}, // guard = 1, sticky = 1 => ++

            // LSB = 1, test combination of round and sticky
            // (short)0x3bff is the largest binary16 less than one
            {0x1.ffcp-1f,                (short)0x3bff}, // guard = 0, sticky = 0
            {0x1.ffc01p-1f,              (short)0x3bff}, // guard = 0, sticky = 1
            {0x1.ffep-1f,                (short)0x3c00}, // guard = 1, sticky = 0 => ++
            {0x1.ffe01p-1f,              (short)0x3c00}, // guard = 1, sticky = 1 => ++

            // Test subnormal rounding
            // Largest subnormal binary16 0x03ff => 0x1.ff8p-15f
            // LSB = 1
            {0x1.ff8p-15f,               (short)0x03ff}, // guard = 0, sticky = 0
            {0x1.ff801p-15f,             (short)0x03ff}, // guard = 0, sticky = 1
            {0x1.ffcp-15f,               (short)0x0400}, // guard = 1, sticky = 1 => ++
            {0x1.ffc01p-15f,             (short)0x0400}, // guard = 1, sticky = 1 => ++
        };


        for (var testCase : testCases) {
            float input    = testCase[0];
            short expected = (short)testCase[1];
            short actual   = Float.floatToBinary16AsShortBits(input);

            System.out.println(input + "\t" +
                               Integer.toHexString(expected) + "\t" +
                               Integer.toHexString(actual));

            if (Binary16.compare(actual, expected) != 0) {
                errors++;
                System.out.println("Unexpected result of converting " +
                                   Float.toHexString(input) +
                                   " to short. Expected " + expected +
                                   " got " + actual);
            }


        }

        return errors;
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
                    return Integer.compare((int)bin16_1, (int) bin16_2);
                }
            }
        }
    }
}
