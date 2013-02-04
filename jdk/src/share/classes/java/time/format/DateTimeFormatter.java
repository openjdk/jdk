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

import java.io.IOException;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParseException;
import java.text.ParsePosition;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatterBuilder.CompositePrinterParser;
import java.time.temporal.Chrono;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalQuery;
import java.util.Locale;
import java.util.Objects;

/**
 * Formatter for printing and parsing date-time objects.
 * <p>
 * This class provides the main application entry point for printing and parsing.
 * Common instances of {@code DateTimeFormatter} are provided by {@link DateTimeFormatters}.
 * For more complex formatters, a {@link DateTimeFormatterBuilder builder} is provided.
 * <p>
 * In most cases, it is not necessary to use this class directly when formatting.
 * The main date-time classes provide two methods - one for printing,
 * {@code toString(DateTimeFormatter formatter)}, and one for parsing,
 * {@code parse(CharSequence text, DateTimeFormatter formatter)}.
 * For example:
 * <pre>
 *  String text = date.toString(formatter);
 *  LocalDate date = LocalDate.parse(text, formatter);
 * </pre>
 * Some aspects of printing and parsing are dependent on the locale.
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
    private final Chrono<?> chrono;
    /**
     * The zone to use for formatting, null for no override.
     */
    private final ZoneId zone;

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
                      DateTimeFormatSymbols symbols, Chrono chrono, ZoneId zone) {
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
     * See {@link #withChrono(Chrono)} for more details on overriding.
     *
     * @return the chronology of this formatter, null if no override
     */
    public Chrono<?> getChrono() {
        return chrono;
    }

    /**
     * Returns a copy of this formatter with a new override chronology.
     * <p>
     * This returns a formatter with similar state to this formatter but
     * with the override chronology set.
     * By default, a formatter has no override chronology, returning null.
     * <p>
     * If an override is added, then any date that is printed or parsed will be affected.
     * <p>
     * When printing, if the {@code Temporal} object contains a date then it will
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
    public DateTimeFormatter withChrono(Chrono chrono) {
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
     * If an override is added, then any instant that is printed or parsed will be affected.
     * <p>
     * When printing, if the {@code Temporal} object contains an instant then it will
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
     * Prints a date-time object using this formatter.
     * <p>
     * This prints the date-time to a String using the rules of the formatter.
     *
     * @param temporal  the temporal object to print, not null
     * @return the printed string, not null
     * @throws DateTimeException if an error occurs during printing
     */
    public String print(TemporalAccessor temporal) {
        StringBuilder buf = new StringBuilder(32);
        printTo(temporal, buf);
        return buf.toString();
    }

    //-----------------------------------------------------------------------
    /**
     * Prints a date-time object to an {@code Appendable} using this formatter.
     * <p>
     * This prints the date-time to the specified destination.
     * {@link Appendable} is a general purpose interface that is implemented by all
     * key character output classes including {@code StringBuffer}, {@code StringBuilder},
     * {@code PrintStream} and {@code Writer}.
     * <p>
     * Although {@code Appendable} methods throw an {@code IOException}, this method does not.
     * Instead, any {@code IOException} is wrapped in a runtime exception.
     * See {@link DateTimePrintException#rethrowIOException()} for a means
     * to extract the {@code IOException}.
     *
     * @param temporal  the temporal object to print, not null
     * @param appendable  the appendable to print to, not null
     * @throws DateTimeException if an error occurs during printing
     */
    public void printTo(TemporalAccessor temporal, Appendable appendable) {
        Objects.requireNonNull(temporal, "temporal");
        Objects.requireNonNull(appendable, "appendable");
        try {
            DateTimePrintContext context = new DateTimePrintContext(temporal, this);
            if (appendable instanceof StringBuilder) {
                printerParser.print(context, (StringBuilder) appendable);
            } else {
                // buffer output to avoid writing to appendable in case of error
                StringBuilder buf = new StringBuilder(32);
                printerParser.print(context, buf);
                appendable.append(buf);
            }
        } catch (IOException ex) {
            throw new DateTimePrintException(ex.getMessage(), ex);
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
     * @throws DateTimeParseException if the parse fails
     */
    public <T> T parse(CharSequence text, TemporalQuery<T> query) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(query, "query");
        String str = text.toString();  // parsing whole String, so this makes sense
        try {
            DateTimeBuilder builder = parseToBuilder(str).resolve();
            return builder.query(query);
        } catch (DateTimeParseException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw createError(str, ex);
        }
    }

    /**
     * Fully parses the text producing an object of one of the specified types.
     * <p>
     * This parse method is convenient for use when the parser can handle optional elements.
     * For example, a pattern of 'yyyy-MM[-dd[Z]]' can be fully parsed to an {@code OffsetDate},
     * or partially parsed to a {@code LocalDate} or a {@code YearMonth}.
     * The queries must be specified in order, starting from the best matching full-parse option
     * and ending with the worst matching minimal parse option.
     * The query is typically a method reference to a {@code from(TemporalAccessor)} method.
     * <p>
     * The result is associated with the first type that successfully parses.
     * Normally, applications will use {@code instanceof} to check the result.
     * For example:
     * <pre>
     *  TemporalAccessor dt = parser.parseBest(str, OffsetDate::from, LocalDate::from);
     *  if (dt instanceof OffsetDate) {
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
     * @throws DateTimeException if none of the queries can be parsed from the input
     * @throws DateTimeParseException if the parse fails
     */
    public TemporalAccessor parseBest(CharSequence text, TemporalQuery<?>... queries) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(queries, "queries");
        if (queries.length < 2) {
            throw new IllegalArgumentException("At least two queries must be specified");
        }
        String str = text.toString();  // parsing whole String, so this makes sense
        try {
            DateTimeBuilder builder = parseToBuilder(str).resolve();
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
            throw createError(str, ex);
        }
    }

    private DateTimeParseException createError(String str, RuntimeException ex) {
        String abbr = str;
        if (abbr.length() > 64) {
            abbr = abbr.substring(0, 64) + "...";
        }
        return new DateTimeParseException("Text '" + abbr + "' could not be parsed: " + ex.getMessage(), str, 0, ex);
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
     * @return the engine representing the result of the parse, not null
     * @throws DateTimeParseException if the parse fails
     */
    public DateTimeBuilder parseToBuilder(CharSequence text) {
        Objects.requireNonNull(text, "text");
        String str = text.toString();  // parsing whole String, so this makes sense
        ParsePosition pos = new ParsePosition(0);
        DateTimeBuilder result = parseToBuilder(str, pos);
        if (result == null || pos.getErrorIndex() >= 0 || pos.getIndex() < str.length()) {
            String abbr = str;
            if (abbr.length() > 64) {
                abbr = abbr.substring(0, 64) + "...";
            }
            if (pos.getErrorIndex() >= 0) {
                throw new DateTimeParseException("Text '" + abbr + "' could not be parsed at index " +
                        pos.getErrorIndex(), str, pos.getErrorIndex());
            } else {
                throw new DateTimeParseException("Text '" + abbr + "' could not be parsed, unparsed text found at index " +
                        pos.getIndex(), str, pos.getIndex());
            }
        }
        return result;
    }

    /**
     * Parses the text to a builder.
     * <p>
     * This parses to a {@code DateTimeBuilder} but does not require the input to be fully parsed.
     * <p>
     * This method does not throw {@link DateTimeParseException}.
     * Instead, errors are returned within the state of the specified parse position.
     * Callers must check for errors before using the context.
     * <p>
     * This method may throw some other {@code DateTimeException} if a date/time problem occurs.
     *
     * @param text  the text to parse, not null
     * @param position  the position to parse from, updated with length parsed
     *  and the index of any error, not null
     * @return the parsed text, null only if the parse results in an error
     * @throws DateTimeException if some problem occurs during parsing
     * @throws IndexOutOfBoundsException if the position is invalid
     */
    public DateTimeBuilder parseToBuilder(CharSequence text, ParsePosition position) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(position, "position");
        DateTimeParseContext context = new DateTimeParseContext(this);
        int pos = position.getIndex();
        pos = printerParser.parse(context, text, pos);
        if (pos < 0) {
            position.setErrorIndex(~pos);
            return null;
        }
        position.setIndex(pos);
        return context.toBuilder();
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
     * The returned {@link Format} instance will print any {@link java.time.temporal.TemporalAccessor}
     * and parses to a resolved {@link DateTimeBuilder}.
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
     * The returned {@link Format} instance will print any {@link java.time.temporal.TemporalAccessor}
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
                formatter.printTo((TemporalAccessor) obj, toAppendTo);
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException(ex.getMessage(), ex);
            }
            return toAppendTo;
        }
        @Override
        public Object parseObject(String text) throws ParseException {
            Objects.requireNonNull(text, "text");
            try {
                if (parseType != null) {
                    return formatter.parse(text, parseType);
                }
                return formatter.parseToBuilder(text);
            } catch (DateTimeParseException ex) {
                throw new ParseException(ex.getMessage(), ex.getErrorIndex());
            } catch (RuntimeException ex) {
                throw (ParseException) new ParseException(ex.getMessage(), 0).initCause(ex);
            }
        }
        @Override
        public Object parseObject(String text, ParsePosition pos) {
            Objects.requireNonNull(text, "text");
            DateTimeBuilder builder;
            try {
                builder = formatter.parseToBuilder(text, pos);
            } catch (IndexOutOfBoundsException ex) {
                if (pos.getErrorIndex() < 0) {
                    pos.setErrorIndex(0);
                }
                return null;
            }
            if (builder == null) {
                if (pos.getErrorIndex() < 0) {
                    pos.setErrorIndex(0);
                }
                return null;
            }
            if (parseType == null) {
                return builder;
            }
            try {
                return builder.resolve().query(parseType);
            } catch (RuntimeException ex) {
                pos.setErrorIndex(0);
                return null;
            }
        }
    }

}
