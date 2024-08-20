/*
 * Copyright (c) 1996, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.spi.NumberFormatProvider;
import java.util.Currency;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleServiceProviderPool;

/**
 * {@code NumberFormat} is the abstract base class for all number
 * formats. This class provides the interface for formatting and parsing
 * numbers in a localized manner. This enables code that can be completely
 * independent of the locale conventions for decimal points, thousands-separators,
 * the particular decimal digits used, or whether the number format is even
 * decimal. For example, this class could be used within an application to
 * produce a number in a currency format according to the conventions of the desired
 * locale.
 *
 * <h2 id="factory_methods">Getting a NumberFormat</h2>
 * To get a {@code NumberFormat} for the default Locale, use one of the static
 * factory methods that return a concrete subclass of {@code NumberFormat}.
 * The following formats all provide an example of formatting the {@code Number}
 * "2000.50" with the {@link java.util.Locale#US US} locale as the default locale.
 * <ul>
 * <li> Use {@link #getInstance()} or {@link #getNumberInstance()} to get
 * a decimal format. For example, {@code "2,000.5"}.
 * <li> Use {@link #getIntegerInstance()} to get an integer number format.
 * For example, {@code "2,000"}.
 * <li> Use {@link #getCurrencyInstance} to get a currency number format.
 * For example, {@code "$2,000.50"}.
 * <li> Use {@link #getCompactNumberInstance} to get a compact number format.
 * For example, {@code "2K"}.
 * <li> Use {@link #getPercentInstance} to get a format for displaying percentages.
 * For example, {@code "200,050%"}.
 * </ul>
 *
 * Alternatively, if a {@code NumberFormat} for a different locale is required, use
 * one of the overloaded factory methods that take {@code Locale} as a parameter,
 * for example, {@link #getIntegerInstance(Locale)}. If the installed locale-sensitive
 * service implementation does not support the given {@code Locale}, the parent
 * locale chain will be looked up, and a {@code Locale} used that is supported.
 *
 * <h3>Locale Extensions</h3>
 * Formatting behavior can be changed when using a locale that contains any of the following
 * <a href="../util/Locale.html#def_locale_extension">Unicode extensions</a>,
 * <ul>
 * <li> "nu"
 * (<a href="https://unicode.org/reports/tr35/#UnicodeNumberSystemIdentifier">
 * Numbering System</a>) - Overrides the decimal digits used
 * <li> "rg"
 * (<a href="https://unicode.org/reports/tr35/#RegionOverride">
 * Region Override</a>) - Overrides the country used
 * <li> "cf"
 * (<a href="https://www.unicode.org/reports/tr35/tr35.html#UnicodeCurrencyFormatIdentifier">
 * Currency Format style</a>) - Overrides the Currency Format style used
 * </ul>
 * <p>
 * If both "nu" and "rg" are specified, the decimal digits from the "nu"
 * extension supersedes the implicit one from the "rg" extension.
 * Although <a href="../util/Locale.html#def_locale_extension">Unicode extensions</a>
 * defines various keys and values, actual locale-sensitive service implementations
 * in a Java Runtime Environment might not support any particular Unicode locale
 * attributes or key/type pairs.
 * <p>Below is an example of a "US" locale currency format with accounting style,
 * <blockquote>{@code NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-US-u-cf-account"));}</blockquote>
 * With this style, a negative value is formatted enclosed in parentheses, instead
 * of being prepended with a minus sign.
 *
 * <h2>Using NumberFormat</h2>
 * The following is an example of formatting and parsing in a localized fashion,
 * {@snippet lang=java :
 * NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
 * currencyFormat.format(100000); // returns "$100,000.00"
 * currencyFormat.parse("$100,000.00"); // returns 100000
 * }
 *
 * <h2>Customizing NumberFormat</h2>
 * {@code NumberFormat} provides API to customize formatting and parsing behavior,
 * <ul>
 * <li> {@link #setParseIntegerOnly(boolean)}; when {@code true}, will only return the
 * integer portion of the number parsed from the String.
 * <li> {@link #setMinimumFractionDigits(int)}; Use to adjust the expected digits when
 * formatting. Use any of the other minimum/maximum or fraction/integer setter methods
 * in the same manner.
 * <li> {@link #setGroupingUsed(boolean)}; when {@code true}, formatted numbers will be displayed
 * with grouping separators. Additionally, when {@code false}, parsing will not expect
 * grouping separators in the parsed String.
 * <li> {@link #setStrict(boolean)}; when {@code true}, parsing will be done strictly.
 * The behavior of strict parsing should be referred to in the implementing
 * {@code NumberFormat} subclass.
 * </ul>
 *
 * <p>
 * To provide more control over formatting or parsing behavior, type checking can
 * be done to safely convert to an implementing subclass of {@code NumberFormat}; this
 * provides additional methods defined by the subclass.
 * For example,
 * {@snippet lang=java :
 * NumberFormat nFmt = NumberFormat.getInstance(Locale.US);
 * if (nFmt instanceof DecimalFormat dFmt) {
 *     dFmt.setDecimalSeparatorAlwaysShown(true);
 *     dFmt.format(100); // returns "100."
 * }
 * }
 * The {@code NumberFormat} subclass returned by the factory methods is dependent
 * on the locale-service provider implementation installed, and may not always
 * be {@link DecimalFormat} or {@link CompactNumberFormat}.
 *
 * <p>
 * You can also use forms of the {@code parse} and {@code format}
 * methods with {@code ParsePosition} and {@code FieldPosition} to
 * allow you to:
 * <ul>
 * <li> Progressively parse through pieces of a string
 * <li> Align the decimal point and other areas
 * </ul>
 * For example, you can align numbers in two ways:
 * <ol>
 * <li> If you are using a monospaced font with spacing for alignment,
 *      you can pass the {@code FieldPosition} in your format call, with
 *      {@code field} = {@code INTEGER_FIELD}. On output,
 *      {@code getEndIndex} will be set to the offset between the
 *      last character of the integer and the decimal. Add
 *      (desiredSpaceCount - getEndIndex) spaces at the front of the string.
 *
 * <li> If you are using proportional fonts,
 *      instead of padding with spaces, measure the width
 *      of the string in pixels from the start to {@code getEndIndex}.
 *      Then move the pen by
 *      (desiredPixelWidth - widthToAlignmentPoint) before drawing the text.
 *      It also works where there is no decimal, but possibly additional
 *      characters at the end, e.g., with parentheses in negative
 *      numbers: "(12)" for -12.
 * </ol>
 *
 * <h2><a id="leniency">Leniency</a></h2>
 * {@code NumberFormat} by default, parses leniently. Subclasses may consider
 * implementing strict parsing and as such, overriding and providing
 * implementations for the optional {@link #isStrict()} and {@link
 * #setStrict(boolean)} methods.
 * <p>
 * Lenient parsing should be used when attempting to parse a number
 * out of a String that contains non-numerical or non-format related values.
 * For example, using a {@link Locale#US} currency format to parse the number
 * {@code 1000} out of the String "$1,000.00 was paid".
 * <p>
 * Strict parsing should be used when attempting to ensure a String adheres exactly
 * to a locale's conventions, and can thus serve to validate input. For example, successfully
 * parsing the number {@code 1000.55} out of the String "1.000,55" confirms the String
 * exactly adhered to the {@link Locale#GERMANY} numerical conventions.
 *
 * <h2><a id="synchronization">Synchronization</a></h2>
 * Number formats are generally not synchronized.
 * It is recommended to create separate format instances for each thread.
 * If multiple threads access a format concurrently, it must be synchronized
 * externally.
 *
 * @implSpec
 * Null Parameter Handling
 * <ul>
 * <li> The {@link #format(double, StringBuffer, FieldPosition)},
 * {@link #format(long, StringBuffer, FieldPosition)} and
 * {@link #parse(String, ParsePosition)} methods may throw
 * {@code NullPointerException}, if any of their parameter is {@code null}.
 * The subclass may provide its own implementation and specification about
 * {@code NullPointerException}.
 * </ul>
 *
 * Default RoundingMode
 * <ul>
 * <li> The default implementation provides rounding modes defined
 * in {@link java.math.RoundingMode} for formatting numbers. It
 * uses the {@linkplain java.math.RoundingMode#HALF_EVEN
 * round half-even algorithm}. To change the rounding mode use
 * {@link #setRoundingMode(java.math.RoundingMode) setRoundingMode}.
 * The {@code NumberFormat} returned by the static factory methods is
 * configured to round floating point numbers using half-even
 * rounding (see {@link java.math.RoundingMode#HALF_EVEN
 * RoundingMode.HALF_EVEN}) for formatting.
 * </ul>
 *
 * @spec         https://www.unicode.org/reports/tr35
 *               Unicode Locale Data Markup Language (LDML)
 * @see          DecimalFormat
 * @see          ChoiceFormat
 * @see          CompactNumberFormat
 * @see          Locale
 * @author       Mark Davis
 * @author       Helena Shih
 * @since 1.1
 */
public abstract class NumberFormat extends Format  {

    /**
     * Field constant used to construct a FieldPosition object. Signifies that
     * the position of the integer part of a formatted number should be returned.
     * @see java.text.FieldPosition
     */
    public static final int INTEGER_FIELD = 0;

    /**
     * Field constant used to construct a FieldPosition object. Signifies that
     * the position of the fraction part of a formatted number should be returned.
     * @see java.text.FieldPosition
     */
    public static final int FRACTION_FIELD = 1;

    /**
     * Sole constructor.  (For invocation by subclass constructors, typically
     * implicit.)
     */
    protected NumberFormat() {
    }

    /**
     * Formats a number and appends the resulting text to the given string
     * buffer.
     * The number can be of any subclass of {@link java.lang.Number}.
     * <p>
     * This implementation extracts the number's value using
     * {@link java.lang.Number#longValue()} for all integral type values that
     * can be converted to {@code long} without loss of information,
     * including {@code BigInteger} values with a
     * {@link java.math.BigInteger#bitLength() bit length} of less than 64,
     * and {@link java.lang.Number#doubleValue()} for all other types. It
     * then calls
     * {@link #format(long,java.lang.StringBuffer,java.text.FieldPosition)}
     * or {@link #format(double,java.lang.StringBuffer,java.text.FieldPosition)}.
     * This may result in loss of magnitude information and precision for
     * {@code BigInteger} and {@code BigDecimal} values.
     * @param number     the number to format
     * @param toAppendTo the {@code StringBuffer} to which the formatted
     *                   text is to be appended
     * @param pos        keeps track on the position of the field within the
     *                   returned string. For example, for formatting a number
     *                   {@code 1234567.89} in {@code Locale.US} locale,
     *                   if the given {@code fieldPosition} is
     *                   {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                   and end index of {@code fieldPosition} will be set
     *                   to 0 and 9, respectively for the output string
     *                   {@code 1,234,567.89}.
     * @return           the value passed in as {@code toAppendTo}
     * @throws           IllegalArgumentException if {@code number} is
     *                   null or not an instance of {@code Number}.
     * @throws           NullPointerException if {@code toAppendTo} or
     *                   {@code pos} is null
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see              java.text.FieldPosition
     */
    @Override
    public StringBuffer format(Object number,
                               StringBuffer toAppendTo,
                               FieldPosition pos) {
        return switch (number) {
            case Long l -> format(l.longValue(), toAppendTo, pos);
            case Integer i -> format(i.longValue(), toAppendTo, pos);
            case Short s -> format(s.longValue(), toAppendTo, pos);
            case Byte b -> format(b.longValue(), toAppendTo, pos);
            case AtomicInteger ai -> format(ai.longValue(), toAppendTo, pos);
            case AtomicLong al -> format(al.longValue(), toAppendTo, pos);
            case BigInteger bi when bi.bitLength() < 64 -> format(bi.longValue(), toAppendTo, pos);
            case Number n -> format(n.doubleValue(), toAppendTo, pos);
            case null, default -> throw new IllegalArgumentException("Cannot format given Object as a Number");
        };
    }

    @Override
    StringBuf format(Object number,
                     StringBuf toAppendTo,
                     FieldPosition pos) {
        return switch (number) {
            case Long l -> format(l.longValue(), toAppendTo, pos);
            case Integer i -> format(i.longValue(), toAppendTo, pos);
            case Short s -> format(s.longValue(), toAppendTo, pos);
            case Byte b -> format(b.longValue(), toAppendTo, pos);
            case AtomicInteger ai -> format(ai.longValue(), toAppendTo, pos);
            case AtomicLong al -> format(al.longValue(), toAppendTo, pos);
            case BigInteger bi when bi.bitLength() < 64 -> format(bi.longValue(), toAppendTo, pos);
            case Number n -> format(n.doubleValue(), toAppendTo, pos);
            case null, default -> throw new IllegalArgumentException("Cannot format given Object as a Number");
        };
    }

    /**
     * {@inheritDoc Format}
     *
     * @implSpec This implementation is equivalent to calling {@code parse(source,
     *           pos)}.
     * @param source the {@code String} to parse
     * @param pos A {@code ParsePosition} object with index and error
     *            index information as described above.
     * @return A {@code Number} parsed from the string. In case of
     *         error, returns null.
     * @throws NullPointerException if {@code source} or {@code pos} is null.
     */
    @Override
    public final Object parseObject(String source, ParsePosition pos) {
        return parse(source, pos);
    }

    /**
     * Specialization of format.
     *
     * @param number the double number to format
     * @return the formatted String
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.Format#format
     */
    public final String format(double number) {
        // Use fast-path for double result if that works
        String result = fastFormat(number);
        if (result != null)
            return result;

        if ("java.text".equals(getClass().getPackageName())) {
            return format(number, StringBufFactory.of(),
                    DontCareFieldPosition.INSTANCE).toString();
        } else {
            return format(number, new StringBuffer(),
                    DontCareFieldPosition.INSTANCE).toString();
        }
    }

    /*
     * fastFormat() is supposed to be implemented in concrete subclasses only.
     * Default implem always returns null.
     */
    String fastFormat(double number) { return null; }

    /**
     * Specialization of format.
     *
     * @param number the long number to format
     * @return the formatted String
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.Format#format
     */
    public final String format(long number) {
        if ("java.text".equals(getClass().getPackageName())) {
            return format(number, StringBufFactory.of(),
                    DontCareFieldPosition.INSTANCE).toString();
        } else {
            return format(number, new StringBuffer(),
                    DontCareFieldPosition.INSTANCE).toString();
        }
    }

    /**
     * Specialization of format.
     *
     * @param number     the double number to format
     * @param toAppendTo the StringBuffer to which the formatted text is to be
     *                   appended
     * @param pos        keeps track on the position of the field within the
     *                   returned string. For example, for formatting a number
     *                   {@code 1234567.89} in {@code Locale.US} locale,
     *                   if the given {@code fieldPosition} is
     *                   {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                   and end index of {@code fieldPosition} will be set
     *                   to 0 and 9, respectively for the output string
     *                   {@code 1,234,567.89}.
     * @return the formatted StringBuffer
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.Format#format
     */
    public abstract StringBuffer format(double number,
                                        StringBuffer toAppendTo,
                                        FieldPosition pos);

    StringBuf format(double number,
                     StringBuf toAppendTo,
                     FieldPosition pos) {
        throw new UnsupportedOperationException("Subclasses should override this method");
    }

    /**
     * Specialization of format.
     *
     * @param number     the long number to format
     * @param toAppendTo the StringBuffer to which the formatted text is to be
     *                   appended
     * @param pos        keeps track on the position of the field within the
     *                   returned string. For example, for formatting a number
     *                   {@code 123456789} in {@code Locale.US} locale,
     *                   if the given {@code fieldPosition} is
     *                   {@link NumberFormat#INTEGER_FIELD}, the begin index
     *                   and end index of {@code fieldPosition} will be set
     *                   to 0 and 11, respectively for the output string
     *                   {@code 123,456,789}.
     * @return the formatted StringBuffer
     * @throws           ArithmeticException if rounding is needed with rounding
     *                   mode being set to RoundingMode.UNNECESSARY
     * @see java.text.Format#format
     */
    public abstract StringBuffer format(long number,
                                        StringBuffer toAppendTo,
                                        FieldPosition pos);

    StringBuf format(long number,
                     StringBuf toAppendTo,
                     FieldPosition pos) {
        throw new UnsupportedOperationException("Subclasses should override this method");
    }

    /**
     * Parses text from the beginning of the given string to produce a {@code Number}.
     * <p>
     * This method attempts to parse text starting at the index given by the
     * {@code ParsePosition}. If parsing succeeds, then the index of the {@code
     * ParsePosition} is updated to the index after the last character used
     * (parsing does not necessarily use all characters up to the end of the
     * string), and the parsed number is returned. The updated {@code
     * ParsePosition} can be used to indicate the starting
     * point for the next call to this method. If an error occurs, then the
     * index of the {@code ParsePosition} is not changed, the error index of the
     * {@code ParsePosition} is set to the index of the character where the error
     * occurred, and {@code null} is returned.
     * <p>
     * This method will return a Long if possible (e.g., within the range [Long.MIN_VALUE,
     * Long.MAX_VALUE] and with no decimals), otherwise a Double.
     *
     * @param source the {@code String} to parse
     * @param parsePosition A {@code ParsePosition} object with index and error
     *            index information as described above.
     * @return A {@code Number} parsed from the string. In case of
     *         failure, returns {@code null}.
     * @throws NullPointerException if {@code source} or {@code ParsePosition}
     *         is {@code null}.
     * @see #isStrict()
     */
    public abstract Number parse(String source, ParsePosition parsePosition);

    /**
     * Parses text from the beginning of the given string to produce a {@code Number}.
     * <p>
     * This method will return a Long if possible (e.g., within the range [Long.MIN_VALUE,
     * Long.MAX_VALUE] and with no decimals), otherwise a Double.
     *
     * @param source A {@code String}, to be parsed from the beginning.
     * @return A {@code Number} parsed from the string.
     * @throws ParseException if parsing fails
     * @throws NullPointerException if {@code source} is {@code null}.
     * @see #isStrict()
     */
    public Number parse(String source) throws ParseException {
        ParsePosition parsePosition = new ParsePosition(0);
        Number result = parse(source, parsePosition);
        if (parsePosition.index == 0) {
            throw new ParseException("Unparseable number: \"" + source + "\"",
                                     parsePosition.errorIndex);
        }
        return result;
    }

    /**
     * Returns {@code true} if this format will parse numbers as integers only.
     * The {@code ParsePosition} index will be set to the position of the decimal
     * symbol. The exact format accepted by the parse operation is locale dependent.
     * For example in the English locale, with ParseIntegerOnly true, the
     * string "123.45" would be parsed as the integer value 123.
     *
     * @return {@code true} if numbers should be parsed as integers only;
     *         {@code false} otherwise
     */
    public boolean isParseIntegerOnly() {
        return parseIntegerOnly;
    }

    /**
     * Sets whether or not numbers should be parsed as integers only.
     *
     * @param value {@code true} if numbers should be parsed as integers only;
     *              {@code false} otherwise
     * @see #isParseIntegerOnly
     */
    public void setParseIntegerOnly(boolean value) {
        parseIntegerOnly = value;
    }

    /**
     * {@return {@code true} if this format will parse numbers strictly;
     * {@code false} otherwise}
     *
     * @implSpec The default implementation always throws {@code
     * UnsupportedOperationException}. Subclasses should override this method
     * when implementing strict parsing.
     * @throws    UnsupportedOperationException if the implementation of this
     *            method does not support this operation
     * @see ##leniency Leniency Section
     * @see #setStrict(boolean)
     * @since 23
     */
    public boolean isStrict() {
        throw new UnsupportedOperationException("Subclasses should override this " +
                "method when implementing strict parsing");
    }

    /**
     * Change the leniency value for parsing. Parsing can either be strict or lenient,
     * by default it is lenient.
     *
     * @implSpec The default implementation always throws {@code
     * UnsupportedOperationException}. Subclasses should override this method
     * when implementing strict parsing.
     * @param strict {@code true} if parsing should be done strictly;
     *               {@code false} otherwise
     * @throws    UnsupportedOperationException if the implementation of this
     *            method does not support this operation
     * @see ##leniency Leniency Section
     * @see #isStrict()
     * @since 23
     */
    public void setStrict(boolean strict) {
        throw new UnsupportedOperationException("Subclasses should override this " +
                "method when implementing strict parsing");
    }

    //============== Locale Stuff =====================

    /**
     * Returns a general-purpose number format for the current default
     * {@link java.util.Locale.Category#FORMAT FORMAT} locale.
     * This is the same as calling
     * {@link #getNumberInstance() getNumberInstance()}.
     *
     * @return the {@code NumberFormat} instance for general-purpose number
     * formatting
     */
    public static final NumberFormat getInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), null, NUMBERSTYLE);
    }

    /**
     * Returns a general-purpose number format for the specified locale.
     * This is the same as calling
     * {@link #getNumberInstance(java.util.Locale) getNumberInstance(inLocale)}.
     *
     * @param inLocale the desired locale
     * @return the {@code NumberFormat} instance for general-purpose number
     * formatting
     */
    public static NumberFormat getInstance(Locale inLocale) {
        return getInstance(inLocale, null, NUMBERSTYLE);
    }

    /**
     * Returns a general-purpose number format for the current default
     * {@link java.util.Locale.Category#FORMAT FORMAT} locale.
     * <p>This is equivalent to calling
     * {@link #getNumberInstance(Locale)
     *     getNumberInstance(Locale.getDefault(Locale.Category.FORMAT))}.
     *
     * @return the {@code NumberFormat} instance for general-purpose number
     * formatting
     * @see java.util.Locale#getDefault(java.util.Locale.Category)
     * @see java.util.Locale.Category#FORMAT
     */
    public static final NumberFormat getNumberInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), null, NUMBERSTYLE);
    }

    /**
     * Returns a general-purpose number format for the specified locale.
     *
     * @param inLocale the desired locale
     * @return the {@code NumberFormat} instance for general-purpose number
     * formatting
     */
    public static NumberFormat getNumberInstance(Locale inLocale) {
        return getInstance(inLocale, null, NUMBERSTYLE);
    }

    /**
     * Returns an integer number format for the current default
     * {@link java.util.Locale.Category#FORMAT FORMAT} locale. The
     * returned number format is configured to round floating point numbers
     * to the nearest integer using half-even rounding (see {@link
     * java.math.RoundingMode#HALF_EVEN RoundingMode.HALF_EVEN}) for formatting,
     * and to parse only the integer part of an input string (see {@link
     * #isParseIntegerOnly isParseIntegerOnly}).
     * <p>This is equivalent to calling
     * {@link #getIntegerInstance(Locale)
     *     getIntegerInstance(Locale.getDefault(Locale.Category.FORMAT))}.
     *
     * @see #getRoundingMode()
     * @see java.util.Locale#getDefault(java.util.Locale.Category)
     * @see java.util.Locale.Category#FORMAT
     * @return a number format for integer values
     * @since 1.4
     */
    public static final NumberFormat getIntegerInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), null, INTEGERSTYLE);
    }

    /**
     * Returns an integer number format for the specified locale. The
     * returned number format is configured to round floating point numbers
     * to the nearest integer using half-even rounding (see {@link
     * java.math.RoundingMode#HALF_EVEN RoundingMode.HALF_EVEN}) for formatting,
     * and to parse only the integer part of an input string (see {@link
     * #isParseIntegerOnly isParseIntegerOnly}).
     *
     * @param inLocale the desired locale
     * @see #getRoundingMode()
     * @return a number format for integer values
     * @since 1.4
     */
    public static NumberFormat getIntegerInstance(Locale inLocale) {
        return getInstance(inLocale, null, INTEGERSTYLE);
    }

    /**
     * Returns a currency format for the current default
     * {@link java.util.Locale.Category#FORMAT FORMAT} locale.
     * <p>This is equivalent to calling
     * {@link #getCurrencyInstance(Locale)
     *     getCurrencyInstance(Locale.getDefault(Locale.Category.FORMAT))}.
     *
     * @return the {@code NumberFormat} instance for currency formatting
     * @see java.util.Locale#getDefault(java.util.Locale.Category)
     * @see java.util.Locale.Category#FORMAT
     */
    public static final NumberFormat getCurrencyInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), null, CURRENCYSTYLE);
    }

    /**
     * Returns a currency format for the specified locale.
     *
     * <p>If the specified locale contains the "{@code cf}" (
     * <a href="https://www.unicode.org/reports/tr35/tr35.html#UnicodeCurrencyFormatIdentifier">
     * currency format style</a>)
     * <a href="../util/Locale.html#def_locale_extension">Unicode extension</a>,
     * the returned currency format uses the style if it is available.
     * Otherwise, the style uses the default "{@code standard}" currency format.
     * For example, if the style designates "{@code account}", negative
     * currency amounts use a pair of parentheses in some locales.
     *
     * @param inLocale the desired locale
     * @return the {@code NumberFormat} instance for currency formatting
     *
     * @spec https://www.unicode.org/reports/tr35 Unicode Locale Data Markup Language (LDML)
     */
    public static NumberFormat getCurrencyInstance(Locale inLocale) {
        return getInstance(inLocale, null, CURRENCYSTYLE);
    }

    /**
     * Returns a percentage format for the current default
     * {@link java.util.Locale.Category#FORMAT FORMAT} locale.
     * <p>This is equivalent to calling
     * {@link #getPercentInstance(Locale)
     *     getPercentInstance(Locale.getDefault(Locale.Category.FORMAT))}.
     *
     * @return the {@code NumberFormat} instance for percentage formatting
     * @see java.util.Locale#getDefault(java.util.Locale.Category)
     * @see java.util.Locale.Category#FORMAT
     */
    public static final NumberFormat getPercentInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), null, PERCENTSTYLE);
    }

    /**
     * Returns a percentage format for the specified locale.
     *
     * @param inLocale the desired locale
     * @return the {@code NumberFormat} instance for percentage formatting
     */
    public static NumberFormat getPercentInstance(Locale inLocale) {
        return getInstance(inLocale, null, PERCENTSTYLE);
    }

    /**
     * Returns a scientific format for the current default locale.
     */
    /*public*/ static final NumberFormat getScientificInstance() {
        return getInstance(Locale.getDefault(Locale.Category.FORMAT), null, SCIENTIFICSTYLE);
    }

    /**
     * Returns a scientific format for the specified locale.
     *
     * @param inLocale the desired locale
     */
    /*public*/ static NumberFormat getScientificInstance(Locale inLocale) {
        return getInstance(inLocale, null, SCIENTIFICSTYLE);
    }

    /**
     * Returns a compact number format for the default
     * {@link java.util.Locale.Category#FORMAT FORMAT} locale with
     * {@link NumberFormat.Style#SHORT "SHORT"} format style.
     *
     * @return A {@code NumberFormat} instance for compact number
     *         formatting
     *
     * @see CompactNumberFormat
     * @see NumberFormat.Style
     * @see java.util.Locale#getDefault(java.util.Locale.Category)
     * @see java.util.Locale.Category#FORMAT
     * @since 12
     */
    public static NumberFormat getCompactNumberInstance() {
        return getInstance(Locale.getDefault(
                Locale.Category.FORMAT), NumberFormat.Style.SHORT, COMPACTSTYLE);
    }

    /**
     * Returns a compact number format for the specified {@link java.util.Locale locale}
     * and {@link NumberFormat.Style formatStyle}.
     *
     * @param locale the desired locale
     * @param formatStyle the style for formatting a number
     * @return A {@code NumberFormat} instance for compact number
     *         formatting
     * @throws NullPointerException if {@code locale} or {@code formatStyle}
     *                              is {@code null}
     *
     * @see CompactNumberFormat
     * @see NumberFormat.Style
     * @see java.util.Locale
     * @since 12
     */
    public static NumberFormat getCompactNumberInstance(Locale locale,
            NumberFormat.Style formatStyle) {

        Objects.requireNonNull(locale);
        Objects.requireNonNull(formatStyle);
        return getInstance(locale, formatStyle, COMPACTSTYLE);
    }

    /**
     * This method compares the passed NumberFormat to a number of pre-defined
     * style NumberFormat instances, (created with the passed locale). Returns a
     * matching FormatStyle string if found, otherwise null.
     * This method is used by MessageFormat to provide string pattens for NumberFormat
     * Subformats. Any future pre-defined NumberFormat styles should be added to this method.
     */
    static String matchToStyle(NumberFormat fmt, Locale locale) {
        if (fmt.equals(NumberFormat.getInstance(locale))) {
            return "";
        } else if (fmt.equals(NumberFormat.getCurrencyInstance(locale))) {
            return "currency";
        } else if (fmt.equals(NumberFormat.getPercentInstance(locale))) {
            return "percent";
        } else if (fmt.equals(NumberFormat.getIntegerInstance(locale))) {
            return "integer";
        } else if (fmt.equals(NumberFormat.getCompactNumberInstance(locale,
                NumberFormat.Style.SHORT))) {
            return "compact_short";
        } else if (fmt.equals(NumberFormat.getCompactNumberInstance(locale,
                NumberFormat.Style.LONG))) {
            return "compact_long";
        } else {
            return null;
        }
    }

    /**
     * Returns an array of all locales for which the
     * {@code get*Instance} methods of this class can return
     * localized instances.
     * The returned array represents the union of locales supported by the Java
     * runtime and by installed
     * {@link java.text.spi.NumberFormatProvider NumberFormatProvider} implementations.
     * At a minimum, the returned array must contain a {@code Locale} instance equal to
     * {@link Locale#ROOT Locale.ROOT} and a {@code Locale} instance equal to
     * {@link Locale#US Locale.US}.
     *
     * @return An array of locales for which localized
     *         {@code NumberFormat} instances are available.
     */
    public static Locale[] getAvailableLocales() {
        LocaleServiceProviderPool pool =
            LocaleServiceProviderPool.getPool(NumberFormatProvider.class);
        return pool.getAvailableLocales();
    }

    /**
     * {@return the hash code for this {@code NumberFormat}}
     *
     * @implSpec This method calculates the hash code value using the values returned by
     * {@link #getMaximumIntegerDigits()} and {@link #getMaximumFractionDigits()}.
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return maximumIntegerDigits * 37 + maxFractionDigits;
        // just enough fields for a reasonable distribution
    }

    /**
     * Compares the specified object with this {@code NumberFormat} for equality.
     * Returns true if the object is also a {@code NumberFormat} and the
     * two formats would format any value the same.
     *
     * @implSpec This method performs an equality check with a notion of class
     * identity based on {@code getClass()}, rather than {@code instanceof}.
     * Therefore, in the equals methods in subclasses, no instance of this class
     * should compare as equal to an instance of a subclass.
     * @param  obj object to be compared for equality
     * @return {@code true} if the specified object is equal to this {@code NumberFormat}
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        NumberFormat other = (NumberFormat) obj;
        return maximumIntegerDigits == other.maximumIntegerDigits
            && minimumIntegerDigits == other.minimumIntegerDigits
            && maximumFractionDigits == other.maximumFractionDigits
            && minimumFractionDigits == other.minimumFractionDigits
            && groupingUsed == other.groupingUsed
            && parseIntegerOnly == other.parseIntegerOnly;
    }

    /**
     * Overrides Cloneable.
     */
    @Override
    public Object clone() {
        NumberFormat other = (NumberFormat) super.clone();
        return other;
    }

    /**
     * Returns true if grouping is used in this format. For example, in the
     * English locale, with grouping on, the number 1234567 might be formatted
     * as "1,234,567". The grouping separator as well as the size of each group
     * is locale dependent and is determined by sub-classes of NumberFormat.
     *
     * @return {@code true} if grouping is used;
     *         {@code false} otherwise
     * @see #setGroupingUsed
     */
    public boolean isGroupingUsed() {
        return groupingUsed;
    }

    /**
     * Set whether or not grouping will be used in this format.
     *
     * @param newValue {@code true} if grouping is used;
     *                 {@code false} otherwise
     * @see #isGroupingUsed
     */
    public void setGroupingUsed(boolean newValue) {
        groupingUsed = newValue;
    }

    /**
     * Returns the maximum number of digits allowed in the integer portion of a
     * number.
     *
     * @return the maximum number of digits
     * @see #setMaximumIntegerDigits
     */
    public int getMaximumIntegerDigits() {
        return maximumIntegerDigits;
    }

    /**
     * Sets the maximum number of digits allowed in the integer portion of a
     * number. maximumIntegerDigits must be &ge; minimumIntegerDigits.  If the
     * new value for maximumIntegerDigits is less than the current value
     * of minimumIntegerDigits, then minimumIntegerDigits will also be set to
     * the new value.
     *
     * @param newValue the maximum number of integer digits to be shown; if
     * less than zero, then zero is used. The concrete subclass may enforce an
     * upper limit to this value appropriate to the numeric type being formatted.
     * @see #getMaximumIntegerDigits
     */
    public void setMaximumIntegerDigits(int newValue) {
        maximumIntegerDigits = Math.max(0,newValue);
        if (minimumIntegerDigits > maximumIntegerDigits) {
            minimumIntegerDigits = maximumIntegerDigits;
        }
    }

    /**
     * Returns the minimum number of digits allowed in the integer portion of a
     * number.
     *
     * @return the minimum number of digits
     * @see #setMinimumIntegerDigits
     */
    public int getMinimumIntegerDigits() {
        return minimumIntegerDigits;
    }

    /**
     * Sets the minimum number of digits allowed in the integer portion of a
     * number. minimumIntegerDigits must be &le; maximumIntegerDigits.  If the
     * new value for minimumIntegerDigits exceeds the current value
     * of maximumIntegerDigits, then maximumIntegerDigits will also be set to
     * the new value
     *
     * @param newValue the minimum number of integer digits to be shown; if
     * less than zero, then zero is used. The concrete subclass may enforce an
     * upper limit to this value appropriate to the numeric type being formatted.
     * @see #getMinimumIntegerDigits
     */
    public void setMinimumIntegerDigits(int newValue) {
        minimumIntegerDigits = Math.max(0,newValue);
        if (minimumIntegerDigits > maximumIntegerDigits) {
            maximumIntegerDigits = minimumIntegerDigits;
        }
    }

    /**
     * Returns the maximum number of digits allowed in the fraction portion of a
     * number.
     *
     * @return the maximum number of digits.
     * @see #setMaximumFractionDigits
     */
    public int getMaximumFractionDigits() {
        return maximumFractionDigits;
    }

    /**
     * Sets the maximum number of digits allowed in the fraction portion of a
     * number. maximumFractionDigits must be &ge; minimumFractionDigits.  If the
     * new value for maximumFractionDigits is less than the current value
     * of minimumFractionDigits, then minimumFractionDigits will also be set to
     * the new value.
     *
     * @param newValue the maximum number of fraction digits to be shown; if
     * less than zero, then zero is used. The concrete subclass may enforce an
     * upper limit to this value appropriate to the numeric type being formatted.
     * @see #getMaximumFractionDigits
     */
    public void setMaximumFractionDigits(int newValue) {
        maximumFractionDigits = Math.max(0,newValue);
        if (maximumFractionDigits < minimumFractionDigits) {
            minimumFractionDigits = maximumFractionDigits;
        }
    }

    /**
     * Returns the minimum number of digits allowed in the fraction portion of a
     * number.
     *
     * @return the minimum number of digits
     * @see #setMinimumFractionDigits
     */
    public int getMinimumFractionDigits() {
        return minimumFractionDigits;
    }

    /**
     * Sets the minimum number of digits allowed in the fraction portion of a
     * number. minimumFractionDigits must be &le; maximumFractionDigits.  If the
     * new value for minimumFractionDigits exceeds the current value
     * of maximumFractionDigits, then maximumFractionDigits will also be set to
     * the new value
     *
     * @param newValue the minimum number of fraction digits to be shown; if
     * less than zero, then zero is used. The concrete subclass may enforce an
     * upper limit to this value appropriate to the numeric type being formatted.
     * @see #getMinimumFractionDigits
     */
    public void setMinimumFractionDigits(int newValue) {
        minimumFractionDigits = Math.max(0,newValue);
        if (maximumFractionDigits < minimumFractionDigits) {
            maximumFractionDigits = minimumFractionDigits;
        }
    }

    /**
     * Gets the currency used by this number format when formatting
     * currency values. The initial value is derived in a locale dependent
     * way. The returned value may be {@code null} if no valid
     * currency could be determined and no currency has been set using
     * {@link #setCurrency(Currency)}.
     *
     * @implSpec The default implementation always throws {@code
     * UnsupportedOperationException}. Subclasses should override this method
     * if currency formatting is desired.
     * @return the currency used by this number format, or {@code null}
     * @throws    UnsupportedOperationException if the implementation of this
     *            method does not support this operation
     * @since 1.4
     */
    public Currency getCurrency() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the currency used by this number format when formatting
     * currency values. This does not update the minimum or maximum
     * number of fraction digits used by the number format.
     *
     * @implSpec The default implementation always throws {@code
     * UnsupportedOperationException}. Subclasses should override this method
     * if currency formatting is desired.
     * @param currency the new currency to be used by this number format
     * @throws    NullPointerException if {@code currency} is {@code null}
     * @throws    UnsupportedOperationException if the implementation of this
     *            method does not support this operation
     * @since 1.4
     */
    public void setCurrency(Currency currency) {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the {@link java.math.RoundingMode} used in this NumberFormat.
     *
     * @implSpec The default implementation always throws {@code
     * UnsupportedOperationException}. Subclasses which handle different
     * rounding modes should override this method.
     * @return The {@code RoundingMode} used for this NumberFormat.
     * @throws    UnsupportedOperationException if the implementation of this
     *            method does not support this operation
     * @see #setRoundingMode(RoundingMode)
     * @since 1.6
     */
    public RoundingMode getRoundingMode() {
        throw new UnsupportedOperationException();
    }

    /**
     * Sets the {@link java.math.RoundingMode} used in this NumberFormat.
     *
     * @implSpec The default implementation always throws {@code
     * UnsupportedOperationException}. Subclasses which handle different
     * rounding modes should override this method.
     * @throws    NullPointerException if {@code roundingMode} is {@code null}
     * @throws    UnsupportedOperationException if the implementation of this
     *            method does not support this operation
     * @param roundingMode The {@code RoundingMode} to be used
     * @see #getRoundingMode()
     * @since 1.6
     */
    public void setRoundingMode(RoundingMode roundingMode) {
        throw new UnsupportedOperationException();
    }

    // =======================privates===============================

    private static NumberFormat getInstance(Locale desiredLocale,
                                            Style formatStyle, int choice) {
        LocaleProviderAdapter adapter;
        adapter = LocaleProviderAdapter.getAdapter(NumberFormatProvider.class,
                desiredLocale);
        NumberFormat numberFormat = getInstance(adapter, desiredLocale,
                formatStyle, choice);
        if (numberFormat == null) {
            numberFormat = getInstance(LocaleProviderAdapter.forJRE(),
                    desiredLocale, formatStyle, choice);
        }
        return numberFormat;
    }

    private static NumberFormat getInstance(LocaleProviderAdapter adapter,
                                            Locale locale, Style formatStyle,
                                            int choice) {
        NumberFormatProvider provider = adapter.getNumberFormatProvider();
        return switch (choice) {
            case NUMBERSTYLE   -> provider.getNumberInstance(locale);
            case PERCENTSTYLE  -> provider.getPercentInstance(locale);
            case CURRENCYSTYLE -> provider.getCurrencyInstance(locale);
            case INTEGERSTYLE  -> provider.getIntegerInstance(locale);
            case COMPACTSTYLE  -> provider.getCompactNumberInstance(locale, formatStyle);
            default            -> null;
        };
    }

    /**
     * First, read in the default serializable data.
     *
     * Then, if {@code serialVersionOnStream} is less than 1, indicating that
     * the stream was written by JDK 1.1,
     * set the {@code int} fields such as {@code maximumIntegerDigits}
     * to be equal to the {@code byte} fields such as {@code maxIntegerDigits},
     * since the {@code int} fields were not present in JDK 1.1.
     * Finally, set serialVersionOnStream back to the maximum allowed value so that
     * default serialization will work properly if this object is streamed out again.
     *
     * <p>If {@code minimumIntegerDigits} is greater than
     * {@code maximumIntegerDigits} or {@code minimumFractionDigits}
     * is greater than {@code maximumFractionDigits}, then the stream data
     * is invalid and this method throws an {@code InvalidObjectException}.
     * In addition, if any of these values is negative, then this method throws
     * an {@code InvalidObjectException}.
     *
     * @since 1.2
     */
    @java.io.Serial
    private void readObject(ObjectInputStream stream)
         throws IOException, ClassNotFoundException
    {
        stream.defaultReadObject();
        if (serialVersionOnStream < 1) {
            // Didn't have additional int fields, reassign to use them.
            maximumIntegerDigits = maxIntegerDigits;
            minimumIntegerDigits = minIntegerDigits;
            maximumFractionDigits = maxFractionDigits;
            minimumFractionDigits = minFractionDigits;
        }
        if (minimumIntegerDigits > maximumIntegerDigits ||
            minimumFractionDigits > maximumFractionDigits ||
            minimumIntegerDigits < 0 || minimumFractionDigits < 0) {
            throw new InvalidObjectException("Digit count range invalid");
        }
        serialVersionOnStream = currentSerialVersion;
    }

    /**
     * Write out the default serializable data, after first setting
     * the {@code byte} fields such as {@code maxIntegerDigits} to be
     * equal to the {@code int} fields such as {@code maximumIntegerDigits}
     * (or to {@code Byte.MAX_VALUE}, whichever is smaller), for compatibility
     * with the JDK 1.1 version of the stream format.
     *
     * @since 1.2
     */
    @java.io.Serial
    private void writeObject(ObjectOutputStream stream)
         throws IOException
    {
        maxIntegerDigits = (maximumIntegerDigits > Byte.MAX_VALUE) ?
                           Byte.MAX_VALUE : (byte)maximumIntegerDigits;
        minIntegerDigits = (minimumIntegerDigits > Byte.MAX_VALUE) ?
                           Byte.MAX_VALUE : (byte)minimumIntegerDigits;
        maxFractionDigits = (maximumFractionDigits > Byte.MAX_VALUE) ?
                            Byte.MAX_VALUE : (byte)maximumFractionDigits;
        minFractionDigits = (minimumFractionDigits > Byte.MAX_VALUE) ?
                            Byte.MAX_VALUE : (byte)minimumFractionDigits;
        stream.defaultWriteObject();
    }

    // Constants used by factory methods to specify a style of format.
    private static final int NUMBERSTYLE = 0;
    private static final int CURRENCYSTYLE = 1;
    private static final int PERCENTSTYLE = 2;
    private static final int SCIENTIFICSTYLE = 3;
    private static final int INTEGERSTYLE = 4;
    private static final int COMPACTSTYLE = 5;

    /**
     * True if the grouping (i.e. thousands) separator is used when
     * formatting and parsing numbers.
     *
     * @serial
     * @see #isGroupingUsed
     */
    private boolean groupingUsed = true;

    /**
     * The maximum number of digits allowed in the integer portion of a
     * number.  {@code maxIntegerDigits} must be greater than or equal to
     * {@code minIntegerDigits}.
     * <p>
     * <strong>Note:</strong> This field exists only for serialization
     * compatibility with JDK 1.1.  In Java platform 2 v1.2 and higher, the new
     * {@code int} field {@code maximumIntegerDigits} is used instead.
     * When writing to a stream, {@code maxIntegerDigits} is set to
     * {@code maximumIntegerDigits} or {@code Byte.MAX_VALUE},
     * whichever is smaller.  When reading from a stream, this field is used
     * only if {@code serialVersionOnStream} is less than 1.
     *
     * @serial
     * @see #getMaximumIntegerDigits
     */
    private byte    maxIntegerDigits = 40;

    /**
     * The minimum number of digits allowed in the integer portion of a
     * number.  {@code minimumIntegerDigits} must be less than or equal to
     * {@code maximumIntegerDigits}.
     * <p>
     * <strong>Note:</strong> This field exists only for serialization
     * compatibility with JDK 1.1.  In Java platform 2 v1.2 and higher, the new
     * {@code int} field {@code minimumIntegerDigits} is used instead.
     * When writing to a stream, {@code minIntegerDigits} is set to
     * {@code minimumIntegerDigits} or {@code Byte.MAX_VALUE},
     * whichever is smaller.  When reading from a stream, this field is used
     * only if {@code serialVersionOnStream} is less than 1.
     *
     * @serial
     * @see #getMinimumIntegerDigits
     */
    private byte    minIntegerDigits = 1;

    /**
     * The maximum number of digits allowed in the fractional portion of a
     * number.  {@code maximumFractionDigits} must be greater than or equal to
     * {@code minimumFractionDigits}.
     * <p>
     * <strong>Note:</strong> This field exists only for serialization
     * compatibility with JDK 1.1.  In Java platform 2 v1.2 and higher, the new
     * {@code int} field {@code maximumFractionDigits} is used instead.
     * When writing to a stream, {@code maxFractionDigits} is set to
     * {@code maximumFractionDigits} or {@code Byte.MAX_VALUE},
     * whichever is smaller.  When reading from a stream, this field is used
     * only if {@code serialVersionOnStream} is less than 1.
     *
     * @serial
     * @see #getMaximumFractionDigits
     */
    private byte    maxFractionDigits = 3;    // invariant, >= minFractionDigits

    /**
     * The minimum number of digits allowed in the fractional portion of a
     * number.  {@code minimumFractionDigits} must be less than or equal to
     * {@code maximumFractionDigits}.
     * <p>
     * <strong>Note:</strong> This field exists only for serialization
     * compatibility with JDK 1.1.  In Java platform 2 v1.2 and higher, the new
     * {@code int} field {@code minimumFractionDigits} is used instead.
     * When writing to a stream, {@code minFractionDigits} is set to
     * {@code minimumFractionDigits} or {@code Byte.MAX_VALUE},
     * whichever is smaller.  When reading from a stream, this field is used
     * only if {@code serialVersionOnStream} is less than 1.
     *
     * @serial
     * @see #getMinimumFractionDigits
     */
    private byte    minFractionDigits = 0;

    /**
     * True if this format will parse numbers as integers only.
     *
     * @serial
     * @see #isParseIntegerOnly
     */
    private boolean parseIntegerOnly = false;

    // new fields for 1.2.  byte is too small for integer digits.

    /**
     * The maximum number of digits allowed in the integer portion of a
     * number.  {@code maximumIntegerDigits} must be greater than or equal to
     * {@code minimumIntegerDigits}.
     *
     * @serial
     * @since 1.2
     * @see #getMaximumIntegerDigits
     */
    private int    maximumIntegerDigits = 40;

    /**
     * The minimum number of digits allowed in the integer portion of a
     * number.  {@code minimumIntegerDigits} must be less than or equal to
     * {@code maximumIntegerDigits}.
     *
     * @serial
     * @since 1.2
     * @see #getMinimumIntegerDigits
     */
    private int    minimumIntegerDigits = 1;

    /**
     * The maximum number of digits allowed in the fractional portion of a
     * number.  {@code maximumFractionDigits} must be greater than or equal to
     * {@code minimumFractionDigits}.
     *
     * @serial
     * @since 1.2
     * @see #getMaximumFractionDigits
     */
    private int    maximumFractionDigits = 3;    // invariant, >= minFractionDigits

    /**
     * The minimum number of digits allowed in the fractional portion of a
     * number.  {@code minimumFractionDigits} must be less than or equal to
     * {@code maximumFractionDigits}.
     *
     * @serial
     * @since 1.2
     * @see #getMinimumFractionDigits
     */
    private int    minimumFractionDigits = 0;

    static final int currentSerialVersion = 1;

    /**
     * Describes the version of {@code NumberFormat} present on the stream.
     * Possible values are:
     * <ul>
     * <li><b>0</b> (or uninitialized): the JDK 1.1 version of the stream format.
     *     In this version, the {@code int} fields such as
     *     {@code maximumIntegerDigits} were not present, and the {@code byte}
     *     fields such as {@code maxIntegerDigits} are used instead.
     *
     * <li><b>1</b>: the 1.2 version of the stream format.  The values of the
     *     {@code byte} fields such as {@code maxIntegerDigits} are ignored,
     *     and the {@code int} fields such as {@code maximumIntegerDigits}
     *     are used instead.
     * </ul>
     * When streaming out a {@code NumberFormat}, the most recent format
     * (corresponding to the highest allowable {@code serialVersionOnStream})
     * is always written.
     *
     * @serial
     * @since 1.2
     */
    private int serialVersionOnStream = currentSerialVersion;

    // Removed "implements Cloneable" clause.  Needs to update serialization
    // ID for backward compatibility.
    @java.io.Serial
    static final long serialVersionUID = -2308460125733713944L;


    //
    // class for AttributedCharacterIterator attributes
    //
    /**
     * Defines constants that are used as attribute keys in the
     * {@code AttributedCharacterIterator} returned
     * from {@code NumberFormat.formatToCharacterIterator} and as
     * field identifiers in {@code FieldPosition}.
     *
     * @since 1.4
     */
    public static class Field extends Format.Field {

        // Proclaim serial compatibility with 1.4 FCS
        @java.io.Serial
        private static final long serialVersionUID = 7494728892700160890L;

        // table of all instances in this class, used by readResolve
        private static final Map<String, Field> instanceMap = new HashMap<>(11);

        /**
         * Creates a Field instance with the specified
         * name.
         *
         * @param name Name of the attribute
         */
        @SuppressWarnings("this-escape")
        protected Field(String name) {
            super(name);
            if (this.getClass() == NumberFormat.Field.class) {
                instanceMap.put(name, this);
            }
        }

        /**
         * Resolves instances being deserialized to the predefined constants.
         *
         * @throws InvalidObjectException if the constant could not be resolved.
         * @return resolved NumberFormat.Field constant
         */
        @Override
        @java.io.Serial
        protected Object readResolve() throws InvalidObjectException {
            if (this.getClass() != NumberFormat.Field.class) {
                throw new InvalidObjectException("subclass didn't correctly implement readResolve");
            }

            Object instance = instanceMap.get(getName());
            if (instance != null) {
                return instance;
            } else {
                throw new InvalidObjectException("unknown attribute name");
            }
        }

        /**
         * Constant identifying the integer field.
         */
        public static final Field INTEGER = new Field("integer");

        /**
         * Constant identifying the fraction field.
         */
        public static final Field FRACTION = new Field("fraction");

        /**
         * Constant identifying the exponent field.
         */
        public static final Field EXPONENT = new Field("exponent");

        /**
         * Constant identifying the decimal separator field.
         */
        public static final Field DECIMAL_SEPARATOR =
                            new Field("decimal separator");

        /**
         * Constant identifying the sign field.
         */
        public static final Field SIGN = new Field("sign");

        /**
         * Constant identifying the grouping separator field.
         */
        public static final Field GROUPING_SEPARATOR =
                            new Field("grouping separator");

        /**
         * Constant identifying the exponent symbol field.
         */
        public static final Field EXPONENT_SYMBOL = new
                            Field("exponent symbol");

        /**
         * Constant identifying the percent field.
         */
        public static final Field PERCENT = new Field("percent");

        /**
         * Constant identifying the permille field.
         */
        public static final Field PERMILLE = new Field("per mille");

        /**
         * Constant identifying the currency field.
         */
        public static final Field CURRENCY = new Field("currency");

        /**
         * Constant identifying the exponent sign field.
         */
        public static final Field EXPONENT_SIGN = new Field("exponent sign");

        /**
         * Constant identifying the prefix field.
         *
         * @since 12
         */
        public static final Field PREFIX = new Field("prefix");

        /**
         * Constant identifying the suffix field.
         *
         * @since 12
         */
        public static final Field SUFFIX = new Field("suffix");
    }

    /**
     * A number format style.
     * <p>
     * {@code Style} is an enum which represents the style for formatting
     * a number within a given {@code NumberFormat} instance.
     *
     * @see CompactNumberFormat
     * @see NumberFormat#getCompactNumberInstance(Locale, Style)
     * @since 12
     */
    public enum Style {

        /**
         * The {@code SHORT} number format style.
         */
        SHORT,

        /**
         * The {@code LONG} number format style.
         */
        LONG

    }
}
