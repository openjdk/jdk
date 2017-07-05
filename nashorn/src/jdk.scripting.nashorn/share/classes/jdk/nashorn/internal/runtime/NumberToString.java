/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.runtime;

import java.math.BigInteger;

/**
 * JavaScript number to string conversion, refinement of sun.misc.FloatingDecimal.
 */
public final class NumberToString {
    /** Is not a number flag */
    private final boolean isNaN;

    /** Is a negative number flag. */
    private boolean isNegative;

    /** Decimal exponent value (for E notation.) */
    private int decimalExponent;

    /** Actual digits. */
    private char digits[];

    /** Number of digits to use. (nDigits <= digits.length). */
    private int nDigits;

    /*
     * IEEE-754 constants.
     */

    //private static final long   signMask           = 0x8000000000000000L;
    private static final int    expMask            = 0x7FF;
    private static final long   fractMask          = 0x000F_FFFF_FFFF_FFFFL;
    private static final int    expShift           = 52;
    private static final int    expBias            = 1_023;
    private static final long   fractHOB           = (1L << expShift);
    private static final long   expOne             = ((long)expBias) << expShift;
    private static final int    maxSmallBinExp     = 62;
    private static final int    minSmallBinExp     = -(63 / 3);

    /** Powers of 5 fitting a long. */
    private static final long powersOf5[] = {
        1L,
        5L,
        5L * 5,
        5L * 5 * 5,
        5L * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5,
        5L * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5 * 5
    };

    // Approximately ceil(log2(longPowers5[i])).
    private static final int nBitsPowerOf5[] = {
        0,
        3,
        5,
        7,
        10,
        12,
        14,
        17,
        19,
        21,
        24,
        26,
        28,
        31,
        33,
        35,
        38,
        40,
        42,
        45,
        47,
        49,
        52,
        54,
        56,
        59,
        61
    };

    /** Digits used for infinity result. */
    private static final char infinityDigits[]   = { 'I', 'n', 'f', 'i', 'n', 'i', 't', 'y' };

    /** Digits used for NaN result. */
    private static final char nanDigits[]        = { 'N', 'a', 'N' };

    /** Zeros used to pad result. */
    private static final char zeroes[]           = { '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0' };


    /**
     * Convert a number into a JavaScript string.
     * @param value Double to convert.
     * @return JavaScript formated number.
     */
    public static String stringFor(final double value) {
        return new NumberToString(value).toString();
    }

    /*
     * Constructor.
     */

    private NumberToString(final double value) {
        // Double as bits.
        long bits = Double.doubleToLongBits(value);

        // Get upper word.
        final int upper = (int)(bits >> 32);

        // Detect sign.
        isNegative = upper < 0;

        // Extract exponent.
        int exponent = (upper >> (expShift - 32)) & expMask;

        // Clear sign and exponent.
        bits &= fractMask;

        // Detect NaN.
        if (exponent == expMask) {
            isNaN = true;

            // Detect Infinity.
            if (bits == 0L) {
                digits =  infinityDigits;
            } else {
                digits = nanDigits;
                isNegative = false;
            }

            nDigits = digits.length;

            return;
        }

        // We have a working double.
        isNaN = false;

        int nSignificantBits;

        // Detect denormalized value.
        if (exponent == 0) {
            // Detect zero value.
            if (bits == 0L) {
                decimalExponent = 0;
                digits = zeroes;
                nDigits = 1;

                return;
            }

            // Normalize value, using highest significant bit as HOB.
            while ((bits & fractHOB) == 0L) {
                bits <<= 1;
                exponent -= 1;
            }

            // Compute number of significant bits.
            nSignificantBits = expShift + exponent +1;
            // Bias exponent by HOB.
            exponent += 1;
        } else {
            // Add implicit HOB.
            bits |= fractHOB;
            // Compute number of significant bits.
            nSignificantBits = expShift + 1;
        }

        // Unbias exponent (represents bit shift).
        exponent -= expBias;

        // Determine the number of significant bits in the fraction.
        final int nFractBits = countSignificantBits(bits);

        // Number of bits to the right of the decimal.
        final int nTinyBits = Math.max(0, nFractBits - exponent - 1);

        // Computed decimal exponent.
        int decExponent;

        if (exponent <= maxSmallBinExp && exponent >= minSmallBinExp) {
            // Look more closely at the number to decide if,
            // with scaling by 10^nTinyBits, the result will fit in
            // a long.
            if (nTinyBits < powersOf5.length && (nFractBits + nBitsPowerOf5[nTinyBits]) < 64) {
                /*
                 * We can do this:
                 * take the fraction bits, which are normalized.
                 * (a) nTinyBits == 0: Shift left or right appropriately
                 *     to align the binary point at the extreme right, i.e.
                 *     where a long int point is expected to be. The integer
                 *     result is easily converted to a string.
                 * (b) nTinyBits > 0: Shift right by expShift - nFractBits,
                 *     which effectively converts to long and scales by
                 *     2^nTinyBits. Then multiply by 5^nTinyBits to
                 *     complete the scaling. We know this won't overflow
                 *     because we just counted the number of bits necessary
                 *     in the result. The integer you get from this can
                 *     then be converted to a string pretty easily.
                 */

                if (nTinyBits == 0) {
                    long halfULP;

                    if (exponent > nSignificantBits) {
                        halfULP = 1L << (exponent - nSignificantBits - 1);
                    } else {
                        halfULP = 0L;
                    }

                    if (exponent >= expShift) {
                        bits <<= exponent - expShift;
                    } else {
                        bits >>>= expShift - exponent;
                    }

                    // Discard non-significant low-order bits, while rounding,
                    // up to insignificant value.
                    int i;
                    for (i = 0; halfULP >= 10L; i++) {
                        halfULP /= 10L;
                    }

                    /**
                     * This is the easy subcase --
                     * all the significant bits, after scaling, are held in bits.
                     * isNegative and decExponent tell us what processing and scaling
                     * has already been done. Exceptional cases have already been
                     * stripped out.
                     * In particular:
                     *      bits is a finite number (not Infinite, nor NaN)
                     *      bits > 0L (not zero, nor negative).
                     *
                     * The only reason that we develop the digits here, rather than
                     * calling on Long.toString() is that we can do it a little faster,
                     * and besides want to treat trailing 0s specially. If Long.toString
                     * changes, we should re-evaluate this strategy!
                     */

                    int decExp = 0;

                    if (i != 0) {
                         // 10^i == 5^i * 2^i
                        final long powerOf10 = powersOf5[i] << i;
                        final long residue = bits % powerOf10;
                        bits /= powerOf10;
                        decExp += i;

                        if (residue >= (powerOf10 >> 1)) {
                            // Round up based on the low-order bits we're discarding.
                            bits++;
                        }
                    }

                    int ndigits = 20;
                    final char[] digits0 = new char[26];
                    int digitno = ndigits - 1;
                    int c = (int)(bits % 10L);
                    bits /= 10L;

                    while (c == 0) {
                        decExp++;
                        c = (int)(bits % 10L);
                        bits /= 10L;
                    }

                    while (bits != 0L) {
                        digits0[digitno--] = (char)(c + '0');
                        decExp++;
                        c = (int)(bits % 10L);
                        bits /= 10;
                    }

                    digits0[digitno] = (char)(c + '0');

                    ndigits -= digitno;
                    final char[] result = new char[ndigits];
                    System.arraycopy(digits0, digitno, result, 0, ndigits);

                    this.digits          = result;
                    this.decimalExponent = decExp + 1;
                    this.nDigits         = ndigits;

                    return;
                }
            }
        }

        /*
         * This is the hard case. We are going to compute large positive
         * integers B and S and integer decExp, s.t.
         *      d = (B / S) * 10^decExp
         *      1 <= B / S < 10
         * Obvious choices are:
         *      decExp = floor(log10(d))
         *      B      = d * 2^nTinyBits * 10^max(0, -decExp)
         *      S      = 10^max(0, decExp) * 2^nTinyBits
         * (noting that nTinyBits has already been forced to non-negative)
         * I am also going to compute a large positive integer
         *      M      = (1/2^nSignificantBits) * 2^nTinyBits * 10^max(0, -decExp)
         * i.e. M is (1/2) of the ULP of d, scaled like B.
         * When we iterate through dividing B/S and picking off the
         * quotient bits, we will know when to stop when the remainder
         * is <= M.
         *
         * We keep track of powers of 2 and powers of 5.
         */

        /*
         * Estimate decimal exponent. (If it is small-ish,
         * we could double-check.)
         *
         * First, scale the mantissa bits such that 1 <= d2 < 2.
         * We are then going to estimate
         *          log10(d2) ~=~  (d2-1.5)/1.5 + log(1.5)
         * and so we can estimate
         *      log10(d) ~=~ log10(d2) + binExp * log10(2)
         * take the floor and call it decExp.
         */
        final double d2 = Double.longBitsToDouble(expOne | (bits & ~fractHOB));
        decExponent = (int)Math.floor((d2 - 1.5D) * 0.289529654D + 0.176091259D + exponent * 0.301029995663981D);

        // Powers of 2 and powers of 5, respectively, in B.
        final int B5 = Math.max(0, -decExponent);
        int B2 = B5 + nTinyBits + exponent;

        // Powers of 2 and powers of 5, respectively, in S.
        final int S5 = Math.max(0, decExponent);
        int S2 = S5 + nTinyBits;

        // Powers of 2 and powers of 5, respectively, in M.
        final int M5 = B5;
        int M2 = B2 - nSignificantBits;

        /*
         * The long integer fractBits contains the (nFractBits) interesting
         * bits from the mantissa of d (hidden 1 added if necessary) followed
         * by (expShift + 1 - nFractBits) zeros. In the interest of compactness,
         * I will shift out those zeros before turning fractBits into a
         * BigInteger. The resulting whole number will be
         *      d * 2^(nFractBits - 1 - binExp).
         */

        bits >>>= expShift + 1 - nFractBits;
        B2 -= nFractBits - 1;
        final int common2factor = Math.min(B2, S2);
        B2 -= common2factor;
        S2 -= common2factor;
        M2 -= common2factor;

        /*
         * HACK!!For exact powers of two, the next smallest number
         * is only half as far away as we think (because the meaning of
         * ULP changes at power-of-two bounds) for this reason, we
         * hack M2. Hope this works.
         */
        if (nFractBits == 1) {
            M2 -= 1;
        }

        if (M2 < 0) {
            // Oops.  Since we cannot scale M down far enough,
            // we must scale the other values up.
            B2 -= M2;
            S2 -= M2;
            M2 =  0;
        }

        /*
         * Construct, Scale, iterate.
         * Some day, we'll write a stopping test that takes
         * account of the asymmetry of the spacing of floating-point
         * numbers below perfect powers of 2
         * 26 Sept 96 is not that day.
         * So we use a symmetric test.
         */

        final char digits0[] = this.digits = new char[32];
        int  ndigit;
        boolean low, high;
        long lowDigitDifference;
        int  q;

        /*
         * Detect the special cases where all the numbers we are about
         * to compute will fit in int or long integers.
         * In these cases, we will avoid doing BigInteger arithmetic.
         * We use the same algorithms, except that we "normalize"
         * our FDBigInts before iterating. This is to make division easier,
         * as it makes our fist guess (quotient of high-order words)
         * more accurate!
         */

        // Binary digits needed to represent B, approx.
        final int Bbits = nFractBits + B2 + ((B5 < nBitsPowerOf5.length) ? nBitsPowerOf5[B5] : (B5*3));
        // Binary digits needed to represent 10*S, approx.
        final int tenSbits = S2 + 1 + (((S5 + 1) < nBitsPowerOf5.length) ? nBitsPowerOf5[(S5 + 1)] : ((S5 + 1) * 3));

        if (Bbits < 64 && tenSbits < 64) {
            long b = (bits * powersOf5[B5]) << B2;
            final long s = powersOf5[S5] << S2;
            long m = powersOf5[M5] << M2;
            final long tens = s * 10L;

            /*
             * Unroll the first iteration. If our decExp estimate
             * was too high, our first quotient will be zero. In this
             * case, we discard it and decrement decExp.
             */

            ndigit = 0;
            q = (int)(b / s);
            b = 10L * (b % s);
            m *= 10L;
            low  = b <  m;
            high = (b + m) > tens;

            if (q == 0 && !high) {
                // Ignore leading zero.
                decExponent--;
            } else {
                digits0[ndigit++] = (char)('0' + q);
            }

            if (decExponent < -3 || decExponent >= 8) {
                high = low = false;
            }

            while (!low && !high) {
                q = (int)(b / s);
                b = 10 * (b % s);
                m *= 10;

                if (m > 0L) {
                    low  = b < m;
                    high = (b + m) > tens;
                } else {
                    low = true;
                    high = true;
                }

                if (low && q == 0) {
                    break;
                }
                digits0[ndigit++] = (char)('0' + q);
            }

            lowDigitDifference = (b << 1) - tens;
        } else {
            /*
             * We must do BigInteger arithmetic.
             * First, construct our BigInteger initial values.
             */

            BigInteger Bval = multiplyPowerOf5And2(BigInteger.valueOf(bits), B5, B2);
            BigInteger Sval = constructPowerOf5And2(S5, S2);
            BigInteger Mval = constructPowerOf5And2(M5, M2);


            // Normalize so that BigInteger division works better.
            final int shiftBias = Long.numberOfLeadingZeros(bits) - 4;
            Bval = Bval.shiftLeft(shiftBias);
            Mval = Mval.shiftLeft(shiftBias);
            Sval = Sval.shiftLeft(shiftBias);
            final BigInteger tenSval = Sval.multiply(BigInteger.TEN);

            /*
             * Unroll the first iteration. If our decExp estimate
             * was too high, our first quotient will be zero. In this
             * case, we discard it and decrement decExp.
             */

            ndigit = 0;

            BigInteger[] quoRem = Bval.divideAndRemainder(Sval);
            q    = quoRem[0].intValue();
            Bval = quoRem[1].multiply(BigInteger.TEN);
            Mval = Mval.multiply(BigInteger.TEN);
            low  = (Bval.compareTo(Mval) < 0);
            high = (Bval.add(Mval).compareTo(tenSval) > 0);

            if (q == 0 && !high) {
                // Ignore leading zero.
                decExponent--;
            } else {
                digits0[ndigit++] = (char)('0' + q);
            }

            if (decExponent < -3 || decExponent >= 8) {
                high = low = false;
            }

            while(!low && !high) {
                quoRem = Bval.divideAndRemainder(Sval);
                q = quoRem[0].intValue();
                Bval = quoRem[1].multiply(BigInteger.TEN);
                Mval = Mval.multiply(BigInteger.TEN);
                low  = (Bval.compareTo(Mval) < 0);
                high = (Bval.add(Mval).compareTo(tenSval) > 0);

                if (low && q == 0) {
                    break;
                }
                digits0[ndigit++] = (char)('0' + q);
            }

            if (high && low) {
                Bval = Bval.shiftLeft(1);
                lowDigitDifference = Bval.compareTo(tenSval);
            } else {
                lowDigitDifference = 0L;
            }
        }

        this.decimalExponent = decExponent + 1;
        this.digits          = digits0;
        this.nDigits         = ndigit;

        /*
         * Last digit gets rounded based on stopping condition.
         */

        if (high) {
            if (low) {
                if (lowDigitDifference == 0L) {
                    // it's a tie!
                    // choose based on which digits we like.
                    if ((digits0[nDigits - 1] & 1) != 0) {
                        roundup();
                    }
                } else if (lowDigitDifference > 0) {
                    roundup();
                }
            } else {
                roundup();
            }
        }
    }

    /**
     * Count number of significant bits.
     * @param bits Double's fraction.
     * @return Number of significant bits.
     */
    private static int countSignificantBits(final long bits) {
        if (bits != 0) {
            return 64 - Long.numberOfLeadingZeros(bits) - Long.numberOfTrailingZeros(bits);
        }

        return 0;
    }

    /*
     * Cache big powers of 5 handy for future reference.
     */
    private static BigInteger powerOf5Cache[];

    /**
     * Determine the largest power of 5 needed (as BigInteger.)
     * @param power Power of 5.
     * @return BigInteger of power of 5.
     */
    private static BigInteger bigPowerOf5(final int power) {
        if (powerOf5Cache == null) {
            powerOf5Cache = new BigInteger[power + 1];
        } else if (powerOf5Cache.length <= power) {
            final BigInteger t[] = new BigInteger[ power+1 ];
            System.arraycopy(powerOf5Cache, 0, t, 0, powerOf5Cache.length);
            powerOf5Cache = t;
        }

        if (powerOf5Cache[power] != null) {
            return powerOf5Cache[power];
        } else if (power < powersOf5.length) {
            return powerOf5Cache[power] = BigInteger.valueOf(powersOf5[power]);
        } else {
            // Construct the value recursively.
            // in order to compute 5^p,
            // compute its square root, 5^(p/2) and square.
            // or, let q = p / 2, r = p -q, then
            // 5^p = 5^(q+r) = 5^q * 5^r
            final int q = power >> 1;
            final int r = power - q;
            BigInteger bigQ = powerOf5Cache[q];

            if (bigQ == null) {
                bigQ = bigPowerOf5(q);
            }

            if (r < powersOf5.length) {
                return (powerOf5Cache[power] = bigQ.multiply(BigInteger.valueOf(powersOf5[r])));
            }
            BigInteger bigR = powerOf5Cache[ r ];

            if (bigR == null) {
                bigR = bigPowerOf5(r);
            }

            return (powerOf5Cache[power] = bigQ.multiply(bigR));
        }
    }

    /**
     * Multiply BigInteger by powers of 5 and 2 (i.e., 10)
     * @param value Value to multiply.
     * @param p5    Power of 5.
     * @param p2    Power of 2.
     * @return Result.
     */
    private static BigInteger multiplyPowerOf5And2(final BigInteger value, final int p5, final int p2) {
        BigInteger returnValue = value;

        if (p5 != 0) {
            returnValue = returnValue.multiply(bigPowerOf5(p5));
        }

        if (p2 != 0) {
            returnValue = returnValue.shiftLeft(p2);
        }

        return returnValue;
    }

    /**
     * Construct a BigInteger power of 5 and 2 (i.e., 10)
     * @param p5    Power of 5.
     * @param p2    Power of 2.
     * @return Result.
     */
    private static BigInteger constructPowerOf5And2(final int p5, final int p2) {
        BigInteger v = bigPowerOf5(p5);

        if (p2 != 0) {
            v = v.shiftLeft(p2);
        }

        return v;
    }

    /**
     * Round up last digit by adding one to the least significant digit.
     * In the unlikely event there is a carry out, deal with it.
     * assert that this will only happen where there
     * is only one digit, e.g. (float)1e-44 seems to do it.
     */
    private void roundup() {
        int i;
        int q = digits[ i = (nDigits-1)];

        while (q == '9' && i > 0) {
            if (decimalExponent < 0) {
                nDigits--;
            } else {
                digits[i] = '0';
            }

            q = digits[--i];
        }

        if (q == '9') {
            // Carryout! High-order 1, rest 0s, larger exp.
            decimalExponent += 1;
            digits[0] = '1';

            return;
        }

        digits[i] = (char)(q + 1);
    }

    /**
     * Format final number string.
     * @return Formatted string.
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(32);

        if (isNegative) {
            sb.append('-');
        }

        if (isNaN) {
            sb.append(digits, 0, nDigits);
        } else {
            if (decimalExponent > 0 && decimalExponent <= 21) {
                final int charLength = Math.min(nDigits, decimalExponent);
                sb.append(digits, 0, charLength);

                if (charLength < decimalExponent) {
                    sb.append(zeroes, 0, decimalExponent - charLength);
                } else if (charLength < nDigits) {
                    sb.append('.');
                    sb.append(digits, charLength, nDigits - charLength);
                }
            } else if (decimalExponent <=0 && decimalExponent > -6) {
                sb.append('0');
                sb.append('.');

                if (decimalExponent != 0) {
                    sb.append(zeroes, 0, -decimalExponent);
                }

                sb.append(digits, 0, nDigits);
            } else {
                sb.append(digits[0]);

                if (nDigits > 1) {
                    sb.append('.');
                    sb.append(digits, 1, nDigits - 1);
                }

                sb.append('e');
                final int exponent;
                int e;

                if (decimalExponent <= 0) {
                    sb.append('-');
                    exponent = e = -decimalExponent + 1;
                } else {
                    sb.append('+');
                    exponent = e = decimalExponent - 1;
                }

                if (exponent > 99) {
                    sb.append((char)(e / 100 + '0'));
                    e %= 100;
                }

                if (exponent > 9) {
                    sb.append((char)(e / 10 + '0'));
                    e %= 10;
                }

                sb.append((char)(e + '0'));
            }
        }

        return sb.toString();
    }
}
