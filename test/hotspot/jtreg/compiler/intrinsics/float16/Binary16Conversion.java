/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8289551 8302976
 * @summary Verify conversion between float and the binary16 format
 * @requires (vm.cpu.features ~= ".*avx512vl.*" | vm.cpu.features ~= ".*f16c.*") | os.arch=="aarch64"
 * @requires vm.compiler1.enabled & vm.compiler2.enabled
 * @requires vm.compMode != "Xcomp"
 * @comment default run
 * @run main Binary16Conversion
 * @comment C1 JIT compilation only:
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1 -XX:CompileCommand=compileonly,Binary16Conversion::test* Binary16Conversion
 * @comment C2 JIT compilation only:
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:CompileCommand=compileonly,Binary16Conversion::test* Binary16Conversion
 */

public class Binary16Conversion {

    public static final int FLOAT_SIGNIFICAND_WIDTH   = 24;

    public static void main(String... argv) {
        System.out.println("Start ...");
        short s = Float.floatToFloat16(0.0f); // Load Float class

        int errors = 0;
        errors += testBinary16RoundTrip();
        // Note that helper methods do sign-symmetric testing
        errors += testBinary16CardinalValues();
        errors += testRoundFloatToBinary16();
        errors += testRoundFloatToBinary16HalfWayCases();
        errors += testRoundFloatToBinary16FullBinade();
        errors += testAlternativeImplementation();

        if (errors > 0)
            throw new RuntimeException(errors + " errors");
    }

    /*
     * Put all 16-bit values through a conversion loop and make sure
     * the values are preserved (NaN bit patterns notwithstanding).
     */
    private static int testBinary16RoundTrip() {
        int errors = 0;
        for (int i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
            short s = (short)i;
            float f =  Float.float16ToFloat(s);
            short s2 = Float.floatToFloat16(f);

            if (!Binary16.equivalent(s, s2)) {
                errors++;
                System.out.println("Roundtrip failure on " +
                                   Integer.toHexString(0xFFFF & (int)s) +
                                   "\t got back " + Integer.toHexString(0xFFFF & (int)s2));
            }
        }
        return errors;
    }

    private static int testBinary16CardinalValues() {
        int errors = 0;
        // Encode short value for different binary16 cardinal values as an
        // integer-valued float.
        float[][] testCases = {
            {Binary16.POSITIVE_ZERO,         +0.0f},
            {Binary16.MIN_VALUE,              0x1.0p-24f},
            {Binary16.MAX_SUBNORMAL,          0x1.ff8p-15f},
            {Binary16.MIN_NORMAL,             0x1.0p-14f},
            {Binary16.ONE,                    1.0f},
            {Binary16.MAX_VALUE,              65504.0f},
            {Binary16.POSITIVE_INFINITY,      Float.POSITIVE_INFINITY},
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
    }

    private static int testRoundFloatToBinary16() {
        int errors = 0;

        float[][] testCases = {
            // Test all combinations of LSB, round, and sticky bit

            // LSB = 0, test combination of round and sticky
            {0x1.ff8000p-1f,       (short)0x3bfe},              // round = 0, sticky = 0
            {0x1.ff8010p-1f,       (short)0x3bfe},              // round = 0, sticky = 1
            {0x1.ffa000p-1f,       (short)0x3bfe},              // round = 1, sticky = 0
            {0x1.ffa010p-1f,       (short)0x3bff},              // round = 1, sticky = 1 => ++

            // LSB = 1, test combination of round and sticky
            {0x1.ffc000p-1f,       Binary16.ONE-1},             // round = 0, sticky = 0
            {0x1.ffc010p-1f,       Binary16.ONE-1},             // round = 0, sticky = 1
            {0x1.ffe000p-1f,       Binary16.ONE},               // round = 1, sticky = 0 => ++
            {0x1.ffe010p-1f,       Binary16.ONE},               // round = 1, sticky = 1 => ++

            // Test subnormal rounding
            // Largest subnormal binary16 0x03ff => 0x1.ff8p-15f; LSB = 1
            {0x1.ff8000p-15f,      Binary16.MAX_SUBNORMAL},     // round = 0, sticky = 0
            {0x1.ff8010p-15f,      Binary16.MAX_SUBNORMAL},     // round = 0, sticky = 1
            {0x1.ffc000p-15f,      Binary16.MIN_NORMAL},        // round = 1, sticky = 0 => ++
            {0x1.ffc010p-15f,      Binary16.MIN_NORMAL},        // round = 1, sticky = 1 => ++

            // Test rounding near binary16 MIN_VALUE
            // Smallest in magnitude subnormal binary16 value 0x0001 => 0x1.0p-24f
            // Half-way case,0x1.0p-25f, and smaller should round down to zero
            {0x1.fffffep-26f,      Binary16.POSITIVE_ZERO},     // nextDown in float
            {0x1.000000p-25f,      Binary16.POSITIVE_ZERO},
            {0x1.000002p-25f,      Binary16.MIN_VALUE},         // nextUp in float
            {0x1.100000p-25f,      Binary16.MIN_VALUE},

            // Test rounding near overflow threshold
            // Largest normal binary16 number 0x7bff => 0x1.ffcp15f; LSB = 1
            {0x1.ffc000p15f,       Binary16.MAX_VALUE},         // round = 0, sticky = 0
            {0x1.ffc010p15f,       Binary16.MAX_VALUE},         // round = 0, sticky = 1
            {0x1.ffe000p15f,       Binary16.POSITIVE_INFINITY}, // round = 1, sticky = 0 => ++
            {0x1.ffe010p15f,       Binary16.POSITIVE_INFINITY}, // round = 1, sticky = 1 => ++
        };

        for (var testCase : testCases) {
            errors += compareAndReportError(testCase[0],
                                            (short)testCase[1]);
        }
        return errors;
    }

    private static int testRoundFloatToBinary16HalfWayCases() {
        int errors = 0;

        // Test rounding of exact half-way cases between each pair of
        // finite exactly-representable binary16 numbers. Also test
        // rounding of half-way +/- ulp of the *float* value.
        // Additionally, test +/- float ulp of the endpoints. (Other
        // tests in this file make sure all short values round-trip so
        // that doesn't need to be tested here.)

        for (int i = Binary16.POSITIVE_ZERO; // 0x0000
             i    <= Binary16.MAX_VALUE;     // 0x7bff
             i += 2) {     // Check every even/odd pair once
            short lower = (short) i;
            short upper = (short)(i+1);

            float lowerFloat = Float.float16ToFloat(lower);
            float upperFloat = Float.float16ToFloat(upper);
            assert lowerFloat < upperFloat;

            float midway = (lowerFloat + upperFloat) * 0.5f; // Exact midpoint

            errors += compareAndReportError(Math.nextUp(lowerFloat),   lower);
            errors += compareAndReportError(Math.nextDown(midway),     lower);

            // Under round to nearest even, the midway point will
            // round *down* to the (even) lower endpoint.
            errors += compareAndReportError(              midway,      lower);

            errors += compareAndReportError(Math.nextUp(  midway),     upper);
            errors += compareAndReportError(Math.nextDown(upperFloat), upper);
        }

        // More testing around the overflow threshold
        // Binary16.ulp(Binary16.MAX_VALUE) == 32.0f; test around Binary16.MAX_VALUE + 1/2 ulp
        float binary16_MAX_VALUE = Float.float16ToFloat(Binary16.MAX_VALUE);
        float binary16_MAX_VALUE_halfUlp = binary16_MAX_VALUE + 16.0f;

        errors += compareAndReportError(Math.nextDown(binary16_MAX_VALUE), Binary16.MAX_VALUE);
        errors += compareAndReportError(              binary16_MAX_VALUE,  Binary16.MAX_VALUE);
        errors += compareAndReportError(Math.nextUp(  binary16_MAX_VALUE), Binary16.MAX_VALUE);

        // Binary16.MAX_VALUE is an "odd" value since its LSB = 1 so
        // the half-way value greater than Binary16.MAX_VALUE should
        // round up to the next even value, in this case Binary16.POSITIVE_INFINITY.
        errors += compareAndReportError(Math.nextDown(binary16_MAX_VALUE_halfUlp), Binary16.MAX_VALUE);
        errors += compareAndReportError(              binary16_MAX_VALUE_halfUlp,  Binary16.POSITIVE_INFINITY);
        errors += compareAndReportError(Math.nextUp(  binary16_MAX_VALUE_halfUlp), Binary16.POSITIVE_INFINITY);

        return errors;
    }

    private static int compareAndReportError(float input,
                                             short expected) {
        // Round to nearest even is sign symmetric
        return compareAndReportError0( input,                 expected) +
               compareAndReportError0(-input, Binary16.negate(expected));
    }

    private static int compareAndReportError0(float input,
                                              short expected) {
        short actual = Float.floatToFloat16(input);
        if (!Binary16.equivalent(actual, expected)) {
            System.out.println("Unexpected result of converting " +
                               Float.toHexString(input) +
                               " to short. Expected 0x" + Integer.toHexString(0xFFFF & expected) +
                               " got 0x" + Integer.toHexString(0xFFFF & actual));
            return 1;
            }
        return 0;
    }

    private static int compareAndReportError0(short input,
                                              float expected) {
        float actual = Float.float16ToFloat(input);
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

    private static int testRoundFloatToBinary16FullBinade() {
        int errors = 0;

        // For each float value between 1.0 and less than 2.0
        // (i.e. set of float values with an exponent of 0), convert
        // each value to binary16 and then convert that binary16 value
        // back to float.
        //
        // Any exponent could be used; the maximum exponent for normal
        // values would not exercise the full set of code paths since
        // there is an up-front check on values that would overflow,
        // which correspond to a ripple-carry of the significand that
        // bumps the exponent.
        short previous = (short)0;
        for (int i = Float.floatToIntBits(1.0f);
             i <= Float.floatToIntBits(Math.nextDown(2.0f));
             i++) {
            // (Could also express the loop control directly in terms
            // of floating-point operations, incrementing by ulp(1.0),
            // etc.)

            float f = Float.intBitsToFloat(i);
            short f_as_bin16 = Float.floatToFloat16(f);
            short f_as_bin16_down = (short)(f_as_bin16 - 1);
            short f_as_bin16_up   = (short)(f_as_bin16 + 1);

            // Across successive float values to convert to binary16,
            // the binary16 results should be semi-monotonic,
            // non-decreasing in this case.

            // Only positive binary16 values so can compare using integer operations
            if (f_as_bin16 < previous) {
                errors++;
                System.out.println("Semi-monotonicity violation observed on loat: " + Float.toHexString(f) + "/" + Integer.toHexString(i) + " " +
                                   Integer.toHexString(0xffff & f_as_bin16) + " previous: " + Integer.toHexString(0xffff & previous) + " f_as_bin16: " + Integer.toHexString(0xffff & f_as_bin16));
            }
            // previous = f_as_bin16;

            // If round-to-nearest was correctly done, when exactly
            // mapped back to float, f_as_bin16 should be at least as
            // close as either of its neighbors to the original value
            // of f.

            float f_prime_down = Float.float16ToFloat(f_as_bin16_down);
            float f_prime      = Float.float16ToFloat(f_as_bin16);
            float f_prime_up   = Float.float16ToFloat(f_as_bin16_up);

            previous = f_as_bin16;

            float f_prime_diff = Math.abs(f - f_prime);
            if (f_prime_diff == 0.0) {
                continue;
            }
            float f_prime_down_diff = Math.abs(f - f_prime_down);
            float f_prime_up_diff   = Math.abs(f - f_prime_up);

            if (f_prime_diff > f_prime_down_diff ||
                f_prime_diff > f_prime_up_diff) {
                errors++;
                System.out.println("Round-to-nearest violation on converting " +
                                   Float.toHexString(f) + "/" + Integer.toHexString(i) + " to binary16 and back: " +
                                   Integer.toHexString(0xffff & f_as_bin16) + " f_prime: " + Float.toHexString(f_prime));
            }
        }
        return errors;
    }

    private static int testAlternativeImplementation() {
        int errors = 0;

        // For exhaustive test of all float values use
        // for (long ell = Integer.MIN_VALUE; ell <= Integer.MAX_VALUE; ell++) {

        for (long ell   = Float.floatToIntBits(2.0f);
             ell       <= Float.floatToIntBits(4.0f);
             ell++) {
            float f = Float.intBitsToFloat((int)ell);
            short s1 = Float.floatToFloat16(f);
            short s2 = testAltFloatToFloat16(f);

            if (s1 != s2) {
                errors++;
                System.out.println("Different conversion of float value (" + f + "/" +
                                    Integer.toHexString(Float.floatToRawIntBits(f)) + "): " +
                                    Integer.toHexString(s1 & 0xffff) + "(" + s1 + ")" + " != " +
                                    Integer.toHexString(s2 & 0xffff) + "(" + s2 + ")");
            }
        }

        return errors;
    }

    /*
     * Rely on float operations to do rounding in both normal and
     * subnormal binary16 cases.
     */
    public static short testAltFloatToFloat16(float f) {
        int doppel = Float.floatToRawIntBits(f);
        short sign_bit = (short)((doppel & 0x8000_0000) >> 16);

        if (Float.isNaN(f)) {
            // Preserve sign and attempt to preserve significand bits
            return (short)(sign_bit
                    | 0x7c00 // max exponent + 1
                    // Preserve high order bit of float NaN in the
                    // binary16 result NaN (tenth bit); OR in remaining
                    // bits into lower 9 bits of binary 16 significand.
                    | (doppel & 0x007f_e000) >> 13 // 10 bits
                    | (doppel & 0x0000_1ff0) >> 4  //  9 bits
                    | (doppel & 0x0000_000f));     //  4 bits
        }

        float abs_f = Math.abs(f);

        // The overflow threshold is binary16 MAX_VALUE + 1/2 ulp
        if (abs_f >= (65504.0f + 16.0f) ) {
            return (short)(sign_bit | 0x7c00); // Positive or negative infinity
        } else {
            // Smallest magnitude nonzero representable binary16 value
            // is equal to 0x1.0p-24; half-way and smaller rounds to zero.
            if (abs_f <= 0x1.0p-25f) { // Covers float zeros and subnormals.
                return sign_bit; // Positive or negative zero
            }

            // Dealing with finite values in exponent range of
            // binary16 (when rounding is done, could still round up)
            int exp = Math.getExponent(f);
            assert -25 <= exp && exp <= 15;
            short signif_bits;

            if (exp <= -15) { // scale down to float subnormal range to do rounding
                // Use a float multiply to compute the correct
                // trailing significand bits for a binary16 subnormal.
                //
                // The exponent range of normalized binary16 subnormal
                // values is [-24, -15]. The exponent range of float
                // subnormals is [-149, -140]. Multiply abs_f down by
                // 2^(-125) -- since (-125 = -149 - (-24)) -- so that
                // the trailing bits of a subnormal float represent
                // the correct trailing bits of a binary16 subnormal.
                exp = -15; // Subnormal encoding using -E_max.
                float f_adjust = abs_f * 0x1.0p-125f;

                // In case the significand rounds up and has a carry
                // propagate all the way up, take the bottom 11 bits
                // rather than bottom 10 bits. Adding this value,
                // rather than OR'ing htis value, will cause the right
                // exponent adjustment.
                signif_bits = (short)(Float.floatToRawIntBits(f_adjust) & 0x07ff);
                return (short)(sign_bit | ( ((exp + 15) << 10) + signif_bits ) );
            } else {
                // Scale down to subnormal range to round off excess bits
                int scalingExp = -139 - exp;
                float scaled = Math.scalb(Math.scalb(f, scalingExp),
                                                       -scalingExp);
                exp = Math.getExponent(scaled);
                doppel = Float.floatToRawIntBits(scaled);

                signif_bits = (short)((doppel & 0x007f_e000) >>
                                      (FLOAT_SIGNIFICAND_WIDTH - 11));
                return (short)(sign_bit | ( ((exp + 15) << 10) | signif_bits ) );
            }
        }
    }

    public static class Binary16 {
        public static final short POSITIVE_INFINITY = (short)0x7c00;
        public static final short MAX_VALUE         = 0x7bff;
        public static final short ONE               = 0x3c00;
        public static final short MIN_NORMAL        = 0x0400;
        public static final short MAX_SUBNORMAL     = 0x03ff;
        public static final short MIN_VALUE         = 0x0001;
        public static final short POSITIVE_ZERO     = 0x0000;

        public static boolean isNaN(short binary16) {
            return ((binary16 & 0x7c00) == 0x7c00) // Max exponent and...
                && ((binary16 & 0x03ff) != 0 );    // significand nonzero.
        }

        public static short negate(short binary16) {
            return (short)(binary16 ^ 0x8000 ); // Flip only sign bit.
        }

        public static boolean equivalent(short bin16_1, short bin16_2) {
            return (bin16_1 == bin16_2) ||
                isNaN(bin16_1) && isNaN(bin16_2);
        }
    }
}
