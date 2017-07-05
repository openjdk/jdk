/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.misc;

import sun.misc.FloatConsts;
import sun.misc.DoubleConsts;

/**
 * The class {@code FpUtils} contains static utility methods for
 * manipulating and inspecting {@code float} and
 * {@code double} floating-point numbers.  These methods include
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

    // Helper Methods

    // The following helper methods are used in the implementation of
    // the public recommended functions; they generally omit certain
    // tests for exception cases.

    /**
     * Returns unbiased exponent of a {@code double}.
     * @deprecated Use Math.getExponent.
     */
    @Deprecated
    public static int getExponent(double d){
        return Math.getExponent(d);
    }

    /**
     * Returns unbiased exponent of a {@code float}.
     * @deprecated Use Math.getExponent.
     */
    @Deprecated
    public static int getExponent(float f){
        return Math.getExponent(f);
    }


    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  Note that unlike the {@link
     * FpUtils#copySign(double, double) copySign} method, this method
     * does not require NaN {@code sign} arguments to be treated
     * as positive values; implementations are permitted to treat some
     * NaN arguments as positive and other NaN arguments as negative
     * to allow greater performance.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude}
     * and the sign of {@code sign}.
     * @author Joseph D. Darcy
     * @deprecated Use Math.copySign.
     */
    @Deprecated
    public static double rawCopySign(double magnitude, double sign) {
        return Math.copySign(magnitude, sign);
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  Note that unlike the {@link
     * FpUtils#copySign(float, float) copySign} method, this method
     * does not require NaN {@code sign} arguments to be treated
     * as positive values; implementations are permitted to treat some
     * NaN arguments as positive and other NaN arguments as negative
     * to allow greater performance.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude}
     * and the sign of {@code sign}.
     * @author Joseph D. Darcy
     * @deprecated Use Math.copySign.
     */
    @Deprecated
    public static float rawCopySign(float magnitude, float sign) {
        return Math.copySign(magnitude, sign);
    }

    /* ***************************************************************** */

    /**
     * Returns {@code true} if the argument is a finite
     * floating-point value; returns {@code false} otherwise (for
     * NaN and infinity arguments).
     *
     * @param d the {@code double} value to be tested
     * @return {@code true} if the argument is a finite
     * floating-point value, {@code false} otherwise.
     * @deprecated Use Double.isFinite.
     */
    @Deprecated
    public static boolean isFinite(double d) {
        return Double.isFinite(d);
    }

    /**
     * Returns {@code true} if the argument is a finite
     * floating-point value; returns {@code false} otherwise (for
     * NaN and infinity arguments).
     *
     * @param f the {@code float} value to be tested
     * @return {@code true} if the argument is a finite
     * floating-point value, {@code false} otherwise.
     * @deprecated Use Float.isFinite.
     */
     @Deprecated
     public static boolean isFinite(float f) {
         return Float.isFinite(f);
    }

    /**
     * Returns {@code true} if the specified number is infinitely
     * large in magnitude, {@code false} otherwise.
     *
     * <p>Note that this method is equivalent to the {@link
     * Double#isInfinite(double) Double.isInfinite} method; the
     * functionality is included in this class for convenience.
     *
     * @param   d   the value to be tested.
     * @return  {@code true} if the value of the argument is positive
     *          infinity or negative infinity; {@code false} otherwise.
     */
    public static boolean isInfinite(double d) {
        return Double.isInfinite(d);
    }

    /**
     * Returns {@code true} if the specified number is infinitely
     * large in magnitude, {@code false} otherwise.
     *
     * <p>Note that this method is equivalent to the {@link
     * Float#isInfinite(float) Float.isInfinite} method; the
     * functionality is included in this class for convenience.
     *
     * @param   f   the value to be tested.
     * @return  {@code true} if the argument is positive infinity or
     *          negative infinity; {@code false} otherwise.
     */
     public static boolean isInfinite(float f) {
         return Float.isInfinite(f);
    }

    /**
     * Returns {@code true} if the specified number is a
     * Not-a-Number (NaN) value, {@code false} otherwise.
     *
     * <p>Note that this method is equivalent to the {@link
     * Double#isNaN(double) Double.isNaN} method; the functionality is
     * included in this class for convenience.
     *
     * @param   d   the value to be tested.
     * @return  {@code true} if the value of the argument is NaN;
     *          {@code false} otherwise.
     */
    public static boolean isNaN(double d) {
        return Double.isNaN(d);
    }

    /**
     * Returns {@code true} if the specified number is a
     * Not-a-Number (NaN) value, {@code false} otherwise.
     *
     * <p>Note that this method is equivalent to the {@link
     * Float#isNaN(float) Float.isNaN} method; the functionality is
     * included in this class for convenience.
     *
     * @param   f   the value to be tested.
     * @return  {@code true} if the argument is NaN;
     *          {@code false} otherwise.
     */
     public static boolean isNaN(float f) {
        return Float.isNaN(f);
    }

    /**
     * Returns {@code true} if the unordered relation holds
     * between the two arguments.  When two floating-point values are
     * unordered, one value is neither less than, equal to, nor
     * greater than the other.  For the unordered relation to be true,
     * at least one argument must be a {@code NaN}.
     *
     * @param arg1      the first argument
     * @param arg2      the second argument
     * @return {@code true} if at least one argument is a NaN,
     * {@code false} otherwise.
     */
    public static boolean isUnordered(double arg1, double arg2) {
        return isNaN(arg1) || isNaN(arg2);
    }

    /**
     * Returns {@code true} if the unordered relation holds
     * between the two arguments.  When two floating-point values are
     * unordered, one value is neither less than, equal to, nor
     * greater than the other.  For the unordered relation to be true,
     * at least one argument must be a {@code NaN}.
     *
     * @param arg1      the first argument
     * @param arg2      the second argument
     * @return {@code true} if at least one argument is a NaN,
     * {@code false} otherwise.
     */
     public static boolean isUnordered(float arg1, float arg2) {
        return isNaN(arg1) || isNaN(arg2);
    }

    /**
     * Returns unbiased exponent of a {@code double}; for
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

        default:
            assert( exponent >= DoubleConsts.MIN_EXPONENT &&
                    exponent <= DoubleConsts.MAX_EXPONENT);
            return exponent;
        }
    }

    /**
     * Returns unbiased exponent of a {@code float}; for
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

        default:
            assert( exponent >= FloatConsts.MIN_EXPONENT &&
                    exponent <= FloatConsts.MAX_EXPONENT);
            return exponent;
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
     * Return {@code d} &times;
     * 2<sup>{@code scale_factor}</sup> rounded as if performed
     * by a single correctly rounded floating-point multiply to a
     * member of the double value set.  See section 4.2.3 of
     * <cite>The Java&trade; Language Specification</cite>
     * for a discussion of floating-point
     * value sets.  If the exponent of the result is between the
     * {@code double}'s minimum exponent and maximum exponent,
     * the answer is calculated exactly.  If the exponent of the
     * result would be larger than {@code doubles}'s maximum
     * exponent, an infinity is returned.  Note that if the result is
     * subnormal, precision may be lost; that is, when {@code scalb(x,
     * n)} is subnormal, {@code scalb(scalb(x, n), -n)} may
     * not equal <i>x</i>.  When the result is non-NaN, the result has
     * the same sign as {@code d}.
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
     * @param scale_factor power of 2 used to scale {@code d}
     * @return {@code d * }2<sup>{@code scale_factor}</sup>
     * @author Joseph D. Darcy
     * @deprecated Use Math.scalb.
     */
    @Deprecated
    public static double scalb(double d, int scale_factor) {
        return Math.scalb(d, scale_factor);
    }

    /**
     * Return {@code f} &times;
     * 2<sup>{@code scale_factor}</sup> rounded as if performed
     * by a single correctly rounded floating-point multiply to a
     * member of the float value set.  See section 4.2.3 of
     * <cite>The Java&trade; Language Specification</cite>
     * for a discussion of floating-point
     * value sets. If the exponent of the result is between the
     * {@code float}'s minimum exponent and maximum exponent, the
     * answer is calculated exactly.  If the exponent of the result
     * would be larger than {@code float}'s maximum exponent, an
     * infinity is returned.  Note that if the result is subnormal,
     * precision may be lost; that is, when {@code scalb(x, n)}
     * is subnormal, {@code scalb(scalb(x, n), -n)} may not equal
     * <i>x</i>.  When the result is non-NaN, the result has the same
     * sign as {@code f}.
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
     * @param scale_factor power of 2 used to scale {@code f}
     * @return {@code f * }2<sup>{@code scale_factor}</sup>
     * @author Joseph D. Darcy
     * @deprecated Use Math.scalb.
     */
    @Deprecated
    public static float scalb(float f, int scale_factor) {
        return Math.scalb(f, scale_factor);
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
     * <li> If both arguments are signed zeros, {@code direction}
     * is returned unchanged (as implied by the requirement of
     * returning the second argument if the arguments compare as
     * equal).
     *
     * <li> If {@code start} is
     * &plusmn;{@code Double.MIN_VALUE} and {@code direction}
     * has a value such that the result should have a smaller
     * magnitude, then a zero with the same sign as {@code start}
     * is returned.
     *
     * <li> If {@code start} is infinite and
     * {@code direction} has a value such that the result should
     * have a smaller magnitude, {@code Double.MAX_VALUE} with the
     * same sign as {@code start} is returned.
     *
     * <li> If {@code start} is equal to &plusmn;
     * {@code Double.MAX_VALUE} and {@code direction} has a
     * value such that the result should have a larger magnitude, an
     * infinity with same sign as {@code start} is returned.
     * </ul>
     *
     * @param start     starting floating-point value
     * @param direction value indicating which of
     * {@code start}'s neighbors or {@code start} should
     * be returned
     * @return The floating-point number adjacent to {@code start} in the
     * direction of {@code direction}.
     * @author Joseph D. Darcy
     * @deprecated Use Math.nextAfter
     */
    @Deprecated
    public static double nextAfter(double start, double direction) {
        return Math.nextAfter(start, direction);
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
     * <li> If both arguments are signed zeros, a {@code float}
     * zero with the same sign as {@code direction} is returned
     * (as implied by the requirement of returning the second argument
     * if the arguments compare as equal).
     *
     * <li> If {@code start} is
     * &plusmn;{@code Float.MIN_VALUE} and {@code direction}
     * has a value such that the result should have a smaller
     * magnitude, then a zero with the same sign as {@code start}
     * is returned.
     *
     * <li> If {@code start} is infinite and
     * {@code direction} has a value such that the result should
     * have a smaller magnitude, {@code Float.MAX_VALUE} with the
     * same sign as {@code start} is returned.
     *
     * <li> If {@code start} is equal to &plusmn;
     * {@code Float.MAX_VALUE} and {@code direction} has a
     * value such that the result should have a larger magnitude, an
     * infinity with same sign as {@code start} is returned.
     * </ul>
     *
     * @param start     starting floating-point value
     * @param direction value indicating which of
     * {@code start}'s neighbors or {@code start} should
     * be returned
     * @return The floating-point number adjacent to {@code start} in the
     * direction of {@code direction}.
     * @author Joseph D. Darcy
     * @deprecated Use Math.nextAfter.
     */
    @Deprecated
    public static float nextAfter(float start, double direction) {
        return Math.nextAfter(start, direction);
    }

    /**
     * Returns the floating-point value adjacent to {@code d} in
     * the direction of positive infinity.  This method is
     * semantically equivalent to {@code nextAfter(d,
     * Double.POSITIVE_INFINITY)}; however, a {@code nextUp}
     * implementation may run faster than its equivalent
     * {@code nextAfter} call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is positive infinity, the result is
     * positive infinity.
     *
     * <li> If the argument is zero, the result is
     * {@code Double.MIN_VALUE}
     *
     * </ul>
     *
     * @param d  starting floating-point value
     * @return The adjacent floating-point value closer to positive
     * infinity.
     * @author Joseph D. Darcy
     * @deprecated use Math.nextUp.
     */
    @Deprecated
    public static double nextUp(double d) {
        return Math.nextUp(d);
    }

    /**
     * Returns the floating-point value adjacent to {@code f} in
     * the direction of positive infinity.  This method is
     * semantically equivalent to {@code nextAfter(f,
     * Double.POSITIVE_INFINITY)}; however, a {@code nextUp}
     * implementation may run faster than its equivalent
     * {@code nextAfter} call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is positive infinity, the result is
     * positive infinity.
     *
     * <li> If the argument is zero, the result is
     * {@code Float.MIN_VALUE}
     *
     * </ul>
     *
     * @param f  starting floating-point value
     * @return The adjacent floating-point value closer to positive
     * infinity.
     * @author Joseph D. Darcy
     * @deprecated Use Math.nextUp.
     */
    @Deprecated
    public static float nextUp(float f) {
        return Math.nextUp(f);
    }

    /**
     * Returns the floating-point value adjacent to {@code d} in
     * the direction of negative infinity.  This method is
     * semantically equivalent to {@code nextAfter(d,
     * Double.NEGATIVE_INFINITY)}; however, a
     * {@code nextDown} implementation may run faster than its
     * equivalent {@code nextAfter} call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is negative infinity, the result is
     * negative infinity.
     *
     * <li> If the argument is zero, the result is
     * {@code -Double.MIN_VALUE}
     *
     * </ul>
     *
     * @param d  starting floating-point value
     * @return The adjacent floating-point value closer to negative
     * infinity.
     * @author Joseph D. Darcy
     * @deprecated Use Math.nextDown.
     */
    @Deprecated
    public static double nextDown(double d) {
        return Math.nextDown(d);
    }

    /**
     * Returns the floating-point value adjacent to {@code f} in
     * the direction of negative infinity.  This method is
     * semantically equivalent to {@code nextAfter(f,
     * Float.NEGATIVE_INFINITY)}; however, a
     * {@code nextDown} implementation may run faster than its
     * equivalent {@code nextAfter} call.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, the result is NaN.
     *
     * <li> If the argument is negative infinity, the result is
     * negative infinity.
     *
     * <li> If the argument is zero, the result is
     * {@code -Float.MIN_VALUE}
     *
     * </ul>
     *
     * @param f  starting floating-point value
     * @return The adjacent floating-point value closer to negative
     * infinity.
     * @author Joseph D. Darcy
     * @deprecated Use Math.nextDown.
     */
    @Deprecated
    public static double nextDown(float f) {
        return Math.nextDown(f);
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  For this method, a NaN
     * {@code sign} argument is always treated as if it were
     * positive.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude}
     * and the sign of {@code sign}.
     * @author Joseph D. Darcy
     * @since 1.5
     * @deprecated Use StrictMath.copySign.
     */
    @Deprecated
    public static double copySign(double magnitude, double sign) {
        return StrictMath.copySign(magnitude, sign);
    }

    /**
     * Returns the first floating-point argument with the sign of the
     * second floating-point argument.  For this method, a NaN
     * {@code sign} argument is always treated as if it were
     * positive.
     *
     * @param magnitude  the parameter providing the magnitude of the result
     * @param sign   the parameter providing the sign of the result
     * @return a value with the magnitude of {@code magnitude}
     * and the sign of {@code sign}.
     * @author Joseph D. Darcy
     * @deprecated Use StrictMath.copySign.
     */
    @Deprecated
    public static float copySign(float magnitude, float sign) {
        return StrictMath.copySign(magnitude, sign);
    }

    /**
     * Returns the size of an ulp of the argument.  An ulp of a
     * {@code double} value is the positive distance between this
     * floating-point value and the {@code double} value next
     * larger in magnitude.  Note that for non-NaN <i>x</i>,
     * <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive or negative infinity, then the
     * result is positive infinity.
     * <li> If the argument is positive or negative zero, then the result is
     * {@code Double.MIN_VALUE}.
     * <li> If the argument is &plusmn;{@code Double.MAX_VALUE}, then
     * the result is equal to 2<sup>971</sup>.
     * </ul>
     *
     * @param d the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     * @deprecated Use Math.ulp.
     */
    @Deprecated
    public static double ulp(double d) {
        return Math.ulp(d);
    }

    /**
     * Returns the size of an ulp of the argument.  An ulp of a
     * {@code float} value is the positive distance between this
     * floating-point value and the {@code float} value next
     * larger in magnitude.  Note that for non-NaN <i>x</i>,
     * <code>ulp(-<i>x</i>) == ulp(<i>x</i>)</code>.
     *
     * <p>Special Cases:
     * <ul>
     * <li> If the argument is NaN, then the result is NaN.
     * <li> If the argument is positive or negative infinity, then the
     * result is positive infinity.
     * <li> If the argument is positive or negative zero, then the result is
     * {@code Float.MIN_VALUE}.
     * <li> If the argument is &plusmn;{@code Float.MAX_VALUE}, then
     * the result is equal to 2<sup>104</sup>.
     * </ul>
     *
     * @param f the floating-point value whose ulp is to be returned
     * @return the size of an ulp of the argument
     * @author Joseph D. Darcy
     * @since 1.5
     * @deprecated Use Math.ulp.
     */
     @Deprecated
     public static float ulp(float f) {
        return Math.ulp(f);
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
     * @deprecated Use Math.signum.
     */
    @Deprecated
    public static double signum(double d) {
        return Math.signum(d);
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
     * @deprecated Use Math.signum.
     */
    @Deprecated
    public static float signum(float f) {
        return Math.signum(f);
    }
}
