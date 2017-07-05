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
 * Copyright (c) 2012, Stephen Colebourne & Michael Nascimento Santos
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

package java.time.chrono;

import static java.time.temporal.ChronoField.EPOCH_DAY;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import sun.util.logging.PlatformLogger;

/**
 * The Hijrah calendar is a lunar calendar supporting Islamic calendars.
 * <p>
 * The HijrahChronology follows the rules of the Hijrah calendar system. The Hijrah
 * calendar has several variants based on differences in when the new moon is
 * determined to have occurred and where the observation is made.
 * In some variants the length of each month is
 * computed algorithmically from the astronomical data for the moon and earth and
 * in others the length of the month is determined by an authorized sighting
 * of the new moon. For the algorithmically based calendars the calendar
 * can project into the future.
 * For sighting based calendars only historical data from past
 * sightings is available.
 * <p>
 * The length of each month is 29 or 30 days.
 * Ordinary years have 354 days; leap years have 355 days.
 *
 * <p>
 * CLDR and LDML identify variants:
 * <table cellpadding="2" summary="Variants of Hijrah Calendars">
 * <thead>
 * <tr class="tableSubHeadingColor">
 * <th class="colFirst" align="left" >Chronology ID</th>
 * <th class="colFirst" align="left" >Calendar Type</th>
 * <th class="colFirst" align="left" >Locale extension, see {@link java.util.Locale}</th>
 * <th class="colLast" align="left" >Description</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr class="altColor">
 * <td>Hijrah-umalqura</td>
 * <td>islamic-umalqura</td>
 * <td>ca-islamic-umalqura</td>
 * <td>Islamic - Umm Al-Qura calendar of Saudi Arabia</td>
 * </tr>
 * </tbody>
 * </table>
 * <p>Additional variants may be available through {@link Chronology#getAvailableChronologies()}.
 *
 * <p>Example</p>
 * <p>
 * Selecting the chronology from the locale uses {@link Chronology#ofLocale}
 * to find the Chronology based on Locale supported BCP 47 extension mechanism
 * to request a specific calendar ("ca"). For example,
 * </p>
 * <pre>
 *      Locale locale = Locale.forLanguageTag("en-US-u-ca-islamic-umalqura");
 *      Chronology chrono = Chronology.ofLocale(locale);
 * </pre>
 *
 * @implSpec
 * This class is immutable and thread-safe.
 *
 * @implNote
 * Each Hijrah variant is configured individually. Each variant is defined by a
 * property resource that defines the {@code ID}, the {@code calendar type},
 * the start of the calendar, the alignment with the
 * ISO calendar, and the length of each month for a range of years.
 * The variants are identified in the {@code calendars.properties} file.
 * The new properties are prefixed with {@code "calendars.hijrah."}:
 * <table cellpadding="2" border="0" summary="Configuration of Hijrah Calendar Variants">
 * <thead>
 * <tr class="tableSubHeadingColor">
 * <th class="colFirst" align="left">Property Name</th>
 * <th class="colFirst" align="left">Property value</th>
 * <th class="colLast" align="left">Description </th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr class="altColor">
 * <td>calendars.hijrah.{ID}</td>
 * <td>The property resource defining the {@code {ID}} variant</td>
 * <td>The property resource is located with the {@code calendars.properties} file</td>
 * </tr>
 * <tr class="rowColor">
 * <td>calendars.hijrah.{ID}.type</td>
 * <td>The calendar type</td>
 * <td>LDML defines the calendar type names</td>
 * </tr>
 * </tbody>
 * </table>
 * <p>
 * The Hijrah property resource is a set of properties that describe the calendar.
 * The syntax is defined by {@code java.util.Properties#load(Reader)}.
 * <table cellpadding="2" summary="Configuration of Hijrah Calendar">
 * <thead>
 * <tr class="tableSubHeadingColor">
 * <th class="colFirst" align="left" > Property Name</th>
 * <th class="colFirst" align="left" > Property value</th>
 * <th class="colLast" align="left" > Description </th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr class="altColor">
 * <td>id</td>
 * <td>Chronology Id, for example, "Hijrah-umalqura"</td>
 * <td>The Id of the calendar in common usage</td>
 * </tr>
 * <tr class="rowColor">
 * <td>type</td>
 * <td>Calendar type, for example, "islamic-umalqura"</td>
 * <td>LDML defines the calendar types</td>
 * </tr>
 * <tr class="altColor">
 * <td>version</td>
 * <td>Version, for example: "1.8.0_1"</td>
 * <td>The version of the Hijrah variant data</td>
 * </tr>
 * <tr class="rowColor">
 * <td>iso-start</td>
 * <td>ISO start date, formatted as {@code yyyy-MM-dd}, for example: "1900-04-30"</td>
 * <td>The ISO date of the first day of the minimum Hijrah year.</td>
 * </tr>
 * <tr class="altColor">
 * <td>yyyy - a numeric 4 digit year, for example "1434"</td>
 * <td>The value is a sequence of 12 month lengths,
 * for example: "29 30 29 30 29 30 30 30 29 30 29 29"</td>
 * <td>The lengths of the 12 months of the year separated by whitespace.
 * A numeric year property must be present for every year without any gaps.
 * The month lengths must be between 29-32 inclusive.
 * </td>
 * </tr>
 * </tbody>
 * </table>
 *
 * @since 1.8
 */
public final class HijrahChronology extends AbstractChronology implements Serializable {

    /**
     * The Hijrah Calendar id.
     */
    private final transient String typeId;
    /**
     * The Hijrah calendarType.
     */
    private final transient String calendarType;
    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 3127340209035924785L;
    /**
     * Singleton instance of the Islamic Umm Al-Qura calendar of Saudi Arabia.
     * Other Hijrah chronology variants may be available from
     * {@link Chronology#getAvailableChronologies}.
     */
    public static final HijrahChronology INSTANCE;
    /**
     * Flag to indicate the initialization of configuration data is complete.
     * @see #checkCalendarInit()
     */
    private transient volatile boolean initComplete;
    /**
     * Array of epoch days indexed by Hijrah Epoch month.
     * Computed by {@link #loadCalendarData}.
     */
    private transient int[] hijrahEpochMonthStartDays;
    /**
     * The minimum epoch day of this Hijrah calendar.
     * Computed by {@link #loadCalendarData}.
     */
    private transient int minEpochDay;
    /**
     * The maximum epoch day for which calendar data is available.
     * Computed by {@link #loadCalendarData}.
     */
    private transient int maxEpochDay;
    /**
     * The minimum epoch month.
     * Computed by {@link #loadCalendarData}.
     */
    private transient int hijrahStartEpochMonth;
    /**
     * The minimum length of a month.
     * Computed by {@link #createEpochMonths}.
     */
    private transient int minMonthLength;
    /**
     * The maximum length of a month.
     * Computed by {@link #createEpochMonths}.
     */
    private transient int maxMonthLength;
    /**
     * The minimum length of a year in days.
     * Computed by {@link #createEpochMonths}.
     */
    private transient int minYearLength;
    /**
     * The maximum length of a year in days.
     * Computed by {@link #createEpochMonths}.
     */
    private transient int maxYearLength;
    /**
     * A reference to the properties stored in
     * ${java.home}/lib/calendars.properties
     */
    private final transient static Properties calendarProperties;

    /**
     * Prefix of property names for Hijrah calendar variants.
     */
    private static final String PROP_PREFIX = "calendar.hijrah.";
    /**
     * Suffix of property names containing the calendar type of a variant.
     */
    private static final String PROP_TYPE_SUFFIX = ".type";

    /**
     * Static initialization of the predefined calendars found in the
     * lib/calendars.properties file.
     */
    static {
        try {
            calendarProperties = sun.util.calendar.BaseCalendar.getCalendarProperties();
        } catch (IOException ioe) {
            throw new InternalError("Can't initialize lib/calendars.properties", ioe);
        }

        try {
            INSTANCE = new HijrahChronology("Hijrah-umalqura");
            // Register it by its aliases
            AbstractChronology.registerChrono(INSTANCE, "Hijrah");
            AbstractChronology.registerChrono(INSTANCE, "islamic");
        } catch (DateTimeException ex) {
            // Absence of Hijrah calendar is fatal to initializing this class.
            PlatformLogger logger = PlatformLogger.getLogger("java.time.chrono");
            logger.severe("Unable to initialize Hijrah calendar: Hijrah-umalqura", ex);
            throw new RuntimeException("Unable to initialize Hijrah-umalqura calendar", ex.getCause());
        }
        registerVariants();
    }

    /**
     * For each Hijrah variant listed, create the HijrahChronology and register it.
     * Exceptions during initialization are logged but otherwise ignored.
     */
    private static void registerVariants() {
        for (String name : calendarProperties.stringPropertyNames()) {
            if (name.startsWith(PROP_PREFIX)) {
                String id = name.substring(PROP_PREFIX.length());
                if (id.indexOf('.') >= 0) {
                    continue;   // no name or not a simple name of a calendar
                }
                if (id.equals(INSTANCE.getId())) {
                    continue;           // do not duplicate the default
                }
                try {
                    // Create and register the variant
                    HijrahChronology chrono = new HijrahChronology(id);
                    AbstractChronology.registerChrono(chrono);
                } catch (DateTimeException ex) {
                    // Log error and continue
                    PlatformLogger logger = PlatformLogger.getLogger("java.time.chrono");
                    logger.severe("Unable to initialize Hijrah calendar: " + id, ex);
                }
            }
        }
    }

    /**
     * Create a HijrahChronology for the named variant.
     * The resource and calendar type are retrieved from properties
     * in the {@code calendars.properties}.
     * The property names are {@code "calendar.hijrah." + id}
     * and  {@code "calendar.hijrah." + id + ".type"}
     * @param id the id of the calendar
     * @throws DateTimeException if the calendar type is missing from the properties file.
     * @throws IllegalArgumentException if the id is empty
     */
    private HijrahChronology(String id) throws DateTimeException {
        if (id.isEmpty()) {
            throw new IllegalArgumentException("calendar id is empty");
        }
        String propName = PROP_PREFIX + id + PROP_TYPE_SUFFIX;
        String calType = calendarProperties.getProperty(propName);
        if (calType == null || calType.isEmpty()) {
            throw new DateTimeException("calendarType is missing or empty for: " + propName);
        }
        this.typeId = id;
        this.calendarType = calType;
    }

    /**
     * Check and ensure that the calendar data has been initialized.
     * The initialization check is performed at the boundary between
     * public and package methods.  If a public calls another public method
     * a check is not necessary in the caller.
     * The constructors of HijrahDate call {@link #getEpochDay} or
     * {@link #getHijrahDateInfo} so every call from HijrahDate to a
     * HijrahChronology via package private methods has been checked.
     *
     * @throws DateTimeException if the calendar data configuration is
     *     malformed or IOExceptions occur loading the data
     */
    private void checkCalendarInit() {
        // Keep this short so it can be inlined for performance
        if (initComplete == false) {
            loadCalendarData();
            initComplete = true;
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the ID of the chronology.
     * <p>
     * The ID uniquely identifies the {@code Chronology}. It can be used to
     * lookup the {@code Chronology} using {@link #of(String)}.
     *
     * @return the chronology ID, non-null
     * @see #getCalendarType()
     */
    @Override
    public String getId() {
        return typeId;
    }

    /**
     * Gets the calendar type of the Islamic calendar.
     * <p>
     * The calendar type is an identifier defined by the
     * <em>Unicode Locale Data Markup Language (LDML)</em> specification.
     * It can be used to lookup the {@code Chronology} using {@link #of(String)}.
     *
     * @return the calendar system type; non-null if the calendar has
     *    a standard type, otherwise null
     * @see #getId()
     */
    @Override
    public String getCalendarType() {
        return calendarType;
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains a local date in Hijrah calendar system from the
     * era, year-of-era, month-of-year and day-of-month fields.
     *
     * @param era  the Hijrah era, not null
     * @param yearOfEra  the year-of-era
     * @param month  the month-of-year
     * @param dayOfMonth  the day-of-month
     * @return the Hijrah local date, not null
     * @throws DateTimeException if unable to create the date
     * @throws ClassCastException if the {@code era} is not a {@code HijrahEra}
     */
    @Override
    public HijrahDate date(Era era, int yearOfEra, int month, int dayOfMonth) {
        return date(prolepticYear(era, yearOfEra), month, dayOfMonth);
    }

    /**
     * Obtains a local date in Hijrah calendar system from the
     * proleptic-year, month-of-year and day-of-month fields.
     *
     * @param prolepticYear  the proleptic-year
     * @param month  the month-of-year
     * @param dayOfMonth  the day-of-month
     * @return the Hijrah local date, not null
     * @throws DateTimeException if unable to create the date
     */
    @Override
    public HijrahDate date(int prolepticYear, int month, int dayOfMonth) {
        return HijrahDate.of(this, prolepticYear, month, dayOfMonth);
    }

    /**
     * Obtains a local date in Hijrah calendar system from the
     * era, year-of-era and day-of-year fields.
     *
     * @param era  the Hijrah era, not null
     * @param yearOfEra  the year-of-era
     * @param dayOfYear  the day-of-year
     * @return the Hijrah local date, not null
     * @throws DateTimeException if unable to create the date
     * @throws ClassCastException if the {@code era} is not a {@code HijrahEra}
     */
    @Override
    public HijrahDate dateYearDay(Era era, int yearOfEra, int dayOfYear) {
        return dateYearDay(prolepticYear(era, yearOfEra), dayOfYear);
    }

    /**
     * Obtains a local date in Hijrah calendar system from the
     * proleptic-year and day-of-year fields.
     *
     * @param prolepticYear  the proleptic-year
     * @param dayOfYear  the day-of-year
     * @return the Hijrah local date, not null
     * @throws DateTimeException if the value of the year is out of range,
     *  or if the day-of-year is invalid for the year
     */
    @Override
    public HijrahDate dateYearDay(int prolepticYear, int dayOfYear) {
        HijrahDate date = HijrahDate.of(this, prolepticYear, 1, 1);
        if (dayOfYear > date.lengthOfYear()) {
            throw new DateTimeException("Invalid dayOfYear: " + dayOfYear);
        }
        return date.plusDays(dayOfYear - 1);
    }

    /**
     * Obtains a local date in the Hijrah calendar system from the epoch-day.
     *
     * @param epochDay  the epoch day
     * @return the Hijrah local date, not null
     * @throws DateTimeException if unable to create the date
     */
    @Override  // override with covariant return type
    public HijrahDate dateEpochDay(long epochDay) {
        return HijrahDate.ofEpochDay(this, epochDay);
    }

    @Override
    public HijrahDate dateNow() {
        return dateNow(Clock.systemDefaultZone());
    }

    @Override
    public HijrahDate dateNow(ZoneId zone) {
        return dateNow(Clock.system(zone));
    }

    @Override
    public HijrahDate dateNow(Clock clock) {
        return date(LocalDate.now(clock));
    }

    @Override
    public HijrahDate date(TemporalAccessor temporal) {
        if (temporal instanceof HijrahDate) {
            return (HijrahDate) temporal;
        }
        return HijrahDate.ofEpochDay(this, temporal.getLong(EPOCH_DAY));
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChronoLocalDateTime<HijrahDate> localDateTime(TemporalAccessor temporal) {
        return (ChronoLocalDateTime<HijrahDate>) super.localDateTime(temporal);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChronoZonedDateTime<HijrahDate> zonedDateTime(TemporalAccessor temporal) {
        return (ChronoZonedDateTime<HijrahDate>) super.zonedDateTime(temporal);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ChronoZonedDateTime<HijrahDate> zonedDateTime(Instant instant, ZoneId zone) {
        return (ChronoZonedDateTime<HijrahDate>) super.zonedDateTime(instant, zone);
    }

    //-----------------------------------------------------------------------
    @Override
    public boolean isLeapYear(long prolepticYear) {
        checkCalendarInit();
        int epochMonth = yearToEpochMonth((int) prolepticYear);
        if (epochMonth < 0 || epochMonth > maxEpochDay) {
            throw new DateTimeException("Hijrah date out of range");
        }
        int len = getYearLength((int) prolepticYear);
        return (len > 354);
    }

    @Override
    public int prolepticYear(Era era, int yearOfEra) {
        if (era instanceof HijrahEra == false) {
            throw new ClassCastException("Era must be HijrahEra");
        }
        return yearOfEra;
    }

    @Override
    public HijrahEra eraOf(int eraValue) {
        switch (eraValue) {
            case 1:
                return HijrahEra.AH;
            default:
                throw new DateTimeException("invalid Hijrah era");
        }
    }

    @Override
    public List<Era> eras() {
        return Arrays.<Era>asList(HijrahEra.values());
    }

    //-----------------------------------------------------------------------
    @Override
    public ValueRange range(ChronoField field) {
        checkCalendarInit();
        if (field instanceof ChronoField) {
            ChronoField f = field;
            switch (f) {
                case DAY_OF_MONTH:
                    return ValueRange.of(1, 1, getMinimumMonthLength(), getMaximumMonthLength());
                case DAY_OF_YEAR:
                    return ValueRange.of(1, getMaximumDayOfYear());
                case ALIGNED_WEEK_OF_MONTH:
                    return ValueRange.of(1, 5);
                case YEAR:
                case YEAR_OF_ERA:
                    return ValueRange.of(getMinimumYear(), getMaximumYear());
                case ERA:
                    return ValueRange.of(1, 1);
                default:
                    return field.range();
            }
        }
        return field.range();
    }

    //-----------------------------------------------------------------------
    @Override  // override for return type
    public HijrahDate resolveDate(Map<TemporalField, Long> fieldValues, ResolverStyle resolverStyle) {
        return (HijrahDate) super.resolveDate(fieldValues, resolverStyle);
    }

    //-----------------------------------------------------------------------
    /**
     * Check the validity of a year.
     *
     * @param prolepticYear the year to check
     */
    int checkValidYear(long prolepticYear) {
        if (prolepticYear < getMinimumYear() || prolepticYear > getMaximumYear()) {
            throw new DateTimeException("Invalid Hijrah year: " + prolepticYear);
        }
        return (int) prolepticYear;
    }

    void checkValidDayOfYear(int dayOfYear) {
        if (dayOfYear < 1 || dayOfYear > getMaximumDayOfYear()) {
            throw new DateTimeException("Invalid Hijrah day of year: " + dayOfYear);
        }
    }

    void checkValidMonth(int month) {
        if (month < 1 || month > 12) {
            throw new DateTimeException("Invalid Hijrah month: " + month);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Returns an array containing the Hijrah year, month and day
     * computed from the epoch day.
     *
     * @param epochDay  the EpochDay
     * @return int[0] = YEAR, int[1] = MONTH, int[2] = DATE
     */
    int[] getHijrahDateInfo(int epochDay) {
        checkCalendarInit();    // ensure that the chronology is initialized
        if (epochDay < minEpochDay || epochDay >= maxEpochDay) {
            throw new DateTimeException("Hijrah date out of range");
        }

        int epochMonth = epochDayToEpochMonth(epochDay);
        int year = epochMonthToYear(epochMonth);
        int month = epochMonthToMonth(epochMonth);
        int day1 = epochMonthToEpochDay(epochMonth);
        int date = epochDay - day1; // epochDay - dayOfEpoch(year, month);

        int dateInfo[] = new int[3];
        dateInfo[0] = year;
        dateInfo[1] = month + 1; // change to 1-based.
        dateInfo[2] = date + 1; // change to 1-based.
        return dateInfo;
    }

    /**
     * Return the epoch day computed from Hijrah year, month, and day.
     *
     * @param prolepticYear the year to represent, 0-origin
     * @param monthOfYear the month-of-year to represent, 1-origin
     * @param dayOfMonth the day-of-month to represent, 1-origin
     * @return the epoch day
     */
    long getEpochDay(int prolepticYear, int monthOfYear, int dayOfMonth) {
        checkCalendarInit();    // ensure that the chronology is initialized
        checkValidMonth(monthOfYear);
        int epochMonth = yearToEpochMonth(prolepticYear) + (monthOfYear - 1);
        if (epochMonth < 0 || epochMonth >= hijrahEpochMonthStartDays.length) {
            throw new DateTimeException("Invalid Hijrah date, year: " +
                    prolepticYear +  ", month: " + monthOfYear);
        }
        if (dayOfMonth < 1 || dayOfMonth > getMonthLength(prolepticYear, monthOfYear)) {
            throw new DateTimeException("Invalid Hijrah day of month: " + dayOfMonth);
        }
        return epochMonthToEpochDay(epochMonth) + (dayOfMonth - 1);
    }

    /**
     * Returns day of year for the year and month.
     *
     * @param prolepticYear a proleptic year
     * @param month a month, 1-origin
     * @return the day of year, 1-origin
     */
    int getDayOfYear(int prolepticYear, int month) {
        return yearMonthToDayOfYear(prolepticYear, (month - 1));
    }

    /**
     * Returns month length for the year and month.
     *
     * @param prolepticYear a proleptic year
     * @param monthOfYear a month, 1-origin.
     * @return the length of the month
     */
    int getMonthLength(int prolepticYear, int monthOfYear) {
        int epochMonth = yearToEpochMonth(prolepticYear) + (monthOfYear - 1);
        if (epochMonth < 0 || epochMonth >= hijrahEpochMonthStartDays.length) {
            throw new DateTimeException("Invalid Hijrah date, year: " +
                    prolepticYear +  ", month: " + monthOfYear);
        }
        return epochMonthLength(epochMonth);
    }

    /**
     * Returns year length.
     * Note: The 12th month must exist in the data.
     *
     * @param prolepticYear a proleptic year
     * @return year length in days
     */
    int getYearLength(int prolepticYear) {
        return yearMonthToDayOfYear(prolepticYear, 12);
    }

    /**
     * Return the minimum supported Hijrah year.
     *
     * @return the minimum
     */
    int getMinimumYear() {
        return epochMonthToYear(0);
    }

    /**
     * Return the maximum supported Hijrah ear.
     *
     * @return the minimum
     */
    int getMaximumYear() {
        return epochMonthToYear(hijrahEpochMonthStartDays.length - 1) - 1;
    }

    /**
     * Returns maximum day-of-month.
     *
     * @return maximum day-of-month
     */
    int getMaximumMonthLength() {
        return maxMonthLength;
    }

    /**
     * Returns smallest maximum day-of-month.
     *
     * @return smallest maximum day-of-month
     */
    int getMinimumMonthLength() {
        return minMonthLength;
    }

    /**
     * Returns maximum day-of-year.
     *
     * @return maximum day-of-year
     */
    int getMaximumDayOfYear() {
        return maxYearLength;
    }

    /**
     * Returns smallest maximum day-of-year.
     *
     * @return smallest maximum day-of-year
     */
    int getSmallestMaximumDayOfYear() {
        return minYearLength;
    }

    /**
     * Returns the epochMonth found by locating the epochDay in the table. The
     * epochMonth is the index in the table
     *
     * @param epochDay
     * @return The index of the element of the start of the month containing the
     * epochDay.
     */
    private int epochDayToEpochMonth(int epochDay) {
        // binary search
        int ndx = Arrays.binarySearch(hijrahEpochMonthStartDays, epochDay);
        if (ndx < 0) {
            ndx = -ndx - 2;
        }
        return ndx;
    }

    /**
     * Returns the year computed from the epochMonth
     *
     * @param epochMonth the epochMonth
     * @return the Hijrah Year
     */
    private int epochMonthToYear(int epochMonth) {
        return (epochMonth + hijrahStartEpochMonth) / 12;
    }

    /**
     * Returns the epochMonth for the Hijrah Year.
     *
     * @param year the HijrahYear
     * @return the epochMonth for the beginning of the year.
     */
    private int yearToEpochMonth(int year) {
        return (year * 12) - hijrahStartEpochMonth;
    }

    /**
     * Returns the Hijrah month from the epochMonth.
     *
     * @param epochMonth the epochMonth
     * @return the month of the Hijrah Year
     */
    private int epochMonthToMonth(int epochMonth) {
        return (epochMonth + hijrahStartEpochMonth) % 12;
    }

    /**
     * Returns the epochDay for the start of the epochMonth.
     *
     * @param epochMonth the epochMonth
     * @return the epochDay for the start of the epochMonth.
     */
    private int epochMonthToEpochDay(int epochMonth) {
        return hijrahEpochMonthStartDays[epochMonth];

    }

    /**
     * Returns the day of year for the requested HijrahYear and month.
     *
     * @param prolepticYear the Hijrah year
     * @param month the Hijrah month
     * @return the day of year for the start of the month of the year
     */
    private int yearMonthToDayOfYear(int prolepticYear, int month) {
        int epochMonthFirst = yearToEpochMonth(prolepticYear);
        return epochMonthToEpochDay(epochMonthFirst + month)
                - epochMonthToEpochDay(epochMonthFirst);
    }

    /**
     * Returns the length of the epochMonth. It is computed from the start of
     * the following month minus the start of the requested month.
     *
     * @param epochMonth the epochMonth; assumed to be within range
     * @return the length in days of the epochMonth
     */
    private int epochMonthLength(int epochMonth) {
        // The very last entry in the epochMonth table is not the start of a month
        return hijrahEpochMonthStartDays[epochMonth + 1]
                - hijrahEpochMonthStartDays[epochMonth];
    }

    //-----------------------------------------------------------------------
    private static final String KEY_ID = "id";
    private static final String KEY_TYPE = "type";
    private static final String KEY_VERSION = "version";
    private static final String KEY_ISO_START = "iso-start";

    /**
     * Return the configuration properties from the resource.
     * <p>
     * The default location of the variant configuration resource is:
     * <pre>
     *   "$java.home/lib/" + resource-name
     * </pre>
     *
     * @param resource the name of the calendar property resource
     * @return a Properties containing the properties read from the resource.
     * @throws Exception if access to the property resource fails
     */
    private static Properties readConfigProperties(final String resource) throws Exception {
        try {
            return AccessController
                    .doPrivileged((java.security.PrivilegedExceptionAction<Properties>)
                        () -> {
                        String libDir = System.getProperty("java.home") + File.separator + "lib";
                        File file = new File(libDir, resource);
                        Properties props = new Properties();
                        try (InputStream is = new FileInputStream(file)) {
                            props.load(is);
                        }
                        return props;
                    });
        } catch (PrivilegedActionException pax) {
            throw pax.getException();
        }
    }

    /**
     * Loads and processes the Hijrah calendar properties file for this calendarType.
     * The starting Hijrah date and the corresponding ISO date are
     * extracted and used to calculate the epochDate offset.
     * The version number is identified and ignored.
     * Everything else is the data for a year with containing the length of each
     * of 12 months.
     *
     * @throws DateTimeException if initialization of the calendar data from the
     *     resource fails
     */
    private void loadCalendarData() {
        try {
            String resourceName = calendarProperties.getProperty(PROP_PREFIX + typeId);
            Objects.requireNonNull(resourceName, "Resource missing for calendar: " + PROP_PREFIX + typeId);
            Properties props = readConfigProperties(resourceName);

            Map<Integer, int[]> years = new HashMap<>();
            int minYear = Integer.MAX_VALUE;
            int maxYear = Integer.MIN_VALUE;
            String id = null;
            String type = null;
            String version = null;
            int isoStart = 0;
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String key = (String) entry.getKey();
                switch (key) {
                    case KEY_ID:
                        id = (String)entry.getValue();
                        break;
                    case KEY_TYPE:
                        type = (String)entry.getValue();
                        break;
                    case KEY_VERSION:
                        version = (String)entry.getValue();
                        break;
                    case KEY_ISO_START: {
                        int[] ymd = parseYMD((String) entry.getValue());
                        isoStart = (int) LocalDate.of(ymd[0], ymd[1], ymd[2]).toEpochDay();
                        break;
                    }
                    default:
                        try {
                            // Everything else is either a year or invalid
                            int year = Integer.valueOf(key);
                            int[] months = parseMonths((String) entry.getValue());
                            years.put(year, months);
                            maxYear = Math.max(maxYear, year);
                            minYear = Math.min(minYear, year);
                        } catch (NumberFormatException nfe) {
                            throw new IllegalArgumentException("bad key: " + key);
                        }
                }
            }

            if (!getId().equals(id)) {
                throw new IllegalArgumentException("Configuration is for a different calendar: " + id);
            }
            if (!getCalendarType().equals(type)) {
                throw new IllegalArgumentException("Configuration is for a different calendar type: " + type);
            }
            if (version == null || version.isEmpty()) {
                throw new IllegalArgumentException("Configuration does not contain a version");
            }
            if (isoStart == 0) {
                throw new IllegalArgumentException("Configuration does not contain a ISO start date");
            }

            // Now create and validate the array of epochDays indexed by epochMonth
            hijrahStartEpochMonth = minYear * 12;
            minEpochDay = isoStart;
            hijrahEpochMonthStartDays = createEpochMonths(minEpochDay, minYear, maxYear, years);
            maxEpochDay = hijrahEpochMonthStartDays[hijrahEpochMonthStartDays.length - 1];

            // Compute the min and max year length in days.
            for (int year = minYear; year < maxYear; year++) {
                int length = getYearLength(year);
                minYearLength = Math.min(minYearLength, length);
                maxYearLength = Math.max(maxYearLength, length);
            }
        } catch (Exception ex) {
            // Log error and throw a DateTimeException
            PlatformLogger logger = PlatformLogger.getLogger("java.time.chrono");
            logger.severe("Unable to initialize Hijrah calendar proxy: " + typeId, ex);
            throw new DateTimeException("Unable to initialize HijrahCalendar: " + typeId, ex);
        }
    }

    /**
     * Converts the map of year to month lengths ranging from minYear to maxYear
     * into a linear contiguous array of epochDays. The index is the hijrahMonth
     * computed from year and month and offset by minYear. The value of each
     * entry is the epochDay corresponding to the first day of the month.
     *
     * @param minYear The minimum year for which data is provided
     * @param maxYear The maximum year for which data is provided
     * @param years a Map of year to the array of 12 month lengths
     * @return array of epochDays for each month from min to max
     */
    private int[] createEpochMonths(int epochDay, int minYear, int maxYear, Map<Integer, int[]> years) {
        // Compute the size for the array of dates
        int numMonths = (maxYear - minYear + 1) * 12 + 1;

        // Initialize the running epochDay as the corresponding ISO Epoch day
        int epochMonth = 0; // index into array of epochMonths
        int[] epochMonths = new int[numMonths];
        minMonthLength = Integer.MAX_VALUE;
        maxMonthLength = Integer.MIN_VALUE;

        // Only whole years are valid, any zero's in the array are illegal
        for (int year = minYear; year <= maxYear; year++) {
            int[] months = years.get(year);// must not be gaps
            for (int month = 0; month < 12; month++) {
                int length = months[month];
                epochMonths[epochMonth++] = epochDay;

                if (length < 29 || length > 32) {
                    throw new IllegalArgumentException("Invalid month length in year: " + minYear);
                }
                epochDay += length;
                minMonthLength = Math.min(minMonthLength, length);
                maxMonthLength = Math.max(maxMonthLength, length);
            }
        }

        // Insert the final epochDay
        epochMonths[epochMonth++] = epochDay;

        if (epochMonth != epochMonths.length) {
            throw new IllegalStateException("Did not fill epochMonths exactly: ndx = " + epochMonth
                    + " should be " + epochMonths.length);
        }

        return epochMonths;
    }

    /**
     * Parses the 12 months lengths from a property value for a specific year.
     *
     * @param line the value of a year property
     * @return an array of int[12] containing the 12 month lengths
     * @throws IllegalArgumentException if the number of months is not 12
     * @throws NumberFormatException if the 12 tokens are not numbers
     */
    private int[] parseMonths(String line) {
        int[] months = new int[12];
        String[] numbers = line.split("\\s");
        if (numbers.length != 12) {
            throw new IllegalArgumentException("wrong number of months on line: " + Arrays.toString(numbers) + "; count: " + numbers.length);
        }
        for (int i = 0; i < 12; i++) {
            try {
                months[i] = Integer.valueOf(numbers[i]);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("bad key: " + numbers[i]);
            }
        }
        return months;
    }

    /**
     * Parse yyyy-MM-dd into a 3 element array [yyyy, mm, dd].
     *
     * @param string the input string
     * @return the 3 element array with year, month, day
     */
    private int[] parseYMD(String string) {
        // yyyy-MM-dd
        string = string.trim();
        try {
            if (string.charAt(4) != '-' || string.charAt(7) != '-') {
                throw new IllegalArgumentException("date must be yyyy-MM-dd");
            }
            int[] ymd = new int[3];
            ymd[0] = Integer.valueOf(string.substring(0, 4));
            ymd[1] = Integer.valueOf(string.substring(5, 7));
            ymd[2] = Integer.valueOf(string.substring(8, 10));
            return ymd;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("date must be yyyy-MM-dd", ex);
        }
    }

    //-----------------------------------------------------------------------
    /**
     * Writes the Chronology using a
     * <a href="../../../serialized-form.html#java.time.chrono.Ser">dedicated serialized form</a>.
     * @serialData
     * <pre>
     *  out.writeByte(1);     // identifies a Chronology
     *  out.writeUTF(getId());
     * </pre>
     *
     * @return the instance of {@code Ser}, not null
     */
    @Override
    Object writeReplace() {
        return super.writeReplace();
    }

    /**
     * Defend against malicious streams.
     *
     * @param s the stream to read
     * @throws InvalidObjectException always
     */
    private void readObject(ObjectInputStream s) throws InvalidObjectException {
        throw new InvalidObjectException("Deserialization via serialization delegate");
    }
}
