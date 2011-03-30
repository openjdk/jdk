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

package java.util;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.Flushable;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.DateFormatSymbols;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import sun.misc.FpUtils;
import sun.misc.DoubleConsts;
import sun.misc.FormattedFloatingDecimal;

/**
 * An interpreter for printf-style format strings.  This class provides support
 * for layout justification and alignment, common formats for numeric, string,
 * and date/time data, and locale-specific output.  Common Java types such as
 * {@code byte}, {@link java.math.BigDecimal BigDecimal}, and {@link Calendar}
 * are supported.  Limited formatting customization for arbitrary user types is
 * provided through the {@link Formattable} interface.
 *
 * <p> Formatters are not necessarily safe for multithreaded access.  Thread
 * safety is optional and is the responsibility of users of methods in this
 * class.
 *
 * <p> Formatted printing for the Java language is heavily inspired by C's
 * {@code printf}.  Although the format strings are similar to C, some
 * customizations have been made to accommodate the Java language and exploit
 * some of its features.  Also, Java formatting is more strict than C's; for
 * example, if a conversion is incompatible with a flag, an exception will be
 * thrown.  In C inapplicable flags are silently ignored.  The format strings
 * are thus intended to be recognizable to C programmers but not necessarily
 * completely compatible with those in C.
 *
 * <p> Examples of expected usage:
 *
 * <blockquote><pre>
 *   StringBuilder sb = new StringBuilder();
 *   // Send all output to the Appendable object sb
 *   Formatter formatter = new Formatter(sb, Locale.US);
 *
 *   // Explicit argument indices may be used to re-order output.
 *   formatter.format("%4$2s %3$2s %2$2s %1$2s", "a", "b", "c", "d")
 *   // -&gt; " d  c  b  a"
 *
 *   // Optional locale as the first argument can be used to get
 *   // locale-specific formatting of numbers.  The precision and width can be
 *   // given to round and align the value.
 *   formatter.format(Locale.FRANCE, "e = %+10.4f", Math.E);
 *   // -&gt; "e =    +2,7183"
 *
 *   // The '(' numeric flag may be used to format negative numbers with
 *   // parentheses rather than a minus sign.  Group separators are
 *   // automatically inserted.
 *   formatter.format("Amount gained or lost since last statement: $ %(,.2f",
 *                    balanceDelta);
 *   // -&gt; "Amount gained or lost since last statement: $ (6,217.58)"
 * </pre></blockquote>
 *
 * <p> Convenience methods for common formatting requests exist as illustrated
 * by the following invocations:
 *
 * <blockquote><pre>
 *   // Writes a formatted string to System.out.
 *   System.out.format("Local time: %tT", Calendar.getInstance());
 *   // -&gt; "Local time: 13:34:18"
 *
 *   // Writes formatted output to System.err.
 *   System.err.printf("Unable to open file '%1$s': %2$s",
 *                     fileName, exception.getMessage());
 *   // -&gt; "Unable to open file 'food': No such file or directory"
 * </pre></blockquote>
 *
 * <p> Like C's {@code sprintf(3)}, Strings may be formatted using the static
 * method {@link String#format(String,Object...) String.format}:
 *
 * <blockquote><pre>
 *   // Format a string containing a date.
 *   import java.util.Calendar;
 *   import java.util.GregorianCalendar;
 *   import static java.util.Calendar.*;
 *
 *   Calendar c = new GregorianCalendar(1995, MAY, 23);
 *   String s = String.format("Duke's Birthday: %1$tm %1$te,%1$tY", c);
 *   // -&gt; s == "Duke's Birthday: May 23, 1995"
 * </pre></blockquote>
 *
 * <h3><a name="org">Organization</a></h3>
 *
 * <p> This specification is divided into two sections.  The first section, <a
 * href="#summary">Summary</a>, covers the basic formatting concepts.  This
 * section is intended for users who want to get started quickly and are
 * familiar with formatted printing in other programming languages.  The second
 * section, <a href="#detail">Details</a>, covers the specific implementation
 * details.  It is intended for users who want more precise specification of
 * formatting behavior.
 *
 * <h3><a name="summary">Summary</a></h3>
 *
 * <p> This section is intended to provide a brief overview of formatting
 * concepts.  For precise behavioral details, refer to the <a
 * href="#detail">Details</a> section.
 *
 * <h4><a name="syntax">Format String Syntax</a></h4>
 *
 * <p> Every method which produces formatted output requires a <i>format
 * string</i> and an <i>argument list</i>.  The format string is a {@link
 * String} which may contain fixed text and one or more embedded <i>format
 * specifiers</i>.  Consider the following example:
 *
 * <blockquote><pre>
 *   Calendar c = ...;
 *   String s = String.format("Duke's Birthday: %1$tm %1$te,%1$tY", c);
 * </pre></blockquote>
 *
 * This format string is the first argument to the {@code format} method.  It
 * contains three format specifiers "{@code %1$tm}", "{@code %1$te}", and
 * "{@code %1$tY}" which indicate how the arguments should be processed and
 * where they should be inserted in the text.  The remaining portions of the
 * format string are fixed text including {@code "Dukes Birthday: "} and any
 * other spaces or punctuation.
 *
 * The argument list consists of all arguments passed to the method after the
 * format string.  In the above example, the argument list is of size one and
 * consists of the {@link java.util.Calendar Calendar} object {@code c}.
 *
 * <ul>
 *
 * <li> The format specifiers for general, character, and numeric types have
 * the following syntax:
 *
 * <blockquote><pre>
 *   %[argument_index$][flags][width][.precision]conversion
 * </pre></blockquote>
 *
 * <p> The optional <i>argument_index</i> is a decimal integer indicating the
 * position of the argument in the argument list.  The first argument is
 * referenced by "{@code 1$}", the second by "{@code 2$}", etc.
 *
 * <p> The optional <i>flags</i> is a set of characters that modify the output
 * format.  The set of valid flags depends on the conversion.
 *
 * <p> The optional <i>width</i> is a non-negative decimal integer indicating
 * the minimum number of characters to be written to the output.
 *
 * <p> The optional <i>precision</i> is a non-negative decimal integer usually
 * used to restrict the number of characters.  The specific behavior depends on
 * the conversion.
 *
 * <p> The required <i>conversion</i> is a character indicating how the
 * argument should be formatted.  The set of valid conversions for a given
 * argument depends on the argument's data type.
 *
 * <li> The format specifiers for types which are used to represents dates and
 * times have the following syntax:
 *
 * <blockquote><pre>
 *   %[argument_index$][flags][width]conversion
 * </pre></blockquote>
 *
 * <p> The optional <i>argument_index</i>, <i>flags</i> and <i>width</i> are
 * defined as above.
 *
 * <p> The required <i>conversion</i> is a two character sequence.  The first
 * character is {@code 't'} or {@code 'T'}.  The second character indicates
 * the format to be used.  These characters are similar to but not completely
 * identical to those defined by GNU {@code date} and POSIX
 * {@code strftime(3c)}.
 *
 * <li> The format specifiers which do not correspond to arguments have the
 * following syntax:
 *
 * <blockquote><pre>
 *   %[flags][width]conversion
 * </pre></blockquote>
 *
 * <p> The optional <i>flags</i> and <i>width</i> is defined as above.
 *
 * <p> The required <i>conversion</i> is a character indicating content to be
 * inserted in the output.
 *
 * </ul>
 *
 * <h4> Conversions </h4>
 *
 * <p> Conversions are divided into the following categories:
 *
 * <ol>
 *
 * <li> <b>General</b> - may be applied to any argument
 * type
 *
 * <li> <b>Character</b> - may be applied to basic types which represent
 * Unicode characters: {@code char}, {@link Character}, {@code byte}, {@link
 * Byte}, {@code short}, and {@link Short}. This conversion may also be
 * applied to the types {@code int} and {@link Integer} when {@link
 * Character#isValidCodePoint} returns {@code true}
 *
 * <li> <b>Numeric</b>
 *
 * <ol>
 *
 * <li> <b>Integral</b> - may be applied to Java integral types: {@code byte},
 * {@link Byte}, {@code short}, {@link Short}, {@code int} and {@link
 * Integer}, {@code long}, {@link Long}, and {@link java.math.BigInteger
 * BigInteger}
 *
 * <li><b>Floating Point</b> - may be applied to Java floating-point types:
 * {@code float}, {@link Float}, {@code double}, {@link Double}, and {@link
 * java.math.BigDecimal BigDecimal}
 *
 * </ol>
 *
 * <li> <b>Date/Time</b> - may be applied to Java types which are capable of
 * encoding a date or time: {@code long}, {@link Long}, {@link Calendar}, and
 * {@link Date}.
 *
 * <li> <b>Percent</b> - produces a literal {@code '%'}
 * (<tt>'&#92;u0025'</tt>)
 *
 * <li> <b>Line Separator</b> - produces the platform-specific line separator
 *
 * </ol>
 *
 * <p> The following table summarizes the supported conversions.  Conversions
 * denoted by an upper-case character (i.e. {@code 'B'}, {@code 'H'},
 * {@code 'S'}, {@code 'C'}, {@code 'X'}, {@code 'E'}, {@code 'G'},
 * {@code 'A'}, and {@code 'T'}) are the same as those for the corresponding
 * lower-case conversion characters except that the result is converted to
 * upper case according to the rules of the prevailing {@link java.util.Locale
 * Locale}.  The result is equivalent to the following invocation of {@link
 * String#toUpperCase()}
 *
 * <pre>
 *    out.toUpperCase() </pre>
 *
 * <table cellpadding=5 summary="genConv">
 *
 * <tr><th valign="bottom"> Conversion
 *     <th valign="bottom"> Argument Category
 *     <th valign="bottom"> Description
 *
 * <tr><td valign="top"> {@code 'b'}, {@code 'B'}
 *     <td valign="top"> general
 *     <td> If the argument <i>arg</i> is {@code null}, then the result is
 *     "{@code false}".  If <i>arg</i> is a {@code boolean} or {@link
 *     Boolean}, then the result is the string returned by {@link
 *     String#valueOf(boolean) String.valueOf(arg)}.  Otherwise, the result is
 *     "true".
 *
 * <tr><td valign="top"> {@code 'h'}, {@code 'H'}
 *     <td valign="top"> general
 *     <td> If the argument <i>arg</i> is {@code null}, then the result is
 *     "{@code null}".  Otherwise, the result is obtained by invoking
 *     {@code Integer.toHexString(arg.hashCode())}.
 *
 * <tr><td valign="top"> {@code 's'}, {@code 'S'}
 *     <td valign="top"> general
 *     <td> If the argument <i>arg</i> is {@code null}, then the result is
 *     "{@code null}".  If <i>arg</i> implements {@link Formattable}, then
 *     {@link Formattable#formatTo arg.formatTo} is invoked. Otherwise, the
 *     result is obtained by invoking {@code arg.toString()}.
 *
 * <tr><td valign="top">{@code 'c'}, {@code 'C'}
 *     <td valign="top"> character
 *     <td> The result is a Unicode character
 *
 * <tr><td valign="top">{@code 'd'}
 *     <td valign="top"> integral
 *     <td> The result is formatted as a decimal integer
 *
 * <tr><td valign="top">{@code 'o'}
 *     <td valign="top"> integral
 *     <td> The result is formatted as an octal integer
 *
 * <tr><td valign="top">{@code 'x'}, {@code 'X'}
 *     <td valign="top"> integral
 *     <td> The result is formatted as a hexadecimal integer
 *
 * <tr><td valign="top">{@code 'e'}, {@code 'E'}
 *     <td valign="top"> floating point
 *     <td> The result is formatted as a decimal number in computerized
 *     scientific notation
 *
 * <tr><td valign="top">{@code 'f'}
 *     <td valign="top"> floating point
 *     <td> The result is formatted as a decimal number
 *
 * <tr><td valign="top">{@code 'g'}, {@code 'G'}
 *     <td valign="top"> floating point
 *     <td> The result is formatted using computerized scientific notation or
 *     decimal format, depending on the precision and the value after rounding.
 *
 * <tr><td valign="top">{@code 'a'}, {@code 'A'}
 *     <td valign="top"> floating point
 *     <td> The result is formatted as a hexadecimal floating-point number with
 *     a significand and an exponent
 *
 * <tr><td valign="top">{@code 't'}, {@code 'T'}
 *     <td valign="top"> date/time
 *     <td> Prefix for date and time conversion characters.  See <a
 *     href="#dt">Date/Time Conversions</a>.
 *
 * <tr><td valign="top">{@code '%'}
 *     <td valign="top"> percent
 *     <td> The result is a literal {@code '%'} (<tt>'&#92;u0025'</tt>)
 *
 * <tr><td valign="top">{@code 'n'}
 *     <td valign="top"> line separator
 *     <td> The result is the platform-specific line separator
 *
 * </table>
 *
 * <p> Any characters not explicitly defined as conversions are illegal and are
 * reserved for future extensions.
 *
 * <h4><a name="dt">Date/Time Conversions</a></h4>
 *
 * <p> The following date and time conversion suffix characters are defined for
 * the {@code 't'} and {@code 'T'} conversions.  The types are similar to but
 * not completely identical to those defined by GNU {@code date} and POSIX
 * {@code strftime(3c)}.  Additional conversion types are provided to access
 * Java-specific functionality (e.g. {@code 'L'} for milliseconds within the
 * second).
 *
 * <p> The following conversion characters are used for formatting times:
 *
 * <table cellpadding=5 summary="time">
 *
 * <tr><td valign="top"> {@code 'H'}
 *     <td> Hour of the day for the 24-hour clock, formatted as two digits with
 *     a leading zero as necessary i.e. {@code 00 - 23}.
 *
 * <tr><td valign="top">{@code 'I'}
 *     <td> Hour for the 12-hour clock, formatted as two digits with a leading
 *     zero as necessary, i.e.  {@code 01 - 12}.
 *
 * <tr><td valign="top">{@code 'k'}
 *     <td> Hour of the day for the 24-hour clock, i.e. {@code 0 - 23}.
 *
 * <tr><td valign="top">{@code 'l'}
 *     <td> Hour for the 12-hour clock, i.e. {@code 1 - 12}.
 *
 * <tr><td valign="top">{@code 'M'}
 *     <td> Minute within the hour formatted as two digits with a leading zero
 *     as necessary, i.e.  {@code 00 - 59}.
 *
 * <tr><td valign="top">{@code 'S'}
 *     <td> Seconds within the minute, formatted as two digits with a leading
 *     zero as necessary, i.e. {@code 00 - 60} ("{@code 60}" is a special
 *     value required to support leap seconds).
 *
 * <tr><td valign="top">{@code 'L'}
 *     <td> Millisecond within the second formatted as three digits with
 *     leading zeros as necessary, i.e. {@code 000 - 999}.
 *
 * <tr><td valign="top">{@code 'N'}
 *     <td> Nanosecond within the second, formatted as nine digits with leading
 *     zeros as necessary, i.e. {@code 000000000 - 999999999}.
 *
 * <tr><td valign="top">{@code 'p'}
 *     <td> Locale-specific {@linkplain
 *     java.text.DateFormatSymbols#getAmPmStrings morning or afternoon} marker
 *     in lower case, e.g."{@code am}" or "{@code pm}". Use of the conversion
 *     prefix {@code 'T'} forces this output to upper case.
 *
 * <tr><td valign="top">{@code 'z'}
 *     <td> <a href="http://www.ietf.org/rfc/rfc0822.txt">RFC&nbsp;822</a>
 *     style numeric time zone offset from GMT, e.g. {@code -0800}.  This
 *     value will be adjusted as necessary for Daylight Saving Time.  For
 *     {@code long}, {@link Long}, and {@link Date} the time zone used is
 *     the {@linkplain TimeZone#getDefault() default time zone} for this
 *     instance of the Java virtual machine.
 *
 * <tr><td valign="top">{@code 'Z'}
 *     <td> A string representing the abbreviation for the time zone.  This
 *     value will be adjusted as necessary for Daylight Saving Time.  For
 *     {@code long}, {@link Long}, and {@link Date} the  time zone used is
 *     the {@linkplain TimeZone#getDefault() default time zone} for this
 *     instance of the Java virtual machine.  The Formatter's locale will
 *     supersede the locale of the argument (if any).
 *
 * <tr><td valign="top">{@code 's'}
 *     <td> Seconds since the beginning of the epoch starting at 1 January 1970
 *     {@code 00:00:00} UTC, i.e. {@code Long.MIN_VALUE/1000} to
 *     {@code Long.MAX_VALUE/1000}.
 *
 * <tr><td valign="top">{@code 'Q'}
 *     <td> Milliseconds since the beginning of the epoch starting at 1 January
 *     1970 {@code 00:00:00} UTC, i.e. {@code Long.MIN_VALUE} to
 *     {@code Long.MAX_VALUE}.
 *
 * </table>
 *
 * <p> The following conversion characters are used for formatting dates:
 *
 * <table cellpadding=5 summary="date">
 *
 * <tr><td valign="top">{@code 'B'}
 *     <td> Locale-specific {@linkplain java.text.DateFormatSymbols#getMonths
 *     full month name}, e.g. {@code "January"}, {@code "February"}.
 *
 * <tr><td valign="top">{@code 'b'}
 *     <td> Locale-specific {@linkplain
 *     java.text.DateFormatSymbols#getShortMonths abbreviated month name},
 *     e.g. {@code "Jan"}, {@code "Feb"}.
 *
 * <tr><td valign="top">{@code 'h'}
 *     <td> Same as {@code 'b'}.
 *
 * <tr><td valign="top">{@code 'A'}
 *     <td> Locale-specific full name of the {@linkplain
 *     java.text.DateFormatSymbols#getWeekdays day of the week},
 *     e.g. {@code "Sunday"}, {@code "Monday"}
 *
 * <tr><td valign="top">{@code 'a'}
 *     <td> Locale-specific short name of the {@linkplain
 *     java.text.DateFormatSymbols#getShortWeekdays day of the week},
 *     e.g. {@code "Sun"}, {@code "Mon"}
 *
 * <tr><td valign="top">{@code 'C'}
 *     <td> Four-digit year divided by {@code 100}, formatted as two digits
 *     with leading zero as necessary, i.e. {@code 00 - 99}
 *
 * <tr><td valign="top">{@code 'Y'}
 *     <td> Year, formatted as at least four digits with leading zeros as
 *     necessary, e.g. {@code 0092} equals {@code 92} CE for the Gregorian
 *     calendar.
 *
 * <tr><td valign="top">{@code 'y'}
 *     <td> Last two digits of the year, formatted with leading zeros as
 *     necessary, i.e. {@code 00 - 99}.
 *
 * <tr><td valign="top">{@code 'j'}
 *     <td> Day of year, formatted as three digits with leading zeros as
 *     necessary, e.g. {@code 001 - 366} for the Gregorian calendar.
 *
 * <tr><td valign="top">{@code 'm'}
 *     <td> Month, formatted as two digits with leading zeros as necessary,
 *     i.e. {@code 01 - 13}.
 *
 * <tr><td valign="top">{@code 'd'}
 *     <td> Day of month, formatted as two digits with leading zeros as
 *     necessary, i.e. {@code 01 - 31}
 *
 * <tr><td valign="top">{@code 'e'}
 *     <td> Day of month, formatted as two digits, i.e. {@code 1 - 31}.
 *
 * </table>
 *
 * <p> The following conversion characters are used for formatting common
 * date/time compositions.
 *
 * <table cellpadding=5 summary="composites">
 *
 * <tr><td valign="top">{@code 'R'}
 *     <td> Time formatted for the 24-hour clock as {@code "%tH:%tM"}
 *
 * <tr><td valign="top">{@code 'T'}
 *     <td> Time formatted for the 24-hour clock as {@code "%tH:%tM:%tS"}.
 *
 * <tr><td valign="top">{@code 'r'}
 *     <td> Time formatted for the 12-hour clock as {@code "%tI:%tM:%tS %Tp"}.
 *     The location of the morning or afternoon marker ({@code '%Tp'}) may be
 *     locale-dependent.
 *
 * <tr><td valign="top">{@code 'D'}
 *     <td> Date formatted as {@code "%tm/%td/%ty"}.
 *
 * <tr><td valign="top">{@code 'F'}
 *     <td> <a href="http://www.w3.org/TR/NOTE-datetime">ISO&nbsp;8601</a>
 *     complete date formatted as {@code "%tY-%tm-%td"}.
 *
 * <tr><td valign="top">{@code 'c'}
 *     <td> Date and time formatted as {@code "%ta %tb %td %tT %tZ %tY"},
 *     e.g. {@code "Sun Jul 20 16:17:00 EDT 1969"}.
 *
 * </table>
 *
 * <p> Any characters not explicitly defined as date/time conversion suffixes
 * are illegal and are reserved for future extensions.
 *
 * <h4> Flags </h4>
 *
 * <p> The following table summarizes the supported flags.  <i>y</i> means the
 * flag is supported for the indicated argument types.
 *
 * <table cellpadding=5 summary="genConv">
 *
 * <tr><th valign="bottom"> Flag <th valign="bottom"> General
 *     <th valign="bottom"> Character <th valign="bottom"> Integral
 *     <th valign="bottom"> Floating Point
 *     <th valign="bottom"> Date/Time
 *     <th valign="bottom"> Description
 *
 * <tr><td> '-' <td align="center" valign="top"> y
 *     <td align="center" valign="top"> y
 *     <td align="center" valign="top"> y
 *     <td align="center" valign="top"> y
 *     <td align="center" valign="top"> y
 *     <td> The result will be left-justified.
 *
 * <tr><td> '#' <td align="center" valign="top"> y<sup>1</sup>
 *     <td align="center" valign="top"> -
 *     <td align="center" valign="top"> y<sup>3</sup>
 *     <td align="center" valign="top"> y
 *     <td align="center" valign="top"> -
 *     <td> The result should use a conversion-dependent alternate form
 *
 * <tr><td> '+' <td align="center" valign="top"> -
 *     <td align="center" valign="top"> -
 *     <td align="center" valign="top"> y<sup>4</sup>
 *     <td align="center" valign="top"> y
 *     <td align="center" valign="top"> -
 *     <td> The result will always include a sign
 *
 * <tr><td> '&nbsp;&nbsp;' <td align="center" valign="top"> -
 *     <td align="center" valign="top"> -
 *     <td align="center" valign="top"> y<sup>4</sup>
 *     <td align="center" valign="top"> y
 *     <td align="center" valign="top"> -
 *     <td> The result will include a leading space for positive values
 *
 * <tr><td> '0' <td align="center" valign="top"> -
 *     <td align="center" valign="top"> -
 *     <td align="center" valign="top"> y
 *     <td align="center" valign="top"> y
 *     <td align="center" valign="top"> -
 *     <td> The result will be zero-padded
 *
 * <tr><td> ',' <td align="center" valign="top"> -
 *     <td align="center" valign="top"> -
 *     <td align="center" valign="top"> y<sup>2</sup>
 *     <td align="center" valign="top"> y<sup>5</sup>
 *     <td align="center" valign="top"> -
 *     <td> The result will include locale-specific {@linkplain
 *     java.text.DecimalFormatSymbols#getGroupingSeparator grouping separators}
 *
 * <tr><td> '(' <td align="center" valign="top"> -
 *     <td align="center" valign="top"> -
 *     <td align="center" valign="top"> y<sup>4</sup>
 *     <td align="center" valign="top"> y<sup>5</sup>
 *     <td align="center"> -
 *     <td> The result will enclose negative numbers in parentheses
 *
 * </table>
 *
 * <p> <sup>1</sup> Depends on the definition of {@link Formattable}.
 *
 * <p> <sup>2</sup> For {@code 'd'} conversion only.
 *
 * <p> <sup>3</sup> For {@code 'o'}, {@code 'x'}, and {@code 'X'}
 * conversions only.
 *
 * <p> <sup>4</sup> For {@code 'd'}, {@code 'o'}, {@code 'x'}, and
 * {@code 'X'} conversions applied to {@link java.math.BigInteger BigInteger}
 * or {@code 'd'} applied to {@code byte}, {@link Byte}, {@code short}, {@link
 * Short}, {@code int} and {@link Integer}, {@code long}, and {@link Long}.
 *
 * <p> <sup>5</sup> For {@code 'e'}, {@code 'E'}, {@code 'f'},
 * {@code 'g'}, and {@code 'G'} conversions only.
 *
 * <p> Any characters not explicitly defined as flags are illegal and are
 * reserved for future extensions.
 *
 * <h4> Width </h4>
 *
 * <p> The width is the minimum number of characters to be written to the
 * output.  For the line separator conversion, width is not applicable; if it
 * is provided, an exception will be thrown.
 *
 * <h4> Precision </h4>
 *
 * <p> For general argument types, the precision is the maximum number of
 * characters to be written to the output.
 *
 * <p> For the floating-point conversions {@code 'e'}, {@code 'E'}, and
 * {@code 'f'} the precision is the number of digits after the decimal
 * separator.  If the conversion is {@code 'g'} or {@code 'G'}, then the
 * precision is the total number of digits in the resulting magnitude after
 * rounding.  If the conversion is {@code 'a'} or {@code 'A'}, then the
 * precision must not be specified.
 *
 * <p> For character, integral, and date/time argument types and the percent
 * and line separator conversions, the precision is not applicable; if a
 * precision is provided, an exception will be thrown.
 *
 * <h4> Argument Index </h4>
 *
 * <p> The argument index is a decimal integer indicating the position of the
 * argument in the argument list.  The first argument is referenced by
 * "{@code 1$}", the second by "{@code 2$}", etc.
 *
 * <p> Another way to reference arguments by position is to use the
 * {@code '<'} (<tt>'&#92;u003c'</tt>) flag, which causes the argument for
 * the previous format specifier to be re-used.  For example, the following two
 * statements would produce identical strings:
 *
 * <blockquote><pre>
 *   Calendar c = ...;
 *   String s1 = String.format("Duke's Birthday: %1$tm %1$te,%1$tY", c);
 *
 *   String s2 = String.format("Duke's Birthday: %1$tm %&lt;te,%&lt;tY", c);
 * </pre></blockquote>
 *
 * <hr>
 * <h3><a name="detail">Details</a></h3>
 *
 * <p> This section is intended to provide behavioral details for formatting,
 * including conditions and exceptions, supported data types, localization, and
 * interactions between flags, conversions, and data types.  For an overview of
 * formatting concepts, refer to the <a href="#summary">Summary</a>
 *
 * <p> Any characters not explicitly defined as conversions, date/time
 * conversion suffixes, or flags are illegal and are reserved for
 * future extensions.  Use of such a character in a format string will
 * cause an {@link UnknownFormatConversionException} or {@link
 * UnknownFormatFlagsException} to be thrown.
 *
 * <p> If the format specifier contains a width or precision with an invalid
 * value or which is otherwise unsupported, then a {@link
 * IllegalFormatWidthException} or {@link IllegalFormatPrecisionException}
 * respectively will be thrown.
 *
 * <p> If a format specifier contains a conversion character that is not
 * applicable to the corresponding argument, then an {@link
 * IllegalFormatConversionException} will be thrown.
 *
 * <p> All specified exceptions may be thrown by any of the {@code format}
 * methods of {@code Formatter} as well as by any {@code format} convenience
 * methods such as {@link String#format(String,Object...) String.format} and
 * {@link java.io.PrintStream#printf(String,Object...) PrintStream.printf}.
 *
 * <p> Conversions denoted by an upper-case character (i.e. {@code 'B'},
 * {@code 'H'}, {@code 'S'}, {@code 'C'}, {@code 'X'}, {@code 'E'},
 * {@code 'G'}, {@code 'A'}, and {@code 'T'}) are the same as those for the
 * corresponding lower-case conversion characters except that the result is
 * converted to upper case according to the rules of the prevailing {@link
 * java.util.Locale Locale}.  The result is equivalent to the following
 * invocation of {@link String#toUpperCase()}
 *
 * <pre>
 *    out.toUpperCase() </pre>
 *
 * <h4><a name="dgen">General</a></h4>
 *
 * <p> The following general conversions may be applied to any argument type:
 *
 * <table cellpadding=5 summary="dgConv">
 *
 * <tr><td valign="top"> {@code 'b'}
 *     <td valign="top"> <tt>'&#92;u0062'</tt>
 *     <td> Produces either "{@code true}" or "{@code false}" as returned by
 *     {@link Boolean#toString(boolean)}.
 *
 *     <p> If the argument is {@code null}, then the result is
 *     "{@code false}".  If the argument is a {@code boolean} or {@link
 *     Boolean}, then the result is the string returned by {@link
 *     String#valueOf(boolean) String.valueOf()}.  Otherwise, the result is
 *     "{@code true}".
 *
 *     <p> If the {@code '#'} flag is given, then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'B'}
 *     <td valign="top"> <tt>'&#92;u0042'</tt>
 *     <td> The upper-case variant of {@code 'b'}.
 *
 * <tr><td valign="top"> {@code 'h'}
 *     <td valign="top"> <tt>'&#92;u0068'</tt>
 *     <td> Produces a string representing the hash code value of the object.
 *
 *     <p> If the argument, <i>arg</i> is {@code null}, then the
 *     result is "{@code null}".  Otherwise, the result is obtained
 *     by invoking {@code Integer.toHexString(arg.hashCode())}.
 *
 *     <p> If the {@code '#'} flag is given, then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'H'}
 *     <td valign="top"> <tt>'&#92;u0048'</tt>
 *     <td> The upper-case variant of {@code 'h'}.
 *
 * <tr><td valign="top"> {@code 's'}
 *     <td valign="top"> <tt>'&#92;u0073'</tt>
 *     <td> Produces a string.
 *
 *     <p> If the argument is {@code null}, then the result is
 *     "{@code null}".  If the argument implements {@link Formattable}, then
 *     its {@link Formattable#formatTo formatTo} method is invoked.
 *     Otherwise, the result is obtained by invoking the argument's
 *     {@code toString()} method.
 *
 *     <p> If the {@code '#'} flag is given and the argument is not a {@link
 *     Formattable} , then a {@link FormatFlagsConversionMismatchException}
 *     will be thrown.
 *
 * <tr><td valign="top"> {@code 'S'}
 *     <td valign="top"> <tt>'&#92;u0053'</tt>
 *     <td> The upper-case variant of {@code 's'}.
 *
 * </table>
 *
 * <p> The following <a name="dFlags">flags</a> apply to general conversions:
 *
 * <table cellpadding=5 summary="dFlags">
 *
 * <tr><td valign="top"> {@code '-'}
 *     <td valign="top"> <tt>'&#92;u002d'</tt>
 *     <td> Left justifies the output.  Spaces (<tt>'&#92;u0020'</tt>) will be
 *     added at the end of the converted value as required to fill the minimum
 *     width of the field.  If the width is not provided, then a {@link
 *     MissingFormatWidthException} will be thrown.  If this flag is not given
 *     then the output will be right-justified.
 *
 * <tr><td valign="top"> {@code '#'}
 *     <td valign="top"> <tt>'&#92;u0023'</tt>
 *     <td> Requires the output use an alternate form.  The definition of the
 *     form is specified by the conversion.
 *
 * </table>
 *
 * <p> The <a name="genWidth">width</a> is the minimum number of characters to
 * be written to the
 * output.  If the length of the converted value is less than the width then
 * the output will be padded by <tt>'&nbsp;&nbsp;'</tt> (<tt>'&#92;u0020'</tt>)
 * until the total number of characters equals the width.  The padding is on
 * the left by default.  If the {@code '-'} flag is given, then the padding
 * will be on the right.  If the width is not specified then there is no
 * minimum.
 *
 * <p> The precision is the maximum number of characters to be written to the
 * output.  The precision is applied before the width, thus the output will be
 * truncated to {@code precision} characters even if the width is greater than
 * the precision.  If the precision is not specified then there is no explicit
 * limit on the number of characters.
 *
 * <h4><a name="dchar">Character</a></h4>
 *
 * This conversion may be applied to {@code char} and {@link Character}.  It
 * may also be applied to the types {@code byte}, {@link Byte},
 * {@code short}, and {@link Short}, {@code int} and {@link Integer} when
 * {@link Character#isValidCodePoint} returns {@code true}.  If it returns
 * {@code false} then an {@link IllegalFormatCodePointException} will be
 * thrown.
 *
 * <table cellpadding=5 summary="charConv">
 *
 * <tr><td valign="top"> {@code 'c'}
 *     <td valign="top"> <tt>'&#92;u0063'</tt>
 *     <td> Formats the argument as a Unicode character as described in <a
 *     href="../lang/Character.html#unicode">Unicode Character
 *     Representation</a>.  This may be more than one 16-bit {@code char} in
 *     the case where the argument represents a supplementary character.
 *
 *     <p> If the {@code '#'} flag is given, then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'C'}
 *     <td valign="top"> <tt>'&#92;u0043'</tt>
 *     <td> The upper-case variant of {@code 'c'}.
 *
 * </table>
 *
 * <p> The {@code '-'} flag defined for <a href="#dFlags">General
 * conversions</a> applies.  If the {@code '#'} flag is given, then a {@link
 * FormatFlagsConversionMismatchException} will be thrown.
 *
 * <p> The width is defined as for <a href="#genWidth">General conversions</a>.
 *
 * <p> The precision is not applicable.  If the precision is specified then an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * <h4><a name="dnum">Numeric</a></h4>
 *
 * <p> Numeric conversions are divided into the following categories:
 *
 * <ol>
 *
 * <li> <a href="#dnint"><b>Byte, Short, Integer, and Long</b></a>
 *
 * <li> <a href="#dnbint"><b>BigInteger</b></a>
 *
 * <li> <a href="#dndec"><b>Float and Double</b></a>
 *
 * <li> <a href="#dndec"><b>BigDecimal</b></a>
 *
 * </ol>
 *
 * <p> Numeric types will be formatted according to the following algorithm:
 *
 * <p><b><a name="l10n algorithm"> Number Localization Algorithm</a></b>
 *
 * <p> After digits are obtained for the integer part, fractional part, and
 * exponent (as appropriate for the data type), the following transformation
 * is applied:
 *
 * <ol>
 *
 * <li> Each digit character <i>d</i> in the string is replaced by a
 * locale-specific digit computed relative to the current locale's
 * {@linkplain java.text.DecimalFormatSymbols#getZeroDigit() zero digit}
 * <i>z</i>; that is <i>d&nbsp;-&nbsp;</i> {@code '0'}
 * <i>&nbsp;+&nbsp;z</i>.
 *
 * <li> If a decimal separator is present, a locale-specific {@linkplain
 * java.text.DecimalFormatSymbols#getDecimalSeparator decimal separator} is
 * substituted.
 *
 * <li> If the {@code ','} (<tt>'&#92;u002c'</tt>)
 * <a name="l10n group">flag</a> is given, then the locale-specific {@linkplain
 * java.text.DecimalFormatSymbols#getGroupingSeparator grouping separator} is
 * inserted by scanning the integer part of the string from least significant
 * to most significant digits and inserting a separator at intervals defined by
 * the locale's {@linkplain java.text.DecimalFormat#getGroupingSize() grouping
 * size}.
 *
 * <li> If the {@code '0'} flag is given, then the locale-specific {@linkplain
 * java.text.DecimalFormatSymbols#getZeroDigit() zero digits} are inserted
 * after the sign character, if any, and before the first non-zero digit, until
 * the length of the string is equal to the requested field width.
 *
 * <li> If the value is negative and the {@code '('} flag is given, then a
 * {@code '('} (<tt>'&#92;u0028'</tt>) is prepended and a {@code ')'}
 * (<tt>'&#92;u0029'</tt>) is appended.
 *
 * <li> If the value is negative (or floating-point negative zero) and
 * {@code '('} flag is not given, then a {@code '-'} (<tt>'&#92;u002d'</tt>)
 * is prepended.
 *
 * <li> If the {@code '+'} flag is given and the value is positive or zero (or
 * floating-point positive zero), then a {@code '+'} (<tt>'&#92;u002b'</tt>)
 * will be prepended.
 *
 * </ol>
 *
 * <p> If the value is NaN or positive infinity the literal strings "NaN" or
 * "Infinity" respectively, will be output.  If the value is negative infinity,
 * then the output will be "(Infinity)" if the {@code '('} flag is given
 * otherwise the output will be "-Infinity".  These values are not localized.
 *
 * <p><a name="dnint"><b> Byte, Short, Integer, and Long </b></a>
 *
 * <p> The following conversions may be applied to {@code byte}, {@link Byte},
 * {@code short}, {@link Short}, {@code int} and {@link Integer},
 * {@code long}, and {@link Long}.
 *
 * <table cellpadding=5 summary="IntConv">
 *
 * <tr><td valign="top"> {@code 'd'}
 *     <td valign="top"> <tt>'&#92;u0054'</tt>
 *     <td> Formats the argument as a decimal integer. The <a
 *     href="#l10n algorithm">localization algorithm</a> is applied.
 *
 *     <p> If the {@code '0'} flag is given and the value is negative, then
 *     the zero padding will occur after the sign.
 *
 *     <p> If the {@code '#'} flag is given then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'o'}
 *     <td valign="top"> <tt>'&#92;u006f'</tt>
 *     <td> Formats the argument as an integer in base eight.  No localization
 *     is applied.
 *
 *     <p> If <i>x</i> is negative then the result will be an unsigned value
 *     generated by adding 2<sup>n</sup> to the value where {@code n} is the
 *     number of bits in the type as returned by the static {@code SIZE} field
 *     in the {@linkplain Byte#SIZE Byte}, {@linkplain Short#SIZE Short},
 *     {@linkplain Integer#SIZE Integer}, or {@linkplain Long#SIZE Long}
 *     classes as appropriate.
 *
 *     <p> If the {@code '#'} flag is given then the output will always begin
 *     with the radix indicator {@code '0'}.
 *
 *     <p> If the {@code '0'} flag is given then the output will be padded
 *     with leading zeros to the field width following any indication of sign.
 *
 *     <p> If {@code '('}, {@code '+'}, '&nbsp&nbsp;', or {@code ','} flags
 *     are given then a {@link FormatFlagsConversionMismatchException} will be
 *     thrown.
 *
 * <tr><td valign="top"> {@code 'x'}
 *     <td valign="top"> <tt>'&#92;u0078'</tt>
 *     <td> Formats the argument as an integer in base sixteen. No
 *     localization is applied.
 *
 *     <p> If <i>x</i> is negative then the result will be an unsigned value
 *     generated by adding 2<sup>n</sup> to the value where {@code n} is the
 *     number of bits in the type as returned by the static {@code SIZE} field
 *     in the {@linkplain Byte#SIZE Byte}, {@linkplain Short#SIZE Short},
 *     {@linkplain Integer#SIZE Integer}, or {@linkplain Long#SIZE Long}
 *     classes as appropriate.
 *
 *     <p> If the {@code '#'} flag is given then the output will always begin
 *     with the radix indicator {@code "0x"}.
 *
 *     <p> If the {@code '0'} flag is given then the output will be padded to
 *     the field width with leading zeros after the radix indicator or sign (if
 *     present).
 *
 *     <p> If {@code '('}, <tt>'&nbsp;&nbsp;'</tt>, {@code '+'}, or
 *     {@code ','} flags are given then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'X'}
 *     <td valign="top"> <tt>'&#92;u0058'</tt>
 *     <td> The upper-case variant of {@code 'x'}.  The entire string
 *     representing the number will be converted to {@linkplain
 *     String#toUpperCase upper case} including the {@code 'x'} (if any) and
 *     all hexadecimal digits {@code 'a'} - {@code 'f'}
 *     (<tt>'&#92;u0061'</tt> -  <tt>'&#92;u0066'</tt>).
 *
 * </table>
 *
 * <p> If the conversion is {@code 'o'}, {@code 'x'}, or {@code 'X'} and
 * both the {@code '#'} and the {@code '0'} flags are given, then result will
 * contain the radix indicator ({@code '0'} for octal and {@code "0x"} or
 * {@code "0X"} for hexadecimal), some number of zeros (based on the width),
 * and the value.
 *
 * <p> If the {@code '-'} flag is not given, then the space padding will occur
 * before the sign.
 *
 * <p> The following <a name="intFlags">flags</a> apply to numeric integral
 * conversions:
 *
 * <table cellpadding=5 summary="intFlags">
 *
 * <tr><td valign="top"> {@code '+'}
 *     <td valign="top"> <tt>'&#92;u002b'</tt>
 *     <td> Requires the output to include a positive sign for all positive
 *     numbers.  If this flag is not given then only negative values will
 *     include a sign.
 *
 *     <p> If both the {@code '+'} and <tt>'&nbsp;&nbsp;'</tt> flags are given
 *     then an {@link IllegalFormatFlagsException} will be thrown.
 *
 * <tr><td valign="top"> <tt>'&nbsp;&nbsp;'</tt>
 *     <td valign="top"> <tt>'&#92;u0020'</tt>
 *     <td> Requires the output to include a single extra space
 *     (<tt>'&#92;u0020'</tt>) for non-negative values.
 *
 *     <p> If both the {@code '+'} and <tt>'&nbsp;&nbsp;'</tt> flags are given
 *     then an {@link IllegalFormatFlagsException} will be thrown.
 *
 * <tr><td valign="top"> {@code '0'}
 *     <td valign="top"> <tt>'&#92;u0030'</tt>
 *     <td> Requires the output to be padded with leading {@linkplain
 *     java.text.DecimalFormatSymbols#getZeroDigit zeros} to the minimum field
 *     width following any sign or radix indicator except when converting NaN
 *     or infinity.  If the width is not provided, then a {@link
 *     MissingFormatWidthException} will be thrown.
 *
 *     <p> If both the {@code '-'} and {@code '0'} flags are given then an
 *     {@link IllegalFormatFlagsException} will be thrown.
 *
 * <tr><td valign="top"> {@code ','}
 *     <td valign="top"> <tt>'&#92;u002c'</tt>
 *     <td> Requires the output to include the locale-specific {@linkplain
 *     java.text.DecimalFormatSymbols#getGroupingSeparator group separators} as
 *     described in the <a href="#l10n group">"group" section</a> of the
 *     localization algorithm.
 *
 * <tr><td valign="top"> {@code '('}
 *     <td valign="top"> <tt>'&#92;u0028'</tt>
 *     <td> Requires the output to prepend a {@code '('}
 *     (<tt>'&#92;u0028'</tt>) and append a {@code ')'}
 *     (<tt>'&#92;u0029'</tt>) to negative values.
 *
 * </table>
 *
 * <p> If no <a name="intdFlags">flags</a> are given the default formatting is
 * as follows:
 *
 * <ul>
 *
 * <li> The output is right-justified within the {@code width}
 *
 * <li> Negative numbers begin with a {@code '-'} (<tt>'&#92;u002d'</tt>)
 *
 * <li> Positive numbers and zero do not include a sign or extra leading
 * space
 *
 * <li> No grouping separators are included
 *
 * </ul>
 *
 * <p> The <a name="intWidth">width</a> is the minimum number of characters to
 * be written to the output.  This includes any signs, digits, grouping
 * separators, radix indicator, and parentheses.  If the length of the
 * converted value is less than the width then the output will be padded by
 * spaces (<tt>'&#92;u0020'</tt>) until the total number of characters equals
 * width.  The padding is on the left by default.  If {@code '-'} flag is
 * given then the padding will be on the right.  If width is not specified then
 * there is no minimum.
 *
 * <p> The precision is not applicable.  If precision is specified then an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * <p><a name="dnbint"><b> BigInteger </b></a>
 *
 * <p> The following conversions may be applied to {@link
 * java.math.BigInteger}.
 *
 * <table cellpadding=5 summary="BIntConv">
 *
 * <tr><td valign="top"> {@code 'd'}
 *     <td valign="top"> <tt>'&#92;u0054'</tt>
 *     <td> Requires the output to be formatted as a decimal integer. The <a
 *     href="#l10n algorithm">localization algorithm</a> is applied.
 *
 *     <p> If the {@code '#'} flag is given {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'o'}
 *     <td valign="top"> <tt>'&#92;u006f'</tt>
 *     <td> Requires the output to be formatted as an integer in base eight.
 *     No localization is applied.
 *
 *     <p> If <i>x</i> is negative then the result will be a signed value
 *     beginning with {@code '-'} (<tt>'&#92;u002d'</tt>).  Signed output is
 *     allowed for this type because unlike the primitive types it is not
 *     possible to create an unsigned equivalent without assuming an explicit
 *     data-type size.
 *
 *     <p> If <i>x</i> is positive or zero and the {@code '+'} flag is given
 *     then the result will begin with {@code '+'} (<tt>'&#92;u002b'</tt>).
 *
 *     <p> If the {@code '#'} flag is given then the output will always begin
 *     with {@code '0'} prefix.
 *
 *     <p> If the {@code '0'} flag is given then the output will be padded
 *     with leading zeros to the field width following any indication of sign.
 *
 *     <p> If the {@code ','} flag is given then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'x'}
 *     <td valign="top"> <tt>'&#92;u0078'</tt>
 *     <td> Requires the output to be formatted as an integer in base
 *     sixteen.  No localization is applied.
 *
 *     <p> If <i>x</i> is negative then the result will be a signed value
 *     beginning with {@code '-'} (<tt>'&#92;u002d'</tt>).  Signed output is
 *     allowed for this type because unlike the primitive types it is not
 *     possible to create an unsigned equivalent without assuming an explicit
 *     data-type size.
 *
 *     <p> If <i>x</i> is positive or zero and the {@code '+'} flag is given
 *     then the result will begin with {@code '+'} (<tt>'&#92;u002b'</tt>).
 *
 *     <p> If the {@code '#'} flag is given then the output will always begin
 *     with the radix indicator {@code "0x"}.
 *
 *     <p> If the {@code '0'} flag is given then the output will be padded to
 *     the field width with leading zeros after the radix indicator or sign (if
 *     present).
 *
 *     <p> If the {@code ','} flag is given then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'X'}
 *     <td valign="top"> <tt>'&#92;u0058'</tt>
 *     <td> The upper-case variant of {@code 'x'}.  The entire string
 *     representing the number will be converted to {@linkplain
 *     String#toUpperCase upper case} including the {@code 'x'} (if any) and
 *     all hexadecimal digits {@code 'a'} - {@code 'f'}
 *     (<tt>'&#92;u0061'</tt> - <tt>'&#92;u0066'</tt>).
 *
 * </table>
 *
 * <p> If the conversion is {@code 'o'}, {@code 'x'}, or {@code 'X'} and
 * both the {@code '#'} and the {@code '0'} flags are given, then result will
 * contain the base indicator ({@code '0'} for octal and {@code "0x"} or
 * {@code "0X"} for hexadecimal), some number of zeros (based on the width),
 * and the value.
 *
 * <p> If the {@code '0'} flag is given and the value is negative, then the
 * zero padding will occur after the sign.
 *
 * <p> If the {@code '-'} flag is not given, then the space padding will occur
 * before the sign.
 *
 * <p> All <a href="#intFlags">flags</a> defined for Byte, Short, Integer, and
 * Long apply.  The <a href="#intdFlags">default behavior</a> when no flags are
 * given is the same as for Byte, Short, Integer, and Long.
 *
 * <p> The specification of <a href="#intWidth">width</a> is the same as
 * defined for Byte, Short, Integer, and Long.
 *
 * <p> The precision is not applicable.  If precision is specified then an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * <p><a name="dndec"><b> Float and Double</b></a>
 *
 * <p> The following conversions may be applied to {@code float}, {@link
 * Float}, {@code double} and {@link Double}.
 *
 * <table cellpadding=5 summary="floatConv">
 *
 * <tr><td valign="top"> {@code 'e'}
 *     <td valign="top"> <tt>'&#92;u0065'</tt>
 *     <td> Requires the output to be formatted using <a
 *     name="scientific">computerized scientific notation</a>.  The <a
 *     href="#l10n algorithm">localization algorithm</a> is applied.
 *
 *     <p> The formatting of the magnitude <i>m</i> depends upon its value.
 *
 *     <p> If <i>m</i> is NaN or infinite, the literal strings "NaN" or
 *     "Infinity", respectively, will be output.  These values are not
 *     localized.
 *
 *     <p> If <i>m</i> is positive-zero or negative-zero, then the exponent
 *     will be {@code "+00"}.
 *
 *     <p> Otherwise, the result is a string that represents the sign and
 *     magnitude (absolute value) of the argument.  The formatting of the sign
 *     is described in the <a href="#l10n algorithm">localization
 *     algorithm</a>. The formatting of the magnitude <i>m</i> depends upon its
 *     value.
 *
 *     <p> Let <i>n</i> be the unique integer such that 10<sup><i>n</i></sup>
 *     &lt;= <i>m</i> &lt; 10<sup><i>n</i>+1</sup>; then let <i>a</i> be the
 *     mathematically exact quotient of <i>m</i> and 10<sup><i>n</i></sup> so
 *     that 1 &lt;= <i>a</i> &lt; 10. The magnitude is then represented as the
 *     integer part of <i>a</i>, as a single decimal digit, followed by the
 *     decimal separator followed by decimal digits representing the fractional
 *     part of <i>a</i>, followed by the exponent symbol {@code 'e'}
 *     (<tt>'&#92;u0065'</tt>), followed by the sign of the exponent, followed
 *     by a representation of <i>n</i> as a decimal integer, as produced by the
 *     method {@link Long#toString(long, int)}, and zero-padded to include at
 *     least two digits.
 *
 *     <p> The number of digits in the result for the fractional part of
 *     <i>m</i> or <i>a</i> is equal to the precision.  If the precision is not
 *     specified then the default value is {@code 6}. If the precision is less
 *     than the number of digits which would appear after the decimal point in
 *     the string returned by {@link Float#toString(float)} or {@link
 *     Double#toString(double)} respectively, then the value will be rounded
 *     using the {@linkplain java.math.BigDecimal#ROUND_HALF_UP round half up
 *     algorithm}.  Otherwise, zeros may be appended to reach the precision.
 *     For a canonical representation of the value, use {@link
 *     Float#toString(float)} or {@link Double#toString(double)} as
 *     appropriate.
 *
 *     <p>If the {@code ','} flag is given, then an {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'E'}
 *     <td valign="top"> <tt>'&#92;u0045'</tt>
 *     <td> The upper-case variant of {@code 'e'}.  The exponent symbol
 *     will be {@code 'E'} (<tt>'&#92;u0045'</tt>).
 *
 * <tr><td valign="top"> {@code 'g'}
 *     <td valign="top"> <tt>'&#92;u0067'</tt>
 *     <td> Requires the output to be formatted in general scientific notation
 *     as described below. The <a href="#l10n algorithm">localization
 *     algorithm</a> is applied.
 *
 *     <p> After rounding for the precision, the formatting of the resulting
 *     magnitude <i>m</i> depends on its value.
 *
 *     <p> If <i>m</i> is greater than or equal to 10<sup>-4</sup> but less
 *     than 10<sup>precision</sup> then it is represented in <i><a
 *     href="#decimal">decimal format</a></i>.
 *
 *     <p> If <i>m</i> is less than 10<sup>-4</sup> or greater than or equal to
 *     10<sup>precision</sup>, then it is represented in <i><a
 *     href="#scientific">computerized scientific notation</a></i>.
 *
 *     <p> The total number of significant digits in <i>m</i> is equal to the
 *     precision.  If the precision is not specified, then the default value is
 *     {@code 6}.  If the precision is {@code 0}, then it is taken to be
 *     {@code 1}.
 *
 *     <p> If the {@code '#'} flag is given then an {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'G'}
 *     <td valign="top"> <tt>'&#92;u0047'</tt>
 *     <td> The upper-case variant of {@code 'g'}.
 *
 * <tr><td valign="top"> {@code 'f'}
 *     <td valign="top"> <tt>'&#92;u0066'</tt>
 *     <td> Requires the output to be formatted using <a name="decimal">decimal
 *     format</a>.  The <a href="#l10n algorithm">localization algorithm</a> is
 *     applied.
 *
 *     <p> The result is a string that represents the sign and magnitude
 *     (absolute value) of the argument.  The formatting of the sign is
 *     described in the <a href="#l10n algorithm">localization
 *     algorithm</a>. The formatting of the magnitude <i>m</i> depends upon its
 *     value.
 *
 *     <p> If <i>m</i> NaN or infinite, the literal strings "NaN" or
 *     "Infinity", respectively, will be output.  These values are not
 *     localized.
 *
 *     <p> The magnitude is formatted as the integer part of <i>m</i>, with no
 *     leading zeroes, followed by the decimal separator followed by one or
 *     more decimal digits representing the fractional part of <i>m</i>.
 *
 *     <p> The number of digits in the result for the fractional part of
 *     <i>m</i> or <i>a</i> is equal to the precision.  If the precision is not
 *     specified then the default value is {@code 6}. If the precision is less
 *     than the number of digits which would appear after the decimal point in
 *     the string returned by {@link Float#toString(float)} or {@link
 *     Double#toString(double)} respectively, then the value will be rounded
 *     using the {@linkplain java.math.BigDecimal#ROUND_HALF_UP round half up
 *     algorithm}.  Otherwise, zeros may be appended to reach the precision.
 *     For a canonical representation of the value, use {@link
 *     Float#toString(float)} or {@link Double#toString(double)} as
 *     appropriate.
 *
 * <tr><td valign="top"> {@code 'a'}
 *     <td valign="top"> <tt>'&#92;u0061'</tt>
 *     <td> Requires the output to be formatted in hexadecimal exponential
 *     form.  No localization is applied.
 *
 *     <p> The result is a string that represents the sign and magnitude
 *     (absolute value) of the argument <i>x</i>.
 *
 *     <p> If <i>x</i> is negative or a negative-zero value then the result
 *     will begin with {@code '-'} (<tt>'&#92;u002d'</tt>).
 *
 *     <p> If <i>x</i> is positive or a positive-zero value and the
 *     {@code '+'} flag is given then the result will begin with {@code '+'}
 *     (<tt>'&#92;u002b'</tt>).
 *
 *     <p> The formatting of the magnitude <i>m</i> depends upon its value.
 *
 *     <ul>
 *
 *     <li> If the value is NaN or infinite, the literal strings "NaN" or
 *     "Infinity", respectively, will be output.
 *
 *     <li> If <i>m</i> is zero then it is represented by the string
 *     {@code "0x0.0p0"}.
 *
 *     <li> If <i>m</i> is a {@code double} value with a normalized
 *     representation then substrings are used to represent the significand and
 *     exponent fields.  The significand is represented by the characters
 *     {@code "0x1."} followed by the hexadecimal representation of the rest
 *     of the significand as a fraction.  The exponent is represented by
 *     {@code 'p'} (<tt>'&#92;u0070'</tt>) followed by a decimal string of the
 *     unbiased exponent as if produced by invoking {@link
 *     Integer#toString(int) Integer.toString} on the exponent value.
 *
 *     <li> If <i>m</i> is a {@code double} value with a subnormal
 *     representation then the significand is represented by the characters
 *     {@code '0x0.'} followed by the hexadecimal representation of the rest
 *     of the significand as a fraction.  The exponent is represented by
 *     {@code 'p-1022'}.  Note that there must be at least one nonzero digit
 *     in a subnormal significand.
 *
 *     </ul>
 *
 *     <p> If the {@code '('} or {@code ','} flags are given, then a {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'A'}
 *     <td valign="top"> <tt>'&#92;u0041'</tt>
 *     <td> The upper-case variant of {@code 'a'}.  The entire string
 *     representing the number will be converted to upper case including the
 *     {@code 'x'} (<tt>'&#92;u0078'</tt>) and {@code 'p'}
 *     (<tt>'&#92;u0070'</tt> and all hexadecimal digits {@code 'a'} -
 *     {@code 'f'} (<tt>'&#92;u0061'</tt> - <tt>'&#92;u0066'</tt>).
 *
 * </table>
 *
 * <p> All <a href="#intFlags">flags</a> defined for Byte, Short, Integer, and
 * Long apply.
 *
 * <p> If the {@code '#'} flag is given, then the decimal separator will
 * always be present.
 *
 * <p> If no <a name="floatdFlags">flags</a> are given the default formatting
 * is as follows:
 *
 * <ul>
 *
 * <li> The output is right-justified within the {@code width}
 *
 * <li> Negative numbers begin with a {@code '-'}
 *
 * <li> Positive numbers and positive zero do not include a sign or extra
 * leading space
 *
 * <li> No grouping separators are included
 *
 * <li> The decimal separator will only appear if a digit follows it
 *
 * </ul>
 *
 * <p> The <a name="floatDWidth">width</a> is the minimum number of characters
 * to be written to the output.  This includes any signs, digits, grouping
 * separators, decimal separators, exponential symbol, radix indicator,
 * parentheses, and strings representing infinity and NaN as applicable.  If
 * the length of the converted value is less than the width then the output
 * will be padded by spaces (<tt>'&#92;u0020'</tt>) until the total number of
 * characters equals width.  The padding is on the left by default.  If the
 * {@code '-'} flag is given then the padding will be on the right.  If width
 * is not specified then there is no minimum.
 *
 * <p> If the <a name="floatDPrec">conversion</a> is {@code 'e'},
 * {@code 'E'} or {@code 'f'}, then the precision is the number of digits
 * after the decimal separator.  If the precision is not specified, then it is
 * assumed to be {@code 6}.
 *
 * <p> If the conversion is {@code 'g'} or {@code 'G'}, then the precision is
 * the total number of significant digits in the resulting magnitude after
 * rounding.  If the precision is not specified, then the default value is
 * {@code 6}.  If the precision is {@code 0}, then it is taken to be
 * {@code 1}.
 *
 * <p> If the conversion is {@code 'a'} or {@code 'A'}, then the precision
 * is the number of hexadecimal digits after the decimal separator.  If the
 * precision is not provided, then all of the digits as returned by {@link
 * Double#toHexString(double)} will be output.
 *
 * <p><a name="dndec"><b> BigDecimal </b></a>
 *
 * <p> The following conversions may be applied {@link java.math.BigDecimal
 * BigDecimal}.
 *
 * <table cellpadding=5 summary="floatConv">
 *
 * <tr><td valign="top"> {@code 'e'}
 *     <td valign="top"> <tt>'&#92;u0065'</tt>
 *     <td> Requires the output to be formatted using <a
 *     name="scientific">computerized scientific notation</a>.  The <a
 *     href="#l10n algorithm">localization algorithm</a> is applied.
 *
 *     <p> The formatting of the magnitude <i>m</i> depends upon its value.
 *
 *     <p> If <i>m</i> is positive-zero or negative-zero, then the exponent
 *     will be {@code "+00"}.
 *
 *     <p> Otherwise, the result is a string that represents the sign and
 *     magnitude (absolute value) of the argument.  The formatting of the sign
 *     is described in the <a href="#l10n algorithm">localization
 *     algorithm</a>. The formatting of the magnitude <i>m</i> depends upon its
 *     value.
 *
 *     <p> Let <i>n</i> be the unique integer such that 10<sup><i>n</i></sup>
 *     &lt;= <i>m</i> &lt; 10<sup><i>n</i>+1</sup>; then let <i>a</i> be the
 *     mathematically exact quotient of <i>m</i> and 10<sup><i>n</i></sup> so
 *     that 1 &lt;= <i>a</i> &lt; 10. The magnitude is then represented as the
 *     integer part of <i>a</i>, as a single decimal digit, followed by the
 *     decimal separator followed by decimal digits representing the fractional
 *     part of <i>a</i>, followed by the exponent symbol {@code 'e'}
 *     (<tt>'&#92;u0065'</tt>), followed by the sign of the exponent, followed
 *     by a representation of <i>n</i> as a decimal integer, as produced by the
 *     method {@link Long#toString(long, int)}, and zero-padded to include at
 *     least two digits.
 *
 *     <p> The number of digits in the result for the fractional part of
 *     <i>m</i> or <i>a</i> is equal to the precision.  If the precision is not
 *     specified then the default value is {@code 6}.  If the precision is
 *     less than the number of digits to the right of the decimal point then
 *     the value will be rounded using the
 *     {@linkplain java.math.BigDecimal#ROUND_HALF_UP round half up
 *     algorithm}.  Otherwise, zeros may be appended to reach the precision.
 *     For a canonical representation of the value, use {@link
 *     BigDecimal#toString()}.
 *
 *     <p> If the {@code ','} flag is given, then an {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'E'}
 *     <td valign="top"> <tt>'&#92;u0045'</tt>
 *     <td> The upper-case variant of {@code 'e'}.  The exponent symbol
 *     will be {@code 'E'} (<tt>'&#92;u0045'</tt>).
 *
 * <tr><td valign="top"> {@code 'g'}
 *     <td valign="top"> <tt>'&#92;u0067'</tt>
 *     <td> Requires the output to be formatted in general scientific notation
 *     as described below. The <a href="#l10n algorithm">localization
 *     algorithm</a> is applied.
 *
 *     <p> After rounding for the precision, the formatting of the resulting
 *     magnitude <i>m</i> depends on its value.
 *
 *     <p> If <i>m</i> is greater than or equal to 10<sup>-4</sup> but less
 *     than 10<sup>precision</sup> then it is represented in <i><a
 *     href="#decimal">decimal format</a></i>.
 *
 *     <p> If <i>m</i> is less than 10<sup>-4</sup> or greater than or equal to
 *     10<sup>precision</sup>, then it is represented in <i><a
 *     href="#scientific">computerized scientific notation</a></i>.
 *
 *     <p> The total number of significant digits in <i>m</i> is equal to the
 *     precision.  If the precision is not specified, then the default value is
 *     {@code 6}.  If the precision is {@code 0}, then it is taken to be
 *     {@code 1}.
 *
 *     <p> If the {@code '#'} flag is given then an {@link
 *     FormatFlagsConversionMismatchException} will be thrown.
 *
 * <tr><td valign="top"> {@code 'G'}
 *     <td valign="top"> <tt>'&#92;u0047'</tt>
 *     <td> The upper-case variant of {@code 'g'}.
 *
 * <tr><td valign="top"> {@code 'f'}
 *     <td valign="top"> <tt>'&#92;u0066'</tt>
 *     <td> Requires the output to be formatted using <a name="decimal">decimal
 *     format</a>.  The <a href="#l10n algorithm">localization algorithm</a> is
 *     applied.
 *
 *     <p> The result is a string that represents the sign and magnitude
 *     (absolute value) of the argument.  The formatting of the sign is
 *     described in the <a href="#l10n algorithm">localization
 *     algorithm</a>. The formatting of the magnitude <i>m</i> depends upon its
 *     value.
 *
 *     <p> The magnitude is formatted as the integer part of <i>m</i>, with no
 *     leading zeroes, followed by the decimal separator followed by one or
 *     more decimal digits representing the fractional part of <i>m</i>.
 *
 *     <p> The number of digits in the result for the fractional part of
 *     <i>m</i> or <i>a</i> is equal to the precision. If the precision is not
 *     specified then the default value is {@code 6}.  If the precision is
 *     less than the number of digits to the right of the decimal point
 *     then the value will be rounded using the
 *     {@linkplain java.math.BigDecimal#ROUND_HALF_UP round half up
 *     algorithm}.  Otherwise, zeros may be appended to reach the precision.
 *     For a canonical representation of the value, use {@link
 *     BigDecimal#toString()}.
 *
 * </table>
 *
 * <p> All <a href="#intFlags">flags</a> defined for Byte, Short, Integer, and
 * Long apply.
 *
 * <p> If the {@code '#'} flag is given, then the decimal separator will
 * always be present.
 *
 * <p> The <a href="#floatdFlags">default behavior</a> when no flags are
 * given is the same as for Float and Double.
 *
 * <p> The specification of <a href="#floatDWidth">width</a> and <a
 * href="#floatDPrec">precision</a> is the same as defined for Float and
 * Double.
 *
 * <h4><a name="ddt">Date/Time</a></h4>
 *
 * <p> This conversion may be applied to {@code long}, {@link Long}, {@link
 * Calendar}, and {@link Date}.
 *
 * <table cellpadding=5 summary="DTConv">
 *
 * <tr><td valign="top"> {@code 't'}
 *     <td valign="top"> <tt>'&#92;u0074'</tt>
 *     <td> Prefix for date and time conversion characters.
 * <tr><td valign="top"> {@code 'T'}
 *     <td valign="top"> <tt>'&#92;u0054'</tt>
 *     <td> The upper-case variant of {@code 't'}.
 *
 * </table>
 *
 * <p> The following date and time conversion character suffixes are defined
 * for the {@code 't'} and {@code 'T'} conversions.  The types are similar to
 * but not completely identical to those defined by GNU {@code date} and
 * POSIX {@code strftime(3c)}.  Additional conversion types are provided to
 * access Java-specific functionality (e.g. {@code 'L'} for milliseconds
 * within the second).
 *
 * <p> The following conversion characters are used for formatting times:
 *
 * <table cellpadding=5 summary="time">
 *
 * <tr><td valign="top"> {@code 'H'}
 *     <td valign="top"> <tt>'&#92;u0048'</tt>
 *     <td> Hour of the day for the 24-hour clock, formatted as two digits with
 *     a leading zero as necessary i.e. {@code 00 - 23}. {@code 00}
 *     corresponds to midnight.
 *
 * <tr><td valign="top">{@code 'I'}
 *     <td valign="top"> <tt>'&#92;u0049'</tt>
 *     <td> Hour for the 12-hour clock, formatted as two digits with a leading
 *     zero as necessary, i.e.  {@code 01 - 12}.  {@code 01} corresponds to
 *     one o'clock (either morning or afternoon).
 *
 * <tr><td valign="top">{@code 'k'}
 *     <td valign="top"> <tt>'&#92;u006b'</tt>
 *     <td> Hour of the day for the 24-hour clock, i.e. {@code 0 - 23}.
 *     {@code 0} corresponds to midnight.
 *
 * <tr><td valign="top">{@code 'l'}
 *     <td valign="top"> <tt>'&#92;u006c'</tt>
 *     <td> Hour for the 12-hour clock, i.e. {@code 1 - 12}.  {@code 1}
 *     corresponds to one o'clock (either morning or afternoon).
 *
 * <tr><td valign="top">{@code 'M'}
 *     <td valign="top"> <tt>'&#92;u004d'</tt>
 *     <td> Minute within the hour formatted as two digits with a leading zero
 *     as necessary, i.e.  {@code 00 - 59}.
 *
 * <tr><td valign="top">{@code 'S'}
 *     <td valign="top"> <tt>'&#92;u0053'</tt>
 *     <td> Seconds within the minute, formatted as two digits with a leading
 *     zero as necessary, i.e. {@code 00 - 60} ("{@code 60}" is a special
 *     value required to support leap seconds).
 *
 * <tr><td valign="top">{@code 'L'}
 *     <td valign="top"> <tt>'&#92;u004c'</tt>
 *     <td> Millisecond within the second formatted as three digits with
 *     leading zeros as necessary, i.e. {@code 000 - 999}.
 *
 * <tr><td valign="top">{@code 'N'}
 *     <td valign="top"> <tt>'&#92;u004e'</tt>
 *     <td> Nanosecond within the second, formatted as nine digits with leading
 *     zeros as necessary, i.e. {@code 000000000 - 999999999}.  The precision
 *     of this value is limited by the resolution of the underlying operating
 *     system or hardware.
 *
 * <tr><td valign="top">{@code 'p'}
 *     <td valign="top"> <tt>'&#92;u0070'</tt>
 *     <td> Locale-specific {@linkplain
 *     java.text.DateFormatSymbols#getAmPmStrings morning or afternoon} marker
 *     in lower case, e.g."{@code am}" or "{@code pm}".  Use of the
 *     conversion prefix {@code 'T'} forces this output to upper case.  (Note
 *     that {@code 'p'} produces lower-case output.  This is different from
 *     GNU {@code date} and POSIX {@code strftime(3c)} which produce
 *     upper-case output.)
 *
 * <tr><td valign="top">{@code 'z'}
 *     <td valign="top"> <tt>'&#92;u007a'</tt>
 *     <td> <a href="http://www.ietf.org/rfc/rfc0822.txt">RFC&nbsp;822</a>
 *     style numeric time zone offset from GMT, e.g. {@code -0800}.  This
 *     value will be adjusted as necessary for Daylight Saving Time.  For
 *     {@code long}, {@link Long}, and {@link Date} the time zone used is
 *     the {@linkplain TimeZone#getDefault() default time zone} for this
 *     instance of the Java virtual machine.
 *
 * <tr><td valign="top">{@code 'Z'}
 *     <td valign="top"> <tt>'&#92;u005a'</tt>
 *     <td> A string representing the abbreviation for the time zone.  This
 *     value will be adjusted as necessary for Daylight Saving Time.  For
 *     {@code long}, {@link Long}, and {@link Date} the time zone used is
 *     the {@linkplain TimeZone#getDefault() default time zone} for this
 *     instance of the Java virtual machine.  The Formatter's locale will
 *     supersede the locale of the argument (if any).
 *
 * <tr><td valign="top">{@code 's'}
 *     <td valign="top"> <tt>'&#92;u0073'</tt>
 *     <td> Seconds since the beginning of the epoch starting at 1 January 1970
 *     {@code 00:00:00} UTC, i.e. {@code Long.MIN_VALUE/1000} to
 *     {@code Long.MAX_VALUE/1000}.
 *
 * <tr><td valign="top">{@code 'Q'}
 *     <td valign="top"> <tt>'&#92;u004f'</tt>
 *     <td> Milliseconds since the beginning of the epoch starting at 1 January
 *     1970 {@code 00:00:00} UTC, i.e. {@code Long.MIN_VALUE} to
 *     {@code Long.MAX_VALUE}. The precision of this value is limited by
 *     the resolution of the underlying operating system or hardware.
 *
 * </table>
 *
 * <p> The following conversion characters are used for formatting dates:
 *
 * <table cellpadding=5 summary="date">
 *
 * <tr><td valign="top">{@code 'B'}
 *     <td valign="top"> <tt>'&#92;u0042'</tt>
 *     <td> Locale-specific {@linkplain java.text.DateFormatSymbols#getMonths
 *     full month name}, e.g. {@code "January"}, {@code "February"}.
 *
 * <tr><td valign="top">{@code 'b'}
 *     <td valign="top"> <tt>'&#92;u0062'</tt>
 *     <td> Locale-specific {@linkplain
 *     java.text.DateFormatSymbols#getShortMonths abbreviated month name},
 *     e.g. {@code "Jan"}, {@code "Feb"}.
 *
 * <tr><td valign="top">{@code 'h'}
 *     <td valign="top"> <tt>'&#92;u0068'</tt>
 *     <td> Same as {@code 'b'}.
 *
 * <tr><td valign="top">{@code 'A'}
 *     <td valign="top"> <tt>'&#92;u0041'</tt>
 *     <td> Locale-specific full name of the {@linkplain
 *     java.text.DateFormatSymbols#getWeekdays day of the week},
 *     e.g. {@code "Sunday"}, {@code "Monday"}
 *
 * <tr><td valign="top">{@code 'a'}
 *     <td valign="top"> <tt>'&#92;u0061'</tt>
 *     <td> Locale-specific short name of the {@linkplain
 *     java.text.DateFormatSymbols#getShortWeekdays day of the week},
 *     e.g. {@code "Sun"}, {@code "Mon"}
 *
 * <tr><td valign="top">{@code 'C'}
 *     <td valign="top"> <tt>'&#92;u0043'</tt>
 *     <td> Four-digit year divided by {@code 100}, formatted as two digits
 *     with leading zero as necessary, i.e. {@code 00 - 99}
 *
 * <tr><td valign="top">{@code 'Y'}
 *     <td valign="top"> <tt>'&#92;u0059'</tt> <td> Year, formatted to at least
 *     four digits with leading zeros as necessary, e.g. {@code 0092} equals
 *     {@code 92} CE for the Gregorian calendar.
 *
 * <tr><td valign="top">{@code 'y'}
 *     <td valign="top"> <tt>'&#92;u0079'</tt>
 *     <td> Last two digits of the year, formatted with leading zeros as
 *     necessary, i.e. {@code 00 - 99}.
 *
 * <tr><td valign="top">{@code 'j'}
 *     <td valign="top"> <tt>'&#92;u006a'</tt>
 *     <td> Day of year, formatted as three digits with leading zeros as
 *     necessary, e.g. {@code 001 - 366} for the Gregorian calendar.
 *     {@code 001} corresponds to the first day of the year.
 *
 * <tr><td valign="top">{@code 'm'}
 *     <td valign="top"> <tt>'&#92;u006d'</tt>
 *     <td> Month, formatted as two digits with leading zeros as necessary,
 *     i.e. {@code 01 - 13}, where "{@code 01}" is the first month of the
 *     year and ("{@code 13}" is a special value required to support lunar
 *     calendars).
 *
 * <tr><td valign="top">{@code 'd'}
 *     <td valign="top"> <tt>'&#92;u0064'</tt>
 *     <td> Day of month, formatted as two digits with leading zeros as
 *     necessary, i.e. {@code 01 - 31}, where "{@code 01}" is the first day
 *     of the month.
 *
 * <tr><td valign="top">{@code 'e'}
 *     <td valign="top"> <tt>'&#92;u0065'</tt>
 *     <td> Day of month, formatted as two digits, i.e. {@code 1 - 31} where
 *     "{@code 1}" is the first day of the month.
 *
 * </table>
 *
 * <p> The following conversion characters are used for formatting common
 * date/time compositions.
 *
 * <table cellpadding=5 summary="composites">
 *
 * <tr><td valign="top">{@code 'R'}
 *     <td valign="top"> <tt>'&#92;u0052'</tt>
 *     <td> Time formatted for the 24-hour clock as {@code "%tH:%tM"}
 *
 * <tr><td valign="top">{@code 'T'}
 *     <td valign="top"> <tt>'&#92;u0054'</tt>
 *     <td> Time formatted for the 24-hour clock as {@code "%tH:%tM:%tS"}.
 *
 * <tr><td valign="top">{@code 'r'}
 *     <td valign="top"> <tt>'&#92;u0072'</tt>
 *     <td> Time formatted for the 12-hour clock as {@code "%tI:%tM:%tS
 *     %Tp"}.  The location of the morning or afternoon marker
 *     ({@code '%Tp'}) may be locale-dependent.
 *
 * <tr><td valign="top">{@code 'D'}
 *     <td valign="top"> <tt>'&#92;u0044'</tt>
 *     <td> Date formatted as {@code "%tm/%td/%ty"}.
 *
 * <tr><td valign="top">{@code 'F'}
 *     <td valign="top"> <tt>'&#92;u0046'</tt>
 *     <td> <a href="http://www.w3.org/TR/NOTE-datetime">ISO&nbsp;8601</a>
 *     complete date formatted as {@code "%tY-%tm-%td"}.
 *
 * <tr><td valign="top">{@code 'c'}
 *     <td valign="top"> <tt>'&#92;u0063'</tt>
 *     <td> Date and time formatted as {@code "%ta %tb %td %tT %tZ %tY"},
 *     e.g. {@code "Sun Jul 20 16:17:00 EDT 1969"}.
 *
 * </table>
 *
 * <p> The {@code '-'} flag defined for <a href="#dFlags">General
 * conversions</a> applies.  If the {@code '#'} flag is given, then a {@link
 * FormatFlagsConversionMismatchException} will be thrown.
 *
 * <p> The <a name="dtWidth">width</a> is the minimum number of characters to
 * be written to the output.  If the length of the converted value is less than
 * the {@code width} then the output will be padded by spaces
 * (<tt>'&#92;u0020'</tt>) until the total number of characters equals width.
 * The padding is on the left by default.  If the {@code '-'} flag is given
 * then the padding will be on the right.  If width is not specified then there
 * is no minimum.
 *
 * <p> The precision is not applicable.  If the precision is specified then an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * <h4><a name="dper">Percent</a></h4>
 *
 * <p> The conversion does not correspond to any argument.
 *
 * <table cellpadding=5 summary="DTConv">
 *
 * <tr><td valign="top">{@code '%'}
 *     <td> The result is a literal {@code '%'} (<tt>'&#92;u0025'</tt>)
 *
 * <p> The <a name="dtWidth">width</a> is the minimum number of characters to
 * be written to the output including the {@code '%'}.  If the length of the
 * converted value is less than the {@code width} then the output will be
 * padded by spaces (<tt>'&#92;u0020'</tt>) until the total number of
 * characters equals width.  The padding is on the left.  If width is not
 * specified then just the {@code '%'} is output.
 *
 * <p> The {@code '-'} flag defined for <a href="#dFlags">General
 * conversions</a> applies.  If any other flags are provided, then a
 * {@link FormatFlagsConversionMismatchException} will be thrown.
 *
 * <p> The precision is not applicable.  If the precision is specified an
 * {@link IllegalFormatPrecisionException} will be thrown.
 *
 * </table>
 *
 * <h4><a name="dls">Line Separator</a></h4>
 *
 * <p> The conversion does not correspond to any argument.
 *
 * <table cellpadding=5 summary="DTConv">
 *
 * <tr><td valign="top">{@code 'n'}
 *     <td> the platform-specific line separator as returned by {@link
 *     System#getProperty System.getProperty("line.separator")}.
 *
 * </table>
 *
 * <p> Flags, width, and precision are not applicable.  If any are provided an
 * {@link IllegalFormatFlagsException}, {@link IllegalFormatWidthException},
 * and {@link IllegalFormatPrecisionException}, respectively will be thrown.
 *
 * <h4><a name="dpos">Argument Index</a></h4>
 *
 * <p> Format specifiers can reference arguments in three ways:
 *
 * <ul>
 *
 * <li> <i>Explicit indexing</i> is used when the format specifier contains an
 * argument index.  The argument index is a decimal integer indicating the
 * position of the argument in the argument list.  The first argument is
 * referenced by "{@code 1$}", the second by "{@code 2$}", etc.  An argument
 * may be referenced more than once.
 *
 * <p> For example:
 *
 * <blockquote><pre>
 *   formatter.format("%4$s %3$s %2$s %1$s %4$s %3$s %2$s %1$s",
 *                    "a", "b", "c", "d")
 *   // -&gt; "d c b a d c b a"
 * </pre></blockquote>
 *
 * <li> <i>Relative indexing</i> is used when the format specifier contains a
 * {@code '<'} (<tt>'&#92;u003c'</tt>) flag which causes the argument for
 * the previous format specifier to be re-used.  If there is no previous
 * argument, then a {@link MissingFormatArgumentException} is thrown.
 *
 * <blockquote><pre>
 *    formatter.format("%s %s %&lt;s %&lt;s", "a", "b", "c", "d")
 *    // -&gt; "a b b b"
 *    // "c" and "d" are ignored because they are not referenced
 * </pre></blockquote>
 *
 * <li> <i>Ordinary indexing</i> is used when the format specifier contains
 * neither an argument index nor a {@code '<'} flag.  Each format specifier
 * which uses ordinary indexing is assigned a sequential implicit index into
 * argument list which is independent of the indices used by explicit or
 * relative indexing.
 *
 * <blockquote><pre>
 *   formatter.format("%s %s %s %s", "a", "b", "c", "d")
 *   // -&gt; "a b c d"
 * </pre></blockquote>
 *
 * </ul>
 *
 * <p> It is possible to have a format string which uses all forms of indexing,
 * for example:
 *
 * <blockquote><pre>
 *   formatter.format("%2$s %s %&lt;s %s", "a", "b", "c", "d")
 *   // -&gt; "b a a b"
 *   // "c" and "d" are ignored because they are not referenced
 * </pre></blockquote>
 *
 * <p> The maximum number of arguments is limited by the maximum dimension of a
 * Java array as defined by the <a
 * href="http://java.sun.com/docs/books/vmspec/">Java Virtual Machine
 * Specification</a>.  If the argument index is does not correspond to an
 * available argument, then a {@link MissingFormatArgumentException} is thrown.
 *
 * <p> If there are more arguments than format specifiers, the extra arguments
 * are ignored.
 *
 * <p> Unless otherwise specified, passing a {@code null} argument to any
 * method or constructor in this class will cause a {@link
 * NullPointerException} to be thrown.
 *
 * @author  Iris Clark
 * @since 1.5
 */
public final class Formatter implements Closeable, Flushable {
    private Appendable a;
    private final Locale l;

    private IOException lastException;

    private final char zero;
    private static double scaleUp;

    // 1 (sign) + 19 (max # sig digits) + 1 ('.') + 1 ('e') + 1 (sign)
    // + 3 (max # exp digits) + 4 (error) = 30
    private static final int MAX_FD_CHARS = 30;

    /**
     * Returns a charset object for the given charset name.
     * @throws NullPointerException          is csn is null
     * @throws UnsupportedEncodingException  if the charset is not supported
     */
    private static Charset toCharset(String csn)
        throws UnsupportedEncodingException
    {
        Objects.requireNonNull(csn, "charsetName");
        try {
            return Charset.forName(csn);
        } catch (IllegalCharsetNameException|UnsupportedCharsetException unused) {
            // UnsupportedEncodingException should be thrown
            throw new UnsupportedEncodingException(csn);
        }
    }

    private static final Appendable nonNullAppendable(Appendable a) {
        if (a == null)
            return new StringBuilder();

        return a;
    }

    /* Private constructors */
    private Formatter(Locale l, Appendable a) {
        this.a = a;
        this.l = l;
        this.zero = getZero(l);
    }

    private Formatter(Charset charset, Locale l, File file)
        throws FileNotFoundException
    {
        this(l,
             new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset)));
    }

    /**
     * Constructs a new formatter.
     *
     * <p> The destination of the formatted output is a {@link StringBuilder}
     * which may be retrieved by invoking {@link #out out()} and whose
     * current content may be converted into a string by invoking {@link
     * #toString toString()}.  The locale used is the {@linkplain
     * Locale#getDefault() default locale} for this instance of the Java
     * virtual machine.
     */
    public Formatter() {
        this(Locale.getDefault(Locale.Category.FORMAT), new StringBuilder());
    }

    /**
     * Constructs a new formatter with the specified destination.
     *
     * <p> The locale used is the {@linkplain Locale#getDefault() default
     * locale} for this instance of the Java virtual machine.
     *
     * @param  a
     *         Destination for the formatted output.  If {@code a} is
     *         {@code null} then a {@link StringBuilder} will be created.
     */
    public Formatter(Appendable a) {
        this(Locale.getDefault(Locale.Category.FORMAT), nonNullAppendable(a));
    }

    /**
     * Constructs a new formatter with the specified locale.
     *
     * <p> The destination of the formatted output is a {@link StringBuilder}
     * which may be retrieved by invoking {@link #out out()} and whose current
     * content may be converted into a string by invoking {@link #toString
     * toString()}.
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     */
    public Formatter(Locale l) {
        this(l, new StringBuilder());
    }

    /**
     * Constructs a new formatter with the specified destination and locale.
     *
     * @param  a
     *         Destination for the formatted output.  If {@code a} is
     *         {@code null} then a {@link StringBuilder} will be created.
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     */
    public Formatter(Appendable a, Locale l) {
        this(l, nonNullAppendable(a));
    }

    /**
     * Constructs a new formatter with the specified file name.
     *
     * <p> The charset used is the {@linkplain
     * java.nio.charset.Charset#defaultCharset() default charset} for this
     * instance of the Java virtual machine.
     *
     * <p> The locale used is the {@linkplain Locale#getDefault() default
     * locale} for this instance of the Java virtual machine.
     *
     * @param  fileName
     *         The name of the file to use as the destination of this
     *         formatter.  If the file exists then it will be truncated to
     *         zero size; otherwise, a new file will be created.  The output
     *         will be written to the file and is buffered.
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(fileName)} denies write
     *          access to the file
     *
     * @throws  FileNotFoundException
     *          If the given file name does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     */
    public Formatter(String fileName) throws FileNotFoundException {
        this(Locale.getDefault(Locale.Category.FORMAT),
             new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName))));
    }

    /**
     * Constructs a new formatter with the specified file name and charset.
     *
     * <p> The locale used is the {@linkplain Locale#getDefault default
     * locale} for this instance of the Java virtual machine.
     *
     * @param  fileName
     *         The name of the file to use as the destination of this
     *         formatter.  If the file exists then it will be truncated to
     *         zero size; otherwise, a new file will be created.  The output
     *         will be written to the file and is buffered.
     *
     * @param  csn
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @throws  FileNotFoundException
     *          If the given file name does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(fileName)} denies write
     *          access to the file
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     */
    public Formatter(String fileName, String csn)
        throws FileNotFoundException, UnsupportedEncodingException
    {
        this(fileName, csn, Locale.getDefault(Locale.Category.FORMAT));
    }

    /**
     * Constructs a new formatter with the specified file name, charset, and
     * locale.
     *
     * @param  fileName
     *         The name of the file to use as the destination of this
     *         formatter.  If the file exists then it will be truncated to
     *         zero size; otherwise, a new file will be created.  The output
     *         will be written to the file and is buffered.
     *
     * @param  csn
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     *
     * @throws  FileNotFoundException
     *          If the given file name does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(fileName)} denies write
     *          access to the file
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     */
    public Formatter(String fileName, String csn, Locale l)
        throws FileNotFoundException, UnsupportedEncodingException
    {
        this(toCharset(csn), l, new File(fileName));
    }

    /**
     * Constructs a new formatter with the specified file.
     *
     * <p> The charset used is the {@linkplain
     * java.nio.charset.Charset#defaultCharset() default charset} for this
     * instance of the Java virtual machine.
     *
     * <p> The locale used is the {@linkplain Locale#getDefault() default
     * locale} for this instance of the Java virtual machine.
     *
     * @param  file
     *         The file to use as the destination of this formatter.  If the
     *         file exists then it will be truncated to zero size; otherwise,
     *         a new file will be created.  The output will be written to the
     *         file and is buffered.
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(file.getPath())} denies
     *          write access to the file
     *
     * @throws  FileNotFoundException
     *          If the given file object does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     */
    public Formatter(File file) throws FileNotFoundException {
        this(Locale.getDefault(Locale.Category.FORMAT),
             new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file))));
    }

    /**
     * Constructs a new formatter with the specified file and charset.
     *
     * <p> The locale used is the {@linkplain Locale#getDefault default
     * locale} for this instance of the Java virtual machine.
     *
     * @param  file
     *         The file to use as the destination of this formatter.  If the
     *         file exists then it will be truncated to zero size; otherwise,
     *         a new file will be created.  The output will be written to the
     *         file and is buffered.
     *
     * @param  csn
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @throws  FileNotFoundException
     *          If the given file object does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(file.getPath())} denies
     *          write access to the file
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     */
    public Formatter(File file, String csn)
        throws FileNotFoundException, UnsupportedEncodingException
    {
        this(file, csn, Locale.getDefault(Locale.Category.FORMAT));
    }

    /**
     * Constructs a new formatter with the specified file, charset, and
     * locale.
     *
     * @param  file
     *         The file to use as the destination of this formatter.  If the
     *         file exists then it will be truncated to zero size; otherwise,
     *         a new file will be created.  The output will be written to the
     *         file and is buffered.
     *
     * @param  csn
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     *
     * @throws  FileNotFoundException
     *          If the given file object does not denote an existing, writable
     *          regular file and a new regular file of that name cannot be
     *          created, or if some other error occurs while opening or
     *          creating the file
     *
     * @throws  SecurityException
     *          If a security manager is present and {@link
     *          SecurityManager#checkWrite checkWrite(file.getPath())} denies
     *          write access to the file
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     */
    public Formatter(File file, String csn, Locale l)
        throws FileNotFoundException, UnsupportedEncodingException
    {
        this(toCharset(csn), l, file);
    }

    /**
     * Constructs a new formatter with the specified print stream.
     *
     * <p> The locale used is the {@linkplain Locale#getDefault() default
     * locale} for this instance of the Java virtual machine.
     *
     * <p> Characters are written to the given {@link java.io.PrintStream
     * PrintStream} object and are therefore encoded using that object's
     * charset.
     *
     * @param  ps
     *         The stream to use as the destination of this formatter.
     */
    public Formatter(PrintStream ps) {
        this(Locale.getDefault(Locale.Category.FORMAT),
             (Appendable)Objects.requireNonNull(ps));
    }

    /**
     * Constructs a new formatter with the specified output stream.
     *
     * <p> The charset used is the {@linkplain
     * java.nio.charset.Charset#defaultCharset() default charset} for this
     * instance of the Java virtual machine.
     *
     * <p> The locale used is the {@linkplain Locale#getDefault() default
     * locale} for this instance of the Java virtual machine.
     *
     * @param  os
     *         The output stream to use as the destination of this formatter.
     *         The output will be buffered.
     */
    public Formatter(OutputStream os) {
        this(Locale.getDefault(Locale.Category.FORMAT),
             new BufferedWriter(new OutputStreamWriter(os)));
    }

    /**
     * Constructs a new formatter with the specified output stream and
     * charset.
     *
     * <p> The locale used is the {@linkplain Locale#getDefault default
     * locale} for this instance of the Java virtual machine.
     *
     * @param  os
     *         The output stream to use as the destination of this formatter.
     *         The output will be buffered.
     *
     * @param  csn
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     */
    public Formatter(OutputStream os, String csn)
        throws UnsupportedEncodingException
    {
        this(os, csn, Locale.getDefault(Locale.Category.FORMAT));
    }

    /**
     * Constructs a new formatter with the specified output stream, charset,
     * and locale.
     *
     * @param  os
     *         The output stream to use as the destination of this formatter.
     *         The output will be buffered.
     *
     * @param  csn
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     */
    public Formatter(OutputStream os, String csn, Locale l)
        throws UnsupportedEncodingException
    {
        this(l, new BufferedWriter(new OutputStreamWriter(os, csn)));
    }

    private static char getZero(Locale l) {
        if ((l != null) && !l.equals(Locale.US)) {
            DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(l);
            return dfs.getZeroDigit();
        } else {
            return '0';
        }
    }

    /**
     * Returns the locale set by the construction of this formatter.
     *
     * <p> The {@link #format(java.util.Locale,String,Object...) format} method
     * for this object which has a locale argument does not change this value.
     *
     * @return  {@code null} if no localization is applied, otherwise a
     *          locale
     *
     * @throws  FormatterClosedException
     *          If this formatter has been closed by invoking its {@link
     *          #close()} method
     */
    public Locale locale() {
        ensureOpen();
        return l;
    }

    /**
     * Returns the destination for the output.
     *
     * @return  The destination for the output
     *
     * @throws  FormatterClosedException
     *          If this formatter has been closed by invoking its {@link
     *          #close()} method
     */
    public Appendable out() {
        ensureOpen();
        return a;
    }

    /**
     * Returns the result of invoking {@code toString()} on the destination
     * for the output.  For example, the following code formats text into a
     * {@link StringBuilder} then retrieves the resultant string:
     *
     * <blockquote><pre>
     *   Formatter f = new Formatter();
     *   f.format("Last reboot at %tc", lastRebootDate);
     *   String s = f.toString();
     *   // -&gt; s == "Last reboot at Sat Jan 01 00:00:00 PST 2000"
     * </pre></blockquote>
     *
     * <p> An invocation of this method behaves in exactly the same way as the
     * invocation
     *
     * <pre>
     *     out().toString() </pre>
     *
     * <p> Depending on the specification of {@code toString} for the {@link
     * Appendable}, the returned string may or may not contain the characters
     * written to the destination.  For instance, buffers typically return
     * their contents in {@code toString()}, but streams cannot since the
     * data is discarded.
     *
     * @return  The result of invoking {@code toString()} on the destination
     *          for the output
     *
     * @throws  FormatterClosedException
     *          If this formatter has been closed by invoking its {@link
     *          #close()} method
     */
    public String toString() {
        ensureOpen();
        return a.toString();
    }

    /**
     * Flushes this formatter.  If the destination implements the {@link
     * java.io.Flushable} interface, its {@code flush} method will be invoked.
     *
     * <p> Flushing a formatter writes any buffered output in the destination
     * to the underlying stream.
     *
     * @throws  FormatterClosedException
     *          If this formatter has been closed by invoking its {@link
     *          #close()} method
     */
    public void flush() {
        ensureOpen();
        if (a instanceof Flushable) {
            try {
                ((Flushable)a).flush();
            } catch (IOException ioe) {
                lastException = ioe;
            }
        }
    }

    /**
     * Closes this formatter.  If the destination implements the {@link
     * java.io.Closeable} interface, its {@code close} method will be invoked.
     *
     * <p> Closing a formatter allows it to release resources it may be holding
     * (such as open files).  If the formatter is already closed, then invoking
     * this method has no effect.
     *
     * <p> Attempting to invoke any methods except {@link #ioException()} in
     * this formatter after it has been closed will result in a {@link
     * FormatterClosedException}.
     */
    public void close() {
        if (a == null)
            return;
        try {
            if (a instanceof Closeable)
                ((Closeable)a).close();
        } catch (IOException ioe) {
            lastException = ioe;
        } finally {
            a = null;
        }
    }

    private void ensureOpen() {
        if (a == null)
            throw new FormatterClosedException();
    }

    /**
     * Returns the {@code IOException} last thrown by this formatter's {@link
     * Appendable}.
     *
     * <p> If the destination's {@code append()} method never throws
     * {@code IOException}, then this method will always return {@code null}.
     *
     * @return  The last exception thrown by the Appendable or {@code null} if
     *          no such exception exists.
     */
    public IOException ioException() {
        return lastException;
    }

    /**
     * Writes a formatted string to this object's destination using the
     * specified format string and arguments.  The locale used is the one
     * defined during the construction of this formatter.
     *
     * @param  format
     *         A format string as described in <a href="#syntax">Format string
     *         syntax</a>.
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         the <a href="http://java.sun.com/docs/books/vmspec/">Java
     *         Virtual Machine Specification</a>.
     *
     * @throws  IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a href="#detail">Details</a>
     *          section of the formatter class specification.
     *
     * @throws  FormatterClosedException
     *          If this formatter has been closed by invoking its {@link
     *          #close()} method
     *
     * @return  This formatter
     */
    public Formatter format(String format, Object ... args) {
        return format(l, format, args);
    }

    /**
     * Writes a formatted string to this object's destination using the
     * specified locale, format string, and arguments.
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.  This does not change this object's locale that was
     *         set during construction.
     *
     * @param  format
     *         A format string as described in <a href="#syntax">Format string
     *         syntax</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         the <a href="http://java.sun.com/docs/books/vmspec/">Java
     *         Virtual Machine Specification</a>
     *
     * @throws  IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a href="#detail">Details</a>
     *          section of the formatter class specification.
     *
     * @throws  FormatterClosedException
     *          If this formatter has been closed by invoking its {@link
     *          #close()} method
     *
     * @return  This formatter
     */
    public Formatter format(Locale l, String format, Object ... args) {
        ensureOpen();

        // index of last argument referenced
        int last = -1;
        // last ordinary index
        int lasto = -1;

        FormatString[] fsa = parse(format);
        for (int i = 0; i < fsa.length; i++) {
            FormatString fs = fsa[i];
            int index = fs.index();
            try {
                switch (index) {
                case -2:  // fixed string, "%n", or "%%"
                    fs.print(null, l);
                    break;
                case -1:  // relative index
                    if (last < 0 || (args != null && last > args.length - 1))
                        throw new MissingFormatArgumentException(fs.toString());
                    fs.print((args == null ? null : args[last]), l);
                    break;
                case 0:  // ordinary index
                    lasto++;
                    last = lasto;
                    if (args != null && lasto > args.length - 1)
                        throw new MissingFormatArgumentException(fs.toString());
                    fs.print((args == null ? null : args[lasto]), l);
                    break;
                default:  // explicit index
                    last = index - 1;
                    if (args != null && last > args.length - 1)
                        throw new MissingFormatArgumentException(fs.toString());
                    fs.print((args == null ? null : args[last]), l);
                    break;
                }
            } catch (IOException x) {
                lastException = x;
            }
        }
        return this;
    }

    // %[argument_index$][flags][width][.precision][t]conversion
    private static final String formatSpecifier
        = "%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])";

    private static Pattern fsPattern = Pattern.compile(formatSpecifier);

    /**
     * Finds format specifiers in the format string.
     */
    private FormatString[] parse(String s) {
        ArrayList<FormatString> al = new ArrayList<>();
        Matcher m = fsPattern.matcher(s);
        for (int i = 0, len = s.length(); i < len; ) {
            if (m.find(i)) {
                // Anything between the start of the string and the beginning
                // of the format specifier is either fixed text or contains
                // an invalid format string.
                if (m.start() != i) {
                    // Make sure we didn't miss any invalid format specifiers
                    checkText(s, i, m.start());
                    // Assume previous characters were fixed text
                    al.add(new FixedString(s.substring(i, m.start())));
                }

                al.add(new FormatSpecifier(m));
                i = m.end();
            } else {
                // No more valid format specifiers.  Check for possible invalid
                // format specifiers.
                checkText(s, i, len);
                // The rest of the string is fixed text
                al.add(new FixedString(s.substring(i)));
                break;
            }
        }
        return al.toArray(new FormatString[al.size()]);
    }

    private static void checkText(String s, int start, int end) {
        for (int i = start; i < end; i++) {
            // Any '%' found in the region starts an invalid format specifier.
            if (s.charAt(i) == '%') {
                char c = (i == end - 1) ? '%' : s.charAt(i + 1);
                throw new UnknownFormatConversionException(String.valueOf(c));
            }
        }
    }

    private interface FormatString {
        int index();
        void print(Object arg, Locale l) throws IOException;
        String toString();
    }

    private class FixedString implements FormatString {
        private String s;
        FixedString(String s) { this.s = s; }
        public int index() { return -2; }
        public void print(Object arg, Locale l)
            throws IOException { a.append(s); }
        public String toString() { return s; }
    }

    public enum BigDecimalLayoutForm { SCIENTIFIC, DECIMAL_FLOAT };

    private class FormatSpecifier implements FormatString {
        private int index = -1;
        private Flags f = Flags.NONE;
        private int width;
        private int precision;
        private boolean dt = false;
        private char c;

        private int index(String s) {
            if (s != null) {
                try {
                    index = Integer.parseInt(s.substring(0, s.length() - 1));
                } catch (NumberFormatException x) {
                    assert(false);
                }
            } else {
                index = 0;
            }
            return index;
        }

        public int index() {
            return index;
        }

        private Flags flags(String s) {
            f = Flags.parse(s);
            if (f.contains(Flags.PREVIOUS))
                index = -1;
            return f;
        }

        Flags flags() {
            return f;
        }

        private int width(String s) {
            width = -1;
            if (s != null) {
                try {
                    width  = Integer.parseInt(s);
                    if (width < 0)
                        throw new IllegalFormatWidthException(width);
                } catch (NumberFormatException x) {
                    assert(false);
                }
            }
            return width;
        }

        int width() {
            return width;
        }

        private int precision(String s) {
            precision = -1;
            if (s != null) {
                try {
                    // remove the '.'
                    precision = Integer.parseInt(s.substring(1));
                    if (precision < 0)
                        throw new IllegalFormatPrecisionException(precision);
                } catch (NumberFormatException x) {
                    assert(false);
                }
            }
            return precision;
        }

        int precision() {
            return precision;
        }

        private char conversion(String s) {
            c = s.charAt(0);
            if (!dt) {
                if (!Conversion.isValid(c))
                    throw new UnknownFormatConversionException(String.valueOf(c));
                if (Character.isUpperCase(c))
                    f.add(Flags.UPPERCASE);
                c = Character.toLowerCase(c);
                if (Conversion.isText(c))
                    index = -2;
            }
            return c;
        }

        private char conversion() {
            return c;
        }

        FormatSpecifier(Matcher m) {
            int idx = 1;

            index(m.group(idx++));
            flags(m.group(idx++));
            width(m.group(idx++));
            precision(m.group(idx++));

            String tT = m.group(idx++);
            if (tT != null) {
                dt = true;
                if (tT.equals("T"))
                    f.add(Flags.UPPERCASE);
            }

            conversion(m.group(idx));

            if (dt)
                checkDateTime();
            else if (Conversion.isGeneral(c))
                checkGeneral();
            else if (Conversion.isCharacter(c))
                checkCharacter();
            else if (Conversion.isInteger(c))
                checkInteger();
            else if (Conversion.isFloat(c))
                checkFloat();
            else if (Conversion.isText(c))
                checkText();
            else
                throw new UnknownFormatConversionException(String.valueOf(c));
        }

        public void print(Object arg, Locale l) throws IOException {
            if (dt) {
                printDateTime(arg, l);
                return;
            }
            switch(c) {
            case Conversion.DECIMAL_INTEGER:
            case Conversion.OCTAL_INTEGER:
            case Conversion.HEXADECIMAL_INTEGER:
                printInteger(arg, l);
                break;
            case Conversion.SCIENTIFIC:
            case Conversion.GENERAL:
            case Conversion.DECIMAL_FLOAT:
            case Conversion.HEXADECIMAL_FLOAT:
                printFloat(arg, l);
                break;
            case Conversion.CHARACTER:
            case Conversion.CHARACTER_UPPER:
                printCharacter(arg);
                break;
            case Conversion.BOOLEAN:
                printBoolean(arg);
                break;
            case Conversion.STRING:
                printString(arg, l);
                break;
            case Conversion.HASHCODE:
                printHashCode(arg);
                break;
            case Conversion.LINE_SEPARATOR:
                a.append(System.lineSeparator());
                break;
            case Conversion.PERCENT_SIGN:
                a.append('%');
                break;
            default:
                assert false;
            }
        }

        private void printInteger(Object arg, Locale l) throws IOException {
            if (arg == null)
                print("null");
            else if (arg instanceof Byte)
                print(((Byte)arg).byteValue(), l);
            else if (arg instanceof Short)
                print(((Short)arg).shortValue(), l);
            else if (arg instanceof Integer)
                print(((Integer)arg).intValue(), l);
            else if (arg instanceof Long)
                print(((Long)arg).longValue(), l);
            else if (arg instanceof BigInteger)
                print(((BigInteger)arg), l);
            else
                failConversion(c, arg);
        }

        private void printFloat(Object arg, Locale l) throws IOException {
            if (arg == null)
                print("null");
            else if (arg instanceof Float)
                print(((Float)arg).floatValue(), l);
            else if (arg instanceof Double)
                print(((Double)arg).doubleValue(), l);
            else if (arg instanceof BigDecimal)
                print(((BigDecimal)arg), l);
            else
                failConversion(c, arg);
        }

        private void printDateTime(Object arg, Locale l) throws IOException {
            if (arg == null) {
                print("null");
                return;
            }
            Calendar cal = null;

            // Instead of Calendar.setLenient(true), perhaps we should
            // wrap the IllegalArgumentException that might be thrown?
            if (arg instanceof Long) {
                // Note that the following method uses an instance of the
                // default time zone (TimeZone.getDefaultRef().
                cal = Calendar.getInstance(l == null ? Locale.US : l);
                cal.setTimeInMillis((Long)arg);
            } else if (arg instanceof Date) {
                // Note that the following method uses an instance of the
                // default time zone (TimeZone.getDefaultRef().
                cal = Calendar.getInstance(l == null ? Locale.US : l);
                cal.setTime((Date)arg);
            } else if (arg instanceof Calendar) {
                cal = (Calendar) ((Calendar)arg).clone();
                cal.setLenient(true);
            } else {
                failConversion(c, arg);
            }
            // Use the provided locale so that invocations of
            // localizedMagnitude() use optimizations for null.
            print(cal, c, l);
        }

        private void printCharacter(Object arg) throws IOException {
            if (arg == null) {
                print("null");
                return;
            }
            String s = null;
            if (arg instanceof Character) {
                s = ((Character)arg).toString();
            } else if (arg instanceof Byte) {
                byte i = ((Byte)arg).byteValue();
                if (Character.isValidCodePoint(i))
                    s = new String(Character.toChars(i));
                else
                    throw new IllegalFormatCodePointException(i);
            } else if (arg instanceof Short) {
                short i = ((Short)arg).shortValue();
                if (Character.isValidCodePoint(i))
                    s = new String(Character.toChars(i));
                else
                    throw new IllegalFormatCodePointException(i);
            } else if (arg instanceof Integer) {
                int i = ((Integer)arg).intValue();
                if (Character.isValidCodePoint(i))
                    s = new String(Character.toChars(i));
                else
                    throw new IllegalFormatCodePointException(i);
            } else {
                failConversion(c, arg);
            }
            print(s);
        }

        private void printString(Object arg, Locale l) throws IOException {
            if (arg instanceof Formattable) {
                Formatter fmt = Formatter.this;
                if (fmt.locale() != l)
                    fmt = new Formatter(fmt.out(), l);
                ((Formattable)arg).formatTo(fmt, f.valueOf(), width, precision);
            } else {
                if (f.contains(Flags.ALTERNATE))
                    failMismatch(Flags.ALTERNATE, 's');
                if (arg == null)
                    print("null");
                else
                    print(arg.toString());
            }
        }

        private void printBoolean(Object arg) throws IOException {
            String s;
            if (arg != null)
                s = ((arg instanceof Boolean)
                     ? ((Boolean)arg).toString()
                     : Boolean.toString(true));
            else
                s = Boolean.toString(false);
            print(s);
        }

        private void printHashCode(Object arg) throws IOException {
            String s = (arg == null
                        ? "null"
                        : Integer.toHexString(arg.hashCode()));
            print(s);
        }

        private void print(String s) throws IOException {
            if (precision != -1 && precision < s.length())
                s = s.substring(0, precision);
            if (f.contains(Flags.UPPERCASE))
                s = s.toUpperCase();
            a.append(justify(s));
        }

        private String justify(String s) {
            if (width == -1)
                return s;
            StringBuilder sb = new StringBuilder();
            boolean pad = f.contains(Flags.LEFT_JUSTIFY);
            int sp = width - s.length();
            if (!pad)
                for (int i = 0; i < sp; i++) sb.append(' ');
            sb.append(s);
            if (pad)
                for (int i = 0; i < sp; i++) sb.append(' ');
            return sb.toString();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder('%');
            // Flags.UPPERCASE is set internally for legal conversions.
            Flags dupf = f.dup().remove(Flags.UPPERCASE);
            sb.append(dupf.toString());
            if (index > 0)
                sb.append(index).append('$');
            if (width != -1)
                sb.append(width);
            if (precision != -1)
                sb.append('.').append(precision);
            if (dt)
                sb.append(f.contains(Flags.UPPERCASE) ? 'T' : 't');
            sb.append(f.contains(Flags.UPPERCASE)
                      ? Character.toUpperCase(c) : c);
            return sb.toString();
        }

        private void checkGeneral() {
            if ((c == Conversion.BOOLEAN || c == Conversion.HASHCODE)
                && f.contains(Flags.ALTERNATE))
                failMismatch(Flags.ALTERNATE, c);
            // '-' requires a width
            if (width == -1 && f.contains(Flags.LEFT_JUSTIFY))
                throw new MissingFormatWidthException(toString());
            checkBadFlags(Flags.PLUS, Flags.LEADING_SPACE, Flags.ZERO_PAD,
                          Flags.GROUP, Flags.PARENTHESES);
        }

        private void checkDateTime() {
            if (precision != -1)
                throw new IllegalFormatPrecisionException(precision);
            if (!DateTime.isValid(c))
                throw new UnknownFormatConversionException("t" + c);
            checkBadFlags(Flags.ALTERNATE, Flags.PLUS, Flags.LEADING_SPACE,
                          Flags.ZERO_PAD, Flags.GROUP, Flags.PARENTHESES);
            // '-' requires a width
            if (width == -1 && f.contains(Flags.LEFT_JUSTIFY))
                throw new MissingFormatWidthException(toString());
        }

        private void checkCharacter() {
            if (precision != -1)
                throw new IllegalFormatPrecisionException(precision);
            checkBadFlags(Flags.ALTERNATE, Flags.PLUS, Flags.LEADING_SPACE,
                          Flags.ZERO_PAD, Flags.GROUP, Flags.PARENTHESES);
            // '-' requires a width
            if (width == -1 && f.contains(Flags.LEFT_JUSTIFY))
                throw new MissingFormatWidthException(toString());
        }

        private void checkInteger() {
            checkNumeric();
            if (precision != -1)
                throw new IllegalFormatPrecisionException(precision);

            if (c == Conversion.DECIMAL_INTEGER)
                checkBadFlags(Flags.ALTERNATE);
            else if (c == Conversion.OCTAL_INTEGER)
                checkBadFlags(Flags.GROUP);
            else
                checkBadFlags(Flags.GROUP);
        }

        private void checkBadFlags(Flags ... badFlags) {
            for (int i = 0; i < badFlags.length; i++)
                if (f.contains(badFlags[i]))
                    failMismatch(badFlags[i], c);
        }

        private void checkFloat() {
            checkNumeric();
            if (c == Conversion.DECIMAL_FLOAT) {
            } else if (c == Conversion.HEXADECIMAL_FLOAT) {
                checkBadFlags(Flags.PARENTHESES, Flags.GROUP);
            } else if (c == Conversion.SCIENTIFIC) {
                checkBadFlags(Flags.GROUP);
            } else if (c == Conversion.GENERAL) {
                checkBadFlags(Flags.ALTERNATE);
            }
        }

        private void checkNumeric() {
            if (width != -1 && width < 0)
                throw new IllegalFormatWidthException(width);

            if (precision != -1 && precision < 0)
                throw new IllegalFormatPrecisionException(precision);

            // '-' and '0' require a width
            if (width == -1
                && (f.contains(Flags.LEFT_JUSTIFY) || f.contains(Flags.ZERO_PAD)))
                throw new MissingFormatWidthException(toString());

            // bad combination
            if ((f.contains(Flags.PLUS) && f.contains(Flags.LEADING_SPACE))
                || (f.contains(Flags.LEFT_JUSTIFY) && f.contains(Flags.ZERO_PAD)))
                throw new IllegalFormatFlagsException(f.toString());
        }

        private void checkText() {
            if (precision != -1)
                throw new IllegalFormatPrecisionException(precision);
            switch (c) {
            case Conversion.PERCENT_SIGN:
                if (f.valueOf() != Flags.LEFT_JUSTIFY.valueOf()
                    && f.valueOf() != Flags.NONE.valueOf())
                    throw new IllegalFormatFlagsException(f.toString());
                // '-' requires a width
                if (width == -1 && f.contains(Flags.LEFT_JUSTIFY))
                    throw new MissingFormatWidthException(toString());
                break;
            case Conversion.LINE_SEPARATOR:
                if (width != -1)
                    throw new IllegalFormatWidthException(width);
                if (f.valueOf() != Flags.NONE.valueOf())
                    throw new IllegalFormatFlagsException(f.toString());
                break;
            default:
                assert false;
            }
        }

        private void print(byte value, Locale l) throws IOException {
            long v = value;
            if (value < 0
                && (c == Conversion.OCTAL_INTEGER
                    || c == Conversion.HEXADECIMAL_INTEGER)) {
                v += (1L << 8);
                assert v >= 0 : v;
            }
            print(v, l);
        }

        private void print(short value, Locale l) throws IOException {
            long v = value;
            if (value < 0
                && (c == Conversion.OCTAL_INTEGER
                    || c == Conversion.HEXADECIMAL_INTEGER)) {
                v += (1L << 16);
                assert v >= 0 : v;
            }
            print(v, l);
        }

        private void print(int value, Locale l) throws IOException {
            long v = value;
            if (value < 0
                && (c == Conversion.OCTAL_INTEGER
                    || c == Conversion.HEXADECIMAL_INTEGER)) {
                v += (1L << 32);
                assert v >= 0 : v;
            }
            print(v, l);
        }

        private void print(long value, Locale l) throws IOException {

            StringBuilder sb = new StringBuilder();

            if (c == Conversion.DECIMAL_INTEGER) {
                boolean neg = value < 0;
                char[] va;
                if (value < 0)
                    va = Long.toString(value, 10).substring(1).toCharArray();
                else
                    va = Long.toString(value, 10).toCharArray();

                // leading sign indicator
                leadingSign(sb, neg);

                // the value
                localizedMagnitude(sb, va, f, adjustWidth(width, f, neg), l);

                // trailing sign indicator
                trailingSign(sb, neg);
            } else if (c == Conversion.OCTAL_INTEGER) {
                checkBadFlags(Flags.PARENTHESES, Flags.LEADING_SPACE,
                              Flags.PLUS);
                String s = Long.toOctalString(value);
                int len = (f.contains(Flags.ALTERNATE)
                           ? s.length() + 1
                           : s.length());

                // apply ALTERNATE (radix indicator for octal) before ZERO_PAD
                if (f.contains(Flags.ALTERNATE))
                    sb.append('0');
                if (f.contains(Flags.ZERO_PAD))
                    for (int i = 0; i < width - len; i++) sb.append('0');
                sb.append(s);
            } else if (c == Conversion.HEXADECIMAL_INTEGER) {
                checkBadFlags(Flags.PARENTHESES, Flags.LEADING_SPACE,
                              Flags.PLUS);
                String s = Long.toHexString(value);
                int len = (f.contains(Flags.ALTERNATE)
                           ? s.length() + 2
                           : s.length());

                // apply ALTERNATE (radix indicator for hex) before ZERO_PAD
                if (f.contains(Flags.ALTERNATE))
                    sb.append(f.contains(Flags.UPPERCASE) ? "0X" : "0x");
                if (f.contains(Flags.ZERO_PAD))
                    for (int i = 0; i < width - len; i++) sb.append('0');
                if (f.contains(Flags.UPPERCASE))
                    s = s.toUpperCase();
                sb.append(s);
            }

            // justify based on width
            a.append(justify(sb.toString()));
        }

        // neg := val < 0
        private StringBuilder leadingSign(StringBuilder sb, boolean neg) {
            if (!neg) {
                if (f.contains(Flags.PLUS)) {
                    sb.append('+');
                } else if (f.contains(Flags.LEADING_SPACE)) {
                    sb.append(' ');
                }
            } else {
                if (f.contains(Flags.PARENTHESES))
                    sb.append('(');
                else
                    sb.append('-');
            }
            return sb;
        }

        // neg := val < 0
        private StringBuilder trailingSign(StringBuilder sb, boolean neg) {
            if (neg && f.contains(Flags.PARENTHESES))
                sb.append(')');
            return sb;
        }

        private void print(BigInteger value, Locale l) throws IOException {
            StringBuilder sb = new StringBuilder();
            boolean neg = value.signum() == -1;
            BigInteger v = value.abs();

            // leading sign indicator
            leadingSign(sb, neg);

            // the value
            if (c == Conversion.DECIMAL_INTEGER) {
                char[] va = v.toString().toCharArray();
                localizedMagnitude(sb, va, f, adjustWidth(width, f, neg), l);
            } else if (c == Conversion.OCTAL_INTEGER) {
                String s = v.toString(8);

                int len = s.length() + sb.length();
                if (neg && f.contains(Flags.PARENTHESES))
                    len++;

                // apply ALTERNATE (radix indicator for octal) before ZERO_PAD
                if (f.contains(Flags.ALTERNATE)) {
                    len++;
                    sb.append('0');
                }
                if (f.contains(Flags.ZERO_PAD)) {
                    for (int i = 0; i < width - len; i++)
                        sb.append('0');
                }
                sb.append(s);
            } else if (c == Conversion.HEXADECIMAL_INTEGER) {
                String s = v.toString(16);

                int len = s.length() + sb.length();
                if (neg && f.contains(Flags.PARENTHESES))
                    len++;

                // apply ALTERNATE (radix indicator for hex) before ZERO_PAD
                if (f.contains(Flags.ALTERNATE)) {
                    len += 2;
                    sb.append(f.contains(Flags.UPPERCASE) ? "0X" : "0x");
                }
                if (f.contains(Flags.ZERO_PAD))
                    for (int i = 0; i < width - len; i++)
                        sb.append('0');
                if (f.contains(Flags.UPPERCASE))
                    s = s.toUpperCase();
                sb.append(s);
            }

            // trailing sign indicator
            trailingSign(sb, (value.signum() == -1));

            // justify based on width
            a.append(justify(sb.toString()));
        }

        private void print(float value, Locale l) throws IOException {
            print((double) value, l);
        }

        private void print(double value, Locale l) throws IOException {
            StringBuilder sb = new StringBuilder();
            boolean neg = Double.compare(value, 0.0) == -1;

            if (!Double.isNaN(value)) {
                double v = Math.abs(value);

                // leading sign indicator
                leadingSign(sb, neg);

                // the value
                if (!Double.isInfinite(v))
                    print(sb, v, l, f, c, precision, neg);
                else
                    sb.append(f.contains(Flags.UPPERCASE)
                              ? "INFINITY" : "Infinity");

                // trailing sign indicator
                trailingSign(sb, neg);
            } else {
                sb.append(f.contains(Flags.UPPERCASE) ? "NAN" : "NaN");
            }

            // justify based on width
            a.append(justify(sb.toString()));
        }

        // !Double.isInfinite(value) && !Double.isNaN(value)
        private void print(StringBuilder sb, double value, Locale l,
                           Flags f, char c, int precision, boolean neg)
            throws IOException
        {
            if (c == Conversion.SCIENTIFIC) {
                // Create a new FormattedFloatingDecimal with the desired
                // precision.
                int prec = (precision == -1 ? 6 : precision);

                FormattedFloatingDecimal fd
                    = new FormattedFloatingDecimal(value, prec,
                        FormattedFloatingDecimal.Form.SCIENTIFIC);

                char[] v = new char[MAX_FD_CHARS];
                int len = fd.getChars(v);

                char[] mant = addZeros(mantissa(v, len), prec);

                // If the precision is zero and the '#' flag is set, add the
                // requested decimal point.
                if (f.contains(Flags.ALTERNATE) && (prec == 0))
                    mant = addDot(mant);

                char[] exp = (value == 0.0)
                    ? new char[] {'+','0','0'} : exponent(v, len);

                int newW = width;
                if (width != -1)
                    newW = adjustWidth(width - exp.length - 1, f, neg);
                localizedMagnitude(sb, mant, f, newW, l);

                sb.append(f.contains(Flags.UPPERCASE) ? 'E' : 'e');

                Flags flags = f.dup().remove(Flags.GROUP);
                char sign = exp[0];
                assert(sign == '+' || sign == '-');
                sb.append(sign);

                char[] tmp = new char[exp.length - 1];
                System.arraycopy(exp, 1, tmp, 0, exp.length - 1);
                sb.append(localizedMagnitude(null, tmp, flags, -1, l));
            } else if (c == Conversion.DECIMAL_FLOAT) {
                // Create a new FormattedFloatingDecimal with the desired
                // precision.
                int prec = (precision == -1 ? 6 : precision);

                FormattedFloatingDecimal fd
                    = new FormattedFloatingDecimal(value, prec,
                        FormattedFloatingDecimal.Form.DECIMAL_FLOAT);

                // MAX_FD_CHARS + 1 (round?)
                char[] v = new char[MAX_FD_CHARS + 1
                                   + Math.abs(fd.getExponent())];
                int len = fd.getChars(v);

                char[] mant = addZeros(mantissa(v, len), prec);

                // If the precision is zero and the '#' flag is set, add the
                // requested decimal point.
                if (f.contains(Flags.ALTERNATE) && (prec == 0))
                    mant = addDot(mant);

                int newW = width;
                if (width != -1)
                    newW = adjustWidth(width, f, neg);
                localizedMagnitude(sb, mant, f, newW, l);
            } else if (c == Conversion.GENERAL) {
                int prec = precision;
                if (precision == -1)
                    prec = 6;
                else if (precision == 0)
                    prec = 1;

                FormattedFloatingDecimal fd
                    = new FormattedFloatingDecimal(value, prec,
                        FormattedFloatingDecimal.Form.GENERAL);

                // MAX_FD_CHARS + 1 (round?)
                char[] v = new char[MAX_FD_CHARS + 1
                                   + Math.abs(fd.getExponent())];
                int len = fd.getChars(v);

                char[] exp = exponent(v, len);
                if (exp != null) {
                    prec -= 1;
                } else {
                    prec = prec - (value == 0 ? 0 : fd.getExponentRounded()) - 1;
                }

                char[] mant = addZeros(mantissa(v, len), prec);
                // If the precision is zero and the '#' flag is set, add the
                // requested decimal point.
                if (f.contains(Flags.ALTERNATE) && (prec == 0))
                    mant = addDot(mant);

                int newW = width;
                if (width != -1) {
                    if (exp != null)
                        newW = adjustWidth(width - exp.length - 1, f, neg);
                    else
                        newW = adjustWidth(width, f, neg);
                }
                localizedMagnitude(sb, mant, f, newW, l);

                if (exp != null) {
                    sb.append(f.contains(Flags.UPPERCASE) ? 'E' : 'e');

                    Flags flags = f.dup().remove(Flags.GROUP);
                    char sign = exp[0];
                    assert(sign == '+' || sign == '-');
                    sb.append(sign);

                    char[] tmp = new char[exp.length - 1];
                    System.arraycopy(exp, 1, tmp, 0, exp.length - 1);
                    sb.append(localizedMagnitude(null, tmp, flags, -1, l));
                }
            } else if (c == Conversion.HEXADECIMAL_FLOAT) {
                int prec = precision;
                if (precision == -1)
                    // assume that we want all of the digits
                    prec = 0;
                else if (precision == 0)
                    prec = 1;

                String s = hexDouble(value, prec);

                char[] va;
                boolean upper = f.contains(Flags.UPPERCASE);
                sb.append(upper ? "0X" : "0x");

                if (f.contains(Flags.ZERO_PAD))
                    for (int i = 0; i < width - s.length() - 2; i++)
                        sb.append('0');

                int idx = s.indexOf('p');
                va = s.substring(0, idx).toCharArray();
                if (upper) {
                    String tmp = new String(va);
                    // don't localize hex
                    tmp = tmp.toUpperCase(Locale.US);
                    va = tmp.toCharArray();
                }
                sb.append(prec != 0 ? addZeros(va, prec) : va);
                sb.append(upper ? 'P' : 'p');
                sb.append(s.substring(idx+1));
            }
        }

        private char[] mantissa(char[] v, int len) {
            int i;
            for (i = 0; i < len; i++) {
                if (v[i] == 'e')
                    break;
            }
            char[] tmp = new char[i];
            System.arraycopy(v, 0, tmp, 0, i);
            return tmp;
        }

        private char[] exponent(char[] v, int len) {
            int i;
            for (i = len - 1; i >= 0; i--) {
                if (v[i] == 'e')
                    break;
            }
            if (i == -1)
                return null;
            char[] tmp = new char[len - i - 1];
            System.arraycopy(v, i + 1, tmp, 0, len - i - 1);
            return tmp;
        }

        // Add zeros to the requested precision.
        private char[] addZeros(char[] v, int prec) {
            // Look for the dot.  If we don't find one, the we'll need to add
            // it before we add the zeros.
            int i;
            for (i = 0; i < v.length; i++) {
                if (v[i] == '.')
                    break;
            }
            boolean needDot = false;
            if (i == v.length) {
                needDot = true;
            }

            // Determine existing precision.
            int outPrec = v.length - i - (needDot ? 0 : 1);
            assert (outPrec <= prec);
            if (outPrec == prec)
                return v;

            // Create new array with existing contents.
            char[] tmp
                = new char[v.length + prec - outPrec + (needDot ? 1 : 0)];
            System.arraycopy(v, 0, tmp, 0, v.length);

            // Add dot if previously determined to be necessary.
            int start = v.length;
            if (needDot) {
                tmp[v.length] = '.';
                start++;
            }

            // Add zeros.
            for (int j = start; j < tmp.length; j++)
                tmp[j] = '0';

            return tmp;
        }

        // Method assumes that d > 0.
        private String hexDouble(double d, int prec) {
            // Let Double.toHexString handle simple cases
            if(!FpUtils.isFinite(d) || d == 0.0 || prec == 0 || prec >= 13)
                // remove "0x"
                return Double.toHexString(d).substring(2);
            else {
                assert(prec >= 1 && prec <= 12);

                int exponent  = FpUtils.getExponent(d);
                boolean subnormal
                    = (exponent == DoubleConsts.MIN_EXPONENT - 1);

                // If this is subnormal input so normalize (could be faster to
                // do as integer operation).
                if (subnormal) {
                    scaleUp = FpUtils.scalb(1.0, 54);
                    d *= scaleUp;
                    // Calculate the exponent.  This is not just exponent + 54
                    // since the former is not the normalized exponent.
                    exponent = FpUtils.getExponent(d);
                    assert exponent >= DoubleConsts.MIN_EXPONENT &&
                        exponent <= DoubleConsts.MAX_EXPONENT: exponent;
                }

                int precision = 1 + prec*4;
                int shiftDistance
                    =  DoubleConsts.SIGNIFICAND_WIDTH - precision;
                assert(shiftDistance >= 1 && shiftDistance < DoubleConsts.SIGNIFICAND_WIDTH);

                long doppel = Double.doubleToLongBits(d);
                // Deterime the number of bits to keep.
                long newSignif
                    = (doppel & (DoubleConsts.EXP_BIT_MASK
                                 | DoubleConsts.SIGNIF_BIT_MASK))
                                     >> shiftDistance;
                // Bits to round away.
                long roundingBits = doppel & ~(~0L << shiftDistance);

                // To decide how to round, look at the low-order bit of the
                // working significand, the highest order discarded bit (the
                // round bit) and whether any of the lower order discarded bits
                // are nonzero (the sticky bit).

                boolean leastZero = (newSignif & 0x1L) == 0L;
                boolean round
                    = ((1L << (shiftDistance - 1) ) & roundingBits) != 0L;
                boolean sticky  = shiftDistance > 1 &&
                    (~(1L<< (shiftDistance - 1)) & roundingBits) != 0;
                if((leastZero && round && sticky) || (!leastZero && round)) {
                    newSignif++;
                }

                long signBit = doppel & DoubleConsts.SIGN_BIT_MASK;
                newSignif = signBit | (newSignif << shiftDistance);
                double result = Double.longBitsToDouble(newSignif);

                if (Double.isInfinite(result) ) {
                    // Infinite result generated by rounding
                    return "1.0p1024";
                } else {
                    String res = Double.toHexString(result).substring(2);
                    if (!subnormal)
                        return res;
                    else {
                        // Create a normalized subnormal string.
                        int idx = res.indexOf('p');
                        if (idx == -1) {
                            // No 'p' character in hex string.
                            assert false;
                            return null;
                        } else {
                            // Get exponent and append at the end.
                            String exp = res.substring(idx + 1);
                            int iexp = Integer.parseInt(exp) -54;
                            return res.substring(0, idx) + "p"
                                + Integer.toString(iexp);
                        }
                    }
                }
            }
        }

        private void print(BigDecimal value, Locale l) throws IOException {
            if (c == Conversion.HEXADECIMAL_FLOAT)
                failConversion(c, value);
            StringBuilder sb = new StringBuilder();
            boolean neg = value.signum() == -1;
            BigDecimal v = value.abs();
            // leading sign indicator
            leadingSign(sb, neg);

            // the value
            print(sb, v, l, f, c, precision, neg);

            // trailing sign indicator
            trailingSign(sb, neg);

            // justify based on width
            a.append(justify(sb.toString()));
        }

        // value > 0
        private void print(StringBuilder sb, BigDecimal value, Locale l,
                           Flags f, char c, int precision, boolean neg)
            throws IOException
        {
            if (c == Conversion.SCIENTIFIC) {
                // Create a new BigDecimal with the desired precision.
                int prec = (precision == -1 ? 6 : precision);
                int scale = value.scale();
                int origPrec = value.precision();
                int nzeros = 0;
                int compPrec;

                if (prec > origPrec - 1) {
                    compPrec = origPrec;
                    nzeros = prec - (origPrec - 1);
                } else {
                    compPrec = prec + 1;
                }

                MathContext mc = new MathContext(compPrec);
                BigDecimal v
                    = new BigDecimal(value.unscaledValue(), scale, mc);

                BigDecimalLayout bdl
                    = new BigDecimalLayout(v.unscaledValue(), v.scale(),
                                           BigDecimalLayoutForm.SCIENTIFIC);

                char[] mant = bdl.mantissa();

                // Add a decimal point if necessary.  The mantissa may not
                // contain a decimal point if the scale is zero (the internal
                // representation has no fractional part) or the original
                // precision is one. Append a decimal point if '#' is set or if
                // we require zero padding to get to the requested precision.
                if ((origPrec == 1 || !bdl.hasDot())
                    && (nzeros > 0 || (f.contains(Flags.ALTERNATE))))
                    mant = addDot(mant);

                // Add trailing zeros in the case precision is greater than
                // the number of available digits after the decimal separator.
                mant = trailingZeros(mant, nzeros);

                char[] exp = bdl.exponent();
                int newW = width;
                if (width != -1)
                    newW = adjustWidth(width - exp.length - 1, f, neg);
                localizedMagnitude(sb, mant, f, newW, l);

                sb.append(f.contains(Flags.UPPERCASE) ? 'E' : 'e');

                Flags flags = f.dup().remove(Flags.GROUP);
                char sign = exp[0];
                assert(sign == '+' || sign == '-');
                sb.append(exp[0]);

                char[] tmp = new char[exp.length - 1];
                System.arraycopy(exp, 1, tmp, 0, exp.length - 1);
                sb.append(localizedMagnitude(null, tmp, flags, -1, l));
            } else if (c == Conversion.DECIMAL_FLOAT) {
                // Create a new BigDecimal with the desired precision.
                int prec = (precision == -1 ? 6 : precision);
                int scale = value.scale();

                if (scale > prec) {
                    // more "scale" digits than the requested "precision"
                    int compPrec = value.precision();
                    if (compPrec <= scale) {
                        // case of 0.xxxxxx
                        value = value.setScale(prec, RoundingMode.HALF_UP);
                    } else {
                        compPrec -= (scale - prec);
                        value = new BigDecimal(value.unscaledValue(),
                                               scale,
                                               new MathContext(compPrec));
                    }
                }
                BigDecimalLayout bdl = new BigDecimalLayout(
                                           value.unscaledValue(),
                                           value.scale(),
                                           BigDecimalLayoutForm.DECIMAL_FLOAT);

                char mant[] = bdl.mantissa();
                int nzeros = (bdl.scale() < prec ? prec - bdl.scale() : 0);

                // Add a decimal point if necessary.  The mantissa may not
                // contain a decimal point if the scale is zero (the internal
                // representation has no fractional part).  Append a decimal
                // point if '#' is set or we require zero padding to get to the
                // requested precision.
                if (bdl.scale() == 0 && (f.contains(Flags.ALTERNATE) || nzeros > 0))
                    mant = addDot(bdl.mantissa());

                // Add trailing zeros if the precision is greater than the
                // number of available digits after the decimal separator.
                mant = trailingZeros(mant, nzeros);

                localizedMagnitude(sb, mant, f, adjustWidth(width, f, neg), l);
            } else if (c == Conversion.GENERAL) {
                int prec = precision;
                if (precision == -1)
                    prec = 6;
                else if (precision == 0)
                    prec = 1;

                BigDecimal tenToTheNegFour = BigDecimal.valueOf(1, 4);
                BigDecimal tenToThePrec = BigDecimal.valueOf(1, -prec);
                if ((value.equals(BigDecimal.ZERO))
                    || ((value.compareTo(tenToTheNegFour) != -1)
                        && (value.compareTo(tenToThePrec) == -1))) {

                    int e = - value.scale()
                        + (value.unscaledValue().toString().length() - 1);

                    // xxx.yyy
                    //   g precision (# sig digits) = #x + #y
                    //   f precision = #y
                    //   exponent = #x - 1
                    // => f precision = g precision - exponent - 1
                    // 0.000zzz
                    //   g precision (# sig digits) = #z
                    //   f precision = #0 (after '.') + #z
                    //   exponent = - #0 (after '.') - 1
                    // => f precision = g precision - exponent - 1
                    prec = prec - e - 1;

                    print(sb, value, l, f, Conversion.DECIMAL_FLOAT, prec,
                          neg);
                } else {
                    print(sb, value, l, f, Conversion.SCIENTIFIC, prec - 1, neg);
                }
            } else if (c == Conversion.HEXADECIMAL_FLOAT) {
                // This conversion isn't supported.  The error should be
                // reported earlier.
                assert false;
            }
        }

        private class BigDecimalLayout {
            private StringBuilder mant;
            private StringBuilder exp;
            private boolean dot = false;
            private int scale;

            public BigDecimalLayout(BigInteger intVal, int scale, BigDecimalLayoutForm form) {
                layout(intVal, scale, form);
            }

            public boolean hasDot() {
                return dot;
            }

            public int scale() {
                return scale;
            }

            // char[] with canonical string representation
            public char[] layoutChars() {
                StringBuilder sb = new StringBuilder(mant);
                if (exp != null) {
                    sb.append('E');
                    sb.append(exp);
                }
                return toCharArray(sb);
            }

            public char[] mantissa() {
                return toCharArray(mant);
            }

            // The exponent will be formatted as a sign ('+' or '-') followed
            // by the exponent zero-padded to include at least two digits.
            public char[] exponent() {
                return toCharArray(exp);
            }

            private char[] toCharArray(StringBuilder sb) {
                if (sb == null)
                    return null;
                char[] result = new char[sb.length()];
                sb.getChars(0, result.length, result, 0);
                return result;
            }

            private void layout(BigInteger intVal, int scale, BigDecimalLayoutForm form) {
                char coeff[] = intVal.toString().toCharArray();
                this.scale = scale;

                // Construct a buffer, with sufficient capacity for all cases.
                // If E-notation is needed, length will be: +1 if negative, +1
                // if '.' needed, +2 for "E+", + up to 10 for adjusted
                // exponent.  Otherwise it could have +1 if negative, plus
                // leading "0.00000"
                mant = new StringBuilder(coeff.length + 14);

                if (scale == 0) {
                    int len = coeff.length;
                    if (len > 1) {
                        mant.append(coeff[0]);
                        if (form == BigDecimalLayoutForm.SCIENTIFIC) {
                            mant.append('.');
                            dot = true;
                            mant.append(coeff, 1, len - 1);
                            exp = new StringBuilder("+");
                            if (len < 10)
                                exp.append("0").append(len - 1);
                            else
                                exp.append(len - 1);
                        } else {
                            mant.append(coeff, 1, len - 1);
                        }
                    } else {
                        mant.append(coeff);
                        if (form == BigDecimalLayoutForm.SCIENTIFIC)
                            exp = new StringBuilder("+00");
                    }
                    return;
                }
                long adjusted = -(long) scale + (coeff.length - 1);
                if (form == BigDecimalLayoutForm.DECIMAL_FLOAT) {
                    // count of padding zeros
                    int pad = scale - coeff.length;
                    if (pad >= 0) {
                        // 0.xxx form
                        mant.append("0.");
                        dot = true;
                        for (; pad > 0 ; pad--) mant.append('0');
                        mant.append(coeff);
                    } else {
                        if (-pad < coeff.length) {
                            // xx.xx form
                            mant.append(coeff, 0, -pad);
                            mant.append('.');
                            dot = true;
                            mant.append(coeff, -pad, scale);
                        } else {
                            // xx form
                            mant.append(coeff, 0, coeff.length);
                            for (int i = 0; i < -scale; i++)
                                mant.append('0');
                            this.scale = 0;
                        }
                    }
                } else {
                    // x.xxx form
                    mant.append(coeff[0]);
                    if (coeff.length > 1) {
                        mant.append('.');
                        dot = true;
                        mant.append(coeff, 1, coeff.length-1);
                    }
                    exp = new StringBuilder();
                    if (adjusted != 0) {
                        long abs = Math.abs(adjusted);
                        // require sign
                        exp.append(adjusted < 0 ? '-' : '+');
                        if (abs < 10)
                            exp.append('0');
                        exp.append(abs);
                    } else {
                        exp.append("+00");
                    }
                }
            }
        }

        private int adjustWidth(int width, Flags f, boolean neg) {
            int newW = width;
            if (newW != -1 && neg && f.contains(Flags.PARENTHESES))
                newW--;
            return newW;
        }

        // Add a '.' to th mantissa if required
        private char[] addDot(char[] mant) {
            char[] tmp = mant;
            tmp = new char[mant.length + 1];
            System.arraycopy(mant, 0, tmp, 0, mant.length);
            tmp[tmp.length - 1] = '.';
            return tmp;
        }

        // Add trailing zeros in the case precision is greater than the number
        // of available digits after the decimal separator.
        private char[] trailingZeros(char[] mant, int nzeros) {
            char[] tmp = mant;
            if (nzeros > 0) {
                tmp = new char[mant.length + nzeros];
                System.arraycopy(mant, 0, tmp, 0, mant.length);
                for (int i = mant.length; i < tmp.length; i++)
                    tmp[i] = '0';
            }
            return tmp;
        }

        private void print(Calendar t, char c, Locale l)  throws IOException
        {
            StringBuilder sb = new StringBuilder();
            print(sb, t, c, l);

            // justify based on width
            String s = justify(sb.toString());
            if (f.contains(Flags.UPPERCASE))
                s = s.toUpperCase();

            a.append(s);
        }

        private Appendable print(StringBuilder sb, Calendar t, char c,
                                 Locale l)
            throws IOException
        {
            assert(width == -1);
            if (sb == null)
                sb = new StringBuilder();
            switch (c) {
            case DateTime.HOUR_OF_DAY_0: // 'H' (00 - 23)
            case DateTime.HOUR_0:        // 'I' (01 - 12)
            case DateTime.HOUR_OF_DAY:   // 'k' (0 - 23) -- like H
            case DateTime.HOUR:        { // 'l' (1 - 12) -- like I
                int i = t.get(Calendar.HOUR_OF_DAY);
                if (c == DateTime.HOUR_0 || c == DateTime.HOUR)
                    i = (i == 0 || i == 12 ? 12 : i % 12);
                Flags flags = (c == DateTime.HOUR_OF_DAY_0
                               || c == DateTime.HOUR_0
                               ? Flags.ZERO_PAD
                               : Flags.NONE);
                sb.append(localizedMagnitude(null, i, flags, 2, l));
                break;
            }
            case DateTime.MINUTE:      { // 'M' (00 - 59)
                int i = t.get(Calendar.MINUTE);
                Flags flags = Flags.ZERO_PAD;
                sb.append(localizedMagnitude(null, i, flags, 2, l));
                break;
            }
            case DateTime.NANOSECOND:  { // 'N' (000000000 - 999999999)
                int i = t.get(Calendar.MILLISECOND) * 1000000;
                Flags flags = Flags.ZERO_PAD;
                sb.append(localizedMagnitude(null, i, flags, 9, l));
                break;
            }
            case DateTime.MILLISECOND: { // 'L' (000 - 999)
                int i = t.get(Calendar.MILLISECOND);
                Flags flags = Flags.ZERO_PAD;
                sb.append(localizedMagnitude(null, i, flags, 3, l));
                break;
            }
            case DateTime.MILLISECOND_SINCE_EPOCH: { // 'Q' (0 - 99...?)
                long i = t.getTimeInMillis();
                Flags flags = Flags.NONE;
                sb.append(localizedMagnitude(null, i, flags, width, l));
                break;
            }
            case DateTime.AM_PM:       { // 'p' (am or pm)
                // Calendar.AM = 0, Calendar.PM = 1, LocaleElements defines upper
                String[] ampm = { "AM", "PM" };
                if (l != null && l != Locale.US) {
                    DateFormatSymbols dfs = DateFormatSymbols.getInstance(l);
                    ampm = dfs.getAmPmStrings();
                }
                String s = ampm[t.get(Calendar.AM_PM)];
                sb.append(s.toLowerCase(l != null ? l : Locale.US));
                break;
            }
            case DateTime.SECONDS_SINCE_EPOCH: { // 's' (0 - 99...?)
                long i = t.getTimeInMillis() / 1000;
                Flags flags = Flags.NONE;
                sb.append(localizedMagnitude(null, i, flags, width, l));
                break;
            }
            case DateTime.SECOND:      { // 'S' (00 - 60 - leap second)
                int i = t.get(Calendar.SECOND);
                Flags flags = Flags.ZERO_PAD;
                sb.append(localizedMagnitude(null, i, flags, 2, l));
                break;
            }
            case DateTime.ZONE_NUMERIC: { // 'z' ({-|+}####) - ls minus?
                int i = t.get(Calendar.ZONE_OFFSET) + t.get(Calendar.DST_OFFSET);
                boolean neg = i < 0;
                sb.append(neg ? '-' : '+');
                if (neg)
                    i = -i;
                int min = i / 60000;
                // combine minute and hour into a single integer
                int offset = (min / 60) * 100 + (min % 60);
                Flags flags = Flags.ZERO_PAD;

                sb.append(localizedMagnitude(null, offset, flags, 4, l));
                break;
            }
            case DateTime.ZONE:        { // 'Z' (symbol)
                TimeZone tz = t.getTimeZone();
                sb.append(tz.getDisplayName((t.get(Calendar.DST_OFFSET) != 0),
                                           TimeZone.SHORT,
                                            (l == null) ? Locale.US : l));
                break;
            }

            // Date
            case DateTime.NAME_OF_DAY_ABBREV:     // 'a'
            case DateTime.NAME_OF_DAY:          { // 'A'
                int i = t.get(Calendar.DAY_OF_WEEK);
                Locale lt = ((l == null) ? Locale.US : l);
                DateFormatSymbols dfs = DateFormatSymbols.getInstance(lt);
                if (c == DateTime.NAME_OF_DAY)
                    sb.append(dfs.getWeekdays()[i]);
                else
                    sb.append(dfs.getShortWeekdays()[i]);
                break;
            }
            case DateTime.NAME_OF_MONTH_ABBREV:   // 'b'
            case DateTime.NAME_OF_MONTH_ABBREV_X: // 'h' -- same b
            case DateTime.NAME_OF_MONTH:        { // 'B'
                int i = t.get(Calendar.MONTH);
                Locale lt = ((l == null) ? Locale.US : l);
                DateFormatSymbols dfs = DateFormatSymbols.getInstance(lt);
                if (c == DateTime.NAME_OF_MONTH)
                    sb.append(dfs.getMonths()[i]);
                else
                    sb.append(dfs.getShortMonths()[i]);
                break;
            }
            case DateTime.CENTURY:                // 'C' (00 - 99)
            case DateTime.YEAR_2:                 // 'y' (00 - 99)
            case DateTime.YEAR_4:               { // 'Y' (0000 - 9999)
                int i = t.get(Calendar.YEAR);
                int size = 2;
                switch (c) {
                case DateTime.CENTURY:
                    i /= 100;
                    break;
                case DateTime.YEAR_2:
                    i %= 100;
                    break;
                case DateTime.YEAR_4:
                    size = 4;
                    break;
                }
                Flags flags = Flags.ZERO_PAD;
                sb.append(localizedMagnitude(null, i, flags, size, l));
                break;
            }
            case DateTime.DAY_OF_MONTH_0:         // 'd' (01 - 31)
            case DateTime.DAY_OF_MONTH:         { // 'e' (1 - 31) -- like d
                int i = t.get(Calendar.DATE);
                Flags flags = (c == DateTime.DAY_OF_MONTH_0
                               ? Flags.ZERO_PAD
                               : Flags.NONE);
                sb.append(localizedMagnitude(null, i, flags, 2, l));
                break;
            }
            case DateTime.DAY_OF_YEAR:          { // 'j' (001 - 366)
                int i = t.get(Calendar.DAY_OF_YEAR);
                Flags flags = Flags.ZERO_PAD;
                sb.append(localizedMagnitude(null, i, flags, 3, l));
                break;
            }
            case DateTime.MONTH:                { // 'm' (01 - 12)
                int i = t.get(Calendar.MONTH) + 1;
                Flags flags = Flags.ZERO_PAD;
                sb.append(localizedMagnitude(null, i, flags, 2, l));
                break;
            }

            // Composites
            case DateTime.TIME:         // 'T' (24 hour hh:mm:ss - %tH:%tM:%tS)
            case DateTime.TIME_24_HOUR:    { // 'R' (hh:mm same as %H:%M)
                char sep = ':';
                print(sb, t, DateTime.HOUR_OF_DAY_0, l).append(sep);
                print(sb, t, DateTime.MINUTE, l);
                if (c == DateTime.TIME) {
                    sb.append(sep);
                    print(sb, t, DateTime.SECOND, l);
                }
                break;
            }
            case DateTime.TIME_12_HOUR:    { // 'r' (hh:mm:ss [AP]M)
                char sep = ':';
                print(sb, t, DateTime.HOUR_0, l).append(sep);
                print(sb, t, DateTime.MINUTE, l).append(sep);
                print(sb, t, DateTime.SECOND, l).append(' ');
                // this may be in wrong place for some locales
                StringBuilder tsb = new StringBuilder();
                print(tsb, t, DateTime.AM_PM, l);
                sb.append(tsb.toString().toUpperCase(l != null ? l : Locale.US));
                break;
            }
            case DateTime.DATE_TIME:    { // 'c' (Sat Nov 04 12:02:33 EST 1999)
                char sep = ' ';
                print(sb, t, DateTime.NAME_OF_DAY_ABBREV, l).append(sep);
                print(sb, t, DateTime.NAME_OF_MONTH_ABBREV, l).append(sep);
                print(sb, t, DateTime.DAY_OF_MONTH_0, l).append(sep);
                print(sb, t, DateTime.TIME, l).append(sep);
                print(sb, t, DateTime.ZONE, l).append(sep);
                print(sb, t, DateTime.YEAR_4, l);
                break;
            }
            case DateTime.DATE:            { // 'D' (mm/dd/yy)
                char sep = '/';
                print(sb, t, DateTime.MONTH, l).append(sep);
                print(sb, t, DateTime.DAY_OF_MONTH_0, l).append(sep);
                print(sb, t, DateTime.YEAR_2, l);
                break;
            }
            case DateTime.ISO_STANDARD_DATE: { // 'F' (%Y-%m-%d)
                char sep = '-';
                print(sb, t, DateTime.YEAR_4, l).append(sep);
                print(sb, t, DateTime.MONTH, l).append(sep);
                print(sb, t, DateTime.DAY_OF_MONTH_0, l);
                break;
            }
            default:
                assert false;
            }
            return sb;
        }

        // -- Methods to support throwing exceptions --

        private void failMismatch(Flags f, char c) {
            String fs = f.toString();
            throw new FormatFlagsConversionMismatchException(fs, c);
        }

        private void failConversion(char c, Object arg) {
            throw new IllegalFormatConversionException(c, arg.getClass());
        }

        private char getZero(Locale l) {
            if ((l != null) &&  !l.equals(locale())) {
                DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(l);
                return dfs.getZeroDigit();
            }
            return zero;
        }

        private StringBuilder
            localizedMagnitude(StringBuilder sb, long value, Flags f,
                               int width, Locale l)
        {
            char[] va = Long.toString(value, 10).toCharArray();
            return localizedMagnitude(sb, va, f, width, l);
        }

        private StringBuilder
            localizedMagnitude(StringBuilder sb, char[] value, Flags f,
                               int width, Locale l)
        {
            if (sb == null)
                sb = new StringBuilder();
            int begin = sb.length();

            char zero = getZero(l);

            // determine localized grouping separator and size
            char grpSep = '\0';
            int  grpSize = -1;
            char decSep = '\0';

            int len = value.length;
            int dot = len;
            for (int j = 0; j < len; j++) {
                if (value[j] == '.') {
                    dot = j;
                    break;
                }
            }

            if (dot < len) {
                if (l == null || l.equals(Locale.US)) {
                    decSep  = '.';
                } else {
                    DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(l);
                    decSep  = dfs.getDecimalSeparator();
                }
            }

            if (f.contains(Flags.GROUP)) {
                if (l == null || l.equals(Locale.US)) {
                    grpSep = ',';
                    grpSize = 3;
                } else {
                    DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(l);
                    grpSep = dfs.getGroupingSeparator();
                    DecimalFormat df = (DecimalFormat) NumberFormat.getIntegerInstance(l);
                    grpSize = df.getGroupingSize();
                }
            }

            // localize the digits inserting group separators as necessary
            for (int j = 0; j < len; j++) {
                if (j == dot) {
                    sb.append(decSep);
                    // no more group separators after the decimal separator
                    grpSep = '\0';
                    continue;
                }

                char c = value[j];
                sb.append((char) ((c - '0') + zero));
                if (grpSep != '\0' && j != dot - 1 && ((dot - j) % grpSize == 1))
                    sb.append(grpSep);
            }

            // apply zero padding
            len = sb.length();
            if (width != -1 && f.contains(Flags.ZERO_PAD))
                for (int k = 0; k < width - len; k++)
                    sb.insert(begin, zero);

            return sb;
        }
    }

    private static class Flags {
        private int flags;

        static final Flags NONE          = new Flags(0);      // ''

        // duplicate declarations from Formattable.java
        static final Flags LEFT_JUSTIFY  = new Flags(1<<0);   // '-'
        static final Flags UPPERCASE     = new Flags(1<<1);   // '^'
        static final Flags ALTERNATE     = new Flags(1<<2);   // '#'

        // numerics
        static final Flags PLUS          = new Flags(1<<3);   // '+'
        static final Flags LEADING_SPACE = new Flags(1<<4);   // ' '
        static final Flags ZERO_PAD      = new Flags(1<<5);   // '0'
        static final Flags GROUP         = new Flags(1<<6);   // ','
        static final Flags PARENTHESES   = new Flags(1<<7);   // '('

        // indexing
        static final Flags PREVIOUS      = new Flags(1<<8);   // '<'

        private Flags(int f) {
            flags = f;
        }

        public int valueOf() {
            return flags;
        }

        public boolean contains(Flags f) {
            return (flags & f.valueOf()) == f.valueOf();
        }

        public Flags dup() {
            return new Flags(flags);
        }

        private Flags add(Flags f) {
            flags |= f.valueOf();
            return this;
        }

        public Flags remove(Flags f) {
            flags &= ~f.valueOf();
            return this;
        }

        public static Flags parse(String s) {
            char[] ca = s.toCharArray();
            Flags f = new Flags(0);
            for (int i = 0; i < ca.length; i++) {
                Flags v = parse(ca[i]);
                if (f.contains(v))
                    throw new DuplicateFormatFlagsException(v.toString());
                f.add(v);
            }
            return f;
        }

        // parse those flags which may be provided by users
        private static Flags parse(char c) {
            switch (c) {
            case '-': return LEFT_JUSTIFY;
            case '#': return ALTERNATE;
            case '+': return PLUS;
            case ' ': return LEADING_SPACE;
            case '0': return ZERO_PAD;
            case ',': return GROUP;
            case '(': return PARENTHESES;
            case '<': return PREVIOUS;
            default:
                throw new UnknownFormatFlagsException(String.valueOf(c));
            }
        }

        // Returns a string representation of the current {@code Flags}.
        public static String toString(Flags f) {
            return f.toString();
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (contains(LEFT_JUSTIFY))  sb.append('-');
            if (contains(UPPERCASE))     sb.append('^');
            if (contains(ALTERNATE))     sb.append('#');
            if (contains(PLUS))          sb.append('+');
            if (contains(LEADING_SPACE)) sb.append(' ');
            if (contains(ZERO_PAD))      sb.append('0');
            if (contains(GROUP))         sb.append(',');
            if (contains(PARENTHESES))   sb.append('(');
            if (contains(PREVIOUS))      sb.append('<');
            return sb.toString();
        }
    }

    private static class Conversion {
        // Byte, Short, Integer, Long, BigInteger
        // (and associated primitives due to autoboxing)
        static final char DECIMAL_INTEGER     = 'd';
        static final char OCTAL_INTEGER       = 'o';
        static final char HEXADECIMAL_INTEGER = 'x';
        static final char HEXADECIMAL_INTEGER_UPPER = 'X';

        // Float, Double, BigDecimal
        // (and associated primitives due to autoboxing)
        static final char SCIENTIFIC          = 'e';
        static final char SCIENTIFIC_UPPER    = 'E';
        static final char GENERAL             = 'g';
        static final char GENERAL_UPPER       = 'G';
        static final char DECIMAL_FLOAT       = 'f';
        static final char HEXADECIMAL_FLOAT   = 'a';
        static final char HEXADECIMAL_FLOAT_UPPER = 'A';

        // Character, Byte, Short, Integer
        // (and associated primitives due to autoboxing)
        static final char CHARACTER           = 'c';
        static final char CHARACTER_UPPER     = 'C';

        // java.util.Date, java.util.Calendar, long
        static final char DATE_TIME           = 't';
        static final char DATE_TIME_UPPER     = 'T';

        // if (arg.TYPE != boolean) return boolean
        // if (arg != null) return true; else return false;
        static final char BOOLEAN             = 'b';
        static final char BOOLEAN_UPPER       = 'B';
        // if (arg instanceof Formattable) arg.formatTo()
        // else arg.toString();
        static final char STRING              = 's';
        static final char STRING_UPPER        = 'S';
        // arg.hashCode()
        static final char HASHCODE            = 'h';
        static final char HASHCODE_UPPER      = 'H';

        static final char LINE_SEPARATOR      = 'n';
        static final char PERCENT_SIGN        = '%';

        static boolean isValid(char c) {
            return (isGeneral(c) || isInteger(c) || isFloat(c) || isText(c)
                    || c == 't' || isCharacter(c));
        }

        // Returns true iff the Conversion is applicable to all objects.
        static boolean isGeneral(char c) {
            switch (c) {
            case BOOLEAN:
            case BOOLEAN_UPPER:
            case STRING:
            case STRING_UPPER:
            case HASHCODE:
            case HASHCODE_UPPER:
                return true;
            default:
                return false;
            }
        }

        // Returns true iff the Conversion is applicable to character.
        static boolean isCharacter(char c) {
            switch (c) {
            case CHARACTER:
            case CHARACTER_UPPER:
                return true;
            default:
                return false;
            }
        }

        // Returns true iff the Conversion is an integer type.
        static boolean isInteger(char c) {
            switch (c) {
            case DECIMAL_INTEGER:
            case OCTAL_INTEGER:
            case HEXADECIMAL_INTEGER:
            case HEXADECIMAL_INTEGER_UPPER:
                return true;
            default:
                return false;
            }
        }

        // Returns true iff the Conversion is a floating-point type.
        static boolean isFloat(char c) {
            switch (c) {
            case SCIENTIFIC:
            case SCIENTIFIC_UPPER:
            case GENERAL:
            case GENERAL_UPPER:
            case DECIMAL_FLOAT:
            case HEXADECIMAL_FLOAT:
            case HEXADECIMAL_FLOAT_UPPER:
                return true;
            default:
                return false;
            }
        }

        // Returns true iff the Conversion does not require an argument
        static boolean isText(char c) {
            switch (c) {
            case LINE_SEPARATOR:
            case PERCENT_SIGN:
                return true;
            default:
                return false;
            }
        }
    }

    private static class DateTime {
        static final char HOUR_OF_DAY_0 = 'H'; // (00 - 23)
        static final char HOUR_0        = 'I'; // (01 - 12)
        static final char HOUR_OF_DAY   = 'k'; // (0 - 23) -- like H
        static final char HOUR          = 'l'; // (1 - 12) -- like I
        static final char MINUTE        = 'M'; // (00 - 59)
        static final char NANOSECOND    = 'N'; // (000000000 - 999999999)
        static final char MILLISECOND   = 'L'; // jdk, not in gnu (000 - 999)
        static final char MILLISECOND_SINCE_EPOCH = 'Q'; // (0 - 99...?)
        static final char AM_PM         = 'p'; // (am or pm)
        static final char SECONDS_SINCE_EPOCH = 's'; // (0 - 99...?)
        static final char SECOND        = 'S'; // (00 - 60 - leap second)
        static final char TIME          = 'T'; // (24 hour hh:mm:ss)
        static final char ZONE_NUMERIC  = 'z'; // (-1200 - +1200) - ls minus?
        static final char ZONE          = 'Z'; // (symbol)

        // Date
        static final char NAME_OF_DAY_ABBREV    = 'a'; // 'a'
        static final char NAME_OF_DAY           = 'A'; // 'A'
        static final char NAME_OF_MONTH_ABBREV  = 'b'; // 'b'
        static final char NAME_OF_MONTH         = 'B'; // 'B'
        static final char CENTURY               = 'C'; // (00 - 99)
        static final char DAY_OF_MONTH_0        = 'd'; // (01 - 31)
        static final char DAY_OF_MONTH          = 'e'; // (1 - 31) -- like d
// *    static final char ISO_WEEK_OF_YEAR_2    = 'g'; // cross %y %V
// *    static final char ISO_WEEK_OF_YEAR_4    = 'G'; // cross %Y %V
        static final char NAME_OF_MONTH_ABBREV_X  = 'h'; // -- same b
        static final char DAY_OF_YEAR           = 'j'; // (001 - 366)
        static final char MONTH                 = 'm'; // (01 - 12)
// *    static final char DAY_OF_WEEK_1         = 'u'; // (1 - 7) Monday
// *    static final char WEEK_OF_YEAR_SUNDAY   = 'U'; // (0 - 53) Sunday+
// *    static final char WEEK_OF_YEAR_MONDAY_01 = 'V'; // (01 - 53) Monday+
// *    static final char DAY_OF_WEEK_0         = 'w'; // (0 - 6) Sunday
// *    static final char WEEK_OF_YEAR_MONDAY   = 'W'; // (00 - 53) Monday
        static final char YEAR_2                = 'y'; // (00 - 99)
        static final char YEAR_4                = 'Y'; // (0000 - 9999)

        // Composites
        static final char TIME_12_HOUR  = 'r'; // (hh:mm:ss [AP]M)
        static final char TIME_24_HOUR  = 'R'; // (hh:mm same as %H:%M)
// *    static final char LOCALE_TIME   = 'X'; // (%H:%M:%S) - parse format?
        static final char DATE_TIME             = 'c';
                                            // (Sat Nov 04 12:02:33 EST 1999)
        static final char DATE                  = 'D'; // (mm/dd/yy)
        static final char ISO_STANDARD_DATE     = 'F'; // (%Y-%m-%d)
// *    static final char LOCALE_DATE           = 'x'; // (mm/dd/yy)

        static boolean isValid(char c) {
            switch (c) {
            case HOUR_OF_DAY_0:
            case HOUR_0:
            case HOUR_OF_DAY:
            case HOUR:
            case MINUTE:
            case NANOSECOND:
            case MILLISECOND:
            case MILLISECOND_SINCE_EPOCH:
            case AM_PM:
            case SECONDS_SINCE_EPOCH:
            case SECOND:
            case TIME:
            case ZONE_NUMERIC:
            case ZONE:

            // Date
            case NAME_OF_DAY_ABBREV:
            case NAME_OF_DAY:
            case NAME_OF_MONTH_ABBREV:
            case NAME_OF_MONTH:
            case CENTURY:
            case DAY_OF_MONTH_0:
            case DAY_OF_MONTH:
// *        case ISO_WEEK_OF_YEAR_2:
// *        case ISO_WEEK_OF_YEAR_4:
            case NAME_OF_MONTH_ABBREV_X:
            case DAY_OF_YEAR:
            case MONTH:
// *        case DAY_OF_WEEK_1:
// *        case WEEK_OF_YEAR_SUNDAY:
// *        case WEEK_OF_YEAR_MONDAY_01:
// *        case DAY_OF_WEEK_0:
// *        case WEEK_OF_YEAR_MONDAY:
            case YEAR_2:
            case YEAR_4:

            // Composites
            case TIME_12_HOUR:
            case TIME_24_HOUR:
// *        case LOCALE_TIME:
            case DATE_TIME:
            case DATE:
            case ISO_STANDARD_DATE:
// *        case LOCALE_DATE:
                return true;
            default:
                return false;
            }
        }
    }
}
