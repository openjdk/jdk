/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * (C) Copyright Taligent, Inc. 1996 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996-1998 - All Rights Reserved
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
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import sun.util.calendar.CalendarUtils;
import sun.util.calendar.ZoneInfoFile;
import sun.util.resources.LocaleData;

/**
 * <code>SimpleDateFormat</code> is a concrete class for formatting and
 * parsing dates in a locale-sensitive manner. It allows for formatting
 * (date -> text), parsing (text -> date), and normalization.
 *
 * <p>
 * <code>SimpleDateFormat</code> allows you to start by choosing
 * any user-defined patterns for date-time formatting. However, you
 * are encouraged to create a date-time formatter with either
 * <code>getTimeInstance</code>, <code>getDateInstance</code>, or
 * <code>getDateTimeInstance</code> in <code>DateFormat</code>. Each
 * of these class methods can return a date/time formatter initialized
 * with a default format pattern. You may modify the format pattern
 * using the <code>applyPattern</code> methods as desired.
 * For more information on using these methods, see
 * {@link DateFormat}.
 *
 * <h4>Date and Time Patterns</h4>
 * <p>
 * Date and time formats are specified by <em>date and time pattern</em>
 * strings.
 * Within date and time pattern strings, unquoted letters from
 * <code>'A'</code> to <code>'Z'</code> and from <code>'a'</code> to
 * <code>'z'</code> are interpreted as pattern letters representing the
 * components of a date or time string.
 * Text can be quoted using single quotes (<code>'</code>) to avoid
 * interpretation.
 * <code>"''"</code> represents a single quote.
 * All other characters are not interpreted; they're simply copied into the
 * output string during formatting or matched against the input string
 * during parsing.
 * <p>
 * The following pattern letters are defined (all other characters from
 * <code>'A'</code> to <code>'Z'</code> and from <code>'a'</code> to
 * <code>'z'</code> are reserved):
 * <blockquote>
 * <table border=0 cellspacing=3 cellpadding=0 summary="Chart shows pattern letters, date/time component, presentation, and examples.">
 *     <tr bgcolor="#ccccff">
 *         <th align=left>Letter
 *         <th align=left>Date or Time Component
 *         <th align=left>Presentation
 *         <th align=left>Examples
 *     <tr>
 *         <td><code>G</code>
 *         <td>Era designator
 *         <td><a href="#text">Text</a>
 *         <td><code>AD</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>y</code>
 *         <td>Year
 *         <td><a href="#year">Year</a>
 *         <td><code>1996</code>; <code>96</code>
 *     <tr>
 *         <td><code>M</code>
 *         <td>Month in year
 *         <td><a href="#month">Month</a>
 *         <td><code>July</code>; <code>Jul</code>; <code>07</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>w</code>
 *         <td>Week in year
 *         <td><a href="#number">Number</a>
 *         <td><code>27</code>
 *     <tr>
 *         <td><code>W</code>
 *         <td>Week in month
 *         <td><a href="#number">Number</a>
 *         <td><code>2</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>D</code>
 *         <td>Day in year
 *         <td><a href="#number">Number</a>
 *         <td><code>189</code>
 *     <tr>
 *         <td><code>d</code>
 *         <td>Day in month
 *         <td><a href="#number">Number</a>
 *         <td><code>10</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>F</code>
 *         <td>Day of week in month
 *         <td><a href="#number">Number</a>
 *         <td><code>2</code>
 *     <tr>
 *         <td><code>E</code>
 *         <td>Day in week
 *         <td><a href="#text">Text</a>
 *         <td><code>Tuesday</code>; <code>Tue</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>a</code>
 *         <td>Am/pm marker
 *         <td><a href="#text">Text</a>
 *         <td><code>PM</code>
 *     <tr>
 *         <td><code>H</code>
 *         <td>Hour in day (0-23)
 *         <td><a href="#number">Number</a>
 *         <td><code>0</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>k</code>
 *         <td>Hour in day (1-24)
 *         <td><a href="#number">Number</a>
 *         <td><code>24</code>
 *     <tr>
 *         <td><code>K</code>
 *         <td>Hour in am/pm (0-11)
 *         <td><a href="#number">Number</a>
 *         <td><code>0</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>h</code>
 *         <td>Hour in am/pm (1-12)
 *         <td><a href="#number">Number</a>
 *         <td><code>12</code>
 *     <tr>
 *         <td><code>m</code>
 *         <td>Minute in hour
 *         <td><a href="#number">Number</a>
 *         <td><code>30</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>s</code>
 *         <td>Second in minute
 *         <td><a href="#number">Number</a>
 *         <td><code>55</code>
 *     <tr>
 *         <td><code>S</code>
 *         <td>Millisecond
 *         <td><a href="#number">Number</a>
 *         <td><code>978</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>z</code>
 *         <td>Time zone
 *         <td><a href="#timezone">General time zone</a>
 *         <td><code>Pacific Standard Time</code>; <code>PST</code>; <code>GMT-08:00</code>
 *     <tr>
 *         <td><code>Z</code>
 *         <td>Time zone
 *         <td><a href="#rfc822timezone">RFC 822 time zone</a>
 *         <td><code>-0800</code>
 * </table>
 * </blockquote>
 * Pattern letters are usually repeated, as their number determines the
 * exact presentation:
 * <ul>
 * <li><strong><a name="text">Text:</a></strong>
 *     For formatting, if the number of pattern letters is 4 or more,
 *     the full form is used; otherwise a short or abbreviated form
 *     is used if available.
 *     For parsing, both forms are accepted, independent of the number
 *     of pattern letters.
 * <li><strong><a name="number">Number:</a></strong>
 *     For formatting, the number of pattern letters is the minimum
 *     number of digits, and shorter numbers are zero-padded to this amount.
 *     For parsing, the number of pattern letters is ignored unless
 *     it's needed to separate two adjacent fields.
 * <li><strong><a name="year">Year:</a></strong>
 *     If the formatter's {@link #getCalendar() Calendar} is the Gregorian
 *     calendar, the following rules are applied.<br>
 *     <ul>
 *     <li>For formatting, if the number of pattern letters is 2, the year
 *         is truncated to 2 digits; otherwise it is interpreted as a
 *         <a href="#number">number</a>.
 *     <li>For parsing, if the number of pattern letters is more than 2,
 *         the year is interpreted literally, regardless of the number of
 *         digits. So using the pattern "MM/dd/yyyy", "01/11/12" parses to
 *         Jan 11, 12 A.D.
 *     <li>For parsing with the abbreviated year pattern ("y" or "yy"),
 *         <code>SimpleDateFormat</code> must interpret the abbreviated year
 *         relative to some century.  It does this by adjusting dates to be
 *         within 80 years before and 20 years after the time the <code>SimpleDateFormat</code>
 *         instance is created. For example, using a pattern of "MM/dd/yy" and a
 *         <code>SimpleDateFormat</code> instance created on Jan 1, 1997,  the string
 *         "01/11/12" would be interpreted as Jan 11, 2012 while the string "05/04/64"
 *         would be interpreted as May 4, 1964.
 *         During parsing, only strings consisting of exactly two digits, as defined by
 *         {@link Character#isDigit(char)}, will be parsed into the default century.
 *         Any other numeric string, such as a one digit string, a three or more digit
 *         string, or a two digit string that isn't all digits (for example, "-1"), is
 *         interpreted literally.  So "01/02/3" or "01/02/003" are parsed, using the
 *         same pattern, as Jan 2, 3 AD.  Likewise, "01/02/-3" is parsed as Jan 2, 4 BC.
 *     </ul>
 *     Otherwise, calendar system specific forms are applied.
 *     For both formatting and parsing, if the number of pattern
 *     letters is 4 or more, a calendar specific {@linkplain
 *     Calendar#LONG long form} is used. Otherwise, a calendar
 *     specific {@linkplain Calendar#SHORT short or abbreviated form}
 *     is used.
 * <li><strong><a name="month">Month:</a></strong>
 *     If the number of pattern letters is 3 or more, the month is
 *     interpreted as <a href="#text">text</a>; otherwise,
 *     it is interpreted as a <a href="#number">number</a>.
 * <li><strong><a name="timezone">General time zone:</a></strong>
 *     Time zones are interpreted as <a href="#text">text</a> if they have
 *     names. For time zones representing a GMT offset value, the
 *     following syntax is used:
 *     <pre>
 *     <a name="GMTOffsetTimeZone"><i>GMTOffsetTimeZone:</i></a>
 *             <code>GMT</code> <i>Sign</i> <i>Hours</i> <code>:</code> <i>Minutes</i>
 *     <i>Sign:</i> one of
 *             <code>+ -</code>
 *     <i>Hours:</i>
 *             <i>Digit</i>
 *             <i>Digit</i> <i>Digit</i>
 *     <i>Minutes:</i>
 *             <i>Digit</i> <i>Digit</i>
 *     <i>Digit:</i> one of
 *             <code>0 1 2 3 4 5 6 7 8 9</code></pre>
 *     <i>Hours</i> must be between 0 and 23, and <i>Minutes</i> must be between
 *     00 and 59. The format is locale independent and digits must be taken
 *     from the Basic Latin block of the Unicode standard.
 *     <p>For parsing, <a href="#rfc822timezone">RFC 822 time zones</a> are also
 *     accepted.
 * <li><strong><a name="rfc822timezone">RFC 822 time zone:</a></strong>
 *     For formatting, the RFC 822 4-digit time zone format is used:
 *     <pre>
 *     <i>RFC822TimeZone:</i>
 *             <i>Sign</i> <i>TwoDigitHours</i> <i>Minutes</i>
 *     <i>TwoDigitHours:</i>
 *             <i>Digit Digit</i></pre>
 *     <i>TwoDigitHours</i> must be between 00 and 23. Other definitions
 *     are as for <a href="#timezone">general time zones</a>.
 *     <p>For parsing, <a href="#timezone">general time zones</a> are also
 *     accepted.
 * </ul>
 * <code>SimpleDateFormat</code> also supports <em>localized date and time
 * pattern</em> strings. In these strings, the pattern letters described above
 * may be replaced with other, locale dependent, pattern letters.
 * <code>SimpleDateFormat</code> does not deal with the localization of text
 * other than the pattern letters; that's up to the client of the class.
 * <p>
 *
 * <h4>Examples</h4>
 *
 * The following examples show how date and time patterns are interpreted in
 * the U.S. locale. The given date and time are 2001-07-04 12:08:56 local time
 * in the U.S. Pacific Time time zone.
 * <blockquote>
 * <table border=0 cellspacing=3 cellpadding=0 summary="Examples of date and time patterns interpreted in the U.S. locale">
 *     <tr bgcolor="#ccccff">
 *         <th align=left>Date and Time Pattern
 *         <th align=left>Result
 *     <tr>
 *         <td><code>"yyyy.MM.dd G 'at' HH:mm:ss z"</code>
 *         <td><code>2001.07.04 AD at 12:08:56 PDT</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>"EEE, MMM d, ''yy"</code>
 *         <td><code>Wed, Jul 4, '01</code>
 *     <tr>
 *         <td><code>"h:mm a"</code>
 *         <td><code>12:08 PM</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>"hh 'o''clock' a, zzzz"</code>
 *         <td><code>12 o'clock PM, Pacific Daylight Time</code>
 *     <tr>
 *         <td><code>"K:mm a, z"</code>
 *         <td><code>0:08 PM, PDT</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>"yyyyy.MMMMM.dd GGG hh:mm aaa"</code>
 *         <td><code>02001.July.04 AD 12:08 PM</code>
 *     <tr>
 *         <td><code>"EEE, d MMM yyyy HH:mm:ss Z"</code>
 *         <td><code>Wed, 4 Jul 2001 12:08:56 -0700</code>
 *     <tr bgcolor="#eeeeff">
 *         <td><code>"yyMMddHHmmssZ"</code>
 *         <td><code>010704120856-0700</code>
 *     <tr>
 *         <td><code>"yyyy-MM-dd'T'HH:mm:ss.SSSZ"</code>
 *         <td><code>2001-07-04T12:08:56.235-0700</code>
 * </table>
 * </blockquote>
 *
 * <h4><a name="synchronization">Synchronization</a></h4>
 *
 * <p>
 * Date formats are not synchronized.
 * It is recommended to create separate format instances for each thread.
 * If multiple threads access a format concurrently, it must be synchronized
 * externally.
 *
 * @see          <a href="http://java.sun.com/docs/books/tutorial/i18n/format/simpleDateFormat.html">Java Tutorial</a>
 * @see          java.util.Calendar
 * @see          java.util.TimeZone
 * @see          DateFormat
 * @see          DateFormatSymbols
 * @author       Mark Davis, Chen-Lieh Huang, Alan Liu
 */
public class SimpleDateFormat extends DateFormat {

    // the official serial version ID which says cryptically
    // which version we're compatible with
    static final long serialVersionUID = 4774881970558875024L;

    // the internal serial version which says which version was written
    // - 0 (default) for version up to JDK 1.1.3
    // - 1 for version from JDK 1.1.4, which includes a new field
    static final int currentSerialVersion = 1;

    /**
     * The version of the serialized data on the stream.  Possible values:
     * <ul>
     * <li><b>0</b> or not present on stream: JDK 1.1.3.  This version
     * has no <code>defaultCenturyStart</code> on stream.
     * <li><b>1</b> JDK 1.1.4 or later.  This version adds
     * <code>defaultCenturyStart</code>.
     * </ul>
     * When streaming out this class, the most recent format
     * and the highest allowable <code>serialVersionOnStream</code>
     * is written.
     * @serial
     * @since JDK1.1.4
     */
    private int serialVersionOnStream = currentSerialVersion;

    /**
     * The pattern string of this formatter.  This is always a non-localized
     * pattern.  May not be null.  See class documentation for details.
     * @serial
     */
    private String pattern;

    /**
     * Saved numberFormat and pattern.
     * @see SimpleDateFormat#checkNegativeNumberExpression
     */
    transient private NumberFormat originalNumberFormat;
    transient private String originalNumberPattern;

    /**
     * The minus sign to be used with format and parse.
     */
    transient private char minusSign = '-';

    /**
     * True when a negative sign follows a number.
     * (True as default in Arabic.)
     */
    transient private boolean hasFollowingMinusSign = false;

    /**
     * The compiled pattern.
     */
    transient private char[] compiledPattern;

    /**
     * Tags for the compiled pattern.
     */
    private final static int TAG_QUOTE_ASCII_CHAR       = 100;
    private final static int TAG_QUOTE_CHARS            = 101;

    /**
     * Locale dependent digit zero.
     * @see #zeroPaddingNumber
     * @see java.text.DecimalFormatSymbols#getZeroDigit
     */
    transient private char zeroDigit;

    /**
     * The symbols used by this formatter for week names, month names,
     * etc.  May not be null.
     * @serial
     * @see java.text.DateFormatSymbols
     */
    private DateFormatSymbols formatData;

    /**
     * We map dates with two-digit years into the century starting at
     * <code>defaultCenturyStart</code>, which may be any date.  May
     * not be null.
     * @serial
     * @since JDK1.1.4
     */
    private Date defaultCenturyStart;

    transient private int defaultCenturyStartYear;

    private static final int MILLIS_PER_MINUTE = 60 * 1000;

    // For time zones that have no names, use strings GMT+minutes and
    // GMT-minutes. For instance, in France the time zone is GMT+60.
    private static final String GMT = "GMT";

    /**
     * Cache to hold the DateTimePatterns of a Locale.
     */
    private static Hashtable<String,String[]> cachedLocaleData
        = new Hashtable<String,String[]>(3);

    /**
     * Cache NumberFormat instances with Locale key.
     */
    private static Hashtable<Locale,NumberFormat> cachedNumberFormatData
        = new Hashtable<Locale,NumberFormat>(3);

    /**
     * The Locale used to instantiate this
     * <code>SimpleDateFormat</code>. The value may be null if this object
     * has been created by an older <code>SimpleDateFormat</code> and
     * deserialized.
     *
     * @serial
     * @since 1.6
     */
    private Locale locale;

    /**
     * Indicates whether this <code>SimpleDateFormat</code> should use
     * the DateFormatSymbols. If true, the format and parse methods
     * use the DateFormatSymbols values. If false, the format and
     * parse methods call Calendar.getDisplayName or
     * Calendar.getDisplayNames.
     */
    transient boolean useDateFormatSymbols;

    /**
     * Constructs a <code>SimpleDateFormat</code> using the default pattern and
     * date format symbols for the default locale.
     * <b>Note:</b> This constructor may not support all locales.
     * For full coverage, use the factory methods in the {@link DateFormat}
     * class.
     */
    public SimpleDateFormat() {
        this(SHORT, SHORT, Locale.getDefault());
    }

    /**
     * Constructs a <code>SimpleDateFormat</code> using the given pattern and
     * the default date format symbols for the default locale.
     * <b>Note:</b> This constructor may not support all locales.
     * For full coverage, use the factory methods in the {@link DateFormat}
     * class.
     *
     * @param pattern the pattern describing the date and time format
     * @exception NullPointerException if the given pattern is null
     * @exception IllegalArgumentException if the given pattern is invalid
     */
    public SimpleDateFormat(String pattern)
    {
        this(pattern, Locale.getDefault());
    }

    /**
     * Constructs a <code>SimpleDateFormat</code> using the given pattern and
     * the default date format symbols for the given locale.
     * <b>Note:</b> This constructor may not support all locales.
     * For full coverage, use the factory methods in the {@link DateFormat}
     * class.
     *
     * @param pattern the pattern describing the date and time format
     * @param locale the locale whose date format symbols should be used
     * @exception NullPointerException if the given pattern or locale is null
     * @exception IllegalArgumentException if the given pattern is invalid
     */
    public SimpleDateFormat(String pattern, Locale locale)
    {
        if (pattern == null || locale == null) {
            throw new NullPointerException();
        }

        initializeCalendar(locale);
        this.pattern = pattern;
        this.formatData = DateFormatSymbols.getInstance(locale);
        this.locale = locale;
        initialize(locale);
    }

    /**
     * Constructs a <code>SimpleDateFormat</code> using the given pattern and
     * date format symbols.
     *
     * @param pattern the pattern describing the date and time format
     * @param formatSymbols the date format symbols to be used for formatting
     * @exception NullPointerException if the given pattern or formatSymbols is null
     * @exception IllegalArgumentException if the given pattern is invalid
     */
    public SimpleDateFormat(String pattern, DateFormatSymbols formatSymbols)
    {
        if (pattern == null || formatSymbols == null) {
            throw new NullPointerException();
        }

        this.pattern = pattern;
        this.formatData = (DateFormatSymbols) formatSymbols.clone();
        this.locale = Locale.getDefault();
        initializeCalendar(this.locale);
        initialize(this.locale);
        useDateFormatSymbols = true;
    }

    /* Package-private, called by DateFormat factory methods */
    SimpleDateFormat(int timeStyle, int dateStyle, Locale loc) {
        if (loc == null) {
            throw new NullPointerException();
        }

        this.locale = loc;
        // initialize calendar and related fields
        initializeCalendar(loc);

        /* try the cache first */
        String key = getKey();
        String[] dateTimePatterns = cachedLocaleData.get(key);
        if (dateTimePatterns == null) { /* cache miss */
            ResourceBundle r = LocaleData.getDateFormatData(loc);
            if (!isGregorianCalendar()) {
                try {
                    dateTimePatterns = r.getStringArray(getCalendarName() + ".DateTimePatterns");
                } catch (MissingResourceException e) {
                }
            }
            if (dateTimePatterns == null) {
                dateTimePatterns = r.getStringArray("DateTimePatterns");
            }
            /* update cache */
            cachedLocaleData.put(key, dateTimePatterns);
        }
        formatData = DateFormatSymbols.getInstance(loc);
        if ((timeStyle >= 0) && (dateStyle >= 0)) {
            Object[] dateTimeArgs = {dateTimePatterns[timeStyle],
                                     dateTimePatterns[dateStyle + 4]};
            pattern = MessageFormat.format(dateTimePatterns[8], dateTimeArgs);
        }
        else if (timeStyle >= 0) {
            pattern = dateTimePatterns[timeStyle];
        }
        else if (dateStyle >= 0) {
            pattern = dateTimePatterns[dateStyle + 4];
        }
        else {
            throw new IllegalArgumentException("No date or time style specified");
        }

        initialize(loc);
    }

    /* Initialize compiledPattern and numberFormat fields */
    private void initialize(Locale loc) {
        // Verify and compile the given pattern.
        compiledPattern = compile(pattern);

        /* try the cache first */
        numberFormat = cachedNumberFormatData.get(loc);
        if (numberFormat == null) { /* cache miss */
            numberFormat = NumberFormat.getIntegerInstance(loc);
            numberFormat.setGroupingUsed(false);

            /* update cache */
            cachedNumberFormatData.put(loc, numberFormat);
        }
        numberFormat = (NumberFormat) numberFormat.clone();

        initializeDefaultCentury();
    }

    private void initializeCalendar(Locale loc) {
        if (calendar == null) {
            assert loc != null;
            // The format object must be constructed using the symbols for this zone.
            // However, the calendar should use the current default TimeZone.
            // If this is not contained in the locale zone strings, then the zone
            // will be formatted using generic GMT+/-H:MM nomenclature.
            calendar = Calendar.getInstance(TimeZone.getDefault(), loc);
        }
    }

    private String getKey() {
        StringBuilder sb = new StringBuilder();
        sb.append(getCalendarName()).append('.');
        sb.append(locale.getLanguage()).append('_').append(locale.getCountry()).append('_').append(locale.getVariant());
        return sb.toString();
    }

    /**
     * Returns the compiled form of the given pattern. The syntax of
     * the compiled pattern is:
     * <blockquote>
     * CompiledPattern:
     *     EntryList
     * EntryList:
     *     Entry
     *     EntryList Entry
     * Entry:
     *     TagField
     *     TagField data
     * TagField:
     *     Tag Length
     *     TaggedData
     * Tag:
     *     pattern_char_index
     *     TAG_QUOTE_CHARS
     * Length:
     *     short_length
     *     long_length
     * TaggedData:
     *     TAG_QUOTE_ASCII_CHAR ascii_char
     *
     * </blockquote>
     *
     * where `short_length' is an 8-bit unsigned integer between 0 and
     * 254.  `long_length' is a sequence of an 8-bit integer 255 and a
     * 32-bit signed integer value which is split into upper and lower
     * 16-bit fields in two char's. `pattern_char_index' is an 8-bit
     * integer between 0 and 18. `ascii_char' is an 7-bit ASCII
     * character value. `data' depends on its Tag value.
     * <p>
     * If Length is short_length, Tag and short_length are packed in a
     * single char, as illustrated below.
     * <blockquote>
     *     char[0] = (Tag << 8) | short_length;
     * </blockquote>
     *
     * If Length is long_length, Tag and 255 are packed in the first
     * char and a 32-bit integer, as illustrated below.
     * <blockquote>
     *     char[0] = (Tag << 8) | 255;
     *     char[1] = (char) (long_length >>> 16);
     *     char[2] = (char) (long_length & 0xffff);
     * </blockquote>
     * <p>
     * If Tag is a pattern_char_index, its Length is the number of
     * pattern characters. For example, if the given pattern is
     * "yyyy", Tag is 1 and Length is 4, followed by no data.
     * <p>
     * If Tag is TAG_QUOTE_CHARS, its Length is the number of char's
     * following the TagField. For example, if the given pattern is
     * "'o''clock'", Length is 7 followed by a char sequence of
     * <code>o&nbs;'&nbs;c&nbs;l&nbs;o&nbs;c&nbs;k</code>.
     * <p>
     * TAG_QUOTE_ASCII_CHAR is a special tag and has an ASCII
     * character in place of Length. For example, if the given pattern
     * is "'o'", the TaggedData entry is
     * <code>((TAG_QUOTE_ASCII_CHAR&nbs;<<&nbs;8)&nbs;|&nbs;'o')</code>.
     *
     * @exception NullPointerException if the given pattern is null
     * @exception IllegalArgumentException if the given pattern is invalid
     */
    private char[] compile(String pattern) {
        int length = pattern.length();
        boolean inQuote = false;
        StringBuilder compiledPattern = new StringBuilder(length * 2);
        StringBuilder tmpBuffer = null;
        int count = 0;
        int lastTag = -1;

        for (int i = 0; i < length; i++) {
            char c = pattern.charAt(i);

            if (c == '\'') {
                // '' is treated as a single quote regardless of being
                // in a quoted section.
                if ((i + 1) < length) {
                    c = pattern.charAt(i + 1);
                    if (c == '\'') {
                        i++;
                        if (count != 0) {
                            encode(lastTag, count, compiledPattern);
                            lastTag = -1;
                            count = 0;
                        }
                        if (inQuote) {
                            tmpBuffer.append(c);
                        } else {
                            compiledPattern.append((char)(TAG_QUOTE_ASCII_CHAR << 8 | c));
                        }
                        continue;
                    }
                }
                if (!inQuote) {
                    if (count != 0) {
                        encode(lastTag, count, compiledPattern);
                        lastTag = -1;
                        count = 0;
                    }
                    if (tmpBuffer == null) {
                        tmpBuffer = new StringBuilder(length);
                    } else {
                        tmpBuffer.setLength(0);
                    }
                    inQuote = true;
                } else {
                    int len = tmpBuffer.length();
                    if (len == 1) {
                        char ch = tmpBuffer.charAt(0);
                        if (ch < 128) {
                            compiledPattern.append((char)(TAG_QUOTE_ASCII_CHAR << 8 | ch));
                        } else {
                            compiledPattern.append((char)(TAG_QUOTE_CHARS << 8 | 1));
                            compiledPattern.append(ch);
                        }
                    } else {
                        encode(TAG_QUOTE_CHARS, len, compiledPattern);
                        compiledPattern.append(tmpBuffer);
                    }
                    inQuote = false;
                }
                continue;
            }
            if (inQuote) {
                tmpBuffer.append(c);
                continue;
            }
            if (!(c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z')) {
                if (count != 0) {
                    encode(lastTag, count, compiledPattern);
                    lastTag = -1;
                    count = 0;
                }
                if (c < 128) {
                    // In most cases, c would be a delimiter, such as ':'.
                    compiledPattern.append((char)(TAG_QUOTE_ASCII_CHAR << 8 | c));
                } else {
                    // Take any contiguous non-ASCII alphabet characters and
                    // put them in a single TAG_QUOTE_CHARS.
                    int j;
                    for (j = i + 1; j < length; j++) {
                        char d = pattern.charAt(j);
                        if (d == '\'' || (d >= 'a' && d <= 'z' || d >= 'A' && d <= 'Z')) {
                            break;
                        }
                    }
                    compiledPattern.append((char)(TAG_QUOTE_CHARS << 8 | (j - i)));
                    for (; i < j; i++) {
                        compiledPattern.append(pattern.charAt(i));
                    }
                    i--;
                }
                continue;
            }

            int tag;
            if ((tag = DateFormatSymbols.patternChars.indexOf(c)) == -1) {
                throw new IllegalArgumentException("Illegal pattern character " +
                                                   "'" + c + "'");
            }
            if (lastTag == -1 || lastTag == tag) {
                lastTag = tag;
                count++;
                continue;
            }
            encode(lastTag, count, compiledPattern);
            lastTag = tag;
            count = 1;
        }

        if (inQuote) {
            throw new IllegalArgumentException("Unterminated quote");
        }

        if (count != 0) {
            encode(lastTag, count, compiledPattern);
        }

        // Copy the compiled pattern to a char array
        int len = compiledPattern.length();
        char[] r = new char[len];
        compiledPattern.getChars(0, len, r, 0);
        return r;
    }

    /**
     * Encodes the given tag and length and puts encoded char(s) into buffer.
     */
    private static final void encode(int tag, int length, StringBuilder buffer) {
        if (length < 255) {
            buffer.append((char)(tag << 8 | length));
        } else {
            buffer.append((char)((tag << 8) | 0xff));
            buffer.append((char)(length >>> 16));
            buffer.append((char)(length & 0xffff));
        }
    }

    /* Initialize the fields we use to disambiguate ambiguous years. Separate
     * so we can call it from readObject().
     */
    private void initializeDefaultCentury() {
        calendar.setTime( new Date() );
        calendar.add( Calendar.YEAR, -80 );
        parseAmbiguousDatesAsAfter(calendar.getTime());
    }

    /* Define one-century window into which to disambiguate dates using
     * two-digit years.
     */
    private void parseAmbiguousDatesAsAfter(Date startDate) {
        defaultCenturyStart = startDate;
        calendar.setTime(startDate);
        defaultCenturyStartYear = calendar.get(Calendar.YEAR);
    }

    /**
     * Sets the 100-year period 2-digit years will be interpreted as being in
     * to begin on the date the user specifies.
     *
     * @param startDate During parsing, two digit years will be placed in the range
     * <code>startDate</code> to <code>startDate + 100 years</code>.
     * @see #get2DigitYearStart
     * @since 1.2
     */
    public void set2DigitYearStart(Date startDate) {
        parseAmbiguousDatesAsAfter(startDate);
    }

    /**
     * Returns the beginning date of the 100-year period 2-digit years are interpreted
     * as being within.
     *
     * @return the start of the 100-year period into which two digit years are
     * parsed
     * @see #set2DigitYearStart
     * @since 1.2
     */
    public Date get2DigitYearStart() {
        return defaultCenturyStart;
    }

    /**
     * Formats the given <code>Date</code> into a date/time string and appends
     * the result to the given <code>StringBuffer</code>.
     *
     * @param date the date-time value to be formatted into a date-time string.
     * @param toAppendTo where the new date-time text is to be appended.
     * @param pos the formatting position. On input: an alignment field,
     * if desired. On output: the offsets of the alignment field.
     * @return the formatted date-time string.
     * @exception NullPointerException if the given date is null
     */
    public StringBuffer format(Date date, StringBuffer toAppendTo,
                               FieldPosition pos)
    {
        pos.beginIndex = pos.endIndex = 0;
        return format(date, toAppendTo, pos.getFieldDelegate());
    }

    // Called from Format after creating a FieldDelegate
    private StringBuffer format(Date date, StringBuffer toAppendTo,
                                FieldDelegate delegate) {
        // Convert input date to time field list
        calendar.setTime(date);

        boolean useDateFormatSymbols = useDateFormatSymbols();

        for (int i = 0; i < compiledPattern.length; ) {
            int tag = compiledPattern[i] >>> 8;
            int count = compiledPattern[i++] & 0xff;
            if (count == 255) {
                count = compiledPattern[i++] << 16;
                count |= compiledPattern[i++];
            }

            switch (tag) {
            case TAG_QUOTE_ASCII_CHAR:
                toAppendTo.append((char)count);
                break;

            case TAG_QUOTE_CHARS:
                toAppendTo.append(compiledPattern, i, count);
                i += count;
                break;

            default:
                subFormat(tag, count, delegate, toAppendTo, useDateFormatSymbols);
                break;
            }
        }
        return toAppendTo;
    }

    /**
     * Formats an Object producing an <code>AttributedCharacterIterator</code>.
     * You can use the returned <code>AttributedCharacterIterator</code>
     * to build the resulting String, as well as to determine information
     * about the resulting String.
     * <p>
     * Each attribute key of the AttributedCharacterIterator will be of type
     * <code>DateFormat.Field</code>, with the corresponding attribute value
     * being the same as the attribute key.
     *
     * @exception NullPointerException if obj is null.
     * @exception IllegalArgumentException if the Format cannot format the
     *            given object, or if the Format's pattern string is invalid.
     * @param obj The object to format
     * @return AttributedCharacterIterator describing the formatted value.
     * @since 1.4
     */
    public AttributedCharacterIterator formatToCharacterIterator(Object obj) {
        StringBuffer sb = new StringBuffer();
        CharacterIteratorFieldDelegate delegate = new
                         CharacterIteratorFieldDelegate();

        if (obj instanceof Date) {
            format((Date)obj, sb, delegate);
        }
        else if (obj instanceof Number) {
            format(new Date(((Number)obj).longValue()), sb, delegate);
        }
        else if (obj == null) {
            throw new NullPointerException(
                   "formatToCharacterIterator must be passed non-null object");
        }
        else {
            throw new IllegalArgumentException(
                             "Cannot format given Object as a Date");
        }
        return delegate.getIterator(sb.toString());
    }

    // Map index into pattern character string to Calendar field number
    private static final int[] PATTERN_INDEX_TO_CALENDAR_FIELD =
    {
        Calendar.ERA, Calendar.YEAR, Calendar.MONTH, Calendar.DATE,
        Calendar.HOUR_OF_DAY, Calendar.HOUR_OF_DAY, Calendar.MINUTE,
        Calendar.SECOND, Calendar.MILLISECOND, Calendar.DAY_OF_WEEK,
        Calendar.DAY_OF_YEAR, Calendar.DAY_OF_WEEK_IN_MONTH,
        Calendar.WEEK_OF_YEAR, Calendar.WEEK_OF_MONTH,
        Calendar.AM_PM, Calendar.HOUR, Calendar.HOUR, Calendar.ZONE_OFFSET,
        Calendar.ZONE_OFFSET
    };

    // Map index into pattern character string to DateFormat field number
    private static final int[] PATTERN_INDEX_TO_DATE_FORMAT_FIELD = {
        DateFormat.ERA_FIELD, DateFormat.YEAR_FIELD, DateFormat.MONTH_FIELD,
        DateFormat.DATE_FIELD, DateFormat.HOUR_OF_DAY1_FIELD,
        DateFormat.HOUR_OF_DAY0_FIELD, DateFormat.MINUTE_FIELD,
        DateFormat.SECOND_FIELD, DateFormat.MILLISECOND_FIELD,
        DateFormat.DAY_OF_WEEK_FIELD, DateFormat.DAY_OF_YEAR_FIELD,
        DateFormat.DAY_OF_WEEK_IN_MONTH_FIELD, DateFormat.WEEK_OF_YEAR_FIELD,
        DateFormat.WEEK_OF_MONTH_FIELD, DateFormat.AM_PM_FIELD,
        DateFormat.HOUR1_FIELD, DateFormat.HOUR0_FIELD,
        DateFormat.TIMEZONE_FIELD, DateFormat.TIMEZONE_FIELD,
    };

    // Maps from DecimalFormatSymbols index to Field constant
    private static final Field[] PATTERN_INDEX_TO_DATE_FORMAT_FIELD_ID = {
        Field.ERA, Field.YEAR, Field.MONTH, Field.DAY_OF_MONTH,
        Field.HOUR_OF_DAY1, Field.HOUR_OF_DAY0, Field.MINUTE,
        Field.SECOND, Field.MILLISECOND, Field.DAY_OF_WEEK,
        Field.DAY_OF_YEAR, Field.DAY_OF_WEEK_IN_MONTH,
        Field.WEEK_OF_YEAR, Field.WEEK_OF_MONTH,
        Field.AM_PM, Field.HOUR1, Field.HOUR0, Field.TIME_ZONE,
        Field.TIME_ZONE,
    };

    /**
     * Private member function that does the real date/time formatting.
     */
    private void subFormat(int patternCharIndex, int count,
                           FieldDelegate delegate, StringBuffer buffer,
                           boolean useDateFormatSymbols)
    {
        int     maxIntCount = Integer.MAX_VALUE;
        String  current = null;
        int     beginOffset = buffer.length();

        int field = PATTERN_INDEX_TO_CALENDAR_FIELD[patternCharIndex];
        int value = calendar.get(field);
        int style = (count >= 4) ? Calendar.LONG : Calendar.SHORT;
        if (!useDateFormatSymbols) {
            current = calendar.getDisplayName(field, style, locale);
        }

        // Note: zeroPaddingNumber() assumes that maxDigits is either
        // 2 or maxIntCount. If we make any changes to this,
        // zeroPaddingNumber() must be fixed.

        switch (patternCharIndex) {
        case 0: // 'G' - ERA
            if (useDateFormatSymbols) {
                String[] eras = formatData.getEras();
                if (value < eras.length)
                    current = eras[value];
            }
            if (current == null)
                current = "";
            break;

        case 1: // 'y' - YEAR
            if (calendar instanceof GregorianCalendar) {
                if (count != 2)
                    zeroPaddingNumber(value, count, maxIntCount, buffer);
                else // count == 2
                    zeroPaddingNumber(value, 2, 2, buffer); // clip 1996 to 96
            } else {
                if (current == null) {
                    zeroPaddingNumber(value, style == Calendar.LONG ? 1 : count,
                                      maxIntCount, buffer);
                }
            }
            break;

        case 2: // 'M' - MONTH
            if (useDateFormatSymbols) {
                String[] months;
                if (count >= 4) {
                    months = formatData.getMonths();
                    current = months[value];
                } else if (count == 3) {
                    months = formatData.getShortMonths();
                    current = months[value];
                }
            } else {
                if (count < 3) {
                    current = null;
                }
            }
            if (current == null) {
                zeroPaddingNumber(value+1, count, maxIntCount, buffer);
            }
            break;

        case 4: // 'k' - HOUR_OF_DAY: 1-based.  eg, 23:59 + 1 hour =>> 24:59
            if (current == null) {
                if (value == 0)
                    zeroPaddingNumber(calendar.getMaximum(Calendar.HOUR_OF_DAY)+1,
                                      count, maxIntCount, buffer);
                else
                    zeroPaddingNumber(value, count, maxIntCount, buffer);
            }
            break;

        case 9: // 'E' - DAY_OF_WEEK
            if (useDateFormatSymbols) {
                String[] weekdays;
                if (count >= 4) {
                    weekdays = formatData.getWeekdays();
                    current = weekdays[value];
                } else { // count < 4, use abbreviated form if exists
                    weekdays = formatData.getShortWeekdays();
                    current = weekdays[value];
                }
            }
            break;

        case 14:    // 'a' - AM_PM
            if (useDateFormatSymbols) {
                String[] ampm = formatData.getAmPmStrings();
                current = ampm[value];
            }
            break;

        case 15: // 'h' - HOUR:1-based.  eg, 11PM + 1 hour =>> 12 AM
            if (current == null) {
                if (value == 0)
                    zeroPaddingNumber(calendar.getLeastMaximum(Calendar.HOUR)+1,
                                      count, maxIntCount, buffer);
                else
                    zeroPaddingNumber(value, count, maxIntCount, buffer);
            }
            break;

        case 17: // 'z' - ZONE_OFFSET
            if (current == null) {
                if (formatData.locale == null || formatData.isZoneStringsSet) {
                    int zoneIndex =
                        formatData.getZoneIndex(calendar.getTimeZone().getID());
                    if (zoneIndex == -1) {
                        value = calendar.get(Calendar.ZONE_OFFSET) +
                            calendar.get(Calendar.DST_OFFSET);
                        buffer.append(ZoneInfoFile.toCustomID(value));
                    } else {
                        int index = (calendar.get(Calendar.DST_OFFSET) == 0) ? 1: 3;
                        if (count < 4) {
                            // Use the short name
                            index++;
                        }
                        String[][] zoneStrings = formatData.getZoneStringsWrapper();
                        buffer.append(zoneStrings[zoneIndex][index]);
                    }
                } else {
                    TimeZone tz = calendar.getTimeZone();
                    boolean daylight = (calendar.get(Calendar.DST_OFFSET) != 0);
                    int tzstyle = (count < 4 ? TimeZone.SHORT : TimeZone.LONG);
                    buffer.append(tz.getDisplayName(daylight, tzstyle, formatData.locale));
                }
            }
            break;

        case 18: // 'Z' - ZONE_OFFSET ("-/+hhmm" form)
            value = (calendar.get(Calendar.ZONE_OFFSET) +
                     calendar.get(Calendar.DST_OFFSET)) / 60000;

            int width = 4;
            if (value >= 0) {
                buffer.append('+');
            } else {
                width++;
            }

            int num = (value / 60) * 100 + (value % 60);
            CalendarUtils.sprintf0d(buffer, num, width);
            break;

        default:
            // case 3: // 'd' - DATE
            // case 5: // 'H' - HOUR_OF_DAY:0-based.  eg, 23:59 + 1 hour =>> 00:59
            // case 6: // 'm' - MINUTE
            // case 7: // 's' - SECOND
            // case 8: // 'S' - MILLISECOND
            // case 10: // 'D' - DAY_OF_YEAR
            // case 11: // 'F' - DAY_OF_WEEK_IN_MONTH
            // case 12: // 'w' - WEEK_OF_YEAR
            // case 13: // 'W' - WEEK_OF_MONTH
            // case 16: // 'K' - HOUR: 0-based.  eg, 11PM + 1 hour =>> 0 AM
            if (current == null) {
                zeroPaddingNumber(value, count, maxIntCount, buffer);
            }
            break;
        } // switch (patternCharIndex)

        if (current != null) {
            buffer.append(current);
        }

        int fieldID = PATTERN_INDEX_TO_DATE_FORMAT_FIELD[patternCharIndex];
        Field f = PATTERN_INDEX_TO_DATE_FORMAT_FIELD_ID[patternCharIndex];

        delegate.formatted(fieldID, f, f, beginOffset, buffer.length(), buffer);
    }

    /**
     * Formats a number with the specified minimum and maximum number of digits.
     */
    private final void zeroPaddingNumber(int value, int minDigits, int maxDigits, StringBuffer buffer)
    {
        // Optimization for 1, 2 and 4 digit numbers. This should
        // cover most cases of formatting date/time related items.
        // Note: This optimization code assumes that maxDigits is
        // either 2 or Integer.MAX_VALUE (maxIntCount in format()).
        try {
            if (zeroDigit == 0) {
                zeroDigit = ((DecimalFormat)numberFormat).getDecimalFormatSymbols().getZeroDigit();
            }
            if (value >= 0) {
                if (value < 100 && minDigits >= 1 && minDigits <= 2) {
                    if (value < 10) {
                        if (minDigits == 2) {
                            buffer.append(zeroDigit);
                        }
                        buffer.append((char)(zeroDigit + value));
                    } else {
                        buffer.append((char)(zeroDigit + value / 10));
                        buffer.append((char)(zeroDigit + value % 10));
                    }
                    return;
                } else if (value >= 1000 && value < 10000) {
                    if (minDigits == 4) {
                        buffer.append((char)(zeroDigit + value / 1000));
                        value %= 1000;
                        buffer.append((char)(zeroDigit + value / 100));
                        value %= 100;
                        buffer.append((char)(zeroDigit + value / 10));
                        buffer.append((char)(zeroDigit + value % 10));
                        return;
                    }
                    if (minDigits == 2 && maxDigits == 2) {
                        zeroPaddingNumber(value % 100, 2, 2, buffer);
                        return;
                    }
                }
            }
        } catch (Exception e) {
        }

        numberFormat.setMinimumIntegerDigits(minDigits);
        numberFormat.setMaximumIntegerDigits(maxDigits);
        numberFormat.format((long)value, buffer, DontCareFieldPosition.INSTANCE);
    }


    /**
     * Parses text from a string to produce a <code>Date</code>.
     * <p>
     * The method attempts to parse text starting at the index given by
     * <code>pos</code>.
     * If parsing succeeds, then the index of <code>pos</code> is updated
     * to the index after the last character used (parsing does not necessarily
     * use all characters up to the end of the string), and the parsed
     * date is returned. The updated <code>pos</code> can be used to
     * indicate the starting point for the next call to this method.
     * If an error occurs, then the index of <code>pos</code> is not
     * changed, the error index of <code>pos</code> is set to the index of
     * the character where the error occurred, and null is returned.
     *
     * <p>This parsing operation uses the {@link DateFormat#calendar
     * calendar} to produce a {@code Date}. All of the {@code
     * calendar}'s date-time fields are {@linkplain Calendar#clear()
     * cleared} before parsing, and the {@code calendar}'s default
     * values of the date-time fields are used for any missing
     * date-time information. For example, the year value of the
     * parsed {@code Date} is 1970 with {@link GregorianCalendar} if
     * no year value is given from the parsing operation.  The {@code
     * TimeZone} value may be overwritten, depending on the given
     * pattern and the time zone value in {@code text}. Any {@code
     * TimeZone} value that has previously been set by a call to
     * {@link #setTimeZone(java.util.TimeZone) setTimeZone} may need
     * to be restored for further operations.
     *
     * @param text  A <code>String</code>, part of which should be parsed.
     * @param pos   A <code>ParsePosition</code> object with index and error
     *              index information as described above.
     * @return A <code>Date</code> parsed from the string. In case of
     *         error, returns null.
     * @exception NullPointerException if <code>text</code> or <code>pos</code> is null.
     */
    public Date parse(String text, ParsePosition pos)
    {
        checkNegativeNumberExpression();

        int start = pos.index;
        int oldStart = start;
        int textLength = text.length();

        calendar.clear(); // Clears all the time fields

        boolean[] ambiguousYear = {false};


        for (int i = 0; i < compiledPattern.length; ) {
            int tag = compiledPattern[i] >>> 8;
            int count = compiledPattern[i++] & 0xff;
            if (count == 255) {
                count = compiledPattern[i++] << 16;
                count |= compiledPattern[i++];
            }

            switch (tag) {
            case TAG_QUOTE_ASCII_CHAR:
                if (start >= textLength || text.charAt(start) != (char)count) {
                    pos.index = oldStart;
                    pos.errorIndex = start;
                    return null;
                }
                start++;
                break;

            case TAG_QUOTE_CHARS:
                while (count-- > 0) {
                    if (start >= textLength || text.charAt(start) != compiledPattern[i++]) {
                        pos.index = oldStart;
                        pos.errorIndex = start;
                        return null;
                    }
                    start++;
                }
                break;

            default:
                // Peek the next pattern to determine if we need to
                // obey the number of pattern letters for
                // parsing. It's required when parsing contiguous
                // digit text (e.g., "20010704") with a pattern which
                // has no delimiters between fields, like "yyyyMMdd".
                boolean obeyCount = false;

                // In Arabic, a minus sign for a negative number is put after
                // the number. Even in another locale, a minus sign can be
                // put after a number using DateFormat.setNumberFormat().
                // If both the minus sign and the field-delimiter are '-',
                // subParse() needs to determine whether a '-' after a number
                // in the given text is a delimiter or is a minus sign for the
                // preceding number. We give subParse() a clue based on the
                // information in compiledPattern.
                boolean useFollowingMinusSignAsDelimiter = false;

                if (i < compiledPattern.length) {
                    int nextTag = compiledPattern[i] >>> 8;
                    if (!(nextTag == TAG_QUOTE_ASCII_CHAR ||
                          nextTag == TAG_QUOTE_CHARS)) {
                        obeyCount = true;
                    }

                    if (hasFollowingMinusSign &&
                        (nextTag == TAG_QUOTE_ASCII_CHAR ||
                         nextTag == TAG_QUOTE_CHARS)) {
                        int c;
                        if (nextTag == TAG_QUOTE_ASCII_CHAR) {
                            c = compiledPattern[i] & 0xff;
                        } else {
                            c = compiledPattern[i+1];
                        }

                        if (c == minusSign) {
                            useFollowingMinusSignAsDelimiter = true;
                        }
                    }
                }
                start = subParse(text, start, tag, count, obeyCount,
                                 ambiguousYear, pos,
                                 useFollowingMinusSignAsDelimiter);
                if (start < 0) {
                    pos.index = oldStart;
                    return null;
                }
            }
        }

        // At this point the fields of Calendar have been set.  Calendar
        // will fill in default values for missing fields when the time
        // is computed.

        pos.index = start;

        // This part is a problem:  When we call parsedDate.after, we compute the time.
        // Take the date April 3 2004 at 2:30 am.  When this is first set up, the year
        // will be wrong if we're parsing a 2-digit year pattern.  It will be 1904.
        // April 3 1904 is a Sunday (unlike 2004) so it is the DST onset day.  2:30 am
        // is therefore an "impossible" time, since the time goes from 1:59 to 3:00 am
        // on that day.  It is therefore parsed out to fields as 3:30 am.  Then we
        // add 100 years, and get April 3 2004 at 3:30 am.  Note that April 3 2004 is
        // a Saturday, so it can have a 2:30 am -- and it should. [LIU]
        /*
        Date parsedDate = calendar.getTime();
        if( ambiguousYear[0] && !parsedDate.after(defaultCenturyStart) ) {
            calendar.add(Calendar.YEAR, 100);
            parsedDate = calendar.getTime();
        }
        */
        // Because of the above condition, save off the fields in case we need to readjust.
        // The procedure we use here is not particularly efficient, but there is no other
        // way to do this given the API restrictions present in Calendar.  We minimize
        // inefficiency by only performing this computation when it might apply, that is,
        // when the two-digit year is equal to the start year, and thus might fall at the
        // front or the back of the default century.  This only works because we adjust
        // the year correctly to start with in other cases -- see subParse().
        Date parsedDate;
        try {
            if (ambiguousYear[0]) // If this is true then the two-digit year == the default start year
            {
                // We need a copy of the fields, and we need to avoid triggering a call to
                // complete(), which will recalculate the fields.  Since we can't access
                // the fields[] array in Calendar, we clone the entire object.  This will
                // stop working if Calendar.clone() is ever rewritten to call complete().
                Calendar savedCalendar = (Calendar)calendar.clone();
                parsedDate = calendar.getTime();
                if (parsedDate.before(defaultCenturyStart))
                {
                    // We can't use add here because that does a complete() first.
                    savedCalendar.set(Calendar.YEAR, defaultCenturyStartYear + 100);
                    parsedDate = savedCalendar.getTime();
                }
            }
            else parsedDate = calendar.getTime();
        }
        // An IllegalArgumentException will be thrown by Calendar.getTime()
        // if any fields are out of range, e.g., MONTH == 17.
        catch (IllegalArgumentException e) {
            pos.errorIndex = start;
            pos.index = oldStart;
            return null;
        }

        return parsedDate;
    }

    /**
     * Private code-size reduction function used by subParse.
     * @param text the time text being parsed.
     * @param start where to start parsing.
     * @param field the date field being parsed.
     * @param data the string array to parsed.
     * @return the new start position if matching succeeded; a negative number
     * indicating matching failure, otherwise.
     */
    private int matchString(String text, int start, int field, String[] data)
    {
        int i = 0;
        int count = data.length;

        if (field == Calendar.DAY_OF_WEEK) i = 1;

        // There may be multiple strings in the data[] array which begin with
        // the same prefix (e.g., Cerven and Cervenec (June and July) in Czech).
        // We keep track of the longest match, and return that.  Note that this
        // unfortunately requires us to test all array elements.
        int bestMatchLength = 0, bestMatch = -1;
        for (; i<count; ++i)
        {
            int length = data[i].length();
            // Always compare if we have no match yet; otherwise only compare
            // against potentially better matches (longer strings).
            if (length > bestMatchLength &&
                text.regionMatches(true, start, data[i], 0, length))
            {
                bestMatch = i;
                bestMatchLength = length;
            }
        }
        if (bestMatch >= 0)
        {
            calendar.set(field, bestMatch);
            return start + bestMatchLength;
        }
        return -start;
    }

    /**
     * Performs the same thing as matchString(String, int, int,
     * String[]). This method takes a Map<String, Integer> instead of
     * String[].
     */
    private int matchString(String text, int start, int field, Map<String,Integer> data) {
        if (data != null) {
            String bestMatch = null;

            for (String name : data.keySet()) {
                int length = name.length();
                if (bestMatch == null || length > bestMatch.length()) {
                    if (text.regionMatches(true, start, name, 0, length)) {
                        bestMatch = name;
                    }
                }
            }

            if (bestMatch != null) {
                calendar.set(field, data.get(bestMatch));
                return start + bestMatch.length();
            }
        }
        return -start;
    }

    private int matchZoneString(String text, int start, String[] zoneNames) {
        for (int i = 1; i <= 4; ++i) {
            // Checking long and short zones [1 & 2],
            // and long and short daylight [3 & 4].
            String zoneName = zoneNames[i];
            if (text.regionMatches(true, start,
                                   zoneName, 0, zoneName.length())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * find time zone 'text' matched zoneStrings and set to internal
     * calendar.
     */
    private int subParseZoneString(String text, int start) {
        boolean useSameName = false; // true if standard and daylight time use the same abbreviation.
        TimeZone currentTimeZone = getTimeZone();

        // At this point, check for named time zones by looking through
        // the locale data from the TimeZoneNames strings.
        // Want to be able to parse both short and long forms.
        int zoneIndex = formatData.getZoneIndex(currentTimeZone.getID());
        TimeZone tz = null;
        String[][] zoneStrings = formatData.getZoneStringsWrapper();
        String[] zoneNames = null;
        int nameIndex = 0;
        if (zoneIndex != -1) {
            zoneNames = zoneStrings[zoneIndex];
            if ((nameIndex = matchZoneString(text, start, zoneNames)) > 0) {
                if (nameIndex <= 2) {
                    // Check if the standard name (abbr) and the daylight name are the same.
                    useSameName = zoneNames[nameIndex].equalsIgnoreCase(zoneNames[nameIndex + 2]);
                }
                tz = TimeZone.getTimeZone(zoneNames[0]);
            }
        }
        if (tz == null) {
            zoneIndex = formatData.getZoneIndex(TimeZone.getDefault().getID());
            if (zoneIndex != -1) {
                zoneNames = zoneStrings[zoneIndex];
                if ((nameIndex = matchZoneString(text, start, zoneNames)) > 0) {
                    if (nameIndex <= 2) {
                        useSameName = zoneNames[nameIndex].equalsIgnoreCase(zoneNames[nameIndex + 2]);
                    }
                    tz = TimeZone.getTimeZone(zoneNames[0]);
                }
            }
        }
        if (tz == null) {
            int len = zoneStrings.length;
            for (int i = 0; i < len; i++) {
                zoneNames = zoneStrings[i];
                if ((nameIndex = matchZoneString(text, start, zoneNames)) > 0) {
                    if (nameIndex <= 2) {
                        useSameName = zoneNames[nameIndex].equalsIgnoreCase(zoneNames[nameIndex + 2]);
                    }
                    tz = TimeZone.getTimeZone(zoneNames[0]);
                    break;
                }
            }
        }
        if (tz != null) { // Matched any ?
            if (!tz.equals(currentTimeZone)) {
                setTimeZone(tz);
            }
            // If the time zone matched uses the same name
            // (abbreviation) for both standard and daylight time,
            // let the time zone in the Calendar decide which one.
            //
            // Also if tz.getDSTSaving() returns 0 for DST, use tz to
            // determine the local time. (6645292)
            int dstAmount = (nameIndex >= 3) ? tz.getDSTSavings() : 0;
            if (!(useSameName || (nameIndex >= 3 && dstAmount == 0))) {
                calendar.set(Calendar.ZONE_OFFSET, tz.getRawOffset());
                calendar.set(Calendar.DST_OFFSET, dstAmount);
            }
            return (start + zoneNames[nameIndex].length());
        }
        return 0;
    }

    /**
     * Private member function that converts the parsed date strings into
     * timeFields. Returns -start (for ParsePosition) if failed.
     * @param text the time text to be parsed.
     * @param start where to start parsing.
     * @param ch the pattern character for the date field text to be parsed.
     * @param count the count of a pattern character.
     * @param obeyCount if true, then the next field directly abuts this one,
     * and we should use the count to know when to stop parsing.
     * @param ambiguousYear return parameter; upon return, if ambiguousYear[0]
     * is true, then a two-digit year was parsed and may need to be readjusted.
     * @param origPos origPos.errorIndex is used to return an error index
     * at which a parse error occurred, if matching failure occurs.
     * @return the new start position if matching succeeded; -1 indicating
     * matching failure, otherwise. In case matching failure occurred,
     * an error index is set to origPos.errorIndex.
     */
    private int subParse(String text, int start, int patternCharIndex, int count,
                         boolean obeyCount, boolean[] ambiguousYear,
                         ParsePosition origPos,
                         boolean useFollowingMinusSignAsDelimiter) {
        Number number = null;
        int value = 0;
        ParsePosition pos = new ParsePosition(0);
        pos.index = start;
        int field = PATTERN_INDEX_TO_CALENDAR_FIELD[patternCharIndex];

        // If there are any spaces here, skip over them.  If we hit the end
        // of the string, then fail.
        for (;;) {
            if (pos.index >= text.length()) {
                origPos.errorIndex = start;
                return -1;
            }
            char c = text.charAt(pos.index);
            if (c != ' ' && c != '\t') break;
            ++pos.index;
        }

      parsing:
        {
            // We handle a few special cases here where we need to parse
            // a number value.  We handle further, more generic cases below.  We need
            // to handle some of them here because some fields require extra processing on
            // the parsed value.
            if (patternCharIndex == 4 /* HOUR_OF_DAY1_FIELD */ ||
                patternCharIndex == 15 /* HOUR1_FIELD */ ||
                (patternCharIndex == 2 /* MONTH_FIELD */ && count <= 2) ||
                patternCharIndex == 1 /* YEAR_FIELD */) {
                // It would be good to unify this with the obeyCount logic below,
                // but that's going to be difficult.
                if (obeyCount) {
                    if ((start+count) > text.length()) {
                        break parsing;
                    }
                    number = numberFormat.parse(text.substring(0, start+count), pos);
                } else {
                    number = numberFormat.parse(text, pos);
                }
                if (number == null) {
                    if (patternCharIndex != 1 || calendar instanceof GregorianCalendar) {
                        break parsing;
                    }
                } else {
                    value = number.intValue();

                    if (useFollowingMinusSignAsDelimiter && (value < 0) &&
                        (((pos.index < text.length()) &&
                         (text.charAt(pos.index) != minusSign)) ||
                         ((pos.index == text.length()) &&
                          (text.charAt(pos.index-1) == minusSign)))) {
                        value = -value;
                        pos.index--;
                    }
                }
            }

            boolean useDateFormatSymbols = useDateFormatSymbols();

            int index;
            switch (patternCharIndex) {
            case 0: // 'G' - ERA
                if (useDateFormatSymbols) {
                    if ((index = matchString(text, start, Calendar.ERA, formatData.getEras())) > 0) {
                        return index;
                    }
                } else {
                    Map<String, Integer> map = calendar.getDisplayNames(field,
                                                                        Calendar.ALL_STYLES,
                                                                        locale);
                    if ((index = matchString(text, start, field, map)) > 0) {
                        return index;
                    }
                }
                break parsing;

            case 1: // 'y' - YEAR
                if (!(calendar instanceof GregorianCalendar)) {
                    // calendar might have text representations for year values,
                    // such as "\u5143" in JapaneseImperialCalendar.
                    int style = (count >= 4) ? Calendar.LONG : Calendar.SHORT;
                    Map<String, Integer> map = calendar.getDisplayNames(field, style, locale);
                    if (map != null) {
                        if ((index = matchString(text, start, field, map)) > 0) {
                            return index;
                        }
                    }
                    calendar.set(field, value);
                    return pos.index;
                }

                // If there are 3 or more YEAR pattern characters, this indicates
                // that the year value is to be treated literally, without any
                // two-digit year adjustments (e.g., from "01" to 2001).  Otherwise
                // we made adjustments to place the 2-digit year in the proper
                // century, for parsed strings from "00" to "99".  Any other string
                // is treated literally:  "2250", "-1", "1", "002".
                if (count <= 2 && (pos.index - start) == 2
                    && Character.isDigit(text.charAt(start))
                    && Character.isDigit(text.charAt(start+1)))
                {
                    // Assume for example that the defaultCenturyStart is 6/18/1903.
                    // This means that two-digit years will be forced into the range
                    // 6/18/1903 to 6/17/2003.  As a result, years 00, 01, and 02
                    // correspond to 2000, 2001, and 2002.  Years 04, 05, etc. correspond
                    // to 1904, 1905, etc.  If the year is 03, then it is 2003 if the
                    // other fields specify a date before 6/18, or 1903 if they specify a
                    // date afterwards.  As a result, 03 is an ambiguous year.  All other
                    // two-digit years are unambiguous.
                    int ambiguousTwoDigitYear = defaultCenturyStartYear % 100;
                    ambiguousYear[0] = value == ambiguousTwoDigitYear;
                    value += (defaultCenturyStartYear/100)*100 +
                        (value < ambiguousTwoDigitYear ? 100 : 0);
                }
                calendar.set(Calendar.YEAR, value);
                return pos.index;

            case 2: // 'M' - MONTH
                if (count <= 2) // i.e., M or MM.
                {
                    // Don't want to parse the month if it is a string
                    // while pattern uses numeric style: M or MM.
                    // [We computed 'value' above.]
                    calendar.set(Calendar.MONTH, value - 1);
                    return pos.index;
                }

                if (useDateFormatSymbols) {
                    // count >= 3 // i.e., MMM or MMMM
                    // Want to be able to parse both short and long forms.
                    // Try count == 4 first:
                    int newStart = 0;
                    if ((newStart = matchString(text, start, Calendar.MONTH,
                                                formatData.getMonths())) > 0) {
                        return newStart;
                    }
                    // count == 4 failed, now try count == 3
                    if ((index = matchString(text, start, Calendar.MONTH,
                                             formatData.getShortMonths())) > 0) {
                        return index;
                    }
                } else {
                    Map<String, Integer> map = calendar.getDisplayNames(field,
                                                                        Calendar.ALL_STYLES,
                                                                        locale);
                    if ((index = matchString(text, start, field, map)) > 0) {
                        return index;
                    }
                }
                break parsing;

            case 4: // 'k' - HOUR_OF_DAY: 1-based.  eg, 23:59 + 1 hour =>> 24:59
                // [We computed 'value' above.]
                if (value == calendar.getMaximum(Calendar.HOUR_OF_DAY)+1) value = 0;
                calendar.set(Calendar.HOUR_OF_DAY, value);
                return pos.index;

            case 9:
                { // 'E' - DAY_OF_WEEK
                    if (useDateFormatSymbols) {
                        // Want to be able to parse both short and long forms.
                        // Try count == 4 (DDDD) first:
                        int newStart = 0;
                        if ((newStart=matchString(text, start, Calendar.DAY_OF_WEEK,
                                                  formatData.getWeekdays())) > 0) {
                            return newStart;
                        }
                        // DDDD failed, now try DDD
                        if ((index = matchString(text, start, Calendar.DAY_OF_WEEK,
                                                 formatData.getShortWeekdays())) > 0) {
                            return index;
                        }
                    } else {
                        int[] styles = { Calendar.LONG, Calendar.SHORT };
                        for (int style : styles) {
                            Map<String,Integer> map = calendar.getDisplayNames(field, style, locale);
                            if ((index = matchString(text, start, field, map)) > 0) {
                                return index;
                            }
                        }
                    }
                }
                break parsing;

            case 14:    // 'a' - AM_PM
                if (useDateFormatSymbols) {
                    if ((index = matchString(text, start, Calendar.AM_PM, formatData.getAmPmStrings())) > 0) {
                        return index;
                    }
                } else {
                    Map<String,Integer> map = calendar.getDisplayNames(field, Calendar.ALL_STYLES, locale);
                    if ((index = matchString(text, start, field, map)) > 0) {
                        return index;
                    }
                }
                break parsing;

            case 15: // 'h' - HOUR:1-based.  eg, 11PM + 1 hour =>> 12 AM
                // [We computed 'value' above.]
                if (value == calendar.getLeastMaximum(Calendar.HOUR)+1) value = 0;
                calendar.set(Calendar.HOUR, value);
                return pos.index;

            case 17: // 'z' - ZONE_OFFSET
            case 18: // 'Z' - ZONE_OFFSET
                // First try to parse generic forms such as GMT-07:00. Do this first
                // in case localized TimeZoneNames contains the string "GMT"
                // for a zone; in that case, we don't want to match the first three
                // characters of GMT+/-hh:mm etc.
                {
                    int sign = 0;
                    int offset;

                    // For time zones that have no known names, look for strings
                    // of the form:
                    //    GMT[+-]hours:minutes or
                    //    GMT.
                    if ((text.length() - start) >= GMT.length() &&
                        text.regionMatches(true, start, GMT, 0, GMT.length())) {
                        int num;
                        calendar.set(Calendar.DST_OFFSET, 0);
                        pos.index = start + GMT.length();

                        try { // try-catch for "GMT" only time zone string
                            char c = text.charAt(pos.index);
                            if (c == '+') {
                                sign = 1;
                            } else if (c == '-') {
                                sign = -1;
                            }
                        }
                        catch(StringIndexOutOfBoundsException e) {}

                        if (sign == 0) {        /* "GMT" without offset */
                            calendar.set(Calendar.ZONE_OFFSET, 0);
                            return pos.index;
                        }

                        // Look for hours.
                        try {
                            char c = text.charAt(++pos.index);
                            if (c < '0' || c > '9') { /* must be from '0' to '9'. */
                                break parsing;
                            }
                            num = c - '0';

                            if (text.charAt(++pos.index) != ':') {
                                c = text.charAt(pos.index);
                                if (c < '0' || c > '9') { /* must be from '0' to '9'. */
                                    break parsing;
                                }
                                num *= 10;
                                num += c - '0';
                                pos.index++;
                            }
                            if (num > 23) {
                                --pos.index;
                                break parsing;
                            }
                            if  (text.charAt(pos.index) != ':') {
                                break parsing;
                            }

                            // Look for minutes.
                            offset = num * 60;
                            c = text.charAt(++pos.index);
                            if (c < '0' || c > '9') { /* must be from '0' to '9'. */
                                break parsing;
                            }
                            num = c - '0';
                            c = text.charAt(++pos.index);
                            if (c < '0' || c > '9') { /* must be from '0' to '9'. */
                                break parsing;
                            }
                            num *= 10;
                            num += c - '0';

                            if (num > 59) {
                                break parsing;
                            }
                        } catch (StringIndexOutOfBoundsException e) {
                            break parsing;
                        }
                        offset += num;
                        // Fall through for final processing below of 'offset' and 'sign'.
                    } else {
                        // If the first character is a sign, look for numeric timezones of
                        // the form [+-]hhmm as specified by RFC 822. Otherwise, check
                        // for named time zones by looking through the locale data from
                        // the TimeZoneNames strings.
                        try {
                            char c = text.charAt(pos.index);
                            if (c == '+') {
                                sign = 1;
                            } else if (c == '-') {
                                sign = -1;
                            } else {
                                // Try parsing the text as a time zone name (abbr).
                                int i = subParseZoneString(text, pos.index);
                                if (i != 0) {
                                    return i;
                                }
                                break parsing;
                            }

                            // Parse the text as an RFC 822 time zone string. This code is
                            // actually a little more permissive than RFC 822.  It will
                            // try to do its best with numbers that aren't strictly 4
                            // digits long.

                            // Look for hh.
                            int hours = 0;
                            c = text.charAt(++pos.index);
                            if (c < '0' || c > '9') { /* must be from '0' to '9'. */
                                break parsing;
                            }
                            hours = c - '0';
                            c = text.charAt(++pos.index);
                            if (c < '0' || c > '9') { /* must be from '0' to '9'. */
                                break parsing;
                            }
                            hours *= 10;
                            hours += c - '0';

                            if (hours > 23) {
                                break parsing;
                            }

                            // Look for mm.
                            int minutes = 0;
                            c = text.charAt(++pos.index);
                            if (c < '0' || c > '9') { /* must be from '0' to '9'. */
                                break parsing;
                            }
                            minutes = c - '0';
                            c = text.charAt(++pos.index);
                            if (c < '0' || c > '9') { /* must be from '0' to '9'. */
                                break parsing;
                            }
                            minutes *= 10;
                            minutes += c - '0';

                            if (minutes > 59) {
                                break parsing;
                            }

                            offset = hours * 60 + minutes;
                        } catch (StringIndexOutOfBoundsException e) {
                            break parsing;
                        }
                    }

                    // Do the final processing for both of the above cases.  We only
                    // arrive here if the form GMT+/-... or an RFC 822 form was seen.
                    if (sign != 0) {
                        offset *= MILLIS_PER_MINUTE * sign;
                        calendar.set(Calendar.ZONE_OFFSET, offset);
                        calendar.set(Calendar.DST_OFFSET, 0);
                        return ++pos.index;
                    }
                }
                break parsing;

            default:
                // case 3: // 'd' - DATE
                // case 5: // 'H' - HOUR_OF_DAY:0-based.  eg, 23:59 + 1 hour =>> 00:59
                // case 6: // 'm' - MINUTE
                // case 7: // 's' - SECOND
                // case 8: // 'S' - MILLISECOND
                // case 10: // 'D' - DAY_OF_YEAR
                // case 11: // 'F' - DAY_OF_WEEK_IN_MONTH
                // case 12: // 'w' - WEEK_OF_YEAR
                // case 13: // 'W' - WEEK_OF_MONTH
                // case 16: // 'K' - HOUR: 0-based.  eg, 11PM + 1 hour =>> 0 AM

                // Handle "generic" fields
                if (obeyCount) {
                    if ((start+count) > text.length()) {
                        break parsing;
                    }
                    number = numberFormat.parse(text.substring(0, start+count), pos);
                } else {
                    number = numberFormat.parse(text, pos);
                }
                if (number != null) {
                    value = number.intValue();

                    if (useFollowingMinusSignAsDelimiter && (value < 0) &&
                        (((pos.index < text.length()) &&
                         (text.charAt(pos.index) != minusSign)) ||
                         ((pos.index == text.length()) &&
                          (text.charAt(pos.index-1) == minusSign)))) {
                        value = -value;
                        pos.index--;
                    }

                    calendar.set(field, value);
                    return pos.index;
                }
                break parsing;
            }
        }

        // Parsing failed.
        origPos.errorIndex = pos.index;
        return -1;
    }

    private final String getCalendarName() {
        return calendar.getClass().getName();
    }

    private boolean useDateFormatSymbols() {
        if (useDateFormatSymbols) {
            return true;
        }
        return isGregorianCalendar() || locale == null;
    }

    private boolean isGregorianCalendar() {
        return "java.util.GregorianCalendar".equals(getCalendarName());
    }

    /**
     * Translates a pattern, mapping each character in the from string to the
     * corresponding character in the to string.
     *
     * @exception IllegalArgumentException if the given pattern is invalid
     */
    private String translatePattern(String pattern, String from, String to) {
        StringBuilder result = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < pattern.length(); ++i) {
            char c = pattern.charAt(i);
            if (inQuote) {
                if (c == '\'')
                    inQuote = false;
            }
            else {
                if (c == '\'')
                    inQuote = true;
                else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                    int ci = from.indexOf(c);
                    if (ci == -1)
                        throw new IllegalArgumentException("Illegal pattern " +
                                                           " character '" +
                                                           c + "'");
                    c = to.charAt(ci);
                }
            }
            result.append(c);
        }
        if (inQuote)
            throw new IllegalArgumentException("Unfinished quote in pattern");
        return result.toString();
    }

    /**
     * Returns a pattern string describing this date format.
     *
     * @return a pattern string describing this date format.
     */
    public String toPattern() {
        return pattern;
    }

    /**
     * Returns a localized pattern string describing this date format.
     *
     * @return a localized pattern string describing this date format.
     */
    public String toLocalizedPattern() {
        return translatePattern(pattern,
                                DateFormatSymbols.patternChars,
                                formatData.getLocalPatternChars());
    }

    /**
     * Applies the given pattern string to this date format.
     *
     * @param pattern the new date and time pattern for this date format
     * @exception NullPointerException if the given pattern is null
     * @exception IllegalArgumentException if the given pattern is invalid
     */
    public void applyPattern (String pattern)
    {
        compiledPattern = compile(pattern);
        this.pattern = pattern;
    }

    /**
     * Applies the given localized pattern string to this date format.
     *
     * @param pattern a String to be mapped to the new date and time format
     *        pattern for this format
     * @exception NullPointerException if the given pattern is null
     * @exception IllegalArgumentException if the given pattern is invalid
     */
    public void applyLocalizedPattern(String pattern) {
         String p = translatePattern(pattern,
                                     formatData.getLocalPatternChars(),
                                     DateFormatSymbols.patternChars);
         compiledPattern = compile(p);
         this.pattern = p;
    }

    /**
     * Gets a copy of the date and time format symbols of this date format.
     *
     * @return the date and time format symbols of this date format
     * @see #setDateFormatSymbols
     */
    public DateFormatSymbols getDateFormatSymbols()
    {
        return (DateFormatSymbols)formatData.clone();
    }

    /**
     * Sets the date and time format symbols of this date format.
     *
     * @param newFormatSymbols the new date and time format symbols
     * @exception NullPointerException if the given newFormatSymbols is null
     * @see #getDateFormatSymbols
     */
    public void setDateFormatSymbols(DateFormatSymbols newFormatSymbols)
    {
        this.formatData = (DateFormatSymbols)newFormatSymbols.clone();
        useDateFormatSymbols = true;
    }

    /**
     * Creates a copy of this <code>SimpleDateFormat</code>. This also
     * clones the format's date format symbols.
     *
     * @return a clone of this <code>SimpleDateFormat</code>
     */
    public Object clone() {
        SimpleDateFormat other = (SimpleDateFormat) super.clone();
        other.formatData = (DateFormatSymbols) formatData.clone();
        return other;
    }

    /**
     * Returns the hash code value for this <code>SimpleDateFormat</code> object.
     *
     * @return the hash code value for this <code>SimpleDateFormat</code> object.
     */
    public int hashCode()
    {
        return pattern.hashCode();
        // just enough fields for a reasonable distribution
    }

    /**
     * Compares the given object with this <code>SimpleDateFormat</code> for
     * equality.
     *
     * @return true if the given object is equal to this
     * <code>SimpleDateFormat</code>
     */
    public boolean equals(Object obj)
    {
        if (!super.equals(obj)) return false; // super does class check
        SimpleDateFormat that = (SimpleDateFormat) obj;
        return (pattern.equals(that.pattern)
                && formatData.equals(that.formatData));
    }

    /**
     * After reading an object from the input stream, the format
     * pattern in the object is verified.
     * <p>
     * @exception InvalidObjectException if the pattern is invalid
     */
    private void readObject(ObjectInputStream stream)
                         throws IOException, ClassNotFoundException {
        stream.defaultReadObject();

        try {
            compiledPattern = compile(pattern);
        } catch (Exception e) {
            throw new InvalidObjectException("invalid pattern");
        }

        if (serialVersionOnStream < 1) {
            // didn't have defaultCenturyStart field
            initializeDefaultCentury();
        }
        else {
            // fill in dependent transient field
            parseAmbiguousDatesAsAfter(defaultCenturyStart);
        }
        serialVersionOnStream = currentSerialVersion;

        // If the deserialized object has a SimpleTimeZone, try
        // to replace it with a ZoneInfo equivalent in order to
        // be compatible with the SimpleTimeZone-based
        // implementation as much as possible.
        TimeZone tz = getTimeZone();
        if (tz instanceof SimpleTimeZone) {
            String id = tz.getID();
            TimeZone zi = TimeZone.getTimeZone(id);
            if (zi != null && zi.hasSameRules(tz) && zi.getID().equals(id)) {
                setTimeZone(zi);
            }
        }
    }

    /**
     * Analyze the negative subpattern of DecimalFormat and set/update values
     * as necessary.
     */
    private void checkNegativeNumberExpression() {
        if ((numberFormat instanceof DecimalFormat) &&
            !numberFormat.equals(originalNumberFormat)) {
            String numberPattern = ((DecimalFormat)numberFormat).toPattern();
            if (!numberPattern.equals(originalNumberPattern)) {
                hasFollowingMinusSign = false;

                int separatorIndex = numberPattern.indexOf(';');
                // If the negative subpattern is not absent, we have to analayze
                // it in order to check if it has a following minus sign.
                if (separatorIndex > -1) {
                    int minusIndex = numberPattern.indexOf('-', separatorIndex);
                    if ((minusIndex > numberPattern.lastIndexOf('0')) &&
                        (minusIndex > numberPattern.lastIndexOf('#'))) {
                        hasFollowingMinusSign = true;
                        minusSign = ((DecimalFormat)numberFormat).getDecimalFormatSymbols().getMinusSign();
                    }
                }
                originalNumberPattern = numberPattern;
            }
            originalNumberFormat = numberFormat;
        }
    }

}
