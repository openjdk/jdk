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

import java.io.IOException;
import java.io.Serializable;
import java.text.ParseException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.ValueRange;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * The Hijrah calendar system.
 * <p>
 * This chronology defines the rules of the Hijrah calendar system.
 * <p>
 * The implementation follows the Freeman-Grenville algorithm (*1) and has following features.
 * <p><ul>
 * <li>A year has 12 months.</li>
 * <li>Over a cycle of 30 years there are 11 leap years.</li>
 * <li>There are 30 days in month number 1, 3, 5, 7, 9, and 11,
 * and 29 days in month number 2, 4, 6, 8, 10, and 12.</li>
 * <li>In a leap year month 12 has 30 days.</li>
 * <li>In a 30 year cycle, year 2, 5, 7, 10, 13, 16, 18, 21, 24,
 * 26, and 29 are leap years.</li>
 * <li>Total of 10631 days in a 30 years cycle.</li>
 * </ul><p>
 * <P>
 * The table shows the features described above.
 * <blockquote>
 * <table border="1">
 *   <caption>Hijrah Calendar Months</caption>
 *   <tbody>
 *     <tr>
 *       <th># of month</th>
 *       <th>Name of month</th>
 *       <th>Number of days</th>
 *     </tr>
 *     <tr>
 *       <td>1</td>
 *       <td>Muharram</td>
 *       <td>30</td>
 *     </tr>
 *     <tr>
 *       <td>2</td>
 *       <td>Safar</td>
 *       <td>29</td>
 *     </tr>
 *     <tr>
 *       <td>3</td>
 *       <td>Rabi'al-Awwal</td>
 *       <td>30</td>
 *     </tr>
 *     <tr>
 *       <td>4</td>
 *       <td>Rabi'ath-Thani</td>
 *       <td>29</td>
 *     </tr>
 *     <tr>
 *       <td>5</td>
 *       <td>Jumada l-Ula</td>
 *       <td>30</td>
 *     </tr>
 *     <tr>
 *       <td>6</td>
 *       <td>Jumada t-Tania</td>
 *       <td>29</td>
 *     </tr>
 *     <tr>
 *       <td>7</td>
 *       <td>Rajab</td>
 *       <td>30</td>
 *     </tr>
 *     <tr>
 *       <td>8</td>
 *       <td>Sha`ban</td>
 *       <td>29</td>
 *     </tr>
 *     <tr>
 *       <td>9</td>
 *       <td>Ramadan</td>
 *       <td>30</td>
 *     </tr>
 *     <tr>
 *       <td>10</td>
 *       <td>Shawwal</td>
 *       <td>29</td>
 *     </tr>
 *     <tr>
 *       <td>11</td>
 *       <td>Dhu 'l-Qa`da</td>
 *       <td>30</td>
 *     </tr>
 *     <tr>
 *       <td>12</td>
 *       <td>Dhu 'l-Hijja</td>
 *       <td>29, but 30 days in years 2, 5, 7, 10,<br>
 * 13, 16, 18, 21, 24, 26, and 29</td>
 *     </tr>
 *   </tbody>
 * </table>
 * </blockquote>
 * <p>
 * (*1) The algorithm is taken from the book,
 * The Muslim and Christian Calendars by G.S.P. Freeman-Grenville.
 * <p>
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
public final class HijrahChronology extends Chronology implements Serializable {

    /**
     * The Hijrah Calendar id.
     */
    private final String typeId;

    /**
     * The Hijrah calendarType.
     */
    private final String calendarType;

    /**
     * The singleton instance for the era before the current one - Before Hijrah -
     * which has the value 0.
     */
    public static final Era ERA_BEFORE_AH = HijrahEra.BEFORE_AH;
    /**
     * The singleton instance for the current era - Hijrah - which has the value 1.
     */
    public static final Era ERA_AH = HijrahEra.AH;
    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 3127340209035924785L;
    /**
     * The minimum valid year-of-era.
     */
    public static final int MIN_YEAR_OF_ERA = 1;
    /**
     * The maximum valid year-of-era.
     * This is currently set to 9999 but may be changed to increase the valid range
     * in a future version of the specification.
     */
    public static final int MAX_YEAR_OF_ERA = 9999;

    /**
     * Number of Gregorian day of July 19, year 622 (Gregorian), which is epoch day
     * of Hijrah calendar.
     */
    private static final int HIJRAH_JAN_1_1_GREGORIAN_DAY = -492148;
    /**
     * 0-based, for number of day-of-year in the beginning of month in normal
     * year.
     */
    private static final int NUM_DAYS[] =
        {0, 30, 59, 89, 118, 148, 177, 207, 236, 266, 295, 325};
    /**
     * 0-based, for number of day-of-year in the beginning of month in leap year.
     */
    private static final int LEAP_NUM_DAYS[] =
        {0, 30, 59, 89, 118, 148, 177, 207, 236, 266, 295, 325};
    /**
     * 0-based, for day-of-month in normal year.
     */
    private static final int MONTH_LENGTH[] =
        {30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 29};
    /**
     * 0-based, for day-of-month in leap year.
     */
    private static final int LEAP_MONTH_LENGTH[] =
        {30, 29, 30, 29, 30, 29, 30, 29, 30, 29, 30, 30};

    /**
     * <pre>
     *                            Greatest       Least
     * Field name        Minimum   Minimum     Maximum     Maximum
     * ----------        -------   -------     -------     -------
     * ERA                     0         0           1           1
     * YEAR_OF_ERA             1         1        9999        9999
     * MONTH_OF_YEAR           1         1          12          12
     * DAY_OF_MONTH            1         1          29          30
     * DAY_OF_YEAR             1         1         354         355
     * </pre>
     *
     * Minimum values.
     */
    private static final int MIN_VALUES[] =
        {
        0,
        MIN_YEAR_OF_ERA,
        0,
        1,
        0,
        1,
        1
        };

    /**
     * Least maximum values.
     */
    private static final int LEAST_MAX_VALUES[] =
        {
        1,
        MAX_YEAR_OF_ERA,
        11,
        51,
        5,
        29,
        354
        };

    /**
     * Maximum values.
     */
    private static final int MAX_VALUES[] =
        {
        1,
        MAX_YEAR_OF_ERA,
        11,
        52,
        6,
        30,
        355
        };

   /**
     * Position of day-of-month. This value is used to get the min/max value
     * from an array.
     */
    private static final int POSITION_DAY_OF_MONTH = 5;
    /**
     * Position of day-of-year. This value is used to get the min/max value from
     * an array.
     */
    private static final int POSITION_DAY_OF_YEAR = 6;
    /**
     * Zero-based start date of cycle year.
     */
    private static final int CYCLEYEAR_START_DATE[] =
        {
        0,
        354,
        709,
        1063,
        1417,
        1772,
        2126,
        2481,
        2835,
        3189,
        3544,
        3898,
        4252,
        4607,
        4961,
        5315,
        5670,
        6024,
        6379,
        6733,
        7087,
        7442,
        7796,
        8150,
        8505,
        8859,
        9214,
        9568,
        9922,
        10277
        };

    /**
     * Holding the adjusted month days in year. The key is a year (Integer) and
     * the value is the all the month days in year (int[]).
     */
    private final HashMap<Integer, int[]> ADJUSTED_MONTH_DAYS = new HashMap<>();
    /**
     * Holding the adjusted month length in year. The key is a year (Integer)
     * and the value is the all the month length in year (int[]).
     */
    private final HashMap<Integer, int[]> ADJUSTED_MONTH_LENGTHS = new HashMap<>();
    /**
     * Holding the adjusted days in the 30 year cycle. The key is a cycle number
     * (Integer) and the value is the all the starting days of the year in the
     * cycle (int[]).
     */
    private final HashMap<Integer, int[]> ADJUSTED_CYCLE_YEARS = new HashMap<>();
    /**
     * Holding the adjusted cycle in the 1 - 30000 year. The key is the cycle
     * number (Integer) and the value is the starting days in the cycle in the
     * term.
     */
    private final long[] ADJUSTED_CYCLES;
    /**
     * Holding the adjusted min values.
     */
    private final int[] ADJUSTED_MIN_VALUES;
    /**
     * Holding the adjusted max least max values.
     */
    private final int[] ADJUSTED_LEAST_MAX_VALUES;
    /**
     * Holding adjusted max values.
     */
    private final int[] ADJUSTED_MAX_VALUES;
    /**
     * Holding the non-adjusted month days in year for non leap year.
     */
    private static final int[] DEFAULT_MONTH_DAYS;
    /**
     * Holding the non-adjusted month days in year for leap year.
     */
    private static final int[] DEFAULT_LEAP_MONTH_DAYS;
    /**
     * Holding the non-adjusted month length for non leap year.
     */
    private static final int[] DEFAULT_MONTH_LENGTHS;
    /**
     * Holding the non-adjusted month length for leap year.
     */
    private static final int[] DEFAULT_LEAP_MONTH_LENGTHS;
    /**
     * Holding the non-adjusted 30 year cycle starting day.
     */
    private static final int[] DEFAULT_CYCLE_YEARS;
    /**
     * number of 30-year cycles to hold the deviation data.
     */
    private static final int MAX_ADJUSTED_CYCLE = 334; // to support year 9999


    /**
     * Narrow names for eras.
     */
    private static final HashMap<String, String[]> ERA_NARROW_NAMES = new HashMap<>();
    /**
     * Short names for eras.
     */
    private static final HashMap<String, String[]> ERA_SHORT_NAMES = new HashMap<>();
    /**
     * Full names for eras.
     */
    private static final HashMap<String, String[]> ERA_FULL_NAMES = new HashMap<>();
    /**
     * Fallback language for the era names.
     */
    private static final String FALLBACK_LANGUAGE = "en";

    /**
     * Singleton instance of the Hijrah chronology.
     * Must be initialized after the rest of the static initialization.
     */
    public static final HijrahChronology INSTANCE;

    /**
     * Name data.
     */
    static {
        ERA_NARROW_NAMES.put(FALLBACK_LANGUAGE, new String[]{"BH", "HE"});
        ERA_SHORT_NAMES.put(FALLBACK_LANGUAGE, new String[]{"B.H.", "H.E."});
        ERA_FULL_NAMES.put(FALLBACK_LANGUAGE, new String[]{"Before Hijrah", "Hijrah Era"});

        DEFAULT_MONTH_DAYS = Arrays.copyOf(NUM_DAYS, NUM_DAYS.length);

        DEFAULT_LEAP_MONTH_DAYS = Arrays.copyOf(LEAP_NUM_DAYS, LEAP_NUM_DAYS.length);

        DEFAULT_MONTH_LENGTHS = Arrays.copyOf(MONTH_LENGTH, MONTH_LENGTH.length);

        DEFAULT_LEAP_MONTH_LENGTHS = Arrays.copyOf(LEAP_MONTH_LENGTH, LEAP_MONTH_LENGTH.length);

        DEFAULT_CYCLE_YEARS = Arrays.copyOf(CYCLEYEAR_START_DATE, CYCLEYEAR_START_DATE.length);

        INSTANCE = new HijrahChronology();

        String extraCalendars = java.security.AccessController.doPrivileged(
            new sun.security.action.GetPropertyAction("java.time.chrono.HijrahCalendars"));
        if (extraCalendars != null) {
            try {
                // Split on whitespace
                String[] splits = extraCalendars.split("\\s");
                for (String cal : splits) {
                    if (!cal.isEmpty()) {
                        // Split on the delimiter between typeId "-" calendarType
                        String[] type = cal.split("-");
                        Chronology cal2 = new HijrahChronology(type[0], type.length > 1 ? type[1] : type[0]);
                    }
                }
            } catch (Exception ex) {
                // Log the error
                // ex.printStackTrace();
            }
        }
    }

    /**
     * Restricted constructor.
     */
    private HijrahChronology() {
        this("Hijrah", "islamicc");
    }
    /**
     * Constructor for name and type HijrahChronology.
     * @param id the id of the calendar
     * @param calendarType the calendar type
     */
    private HijrahChronology(String id, String calendarType) {
        this.typeId = id;
        this.calendarType = calendarType;

        ADJUSTED_CYCLES = new long[MAX_ADJUSTED_CYCLE];
        for (int i = 0; i < ADJUSTED_CYCLES.length; i++) {
            ADJUSTED_CYCLES[i] = (10631L * i);
        }
        // Initialize min values, least max values and max values.
        ADJUSTED_MIN_VALUES = Arrays.copyOf(MIN_VALUES, MIN_VALUES.length);
        ADJUSTED_LEAST_MAX_VALUES = Arrays.copyOf(LEAST_MAX_VALUES, LEAST_MAX_VALUES.length);
        ADJUSTED_MAX_VALUES = Arrays.copyOf(MAX_VALUES,MAX_VALUES.length);

        try {
            // Implicitly reads deviation data for this HijrahChronology.
            boolean any = HijrahDeviationReader.readDeviation(typeId, calendarType, this::addDeviationAsHijrah);
        } catch (IOException | ParseException e) {
            // do nothing. Log deviation config errors.
            //e.printStackTrace();
        }
    }

    /**
     * Resolve singleton.
     *
     * @return the singleton instance, not null
     */
    private Object readResolve() {
        return INSTANCE;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the ID of the chronology - 'Hijrah'.
     * <p>
     * The ID uniquely identifies the {@code Chronology}.
     * It can be used to lookup the {@code Chronology} using {@link #of(String)}.
     *
     * @return the chronology ID - 'Hijrah'
     * @see #getCalendarType()
     */
    @Override
    public String getId() {
        return typeId;
    }

    /**
     * Gets the calendar type of the underlying calendar system - 'islamicc'.
     * <p>
     * The calendar type is an identifier defined by the
     * <em>Unicode Locale Data Markup Language (LDML)</em> specification.
     * It can be used to lookup the {@code Chronology} using {@link #of(String)}.
     * It can also be used as part of a locale, accessible via
     * {@link Locale#getUnicodeLocaleType(String)} with the key 'ca'.
     *
     * @return the calendar system type - 'islamicc'
     * @see #getId()
     */
    @Override
    public String getCalendarType() {
        return calendarType;
    }

    //-----------------------------------------------------------------------
    @Override
    public HijrahDate date(int prolepticYear, int month, int dayOfMonth) {
        return HijrahDate.of(this, prolepticYear, month, dayOfMonth);
    }

    @Override
    public HijrahDate dateYearDay(int prolepticYear, int dayOfYear) {
        return HijrahDate.of(this, prolepticYear, 1, 1).plusDays(dayOfYear - 1);  // TODO better
    }

    @Override
    public HijrahDate date(TemporalAccessor temporal) {
        if (temporal instanceof HijrahDate) {
            return (HijrahDate) temporal;
        }
        return HijrahDate.ofEpochDay(this, temporal.getLong(EPOCH_DAY));
    }

    @Override
    public HijrahDate date(Era era, int yearOfEra, int month, int dayOfMonth) {
        return date(prolepticYear(era, yearOfEra), month, dayOfMonth);

    }

    @Override
    public HijrahDate dateYearDay(Era era, int yearOfEra, int dayOfYear) {
        return dateYearDay(prolepticYear(era, yearOfEra), dayOfYear);
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
    public ChronoLocalDateTime<HijrahDate> localDateTime(TemporalAccessor temporal) {
        return (ChronoLocalDateTime<HijrahDate>)super.localDateTime(temporal);
    }

    @Override
    public ChronoZonedDateTime<HijrahDate> zonedDateTime(TemporalAccessor temporal) {
        return (ChronoZonedDateTime<HijrahDate>)super.zonedDateTime(temporal);
    }

    @Override
    public ChronoZonedDateTime<HijrahDate> zonedDateTime(Instant instant, ZoneId zone) {
        return (ChronoZonedDateTime<HijrahDate>)super.zonedDateTime(instant, zone);
    }

    //-----------------------------------------------------------------------
    @Override
    public boolean isLeapYear(long prolepticYear) {
        return isLeapYear0(prolepticYear);
    }
    /**
     * Returns if the year is a leap year.
     * @param prolepticYear he year to compute from
     * @return {@code true} if the year is a leap year, otherwise {@code false}
     */
    private static boolean isLeapYear0(long prolepticYear) {
        return (14 + 11 * (prolepticYear > 0 ? prolepticYear : -prolepticYear)) % 30 < 11;
    }

    @Override
    public int prolepticYear(Era era, int yearOfEra) {
        if (era instanceof HijrahEra == false) {
            throw new DateTimeException("Era must be HijrahEra");
        }
        return (era == HijrahEra.AH ? yearOfEra : 1 - yearOfEra);
    }

    @Override
    public Era eraOf(int eraValue) {
        switch (eraValue) {
            case 0:
                return HijrahEra.BEFORE_AH;
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
        return field.range();
    }

    /**
     * Check the validity of a yearOfEra.
     * @param yearOfEra the year to check
     */
    void checkValidYearOfEra(int yearOfEra) {
         if (yearOfEra < MIN_YEAR_OF_ERA  ||
                 yearOfEra > MAX_YEAR_OF_ERA) {
             throw new DateTimeException("Invalid year of Hijrah Era");
         }
    }

    void checkValidDayOfYear(int dayOfYear) {
         if (dayOfYear < 1  ||
                 dayOfYear > getMaximumDayOfYear()) {
             throw new DateTimeException("Invalid day of year of Hijrah date");
         }
    }

    void checkValidMonth(int month) {
         if (month < 1 || month > 12) {
             throw new DateTimeException("Invalid month of Hijrah date");
         }
    }

    void checkValidDayOfMonth(int dayOfMonth) {
         if (dayOfMonth < 1  ||
                 dayOfMonth > getMaximumDayOfMonth()) {
             throw new DateTimeException("Invalid day of month of Hijrah date, day "
                     + dayOfMonth + " greater than " + getMaximumDayOfMonth() + " or less than 1");
         }
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the int array containing the following field from the julian day.
     *
     * int[0] = ERA
     * int[1] = YEAR
     * int[2] = MONTH
     * int[3] = DATE
     * int[4] = DAY_OF_YEAR
     * int[5] = DAY_OF_WEEK
     *
     * @param gregorianDays  a julian day.
     */
    int[] getHijrahDateInfo(long gregorianDays) {
        int era, year, month, date, dayOfWeek, dayOfYear;

        int cycleNumber, yearInCycle, dayOfCycle;

        long epochDay = gregorianDays - HIJRAH_JAN_1_1_GREGORIAN_DAY;

        if (epochDay >= 0) {
            cycleNumber = getCycleNumber(epochDay); // 0 - 99.
            dayOfCycle = getDayOfCycle(epochDay, cycleNumber); // 0 - 10631.
            yearInCycle = getYearInCycle(cycleNumber, dayOfCycle); // 0 - 29.
            dayOfYear = getDayOfYear(cycleNumber, dayOfCycle, yearInCycle);
            // 0 - 354/355
            year = cycleNumber * 30 + yearInCycle + 1; // 1-based year.
            month = getMonthOfYear(dayOfYear, year); // 0-based month-of-year
            date = getDayOfMonth(dayOfYear, month, year); // 0-based date
            ++date; // Convert from 0-based to 1-based
            era = HijrahEra.AH.getValue();
        } else {
            cycleNumber = (int) epochDay / 10631; // 0 or negative number.
            dayOfCycle = (int) epochDay % 10631; // -10630 - 0.
            if (dayOfCycle == 0) {
                dayOfCycle = -10631;
                cycleNumber++;
            }
            yearInCycle = getYearInCycle(cycleNumber, dayOfCycle); // 0 - 29.
            dayOfYear = getDayOfYear(cycleNumber, dayOfCycle, yearInCycle);
            year = cycleNumber * 30 - yearInCycle; // negative number.
            year = 1 - year;
            dayOfYear = (isLeapYear(year) ? (dayOfYear + 355)
                    : (dayOfYear + 354));
            month = getMonthOfYear(dayOfYear, year);
            date = getDayOfMonth(dayOfYear, month, year);
            ++date; // Convert from 0-based to 1-based
            era = HijrahEra.BEFORE_AH.getValue();
        }
        // Hijrah day zero is a Friday
        dayOfWeek = (int) ((epochDay + 5) % 7);
        dayOfWeek += (dayOfWeek <= 0) ? 7 : 0;

        int dateInfo[] = new int[6];
        dateInfo[0] = era;
        dateInfo[1] = year;
        dateInfo[2] = month + 1; // change to 1-based.
        dateInfo[3] = date;
        dateInfo[4] = dayOfYear + 1; // change to 1-based.
        dateInfo[5] = dayOfWeek;
        return dateInfo;
    }

    /**
     * Return Gregorian epoch day from Hijrah year, month, and day.
     *
     * @param prolepticYear  the year to represent, caller calculated
     * @param monthOfYear  the month-of-year to represent, caller calculated
     * @param dayOfMonth  the day-of-month to represent, caller calculated
     * @return a julian day
     */
    long getGregorianEpochDay(int prolepticYear, int monthOfYear, int dayOfMonth) {
        long day = yearToGregorianEpochDay(prolepticYear);
        day += getMonthDays(monthOfYear - 1, prolepticYear);
        day += dayOfMonth;
        return day;
    }

    /**
     * Returns the Gregorian epoch day from the proleptic year
     * @param prolepticYear the proleptic year
     * @return the Epoch day
     */
    private long yearToGregorianEpochDay(int prolepticYear) {

        int cycleNumber = (prolepticYear - 1) / 30; // 0-based.
        int yearInCycle = (prolepticYear - 1) % 30; // 0-based.

        int dayInCycle = getAdjustedCycle(cycleNumber)[Math.abs(yearInCycle)]
                ;

        if (yearInCycle < 0) {
            dayInCycle = -dayInCycle;
        }

        Long cycleDays;

        try {
            cycleDays = ADJUSTED_CYCLES[cycleNumber];
        } catch (ArrayIndexOutOfBoundsException e) {
            cycleDays = null;
        }

        if (cycleDays == null) {
            cycleDays = new Long(cycleNumber * 10631);
        }

        return (cycleDays.longValue() + dayInCycle + HIJRAH_JAN_1_1_GREGORIAN_DAY - 1);
    }

    /**
     * Returns the 30 year cycle number from the epoch day.
     *
     * @param epochDay  an epoch day
     * @return a cycle number
     */
    private int getCycleNumber(long epochDay) {
        long[] days = ADJUSTED_CYCLES;
        int cycleNumber;
        try {
            for (int i = 0; i < days.length; i++) {
                if (epochDay < days[i]) {
                    return i - 1;
                }
            }
            cycleNumber = (int) epochDay / 10631;
        } catch (ArrayIndexOutOfBoundsException e) {
            cycleNumber = (int) epochDay / 10631;
        }
        return cycleNumber;
    }

    /**
     * Returns day of cycle from the epoch day and cycle number.
     *
     * @param epochDay  an epoch day
     * @param cycleNumber  a cycle number
     * @return a day of cycle
     */
    private int getDayOfCycle(long epochDay, int cycleNumber) {
        Long day;

        try {
            day = ADJUSTED_CYCLES[cycleNumber];
        } catch (ArrayIndexOutOfBoundsException e) {
            day = null;
        }
        if (day == null) {
            day = new Long(cycleNumber * 10631);
        }
        return (int) (epochDay - day.longValue());
    }

    /**
     * Returns the year in cycle from the cycle number and day of cycle.
     *
     * @param cycleNumber  a cycle number
     * @param dayOfCycle  day of cycle
     * @return a year in cycle
     */
    private int getYearInCycle(int cycleNumber, long dayOfCycle) {
        int[] cycles = getAdjustedCycle(cycleNumber);
        if (dayOfCycle == 0) {
            return 0;
        }

        if (dayOfCycle > 0) {
            for (int i = 0; i < cycles.length; i++) {
                if (dayOfCycle < cycles[i]) {
                    return i - 1;
                }
            }
            return 29;
        } else {
            dayOfCycle = -dayOfCycle;
            for (int i = 0; i < cycles.length; i++) {
                if (dayOfCycle <= cycles[i]) {
                    return i - 1;
                }
            }
            return 29;
        }
    }

    /**
     * Returns adjusted 30 year cycle starting day as Integer array from the
     * cycle number specified.
     *
     * @param cycleNumber  a cycle number
     * @return an Integer array
     */
    int[] getAdjustedCycle(int cycleNumber) {
        int[] cycles;
        try {
            cycles = ADJUSTED_CYCLE_YEARS.get(cycleNumber);
        } catch (ArrayIndexOutOfBoundsException e) {
            cycles = null;
        }
        if (cycles == null) {
            cycles = DEFAULT_CYCLE_YEARS;
        }
        return cycles;
    }

    /**
     * Returns adjusted month days as Integer array form the year specified.
     *
     * @param year  a year
     * @return an Integer array
     */
    int[] getAdjustedMonthDays(int year) {
        int[] newMonths;
        try {
            newMonths = ADJUSTED_MONTH_DAYS.get(year);
        } catch (ArrayIndexOutOfBoundsException e) {
            newMonths = null;
        }
        if (newMonths == null) {
            if (isLeapYear0(year)) {
                newMonths = DEFAULT_LEAP_MONTH_DAYS;
            } else {
                newMonths = DEFAULT_MONTH_DAYS;
            }
        }
        return newMonths;
    }

    /**
     * Returns adjusted month length as Integer array form the year specified.
     *
     * @param year  a year
     * @return an Integer array
     */
    int[] getAdjustedMonthLength(int year) {
        int[] newMonths;
        try {
            newMonths = ADJUSTED_MONTH_LENGTHS.get(year);
        } catch (ArrayIndexOutOfBoundsException e) {
            newMonths = null;
        }
        if (newMonths == null) {
            if (isLeapYear0(year)) {
                newMonths = DEFAULT_LEAP_MONTH_LENGTHS;
            } else {
                newMonths = DEFAULT_MONTH_LENGTHS;
            }
        }
        return newMonths;
    }

    /**
     * Returns day-of-year.
     *
     * @param cycleNumber  a cycle number
     * @param dayOfCycle  day of cycle
     * @param yearInCycle  year in cycle
     * @return day-of-year
     */
    private int getDayOfYear(int cycleNumber, int dayOfCycle, int yearInCycle) {
        int[] cycles = getAdjustedCycle(cycleNumber);

        if (dayOfCycle > 0) {
            return dayOfCycle - cycles[yearInCycle];
        } else {
            return cycles[yearInCycle] + dayOfCycle;
        }
    }

    /**
     * Returns month-of-year. 0-based.
     *
     * @param dayOfYear  day-of-year
     * @param year  a year
     * @return month-of-year
     */
    private int getMonthOfYear(int dayOfYear, int year) {

        int[] newMonths = getAdjustedMonthDays(year);

        if (dayOfYear >= 0) {
            for (int i = 0; i < newMonths.length; i++) {
                if (dayOfYear < newMonths[i]) {
                    return i - 1;
                }
            }
            return 11;
        } else {
            dayOfYear = (isLeapYear0(year) ? (dayOfYear + 355)
                    : (dayOfYear + 354));
            for (int i = 0; i < newMonths.length; i++) {
                if (dayOfYear < newMonths[i]) {
                    return i - 1;
                }
            }
            return 11;
        }
    }

    /**
     * Returns day-of-month.
     *
     * @param dayOfYear  day of  year
     * @param month  month
     * @param year  year
     * @return day-of-month
     */
    private int getDayOfMonth(int dayOfYear, int month, int year) {

        int[] newMonths = getAdjustedMonthDays(year);

        if (dayOfYear >= 0) {
            if (month > 0) {
                return dayOfYear - newMonths[month];
            } else {
                return dayOfYear;
            }
        } else {
            dayOfYear = (isLeapYear0(year) ? (dayOfYear + 355)
                    : (dayOfYear + 354));
            if (month > 0) {
                return dayOfYear - newMonths[month];
            } else {
                return dayOfYear;
            }
        }
    }


    /**
     * Returns month days from the beginning of year.
     *
     * @param month  month (0-based)
     * @parma year  year
     * @return month days from the beginning of year
     */
    private int getMonthDays(int month, int year) {
        int[] newMonths = getAdjustedMonthDays(year);
        return newMonths[month];
    }

    /**
     * Returns month length.
     *
     * @param month  month (0-based)
     * @param year  year
     * @return month length
     */
    private int getMonthLength(int month, int year) {
      int[] newMonths = getAdjustedMonthLength(year);
      return newMonths[month];
    }

    /**
     * Returns year length.
     *
     * @param year  year
     * @return year length
     */
    int getYearLength(int year) {

        int cycleNumber = (year - 1) / 30;
        int[] cycleYears;
        try {
            cycleYears = ADJUSTED_CYCLE_YEARS.get(cycleNumber);
        } catch (ArrayIndexOutOfBoundsException e) {
            cycleYears = null;
        }
        if (cycleYears != null) {
            int yearInCycle = (year - 1) % 30;
            if (yearInCycle == 29) {
                return (int)(ADJUSTED_CYCLES[cycleNumber + 1]
                        - ADJUSTED_CYCLES[cycleNumber]
                        - cycleYears[yearInCycle]);
            }
            return cycleYears[yearInCycle + 1]
                    - cycleYears[yearInCycle];
        } else {
            return isLeapYear0(year) ? 355 : 354;
        }
    }


    /**
     * Returns maximum day-of-month.
     *
     * @return maximum day-of-month
     */
    int getMaximumDayOfMonth() {
        return ADJUSTED_MAX_VALUES[POSITION_DAY_OF_MONTH];
    }

    /**
     * Returns smallest maximum day-of-month.
     *
     * @return smallest maximum day-of-month
     */
    int getSmallestMaximumDayOfMonth() {
        return ADJUSTED_LEAST_MAX_VALUES[POSITION_DAY_OF_MONTH];
    }

    /**
     * Returns maximum day-of-year.
     *
     * @return maximum day-of-year
     */
    int getMaximumDayOfYear() {
        return ADJUSTED_MAX_VALUES[POSITION_DAY_OF_YEAR];
    }

    /**
     * Returns smallest maximum day-of-year.
     *
     * @return smallest maximum day-of-year
     */
    int getSmallestMaximumDayOfYear() {
        return ADJUSTED_LEAST_MAX_VALUES[POSITION_DAY_OF_YEAR];
    }

    // ----- Deviation handling -----//

    /**
     * Adds deviation definition. The year and month specified should be the
     * calculated Hijrah year and month. The month is 1 based. e.g. 9 for
     * Ramadan (9th month) Addition of anything minus deviation days is
     * calculated negatively in the case the user wants to subtract days from
     * the calendar. For example, adding -1 days will subtract one day from the
     * current date.
     *
     * @param startYear  start year, 1 origin
     * @param startMonth  start month, 1 origin
     * @param endYear  end year, 1 origin
     * @param endMonth  end month, 1 origin
     * @param offset  offset -2, -1, +1, +2
     */
    private void addDeviationAsHijrah(Deviation entry) {
        int startYear = entry.startYear;
        int startMonth = entry.startMonth - 1 ;
        int endYear = entry.endYear;
        int endMonth = entry.endMonth - 1;
        int offset = entry.offset;

        if (startYear < 1) {
            throw new IllegalArgumentException("startYear < 1");
        }
        if (endYear < 1) {
            throw new IllegalArgumentException("endYear < 1");
        }
        if (startMonth < 0 || startMonth > 11) {
            throw new IllegalArgumentException(
                    "startMonth < 0 || startMonth > 11");
        }
        if (endMonth < 0 || endMonth > 11) {
            throw new IllegalArgumentException("endMonth < 0 || endMonth > 11");
        }
        if (endYear > 9999) {
            throw new IllegalArgumentException("endYear > 9999");
        }
        if (endYear < startYear) {
            throw new IllegalArgumentException("startYear > endYear");
        }
        if (endYear == startYear && endMonth < startMonth) {
            throw new IllegalArgumentException(
                    "startYear == endYear && endMonth < startMonth");
        }

        // Adjusting start year.
        boolean isStartYLeap = isLeapYear0(startYear);

        // Adjusting the number of month.
        int[] orgStartMonthNums = ADJUSTED_MONTH_DAYS.get(startYear);
        if (orgStartMonthNums == null) {
            if (isStartYLeap) {
                orgStartMonthNums = Arrays.copyOf(LEAP_NUM_DAYS, LEAP_NUM_DAYS.length);
            } else {
                orgStartMonthNums = Arrays.copyOf(NUM_DAYS, NUM_DAYS.length);
            }
        }

        int[] newStartMonthNums = new int[orgStartMonthNums.length];

        for (int month = 0; month < 12; month++) {
            if (month > startMonth) {
                newStartMonthNums[month] = (orgStartMonthNums[month] - offset);
            } else {
                newStartMonthNums[month] = (orgStartMonthNums[month]);
            }
        }

        ADJUSTED_MONTH_DAYS.put(startYear, newStartMonthNums);

        // Adjusting the days of month.

        int[] orgStartMonthLengths = ADJUSTED_MONTH_LENGTHS.get(startYear);
        if (orgStartMonthLengths == null) {
            if (isStartYLeap) {
                orgStartMonthLengths = Arrays.copyOf(LEAP_MONTH_LENGTH, LEAP_MONTH_LENGTH.length);
            } else {
                orgStartMonthLengths = Arrays.copyOf(MONTH_LENGTH, MONTH_LENGTH.length);
            }
        }

        int[] newStartMonthLengths = new int[orgStartMonthLengths.length];

        for (int month = 0; month < 12; month++) {
            if (month == startMonth) {
                newStartMonthLengths[month] = orgStartMonthLengths[month] - offset;
            } else {
                newStartMonthLengths[month] = orgStartMonthLengths[month];
            }
        }

        ADJUSTED_MONTH_LENGTHS.put(startYear, newStartMonthLengths);

        if (startYear != endYear) {
            // System.out.println("over year");
            // Adjusting starting 30 year cycle.
            int sCycleNumber = (startYear - 1) / 30;
            int sYearInCycle = (startYear - 1) % 30; // 0-based.
            int[] startCycles = ADJUSTED_CYCLE_YEARS.get(sCycleNumber);
            if (startCycles == null) {
                startCycles = Arrays.copyOf(CYCLEYEAR_START_DATE, CYCLEYEAR_START_DATE.length);
            }

            for (int j = sYearInCycle + 1; j < CYCLEYEAR_START_DATE.length; j++) {
                startCycles[j] = startCycles[j] - offset;
            }

            // System.out.println(sCycleNumber + ":" + sYearInCycle);
            ADJUSTED_CYCLE_YEARS.put(sCycleNumber, startCycles);

            int sYearInMaxY = (startYear - 1) / 30;
            int sEndInMaxY = (endYear - 1) / 30;

            if (sYearInMaxY != sEndInMaxY) {
                // System.out.println("over 30");
                // Adjusting starting 30 * MAX_ADJUSTED_CYCLE year cycle.
                // System.out.println(sYearInMaxY);

                for (int j = sYearInMaxY + 1; j < ADJUSTED_CYCLES.length; j++) {
                    ADJUSTED_CYCLES[j] = ADJUSTED_CYCLES[j] - offset;
                }

                // Adjusting ending 30 * MAX_ADJUSTED_CYCLE year cycles.
                for (int j = sEndInMaxY + 1; j < ADJUSTED_CYCLES.length; j++) {
                    ADJUSTED_CYCLES[j] = ADJUSTED_CYCLES[j] + offset;
                }
            }

            // Adjusting ending 30 year cycle.
            int eCycleNumber = (endYear - 1) / 30;
            int sEndInCycle = (endYear - 1) % 30; // 0-based.
            int[] endCycles = ADJUSTED_CYCLE_YEARS.get(eCycleNumber);
            if (endCycles == null) {
                endCycles = Arrays.copyOf(CYCLEYEAR_START_DATE, CYCLEYEAR_START_DATE.length);
            }
            for (int j = sEndInCycle + 1; j < CYCLEYEAR_START_DATE.length; j++) {
                endCycles[j] = endCycles[j] + offset;
            }
            ADJUSTED_CYCLE_YEARS.put(eCycleNumber, endCycles);
        }

        // Adjusting ending year.
        boolean isEndYLeap = isLeapYear0(endYear);

        int[] orgEndMonthDays = ADJUSTED_MONTH_DAYS.get(endYear);

        if (orgEndMonthDays == null) {
            if (isEndYLeap) {
                orgEndMonthDays = Arrays.copyOf(LEAP_NUM_DAYS, LEAP_NUM_DAYS.length);
            } else {
                orgEndMonthDays = Arrays.copyOf(NUM_DAYS, NUM_DAYS.length);
            }
        }

        int[] newEndMonthDays = new int[orgEndMonthDays.length];

        for (int month = 0; month < 12; month++) {
            if (month > endMonth) {
                newEndMonthDays[month] = orgEndMonthDays[month] + offset;
            } else {
                newEndMonthDays[month] = orgEndMonthDays[month];
            }
        }

        ADJUSTED_MONTH_DAYS.put(endYear, newEndMonthDays);

        // Adjusting the days of month.
        int[] orgEndMonthLengths = ADJUSTED_MONTH_LENGTHS.get(endYear);

        if (orgEndMonthLengths == null) {
            if (isEndYLeap) {
                orgEndMonthLengths = Arrays.copyOf(LEAP_MONTH_LENGTH, LEAP_MONTH_LENGTH.length);
            } else {
                orgEndMonthLengths = Arrays.copyOf(MONTH_LENGTH, MONTH_LENGTH.length);
            }
        }

        int[] newEndMonthLengths = new int[orgEndMonthLengths.length];

        for (int month = 0; month < 12; month++) {
            if (month == endMonth) {
                newEndMonthLengths[month] = orgEndMonthLengths[month] + offset;
            } else {
                newEndMonthLengths[month] = orgEndMonthLengths[month];
            }
        }

        ADJUSTED_MONTH_LENGTHS.put(endYear, newEndMonthLengths);

        int[] startMonthLengths = ADJUSTED_MONTH_LENGTHS.get(startYear);
        int[] endMonthLengths = ADJUSTED_MONTH_LENGTHS.get(endYear);
        int[] startMonthDays = ADJUSTED_MONTH_DAYS.get(startYear);
        int[] endMonthDays = ADJUSTED_MONTH_DAYS.get(endYear);

        int startMonthLength = startMonthLengths[startMonth];
        int endMonthLength = endMonthLengths[endMonth];
        int startMonthDay = startMonthDays[11] + startMonthLengths[11];
        int endMonthDay = endMonthDays[11] + endMonthLengths[11];

        int maxMonthLength = ADJUSTED_MAX_VALUES[POSITION_DAY_OF_MONTH];
        int leastMaxMonthLength = ADJUSTED_LEAST_MAX_VALUES[POSITION_DAY_OF_MONTH];

        if (maxMonthLength < startMonthLength) {
            maxMonthLength = startMonthLength;
        }
        if (maxMonthLength < endMonthLength) {
            maxMonthLength = endMonthLength;
        }
        ADJUSTED_MAX_VALUES[POSITION_DAY_OF_MONTH] = maxMonthLength;

        if (leastMaxMonthLength > startMonthLength) {
            leastMaxMonthLength = startMonthLength;
        }
        if (leastMaxMonthLength > endMonthLength) {
            leastMaxMonthLength = endMonthLength;
        }
        ADJUSTED_LEAST_MAX_VALUES[POSITION_DAY_OF_MONTH] = leastMaxMonthLength;

        int maxMonthDay = ADJUSTED_MAX_VALUES[POSITION_DAY_OF_YEAR];
        int leastMaxMonthDay = ADJUSTED_LEAST_MAX_VALUES[POSITION_DAY_OF_YEAR];

        if (maxMonthDay < startMonthDay) {
            maxMonthDay = startMonthDay;
        }
        if (maxMonthDay < endMonthDay) {
            maxMonthDay = endMonthDay;
        }

        ADJUSTED_MAX_VALUES[POSITION_DAY_OF_YEAR] = maxMonthDay;

        if (leastMaxMonthDay > startMonthDay) {
            leastMaxMonthDay = startMonthDay;
        }
        if (leastMaxMonthDay > endMonthDay) {
            leastMaxMonthDay = endMonthDay;
        }
        ADJUSTED_LEAST_MAX_VALUES[POSITION_DAY_OF_YEAR] = leastMaxMonthDay;
    }

    /**
     * Package private Entry for suppling deviations from the Reader.
     * Each entry consists of a range using the Hijrah calendar,
     * start year, month, end year, end month, and an offset.
     * The offset is used to modify the length of the month +2, +1, -1, -2.
     */
    static final class Deviation {

        Deviation(int startYear, int startMonth, int endYear, int endMonth, int offset) {
            this.startYear = startYear;
            this.startMonth = startMonth;
            this.endYear = endYear;
            this.endMonth = endMonth;
            this.offset = offset;
        }

        final int startYear;
        final int startMonth;
        final int endYear;
        final int endMonth;
        final int offset;

        int getStartYear() {
            return startYear;
        }

        int getStartMonth() {
            return startMonth;
        }

        int getEndYear() {
            return endYear;
        }

        int getEndMonth() {
            return endMonth;
        }

        int getOffset() {
            return offset;
        }

        @Override
        public String toString() {
            return String.format("[year: %4d, month: %2d, offset: %+d]", startYear, startMonth, offset);
        }
    }

}
