/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.parser;

import static java.lang.Character.DECIMAL_DIGIT_NUMBER;
import static java.lang.Character.LOWERCASE_LETTER;
import static java.lang.Character.OTHER_PUNCTUATION;
import static java.lang.Character.SPACE_SEPARATOR;
import static java.lang.Character.UPPERCASE_LETTER;

import java.util.HashMap;
import java.util.Locale;

/**
 * JavaScript date parser. This class first tries to parse a date string
 * according to the extended ISO 8601 format specified in ES5 15.9.1.15.
 * If that fails, it falls back to legacy mode in which it accepts a range
 * of different formats.
 *
 * <p>This class is neither thread-safe nor reusable. Calling the
 * <tt>parse()</tt> method more than once will yield undefined results.</p>
 */
public class DateParser {

    /** Constant for index position of parsed year value. */
    public final static int YEAR        = 0;
    /** Constant for index position of parsed month value. */
    public final static int MONTH       = 1;
    /** Constant for index position of parsed day value. */
    public final static int DAY         = 2;
    /** Constant for index position of parsed hour value. */
    public final static int HOUR        = 3;
    /** Constant for index position of parsed minute value. */
    public final static int MINUTE      = 4;
    /** Constant for index position of parsed second value. */
    public final static int SECOND      = 5;
    /** Constant for index position of parsed millisecond value. */
    public final static int MILLISECOND = 6;
    /** Constant for index position of parsed time zone offset value. */
    public final static int TIMEZONE    = 7;

    private enum Token {
        UNKNOWN, NUMBER, SEPARATOR, PARENTHESIS, NAME, SIGN, END
    }

    private final String string;
    private final int length;
    private final Integer[] fields;
    private int pos = 0;
    private Token token;
    private int tokenLength;
    private Name nameValue;
    private int numValue;
    private int currentField = YEAR;
    private int yearSign = 0;
    private boolean namedMonth = false;

    private final static HashMap<String,Name> names = new HashMap<>();

    static {
        addName("monday", Name.DAY_OF_WEEK, 0);
        addName("tuesday", Name.DAY_OF_WEEK, 0);
        addName("wednesday", Name.DAY_OF_WEEK, 0);
        addName("thursday", Name.DAY_OF_WEEK, 0);
        addName("friday", Name.DAY_OF_WEEK, 0);
        addName("saturday", Name.DAY_OF_WEEK, 0);
        addName("sunday", Name.DAY_OF_WEEK, 0);
        addName("january", Name.MONTH_NAME, 1);
        addName("february", Name.MONTH_NAME, 2);
        addName("march", Name.MONTH_NAME, 3);
        addName("april", Name.MONTH_NAME, 4);
        addName("may", Name.MONTH_NAME, 5);
        addName("june", Name.MONTH_NAME, 6);
        addName("july", Name.MONTH_NAME, 7);
        addName("august", Name.MONTH_NAME, 8);
        addName("september", Name.MONTH_NAME, 9);
        addName("october", Name.MONTH_NAME, 10);
        addName("november", Name.MONTH_NAME, 11);
        addName("december", Name.MONTH_NAME, 12);
        addName("am", Name.AM_PM, 0);
        addName("pm", Name.AM_PM, 12);
        addName("z", Name.TIMEZONE_ID, 0);
        addName("gmt", Name.TIMEZONE_ID, 0);
        addName("ut", Name.TIMEZONE_ID, 0);
        addName("utc", Name.TIMEZONE_ID, 0);
        addName("est", Name.TIMEZONE_ID, -5 * 60);
        addName("edt", Name.TIMEZONE_ID, -4 * 60);
        addName("cst", Name.TIMEZONE_ID, -6 * 60);
        addName("cdt", Name.TIMEZONE_ID, -5 * 60);
        addName("mst", Name.TIMEZONE_ID, -7 * 60);
        addName("mdt", Name.TIMEZONE_ID, -6 * 60);
        addName("pst", Name.TIMEZONE_ID, -8 * 60);
        addName("pdt", Name.TIMEZONE_ID, -7 * 60);
        addName("t", Name.TIME_SEPARATOR, 0);
    }

    /**
     * Construct a new <tt>DateParser</tt> instance for parsing the given string.
     * @param string the string to be parsed
     */
    public DateParser(final String string) {
        this.string = string;
        this.length = string.length();
        this.fields = new Integer[TIMEZONE + 1];
    }

    /**
     * Try parsing the given string as date according to the extended ISO 8601 format
     * specified in ES5 15.9.1.15. Fall back to legacy mode if that fails.
     * This method returns <tt>true</tt> if the string could be parsed.
     * @return true if the string could be parsed as date
     */
    public boolean parse() {
        return parseEcmaDate() || parseLegacyDate();
    }

    /**
     * Try parsing the date string according to the rules laid out in ES5 15.9.1.15.
     * The date string must conform to the following format:
     *
     * <pre>  [('-'|'+')yy]yyyy[-MM[-dd]][hh:mm[:ss[.sss]][Z|(+|-)hh:mm]] </pre>
     *
     * <p>If the string does not contain a time zone offset, the <tt>TIMEZONE</tt> field
     * is set to <tt>0</tt> (GMT).</p>
     * @return true if string represents a valid ES5 date string.
     */
    public boolean parseEcmaDate() {

        if (token == null) {
            token = next();
        }

        while (token != Token.END) {

            switch (token) {
                case NUMBER:
                    if (currentField == YEAR && yearSign != 0) {
                        // 15.9.1.15.1 Extended year must have six digits
                        if (tokenLength != 6) {
                            return false;
                        }
                        numValue *= yearSign;
                    } else if (!checkEcmaField(currentField, numValue)) {
                        return false;
                    }
                    if (!skipEcmaDelimiter()) {
                        return false;
                    }
                    if (currentField < TIMEZONE) {
                        set(currentField++, numValue);
                    }
                    break;

                case NAME:
                    if (nameValue == null) {
                        return false;
                    }
                    switch (nameValue.type) {
                        case Name.TIME_SEPARATOR:
                            if (currentField == YEAR || currentField > HOUR) {
                                return false;
                            }
                            currentField = HOUR;
                            break;
                        case Name.TIMEZONE_ID:
                            if (!nameValue.key.equals("z") || !setTimezone(nameValue.value, false)) {
                                return false;
                            }
                            break;
                        default:
                            return false;
                    }
                    break;

                case SIGN:
                    if (peek() == -1) {
                        // END after sign - wrong!
                        return false;
                    }

                    if (currentField == YEAR) {
                        yearSign = numValue;
                    } else if (currentField < SECOND || !setTimezone(readTimeZoneOffset(), true)) {
                        // Note: Spidermonkey won't parse timezone unless time includes seconds and milliseconds
                        return false;
                    }
                    break;

                default:
                    return false;
            }
            token = next();
        }

        return patchResult(true);
    }

    /**
     * Try parsing the date using a fuzzy algorithm that can handle a variety of formats.
     *
     * <p>Numbers separated by <tt>':'</tt> are treated as time values, optionally followed by a
     * millisecond value separated by <tt>'.'</tt>. Other number values are treated as date values.
     * The exact sequence of day, month, and year values to apply is determined heuristically.</p>
     *
     * <p>English month names and selected time zone names as well as AM/PM markers are recognized
     * and handled properly. Additionally, numeric time zone offsets such as <tt>(+|-)hh:mm</tt> or
     * <tt>(+|-)hhmm</tt> are recognized. If the string does not contain a time zone offset
     * the <tt>TIMEZONE</tt>field is left undefined, meaning the local time zone should be applied.</p>
     *
     * <p>English weekday names are recognized but ignored. All text in parentheses is ignored as well.
     * All other text causes parsing to fail.</p>
     *
     * @return true if the string could be parsed
     */
    public boolean parseLegacyDate() {

        if (yearSign != 0 || currentField > DAY) {
            // we don't support signed years in legacy mode
            return false;
        }
        if (token == null) {
            token = next();
        }

        while (token != Token.END) {

            switch (token) {
                case NUMBER:
                    if (skip(':')) {
                        // A number followed by ':' is parsed as time
                        if (!setTimeField(numValue)) {
                            return false;
                        }
                        // consume remaining time tokens
                        do {
                            token = next();
                            if (token != Token.NUMBER || !setTimeField(numValue)) {
                                return false;
                            }
                        } while (skip(isSet(SECOND) ? '.' : ':'));

                    } else {
                        // Parse as date token
                        if (!setDateField(numValue)) {
                            return false;
                        }
                        skip('-');
                    }
                    break;

                case NAME:
                    if (nameValue == null) {
                        return false;
                    }
                    switch (nameValue.type) {
                        case Name.AM_PM:
                            if (!setAmPm(nameValue.value)) {
                                return false;
                            }
                            break;
                        case Name.MONTH_NAME:
                            if (!setMonth(nameValue.value)) {
                                return false;
                            }
                            break;
                        case Name.TIMEZONE_ID:
                            if (!setTimezone(nameValue.value, false)) {
                                return false;
                            }
                            break;
                        case Name.TIME_SEPARATOR:
                            return false;
                        default:
                            break;
                    }
                    if (nameValue.type != Name.TIMEZONE_ID) {
                        skip('-');
                    }
                    break;

                case SIGN:
                    if (peek() == -1) {
                        // END after sign - wrong!
                        return false;
                    }

                    if (!setTimezone(readTimeZoneOffset(), true)) {
                        return false;
                    }
                    break;

                case PARENTHESIS:
                    if (!skipParentheses()) {
                        return false;
                    }
                    break;

                case SEPARATOR:
                    break;

                default:
                    return false;
            }
            token = next();
        }

        return patchResult(false);
    }

    /**
     * Get the parsed date and time fields as an array of <tt>Integers</tt>.
     *
     * <p>If parsing was successful, all fields are guaranteed to be set except for the
     * <tt>TIMEZONE</tt> field which may be <tt>null</tt>, meaning that local time zone
     * offset should be applied.</p>
     *
     * @return the parsed date fields
     */
    public Integer[] getDateFields() {
        return fields;
    }

    private boolean isSet(final int field) {
        return fields[field] != null;
    }

    private Integer get(final int field) {
        return fields[field];
    }

    private void set(final int field, final int value) {
        fields[field] = value;
    }

    private int peek() {
        return pos < length ? string.charAt(pos) : -1;
    }

    private boolean skip(final char c) {
        if (pos < length && string.charAt(pos) == c) {
            token = null;
            pos++;
            return true;
        }
        return false;
    }

    private Token next() {
        if (pos >= length) {
            tokenLength = 0;
            return Token.END;
        }

        final char c = string.charAt(pos);

        if (c > 0x80) {
            tokenLength = 1;
            pos++;
            return Token.UNKNOWN; // We only deal with ASCII here
        }

        final int type = Character.getType(c);
        switch (type) {
            case DECIMAL_DIGIT_NUMBER:
                numValue = readNumber(6);
                return Token.NUMBER;
            case SPACE_SEPARATOR :
            case OTHER_PUNCTUATION:
                tokenLength = 1;
                pos++;
                return Token.SEPARATOR;
            case UPPERCASE_LETTER:
            case LOWERCASE_LETTER:
                nameValue = readName();
                return Token.NAME;
            default:
                tokenLength = 1;
                pos++;
                switch (c) {
                    case '(':
                        return Token.PARENTHESIS;
                    case '-':
                    case '+':
                        numValue = c == '-' ? -1 : 1;
                        return Token.SIGN;
                    default:
                        return Token.UNKNOWN;
                }
        }
    }

    private static boolean checkLegacyField(final int field, final int value) {
        switch (field) {
            case HOUR:
                return isHour(value);
            case MINUTE:
            case SECOND:
                return isMinuteOrSecond(value);
            case MILLISECOND:
                return isMillisecond(value);
            default:
                // skip validation on other legacy fields as we don't know what's what
                return true;
        }
    }

    private boolean checkEcmaField(final int field, final int value) {
        switch (field) {
            case YEAR:
                return tokenLength == 4;
            case MONTH:
                return tokenLength == 2 && isMonth(value);
            case DAY:
                return tokenLength == 2 && isDay(value);
            case HOUR:
                return tokenLength == 2 && isHour(value);
            case MINUTE:
            case SECOND:
                return tokenLength == 2 && isMinuteOrSecond(value);
            case MILLISECOND:
                // we allow millisecond to be less than 3 digits
                return tokenLength < 4 && isMillisecond(value);
            default:
                return true;
        }
    }

    private boolean skipEcmaDelimiter() {
        switch (currentField) {
            case YEAR:
            case MONTH:
                return skip('-') || peek() == 'T' || peek() == -1;
            case DAY:
                return peek() == 'T' || peek() == -1;
            case HOUR:
            case MINUTE:
                return skip(':') || endOfTime();
            case SECOND:
                return skip('.') || endOfTime();
            default:
                return true;
        }
    }

    private boolean endOfTime() {
        final int c = peek();
        return c == -1 || c == 'Z' || c == '-' || c == '+' || c == ' ';
    }

    private static boolean isAsciiLetter(final char ch) {
        return ('A' <= ch && ch <= 'Z') || ('a' <= ch && ch <= 'z');
    }

    private static boolean isAsciiDigit(final char ch) {
        return '0' <= ch && ch <= '9';
    }

    private int readNumber(final int maxDigits) {
        final int start = pos;
        int n = 0;
        final int max = Math.min(length, pos + maxDigits);
        while (pos < max && isAsciiDigit(string.charAt(pos))) {
            n = n * 10 + string.charAt(pos++) - '0';
        }
        tokenLength = pos - start;
        return n;
    }

    private Name readName() {
        final int start = pos;
        final int limit = Math.min(pos + 3, length);

        // first read up to the key length
        while (pos < limit && isAsciiLetter(string.charAt(pos))) {
            pos++;
        }
        final String key = string.substring(start, pos).toLowerCase(Locale.ENGLISH);
        final Name name = names.get(key);
        // then advance to end of name
        while (pos < length && isAsciiLetter(string.charAt(pos))) {
            pos++;
        }

        tokenLength = pos - start;
        // make sure we have the full name or a prefix
        if (name != null && name.matches(string, start, tokenLength)) {
            return name;
        }
        return null;
    }

    private int readTimeZoneOffset() {
        final int sign = string.charAt(pos - 1) == '+' ? 1 : -1;
        int offset = readNumber(2);
        skip(':');
        offset = offset * 60 + readNumber(2);
        return sign * offset;
    }

    private boolean skipParentheses() {
        int parenCount = 1;
        while (pos < length && parenCount != 0) {
            final char c = string.charAt(pos++);
            if (c == '(') {
                parenCount++;
            } else if (c == ')') {
                parenCount--;
            }
        }
        return true;
    }

    private static int getDefaultValue(final int field) {
        switch (field) {
            case MONTH:
            case DAY:
                return 1;
            default:
                return 0;
        }
    }

    private static boolean isDay(final int n) {
        return 1 <= n && n <= 31;
    }

    private static boolean isMonth(final int n) {
        return 1 <= n && n <= 12;
    }

    private static boolean isHour(final int n) {
        return 0 <= n && n <= 24;
    }

    private static boolean isMinuteOrSecond(final int n) {
        return 0 <= n && n < 60;
    }

    private static boolean isMillisecond(final int n) {
        return 0<= n && n < 1000;
    }

    private boolean setMonth(final int m) {
        if (!isSet(MONTH)) {
            namedMonth = true;
            set(MONTH, m);
            return true;
        }
        return false;
    }

    private boolean setDateField(final int n) {
        for (int field = YEAR; field != HOUR; field++) {
            if (!isSet(field)) {
                // no validation on legacy date fields
                set(field, n);
                return true;
            }
        }
        return false;
    }

    private boolean setTimeField(final int n) {
        for (int field = HOUR; field != TIMEZONE; field++) {
            if (!isSet(field)) {
                if (checkLegacyField(field, n)) {
                    set(field, n);
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    private boolean setTimezone(final int offset, final boolean asNumericOffset) {
        if (!isSet(TIMEZONE) || (asNumericOffset && get(TIMEZONE) == 0)) {
            set(TIMEZONE, offset);
            return true;
        }
        return false;
    }

    private boolean setAmPm(final int offset) {
        if (!isSet(HOUR)) {
            return false;
        }
        final int hour = get(HOUR);
        if (hour >= 0 && hour <= 12) {
            set(HOUR, hour + offset);
        }
        return true;
    }

    private boolean patchResult(final boolean strict) {
        // sanity checks - make sure we have something
        if (!isSet(YEAR) && !isSet(HOUR)) {
            return false;
        }
        if (isSet(HOUR) && !isSet(MINUTE)) {
            return false;
        }
        // fill in default values for unset fields except timezone
        for (int field = YEAR; field <= TIMEZONE; field++) {
            if (get(field) == null) {
                if (field == TIMEZONE && !strict) {
                    // We only use UTC as default timezone for dates parsed complying with
                    // the format specified in ES5 15.9.1.15. Otherwise the slot is left empty
                    // and local timezone is used.
                    continue;
                }
                final int value = getDefaultValue(field);
                set(field, value);
            }
        }

        if (!strict) {
            // swap year, month, and day if it looks like the right thing to do
            if (isDay(get(YEAR))) {
                final int d = get(YEAR);
                set(YEAR, get(DAY));
                if (namedMonth) {
                    // d-m-y
                    set(DAY, d);
                } else {
                    // m-d-y
                    final int d2 = get(MONTH);
                    set(MONTH, d);
                    set(DAY, d2);
                }
            }
            // sanity checks now that we know what's what
            if (!isMonth(get(MONTH)) || !isDay(get(DAY))) {
                return false;
            }

            // add 1900 or 2000 to year if it's between 0 and 100
            final int year = get(YEAR);
            if (year >= 0 && year < 100) {
                set(YEAR, year >= 50 ? 1900 + year : 2000 + year);
            }
        } else {
            // 24 hour value is only allowed if all other time values are zero
            if (get(HOUR) == 24 &&
                    (get(MINUTE) != 0 || get(SECOND) != 0 || get(MILLISECOND) != 0)) {
                return false;
            }
        }

        // set month to 0-based
        set(MONTH, get(MONTH) - 1);
        return true;
    }

    private static void addName(final String str, final int type, final int value) {
        final Name name = new Name(str, type, value);
        names.put(name.key, name);
    }

    private static class Name {
        final String name;
        final String key;
        final int value;
        final int type;

        final static int DAY_OF_WEEK    = -1;
        final static int MONTH_NAME     = 0;
        final static int AM_PM          = 1;
        final static int TIMEZONE_ID    = 2;
        final static int TIME_SEPARATOR = 3;

        Name(final String name, final int type, final int value) {
            assert name != null;
            assert name.equals(name.toLowerCase(Locale.ENGLISH));

            this.name = name;
            // use first three characters as lookup key
            this.key = name.substring(0, Math.min(3, name.length()));
            this.type = type;
            this.value = value;
        }

        public boolean matches(final String str, final int offset, final int len) {
            return name.regionMatches(true, 0, str, offset, len);
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
