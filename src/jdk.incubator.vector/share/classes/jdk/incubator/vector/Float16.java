/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.vector;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

// import jdk.internal.math.*;

import static jdk.incubator.vector.Float16Consts.SIGN_BIT_MASK;
import static jdk.incubator.vector.Float16Consts.EXP_BIT_MASK;
import static jdk.incubator.vector.Float16Consts.SIGNIF_BIT_MASK;

import static java.lang.Float.float16ToFloat;
import static java.lang.Float.floatToFloat16;
import static java.lang.Integer.numberOfLeadingZeros;
import static java.lang.Math.multiplyHigh;
import jdk.internal.vm.annotation.ForceInline;
import jdk.internal.vm.vector.Float16Math;

/**
 * The {@code Float16} is a class holding 16-bit data
 * in IEEE 754 binary16 format.
 *
 * <p>Binary16 Format:<br>
 *   S EEEEE  MMMMMMMMMM<br>
 *   Sign        - 1 bit<br>
 *   Exponent    - 5 bits<br>
 *   Significand - 10 bits (does not include the <i>implicit bit</i>
 *                    inferred from the exponent, see {@link #PRECISION})<br>
 *
 * <p>Unless otherwise specified, the methods in this class use a
 * <em>rounding policy</em> (JLS {@jls 15.4}) of {@linkplain
 * java.math.RoundingMode#HALF_EVEN round to nearest}.
 *
 * <p>This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
 * class; programmers should treat instances that are
 * {@linkplain #equals(Object) equal} as interchangeable and should not
 * use instances for synchronization, or unpredictable behavior may
 * occur. For example, in a future release, synchronization may fail.
 *
 * <h2><a id=equivalenceRelation>Floating-point Equality, Equivalence,
 * and Comparison</a></h2>
 *
 * The class {@code java.lang.Double} has a {@linkplain
 * Double##equivalenceRelation discussion of equality,
 * equivalence, and comparison of floating-point values} that is
 * equally applicable to {@code Float16} values.
 *
 * <h2><a id=decimalToBinaryConversion>Decimal &harr; Binary Conversion Issues</a></h2>
 *
 * The {@linkplain Double##decimalToBinaryConversion discussion of binary to
 * decimal conversion issues} in {@code java.lang.Double} is also
 * applicable to {@code Float16} values.
 *
 *
 * @apiNote
 * The methods in this class generally have analogous methods in
 * either {@link Float}/{@link Double} or {@link Math}/{@link
 * StrictMath}. Unless otherwise specified, the handling of special
 * floating-point values such as {@linkplain #isNaN(Float16) NaN}
 * values, {@linkplain #isInfinite(Float16) infinities}, and signed
 * zeros of methods in this class is wholly analogous to the handling
 * of equivalent cases by methods in {@code Float}, {@code Double},
 * {@code Math}, etc.
 *
 * @since 24
 *
 * @see <a href="https://standards.ieee.org/ieee/754/6210/">
 *      <cite>IEEE Standard for Floating-Point Arithmetic</cite></a>
 */

// Currently Float16 is a value-based class and in future it is
// expected to be aligned with Value Classes and Object as described in
// JEP-401 (https://openjdk.org/jeps/401).
@jdk.internal.ValueBased
public final class Float16
    extends Number
    implements Comparable<Float16> {
    /** @serial */
    private final short value;
    private static final long serialVersionUID = 16; // May not be needed when a value class?

    // Functionality for future consideration:
    // IEEEremainder / remainder operator remainder

    // Do *not* define any public constructors
   /**
    * Returns a {@code Float16} instance wrapping IEEE 754 binary16
    * encoded {@code short} value.
    *
    * @param  bits a short value.
    */
    private Float16(short bits) {
        this.value = bits;
    }

    /**
     * A constant holding the positive infinity of type {@code
     * Float16}.
     *
     * @see Float#POSITIVE_INFINITY
     * @see Double#POSITIVE_INFINITY
     */
    public static final Float16 POSITIVE_INFINITY = valueOf(Float.POSITIVE_INFINITY);

    /**
     * A constant holding the negative infinity of type {@code
     * Float16}.
     *
     * @see Float#NEGATIVE_INFINITY
     * @see Double#NEGATIVE_INFINITY
     */
    public static final Float16 NEGATIVE_INFINITY = valueOf(Float.NEGATIVE_INFINITY);

    /**
     * A constant holding a Not-a-Number (NaN) value of type {@code
     * Float16}.
     *
     * @see Float#NaN
     * @see Double#NaN
     */
    public static final Float16 NaN = valueOf(Float.NaN);

    /**
     * A constant holding the largest positive finite value of type
     * {@code Float16},
     * (2-2<sup>-10</sup>)&middot;2<sup>15</sup>, numerically equal to 65504.0.
     *
     * @see Float#MAX_VALUE
     * @see Double#MAX_VALUE
     */
    public static final Float16 MAX_VALUE = valueOf(0x1.ffcp15f);

    /**
     * A constant holding the smallest positive normal value of type
     * {@code Float16}, 2<sup>-14</sup>.
     *
     * @see Float#MIN_NORMAL
     * @see Double#MIN_NORMAL
     */
    public static final Float16 MIN_NORMAL = valueOf(0x1.0p-14f);

    /**
     * A constant holding the smallest positive nonzero value of type
     * {@code Float16}, 2<sup>-24</sup>.
     *
     * @see Float#MIN_VALUE
     * @see Double#MIN_VALUE
     */
    public static final Float16 MIN_VALUE = valueOf(0x1.0p-24f);

    /**
     * The number of bits used to represent a {@code Float16} value,
     * {@value}.
     *
     * @see Float#SIZE
     * @see Double#SIZE
     */
    public static final int SIZE = 16;

    /**
     * The number of bits in the significand of a {@code Float16}
     * value, {@value}.  This corresponds to parameter N in section
     * {@jls 4.2.3} of <cite>The Java Language Specification</cite>.
     *
     * @see Float#PRECISION
     * @see Double#PRECISION
     */
    public static final int PRECISION = 11;

    /**
     * Maximum exponent a finite {@code Float16} variable may have,
     * {@value}. It is equal to the value returned by {@code
     * Float16.getExponent(Float16.MAX_VALUE)}.
     *
     * @see Float#MAX_EXPONENT
     * @see Double#MAX_EXPONENT
     */
    public static final int MAX_EXPONENT = (1 << (SIZE - PRECISION - 1)) - 1; // 15

    /**
     * Minimum exponent a normalized {@code Float16} variable may
     * have, {@value}.  It is equal to the value returned by {@code
     * Float16.getExponent(Float16.MIN_NORMAL)}.
     *
     * @see Float#MIN_EXPONENT
     * @see Double#MIN_EXPONENT
     */
    public static final int MIN_EXPONENT = 1 - MAX_EXPONENT; // -14

    /**
     * The number of bytes used to represent a {@code Float16} value,
     * {@value}.
     *
     * @see Float#BYTES
     * @see Double#BYTES
     */
    public static final int BYTES = SIZE / Byte.SIZE;

    /**
     * Returns a string representation of the {@code Float16}
     * argument.
     *
     * The behavior of this method is analogous to {@link
     * Float#toString(float)} in the handling of special values
     * (signed zeros, infinities, and NaN) and the generation of a
     * decimal string that will convert back to the argument value.
     *
     * @param   f16   the {@code Float16} to be converted.
     * @return a string representation of the argument.
     * @see java.lang.Float#toString(float)
     */
    public static String toString(Float16 f16) {
         return Float16ToDecimal.toString(f16);
    }

    /**
     * Returns a hexadecimal string representation of the {@code
     * Float16} argument.
     *
     * The behavior of this class is analogous to {@link
     * Float#toHexString(float)} except that an exponent value of
     * {@code "p-14"} is used for subnormal {@code Float16} values.
     *
     * @apiNote
     * This method corresponds to the convertToHexCharacter operation
     * defined in IEEE 754.
     *
     * @param   f16   the {@code Float16} to be converted.
     * @return a hex string representation of the argument.
     *
     * @see Float#toHexString(float)
     * @see Double#toHexString(double)
     */
    public static String toHexString(Float16 f16) {
        float f = f16.floatValue();
        if (Math.abs(f) < float16ToFloat(MIN_NORMAL.value)
            &&  f != 0.0f ) {// Float16 subnormal
            // Adjust exponent to create subnormal double, then
            // replace subnormal double exponent with subnormal Float16
            // exponent
            String s = Double.toHexString(Math.scalb((double)f,
                                                     /* -1022+14 */
                                                     Double.MIN_EXPONENT-
                                                     MIN_EXPONENT));
            return s.replaceFirst("p-1022$", "p-14");
        } else {// double string will be the same as Float16 string
            return Double.toHexString(f);
        }
    }

    // -----------------------

   /**
    * {@return the value of an {@code int} converted to {@code
    * Float16}}
    *
    * @param  value an {@code int} value.
    *
    * @apiNote
    * This method corresponds to the convertFromInt operation defined
    * in IEEE 754.
    */
    public static Float16 valueOf(int value) {
        // int -> double conversion is exact
        return valueOf((double)value);
    }

   /**
    * {@return the value of a {@code long} converted to {@code Float16}}
    *
    * @apiNote
    * This method corresponds to the convertFromInt operation defined
    * in IEEE 754.
    *
    * @param  value a {@code long} value.
    */
    public static Float16 valueOf(long value) {
        if (value <= -65_520L) {  // -(Float16.MAX_VALUE + Float16.ulp(Float16.MAX_VALUE) / 2)
            return NEGATIVE_INFINITY;
        } else {
            if (value >= 65_520L) {  // Float16.MAX_VALUE + Float16.ulp(Float16.MAX_VALUE) / 2
                return POSITIVE_INFINITY;
            }
            // Remaining range of long, the integers in approx. +/-
            // 2^16, all fit in a float so the correct conversion can
            // be done via an intermediate float conversion.
            return valueOf((float)value);
        }
    }

   /**
    * {@return a {@code Float16} value rounded from the {@code float}
    * argument using the round to nearest rounding policy}
    *
    * @apiNote
    * This method corresponds to the convertFormat operation defined
    * in IEEE 754.
    *
    * @param  f a {@code float}
    */
    @ForceInline
    public static Float16 valueOf(float f) {
        return new Float16(floatToFloat16(f));
    }

   /**
    * {@return a {@code Float16} value rounded from the {@code double}
    * argument using the round to nearest rounding policy}
    *
    * @apiNote
    * This method corresponds to the convertFormat operation defined
    * in IEEE 754.
    *
    * @param  d a {@code double}
    */
    public static Float16 valueOf(double d) {
        long doppel = Double.doubleToRawLongBits(d);

        short sign_bit = (short)((doppel & 0x8000_0000_0000_0000L) >> 48);

        if (Double.isNaN(d)) {
            // Have existing float code handle any attempts to
            // preserve NaN bits.
            return valueOf((float)d);
        }

        double abs_d = Math.abs(d);

        // The overflow threshold is binary16 MAX_VALUE + 1/2 ulp
        if (abs_d >= (0x1.ffcp15 + 0x0.002p15) ) {
             // correctly signed infinity
            return new Float16((short)(sign_bit | 0x7c00));
        }

        // Smallest magnitude nonzero representable binary16 value
        // is equal to 0x1.0p-24; half-way and smaller rounds to zero.
        if (abs_d <= 0x1.0p-24d * 0.5d) { // Covers double zeros and subnormals.
            return new Float16(sign_bit); // Positive or negative zero
        }

        // Dealing with finite values in exponent range of binary16
        // (when rounding is done, could still round up)
        int exp = Math.getExponent(d);
        assert -25 <= exp && exp <= 15;

        // For binary16 subnormals, beside forcing exp to -15, retain
        // the difference expdelta = E_min - exp.  This is the excess
        // shift value, in addition to 42, to be used in the
        // computations below.  Further the (hidden) msb with value 1
        // in d must be involved as well.
        int expdelta = 0;
        long msb = 0x0000_0000_0000_0000L;
        if (exp < -14) {
            expdelta = -14 - exp; // FIXME?
            exp = -15;
            msb = 0x0010_0000_0000_0000L; // should be 0x0020_... ?
        }
        long f_signif_bits = doppel & 0x000f_ffff_ffff_ffffL | msb;

        // Significand bits as if using rounding to zero (truncation).
        short signif_bits = (short)(f_signif_bits >> (42 + expdelta));

        // For round to nearest even, determining whether or not to
        // round up (in magnitude) is a function of the least
        // significant bit (LSB), the next bit position (the round
        // position), and the sticky bit (whether there are any
        // nonzero bits in the exact result to the right of the round
        // digit). An increment occurs in three cases:
        //
        // LSB  Round Sticky
        // 0    1     1
        // 1    1     0
        // 1    1     1
        // See "Computer Arithmetic Algorithms," Koren, Table 4.9

        long lsb    = f_signif_bits & (1L << 42 + expdelta);
        long round  = f_signif_bits & (1L << 41 + expdelta);
        long sticky = f_signif_bits & ((1L << 41 + expdelta) - 1);

        if (round != 0 && ((lsb | sticky) != 0 )) {
            signif_bits++;
        }

        // No bits set in significand beyond the *first* exponent bit,
        // not just the significand; quantity is added to the exponent
        // to implement a carry out from rounding the significand.
        assert (0xf800 & signif_bits) == 0x0;

        return new Float16((short)(sign_bit | ( ((exp + 15) << 10) + signif_bits ) ));
    }

    /**
     * Returns a {@code Float16} holding the floating-point value
     * represented by the argument string.
     *
     * The grammar of strings accepted by this method is the same as
     * that accepted by {@link Double#valueOf(String)}. The rounding
     * policy is also analogous to the one used by that method, a
     * valid input is regarded as an exact numerical value that is
     * rounded once to the nearest representable {@code Float16} value.
     *
     * @apiNote
     * This method corresponds to the convertFromDecimalCharacter and
     * convertFromHexCharacter operations defined in IEEE 754.
     *
     * @param  s the string to be parsed.
     * @return the {@code Float16} value represented by the string
     *         argument.
     * @throws NullPointerException  if the string is null
     * @throws NumberFormatException if the string does not contain a
     *               parsable {@code Float16}.
     * @see    java.lang.Float#valueOf(String)
     */
    public static Float16 valueOf(String s) throws NumberFormatException {
        s = s.trim(); // Legacy behavior from analogous methods on
                      // Float and Double.

        // Trial conversion from String -> double. Do quick range
        // check for a pass-through, then check for possibility of
        // double-rounding and another conversion using
        // BigInteger/BigDecimal, if needed.
        double trialResult = Double.parseDouble(s);
        // After this point, the trimmed string is known to be
        // syntactically well-formed; should be able to operate on
        // characters rather than codepoints.

        if (trialResult == 0.0 // handles signed zeros
            || Math.abs(trialResult) > (65504.0 + 32.0) || // Float.MAX_VALUE + ulp(MAX_VALUE),
                                                           // handles infinities too
            Double.isNaN(trialResult) ||
            noDoubleRoundingToFloat16(trialResult)) {
            return valueOf(trialResult);
        } else {
            // If double rounding is not ruled out, re-parse, create a
            // BigDecimal to hold the exact numerical value, round and
            // return.

            // Remove any trailing FloatTypeSuffix (f|F|d|D), not
            // recognized by BigDecimal (or BigInteger)
            int sLength = s.length();
            if (Character.isAlphabetic(s.charAt(sLength-1))) {
                s = s.substring(0, sLength - 1);
            }

            char startingChar = s.charAt(0);
            boolean isSigned = (startingChar == '-') || (startingChar == '+');
            // Hex literal will start "-0x..." or "+0x..." or "0x...""
            // A valid hex literal must be at least three characters
            // long "0xH" where H is a hex digit.
            boolean hexInput = (s.length() >= 3 ) && isX(s.charAt(isSigned ? 2 : 1));

            if (!hexInput) { // Decimal input
                // Grammar of BigDecimal string input is compatible
                // with the decimal grammar for this method after
                // trimming and removal of any FloatTypeSuffix.
                return valueOf(new BigDecimal(s));
            } else {
                // For hex inputs, convert the significand and
                // exponent portions separately.
                //
                // Rough form of the hex input:
                // Sign_opt 0x IntHexDigits_opt . FracHexDigits_opt [p|P] SignedInteger
                //
                // Partition input into between "0x" and "p" and from
                // "p" to end of string.
                //
                // For the region between x and p, see if there is a
                // period present. If so, the net exponent will need
                // to be adjusted by the number of digits to the right
                // of the (hexa)decimal point.
                //
                // Use BigInteger(String, 16) to construct the
                // significand -- accepts leading sign.
                StringBuilder hexSignificand = new StringBuilder();
                if (isSigned) {
                    hexSignificand.append(startingChar);
                }

                int fractionDigits = 0;
                int digitStart = isSigned ? 3 : 2 ;

                int periodIndex = s.indexOf((int)'.');

                int pIndex = findPIndex(s);

                // Gather the significand digits
                if (periodIndex != -1) {
                    // Reconstruct integer and fraction digit sequence
                    // without the period.
                    hexSignificand.append(s, digitStart,      periodIndex);
                    hexSignificand.append(s, periodIndex + 1, pIndex);
                    fractionDigits = pIndex - periodIndex - 1;
                } else {
                    // All integer digits, no fraction digits
                    hexSignificand.append(s, digitStart, pIndex);
                }

                // The exponent of a hexadecimal floating-point
                // literal is written in _decimal_.
                int rawExponent = Integer.parseInt(s.substring(pIndex+1));

                // The exact numerical value of the string is:
                //
                // normalizedSignificand * 2^(adjustedExponent)
                //
                // Given the set of methods on BigDecimal, in
                // particular pow being limited to non-negative
                // exponents, this is computed either by multiplying
                // by BigDecimal.TWO raised to the adjustedExponent or
                // dividing by BigDecimal.TWO raised to the negated
                // adjustedExponent.

                BigDecimal normalizedSignficand =
                    new BigDecimal(new BigInteger(hexSignificand.toString(), 16));

                // Each hex fraction digit is four bits
                int adjustedExponent = rawExponent - 4*fractionDigits;

                BigDecimal convertedStringValue = (adjustedExponent >= 0) ?
                    normalizedSignficand.multiply(BigDecimal.TWO.pow( adjustedExponent)) :
                    normalizedSignficand.divide(  BigDecimal.TWO.pow(-adjustedExponent));
                return valueOf(convertedStringValue);
            }
        }
    }

    private static boolean noDoubleRoundingToFloat16(double d) {
        // Note that if the String -> double conversion returned
        // whether or not the conversion was exact, many cases could
        // be skipped since the double-rounding would be known not to
        // have occurred.
        long dAsLong = Double.doubleToRawLongBits(d);
        long mask = 0x03FF_FFFF_FFFFL; // 42 low-order bits
        long maskedValue = dAsLong & mask;
        // not half-way between two adjacent Float16 values
        return maskedValue != 0x0200_0000_0000L;
    }

    private static boolean isX(int character) {
        return character == (int)'x' || character == (int)'X';
    }

    private static int findPIndex(String s) {
        int pIndex = s.indexOf((int)'p');
        return (pIndex != -1) ?  pIndex : s.indexOf((int)'P');
    }

    /**
     * {@return a {@link Float16} value rounded from the {@link BigDecimal}
     * argument using the round to nearest rounding policy}
     *
     * @param  v a {@link BigDecimal}
     */
    public static Float16 valueOf(BigDecimal v) {
        return BigDecimalConversion.float16Value(v);
    }

    // TODO: Host this functionality in this class for now until
    // java.base has Float16 support and this logic can be moved to
    // BigDecimal.
    private class BigDecimalConversion {
        /*
         * Let l = log_2(10).
         * Then, L < l < L + ulp(L) / 2, that is, L = roundTiesToEven(l).
         */
        private static final double L = 3.321928094887362;

        private static final int P_F16 = PRECISION;  // 11
        private static final int Q_MIN_F16 = MIN_EXPONENT - (P_F16 - 1);  // -24
        private static final int Q_MAX_F16 = MAX_EXPONENT - (P_F16 - 1);  // 5

        /**
         * Powers of 10 which can be represented exactly in {@code
         * Float16}.
         */
        private static final Float16[] FLOAT16_10_POW = {
            Float16.valueOf(1), Float16.valueOf(10), Float16.valueOf(100),
            Float16.valueOf(1_000), Float16.valueOf(10_000)
        };

        public static Float16 float16Value(BigDecimal bd) {
            int scale = bd.scale();
            BigInteger unscaledValue = bd.unscaledValue();

            if (unscaledValue.abs().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) <= 0) {
                long intCompact = unscaledValue.longValue();
                Float16 v = Float16.valueOf(intCompact);
                if (scale == 0) {
                    return v;
                }
                /*
                 * The discussion for the double case also applies here. That is,
                 * the following test is precise for all long values, but here
                 * Long.MAX_VALUE is not an issue.
                 */
                if (v.longValue() == intCompact) {
                    if (0 < scale && scale < FLOAT16_10_POW.length) {
                        return Float16.divide(v, FLOAT16_10_POW[scale]);
                    }
                    if (0 > scale && scale > -FLOAT16_10_POW.length) {
                        return Float16.multiply(v, FLOAT16_10_POW[-scale]);
                    }
                }
            }
            return fullFloat16Value(bd);
        }

        private static BigInteger bigTenToThe(int scale) {
            return BigInteger.TEN.pow(scale);
        }

        private static Float16 fullFloat16Value(BigDecimal bd) {
            if (BigDecimal.ZERO.compareTo(bd) == 0) {
                return Float16.valueOf(0);
            }
            BigInteger w = bd.unscaledValue().abs();
            int scale = bd.scale();
            long qb = w.bitLength() - (long) Math.ceil(scale * L);
            Float16 signum = Float16.valueOf(bd.signum());
            if (qb < Q_MIN_F16 - 2) {  // qb < -26
                return Float16.multiply(signum, Float16.valueOf(0));
            }
            if (qb > Q_MAX_F16 + P_F16 + 1) {  // qb > 17
                return Float16.multiply(signum, Float16.POSITIVE_INFINITY);
            }
            if (scale < 0) {
                return Float16.multiply(signum, valueOf(w.multiply(bigTenToThe(-scale))));
            }
            if (scale == 0) {
                return Float16.multiply(signum, valueOf(w));
            }
            int ql = (int) qb - (P_F16 + 3);
            BigInteger pow10 =  bigTenToThe(scale);
            BigInteger m, n;
            if (ql <= 0) {
                m = w.shiftLeft(-ql);
                n = pow10;
            } else {
                m = w;
                n = pow10.shiftLeft(ql);
            }
            BigInteger[] qr = m.divideAndRemainder(n);
            /*
             * We have
             *      2^12 = 2^{P+1} <= i < 2^{P+5} = 2^16
             * Contrary to the double and float cases, where we use long and int, resp.,
             * here we cannot simply declare i as short, because P + 5 < Short.SIZE
             * fails to hold.
             * Using int is safe, though.
             *
             * Further, as Math.scalb(Float16) does not exists, we fall back to
             * Math.scalb(double).
             */
            int i = qr[0].intValue();
            int sb = qr[1].signum();
            int dq = (Integer.SIZE - (P_F16 + 2)) - Integer.numberOfLeadingZeros(i);
            int eq = (Q_MIN_F16 - 2) - ql;
            if (dq >= eq) {
                return Float16.valueOf(bd.signum() * Math.scalb((double) (i | sb), ql));
            }
            int mask = (1 << eq) - 1;
            int j = i >> eq | (Integer.signum(i & mask)) | sb;
            return Float16.valueOf(bd.signum() * Math.scalb((double) j, Q_MIN_F16 - 2));
        }

        public static Float16 valueOf(BigInteger bi) {
            int signum = bi.signum();
            return (signum == 0 || bi.bitLength() <= 31)
                ? Float16.valueOf(bi.longValue())  // might return infinities
                : signum > 0
                ? Float16.POSITIVE_INFINITY
                : Float16.NEGATIVE_INFINITY;
        }
    }

    /**
     * Returns {@code true} if the specified number is a
     * Not-a-Number (NaN) value, {@code false} otherwise.
     *
     * @apiNote
     * This method corresponds to the isNaN operation defined in IEEE
     * 754.
     *
     * @param   f16   the value to be tested.
     * @return  {@code true} if the argument is NaN;
     *          {@code false} otherwise.
     *
     * @see Float#isNaN(float)
     * @see Double#isNaN(double)
     */
    public static boolean isNaN(Float16 f16) {
        final short bits = float16ToRawShortBits(f16);
        // A NaN value has all ones in its exponent and a non-zero significand
        return ((bits & 0x7c00) == 0x7c00 && (bits & 0x03ff) != 0);
    }

    /**
     * Returns {@code true} if the specified number is infinitely
     * large in magnitude, {@code false} otherwise.
     *
     * @apiNote
     * This method corresponds to the isInfinite operation defined in
     * IEEE 754.
     *
     * @param   f16   the value to be tested.
     * @return  {@code true} if the argument is positive infinity or
     *          negative infinity; {@code false} otherwise.
     *
     * @see Float#isInfinite(float)
     * @see Double#isInfinite(double)
     */
    public static boolean isInfinite(Float16 f16) {
        return ((float16ToRawShortBits(f16) ^
                 float16ToRawShortBits(POSITIVE_INFINITY)) & 0x7fff) == 0;
    }

    /**
     * Returns {@code true} if the argument is a finite floating-point
     * value; returns {@code false} otherwise (for NaN and infinity
     * arguments).
     *
     * @apiNote
     * This method corresponds to the isFinite operation defined in
     * IEEE 754.
     *
     * @param f16 the {@code Float16} value to be tested
     * @return {@code true} if the argument is a finite
     * floating-point value, {@code false} otherwise.
     *
     * @see Float#isFinite(float)
     * @see Double#isFinite(double)
     */
    public static boolean isFinite(Float16 f16) {
        return (float16ToRawShortBits(f16) & (short)0x0000_7FFF) <=
            float16ToRawShortBits(MAX_VALUE);
     }

    /**
     * {@return the value of this {@code Float16} as a {@code byte} after
     * a narrowing primitive conversion}
     *
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    @ForceInline
    public byte byteValue() {
        return (byte)floatValue();
    }

    /**
     * {@return a string representation of this {@code Float16}}
     *
     * @implSpec
     * This method returns the result of {@code Float16.toString(this)}.
     */
    public String toString() {
        return toString(this);
    }

    /**
     * {@return the value of this {@code Float16} as a {@code short}
     * after a narrowing primitive conversion}
     *
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    @ForceInline
    public short shortValue() {
        return (short)floatValue();
    }

    /**
     * {@return the value of this {@code Float16} as an {@code int} after
     * a narrowing primitive conversion}
     *
     * @apiNote
     * This method corresponds to the convertToIntegerTowardZero
     * operation defined in IEEE 754.
     *
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    @ForceInline
    public int intValue() {
        return (int)floatValue();
    }

    /**
     * {@return value of this {@code Float16} as a {@code long} after a
     * narrowing primitive conversion}
     *
     * @apiNote
     * This method corresponds to the convertToIntegerTowardZero
     * operation defined in IEEE 754.
     *
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    @Override
    public long longValue() {
        return (long)floatValue();
    }

    /**
     * {@return the value of this {@code Float16} as a {@code float}
     * after a widening primitive conversion}
     *
     * @apiNote
     * This method corresponds to the convertFormat operation defined
     * in IEEE 754.
     *
     * @jls 5.1.2 Widening Primitive Conversion
     */
    @Override
    @ForceInline
    public float floatValue() {
        return float16ToFloat(value);
    }

    /**
     * {@return the value of this {@code Float16} as a {@code double}
     * after a widening primitive conversion}
     *
     * @apiNote
     * This method corresponds to the convertFormat operation defined
     * in IEEE 754.
     *
     * @jls 5.1.2 Widening Primitive Conversion
     */
    @Override
    @ForceInline
    public double doubleValue() {
        return (double)floatValue();
    }

    /**
     * {@return a hash code for this {@code Float16} object}
     *
     * The general contract of {@code Object#hashCode()} is satisfied.
     * All NaN values have the same hash code. Additionally, all
     * distinct numerical values have unique hash codes; in
     * particular, negative zero and positive zero have different hash
     * codes from each other.
     */
    @Override
    public int hashCode() {
        return hashCode(this);
    }

    /**
     * Returns a hash code for a {@code Float16} value; compatible with
     * {@code Float16.hashCode()}.
     *
     * @param value the value to hash
     * @return a hash code value for a {@code Float16} value.
     */
    public static int hashCode(Float16 value) {
        // Use bit-pattern of canonical NaN for hashing.
        Float16 f16 = isNaN(value) ? Float16.NaN : value;
        return (int)float16ToRawShortBits(f16);
    }

    /**
     * Compares this object against the specified object.  The result
     * is {@code true} if and only if the argument is not
     * {@code null} and is a {@code Float16} object that
     * represents a {@code Float16} that has the same value as the
     * {@code double} represented by this object.
     *
     * @jls 15.21.1 Numerical Equality Operators == and !=
     */
    public boolean equals(Object obj) {
        return (obj instanceof Float16 f16) &&
            (float16ToShortBits(f16) == float16ToShortBits(this));
    }

    /**
     * Returns a representation of the specified floating-point value
     * according to the IEEE 754 floating-point binary16 bit layout.
     *
     * @param   f16   a {@code Float16} floating-point number.
     * @return the bits that represent the floating-point number.
     *
     * @see Float#floatToRawIntBits(float)
     * @see Double#doubleToRawLongBits(double)
     */
    public static short float16ToRawShortBits(Float16 f16) {
        return f16.value;
    }

    /**
     * Returns a representation of the specified floating-point value
     * according to the IEEE 754 floating-point binary16 bit layout.
     * All NaN values return the same bit pattern as {@link Float16#NaN}.
     *
     * @param   f16   a {@code Float16} floating-point number.
     * @return the bits that represent the floating-point number.
     *
     * @see Float#floatToRawIntBits(float)
     * @see Double#doubleToRawLongBits(double)
     */
    public static short float16ToShortBits(Float16 f16) {
        if (isNaN(f16)) {
            return Float16.NaN.value;
        }
        return f16.value;
    }

    /**
     * Returns the {@code Float16} value corresponding to a given bit
     * representation.
     *
     * @param   bits   any {@code short} integer.
     * @return  the {@code Float16} floating-point value with the same
     *          bit pattern.
     *
     * @see Float#intBitsToFloat(int)
     * @see Double#longBitsToDouble(long)
     */
    public static Float16 shortBitsToFloat16(short bits) {
        return new Float16(bits);
    }

    /**
     * Compares two {@code Float16} objects numerically.
     *
     * This method imposes a total order on {@code Float16} objects
     * with two differences compared to the incomplete order defined by
     * the Java language numerical comparison operators ({@code <, <=,
     * ==, >=, >}) on {@code float} and {@code double} values.
     *
     * <ul><li> A NaN is <em>unordered</em> with respect to other
     *          values and unequal to itself under the comparison
     *          operators.  This method chooses to define {@code
     *          Float16.NaN} to be equal to itself and greater than all
     *          other {@code Float16} values (including {@code
     *          Float16.POSITIVE_INFINITY}).
     *
     *      <li> Positive zero and negative zero compare equal
     *      numerically, but are distinct and distinguishable values.
     *      This method chooses to define positive zero
     *      to be greater than negative zero.
     * </ul>
     *
     * @param   anotherFloat16   the {@code Float16} to be compared.
     * @return  the value {@code 0} if {@code anotherFloat16} is
     *          numerically equal to this {@code Float16}; a value
     *          less than {@code 0} if this {@code Float16}
     *          is numerically less than {@code anotherFloat16};
     *          and a value greater than {@code 0} if this
     *          {@code Float16} is numerically greater than
     *          {@code anotherFloat16}.
     *
     * @see Float#compareTo(Float)
     * @see Double#compareTo(Double)
     * @jls 15.20.1 Numerical Comparison Operators {@code <}, {@code <=}, {@code >}, and {@code >=}
     */
    @Override
    public int compareTo(Float16 anotherFloat16) {
        return compare(this, anotherFloat16);
    }

    /**
     * Compares the two specified {@code Float16} values.
     *
     * @param   f1        the first {@code Float16} to compare
     * @param   f2        the second {@code Float16} to compare
     * @return  the value {@code 0} if {@code f1} is
     *          numerically equal to {@code f2}; a value less than
     *          {@code 0} if {@code f1} is numerically less than
     *          {@code f2}; and a value greater than {@code 0}
     *          if {@code f1} is numerically greater than
     *          {@code f2}.
     *
     * @see Float#compare(float, float)
     * @see Double#compare(double, double)
     */
    public static int compare(Float16 f1, Float16 f2) {
        return Float.compare(f1.floatValue(), f2.floatValue());
    }

    /**
     * Returns the larger of two {@code Float16} values.
     *
     * The handling of signed zeros, NaNs, infinities, and other
     * special cases by this method is analogous to the handling of
     * those cases by the Math#max(double, double) method.
     *
     * @apiNote
     * This method corresponds to the maximum operation defined in
     * IEEE 754.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the greater of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @see Math#max(float, float)
     */
    public static Float16 max(Float16 a, Float16 b) {
        return valueOf(Math.max(a.floatValue(), b.floatValue()));
    }

    /**
     * Returns the smaller of two {@code Float16} values.
     *
     * The handling of signed zeros, NaNs, infinities, and other
     * special cases by this method is analogous to the handling of
     * those cases by the Math#min(double, double) method.
     *
     * @apiNote
     * This method corresponds to the minimum operation defined in
     * IEEE 754.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the smaller of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @see Math#min(float, float)
     */
    public static Float16 min(Float16 a, Float16 b) {
        return valueOf(Math.min(a.floatValue(), b.floatValue()));
    }

    // Skipping for now
    // public Optional<Float16> describeConstable()
    // public Float16 resolveConstantDesc(MethodHandles.Lookup lookup)

    /*
     * Note: for the basic arithmetic operations {+, -, *, /} and
     * square root, among binary interchange formats (binary16,
     * binary32 a.k.a. float, binary64 a.k.a double, etc.) the "2p + 2"
     * property holds. That is, if one format has p bits of precision,
     * if the next larger format has at least 2p + 2 bits of
     * precision, arithmetic on the smaller format can be implemented by:
     *
     * 1) converting each argument to the wider format
     * 2) performing the operation in the wider format
     * 3) converting the result from 2) to the narrower format
     *
     * For example, this property hold between the formats used for the
     * float and double types. Therefore, the following is a valid
     * implementation of a float addition:
     *
     * float add(float addend, float augend) {
     *     return (float)((double)addend + (double)augend);
     * }
     *
     * The same property holds between the float16 format and
     * float. Therefore, the software implementations of Float16 {+,
     * -, *, /} and square root below use the technique of widening
     * the Float16 arguments to float, performing the operation in
     * float arithmetic, and then rounding the float result to
     * Float16.
     *
     * For discussion and derivation of this property see:
     *
     * "When Is Double Rounding Innocuous?" by Samuel Figueroa
     * ACM SIGNUM Newsletter, Volume 30 Issue 3, pp 21-26
     * https://dl.acm.org/doi/pdf/10.1145/221332.221334
     *
     * Figueroa's write-up refers to lecture notes by W. Kahan. Those
     * lectures notes are assumed to be these ones by Kahan and
     * others:
     *
     * https://www.arithmazium.org/classroom/lib/Lecture_08_notes_slides.pdf
     * https://www.arithmazium.org/classroom/lib/Lecture_09_notes_slides.pdf
     */

    /**
     * Adds two {@code Float16} values together as per the {@code +}
     * operator semantics using the round to nearest rounding policy.
     *
     * The handling of signed zeros, NaNs, infinities, and other
     * special cases by this method is the same as for the handling of
     * those cases by the built-in {@code +} operator for
     * floating-point addition (JLS {@jls 15.18.2}).
     *
     * @apiNote This method corresponds to the addition operation
     * defined in IEEE 754.
     *
     * @param addend the first operand
     * @param augend the second operand
     * @return the sum of the operands
     *
     * @jls 15.4 Floating-point Expressions
     * @jls 15.18.2 Additive Operators (+ and -) for Numeric Types
     */
    public static Float16 add(Float16 addend, Float16 augend) {
        return valueOf(addend.floatValue() + augend.floatValue());
    }

    /**
     * Subtracts two {@code Float16} values as per the {@code -}
     * operator semantics using the round to nearest rounding policy.
     *
     * The handling of signed zeros, NaNs, infinities, and other
     * special cases by this method is the same as for the handling of
     * those cases by the built-in {@code -} operator for
     * floating-point subtraction (JLS {@jls 15.18.2}).
     *
     * @apiNote This method corresponds to the subtraction operation
     * defined in IEEE 754.
     *
     * @param minuend the first operand
     * @param  subtrahend the second operand
     * @return the difference of the operands
     *
     * @jls 15.4 Floating-point Expressions
     * @jls 15.18.2 Additive Operators (+ and -) for Numeric Types
     */
    public static Float16 subtract(Float16 minuend, Float16 subtrahend) {
        return valueOf(minuend.floatValue() - subtrahend.floatValue());
    }

    /**
     * Multiplies two {@code Float16} values as per the {@code *}
     * operator semantics using the round to nearest rounding policy.
     *
     * The handling of signed zeros, NaNs, and infinities, other
     * special cases by this method is the same as for the handling of
     * those cases by the built-in {@code *} operator for
     * floating-point multiplication (JLS {@jls 15.17.1}).
     *
     * @apiNote This method corresponds to the multiplication
     * operation defined in IEEE 754.
     *
     * @param multiplier the first operand
     * @param multiplicand the second operand
     * @return the product of the operands
     *
     * @jls 15.4 Floating-point Expressions
     * @jls 15.17.1 Multiplication Operator *
     */
    public static Float16 multiply(Float16 multiplier, Float16 multiplicand) {
        return valueOf(multiplier.floatValue() * multiplicand.floatValue());
    }

    /**
     * Divides two {@code Float16} values as per the {@code /}
     * operator semantics using the round to nearest rounding policy.
     *
     * The handling of signed zeros, NaNs, and infinities, other
     * special cases by this method is the same as for the handling of
     * those cases by the built-in {@code /} operator for
     * floating-point division (JLS {@jls 15.17.2}).
     *
     * @apiNote This method corresponds to the division
     * operation defined in IEEE 754.
     *
     * @param dividend the first operand
     * @param divisor the second operand
     * @return the quotient of the operands
     *
     * @jls 15.4 Floating-point Expressions
     * @jls 15.17.2 Division Operator /
     */
    public static Float16 divide(Float16 dividend, Float16 divisor) {
        return valueOf(dividend.floatValue() / divisor.floatValue());
    }

    /**
     * {@return the square root of the operand} The square root is
     * computed using the round to nearest rounding policy.
     *
     * The handling of zeros, NaN, infinities, and negative arguments
     * by this method is analogous to the handling of those cases by
     * {@link Math#sqrt(double)}.
     *
     * @apiNote
     * This method corresponds to the squareRoot operation defined in
     * IEEE 754.
     *
     * @param radicand the argument to have its square root taken
     *
     * @see Math#sqrt(double)
     */
    public static Float16 sqrt(Float16 radicand) {
        return Float16Math.sqrt(Float16.class, radicand,
            (_radicand) -> {
                // Rounding path of sqrt(Float16 -> double) -> Float16 is fine
                // for preserving the correct final value. The conversion
                // Float16 -> double preserves the exact numerical value. The
                // conversion of double -> Float16 also benefits from the
                // 2p+2 property of IEEE 754 arithmetic.
               return valueOf(Math.sqrt(_radicand.doubleValue()));
            }
        );
    }

    /**
     * Returns the fused multiply add of the three arguments; that is,
     * returns the exact product of the first two arguments summed
     * with the third argument and then rounded once to the nearest
     * {@code Float16}.
     *
     * The handling of zeros, NaN, infinities, and other special cases
     * by this method is analogous to the handling of those cases by
     * {@link Math#fma(float, float, float)}.
     *
     * @apiNote This method corresponds to the fusedMultiplyAdd
     * operation defined in IEEE 754.
     *
     * @param a a value
     * @param b a value
     * @param c a value
     *
     * @return (<i>a</i>&nbsp;&times;&nbsp;<i>b</i>&nbsp;+&nbsp;<i>c</i>)
     * computed, as if with unlimited range and precision, and rounded
     * once to the nearest {@code Float16} value
     *
     * @see Math#fma(float, float, float)
     * @see Math#fma(double, double, double)
     */
    public static Float16 fma(Float16 a, Float16 b, Float16 c) {
        /*
         * The double format has sufficient precision that a Float16
         * fma can be computed by doing the arithmetic in double, with
         * one rounding error for the sum, and then a second rounding
         * error to round the product-sum to Float16. In pseudocode,
         * this method is equivalent to the following code, assuming
         * casting was defined between Float16 and double:
         *
         * double product = (double)a * (double)b;  // Always exact
         * double productSum = product + (double)c;
         * return (Float16)produdctSum;
         *
         * (Note that a similar relationship does *not* hold between
         * the double format and computing a float fma.)
         *
         * Below is a sketch of the proof that simple double
         * arithmetic can be used to implement a correctly rounded
         * Float16 fma.
         *
         * ----------------------
         *
         * As preliminaries, the handling of NaN and Infinity
         * arguments falls out as a consequence of general operation
         * of non-finite values by double * and +. Any NaN argument to
         * fma will lead to a NaN result, infinities will propagate or
         * get turned into NaN as appropriate, etc.
         *
         * One or more zero arguments are also handled correctly,
         * including the propagation of the sign of zero if all three
         * arguments are zero.
         *
         * The double format has 53 logical bits of precision and its
         * exponent range goes from -1022 to 1023. The Float16 format
         * has 11 bits of logical precision and its exponent range
         * goes from -14 to 15. Therefore, the individual powers of 2
         * representable in the Float16 format range from the
         * subnormal 2^(-24), MIN_VALUE, to 2^15, the leading bit
         * position of MAX_VALUE.
         *
         * In cases where the numerical value of (a * b) + c is
         * computed exactly in a double, after a single rounding to
         * Float16, the result is necessarily correct since the one
         * double -> Float16 conversion is the only source of
         * numerical error. The operation as implemented in those
         * cases would be equivalent to rounding the infinitely precise
         * value to the result format, etc.
         *
         * However, for some inputs, the intermediate product-sum will
         * *not* be exact and additional analysis is needed to justify
         * not having any corrective computation to compensate for
         * intermediate rounding errors.
         *
         * The following analysis will rely on the range of bit
         * positions representable in the intermediate
         * product-sum.
         *
         * For the product a*b of Float16 inputs, the range of
         * exponents for nonzero finite results goes from 2^(-48)
         * (from MIN_VALUE squared) to 2^31 (from the exact value of
         * MAX_VALUE squared). This full range of exponent positions,
         * (31 -(-48) + 1 ) = 80 exceeds the precision of
         * double. However, only the product a*b can exceed the
         * exponent range of Float16. Therefore, there are three main
         * cases to consider:
         *
         * 1) Large exponent product, exponent > Float16.MAX_EXPONENT
         *
         * The magnitude of the overflow threshold for Float16 is:
         *
         * MAX_VALUE + 1/2 * ulp(MAX_VALUE) =  0x1.ffcp15 + 0x0.002p15 = 0x1.ffep15
         *
         * Therefore, for any product greater than or equal in
         * magnitude to (0x1.ffep15 + MAX_VALUE) = 0x1.ffdp16, the
         * final fma result will certainly overflow to infinity (under
         * round to nearest) since adding in c = -MAX_VALUE will still
         * be at or above the overflow threshold.
         *
         * If the exponent of the product is 15 or 16, the smallest
         * subnormal Float16 is 2^-24 and the ~40 bit wide range bit
         * positions would fit in a single double exactly.
         *
         * 2) Exponent of product is within the range of _normal_
         * Float16 values; Float16.MIN_EXPONENT <=  exponent <= Float16.MAX_EXPONENT
         *
         * The exact product has at most 22 contiguous bits in its
         * logical significand. The third number being added in has at
         * most 11 contiguous bits in its significand and the lowest
         * bit position that could be set is 2^(-24). Therefore, when
         * the product has the maximum in-range exponent, 2^15, a
         * single double has enough precision to hold down to the
         * smallest subnormal bit position, 15 - (-24) + 1 = 40 <
         * 53. If the product was large and rounded up, increasing the
         * exponent, when the third operand was added, this would
         * cause the exponent to go up to 16, which is within the
         * range of double, so the product-sum is exact and will be
         * correct when rounded to Float16.
         *
         * 3) Exponent of product is in the range of subnormal values or smaller,
         * exponent < Float16.MIN_EXPONENT
         *
         * The smallest exponent possible in a product is 2^(-48).
         * For moderately sized Float16 values added to the product,
         * with an exponent of about 4, the sum will not be
         * exact. Therefore, an analysis is needed to determine if the
         * double-rounding is benign or would lead to a different
         * final Float16 result. Double rounding can lead to a
         * different result in two cases:
         *
         * 1) The first rounding from the exact value to the extended
         * precision (here `double`) happens to be directed _toward_ 0
         * to a value exactly midway between two adjacent working
         * precision (here `Float16`) values, followed by a second
         * rounding from there which again happens to be directed
         * _toward_ 0 to one of these values (the one with lesser
         * magnitude).  A single rounding from the exact value to the
         * working precision, in contrast, rounds to the value with
         * larger magnitude.
         *
         * 2) Symmetrically, the first rounding to the extended
         * precision happens to be directed _away_ from 0 to a value
         * exactly midway between two adjacent working precision
         * values, followed by a second rounding from there which
         * again happens to be directed _away_ from 0 to one of these
         * values (the one with larger magnitude).  However, a single
         * rounding from the exact value to the working precision
         * rounds to the value with lesser magnitude.
         *
         * If the double rounding occurs in other cases, it is
         * innocuous, returning the same value as a single rounding to
         * the final format. Therefore, it is sufficient to show that
         * the first rounding to double does not occur at the midpoint
         * of two adjacent Float16 values:
         *
         * 1) If a, b and c have the same sign, the sum a*b + c has a
         * significand with a large gap of 20 or more 0s between the
         * bits of the significand of c to the left (at most 11 bits)
         * and those of the product a*b to the right (at most 22
         * bits).  The rounding bit for the final working precision of
         * `float16` is the leftmost 0 in the gap.
         *
         *   a) If rounding to `double` is directed toward 0, all the
         *   0s in the gap are preserved, thus the `Float16` rounding
         *   bit is unaffected and remains 0. This means that the
         *   `double` value is _not_ the midpoint of two adjacent
         *   `float16` values, so double rounding is harmless.
         *
         *   b) If rounding to `double` is directed away form 0, the
         *   rightmost 0 in the gap might be replaced by a 1, but the
         *   others are unaffected, including the `float16` rounding
         *   bit. Again, this shows that the `double` value is _not_
         *   the midpoint of two adjacent `float16` values, and double
         *   rounding is innocuous.
         *
         * 2) If a, b and c have opposite signs, in the sum a*b + c
         * the long gap of 0s above is replaced by a long gap of
         * 1s. The `float16` rounding bit is the leftmost 1 in the
         * gap, or the second leftmost 1 iff c is a power of 2. In
         * both cases, the rounding bit is followed by at least
         * another 1.
         *
         *   a) If rounding to `double` is directed toward 0, the
         *   `float16` rounding bit and its follower are preserved and
         *   both 1, so the `double` value is _not_ the midpoint of
         *   two adjacent `float16` values, and double rounding is
         *   harmless.
         *
         *   b) If rounding to `double` is directed away from 0, the
         *   `float16` rounding bit and its follower are either
         *   preserved (both 1), or both switch to 0. Either way, the
         *   `double` value is again _not_ the midpoint of two
         *   adjacent `float16` values, and double rounding is
         *   harmless.
         */

         return Float16Math.fma(Float16.class, a, b, c,
                (_a, _b, _c) -> {
                    // product is numerically exact in float before the cast to
                    // double; not necessary to widen to double before the
                    // multiply.
                    double product = (double)(_a.floatValue() * _b.floatValue());
                    return valueOf(product + _c.doubleValue());
                });
    }

    /**
     * {@return the negation of the argument}
     *
     * Special cases:
     * <ul>
     * <li> If the argument is zero, the result is a zero with the
     * opposite sign as the argument.
     * <li> If the argument is infinite, the result is an infinity
     * with the opposite sign as the argument.
     * <li> If the argument is a NaN, the result is a NaN.
     * </ul>
     *
     * @apiNote
     * This method corresponds to the negate operation defined in IEEE
     * 754.
     *
     * @param f16 the value to be negated
     * @jls 15.15.4 Unary Minus Operator {@code -}
     */
    public static Float16 negate(Float16 f16) {
        // Negate sign bit only. Per IEEE 754-2019 section 5.5.1,
        // negate is a bit-level operation and not a logical
        // operation. Therefore, in this case do _not_ use the float
        // unary minus as an implementation as that is not guaranteed
        // to flip the sign bit of a NaN.
        return shortBitsToFloat16((short)(f16.value ^ (short)0x0000_8000));
    }

    /**
     * {@return the absolute value of the argument}
     *
     * The handling of zeros, NaN, and infinities by this method is
     * analogous to the handling of those cases by {@link
     * Math#abs(float)}.
     *
     * @param f16 the argument whose absolute value is to be determined
     *
     * @see Math#abs(float)
     * @see Math#abs(double)
     */
    public static Float16 abs(Float16 f16) {
        // Zero out sign bit. Per IEE 754-2019 section 5.5.1, abs is a
        // bit-level operation and not a logical operation.
        return shortBitsToFloat16((short)(f16.value & (short)0x0000_7FFF));
    }

    /**
     * Returns the unbiased exponent used in the representation of a
     * {@code Float16}.
     *
     * <ul>
     * <li>If the argument is NaN or infinite, then the result is
     * {@link Float16#MAX_EXPONENT} + 1.
     * <li>If the argument is zero or subnormal, then the result is
     * {@link Float16#MIN_EXPONENT} - 1.
     * </ul>
     *
     * @apiNote
     * This method is analogous to the logB operation defined in IEEE
     * 754, but returns a different value on subnormal arguments.
     *
     * @param f16 a {@code Float16} value
     * @return the unbiased exponent of the argument
     *
     * @see Math#getExponent(float)
     * @see Math#getExponent(double)
     */
    public static int getExponent(Float16 f16) {
        return getExponent0(f16.value);
    }

    /**
     * From the bitwise representation of a float16, mask out exponent
     * bits, shift to the right and then subtract out float16's bias
     * adjust, 15, to get true exponent value.
     */
    /*package*/ static int getExponent0(short bits) {
        // package private to be usable in java.lang.Float.
        int bin16ExpBits     = 0x0000_7c00 & bits;     // Five exponent bits.
        return (bin16ExpBits >> (PRECISION - 1)) - 15;
    }

    /**
     * Returns the size of an ulp of the argument.  An ulp, unit in
     * the last place, of a {@code Float16} value is the positive
     * distance between this floating-point value and the {@code
     * Float16} value next larger in magnitude.  Note that for non-NaN
     * <i>x</i>, <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive or negative infinity, then the
     * result is positive infinity.
     * <li> If the argument is positive or negative zero, then the result is
     * {@code Float16.MIN_VALUE}.
     * <li> If the argument is &plusmn;{@code Float16.MAX_VALUE}, then
     * the result is equal to 2<sup>5</sup>, 32.0.
     * </ul>
     *
     * @param f16 the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     *
     * @see Math#ulp(float)
     * @see Math#ulp(double)
     */
    public static Float16 ulp(Float16 f16) {
        int exp = getExponent(f16);

        return switch(exp) {
        case MAX_EXPONENT + 1 -> abs(f16);          // NaN or infinity
        case MIN_EXPONENT - 1 -> Float16.MIN_VALUE; // zero or subnormal
        default -> {
            assert exp <= MAX_EXPONENT && exp >= MIN_EXPONENT;
            // ulp(x) is usually 2^(SIGNIFICAND_WIDTH-1)*(2^ilogb(x))
            // Let float -> float16 conversion handle encoding issues.
            yield scalb(valueOf(1), exp - (PRECISION - 1));
        }
        };
    }

    /**
     * Returns the floating-point value adjacent to {@code v} in
     * the direction of positive infinity.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is positive infinity, the result is
     * positive infinity.
     *
     * <li> If the argument is zero, the result is
     * {@link #MIN_VALUE}
     *
     * </ul>
     *
     * @apiNote This method corresponds to the nextUp
     * operation defined in IEEE 754.
     *
     * @param v starting floating-point value
     * @return The adjacent floating-point value closer to positive
     * infinity.
     *
     * @see Math#nextUp(float)
     * @see Math#nextUp(double)
     */
    public static Float16 nextUp(Float16 v) {
        float f = v.floatValue();
        if (f < Float.POSITIVE_INFINITY) {
            if (f != 0) {
                int bits = float16ToRawShortBits(v);
                return shortBitsToFloat16((short) (bits + ((bits >= 0) ? 1 : -1)));
            }
            return MIN_VALUE;
        }
        return v; // v is NaN or +Infinity
    }

    /**
     * Returns the floating-point value adjacent to {@code v} in
     * the direction of negative infinity.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is negative infinity, the result is
     * negative infinity.
     *
     * <li> If the argument is zero, the result is
     * -{@link #MIN_VALUE}
     *
     * </ul>
     *
     * @apiNote This method corresponds to the nextDown
     * operation defined in IEEE 754.
     *
     * @param v  starting floating-point value
     * @return The adjacent floating-point value closer to negative
     * infinity.
     *
     * @see Math#nextDown(float)
     * @see Math#nextDown(double)
     */
    public static Float16 nextDown(Float16 v) {
        float f = v.floatValue();
        if (f > Float.NEGATIVE_INFINITY) {
            if (f != 0) {
                int bits = float16ToRawShortBits(v);
                return shortBitsToFloat16((short) (bits - ((bits >= 0) ? 1 : -1)));
            }
            return negate(MIN_VALUE);
        }
        return v; // v is NaN or -Infinity
    }

    /**
     * Returns {@code v} &times; 2<sup>{@code scaleFactor}</sup>
     * rounded as if performed by a single correctly rounded
     * floating-point multiply.  If the exponent of the result is
     * between {@link Float16#MIN_EXPONENT} and {@link
     * Float16#MAX_EXPONENT}, the answer is calculated exactly.  If the
     * exponent of the result would be larger than {@code
     * Float16.MAX_EXPONENT}, an infinity is returned.  Note that if the
     * result is subnormal, precision may be lost; that is, when
     * {@code scalb(x, n)} is subnormal, {@code scalb(scalb(x, n),
     * -n)} may not equal <i>x</i>.  When the result is non-NaN, the
     * result has the same sign as {@code v}.
     *
     * <p>Special cases:
     * <ul>
     * <li> If the first argument is NaN, NaN is returned.
     * <li> If the first argument is infinite, then an infinity of the
     * same sign is returned.
     * <li> If the first argument is zero, then a zero of the same
     * sign is returned.
     * </ul>
     *
     * @apiNote This method corresponds to the scaleB operation
     * defined in IEEE 754.
     *
     * @param v number to be scaled by a power of two.
     * @param scaleFactor power of 2 used to scale {@code v}
     * @return {@code v} &times; 2<sup>{@code scaleFactor}</sup>
     *
     * @see Math#scalb(float, int)
     * @see Math#scalb(double, int)
     */
    public static Float16 scalb(Float16 v, int scaleFactor) {
        // magnitude of a power of two so large that scaling a finite
        // nonzero value by it would be guaranteed to over or
        // underflow; due to rounding, scaling down takes an
        // additional power of two which is reflected here
        final int MAX_SCALE = Float16.MAX_EXPONENT + -Float16.MIN_EXPONENT +
                Float16Consts.SIGNIFICAND_WIDTH + 1;

        // Make sure scaling factor is in a reasonable range
        scaleFactor = Math.max(Math.min(scaleFactor, MAX_SCALE), -MAX_SCALE);

        int DoubleConsts_EXP_BIAS = 1023;
        /*
         * Since + MAX_SCALE for Float16 fits well within the double
         * exponent range and + Float16 -> double conversion is exact
         * the multiplication below will be exact. Therefore, the
         * rounding that occurs when the double product is cast to
         * Float16 will be the correctly rounded Float16 result.
         */
        return valueOf(v.doubleValue()
                * Double.longBitsToDouble((long) (scaleFactor + DoubleConsts_EXP_BIAS) << Double.PRECISION - 1));
    }
    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.
     * This method does not require NaN {@code sign}
     * arguments to be treated as positive values; implementations are
     * permitted to treat some NaN arguments as positive and other NaN
     * arguments as negative to allow greater performance.
     *
     * @apiNote
     * This method corresponds to the copySign operation defined in
     * IEEE 754.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude}
     * and the sign of {@code sign}.
     *
     * @see Math#copySign(float, float)
     * @see Math#copySign(double, double)
     */
    public static Float16 copySign(Float16 magnitude, Float16 sign) {
        return shortBitsToFloat16((short) ((float16ToRawShortBits(sign) & SIGN_BIT_MASK) |
                                           (float16ToRawShortBits(magnitude) &
                                            (EXP_BIT_MASK | SIGNIF_BIT_MASK) )));
    }

    /**
     * Returns the signum function of the argument; zero if the argument
     * is zero, 1.0 if the argument is greater than zero, -1.0 if the
     * argument is less than zero.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive zero or negative zero, then the
     *      result is the same as the argument.
     * </ul>
     *
     * @param f the floating-point value whose signum is to be returned
     * @return the signum function of the argument
     *
     * @see Math#signum(float)
     * @see Math#signum(double)
     */
    public static Float16 signum(Float16 f) {
        return (f.floatValue() == 0.0f || isNaN(f)) ? f : copySign(valueOf(1), f);
    }

    // TODO: Temporary location for this functionality while Float16
    // resides in incubator.
    private static final class Float16ToDecimal {
        /*
         * For full details about this code see the following references:
         *
         * [1] Giulietti, "The Schubfach way to render doubles",
         *     https://drive.google.com/file/d/1gp5xv4CAa78SVgCeWfGqqI4FfYYYuNFb
         *
         * [2] IEEE Computer Society, "IEEE Standard for Floating-Point Arithmetic"
         *
         * [3] Bouvier & Zimmermann, "Division-Free Binary-to-Decimal Conversion"
         *
         * Divisions are avoided altogether for the benefit of those architectures
         * that do not provide specific machine instructions or where they are slow.
         * This is discussed in section 10 of [1].
         */

        /* The precision in bits */
        static final int P = PRECISION;

        /* Exponent width in bits */
        private static final int W = (Float16.SIZE - 1) - (P - 1);

        /* Minimum value of the exponent: -(2^(W-1)) - P + 3 */
        static final int Q_MIN = (-1 << (W - 1)) - P + 3;

        /* Maximum value of the exponent: 2^(W-1) - P */
        static final int Q_MAX = (1 << (W - 1)) - P;

        /* 10^(E_MIN - 1) <= MIN_VALUE < 10^E_MIN */
        static final int E_MIN = -7;

        /* 10^(E_MAX - 1) <= MAX_VALUE < 10^E_MAX */
        static final int E_MAX = 5;

        /* Threshold to detect tiny values, as in section 8.2.1 of [1] */
        static final int C_TINY = 2;

        /* The minimum and maximum k, as in section 8 of [1] */
        static final int K_MIN = -8;
        static final int K_MAX = 1;

        /* H is as in section 8.1 of [1] */
        static final int H = 5;

        /* Minimum value of the significand of a normal value: 2^(P-1) */
        private static final int C_MIN = 1 << (P - 1);

        /* Mask to extract the biased exponent */
        private static final int BQ_MASK = (1 << W) - 1;

        /* Mask to extract the fraction bits */
        private static final int T_MASK = (1 << (P - 1)) - 1;

        /* Used in rop() */
        private static final long MASK_32 = (1L << 32) - 1;

        /* Used for left-to-tight digit extraction */
        private static final int MASK_15 = (1 << 15) - 1;

        private static final int NON_SPECIAL    = 0;
        private static final int PLUS_ZERO      = 1;
        private static final int MINUS_ZERO     = 2;
        private static final int PLUS_INF       = 3;
        private static final int MINUS_INF      = 4;
        private static final int NAN            = 5;

        /*
         * Room for the longer of the forms
         *     -ddd.dd      H + 2 characters
         *     -ddddd.0     H + 3 characters
         *     -0.00ddddd   H + 5 characters
         *     -d.ddddE-e   H + 5 characters
         * where there are H digits d
         */
        public static final int MAX_CHARS = H + 5;

        private final byte[] bytes = new byte[MAX_CHARS];

        /* Index into bytes of rightmost valid character */
        private int index;

        private Float16ToDecimal() {
        }

        /**
         * Returns a string representation of the {@code Float16}
         * argument. All characters mentioned below are ASCII characters.
         *
         * @param   v   the {@code Float16} to be converted.
         * @return a string representation of the argument.
         * @see Float16#toString(Float16)
         */
        public static String toString(Float16 v) {
            return new Float16ToDecimal().toDecimalString(v);
        }

        /**
         * Appends the rendering of the {@code v} to {@code app}.
         *
         * <p>The outcome is the same as if {@code v} were first
         * {@link #toString(Float16) rendered} and the resulting string were then
         * {@link Appendable#append(CharSequence) appended} to {@code app}.
         *
         * @param v the {@code Float16} whose rendering is appended.
         * @param app the {@link Appendable} to append to.
         * @throws IOException If an I/O error occurs
         */
        public static Appendable appendTo(Float16 v, Appendable app)
                throws IOException {
            return new Float16ToDecimal().appendDecimalTo(v, app);
        }

        private String toDecimalString(Float16 v) {
            return switch (toDecimal(v)) {
                case NON_SPECIAL -> charsToString();
                case PLUS_ZERO -> "0.0";
                case MINUS_ZERO -> "-0.0";
                case PLUS_INF -> "Infinity";
                case MINUS_INF -> "-Infinity";
                default -> "NaN";
            };
        }

        private Appendable appendDecimalTo(Float16 v, Appendable app)
                throws IOException {
            switch (toDecimal(v)) {
                case NON_SPECIAL:
                    char[] chars = new char[index + 1];
                    for (int i = 0; i < chars.length; ++i) {
                        chars[i] = (char) bytes[i];
                    }
                    if (app instanceof StringBuilder builder) {
                        return builder.append(chars);
                    }
                    if (app instanceof StringBuffer buffer) {
                        return buffer.append(chars);
                    }
                    for (char c : chars) {
                        app.append(c);
                    }
                    return app;
                case PLUS_ZERO: return app.append("0.0");
                case MINUS_ZERO: return app.append("-0.0");
                case PLUS_INF: return app.append("Infinity");
                case MINUS_INF: return app.append("-Infinity");
                default: return app.append("NaN");
            }
        }

        /*
         * Returns
         *     PLUS_ZERO       iff v is 0.0
         *     MINUS_ZERO      iff v is -0.0
         *     PLUS_INF        iff v is POSITIVE_INFINITY
         *     MINUS_INF       iff v is NEGATIVE_INFINITY
         *     NAN             iff v is NaN
         */
        private int toDecimal(Float16 v) {
            /*
             * For full details see references [2] and [1].
             *
             * For finite v != 0, determine integers c and q such that
             *     |v| = c 2^q    and
             *     Q_MIN <= q <= Q_MAX    and
             *         either    2^(P-1) <= c < 2^P                 (normal)
             *         or        0 < c < 2^(P-1)  and  q = Q_MIN    (subnormal)
             */
            int bits = float16ToRawShortBits(v);
            int t = bits & T_MASK;
            int bq = (bits >>> P - 1) & BQ_MASK;
            if (bq < BQ_MASK) {
                index = -1;
                if (bits < 0) {
                    append('-');
                }
                if (bq != 0) {
                    /* normal value. Here mq = -q */
                    int mq = -Q_MIN + 1 - bq;
                    int c = C_MIN | t;
                    /* The fast path discussed in section 8.3 of [1] */
                    if (0 < mq & mq < P) {
                        int f = c >> mq;
                        if (f << mq == c) {
                            return toChars(f, 0);
                        }
                    }
                    return toDecimal(-mq, c, 0);
                }
                if (t != 0) {
                    /* subnormal value */
                    return t < C_TINY
                            ? toDecimal(Q_MIN, 10 * t, -1)
                            : toDecimal(Q_MIN, t, 0);
                }
                return bits == 0 ? PLUS_ZERO : MINUS_ZERO;
            }
            if (t != 0) {
                return NAN;
            }
            return bits > 0 ? PLUS_INF : MINUS_INF;
        }

        private int toDecimal(int q, int c, int dk) {
            /*
             * The skeleton corresponds to figure 7 of [1].
             * The efficient computations are those summarized in figure 9.
             * Also check the appendix.
             *
             * Here's a correspondence between Java names and names in [1],
             * expressed as approximate LaTeX source code and informally.
             * Other names are identical.
             * cb:     \bar{c}     "c-bar"
             * cbr:    \bar{c}_r   "c-bar-r"
             * cbl:    \bar{c}_l   "c-bar-l"
             *
             * vb:     \bar{v}     "v-bar"
             * vbr:    \bar{v}_r   "v-bar-r"
             * vbl:    \bar{v}_l   "v-bar-l"
             *
             * rop:    r_o'        "r-o-prime"
             */
            int out = c & 0x1;
            long cb = c << 2;
            long cbr = cb + 2;
            long cbl;
            int k;
            /*
             * flog10pow2(e) = floor(log_10(2^e))
             * flog10threeQuartersPow2(e) = floor(log_10(3/4 2^e))
             * flog2pow10(e) = floor(log_2(10^e))
             */
            if (c != C_MIN | q == Q_MIN) {
                /* regular spacing */
                cbl = cb - 2;
                k = MathUtils.flog10pow2(q);
            } else {
                /* irregular spacing */
                cbl = cb - 1;
                k = MathUtils.flog10threeQuartersPow2(q);
            }
            int h = q + MathUtils.flog2pow10(-k) + 33;

            /* g is as in the appendix */
            long g = MathUtils.g1(k) + 1;

            int vb = rop(g, cb << h);
            int vbl = rop(g, cbl << h);
            int vbr = rop(g, cbr << h);

            int s = vb >> 2;
            if (s >= 100) {
                /*
                 * For n = 5, m = 1 the discussion in section 10 of [1] shows
                 *     s' = floor(s / 10) = floor(s 52_429 / 2^19)
                 *
                 * sp10 = 10 s'
                 * tp10 = 10 t'
                 * upin    iff    u' = sp10 10^k in Rv
                 * wpin    iff    w' = tp10 10^k in Rv
                 * See section 9.3 of [1].
                 */
                int sp10 = 10 * (int) (s * 52_429L >>> 19);
                int tp10 = sp10 + 10;
                boolean upin = vbl + out <= sp10 << 2;
                boolean wpin = (tp10 << 2) + out <= vbr;
                if (upin != wpin) {
                    return toChars(upin ? sp10 : tp10, k);
                }
            }

            /*
             * 10 <= s < 100    or    s >= 100  and  u', w' not in Rv
             * uin    iff    u = s 10^k in Rv
             * win    iff    w = t 10^k in Rv
             * See section 9.3 of [1].
             */
            int t = s + 1;
            boolean uin = vbl + out <= s << 2;
            boolean win = (t << 2) + out <= vbr;
            if (uin != win) {
                /* Exactly one of u or w lies in Rv */
                return toChars(uin ? s : t, k + dk);
            }
            /*
             * Both u and w lie in Rv: determine the one closest to v.
             * See section 9.3 of [1].
             */
            int cmp = vb - (s + t << 1);
            return toChars(cmp < 0 || cmp == 0 && (s & 0x1) == 0 ? s : t, k + dk);
        }

        /*
         * Computes rop(cp g 2^(-95))
         * See appendix and figure 11 of [1].
         */
        private static int rop(long g, long cp) {
            long x1 = multiplyHigh(g, cp);
            long vbp = x1 >>> 31;
            return (int) (vbp | (x1 & MASK_32) + MASK_32 >>> 32);
        }

        /*
         * Formats the decimal f 10^e.
         */
        private int toChars(int f, int e) {
            /*
             * For details not discussed here see section 10 of [1].
             *
             * Determine len such that
             *     10^(len-1) <= f < 10^len
             */
            int len = MathUtils.flog10pow2(Integer.SIZE - numberOfLeadingZeros(f));
            if (f >= MathUtils.pow10(len)) {
                len += 1;
            }

            /*
             * Let fp and ep be the original f and e, respectively.
             * Transform f and e to ensure
             *     10^(H-1) <= f < 10^H
             *     fp 10^ep = f 10^(e-H) = 0.f 10^e
             */
            f *= (int)MathUtils.pow10(H - len);
            e += len;

            /*
             * The toChars?() methods perform left-to-right digits extraction
             * using ints, provided that the arguments are limited to 8 digits.
             * Therefore, split the H = 9 digits of f into:
             *     h = the most significant digit of f
             *     l = the last 4, least significant digits of f
             *
             * For n = 5, m = 4 the discussion in section 10 of [1] shows
             *     floor(f / 10^4) = floor(107_375L f / 2^30)
             */
            int h = (int) (f * 107_375L >>> 30);
            int l = f - 10_000 * h;

            if (0 < e && e <= 7) {
                return toChars1(h, l, e);
            }
            if (-3 < e && e <= 0) {
                return toChars2(h, l, e);
            }
            return toChars3(h, l, e);
        }

        private int toChars1(int h, int l, int e) {
            /*
             * 0 < e <= 7: plain format without leading zeroes.
             * Left-to-right digits extraction:
             * algorithm 1 in [3], with b = 10, k = 4, n = 15.
             */
            appendDigit(h);
            int y = y(l);
            int t;
            int i = 1;
            for (; i < e; ++i) {
                t = 10 * y;
                appendDigit(t >>> 15);
                y = t & MASK_15;
            }
            append('.');
            for (; i <= 4; ++i) {
                t = 10 * y;
                appendDigit(t >>> 15);
                y = t & MASK_15;
            }
            /*
             * As H = 5 < 7, where 7 is the threshold for plain format without
             * leading zeros, it can happen that the 2nd loop above is not executed.
             * The following line ensures the presence of a digit to the right
             * of the decimal point.
             */
            appendDigit(0);
            removeTrailingZeroes();
            return NON_SPECIAL;
        }

        private int toChars2(int h, int l, int e) {
            /* -3 < e <= 0: plain format with leading zeroes */
            appendDigit(0);
            append('.');
            for (; e < 0; ++e) {
                appendDigit(0);
            }
            appendDigit(h);
            append4Digits(l);
            removeTrailingZeroes();
            return NON_SPECIAL;
        }

        private int toChars3(int h, int l, int e) {
            /* -3 >= e | e > 7: computerized scientific notation */
            appendDigit(h);
            append('.');
            append4Digits(l);
            removeTrailingZeroes();
            exponent(e - 1);
            return NON_SPECIAL;
        }

        private void append4Digits(int m) {
            /*
             * Left-to-right digits extraction:
             * algorithm 1 in [3], with b = 10, k = 4, n = 15.
             */
            int y = y(m);
            for (int i = 0; i < 4; ++i) {
                int t = 10 * y;
                appendDigit(t >>> 15);
                y = t & MASK_15;
            }
        }

        private void removeTrailingZeroes() {
            while (bytes[index] == '0') {
                --index;
            }
            /* ... but do not remove the one directly to the right of '.' */
            if (bytes[index] == '.') {
                ++index;
            }
        }

        private int y(int a) {
            /*
             * Algorithm 1 in [3] needs computation of
             *     floor((a + 1) 2^n / b^k) - 1
             * with a < 10^4, b = 10, k = 4, n = 15.
             * Noting that
             *     (a + 1) 2^n <= 10^4 2^15 < 10^9
             * For n = 9, m = 4 the discussion in section 10 of [1] leads to:
             */
            return (int) (((a + 1) << 15) * 1_759_218_605L >>> 44) - 1;
        }

        private void exponent(int e) {
            append('E');
            if (e < 0) {
                append('-');
                e = -e;
            }
            appendDigit(e);
        }

        private void append(int c) {
            bytes[++index] = (byte) c;
        }

        private void appendDigit(int d) {
            bytes[++index] = (byte) ('0' + d);
        }

        /* Using the deprecated constructor enhances performance */
        @SuppressWarnings("deprecation")
        private String charsToString() {
            return new String(bytes, 0, 0, index + 1);
        }

    }

    /* TODO Temporary hack while Float16 resides in incubator */

    /**
     * This class exposes package private utilities for other classes.
     * Thus, all methods are assumed to be invoked with correct arguments,
     * so these are not checked at all.
     */
    private static final class MathUtils {
        /*
         * For full details about this code see the following reference:
         *
         *     Giulietti, "The Schubfach way to render doubles",
         *     https://drive.google.com/file/d/1gp5xv4CAa78SVgCeWfGqqI4FfYYYuNFb
         */

        /*
         * The boundaries for k in g0(int) and g1(int).
         * K_MIN must be DoubleToDecimal.K_MIN or less.
         * K_MAX must be DoubleToDecimal.K_MAX or more.
         */
        static final int K_MIN = -8;
        static final int K_MAX = 1;

        /* Must be DoubleToDecimal.H or more */
        static final int H = 17;

        /* C_10 = floor(log10(2) * 2^Q_10), A_10 = floor(log10(3/4) * 2^Q_10) */
        private static final int Q_10 = 41;
        private static final long C_10 = 661_971_961_083L;
        private static final long A_10 = -274_743_187_321L;

        /* C_2 = floor(log2(10) * 2^Q_2) */
        private static final int Q_2 = 38;
        private static final long C_2 = 913_124_641_741L;

        private MathUtils() {
            throw new RuntimeException("not supposed to be instantiated.");
        }

        /* The first powers of 10. The last entry must be 10^(DoubleToDecimal.H) */
        private static final long[] pow10 = {
                1L,
                10L,
                100L,
                1_000L,
                10_000L,
                100_000L,
                1_000_000L,
                10_000_000L,
                100_000_000L,
                1_000_000_000L,
                10_000_000_000L,
                100_000_000_000L,
                1_000_000_000_000L,
                10_000_000_000_000L,
                100_000_000_000_000L,
                1_000_000_000_000_000L,
                10_000_000_000_000_000L,
                100_000_000_000_000_000L,
        };

        /**
         * Returns 10<sup>{@code e}</sup>.
         *
         * @param e The exponent which must meet
         *          0 &le; {@code e} &le; {@link #H}.
         * @return 10<sup>{@code e}</sup>.
         */
        static long pow10(int e) {
            return pow10[e];
        }

        /**
         * Returns the unique integer <i>k</i> such that
         * 10<sup><i>k</i></sup> &le; 2<sup>{@code e}</sup>
         * &lt; 10<sup><i>k</i>+1</sup>.
         * <p>
         * The result is correct when |{@code e}| &le; 6_432_162.
         * Otherwise, the result is undefined.
         *
         * @param e The exponent of 2, which should meet
         *          |{@code e}| &le; 6_432_162 for safe results.
         * @return &lfloor;log<sub>10</sub>2<sup>{@code e}</sup>&rfloor;.
         */
        static int flog10pow2(int e) {
            return (int) (e * C_10 >> Q_10);
        }

        /**
         * Returns the unique integer <i>k</i> such that
         * 10<sup><i>k</i></sup> &le; 3/4 &middot; 2<sup>{@code e}</sup>
         * &lt; 10<sup><i>k</i>+1</sup>.
         * <p>
         * The result is correct when
         * -3_606_689 &le; {@code e} &le; 3_150_619.
         * Otherwise, the result is undefined.
         *
         * @param e The exponent of 2, which should meet
         *          -3_606_689 &le; {@code e} &le; 3_150_619 for safe results.
         * @return &lfloor;log<sub>10</sub>(3/4 &middot;
         * 2<sup>{@code e}</sup>)&rfloor;.
         */
        static int flog10threeQuartersPow2(int e) {
            return (int) (e * C_10 + A_10 >> Q_10);
        }

        /**
         * Returns the unique integer <i>k</i> such that
         * 2<sup><i>k</i></sup> &le; 10<sup>{@code e}</sup>
         * &lt; 2<sup><i>k</i>+1</sup>.
         * <p>
         * The result is correct when |{@code e}| &le; 1_838_394.
         * Otherwise, the result is undefined.
         *
         * @param e The exponent of 10, which should meet
         *          |{@code e}| &le; 1_838_394 for safe results.
         * @return &lfloor;log<sub>2</sub>10<sup>{@code e}</sup>&rfloor;.
         */
        static int flog2pow10(int e) {
            return (int) (e * C_2 >> Q_2);
        }

        /**
         * Let 10<sup>-{@code k}</sup> = <i>&beta;</i> 2<sup><i>r</i></sup>,
         * for the unique pair of integer <i>r</i> and real <i>&beta;</i> meeting
         * 2<sup>125</sup> &le; <i>&beta;</i> &lt; 2<sup>126</sup>.
         * Further, let <i>g</i> = &lfloor;<i>&beta;</i>&rfloor; + 1.
         * Split <i>g</i> into the higher 63 bits <i>g</i><sub>1</sub> and
         * the lower 63 bits <i>g</i><sub>0</sub>. Thus,
         * <i>g</i><sub>1</sub> =
         * &lfloor;<i>g</i> 2<sup>-63</sup>&rfloor;
         * and
         * <i>g</i><sub>0</sub> =
         * <i>g</i> - <i>g</i><sub>1</sub> 2<sup>63</sup>.
         * <p>
         * This method returns <i>g</i><sub>1</sub> while
         * {@link #g0(int)} returns <i>g</i><sub>0</sub>.
         * <p>
         * If needed, the exponent <i>r</i> can be computed as
         * <i>r</i> = {@code flog2pow10(-k)} - 125 (see {@link #flog2pow10(int)}).
         *
         * @param k The exponent of 10, which must meet
         *          {@link #K_MIN} &le; {@code e} &le; {@link #K_MAX}.
         * @return <i>g</i><sub>1</sub> as described above.
         */
        static long g1(int k) {
            return g[k - K_MIN << 1];
        }

        /**
         * Returns <i>g</i><sub>0</sub> as described in
         * {@link #g1(int)}.
         *
         * @param k The exponent of 10, which must meet
         *          {@link #K_MIN} &le; {@code e} &le; {@link #K_MAX}.
         * @return <i>g</i><sub>0</sub> as described in
         * {@link #g1(int)}.
         */
        static long g0(int k) {
            return g[k - K_MIN << 1 | 1];
        }

        /*
         * The precomputed values for g1(int) and g0(int).
         * The first entry must be for an exponent of K_MIN or less.
         * The last entry must be for an exponent of K_MAX or more.
         */
        private static final long[] g = {
                0x5F5E_1000_0000_0000L, 0x0000_0000_0000_0001L, //   -8
                0x4C4B_4000_0000_0000L, 0x0000_0000_0000_0001L, //   -7
                0x7A12_0000_0000_0000L, 0x0000_0000_0000_0001L, //   -6
                0x61A8_0000_0000_0000L, 0x0000_0000_0000_0001L, //   -5
                0x4E20_0000_0000_0000L, 0x0000_0000_0000_0001L, //   -4
                0x7D00_0000_0000_0000L, 0x0000_0000_0000_0001L, //   -3
                0x6400_0000_0000_0000L, 0x0000_0000_0000_0001L, //   -2
                0x5000_0000_0000_0000L, 0x0000_0000_0000_0001L, //   -1
                0x4000_0000_0000_0000L, 0x0000_0000_0000_0001L, //    0
                0x6666_6666_6666_6666L, 0x3333_3333_3333_3334L, //    1
        };

    }
}
