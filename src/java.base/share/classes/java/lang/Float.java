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

import jdk.internal.math.FloatConsts;
import jdk.internal.math.FloatingDecimal;
import jdk.internal.math.FloatToDecimal;
import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * The {@code Float} class wraps a value of primitive type
 * {@code float} in an object. An object of type
 * {@code Float} contains a single field whose type is
 * {@code float}.
 *
 * <p>In addition, this class provides several methods for converting a
 * {@code float} to a {@code String} and a
 * {@code String} to a {@code float}, as well as other
 * constants and methods useful when dealing with a
 * {@code float}.
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
 * equally applicable to {@code float} values.
 *
 * <h2><a id=decimalToBinaryConversion>Decimal &harr; Binary Conversion Issues</a></h2>
 *
 * The {@linkplain Double##decimalToBinaryConversion discussion of binary to
 * decimal conversion issues} in {@code java.lang.Double} is also
 * applicable to {@code float} values.
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
public final class Float extends Number
        implements Comparable<Float>, Constable, ConstantDesc {
    /**
     * A constant holding the positive infinity of type
     * {@code float}. It is equal to the value returned by
     * {@code Float.intBitsToFloat(0x7f800000)}.
     */
    public static final float POSITIVE_INFINITY = 1.0f / 0.0f;

    /**
     * A constant holding the negative infinity of type
     * {@code float}. It is equal to the value returned by
     * {@code Float.intBitsToFloat(0xff800000)}.
     */
    public static final float NEGATIVE_INFINITY = -1.0f / 0.0f;

    /**
     * A constant holding a Not-a-Number (NaN) value of type
     * {@code float}.  It is equivalent to the value returned by
     * {@code Float.intBitsToFloat(0x7fc00000)}.
     */
    public static final float NaN = 0.0f / 0.0f;

    /**
     * A constant holding the largest positive finite value of type
     * {@code float}, (2-2<sup>-23</sup>)&middot;2<sup>127</sup>.
     * It is equal to the hexadecimal floating-point literal
     * {@code 0x1.fffffeP+127f} and also equal to
     * {@code Float.intBitsToFloat(0x7f7fffff)}.
     */
    public static final float MAX_VALUE = 0x1.fffffeP+127f; // 3.4028235e+38f

    /**
     * A constant holding the smallest positive normal value of type
     * {@code float}, 2<sup>-126</sup>.  It is equal to the
     * hexadecimal floating-point literal {@code 0x1.0p-126f} and also
     * equal to {@code Float.intBitsToFloat(0x00800000)}.
     *
     * @since 1.6
     */
    public static final float MIN_NORMAL = 0x1.0p-126f; // 1.17549435E-38f

    /**
     * A constant holding the smallest positive nonzero value of type
     * {@code float}, 2<sup>-149</sup>. It is equal to the
     * hexadecimal floating-point literal {@code 0x0.000002P-126f}
     * and also equal to {@code Float.intBitsToFloat(0x1)}.
     */
    public static final float MIN_VALUE = 0x0.000002P-126f; // 1.4e-45f

    /**
     * The number of bits used to represent a {@code float} value.
     *
     * @since 1.5
     */
    public static final int SIZE = 32;

    /**
     * The number of bits in the significand of a {@code float} value.
     * This is the parameter N in section {@jls 4.2.3} of
     * <cite>The Java Language Specification</cite>.
     *
     * @since 19
     */
    public static final int PRECISION = 24;

    /**
     * Maximum exponent a finite {@code float} variable may have.  It
     * is equal to the value returned by {@code
     * Math.getExponent(Float.MAX_VALUE)}.
     *
     * @since 1.6
     */
    public static final int MAX_EXPONENT = (1 << (SIZE - PRECISION - 1)) - 1; // 127

    /**
     * Minimum exponent a normalized {@code float} variable may have.
     * It is equal to the value returned by {@code
     * Math.getExponent(Float.MIN_NORMAL)}.
     *
     * @since 1.6
     */
    public static final int MIN_EXPONENT = 1 - MAX_EXPONENT; // -126

    /**
     * The number of bytes used to represent a {@code float} value.
     *
     * @since 1.8
     */
    public static final int BYTES = SIZE / Byte.SIZE;

    /**
     * The {@code Class} instance representing the primitive type
     * {@code float}.
     *
     * @since 1.1
     */
    @SuppressWarnings("unchecked")
    public static final Class<Float> TYPE = (Class<Float>) Class.getPrimitiveClass("float");

    /**
     * Returns a string representation of the {@code float}
     * argument. All characters mentioned below are ASCII characters.
     * <ul>
     * <li>If the argument is NaN, the result is the string
     * "{@code NaN}".
     * <li>Otherwise, the result is a string that represents the sign and
     *     magnitude (absolute value) of the argument. If the sign is
     *     negative, the first character of the result is
     *     '{@code -}' ({@code '\u005Cu002D'}); if the sign is
     *     positive, no sign character appears in the result. As for
     *     the magnitude <i>m</i>:
     * <ul>
     * <li>If <i>m</i> is infinity, it is represented by the characters
     *     {@code "Infinity"}; thus, positive infinity produces
     *     the result {@code "Infinity"} and negative infinity
     *     produces the result {@code "-Infinity"}.
     * <li>If <i>m</i> is zero, it is represented by the characters
     *     {@code "0.0"}; thus, negative zero produces the result
     *     {@code "-0.0"} and positive zero produces the result
     *     {@code "0.0"}.
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
     * @param   f   the {@code float} to be converted.
     * @return a string representation of the argument.
     */
    public static String toString(float f) {
        return FloatToDecimal.toString(f);
    }

    /**
     * Returns a hexadecimal string representation of the
     * {@code float} argument. All characters mentioned below are
     * ASCII characters.
     *
     * <ul>
     * <li>If the argument is NaN, the result is the string
     *     "{@code NaN}".
     * <li>Otherwise, the result is a string that represents the sign and
     * magnitude (absolute value) of the argument. If the sign is negative,
     * the first character of the result is '{@code -}'
     * ({@code '\u005Cu002D'}); if the sign is positive, no sign character
     * appears in the result. As for the magnitude <i>m</i>:
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
     * <li>If <i>m</i> is a {@code float} value with a
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
     * <li>If <i>m</i> is a {@code float} value with a subnormal
     * representation, the significand is represented by the
     * characters {@code "0x0."} followed by a
     * hexadecimal representation of the rest of the significand as a
     * fraction.  Trailing zeros in the hexadecimal representation are
     * removed. Next, the exponent is represented by
     * {@code "p-126"}.  Note that there must be at
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
     * <tbody>
     * <tr><th scope="row">{@code 1.0}</th> <td>{@code 0x1.0p0}</td>
     * <tr><th scope="row">{@code -1.0}</th>        <td>{@code -0x1.0p0}</td>
     * <tr><th scope="row">{@code 2.0}</th> <td>{@code 0x1.0p1}</td>
     * <tr><th scope="row">{@code 3.0}</th> <td>{@code 0x1.8p1}</td>
     * <tr><th scope="row">{@code 0.5}</th> <td>{@code 0x1.0p-1}</td>
     * <tr><th scope="row">{@code 0.25}</th>        <td>{@code 0x1.0p-2}</td>
     * <tr><th scope="row">{@code Float.MAX_VALUE}</th>
     *     <td>{@code 0x1.fffffep127}</td>
     * <tr><th scope="row">{@code Minimum Normal Value}</th>
     *     <td>{@code 0x1.0p-126}</td>
     * <tr><th scope="row">{@code Maximum Subnormal Value}</th>
     *     <td>{@code 0x0.fffffep-126}</td>
     * <tr><th scope="row">{@code Float.MIN_VALUE}</th>
     *     <td>{@code 0x0.000002p-126}</td>
     * </tbody>
     * </table>
     * @param   f   the {@code float} to be converted.
     * @return a hex string representation of the argument.
     * @since 1.5
     * @author Joseph D. Darcy
     */
    public static String toHexString(float f) {
        if (Math.abs(f) < Float.MIN_NORMAL
            &&  f != 0.0f ) {// float subnormal
            // Adjust exponent to create subnormal double, then
            // replace subnormal double exponent with subnormal float
            // exponent
            String s = Double.toHexString(Math.scalb((double)f,
                                                     /* -1022+126 */
                                                     Double.MIN_EXPONENT-
                                                     Float.MIN_EXPONENT));
            return s.replaceFirst("p-1022$", "p-126");
        }
        else // double string will be the same as float string
            return Double.toHexString(f);
    }

    /**
     * Returns a {@code Float} object holding the
     * {@code float} value represented by the argument string
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
     * binary value that is then rounded to type {@code float}
     * by the usual round-to-nearest rule of IEEE 754 floating-point
     * arithmetic, which includes preserving the sign of a zero
     * value.
     *
     * Note that the round-to-nearest rule also implies overflow and
     * underflow behaviour; if the exact value of {@code s} is large
     * enough in magnitude (greater than or equal to ({@link
     * #MAX_VALUE} + {@link Math#ulp(float) ulp(MAX_VALUE)}/2),
     * rounding to {@code float} will result in an infinity and if the
     * exact value of {@code s} is small enough in magnitude (less
     * than or equal to {@link #MIN_VALUE}/2), rounding to float will
     * result in a zero.
     *
     * Finally, after rounding a {@code Float} object representing
     * this {@code float} value is returned.
     *
     * <p>Note that trailing format specifiers, specifiers that
     * determine the type of a floating-point literal
     * ({@code 1.0f} is a {@code float} value;
     * {@code 1.0d} is a {@code double} value), do
     * <em>not</em> influence the results of this method.  In other
     * words, the numerical value of the input string is converted
     * directly to the target floating-point type.  In general, the
     * two-step sequence of conversions, string to {@code double}
     * followed by {@code double} to {@code float}, is
     * <em>not</em> equivalent to converting a string directly to
     * {@code float}.  For example, if first converted to an
     * intermediate {@code double} and then to
     * {@code float}, the string<br>
     * {@code "1.00000017881393421514957253748434595763683319091796875001d"}<br>
     * results in the {@code float} value
     * {@code 1.0000002f}; if the string is converted directly to
     * {@code float}, <code>1.000000<b>1</b>f</code> results.
     *
     * <p>To avoid calling this method on an invalid string and having
     * a {@code NumberFormatException} be thrown, the documentation
     * for {@link Double#valueOf Double.valueOf} lists a regular
     * expression which can be used to screen the input.
     *
     * @apiNote To interpret localized string representations of a
     * floating-point value, or string representations that have
     * non-ASCII digits, use {@link java.text.NumberFormat}. For
     * example,
     * {@snippet lang="java" :
     *     NumberFormat.getInstance(l).parse(s).floatValue();
     * }
     * where {@code l} is the desired locale, or
     * {@link java.util.Locale#ROOT} if locale insensitive.
     *
     * @param   s   the string to be parsed.
     * @return  a {@code Float} object holding the value
     *          represented by the {@code String} argument.
     * @throws  NumberFormatException  if the string does not contain a
     *          parsable number.
     * @see Double##decimalToBinaryConversion Decimal &harr; Binary Conversion Issues
     */
    public static Float valueOf(String s) throws NumberFormatException {
        return new Float(parseFloat(s));
    }

    /**
     * Returns a {@code Float} instance representing the specified
     * {@code float} value.
     * If a new {@code Float} instance is not required, this method
     * should generally be used in preference to the constructor
     * {@link #Float(float)}, as this method is likely to yield
     * significantly better space and time performance by caching
     * frequently requested values.
     *
     * @param  f a float value.
     * @return a {@code Float} instance representing {@code f}.
     * @since  1.5
     */
    @IntrinsicCandidate
    public static Float valueOf(float f) {
        return new Float(f);
    }

    /**
     * Returns a new {@code float} initialized to the value
     * represented by the specified {@code String}, as performed
     * by the {@code valueOf} method of class {@code Float}.
     *
     * @param  s the string to be parsed.
     * @return the {@code float} value represented by the string
     *         argument.
     * @throws NullPointerException  if the string is null
     * @throws NumberFormatException if the string does not contain a
     *               parsable {@code float}.
     * @see    java.lang.Float#valueOf(String)
     * @see    Double##decimalToBinaryConversion Decimal &harr; Binary Conversion Issues
     * @since 1.2
     */
    public static float parseFloat(String s) throws NumberFormatException {
        return FloatingDecimal.parseFloat(s);
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
     * @return  {@code true} if the argument is NaN;
     *          {@code false} otherwise.
     */
    public static boolean isNaN(float v) {
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
     * @return  {@code true} if the argument is positive infinity or
     *          negative infinity; {@code false} otherwise.
     */
    @IntrinsicCandidate
    public static boolean isInfinite(float v) {
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
     * @param f the {@code float} value to be tested
     * @return {@code true} if the argument is a finite
     * floating-point value, {@code false} otherwise.
     * @since 1.8
     */
     @IntrinsicCandidate
     public static boolean isFinite(float f) {
        return Math.abs(f) <= Float.MAX_VALUE;
    }

    /**
     * The value of the Float.
     *
     * @serial
     */
    private final float value;

    /**
     * Constructs a newly allocated {@code Float} object that
     * represents the primitive {@code float} argument.
     *
     * @param   value   the value to be represented by the {@code Float}.
     *
     * @deprecated
     * It is rarely appropriate to use this constructor. The static factory
     * {@link #valueOf(float)} is generally a better choice, as it is
     * likely to yield significantly better space and time performance.
     */
    @Deprecated(since="9", forRemoval = true)
    public Float(float value) {
        this.value = value;
    }

    /**
     * Constructs a newly allocated {@code Float} object that
     * represents the argument converted to type {@code float}.
     *
     * @param   value   the value to be represented by the {@code Float}.
     *
     * @deprecated
     * It is rarely appropriate to use this constructor. Instead, use the
     * static factory method {@link #valueOf(float)} method as follows:
     * {@code Float.valueOf((float)value)}.
     */
    @Deprecated(since="9", forRemoval = true)
    public Float(double value) {
        this.value = (float)value;
    }

    /**
     * Constructs a newly allocated {@code Float} object that
     * represents the floating-point value of type {@code float}
     * represented by the string. The string is converted to a
     * {@code float} value as if by the {@code valueOf} method.
     *
     * @param   s   a string to be converted to a {@code Float}.
     * @throws      NumberFormatException if the string does not contain a
     *              parsable number.
     *
     * @deprecated
     * It is rarely appropriate to use this constructor.
     * Use {@link #parseFloat(String)} to convert a string to a
     * {@code float} primitive, or use {@link #valueOf(String)}
     * to convert a string to a {@code Float} object.
     */
    @Deprecated(since="9", forRemoval = true)
    public Float(String s) throws NumberFormatException {
        value = parseFloat(s);
    }

    /**
     * Returns {@code true} if this {@code Float} value is a
     * Not-a-Number (NaN), {@code false} otherwise.
     *
     * @return  {@code true} if the value represented by this object is
     *          NaN; {@code false} otherwise.
     */
    public boolean isNaN() {
        return isNaN(value);
    }

    /**
     * Returns {@code true} if this {@code Float} value is
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
     * Returns a string representation of this {@code Float} object.
     * The primitive {@code float} value represented by this object
     * is converted to a {@code String} exactly as if by the method
     * {@code toString} of one argument.
     *
     * @return  a {@code String} representation of this object.
     * @see java.lang.Float#toString(float)
     */
    public String toString() {
        return Float.toString(value);
    }

    /**
     * Returns the value of this {@code Float} as a {@code byte} after
     * a narrowing primitive conversion.
     *
     * @return  the {@code float} value represented by this object
     *          converted to type {@code byte}
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    public byte byteValue() {
        return (byte)value;
    }

    /**
     * Returns the value of this {@code Float} as a {@code short}
     * after a narrowing primitive conversion.
     *
     * @return  the {@code float} value represented by this object
     *          converted to type {@code short}
     * @jls 5.1.3 Narrowing Primitive Conversion
     * @since 1.1
     */
    public short shortValue() {
        return (short)value;
    }

    /**
     * Returns the value of this {@code Float} as an {@code int} after
     * a narrowing primitive conversion.
     *
     * @return  the {@code float} value represented by this object
     *          converted to type {@code int}
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    public int intValue() {
        return (int)value;
    }

    /**
     * Returns value of this {@code Float} as a {@code long} after a
     * narrowing primitive conversion.
     *
     * @return  the {@code float} value represented by this object
     *          converted to type {@code long}
     * @jls 5.1.3 Narrowing Primitive Conversion
     */
    public long longValue() {
        return (long)value;
    }

    /**
     * Returns the {@code float} value of this {@code Float} object.
     *
     * @return the {@code float} value represented by this object
     */
    @IntrinsicCandidate
    public float floatValue() {
        return value;
    }

    /**
     * Returns the value of this {@code Float} as a {@code double}
     * after a widening primitive conversion.
     *
     * @apiNote
     * This method corresponds to the convertFormat operation defined
     * in IEEE 754.
     *
     * @return the {@code float} value represented by this
     *         object converted to type {@code double}
     * @jls 5.1.2 Widening Primitive Conversion
     */
    public double doubleValue() {
        return (double)value;
    }

    /**
     * Returns a hash code for this {@code Float} object. The
     * result is the integer bit representation, exactly as produced
     * by the method {@link #floatToIntBits(float)}, of the primitive
     * {@code float} value represented by this {@code Float}
     * object.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return Float.hashCode(value);
    }

    /**
     * Returns a hash code for a {@code float} value; compatible with
     * {@code Float.hashCode()}.
     *
     * @param value the value to hash
     * @return a hash code value for a {@code float} value.
     * @since 1.8
     */
    public static int hashCode(float value) {
        return floatToIntBits(value);
    }

    /**
     * Compares this object against the specified object.  The result
     * is {@code true} if and only if the argument is not
     * {@code null} and is a {@code Float} object that
     * represents a {@code float} with the same value as the
     * {@code float} represented by this object. For this
     * purpose, two {@code float} values are considered to be the
     * same if and only if the method {@link #floatToIntBits(float)}
     * returns the identical {@code int} value when applied to
     * each.
     *
     * @apiNote
     * This method is defined in terms of {@link
     * #floatToIntBits(float)} rather than the {@code ==} operator on
     * {@code float} values since the {@code ==} operator does
     * <em>not</em> define an equivalence relation and to satisfy the
     * {@linkplain Object#equals equals contract} an equivalence
     * relation must be implemented; see <a
     * href="Double.html#equivalenceRelation">this discussion</a> for
     * details of floating-point equality and equivalence.
     *
     * @param obj the object to be compared
     * @return  {@code true} if the objects are the same;
     *          {@code false} otherwise.
     * @see java.lang.Float#floatToIntBits(float)
     * @jls 15.21.1 Numerical Equality Operators == and !=
     */
    public boolean equals(Object obj) {
        return (obj instanceof Float)
               && (floatToIntBits(((Float)obj).value) == floatToIntBits(value));
    }

    /**
     * Returns a representation of the specified floating-point value
     * according to the IEEE 754 floating-point "single format" bit
     * layout.
     *
     * <p>Bit 31 (the bit that is selected by the mask
     * {@code 0x80000000}) represents the sign of the floating-point
     * number.
     * Bits 30-23 (the bits that are selected by the mask
     * {@code 0x7f800000}) represent the exponent.
     * Bits 22-0 (the bits that are selected by the mask
     * {@code 0x007fffff}) represent the significand (sometimes called
     * the mantissa) of the floating-point number.
     *
     * <p>If the argument is positive infinity, the result is
     * {@code 0x7f800000}.
     *
     * <p>If the argument is negative infinity, the result is
     * {@code 0xff800000}.
     *
     * <p>If the argument is NaN, the result is {@code 0x7fc00000}.
     *
     * <p>In all cases, the result is an integer that, when given to the
     * {@link #intBitsToFloat(int)} method, will produce a floating-point
     * value the same as the argument to {@code floatToIntBits}
     * (except all NaN values are collapsed to a single
     * "canonical" NaN value).
     *
     * @param   value   a floating-point number.
     * @return the bits that represent the floating-point number.
     */
    @IntrinsicCandidate
    public static int floatToIntBits(float value) {
        if (!isNaN(value)) {
            return floatToRawIntBits(value);
        }
        return 0x7fc00000;
    }

    /**
     * Returns a representation of the specified floating-point value
     * according to the IEEE 754 floating-point "single format" bit
     * layout, preserving Not-a-Number (NaN) values.
     *
     * <p>Bit 31 (the bit that is selected by the mask
     * {@code 0x80000000}) represents the sign of the floating-point
     * number.
     * Bits 30-23 (the bits that are selected by the mask
     * {@code 0x7f800000}) represent the exponent.
     * Bits 22-0 (the bits that are selected by the mask
     * {@code 0x007fffff}) represent the significand (sometimes called
     * the mantissa) of the floating-point number.
     *
     * <p>If the argument is positive infinity, the result is
     * {@code 0x7f800000}.
     *
     * <p>If the argument is negative infinity, the result is
     * {@code 0xff800000}.
     *
     * <p>If the argument is NaN, the result is the integer representing
     * the actual NaN value.  Unlike the {@code floatToIntBits}
     * method, {@code floatToRawIntBits} does not collapse all the
     * bit patterns encoding a NaN to a single "canonical"
     * NaN value.
     *
     * <p>In all cases, the result is an integer that, when given to the
     * {@link #intBitsToFloat(int)} method, will produce a
     * floating-point value the same as the argument to
     * {@code floatToRawIntBits}.
     *
     * @param   value   a floating-point number.
     * @return the bits that represent the floating-point number.
     * @since 1.3
     */
    @IntrinsicCandidate
    public static native int floatToRawIntBits(float value);

    /**
     * Returns the {@code float} value corresponding to a given
     * bit representation.
     * The argument is considered to be a representation of a
     * floating-point value according to the IEEE 754 floating-point
     * "single format" bit layout.
     *
     * <p>If the argument is {@code 0x7f800000}, the result is positive
     * infinity.
     *
     * <p>If the argument is {@code 0xff800000}, the result is negative
     * infinity.
     *
     * <p>If the argument is any value in the range
     * {@code 0x7f800001} through {@code 0x7fffffff} or in
     * the range {@code 0xff800001} through
     * {@code 0xffffffff}, the result is a NaN.  No IEEE 754
     * floating-point operation provided by Java can distinguish
     * between two NaN values of the same type with different bit
     * patterns.  Distinct values of NaN are only distinguishable by
     * use of the {@code Float.floatToRawIntBits} method.
     *
     * <p>In all other cases, let <i>s</i>, <i>e</i>, and <i>m</i> be three
     * values that can be computed from the argument:
     *
     * {@snippet lang="java" :
     * int s = ((bits >> 31) == 0) ? 1 : -1;
     * int e = ((bits >> 23) & 0xff);
     * int m = (e == 0) ?
     *                 (bits & 0x7fffff) << 1 :
     *                 (bits & 0x7fffff) | 0x800000;
     * }
     *
     * Then the floating-point result equals the value of the mathematical
     * expression <i>s</i>&middot;<i>m</i>&middot;2<sup><i>e</i>-150</sup>.
     *
     * <p>Note that this method may not be able to return a
     * {@code float} NaN with exactly same bit pattern as the
     * {@code int} argument.  IEEE 754 distinguishes between two
     * kinds of NaNs, quiet NaNs and <i>signaling NaNs</i>.  The
     * differences between the two kinds of NaN are generally not
     * visible in Java.  Arithmetic operations on signaling NaNs turn
     * them into quiet NaNs with a different, but often similar, bit
     * pattern.  However, on some processors merely copying a
     * signaling NaN also performs that conversion.  In particular,
     * copying a signaling NaN to return it to the calling method may
     * perform this conversion.  So {@code intBitsToFloat} may
     * not be able to return a {@code float} with a signaling NaN
     * bit pattern.  Consequently, for some {@code int} values,
     * {@code floatToRawIntBits(intBitsToFloat(start))} may
     * <i>not</i> equal {@code start}.  Moreover, which
     * particular bit patterns represent signaling NaNs is platform
     * dependent; although all NaN bit patterns, quiet or signaling,
     * must be in the NaN range identified above.
     *
     * @param   bits   an integer.
     * @return  the {@code float} floating-point value with the same bit
     *          pattern.
     */
    @IntrinsicCandidate
    public static native float intBitsToFloat(int bits);

    /**
     * {@return the {@code float} value closest to the numerical value
     * of the argument, a floating-point binary16 value encoded in a
     * {@code short}} The conversion is exact; all binary16 values can
     * be exactly represented in {@code float}.
     *
     * Special cases:
     * <ul>
     * <li> If the argument is zero, the result is a zero with the
     * same sign as the argument.
     * <li> If the argument is infinite, the result is an infinity
     * with the same sign as the argument.
     * <li> If the argument is a NaN, the result is a NaN.
     * </ul>
     *
     * <h4><a id=binary16Format>IEEE 754 binary16 format</a></h4>
     * The IEEE 754 standard defines binary16 as a 16-bit format, along
     * with the 32-bit binary32 format (corresponding to the {@code
     * float} type) and the 64-bit binary64 format (corresponding to
     * the {@code double} type). The binary16 format is similar to the
     * other IEEE 754 formats, except smaller, having all the usual
     * IEEE 754 values such as NaN, signed infinities, signed zeros,
     * and subnormals. The parameters (JLS {@jls 4.2.3}) for the
     * binary16 format are N = 11 precision bits, K = 5 exponent bits,
     * <i>E</i><sub><i>max</i></sub> = 15, and
     * <i>E</i><sub><i>min</i></sub> = -14.
     *
     * @apiNote
     * This method corresponds to the convertFormat operation defined
     * in IEEE 754 from the binary16 format to the binary32 format.
     * The operation of this method is analogous to a primitive
     * widening conversion (JLS {@jls 5.1.2}).
     *
     * @param floatBinary16 the binary16 value to convert to {@code float}
     * @since 20
     */
    @IntrinsicCandidate
    public static float float16ToFloat(short floatBinary16) {
        /*
         * The binary16 format has 1 sign bit, 5 exponent bits, and 10
         * significand bits. The exponent bias is 15.
         */
        int bin16arg = (int)floatBinary16;
        int bin16SignBit     = 0x8000 & bin16arg;
        int bin16ExpBits     = 0x7c00 & bin16arg;
        int bin16SignifBits  = 0x03FF & bin16arg;

        // Shift left difference in the number of significand bits in
        // the float and binary16 formats
        final int SIGNIF_SHIFT = (FloatConsts.SIGNIFICAND_WIDTH - 11);

        float sign = (bin16SignBit != 0) ? -1.0f : 1.0f;

        // Extract binary16 exponent, remove its bias, add in the bias
        // of a float exponent and shift to correct bit location
        // (significand width includes the implicit bit so shift one
        // less).
        int bin16Exp = (bin16ExpBits >> 10) - 15;
        if (bin16Exp == -15) {
            // For subnormal binary16 values and 0, the numerical
            // value is 2^24 * the significand as an integer (no
            // implicit bit).
            return sign * (0x1p-24f * bin16SignifBits);
        } else if (bin16Exp == 16) {
            return (bin16SignifBits == 0) ?
                sign * Float.POSITIVE_INFINITY :
                Float.intBitsToFloat((bin16SignBit << 16) |
                                     0x7f80_0000 |
                                     // Preserve NaN signif bits
                                     ( bin16SignifBits << SIGNIF_SHIFT ));
        }

        assert -15 < bin16Exp  && bin16Exp < 16;

        int floatExpBits = (bin16Exp + FloatConsts.EXP_BIAS)
            << (FloatConsts.SIGNIFICAND_WIDTH - 1);

        // Compute and combine result sign, exponent, and significand bits.
        return Float.intBitsToFloat((bin16SignBit << 16) |
                                    floatExpBits |
                                    (bin16SignifBits << SIGNIF_SHIFT));
    }

    /**
     * {@return the floating-point binary16 value, encoded in a {@code
     * short}, closest in value to the argument}
     * The conversion is computed under the {@linkplain
     * java.math.RoundingMode#HALF_EVEN round to nearest even rounding
     * mode}.
     *
     * Special cases:
     * <ul>
     * <li> If the argument is zero, the result is a zero with the
     * same sign as the argument.
     * <li> If the argument is infinite, the result is an infinity
     * with the same sign as the argument.
     * <li> If the argument is a NaN, the result is a NaN.
     * </ul>
     *
     * The <a href="#binary16Format">binary16 format</a> is discussed in
     * more detail in the {@link #float16ToFloat} method.
     *
     * @apiNote
     * This method corresponds to the convertFormat operation defined
     * in IEEE 754 from the binary32 format to the binary16 format.
     * The operation of this method is analogous to a primitive
     * narrowing conversion (JLS {@jls 5.1.3}).
     *
     * @param f the {@code float} value to convert to binary16
     * @since 20
     */
    @IntrinsicCandidate
    public static short floatToFloat16(float f) {
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
        if (abs_f >= (0x1.ffcp15f + 0x0.002p15f) ) {
            return (short)(sign_bit | 0x7c00); // Positive or negative infinity
        }

        // Smallest magnitude nonzero representable binary16 value
        // is equal to 0x1.0p-24; half-way and smaller rounds to zero.
        if (abs_f <= 0x1.0p-24f * 0.5f) { // Covers float zeros and subnormals.
            return sign_bit; // Positive or negative zero
        }

        // Dealing with finite values in exponent range of binary16
        // (when rounding is done, could still round up)
        int exp = Math.getExponent(f);
        assert -25 <= exp && exp <= 15;

        // For binary16 subnormals, beside forcing exp to -15, retain
        // the difference expdelta = E_min - exp.  This is the excess
        // shift value, in addition to 13, to be used in the
        // computations below.  Further the (hidden) msb with value 1
        // in f must be involved as well.
        int expdelta = 0;
        int msb = 0x0000_0000;
        if (exp < -14) {
            expdelta = -14 - exp;
            exp = -15;
            msb = 0x0080_0000;
        }
        int f_signif_bits = doppel & 0x007f_ffff | msb;

        // Significand bits as if using rounding to zero (truncation).
        short signif_bits = (short)(f_signif_bits >> (13 + expdelta));

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

        int lsb    = f_signif_bits & (1 << 13 + expdelta);
        int round  = f_signif_bits & (1 << 12 + expdelta);
        int sticky = f_signif_bits & ((1 << 12 + expdelta) - 1);

        if (round != 0 && ((lsb | sticky) != 0 )) {
            signif_bits++;
        }

        // No bits set in significand beyond the *first* exponent bit,
        // not just the significand; quantity is added to the exponent
        // to implement a carry out from rounding the significand.
        assert (0xf800 & signif_bits) == 0x0;

        return (short)(sign_bit | ( ((exp + 15) << 10) + signif_bits ) );
    }

    /**
     * Compares two {@code Float} objects numerically.
     *
     * This method imposes a total order on {@code Float} objects
     * with two differences compared to the incomplete order defined by
     * the Java language numerical comparison operators ({@code <, <=,
     * ==, >=, >}) on {@code float} values.
     *
     * <ul><li> A NaN is <em>unordered</em> with respect to other
     *          values and unequal to itself under the comparison
     *          operators.  This method chooses to define {@code
     *          Float.NaN} to be equal to itself and greater than all
     *          other {@code double} values (including {@code
     *          Float.POSITIVE_INFINITY}).
     *
     *      <li> Positive zero and negative zero compare equal
     *      numerically, but are distinct and distinguishable values.
     *      This method chooses to define positive zero ({@code +0.0f}),
     *      to be greater than negative zero ({@code -0.0f}).
     * </ul>
     *
     * This ensures that the <i>natural ordering</i> of {@code Float}
     * objects imposed by this method is <i>consistent with
     * equals</i>; see <a href="Double.html#equivalenceRelation">this
     * discussion</a> for details of floating-point comparison and
     * ordering.
     *
     *
     * @param   anotherFloat   the {@code Float} to be compared.
     * @return  the value {@code 0} if {@code anotherFloat} is
     *          numerically equal to this {@code Float}; a value
     *          less than {@code 0} if this {@code Float}
     *          is numerically less than {@code anotherFloat};
     *          and a value greater than {@code 0} if this
     *          {@code Float} is numerically greater than
     *          {@code anotherFloat}.
     *
     * @jls 15.20.1 Numerical Comparison Operators {@code <}, {@code <=}, {@code >}, and {@code >=}
     * @since   1.2
     */
    public int compareTo(Float anotherFloat) {
        return Float.compare(value, anotherFloat.value);
    }

    /**
     * Compares the two specified {@code float} values. The sign
     * of the integer value returned is the same as that of the
     * integer that would be returned by the call:
     * <pre>
     *    Float.valueOf(f1).compareTo(Float.valueOf(f2))
     * </pre>
     *
     * @param   f1        the first {@code float} to compare.
     * @param   f2        the second {@code float} to compare.
     * @return  the value {@code 0} if {@code f1} is
     *          numerically equal to {@code f2}; a value less than
     *          {@code 0} if {@code f1} is numerically less than
     *          {@code f2}; and a value greater than {@code 0}
     *          if {@code f1} is numerically greater than
     *          {@code f2}.
     * @since 1.4
     */
    public static int compare(float f1, float f2) {
        if (f1 < f2)
            return -1;           // Neither val is NaN, thisVal is smaller
        if (f1 > f2)
            return 1;            // Neither val is NaN, thisVal is larger

        // Cannot use floatToRawIntBits because of possibility of NaNs.
        int thisBits    = Float.floatToIntBits(f1);
        int anotherBits = Float.floatToIntBits(f2);

        return (thisBits == anotherBits ?  0 : // Values are equal
                (thisBits < anotherBits ? -1 : // (-0.0, 0.0) or (!NaN, NaN)
                 1));                          // (0.0, -0.0) or (NaN, !NaN)
    }

    /**
     * Adds two {@code float} values together as per the + operator.
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
    public static float sum(float a, float b) {
        return a + b;
    }

    /**
     * Returns the greater of two {@code float} values
     * as if by calling {@link Math#max(float, float) Math.max}.
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
    public static float max(float a, float b) {
        return Math.max(a, b);
    }

    /**
     * Returns the smaller of two {@code float} values
     * as if by calling {@link Math#min(float, float) Math.min}.
     *
     * @apiNote
     * This method corresponds to the minimum operation defined in
     * IEEE 754.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the smaller of {@code a} and {@code b}
     * @see java.util.function.BinaryOperator
     * @since 1.8
     */
    public static float min(float a, float b) {
        return Math.min(a, b);
    }

    /**
     * Returns an {@link Optional} containing the nominal descriptor for this
     * instance, which is the instance itself.
     *
     * @return an {@link Optional} describing the {@linkplain Float} instance
     * @since 12
     */
    @Override
    public Optional<Float> describeConstable() {
        return Optional.of(this);
    }

    /**
     * Resolves this instance as a {@link ConstantDesc}, the result of which is
     * the instance itself.
     *
     * @param lookup ignored
     * @return the {@linkplain Float} instance
     * @since 12
     */
    @Override
    public Float resolveConstantDesc(MethodHandles.Lookup lookup) {
        return this;
    }

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = -2671257302660747028L;
}
