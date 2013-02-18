/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Copyright (c) 2008-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.time.format;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.DAY_OF_YEAR;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import java.io.IOException;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder.CompositePrinterParser;
import java.time.chrono.Chronology;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Formatter for printing and parsing date-time objects.
 * <p>
 * This class provides the main application entry point for printing and parsing
 * and provides common implementations of {@code DateTimeFormatter}:
 * <p><ul>
 * <li>Using pattern letters, such as {@code yyyy-MMM-dd}
 * <li>Using localized styles, such as {@code long} or {@code medium}
 * <li>Using predefined constants, such as {@code ISO_LOCAL_DATE}
 * </ul></p>
 *
 * <p>
 * In most cases, provided formatters will be sufficient.
 * For more complex formatters, a {@link DateTimeFormatterBuilder builder} is provided.
 * The main date-time classes provide two methods - one for printing,
 * {@code toString(DateTimeFormatter formatter)}, and one for parsing,
 * {@code parse(CharSequence text, DateTimeFormatter formatter)}.
 * For example:
 * <pre>
 *  String text = date.toString(formatter);
 *  LocalDate date = LocalDate.parse(text, formatter);
 * </pre>
 * Some aspects of formatting and parsing are dependent on the locale.
 * The locale can be changed using the {@link #withLocale(Locale)} method
 * which returns a new formatter in the requested locale.
 * <p>
 * Some applications may need to use the older {@link Format} class for formatting.
 * The {@link #toFormat()} method returns an implementation of the old API.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
public final class DateTimeFormatter {

    /**
     * The printer and/or parser to use, not null.
     */
    private final CompositePrinterParser printerParser;
    /**
     * The locale to use for formatting, not null.
     */
    private final Locale locale;
    /**
     * The symbols to use for formatting, not null.
     */
    private final DateTimeFormatSymbols symbols;
    /**
     * The chronology to use for formatting, null for no override.
     */
    private final Chronology chrono;
    /**
     * The zone to use for formatting, null for no override.
     */
    private final ZoneId zone;

    //-----------------------------------------------------------------------
    /**
     * Creates a formatter using the specified pattern.
     * <p>
     * This method will create a formatter based on a simple pattern of letters and symbols.
     * For example, {@code d MMM yyyy} will format 2011-12-03 as '3 Dec 2011'.
     * <p>
     * The returned formatter will use the default locale, but this can be changed
     * using {@link DateTimeFormatter#withLocale(Locale)}.
     * <p>
     * All letters 'A' to 'Z' and 'a' to 'z' are reserved as pattern letters.
     * The following pattern letters are defined:
     * <pre>
     *  Symbol  Meaning                     Presentation      Examples
     *  ------  -------                     ------------      -------
     *   G       era                         text              A; AD; Anno Domini
     *   y       year                        year              2004; 04
     *   D       day-of-year                 number            189
     *   M       month-of-year               number/text       7; 07; Jul; July; J
     *   d       day-of-month                number            10
     *
     *   Q       quarter-of-year             number/text       3; 03; Q3
     *   Y       week-based-year             year              1996; 96
     *   w       week-of-year                number            27
     *   W       week-of-month               number            27
     *   e       localized day-of-week       number            2; Tue; Tuesday; T
     *   E       day-of-week                 number/text       2; Tue; Tuesday; T
     *   F       week-of-month               number            3
     *
     *   a       am-pm-of-day                text              PM
     *   h       clock-hour-of-am-pm (1-12)  number            12
     *   K       hour-of-am-pm (0-11)        number            0
     *   k       clock-hour-of-am-pm (1-24)  number            0
     *
     *   H       hour-of-day (0-23)          number            0
     *   m       minute-of-hour              number            30
     *   s       second-of-minute            number            55
     *   S       fraction-of-second          fraction          978
     *   A       milli-of-day                number            1234
     *   n       nano-of-second              number            987654321
     *   N       nano-of-day                 number            1234000000
     *
     *   V       time-zone ID                zone-id           America/Los_Angeles; Z; -08:30
     *   z       time-zone name              zone-name         Pacific Standard Time; PST
     *   X       zone-offset 'Z' for zero    offset-X          Z; -08; -0830; -08:30; -083015; -08:30:15;
     *   x       zone-offset                 offset-x          +0000; -08; -0830; -08:30; -083015; -08:30:15;
     *   Z       zone-offset                 offset-Z          +0000; -0800; -08:00;
     *
     *   p       pad next                    pad modifier      1
     *
     *   '       escape for text             delimiter
     *   ''      single quote                literal           '
     *   [       optional section start
     *   ]       optional section end
     *   {}      reserved for future use
     * </pre>
     * <p>
     * The count of pattern letters determine the format.
     * <p>
     * <b>Text</b>: The text style is determined based on the number of pattern letters used.
     * Less than 4 pattern letters will use the {@link TextStyle#SHORT short form}.
     * Exactly 4 pattern letters will use the {@link TextStyle#FULL full form}.
     * Exactly 5 pattern letters will use the {@link TextStyle#NARROW narrow form}.
     * <p>
     * <b>Number</b>: If the count of letters is one, then the value is output using the minimum number
     * of digits and without padding as per {@link DateTimeFormatterBuilder#appendValue(java.time.temporal.TemporalField)}.
     * Otherwise, the count of digits is used as the width of the output field as per
     * {@link DateTimeFormatterBuilder#appendValue(java.time.temporal.TemporalField, int)}.
     * <p>
     * <b>Number/Text</b>: If the count of pattern letters is 3 or greater, use the Text rules above.
     * Otherwise use the Number rules above.
     * <p>
     * <b>Fraction</b>: Outputs the nano-of-second field as a fraction-of-second.
     * The nano-of-second value has nine digits, thus the count of pattern letters is from 1 to 9.
     * If it is less than 9, then the nano-of-second value is truncated, with only the most
     * significant digits being output.
     * When parsing in strict mode, the number of parsed digits must match the count of pattern letters.
     * When parsing in lenient mode, the number of parsed digits must be at least the count of pattern
     * letters, up to 9 digits.
     * <p>
     * <b>Year</b>: The count of letters determines the minimum field width below which padding is used.
     * If the count of letters is two, then a {@link DateTimeFormatterBuilder#appendValueReduced reduced}
     * two digit form is used.
     * For printing, this outputs the rightmost two digits. For parsing, this will parse using the
     * base value of 2000, resulting in a year within the range 2000 to 2099 inclusive.
     * If the count of letters is less than four (but not two), then the sign is only output for negative
     * years as per {@link SignStyle#NORMAL}.
     * Otherwise, the sign is output if the pad width is exceeded, as per {@link SignStyle#EXCEEDS_PAD}
     * <p>
     * <b>ZoneId</b>: This outputs the time-zone ID, such as 'Europe/Paris'.
     * If the count of letters is two, then the time-zone ID is output.
     * Any other count of letters throws {@code IllegalArgumentException}.
     * <p>
     * <b>Zone names</b>: This outputs the display name of the time-zone ID.
     * If the count of letters is one, two or three, then the short name is output.
     * If the count of letters is four, then the full name is output.
     * Five or more letters throws {@code IllegalArgumentException}.
     * <p>
     * <b>Offset X and x</b>: This formats the offset based on the number of pattern letters.
     * One letter outputs just the hour', such as '+01', unless the minute is non-zero
     * in which case the minute is also output, such as '+0130'.
     * Two letters outputs the hour and minute, without a colon, such as '+0130'.
     * Three letters outputs the hour and minute, with a colon, such as '+01:30'.
     * Four letters outputs the hour and minute and optional second, without a colon, such as '+013015'.
     * Five letters outputs the hour and minute and optional second, with a colon, such as '+01:30:15'.
     * Six or more letters throws {@code IllegalArgumentException}.
     * Pattern letter 'X' (upper case) will output 'Z' when the offset to be output would be zero,
     * whereas pattern letter 'x' (lower case) will output '+00', '+0000', or '+00:00'.
     * <p>
     * <b>Offset Z</b>: This formats the offset based on the number of pattern letters.
     * One, two or three letters outputs the hour and minute, without a colon, such as '+0130'.
     * Four or more letters throws {@code IllegalArgumentException}.
     * The output will be '+0000' when the offset is zero.
     * <p>
     * <b>Optional section</b>: The optional section markers work exactly like calling
     * {@link DateTimeFormatterBuilder#optionalStart()} and {@link DateTimeFormatterBuilder#optionalEnd()}.
     * <p>
     * <b>Pad modifier</b>: Modifies the pattern that immediately follows to be padded with spaces.
     * The pad width is determined by the number of pattern letters.
     * This is the same as calling {@link DateTimeFormatterBuilder#padNext(int)}.
     * <p>
     * For example, 'ppH' outputs the hour-of-day padded on the left with spaces to a width of 2.
     * <p>
     * Any unrecognized letter is an error.
     * Any non-letter character, other than '[', ']', '{', '}' and the single quote will be output directly.
     * Despite this, it is recommended to use single quotes around all characters that you want to
     * output directly to ensure that future changes do not break your application.
     *
     * @param pattern  the pattern to use, not null
     * @return the formatter based on the pattern, not null
     * @throws IllegalArgumentException if the pattern is invalid
     * @see DateTimeFormatterBuilder#appendPattern(String)
     */
    public static DateTimeFormatter ofPattern(String pattern) {
        return new DateTimeFormatterBuilder().appendPattern(pattern).toFormatter();
    }

    /**
     * Creates a formatter using the specified pattern.
     * <p>
     * This method will create a formatter based on a simple pattern of letters and symbols.
     * For example, {@code d MMM yyyy} will format 2011-12-03 as '3 Dec 2011'.
     * <p>
     * See {@link #ofPattern(String)} for details of the pattern.
     * <p>
     * The returned formatter will use the specified locale, but this can be changed
     * using {@link DateTimeFormatter#withLocale(Locale)}.
     *
     * @param pattern  the pattern to use, not null
     * @param locale  the locale to use, not null
     * @return the formatter based on the pattern, not null
     * @throws IllegalArgumentException if the pattern is invalid
     * @see DateTimeFormatterBuilder#appendPattern(String)
     */
    public static DateTimeFormatter ofPattern(String pattern, Locale locale) {
        return new DateTimeFormatterBuilder().appendPattern(pattern).toFormatter(locale);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a locale specific date format.
     * <p>
     * This returns a formatter that will format or parse a date.
     * The exact format pattern used varies by locale.
     * <p>
     * The locale is determined from the formatter. The formatter returned directly by
     * this method will use the {@link Locale#getDefault(Locale.Category) default FORMAT locale}.
     * The locale can be controlled using {@link DateTimeFormatter#withLocale(Locale) withLocale(Locale)}
     * on the result of this method.
     * <p>
     * Note that the localized pattern is looked up lazily.
     * This {@code DateTimeFormatter} holds the style required and the locale,
     * looking up the pattern required on demand.
     *
     * @param dateStyle  the formatter style to obtain, not null
     * @return the date formatter, not null
     */
    public static DateTimeFormatter ofLocalizedDate(FormatStyle dateStyle) {
        Objects.requireNonNull(dateStyle, "dateStyle");
        return new DateTimeFormatterBuilder().appendLocalized(dateStyle, null).toFormatter();
    }

    /**
     * Returns a locale specific time format.
     * <p>
     * This returns a formatter that will format or parse a time.
     * The exact format pattern used varies by locale.
     * <p>
     * The locale is determined from the formatter. The formatter returned directly by
     * this method will use the {@link Locale#getDefault(Locale.Category) default FORMAT locale}.
     * The locale can be controlled using {@link DateTimeFormatter#withLocale(Locale) withLocale(Locale)}
     * on the result of this method.
     * <p>
     * Note that the localized pattern is looked up lazily.
     * This {@code DateTimeFormatter} holds the style required and the locale,
     * looking up the pattern required on demand.
     *
     * @param timeStyle  the formatter style to obtain, not null
     * @return the time formatter, not null
     */
    public static DateTimeFormatter ofLocalizedTime(FormatStyle timeStyle) {
        Objects.requireNonNull(timeStyle, "timeStyle");
        return new DateTimeFormatterBuilder().appendLocalized(null, timeStyle).toFormatter();
    }

    /**
     * Returns a locale specific date-time formatter, which is typically of short length.
     * <p>
     * This returns a formatter that will format or parse a date-time.
     * The exact format pattern used varies by locale.
     * <p>
     * The locale is determined from the formatter. The formatter returned directly by
     * this method will use the {@link Locale#getDefault(Locale.Category) default FORMAT locale}.
     * The locale can be controlled using {@link DateTimeFormatter#withLocale(Locale) withLocale(Locale)}
     * on the result of this method.
     * <p>
     * Note that the localized pattern is looked up lazily.
     * This {@code DateTimeFormatter} holds the style required and the locale,
     * looking up the pattern required on demand.
     *
     * @param dateTimeStyle  the formatter style to obtain, not null
     * @return the date-time formatter, not null
     */
    public static DateTimeFormatter ofLocalizedDateTime(FormatStyle dateTimeStyle) {
        Objects.requireNonNull(dateTimeStyle, "dateTimeStyle");
        return new DateTimeFormatterBuilder().appendLocalized(dateTimeStyle, dateTimeStyle).toFormatter();
    }

    /**
     * Returns a locale specific date and time format.
     * <p>
     * This returns a formatter that will format or parse a date-time.
     * The exact format pattern used varies by locale.
     * <p>
     * The locale is determined from the formatter. The formatter returned directly by
     * this method will use the {@link Locale#getDefault() default FORMAT locale}.
     * The locale can be controlled using {@link DateTimeFormatter#withLocale(Locale) withLocale(Locale)}
     * on the result of this method.
     * <p>
     * Note that the localized pattern is looked up lazily.
     * This {@code DateTimeFormatter} holds the style required and the locale,
     * looking up the pattern required on demand.
     *
     * @param dateStyle  the date formatter style to obtain, not null
     * @param timeStyle  the time formatter style to obtain, not null
     * @return the date, time or date-time formatter, not null
     */
    public static DateTimeFormatter ofLocalizedDateTime(FormatStyle dateStyle, FormatStyle timeStyle) {
        Objects.requireNonNull(dateStyle, "dateStyle");
        Objects.requireNonNull(timeStyle, "timeStyle");
        return new DateTimeFormatterBuilder().appendLocalized(dateStyle, timeStyle).toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses a date without an offset,
     * such as '2011-12-03'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended local date format.
     * The format consists of:
     * <p><ul>
     * <li>Four digits or more for the {@link ChronoField#YEAR year}.
     * Years in the range 0000 to 9999 will be pre-padded by zero to ensure four digits.
     * Years outside that range will have a prefixed positive or negative symbol.
     * <li>A dash
     * <li>Two digits for the {@link ChronoField#MONTH_OF_YEAR month-of-year}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>A dash
     * <li>Two digits for the {@link ChronoField#DAY_OF_MONTH day-of-month}.
     *  This is pre-padded by zero to ensure two digits.
     * </ul></p>
     */
    public static final DateTimeFormatter ISO_LOCAL_DATE;
    static {
        ISO_LOCAL_DATE = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses a date with an offset,
     * such as '2011-12-03+01:00'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended offset date format.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_LOCAL_DATE}
     * <li>The {@link ZoneOffset#getId() offset ID}. If the offset has seconds then
     *  they will be handled even though this is not part of the ISO-8601 standard.
     *  Parsing is case insensitive.
     * </ul></p>
     */
    public static final DateTimeFormatter ISO_OFFSET_DATE;
    static {
        ISO_OFFSET_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_DATE)
            .appendOffsetId()
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses a date with the
     * offset if available, such as '2011-12-03' or '2011-12-03+01:00'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended date format.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_LOCAL_DATE}
     * <li>If the offset is not available then the format is complete.
     * <li>The {@link ZoneOffset#getId() offset ID}. If the offset has seconds then
     *  they will be handled even though this is not part of the ISO-8601 standard.
     *  Parsing is case insensitive.
     * </ul></p>
     * <p>
     * As this formatter has an optional element, it may be necessary to parse using
     * {@link DateTimeFormatter#parseBest}.
     */
    public static final DateTimeFormatter ISO_DATE;
    static {
        ISO_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_DATE)
            .optionalStart()
            .appendOffsetId()
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO time formatter that formats or parses a time without an offset,
     * such as '10:15' or '10:15:30'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended local time format.
     * The format consists of:
     * <p><ul>
     * <li>Two digits for the {@link ChronoField#HOUR_OF_DAY hour-of-day}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>A colon
     * <li>Two digits for the {@link ChronoField#MINUTE_OF_HOUR minute-of-hour}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>If the second-of-minute is not available then the format is complete.
     * <li>A colon
     * <li>Two digits for the {@link ChronoField#SECOND_OF_MINUTE second-of-minute}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>If the nano-of-second is zero or not available then the format is complete.
     * <li>A decimal point
     * <li>One to nine digits for the {@link ChronoField#NANO_OF_SECOND nano-of-second}.
     *  As many digits will be output as required.
     * </ul></p>
     */
    public static final DateTimeFormatter ISO_LOCAL_TIME;
    static {
        ISO_LOCAL_TIME = new DateTimeFormatterBuilder()
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 0, 9, true)
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO time formatter that formats or parses a time with an offset,
     * such as '10:15+01:00' or '10:15:30+01:00'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended offset time format.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_LOCAL_TIME}
     * <li>The {@link ZoneOffset#getId() offset ID}. If the offset has seconds then
     *  they will be handled even though this is not part of the ISO-8601 standard.
     *  Parsing is case insensitive.
     * </ul></p>
     */
    public static final DateTimeFormatter ISO_OFFSET_TIME;
    static {
        ISO_OFFSET_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_TIME)
            .appendOffsetId()
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO time formatter that formats or parses a time, with the
     * offset if available, such as '10:15', '10:15:30' or '10:15:30+01:00'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended offset time format.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_LOCAL_TIME}
     * <li>If the offset is not available then the format is complete.
     * <li>The {@link ZoneOffset#getId() offset ID}. If the offset has seconds then
     *  they will be handled even though this is not part of the ISO-8601 standard.
     *  Parsing is case insensitive.
     * </ul></p>
     * <p>
     * As this formatter has an optional element, it may be necessary to parse using
     * {@link DateTimeFormatter#parseBest}.
     */
    public static final DateTimeFormatter ISO_TIME;
    static {
        ISO_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_TIME)
            .optionalStart()
            .appendOffsetId()
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses a date-time
     * without an offset, such as '2011-12-03T10:15:30'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended offset date-time format.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_LOCAL_DATE}
     * <li>The letter 'T'. Parsing is case insensitive.
     * <li>The {@link #ISO_LOCAL_TIME}
     * </ul></p>
     */
    public static final DateTimeFormatter ISO_LOCAL_DATE_TIME;
    static {
        ISO_LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_DATE)
            .appendLiteral('T')
            .append(ISO_LOCAL_TIME)
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses a date-time
     * with an offset, such as '2011-12-03T10:15:30+01:00'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended offset date-time format.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_LOCAL_DATE_TIME}
     * <li>The {@link ZoneOffset#getId() offset ID}. If the offset has seconds then
     *  they will be handled even though this is not part of the ISO-8601 standard.
     *  Parsing is case insensitive.
     * </ul></p>
     */
    public static final DateTimeFormatter ISO_OFFSET_DATE_TIME;
    static {
        ISO_OFFSET_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(ISO_LOCAL_DATE_TIME)
            .appendOffsetId()
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses a date-time with
     * offset and zone, such as '2011-12-03T10:15:30+01:00[Europe/Paris]'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * a format that extends the ISO-8601 extended offset date-time format
     * to add the time-zone.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_OFFSET_DATE_TIME}
     * <li>If the zone ID is not available or is a {@code ZoneOffset} then the format is complete.
     * <li>An open square bracket '['.
     * <li>The {@link ZoneId#getId() zone ID}. This is not part of the ISO-8601 standard.
     *  Parsing is case sensitive.
     * <li>A close square bracket ']'.
     * </ul></p>
     */
    public static final DateTimeFormatter ISO_ZONED_DATE_TIME;
    static {
        ISO_ZONED_DATE_TIME = new DateTimeFormatterBuilder()
            .append(ISO_OFFSET_DATE_TIME)
            .optionalStart()
            .appendLiteral('[')
            .parseCaseSensitive()
            .appendZoneRegionId()
            .appendLiteral(']')
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses a date-time
     * with the offset and zone if available, such as '2011-12-03T10:15:30',
     * '2011-12-03T10:15:30+01:00' or '2011-12-03T10:15:30+01:00[Europe/Paris]'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended offset date-time format.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_LOCAL_DATE_TIME}
     * <li>If the offset is not available to format or parse then the format is complete.
     * <li>The {@link ZoneOffset#getId() offset ID}. If the offset has seconds then
     *  they will be handled even though this is not part of the ISO-8601 standard.
     * <li>If the zone ID is not available or is a {@code ZoneOffset} then the format is complete.
     * <li>An open square bracket '['.
     * <li>The {@link ZoneId#getId() zone ID}. This is not part of the ISO-8601 standard.
     *  Parsing is case sensitive.
     * <li>A close square bracket ']'.
     * </ul></p>
     * <p>
     * As this formatter has an optional element, it may be necessary to parse using
     * {@link DateTimeFormatter#parseBest}.
     */
    public static final DateTimeFormatter ISO_DATE_TIME;
    static {
        ISO_DATE_TIME = new DateTimeFormatterBuilder()
            .append(ISO_LOCAL_DATE_TIME)
            .optionalStart()
            .appendOffsetId()
            .optionalStart()
            .appendLiteral('[')
            .parseCaseSensitive()
            .appendZoneRegionId()
            .appendLiteral(']')
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses the ordinal date
     * without an offset, such as '2012-337'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended ordinal date format.
     * The format consists of:
     * <p><ul>
     * <li>Four digits or more for the {@link ChronoField#YEAR year}.
     * Years in the range 0000 to 9999 will be pre-padded by zero to ensure four digits.
     * Years outside that range will have a prefixed positive or negative symbol.
     * <li>A dash
     * <li>Three digits for the {@link ChronoField#DAY_OF_YEAR day-of-year}.
     *  This is pre-padded by zero to ensure three digits.
     * <li>If the offset is not available to format or parse then the format is complete.
     * <li>The {@link ZoneOffset#getId() offset ID}. If the offset has seconds then
     *  they will be handled even though this is not part of the ISO-8601 standard.
     *  Parsing is case insensitive.
     * </ul></p>
     * <p>
     * As this formatter has an optional element, it may be necessary to parse using
     * {@link DateTimeFormatter#parseBest}.
     */
    public static final DateTimeFormatter ISO_ORDINAL_DATE;
    static {
        ISO_ORDINAL_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(DAY_OF_YEAR, 3)
            .optionalStart()
            .appendOffsetId()
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses the week-based date
     * without an offset, such as '2012-W48-6'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 extended week-based date format.
     * The format consists of:
     * <p><ul>
     * <li>Four digits or more for the {@link IsoFields#WEEK_BASED_YEAR week-based-year}.
     * Years in the range 0000 to 9999 will be pre-padded by zero to ensure four digits.
     * Years outside that range will have a prefixed positive or negative symbol.
     * <li>A dash
     * <li>The letter 'W'. Parsing is case insensitive.
     * <li>Two digits for the {@link IsoFields#WEEK_OF_WEEK_BASED_YEAR week-of-week-based-year}.
     *  This is pre-padded by zero to ensure three digits.
     * <li>A dash
     * <li>One digit for the {@link ChronoField#DAY_OF_WEEK day-of-week}.
     *  The value run from Monday (1) to Sunday (7).
     * <li>If the offset is not available to format or parse then the format is complete.
     * <li>The {@link ZoneOffset#getId() offset ID}. If the offset has seconds then
     *  they will be handled even though this is not part of the ISO-8601 standard.
     *  Parsing is case insensitive.
     * </ul></p>
     * <p>
     * As this formatter has an optional element, it may be necessary to parse using
     * {@link DateTimeFormatter#parseBest}.
     */
    public static final DateTimeFormatter ISO_WEEK_DATE;
    static {
        ISO_WEEK_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(IsoFields.WEEK_BASED_YEAR, 4, 10, SignStyle.EXCEEDS_PAD)
            .appendLiteral("-W")
            .appendValue(IsoFields.WEEK_OF_WEEK_BASED_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_WEEK, 1)
            .optionalStart()
            .appendOffsetId()
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO instant formatter that formats or parses an instant in UTC.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 instant format.
     * The format consists of:
     * <p><ul>
     * <li>The {@link #ISO_OFFSET_DATE_TIME} where the instant is converted from
     *  {@link ChronoField#INSTANT_SECONDS} and {@link ChronoField#NANO_OF_SECOND}
     *  using the {@code UTC} offset. Parsing is case insensitive.
     * </ul></p>
     */
    public static final DateTimeFormatter ISO_INSTANT;
    static {
        ISO_INSTANT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendInstant()
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the ISO date formatter that formats or parses a date without an offset,
     * such as '20111203'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * the ISO-8601 basic local date format.
     * The format consists of:
     * <p><ul>
     * <li>Four digits for the {@link ChronoField#YEAR year}.
     *  Only years in the range 0000 to 9999 are supported.
     * <li>Two digits for the {@link ChronoField#MONTH_OF_YEAR month-of-year}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>Two digits for the {@link ChronoField#DAY_OF_MONTH day-of-month}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>If the offset is not available to format or parse then the format is complete.
     * <li>The {@link ZoneOffset#getId() offset ID} without colons. If the offset has
     *  seconds then they will be handled even though this is not part of the ISO-8601 standard.
     *  Parsing is case insensitive.
     * </ul></p>
     * <p>
     * As this formatter has an optional element, it may be necessary to parse using
     * {@link DateTimeFormatter#parseBest}.
     */
    public static final DateTimeFormatter BASIC_ISO_DATE;
    static {
        BASIC_ISO_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(YEAR, 4)
            .appendValue(MONTH_OF_YEAR, 2)
            .appendValue(DAY_OF_MONTH, 2)
            .optionalStart()
            .appendOffset("+HHMMss", "Z")
            .toFormatter();
    }

    //-----------------------------------------------------------------------
    /**
     * The RFC-1123 date-time formatter, such as 'Tue, 3 Jun 2008 11:05:30 GMT'.
     * <p>
     * This returns an immutable formatter capable of formatting and parsing
     * most of the RFC-1123 format.
     * RFC-1123 updates RFC-822 changing the year from two digits to four.
     * This implementation requires a four digit year.
     * This implementation also does not handle North American or military zone
     * names, only 'GMT' and offset amounts.
     * <p>
     * The format consists of:
     * <p><ul>
     * <li>If the day-of-week is not available to format or parse then jump to day-of-month.
     * <li>Three letter {@link ChronoField#DAY_OF_WEEK day-of-week} in English.
     * <li>A comma
     * <li>A space
     * <li>One or two digits for the {@link ChronoField#DAY_OF_MONTH day-of-month}.
     * <li>A space
     * <li>Three letter {@link ChronoField#MONTH_OF_YEAR month-of-year} in English.
     * <li>A space
     * <li>Four digits for the {@link ChronoField#YEAR year}.
     *  Only years in the range 0000 to 9999 are supported.
     * <li>A space
     * <li>Two digits for the {@link ChronoField#HOUR_OF_DAY hour-of-day}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>A colon
     * <li>Two digits for the {@link ChronoField#MINUTE_OF_HOUR minute-of-hour}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>If the second-of-minute is not available then jump to the next space.
     * <li>A colon
     * <li>Two digits for the {@link ChronoField#SECOND_OF_MINUTE second-of-minute}.
     *  This is pre-padded by zero to ensure two digits.
     * <li>A space
     * <li>The {@link ZoneOffset#getId() offset ID} without colons or seconds.
     *  An offset of zero uses "GMT". North American zone names and military zone names are not handled.
     * </ul></p>
     * <p>
     * Parsing is case insensitive.
     */
    public static final DateTimeFormatter RFC_1123_DATE_TIME;
    static {
        // manually code maps to ensure correct data always used
        // (locale data can be changed by application code)
        Map<Long, String> dow = new HashMap<>();
        dow.put(1L, "Mon");
        dow.put(2L, "Tue");
        dow.put(3L, "Wed");
        dow.put(4L, "Thu");
        dow.put(5L, "Fri");
        dow.put(6L, "Sat");
        dow.put(7L, "Sun");
        Map<Long, String> moy = new HashMap<>();
        moy.put(1L, "Jan");
        moy.put(2L, "Feb");
        moy.put(3L, "Mar");
        moy.put(4L, "Apr");
        moy.put(5L, "May");
        moy.put(6L, "Jun");
        moy.put(7L, "Jul");
        moy.put(8L, "Aug");
        moy.put(9L, "Sep");
        moy.put(10L, "Oct");
        moy.put(11L, "Nov");
        moy.put(12L, "Dec");
        RFC_1123_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .parseLenient()
            .optionalStart()
            .appendText(DAY_OF_WEEK, dow)
            .appendLiteral(", ")
            .optionalEnd()
            .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(' ')
            .appendText(MONTH_OF_YEAR, moy)
            .appendLiteral(' ')
            .appendValue(YEAR, 4)  // 2 digit year not handled
            .appendLiteral(' ')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalEnd()
            .appendLiteral(' ')
            .appendOffset("+HHMM", "GMT")  // should handle UT/Z/EST/EDT/CST/CDT/MST/MDT/PST/MDT
            .toFormatter();
    }

    /**
     * Constructor.
     *
     * @param printerParser  the printer/parser to use, not null
     * @param locale  the locale to use, not null
     * @param symbols  the symbols to use, not null
     * @param chrono  the chronology to use, null for no override
     * @param zone  the zone to use, null for no override
     */
    DateTimeFormatter(CompositePrinterParser printerParser, Locale locale,
                      DateTimeFormatSymbols symbols, Chronology chrono, ZoneId zone) {
        this.printerParser = Objects.requireNonNull(printerParser, "printerParser");
        this.locale = Objects.requireNonNull(locale, "locale");
        this.symbols = Objects.requireNonNull(symbols, "symbols");
        this.chrono = chrono;
        this.zone = zone;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the locale to be used during formatting.
     * <p>
     * This is used to lookup any part of the formatter needing specific
     * localization, such as the text or localized pattern.
     *
     * @return the locale of this formatter, not null
     */
    public Locale getLocale() {
        return locale;
    }

    /**
     * Returns a copy of this formatter with a new locale.
     * <p>
     * This is used to lookup any part of the formatter needing specific
     * localization, such as the text or localized pattern.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param locale  the new locale, not null
     * @return a formatter based on this formatter with the requested locale, not null
     */
    public DateTimeFormatter withLocale(Locale locale) {
        if (this.locale.equals(locale)) {
            return this;
        }
        return new DateTimeFormatter(printerParser, locale, symbols, chrono, zone);
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the set of symbols to be used during formatting.
     *
     * @return the locale of this formatter, not null
     */
    public DateTimeFormatSymbols getSymbols() {
        return symbols;
    }

    /**
     * Returns a copy of this formatter with a new set of symbols.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param symbols  the new symbols, not null
     * @return a formatter based on this formatter with the requested symbols, not null
     */
    public DateTimeFormatter withSymbols(DateTimeFormatSymbols symbols) {
        if (this.symbols.equals(symbols)) {
            return this;
        }
        return new DateTimeFormatter(printerParser, locale, symbols, chrono, zone);
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the overriding chronology to be used during formatting.
     * <p>
     * This returns the override chronology, used to convert dates.
     * By default, a formatter has no override chronology, returning null.
     * See {@link #withChronology(Chronology)} for more details on overriding.
     *
     * @return the chronology of this formatter, null if no override
     */
    public Chronology getChronology() {
        return chrono;
    }

    /**
     * Returns a copy of this formatter with a new override chronology.
     * <p>
     * This returns a formatter with similar state to this formatter but
     * with the override chronology set.
     * By default, a formatter has no override chronology, returning null.
     * <p>
     * If an override is added, then any date that is formatted or parsed will be affected.
     * <p>
     * When formatting, if the {@code Temporal} object contains a date then it will
     * be converted to a date in the override chronology.
     * Any time or zone will be retained unless overridden.
     * The converted result will behave in a manner equivalent to an implementation
     * of {@code ChronoLocalDate},{@code ChronoLocalDateTime} or {@code ChronoZonedDateTime}.
     * <p>
     * When parsing, the override chronology will be used to interpret the
     * {@link java.time.temporal.ChronoField fields} into a date unless the
     * formatter directly parses a valid chronology.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param chrono  the new chronology, not null
     * @return a formatter based on this formatter with the requested override chronology, not null
     */
    public DateTimeFormatter withChronology(Chronology chrono) {
        if (Objects.equals(this.chrono, chrono)) {
            return this;
        }
        return new DateTimeFormatter(printerParser, locale, symbols, chrono, zone);
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the overriding zone to be used during formatting.
     * <p>
     * This returns the override zone, used to convert instants.
     * By default, a formatter has no override zone, returning null.
     * See {@link #withZone(ZoneId)} for more details on overriding.
     *
     * @return the chronology of this formatter, null if no override
     */
    public ZoneId getZone() {
        return zone;
    }

    /**
     * Returns a copy of this formatter with a new override zone.
     * <p>
     * This returns a formatter with similar state to this formatter but
     * with the override zone set.
     * By default, a formatter has no override zone, returning null.
     * <p>
     * If an override is added, then any instant that is formatted or parsed will be affected.
     * <p>
     * When formatting, if the {@code Temporal} object contains an instant then it will
     * be converted to a zoned date-time using the override zone.
     * If the input has a chronology then it will be retained unless overridden.
     * If the input does not have a chronology, such as {@code Instant}, then
     * the ISO chronology will be used.
     * The converted result will behave in a manner equivalent to an implementation
     * of {@code ChronoZonedDateTime}.
     * <p>
     * When parsing, the override zone will be used to interpret the
     * {@link java.time.temporal.ChronoField fields} into an instant unless the
     * formatter directly parses a valid zone.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param zone  the new override zone, not null
     * @return a formatter based on this formatter with the requested override zone, not null
     */
    public DateTimeFormatter withZone(ZoneId zone) {
        if (Objects.equals(this.zone, zone)) {
            return this;
        }
        return new DateTimeFormatter(printerParser, locale, symbols, chrono, zone);
    }

    //-----------------------------------------------------------------------
    /**
     * Formats a date-time object using this formatter.
     * <p>
     * This formats the date-time to a String using the rules of the formatter.
     *
     * @param temporal  the temporal object to format, not null
     * @return the formatted string, not null
     * @throws DateTimeException if an error occurs during formatting
     */
    public String format(TemporalAccessor temporal) {
        StringBuilder buf = new StringBuilder(32);
        formatTo(temporal, buf);
        return buf.toString();
    }

    //-----------------------------------------------------------------------
    /**
     * Formats a date-time object to an {@code Appendable} using this formatter.
     * <p>
     * This outputs the formatted date-time to the specified destination.
     * {@link Appendable} is a general purpose interface that is implemented by all
     * key character output classes including {@code StringBuffer}, {@code StringBuilder},
     * {@code PrintStream} and {@code Writer}.
     * <p>
     * Although {@code Appendable} methods throw an {@code IOException}, this method does not.
     * Instead, any {@code IOException} is wrapped in a runtime exception.
     *
     * @param temporal  the temporal object to format, not null
     * @param appendable  the appendable to format to, not null
     * @throws DateTimeException if an error occurs during formatting
     */
    public void formatTo(TemporalAccessor temporal, Appendable appendable) {
        Objects.requireNonNull(temporal, "temporal");
        Objects.requireNonNull(appendable, "appendable");
        try {
            DateTimePrintContext context = new DateTimePrintContext(temporal, this);
            if (appendable instanceof StringBuilder) {
                printerParser.format(context, (StringBuilder) appendable);
            } else {
                // buffer output to avoid writing to appendable in case of error
                StringBuilder buf = new StringBuilder(32);
                printerParser.format(context, buf);
                appendable.append(buf);
            }
        } catch (IOException ex) {
            throw new DateTimeException(ex.getMessage(), ex);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Fully parses the text producing a temporal object.
     * <p>
     * This parses the entire text producing a temporal object.
     * It is typically more useful to use {@link #parse(CharSequence, TemporalQuery)}.
     * The result of this method is {@code TemporalAccessor} which has been resolved,
     * applying basic validation checks to help ensure a valid date-time.
     * <p>
     * If the parse completes without reading the entire length of the text,
     * or a problem occurs during parsing or merging, then an exception is thrown.
     *
     * @param text  the text to parse, not null
     * @return the parsed temporal object, not null
     * @throws DateTimeParseException if unable to parse the requested result
     */
    public TemporalAccessor parse(CharSequence text) {
        Objects.requireNonNull(text, "text");
        try {
            return parseToBuilder(text, null).resolve();
        } catch (DateTimeParseException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw createError(text, ex);
        }
    }

    /**
     * Parses the text using this formatter, providing control over the text position.
     * <p>
     * This parses the text without requiring the parse to start from the beginning
     * of the string or finish at the end.
     * The result of this method is {@code TemporalAccessor} which has been resolved,
     * applying basic validation checks to help ensure a valid date-time.
     * <p>
     * The text will be parsed from the specified start {@code ParsePosition}.
     * The entire length of the text does not have to be parsed, the {@code ParsePosition}
     * will be updated with the index at the end of parsing.
     * <p>
     * The operation of this method is slightly different to similar methods using
     * {@code ParsePosition} on {@code java.text.Format}. That class will return
     * errors using the error index on the {@code ParsePosition}. By contrast, this
     * method will throw a {@link DateTimeParseException} if an error occurs, with
     * the exception containing the error index.
     * This change in behavior is necessary due to the increased complexity of
     * parsing and resolving dates/times in this API.
     * <p>
     * If the formatter parses the same field more than once with different values,
     * the result will be an error.
     *
     * @param text  the text to parse, not null
     * @param position  the position to parse from, updated with length parsed
     *  and the index of any error, not null
     * @return the parsed temporal object, not null
     * @throws DateTimeParseException if unable to parse the requested result
     * @throws IndexOutOfBoundsException if the position is invalid
     */
    public TemporalAccessor parse(CharSequence text, ParsePosition position) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        try {
            return parseToBuilder(text, position).resolve();
        } catch (DateTimeParseException | IndexOutOfBoundsException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw createError(text, ex);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Fully parses the text producing an object of the specified type.
     * <p>
     * Most applications should use this method for parsing.
     * It parses the entire text to produce the required date-time.
     * The query is typically a method reference to a {@code from(TemporalAccessor)} method.
     * For example:
     * <pre>
     *  LocalDateTime dt = parser.parse(str, LocalDateTime::from);
     * </pre>
     * If the parse completes without reading the entire length of the text,
     * or a problem occurs during parsing or merging, then an exception is thrown.
     *
     * @param <T> the type of the parsed date-time
     * @param text  the text to parse, not null
     * @param query  the query defining the type to parse to, not null
     * @return the parsed date-time, not null
     * @throws DateTimeParseException if unable to parse the requested result
     */
    public <T> T parse(CharSequence text, TemporalQuery<T> query) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(query, "query");
        try {
            DateTimeBuilder builder = parseToBuilder(text, null).resolve();
            return builder.query(query);
        } catch (DateTimeParseException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw createError(text, ex);
        }
    }

    /**
     * Fully parses the text producing an object of one of the specified types.
     * <p>
     * This parse method is convenient for use when the parser can handle optional elements.
     * For example, a pattern of 'yyyy-MM-dd HH.mm[Z]]' can be fully parsed to a {@code ZonedDateTime},
     * or partially parsed to a {@code LocalDateTime}.
     * The queries must be specified in order, starting from the best matching full-parse option
     * and ending with the worst matching minimal parse option.
     * The query is typically a method reference to a {@code from(TemporalAccessor)} method.
     * <p>
     * The result is associated with the first type that successfully parses.
     * Normally, applications will use {@code instanceof} to check the result.
     * For example:
     * <pre>
     *  TemporalAccessor dt = parser.parseBest(str, ZonedDateTime::from, LocalDateTime::from);
     *  if (dt instanceof ZonedDateTime) {
     *   ...
     *  } else {
     *   ...
     *  }
     * </pre>
     * If the parse completes without reading the entire length of the text,
     * or a problem occurs during parsing or merging, then an exception is thrown.
     *
     * @param text  the text to parse, not null
     * @param queries  the queries defining the types to attempt to parse to,
     *  must implement {@code TemporalAccessor}, not null
     * @return the parsed date-time, not null
     * @throws IllegalArgumentException if less than 2 types are specified
     * @throws DateTimeParseException if unable to parse the requested result
     */
    public TemporalAccessor parseBest(CharSequence text, TemporalQuery<?>... queries) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(queries, "queries");
        if (queries.length < 2) {
            throw new IllegalArgumentException("At least two queries must be specified");
        }
        try {
            DateTimeBuilder builder = parseToBuilder(text, null).resolve();
            for (TemporalQuery<?> query : queries) {
                try {
                    return (TemporalAccessor) builder.query(query);
                } catch (RuntimeException ex) {
                    // continue
                }
            }
            throw new DateTimeException("Unable to convert parsed text using any of the specified queries");
        } catch (DateTimeParseException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw createError(text, ex);
        }
    }

    private DateTimeParseException createError(CharSequence text, RuntimeException ex) {
        String abbr = "";
        if (text.length() > 64) {
            abbr = text.subSequence(0, 64).toString() + "...";
        } else {
            abbr = text.toString();
        }
        return new DateTimeParseException("Text '" + abbr + "' could not be parsed: " + ex.getMessage(), text, 0, ex);
    }

    //-----------------------------------------------------------------------
    /**
     * Parses the text to a builder.
     * <p>
     * This parses to a {@code DateTimeBuilder} ensuring that the text is fully parsed.
     * This method throws {@link DateTimeParseException} if unable to parse, or
     * some other {@code DateTimeException} if another date/time problem occurs.
     *
     * @param text  the text to parse, not null
     * @param position  the position to parse from, updated with length parsed
     *  and the index of any error, null if parsing whole string
     * @return the engine representing the result of the parse, not null
     * @throws DateTimeParseException if the parse fails
     */
    private DateTimeBuilder parseToBuilder(final CharSequence text, final ParsePosition position) {
        ParsePosition pos = (position != null ? position : new ParsePosition(0));
        DateTimeParseContext result = parseUnresolved0(text, pos);
        if (result == null || pos.getErrorIndex() >= 0 || (position == null && pos.getIndex() < text.length())) {
            String abbr = "";
            if (text.length() > 64) {
                abbr = text.subSequence(0, 64).toString() + "...";
            } else {
                abbr = text.toString();
            }
            if (pos.getErrorIndex() >= 0) {
                throw new DateTimeParseException("Text '" + abbr + "' could not be parsed at index " +
                        pos.getErrorIndex(), text, pos.getErrorIndex());
            } else {
                throw new DateTimeParseException("Text '" + abbr + "' could not be parsed, unparsed text found at index " +
                        pos.getIndex(), text, pos.getIndex());
            }
        }
        return result.resolveFields().toBuilder();
    }

    /**
     * Parses the text using this formatter, without resolving the result, intended
     * for advanced use cases.
     * <p>
     * Parsing is implemented as a two-phase operation.
     * First, the text is parsed using the layout defined by the formatter, producing
     * a {@code Map} of field to value, a {@code ZoneId} and a {@code Chronology}.
     * Second, the parsed data is <em>resolved</em>, by validating, combining and
     * simplifying the various fields into more useful ones.
     * This method performs the parsing stage but not the resolving stage.
     * <p>
     * The result of this method is {@code TemporalAccessor} which represents the
     * data as seen in the input. Values are not validated, thus parsing a date string
     * of '2012-00-65' would result in a temporal with three fields - year of '2012',
     * month of '0' and day-of-month of '65'.
     * <p>
     * The text will be parsed from the specified start {@code ParsePosition}.
     * The entire length of the text does not have to be parsed, the {@code ParsePosition}
     * will be updated with the index at the end of parsing.
     * <p>
     * Errors are returned using the error index field of the {@code ParsePosition}
     * instead of {@code DateTimeParseException}.
     * The returned error index will be set to an index indicative of the error.
     * Callers must check for errors before using the context.
     * <p>
     * If the formatter parses the same field more than once with different values,
     * the result will be an error.
     * <p>
     * This method is intended for advanced use cases that need access to the
     * internal state during parsing. Typical application code should use
     * {@link #parse(CharSequence, TemporalQuery)} or the parse method on the target type.
     *
     * @param text  the text to parse, not null
     * @param position  the position to parse from, updated with length parsed
     *  and the index of any error, not null
     * @return the parsed text, null if the parse results in an error
     * @throws DateTimeException if some problem occurs during parsing
     * @throws IndexOutOfBoundsException if the position is invalid
     */
    public TemporalAccessor parseUnresolved(CharSequence text, ParsePosition position) {
        return parseUnresolved0(text, position);
    }

    private DateTimeParseContext parseUnresolved0(CharSequence text, ParsePosition position) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        DateTimeParseContext context = new DateTimeParseContext(this);
        int pos = position.getIndex();
        pos = printerParser.parse(context, text, pos);
        if (pos < 0) {
            position.setErrorIndex(~pos);  // index not updated from input
            return null;
        }
        position.setIndex(pos);  // errorIndex not updated from input
        return context;
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the formatter as a composite printer parser.
     *
     * @param optional  whether the printer/parser should be optional
     * @return the printer/parser, not null
     */
    CompositePrinterParser toPrinterParser(boolean optional) {
        return printerParser.withOptional(optional);
    }

    /**
     * Returns this formatter as a {@code java.text.Format} instance.
     * <p>
     * The returned {@link Format} instance will format any {@link TemporalAccessor}
     * and parses to a resolved {@link TemporalAccessor}.
     * <p>
     * Exceptions will follow the definitions of {@code Format}, see those methods
     * for details about {@code IllegalArgumentException} during formatting and
     * {@code ParseException} or null during parsing.
     * The format does not support attributing of the returned format string.
     *
     * @return this formatter as a classic format instance, not null
     */
    public Format toFormat() {
        return new ClassicFormat(this, null);
    }

    /**
     * Returns this formatter as a {@code java.text.Format} instance that will
     * parse using the specified query.
     * <p>
     * The returned {@link Format} instance will format any {@link TemporalAccessor}
     * and parses to the type specified.
     * The type must be one that is supported by {@link #parse}.
     * <p>
     * Exceptions will follow the definitions of {@code Format}, see those methods
     * for details about {@code IllegalArgumentException} during formatting and
     * {@code ParseException} or null during parsing.
     * The format does not support attributing of the returned format string.
     *
     * @param parseQuery  the query defining the type to parse to, not null
     * @return this formatter as a classic format instance, not null
     */
    public Format toFormat(TemporalQuery<?> parseQuery) {
        Objects.requireNonNull(parseQuery, "parseQuery");
        return new ClassicFormat(this, parseQuery);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a description of the underlying formatters.
     *
     * @return a description of this formatter, not null
     */
    @Override
    public String toString() {
        String pattern = printerParser.toString();
        pattern = pattern.startsWith("[") ? pattern : pattern.substring(1, pattern.length() - 1);
        return pattern;
        // TODO: Fix tests to not depend on toString()
//        return "DateTimeFormatter[" + locale +
//                (chrono != null ? "," + chrono : "") +
//                (zone != null ? "," + zone : "") +
//                pattern + "]";
    }

    //-----------------------------------------------------------------------
    /**
     * Implements the classic Java Format API.
     * @serial exclude
     */
    @SuppressWarnings("serial")  // not actually serializable
    static class ClassicFormat extends Format {
        /** The formatter. */
        private final DateTimeFormatter formatter;
        /** The type to be parsed. */
        private final TemporalQuery<?> parseType;
        /** Constructor. */
        public ClassicFormat(DateTimeFormatter formatter, TemporalQuery<?> parseType) {
            this.formatter = formatter;
            this.parseType = parseType;
        }

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            Objects.requireNonNull(obj, "obj");
            Objects.requireNonNull(toAppendTo, "toAppendTo");
            Objects.requireNonNull(pos, "pos");
            if (obj instanceof TemporalAccessor == false) {
                throw new IllegalArgumentException("Format target must implement TemporalAccessor");
            }
            pos.setBeginIndex(0);
            pos.setEndIndex(0);
            try {
                formatter.formatTo((TemporalAccessor) obj, toAppendTo);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(ex.getMessage(), ex);
            }
            return toAppendTo;
        }
        @Override
        public Object parseObject(String text) throws ParseException {
            Objects.requireNonNull(text, "text");
            try {
                if (parseType == null) {
                    return formatter.parseToBuilder(text, null).resolve();
                }
                return formatter.parse(text, parseType);
            } catch (DateTimeParseException ex) {
                throw new ParseException(ex.getMessage(), ex.getErrorIndex());
            } catch (RuntimeException ex) {
                throw (ParseException) new ParseException(ex.getMessage(), 0).initCause(ex);
            }
        }
        @Override
        public Object parseObject(String text, ParsePosition pos) {
            Objects.requireNonNull(text, "text");
            DateTimeParseContext unresolved;
            try {
                unresolved = formatter.parseUnresolved0(text, pos);
            } catch (IndexOutOfBoundsException ex) {
                if (pos.getErrorIndex() < 0) {
                    pos.setErrorIndex(0);
                }
                return null;
            }
            if (unresolved == null) {
                if (pos.getErrorIndex() < 0) {
                    pos.setErrorIndex(0);
                }
                return null;
            }
            try {
                DateTimeBuilder builder = unresolved.resolveFields().toBuilder().resolve();
                if (parseType == null) {
                    return builder;
                }
                return builder.query(parseType);
            } catch (RuntimeException ex) {
                pos.setErrorIndex(0);
                return null;
            }
        }
    }

}
