/*
 * Copyright (c) 1994, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.lang.invoke.MethodHandles;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.util.Optional;

import jdk.internal.math.FloatingDecimal;
import jdk.internal.math.DoubleConsts;
import jdk.internal.math.DoubleToDecimal;
import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * The {@code Double} class wraps a value of the primitive type
 * {@code double} in an object. An object of type
 * {@code Double} contains a single field whose type is
 * {@code double}.
 *
 * <p>In addition, this class provides several methods for converting a
 * {@code double} to a {@code String} and a
 * {@code String} to a {@code double}, as well as other
 * constants and methods useful when dealing with a
 * {@code double}.
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
 * IEEE 754 floating-point values include finite nonzero values,
 * signed zeros ({@code +0.0} and {@code -0.0}), signed infinities
 * ({@linkplain Double#POSITIVE_INFINITY positive infinity} and
 * {@linkplain Double#NEGATIVE_INFINITY negative infinity}), and
 * {@linkplain Double#NaN NaN} (not-a-number).
 *
 * <p>An <em>equivalence relation</em> on a set of values is a boolean
 * relation on pairs of values that is reflexive, symmetric, and
 * transitive. For more discussion of equivalence relations and object
 * equality, see the {@link Object#equals Object.equals}
 * specification. An equivalence relation partitions the values it
 * operates over into sets called <i>equivalence classes</i>.  All the
 * members of the equivalence class are equal to each other under the
 * relation. An equivalence class may contain only a single member. At
 * least for some purposes, all the members of an equivalence class
 * are substitutable for each other.  In particular, in a numeric
 * expression equivalent values can be <em>substituted</em> for one
 * another without changing the result of the expression, meaning
 * changing the equivalence class of the result of the expression.
 *
 * <p>Notably, the built-in {@code ==} operation on floating-point
 * values is <em>not</em> an equivalence relation. Despite not
 * defining an equivalence relation, the semantics of the IEEE 754
 * {@code ==} operator were deliberately designed to meet other needs
 * of numerical computation. There are two exceptions where the
 * properties of an equivalence relation are not satisfied by {@code
 * ==} on floating-point values:
 *
 * <ul>
 *
 * <li>If {@code v1} and {@code v2} are both NaN, then {@code v1
 * == v2} has the value {@code false}. Therefore, for two NaN
 * arguments the <em>reflexive</em> property of an equivalence
 * relation is <em>not</em> satisfied by the {@code ==} operator.
 *
 * <li>If {@code v1} represents {@code +0.0} while {@code v2}
 * represents {@code -0.0}, or vice versa, then {@code v1 == v2} has
 * the value {@code true} even though {@code +0.0} and {@code -0.0}
 * are distinguishable under various floating-point operations. For
 * example, {@code 1.0/+0.0} evaluates to positive infinity while
 * {@code 1.0/-0.0} evaluates to <em>negative</em> infinity and
 * positive infinity and negative infinity are neither equal to each
 * other nor equivalent to each other. Thus, while a signed zero input
 * most commonly determines the sign of a zero result, because of
 * dividing by zero, {@code +0.0} and {@code -0.0} may not be
 * substituted for each other in general. The sign of a zero input
 * also has a non-substitutable effect on the result of some math
 * library methods.
 *
 * </ul>
 *
 * <p>For ordered comparisons using the built-in comparison operators
 * ({@code <}, {@code <=}, etc.), NaN values have another anomalous
 * situation: a NaN is neither less than, nor greater than, nor equal
 * to any value, including itself. This means the <i>trichotomy of
 * comparison</i> does <em>not</em> hold.
 *
 * <p>To provide the appropriate semantics for {@code equals} and
 * {@code compareTo} methods, those methods cannot simply be wrappers
 * around {@code ==} or ordered comparison operations. Instead, {@link
 * Double#equals equals} uses <a href=#repEquivalence> representation
 * equivalence</a>, defining NaN arguments to be equal to each other,
 * restoring reflexivity, and defining {@code +0.0} to <em>not</em> be
 * equal to {@code -0.0}. For comparisons, {@link Double#compareTo
 * compareTo} defines a total order where {@code -0.0} is less than
 * {@code +0.0} and where a NaN is equal to itself and considered
 * greater than positive infinity.
 *
 * <p>The operational semantics of {@code equals} and {@code
 * compareTo} are expressed in terms of {@linkplain #doubleToLongBits
 * bit-wise converting} the floating-point values to integral values.
 *
 * <p>The <em>natural ordering</em> implemented by {@link #compareTo
 * compareTo} is {@linkplain Comparable consistent with equals}. That
 * is, two objects are reported as equal by {@code equals} if and only
 * if {@code compareTo} on those objects returns zero.
 *
 * <p>The adjusted behaviors defined for {@code equals} and {@code
 * compareTo} allow instances of wrapper classes to work properly with
 * conventional data structures. For example, defining NaN
 * values to be {@code equals} to one another allows NaN to be used as
 * an element of a {@link java.util.HashSet HashSet} or as the key of
 * a {@link java.util.HashMap HashMap}. Similarly, defining {@code
 * compareTo} as a total ordering, including {@code +0.0}, {@code
 * -0.0}, and NaN, allows instances of wrapper classes to be used as
 * elements of a {@link java.util.SortedSet SortedSet} or as keys of a
 * {@link java.util.SortedMap SortedMap}.
 *
 * <p>Comparing numerical equality to various useful equivalence
 * relations that can be defined over floating-point values:
 *
 * <dl>
 * <dt><a id=fpNumericalEq><i>numerical equality</i></a> ({@code ==}
 * operator): (<em>Not</em> an equivalence relation)</dt>
 * <dd>Two floating-point values represent the same extended real
 * number. The extended real numbers are the real numbers augmented
 * with positive infinity and negative infinity. Under numerical
 * equality, {@code +0.0} and {@code -0.0} are equal since they both
 * map to the same real value, 0. A NaN does not map to any real
 * number and is not equal to any value, including itself.
 * </dd>
 *
 * <dt><i>bit-wise equivalence</i>:</dt>
 * <dd>The bits of the two floating-point values are the same. This
 * equivalence relation for {@code double} values {@code a} and {@code
 * b} is implemented by the expression
 * <br>{@code Double.doubleTo}<code><b>Raw</b></code>{@code LongBits(a) == Double.doubleTo}<code><b>Raw</b></code>{@code LongBits(b)}<br>
 * Under this relation, {@code +0.0} and {@code -0.0} are
 * distinguished from each other and every bit pattern encoding a NaN
 * is distinguished from every other bit pattern encoding a NaN.
 * </dd>
 *
 * <dt><i><a id=repEquivalence>representation equivalence</a></i>:</dt>
 * <dd>The two floating-point values represent the same IEEE 754
 * <i>datum</i>. In particular, for {@linkplain #isFinite(double)
 * finite} values, the sign, {@linkplain Math#getExponent(double)
 * exponent}, and significand components of the floating-point values
 * are the same. Under this relation:
 * <ul>
 * <li> {@code +0.0} and {@code -0.0} are distinguished from each other.
 * <li> every bit pattern encoding a NaN is considered equivalent to each other
 * <li> positive infinity is equivalent to positive infinity; negative
 *      infinity is equivalent to negative infinity.
 * </ul>
 * Expressions implementing this equivalence relation include:
 * <ul>
 * <li>{@code Double.doubleToLongBits(a) == Double.doubleToLongBits(b)}
 * <li>{@code Double.valueOf(a).equals(Double.valueOf(b))}
 * <li>{@code Double.compare(a, b) == 0}
 * </ul>
 * Note that representation equivalence is often an appropriate notion
 * of equivalence to test the behavior of {@linkplain StrictMath math
 * libraries}.
 * </dd>
 * </dl>
 *
 * For two binary floating-point values {@code a} and {@code b}, if
 * neither of {@code a} and {@code b} is zero or NaN, then the three
 * relations numerical equality, bit-wise equivalence, and
 * representation equivalence of {@code a} and {@code b} have the same
 * {@code true}/{@code false} value. In other words, for binary
 * floating-point values, the three relations only differ if at least
 * one argument is zero or NaN.
 *
 * <h2><a id=decimalToBinaryConversion>Decimal &harr; Binary Conversion Issues</a></h2>
 *
 * Many surprising results of binary floating-point arithmetic trace
 * back to aspects of decimal to binary conversion and binary to
 * decimal conversion. While integer values can be exactly represented
 * in any base, which fractional values can be exactly represented in
 * a base is a function of the base. For example, in base 10, 1/3 is a
 * repeating fraction (0.33333....); but in base 3, 1/3 is exactly
 * 0.1<sub>(3)</sub>, that is 1&nbsp;&times;&nbsp;3<sup>-1</sup>.
 * Similarly, in base 10, 1/10 is exactly representable as 0.1
 * (1&nbsp;&times;&nbsp;10<sup>-1</sup>), but in base 2, it is a
 * repeating fraction (0.0001100110011...<sub>(2)</sub>).
 *
 * <p>Values of the {@code float} type have {@value Float#PRECISION}
 * bits of precision and values of the {@code double} type have
 * {@value Double#PRECISION} bits of precision. Therefore, since 0.1
 * is a repeating fraction in base 2 with a four-bit repeat, {@code
 * 0.1f} != {@code 0.1d}. In more detail, including hexadecimal
 * floating-point literals:
 *
 * <ul>
 * <li>The exact numerical value of {@code 0.1f} ({@code 0x1.99999a0000000p-4f}) is
 *     0.100000001490116119384765625.
 * <li>The exact numerical value of {@code 0.1d} ({@code 0x1.999999999999ap-4d}) is
 *     0.1000000000000000055511151231257827021181583404541015625.
 * </ul>
 *
 * These are the closest {@code float} and {@code double} values,
 * respectively, to the numerical value of 0.1.  These results are
 * consistent with a {@code float} value having the equivalent of 6 to
 * 9 digits of decimal precision and a {@code double} value having the
 * equivalent of 15 to 17 digits of decimal precision. (The
 * equivalent precision varies according to the different relative
 * densities of binary and decimal values at different points along the
 * real number line.)
 *
 * <p>This representation hazard of decimal fractions is one reason to
 * use caution when storing monetary values as {@code float} or {@code
 * double}. Alternatives include:
 * <ul>
 * <li>using {@link java.math.BigDecimal BigDecimal} to store decimal
 * fractional values exactly
 *
 * <li>scaling up so the monetary value is an integer &mdash; for
 * example, multiplying by 100 if the value is denominated in cents or
 * multiplying by 1000 if the value is denominated in mills &mdash;
 * and then storing that scaled value in an integer type
 *
 *</ul>
 *
 * <p>For each finite floating-point value and a given floating-point
 * type, there is a contiguous region of the real number line which
 * maps to that value. Under the default round to nearest rounding
 * policy (JLS {@jls 15.4}), this contiguous region for a value is
 * typically one {@linkplain Math#ulp ulp} (unit in the last place)
 * wide and centered around the exactly representable value. (At
 * exponent boundaries, the region is asymmetrical and larger on the
 * side with the larger exponent.) For example, for {@code 0.1f}, the
 * region can be computed as follows:
 *
 * <br>// Numeric values listed are exact values
 * <br>oneTenthApproxAsFloat = 0.100000001490116119384765625;
 * <br>ulpOfoneTenthApproxAsFloat = Math.ulp(0.1f) = 7.450580596923828125E-9;
 * <br>// Numeric range that is converted to the float closest to 0.1, _excludes_ endpoints
 * <br>(oneTenthApproxAsFloat - &frac12;ulpOfoneTenthApproxAsFloat, oneTenthApproxAsFloat + &frac12;ulpOfoneTenthApproxAsFloat) =
 * <br>(0.0999999977648258209228515625, 0.1000000052154064178466796875)
 *
 * <p>In particular, a correctly rounded decimal to binary conversion
 * of any string representing a number in this range, say by {@link
 * Float#parseFloat(String)}, will be converted to the same value:
 *
 * {@snippet lang="java" :
 * Float.parseFloat("0.0999999977648258209228515625000001"); // rounds up to oneTenthApproxAsFloat
 * Float.parseFloat("0.099999998");                          // rounds up to oneTenthApproxAsFloat
 * Float.parseFloat("0.1");                                  // rounds up to oneTenthApproxAsFloat
 * Float.parseFloat("0.100000001490116119384765625");        // exact conversion
 * Float.parseFloat("0.100000005215406417846679687");        // rounds down to oneTenthApproxAsFloat
 * Float.parseFloat("0.100000005215406417846679687499999");  // rounds down to oneTenthApproxAsFloat
 * }
 *
 * <p>Similarly, an analogous range can be constructed  for the {@code
 * double} type based on the exact value of {@code double}
 * approximation to {@code 0.1d} and the numerical value of {@code
 * Math.ulp(0.1d)} and likewise for other particular numerical values
 * in the {@code float} and {@code double} types.
 *
 * <p>As seen in the above conversions, compared to the exact
 * numerical value the operation would have without rounding, the same
 * floating-point value as a result can be:
 * <ul>
 * <li>greater than the exact result
 * <li>equal to the exact result
 * <li>less than the exact result
 * </ul>
 *
 * A floating-point value doesn't "know" whether it was the result of
 * rounding up, or rounding down, or an exact operation; it contains
 * no history of how it was computed. Consequently, the sum of
 * {@snippet lang="java" :
 * 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f;
 * // Numerical value of computed sum: 1.00000011920928955078125,
 * // the next floating-point value larger than 1.0f, equal to Math.nextUp(1.0f).
 * }
 * or
 * {@snippet lang="java" :
 * 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d;
 * // Numerical value of computed sum: 0.99999999999999988897769753748434595763683319091796875,
 * // the next floating-point value smaller than 1.0d, equal to Math.nextDown(1.0d).
 * }
 *
 * should <em>not</em> be expected to be exactly equal to 1.0, but
 * only to be close to 1.0. Consequently, the following code is an
 * infinite loop:
 *
 * {@snippet lang="java" :
 * double d = 0.0;
 * while (d != 1.0) { // Surprising infinite loop
 *   d += 0.1; // Sum never _exactly_ equals 1.0
 * }
 * }
 *
 * Instead, use an integer loop count for counted loops:
 *
 * {@snippet lang="java" :
 * double d = 0.0;
 * for (int i = 0; i < 10; i++) {
 *   d += 0.1;
 * } // Value of d is equal to Math.nextDown(1.0).
 * }
 *
 * or test against a floating-point limit using ordered comparisons
 * ({@code <}, {@code <=}, {@code >}, {@code >=}):
 *
 * {@snippet lang="java" :
 *  double d = 0.0;
 *  while (d <= 1.0) {
 *    d += 0.1;
 *  } // Value of d approximately 1.0999999999999999
 *  }
 *
 * While floating-point arithmetic may have surprising results, IEEE
 * 754 floating-point arithmetic follows a principled design and its
 * behavior is predictable on the Java platform.
 *
 * @jls 4.2.3 Floating-Point Types, Formats, and Values
 * @jls 4.2.4. Floating-Point Operations
 * @jls 15.21.1 Numerical Equality Operators == and !=
 * @jls 15.20.1 Numerical Comparison Operators {@code <}, {@code <=}, {@code >}, and {@code >=}
 *
 * @see <a href="https://standards.ieee.org/ieee/754/6210/">
 *      <cite>IEEE Standard for Floating-Point Arithmetic</cite></a>
 *
 * @author  Lee Boynton
 * @author  Arthur van Hoff
 * @author  Joseph D. Darcy
 * @since 1.0
 */
@jdk.internal.ValueBased
public final class Double extends Number
        implements Comparable<Double>, Constable, ConstantDesc {
    /**
     * A constant holding the positive infinity of type
     * {@code double}. It is equal to the value returned by
     * {@code Double.longBitsToDouble(0x7ff0000000000000L)}.
     */
    public static final double POSITIVE_INFINITY = 1.0 / 0.0;

    /**
     * A constant holding the negative infinity of type
     * {@code double}. It is equal to the value returned by
     * {@code Double.longBitsToDouble(0xfff0000000000000L)}.
     */
    public static final double NEGATIVE_INFINITY = -1.0 / 0.0;

    /**
     * A constant holding a Not-a-Number (NaN) value of type
     * {@code double}. It is equivalent to the value returned by
     * {@code Double.longBitsToDouble(0x7ff8000000000000L)}.
     */
    public static final double NaN = 0.0d / 0.0;

    /**
     * A constant holding the largest positive finite value of type
     * {@code double},
     * (2-2<sup>-52</sup>)&middot;2<sup>1023</sup>.  It is equal to
     * the hexadecimal floating-point literal
     * {@code 0x1.fffffffffffffP+1023} and also equal to
     * {@code Double.longBitsToDouble(0x7fefffffffffffffL)}.
     */
    public static final double MAX_VALUE = 0x1.fffffffffffffP+1023; // 1.7976931348623157e+308

    /**
     * A constant holding the smallest positive normal value of type
     * {@code double}, 2<sup>-1022</sup>.  It is equal to the
     * hexadecimal floating-point literal {@code 0x1.0p-1022} and also
     * equal to {@code Double.longBitsToDouble(0x0010000000000000L)}.
     *
     * @since 1.6
     */
    public static final double MIN_NORMAL = 0x1.0p-1022; // 2.2250738585072014E-308

    /**
     * A constant holding the smallest positive nonzero value of type
     * {@code double}, 2<sup>-1074</sup>. It is equal to the
     * hexadecimal floating-point literal
     * {@code 0x0.0000000000001P-1022} and also equal to
     * {@code Double.longBitsToDouble(0x1L)}.
     */
    public static final double MIN_VALUE = 0x0.0000000000001P-1022; // 4.9e-324

    /**
     * The number of bits used to represent a {@code double} value.
     *
     * @since 1.5
     */
    public static final int SIZE = 64;

    /**
     * The number of bits in the significand of a {@code double} value.
     * This is the parameter N in section {@jls 4.2.3} of
     * <cite>The Java Language Specification</cite>.
     *
     * @since 19
     */
    public static final int PRECISION = 53;

    /**
     * Maximum exponent a finite {@code double} variable may have.
     * It is equal to the value returned by
     * {@code Math.getExponent(Double.MAX_VALUE)}.
     *
     * @since 1.6
     */
    public static final int MAX_EXPONENT = (1 << (SIZE - PRECISION - 1)) - 1; // 1023

    /**
     * Minimum exponent a normalized {@code double} variable may
     * have.  It is equal to the value returned by
     * {@code Math.getExponent(Double.MIN_NORMAL)}.
     *
     * @since 1.6
     */
    public static final int MIN_EXPONENT = 1 - MAX_EXPONENT; // -1022

    /**
     * The number of bytes used to represent a {@code double} value.
     *
     * @since 1.8
     */
    public static final int BYTES = SIZE / Byte.SIZE;

    /**
     * The {@code Class} instance representing the primitive type
     * {@code double}.
     *
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public static final Class<Double>   TYPE = (Class<Double>) Class.getPrimitiveClass("double");

    /**
     * Returns a string representation of the {@code double}
     * argument. All characters mentioned below are ASCII characters.
     * <ul>
     * <li>If the argument is NaN, the result is the string
     *     "{@code NaN}".
     * <li>Otherwise, the result is a string that represents the sign and
     * magnitude (absolute value) of the argument. If the sign is negative,
     * the first character of the result is '{@code -}'
     * ({@code '\u005Cu002D'}); if the sign is positive, no sign character
     * appears in the result. As for the magnitude <i>m</i>:
     * <ul>
     * <li>If <i>m</i> is infinity, it is represented by the characters
     * {@code "Infinity"}; thus, positive infinity produces the result
     * {@code "Infinity"} and negative infinity produces the result
     * {@code "-Infinity"}.
     *
     * <li>If <i>m</i> is zero, it is represented by the characters
     * {@code "0.0"}; thus, negative zero produces the result
     * {@code "-0.0"} and positive zero produces the result
     * {@code "0.0"}.
     *
     * <li> Otherwise <i>m</i> is positive and finite.
     * It is converted to a string in two stages:
     * <ul>
     * <li> <em>Selection of a decimal</em>:
     * A well-defined decimal <i>d</i><sub><i>m</i></sub>
     * is selected to represent <i>m</i>.
     * This decimal is (almost always) the <em>shortest</em> one that
     * rounds to <i>m</i> according to the round to nearest
     * rounding policy of IEEE 754 floating-point arithmetic.
     * <li> <em>Formatting as a string</em>:
     * The decimal <i>d</i><sub><i>m</i></sub> is formatted as a string,
     * either in plain or in computerized scientific notation,
     * depending on its value.
     * </ul>
     * </ul>
     * </ul>
     *
     * <p>A <em>decimal</em> is a number of the form
     * <i>s</i>&times;10<sup><i>i</i></sup>
     * for some (unique) integers <i>s</i> &gt; 0 and <i>i</i> such that
     * <i>s</i> is not a multiple of 10.
     * These integers are the <em>significand</em> and
     * the <em>exponent</em>, respectively, of the decimal.
     * The <em>length</em> of the decimal is the (unique)
     * positive integer <i>n</i> meeting
     * 10<sup><i>n</i>-1</sup> &le; <i>s</i> &lt; 10<sup><i>n</i></sup>.
     *
     * <p>The decimal <i>d</i><sub><i>m</i></sub> for a finite positive <i>m</i>
     * is defined as follows:
     * <ul>
     * <li>Let <i>R</i> be the set of all decimals that round to <i>m</i>
     * according to the usual <em>round to nearest</em> rounding policy of
     * IEEE 754 floating-point arithmetic.
     * <li>Let <i>p</i> be the minimal length over all decimals in <i>R</i>.
     * <li>When <i>p</i> &ge; 2, let <i>T</i> be the set of all decimals
     * in <i>R</i> with length <i>p</i>.
     * Otherwise, let <i>T</i> be the set of all decimals
     * in <i>R</i> with length 1 or 2.
     * <li>Define <i>d</i><sub><i>m</i></sub> as the decimal in <i>T</i>
     * that is closest to <i>m</i>.
     * Or if there are two such decimals in <i>T</i>,
     * select the one with the even significand.
     * </ul>
     *
     * <p>The (uniquely) selected decimal <i>d</i><sub><i>m</i></sub>
     * is then formatted.
     * Let <i>s</i>, <i>i</i> and <i>n</i> be the significand, exponent and
     * length of <i>d</i><sub><i>m</i></sub>, respectively.
     * Further, let <i>e</i> = <i>n</i> + <i>i</i> - 1 and let
     * <i>s</i><sub>1</sub>&hellip;<i>s</i><sub><i>n</i></sub>
     * be the usual decimal expansion of <i>s</i>.
     * Note that <i>s</i><sub>1</sub> &ne; 0
     * and <i>s</i><sub><i>n</i></sub> &ne; 0.
     * Below, the decimal point {@code '.'} is {@code '\u005Cu002E'}
     * and the exponent indicator {@code 'E'} is {@code '\u005Cu0045'}.
     * <ul>
     * <li>Case -3 &le; <i>e</i> &lt; 0:
     * <i>d</i><sub><i>m</i></sub> is formatted as
     * <code>0.0</code>&hellip;<code>0</code><!--
     * --><i>s</i><sub>1</sub>&hellip;<i>s</i><sub><i>n</i></sub>,
     * where there are exactly -(<i>n</i> + <i>i</i>) zeroes between
     * the decimal point and <i>s</i><sub>1</sub>.
     * For example, 123 &times; 10<sup>-4</sup> is formatted as
     * {@code 0.0123}.
     * <li>Case 0 &le; <i>e</i> &lt; 7:
     * <ul>
     * <li>Subcase <i>i</i> &ge; 0:
     * <i>d</i><sub><i>m</i></sub> is formatted as
     * <i>s</i><sub>1</sub>&hellip;<i>s</i><sub><i>n</i></sub><!--
     * --><code>0</code>&hellip;<code>0.0</code>,
     * where there are exactly <i>i</i> zeroes
     * between <i>s</i><sub><i>n</i></sub> and the decimal point.
     * For example, 123 &times; 10<sup>2</sup> is formatted as
     * {@code 12300.0}.
     * <li>Subcase <i>i</i> &lt; 0:
     * <i>d</i><sub><i>m</i></sub> is formatted as
     * <i>s</i><sub>1</sub>&hellip;<!--
     * --><i>s</i><sub><i>n</i>+<i>i</i></sub><code>.</code><!--
     * --><i>s</i><sub><i>n</i>+<i>i</i>+1</sub>&hellip;<!--
     * --><i>s</i><sub><i>n</i></sub>,
     * where there are exactly -<i>i</i> digits to the right of
     * the decimal point.
     * For example, 123 &times; 10<sup>-1</sup> is formatted as
     * {@code 12.3}.
     * </ul>
     * <li>Case <i>e</i> &lt; -3 or <i>e</i> &ge; 7:
     * computerized scientific notation is used to format
     * <i>d</i><sub><i>m</i></sub>.
     * Here <i>e</i> is formatted as by {@link Integer#toString(int)}.
     * <ul>
     * <li>Subcase <i>n</i> = 1:
     * <i>d</i><sub><i>m</i></sub> is formatted as
     * <i>s</i><sub>1</sub><code>.0E</code><i>e</i>.
     * For example, 1 &times; 10<sup>23</sup> is formatted as
     * {@code 1.0E23}.
     * <li>Subcase <i>n</i> &gt; 1:
     * <i>d</i><sub><i>m</i></sub> is formatted as
     * <i>s</i><sub>1</sub><code>.</code><i>s</i><sub>2</sub><!--
     * -->&hellip;<i>s</i><sub><i>n</i></sub><code>E</code><i>e</i>.
     * For example, 123 &times; 10<sup>-21</sup> is formatted as
     * {@code 1.23E-19}.
     * </ul>
     * </ul>
     *
     * <p>To create localized string representations of a floating-point
     * value, use subclasses of {@link java.text.NumberFormat}.
     *
     * @param   d   the {@code double} to be converted.
     * @return a string representation of the argument.
     */
    public static String toString(double d) {
        return DoubleToDecimal.toString(d);
    }

    /**
     * Returns a hexadecimal string representation of the
     * {@code double} argument. All characters mentioned below
     * are ASCII characters.
     *
     * <ul>
     * <li>If the argument is NaN, the result is the string
     *     "{@code NaN}".
     * <li>Otherwise, the result is a string that represents the sign
     * and magnitude of the argument. If the sign is negative, the
     * first character of the result is '{@code -}'
     * ({@code '\u005Cu002D'}); if the sign is positive, no sign
     * character appears in the result. As for the magnitude <i>m</i>:
     *
     * <ul>
     * <li>If <i>m</i> is infinity, it is represented by the string
     * {@code "Infinity"}; thus, positive infinity produces the
     * result {@code "Infinity"} and negative infinity produces
     * the result {@code "-Infinity"}.
     *
     * <li>If <i>m</i> is zero, it is represented by the string
     * {@code "0x0.0p0"}; thus, negative zero produces the result
     * {@code "-0x0.0p0"} and positive zero produces the result
     * {@code "0x0.0p0"}.
     *
     * <li>If <i>m</i> is a {@code double} value with a
     * normalized representation, substrings are used to represent the
     * significand and exponent fields.  The significand is
     * represented by the characters {@code "0x1."}
     * followed by a lowercase hexadecimal representation of the rest
     * of the significand as a fraction.  Trailing zeros in the
     * hexadecimal representation are removed unless all the digits
     * are zero, in which case a single zero is used. Next, the
     * exponent is represented by {@code "p"} followed
     * by a decimal string of the unbiased exponent as if produced by
     * a call to {@link Integer#toString(int) Integer.toString} on the
     * exponent value.
     *
     * <li>If <i>m</i> is a {@code double} value with a subnormal
     * representation, the significand is represented by the
     * characters {@code "0x0."} followed by a
     * hexadecimal representation of the rest of the significand as a
     * fraction.  Trailing zeros in the hexadecimal representation are
     * removed. Next, the exponent is represented by
     * {@code "p-1022"}.  Note that there must be at
     * least one nonzero digit in a subnormal significand.
     *
     * </ul>
     *
     * </ul>
     *
     * <table class="striped">
     * <caption>Examples</caption>
     * <thead>
     * <tr><th scope="col">Floating-point Value</th><th scope="col">Hexadecimal String</th>
     * </thead>
     * <tbody style="text-align:right">
     * <tr><th scope="row">{@code 1.0}</th> <td>{@code 0x1.0p0}</td>
     * <tr><th scope="row">{@code -1.0}</th>        <td>{@code -0x1.0p0}</td>
     * <tr><th scope="row">{@code 2.0}</th> <td>{@code 0x1.0p1}</td>
     * <tr><th scope="row">{@code 3.0}</th> <td>{@code 0x1.8p1}</td>
     * <tr><th scope="row">{@code 0.5}</th> <td>{@code 0x1.0p-1}</td>
     * <tr><th scope="row">{@code 0.25}</th>        <td>{@code 0x1.0p-2}</td>
     * <tr><th scope="row">{@code Double.MAX_VALUE}</th>
     *     <td>{@code 0x1.fffffffffffffp1023}</td>
     * <tr><th scope="row">{@code Minimum Normal Value}</th>
     *     <td>{@code 0x1.0p-1022}</td>
     * <tr><th scope="row">{@code Maximum Subnormal Value}</th>
     *     <td>{@code 0x0.fffffffffffffp-1022}</td>
     * <tr><th scope="row">{@code Double.MIN_VALUE}</th>
     *     <td>{@code 0x0.0000000000001p-1022}</td>
     * </tbody>
     * </table>
     * @param   d   the {@code double} to be converted.
     * @return a hex string representation of the argument.
     * @since 1.5
     * @author Joseph D. Darcy
     */
    public static String toHexString(double d) {
        /*
         * Modeled after the "a" conversion specifier in C99, section
         * 7.19.6.1; however, the output of this method is more
         * tightly specified.
         */
        if (!isFinite(d) )
            // For infinity and NaN, use the decimal output.
            return Double.toString(d);
        else {
            // Initialized to maximum size of output.
            StringBuilder answer = new StringBuilder(24);

            if (Math.copySign(1.0, d) == -1.0)    // value is negative,
                answer.append("-");                  // so append sign info

            answer.append("0x");

            d = Math.abs(d);

            if(d == 0.0) {
                answer.append("0.0p0");
            } else {
                boolean subnormal = (d < Double.MIN_NORMAL);

                // Isolate significand bits and OR in a high-order bit
                // so that the string representation has a known
                // length.
                long signifBits = (Double.doubleToLongBits(d)
                                   & DoubleConsts.SIGNIF_BIT_MASK) |
                    0x1000000000000000L;

                // Subnormal values have a 0 implicit bit; normal
                // values have a 1 implicit bit.
                answer.append(subnormal ? "0." : "1.");

                // Isolate the low-order 13 digits of the hex
                // representation.  If all the digits are zero,
                // replace with a single 0; otherwise, remove all
                // trailing zeros.
                String signif = Long.toHexString(signifBits).substring(3,16);
                answer.append(signif.equals("0000000000000") ? // 13 zeros
                              "0":
                              signif.replaceFirst("0{1,12}$", ""));

                answer.append('p');
                // If the value is subnormal, use the E_min exponent
                // value for double; otherwise, extract and report d's
                // exponent (the representation of a subnormal uses
                // E_min -1).
                answer.append(subnormal ?
                              Double.MIN_EXPONENT:
                              Math.getExponent(d));
            }
            return answer.toString();
        }
    }

    /**
     * Returns a {@code Double} object holding the
     * {@code double} value represented by the argument string
     * {@code s}.
     *
     * <p>If {@code s} is {@code null}, then a
     * {@code NullPointerException} is thrown.
     *
     * <p>Leading and trailing whitespace characters in {@code s}
     * are ignored.  Whitespace is removed as if by the {@link
     * String#trim} method; that is, both ASCII space and control
     * characters are removed. The rest of {@code s} should
     * constitute a <i>FloatValue</i> as described by the lexical
     * syntax rules:
     *
     * <blockquote>
     * <dl>
     * <dt><i>FloatValue:</i>
     * <dd><i>Sign<sub>opt</sub></i> {@code NaN}
     * <dd><i>Sign<sub>opt</sub></i> {@code Infinity}
     * <dd><i>Sign<sub>opt</sub> FloatingPointLiteral</i>
     * <dd><i>Sign<sub>opt</sub> HexFloatingPointLiteral</i>
     * <dd><i>SignedInteger</i>
     * </dl>
     *
     * <dl>
     * <dt><i>HexFloatingPointLiteral</i>:
     * <dd> <i>HexSignificand BinaryExponent FloatTypeSuffix<sub>opt</sub></i>
     * </dl>
     *
     * <dl>
     * <dt><i>HexSignificand:</i>
     * <dd><i>HexNumeral</i>
     * <dd><i>HexNumeral</i> {@code .}
     * <dd>{@code 0x} <i>HexDigits<sub>opt</sub>
     *     </i>{@code .}<i> HexDigits</i>
     * <dd>{@code 0X}<i> HexDigits<sub>opt</sub>
     *     </i>{@code .} <i>HexDigits</i>
     * </dl>
     *
     * <dl>
     * <dt><i>BinaryExponent:</i>
     * <dd><i>BinaryExponentIndicator SignedInteger</i>
     * </dl>
     *
     * <dl>
     * <dt><i>BinaryExponentIndicator:</i>
     * <dd>{@code p}
     * <dd>{@code P}
     * </dl>
     *
     * </blockquote>
     *
     * where <i>Sign</i>, <i>FloatingPointLiteral</i>,
     * <i>HexNumeral</i>, <i>HexDigits</i>, <i>SignedInteger</i> and
     * <i>FloatTypeSuffix</i> are as defined in the lexical structure
     * sections of
     * <cite>The Java Language Specification</cite>,
     * except that underscores are not accepted between digits.
     * If {@code s} does not have the form of
     * a <i>FloatValue</i>, then a {@code NumberFormatException}
     * is thrown. Otherwise, {@code s} is regarded as
     * representing an exact decimal value in the usual
     * "computerized scientific notation" or as an exact
     * hexadecimal value; this exact numerical value is then
     * conceptually converted to an "infinitely precise"
     * binary value that is then rounded to type {@code double}
     * by the usual round-to-nearest rule of IEEE 754 floating-point
     * arithmetic, which includes preserving the sign of a zero
     * value.
     *
     * Note that the round-to-nearest rule also implies overflow and
     * underflow behaviour; if the exact value of {@code s} is large
     * enough in magnitude (greater than or equal to ({@link
     * #MAX_VALUE} + {@link Math#ulp(double) ulp(MAX_VALUE)}/2),
     * rounding to {@code double} will result in an infinity and if the
     * exact value of {@code s} is small enough in magnitude (less
     * than or equal to {@link #MIN_VALUE}/2), rounding to float will
     * result in a zero.
     *
     * Finally, after rounding a {@code Double} object representing
     * this {@code double} value is returned.
     *
     * <p>Note that trailing format specifiers, specifiers that
     * determine the type of a floating-point literal
     * ({@code 1.0f} is a {@code float} value;
     * {@code 1.0d} is a {@code double} value), do
     * <em>not</em> influence the results of this method.  In other
     * words, the numerical value of the input string is converted
     * directly to the target floating-point type.  The two-step
     * sequence of conversions, string to {@code float} followed
     * by {@code float} to {@code double}, is <em>not</em>
     * equivalent to converting a string directly to
     * {@code double}. For example, the {@code float}
     * literal {@code 0.1f} is equal to the {@code double}
     * value {@code 0.10000000149011612}; the {@code float}
     * literal {@code 0.1f} represents a different numerical
     * value than the {@code double} literal
     * {@code 0.1}. (The numerical value 0.1 cannot be exactly
     * represented in a binary floating-point number.)
     *
     * <p>To avoid calling this method on an invalid string and having
     * a {@code NumberFormatException} be thrown, the regular
     * expression below can be used to screen the input string:
     *
     * {@snippet lang="java" :
     *  final String Digits     = "(\\p{Digit}+)";
     *  final String HexDigits  = "(\\p{XDigit}+)";
     *  // an exponent is 'e' or 'E' followed by an optionally
     *  // signed decimal integer.
     *  final String Exp        = "[eE][+-]?"+Digits;
     *  final String fpRegex    =
     *      ("[\\x00-\\x20]*"+  // Optional leading "whitespace"
     *       "[+-]?(" + // Optional sign character
     *       "NaN|" +           // "NaN" string
     *       "Infinity|" +      // "Infinity" string
     *
     *       // A decimal floating-point string representing a finite positive
     *       // number without a leading sign has at most five basic pieces:
     *       // Digits . Digits ExponentPart FloatTypeSuffix
     *       //
     *       // Since this method allows integer-only strings as input
     *       // in addition to strings of floating-point literals, the
     *       // two sub-patterns below are simplifications of the grammar
     *       // productions from section 3.10.2 of
     *       // The Java Language Specification.
     *
     *       // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
     *       "((("+Digits+"(\\.)?("+Digits+"?)("+Exp+")?)|"+
     *
     *       // . Digits ExponentPart_opt FloatTypeSuffix_opt
     *       "(\\.("+Digits+")("+Exp+")?)|"+
     *
     *       // Hexadecimal strings
     *       "((" +
     *        // 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
     *        "(0[xX]" + HexDigits + "(\\.)?)|" +
     *
     *        // 0[xX] HexDigits_opt . HexDigits BinaryExponent FloatTypeSuffix_opt
     *        "(0[xX]" + HexDigits + "?(\\.)" + HexDigits + ")" +
     *
     *        ")[pP][+-]?" + Digits + "))" +
     *       "[fFdD]?))" +
     *       "[\\x00-\\x20]*");// Optional trailing "whitespace"
     *  // @link region substring="Pattern.matches" target ="java.util.regex.Pattern#matches"
     *  if (Pattern.matches(fpRegex, myString))
     *      Double.valueOf(myString); // Will not throw NumberFormatException
     * // @end
     *  else {
     *      // Perform suitable alternative action
     *  }
     * }
     *
     * @apiNote To interpret localized string representations of a
     * floating-point value, or string representations that have
     * non-ASCII digits, use {@link java.text.NumberFormat}. For
     * example,
     * {@snippet lang="java" :
     *     NumberFormat.getInstance(l).parse(s).doubleValue();
     * }
     * where {@code l} is the desired locale, or
     * {@link java.util.Locale#ROOT} if locale insensitive.
     *
     * @param      s   the string to be parsed.
     * @return     a {@code Double} object holding the value
     *             represented by the {@code String} argument.
     * @throws     NumberFormatException  if the string does not contain a
     *             parsable number.
     * @see Double##decimalToBinaryConversion Decimal &harr; Binary Conversion Issues
     */
    public static Double valueOf(String s) throws NumberFormatException {
        return new Double(parseDouble(s));
    }

    /**
     * Returns a {@code Double} instance representing the specified
     * {@code double} value.
     * If a new {@code Double} instance is not required, this method
     * should generally be used in preference to the constructor
     * {@link #Double(double)}, as this method is likely to yield
     * significantly better space and time performance by caching
     * frequently requested values.
     *
     * @param  d a double value.
     * @return a {@code Double} instance representing {@code d}.
     * @since  1.5
     */
    @IntrinsicCandidate
    public static Double valueOf(double d) {
        return new Double(d);
    }

    /**
     * Returns a new {@code double} initialized to the value
     * represented by the specified {@code String}, as performed
     * by the {@code valueOf} method of class
     * {@code Double}.
     *
     * @param  s   the string to be parsed.
     * @return the {@code double} value represented by the string
     *         argument.
     * @throws NullPointerException  if the string is null
     * @throws NumberFormatException if the string does not contain
     *         a parsable {@code double}.
     * @see    java.lang.Double#valueOf(String)
     * @see    Double##decimalToBinaryConversion Decimal &harr; Binary Conversion Issues
     * @since 1.2
     */
    public static double parseDouble(String s) throws NumberFormatException {
        return FloatingDecimal.parseDouble(s);
    }

    /**
     * Returns {@code true} if the specified number is a
     * Not-a-Number (NaN) value, {@code false} otherwise.
     *
     * @apiNote
     * This method corresponds to the isNaN operation defined in IEEE
     * 754.
     *
     * @param   v   the value to be tested.
     * @return  {@code true} if the value of the argument is NaN;
     *          {@code false} otherwise.
     */
    public static boolean isNaN(double v) {
        return (v != v);
    }

    /**
     * Returns {@code true} if the specified number is infinitely
     * large in magnitude, {@code false} otherwise.
     *
     * @apiNote
     * This method corresponds to the isInfinite operation defined in
     * IEEE 754.
     *
     * @param   v   the value to be tested.
     * @return  {@code true} if the value of the argument is positive
     *          infinity or negative infinity; {@code false} otherwise.
     */
    @IntrinsicCandidate
    public static boolean isInfinite(double v) {
        return Math.abs(v) > MAX_VALUE;
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
     * @param d the {@code double} value to be tested
     * @return {@code true} if the argument is a finite
     * floating-point value, {@code false} otherwise.
     * @since 1.8
     */
    @IntrinsicCandidate
    public static boolean isFinite(double d) {
        return Math.abs(d) <= Double.MAX_VALUE;
    }

    /**
     * The value of the Double.
     *
     * @serial
     */
    private final double value;

    /**
     * Constructs a newly allocated {@code Double} object that
     * represents the primitive {@code double} argument.
     *
     * @param   value   the value to be represented by the {@code Double}.
     *
     * @deprecated
     * It is rarely appropriate to use this constructor. The static factory
     * {@link #valueOf(double)} is generally a better choice, as it is
     * likely to yield significantly better space and time performance.
     */
    @Deprecated(since="9", forRemoval = true)
    public Double(double value) {
        this.value = value;
    }

    /**
     * Constructs a newly allocated {@code Double} object that
     * represents the floating-point value of type {@code double}
     * represented by the string. The string is converted to a
     * {@code double} value as if by the {@code valueOf} method.
     *
     * @param  s  a string to be converted to a {@code Double}.
     * @throws    NumberFormatException if the string does not contain a
     *            parsable number.
     *
     * @deprecated
     * It is rarely appropriate to use this constructor.
     * Use {@link #parseDouble(String)} to convert a string to a
     * {@code double} primitive, or use {@link #valueOf(String)}
     * to convert a string to a {@code Double} object.
     */
    @Deprecated(since="9", forRemoval = true)
    public Double(String s) throws NumberFormatException {
        value = parseDouble(s);
    }

    /**
     * Returns {@code true} if this {@code Double} value is
     * a Not-a-Number (NaN), {@code false} otherwise.
     *
     * @return  {@code true} if the value represented by this object is
     *          NaN; {@code false} otherwise.
     */
    public boolean isNaN() {
        return isNaN(value);
    }

    /**
     * Returns {@code true} if this {@code Double} value is
     * infinitely large in magnitude, {@code false} otherwise.
     *
     * @return  {@code true} if the value represented by this object is
     *          positive infinity or negative infinity;
     *          {@code false} otherwise.
     */
    public boolean isInfinite() {
        return isInfinite(value);
    }

    /**
     * Returns a string representation of this {@code Double} object.
     * The primitive {@code double} value represented by this
     * object is converted to a string exactly as if by the method
     * {@code toString} of one argument.
     *
     * @return  a {@code String} representation of this object.
     * @see java.lang.Double#toString(double)
     */
    public String toString() {
        return toString(value);
    }

    /**
     * Returns the value of this {@code Double} as a {@code byte}
     * after a narrowing primitive conversion.
     *
     * @return  the {@code double} value represented by this object
     *          converted to type {@code byte}
     * @jls 5.1.3 Narrowing Primitive Conversion
     * @since 1.1
     */
    public byte byteValue() {
        return (byte)value;
    }

    /**
     * Returns the value of this {@code Double} as a {@code short}
     * after a narrowing primitive conversion.
     *
     * @return  the {@code double} value represented by this object
     *          converted to type {@code short}
     * @jls 5.1.3 Narrowing Primitive Conversion
     * @since 1.1
     */
    public short shortValue() {
        return (short)value;
    }

    /**
     * Returns the value of this {@code Double} as an {@code int}
     * after a narrowing primitive conversion.
     * @jls 5.1.3 Narrowing Primitive Conversion
     *
     * @return  the {@code double} value represented by this object
     *          converted to type {@code int}
     */
    public int intValue() {
        return (int)value;
    }

    /**
     * Returns the value of this {@code Double} as a {@code long}
     * after a narrowing primitive conversion.
     *
     * @return  the {@code double} value represented by this object
     *          converted to type {@code long}
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    public long longValue() {
        return (long)value;
    }

    /**
     * Returns the value of this {@code Double} as a {@code float}
     * after a narrowing primitive conversion.
     *
     * @apiNote
     * This method corresponds to the convertFormat operation defined
     * in IEEE 754.
     *
     * @return  the {@code double} value represented by this object
     *          converted to type {@code float}
     * @jls 5.1.3 Narrowing Primitive Conversion
     * @since 1.0
     */
    public float floatValue() {
        return (float)value;
    }

    /**
     * Returns the {@code double} value of this {@code Double} object.
     *
     * @return the {@code double} value represented by this object
     */
    @IntrinsicCandidate
    public double doubleValue() {
        return value;
    }

    /**
     * Returns a hash code for this {@code Double} object. The
     * result is the exclusive OR of the two halves of the
     * {@code long} integer bit representation, exactly as
     * produced by the method {@link #doubleToLongBits(double)}, of
     * the primitive {@code double} value represented by this
     * {@code Double} object. That is, the hash code is the value
     * of the expression:
     *
     * <blockquote>
     *  {@code (int)(v^(v>>>32))}
     * </blockquote>
     *
     * where {@code v} is defined by:
     *
     * <blockquote>
     *  {@code long v = Double.doubleToLongBits(this.doubleValue());}
     * </blockquote>
     *
     * @return  a {@code hash code} value for this object.
     */
    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    /**
     * Returns a hash code for a {@code double} value; compatible with
     * {@code Double.hashCode()}.
     *
     * @param value the value to hash
     * @return a hash code value for a {@code double} value.
     * @since 1.8
     */
    public static int hashCode(double value) {
        return Long.hashCode(doubleToLongBits(value));
    }

    /**
     * Compares this object against the specified object.  The result
     * is {@code true} if and only if the argument is not
     * {@code null} and is a {@code Double} object that
     * represents a {@code double} that has the same value as the
     * {@code double} represented by this object. For this
     * purpose, two {@code double} values are considered to be
     * the same if and only if the method {@link
     * #doubleToLongBits(double)} returns the identical
     * {@code long} value when applied to each.
     *
     * @apiNote
     * This method is defined in terms of {@link
     * #doubleToLongBits(double)} rather than the {@code ==} operator
     * on {@code double} values since the {@code ==} operator does
     * <em>not</em> define an equivalence relation and to satisfy the
     * {@linkplain Object#equals equals contract} an equivalence
     * relation must be implemented; see <a
     * href="#equivalenceRelation">this discussion</a> for details of
     * floating-point equality and equivalence.
     *
     * @see java.lang.Double#doubleToLongBits(double)
     * @jls 15.21.1 Numerical Equality Operators == and !=
     */
    public boolean equals(Object obj) {
        return (obj instanceof Double)
               && (doubleToLongBits(((Double)obj).value) ==
                      doubleToLongBits(value));
    }

    /**
     * Returns a representation of the specified floating-point value
     * according to the IEEE 754 floating-point "double
     * format" bit layout.
     *
     * <p>Bit 63 (the bit that is selected by the mask
     * {@code 0x8000000000000000L}) represents the sign of the
     * floating-point number. Bits
     * 62-52 (the bits that are selected by the mask
     * {@code 0x7ff0000000000000L}) represent the exponent. Bits 51-0
     * (the bits that are selected by the mask
     * {@code 0x000fffffffffffffL}) represent the significand
     * (sometimes called the mantissa) of the floating-point number.
     *
     * <p>If the argument is positive infinity, the result is
     * {@code 0x7ff0000000000000L}.
     *
     * <p>If the argument is negative infinity, the result is
     * {@code 0xfff0000000000000L}.
     *
     * <p>If the argument is NaN, the result is
     * {@code 0x7ff8000000000000L}.
     *
     * <p>In all cases, the result is a {@code long} integer that, when
     * given to the {@link #longBitsToDouble(long)} method, will produce a
     * floating-point value the same as the argument to
     * {@code doubleToLongBits} (except all NaN values are
     * collapsed to a single "canonical" NaN value).
     *
     * @param   value   a {@code double} precision floating-point number.
     * @return the bits that represent the floating-point number.
     */
    @IntrinsicCandidate
    public static long doubleToLongBits(double value) {
        if (!isNaN(value)) {
            return doubleToRawLongBits(value);
        }
        return 0x7ff8000000000000L;
    }

    /**
     * Returns a representation of the specified floating-point value
     * according to the IEEE 754 floating-point "double
     * format" bit layout, preserving Not-a-Number (NaN) values.
     *
     * <p>Bit 63 (the bit that is selected by the mask
     * {@code 0x8000000000000000L}) represents the sign of the
     * floating-point number. Bits
     * 62-52 (the bits that are selected by the mask
     * {@code 0x7ff0000000000000L}) represent the exponent. Bits 51-0
     * (the bits that are selected by the mask
     * {@code 0x000fffffffffffffL}) represent the significand
     * (sometimes called the mantissa) of the floating-point number.
     *
     * <p>If the argument is positive infinity, the result is
     * {@code 0x7ff0000000000000L}.
     *
     * <p>If the argument is negative infinity, the result is
     * {@code 0xfff0000000000000L}.
     *
     * <p>If the argument is NaN, the result is the {@code long}
     * integer representing the actual NaN value.  Unlike the
     * {@code doubleToLongBits} method,
     * {@code doubleToRawLongBits} does not collapse all the bit
     * patterns encoding a NaN to a single "canonical" NaN
     * value.
     *
     * <p>In all cases, the result is a {@code long} integer that,
     * when given to the {@link #longBitsToDouble(long)} method, will
     * produce a floating-point value the same as the argument to
     * {@code doubleToRawLongBits}.
     *
     * @param   value   a {@code double} precision floating-point number.
     * @return the bits that represent the floating-point number.
     * @since 1.3
     */
    @IntrinsicCandidate
    public static native long doubleToRawLongBits(double value);

    /**
     * Returns the {@code double} value corresponding to a given
     * bit representation.
     * The argument is considered to be a representation of a
     * floating-point value according to the IEEE 754 floating-point
     * "double format" bit layout.
     *
     * <p>If the argument is {@code 0x7ff0000000000000L}, the result
     * is positive infinity.
     *
     * <p>If the argument is {@code 0xfff0000000000000L}, the result
     * is negative infinity.
     *
     * <p>If the argument is any value in the range
     * {@code 0x7ff0000000000001L} through
     * {@code 0x7fffffffffffffffL} or in the range
     * {@code 0xfff0000000000001L} through
     * {@code 0xffffffffffffffffL}, the result is a NaN.  No IEEE
     * 754 floating-point operation provided by Java can distinguish
     * between two NaN values of the same type with different bit
     * patterns.  Distinct values of NaN are only distinguishable by
     * use of the {@code Double.doubleToRawLongBits} method.
     *
     * <p>In all other cases, let <i>s</i>, <i>e</i>, and <i>m</i> be three
     * values that can be computed from the argument:
     *
     * {@snippet lang="java" :
     * int s = ((bits >> 63) == 0) ? 1 : -1;
     * int e = (int)((bits >> 52) & 0x7ffL);
     * long m = (e == 0) ?
     *                 (bits & 0xfffffffffffffL) << 1 :
     *                 (bits & 0xfffffffffffffL) | 0x10000000000000L;
     * }
     *
     * Then the floating-point result equals the value of the mathematical
     * expression <i>s</i>&middot;<i>m</i>&middot;2<sup><i>e</i>-1075</sup>.
     *
     * <p>Note that this method may not be able to return a
     * {@code double} NaN with exactly same bit pattern as the
     * {@code long} argument.  IEEE 754 distinguishes between two
     * kinds of NaNs, quiet NaNs and <i>signaling NaNs</i>.  The
     * differences between the two kinds of NaN are generally not
     * visible in Java.  Arithmetic operations on signaling NaNs turn
     * them into quiet NaNs with a different, but often similar, bit
     * pattern.  However, on some processors merely copying a
     * signaling NaN also performs that conversion.  In particular,
     * copying a signaling NaN to return it to the calling method
     * may perform this conversion.  So {@code longBitsToDouble}
     * may not be able to return a {@code double} with a
     * signaling NaN bit pattern.  Consequently, for some
     * {@code long} values,
     * {@code doubleToRawLongBits(longBitsToDouble(start))} may
     * <i>not</i> equal {@code start}.  Moreover, which
     * particular bit patterns represent signaling NaNs is platform
     * dependent; although all NaN bit patterns, quiet or signaling,
     * must be in the NaN range identified above.
     *
     * @param   bits   any {@code long} integer.
     * @return  the {@code double} floating-point value with the same
     *          bit pattern.
     */
    @IntrinsicCandidate
    public static native double longBitsToDouble(long bits);

    /**
     * Compares two {@code Double} objects numerically.
     *
     * This method imposes a total order on {@code Double} objects
     * with two differences compared to the incomplete order defined by
     * the Java language numerical comparison operators ({@code <, <=,
     * ==, >=, >}) on {@code double} values.
     *
     * <ul><li> A NaN is <em>unordered</em> with respect to other
     *          values and unequal to itself under the comparison
     *          operators.  This method chooses to define {@code
     *          Double.NaN} to be equal to itself and greater than all
     *          other {@code double} values (including {@code
     *          Double.POSITIVE_INFINITY}).
     *
     *      <li> Positive zero and negative zero compare equal
     *      numerically, but are distinct and distinguishable values.
     *      This method chooses to define positive zero ({@code +0.0d}),
     *      to be greater than negative zero ({@code -0.0d}).
     * </ul>

     * This ensures that the <i>natural ordering</i> of {@code Double}
     * objects imposed by this method is <i>consistent with
     * equals</i>; see <a href="#equivalenceRelation">this
     * discussion</a> for details of floating-point comparison and
     * ordering.
     *
     * @param   anotherDouble   the {@code Double} to be compared.
     * @return  the value {@code 0} if {@code anotherDouble} is
     *          numerically equal to this {@code Double}; a value
     *          less than {@code 0} if this {@code Double}
     *          is numerically less than {@code anotherDouble};
     *          and a value greater than {@code 0} if this
     *          {@code Double} is numerically greater than
     *          {@code anotherDouble}.
     *
     * @jls 15.20.1 Numerical Comparison Operators {@code <}, {@code <=}, {@code >}, and {@code >=}
     * @since   1.2
     */
    public int compareTo(Double anotherDouble) {
        return Double.compare(value, anotherDouble.value);
    }

    /**
     * Compares the two specified {@code double} values. The sign
     * of the integer value returned is the same as that of the
     * integer that would be returned by the call:
     * <pre>
     *    Double.valueOf(d1).compareTo(Double.valueOf(d2))
     * </pre>
     *
     * @param   d1        the first {@code double} to compare
     * @param   d2        the second {@code double} to compare
     * @return  the value {@code 0} if {@code d1} is
     *          numerically equal to {@code d2}; a value less than
     *          {@code 0} if {@code d1} is numerically less than
     *          {@code d2}; and a value greater than {@code 0}
     *          if {@code d1} is numerically greater than
     *          {@code d2}.
     * @since 1.4
     */
    public static int compare(double d1, double d2) {
        if (d1 < d2)
            return -1;           // Neither val is NaN, thisVal is smaller
        if (d1 > d2)
            return 1;            // Neither val is NaN, thisVal is larger

        // Cannot use doubleToRawLongBits because of possibility of NaNs.
        long thisBits    = Double.doubleToLongBits(d1);
        long anotherBits = Double.doubleToLongBits(d2);

        return (thisBits == anotherBits ?  0 : // Values are equal
                (thisBits < anotherBits ? -1 : // (-0.0, 0.0) or (!NaN, NaN)
                 1));                          // (0.0, -0.0) or (NaN, !NaN)
    }

    /**
     * Adds two {@code double} values together as per the + operator.
     *
     * @apiNote This method corresponds to the addition operation
     * defined in IEEE 754.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the sum of {@code a} and {@code b}
     * @jls 4.2.4 Floating-Point Operations
     * @see java.util.function.BinaryOperator
     * @since 1.8
     */
    public static double sum(double a, double b) {
        return a + b;
    }

    /**
     * Returns the greater of two {@code double} values
     * as if by calling {@link Math#max(double, double) Math.max}.
     *
     * @apiNote
     * This method corresponds to the maximum operation defined in
     * IEEE 754.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the greater of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 1.8
     */
    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    /**
     * Returns the smaller of two {@code double} values
     * as if by calling {@link Math#min(double, double) Math.min}.
     *
     * @apiNote
     * This method corresponds to the minimum operation defined in
     * IEEE 754.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the smaller of {@code a} and {@code b}.
     * @see java.util.function.BinaryOperator
     * @since 1.8
     */
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    /**
     * Returns an {@link Optional} containing the nominal descriptor for this
     * instance, which is the instance itself.
     *
     * @return an {@link Optional} describing the {@linkplain Double} instance
     * @since 12
     */
    @Override
    public Optional<Double> describeConstable() {
        return Optional.of(this);
    }

    /**
     * Resolves this instance as a {@link ConstantDesc}, the result of which is
     * the instance itself.
     *
     * @param lookup ignored
     * @return the {@linkplain Double} instance
     * @since 12
     */
    @Override
    public Double resolveConstantDesc(MethodHandles.Lookup lookup) {
        return this;
    }

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = -9172774392245257468L;
}
