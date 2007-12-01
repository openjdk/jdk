/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.misc;

import sun.misc.FloatConsts;
import sun.misc.DoubleConsts;

/**
 * The class <code>FpUtils</code> contains static utility methods for
 * manipulating and inspecting <code>float</code> and
 * <code>double</code> floating-point numbers.  These methods include
 * functionality recommended or required by the IEEE 754
 * floating-point standard.
 *
 * @author Joseph D. Darcy
 */

public class FpUtils {
    /*
     * The methods in this class are reasonably implemented using
     * direct or indirect bit-level manipulation of floating-point
     * values.  However, having access to the IEEE 754 recommended
     * functions would obviate the need for most programmers to engage
     * in floating-point bit-twiddling.
     *
     * An IEEE 754 number has three fields, from most significant bit
     * to to least significant, sign, exponent, and significand.
     *
     *  msb                                lsb
     * [sign|exponent|  fractional_significand]
     *
     * Using some encoding cleverness, explained below, the high order
     * bit of the logical significand does not need to be explicitly
     * stored, thus "fractional_significand" instead of simply
     * "significand" in the figure above.
     *
     * For finite normal numbers, the numerical value encoded is
     *
     * (-1)^sign * 2^(exponent)*(1.fractional_significand)
     *
     * Most finite floating-point numbers are normalized; the exponent
     * value is reduced until the leading significand bit is 1.
     * Therefore, the leading 1 is redundant and is not explicitly
     * stored.  If a numerical value is so small it cannot be
     * normalized, it has a subnormal representation. Subnormal
     * numbers don't have a leading 1 in their significand; subnormals
     * are encoding using a special exponent value.  In other words,
     * the high-order bit of the logical significand can be elided in
     * from the representation in either case since the bit's value is
     * implicit from the exponent value.
     *
     * The exponent field uses a biased representation; if the bits of
     * the exponent are interpreted as a unsigned integer E, the
     * exponent represented is E - E_bias where E_bias depends on the
     * floating-point format.  E can range between E_min and E_max,
     * constants which depend on the floating-point format.  E_min and
     * E_max are -126 and +127 for float, -1022 and +1023 for double.
     *
     * The 32-bit float format has 1 sign bit, 8 exponent bits, and 23
     * bits for the significand (which is logically 24 bits wide
     * because of the implicit bit).  The 64-bit double format has 1
     * sign bit, 11 exponent bits, and 52 bits for the significand
     * (logically 53 bits).
     *
     * Subnormal numbers and zero have the special exponent value
     * E_min -1; the numerical value represented by a subnormal is:
     *
     * (-1)^sign * 2^(E_min)*(0.fractional_significand)
     *
     * Zero is represented by all zero bits in the exponent and all
     * zero bits in the significand; zero can have either sign.
     *
     * Infinity and NaN are encoded using the exponent value E_max +
     * 1.  Signed infinities have all significand bits zero; NaNs have
     * at least one non-zero significand bit.
     *
     * The details of IEEE 754 floating-point encoding will be used in
     * the methods below without further comment.  For further
     * exposition on IEEE 754 numbers, see "IEEE Standard for Binary
     * Floating-Point Arithmetic" ANSI/IEEE Std 754-1985 or William
     * Kahan's "Lecture Notes on the Status of IEEE Standard 754 for
     * Binary Floating-Point Arithmetic",
     * http://www.cs.berkeley.edu/~wkahan/ieee754status/ieee754.ps.
     *
     * Many of this class's methods are members of the set of IEEE 754
     * recommended functions or similar functions recommended or
     * required by IEEE 754R.  Discussion of various implementation
     * techniques for these functions have occurred in:
     *
     * W.J. Cody and Jerome T. Coonen, "Algorithm 772 Functions to
     * Support the IEEE Standard for Binary Floating-Point
     * Arithmetic," ACM Transactions on Mathematical Software,
     * vol. 19, no. 4, December 1993, pp. 443-451.
     *
     * Joseph D. Darcy, "Writing robust IEEE recommended functions in
     * ``100% Pure Java''(TM)," University of California, Berkeley
     * technical report UCB//CSD-98-1009.
     */

    /**
     * Don't let anyone instantiate this class.
     */
    private FpUtils() {}

    // Constants used in scalb
    static double twoToTheDoubleScaleUp = powerOfTwoD(512);
    static double twoToTheDoubleScaleDown = powerOfTwoD(-512);

    // Helper Methods

    // The following helper methods are used in the implementation of
    // the public recommended functions; they generally omit certain
    // tests for exception cases.

    /**
     * Returns unbiased exponent of a <code>double</code>.
     */
    public static int getExponent(double d){
        /*
         * Bitwise convert d to long, mask out exponent bits, shift
         * to the right and then subtract out double's bias adjust to
         * get true exponent value.
         */
        return (int)(((Double.doubleToRawLongBits(d) & DoubleConsts.EXP_BIT_MASK) >>
                      (DoubleConsts.SIGNIFICAND_WIDTH - 1)) - DoubleConsts.EXP_BIAS);
    }

    /**
     * Returns unbiased exponent of a <code>float</code>.
     */
    public static int getExponent(float f){
        /*
         * Bitwise convert f to integer, mask out exponent bits, shift
         * to the right and then subtract out float's bias adjust to
         * get true exponent value
         */
        return ((Float.floatToRawIntBits(f) & FloatConsts.EXP_BIT_MASK) >>
                (FloatConsts.SIGNIFICAND_WIDTH - 1)) - FloatConsts.EXP_BIAS;
    }

    /**
     * Returns a floating-point power of two in the normal range.
     */
    static double powerOfTwoD(int n) {
        assert(n >= DoubleConsts.MIN_EXPONENT && n <= DoubleConsts.MAX_EXPONENT);
        return Double.longBitsToDouble((((long)n + (long)DoubleConsts.EXP_BIAS) <<
                                        (DoubleConsts.SIGNIFICAND_WIDTH-1))
                                       & DoubleConsts.EXP_BIT_MASK);
    }

    /**
     * Returns a floating-point power of two in the normal range.
     */
    static float powerOfTwoF(int n) {
        assert(n >= FloatConsts.MIN_EXPONENT && n <= FloatConsts.MAX_EXPONENT);
        return Float.intBitsToFloat(((n + FloatConsts.EXP_BIAS) <<
                                     (FloatConsts.SIGNIFICAND_WIDTH-1))
                                    & FloatConsts.EXP_BIT_MASK);
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  Note that unlike the {@link
     * FpUtils#copySign(double, double) copySign} method, this method
     * does not require NaN <code>sign</code> arguments to be treated
     * as positive values; implementations are permitted to treat some
     * NaN arguments as positive and other NaN arguments as negative
     * to allow greater performance.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of <code>magnitude</code>
     * and the sign of <code>sign</code>.
     * @author Joseph D. Darcy
     */
    public static double rawCopySign(double magnitude, double sign) {
        return Double.longBitsToDouble((Double.doubleToRawLongBits(sign) &
                                        (DoubleConsts.SIGN_BIT_MASK)) |
                                       (Double.doubleToRawLongBits(magnitude) &
                                        (DoubleConsts.EXP_BIT_MASK |
                                         DoubleConsts.SIGNIF_BIT_MASK)));
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  Note that unlike the {@link
     * FpUtils#copySign(float, float) copySign} method, this method
     * does not require NaN <code>sign</code> arguments to be treated
     * as positive values; implementations are permitted to treat some
     * NaN arguments as positive and other NaN arguments as negative
     * to allow greater performance.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of <code>magnitude</code>
     * and the sign of <code>sign</code>.
     * @author Joseph D. Darcy
     */
    public static float rawCopySign(float magnitude, float sign) {
        return Float.intBitsToFloat((Float.floatToRawIntBits(sign) &
                                     (FloatConsts.SIGN_BIT_MASK)) |
                                    (Float.floatToRawIntBits(magnitude) &
                                     (FloatConsts.EXP_BIT_MASK |
                                      FloatConsts.SIGNIF_BIT_MASK)));
    }

    /* ***************************************************************** */

    /**
     * Returns <code>true</code> if the argument is a finite
     * floating-point value; returns <code>false</code> otherwise (for
     * NaN and infinity arguments).
     *
     * @param d the <code>double</code> value to be tested
     * @return <code>true</code> if the argument is a finite
     * floating-point value, <code>false</code> otherwise.
     */
    public static boolean isFinite(double d) {
        return Math.abs(d) <= DoubleConsts.MAX_VALUE;
    }

    /**
     * Returns <code>true</code> if the argument is a finite
     * floating-point value; returns <code>false</code> otherwise (for
     * NaN and infinity arguments).
     *
     * @param f the <code>float</code> value to be tested
     * @return <code>true</code> if the argument is a finite
     * floating-point value, <code>false</code> otherwise.
     */
     public static boolean isFinite(float f) {
        return Math.abs(f) <= FloatConsts.MAX_VALUE;
    }

    /**
     * Returns <code>true</code> if the specified number is infinitely
     * large in magnitude, <code>false</code> otherwise.
     *
     * <p>Note that this method is equivalent to the {@link
     * Double#isInfinite(double) Double.isInfinite} method; the
     * functionality is included in this class for convenience.
     *
     * @param   d   the value to be tested.
     * @return  <code>true</code> if the value of the argument is positive
     *          infinity or negative infinity; <code>false</code> otherwise.
     */
    public static boolean isInfinite(double d) {
        return Double.isInfinite(d);
    }

    /**
     * Returns <code>true</code> if the specified number is infinitely
     * large in magnitude, <code>false</code> otherwise.
     *
     * <p>Note that this method is equivalent to the {@link
     * Float#isInfinite(float) Float.isInfinite} method; the
     * functionality is included in this class for convenience.
     *
     * @param   f   the value to be tested.
     * @return  <code>true</code> if the argument is positive infinity or
     *          negative infinity; <code>false</code> otherwise.
     */
     public static boolean isInfinite(float f) {
         return Float.isInfinite(f);
    }

    /**
     * Returns <code>true</code> if the specified number is a
     * Not-a-Number (NaN) value, <code>false</code> otherwise.
     *
     * <p>Note that this method is equivalent to the {@link
     * Double#isNaN(double) Double.isNaN} method; the functionality is
     * included in this class for convenience.
     *
     * @param   d   the value to be tested.
     * @return  <code>true</code> if the value of the argument is NaN;
     *          <code>false</code> otherwise.
     */
    public static boolean isNaN(double d) {
        return Double.isNaN(d);
    }

    /**
     * Returns <code>true</code> if the specified number is a
     * Not-a-Number (NaN) value, <code>false</code> otherwise.
     *
     * <p>Note that this method is equivalent to the {@link
     * Float#isNaN(float) Float.isNaN} method; the functionality is
     * included in this class for convenience.
     *
     * @param   f   the value to be tested.
     * @return  <code>true</code> if the argument is NaN;
     *          <code>false</code> otherwise.
     */
     public static boolean isNaN(float f) {
        return Float.isNaN(f);
    }

    /**
     * Returns <code>true</code> if the unordered relation holds
     * between the two arguments.  When two floating-point values are
     * unordered, one value is neither less than, equal to, nor
     * greater than the other.  For the unordered relation to be true,
     * at least one argument must be a <code>NaN</code>.
     *
     * @param arg1      the first argument
     * @param arg2      the second argument
     * @return <code>true</code> if at least one argument is a NaN,
     * <code>false</code> otherwise.
     */
    public static boolean isUnordered(double arg1, double arg2) {
        return isNaN(arg1) || isNaN(arg2);
    }

    /**
     * Returns <code>true</code> if the unordered relation holds
     * between the two arguments.  When two floating-point values are
     * unordered, one value is neither less than, equal to, nor
     * greater than the other.  For the unordered relation to be true,
     * at least one argument must be a <code>NaN</code>.
     *
     * @param arg1      the first argument
     * @param arg2      the second argument
     * @return <code>true</code> if at least one argument is a NaN,
     * <code>false</code> otherwise.
     */
     public static boolean isUnordered(float arg1, float arg2) {
        return isNaN(arg1) || isNaN(arg2);
    }

    /**
     * Returns unbiased exponent of a <code>double</code>; for
     * subnormal values, the number is treated as if it were
     * normalized.  That is for all finite, non-zero, positive numbers
     * <i>x</i>, <code>scalb(<i>x</i>, -ilogb(<i>x</i>))</code> is
     * always in the range [1, 2).
     * <p>
     * Special cases:
     * <ul>
     * <li> If the argument is NaN, then the result is 2<sup>30</sup>.
     * <li> If the argument is infinite, then the result is 2<sup>28</sup>.
     * <li> If the argument is zero, then the result is -(2<sup>28</sup>).
     * </ul>
     *
     * @param d floating-point number whose exponent is to be extracted
     * @return unbiased exponent of the argument.
     * @author Joseph D. Darcy
     */
    public static int ilogb(double d) {
        int exponent = getExponent(d);

        switch (exponent) {
        case DoubleConsts.MAX_EXPONENT+1:       // NaN or infinity
            if( isNaN(d) )
                return (1<<30);         // 2^30
            else // infinite value
                return (1<<28);         // 2^28
        // break;

        case DoubleConsts.MIN_EXPONENT-1:       // zero or subnormal
            if(d == 0.0) {
                return -(1<<28);        // -(2^28)
            }
            else {
                long transducer = Double.doubleToRawLongBits(d);

                /*
                 * To avoid causing slow arithmetic on subnormals,
                 * the scaling to determine when d's significand
                 * is normalized is done in integer arithmetic.
                 * (there must be at least one "1" bit in the
                 * significand since zero has been screened out.
                 */

                // isolate significand bits
                transducer &= DoubleConsts.SIGNIF_BIT_MASK;
                assert(transducer != 0L);

                // This loop is simple and functional. We might be
                // able to do something more clever that was faster;
                // e.g. number of leading zero detection on
                // (transducer << (# exponent and sign bits).
                while (transducer <
                       (1L << (DoubleConsts.SIGNIFICAND_WIDTH - 1))) {
                    transducer *= 2;
                    exponent--;
                }
                exponent++;
                assert( exponent >=
                        DoubleConsts.MIN_EXPONENT - (DoubleConsts.SIGNIFICAND_WIDTH-1) &&
                        exponent < DoubleConsts.MIN_EXPONENT);
                return exponent;
            }
        // break;

        default:
            assert( exponent >= DoubleConsts.MIN_EXPONENT &&
                    exponent <= DoubleConsts.MAX_EXPONENT);
            return exponent;
        // break;
        }
    }

    /**
     * Returns unbiased exponent of a <code>float</code>; for
     * subnormal values, the number is treated as if it were
     * normalized.  That is for all finite, non-zero, positive numbers
     * <i>x</i>, <code>scalb(<i>x</i>, -ilogb(<i>x</i>))</code> is
     * always in the range [1, 2).
     * <p>
     * Special cases:
     * <ul>
     * <li> If the argument is NaN, then the result is 2<sup>30</sup>.
     * <li> If the argument is infinite, then the result is 2<sup>28</sup>.
     * <li> If the argument is zero, then the result is -(2<sup>28</sup>).
     * </ul>
     *
     * @param f floating-point number whose exponent is to be extracted
     * @return unbiased exponent of the argument.
     * @author Joseph D. Darcy
     */
     public static int ilogb(float f) {
        int exponent = getExponent(f);

        switch (exponent) {
        case FloatConsts.MAX_EXPONENT+1:        // NaN or infinity
            if( isNaN(f) )
                return (1<<30);         // 2^30
            else // infinite value
                return (1<<28);         // 2^28
        // break;

        case FloatConsts.MIN_EXPONENT-1:        // zero or subnormal
            if(f == 0.0f) {
                return -(1<<28);        // -(2^28)
            }
            else {
                int transducer = Float.floatToRawIntBits(f);

                /*
                 * To avoid causing slow arithmetic on subnormals,
                 * the scaling to determine when f's significand
                 * is normalized is done in integer arithmetic.
                 * (there must be at least one "1" bit in the
                 * significand since zero has been screened out.
                 */

                // isolate significand bits
                transducer &= FloatConsts.SIGNIF_BIT_MASK;
                assert(transducer != 0);

                // This loop is simple and functional. We might be
                // able to do something more clever that was faster;
                // e.g. number of leading zero detection on
                // (transducer << (# exponent and sign bits).
                while (transducer <
                       (1 << (FloatConsts.SIGNIFICAND_WIDTH - 1))) {
                    transducer *= 2;
                    exponent--;
                }
                exponent++;
                assert( exponent >=
                        FloatConsts.MIN_EXPONENT - (FloatConsts.SIGNIFICAND_WIDTH-1) &&
                        exponent < FloatConsts.MIN_EXPONENT);
                return exponent;
            }
        // break;

        default:
            assert( exponent >= FloatConsts.MIN_EXPONENT &&
                    exponent <= FloatConsts.MAX_EXPONENT);
            return exponent;
        // break;
        }
    }


    /*
     * The scalb operation should be reasonably fast; however, there
     * are tradeoffs in writing a method to minimize the worst case
     * performance and writing a method to minimize the time for
     * expected common inputs.  Some processors operate very slowly on
     * subnormal operands, taking hundreds or thousands of cycles for
     * one floating-point add or multiply as opposed to, say, four
     * cycles for normal operands.  For processors with very slow
     * subnormal execution, scalb would be fastest if written entirely
     * with integer operations; in other words, scalb would need to
     * include the logic of performing correct rounding of subnormal
     * values.  This could be reasonably done in at most a few hundred
     * cycles.  However, this approach may penalize normal operations
     * since at least the exponent of the floating-point argument must
     * be examined.
     *
     * The approach taken in this implementation is a compromise.
     * Floating-point multiplication is used to do most of the work;
     * but knowingly multiplying by a subnormal scaling factor is
     * avoided.  However, the floating-point argument is not examined
     * to see whether or not it is subnormal since subnormal inputs
     * are assumed to be rare.  At most three multiplies are needed to
     * scale from the largest to smallest exponent ranges (scaling
     * down, at most two multiplies are needed if subnormal scaling
     * factors are allowed).  However, in this implementation an
     * expensive integer remainder operation is avoided at the cost of
     * requiring five floating-point multiplies in the worst case,
     * which should still be a performance win.
     *
     * If scaling of entire arrays is a concern, it would probably be
     * more efficient to provide a double[] scalb(double[], int)
     * version of scalb to avoid having to recompute the needed
     * scaling factors for each floating-point value.
     */

    /**
     * Return <code>d</code> &times;
     * 2<sup><code>scale_factor</code></sup> rounded as if performed
     * by a single correctly rounded floating-point multiply to a
     * member of the double value set.  See <a
     * href="http://java.sun.com/docs/books/jls/second_edition/html/typesValues.doc.html#9208">&sect;4.2.3</a>
     * of the <a href="http://java.sun.com/docs/books/jls/html/">Java
     * Language Specification</a> for a discussion of floating-point
     * value sets.  If the exponent of the result is between the
     * <code>double</code>'s minimum exponent and maximum exponent,
     * the answer is calculated exactly.  If the exponent of the
     * result would be larger than <code>doubles</code>'s maximum
     * exponent, an infinity is returned.  Note that if the result is
     * subnormal, precision may be lost; that is, when <code>scalb(x,
     * n)</code> is subnormal, <code>scalb(scalb(x, n), -n)</code> may
     * not equal <i>x</i>.  When the result is non-NaN, the result has
     * the same sign as <code>d</code>.
     *
     *<p>
     * Special cases:
     * <ul>
     * <li> If the first argument is NaN, NaN is returned.
     * <li> If the first argument is infinite, then an infinity of the
     * same sign is returned.
     * <li> If the first argument is zero, then a zero of the same
     * sign is returned.
     * </ul>
     *
     * @param d number to be scaled by a power of two.
     * @param scale_factor power of 2 used to scale <code>d</code>
     * @return <code>d * </code>2<sup><code>scale_factor</code></sup>
     * @author Joseph D. Darcy
     */
    public static double scalb(double d, int scale_factor) {
        /*
         * This method does not need to be declared strictfp to
         * compute the same correct result on all platforms.  When
         * scaling up, it does not matter what order the
         * multiply-store operations are done; the result will be
         * finite or overflow regardless of the operation ordering.
         * However, to get the correct result when scaling down, a
         * particular ordering must be used.
         *
         * When scaling down, the multiply-store operations are
         * sequenced so that it is not possible for two consecutive
         * multiply-stores to return subnormal results.  If one
         * multiply-store result is subnormal, the next multiply will
         * round it away to zero.  This is done by first multiplying
         * by 2 ^ (scale_factor % n) and then multiplying several
         * times by by 2^n as needed where n is the exponent of number
         * that is a covenient power of two.  In this way, at most one
         * real rounding error occurs.  If the double value set is
         * being used exclusively, the rounding will occur on a
         * multiply.  If the double-extended-exponent value set is
         * being used, the products will (perhaps) be exact but the
         * stores to d are guaranteed to round to the double value
         * set.
         *
         * It is _not_ a valid implementation to first multiply d by
         * 2^MIN_EXPONENT and then by 2 ^ (scale_factor %
         * MIN_EXPONENT) since even in a strictfp program double
         * rounding on underflow could occur; e.g. if the scale_factor
         * argument was (MIN_EXPONENT - n) and the exponent of d was a
         * little less than -(MIN_EXPONENT - n), meaning the final
         * result would be subnormal.
         *
         * Since exact reproducibility of this method can be achieved
         * without any undue performance burden, there is no
         * compelling reason to allow double rounding on underflow in
         * scalb.
         */

        // magnitude of a power of two so large that scaling a finite
        // nonzero value by it would be guaranteed to over or
        // underflow; due to rounding, scaling down takes takes an
        // additional power of two which is reflected here
        final int MAX_SCALE = DoubleConsts.MAX_EXPONENT + -DoubleConsts.MIN_EXPONENT +
                              DoubleConsts.SIGNIFICAND_WIDTH + 1;
        int exp_adjust = 0;
        int scale_increment = 0;
        double exp_delta = Double.NaN;

        // Make sure scaling factor is in a reasonable range

        if(scale_factor < 0) {
            scale_factor = Math.max(scale_factor, -MAX_SCALE);
            scale_increment = -512;
            exp_delta = twoToTheDoubleScaleDown;
        }
        else {
            scale_factor = Math.min(scale_factor, MAX_SCALE);
            scale_increment = 512;
            exp_delta = twoToTheDoubleScaleUp;
        }

        // Calculate (scale_factor % +/-512), 512 = 2^9, using
        // technique from "Hacker's Delight" section 10-2.
        int t = (scale_factor >> 9-1) >>> 32 - 9;
        exp_adjust = ((scale_factor + t) & (512 -1)) - t;

        d *= powerOfTwoD(exp_adjust);
        scale_factor -= exp_adjust;

        while(scale_factor != 0) {
            d *= exp_delta;
            scale_factor -= scale_increment;
        }
        return d;
    }

    /**
     * Return <code>f </code>&times;
     * 2<sup><code>scale_factor</code></sup> rounded as if performed
     * by a single correctly rounded floating-point multiply to a
     * member of the float value set.  See <a
     * href="http://java.sun.com/docs/books/jls/second_edition/html/typesValues.doc.html#9208">&sect;4.2.3</a>
     * of the <a href="http://java.sun.com/docs/books/jls/html/">Java
     * Language Specification</a> for a discussion of floating-point
     * value set. If the exponent of the result is between the
     * <code>float</code>'s minimum exponent and maximum exponent, the
     * answer is calculated exactly.  If the exponent of the result
     * would be larger than <code>float</code>'s maximum exponent, an
     * infinity is returned.  Note that if the result is subnormal,
     * precision may be lost; that is, when <code>scalb(x, n)</code>
     * is subnormal, <code>scalb(scalb(x, n), -n)</code> may not equal
     * <i>x</i>.  When the result is non-NaN, the result has the same
     * sign as <code>f</code>.
     *
     *<p>
     * Special cases:
     * <ul>
     * <li> If the first argument is NaN, NaN is returned.
     * <li> If the first argument is infinite, then an infinity of the
     * same sign is returned.
     * <li> If the first argument is zero, then a zero of the same
     * sign is returned.
     * </ul>
     *
     * @param f number to be scaled by a power of two.
     * @param scale_factor power of 2 used to scale <code>f</code>
     * @return <code>f * </code>2<sup><code>scale_factor</code></sup>
     * @author Joseph D. Darcy
     */
     public static float scalb(float f, int scale_factor) {
        // magnitude of a power of two so large that scaling a finite
        // nonzero value by it would be guaranteed to over or
        // underflow; due to rounding, scaling down takes takes an
        // additional power of two which is reflected here
        final int MAX_SCALE = FloatConsts.MAX_EXPONENT + -FloatConsts.MIN_EXPONENT +
                              FloatConsts.SIGNIFICAND_WIDTH + 1;

        // Make sure scaling factor is in a reasonable range
        scale_factor = Math.max(Math.min(scale_factor, MAX_SCALE), -MAX_SCALE);

        /*
         * Since + MAX_SCALE for float fits well within the double
         * exponent range and + float -> double conversion is exact
         * the multiplication below will be exact. Therefore, the
         * rounding that occurs when the double product is cast to
         * float will be the correctly rounded float result.  Since
         * all operations other than the final multiply will be exact,
         * it is not necessary to declare this method strictfp.
         */
        return (float)((double)f*powerOfTwoD(scale_factor));
    }

    /**
     * Returns the floating-point number adjacent to the first
     * argument in the direction of the second argument.  If both
     * arguments compare as equal the second argument is returned.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li> If either argument is a NaN, then NaN is returned.
     *
     * <li> If both arguments are signed zeros, <code>direction</code>
     * is returned unchanged (as implied by the requirement of
     * returning the second argument if the arguments compare as
     * equal).
     *
     * <li> If <code>start</code> is
     * &plusmn;<code>Double.MIN_VALUE</code> and <code>direction</code>
     * has a value such that the result should have a smaller
     * magnitude, then a zero with the same sign as <code>start</code>
     * is returned.
     *
     * <li> If <code>start</code> is infinite and
     * <code>direction</code> has a value such that the result should
     * have a smaller magnitude, <code>Double.MAX_VALUE</code> with the
     * same sign as <code>start</code> is returned.
     *
     * <li> If <code>start</code> is equal to &plusmn;
     * <code>Double.MAX_VALUE</code> and <code>direction</code> has a
     * value such that the result should have a larger magnitude, an
     * infinity with same sign as <code>start</code> is returned.
     * </ul>
     *
     * @param start     starting floating-point value
     * @param direction value indicating which of
     * <code>start</code>'s neighbors or <code>start</code> should
     * be returned
     * @return The floating-point number adjacent to <code>start</code> in the
     * direction of <code>direction</code>.
     * @author Joseph D. Darcy
     */
    public static double nextAfter(double start, double direction) {
        /*
         * The cases:
         *
         * nextAfter(+infinity, 0)  == MAX_VALUE
         * nextAfter(+infinity, +infinity)  == +infinity
         * nextAfter(-infinity, 0)  == -MAX_VALUE
         * nextAfter(-infinity, -infinity)  == -infinity
         *
         * are naturally handled without any additional testing
         */

        // First check for NaN values
        if (isNaN(start) || isNaN(direction)) {
            // return a NaN derived from the input NaN(s)
            return start + direction;
        } else if (start == direction) {
            return direction;
        } else {        // start > direction or start < direction
            // Add +0.0 to get rid of a -0.0 (+0.0 + -0.0 => +0.0)
            // then bitwise convert start to integer.
            long transducer = Double.doubleToRawLongBits(start + 0.0d);

            /*
             * IEEE 754 floating-point numbers are lexicographically
             * ordered if treated as signed- magnitude integers .
             * Since Java's integers are two's complement,
             * incrementing" the two's complement representation of a
             * logically negative floating-point value *decrements*
             * the signed-magnitude representation. Therefore, when
             * the integer representation of a floating-point values
             * is less than zero, the adjustment to the representation
             * is in the opposite direction than would be expected at
             * first .
             */
            if (direction > start) { // Calculate next greater value
                transducer = transducer + (transducer >= 0L ? 1L:-1L);
            } else  { // Calculate next lesser value
                assert direction < start;
                if (transducer > 0L)
                    --transducer;
                else
                    if (transducer < 0L )
                        ++transducer;
                    /*
                     * transducer==0, the result is -MIN_VALUE
                     *
                     * The transition from zero (implicitly
                     * positive) to the smallest negative
                     * signed magnitude value must be done
                     * explicitly.
                     */
                    else
                        transducer = DoubleConsts.SIGN_BIT_MASK | 1L;
            }

            return Double.longBitsToDouble(transducer);
        }
    }

    /**
     * Returns the floating-point number adjacent to the first
     * argument in the direction of the second argument.  If both
     * arguments compare as equal, the second argument is returned.
     *
     * <p>
     * Special cases:
     * <ul>
     * <li> If either argument is a NaN, then NaN is returned.
     *
     * <li> If both arguments are signed zeros, a <code>float</code>
     * zero with the same sign as <code>direction</code> is returned
     * (as implied by the requirement of returning the second argument
     * if the arguments compare as equal).
     *
     * <li> If <code>start</code> is
     * &plusmn;<code>Float.MIN_VALUE</code> and <code>direction</code>
     * has a value such that the result should have a smaller
     * magnitude, then a zero with the same sign as <code>start</code>
     * is returned.
     *
     * <li> If <code>start</code> is infinite and
     * <code>direction</code> has a value such that the result should
     * have a smaller magnitude, <code>Float.MAX_VALUE</code> with the
     * same sign as <code>start</code> is returned.
     *
     * <li> If <code>start</code> is equal to &plusmn;
     * <code>Float.MAX_VALUE</code> and <code>direction</code> has a
     * value such that the result should have a larger magnitude, an
     * infinity with same sign as <code>start</code> is returned.
     * </ul>
     *
     * @param start     starting floating-point value
     * @param direction value indicating which of
     * <code>start</code>'s neighbors or <code>start</code> should
     * be returned
     * @return The floating-point number adjacent to <code>start</code> in the
     * direction of <code>direction</code>.
     * @author Joseph D. Darcy
     */
     public static float nextAfter(float start, double direction) {
        /*
         * The cases:
         *
         * nextAfter(+infinity, 0)  == MAX_VALUE
         * nextAfter(+infinity, +infinity)  == +infinity
         * nextAfter(-infinity, 0)  == -MAX_VALUE
         * nextAfter(-infinity, -infinity)  == -infinity
         *
         * are naturally handled without any additional testing
         */

        // First check for NaN values
        if (isNaN(start) || isNaN(direction)) {
            // return a NaN derived from the input NaN(s)
            return start + (float)direction;
        } else if (start == direction) {
            return (float)direction;
        } else {        // start > direction or start < direction
            // Add +0.0 to get rid of a -0.0 (+0.0 + -0.0 => +0.0)
            // then bitwise convert start to integer.
            int transducer = Float.floatToRawIntBits(start + 0.0f);

            /*
             * IEEE 754 floating-point numbers are lexicographically
             * ordered if treated as signed- magnitude integers .
             * Since Java's integers are two's complement,
             * incrementing" the two's complement representation of a
             * logically negative floating-point value *decrements*
             * the signed-magnitude representation. Therefore, when
             * the integer representation of a floating-point values
             * is less than zero, the adjustment to the representation
             * is in the opposite direction than would be expected at
             * first.
             */
            if (direction > start) {// Calculate next greater value
                transducer = transducer + (transducer >= 0 ? 1:-1);
            } else  { // Calculate next lesser value
                assert direction < start;
                if (transducer > 0)
                    --transducer;
                else
                    if (transducer < 0 )
                        ++transducer;
                    /*
                     * transducer==0, the result is -MIN_VALUE
                     *
                     * The transition from zero (implicitly
                     * positive) to the smallest negative
                     * signed magnitude value must be done
                     * explicitly.
                     */
                    else
                        transducer = FloatConsts.SIGN_BIT_MASK | 1;
            }

            return Float.intBitsToFloat(transducer);
        }
    }

    /**
     * Returns the floating-point value adjacent to <code>d</code> in
     * the direction of positive infinity.  This method is
     * semantically equivalent to <code>nextAfter(d,
     * Double.POSITIVE_INFINITY)</code>; however, a <code>nextUp</code>
     * implementation may run faster than its equivalent
     * <code>nextAfter</code> call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is positive infinity, the result is
     * positive infinity.
     *
     * <li> If the argument is zero, the result is
     * <code>Double.MIN_VALUE</code>
     *
     * </ul>
     *
     * @param d  starting floating-point value
     * @return The adjacent floating-point value closer to positive
     * infinity.
     * @author Joseph D. Darcy
     */
    public static double nextUp(double d) {
        if( isNaN(d) || d == Double.POSITIVE_INFINITY)
            return d;
        else {
            d += 0.0d;
            return Double.longBitsToDouble(Double.doubleToRawLongBits(d) +
                                           ((d >= 0.0d)?+1L:-1L));
        }
    }

    /**
     * Returns the floating-point value adjacent to <code>f</code> in
     * the direction of positive infinity.  This method is
     * semantically equivalent to <code>nextAfter(f,
     * Double.POSITIVE_INFINITY)</code>; however, a <code>nextUp</code>
     * implementation may run faster than its equivalent
     * <code>nextAfter</code> call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is positive infinity, the result is
     * positive infinity.
     *
     * <li> If the argument is zero, the result is
     * <code>Float.MIN_VALUE</code>
     *
     * </ul>
     *
     * @param f  starting floating-point value
     * @return The adjacent floating-point value closer to positive
     * infinity.
     * @author Joseph D. Darcy
     */
     public static float nextUp(float f) {
        if( isNaN(f) || f == FloatConsts.POSITIVE_INFINITY)
            return f;
        else {
            f += 0.0f;
            return Float.intBitsToFloat(Float.floatToRawIntBits(f) +
                                        ((f >= 0.0f)?+1:-1));
        }
    }

    /**
     * Returns the floating-point value adjacent to <code>d</code> in
     * the direction of negative infinity.  This method is
     * semantically equivalent to <code>nextAfter(d,
     * Double.NEGATIVE_INFINITY)</code>; however, a
     * <code>nextDown</code> implementation may run faster than its
     * equivalent <code>nextAfter</code> call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is negative infinity, the result is
     * negative infinity.
     *
     * <li> If the argument is zero, the result is
     * <code>-Double.MIN_VALUE</code>
     *
     * </ul>
     *
     * @param d  starting floating-point value
     * @return The adjacent floating-point value closer to negative
     * infinity.
     * @author Joseph D. Darcy
     */
    public static double nextDown(double d) {
        if( isNaN(d) || d == Double.NEGATIVE_INFINITY)
            return d;
        else {
            if (d == 0.0)
                return -Double.MIN_VALUE;
            else
                return Double.longBitsToDouble(Double.doubleToRawLongBits(d) +
                                               ((d > 0.0d)?-1L:+1L));
        }
    }

    /**
     * Returns the floating-point value adjacent to <code>f</code> in
     * the direction of negative infinity.  This method is
     * semantically equivalent to <code>nextAfter(f,
     * Float.NEGATIVE_INFINITY)</code>; however, a
     * <code>nextDown</code> implementation may run faster than its
     * equivalent <code>nextAfter</code> call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is negative infinity, the result is
     * negative infinity.
     *
     * <li> If the argument is zero, the result is
     * <code>-Float.MIN_VALUE</code>
     *
     * </ul>
     *
     * @param f  starting floating-point value
     * @return The adjacent floating-point value closer to negative
     * infinity.
     * @author Joseph D. Darcy
     */
    public static double nextDown(float f) {
        if( isNaN(f) || f == Float.NEGATIVE_INFINITY)
            return f;
        else {
            if (f == 0.0f)
                return -Float.MIN_VALUE;
            else
                return Float.intBitsToFloat(Float.floatToRawIntBits(f) +
                                            ((f > 0.0f)?-1:+1));
        }
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  For this method, a NaN
     * <code>sign</code> argument is always treated as if it were
     * positive.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of <code>magnitude</code>
     * and the sign of <code>sign</code>.
     * @author Joseph D. Darcy
     * @since 1.5
     */
    public static double copySign(double magnitude, double sign) {
        return rawCopySign(magnitude, (isNaN(sign)?1.0d:sign));
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  For this method, a NaN
     * <code>sign</code> argument is always treated as if it were
     * positive.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of <code>magnitude</code>
     * and the sign of <code>sign</code>.
     * @author Joseph D. Darcy
     */
     public static float copySign(float magnitude, float sign) {
        return rawCopySign(magnitude, (isNaN(sign)?1.0f:sign));
    }

    /**
     * Returns the size of an ulp of the argument.  An ulp of a
     * <code>double</code> value is the positive distance between this
     * floating-point value and the <code>double</code> value next
     * larger in magnitude.  Note that for non-NaN <i>x</i>,
     * <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive or negative infinity, then the
     * result is positive infinity.
     * <li> If the argument is positive or negative zero, then the result is
     * <code>Double.MIN_VALUE</code>.
     * <li> If the argument is &plusmn;<code>Double.MAX_VALUE</code>, then
     * the result is equal to 2<sup>971</sup>.
     * </ul>
     *
     * @param d the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    public static double ulp(double d) {
        int exp = getExponent(d);

        switch(exp) {
        case DoubleConsts.MAX_EXPONENT+1:       // NaN or infinity
            return Math.abs(d);
            // break;

        case DoubleConsts.MIN_EXPONENT-1:       // zero or subnormal
            return Double.MIN_VALUE;
            // break

        default:
            assert exp <= DoubleConsts.MAX_EXPONENT && exp >= DoubleConsts.MIN_EXPONENT;

            // ulp(x) is usually 2^(SIGNIFICAND_WIDTH-1)*(2^ilogb(x))
            exp = exp - (DoubleConsts.SIGNIFICAND_WIDTH-1);
            if (exp >= DoubleConsts.MIN_EXPONENT) {
                return powerOfTwoD(exp);
            }
            else {
                // return a subnormal result; left shift integer
                // representation of Double.MIN_VALUE appropriate
                // number of positions
                return Double.longBitsToDouble(1L <<
                (exp - (DoubleConsts.MIN_EXPONENT - (DoubleConsts.SIGNIFICAND_WIDTH-1)) ));
            }
            // break
        }
    }

    /**
     * Returns the size of an ulp of the argument.  An ulp of a
     * <code>float</code> value is the positive distance between this
     * floating-point value and the <code>float</code> value next
     * larger in magnitude.  Note that for non-NaN <i>x</i>,
     * <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive or negative infinity, then the
     * result is positive infinity.
     * <li> If the argument is positive or negative zero, then the result is
     * <code>Float.MIN_VALUE</code>.
     * <li> If the argument is &plusmn;<code>Float.MAX_VALUE</code>, then
     * the result is equal to 2<sup>104</sup>.
     * </ul>
     *
     * @param f the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
     public static float ulp(float f) {
        int exp = getExponent(f);

        switch(exp) {
        case FloatConsts.MAX_EXPONENT+1:        // NaN or infinity
            return Math.abs(f);
            // break;

        case FloatConsts.MIN_EXPONENT-1:        // zero or subnormal
            return FloatConsts.MIN_VALUE;
            // break

        default:
            assert exp <= FloatConsts.MAX_EXPONENT && exp >= FloatConsts.MIN_EXPONENT;

            // ulp(x) is usually 2^(SIGNIFICAND_WIDTH-1)*(2^ilogb(x))
            exp = exp - (FloatConsts.SIGNIFICAND_WIDTH-1);
            if (exp >= FloatConsts.MIN_EXPONENT) {
                return powerOfTwoF(exp);
            }
            else {
                // return a subnormal result; left shift integer
                // representation of FloatConsts.MIN_VALUE appropriate
                // number of positions
                return Float.intBitsToFloat(1 <<
                (exp - (FloatConsts.MIN_EXPONENT - (FloatConsts.SIGNIFICAND_WIDTH-1)) ));
            }
            // break
        }
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
     * @param d the floating-point value whose signum is to be returned
     * @return the signum function of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     */
    public static double signum(double d) {
        return (d == 0.0 || isNaN(d))?d:copySign(1.0, d);
    }

    /**
     * Returns the signum function of the argument; zero if the argument
     * is zero, 1.0f if the argument is greater than zero, -1.0f if the
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
     * @author Joseph D. Darcy
     * @since 1.5
     */
    public static float signum(float f) {
        return (f == 0.0f || isNaN(f))?f:copySign(1.0f, f);
    }

}
