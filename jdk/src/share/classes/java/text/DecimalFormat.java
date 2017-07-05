/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

/*
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 *   The original version of this source code and documentation is copyrighted
 * and owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These
 * materials are provided under terms of a License Agreement between Taligent
 * and Sun. This technology is protected by multiple US and International
 * patents. This notice and attribution to Taligent may not be removed.
 *   Taligent is a registered trademark of Taligent, Inc.
 *
 */

package java.text;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.spi.NumberFormatProvider;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.ResourceBundleBasedAdapter;

/**
 * <code>DecimalFormat</code> is a concrete subclass of
 * <code>NumberFormat</code> that formats decimal numbers. It has a variety of
 * features designed to make it possible to parse and format numbers in any
 * locale, including support for Western, Arabic, and Indic digits.  It also
 * supports different kinds of numbers, including integers (123), fixed-point
 * numbers (123.4), scientific notation (1.23E4), percentages (12%), and
 * currency amounts ($123).  All of these can be localized.
 *
 * <p>To obtain a <code>NumberFormat</code> for a specific locale, including the
 * default locale, call one of <code>NumberFormat</code>'s factory methods, such
 * as <code>getInstance()</code>.  In general, do not call the
 * <code>DecimalFormat</code> constructors directly, since the
 * <code>NumberFormat</code> factory methods may return subclasses other than
 * <code>DecimalFormat</code>. If you need to customize the format object, do
 * something like this:
 *
 * <blockquote><pre>
 * NumberFormat f = NumberFormat.getInstance(loc);
 * if (f instanceof DecimalFormat) {
 *     ((DecimalFormat) f).setDecimalSeparatorAlwaysShown(true);
 * }
 * </pre></blockquote>
 *
 * <p>A <code>DecimalFormat</code> comprises a <em>pattern</em> and a set of
 * <em>symbols</em>.  The pattern may be set directly using
 * <code>applyPattern()</code>, or indirectly using the API methods.  The
 * symbols are stored in a <code>DecimalFormatSymbols</code> object.  When using
 * the <code>NumberFormat</code> factory methods, the pattern and symbols are
 * read from localized <code>ResourceBundle</code>s.
 *
 * <h3>Patterns</h3>
 *
 * <code>DecimalFormat</code> patterns have the following syntax:
 * <blockquote><pre>
 * <i>Pattern:</i>
 *         <i>PositivePattern</i>
 *         <i>PositivePattern</i> ; <i>NegativePattern</i>
 * <i>PositivePattern:</i>
 *         <i>Prefix<sub>opt</sub></i> <i>Number</i> <i>Suffix<sub>opt</sub></i>
 * <i>NegativePattern:</i>
 *         <i>Prefix<sub>opt</sub></i> <i>Number</i> <i>Suffix<sub>opt</sub></i>
 * <i>Prefix:</i>
 *         any Unicode characters except &#92;uFFFE, &#92;uFFFF, and special characters
 * <i>Suffix:</i>
 *         any Unicode characters except &#92;uFFFE, &#92;uFFFF, and special characters
 * <i>Number:</i>
 *         <i>Integer</i> <i>Exponent<sub>opt</sub></i>
 *         <i>Integer</i> . <i>Fraction</i> <i>Exponent<sub>opt</sub></i>
 * <i>Integer:</i>
 *         <i>MinimumInteger</i>
 *         #
 *         # <i>Integer</i>
 *         # , <i>Integer</i>
 * <i>MinimumInteger:</i>
 *         0
 *         0 <i>MinimumInteger</i>
 *         0 , <i>MinimumInteger</i>
 * <i>Fraction:</i>
 *         <i>MinimumFraction<sub>opt</sub></i> <i>OptionalFraction<sub>opt</sub></i>
 * <i>MinimumFraction:</i>
 *         0 <i>MinimumFraction<sub>opt</sub></i>
 * <i>OptionalFraction:</i>
 *         # <i>OptionalFraction<sub>opt</sub></i>
 * <i>Exponent:</i>
 *         E <i>MinimumExponent</i>
 * <i>MinimumExponent:</i>
 *         0 <i>MinimumExponent<sub>opt</sub></i>
 * </pre></blockquote>
 *
 * <p>A <code>DecimalFormat</code> pattern contains a positive and negative
 * subpattern, for example, <code>"#,##0.00;(#,##0.00)"</code>.  Each
 * subpattern has a prefix, numeric part, and suffix. The negative subpattern
 * is optional; if absent, then the positive subpattern prefixed with the
 * localized minus sign (<code>'-'</code> in most locales) is used as the
 * negative subpattern. That is, <code>"0.00"</code> alone is equivalent to
 * <code>"0.00;-0.00"</code>.  If there is an explicit negative subpattern, it
 * serves only to specify the negative prefix and suffix; the number of digits,
 * minimal digits, and other characteristics are all the same as the positive
 * pattern. That means that <code>"#,##0.0#;(#)"</code> produces precisely
 * the same behavior as <code>"#,##0.0#;(#,##0.0#)"</code>.
 *
 * <p>The prefixes, suffixes, and various symbols used for infinity, digits,
 * thousands separators, decimal separators, etc. may be set to arbitrary
 * values, and they will appear properly during formatting.  However, care must
 * be taken that the symbols and strings do not conflict, or parsing will be
 * unreliable.  For example, either the positive and negative prefixes or the
 * suffixes must be distinct for <code>DecimalFormat.parse()</code> to be able
 * to distinguish positive from negative values.  (If they are identical, then
 * <code>DecimalFormat</code> will behave as if no negative subpattern was
 * specified.)  Another example is that the decimal separator and thousands
 * separator should be distinct characters, or parsing will be impossible.
 *
 * <p>The grouping separator is commonly used for thousands, but in some
 * countries it separates ten-thousands. The grouping size is a constant number
 * of digits between the grouping characters, such as 3 for 100,000,000 or 4 for
 * 1,0000,0000.  If you supply a pattern with multiple grouping characters, the
 * interval between the last one and the end of the integer is the one that is
 * used. So <code>"#,##,###,####"</code> == <code>"######,####"</code> ==
 * <code>"##,####,####"</code>.
 *
 * <h4>Special Pattern Characters</h4>
 *
 * <p>Many characters in a pattern are taken literally; they are matched during
 * parsing and output unchanged during formatting.  Special characters, on the
 * other hand, stand for other characters, strings, or classes of characters.
 * They must be quoted, unless noted otherwise, if they are to appear in the
 * prefix or suffix as literals.
 *
 * <p>The characters listed here are used in non-localized patterns.  Localized
 * patterns use the corresponding characters taken from this formatter's
 * <code>DecimalFormatSymbols</code> object instead, and these characters lose
 * their special status.  Two exceptions are the currency sign and quote, which
 * are not localized.
 *
 * <blockquote>
 * <table border=0 cellspacing=3 cellpadding=0 summary="Chart showing symbol,
 *  location, localized, and meaning.">
 *     <tr style="background-color: rgb(204, 204, 255);">
 *          <th align=left>Symbol
 *          <th align=left>Location
 *          <th align=left>Localized?
 *          <th align=left>Meaning
 *     <tr valign=top>
 *          <td><code>0</code>
 *          <td>Number
 *          <td>Yes
 *          <td>Digit
 *     <tr style="vertical-align: top; background-color: rgb(238, 238, 255);">
 *          <td><code>#</code>
 *          <td>Number
 *          <td>Yes
 *          <td>Digit, zero shows as absent
 *     <tr valign=top>
 *          <td><code>.</code>
 *          <td>Number
 *          <td>Yes
 *          <td>Decimal separator or monetary decimal separator
 *     <tr style="vertical-align: top; background-color: rgb(238, 238, 255);">
 *          <td><code>-</code>
 *          <td>Number
 *          <td>Yes
 *          <td>Minus sign
 *     <tr valign=top>
 *          <td><code>,</code>
 *          <td>Number
 *          <td>Yes
 *          <td>Grouping separator
 *     <tr style="vertical-align: top; background-color: rgb(238, 238, 255);">
 *          <td><code>E</code>
 *          <td>Number
 *          <td>Yes
 *          <td>Separates mantissa and exponent in scientific notation.
 *              <em>Need not be quoted in prefix or suffix.</em>
 *     <tr valign=top>
 *          <td><code>;</code>
 *          <td>Subpattern boundary
 *          <td>Yes
 *          <td>Separates positive and negative subpatterns
 *     <tr style="vertical-align: top; background-color: rgb(238, 238, 255);">
 *          <td><code>%</code>
 *          <td>Prefix or suffix
 *          <td>Yes
 *          <td>Multiply by 100 and show as percentage
 *     <tr valign=top>
 *          <td><code>&#92;u2030</code>
 *          <td>Prefix or suffix
 *          <td>Yes
 *          <td>Multiply by 1000 and show as per mille value
 *     <tr style="vertical-align: top; background-color: rgb(238, 238, 255);">
 *          <td><code>&#164;</code> (<code>&#92;u00A4</code>)
 *          <td>Prefix or suffix
 *          <td>No
 *          <td>Currency sign, replaced by currency symbol.  If
 *              doubled, replaced by international currency symbol.
 *              If present in a pattern, the monetary decimal separator
 *              is used instead of the decimal separator.
 *     <tr valign=top>
 *          <td><code>'</code>
 *          <td>Prefix or suffix
 *          <td>No
 *          <td>Used to quote special characters in a prefix or suffix,
 *              for example, <code>"'#'#"</code> formats 123 to
 *              <code>"#123"</code>.  To create a single quote
 *              itself, use two in a row: <code>"# o''clock"</code>.
 * </table>
 * </blockquote>
 *
 * <h4>Scientific Notation</h4>
 *
 * <p>Numbers in scientific notation are expressed as the product of a mantissa
 * and a power of ten, for example, 1234 can be expressed as 1.234 x 10^3.  The
 * mantissa is often in the range 1.0 &le; x {@literal <} 10.0, but it need not
 * be.
 * <code>DecimalFormat</code> can be instructed to format and parse scientific
 * notation <em>only via a pattern</em>; there is currently no factory method
 * that creates a scientific notation format.  In a pattern, the exponent
 * character immediately followed by one or more digit characters indicates
 * scientific notation.  Example: <code>"0.###E0"</code> formats the number
 * 1234 as <code>"1.234E3"</code>.
 *
 * <ul>
 * <li>The number of digit characters after the exponent character gives the
 * minimum exponent digit count.  There is no maximum.  Negative exponents are
 * formatted using the localized minus sign, <em>not</em> the prefix and suffix
 * from the pattern.  This allows patterns such as <code>"0.###E0 m/s"</code>.
 *
 * <li>The minimum and maximum number of integer digits are interpreted
 * together:
 *
 * <ul>
 * <li>If the maximum number of integer digits is greater than their minimum number
 * and greater than 1, it forces the exponent to be a multiple of the maximum
 * number of integer digits, and the minimum number of integer digits to be
 * interpreted as 1.  The most common use of this is to generate
 * <em>engineering notation</em>, in which the exponent is a multiple of three,
 * e.g., <code>"##0.#####E0"</code>. Using this pattern, the number 12345
 * formats to <code>"12.345E3"</code>, and 123456 formats to
 * <code>"123.456E3"</code>.
 *
 * <li>Otherwise, the minimum number of integer digits is achieved by adjusting the
 * exponent.  Example: 0.00123 formatted with <code>"00.###E0"</code> yields
 * <code>"12.3E-4"</code>.
 * </ul>
 *
 * <li>The number of significant digits in the mantissa is the sum of the
 * <em>minimum integer</em> and <em>maximum fraction</em> digits, and is
 * unaffected by the maximum integer digits.  For example, 12345 formatted with
 * <code>"##0.##E0"</code> is <code>"12.3E3"</code>. To show all digits, set
 * the significant digits count to zero.  The number of significant digits
 * does not affect parsing.
 *
 * <li>Exponential patterns may not contain grouping separators.
 * </ul>
 *
 * <h4>Rounding</h4>
 *
 * <code>DecimalFormat</code> provides rounding modes defined in
 * {@link java.math.RoundingMode} for formatting.  By default, it uses
 * {@link java.math.RoundingMode#HALF_EVEN RoundingMode.HALF_EVEN}.
 *
 * <h4>Digits</h4>
 *
 * For formatting, <code>DecimalFormat</code> uses the ten consecutive
 * characters starting with the localized zero digit defined in the
 * <code>DecimalFormatSymbols</code> object as digits. For parsing, these
 * digits as well as all Unicode decimal digits, as defined by
 * {@link Character#digit Character.digit}, are recognized.
 *
 * <h4>Special Values</h4>
 *
 * <p><code>NaN</code> is formatted as a string, which typically has a single character
 * <code>&#92;uFFFD</code>.  This string is determined by the
 * <code>DecimalFormatSymbols</code> object.  This is the only value for which
 * the prefixes and suffixes are not used.
 *
 * <p>Infinity is formatted as a string, which typically has a single character
 * <code>&#92;u221E</code>, with the positive or negative prefixes and suffixes
 * applied.  The infinity string is determined by the
 * <code>DecimalFormatSymbols</code> object.
 *
 * <p>Negative zero (<code>"-0"</code>) parses to
 * <ul>
 * <li><code>BigDecimal(0)</code> if <code>isParseBigDecimal()</code> is
 * true,
 * <li><code>Long(0)</code> if <code>isParseBigDecimal()</code> is false
 *     and <code>isParseIntegerOnly()</code> is true,
 * <li><code>Double(-0.0)</code> if both <code>isParseBigDecimal()</code>
 * and <code>isParseIntegerOnly()</code> are false.
 * </ul>
 *
 * <h4><a name="synchronization">Synchronization</a></h4>
 *
 * <p>
 * Decimal formats are generally not synchronized.
 * It is recommended to create separate format instances for each thread.
 * If multiple threads access a format concurrently, it must be synchronized
 * externally.
 *
 * <h4>Example</h4>
 *
 * <blockquote><pre>{@code
 * <strong>// Print out a number using the localized number, integer, currency,
 * // and percent format for each locale</strong>
 * Locale[] locales = NumberFormat.getAvailableLocales();
 * double myNumber = -1234.56;
 * NumberFormat form;
 * for (int j = 0; j < 4; ++j) {
 *     System.out.println("FORMAT");
 *     for (int i = 0; i < locales.length; ++i) {
 *         if (locales[i].getCountry().length() == 0) {
 *            continue; // Skip language-only locales
 *         }
 *         System.out.print(locales[i].getDisplayName());
 *         switch (j) {
 *         case 0:
 *             form = NumberFormat.getInstance(locales[i]); break;
 *         case 1:
 *             form = NumberFormat.getIntegerInstance(locales[i]); break;
 *         case 2:
 *             form = NumberFormat.getCurrencyInstance(locales[i]); break;
 *         default:
 *             form = NumberFormat.getPercentInstance(locales[i]); break;
 *         }
 *         if (form instanceof DecimalFormat) {
 *             System.out.print(": " + ((DecimalFormat) form).toPattern());
 *         }
 *         System.out.print(" -> " + form.format(myNumber));
 *         try {
 *             System.out.println(" -> " + form.parse(form.format(myNumber)));
 *         } catch (ParseException e) {}
 *     }
 * }
 * }</pre></blockquote>
 *
 * @see          <a href="http://docs.oracle.com/javase/tutorial/i18n/format/decimalFormat.html">Java Tutorial</a>
 * @see          NumberFormat
 * @see          DecimalFormatSymbols
 * @see          ParsePosition
 * @author       Mark Davis
 * @author       Alan Liu
 */
public class DecimalFormat extends NumberFormat {

    /**
     * Creates a DecimalFormat using the default pattern and symbols
     * for the default {@link java.util.Locale.Category#FORMAT FORMAT} locale.
     * This is a convenient way to obtain a
     * DecimalFormat when internationalization is not the main concern.
     * <p>
     * To obtain standard formats for a given locale, use the factory methods
     * on NumberFormat such as getNumberInstance. These factories will
     * return the most appropriate sub-class of NumberFormat for a given
     * locale.
     *
     * @see java.text.NumberFormat#getInstance
     * @see java.text.NumberFormat#getNumberInstance
     * @see java.text.NumberFormat#getCurrencyInstance
     * @see java.text.NumberFormat#getPercentInstance
     */
    public DecimalFormat() {
        // Get the pattern for the default locale.
        Locale def = Locale.getDefault(Locale.Category.FORMAT);
        LocaleProviderAdapter adapter = LocaleProviderAdapter.getAdapter(NumberFormatProvider.class, def);
        if (!(adapter instanceof ResourceBundleBasedAdapter)) {
            adapter = LocaleProviderAdapter.getResourceBundleBased();
        }
        String[] all = adapter.getLocaleResources(def).getNumberPatterns();

        // Always applyPattern after the symbols are set
        this.symbols = DecimalFormatSymbols.getInstance(def);
        applyPattern(all[0], false);
    }


    /**
     * Creates a DecimalFormat using the given pattern and the symbols
     * for the default {@link java.util.Locale.Category#FORMAT FORMAT} locale.
     * This is a convenient way to obtain a
     * DecimalFormat when internationalization is not the main concern.
     * <p>
     * To obtain standard formats for a given locale, use the factory methods
     * on NumberFormat such as getNumberInstance. These factories will
     * return the most appropriate sub-class of NumberFormat for a given
     * locale.
     *
     * @param pattern a non-localized pattern string.
     * @exception NullPointerException if <code>pattern</code> is null
     * @exception IllegalArgumentException if the given pattern is invalid.
     * @see java.text.NumberFormat#getInstance
     * @see java.text.NumberFormat#getNumberInstance
     * @see java.text.NumberFormat#getCurrencyInstance
     * @see java.text.NumberFormat#getPercentInstance
     */
    public DecimalFormat(String pattern) {
        // Always applyPattern after the symbols are set
        this.symbols = DecimalFormatSymbols.getInstance(Locale.getDefault(Locale.Category.FORMAT));
        applyPattern(pattern, false);
    }


    /**
     * Creates a DecimalFormat using the given pattern and symbols.
     * Use this constructor when you need to completely customize the
     * behavior of the format.
     * <p>
     * To obtain standard formats for a given
     * locale, use the factory methods on NumberFormat such as
     * getInstance or getCurrencyInstance. If you need only minor adjustments
     * to a standard format, you can modify the format returned by
     * a NumberFormat factory method.
     *
     * @param pattern a non-localized pattern string
     * @param symbols the set of symbols to be used
     * @exception NullPointerException if any of the given arguments is null
     * @exception IllegalArgumentException if the given pattern is invalid
     * @see java.text.NumberFormat#getInstance
     * @see java.text.NumberFormat#getNumberInstance
     * @see java.text.NumberFormat#getCurrencyInstance
     * @see java.text.NumberFormat#getPercentInstance
     * @see java.text.DecimalFormatSymbols
     */
    public DecimalFormat (String pattern, DecimalFormatSymbols symbols) {
        // Always applyPattern after the symbols are set
        this.symbols = (DecimalFormatSymbols)symbols.clone();
        applyPattern(pattern, false);
    }


    // Overrides
    /**
     * Formats a number and appends the resulting text to the given string
     * buffer.
     * The number can be of any subclass of {@link java.lang.Number}.
     * <p>
     * This implementation uses the maximum precision permitted.
     * @param number     the number to format
     * @param toAppendTo the <code>StringBuffer</code> to which the formatted
     *                   text is to be appended
     * @param pos        On input: an alignment field, if desired.
     *                   On output: the offsets of the alignment field.
     * @return           the value passed in as <code>toAppendTo</code>
     * @exception        IllegalArgumentException if <code>number</code> is
     *                   null or not an instance of <code>Number</code>.
     * @exception        NullPointerException if <code>toAppendTo</code> or
     *                   <code>pos</code> is null
     * @exception        ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see              java.text.FieldPosition
     */
    @Override
    public final StringBuffer format(Object number,
                                     StringBuffer toAppendTo,
                                     FieldPosition pos) {
        if (number instanceof Long || number instanceof Integer ||
                   number instanceof Short || number instanceof Byte ||
                   number instanceof AtomicInteger ||
                   number instanceof AtomicLong ||
                   (number instanceof BigInteger &&
                    ((BigInteger)number).bitLength () < 64)) {
            return format(((Number)number).longValue(), toAppendTo, pos);
        } else if (number instanceof BigDecimal) {
            return format((BigDecimal)number, toAppendTo, pos);
        } else if (number instanceof BigInteger) {
            return format((BigInteger)number, toAppendTo, pos);
        } else if (number instanceof Number) {
            return format(((Number)number).doubleValue(), toAppendTo, pos);
        } else {
            throw new IllegalArgumentException("Cannot format given Object as a Number");
        }
    }

    /**
     * Formats a double to produce a string.
     * @param number    The double to format
     * @param result    where the text is to be appended
     * @param fieldPosition    On input: an alignment field, if desired.
     * On output: the offsets of the alignment field.
     * @exception ArithmeticException if rounding is needed with rounding
     *            mode being set to RoundingMode.UNNECESSARY
     * @return The formatted number string
     * @see java.text.FieldPosition
     */
    @Override
    public StringBuffer format(double number, StringBuffer result,
                               FieldPosition fieldPosition) {
        // If fieldPosition is a DontCareFieldPosition instance we can
        // try to go to fast-path code.
        boolean tryFastPath = false;
        if (fieldPosition == DontCareFieldPosition.INSTANCE)
            tryFastPath = true;
        else {
            fieldPosition.setBeginIndex(0);
            fieldPosition.setEndIndex(0);
        }

        if (tryFastPath) {
            String tempResult = fastFormat(number);
            if (tempResult != null) {
                result.append(tempResult);
                return result;
            }
        }

        // if fast-path could not work, we fallback to standard code.
        return format(number, result, fieldPosition.getFieldDelegate());
    }

    /**
     * Formats a double to produce a string.
     * @param number    The double to format
     * @param result    where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @exception       ArithmeticException if rounding is needed with rounding
     *                  mode being set to RoundingMode.UNNECESSARY
     * @return The formatted number string
     */
    private StringBuffer format(double number, StringBuffer result,
                                FieldDelegate delegate) {
        if (Double.isNaN(number) ||
           (Double.isInfinite(number) && multiplier == 0)) {
            int iFieldStart = result.length();
            result.append(symbols.getNaN());
            delegate.formatted(INTEGER_FIELD, Field.INTEGER, Field.INTEGER,
                               iFieldStart, result.length(), result);
            return result;
        }

        /* Detecting whether a double is negative is easy with the exception of
         * the value -0.0.  This is a double which has a zero mantissa (and
         * exponent), but a negative sign bit.  It is semantically distinct from
         * a zero with a positive sign bit, and this distinction is important
         * to certain kinds of computations.  However, it's a little tricky to
         * detect, since (-0.0 == 0.0) and !(-0.0 < 0.0).  How then, you may
         * ask, does it behave distinctly from +0.0?  Well, 1/(-0.0) ==
         * -Infinity.  Proper detection of -0.0 is needed to deal with the
         * issues raised by bugs 4106658, 4106667, and 4147706.  Liu 7/6/98.
         */
        boolean isNegative = ((number < 0.0) || (number == 0.0 && 1/number < 0.0)) ^ (multiplier < 0);

        if (multiplier != 1) {
            number *= multiplier;
        }

        if (Double.isInfinite(number)) {
            if (isNegative) {
                append(result, negativePrefix, delegate,
                       getNegativePrefixFieldPositions(), Field.SIGN);
            } else {
                append(result, positivePrefix, delegate,
                       getPositivePrefixFieldPositions(), Field.SIGN);
            }

            int iFieldStart = result.length();
            result.append(symbols.getInfinity());
            delegate.formatted(INTEGER_FIELD, Field.INTEGER, Field.INTEGER,
                               iFieldStart, result.length(), result);

            if (isNegative) {
                append(result, negativeSuffix, delegate,
                       getNegativeSuffixFieldPositions(), Field.SIGN);
            } else {
                append(result, positiveSuffix, delegate,
                       getPositiveSuffixFieldPositions(), Field.SIGN);
            }

            return result;
        }

        if (isNegative) {
            number = -number;
        }

        // at this point we are guaranteed a nonnegative finite number.
        assert(number >= 0 && !Double.isInfinite(number));

        synchronized(digitList) {
            int maxIntDigits = super.getMaximumIntegerDigits();
            int minIntDigits = super.getMinimumIntegerDigits();
            int maxFraDigits = super.getMaximumFractionDigits();
            int minFraDigits = super.getMinimumFractionDigits();

            digitList.set(isNegative, number, useExponentialNotation ?
                          maxIntDigits + maxFraDigits : maxFraDigits,
                          !useExponentialNotation);
            return subformat(result, delegate, isNegative, false,
                       maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);
        }
    }

    /**
     * Format a long to produce a string.
     * @param number    The long to format
     * @param result    where the text is to be appended
     * @param fieldPosition    On input: an alignment field, if desired.
     * On output: the offsets of the alignment field.
     * @exception       ArithmeticException if rounding is needed with rounding
     *                  mode being set to RoundingMode.UNNECESSARY
     * @return The formatted number string
     * @see java.text.FieldPosition
     */
    @Override
    public StringBuffer format(long number, StringBuffer result,
                               FieldPosition fieldPosition) {
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);

        return format(number, result, fieldPosition.getFieldDelegate());
    }

    /**
     * Format a long to produce a string.
     * @param number    The long to format
     * @param result    where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @return The formatted number string
     * @exception        ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.FieldPosition
     */
    private StringBuffer format(long number, StringBuffer result,
                               FieldDelegate delegate) {
        boolean isNegative = (number < 0);
        if (isNegative) {
            number = -number;
        }

        // In general, long values always represent real finite numbers, so
        // we don't have to check for +/- Infinity or NaN.  However, there
        // is one case we have to be careful of:  The multiplier can push
        // a number near MIN_VALUE or MAX_VALUE outside the legal range.  We
        // check for this before multiplying, and if it happens we use
        // BigInteger instead.
        boolean useBigInteger = false;
        if (number < 0) { // This can only happen if number == Long.MIN_VALUE.
            if (multiplier != 0) {
                useBigInteger = true;
            }
        } else if (multiplier != 1 && multiplier != 0) {
            long cutoff = Long.MAX_VALUE / multiplier;
            if (cutoff < 0) {
                cutoff = -cutoff;
            }
            useBigInteger = (number > cutoff);
        }

        if (useBigInteger) {
            if (isNegative) {
                number = -number;
            }
            BigInteger bigIntegerValue = BigInteger.valueOf(number);
            return format(bigIntegerValue, result, delegate, true);
        }

        number *= multiplier;
        if (number == 0) {
            isNegative = false;
        } else {
            if (multiplier < 0) {
                number = -number;
                isNegative = !isNegative;
            }
        }

        synchronized(digitList) {
            int maxIntDigits = super.getMaximumIntegerDigits();
            int minIntDigits = super.getMinimumIntegerDigits();
            int maxFraDigits = super.getMaximumFractionDigits();
            int minFraDigits = super.getMinimumFractionDigits();

            digitList.set(isNegative, number,
                     useExponentialNotation ? maxIntDigits + maxFraDigits : 0);

            return subformat(result, delegate, isNegative, true,
                       maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);
        }
    }

    /**
     * Formats a BigDecimal to produce a string.
     * @param number    The BigDecimal to format
     * @param result    where the text is to be appended
     * @param fieldPosition    On input: an alignment field, if desired.
     * On output: the offsets of the alignment field.
     * @return The formatted number string
     * @exception        ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.FieldPosition
     */
    private StringBuffer format(BigDecimal number, StringBuffer result,
                                FieldPosition fieldPosition) {
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);
        return format(number, result, fieldPosition.getFieldDelegate());
    }

    /**
     * Formats a BigDecimal to produce a string.
     * @param number    The BigDecimal to format
     * @param result    where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @exception        ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @return The formatted number string
     */
    private StringBuffer format(BigDecimal number, StringBuffer result,
                                FieldDelegate delegate) {
        if (multiplier != 1) {
            number = number.multiply(getBigDecimalMultiplier());
        }
        boolean isNegative = number.signum() == -1;
        if (isNegative) {
            number = number.negate();
        }

        synchronized(digitList) {
            int maxIntDigits = getMaximumIntegerDigits();
            int minIntDigits = getMinimumIntegerDigits();
            int maxFraDigits = getMaximumFractionDigits();
            int minFraDigits = getMinimumFractionDigits();
            int maximumDigits = maxIntDigits + maxFraDigits;

            digitList.set(isNegative, number, useExponentialNotation ?
                ((maximumDigits < 0) ? Integer.MAX_VALUE : maximumDigits) :
                maxFraDigits, !useExponentialNotation);

            return subformat(result, delegate, isNegative, false,
                maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);
        }
    }

    /**
     * Format a BigInteger to produce a string.
     * @param number    The BigInteger to format
     * @param result    where the text is to be appended
     * @param fieldPosition    On input: an alignment field, if desired.
     * On output: the offsets of the alignment field.
     * @return The formatted number string
     * @exception        ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.FieldPosition
     */
    private StringBuffer format(BigInteger number, StringBuffer result,
                               FieldPosition fieldPosition) {
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);

        return format(number, result, fieldPosition.getFieldDelegate(), false);
    }

    /**
     * Format a BigInteger to produce a string.
     * @param number    The BigInteger to format
     * @param result    where the text is to be appended
     * @param delegate notified of locations of sub fields
     * @return The formatted number string
     * @exception        ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.FieldPosition
     */
    private StringBuffer format(BigInteger number, StringBuffer result,
                               FieldDelegate delegate, boolean formatLong) {
        if (multiplier != 1) {
            number = number.multiply(getBigIntegerMultiplier());
        }
        boolean isNegative = number.signum() == -1;
        if (isNegative) {
            number = number.negate();
        }

        synchronized(digitList) {
            int maxIntDigits, minIntDigits, maxFraDigits, minFraDigits, maximumDigits;
            if (formatLong) {
                maxIntDigits = super.getMaximumIntegerDigits();
                minIntDigits = super.getMinimumIntegerDigits();
                maxFraDigits = super.getMaximumFractionDigits();
                minFraDigits = super.getMinimumFractionDigits();
                maximumDigits = maxIntDigits + maxFraDigits;
            } else {
                maxIntDigits = getMaximumIntegerDigits();
                minIntDigits = getMinimumIntegerDigits();
                maxFraDigits = getMaximumFractionDigits();
                minFraDigits = getMinimumFractionDigits();
                maximumDigits = maxIntDigits + maxFraDigits;
                if (maximumDigits < 0) {
                    maximumDigits = Integer.MAX_VALUE;
                }
            }

            digitList.set(isNegative, number,
                          useExponentialNotation ? maximumDigits : 0);

            return subformat(result, delegate, isNegative, true,
                maxIntDigits, minIntDigits, maxFraDigits, minFraDigits);
        }
    }

    /**
     * Formats an Object producing an <code>AttributedCharacterIterator</code>.
     * You can use the returned <code>AttributedCharacterIterator</code>
     * to build the resulting String, as well as to determine information
     * about the resulting String.
     * <p>
     * Each attribute key of the AttributedCharacterIterator will be of type
     * <code>NumberFormat.Field</code>, with the attribute value being the
     * same as the attribute key.
     *
     * @exception NullPointerException if obj is null.
     * @exception IllegalArgumentException when the Format cannot format the
     *            given object.
     * @exception        ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @param obj The object to format
     * @return AttributedCharacterIterator describing the formatted value.
     * @since 1.4
     */
    @Override
    public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        CharacterIteratorFieldDelegate delegate =
                         new CharacterIteratorFieldDelegate();
        StringBuffer sb = new StringBuffer();

        if (obj instanceof Double || obj instanceof Float) {
            format(((Number)obj).doubleValue(), sb, delegate);
        } else if (obj instanceof Long || obj instanceof Integer ||
                   obj instanceof Short || obj instanceof Byte ||
                   obj instanceof AtomicInteger || obj instanceof AtomicLong) {
            format(((Number)obj).longValue(), sb, delegate);
        } else if (obj instanceof BigDecimal) {
            format((BigDecimal)obj, sb, delegate);
        } else if (obj instanceof BigInteger) {
            format((BigInteger)obj, sb, delegate, false);
        } else if (obj == null) {
            throw new NullPointerException(
                "formatToCharacterIterator must be passed non-null object");
        } else {
            throw new IllegalArgumentException(
                "Cannot format given Object as a Number");
        }
        return delegate.getIterator(sb.toString());
    }

    // ==== Begin fast-path formating logic for double =========================

    /* Fast-path formatting will be used for format(double ...) methods iff a
     * number of conditions are met (see checkAndSetFastPathStatus()):
     * - Only if instance properties meet the right predefined conditions.
     * - The abs value of the double to format is <= Integer.MAX_VALUE.
     *
     * The basic approach is to split the binary to decimal conversion of a
     * double value into two phases:
     * * The conversion of the integer portion of the double.
     * * The conversion of the fractional portion of the double
     *   (limited to two or three digits).
     *
     * The isolation and conversion of the integer portion of the double is
     * straightforward. The conversion of the fraction is more subtle and relies
     * on some rounding properties of double to the decimal precisions in
     * question.  Using the terminology of BigDecimal, this fast-path algorithm
     * is applied when a double value has a magnitude less than Integer.MAX_VALUE
     * and rounding is to nearest even and the destination format has two or
     * three digits of *scale* (digits after the decimal point).
     *
     * Under a rounding to nearest even policy, the returned result is a digit
     * string of a number in the (in this case decimal) destination format
     * closest to the exact numerical value of the (in this case binary) input
     * value.  If two destination format numbers are equally distant, the one
     * with the last digit even is returned.  To compute such a correctly rounded
     * value, some information about digits beyond the smallest returned digit
     * position needs to be consulted.
     *
     * In general, a guard digit, a round digit, and a sticky *bit* are needed
     * beyond the returned digit position.  If the discarded portion of the input
     * is sufficiently large, the returned digit string is incremented.  In round
     * to nearest even, this threshold to increment occurs near the half-way
     * point between digits.  The sticky bit records if there are any remaining
     * trailing digits of the exact input value in the new format; the sticky bit
     * is consulted only in close to half-way rounding cases.
     *
     * Given the computation of the digit and bit values, rounding is then
     * reduced to a table lookup problem.  For decimal, the even/odd cases look
     * like this:
     *
     * Last   Round   Sticky
     * 6      5       0      => 6   // exactly halfway, return even digit.
     * 6      5       1      => 7   // a little bit more than halfway, round up.
     * 7      5       0      => 8   // exactly halfway, round up to even.
     * 7      5       1      => 8   // a little bit more than halfway, round up.
     * With analogous entries for other even and odd last-returned digits.
     *
     * However, decimal negative powers of 5 smaller than 0.5 are *not* exactly
     * representable as binary fraction.  In particular, 0.005 (the round limit
     * for a two-digit scale) and 0.0005 (the round limit for a three-digit
     * scale) are not representable. Therefore, for input values near these cases
     * the sticky bit is known to be set which reduces the rounding logic to:
     *
     * Last   Round   Sticky
     * 6      5       1      => 7   // a little bit more than halfway, round up.
     * 7      5       1      => 8   // a little bit more than halfway, round up.
     *
     * In other words, if the round digit is 5, the sticky bit is known to be
     * set.  If the round digit is something other than 5, the sticky bit is not
     * relevant.  Therefore, some of the logic about whether or not to increment
     * the destination *decimal* value can occur based on tests of *binary*
     * computations of the binary input number.
     */

    /**
     * Check validity of using fast-path for this instance. If fast-path is valid
     * for this instance, sets fast-path state as true and initializes fast-path
     * utility fields as needed.
     *
     * This method is supposed to be called rarely, otherwise that will break the
     * fast-path performance. That means avoiding frequent changes of the
     * properties of the instance, since for most properties, each time a change
     * happens, a call to this method is needed at the next format call.
     *
     * FAST-PATH RULES:
     *  Similar to the default DecimalFormat instantiation case.
     *  More precisely:
     *  - HALF_EVEN rounding mode,
     *  - isGroupingUsed() is true,
     *  - groupingSize of 3,
     *  - multiplier is 1,
     *  - Decimal separator not mandatory,
     *  - No use of exponential notation,
     *  - minimumIntegerDigits is exactly 1 and maximumIntegerDigits at least 10
     *  - For number of fractional digits, the exact values found in the default case:
     *     Currency : min = max = 2.
     *     Decimal  : min = 0. max = 3.
     *
     */
    private void checkAndSetFastPathStatus() {

        boolean fastPathWasOn = isFastPath;

        if ((roundingMode == RoundingMode.HALF_EVEN) &&
            (isGroupingUsed()) &&
            (groupingSize == 3) &&
            (multiplier == 1) &&
            (!decimalSeparatorAlwaysShown) &&
            (!useExponentialNotation)) {

            // The fast-path algorithm is semi-hardcoded against
            //  minimumIntegerDigits and maximumIntegerDigits.
            isFastPath = ((minimumIntegerDigits == 1) &&
                          (maximumIntegerDigits >= 10));

            // The fast-path algorithm is hardcoded against
            //  minimumFractionDigits and maximumFractionDigits.
            if (isFastPath) {
                if (isCurrencyFormat) {
                    if ((minimumFractionDigits != 2) ||
                        (maximumFractionDigits != 2))
                        isFastPath = false;
                } else if ((minimumFractionDigits != 0) ||
                           (maximumFractionDigits != 3))
                    isFastPath = false;
            }
        } else
            isFastPath = false;

        // Since some instance properties may have changed while still falling
        // in the fast-path case, we need to reinitialize fastPathData anyway.
        if (isFastPath) {
            // We need to instantiate fastPathData if not already done.
            if (fastPathData == null)
                fastPathData = new FastPathData();

            // Sets up the locale specific constants used when formatting.
            // '0' is our default representation of zero.
            fastPathData.zeroDelta = symbols.getZeroDigit() - '0';
            fastPathData.groupingChar = symbols.getGroupingSeparator();

            // Sets up fractional constants related to currency/decimal pattern.
            fastPathData.fractionalMaxIntBound = (isCurrencyFormat) ? 99 : 999;
            fastPathData.fractionalScaleFactor = (isCurrencyFormat) ? 100.0d : 1000.0d;

            // Records the need for adding prefix or suffix
            fastPathData.positiveAffixesRequired =
                (positivePrefix.length() != 0) || (positiveSuffix.length() != 0);
            fastPathData.negativeAffixesRequired =
                (negativePrefix.length() != 0) || (negativeSuffix.length() != 0);

            // Creates a cached char container for result, with max possible size.
            int maxNbIntegralDigits = 10;
            int maxNbGroups = 3;
            int containerSize =
                Math.max(positivePrefix.length(), negativePrefix.length()) +
                maxNbIntegralDigits + maxNbGroups + 1 + maximumFractionDigits +
                Math.max(positiveSuffix.length(), negativeSuffix.length());

            fastPathData.fastPathContainer = new char[containerSize];

            // Sets up prefix and suffix char arrays constants.
            fastPathData.charsPositiveSuffix = positiveSuffix.toCharArray();
            fastPathData.charsNegativeSuffix = negativeSuffix.toCharArray();
            fastPathData.charsPositivePrefix = positivePrefix.toCharArray();
            fastPathData.charsNegativePrefix = negativePrefix.toCharArray();

            // Sets up fixed index positions for integral and fractional digits.
            // Sets up decimal point in cached result container.
            int longestPrefixLength =
                Math.max(positivePrefix.length(), negativePrefix.length());
            int decimalPointIndex =
                maxNbIntegralDigits + maxNbGroups + longestPrefixLength;

            fastPathData.integralLastIndex    = decimalPointIndex - 1;
            fastPathData.fractionalFirstIndex = decimalPointIndex + 1;
            fastPathData.fastPathContainer[decimalPointIndex] =
                isCurrencyFormat ?
                symbols.getMonetaryDecimalSeparator() :
                symbols.getDecimalSeparator();

        } else if (fastPathWasOn) {
            // Previous state was fast-path and is no more.
            // Resets cached array constants.
            fastPathData.fastPathContainer = null;
            fastPathData.charsPositiveSuffix = null;
            fastPathData.charsNegativeSuffix = null;
            fastPathData.charsPositivePrefix = null;
            fastPathData.charsNegativePrefix = null;
        }

        fastPathCheckNeeded = false;
    }

    /**
     * Returns true if rounding-up must be done on {@code scaledFractionalPartAsInt},
     * false otherwise.
     *
     * This is a utility method that takes correct half-even rounding decision on
     * passed fractional value at the scaled decimal point (2 digits for currency
     * case and 3 for decimal case), when the approximated fractional part after
     * scaled decimal point is exactly 0.5d.  This is done by means of exact
     * calculations on the {@code fractionalPart} floating-point value.
     *
     * This method is supposed to be called by private {@code fastDoubleFormat}
     * method only.
     *
     * The algorithms used for the exact calculations are :
     *
     * The <b><i>FastTwoSum</i></b> algorithm, from T.J.Dekker, described in the
     * papers  "<i>A  Floating-Point   Technique  for  Extending  the  Available
     * Precision</i>"  by Dekker, and  in "<i>Adaptive  Precision Floating-Point
     * Arithmetic and Fast Robust Geometric Predicates</i>" from J.Shewchuk.
     *
     * A modified version of <b><i>Sum2S</i></b> cascaded summation described in
     * "<i>Accurate Sum and Dot Product</i>" from Takeshi Ogita and All.  As
     * Ogita says in this paper this is an equivalent of the Kahan-Babuska's
     * summation algorithm because we order the terms by magnitude before summing
     * them. For this reason we can use the <i>FastTwoSum</i> algorithm rather
     * than the more expensive Knuth's <i>TwoSum</i>.
     *
     * We do this to avoid a more expensive exact "<i>TwoProduct</i>" algorithm,
     * like those described in Shewchuk's paper above. See comments in the code
     * below.
     *
     * @param  fractionalPart The  fractional value  on which  we  take rounding
     * decision.
     * @param scaledFractionalPartAsInt The integral part of the scaled
     * fractional value.
     *
     * @return the decision that must be taken regarding half-even rounding.
     */
    private boolean exactRoundUp(double fractionalPart,
                                 int scaledFractionalPartAsInt) {

        /* exactRoundUp() method is called by fastDoubleFormat() only.
         * The precondition expected to be verified by the passed parameters is :
         * scaledFractionalPartAsInt ==
         *     (int) (fractionalPart * fastPathData.fractionalScaleFactor).
         * This is ensured by fastDoubleFormat() code.
         */

        /* We first calculate roundoff error made by fastDoubleFormat() on
         * the scaled fractional part. We do this with exact calculation on the
         * passed fractionalPart. Rounding decision will then be taken from roundoff.
         */

        /* ---- TwoProduct(fractionalPart, scale factor (i.e. 1000.0d or 100.0d)).
         *
         * The below is an optimized exact "TwoProduct" calculation of passed
         * fractional part with scale factor, using Ogita's Sum2S cascaded
         * summation adapted as Kahan-Babuska equivalent by using FastTwoSum
         * (much faster) rather than Knuth's TwoSum.
         *
         * We can do this because we order the summation from smallest to
         * greatest, so that FastTwoSum can be used without any additional error.
         *
         * The "TwoProduct" exact calculation needs 17 flops. We replace this by
         * a cascaded summation of FastTwoSum calculations, each involving an
         * exact multiply by a power of 2.
         *
         * Doing so saves overall 4 multiplications and 1 addition compared to
         * using traditional "TwoProduct".
         *
         * The scale factor is either 100 (currency case) or 1000 (decimal case).
         * - when 1000, we replace it by (1024 - 16 - 8) = 1000.
         * - when 100,  we replace it by (128  - 32 + 4) =  100.
         * Every multiplication by a power of 2 (1024, 128, 32, 16, 8, 4) is exact.
         *
         */
        double approxMax;    // Will always be positive.
        double approxMedium; // Will always be negative.
        double approxMin;

        double fastTwoSumApproximation = 0.0d;
        double fastTwoSumRoundOff = 0.0d;
        double bVirtual = 0.0d;

        if (isCurrencyFormat) {
            // Scale is 100 = 128 - 32 + 4.
            // Multiply by 2**n is a shift. No roundoff. No error.
            approxMax    = fractionalPart * 128.00d;
            approxMedium = - (fractionalPart * 32.00d);
            approxMin    = fractionalPart * 4.00d;
        } else {
            // Scale is 1000 = 1024 - 16 - 8.
            // Multiply by 2**n is a shift. No roundoff. No error.
            approxMax    = fractionalPart * 1024.00d;
            approxMedium = - (fractionalPart * 16.00d);
            approxMin    = - (fractionalPart * 8.00d);
        }

        // Shewchuk/Dekker's FastTwoSum(approxMedium, approxMin).
        assert(-approxMedium >= Math.abs(approxMin));
        fastTwoSumApproximation = approxMedium + approxMin;
        bVirtual = fastTwoSumApproximation - approxMedium;
        fastTwoSumRoundOff = approxMin - bVirtual;
        double approxS1 = fastTwoSumApproximation;
        double roundoffS1 = fastTwoSumRoundOff;

        // Shewchuk/Dekker's FastTwoSum(approxMax, approxS1);
        assert(approxMax >= Math.abs(approxS1));
        fastTwoSumApproximation = approxMax + approxS1;
        bVirtual = fastTwoSumApproximation - approxMax;
        fastTwoSumRoundOff = approxS1 - bVirtual;
        double roundoff1000 = fastTwoSumRoundOff;
        double approx1000 = fastTwoSumApproximation;
        double roundoffTotal = roundoffS1 + roundoff1000;

        // Shewchuk/Dekker's FastTwoSum(approx1000, roundoffTotal);
        assert(approx1000 >= Math.abs(roundoffTotal));
        fastTwoSumApproximation = approx1000 + roundoffTotal;
        bVirtual = fastTwoSumApproximation - approx1000;

        // Now we have got the roundoff for the scaled fractional
        double scaledFractionalRoundoff = roundoffTotal - bVirtual;

        // ---- TwoProduct(fractionalPart, scale (i.e. 1000.0d or 100.0d)) end.

        /* ---- Taking the rounding decision
         *
         * We take rounding decision based on roundoff and half-even rounding
         * rule.
         *
         * The above TwoProduct gives us the exact roundoff on the approximated
         * scaled fractional, and we know that this approximation is exactly
         * 0.5d, since that has already been tested by the caller
         * (fastDoubleFormat).
         *
         * Decision comes first from the sign of the calculated exact roundoff.
         * - Since being exact roundoff, it cannot be positive with a scaled
         *   fractional less than 0.5d, as well as negative with a scaled
         *   fractional greater than 0.5d. That leaves us with following 3 cases.
         * - positive, thus scaled fractional == 0.500....0fff ==> round-up.
         * - negative, thus scaled fractional == 0.499....9fff ==> don't round-up.
         * - is zero,  thus scaled fractioanl == 0.5 ==> half-even rounding applies :
         *    we round-up only if the integral part of the scaled fractional is odd.
         *
         */
        if (scaledFractionalRoundoff > 0.0) {
            return true;
        } else if (scaledFractionalRoundoff < 0.0) {
            return false;
        } else if ((scaledFractionalPartAsInt & 1) != 0) {
            return true;
        }

        return false;

        // ---- Taking the rounding decision end
    }

    /**
     * Collects integral digits from passed {@code number}, while setting
     * grouping chars as needed. Updates {@code firstUsedIndex} accordingly.
     *
     * Loops downward starting from {@code backwardIndex} position (inclusive).
     *
     * @param number  The int value from which we collect digits.
     * @param digitsBuffer The char array container where digits and grouping chars
     *  are stored.
     * @param backwardIndex the position from which we start storing digits in
     *  digitsBuffer.
     *
     */
    private void collectIntegralDigits(int number,
                                       char[] digitsBuffer,
                                       int backwardIndex) {
        int index = backwardIndex;
        int q;
        int r;
        while (number > 999) {
            // Generates 3 digits per iteration.
            q = number / 1000;
            r = number - (q << 10) + (q << 4) + (q << 3); // -1024 +16 +8 = 1000.
            number = q;

            digitsBuffer[index--] = DigitArrays.DigitOnes1000[r];
            digitsBuffer[index--] = DigitArrays.DigitTens1000[r];
            digitsBuffer[index--] = DigitArrays.DigitHundreds1000[r];
            digitsBuffer[index--] = fastPathData.groupingChar;
        }

        // Collects last 3 or less digits.
        digitsBuffer[index] = DigitArrays.DigitOnes1000[number];
        if (number > 9) {
            digitsBuffer[--index]  = DigitArrays.DigitTens1000[number];
            if (number > 99)
                digitsBuffer[--index]   = DigitArrays.DigitHundreds1000[number];
        }

        fastPathData.firstUsedIndex = index;
    }

    /**
     * Collects the 2 (currency) or 3 (decimal) fractional digits from passed
     * {@code number}, starting at {@code startIndex} position
     * inclusive.  There is no punctuation to set here (no grouping chars).
     * Updates {@code fastPathData.lastFreeIndex} accordingly.
     *
     *
     * @param number  The int value from which we collect digits.
     * @param digitsBuffer The char array container where digits are stored.
     * @param startIndex the position from which we start storing digits in
     *  digitsBuffer.
     *
     */
    private void collectFractionalDigits(int number,
                                         char[] digitsBuffer,
                                         int startIndex) {
        int index = startIndex;

        char digitOnes = DigitArrays.DigitOnes1000[number];
        char digitTens = DigitArrays.DigitTens1000[number];

        if (isCurrencyFormat) {
            // Currency case. Always collects fractional digits.
            digitsBuffer[index++] = digitTens;
            digitsBuffer[index++] = digitOnes;
        } else if (number != 0) {
            // Decimal case. Hundreds will always be collected
            digitsBuffer[index++] = DigitArrays.DigitHundreds1000[number];

            // Ending zeros won't be collected.
            if (digitOnes != '0') {
                digitsBuffer[index++] = digitTens;
                digitsBuffer[index++] = digitOnes;
            } else if (digitTens != '0')
                digitsBuffer[index++] = digitTens;

        } else
            // This is decimal pattern and fractional part is zero.
            // We must remove decimal point from result.
            index--;

        fastPathData.lastFreeIndex = index;
    }

    /**
     * Internal utility.
     * Adds the passed {@code prefix} and {@code suffix} to {@code container}.
     *
     * @param container  Char array container which to prepend/append the
     *  prefix/suffix.
     * @param prefix     Char sequence to prepend as a prefix.
     * @param suffix     Char sequence to append as a suffix.
     *
     */
    //    private void addAffixes(boolean isNegative, char[] container) {
    private void addAffixes(char[] container, char[] prefix, char[] suffix) {

        // We add affixes only if needed (affix length > 0).
        int pl = prefix.length;
        int sl = suffix.length;
        if (pl != 0) prependPrefix(prefix, pl, container);
        if (sl != 0) appendSuffix(suffix, sl, container);

    }

    /**
     * Prepends the passed {@code prefix} chars to given result
     * {@code container}.  Updates {@code fastPathData.firstUsedIndex}
     * accordingly.
     *
     * @param prefix The prefix characters to prepend to result.
     * @param len The number of chars to prepend.
     * @param container Char array container which to prepend the prefix
     */
    private void prependPrefix(char[] prefix,
                               int len,
                               char[] container) {

        fastPathData.firstUsedIndex -= len;
        int startIndex = fastPathData.firstUsedIndex;

        // If prefix to prepend is only 1 char long, just assigns this char.
        // If prefix is less or equal 4, we use a dedicated algorithm that
        //  has shown to run faster than System.arraycopy.
        // If more than 4, we use System.arraycopy.
        if (len == 1)
            container[startIndex] = prefix[0];
        else if (len <= 4) {
            int dstLower = startIndex;
            int dstUpper = dstLower + len - 1;
            int srcUpper = len - 1;
            container[dstLower] = prefix[0];
            container[dstUpper] = prefix[srcUpper];

            if (len > 2)
                container[++dstLower] = prefix[1];
            if (len == 4)
                container[--dstUpper] = prefix[2];
        } else
            System.arraycopy(prefix, 0, container, startIndex, len);
    }

    /**
     * Appends the passed {@code suffix} chars to given result
     * {@code container}.  Updates {@code fastPathData.lastFreeIndex}
     * accordingly.
     *
     * @param suffix The suffix characters to append to result.
     * @param len The number of chars to append.
     * @param container Char array container which to append the suffix
     */
    private void appendSuffix(char[] suffix,
                              int len,
                              char[] container) {

        int startIndex = fastPathData.lastFreeIndex;

        // If suffix to append is only 1 char long, just assigns this char.
        // If suffix is less or equal 4, we use a dedicated algorithm that
        //  has shown to run faster than System.arraycopy.
        // If more than 4, we use System.arraycopy.
        if (len == 1)
            container[startIndex] = suffix[0];
        else if (len <= 4) {
            int dstLower = startIndex;
            int dstUpper = dstLower + len - 1;
            int srcUpper = len - 1;
            container[dstLower] = suffix[0];
            container[dstUpper] = suffix[srcUpper];

            if (len > 2)
                container[++dstLower] = suffix[1];
            if (len == 4)
                container[--dstUpper] = suffix[2];
        } else
            System.arraycopy(suffix, 0, container, startIndex, len);

        fastPathData.lastFreeIndex += len;
    }

    /**
     * Converts digit chars from {@code digitsBuffer} to current locale.
     *
     * Must be called before adding affixes since we refer to
     * {@code fastPathData.firstUsedIndex} and {@code fastPathData.lastFreeIndex},
     * and do not support affixes (for speed reason).
     *
     * We loop backward starting from last used index in {@code fastPathData}.
     *
     * @param digitsBuffer The char array container where the digits are stored.
     */
    private void localizeDigits(char[] digitsBuffer) {

        // We will localize only the digits, using the groupingSize,
        // and taking into account fractional part.

        // First take into account fractional part.
        int digitsCounter =
            fastPathData.lastFreeIndex - fastPathData.fractionalFirstIndex;

        // The case when there is no fractional digits.
        if (digitsCounter < 0)
            digitsCounter = groupingSize;

        // Only the digits remains to localize.
        for (int cursor = fastPathData.lastFreeIndex - 1;
             cursor >= fastPathData.firstUsedIndex;
             cursor--) {
            if (digitsCounter != 0) {
                // This is a digit char, we must localize it.
                digitsBuffer[cursor] += fastPathData.zeroDelta;
                digitsCounter--;
            } else {
                // Decimal separator or grouping char. Reinit counter only.
                digitsCounter = groupingSize;
            }
        }
    }

    /**
     * This is the main entry point for the fast-path format algorithm.
     *
     * At this point we are sure to be in the expected conditions to run it.
     * This algorithm builds the formatted result and puts it in the dedicated
     * {@code fastPathData.fastPathContainer}.
     *
     * @param d the double value to be formatted.
     * @param negative Flag precising if {@code d} is negative.
     */
    private void fastDoubleFormat(double d,
                                  boolean negative) {

        char[] container = fastPathData.fastPathContainer;

        /*
         * The principle of the algorithm is to :
         * - Break the passed double into its integral and fractional parts
         *    converted into integers.
         * - Then decide if rounding up must be applied or not by following
         *    the half-even rounding rule, first using approximated scaled
         *    fractional part.
         * - For the difficult cases (approximated scaled fractional part
         *    being exactly 0.5d), we refine the rounding decision by calling
         *    exactRoundUp utility method that both calculates the exact roundoff
         *    on the approximation and takes correct rounding decision.
         * - We round-up the fractional part if needed, possibly propagating the
         *    rounding to integral part if we meet a "all-nine" case for the
         *    scaled fractional part.
         * - We then collect digits from the resulting integral and fractional
         *   parts, also setting the required grouping chars on the fly.
         * - Then we localize the collected digits if needed, and
         * - Finally prepend/append prefix/suffix if any is needed.
         */

        // Exact integral part of d.
        int integralPartAsInt = (int) d;

        // Exact fractional part of d (since we subtract it's integral part).
        double exactFractionalPart = d - (double) integralPartAsInt;

        // Approximated scaled fractional part of d (due to multiplication).
        double scaledFractional =
            exactFractionalPart * fastPathData.fractionalScaleFactor;

        // Exact integral part of scaled fractional above.
        int fractionalPartAsInt = (int) scaledFractional;

        // Exact fractional part of scaled fractional above.
        scaledFractional = scaledFractional - (double) fractionalPartAsInt;

        // Only when scaledFractional is exactly 0.5d do we have to do exact
        // calculations and take fine-grained rounding decision, since
        // approximated results above may lead to incorrect decision.
        // Otherwise comparing against 0.5d (strictly greater or less) is ok.
        boolean roundItUp = false;
        if (scaledFractional >= 0.5d) {
            if (scaledFractional == 0.5d)
                // Rounding need fine-grained decision.
                roundItUp = exactRoundUp(exactFractionalPart, fractionalPartAsInt);
            else
                roundItUp = true;

            if (roundItUp) {
                // Rounds up both fractional part (and also integral if needed).
                if (fractionalPartAsInt < fastPathData.fractionalMaxIntBound) {
                    fractionalPartAsInt++;
                } else {
                    // Propagates rounding to integral part since "all nines" case.
                    fractionalPartAsInt = 0;
                    integralPartAsInt++;
                }
            }
        }

        // Collecting digits.
        collectFractionalDigits(fractionalPartAsInt, container,
                                fastPathData.fractionalFirstIndex);
        collectIntegralDigits(integralPartAsInt, container,
                              fastPathData.integralLastIndex);

        // Localizing digits.
        if (fastPathData.zeroDelta != 0)
            localizeDigits(container);

        // Adding prefix and suffix.
        if (negative) {
            if (fastPathData.negativeAffixesRequired)
                addAffixes(container,
                           fastPathData.charsNegativePrefix,
                           fastPathData.charsNegativeSuffix);
        } else if (fastPathData.positiveAffixesRequired)
            addAffixes(container,
                       fastPathData.charsPositivePrefix,
                       fastPathData.charsPositiveSuffix);
    }

    /**
     * A fast-path shortcut of format(double) to be called by NumberFormat, or by
     * format(double, ...) public methods.
     *
     * If instance can be applied fast-path and passed double is not NaN or
     * Infinity, is in the integer range, we call {@code fastDoubleFormat}
     * after changing {@code d} to its positive value if necessary.
     *
     * Otherwise returns null by convention since fast-path can't be exercized.
     *
     * @param d The double value to be formatted
     *
     * @return the formatted result for {@code d} as a string.
     */
    String fastFormat(double d) {
        // (Re-)Evaluates fast-path status if needed.
        if (fastPathCheckNeeded)
            checkAndSetFastPathStatus();

        if (!isFastPath )
            // DecimalFormat instance is not in a fast-path state.
            return null;

        if (!Double.isFinite(d))
            // Should not use fast-path for Infinity and NaN.
            return null;

        // Extracts and records sign of double value, possibly changing it
        // to a positive one, before calling fastDoubleFormat().
        boolean negative = false;
        if (d < 0.0d) {
            negative = true;
            d = -d;
        } else if (d == 0.0d) {
            negative = (Math.copySign(1.0d, d) == -1.0d);
            d = +0.0d;
        }

        if (d > MAX_INT_AS_DOUBLE)
            // Filters out values that are outside expected fast-path range
            return null;
        else
            fastDoubleFormat(d, negative);

        // Returns a new string from updated fastPathContainer.
        return new String(fastPathData.fastPathContainer,
                          fastPathData.firstUsedIndex,
                          fastPathData.lastFreeIndex - fastPathData.firstUsedIndex);

    }

    // ======== End fast-path formating logic for double =========================

    /**
     * Complete the formatting of a finite number.  On entry, the digitList must
     * be filled in with the correct digits.
     */
    private StringBuffer subformat(StringBuffer result, FieldDelegate delegate,
                                   boolean isNegative, boolean isInteger,
                                   int maxIntDigits, int minIntDigits,
                                   int maxFraDigits, int minFraDigits) {
        // NOTE: This isn't required anymore because DigitList takes care of this.
        //
        //  // The negative of the exponent represents the number of leading
        //  // zeros between the decimal and the first non-zero digit, for
        //  // a value < 0.1 (e.g., for 0.00123, -fExponent == 2).  If this
        //  // is more than the maximum fraction digits, then we have an underflow
        //  // for the printed representation.  We recognize this here and set
        //  // the DigitList representation to zero in this situation.
        //
        //  if (-digitList.decimalAt >= getMaximumFractionDigits())
        //  {
        //      digitList.count = 0;
        //  }

        char zero = symbols.getZeroDigit();
        int zeroDelta = zero - '0'; // '0' is the DigitList representation of zero
        char grouping = symbols.getGroupingSeparator();
        char decimal = isCurrencyFormat ?
            symbols.getMonetaryDecimalSeparator() :
            symbols.getDecimalSeparator();

        /* Per bug 4147706, DecimalFormat must respect the sign of numbers which
         * format as zero.  This allows sensible computations and preserves
         * relations such as signum(1/x) = signum(x), where x is +Infinity or
         * -Infinity.  Prior to this fix, we always formatted zero values as if
         * they were positive.  Liu 7/6/98.
         */
        if (digitList.isZero()) {
            digitList.decimalAt = 0; // Normalize
        }

        if (isNegative) {
            append(result, negativePrefix, delegate,
                   getNegativePrefixFieldPositions(), Field.SIGN);
        } else {
            append(result, positivePrefix, delegate,
                   getPositivePrefixFieldPositions(), Field.SIGN);
        }

        if (useExponentialNotation) {
            int iFieldStart = result.length();
            int iFieldEnd = -1;
            int fFieldStart = -1;

            // Minimum integer digits are handled in exponential format by
            // adjusting the exponent.  For example, 0.01234 with 3 minimum
            // integer digits is "123.4E-4".

            // Maximum integer digits are interpreted as indicating the
            // repeating range.  This is useful for engineering notation, in
            // which the exponent is restricted to a multiple of 3.  For
            // example, 0.01234 with 3 maximum integer digits is "12.34e-3".
            // If maximum integer digits are > 1 and are larger than
            // minimum integer digits, then minimum integer digits are
            // ignored.
            int exponent = digitList.decimalAt;
            int repeat = maxIntDigits;
            int minimumIntegerDigits = minIntDigits;
            if (repeat > 1 && repeat > minIntDigits) {
                // A repeating range is defined; adjust to it as follows.
                // If repeat == 3, we have 6,5,4=>3; 3,2,1=>0; 0,-1,-2=>-3;
                // -3,-4,-5=>-6, etc. This takes into account that the
                // exponent we have here is off by one from what we expect;
                // it is for the format 0.MMMMMx10^n.
                if (exponent >= 1) {
                    exponent = ((exponent - 1) / repeat) * repeat;
                } else {
                    // integer division rounds towards 0
                    exponent = ((exponent - repeat) / repeat) * repeat;
                }
                minimumIntegerDigits = 1;
            } else {
                // No repeating range is defined; use minimum integer digits.
                exponent -= minimumIntegerDigits;
            }

            // We now output a minimum number of digits, and more if there
            // are more digits, up to the maximum number of digits.  We
            // place the decimal point after the "integer" digits, which
            // are the first (decimalAt - exponent) digits.
            int minimumDigits = minIntDigits + minFraDigits;
            if (minimumDigits < 0) {    // overflow?
                minimumDigits = Integer.MAX_VALUE;
            }

            // The number of integer digits is handled specially if the number
            // is zero, since then there may be no digits.
            int integerDigits = digitList.isZero() ? minimumIntegerDigits :
                    digitList.decimalAt - exponent;
            if (minimumDigits < integerDigits) {
                minimumDigits = integerDigits;
            }
            int totalDigits = digitList.count;
            if (minimumDigits > totalDigits) {
                totalDigits = minimumDigits;
            }
            boolean addedDecimalSeparator = false;

            for (int i=0; i<totalDigits; ++i) {
                if (i == integerDigits) {
                    // Record field information for caller.
                    iFieldEnd = result.length();

                    result.append(decimal);
                    addedDecimalSeparator = true;

                    // Record field information for caller.
                    fFieldStart = result.length();
                }
                result.append((i < digitList.count) ?
                              (char)(digitList.digits[i] + zeroDelta) :
                              zero);
            }

            if (decimalSeparatorAlwaysShown && totalDigits == integerDigits) {
                // Record field information for caller.
                iFieldEnd = result.length();

                result.append(decimal);
                addedDecimalSeparator = true;

                // Record field information for caller.
                fFieldStart = result.length();
            }

            // Record field information
            if (iFieldEnd == -1) {
                iFieldEnd = result.length();
            }
            delegate.formatted(INTEGER_FIELD, Field.INTEGER, Field.INTEGER,
                               iFieldStart, iFieldEnd, result);
            if (addedDecimalSeparator) {
                delegate.formatted(Field.DECIMAL_SEPARATOR,
                                   Field.DECIMAL_SEPARATOR,
                                   iFieldEnd, fFieldStart, result);
            }
            if (fFieldStart == -1) {
                fFieldStart = result.length();
            }
            delegate.formatted(FRACTION_FIELD, Field.FRACTION, Field.FRACTION,
                               fFieldStart, result.length(), result);

            // The exponent is output using the pattern-specified minimum
            // exponent digits.  There is no maximum limit to the exponent
            // digits, since truncating the exponent would result in an
            // unacceptable inaccuracy.
            int fieldStart = result.length();

            result.append(symbols.getExponentSeparator());

            delegate.formatted(Field.EXPONENT_SYMBOL, Field.EXPONENT_SYMBOL,
                               fieldStart, result.length(), result);

            // For zero values, we force the exponent to zero.  We
            // must do this here, and not earlier, because the value
            // is used to determine integer digit count above.
            if (digitList.isZero()) {
                exponent = 0;
            }

            boolean negativeExponent = exponent < 0;
            if (negativeExponent) {
                exponent = -exponent;
                fieldStart = result.length();
                result.append(symbols.getMinusSign());
                delegate.formatted(Field.EXPONENT_SIGN, Field.EXPONENT_SIGN,
                                   fieldStart, result.length(), result);
            }
            digitList.set(negativeExponent, exponent);

            int eFieldStart = result.length();

            for (int i=digitList.decimalAt; i<minExponentDigits; ++i) {
                result.append(zero);
            }
            for (int i=0; i<digitList.decimalAt; ++i) {
                result.append((i < digitList.count) ?
                          (char)(digitList.digits[i] + zeroDelta) : zero);
            }
            delegate.formatted(Field.EXPONENT, Field.EXPONENT, eFieldStart,
                               result.length(), result);
        } else {
            int iFieldStart = result.length();

            // Output the integer portion.  Here 'count' is the total
            // number of integer digits we will display, including both
            // leading zeros required to satisfy getMinimumIntegerDigits,
            // and actual digits present in the number.
            int count = minIntDigits;
            int digitIndex = 0; // Index into digitList.fDigits[]
            if (digitList.decimalAt > 0 && count < digitList.decimalAt) {
                count = digitList.decimalAt;
            }

            // Handle the case where getMaximumIntegerDigits() is smaller
            // than the real number of integer digits.  If this is so, we
            // output the least significant max integer digits.  For example,
            // the value 1997 printed with 2 max integer digits is just "97".
            if (count > maxIntDigits) {
                count = maxIntDigits;
                digitIndex = digitList.decimalAt - count;
            }

            int sizeBeforeIntegerPart = result.length();
            for (int i=count-1; i>=0; --i) {
                if (i < digitList.decimalAt && digitIndex < digitList.count) {
                    // Output a real digit
                    result.append((char)(digitList.digits[digitIndex++] + zeroDelta));
                } else {
                    // Output a leading zero
                    result.append(zero);
                }

                // Output grouping separator if necessary.  Don't output a
                // grouping separator if i==0 though; that's at the end of
                // the integer part.
                if (isGroupingUsed() && i>0 && (groupingSize != 0) &&
                    (i % groupingSize == 0)) {
                    int gStart = result.length();
                    result.append(grouping);
                    delegate.formatted(Field.GROUPING_SEPARATOR,
                                       Field.GROUPING_SEPARATOR, gStart,
                                       result.length(), result);
                }
            }

            // Determine whether or not there are any printable fractional
            // digits.  If we've used up the digits we know there aren't.
            boolean fractionPresent = (minFraDigits > 0) ||
                (!isInteger && digitIndex < digitList.count);

            // If there is no fraction present, and we haven't printed any
            // integer digits, then print a zero.  Otherwise we won't print
            // _any_ digits, and we won't be able to parse this string.
            if (!fractionPresent && result.length() == sizeBeforeIntegerPart) {
                result.append(zero);
            }

            delegate.formatted(INTEGER_FIELD, Field.INTEGER, Field.INTEGER,
                               iFieldStart, result.length(), result);

            // Output the decimal separator if we always do so.
            int sStart = result.length();
            if (decimalSeparatorAlwaysShown || fractionPresent) {
                result.append(decimal);
            }

            if (sStart != result.length()) {
                delegate.formatted(Field.DECIMAL_SEPARATOR,
                                   Field.DECIMAL_SEPARATOR,
                                   sStart, result.length(), result);
            }
            int fFieldStart = result.length();

            for (int i=0; i < maxFraDigits; ++i) {
                // Here is where we escape from the loop.  We escape if we've
                // output the maximum fraction digits (specified in the for
                // expression above).
                // We also stop when we've output the minimum digits and either:
                // we have an integer, so there is no fractional stuff to
                // display, or we're out of significant digits.
                if (i >= minFraDigits &&
                    (isInteger || digitIndex >= digitList.count)) {
                    break;
                }

                // Output leading fractional zeros. These are zeros that come
                // after the decimal but before any significant digits. These
                // are only output if abs(number being formatted) < 1.0.
                if (-1-i > (digitList.decimalAt-1)) {
                    result.append(zero);
                    continue;
                }

                // Output a digit, if we have any precision left, or a
                // zero if we don't.  We don't want to output noise digits.
                if (!isInteger && digitIndex < digitList.count) {
                    result.append((char)(digitList.digits[digitIndex++] + zeroDelta));
                } else {
                    result.append(zero);
                }
            }

            // Record field information for caller.
            delegate.formatted(FRACTION_FIELD, Field.FRACTION, Field.FRACTION,
                               fFieldStart, result.length(), result);
        }

        if (isNegative) {
            append(result, negativeSuffix, delegate,
                   getNegativeSuffixFieldPositions(), Field.SIGN);
        } else {
            append(result, positiveSuffix, delegate,
                   getPositiveSuffixFieldPositions(), Field.SIGN);
        }

        return result;
    }

    /**
     * Appends the String <code>string</code> to <code>result</code>.
     * <code>delegate</code> is notified of all  the
     * <code>FieldPosition</code>s in <code>positions</code>.
     * <p>
     * If one of the <code>FieldPosition</code>s in <code>positions</code>
     * identifies a <code>SIGN</code> attribute, it is mapped to
     * <code>signAttribute</code>. This is used
     * to map the <code>SIGN</code> attribute to the <code>EXPONENT</code>
     * attribute as necessary.
     * <p>
     * This is used by <code>subformat</code> to add the prefix/suffix.
     */
    private void append(StringBuffer result, String string,
                        FieldDelegate delegate,
                        FieldPosition[] positions,
                        Format.Field signAttribute) {
        int start = result.length();

        if (string.length() > 0) {
            result.append(string);
            for (int counter = 0, max = positions.length; counter < max;
                 counter++) {
                FieldPosition fp = positions[counter];
                Format.Field attribute = fp.getFieldAttribute();

                if (attribute == Field.SIGN) {
                    attribute = signAttribute;
                }
                delegate.formatted(attribute, attribute,
                                   start + fp.getBeginIndex(),
                                   start + fp.getEndIndex(), result);
            }
        }
    }

    /**
     * Parses text from a string to produce a <code>Number</code>.
     * <p>
     * The method attempts to parse text starting at the index given by
     * <code>pos</code>.
     * If parsing succeeds, then the index of <code>pos</code> is updated
     * to the index after the last character used (parsing does not necessarily
     * use all characters up to the end of the string), and the parsed
     * number is returned. The updated <code>pos</code> can be used to
     * indicate the starting point for the next call to this method.
     * If an error occurs, then the index of <code>pos</code> is not
     * changed, the error index of <code>pos</code> is set to the index of
     * the character where the error occurred, and null is returned.
     * <p>
     * The subclass returned depends on the value of {@link #isParseBigDecimal}
     * as well as on the string being parsed.
     * <ul>
     *   <li>If <code>isParseBigDecimal()</code> is false (the default),
     *       most integer values are returned as <code>Long</code>
     *       objects, no matter how they are written: <code>"17"</code> and
     *       <code>"17.000"</code> both parse to <code>Long(17)</code>.
     *       Values that cannot fit into a <code>Long</code> are returned as
     *       <code>Double</code>s. This includes values with a fractional part,
     *       infinite values, <code>NaN</code>, and the value -0.0.
     *       <code>DecimalFormat</code> does <em>not</em> decide whether to
     *       return a <code>Double</code> or a <code>Long</code> based on the
     *       presence of a decimal separator in the source string. Doing so
     *       would prevent integers that overflow the mantissa of a double,
     *       such as <code>"-9,223,372,036,854,775,808.00"</code>, from being
     *       parsed accurately.
     *       <p>
     *       Callers may use the <code>Number</code> methods
     *       <code>doubleValue</code>, <code>longValue</code>, etc., to obtain
     *       the type they want.
     *   <li>If <code>isParseBigDecimal()</code> is true, values are returned
     *       as <code>BigDecimal</code> objects. The values are the ones
     *       constructed by {@link java.math.BigDecimal#BigDecimal(String)}
     *       for corresponding strings in locale-independent format. The
     *       special cases negative and positive infinity and NaN are returned
     *       as <code>Double</code> instances holding the values of the
     *       corresponding <code>Double</code> constants.
     * </ul>
     * <p>
     * <code>DecimalFormat</code> parses all Unicode characters that represent
     * decimal digits, as defined by <code>Character.digit()</code>. In
     * addition, <code>DecimalFormat</code> also recognizes as digits the ten
     * consecutive characters starting with the localized zero digit defined in
     * the <code>DecimalFormatSymbols</code> object.
     *
     * @param text the string to be parsed
     * @param pos  A <code>ParsePosition</code> object with index and error
     *             index information as described above.
     * @return     the parsed value, or <code>null</code> if the parse fails
     * @exception  NullPointerException if <code>text</code> or
     *             <code>pos</code> is null.
     */
    @Override
    public Number parse(String text, ParsePosition pos) {
        // special case NaN
        if (text.regionMatches(pos.index, symbols.getNaN(), 0, symbols.getNaN().length())) {
            pos.index = pos.index + symbols.getNaN().length();
            return new Double(Double.NaN);
        }

        boolean[] status = new boolean[STATUS_LENGTH];
        if (!subparse(text, pos, positivePrefix, negativePrefix, digitList, false, status)) {
            return null;
        }

        // special case INFINITY
        if (status[STATUS_INFINITE]) {
            if (status[STATUS_POSITIVE] == (multiplier >= 0)) {
                return new Double(Double.POSITIVE_INFINITY);
            } else {
                return new Double(Double.NEGATIVE_INFINITY);
            }
        }

        if (multiplier == 0) {
            if (digitList.isZero()) {
                return new Double(Double.NaN);
            } else if (status[STATUS_POSITIVE]) {
                return new Double(Double.POSITIVE_INFINITY);
            } else {
                return new Double(Double.NEGATIVE_INFINITY);
            }
        }

        if (isParseBigDecimal()) {
            BigDecimal bigDecimalResult = digitList.getBigDecimal();

            if (multiplier != 1) {
                try {
                    bigDecimalResult = bigDecimalResult.divide(getBigDecimalMultiplier());
                }
                catch (ArithmeticException e) {  // non-terminating decimal expansion
                    bigDecimalResult = bigDecimalResult.divide(getBigDecimalMultiplier(), roundingMode);
                }
            }

            if (!status[STATUS_POSITIVE]) {
                bigDecimalResult = bigDecimalResult.negate();
            }
            return bigDecimalResult;
        } else {
            boolean gotDouble = true;
            boolean gotLongMinimum = false;
            double  doubleResult = 0.0;
            long    longResult = 0;

            // Finally, have DigitList parse the digits into a value.
            if (digitList.fitsIntoLong(status[STATUS_POSITIVE], isParseIntegerOnly())) {
                gotDouble = false;
                longResult = digitList.getLong();
                if (longResult < 0) {  // got Long.MIN_VALUE
                    gotLongMinimum = true;
                }
            } else {
                doubleResult = digitList.getDouble();
            }

            // Divide by multiplier. We have to be careful here not to do
            // unneeded conversions between double and long.
            if (multiplier != 1) {
                if (gotDouble) {
                    doubleResult /= multiplier;
                } else {
                    // Avoid converting to double if we can
                    if (longResult % multiplier == 0) {
                        longResult /= multiplier;
                    } else {
                        doubleResult = ((double)longResult) / multiplier;
                        gotDouble = true;
                    }
                }
            }

            if (!status[STATUS_POSITIVE] && !gotLongMinimum) {
                doubleResult = -doubleResult;
                longResult = -longResult;
            }

            // At this point, if we divided the result by the multiplier, the
            // result may fit into a long.  We check for this case and return
            // a long if possible.
            // We must do this AFTER applying the negative (if appropriate)
            // in order to handle the case of LONG_MIN; otherwise, if we do
            // this with a positive value -LONG_MIN, the double is > 0, but
            // the long is < 0. We also must retain a double in the case of
            // -0.0, which will compare as == to a long 0 cast to a double
            // (bug 4162852).
            if (multiplier != 1 && gotDouble) {
                longResult = (long)doubleResult;
                gotDouble = ((doubleResult != (double)longResult) ||
                            (doubleResult == 0.0 && 1/doubleResult < 0.0)) &&
                            !isParseIntegerOnly();
            }

            return gotDouble ?
                (Number)new Double(doubleResult) : (Number)new Long(longResult);
        }
    }

    /**
     * Return a BigInteger multiplier.
     */
    private BigInteger getBigIntegerMultiplier() {
        if (bigIntegerMultiplier == null) {
            bigIntegerMultiplier = BigInteger.valueOf(multiplier);
        }
        return bigIntegerMultiplier;
    }
    private transient BigInteger bigIntegerMultiplier;

    /**
     * Return a BigDecimal multiplier.
     */
    private BigDecimal getBigDecimalMultiplier() {
        if (bigDecimalMultiplier == null) {
            bigDecimalMultiplier = new BigDecimal(multiplier);
        }
        return bigDecimalMultiplier;
    }
    private transient BigDecimal bigDecimalMultiplier;

    private static final int STATUS_INFINITE = 0;
    private static final int STATUS_POSITIVE = 1;
    private static final int STATUS_LENGTH   = 2;

    /**
     * Parse the given text into a number.  The text is parsed beginning at
     * parsePosition, until an unparseable character is seen.
     * @param text The string to parse.
     * @param parsePosition The position at which to being parsing.  Upon
     * return, the first unparseable character.
     * @param digits The DigitList to set to the parsed value.
     * @param isExponent If true, parse an exponent.  This means no
     * infinite values and integer only.
     * @param status Upon return contains boolean status flags indicating
     * whether the value was infinite and whether it was positive.
     */
    private final boolean subparse(String text, ParsePosition parsePosition,
                   String positivePrefix, String negativePrefix,
                   DigitList digits, boolean isExponent,
                   boolean status[]) {
        int position = parsePosition.index;
        int oldStart = parsePosition.index;
        int backup;
        boolean gotPositive, gotNegative;

        // check for positivePrefix; take longest
        gotPositive = text.regionMatches(position, positivePrefix, 0,
                                         positivePrefix.length());
        gotNegative = text.regionMatches(position, negativePrefix, 0,
                                         negativePrefix.length());

        if (gotPositive && gotNegative) {
            if (positivePrefix.length() > negativePrefix.length()) {
                gotNegative = false;
            } else if (positivePrefix.length() < negativePrefix.length()) {
                gotPositive = false;
            }
        }

        if (gotPositive) {
            position += positivePrefix.length();
        } else if (gotNegative) {
            position += negativePrefix.length();
        } else {
            parsePosition.errorIndex = position;
            return false;
        }

        // process digits or Inf, find decimal position
        status[STATUS_INFINITE] = false;
        if (!isExponent && text.regionMatches(position,symbols.getInfinity(),0,
                          symbols.getInfinity().length())) {
            position += symbols.getInfinity().length();
            status[STATUS_INFINITE] = true;
        } else {
            // We now have a string of digits, possibly with grouping symbols,
            // and decimal points.  We want to process these into a DigitList.
            // We don't want to put a bunch of leading zeros into the DigitList
            // though, so we keep track of the location of the decimal point,
            // put only significant digits into the DigitList, and adjust the
            // exponent as needed.

            digits.decimalAt = digits.count = 0;
            char zero = symbols.getZeroDigit();
            char decimal = isCurrencyFormat ?
                symbols.getMonetaryDecimalSeparator() :
                symbols.getDecimalSeparator();
            char grouping = symbols.getGroupingSeparator();
            String exponentString = symbols.getExponentSeparator();
            boolean sawDecimal = false;
            boolean sawExponent = false;
            boolean sawDigit = false;
            int exponent = 0; // Set to the exponent value, if any

            // We have to track digitCount ourselves, because digits.count will
            // pin when the maximum allowable digits is reached.
            int digitCount = 0;

            backup = -1;
            for (; position < text.length(); ++position) {
                char ch = text.charAt(position);

                /* We recognize all digit ranges, not only the Latin digit range
                 * '0'..'9'.  We do so by using the Character.digit() method,
                 * which converts a valid Unicode digit to the range 0..9.
                 *
                 * The character 'ch' may be a digit.  If so, place its value
                 * from 0 to 9 in 'digit'.  First try using the locale digit,
                 * which may or MAY NOT be a standard Unicode digit range.  If
                 * this fails, try using the standard Unicode digit ranges by
                 * calling Character.digit().  If this also fails, digit will
                 * have a value outside the range 0..9.
                 */
                int digit = ch - zero;
                if (digit < 0 || digit > 9) {
                    digit = Character.digit(ch, 10);
                }

                if (digit == 0) {
                    // Cancel out backup setting (see grouping handler below)
                    backup = -1; // Do this BEFORE continue statement below!!!
                    sawDigit = true;

                    // Handle leading zeros
                    if (digits.count == 0) {
                        // Ignore leading zeros in integer part of number.
                        if (!sawDecimal) {
                            continue;
                        }

                        // If we have seen the decimal, but no significant
                        // digits yet, then we account for leading zeros by
                        // decrementing the digits.decimalAt into negative
                        // values.
                        --digits.decimalAt;
                    } else {
                        ++digitCount;
                        digits.append((char)(digit + '0'));
                    }
                } else if (digit > 0 && digit <= 9) { // [sic] digit==0 handled above
                    sawDigit = true;
                    ++digitCount;
                    digits.append((char)(digit + '0'));

                    // Cancel out backup setting (see grouping handler below)
                    backup = -1;
                } else if (!isExponent && ch == decimal) {
                    // If we're only parsing integers, or if we ALREADY saw the
                    // decimal, then don't parse this one.
                    if (isParseIntegerOnly() || sawDecimal) {
                        break;
                    }
                    digits.decimalAt = digitCount; // Not digits.count!
                    sawDecimal = true;
                } else if (!isExponent && ch == grouping && isGroupingUsed()) {
                    if (sawDecimal) {
                        break;
                    }
                    // Ignore grouping characters, if we are using them, but
                    // require that they be followed by a digit.  Otherwise
                    // we backup and reprocess them.
                    backup = position;
                } else if (!isExponent && text.regionMatches(position, exponentString, 0, exponentString.length())
                             && !sawExponent) {
                    // Process the exponent by recursively calling this method.
                     ParsePosition pos = new ParsePosition(position + exponentString.length());
                    boolean[] stat = new boolean[STATUS_LENGTH];
                    DigitList exponentDigits = new DigitList();

                    if (subparse(text, pos, "", Character.toString(symbols.getMinusSign()), exponentDigits, true, stat) &&
                        exponentDigits.fitsIntoLong(stat[STATUS_POSITIVE], true)) {
                        position = pos.index; // Advance past the exponent
                        exponent = (int)exponentDigits.getLong();
                        if (!stat[STATUS_POSITIVE]) {
                            exponent = -exponent;
                        }
                        sawExponent = true;
                    }
                    break; // Whether we fail or succeed, we exit this loop
                } else {
                    break;
                }
            }

            if (backup != -1) {
                position = backup;
            }

            // If there was no decimal point we have an integer
            if (!sawDecimal) {
                digits.decimalAt = digitCount; // Not digits.count!
            }

            // Adjust for exponent, if any
            digits.decimalAt += exponent;

            // If none of the text string was recognized.  For example, parse
            // "x" with pattern "#0.00" (return index and error index both 0)
            // parse "$" with pattern "$#0.00". (return index 0 and error
            // index 1).
            if (!sawDigit && digitCount == 0) {
                parsePosition.index = oldStart;
                parsePosition.errorIndex = oldStart;
                return false;
            }
        }

        // check for suffix
        if (!isExponent) {
            if (gotPositive) {
                gotPositive = text.regionMatches(position,positiveSuffix,0,
                                                 positiveSuffix.length());
            }
            if (gotNegative) {
                gotNegative = text.regionMatches(position,negativeSuffix,0,
                                                 negativeSuffix.length());
            }

        // if both match, take longest
        if (gotPositive && gotNegative) {
            if (positiveSuffix.length() > negativeSuffix.length()) {
                gotNegative = false;
            } else if (positiveSuffix.length() < negativeSuffix.length()) {
                gotPositive = false;
            }
        }

        // fail if neither or both
        if (gotPositive == gotNegative) {
            parsePosition.errorIndex = position;
            return false;
        }

        parsePosition.index = position +
            (gotPositive ? positiveSuffix.length() : negativeSuffix.length()); // mark success!
        } else {
            parsePosition.index = position;
        }

        status[STATUS_POSITIVE] = gotPositive;
        if (parsePosition.index == oldStart) {
            parsePosition.errorIndex = position;
            return false;
        }
        return true;
    }

    /**
     * Returns a copy of the decimal format symbols, which is generally not
     * changed by the programmer or user.
     * @return a copy of the desired DecimalFormatSymbols
     * @see java.text.DecimalFormatSymbols
     */
    public DecimalFormatSymbols getDecimalFormatSymbols() {
        try {
            // don't allow multiple references
            return (DecimalFormatSymbols) symbols.clone();
        } catch (Exception foo) {
            return null; // should never happen
        }
    }


    /**
     * Sets the decimal format symbols, which is generally not changed
     * by the programmer or user.
     * @param newSymbols desired DecimalFormatSymbols
     * @see java.text.DecimalFormatSymbols
     */
    public void setDecimalFormatSymbols(DecimalFormatSymbols newSymbols) {
        try {
            // don't allow multiple references
            symbols = (DecimalFormatSymbols) newSymbols.clone();
            expandAffixes();
            fastPathCheckNeeded = true;
        } catch (Exception foo) {
            // should never happen
        }
    }

    /**
     * Get the positive prefix.
     * <P>Examples: +123, $123, sFr123
     *
     * @return the positive prefix
     */
    public String getPositivePrefix () {
        return positivePrefix;
    }

    /**
     * Set the positive prefix.
     * <P>Examples: +123, $123, sFr123
     *
     * @param newValue the new positive prefix
     */
    public void setPositivePrefix (String newValue) {
        positivePrefix = newValue;
        posPrefixPattern = null;
        positivePrefixFieldPositions = null;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns the FieldPositions of the fields in the prefix used for
     * positive numbers. This is not used if the user has explicitly set
     * a positive prefix via <code>setPositivePrefix</code>. This is
     * lazily created.
     *
     * @return FieldPositions in positive prefix
     */
    private FieldPosition[] getPositivePrefixFieldPositions() {
        if (positivePrefixFieldPositions == null) {
            if (posPrefixPattern != null) {
                positivePrefixFieldPositions = expandAffix(posPrefixPattern);
            } else {
                positivePrefixFieldPositions = EmptyFieldPositionArray;
            }
        }
        return positivePrefixFieldPositions;
    }

    /**
     * Get the negative prefix.
     * <P>Examples: -123, ($123) (with negative suffix), sFr-123
     *
     * @return the negative prefix
     */
    public String getNegativePrefix () {
        return negativePrefix;
    }

    /**
     * Set the negative prefix.
     * <P>Examples: -123, ($123) (with negative suffix), sFr-123
     *
     * @param newValue the new negative prefix
     */
    public void setNegativePrefix (String newValue) {
        negativePrefix = newValue;
        negPrefixPattern = null;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns the FieldPositions of the fields in the prefix used for
     * negative numbers. This is not used if the user has explicitly set
     * a negative prefix via <code>setNegativePrefix</code>. This is
     * lazily created.
     *
     * @return FieldPositions in positive prefix
     */
    private FieldPosition[] getNegativePrefixFieldPositions() {
        if (negativePrefixFieldPositions == null) {
            if (negPrefixPattern != null) {
                negativePrefixFieldPositions = expandAffix(negPrefixPattern);
            } else {
                negativePrefixFieldPositions = EmptyFieldPositionArray;
            }
        }
        return negativePrefixFieldPositions;
    }

    /**
     * Get the positive suffix.
     * <P>Example: 123%
     *
     * @return the positive suffix
     */
    public String getPositiveSuffix () {
        return positiveSuffix;
    }

    /**
     * Set the positive suffix.
     * <P>Example: 123%
     *
     * @param newValue the new positive suffix
     */
    public void setPositiveSuffix (String newValue) {
        positiveSuffix = newValue;
        posSuffixPattern = null;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns the FieldPositions of the fields in the suffix used for
     * positive numbers. This is not used if the user has explicitly set
     * a positive suffix via <code>setPositiveSuffix</code>. This is
     * lazily created.
     *
     * @return FieldPositions in positive prefix
     */
    private FieldPosition[] getPositiveSuffixFieldPositions() {
        if (positiveSuffixFieldPositions == null) {
            if (posSuffixPattern != null) {
                positiveSuffixFieldPositions = expandAffix(posSuffixPattern);
            } else {
                positiveSuffixFieldPositions = EmptyFieldPositionArray;
            }
        }
        return positiveSuffixFieldPositions;
    }

    /**
     * Get the negative suffix.
     * <P>Examples: -123%, ($123) (with positive suffixes)
     *
     * @return the negative suffix
     */
    public String getNegativeSuffix () {
        return negativeSuffix;
    }

    /**
     * Set the negative suffix.
     * <P>Examples: 123%
     *
     * @param newValue the new negative suffix
     */
    public void setNegativeSuffix (String newValue) {
        negativeSuffix = newValue;
        negSuffixPattern = null;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns the FieldPositions of the fields in the suffix used for
     * negative numbers. This is not used if the user has explicitly set
     * a negative suffix via <code>setNegativeSuffix</code>. This is
     * lazily created.
     *
     * @return FieldPositions in positive prefix
     */
    private FieldPosition[] getNegativeSuffixFieldPositions() {
        if (negativeSuffixFieldPositions == null) {
            if (negSuffixPattern != null) {
                negativeSuffixFieldPositions = expandAffix(negSuffixPattern);
            } else {
                negativeSuffixFieldPositions = EmptyFieldPositionArray;
            }
        }
        return negativeSuffixFieldPositions;
    }

    /**
     * Gets the multiplier for use in percent, per mille, and similar
     * formats.
     *
     * @return the multiplier
     * @see #setMultiplier(int)
     */
    public int getMultiplier () {
        return multiplier;
    }

    /**
     * Sets the multiplier for use in percent, per mille, and similar
     * formats.
     * For a percent format, set the multiplier to 100 and the suffixes to
     * have '%' (for Arabic, use the Arabic percent sign).
     * For a per mille format, set the multiplier to 1000 and the suffixes to
     * have '&#92;u2030'.
     *
     * <P>Example: with multiplier 100, 1.23 is formatted as "123", and
     * "123" is parsed into 1.23.
     *
     * @param newValue the new multiplier
     * @see #getMultiplier
     */
    public void setMultiplier (int newValue) {
        multiplier = newValue;
        bigDecimalMultiplier = null;
        bigIntegerMultiplier = null;
        fastPathCheckNeeded = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGroupingUsed(boolean newValue) {
        super.setGroupingUsed(newValue);
        fastPathCheckNeeded = true;
    }

    /**
     * Return the grouping size. Grouping size is the number of digits between
     * grouping separators in the integer portion of a number.  For example,
     * in the number "123,456.78", the grouping size is 3.
     *
     * @return the grouping size
     * @see #setGroupingSize
     * @see java.text.NumberFormat#isGroupingUsed
     * @see java.text.DecimalFormatSymbols#getGroupingSeparator
     */
    public int getGroupingSize () {
        return groupingSize;
    }

    /**
     * Set the grouping size. Grouping size is the number of digits between
     * grouping separators in the integer portion of a number.  For example,
     * in the number "123,456.78", the grouping size is 3.
     * <br>
     * The value passed in is converted to a byte, which may lose information.
     *
     * @param newValue the new grouping size
     * @see #getGroupingSize
     * @see java.text.NumberFormat#setGroupingUsed
     * @see java.text.DecimalFormatSymbols#setGroupingSeparator
     */
    public void setGroupingSize (int newValue) {
        groupingSize = (byte)newValue;
        fastPathCheckNeeded = true;
    }

    /**
     * Allows you to get the behavior of the decimal separator with integers.
     * (The decimal separator will always appear with decimals.)
     * <P>Example: Decimal ON: 12345 &rarr; 12345.; OFF: 12345 &rarr; 12345
     *
     * @return {@code true} if the decimal separator is always shown;
     *         {@code false} otherwise
     */
    public boolean isDecimalSeparatorAlwaysShown() {
        return decimalSeparatorAlwaysShown;
    }

    /**
     * Allows you to set the behavior of the decimal separator with integers.
     * (The decimal separator will always appear with decimals.)
     * <P>Example: Decimal ON: 12345 &rarr; 12345.; OFF: 12345 &rarr; 12345
     *
     * @param newValue {@code true} if the decimal separator is always shown;
     *                 {@code false} otherwise
     */
    public void setDecimalSeparatorAlwaysShown(boolean newValue) {
        decimalSeparatorAlwaysShown = newValue;
        fastPathCheckNeeded = true;
    }

    /**
     * Returns whether the {@link #parse(java.lang.String, java.text.ParsePosition)}
     * method returns <code>BigDecimal</code>. The default value is false.
     *
     * @return {@code true} if the parse method returns BigDecimal;
     *         {@code false} otherwise
     * @see #setParseBigDecimal
     * @since 1.5
     */
    public boolean isParseBigDecimal() {
        return parseBigDecimal;
    }

    /**
     * Sets whether the {@link #parse(java.lang.String, java.text.ParsePosition)}
     * method returns <code>BigDecimal</code>.
     *
     * @param newValue {@code true} if the parse method returns BigDecimal;
     *                 {@code false} otherwise
     * @see #isParseBigDecimal
     * @since 1.5
     */
    public void setParseBigDecimal(boolean newValue) {
        parseBigDecimal = newValue;
    }

    /**
     * Standard override; no change in semantics.
     */
    @Override
    public Object clone() {
        DecimalFormat other = (DecimalFormat) super.clone();
        other.symbols = (DecimalFormatSymbols) symbols.clone();
        other.digitList = (DigitList) digitList.clone();

        // Fast-path is almost stateless algorithm. The only logical state is the
        // isFastPath flag. In addition fastPathCheckNeeded is a sentinel flag
        // that forces recalculation of all fast-path fields when set to true.
        //
        // There is thus no need to clone all the fast-path fields.
        // We just only need to set fastPathCheckNeeded to true when cloning,
        // and init fastPathData to null as if it were a truly new instance.
        // Every fast-path field will be recalculated (only once) at next usage of
        // fast-path algorithm.
        other.fastPathCheckNeeded = true;
        other.isFastPath = false;
        other.fastPathData = null;

        return other;
    }

    /**
     * Overrides equals
     */
    @Override
    public boolean equals(Object obj)
    {
        if (obj == null)
            return false;
        if (!super.equals(obj))
            return false; // super does class check
        DecimalFormat other = (DecimalFormat) obj;
        return ((posPrefixPattern == other.posPrefixPattern &&
                 positivePrefix.equals(other.positivePrefix))
                || (posPrefixPattern != null &&
                    posPrefixPattern.equals(other.posPrefixPattern)))
            && ((posSuffixPattern == other.posSuffixPattern &&
                 positiveSuffix.equals(other.positiveSuffix))
                || (posSuffixPattern != null &&
                    posSuffixPattern.equals(other.posSuffixPattern)))
            && ((negPrefixPattern == other.negPrefixPattern &&
                 negativePrefix.equals(other.negativePrefix))
                || (negPrefixPattern != null &&
                    negPrefixPattern.equals(other.negPrefixPattern)))
            && ((negSuffixPattern == other.negSuffixPattern &&
                 negativeSuffix.equals(other.negativeSuffix))
                || (negSuffixPattern != null &&
                    negSuffixPattern.equals(other.negSuffixPattern)))
            && multiplier == other.multiplier
            && groupingSize == other.groupingSize
            && decimalSeparatorAlwaysShown == other.decimalSeparatorAlwaysShown
            && parseBigDecimal == other.parseBigDecimal
            && useExponentialNotation == other.useExponentialNotation
            && (!useExponentialNotation ||
                minExponentDigits == other.minExponentDigits)
            && maximumIntegerDigits == other.maximumIntegerDigits
            && minimumIntegerDigits == other.minimumIntegerDigits
            && maximumFractionDigits == other.maximumFractionDigits
            && minimumFractionDigits == other.minimumFractionDigits
            && roundingMode == other.roundingMode
            && symbols.equals(other.symbols);
    }

    /**
     * Overrides hashCode
     */
    @Override
    public int hashCode() {
        return super.hashCode() * 37 + positivePrefix.hashCode();
        // just enough fields for a reasonable distribution
    }

    /**
     * Synthesizes a pattern string that represents the current state
     * of this Format object.
     *
     * @return a pattern string
     * @see #applyPattern
     */
    public String toPattern() {
        return toPattern( false );
    }

    /**
     * Synthesizes a localized pattern string that represents the current
     * state of this Format object.
     *
     * @return a localized pattern string
     * @see #applyPattern
     */
    public String toLocalizedPattern() {
        return toPattern( true );
    }

    /**
     * Expand the affix pattern strings into the expanded affix strings.  If any
     * affix pattern string is null, do not expand it.  This method should be
     * called any time the symbols or the affix patterns change in order to keep
     * the expanded affix strings up to date.
     */
    private void expandAffixes() {
        // Reuse one StringBuffer for better performance
        StringBuffer buffer = new StringBuffer();
        if (posPrefixPattern != null) {
            positivePrefix = expandAffix(posPrefixPattern, buffer);
            positivePrefixFieldPositions = null;
        }
        if (posSuffixPattern != null) {
            positiveSuffix = expandAffix(posSuffixPattern, buffer);
            positiveSuffixFieldPositions = null;
        }
        if (negPrefixPattern != null) {
            negativePrefix = expandAffix(negPrefixPattern, buffer);
            negativePrefixFieldPositions = null;
        }
        if (negSuffixPattern != null) {
            negativeSuffix = expandAffix(negSuffixPattern, buffer);
            negativeSuffixFieldPositions = null;
        }
    }

    /**
     * Expand an affix pattern into an affix string.  All characters in the
     * pattern are literal unless prefixed by QUOTE.  The following characters
     * after QUOTE are recognized: PATTERN_PERCENT, PATTERN_PER_MILLE,
     * PATTERN_MINUS, and CURRENCY_SIGN.  If CURRENCY_SIGN is doubled (QUOTE +
     * CURRENCY_SIGN + CURRENCY_SIGN), it is interpreted as an ISO 4217
     * currency code.  Any other character after a QUOTE represents itself.
     * QUOTE must be followed by another character; QUOTE may not occur by
     * itself at the end of the pattern.
     *
     * @param pattern the non-null, possibly empty pattern
     * @param buffer a scratch StringBuffer; its contents will be lost
     * @return the expanded equivalent of pattern
     */
    private String expandAffix(String pattern, StringBuffer buffer) {
        buffer.setLength(0);
        for (int i=0; i<pattern.length(); ) {
            char c = pattern.charAt(i++);
            if (c == QUOTE) {
                c = pattern.charAt(i++);
                switch (c) {
                case CURRENCY_SIGN:
                    if (i<pattern.length() &&
                        pattern.charAt(i) == CURRENCY_SIGN) {
                        ++i;
                        buffer.append(symbols.getInternationalCurrencySymbol());
                    } else {
                        buffer.append(symbols.getCurrencySymbol());
                    }
                    continue;
                case PATTERN_PERCENT:
                    c = symbols.getPercent();
                    break;
                case PATTERN_PER_MILLE:
                    c = symbols.getPerMill();
                    break;
                case PATTERN_MINUS:
                    c = symbols.getMinusSign();
                    break;
                }
            }
            buffer.append(c);
        }
        return buffer.toString();
    }

    /**
     * Expand an affix pattern into an array of FieldPositions describing
     * how the pattern would be expanded.
     * All characters in the
     * pattern are literal unless prefixed by QUOTE.  The following characters
     * after QUOTE are recognized: PATTERN_PERCENT, PATTERN_PER_MILLE,
     * PATTERN_MINUS, and CURRENCY_SIGN.  If CURRENCY_SIGN is doubled (QUOTE +
     * CURRENCY_SIGN + CURRENCY_SIGN), it is interpreted as an ISO 4217
     * currency code.  Any other character after a QUOTE represents itself.
     * QUOTE must be followed by another character; QUOTE may not occur by
     * itself at the end of the pattern.
     *
     * @param pattern the non-null, possibly empty pattern
     * @return FieldPosition array of the resulting fields.
     */
    private FieldPosition[] expandAffix(String pattern) {
        ArrayList<FieldPosition> positions = null;
        int stringIndex = 0;
        for (int i=0; i<pattern.length(); ) {
            char c = pattern.charAt(i++);
            if (c == QUOTE) {
                int field = -1;
                Format.Field fieldID = null;
                c = pattern.charAt(i++);
                switch (c) {
                case CURRENCY_SIGN:
                    String string;
                    if (i<pattern.length() &&
                        pattern.charAt(i) == CURRENCY_SIGN) {
                        ++i;
                        string = symbols.getInternationalCurrencySymbol();
                    } else {
                        string = symbols.getCurrencySymbol();
                    }
                    if (string.length() > 0) {
                        if (positions == null) {
                            positions = new ArrayList<>(2);
                        }
                        FieldPosition fp = new FieldPosition(Field.CURRENCY);
                        fp.setBeginIndex(stringIndex);
                        fp.setEndIndex(stringIndex + string.length());
                        positions.add(fp);
                        stringIndex += string.length();
                    }
                    continue;
                case PATTERN_PERCENT:
                    c = symbols.getPercent();
                    field = -1;
                    fieldID = Field.PERCENT;
                    break;
                case PATTERN_PER_MILLE:
                    c = symbols.getPerMill();
                    field = -1;
                    fieldID = Field.PERMILLE;
                    break;
                case PATTERN_MINUS:
                    c = symbols.getMinusSign();
                    field = -1;
                    fieldID = Field.SIGN;
                    break;
                }
                if (fieldID != null) {
                    if (positions == null) {
                        positions = new ArrayList<>(2);
                    }
                    FieldPosition fp = new FieldPosition(fieldID, field);
                    fp.setBeginIndex(stringIndex);
                    fp.setEndIndex(stringIndex + 1);
                    positions.add(fp);
                }
            }
            stringIndex++;
        }
        if (positions != null) {
            return positions.toArray(EmptyFieldPositionArray);
        }
        return EmptyFieldPositionArray;
    }

    /**
     * Appends an affix pattern to the given StringBuffer, quoting special
     * characters as needed.  Uses the internal affix pattern, if that exists,
     * or the literal affix, if the internal affix pattern is null.  The
     * appended string will generate the same affix pattern (or literal affix)
     * when passed to toPattern().
     *
     * @param buffer the affix string is appended to this
     * @param affixPattern a pattern such as posPrefixPattern; may be null
     * @param expAffix a corresponding expanded affix, such as positivePrefix.
     * Ignored unless affixPattern is null.  If affixPattern is null, then
     * expAffix is appended as a literal affix.
     * @param localized true if the appended pattern should contain localized
     * pattern characters; otherwise, non-localized pattern chars are appended
     */
    private void appendAffix(StringBuffer buffer, String affixPattern,
                             String expAffix, boolean localized) {
        if (affixPattern == null) {
            appendAffix(buffer, expAffix, localized);
        } else {
            int i;
            for (int pos=0; pos<affixPattern.length(); pos=i) {
                i = affixPattern.indexOf(QUOTE, pos);
                if (i < 0) {
                    appendAffix(buffer, affixPattern.substring(pos), localized);
                    break;
                }
                if (i > pos) {
                    appendAffix(buffer, affixPattern.substring(pos, i), localized);
                }
                char c = affixPattern.charAt(++i);
                ++i;
                if (c == QUOTE) {
                    buffer.append(c);
                    // Fall through and append another QUOTE below
                } else if (c == CURRENCY_SIGN &&
                           i<affixPattern.length() &&
                           affixPattern.charAt(i) == CURRENCY_SIGN) {
                    ++i;
                    buffer.append(c);
                    // Fall through and append another CURRENCY_SIGN below
                } else if (localized) {
                    switch (c) {
                    case PATTERN_PERCENT:
                        c = symbols.getPercent();
                        break;
                    case PATTERN_PER_MILLE:
                        c = symbols.getPerMill();
                        break;
                    case PATTERN_MINUS:
                        c = symbols.getMinusSign();
                        break;
                    }
                }
                buffer.append(c);
            }
        }
    }

    /**
     * Append an affix to the given StringBuffer, using quotes if
     * there are special characters.  Single quotes themselves must be
     * escaped in either case.
     */
    private void appendAffix(StringBuffer buffer, String affix, boolean localized) {
        boolean needQuote;
        if (localized) {
            needQuote = affix.indexOf(symbols.getZeroDigit()) >= 0
                || affix.indexOf(symbols.getGroupingSeparator()) >= 0
                || affix.indexOf(symbols.getDecimalSeparator()) >= 0
                || affix.indexOf(symbols.getPercent()) >= 0
                || affix.indexOf(symbols.getPerMill()) >= 0
                || affix.indexOf(symbols.getDigit()) >= 0
                || affix.indexOf(symbols.getPatternSeparator()) >= 0
                || affix.indexOf(symbols.getMinusSign()) >= 0
                || affix.indexOf(CURRENCY_SIGN) >= 0;
        } else {
            needQuote = affix.indexOf(PATTERN_ZERO_DIGIT) >= 0
                || affix.indexOf(PATTERN_GROUPING_SEPARATOR) >= 0
                || affix.indexOf(PATTERN_DECIMAL_SEPARATOR) >= 0
                || affix.indexOf(PATTERN_PERCENT) >= 0
                || affix.indexOf(PATTERN_PER_MILLE) >= 0
                || affix.indexOf(PATTERN_DIGIT) >= 0
                || affix.indexOf(PATTERN_SEPARATOR) >= 0
                || affix.indexOf(PATTERN_MINUS) >= 0
                || affix.indexOf(CURRENCY_SIGN) >= 0;
        }
        if (needQuote) buffer.append('\'');
        if (affix.indexOf('\'') < 0) buffer.append(affix);
        else {
            for (int j=0; j<affix.length(); ++j) {
                char c = affix.charAt(j);
                buffer.append(c);
                if (c == '\'') buffer.append(c);
            }
        }
        if (needQuote) buffer.append('\'');
    }

    /**
     * Does the real work of generating a pattern.  */
    private String toPattern(boolean localized) {
        StringBuffer result = new StringBuffer();
        for (int j = 1; j >= 0; --j) {
            if (j == 1)
                appendAffix(result, posPrefixPattern, positivePrefix, localized);
            else appendAffix(result, negPrefixPattern, negativePrefix, localized);
            int i;
            int digitCount = useExponentialNotation
                        ? getMaximumIntegerDigits()
                        : Math.max(groupingSize, getMinimumIntegerDigits())+1;
            for (i = digitCount; i > 0; --i) {
                if (i != digitCount && isGroupingUsed() && groupingSize != 0 &&
                    i % groupingSize == 0) {
                    result.append(localized ? symbols.getGroupingSeparator() :
                                  PATTERN_GROUPING_SEPARATOR);
                }
                result.append(i <= getMinimumIntegerDigits()
                    ? (localized ? symbols.getZeroDigit() : PATTERN_ZERO_DIGIT)
                    : (localized ? symbols.getDigit() : PATTERN_DIGIT));
            }
            if (getMaximumFractionDigits() > 0 || decimalSeparatorAlwaysShown)
                result.append(localized ? symbols.getDecimalSeparator() :
                              PATTERN_DECIMAL_SEPARATOR);
            for (i = 0; i < getMaximumFractionDigits(); ++i) {
                if (i < getMinimumFractionDigits()) {
                    result.append(localized ? symbols.getZeroDigit() :
                                  PATTERN_ZERO_DIGIT);
                } else {
                    result.append(localized ? symbols.getDigit() :
                                  PATTERN_DIGIT);
                }
            }
        if (useExponentialNotation)
        {
            result.append(localized ? symbols.getExponentSeparator() :
                  PATTERN_EXPONENT);
        for (i=0; i<minExponentDigits; ++i)
                    result.append(localized ? symbols.getZeroDigit() :
                                  PATTERN_ZERO_DIGIT);
        }
            if (j == 1) {
                appendAffix(result, posSuffixPattern, positiveSuffix, localized);
                if ((negSuffixPattern == posSuffixPattern && // n == p == null
                     negativeSuffix.equals(positiveSuffix))
                    || (negSuffixPattern != null &&
                        negSuffixPattern.equals(posSuffixPattern))) {
                    if ((negPrefixPattern != null && posPrefixPattern != null &&
                         negPrefixPattern.equals("'-" + posPrefixPattern)) ||
                        (negPrefixPattern == posPrefixPattern && // n == p == null
                         negativePrefix.equals(symbols.getMinusSign() + positivePrefix)))
                        break;
                }
                result.append(localized ? symbols.getPatternSeparator() :
                              PATTERN_SEPARATOR);
            } else appendAffix(result, negSuffixPattern, negativeSuffix, localized);
        }
        return result.toString();
    }

    /**
     * Apply the given pattern to this Format object.  A pattern is a
     * short-hand specification for the various formatting properties.
     * These properties can also be changed individually through the
     * various setter methods.
     * <p>
     * There is no limit to integer digits set
     * by this routine, since that is the typical end-user desire;
     * use setMaximumInteger if you want to set a real value.
     * For negative numbers, use a second pattern, separated by a semicolon
     * <P>Example <code>"#,#00.0#"</code> &rarr; 1,234.56
     * <P>This means a minimum of 2 integer digits, 1 fraction digit, and
     * a maximum of 2 fraction digits.
     * <p>Example: <code>"#,#00.0#;(#,#00.0#)"</code> for negatives in
     * parentheses.
     * <p>In negative patterns, the minimum and maximum counts are ignored;
     * these are presumed to be set in the positive pattern.
     *
     * @param pattern a new pattern
     * @exception NullPointerException if <code>pattern</code> is null
     * @exception IllegalArgumentException if the given pattern is invalid.
     */
    public void applyPattern(String pattern) {
        applyPattern(pattern, false);
    }

    /**
     * Apply the given pattern to this Format object.  The pattern
     * is assumed to be in a localized notation. A pattern is a
     * short-hand specification for the various formatting properties.
     * These properties can also be changed individually through the
     * various setter methods.
     * <p>
     * There is no limit to integer digits set
     * by this routine, since that is the typical end-user desire;
     * use setMaximumInteger if you want to set a real value.
     * For negative numbers, use a second pattern, separated by a semicolon
     * <P>Example <code>"#,#00.0#"</code> &rarr; 1,234.56
     * <P>This means a minimum of 2 integer digits, 1 fraction digit, and
     * a maximum of 2 fraction digits.
     * <p>Example: <code>"#,#00.0#;(#,#00.0#)"</code> for negatives in
     * parentheses.
     * <p>In negative patterns, the minimum and maximum counts are ignored;
     * these are presumed to be set in the positive pattern.
     *
     * @param pattern a new pattern
     * @exception NullPointerException if <code>pattern</code> is null
     * @exception IllegalArgumentException if the given pattern is invalid.
     */
    public void applyLocalizedPattern(String pattern) {
        applyPattern(pattern, true);
    }

    /**
     * Does the real work of applying a pattern.
     */
    private void applyPattern(String pattern, boolean localized) {
        char zeroDigit         = PATTERN_ZERO_DIGIT;
        char groupingSeparator = PATTERN_GROUPING_SEPARATOR;
        char decimalSeparator  = PATTERN_DECIMAL_SEPARATOR;
        char percent           = PATTERN_PERCENT;
        char perMill           = PATTERN_PER_MILLE;
        char digit             = PATTERN_DIGIT;
        char separator         = PATTERN_SEPARATOR;
        String exponent          = PATTERN_EXPONENT;
        char minus             = PATTERN_MINUS;
        if (localized) {
            zeroDigit         = symbols.getZeroDigit();
            groupingSeparator = symbols.getGroupingSeparator();
            decimalSeparator  = symbols.getDecimalSeparator();
            percent           = symbols.getPercent();
            perMill           = symbols.getPerMill();
            digit             = symbols.getDigit();
            separator         = symbols.getPatternSeparator();
            exponent          = symbols.getExponentSeparator();
            minus             = symbols.getMinusSign();
        }
        boolean gotNegative = false;
        decimalSeparatorAlwaysShown = false;
        isCurrencyFormat = false;
        useExponentialNotation = false;

        // Two variables are used to record the subrange of the pattern
        // occupied by phase 1.  This is used during the processing of the
        // second pattern (the one representing negative numbers) to ensure
        // that no deviation exists in phase 1 between the two patterns.
        int phaseOneStart = 0;
        int phaseOneLength = 0;

        int start = 0;
        for (int j = 1; j >= 0 && start < pattern.length(); --j) {
            boolean inQuote = false;
            StringBuffer prefix = new StringBuffer();
            StringBuffer suffix = new StringBuffer();
            int decimalPos = -1;
            int multiplier = 1;
            int digitLeftCount = 0, zeroDigitCount = 0, digitRightCount = 0;
            byte groupingCount = -1;

            // The phase ranges from 0 to 2.  Phase 0 is the prefix.  Phase 1 is
            // the section of the pattern with digits, decimal separator,
            // grouping characters.  Phase 2 is the suffix.  In phases 0 and 2,
            // percent, per mille, and currency symbols are recognized and
            // translated.  The separation of the characters into phases is
            // strictly enforced; if phase 1 characters are to appear in the
            // suffix, for example, they must be quoted.
            int phase = 0;

            // The affix is either the prefix or the suffix.
            StringBuffer affix = prefix;

            for (int pos = start; pos < pattern.length(); ++pos) {
                char ch = pattern.charAt(pos);
                switch (phase) {
                case 0:
                case 2:
                    // Process the prefix / suffix characters
                    if (inQuote) {
                        // A quote within quotes indicates either the closing
                        // quote or two quotes, which is a quote literal. That
                        // is, we have the second quote in 'do' or 'don''t'.
                        if (ch == QUOTE) {
                            if ((pos+1) < pattern.length() &&
                                pattern.charAt(pos+1) == QUOTE) {
                                ++pos;
                                affix.append("''"); // 'don''t'
                            } else {
                                inQuote = false; // 'do'
                            }
                            continue;
                        }
                    } else {
                        // Process unquoted characters seen in prefix or suffix
                        // phase.
                        if (ch == digit ||
                            ch == zeroDigit ||
                            ch == groupingSeparator ||
                            ch == decimalSeparator) {
                            phase = 1;
                            if (j == 1) {
                                phaseOneStart = pos;
                            }
                            --pos; // Reprocess this character
                            continue;
                        } else if (ch == CURRENCY_SIGN) {
                            // Use lookahead to determine if the currency sign
                            // is doubled or not.
                            boolean doubled = (pos + 1) < pattern.length() &&
                                pattern.charAt(pos + 1) == CURRENCY_SIGN;
                            if (doubled) { // Skip over the doubled character
                             ++pos;
                            }
                            isCurrencyFormat = true;
                            affix.append(doubled ? "'\u00A4\u00A4" : "'\u00A4");
                            continue;
                        } else if (ch == QUOTE) {
                            // A quote outside quotes indicates either the
                            // opening quote or two quotes, which is a quote
                            // literal. That is, we have the first quote in 'do'
                            // or o''clock.
                            if (ch == QUOTE) {
                                if ((pos+1) < pattern.length() &&
                                    pattern.charAt(pos+1) == QUOTE) {
                                    ++pos;
                                    affix.append("''"); // o''clock
                                } else {
                                    inQuote = true; // 'do'
                                }
                                continue;
                            }
                        } else if (ch == separator) {
                            // Don't allow separators before we see digit
                            // characters of phase 1, and don't allow separators
                            // in the second pattern (j == 0).
                            if (phase == 0 || j == 0) {
                                throw new IllegalArgumentException("Unquoted special character '" +
                                    ch + "' in pattern \"" + pattern + '"');
                            }
                            start = pos + 1;
                            pos = pattern.length();
                            continue;
                        }

                        // Next handle characters which are appended directly.
                        else if (ch == percent) {
                            if (multiplier != 1) {
                                throw new IllegalArgumentException("Too many percent/per mille characters in pattern \"" +
                                    pattern + '"');
                            }
                            multiplier = 100;
                            affix.append("'%");
                            continue;
                        } else if (ch == perMill) {
                            if (multiplier != 1) {
                                throw new IllegalArgumentException("Too many percent/per mille characters in pattern \"" +
                                    pattern + '"');
                            }
                            multiplier = 1000;
                            affix.append("'\u2030");
                            continue;
                        } else if (ch == minus) {
                            affix.append("'-");
                            continue;
                        }
                    }
                    // Note that if we are within quotes, or if this is an
                    // unquoted, non-special character, then we usually fall
                    // through to here.
                    affix.append(ch);
                    break;

                case 1:
                    // Phase one must be identical in the two sub-patterns. We
                    // enforce this by doing a direct comparison. While
                    // processing the first sub-pattern, we just record its
                    // length. While processing the second, we compare
                    // characters.
                    if (j == 1) {
                        ++phaseOneLength;
                    } else {
                        if (--phaseOneLength == 0) {
                            phase = 2;
                            affix = suffix;
                        }
                        continue;
                    }

                    // Process the digits, decimal, and grouping characters. We
                    // record five pieces of information. We expect the digits
                    // to occur in the pattern ####0000.####, and we record the
                    // number of left digits, zero (central) digits, and right
                    // digits. The position of the last grouping character is
                    // recorded (should be somewhere within the first two blocks
                    // of characters), as is the position of the decimal point,
                    // if any (should be in the zero digits). If there is no
                    // decimal point, then there should be no right digits.
                    if (ch == digit) {
                        if (zeroDigitCount > 0) {
                            ++digitRightCount;
                        } else {
                            ++digitLeftCount;
                        }
                        if (groupingCount >= 0 && decimalPos < 0) {
                            ++groupingCount;
                        }
                    } else if (ch == zeroDigit) {
                        if (digitRightCount > 0) {
                            throw new IllegalArgumentException("Unexpected '0' in pattern \"" +
                                pattern + '"');
                        }
                        ++zeroDigitCount;
                        if (groupingCount >= 0 && decimalPos < 0) {
                            ++groupingCount;
                        }
                    } else if (ch == groupingSeparator) {
                        groupingCount = 0;
                    } else if (ch == decimalSeparator) {
                        if (decimalPos >= 0) {
                            throw new IllegalArgumentException("Multiple decimal separators in pattern \"" +
                                pattern + '"');
                        }
                        decimalPos = digitLeftCount + zeroDigitCount + digitRightCount;
                    } else if (pattern.regionMatches(pos, exponent, 0, exponent.length())){
                        if (useExponentialNotation) {
                            throw new IllegalArgumentException("Multiple exponential " +
                                "symbols in pattern \"" + pattern + '"');
                        }
                        useExponentialNotation = true;
                        minExponentDigits = 0;

                        // Use lookahead to parse out the exponential part
                        // of the pattern, then jump into phase 2.
                        pos = pos+exponent.length();
                         while (pos < pattern.length() &&
                               pattern.charAt(pos) == zeroDigit) {
                            ++minExponentDigits;
                            ++phaseOneLength;
                            ++pos;
                        }

                        if ((digitLeftCount + zeroDigitCount) < 1 ||
                            minExponentDigits < 1) {
                            throw new IllegalArgumentException("Malformed exponential " +
                                "pattern \"" + pattern + '"');
                        }

                        // Transition to phase 2
                        phase = 2;
                        affix = suffix;
                        --pos;
                        continue;
                    } else {
                        phase = 2;
                        affix = suffix;
                        --pos;
                        --phaseOneLength;
                        continue;
                    }
                    break;
                }
            }

            // Handle patterns with no '0' pattern character. These patterns
            // are legal, but must be interpreted.  "##.###" -> "#0.###".
            // ".###" -> ".0##".
            /* We allow patterns of the form "####" to produce a zeroDigitCount
             * of zero (got that?); although this seems like it might make it
             * possible for format() to produce empty strings, format() checks
             * for this condition and outputs a zero digit in this situation.
             * Having a zeroDigitCount of zero yields a minimum integer digits
             * of zero, which allows proper round-trip patterns.  That is, we
             * don't want "#" to become "#0" when toPattern() is called (even
             * though that's what it really is, semantically).
             */
            if (zeroDigitCount == 0 && digitLeftCount > 0 && decimalPos >= 0) {
                // Handle "###.###" and "###." and ".###"
                int n = decimalPos;
                if (n == 0) { // Handle ".###"
                    ++n;
                }
                digitRightCount = digitLeftCount - n;
                digitLeftCount = n - 1;
                zeroDigitCount = 1;
            }

            // Do syntax checking on the digits.
            if ((decimalPos < 0 && digitRightCount > 0) ||
                (decimalPos >= 0 && (decimalPos < digitLeftCount ||
                 decimalPos > (digitLeftCount + zeroDigitCount))) ||
                 groupingCount == 0 || inQuote) {
                throw new IllegalArgumentException("Malformed pattern \"" +
                    pattern + '"');
            }

            if (j == 1) {
                posPrefixPattern = prefix.toString();
                posSuffixPattern = suffix.toString();
                negPrefixPattern = posPrefixPattern;   // assume these for now
                negSuffixPattern = posSuffixPattern;
                int digitTotalCount = digitLeftCount + zeroDigitCount + digitRightCount;
                /* The effectiveDecimalPos is the position the decimal is at or
                 * would be at if there is no decimal. Note that if decimalPos<0,
                 * then digitTotalCount == digitLeftCount + zeroDigitCount.
                 */
                int effectiveDecimalPos = decimalPos >= 0 ?
                    decimalPos : digitTotalCount;
                setMinimumIntegerDigits(effectiveDecimalPos - digitLeftCount);
                setMaximumIntegerDigits(useExponentialNotation ?
                    digitLeftCount + getMinimumIntegerDigits() :
                    MAXIMUM_INTEGER_DIGITS);
                setMaximumFractionDigits(decimalPos >= 0 ?
                    (digitTotalCount - decimalPos) : 0);
                setMinimumFractionDigits(decimalPos >= 0 ?
                    (digitLeftCount + zeroDigitCount - decimalPos) : 0);
                setGroupingUsed(groupingCount > 0);
                this.groupingSize = (groupingCount > 0) ? groupingCount : 0;
                this.multiplier = multiplier;
                setDecimalSeparatorAlwaysShown(decimalPos == 0 ||
                    decimalPos == digitTotalCount);
            } else {
                negPrefixPattern = prefix.toString();
                negSuffixPattern = suffix.toString();
                gotNegative = true;
            }
        }

        if (pattern.length() == 0) {
            posPrefixPattern = posSuffixPattern = "";
            setMinimumIntegerDigits(0);
            setMaximumIntegerDigits(MAXIMUM_INTEGER_DIGITS);
            setMinimumFractionDigits(0);
            setMaximumFractionDigits(MAXIMUM_FRACTION_DIGITS);
        }

        // If there was no negative pattern, or if the negative pattern is
        // identical to the positive pattern, then prepend the minus sign to
        // the positive pattern to form the negative pattern.
        if (!gotNegative ||
            (negPrefixPattern.equals(posPrefixPattern)
             && negSuffixPattern.equals(posSuffixPattern))) {
            negSuffixPattern = posSuffixPattern;
            negPrefixPattern = "'-" + posPrefixPattern;
        }

        expandAffixes();
    }

    /**
     * Sets the maximum number of digits allowed in the integer portion of a
     * number.
     * For formatting numbers other than <code>BigInteger</code> and
     * <code>BigDecimal</code> objects, the lower of <code>newValue</code> and
     * 309 is used. Negative input values are replaced with 0.
     * @see NumberFormat#setMaximumIntegerDigits
     */
    @Override
    public void setMaximumIntegerDigits(int newValue) {
        maximumIntegerDigits = Math.min(Math.max(0, newValue), MAXIMUM_INTEGER_DIGITS);
        super.setMaximumIntegerDigits((maximumIntegerDigits > DOUBLE_INTEGER_DIGITS) ?
            DOUBLE_INTEGER_DIGITS : maximumIntegerDigits);
        if (minimumIntegerDigits > maximumIntegerDigits) {
            minimumIntegerDigits = maximumIntegerDigits;
            super.setMinimumIntegerDigits((minimumIntegerDigits > DOUBLE_INTEGER_DIGITS) ?
                DOUBLE_INTEGER_DIGITS : minimumIntegerDigits);
        }
        fastPathCheckNeeded = true;
    }

    /**
     * Sets the minimum number of digits allowed in the integer portion of a
     * number.
     * For formatting numbers other than <code>BigInteger</code> and
     * <code>BigDecimal</code> objects, the lower of <code>newValue</code> and
     * 309 is used. Negative input values are replaced with 0.
     * @see NumberFormat#setMinimumIntegerDigits
     */
    @Override
    public void setMinimumIntegerDigits(int newValue) {
        minimumIntegerDigits = Math.min(Math.max(0, newValue), MAXIMUM_INTEGER_DIGITS);
        super.setMinimumIntegerDigits((minimumIntegerDigits > DOUBLE_INTEGER_DIGITS) ?
            DOUBLE_INTEGER_DIGITS : minimumIntegerDigits);
        if (minimumIntegerDigits > maximumIntegerDigits) {
            maximumIntegerDigits = minimumIntegerDigits;
            super.setMaximumIntegerDigits((maximumIntegerDigits > DOUBLE_INTEGER_DIGITS) ?
                DOUBLE_INTEGER_DIGITS : maximumIntegerDigits);
        }
        fastPathCheckNeeded = true;
    }

    /**
     * Sets the maximum number of digits allowed in the fraction portion of a
     * number.
     * For formatting numbers other than <code>BigInteger</code> and
     * <code>BigDecimal</code> objects, the lower of <code>newValue</code> and
     * 340 is used. Negative input values are replaced with 0.
     * @see NumberFormat#setMaximumFractionDigits
     */
    @Override
    public void setMaximumFractionDigits(int newValue) {
        maximumFractionDigits = Math.min(Math.max(0, newValue), MAXIMUM_FRACTION_DIGITS);
        super.setMaximumFractionDigits((maximumFractionDigits > DOUBLE_FRACTION_DIGITS) ?
            DOUBLE_FRACTION_DIGITS : maximumFractionDigits);
        if (minimumFractionDigits > maximumFractionDigits) {
            minimumFractionDigits = maximumFractionDigits;
            super.setMinimumFractionDigits((minimumFractionDigits > DOUBLE_FRACTION_DIGITS) ?
                DOUBLE_FRACTION_DIGITS : minimumFractionDigits);
        }
        fastPathCheckNeeded = true;
    }

    /**
     * Sets the minimum number of digits allowed in the fraction portion of a
     * number.
     * For formatting numbers other than <code>BigInteger</code> and
     * <code>BigDecimal</code> objects, the lower of <code>newValue</code> and
     * 340 is used. Negative input values are replaced with 0.
     * @see NumberFormat#setMinimumFractionDigits
     */
    @Override
    public void setMinimumFractionDigits(int newValue) {
        minimumFractionDigits = Math.min(Math.max(0, newValue), MAXIMUM_FRACTION_DIGITS);
        super.setMinimumFractionDigits((minimumFractionDigits > DOUBLE_FRACTION_DIGITS) ?
            DOUBLE_FRACTION_DIGITS : minimumFractionDigits);
        if (minimumFractionDigits > maximumFractionDigits) {
            maximumFractionDigits = minimumFractionDigits;
            super.setMaximumFractionDigits((maximumFractionDigits > DOUBLE_FRACTION_DIGITS) ?
                DOUBLE_FRACTION_DIGITS : maximumFractionDigits);
        }
        fastPathCheckNeeded = true;
    }

    /**
     * Gets the maximum number of digits allowed in the integer portion of a
     * number.
     * For formatting numbers other than <code>BigInteger</code> and
     * <code>BigDecimal</code> objects, the lower of the return value and
     * 309 is used.
     * @see #setMaximumIntegerDigits
     */
    @Override
    public int getMaximumIntegerDigits() {
        return maximumIntegerDigits;
    }

    /**
     * Gets the minimum number of digits allowed in the integer portion of a
     * number.
     * For formatting numbers other than <code>BigInteger</code> and
     * <code>BigDecimal</code> objects, the lower of the return value and
     * 309 is used.
     * @see #setMinimumIntegerDigits
     */
    @Override
    public int getMinimumIntegerDigits() {
        return minimumIntegerDigits;
    }

    /**
     * Gets the maximum number of digits allowed in the fraction portion of a
     * number.
     * For formatting numbers other than <code>BigInteger</code> and
     * <code>BigDecimal</code> objects, the lower of the return value and
     * 340 is used.
     * @see #setMaximumFractionDigits
     */
    @Override
    public int getMaximumFractionDigits() {
        return maximumFractionDigits;
    }

    /**
     * Gets the minimum number of digits allowed in the fraction portion of a
     * number.
     * For formatting numbers other than <code>BigInteger</code> and
     * <code>BigDecimal</code> objects, the lower of the return value and
     * 340 is used.
     * @see #setMinimumFractionDigits
     */
    @Override
    public int getMinimumFractionDigits() {
        return minimumFractionDigits;
    }

    /**
     * Gets the currency used by this decimal format when formatting
     * currency values.
     * The currency is obtained by calling
     * {@link DecimalFormatSymbols#getCurrency DecimalFormatSymbols.getCurrency}
     * on this number format's symbols.
     *
     * @return the currency used by this decimal format, or <code>null</code>
     * @since 1.4
     */
    @Override
    public Currency getCurrency() {
        return symbols.getCurrency();
    }

    /**
     * Sets the currency used by this number format when formatting
     * currency values. This does not update the minimum or maximum
     * number of fraction digits used by the number format.
     * The currency is set by calling
     * {@link DecimalFormatSymbols#setCurrency DecimalFormatSymbols.setCurrency}
     * on this number format's symbols.
     *
     * @param currency the new currency to be used by this decimal format
     * @exception NullPointerException if <code>currency</code> is null
     * @since 1.4
     */
    @Override
    public void setCurrency(Currency currency) {
        if (currency != symbols.getCurrency()) {
            symbols.setCurrency(currency);
            if (isCurrencyFormat) {
                expandAffixes();
            }
        }
        fastPathCheckNeeded = true;
    }

    /**
     * Gets the {@link java.math.RoundingMode} used in this DecimalFormat.
     *
     * @return The <code>RoundingMode</code> used for this DecimalFormat.
     * @see #setRoundingMode(RoundingMode)
     * @since 1.6
     */
    @Override
    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    /**
     * Sets the {@link java.math.RoundingMode} used in this DecimalFormat.
     *
     * @param roundingMode The <code>RoundingMode</code> to be used
     * @see #getRoundingMode()
     * @exception NullPointerException if <code>roundingMode</code> is null.
     * @since 1.6
     */
    @Override
    public void setRoundingMode(RoundingMode roundingMode) {
        if (roundingMode == null) {
            throw new NullPointerException();
        }

        this.roundingMode = roundingMode;
        digitList.setRoundingMode(roundingMode);
        fastPathCheckNeeded = true;
    }

    /**
     * Reads the default serializable fields from the stream and performs
     * validations and adjustments for older serialized versions. The
     * validations and adjustments are:
     * <ol>
     * <li>
     * Verify that the superclass's digit count fields correctly reflect
     * the limits imposed on formatting numbers other than
     * <code>BigInteger</code> and <code>BigDecimal</code> objects. These
     * limits are stored in the superclass for serialization compatibility
     * with older versions, while the limits for <code>BigInteger</code> and
     * <code>BigDecimal</code> objects are kept in this class.
     * If, in the superclass, the minimum or maximum integer digit count is
     * larger than <code>DOUBLE_INTEGER_DIGITS</code> or if the minimum or
     * maximum fraction digit count is larger than
     * <code>DOUBLE_FRACTION_DIGITS</code>, then the stream data is invalid
     * and this method throws an <code>InvalidObjectException</code>.
     * <li>
     * If <code>serialVersionOnStream</code> is less than 4, initialize
     * <code>roundingMode</code> to {@link java.math.RoundingMode#HALF_EVEN
     * RoundingMode.HALF_EVEN}.  This field is new with version 4.
     * <li>
     * If <code>serialVersionOnStream</code> is less than 3, then call
     * the setters for the minimum and maximum integer and fraction digits with
     * the values of the corresponding superclass getters to initialize the
     * fields in this class. The fields in this class are new with version 3.
     * <li>
     * If <code>serialVersionOnStream</code> is less than 1, indicating that
     * the stream was written by JDK 1.1, initialize
     * <code>useExponentialNotation</code>
     * to false, since it was not present in JDK 1.1.
     * <li>
     * Set <code>serialVersionOnStream</code> to the maximum allowed value so
     * that default serialization will work properly if this object is streamed
     * out again.
     * </ol>
     *
     * <p>Stream versions older than 2 will not have the affix pattern variables
     * <code>posPrefixPattern</code> etc.  As a result, they will be initialized
     * to <code>null</code>, which means the affix strings will be taken as
     * literal values.  This is exactly what we want, since that corresponds to
     * the pre-version-2 behavior.
     */
    private void readObject(ObjectInputStream stream)
         throws IOException, ClassNotFoundException
    {
        stream.defaultReadObject();
        digitList = new DigitList();

        // We force complete fast-path reinitialization when the instance is
        // deserialized. See clone() comment on fastPathCheckNeeded.
        fastPathCheckNeeded = true;
        isFastPath = false;
        fastPathData = null;

        if (serialVersionOnStream < 4) {
            setRoundingMode(RoundingMode.HALF_EVEN);
        } else {
            setRoundingMode(getRoundingMode());
        }

        // We only need to check the maximum counts because NumberFormat
        // .readObject has already ensured that the maximum is greater than the
        // minimum count.
        if (super.getMaximumIntegerDigits() > DOUBLE_INTEGER_DIGITS ||
            super.getMaximumFractionDigits() > DOUBLE_FRACTION_DIGITS) {
            throw new InvalidObjectException("Digit count out of range");
        }
        if (serialVersionOnStream < 3) {
            setMaximumIntegerDigits(super.getMaximumIntegerDigits());
            setMinimumIntegerDigits(super.getMinimumIntegerDigits());
            setMaximumFractionDigits(super.getMaximumFractionDigits());
            setMinimumFractionDigits(super.getMinimumFractionDigits());
        }
        if (serialVersionOnStream < 1) {
            // Didn't have exponential fields
            useExponentialNotation = false;
        }
        serialVersionOnStream = currentSerialVersion;
    }

    //----------------------------------------------------------------------
    // INSTANCE VARIABLES
    //----------------------------------------------------------------------

    private transient DigitList digitList = new DigitList();

    /**
     * The symbol used as a prefix when formatting positive numbers, e.g. "+".
     *
     * @serial
     * @see #getPositivePrefix
     */
    private String  positivePrefix = "";

    /**
     * The symbol used as a suffix when formatting positive numbers.
     * This is often an empty string.
     *
     * @serial
     * @see #getPositiveSuffix
     */
    private String  positiveSuffix = "";

    /**
     * The symbol used as a prefix when formatting negative numbers, e.g. "-".
     *
     * @serial
     * @see #getNegativePrefix
     */
    private String  negativePrefix = "-";

    /**
     * The symbol used as a suffix when formatting negative numbers.
     * This is often an empty string.
     *
     * @serial
     * @see #getNegativeSuffix
     */
    private String  negativeSuffix = "";

    /**
     * The prefix pattern for non-negative numbers.  This variable corresponds
     * to <code>positivePrefix</code>.
     *
     * <p>This pattern is expanded by the method <code>expandAffix()</code> to
     * <code>positivePrefix</code> to update the latter to reflect changes in
     * <code>symbols</code>.  If this variable is <code>null</code> then
     * <code>positivePrefix</code> is taken as a literal value that does not
     * change when <code>symbols</code> changes.  This variable is always
     * <code>null</code> for <code>DecimalFormat</code> objects older than
     * stream version 2 restored from stream.
     *
     * @serial
     * @since 1.3
     */
    private String posPrefixPattern;

    /**
     * The suffix pattern for non-negative numbers.  This variable corresponds
     * to <code>positiveSuffix</code>.  This variable is analogous to
     * <code>posPrefixPattern</code>; see that variable for further
     * documentation.
     *
     * @serial
     * @since 1.3
     */
    private String posSuffixPattern;

    /**
     * The prefix pattern for negative numbers.  This variable corresponds
     * to <code>negativePrefix</code>.  This variable is analogous to
     * <code>posPrefixPattern</code>; see that variable for further
     * documentation.
     *
     * @serial
     * @since 1.3
     */
    private String negPrefixPattern;

    /**
     * The suffix pattern for negative numbers.  This variable corresponds
     * to <code>negativeSuffix</code>.  This variable is analogous to
     * <code>posPrefixPattern</code>; see that variable for further
     * documentation.
     *
     * @serial
     * @since 1.3
     */
    private String negSuffixPattern;

    /**
     * The multiplier for use in percent, per mille, etc.
     *
     * @serial
     * @see #getMultiplier
     */
    private int     multiplier = 1;

    /**
     * The number of digits between grouping separators in the integer
     * portion of a number.  Must be greater than 0 if
     * <code>NumberFormat.groupingUsed</code> is true.
     *
     * @serial
     * @see #getGroupingSize
     * @see java.text.NumberFormat#isGroupingUsed
     */
    private byte    groupingSize = 3;  // invariant, > 0 if useThousands

    /**
     * If true, forces the decimal separator to always appear in a formatted
     * number, even if the fractional part of the number is zero.
     *
     * @serial
     * @see #isDecimalSeparatorAlwaysShown
     */
    private boolean decimalSeparatorAlwaysShown = false;

    /**
     * If true, parse returns BigDecimal wherever possible.
     *
     * @serial
     * @see #isParseBigDecimal
     * @since 1.5
     */
    private boolean parseBigDecimal = false;


    /**
     * True if this object represents a currency format.  This determines
     * whether the monetary decimal separator is used instead of the normal one.
     */
    private transient boolean isCurrencyFormat = false;

    /**
     * The <code>DecimalFormatSymbols</code> object used by this format.
     * It contains the symbols used to format numbers, e.g. the grouping separator,
     * decimal separator, and so on.
     *
     * @serial
     * @see #setDecimalFormatSymbols
     * @see java.text.DecimalFormatSymbols
     */
    private DecimalFormatSymbols symbols = null; // LIU new DecimalFormatSymbols();

    /**
     * True to force the use of exponential (i.e. scientific) notation when formatting
     * numbers.
     *
     * @serial
     * @since 1.2
     */
    private boolean useExponentialNotation;  // Newly persistent in the Java 2 platform v.1.2

    /**
     * FieldPositions describing the positive prefix String. This is
     * lazily created. Use <code>getPositivePrefixFieldPositions</code>
     * when needed.
     */
    private transient FieldPosition[] positivePrefixFieldPositions;

    /**
     * FieldPositions describing the positive suffix String. This is
     * lazily created. Use <code>getPositiveSuffixFieldPositions</code>
     * when needed.
     */
    private transient FieldPosition[] positiveSuffixFieldPositions;

    /**
     * FieldPositions describing the negative prefix String. This is
     * lazily created. Use <code>getNegativePrefixFieldPositions</code>
     * when needed.
     */
    private transient FieldPosition[] negativePrefixFieldPositions;

    /**
     * FieldPositions describing the negative suffix String. This is
     * lazily created. Use <code>getNegativeSuffixFieldPositions</code>
     * when needed.
     */
    private transient FieldPosition[] negativeSuffixFieldPositions;

    /**
     * The minimum number of digits used to display the exponent when a number is
     * formatted in exponential notation.  This field is ignored if
     * <code>useExponentialNotation</code> is not true.
     *
     * @serial
     * @since 1.2
     */
    private byte    minExponentDigits;       // Newly persistent in the Java 2 platform v.1.2

    /**
     * The maximum number of digits allowed in the integer portion of a
     * <code>BigInteger</code> or <code>BigDecimal</code> number.
     * <code>maximumIntegerDigits</code> must be greater than or equal to
     * <code>minimumIntegerDigits</code>.
     *
     * @serial
     * @see #getMaximumIntegerDigits
     * @since 1.5
     */
    private int    maximumIntegerDigits = super.getMaximumIntegerDigits();

    /**
     * The minimum number of digits allowed in the integer portion of a
     * <code>BigInteger</code> or <code>BigDecimal</code> number.
     * <code>minimumIntegerDigits</code> must be less than or equal to
     * <code>maximumIntegerDigits</code>.
     *
     * @serial
     * @see #getMinimumIntegerDigits
     * @since 1.5
     */
    private int    minimumIntegerDigits = super.getMinimumIntegerDigits();

    /**
     * The maximum number of digits allowed in the fractional portion of a
     * <code>BigInteger</code> or <code>BigDecimal</code> number.
     * <code>maximumFractionDigits</code> must be greater than or equal to
     * <code>minimumFractionDigits</code>.
     *
     * @serial
     * @see #getMaximumFractionDigits
     * @since 1.5
     */
    private int    maximumFractionDigits = super.getMaximumFractionDigits();

    /**
     * The minimum number of digits allowed in the fractional portion of a
     * <code>BigInteger</code> or <code>BigDecimal</code> number.
     * <code>minimumFractionDigits</code> must be less than or equal to
     * <code>maximumFractionDigits</code>.
     *
     * @serial
     * @see #getMinimumFractionDigits
     * @since 1.5
     */
    private int    minimumFractionDigits = super.getMinimumFractionDigits();

    /**
     * The {@link java.math.RoundingMode} used in this DecimalFormat.
     *
     * @serial
     * @since 1.6
     */
    private RoundingMode roundingMode = RoundingMode.HALF_EVEN;

    // ------ DecimalFormat fields for fast-path for double algorithm  ------

    /**
     * Helper inner utility class for storing the data used in the fast-path
     * algorithm. Almost all fields related to fast-path are encapsulated in
     * this class.
     *
     * Any {@code DecimalFormat} instance has a {@code fastPathData}
     * reference field that is null unless both the properties of the instance
     * are such that the instance is in the "fast-path" state, and a format call
     * has been done at least once while in this state.
     *
     * Almost all fields are related to the "fast-path" state only and don't
     * change until one of the instance properties is changed.
     *
     * {@code firstUsedIndex} and {@code lastFreeIndex} are the only
     * two fields that are used and modified while inside a call to
     * {@code fastDoubleFormat}.
     *
     */
    private static class FastPathData {
        // --- Temporary fields used in fast-path, shared by several methods.

        /** The first unused index at the end of the formatted result. */
        int lastFreeIndex;

        /** The first used index at the beginning of the formatted result */
        int firstUsedIndex;

        // --- State fields related to fast-path status. Changes due to a
        //     property change only. Set by checkAndSetFastPathStatus() only.

        /** Difference between locale zero and default zero representation. */
        int  zeroDelta;

        /** Locale char for grouping separator. */
        char groupingChar;

        /**  Fixed index position of last integral digit of formatted result */
        int integralLastIndex;

        /**  Fixed index position of first fractional digit of formatted result */
        int fractionalFirstIndex;

        /** Fractional constants depending on decimal|currency state */
        double fractionalScaleFactor;
        int fractionalMaxIntBound;


        /** The char array buffer that will contain the formatted result */
        char[] fastPathContainer;

        /** Suffixes recorded as char array for efficiency. */
        char[] charsPositivePrefix;
        char[] charsNegativePrefix;
        char[] charsPositiveSuffix;
        char[] charsNegativeSuffix;
        boolean positiveAffixesRequired = true;
        boolean negativeAffixesRequired = true;
    }

    /** The format fast-path status of the instance. Logical state. */
    private transient boolean isFastPath = false;

    /** Flag stating need of check and reinit fast-path status on next format call. */
    private transient boolean fastPathCheckNeeded = true;

    /** DecimalFormat reference to its FastPathData */
    private transient FastPathData fastPathData;


    //----------------------------------------------------------------------

    static final int currentSerialVersion = 4;

    /**
     * The internal serial version which says which version was written.
     * Possible values are:
     * <ul>
     * <li><b>0</b> (default): versions before the Java 2 platform v1.2
     * <li><b>1</b>: version for 1.2, which includes the two new fields
     *      <code>useExponentialNotation</code> and
     *      <code>minExponentDigits</code>.
     * <li><b>2</b>: version for 1.3 and later, which adds four new fields:
     *      <code>posPrefixPattern</code>, <code>posSuffixPattern</code>,
     *      <code>negPrefixPattern</code>, and <code>negSuffixPattern</code>.
     * <li><b>3</b>: version for 1.5 and later, which adds five new fields:
     *      <code>maximumIntegerDigits</code>,
     *      <code>minimumIntegerDigits</code>,
     *      <code>maximumFractionDigits</code>,
     *      <code>minimumFractionDigits</code>, and
     *      <code>parseBigDecimal</code>.
     * <li><b>4</b>: version for 1.6 and later, which adds one new field:
     *      <code>roundingMode</code>.
     * </ul>
     * @since 1.2
     * @serial
     */
    private int serialVersionOnStream = currentSerialVersion;

    //----------------------------------------------------------------------
    // CONSTANTS
    //----------------------------------------------------------------------

    // ------ Fast-Path for double Constants ------

    /** Maximum valid integer value for applying fast-path algorithm */
    private static final double MAX_INT_AS_DOUBLE = (double) Integer.MAX_VALUE;

    /**
     * The digit arrays used in the fast-path methods for collecting digits.
     * Using 3 constants arrays of chars ensures a very fast collection of digits
     */
    private static class DigitArrays {
        static final char[] DigitOnes1000 = new char[1000];
        static final char[] DigitTens1000 = new char[1000];
        static final char[] DigitHundreds1000 = new char[1000];

        // initialize on demand holder class idiom for arrays of digits
        static {
            int tenIndex = 0;
            int hundredIndex = 0;
            char digitOne = '0';
            char digitTen = '0';
            char digitHundred = '0';
            for (int i = 0;  i < 1000; i++ ) {

                DigitOnes1000[i] = digitOne;
                if (digitOne == '9')
                    digitOne = '0';
                else
                    digitOne++;

                DigitTens1000[i] = digitTen;
                if (i == (tenIndex + 9)) {
                    tenIndex += 10;
                    if (digitTen == '9')
                        digitTen = '0';
                    else
                        digitTen++;
                }

                DigitHundreds1000[i] = digitHundred;
                if (i == (hundredIndex + 99)) {
                    digitHundred++;
                    hundredIndex += 100;
                }
            }
        }
    }
    // ------ Fast-Path for double Constants end ------

    // Constants for characters used in programmatic (unlocalized) patterns.
    private static final char       PATTERN_ZERO_DIGIT         = '0';
    private static final char       PATTERN_GROUPING_SEPARATOR = ',';
    private static final char       PATTERN_DECIMAL_SEPARATOR  = '.';
    private static final char       PATTERN_PER_MILLE          = '\u2030';
    private static final char       PATTERN_PERCENT            = '%';
    private static final char       PATTERN_DIGIT              = '#';
    private static final char       PATTERN_SEPARATOR          = ';';
    private static final String     PATTERN_EXPONENT           = "E";
    private static final char       PATTERN_MINUS              = '-';

    /**
     * The CURRENCY_SIGN is the standard Unicode symbol for currency.  It
     * is used in patterns and substituted with either the currency symbol,
     * or if it is doubled, with the international currency symbol.  If the
     * CURRENCY_SIGN is seen in a pattern, then the decimal separator is
     * replaced with the monetary decimal separator.
     *
     * The CURRENCY_SIGN is not localized.
     */
    private static final char       CURRENCY_SIGN = '\u00A4';

    private static final char       QUOTE = '\'';

    private static FieldPosition[] EmptyFieldPositionArray = new FieldPosition[0];

    // Upper limit on integer and fraction digits for a Java double
    static final int DOUBLE_INTEGER_DIGITS  = 309;
    static final int DOUBLE_FRACTION_DIGITS = 340;

    // Upper limit on integer and fraction digits for BigDecimal and BigInteger
    static final int MAXIMUM_INTEGER_DIGITS  = Integer.MAX_VALUE;
    static final int MAXIMUM_FRACTION_DIGITS = Integer.MAX_VALUE;

    // Proclaim JDK 1.1 serial compatibility.
    static final long serialVersionUID = 864413376551465018L;
}
