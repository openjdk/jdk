/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package java.text;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


/**
 * <p>
 * {@code CompactNumberFormat} is a concrete subclass of {@code NumberFormat}
 * that formats a decimal number in its compact form.
 *
 * The compact number formatting is designed for the environment where the space
 * is limited, and the formatted string can be displayed in that limited space.
 * It is defined by LDML's specification for
 * <a href = "http://unicode.org/reports/tr35/tr35-numbers.html#Compact_Number_Formats">
 * Compact Number Formats</a>. A compact number formatting refers
 * to the representation of a number in a shorter form, based on the patterns
 * provided for a given locale.
 *
 * <p>
 * For example:
 * <br>In the {@link java.util.Locale#US US locale}, {@code 1000} can be formatted
 * as {@code "1K"}, and {@code 1000000} as {@code "1M"}, depending upon the
 * <a href = "#compact_number_style" >style</a> used.
 * <br>In the {@code "hi_IN"} locale, {@code 1000} can be formatted as
 * "1 \u0939\u091C\u093C\u093E\u0930", and {@code 50000000} as "5 \u0915.",
 * depending upon the <a href = "#compact_number_style" >style</a> used.
 *
 * <p>
 * To obtain a {@code CompactNumberFormat} for a locale, use one
 * of the factory methods given by {@code NumberFormat} for compact number
 * formatting. For example,
 * {@link NumberFormat#getCompactNumberInstance(Locale, Style)}.
 *
 * <blockquote><pre>
 * NumberFormat fmt = NumberFormat.getCompactNumberInstance(
 *                             new Locale("hi", "IN"), NumberFormat.Style.SHORT);
 * String result = fmt.format(1000);
 * </pre></blockquote>
 *
 * <h3><a id="compact_number_style">Style</a></h3>
 * <p>
 * A number can be formatted in the compact forms with two different
 * styles, {@link NumberFormat.Style#SHORT SHORT}
 * and {@link NumberFormat.Style#LONG LONG}. Use
 * {@link NumberFormat#getCompactNumberInstance(Locale, Style)} for formatting and
 * parsing a number in {@link NumberFormat.Style#SHORT SHORT} or
 * {@link NumberFormat.Style#LONG LONG} compact form,
 * where the given {@code Style} parameter requests the desired
 * format. A {@link NumberFormat.Style#SHORT SHORT} style
 * compact number instance in the {@link java.util.Locale#US US locale} formats
 * {@code 10000} as {@code "10K"}. However, a
 * {@link NumberFormat.Style#LONG LONG} style instance in same locale
 * formats {@code 10000} as {@code "10 thousand"}.
 *
 * <h3><a id="compact_number_patterns">Compact Number Patterns</a></h3>
 * <p>
 * The compact number patterns are represented in a series of patterns where each
 * pattern is used to format a range of numbers. An example of
 * {@link NumberFormat.Style#SHORT SHORT} styled compact number patterns
 * for the {@link java.util.Locale#US US locale} is {@code {"", "", "", "0K",
 * "00K", "000K", "0M", "00M", "000M", "0B", "00B", "000B", "0T", "00T", "000T"}},
 * ranging from {@code 10}<sup>{@code 0}</sup> to {@code 10}<sup>{@code 14}</sup>.
 * There can be any number of patterns and they are
 * strictly index based starting from the range {@code 10}<sup>{@code 0}</sup>.
 * For example, in the above patterns, pattern at index 3
 * ({@code "0K"}) is used for formatting {@code number >= 1000 and number < 10000},
 * pattern at index 4 ({@code "00K"}) is used for formatting
 * {@code number >= 10000 and number < 100000} and so on. In most of the locales,
 * patterns with the range
 * {@code 10}<sup>{@code 0}</sup>-{@code 10}<sup>{@code 2}</sup> are empty
 * strings, which implicitly means a special pattern {@code "0"}.
 * A special pattern {@code "0"} is used for any range which does not contain
 * a compact pattern. This special pattern can appear explicitly for any specific
 * range, or considered as a default pattern for an empty string.
 * <p>
 * A compact pattern has the following syntax:
 * <blockquote><pre>
 * <i>Pattern:</i>
 *         <i>PositivePattern</i>
 *         <i>PositivePattern</i> <i>[; NegativePattern]<sub>optional</sub></i>
 * <i>PositivePattern:</i>
 *         <i>Prefix<sub>optional</sub></i> <i>MinimumInteger</i> <i>Suffix<sub>optional</sub></i>
 * <i>NegativePattern:</i>
 *        <i>Prefix<sub>optional</sub></i> <i>MinimumInteger</i> <i>Suffix<sub>optional</sub></i>
 * <i>Prefix:</i>
 *      Any Unicode characters except &#92;uFFFE, &#92;uFFFF, and
 *      <a href = "DecimalFormat.html#special_pattern_character">special characters</a>
 * <i>Suffix:</i>
 *      Any Unicode characters except &#92;uFFFE, &#92;uFFFF, and
 *      <a href = "DecimalFormat.html#special_pattern_character">special characters</a>
 * <i>MinimumInteger:</i>
 *      0
 *      0 <i>MinimumInteger</i>
 * </pre></blockquote>
 *
 * A compact pattern contains a positive and negative subpattern
 * separated by a subpattern boundary character {@code ';' (U+003B)},
 * for example, {@code "0K;-0K"}. Each subpattern has a prefix,
 * minimum integer digits, and suffix. The negative subpattern
 * is optional, if absent, then the positive subpattern prefixed with the
 * minus sign ({@code '-' U+002D HYPHEN-MINUS}) is used as the negative
 * subpattern. That is, {@code "0K"} alone is equivalent to {@code "0K;-0K"}.
 * If there is an explicit negative subpattern, it serves only to specify
 * the negative prefix and suffix. The number of minimum integer digits,
 * and other characteristics are all the same as the positive pattern.
 * That means that {@code "0K;-00K"} produces precisely the same behavior
 * as {@code "0K;-0K"}.
 *
 * <p>
 * Many characters in a compact pattern are taken literally, they are matched
 * during parsing and output unchanged during formatting.
 * <a href = "DecimalFormat.html#special_pattern_character">Special characters</a>,
 * on the other hand, stand for other characters, strings, or classes of
 * characters. They must be quoted, using single quote {@code ' (U+0027)}
 * unless noted otherwise, if they are to appear in the prefix or suffix
 * as literals. For example, 0\u0915'.'.
 *
 * <h3>Formatting</h3>
 * The default formatting behavior returns a formatted string with no fractional
 * digits, however users can use the {@link #setMinimumFractionDigits(int)}
 * method to include the fractional part.
 * The number {@code 1000.0} or {@code 1000} is formatted as {@code "1K"}
 * not {@code "1.00K"} (in the {@link java.util.Locale#US US locale}). For this
 * reason, the patterns provided for formatting contain only the minimum
 * integer digits, prefix and/or suffix, but no fractional part.
 * For example, patterns used are {@code {"", "", "", 0K, 00K, ...}}. If the pattern
 * selected for formatting a number is {@code "0"} (special pattern),
 * either explicit or defaulted, then the general number formatting provided by
 * {@link java.text.DecimalFormat DecimalFormat}
 * for the specified locale is used.
 *
 * <h3>Parsing</h3>
 * The default parsing behavior does not allow a grouping separator until
 * grouping used is set to {@code true} by using
 * {@link #setGroupingUsed(boolean)}. The parsing of the fractional part
 * depends on the {@link #isParseIntegerOnly()}. For example, if the
 * parse integer only is set to true, then the fractional part is skipped.
 *
 * <h3>Rounding</h3>
 * {@code CompactNumberFormat} provides rounding modes defined in
 * {@link java.math.RoundingMode} for formatting.  By default, it uses
 * {@link java.math.RoundingMode#HALF_EVEN RoundingMode.HALF_EVEN}.
 *
 * @see CompactNumberFormat.Style
 * @see NumberFormat
 * @see DecimalFormat
 * @since 12
 */
public final class CompactNumberFormat extends NumberFormat {

    private static final long serialVersionUID = 7128367218649234678L;

    /**
     * The patterns for compact form of numbers for this
     * {@code CompactNumberFormat}. A possible example is
     * {@code {"", "", "", "0K", "00K", "000K", "0M", "00M", "000M", "0B",
     * "00B", "000B", "0T", "00T", "000T"}} ranging from
     * {@code 10}<sup>{@code 0}</sup>-{@code 10}<sup>{@code 14}</sup>,
     * where each pattern is used to format a range of numbers.
     * For example, {@code "0K"} is used for formatting
     * {@code number >= 1000 and number < 10000}, {@code "00K"} is used for
     * formatting {@code number >= 10000 and number < 100000} and so on.
     * This field must not be {@code null}.
     *
     * @serial
     */
    private String[] compactPatterns;

    /**
     * List of positive prefix patterns of this formatter's
     * compact number patterns.
     */
    private transient List<String> positivePrefixPatterns;

    /**
     * List of negative prefix patterns of this formatter's
     * compact number patterns.
     */
    private transient List<String> negativePrefixPatterns;

    /**
     * List of positive suffix patterns of this formatter's
     * compact number patterns.
     */
    private transient List<String> positiveSuffixPatterns;

    /**
     * List of negative suffix patterns of this formatter's
     * compact number patterns.
     */
    private transient List<String> negativeSuffixPatterns;

    /**
     * List of divisors of this formatter's compact number patterns.
     * Divisor can be either Long or BigInteger (if the divisor value goes
     * beyond long boundary)
     */
    private transient List<Number> divisors;

    /**
     * The {@code DecimalFormatSymbols} object used by this format.
     * It contains the symbols used to format numbers. For example,
     * the grouping separator, decimal separator, and so on.
     * This field must not be {@code null}.
     *
     * @serial
     * @see DecimalFormatSymbols
     */
    private DecimalFormatSymbols symbols;

    /**
     * The decimal pattern which is used for formatting the numbers
     * matching special pattern "0". This field must not be {@code null}.
     *
     * @serial
     * @see DecimalFormat
     */
    private final String decimalPattern;

    /**
     * A {@code DecimalFormat} used by this format for getting corresponding
     * general number formatting behavior for compact numbers.
     *
     */
    private transient DecimalFormat decimalFormat;

    /**
     * A {@code DecimalFormat} used by this format for getting general number
     * formatting behavior for the numbers which can't be represented as compact
     * numbers. For example, number matching the special pattern "0" are
     * formatted through general number format pattern provided by
     * {@link java.text.DecimalFormat DecimalFormat}
     * for the specified locale.
     *
     */
    private transient DecimalFormat defaultDecimalFormat;

    /**
     * The number of digits between grouping separators in the integer portion
     * of a compact number. For the grouping to work while formatting, this
     * field needs to be greater than 0 with grouping used set as true.
     * This field must not be negative.
     *
     * @serial
     */
    private byte groupingSize = 0;

    /**
     * Returns whether the {@link #parse(String, ParsePosition)}
     * method returns {@code BigDecimal}.
     *
     * @serial
     */
    private boolean parseBigDecimal = false;

    /**
     * The {@code RoundingMode} used in this compact number format.
     * This field must not be {@code null}.
     *
     * @serial
     */
    private RoundingMode roundingMode = RoundingMode.HALF_EVEN;

    /**
     * Special pattern used for compact numbers
     */
    private static final String SPECIAL_PATTERN = "0";

    /**
     * Multiplier for compact pattern range. In
     * the list compact patterns each compact pattern
     * specify the range with the multiplication factor of 10
     * of its previous compact pattern range.
     * For example, 10^0, 10^1, 10^2, 10^3, 10^4...
     *
     */
    private static final int RANGE_MULTIPLIER = 10;

    /**
     * Creates a {@code CompactNumberFormat} using the given decimal pattern,
     * decimal format symbols and compact patterns.
     * To obtain the instance of {@code CompactNumberFormat} with the standard
     * compact patterns for a {@code Locale} and {@code Style},
     * it is recommended to use the factory methods given by
     * {@code NumberFormat} for compact number formatting. For example,
     * {@link NumberFormat#getCompactNumberInstance(Locale, Style)}.
     *
     * @param decimalPattern a decimal pattern for general number formatting
     * @param symbols the set of symbols to be used
     * @param compactPatterns an array of
     *        <a href = "CompactNumberFormat.html#compact_number_patterns">
     *        compact number patterns</a>
     * @throws NullPointerException if any of the given arguments is
     *                                 {@code null}
     * @throws IllegalArgumentException if the given {@code decimalPattern} or the
     *                     {@code compactPatterns} array contains an invalid pattern
     *                     or if a {@code null} appears in the array of compact
     *                     patterns
     * @see DecimalFormat#DecimalFormat(java.lang.String, DecimalFormatSymbols)
     * @see DecimalFormatSymbols
     */
    public CompactNumberFormat(String decimalPattern,
            DecimalFormatSymbols symbols, String[] compactPatterns) {

        Objects.requireNonNull(decimalPattern, "decimalPattern");
        Objects.requireNonNull(symbols, "symbols");
        Objects.requireNonNull(compactPatterns, "compactPatterns");

        this.symbols = symbols;
        // Instantiating the DecimalFormat with "0" pattern; this acts just as a
        // basic pattern; the properties (For example, prefix/suffix)
        // are later computed based on the compact number formatting process.
        decimalFormat = new DecimalFormat(SPECIAL_PATTERN, this.symbols);

        // Initializing the super class state with the decimalFormat values
        // to represent this CompactNumberFormat.
        // For setting the digits counts, use overridden setXXX methods of this
        // CompactNumberFormat, as it performs check with the max range allowed
        // for compact number formatting
        setMaximumIntegerDigits(decimalFormat.getMaximumIntegerDigits());
        setMinimumIntegerDigits(decimalFormat.getMinimumIntegerDigits());
        setMaximumFractionDigits(decimalFormat.getMaximumFractionDigits());
        setMinimumFractionDigits(decimalFormat.getMinimumFractionDigits());

        super.setGroupingUsed(decimalFormat.isGroupingUsed());
        super.setParseIntegerOnly(decimalFormat.isParseIntegerOnly());

        this.compactPatterns = compactPatterns;

        // DecimalFormat used for formatting numbers with special pattern "0".
        // Formatting is delegated to the DecimalFormat's number formatting
        // with no fraction digits
        this.decimalPattern = decimalPattern;
        defaultDecimalFormat = new DecimalFormat(this.decimalPattern,
                this.symbols);
        defaultDecimalFormat.setMaximumFractionDigits(0);
        // Process compact patterns to extract the prefixes, suffixes and
        // divisors
        processCompactPatterns();
    }

    /**
     * Formats a number to produce a string representing its compact form.
     * The number can be of any subclass of {@link java.lang.Number}.
     * @param number     the number to format
     * @param toAppendTo the {@code StringBuffer} to which the formatted
     *                   text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, for formatting
     *                         a number {@code 123456789} in the
     *                         {@link java.util.Locale#US US locale},
     *                         if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin
     *                         index and end index of {@code fieldPosition}
     *                         will be set to 0 and 3, respectively for the
     *                         output string {@code 123M}. Similarly, positions
     *                         of the prefix and the suffix fields can be
     *                         obtained using {@link NumberFormat.Field#PREFIX}
     *                         and {@link NumberFormat.Field#SUFFIX} respectively.
     * @return           the {@code StringBuffer} passed in as {@code toAppendTo}
     * @throws           IllegalArgumentException if {@code number} is
     *                   {@code null} or not an instance of {@code Number}
     * @throws           NullPointerException if {@code toAppendTo} or
     *                   {@code fieldPosition} is {@code null}
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to {@code RoundingMode.UNNECESSARY}
     * @see              FieldPosition
     */
    @Override
    public final StringBuffer format(Object number,
            StringBuffer toAppendTo,
            FieldPosition fieldPosition) {
        if (number instanceof Long || number instanceof Integer
                || number instanceof Short || number instanceof Byte
                || number instanceof AtomicInteger
                || number instanceof AtomicLong
                || (number instanceof BigInteger
                && ((BigInteger) number).bitLength() < 64)) {
            return format(((Number) number).longValue(), toAppendTo,
                    fieldPosition);
        } else if (number instanceof BigDecimal) {
            return format((BigDecimal) number, toAppendTo, fieldPosition);
        } else if (number instanceof BigInteger) {
            return format((BigInteger) number, toAppendTo, fieldPosition);
        } else if (number instanceof Number) {
            return format(((Number) number).doubleValue(), toAppendTo, fieldPosition);
        } else {
            throw new IllegalArgumentException("Cannot format "
                    + number.getClass().getName() + " as a number");
        }
    }

    /**
     * Formats a double to produce a string representing its compact form.
     * @param number    the double number to format
     * @param result    where the text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, to format
     *                         a number {@code 1234567.89} in the
     *                         {@link java.util.Locale#US US locale}
     *                         if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin
     *                         index and end index of {@code fieldPosition}
     *                         will be set to 0 and 1, respectively for the
     *                         output string {@code 1M}. Similarly, positions
     *                         of the prefix and the suffix fields can be
     *                         obtained using {@link NumberFormat.Field#PREFIX}
     *                         and {@link NumberFormat.Field#SUFFIX} respectively.
     * @return    the {@code StringBuffer} passed in as {@code result}
     * @throws NullPointerException if {@code result} or
     *            {@code fieldPosition} is {@code null}
     * @throws ArithmeticException if rounding is needed with rounding
     *            mode being set to {@code RoundingMode.UNNECESSARY}
     * @see FieldPosition
     */
    @Override
    public StringBuffer format(double number, StringBuffer result,
            FieldPosition fieldPosition) {

        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);
        return format(number, result, fieldPosition.getFieldDelegate());
    }

    private StringBuffer format(double number, StringBuffer result,
            FieldDelegate delegate) {

        boolean nanOrInfinity = decimalFormat.handleNaN(number, result, delegate);
        if (nanOrInfinity) {
            return result;
        }

        boolean isNegative = ((number < 0.0)
                || (number == 0.0 && 1 / number < 0.0));

        nanOrInfinity = decimalFormat.handleInfinity(number, result, delegate, isNegative);
        if (nanOrInfinity) {
            return result;
        }

        // Round the double value with min fraction digits, the integer
        // part of the rounded value is used for matching the compact
        // number pattern
        // For example, if roundingMode is HALF_UP with min fraction
        // digits = 0, the number 999.6 should round up
        // to 1000 and outputs 1K/thousand in "en_US" locale
        DigitList dList = new DigitList();
        dList.setRoundingMode(getRoundingMode());
        number = isNegative ? -number : number;
        dList.set(isNegative, number, getMinimumFractionDigits());

        double roundedNumber = dList.getDouble();
        int compactDataIndex = selectCompactPattern((long) roundedNumber);
        if (compactDataIndex != -1) {
            String prefix = isNegative ? negativePrefixPatterns.get(compactDataIndex)
                    : positivePrefixPatterns.get(compactDataIndex);
            String suffix = isNegative ? negativeSuffixPatterns.get(compactDataIndex)
                    : positiveSuffixPatterns.get(compactDataIndex);

            if (!prefix.isEmpty() || !suffix.isEmpty()) {
                appendPrefix(result, prefix, delegate);
                long divisor = (Long) divisors.get(compactDataIndex);
                roundedNumber = roundedNumber / divisor;
                decimalFormat.setDigitList(roundedNumber, isNegative, getMaximumFractionDigits());
                decimalFormat.subformatNumber(result, delegate, isNegative,
                        false, getMaximumIntegerDigits(), getMinimumIntegerDigits(),
                        getMaximumFractionDigits(), getMinimumFractionDigits());
                appendSuffix(result, suffix, delegate);
            } else {
                defaultDecimalFormat.doubleSubformat(number, result, delegate, isNegative);
            }
        } else {
            defaultDecimalFormat.doubleSubformat(number, result, delegate, isNegative);
        }
        return result;
    }

    /**
     * Formats a long to produce a string representing its compact form.
     * @param number    the long number to format
     * @param result    where the text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, to format
     *                         a number {@code 123456789} in the
     *                         {@link java.util.Locale#US US locale},
     *                         if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin
     *                         index and end index of {@code fieldPosition}
     *                         will be set to 0 and 3, respectively for the
     *                         output string {@code 123M}. Similarly, positions
     *                         of the prefix and the suffix fields can be
     *                         obtained using {@link NumberFormat.Field#PREFIX}
     *                         and {@link NumberFormat.Field#SUFFIX} respectively.
     * @return       the {@code StringBuffer} passed in as {@code result}
     * @throws       NullPointerException if {@code result} or
     *               {@code fieldPosition} is {@code null}
     * @throws       ArithmeticException if rounding is needed with rounding
     *               mode being set to {@code RoundingMode.UNNECESSARY}
     * @see FieldPosition
     */
    @Override
    public StringBuffer format(long number, StringBuffer result,
            FieldPosition fieldPosition) {

        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);
        return format(number, result, fieldPosition.getFieldDelegate());
    }

    private StringBuffer format(long number, StringBuffer result, FieldDelegate delegate) {
        boolean isNegative = (number < 0);
        if (isNegative) {
            number = -number;
        }

        if (number < 0) { // LONG_MIN
            BigInteger bigIntegerValue = BigInteger.valueOf(number);
            return format(bigIntegerValue, result, delegate, true);
        }

        int compactDataIndex = selectCompactPattern(number);
        if (compactDataIndex != -1) {
            String prefix = isNegative ? negativePrefixPatterns.get(compactDataIndex)
                    : positivePrefixPatterns.get(compactDataIndex);
            String suffix = isNegative ? negativeSuffixPatterns.get(compactDataIndex)
                    : positiveSuffixPatterns.get(compactDataIndex);
            if (!prefix.isEmpty() || !suffix.isEmpty()) {
                appendPrefix(result, prefix, delegate);
                long divisor = (Long) divisors.get(compactDataIndex);
                if ((number % divisor == 0)) {
                    number = number / divisor;
                    decimalFormat.setDigitList(number, isNegative, 0);
                    decimalFormat.subformatNumber(result, delegate,
                            isNegative, true, getMaximumIntegerDigits(),
                            getMinimumIntegerDigits(), getMaximumFractionDigits(),
                            getMinimumFractionDigits());
                } else {
                    // To avoid truncation of fractional part store
                    // the value in double and follow double path instead of
                    // long path
                    double dNumber = (double) number / divisor;
                    decimalFormat.setDigitList(dNumber, isNegative, getMaximumFractionDigits());
                    decimalFormat.subformatNumber(result, delegate,
                            isNegative, false, getMaximumIntegerDigits(),
                            getMinimumIntegerDigits(), getMaximumFractionDigits(),
                            getMinimumFractionDigits());
                }
                appendSuffix(result, suffix, delegate);
            } else {
                number = isNegative ? -number : number;
                defaultDecimalFormat.format(number, result, delegate);
            }
        } else {
            number = isNegative ? -number : number;
            defaultDecimalFormat.format(number, result, delegate);
        }
        return result;
    }

    /**
     * Formats a BigDecimal to produce a string representing its compact form.
     * @param number    the BigDecimal number to format
     * @param result    where the text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, to format
     *                         a number {@code 1234567.89} in the
     *                         {@link java.util.Locale#US US locale},
     *                         if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin
     *                         index and end index of {@code fieldPosition}
     *                         will be set to 0 and 1, respectively for the
     *                         output string {@code 1M}. Similarly, positions
     *                         of the prefix and the suffix fields can be
     *                         obtained using {@link NumberFormat.Field#PREFIX}
     *                         and {@link NumberFormat.Field#SUFFIX} respectively.
     * @return        the {@code StringBuffer} passed in as {@code result}
     * @throws        ArithmeticException if rounding is needed with rounding
     *                mode being set to {@code RoundingMode.UNNECESSARY}
     * @throws        NullPointerException if any of the given parameter
     *                is {@code null}
     * @see FieldPosition
     */
    private StringBuffer format(BigDecimal number, StringBuffer result,
            FieldPosition fieldPosition) {

        Objects.requireNonNull(number);
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);
        return format(number, result, fieldPosition.getFieldDelegate());
    }

    private StringBuffer format(BigDecimal number, StringBuffer result,
            FieldDelegate delegate) {

        boolean isNegative = number.signum() == -1;
        if (isNegative) {
            number = number.negate();
        }

        // Round the value with min fraction digits, the integer
        // part of the rounded value is used for matching the compact
        // number pattern
        // For example, If roundingMode is HALF_UP with min fraction digits = 0,
        // the number 999.6 should round up
        // to 1000 and outputs 1K/thousand in "en_US" locale
        number = number.setScale(getMinimumFractionDigits(), getRoundingMode());

        int compactDataIndex;
        if (number.toBigInteger().bitLength() < 64) {
            compactDataIndex = selectCompactPattern(number.toBigInteger().longValue());
        } else {
            compactDataIndex = selectCompactPattern(number.toBigInteger());
        }

        if (compactDataIndex != -1) {
            String prefix = isNegative ? negativePrefixPatterns.get(compactDataIndex)
                    : positivePrefixPatterns.get(compactDataIndex);
            String suffix = isNegative ? negativeSuffixPatterns.get(compactDataIndex)
                    : positiveSuffixPatterns.get(compactDataIndex);
            if (!prefix.isEmpty() || !suffix.isEmpty()) {
                appendPrefix(result, prefix, delegate);
                Number divisor = divisors.get(compactDataIndex);
                number = number.divide(new BigDecimal(divisor.toString()), getRoundingMode());
                decimalFormat.setDigitList(number, isNegative, getMaximumFractionDigits());
                decimalFormat.subformatNumber(result, delegate, isNegative,
                        false, getMaximumIntegerDigits(), getMinimumIntegerDigits(),
                        getMaximumFractionDigits(), getMinimumFractionDigits());
                appendSuffix(result, suffix, delegate);
            } else {
                number = isNegative ? number.negate() : number;
                defaultDecimalFormat.format(number, result, delegate);
            }
        } else {
            number = isNegative ? number.negate() : number;
            defaultDecimalFormat.format(number, result, delegate);
        }
        return result;
    }

    /**
     * Formats a BigInteger to produce a string representing its compact form.
     * @param number    the BigInteger number to format
     * @param result    where the text is to be appended
     * @param fieldPosition    keeps track on the position of the field within
     *                         the returned string. For example, to format
     *                         a number {@code 123456789} in the
     *                         {@link java.util.Locale#US US locale},
     *                         if the given {@code fieldPosition} is
     *                         {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                         and end index of {@code fieldPosition} will be set
     *                         to 0 and 3, respectively for the output string
     *                         {@code 123M}. Similarly, positions of the
     *                         prefix and the suffix fields can be obtained
     *                         using {@link NumberFormat.Field#PREFIX} and
     *                         {@link NumberFormat.Field#SUFFIX} respectively.
     * @return        the {@code StringBuffer} passed in as {@code result}
     * @throws        ArithmeticException if rounding is needed with rounding
     *                mode being set to {@code RoundingMode.UNNECESSARY}
     * @throws        NullPointerException if any of the given parameter
     *                is {@code null}
     * @see FieldPosition
     */
    private StringBuffer format(BigInteger number, StringBuffer result,
            FieldPosition fieldPosition) {

        Objects.requireNonNull(number);
        fieldPosition.setBeginIndex(0);
        fieldPosition.setEndIndex(0);
        return format(number, result, fieldPosition.getFieldDelegate(), false);
    }

    private StringBuffer format(BigInteger number, StringBuffer result,
            FieldDelegate delegate, boolean formatLong) {

        boolean isNegative = number.signum() == -1;
        if (isNegative) {
            number = number.negate();
        }

        int compactDataIndex = selectCompactPattern(number);
        if (compactDataIndex != -1) {
            String prefix = isNegative ? negativePrefixPatterns.get(compactDataIndex)
                    : positivePrefixPatterns.get(compactDataIndex);
            String suffix = isNegative ? negativeSuffixPatterns.get(compactDataIndex)
                    : positiveSuffixPatterns.get(compactDataIndex);
            if (!prefix.isEmpty() || !suffix.isEmpty()) {
                appendPrefix(result, prefix, delegate);
                Number divisor = divisors.get(compactDataIndex);
                if (number.mod(new BigInteger(divisor.toString()))
                        .compareTo(BigInteger.ZERO) == 0) {
                    number = number.divide(new BigInteger(divisor.toString()));

                    decimalFormat.setDigitList(number, isNegative, 0);
                    decimalFormat.subformatNumber(result, delegate,
                            isNegative, true, getMaximumIntegerDigits(),
                            getMinimumIntegerDigits(), getMaximumFractionDigits(),
                            getMinimumFractionDigits());
                } else {
                    // To avoid truncation of fractional part store the value in
                    // BigDecimal and follow BigDecimal path instead of
                    // BigInteger path
                    BigDecimal nDecimal = new BigDecimal(number)
                            .divide(new BigDecimal(divisor.toString()), getRoundingMode());
                    decimalFormat.setDigitList(nDecimal, isNegative, getMaximumFractionDigits());
                    decimalFormat.subformatNumber(result, delegate,
                            isNegative, false, getMaximumIntegerDigits(),
                            getMinimumIntegerDigits(), getMaximumFractionDigits(),
                            getMinimumFractionDigits());
                }
                appendSuffix(result, suffix, delegate);
            } else {
                number = isNegative ? number.negate() : number;
                defaultDecimalFormat.format(number, result, delegate, formatLong);
            }
        } else {
            number = isNegative ? number.negate() : number;
            defaultDecimalFormat.format(number, result, delegate, formatLong);
        }
        return result;
    }

    /**
     * Appends the {@code prefix} to the {@code result} and also set the
     * {@code NumberFormat.Field.SIGN} and {@code NumberFormat.Field.PREFIX}
     * field positions.
     * @param result the resulting string, where the pefix is to be appended
     * @param prefix prefix to append
     * @param delegate notified of the locations of
     *                 {@code NumberFormat.Field.SIGN} and
     *                 {@code NumberFormat.Field.PREFIX} fields
     */
    private void appendPrefix(StringBuffer result, String prefix,
            FieldDelegate delegate) {
        append(result, expandAffix(prefix), delegate,
                getFieldPositions(prefix, NumberFormat.Field.PREFIX));
    }

    /**
     * Appends {@code suffix} to the {@code result} and also set the
     * {@code NumberFormat.Field.SIGN} and {@code NumberFormat.Field.SUFFIX}
     * field positions.
     * @param result the resulting string, where the suffix is to be appended
     * @param suffix suffix to append
     * @param delegate notified of the locations of
     *                 {@code NumberFormat.Field.SIGN} and
     *                 {@code NumberFormat.Field.SUFFIX} fields
     */
    private void appendSuffix(StringBuffer result, String suffix,
            FieldDelegate delegate) {
        append(result, expandAffix(suffix), delegate,
                getFieldPositions(suffix, NumberFormat.Field.SUFFIX));
    }

    /**
     * Appends the {@code string} to the {@code result}.
     * {@code delegate} is notified of SIGN, PREFIX and/or SUFFIX
     * field positions.
     * @param result the resulting string, where the text is to be appended
     * @param string the text to append
     * @param delegate notified of the locations of sub fields
     * @param positions a list of {@code FieldPostion} in the given
     *                  string
     */
    private void append(StringBuffer result, String string,
            FieldDelegate delegate, List<FieldPosition> positions) {
        if (!string.isEmpty()) {
            int start = result.length();
            result.append(string);
            for (int counter = 0; counter < positions.size(); counter++) {
                FieldPosition fp = positions.get(counter);
                Format.Field attribute = fp.getFieldAttribute();
                delegate.formatted(attribute, attribute,
                        start + fp.getBeginIndex(),
                        start + fp.getEndIndex(), result);
            }
        }
    }

    /**
     * Expands an affix {@code pattern} into a string of literals.
     * All characters in the pattern are literals unless prefixed by QUOTE.
     * The character prefixed by QUOTE is replaced with its respective
     * localized literal.
     * @param pattern a compact number pattern affix
     * @return an expanded affix
     */
    private String expandAffix(String pattern) {
        // Return if no quoted character exists
        if (pattern.indexOf(QUOTE) < 0) {
            return pattern;
        }
        StringBuilder sb = new StringBuilder();
        for (int index = 0; index < pattern.length();) {
            char ch = pattern.charAt(index++);
            if (ch == QUOTE) {
                ch = pattern.charAt(index++);
                if (ch == MINUS_SIGN) {
                    ch = symbols.getMinusSign();
                }
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Returns a list of {@code FieldPostion} in the given {@code pattern}.
     * @param pattern the pattern to be parsed for {@code FieldPosition}
     * @param field whether a PREFIX or SUFFIX field
     * @return a list of {@code FieldPostion}
     */
    private List<FieldPosition> getFieldPositions(String pattern, Field field) {
        List<FieldPosition> positions = new ArrayList<>();
        StringBuilder affix = new StringBuilder();
        int stringIndex = 0;
        for (int index = 0; index < pattern.length();) {
            char ch = pattern.charAt(index++);
            if (ch == QUOTE) {
                ch = pattern.charAt(index++);
                if (ch == MINUS_SIGN) {
                    ch = symbols.getMinusSign();
                    FieldPosition fp = new FieldPosition(NumberFormat.Field.SIGN);
                    fp.setBeginIndex(stringIndex);
                    fp.setEndIndex(stringIndex + 1);
                    positions.add(fp);
                }
            }
            stringIndex++;
            affix.append(ch);
        }
        if (affix.length() != 0) {
            FieldPosition fp = new FieldPosition(field);
            fp.setBeginIndex(0);
            fp.setEndIndex(affix.length());
            positions.add(fp);
        }
        return positions;
    }

    /**
     * Select the index of the matched compact number pattern for
     * the given {@code long} {@code number}.
     *
     * @param number number to be formatted
     * @return index of matched compact pattern;
     *         -1 if no compact patterns specified
     */
    private int selectCompactPattern(long number) {

        if (compactPatterns.length == 0) {
            return -1;
        }

        // Minimum index can be "0", max index can be "size - 1"
        int dataIndex = number <= 1 ? 0 : (int) Math.log10(number);
        dataIndex = Math.min(dataIndex, compactPatterns.length - 1);
        return dataIndex;
    }

    /**
     * Select the index of the matched compact number
     * pattern for the given {@code BigInteger} {@code number}.
     *
     * @param number number to be formatted
     * @return index of matched compact pattern;
     *         -1 if no compact patterns specified
     */
    private int selectCompactPattern(BigInteger number) {

        int matchedIndex = -1;
        if (compactPatterns.length == 0) {
            return matchedIndex;
        }

        BigInteger currentValue = BigInteger.ONE;

        // For formatting a number, the greatest type less than
        // or equal to number is used
        for (int index = 0; index < compactPatterns.length; index++) {
            if (number.compareTo(currentValue) > 0) {
                // Input number is greater than current type; try matching with
                // the next
                matchedIndex = index;
                currentValue = currentValue.multiply(BigInteger.valueOf(RANGE_MULTIPLIER));
                continue;
            }
            if (number.compareTo(currentValue) < 0) {
                // Current type is greater than the input number;
                // take the previous pattern
                break;
            } else {
                // Equal
                matchedIndex = index;
                break;
            }
        }
        return matchedIndex;
    }

    /**
     * Formats an Object producing an {@code AttributedCharacterIterator}.
     * The returned {@code AttributedCharacterIterator} can be used
     * to build the resulting string, as well as to determine information
     * about the resulting string.
     * <p>
     * Each attribute key of the {@code AttributedCharacterIterator} will
     * be of type {@code NumberFormat.Field}, with the attribute value
     * being the same as the attribute key. The prefix and the suffix
     * parts of the returned iterator (if present) are represented by
     * the attributes {@link NumberFormat.Field#PREFIX} and
     * {@link NumberFormat.Field#SUFFIX} respectively.
     *
     *
     * @throws NullPointerException if obj is null
     * @throws IllegalArgumentException when the Format cannot format the
     *         given object
     * @throws ArithmeticException if rounding is needed with rounding
     *         mode being set to {@code RoundingMode.UNNECESSARY}
     * @param obj The object to format
     * @return an {@code AttributedCharacterIterator} describing the
     *         formatted value
     */
    @Override
    public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        CharacterIteratorFieldDelegate delegate
                = new CharacterIteratorFieldDelegate();
        StringBuffer sb = new StringBuffer();

        if (obj instanceof Double || obj instanceof Float) {
            format(((Number) obj).doubleValue(), sb, delegate);
        } else if (obj instanceof Long || obj instanceof Integer
                || obj instanceof Short || obj instanceof Byte
                || obj instanceof AtomicInteger || obj instanceof AtomicLong) {
            format(((Number) obj).longValue(), sb, delegate);
        } else if (obj instanceof BigDecimal) {
            format((BigDecimal) obj, sb, delegate);
        } else if (obj instanceof BigInteger) {
            format((BigInteger) obj, sb, delegate, false);
        } else if (obj == null) {
            throw new NullPointerException(
                    "formatToCharacterIterator must be passed non-null object");
        } else {
            throw new IllegalArgumentException(
                    "Cannot format given Object as a Number");
        }
        return delegate.getIterator(sb.toString());
    }

    /**
     * Computes the divisor using minimum integer digits and
     * matched pattern index.
     * @param minIntDigits string of 0s in compact pattern
     * @param patternIndex index of matched compact pattern
     * @return divisor value for the number matching the compact
     *         pattern at given {@code patternIndex}
     */
    private Number computeDivisor(String minIntDigits, int patternIndex) {
        int count = minIntDigits.length() - 1;
        Number matchedValue;
        // The divisor value can go above long range, if the compact patterns
        // goes above index 18, divisor may need to be stored as BigInteger,
        // since long can't store numbers >= 10^19,
        if (patternIndex < 19) {
            matchedValue = (long) Math.pow(RANGE_MULTIPLIER, patternIndex);
        } else {
            matchedValue = BigInteger.valueOf(RANGE_MULTIPLIER).pow(patternIndex);
        }
        Number divisor = matchedValue;
        if (count != 0) {
            if (matchedValue instanceof BigInteger) {
                BigInteger bigValue = (BigInteger) matchedValue;
                if (bigValue.compareTo(BigInteger.valueOf((long) Math.pow(RANGE_MULTIPLIER, count))) < 0) {
                    throw new IllegalArgumentException("Invalid Pattern"
                            + " [" + compactPatterns[patternIndex]
                            + "]: min integer digits specified exceeds the limit"
                            + " for the index " + patternIndex);
                }
                divisor = bigValue.divide(BigInteger.valueOf((long) Math.pow(RANGE_MULTIPLIER, count)));
            } else {
                long longValue = (long) matchedValue;
                if (longValue < (long) Math.pow(RANGE_MULTIPLIER, count)) {
                    throw new IllegalArgumentException("Invalid Pattern"
                            + " [" + compactPatterns[patternIndex]
                            + "]: min integer digits specified exceeds the limit"
                            + " for the index " + patternIndex);
                }
                divisor = longValue / (long) Math.pow(RANGE_MULTIPLIER, count);
            }
        }
        return divisor;
    }

    /**
     * Process the series of compact patterns to compute the
     * series of prefixes, suffixes and their respective divisor
     * value.
     *
     */
    private void processCompactPatterns() {
        int size = compactPatterns.length;
        positivePrefixPatterns = new ArrayList<>(size);
        negativePrefixPatterns = new ArrayList<>(size);
        positiveSuffixPatterns = new ArrayList<>(size);
        negativeSuffixPatterns = new ArrayList<>(size);
        divisors = new ArrayList<>(size);

        for (int index = 0; index < size; index++) {
            applyPattern(compactPatterns[index], index);
        }
    }

    /**
     * Process a compact pattern at a specific {@code index}
     * @param pattern the compact pattern to be processed
     * @param index index in the array of compact patterns
     *
     */
    private void applyPattern(String pattern, int index) {

        int start = 0;
        boolean gotNegative = false;

        String positivePrefix = "";
        String positiveSuffix = "";
        String negativePrefix = "";
        String negativeSuffix = "";
        String zeros = "";
        for (int j = 1; j >= 0 && start < pattern.length(); --j) {

            StringBuffer prefix = new StringBuffer();
            StringBuffer suffix = new StringBuffer();
            boolean inQuote = false;
            // The phase ranges from 0 to 2.  Phase 0 is the prefix.  Phase 1 is
            // the section of the pattern with digits. Phase 2 is the suffix.
            // The separation of the characters into phases is
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
                                if ((pos + 1) < pattern.length()
                                        && pattern.charAt(pos + 1) == QUOTE) {
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
                            switch (ch) {
                                case ZERO_DIGIT:
                                    phase = 1;
                                    --pos; // Reprocess this character
                                    continue;
                                case QUOTE:
                                    // A quote outside quotes indicates either the
                                    // opening quote or two quotes, which is a quote
                                    // literal. That is, we have the first quote in 'do'
                                    // or o''clock.
                                    if ((pos + 1) < pattern.length()
                                            && pattern.charAt(pos + 1) == QUOTE) {
                                        ++pos;
                                        affix.append("''"); // o''clock
                                    } else {
                                        inQuote = true; // 'do'
                                    }
                                    continue;
                                case SEPARATOR:
                                    // Don't allow separators before we see digit
                                    // characters of phase 1, and don't allow separators
                                    // in the second pattern (j == 0).
                                    if (phase == 0 || j == 0) {
                                        throw new IllegalArgumentException(
                                                "Unquoted special character '"
                                                + ch + "' in pattern \"" + pattern + "\"");
                                    }
                                    start = pos + 1;
                                    pos = pattern.length();
                                    continue;
                                case MINUS_SIGN:
                                    affix.append("'-");
                                    continue;
                                case DECIMAL_SEPARATOR:
                                case GROUPING_SEPARATOR:
                                case DIGIT:
                                case PERCENT:
                                case PER_MILLE:
                                case CURRENCY_SIGN:
                                    throw new IllegalArgumentException(
                                            "Unquoted special character '" + ch
                                            + "' in pattern \"" + pattern + "\"");
                                default:
                                    break;
                            }
                        }
                        // Note that if we are within quotes, or if this is an
                        // unquoted, non-special character, then we usually fall
                        // through to here.
                        affix.append(ch);
                        break;

                    case 1:
                        // The negative subpattern (j = 0) serves only to specify the
                        // negative prefix and suffix, so all the phase 1 characters,
                        // for example, digits, zeroDigit, groupingSeparator,
                        // decimalSeparator, exponent are ignored
                        if (j == 0) {
                            while (pos < pattern.length()) {
                                char negPatternChar = pattern.charAt(pos);
                                if (negPatternChar == ZERO_DIGIT) {
                                    ++pos;
                                } else {
                                    // Not a phase 1 character, consider it as
                                    // suffix and parse it in phase 2
                                    --pos; //process it again in outer loop
                                    phase = 2;
                                    affix = suffix;
                                    break;
                                }
                            }
                            continue;
                        }
                        // Consider only '0' as valid pattern char which can appear
                        // in number part, rest can be either suffix or prefix
                        if (ch == ZERO_DIGIT) {
                            zeros = zeros + "0";
                        } else {
                            phase = 2;
                            affix = suffix;
                            --pos;
                        }
                        break;
                }
            }

            if (inQuote) {
                throw new IllegalArgumentException("Invalid single quote"
                        + " in pattern \"" + pattern + "\"");
            }

            if (j == 1) {
                positivePrefix = prefix.toString();
                positiveSuffix = suffix.toString();
                negativePrefix = positivePrefix;
                negativeSuffix = positiveSuffix;
            } else {
                negativePrefix = prefix.toString();
                negativeSuffix = suffix.toString();
                gotNegative = true;
            }

            // If there is no negative pattern, or if the negative pattern is
            // identical to the positive pattern, then prepend the minus sign to
            // the positive pattern to form the negative pattern.
            if (!gotNegative
                    || (negativePrefix.equals(positivePrefix)
                    && negativeSuffix.equals(positiveSuffix))) {
                negativeSuffix = positiveSuffix;
                negativePrefix = "'-" + positivePrefix;
            }
        }

        // If no 0s are specified in a non empty pattern, it is invalid
        if (!pattern.isEmpty() && zeros.isEmpty()) {
            throw new IllegalArgumentException("Invalid pattern"
                    + " [" + pattern + "]: all patterns must include digit"
                    + " placement 0s");
        }

        // Only if positive affix exists; else put empty strings
        if (!positivePrefix.isEmpty() || !positiveSuffix.isEmpty()) {
            positivePrefixPatterns.add(positivePrefix);
            negativePrefixPatterns.add(negativePrefix);
            positiveSuffixPatterns.add(positiveSuffix);
            negativeSuffixPatterns.add(negativeSuffix);
            divisors.add(computeDivisor(zeros, index));
        } else {
            positivePrefixPatterns.add("");
            negativePrefixPatterns.add("");
            positiveSuffixPatterns.add("");
            negativeSuffixPatterns.add("");
            divisors.add(1L);
        }
    }

    private final transient DigitList digitList = new DigitList();
    private static final int STATUS_INFINITE = 0;
    private static final int STATUS_POSITIVE = 1;
    private static final int STATUS_LENGTH   = 2;

    private static final char ZERO_DIGIT = '0';
    private static final char DIGIT = '#';
    private static final char DECIMAL_SEPARATOR = '.';
    private static final char GROUPING_SEPARATOR = ',';
    private static final char MINUS_SIGN = '-';
    private static final char PERCENT = '%';
    private static final char PER_MILLE = '\u2030';
    private static final char SEPARATOR = ';';
    private static final char CURRENCY_SIGN = '\u00A4';
    private static final char QUOTE = '\'';

    // Expanded form of positive/negative prefix/suffix,
    // the expanded form contains special characters in
    // its localized form, which are used for matching
    // while parsing a string to number
    private transient List<String> positivePrefixes;
    private transient List<String> negativePrefixes;
    private transient List<String> positiveSuffixes;
    private transient List<String> negativeSuffixes;

    private void expandAffixPatterns() {
        positivePrefixes = new ArrayList<>(compactPatterns.length);
        negativePrefixes = new ArrayList<>(compactPatterns.length);
        positiveSuffixes = new ArrayList<>(compactPatterns.length);
        negativeSuffixes = new ArrayList<>(compactPatterns.length);
        for (int index = 0; index < compactPatterns.length; index++) {
            positivePrefixes.add(expandAffix(positivePrefixPatterns.get(index)));
            negativePrefixes.add(expandAffix(negativePrefixPatterns.get(index)));
            positiveSuffixes.add(expandAffix(positiveSuffixPatterns.get(index)));
            negativeSuffixes.add(expandAffix(negativeSuffixPatterns.get(index)));
        }
    }

    /**
     * Parses a compact number from a string to produce a {@code Number}.
     * <p>
     * The method attempts to parse text starting at the index given by
     * {@code pos}.
     * If parsing succeeds, then the index of {@code pos} is updated
     * to the index after the last character used (parsing does not necessarily
     * use all characters up to the end of the string), and the parsed
     * number is returned. The updated {@code pos} can be used to
     * indicate the starting point for the next call to this method.
     * If an error occurs, then the index of {@code pos} is not
     * changed, the error index of {@code pos} is set to the index of
     * the character where the error occurred, and {@code null} is returned.
     * <p>
     * The value is the numeric part in the given text multiplied
     * by the numeric equivalent of the affix attached
     * (For example, "K" = 1000 in {@link java.util.Locale#US US locale}).
     * The subclass returned depends on the value of
     * {@link #isParseBigDecimal}.
     * <ul>
     * <li>If {@link #isParseBigDecimal()} is false (the default),
     *     most integer values are returned as {@code Long}
     *     objects, no matter how they are written: {@code "17K"} and
     *     {@code "17.000K"} both parse to {@code Long.valueOf(17000)}.
     *     If the value cannot fit into {@code Long}, then the result is
     *     returned as {@code Double}. This includes values with a
     *     fractional part, infinite values, {@code NaN},
     *     and the value -0.0.
     *     <p>
     *     Callers may use the {@code Number} methods {@code doubleValue},
     *     {@code longValue}, etc., to obtain the type they want.
     *
     * <li>If {@link #isParseBigDecimal()} is true, values are returned
     *     as {@code BigDecimal} objects. The special cases negative
     *     and positive infinity and NaN are returned as {@code Double}
     *     instances holding the values of the corresponding
     *     {@code Double} constants.
     * </ul>
     * <p>
     * {@code CompactNumberFormat} parses all Unicode characters that represent
     * decimal digits, as defined by {@code Character.digit()}. In
     * addition, {@code CompactNumberFormat} also recognizes as digits the ten
     * consecutive characters starting with the localized zero digit defined in
     * the {@code DecimalFormatSymbols} object.
     * <p>
     * {@code CompactNumberFormat} parse does not allow parsing scientific
     * notations. For example, parsing a string {@code "1.05E4K"} in
     * {@link java.util.Locale#US US locale} breaks at character 'E'
     * and returns 1.05.
     *
     * @param text the string to be parsed
     * @param pos  a {@code ParsePosition} object with index and error
     *             index information as described above
     * @return the parsed value, or {@code null} if the parse fails
     * @exception  NullPointerException if {@code text} or
     *             {@code pos} is null
     *
     */
    @Override
    public Number parse(String text, ParsePosition pos) {

        Objects.requireNonNull(text);
        Objects.requireNonNull(pos);

        // Lazily expanding the affix patterns, on the first parse
        // call on this instance
        // If not initialized, expand and load all affixes
        if (positivePrefixes == null) {
            expandAffixPatterns();
        }

        // The compact number multiplier for parsed string.
        // Its value is set on parsing prefix and suffix. For example,
        // in the {@link java.util.Locale#US US locale} parsing {@code "1K"}
        // sets its value to 1000, as K (thousand) is abbreviated form of 1000.
        Number cnfMultiplier = 1L;

        // Special case NaN
        if (text.regionMatches(pos.index, symbols.getNaN(),
                0, symbols.getNaN().length())) {
            pos.index = pos.index + symbols.getNaN().length();
            return Double.NaN;
        }

        int position = pos.index;
        int oldStart = pos.index;
        boolean gotPositive = false;
        boolean gotNegative = false;
        int matchedPosIndex = -1;
        int matchedNegIndex = -1;
        String matchedPosPrefix = "";
        String matchedNegPrefix = "";
        String defaultPosPrefix = defaultDecimalFormat.getPositivePrefix();
        String defaultNegPrefix = defaultDecimalFormat.getNegativePrefix();
        // Prefix matching
        for (int compactIndex = 0; compactIndex < compactPatterns.length; compactIndex++) {
            String positivePrefix = positivePrefixes.get(compactIndex);
            String negativePrefix = negativePrefixes.get(compactIndex);

            // Do not break if a match occur; there is a possibility that the
            // subsequent affixes may match the longer subsequence in the given
            // string.
            // For example, matching "Mdx 3" with "M", "Md" as prefix should
            // match with "Md"
            boolean match = matchAffix(text, position, positivePrefix,
                    defaultPosPrefix, matchedPosPrefix);
            if (match) {
                matchedPosIndex = compactIndex;
                matchedPosPrefix = positivePrefix;
                gotPositive = true;
            }

            match = matchAffix(text, position, negativePrefix,
                    defaultNegPrefix, matchedNegPrefix);
            if (match) {
                matchedNegIndex = compactIndex;
                matchedNegPrefix = negativePrefix;
                gotNegative = true;
            }
        }

        // Given text does not match the non empty valid compact prefixes
        // check with the default prefixes
        if (!gotPositive && !gotNegative) {
            if (text.regionMatches(pos.index, defaultPosPrefix, 0,
                    defaultPosPrefix.length())) {
                // Matches the default positive prefix
                matchedPosPrefix = defaultPosPrefix;
                gotPositive = true;
            }
            if (text.regionMatches(pos.index, defaultNegPrefix, 0,
                    defaultNegPrefix.length())) {
                // Matches the default negative prefix
                matchedNegPrefix = defaultNegPrefix;
                gotNegative = true;
            }
        }

        // If both match, take the longest one
        if (gotPositive && gotNegative) {
            if (matchedPosPrefix.length() > matchedNegPrefix.length()) {
                gotNegative = false;
            } else if (matchedPosPrefix.length() < matchedNegPrefix.length()) {
                gotPositive = false;
            }
        }

        // Update the position and take compact multiplier
        // only if it matches the compact prefix, not the default
        // prefix; else multiplier should be 1
        if (gotPositive) {
            position += matchedPosPrefix.length();
            cnfMultiplier = matchedPosIndex != -1
                    ? divisors.get(matchedPosIndex) : 1L;
        } else if (gotNegative) {
            position += matchedNegPrefix.length();
            cnfMultiplier = matchedNegIndex != -1
                    ? divisors.get(matchedNegIndex) : 1L;
        }

        digitList.setRoundingMode(getRoundingMode());
        boolean[] status = new boolean[STATUS_LENGTH];

        // Call DecimalFormat.subparseNumber() method to parse the
        // number part of the input text
        position = decimalFormat.subparseNumber(text, position,
                digitList, false, false, status);

        if (position == -1) {
            // Unable to parse the number successfully
            pos.index = oldStart;
            pos.errorIndex = oldStart;
            return null;
        }

        // If parse integer only is true and the parsing is broken at
        // decimal point, then pass/ignore all digits and move pointer
        // at the start of suffix, to process the suffix part
        if (isParseIntegerOnly()
                && text.charAt(position) == symbols.getDecimalSeparator()) {
            position++; // Pass decimal character
            for (; position < text.length(); ++position) {
                char ch = text.charAt(position);
                int digit = ch - symbols.getZeroDigit();
                if (digit < 0 || digit > 9) {
                    digit = Character.digit(ch, 10);
                    // Parse all digit characters
                    if (!(digit >= 0 && digit <= 9)) {
                        break;
                    }
                }
            }
        }

        // Number parsed successfully; match prefix and
        // suffix to obtain multiplier
        pos.index = position;
        Number multiplier = computeParseMultiplier(text, pos,
                gotPositive ? matchedPosPrefix : matchedNegPrefix,
                status, gotPositive, gotNegative);

        if (multiplier.longValue() == -1L) {
            return null;
        } else if (multiplier.longValue() != 1L) {
            cnfMultiplier = multiplier;
        }

        // Special case INFINITY
        if (status[STATUS_INFINITE]) {
            if (status[STATUS_POSITIVE]) {
                return Double.POSITIVE_INFINITY;
            } else {
                return Double.NEGATIVE_INFINITY;
            }
        }

        if (isParseBigDecimal()) {
            BigDecimal bigDecimalResult = digitList.getBigDecimal();

            if (cnfMultiplier.longValue() != 1) {
                bigDecimalResult = bigDecimalResult
                        .multiply(new BigDecimal(cnfMultiplier.toString()));
            }
            if (!status[STATUS_POSITIVE]) {
                bigDecimalResult = bigDecimalResult.negate();
            }
            return bigDecimalResult;
        } else {
            Number cnfResult;
            if (digitList.fitsIntoLong(status[STATUS_POSITIVE], isParseIntegerOnly())) {
                long longResult = digitList.getLong();
                cnfResult = generateParseResult(longResult, false,
                        longResult < 0, status, cnfMultiplier);
            } else {
                cnfResult = generateParseResult(digitList.getDouble(),
                        true, false, status, cnfMultiplier);
            }
            return cnfResult;
        }
    }

    /**
     * Returns the parsed result by multiplying the parsed number
     * with the multiplier representing the prefix and suffix.
     *
     * @param number parsed number component
     * @param gotDouble whether the parsed number contains decimal
     * @param gotLongMin whether the parsed number is Long.MIN
     * @param status boolean status flags indicating whether the
     *               value is infinite and whether it is positive
     * @param cnfMultiplier compact number multiplier
     * @return parsed result
     */
    private Number generateParseResult(Number number, boolean gotDouble,
            boolean gotLongMin, boolean[] status, Number cnfMultiplier) {

        if (gotDouble) {
            if (cnfMultiplier.longValue() != 1L) {
                double doubleResult = number.doubleValue() * cnfMultiplier.doubleValue();
                doubleResult = (double) convertIfNegative(doubleResult, status, gotLongMin);
                // Check if a double can be represeneted as a long
                long longResult = (long) doubleResult;
                gotDouble = ((doubleResult != (double) longResult)
                        || (doubleResult == 0.0 && 1 / doubleResult < 0.0));
                return gotDouble ? (Number) doubleResult : (Number) longResult;
            }
        } else {
            if (cnfMultiplier.longValue() != 1L) {
                Number result;
                if ((cnfMultiplier instanceof Long) && !gotLongMin) {
                    long longMultiplier = (long) cnfMultiplier;
                    try {
                        result = Math.multiplyExact(number.longValue(),
                                longMultiplier);
                    } catch (ArithmeticException ex) {
                        // If number * longMultiplier can not be represented
                        // as long return as double
                        result = number.doubleValue() * cnfMultiplier.doubleValue();
                    }
                } else {
                    // cnfMultiplier can not be stored into long or the number
                    // part is Long.MIN, return as double
                    result = number.doubleValue() * cnfMultiplier.doubleValue();
                }
                return convertIfNegative(result, status, gotLongMin);
            }
        }

        // Default number
        return convertIfNegative(number, status, gotLongMin);
    }

    /**
     * Negate the parsed value if the positive status flag is false
     * and the value is not a Long.MIN
     * @param number parsed value
     * @param status boolean status flags indicating whether the
     *               value is infinite and whether it is positive
     * @param gotLongMin whether the parsed number is Long.MIN
     * @return the resulting value
     */
    private Number convertIfNegative(Number number, boolean[] status,
            boolean gotLongMin) {

        if (!status[STATUS_POSITIVE] && !gotLongMin) {
            if (number instanceof Long) {
                return -(long) number;
            } else {
                return -(double) number;
            }
        } else {
            return number;
        }
    }

    /**
     * Attempts to match the given {@code affix} in the
     * specified {@code text}.
     */
    private boolean matchAffix(String text, int position, String affix,
            String defaultAffix, String matchedAffix) {

        // Check with the compact affixes which are non empty and
        // do not match with default affix
        if (!affix.isEmpty() && !affix.equals(defaultAffix)) {
            // Look ahead only for the longer match than the previous match
            if (matchedAffix.length() < affix.length()) {
                if (text.regionMatches(position, affix, 0, affix.length())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to match given {@code prefix} and {@code suffix} in
     * the specified {@code text}.
     */
    private boolean matchPrefixAndSuffix(String text, int position, String prefix,
            String matchedPrefix, String defaultPrefix, String suffix,
            String matchedSuffix, String defaultSuffix) {

        // Check the compact pattern suffix only if there is a
        // compact prefix match or a default prefix match
        // because the compact prefix and suffix should match at the same
        // index to obtain the multiplier.
        // The prefix match is required because of the possibility of
        // same prefix at multiple index, in which case matching the suffix
        // is used to obtain the single match

        if (prefix.equals(matchedPrefix)
                || matchedPrefix.equals(defaultPrefix)) {
            return matchAffix(text, position, suffix, defaultSuffix, matchedSuffix);
        }
        return false;
    }

    /**
     * Computes multiplier by matching the given {@code matchedPrefix}
     * and suffix in the specified {@code text} from the lists of
     * prefixes and suffixes extracted from compact patterns.
     *
     * @param text the string to parse
     * @param parsePosition the {@code ParsePosition} object representing the
     *                      index and error index of the parse string
     * @param matchedPrefix prefix extracted which needs to be matched to
     *                      obtain the multiplier
     * @param status upon return contains boolean status flags indicating
     *               whether the value is positive
     * @param gotPositive based on the prefix parsed; whether the number is positive
     * @param gotNegative based on the prefix parsed; whether the number is negative
     * @return the multiplier matching the prefix and suffix; -1 otherwise
     */
    private Number computeParseMultiplier(String text, ParsePosition parsePosition,
            String matchedPrefix, boolean[] status, boolean gotPositive,
            boolean gotNegative) {

        int position = parsePosition.index;
        boolean gotPos = false;
        boolean gotNeg = false;
        int matchedPosIndex = -1;
        int matchedNegIndex = -1;
        String matchedPosSuffix = "";
        String matchedNegSuffix = "";
        for (int compactIndex = 0; compactIndex < compactPatterns.length; compactIndex++) {
            String positivePrefix = positivePrefixes.get(compactIndex);
            String negativePrefix = negativePrefixes.get(compactIndex);
            String positiveSuffix = positiveSuffixes.get(compactIndex);
            String negativeSuffix = negativeSuffixes.get(compactIndex);

            // Do not break if a match occur; there is a possibility that the
            // subsequent affixes may match the longer subsequence in the given
            // string.
            // For example, matching "3Mdx" with "M", "Md" should match with "Md"
            boolean match = matchPrefixAndSuffix(text, position, positivePrefix, matchedPrefix,
                    defaultDecimalFormat.getPositivePrefix(), positiveSuffix,
                    matchedPosSuffix, defaultDecimalFormat.getPositiveSuffix());
            if (match) {
                matchedPosIndex = compactIndex;
                matchedPosSuffix = positiveSuffix;
                gotPos = true;
            }

            match = matchPrefixAndSuffix(text, position, negativePrefix, matchedPrefix,
                    defaultDecimalFormat.getNegativePrefix(), negativeSuffix,
                    matchedNegSuffix, defaultDecimalFormat.getNegativeSuffix());
            if (match) {
                matchedNegIndex = compactIndex;
                matchedNegSuffix = negativeSuffix;
                gotNeg = true;
            }
        }

        // Suffix in the given text does not match with the compact
        // patterns suffixes; match with the default suffix
        if (!gotPos && !gotNeg) {
            String positiveSuffix = defaultDecimalFormat.getPositiveSuffix();
            String negativeSuffix = defaultDecimalFormat.getNegativeSuffix();
            if (text.regionMatches(position, positiveSuffix, 0,
                    positiveSuffix.length())) {
                // Matches the default positive prefix
                matchedPosSuffix = positiveSuffix;
                gotPos = true;
            }
            if (text.regionMatches(position, negativeSuffix, 0,
                    negativeSuffix.length())) {
                // Matches the default negative suffix
                matchedNegSuffix = negativeSuffix;
                gotNeg = true;
            }
        }

        // If both matches, take the longest one
        if (gotPos && gotNeg) {
            if (matchedPosSuffix.length() > matchedNegSuffix.length()) {
                gotNeg = false;
            } else if (matchedPosSuffix.length() < matchedNegSuffix.length()) {
                gotPos = false;
            } else {
                // If longest comparison fails; take the positive and negative
                // sign of matching prefix
                gotPos = gotPositive;
                gotNeg = gotNegative;
            }
        }

        // Fail if neither or both
        if (gotPos == gotNeg) {
            parsePosition.errorIndex = position;
            return -1L;
        }

        Number cnfMultiplier;
        // Update the parse position index and take compact multiplier
        // only if it matches the compact suffix, not the default
        // suffix; else multiplier should be 1
        if (gotPos) {
            parsePosition.index = position + matchedPosSuffix.length();
            cnfMultiplier = matchedPosIndex != -1
                    ? divisors.get(matchedPosIndex) : 1L;
        } else {
            parsePosition.index = position + matchedNegSuffix.length();
            cnfMultiplier = matchedNegIndex != -1
                    ? divisors.get(matchedNegIndex) : 1L;
        }
        status[STATUS_POSITIVE] = gotPos;
        return cnfMultiplier;
    }

    /**
     * Reconstitutes this {@code CompactNumberFormat} from a stream
     * (that is, deserializes it) after performing some validations.
     * This method throws InvalidObjectException, if the stream data is invalid
     * because of the following reasons,
     * <ul>
     * <li> If any of the {@code decimalPattern}, {@code compactPatterns},
     * {@code symbols} or {@code roundingMode} is {@code null}.
     * <li> If the {@code decimalPattern} or the {@code compactPatterns} array
     * contains an invalid pattern or if a {@code null} appears in the array of
     * compact patterns.
     * <li> If the {@code minimumIntegerDigits} is greater than the
     * {@code maximumIntegerDigits} or the {@code minimumFractionDigits} is
     * greater than the {@code maximumFractionDigits}. This check is performed
     * by superclass's Object.
     * <li> If any of the minimum/maximum integer/fraction digit count is
     * negative. This check is performed by superclass's readObject.
     * <li> If the minimum or maximum integer digit count is larger than 309 or
     * if the minimum or maximum fraction digit count is larger than 340.
     * <li> If the grouping size is negative or larger than 127.
     * </ul>
     *
     * @param inStream the stream
     * @throws IOException if an I/O error occurs
     * @throws ClassNotFoundException if the class of a serialized object
     *         could not be found
     */
    private void readObject(ObjectInputStream inStream) throws IOException,
            ClassNotFoundException {

        inStream.defaultReadObject();
        if (decimalPattern == null || compactPatterns == null
                || symbols == null || roundingMode == null) {
            throw new InvalidObjectException("One of the 'decimalPattern',"
                    + " 'compactPatterns', 'symbols' or 'roundingMode'"
                    + " is null");
        }

        // Check only the maximum counts because NumberFormat.readObject has
        // already ensured that the maximum is greater than the minimum count.
        if (getMaximumIntegerDigits() > DecimalFormat.DOUBLE_INTEGER_DIGITS
                || getMaximumFractionDigits() > DecimalFormat.DOUBLE_FRACTION_DIGITS) {
            throw new InvalidObjectException("Digit count out of range");
        }

        // Check if the grouping size is negative, on an attempt to
        // put value > 127, it wraps around, so check just negative value
        if (groupingSize < 0) {
            throw new InvalidObjectException("Grouping size is negative");
        }

        try {
            processCompactPatterns();
        } catch (IllegalArgumentException ex) {
            throw new InvalidObjectException(ex.getMessage());
        }

        decimalFormat = new DecimalFormat(SPECIAL_PATTERN, symbols);
        decimalFormat.setMaximumFractionDigits(getMaximumFractionDigits());
        decimalFormat.setMinimumFractionDigits(getMinimumFractionDigits());
        decimalFormat.setMaximumIntegerDigits(getMaximumIntegerDigits());
        decimalFormat.setMinimumIntegerDigits(getMinimumIntegerDigits());
        decimalFormat.setRoundingMode(getRoundingMode());
        decimalFormat.setGroupingSize(getGroupingSize());
        decimalFormat.setGroupingUsed(isGroupingUsed());
        decimalFormat.setParseIntegerOnly(isParseIntegerOnly());

        try {
            defaultDecimalFormat = new DecimalFormat(decimalPattern, symbols);
            defaultDecimalFormat.setMaximumFractionDigits(0);
        } catch (IllegalArgumentException ex) {
            throw new InvalidObjectException(ex.getMessage());
        }

    }

    /**
     * Sets the maximum number of digits allowed in the integer portion of a
     * number.
     * The maximum allowed integer range is 309, if the {@code newValue} &gt; 309,
     * then the maximum integer digits count is set to 309. Negative input
     * values are replaced with 0.
     *
     * @param newValue the maximum number of integer digits to be shown
     * @see #getMaximumIntegerDigits()
     */
    @Override
    public void setMaximumIntegerDigits(int newValue) {
        // The maximum integer digits is checked with the allowed range before calling
        // the DecimalFormat.setMaximumIntegerDigits, which performs the negative check
        // on the given newValue while setting it as max integer digits.
        // For example, if a negative value is specified, it is replaced with 0
        decimalFormat.setMaximumIntegerDigits(Math.min(newValue,
                DecimalFormat.DOUBLE_INTEGER_DIGITS));
        super.setMaximumIntegerDigits(decimalFormat.getMaximumIntegerDigits());
        if (decimalFormat.getMinimumIntegerDigits() > decimalFormat.getMaximumIntegerDigits()) {
            decimalFormat.setMinimumIntegerDigits(decimalFormat.getMaximumIntegerDigits());
            super.setMinimumIntegerDigits(decimalFormat.getMinimumIntegerDigits());
        }
    }

    /**
     * Sets the minimum number of digits allowed in the integer portion of a
     * number.
     * The maximum allowed integer range is 309, if the {@code newValue} &gt; 309,
     * then the minimum integer digits count is set to 309. Negative input
     * values are replaced with 0.
     *
     * @param newValue the minimum number of integer digits to be shown
     * @see #getMinimumIntegerDigits()
     */
    @Override
    public void setMinimumIntegerDigits(int newValue) {
        // The minimum integer digits is checked with the allowed range before calling
        // the DecimalFormat.setMinimumIntegerDigits, which performs check on the given
        // newValue while setting it as min integer digits. For example, if a negative
        // value is specified, it is replaced with 0
        decimalFormat.setMinimumIntegerDigits(Math.min(newValue,
                DecimalFormat.DOUBLE_INTEGER_DIGITS));
        super.setMinimumIntegerDigits(decimalFormat.getMinimumIntegerDigits());
        if (decimalFormat.getMinimumIntegerDigits() > decimalFormat.getMaximumIntegerDigits()) {
            decimalFormat.setMaximumIntegerDigits(decimalFormat.getMinimumIntegerDigits());
            super.setMaximumIntegerDigits(decimalFormat.getMaximumIntegerDigits());
        }
    }

    /**
     * Sets the minimum number of digits allowed in the fraction portion of a
     * number.
     * The maximum allowed fraction range is 340, if the {@code newValue} &gt; 340,
     * then the minimum fraction digits count is set to 340. Negative input
     * values are replaced with 0.
     *
     * @param newValue the minimum number of fraction digits to be shown
     * @see #getMinimumFractionDigits()
     */
    @Override
    public void setMinimumFractionDigits(int newValue) {
        // The minimum fraction digits is checked with the allowed range before
        // calling the DecimalFormat.setMinimumFractionDigits, which performs
        // check on the given newValue while setting it as min fraction
        // digits. For example, if a negative value is specified, it is
        // replaced with 0
        decimalFormat.setMinimumFractionDigits(Math.min(newValue,
                DecimalFormat.DOUBLE_FRACTION_DIGITS));
        super.setMinimumFractionDigits(decimalFormat.getMinimumFractionDigits());
        if (decimalFormat.getMinimumFractionDigits() > decimalFormat.getMaximumFractionDigits()) {
            decimalFormat.setMaximumFractionDigits(decimalFormat.getMinimumFractionDigits());
            super.setMaximumFractionDigits(decimalFormat.getMaximumFractionDigits());
        }
    }

    /**
     * Sets the maximum number of digits allowed in the fraction portion of a
     * number.
     * The maximum allowed fraction range is 340, if the {@code newValue} &gt; 340,
     * then the maximum fraction digits count is set to 340. Negative input
     * values are replaced with 0.
     *
     * @param newValue the maximum number of fraction digits to be shown
     * @see #getMaximumFractionDigits()
     */
    @Override
    public void setMaximumFractionDigits(int newValue) {
        // The maximum fraction digits is checked with the allowed range before
        // calling the DecimalFormat.setMaximumFractionDigits, which performs
        // check on the given newValue while setting it as max fraction digits.
        // For example, if a negative value is specified, it is replaced with 0
        decimalFormat.setMaximumFractionDigits(Math.min(newValue,
                DecimalFormat.DOUBLE_FRACTION_DIGITS));
        super.setMaximumFractionDigits(decimalFormat.getMaximumFractionDigits());
        if (decimalFormat.getMinimumFractionDigits() > decimalFormat.getMaximumFractionDigits()) {
            decimalFormat.setMinimumFractionDigits(decimalFormat.getMaximumFractionDigits());
            super.setMinimumFractionDigits(decimalFormat.getMinimumFractionDigits());
        }
    }

    /**
     * Gets the {@link java.math.RoundingMode} used in this
     * {@code CompactNumberFormat}.
     *
     * @return the {@code RoundingMode} used for this
     *         {@code CompactNumberFormat}
     * @see #setRoundingMode(RoundingMode)
     */
    @Override
    public RoundingMode getRoundingMode() {
        return roundingMode;
    }

    /**
     * Sets the {@link java.math.RoundingMode} used in this
     * {@code CompactNumberFormat}.
     *
     * @param roundingMode the {@code RoundingMode} to be used
     * @see #getRoundingMode()
     * @throws NullPointerException if {@code roundingMode} is {@code null}
     */
    @Override
    public void setRoundingMode(RoundingMode roundingMode) {
        decimalFormat.setRoundingMode(roundingMode);
        this.roundingMode = roundingMode;
    }

    /**
     * Returns the grouping size. Grouping size is the number of digits between
     * grouping separators in the integer portion of a number. For example,
     * in the compact number {@code "12,347 trillion"} for the
     * {@link java.util.Locale#US US locale}, the grouping size is 3.
     *
     * @return the grouping size
     * @see #setGroupingSize
     * @see java.text.NumberFormat#isGroupingUsed
     * @see java.text.DecimalFormatSymbols#getGroupingSeparator
     */
    public int getGroupingSize() {
        return groupingSize;
    }

    /**
     * Sets the grouping size. Grouping size is the number of digits between
     * grouping separators in the integer portion of a number. For example,
     * in the compact number {@code "12,347 trillion"} for the
     * {@link java.util.Locale#US US locale}, the grouping size is 3. The grouping
     * size must be greater than or equal to zero and less than or equal to 127.
     *
     * @param newValue the new grouping size
     * @see #getGroupingSize
     * @see java.text.NumberFormat#setGroupingUsed
     * @see java.text.DecimalFormatSymbols#setGroupingSeparator
     * @throws IllegalArgumentException if {@code newValue} is negative or
     * larger than 127
     */
    public void setGroupingSize(int newValue) {
        if (newValue < 0 || newValue > 127) {
            throw new IllegalArgumentException(
                    "The value passed is negative or larger than 127");
        }
        groupingSize = (byte) newValue;
        decimalFormat.setGroupingSize(groupingSize);
    }

    /**
     * Returns true if grouping is used in this format. For example, with
     * grouping on and grouping size set to 3, the number {@code 12346567890987654}
     * can be formatted as {@code "12,347 trillion"} in the
     * {@link java.util.Locale#US US locale}.
     * The grouping separator is locale dependent.
     *
     * @return {@code true} if grouping is used;
     *         {@code false} otherwise
     * @see #setGroupingUsed
     */
    @Override
    public boolean isGroupingUsed() {
        return super.isGroupingUsed();
    }

    /**
     * Sets whether or not grouping will be used in this format.
     *
     * @param newValue {@code true} if grouping is used;
     *                 {@code false} otherwise
     * @see #isGroupingUsed
     */
    @Override
    public void setGroupingUsed(boolean newValue) {
        decimalFormat.setGroupingUsed(newValue);
        super.setGroupingUsed(newValue);
    }

    /**
     * Returns true if this format parses only an integer from the number
     * component of a compact number.
     * Parsing an integer means that only an integer is considered from the
     * number component, prefix/suffix is still considered to compute the
     * resulting output.
     * For example, in the {@link java.util.Locale#US US locale}, if this method
     * returns {@code true}, the string {@code "1234.78 thousand"} would be
     * parsed as the value {@code 1234000} (1234 (integer part) * 1000
     * (thousand)) and the fractional part would be skipped.
     * The exact format accepted by the parse operation is locale dependent.
     *
     * @return {@code true} if compact numbers should be parsed as integers
     *         only; {@code false} otherwise
     */
    @Override
    public boolean isParseIntegerOnly() {
        return super.isParseIntegerOnly();
    }

    /**
     * Sets whether or not this format parses only an integer from the number
     * component of a compact number.
     *
     * @param value {@code true} if compact numbers should be parsed as
     *              integers only; {@code false} otherwise
     * @see #isParseIntegerOnly
     */
    @Override
    public void setParseIntegerOnly(boolean value) {
        decimalFormat.setParseIntegerOnly(value);
        super.setParseIntegerOnly(value);
    }

    /**
     * Returns whether the {@link #parse(String, ParsePosition)}
     * method returns {@code BigDecimal}. The default value is false.
     *
     * @return {@code true} if the parse method returns BigDecimal;
     *         {@code false} otherwise
     * @see #setParseBigDecimal
     *
     */
    public boolean isParseBigDecimal() {
        return parseBigDecimal;
    }

    /**
     * Sets whether the {@link #parse(String, ParsePosition)}
     * method returns {@code BigDecimal}.
     *
     * @param newValue {@code true} if the parse method returns BigDecimal;
     *                 {@code false} otherwise
     * @see #isParseBigDecimal
     *
     */
    public void setParseBigDecimal(boolean newValue) {
        parseBigDecimal = newValue;
    }

    /**
     * Checks if this {@code CompactNumberFormat} is equal to the
     * specified {@code obj}. The objects of type {@code CompactNumberFormat}
     * are compared, other types return false; obeys the general contract of
     * {@link java.lang.Object#equals(java.lang.Object) Object.equals}.
     *
     * @param obj the object to compare with
     * @return true if this is equal to the other {@code CompactNumberFormat}
     */
    @Override
    public boolean equals(Object obj) {

        if (!super.equals(obj)) {
            return false;
        }

        CompactNumberFormat other = (CompactNumberFormat) obj;
        return decimalPattern.equals(other.decimalPattern)
                && symbols.equals(other.symbols)
                && Arrays.equals(compactPatterns, other.compactPatterns)
                && roundingMode.equals(other.roundingMode)
                && groupingSize == other.groupingSize
                && parseBigDecimal == other.parseBigDecimal;
    }

    /**
     * Returns the hash code for this {@code CompactNumberFormat} instance.
     *
     * @return hash code for this {@code CompactNumberFormat}
     */
    @Override
    public int hashCode() {
        return 31 * super.hashCode() +
                Objects.hash(decimalPattern, symbols, roundingMode)
                + Arrays.hashCode(compactPatterns) + groupingSize
                + Boolean.hashCode(parseBigDecimal);
    }

    /**
     * Creates and returns a copy of this {@code CompactNumberFormat}
     * instance.
     *
     * @return a clone of this instance
     */
    @Override
    public CompactNumberFormat clone() {
        CompactNumberFormat other = (CompactNumberFormat) super.clone();
        other.compactPatterns = compactPatterns.clone();
        other.symbols = (DecimalFormatSymbols) symbols.clone();
        return other;
    }

}

