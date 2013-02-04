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
package java.time.calendar;

import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_MONTH;
import static java.time.temporal.ChronoField.ALIGNED_WEEK_OF_YEAR;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoLocalDate;
import java.time.temporal.TemporalField;
import java.time.temporal.ValueRange;
import java.util.Objects;

/**
 * A date in the Hijrah calendar system.
 * <p>
 * This implements {@code ChronoLocalDate} for the {@link HijrahChrono Hijrah calendar}.
 * <p>
 * The Hijrah calendar has a different total of days in a year than
 * Gregorian calendar, and a month is based on the period of a complete
 * revolution of the moon around the earth (as between successive new moons).
 * The calendar cycles becomes longer and unstable, and sometimes a manual
 * adjustment (for entering deviation) is necessary for correctness
 * because of the complex algorithm.
 * <p>
 * HijrahDate supports the manual adjustment feature by providing a configuration
 * file. The configuration file contains the adjustment (deviation) data with following format.
 * <pre>
 *   StartYear/StartMonth(0-based)-EndYear/EndMonth(0-based):Deviation day (1, 2, -1, or -2)
 *   Line separator or ";" is used for the separator of each deviation data.</pre>
 *   Here is the example.
 * <pre>
 *     1429/0-1429/1:1
 *     1429/2-1429/7:1;1429/6-1429/11:1
 *     1429/11-9999/11:1</pre>
 * The default location of the configuration file is:
 * <pre>
 *   $CLASSPATH/java/time/i18n</pre>
 * And the default file name is:
 * <pre>
 *   hijrah_deviation.cfg</pre>
 * The default location and file name can be overriden by setting
 * following two Java's system property.
 * <pre>
 *   Location: java.time.i18n.HijrahDate.deviationConfigDir
 *   File name: java.time.i18n.HijrahDate.deviationConfigFile</pre>
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
final class HijrahDate
        extends ChronoDateImpl<HijrahChrono>
        implements ChronoLocalDate<HijrahChrono>, Serializable {
    // this class is package-scoped so that future conversion to public
    // would not change serialization

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -5207853542612002020L;

    /**
     * The Chronology of this HijrahDate.
     */
    private final HijrahChrono chrono;
    /**
     * The era.
     */
    private final transient HijrahEra era;
    /**
     * The year.
     */
    private final transient int yearOfEra;
    /**
     * The month-of-year.
     */
    private final transient int monthOfYear;
    /**
     * The day-of-month.
     */
    private final transient int dayOfMonth;
    /**
     * The day-of-year.
     */
    private final transient int dayOfYear;
    /**
     * The day-of-week.
     */
    private final transient DayOfWeek dayOfWeek;
    /**
     * Gregorian days for this object. Holding number of days since 1970/01/01.
     * The number of days are calculated with pure Gregorian calendar
     * based.
     */
    private final long gregorianEpochDay;
    /**
     * True if year is leap year.
     */
    private final transient boolean isLeapYear;

    //-------------------------------------------------------------------------
    /**
     * Obtains an instance of {@code HijrahDate} from the Hijrah era year,
     * month-of-year and day-of-month. This uses the Hijrah era.
     *
     * @param prolepticYear  the proleptic year to represent in the Hijrah
     * @param monthOfYear  the month-of-year to represent, from 1 to 12
     * @param dayOfMonth  the day-of-month to represent, from 1 to 30
     * @return the Hijrah date, never null
     * @throws DateTimeException if the value of any field is out of range
     */
    static HijrahDate of(HijrahChrono chrono, int prolepticYear, int monthOfYear, int dayOfMonth) {
        return (prolepticYear >= 1) ?
            HijrahDate.of(chrono, HijrahEra.AH, prolepticYear, monthOfYear, dayOfMonth) :
            HijrahDate.of(chrono, HijrahEra.BEFORE_AH, 1 - prolepticYear, monthOfYear, dayOfMonth);
    }

    /**
     * Obtains an instance of {@code HijrahDate} from the era, year-of-era
     * month-of-year and day-of-month.
     *
     * @param era  the era to represent, not null
     * @param yearOfEra  the year-of-era to represent, from 1 to 9999
     * @param monthOfYear  the month-of-year to represent, from 1 to 12
     * @param dayOfMonth  the day-of-month to represent, from 1 to 31
     * @return the Hijrah date, never null
     * @throws DateTimeException if the value of any field is out of range
     */
    private static HijrahDate of(HijrahChrono chrono, HijrahEra era, int yearOfEra, int monthOfYear, int dayOfMonth) {
        Objects.requireNonNull(era, "era");
        chrono.checkValidYearOfEra(yearOfEra);
        chrono.checkValidMonth(monthOfYear);
        chrono.checkValidDayOfMonth(dayOfMonth);
        long gregorianDays = chrono.getGregorianEpochDay(era.prolepticYear(yearOfEra), monthOfYear, dayOfMonth);
        return new HijrahDate(chrono, gregorianDays);
    }

    /**
     * Obtains an instance of {@code HijrahDate} from a date.
     *
     * @param date  the date to use, not null
     * @return the Hijrah date, never null
     * @throws DateTimeException if the year is invalid
     */
    private static HijrahDate of(HijrahChrono chrono, LocalDate date) {
        long gregorianDays = date.toEpochDay();
        return new HijrahDate(chrono, gregorianDays);
    }

    static HijrahDate ofEpochDay(HijrahChrono chrono, long epochDay) {
        return new HijrahDate(chrono, epochDay);
    }

    /**
     * Constructs an instance with the specified date.
     *
     * @param gregorianDay  the number of days from 0001/01/01 (Gregorian), caller calculated
     */
    private HijrahDate(HijrahChrono chrono, long gregorianDay) {
        this.chrono = chrono;
        int[] dateInfo = chrono.getHijrahDateInfo(gregorianDay);

        chrono.checkValidYearOfEra(dateInfo[1]);
        chrono.checkValidMonth(dateInfo[2]);
        chrono.checkValidDayOfMonth(dateInfo[3]);
        chrono.checkValidDayOfYear(dateInfo[4]);

        this.era = HijrahEra.of(dateInfo[0]);
        this.yearOfEra = dateInfo[1];
        this.monthOfYear = dateInfo[2];
        this.dayOfMonth = dateInfo[3];
        this.dayOfYear = dateInfo[4];
        this.dayOfWeek = DayOfWeek.of(dateInfo[5]);
        this.gregorianEpochDay = gregorianDay;
        this.isLeapYear = chrono.isLeapYear(this.yearOfEra);
    }

    //-----------------------------------------------------------------------
    @Override
    public HijrahChrono getChrono() {
        return chrono;
    }

    @Override
    public ValueRange range(TemporalField field) {
        if (field instanceof ChronoField) {
            if (isSupported(field)) {
                ChronoField f = (ChronoField) field;
                switch (f) {
                    case DAY_OF_MONTH: return ValueRange.of(1, lengthOfMonth());
                    case DAY_OF_YEAR: return ValueRange.of(1, lengthOfYear());
                    case ALIGNED_WEEK_OF_MONTH: return ValueRange.of(1, 5);  // TODO
                    case YEAR_OF_ERA: return ValueRange.of(1, 1000);  // TODO
                }
                return getChrono().range(f);
            }
            throw new DateTimeException("Unsupported field: " + field.getName());
        }
        return field.doRange(this);
    }

    @Override
    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            switch ((ChronoField) field) {
                case DAY_OF_WEEK: return dayOfWeek.getValue();
                case ALIGNED_DAY_OF_WEEK_IN_MONTH: return ((dayOfWeek.getValue() - 1) % 7) + 1;
                case ALIGNED_DAY_OF_WEEK_IN_YEAR: return ((dayOfYear - 1) % 7) + 1;
                case DAY_OF_MONTH: return this.dayOfMonth;
                case DAY_OF_YEAR: return this.dayOfYear;
                case EPOCH_DAY: return toEpochDay();
                case ALIGNED_WEEK_OF_MONTH: return ((dayOfMonth - 1) / 7) + 1;
                case ALIGNED_WEEK_OF_YEAR: return ((dayOfYear - 1) / 7) + 1;
                case MONTH_OF_YEAR: return monthOfYear;
                case YEAR_OF_ERA: return yearOfEra;
                case YEAR: return yearOfEra;
                case ERA: return era.getValue();
            }
            throw new DateTimeException("Unsupported field: " + field.getName());
        }
        return field.doGet(this);
    }

    @Override
    public HijrahDate with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            f.checkValidValue(newValue);        // TODO: validate value
            int nvalue = (int) newValue;
            switch (f) {
                case DAY_OF_WEEK: return plusDays(newValue - dayOfWeek.getValue());
                case ALIGNED_DAY_OF_WEEK_IN_MONTH: return plusDays(newValue - getLong(ALIGNED_DAY_OF_WEEK_IN_MONTH));
                case ALIGNED_DAY_OF_WEEK_IN_YEAR: return plusDays(newValue - getLong(ALIGNED_DAY_OF_WEEK_IN_YEAR));
                case DAY_OF_MONTH: return resolvePreviousValid(yearOfEra, monthOfYear, nvalue);
                case DAY_OF_YEAR: return resolvePreviousValid(yearOfEra, ((nvalue - 1) / 30) + 1, ((nvalue - 1) % 30) + 1);
                case EPOCH_DAY: return new HijrahDate(chrono, nvalue);
                case ALIGNED_WEEK_OF_MONTH: return plusDays((newValue - getLong(ALIGNED_WEEK_OF_MONTH)) * 7);
                case ALIGNED_WEEK_OF_YEAR: return plusDays((newValue - getLong(ALIGNED_WEEK_OF_YEAR)) * 7);
                case MONTH_OF_YEAR: return resolvePreviousValid(yearOfEra, nvalue, dayOfMonth);
                case YEAR_OF_ERA: return resolvePreviousValid(yearOfEra >= 1 ? nvalue : 1 - nvalue, monthOfYear, dayOfMonth);
                case YEAR: return resolvePreviousValid(nvalue, monthOfYear, dayOfMonth);
                case ERA: return resolvePreviousValid(1 - yearOfEra, monthOfYear, dayOfMonth);
            }
            throw new DateTimeException("Unsupported field: " + field.getName());
        }
        return (HijrahDate) ChronoLocalDate.super.with(field, newValue);
    }

    private HijrahDate resolvePreviousValid(int yearOfEra, int month, int day) {
        int monthDays = getMonthDays(month - 1, yearOfEra);
        if (day > monthDays) {
            day = monthDays;
        }
        return HijrahDate.of(chrono, yearOfEra, month, day);
    }

    @Override
    public long toEpochDay() {
         return chrono.getGregorianEpochDay(yearOfEra, monthOfYear, dayOfMonth);
    }

    //-----------------------------------------------------------------------
    @Override
    public HijrahEra getEra() {
        return this.era;
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if the year is a leap year, according to the Hijrah calendar system rules.
     *
     * @return true if this date is in a leap year
     */
    @Override
    public boolean isLeapYear() {
        return this.isLeapYear;
    }

    //-----------------------------------------------------------------------
    @Override
    public HijrahDate plusYears(long years) {
        if (years == 0) {
            return this;
        }
        int newYear = Math.addExact(this.yearOfEra, (int)years);
        return HijrahDate.of(chrono, this.era, newYear, this.monthOfYear, this.dayOfMonth);
    }

    @Override
    public HijrahDate plusMonths(long months) {
        if (months == 0) {
            return this;
        }
        int newMonth = this.monthOfYear - 1;
        newMonth = newMonth + (int)months;
        int years = newMonth / 12;
        newMonth = newMonth % 12;
        while (newMonth < 0) {
            newMonth += 12;
            years = Math.subtractExact(years, 1);
        }
        int newYear = Math.addExact(this.yearOfEra, years);
        return HijrahDate.of(chrono, this.era, newYear, newMonth + 1, this.dayOfMonth);
    }

    @Override
    public HijrahDate plusDays(long days) {
        return new HijrahDate(chrono, this.gregorianEpochDay + days);
    }

    /**
     * Returns month days from the beginning of year.
     *
     * @param month  month (0-based)
     * @parma year  year
     * @return month days from the beginning of year
     */
    private int getMonthDays(int month, int year) {
        int[] newMonths = chrono.getAdjustedMonthDays(year);
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
      int[] newMonths = chrono.getAdjustedMonthLength(year);
      return newMonths[month];
    }

    @Override
    public int lengthOfMonth() {
        return getMonthLength(monthOfYear - 1, yearOfEra);
    }

    @Override
    public int lengthOfYear() {
        return chrono.getYearLength(yearOfEra);  // TODO: proleptic year
    }

    //-----------------------------------------------------------------------
    private Object writeReplace() {
        return new Ser(Ser.HIJRAH_DATE_TYPE, this);
    }

    void writeExternal(ObjectOutput out) throws IOException {
        // HijrahChrono is implicit in the Hijrah_DATE_TYPE
        out.writeObject(chrono);
        out.writeInt(get(YEAR));
        out.writeByte(get(MONTH_OF_YEAR));
        out.writeByte(get(DAY_OF_MONTH));
    }

    /**
     * Replaces the date instance from the stream with a valid one.
     * ReadExternal has already read the fields and created a new instance
     * from the data.
     *
     * @return the resolved date, never null
     */
    private Object readResolve() {
        return this;
    }

    static ChronoLocalDate<HijrahChrono> readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        HijrahChrono chrono = (HijrahChrono)in.readObject();
        int year = in.readInt();
        int month = in.readByte();
        int dayOfMonth = in.readByte();
        return chrono.date(year, month, dayOfMonth);
    }

}
