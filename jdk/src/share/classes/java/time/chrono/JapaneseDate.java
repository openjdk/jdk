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

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalField;
import java.time.temporal.TemporalQuery;
import java.time.temporal.TemporalUnit;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.time.temporal.ValueRange;
import java.util.Calendar;
import java.util.Objects;

import sun.util.calendar.LocalGregorianCalendar;

/**
 * A date in the Japanese Imperial calendar system.
 * <p>
 * This date operates using the {@linkplain JapaneseChronology Japanese Imperial calendar}.
 * This calendar system is primarily used in Japan.
 * <p>
 * The Japanese Imperial calendar system is the same as the ISO calendar system
 * apart from the era-based year numbering. The proleptic-year is defined to be
 * equal to the ISO proleptic-year.
 * <p>
 * For example, the Japanese year "Heisei 24" corresponds to ISO year "2012".<br>
 * Calling {@code japaneseDate.get(YEAR_OF_ERA)} will return 24.<br>
 * Calling {@code japaneseDate.get(YEAR)} will return 2012.<br>
 * Calling {@code japaneseDate.get(ERA)} will return 2, corresponding to
 * {@code JapaneseChronology.ERA_HEISEI}.<br>
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 *
 * @since 1.8
 */
public final class JapaneseDate
        extends ChronoDateImpl<JapaneseDate>
        implements ChronoLocalDate<JapaneseDate>, Serializable {

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -305327627230580483L;

    /**
     * The underlying ISO local date.
     */
    private transient final LocalDate isoDate;
    /**
     * The JapaneseEra of this date.
     */
    private transient JapaneseEra era;
    /**
     * The Japanese imperial calendar year of this date.
     */
    private transient int yearOfEra;

    //-----------------------------------------------------------------------
    /**
     * Obtains the current {@code JapaneseDate} from the system clock in the default time-zone.
     * <p>
     * This will query the {@link Clock#systemDefaultZone() system clock} in the default
     * time-zone to obtain the current date.
     * <p>
     * Using this method will prevent the ability to use an alternate clock for testing
     * because the clock is hard-coded.
     *
     * @return the current date using the system clock and default time-zone, not null
     */
    public static JapaneseDate now() {
        return now(Clock.systemDefaultZone());
    }

    /**
     * Obtains the current {@code JapaneseDate} from the system clock in the specified time-zone.
     * <p>
     * This will query the {@link Clock#system(ZoneId) system clock} to obtain the current date.
     * Specifying the time-zone avoids dependence on the default time-zone.
     * <p>
     * Using this method will prevent the ability to use an alternate clock for testing
     * because the clock is hard-coded.
     *
     * @param zone  the zone ID to use, not null
     * @return the current date using the system clock, not null
     */
    public static JapaneseDate now(ZoneId zone) {
        return now(Clock.system(zone));
    }

    /**
     * Obtains the current {@code JapaneseDate} from the specified clock.
     * <p>
     * This will query the specified clock to obtain the current date - today.
     * Using this method allows the use of an alternate clock for testing.
     * The alternate clock may be introduced using {@linkplain Clock dependency injection}.
     *
     * @param clock  the clock to use, not null
     * @return the current date, not null
     * @throws DateTimeException if the current date cannot be obtained
     */
    public static JapaneseDate now(Clock clock) {
        return JapaneseChronology.INSTANCE.date(LocalDate.now(clock));
    }

    /**
     * Obtains a {@code JapaneseDate} representing a date in the Japanese calendar
     * system from the era, year-of-era, month-of-year and day-of-month fields.
     * <p>
     * This returns a {@code JapaneseDate} with the specified fields.
     * The day must be valid for the year and month, otherwise an exception will be thrown.
     *
     * @param era  the Japanese era, not null
     * @param yearOfEra  the Japanese year-of-era
     * @param month  the Japanese month-of-year, from 1 to 12
     * @param dayOfMonth  the Japanese day-of-month, from 1 to 31
     * @return the date in Japanese calendar system, not null
     * @throws DateTimeException if the value of any field is out of range,
     *  or if the day-of-month is invalid for the month-year,
     *  or if the date is not a Japanese era
     */
    public static JapaneseDate of(Era era, int yearOfEra, int month, int dayOfMonth) {
        if (era instanceof JapaneseEra == false) {
            throw new ClassCastException("Era must be JapaneseEra");
        }
        return JapaneseDate.of((JapaneseEra) era, yearOfEra, month, dayOfMonth);
    }

    /**
     * Obtains a {@code JapaneseDate} representing a date in the Japanese calendar
     * system from the proleptic-year, month-of-year and day-of-month fields.
     * <p>
     * This returns a {@code JapaneseDate} with the specified fields.
     * The day must be valid for the year and month, otherwise an exception will be thrown.
     *
     * @param prolepticYear  the Japanese proleptic-year
     * @param month  the Japanese month-of-year, from 1 to 12
     * @param dayOfMonth  the Japanese day-of-month, from 1 to 31
     * @return the date in Japanese calendar system, not null
     * @throws DateTimeException if the value of any field is out of range,
     *  or if the day-of-month is invalid for the month-year
     */
    public static JapaneseDate of(int prolepticYear, int month, int dayOfMonth) {
        return new JapaneseDate(LocalDate.of(prolepticYear, month, dayOfMonth));
    }

    /**
     * Obtains a {@code JapaneseDate} representing a date in the Japanese calendar
     * system from the proleptic-year and day-of-year fields.
     * <p>
     * This returns a {@code JapaneseDate} with the specified fields.
     * The day must be valid for the year, otherwise an exception will be thrown.
     *
     * @param prolepticYear  the chronology proleptic-year
     * @param dayOfYear  the chronology day-of-year, from 1 to 366
     * @return the date in Japanese calendar system, not null
     * @throws DateTimeException if the value of any field is out of range,
     *  or if the day-of-year is invalid for the year
     */
    public static JapaneseDate ofYearDay(int prolepticYear, int dayOfYear) {
        LocalDate date = LocalDate.ofYearDay(prolepticYear, dayOfYear);
        return of(prolepticYear, date.getMonthValue(), date.getDayOfMonth());
    }

    /**
     * Obtains a {@code JapaneseDate} representing a date in the Japanese calendar
     * system from the era, year-of-era, month-of-year and day-of-month fields.
     * <p>
     * This returns a {@code JapaneseDate} with the specified fields.
     * The day must be valid for the year and month, otherwise an exception will be thrown.
     *
     * @param era  the Japanese era, not null
     * @param yearOfEra  the Japanese year-of-era
     * @param month  the Japanese month-of-year, from 1 to 12
     * @param dayOfMonth  the Japanese day-of-month, from 1 to 31
     * @return the date in Japanese calendar system, not null
     * @throws DateTimeException if the value of any field is out of range,
     *  or if the day-of-month is invalid for the month-year
     */
    static JapaneseDate of(JapaneseEra era, int yearOfEra, int month, int dayOfMonth) {
        Objects.requireNonNull(era, "era");
        LocalGregorianCalendar.Date jdate = JapaneseChronology.JCAL.newCalendarDate(null);
        jdate.setEra(era.getPrivateEra()).setDate(yearOfEra, month, dayOfMonth);
        if (!JapaneseChronology.JCAL.validate(jdate)) {
            throw new DateTimeException("year, month, and day not valid for Era");
        }
        LocalDate date = LocalDate.of(jdate.getNormalizedYear(), month, dayOfMonth);
        return new JapaneseDate(era, yearOfEra, date);
    }

    /**
     * Obtains a {@code JapaneseDate} from a temporal object.
     * <p>
     * This obtains a date in the Japanese calendar system based on the specified temporal.
     * A {@code TemporalAccessor} represents an arbitrary set of date and time information,
     * which this factory converts to an instance of {@code JapaneseDate}.
     * <p>
     * The conversion typically uses the {@link ChronoField#EPOCH_DAY EPOCH_DAY}
     * field, which is standardized across calendar systems.
     * <p>
     * This method matches the signature of the functional interface {@link TemporalQuery}
     * allowing it to be used as a query via method reference, {@code JapaneseDate::from}.
     *
     * @param temporal  the temporal object to convert, not null
     * @return the date in Japanese calendar system, not null
     * @throws DateTimeException if unable to convert to a {@code JapaneseDate}
     */
    public static JapaneseDate from(TemporalAccessor temporal) {
        return JapaneseChronology.INSTANCE.date(temporal);
    }

    //-----------------------------------------------------------------------
    /**
     * Creates an instance from an ISO date.
     *
     * @param isoDate  the standard local date, validated not null
     */
    JapaneseDate(LocalDate isoDate) {
        LocalGregorianCalendar.Date jdate = toPrivateJapaneseDate(isoDate);
        this.era = JapaneseEra.toJapaneseEra(jdate.getEra());
        this.yearOfEra = jdate.getYear();
        this.isoDate = isoDate;
    }

    /**
     * Constructs a {@code JapaneseDate}. This constructor does NOT validate the given parameters,
     * and {@code era} and {@code year} must agree with {@code isoDate}.
     *
     * @param era  the era, validated not null
     * @param year  the year-of-era, validated
     * @param isoDate  the standard local date, validated not null
     */
    JapaneseDate(JapaneseEra era, int year, LocalDate isoDate) {
        this.era = era;
        this.yearOfEra = year;
        this.isoDate = isoDate;
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the chronology of this date, which is the Japanese calendar system.
     * <p>
     * The {@code Chronology} represents the calendar system in use.
     * The era and other fields in {@link ChronoField} are defined by the chronology.
     *
     * @return the Japanese chronology, not null
     */
    @Override
    public JapaneseChronology getChronology() {
        return JapaneseChronology.INSTANCE;
    }

    /**
     * Gets the era applicable at this date.
     * <p>
     * The Japanese calendar system has multiple eras defined by {@link JapaneseEra}.
     *
     * @return the era applicable at this date, not null
     */
    @Override
    public JapaneseEra getEra() {
        return era;
    }

    /**
     * Returns the length of the month represented by this date.
     * <p>
     * This returns the length of the month in days.
     * Month lengths match those of the ISO calendar system.
     *
     * @return the length of the month in days
     */
    @Override
    public int lengthOfMonth() {
        return isoDate.lengthOfMonth();
    }

    //-----------------------------------------------------------------------
    @Override
    public ValueRange range(TemporalField field) {
        if (field instanceof ChronoField) {
            if (isSupported(field)) {
                ChronoField f = (ChronoField) field;
                switch (f) {
                    case DAY_OF_MONTH:
                    case ALIGNED_WEEK_OF_MONTH:
                        return isoDate.range(field);
                    case DAY_OF_YEAR:
                        return actualRange(Calendar.DAY_OF_YEAR);
                    case YEAR_OF_ERA:
                        return actualRange(Calendar.YEAR);
                }
                return getChronology().range(f);
            }
            throw new UnsupportedTemporalTypeException("Unsupported field: " + field.getName());
        }
        return field.rangeRefinedBy(this);
    }

    private ValueRange actualRange(int calendarField) {
        Calendar jcal = Calendar.getInstance(JapaneseChronology.LOCALE);
        jcal.set(Calendar.ERA, era.getValue() + JapaneseEra.ERA_OFFSET);  // TODO: cannot calculate this way for SEIREKI
        jcal.set(yearOfEra, isoDate.getMonthValue() - 1, isoDate.getDayOfMonth());
        return ValueRange.of(jcal.getActualMinimum(calendarField),
                jcal.getActualMaximum(calendarField));
    }

    @Override
    public long getLong(TemporalField field) {
        if (field instanceof ChronoField) {
            // same as ISO:
            // DAY_OF_WEEK, ALIGNED_DAY_OF_WEEK_IN_MONTH, DAY_OF_MONTH, EPOCH_DAY,
            // ALIGNED_WEEK_OF_MONTH, MONTH_OF_YEAR, PROLEPTIC_MONTH, YEAR
            //
            // calendar specific fields
            // ALIGNED_DAY_OF_WEEK_IN_YEAR, DAY_OF_YEAR, ALIGNED_WEEK_OF_YEAR, YEAR_OF_ERA, ERA
            switch ((ChronoField) field) {
                case YEAR_OF_ERA:
                    return yearOfEra;
                case ERA:
                    return era.getValue();
                case DAY_OF_YEAR: {
                    LocalGregorianCalendar.Date jdate = toPrivateJapaneseDate(isoDate);
                    return JapaneseChronology.JCAL.getDayOfYear(jdate);
                }
                // TODO: ALIGNED_DAY_OF_WEEK_IN_YEAR and ALIGNED_WEEK_OF_YEAR ???
            }
            return isoDate.getLong(field);
        }
        return field.getFrom(this);
    }

    /**
     * Returns a {@code LocalGregorianCalendar.Date} converted from the given {@code isoDate}.
     *
     * @param isoDate  the local date, not null
     * @return a {@code LocalGregorianCalendar.Date}, not null
     */
    private static LocalGregorianCalendar.Date toPrivateJapaneseDate(LocalDate isoDate) {
        LocalGregorianCalendar.Date jdate = JapaneseChronology.JCAL.newCalendarDate(null);
        sun.util.calendar.Era sunEra = JapaneseEra.privateEraFrom(isoDate);
        int year = isoDate.getYear();
        if (sunEra != null) {
            year -= sunEra.getSinceDate().getYear() - 1;
        }
        jdate.setEra(sunEra).setYear(year).setMonth(isoDate.getMonthValue()).setDayOfMonth(isoDate.getDayOfMonth());
        JapaneseChronology.JCAL.normalize(jdate);
        return jdate;
    }

    //-----------------------------------------------------------------------
    @Override
    public JapaneseDate with(TemporalField field, long newValue) {
        if (field instanceof ChronoField) {
            ChronoField f = (ChronoField) field;
            if (getLong(f) == newValue) {
                return this;
            }
            switch (f) {
                case YEAR_OF_ERA:
                case YEAR:
                case ERA: {
                    int nvalue = getChronology().range(f).checkValidIntValue(newValue, f);
                    switch (f) {
                        case YEAR_OF_ERA:
                            return this.withYear(nvalue);
                        case YEAR:
                            return with(isoDate.withYear(nvalue));
                        case ERA: {
                            return this.withYear(JapaneseEra.of(nvalue), yearOfEra);
                        }
                    }
                }
            }
            // YEAR, PROLEPTIC_MONTH and others are same as ISO
            // TODO: review other fields, such as WEEK_OF_YEAR
            return with(isoDate.with(field, newValue));
        }
        return ChronoLocalDate.super.with(field, newValue);
    }

    /**
     * {@inheritDoc}
     * @throws DateTimeException {@inheritDoc}
     * @throws ArithmeticException {@inheritDoc}
     */
    @Override
    public  JapaneseDate with(TemporalAdjuster adjuster) {
        return super.with(adjuster);
    }

    /**
     * {@inheritDoc}
     * @throws DateTimeException {@inheritDoc}
     * @throws ArithmeticException {@inheritDoc}
     */
    @Override
    public JapaneseDate plus(TemporalAmount amount) {
        return super.plus(amount);
    }

    /**
     * {@inheritDoc}
     * @throws DateTimeException {@inheritDoc}
     * @throws ArithmeticException {@inheritDoc}
     */
    @Override
    public JapaneseDate minus(TemporalAmount amount) {
        return super.minus(amount);
    }
    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this date with the year altered.
     * <p>
     * This method changes the year of the date.
     * If the month-day is invalid for the year, then the previous valid day
     * will be selected instead.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param era  the era to set in the result, not null
     * @param yearOfEra  the year-of-era to set in the returned date
     * @return a {@code JapaneseDate} based on this date with the requested year, never null
     * @throws DateTimeException if {@code year} is invalid
     */
    private JapaneseDate withYear(JapaneseEra era, int yearOfEra) {
        int year = JapaneseChronology.INSTANCE.prolepticYear(era, yearOfEra);
        return with(isoDate.withYear(year));
    }

    /**
     * Returns a copy of this date with the year-of-era altered.
     * <p>
     * This method changes the year-of-era of the date.
     * If the month-day is invalid for the year, then the previous valid day
     * will be selected instead.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param year  the year to set in the returned date
     * @return a {@code JapaneseDate} based on this date with the requested year-of-era, never null
     * @throws DateTimeException if {@code year} is invalid
     */
    private JapaneseDate withYear(int year) {
        return withYear(getEra(), year);
    }

    //-----------------------------------------------------------------------
    @Override
    JapaneseDate plusYears(long years) {
        return with(isoDate.plusYears(years));
    }

    @Override
    JapaneseDate plusMonths(long months) {
        return with(isoDate.plusMonths(months));
    }

    @Override
    JapaneseDate plusWeeks(long weeksToAdd) {
        return with(isoDate.plusWeeks(weeksToAdd));
    }

    @Override
    JapaneseDate plusDays(long days) {
        return with(isoDate.plusDays(days));
    }

    @Override
    public JapaneseDate plus(long amountToAdd, TemporalUnit unit) {
        return super.plus(amountToAdd, unit);
    }

    @Override
    public JapaneseDate minus(long amountToAdd, TemporalUnit unit) {
        return super.minus(amountToAdd, unit);
    }

    @Override
    JapaneseDate minusYears(long yearsToSubtract) {
        return super.minusYears(yearsToSubtract);
    }

    @Override
    JapaneseDate minusMonths(long monthsToSubtract) {
        return super.minusMonths(monthsToSubtract);
    }

    @Override
    JapaneseDate minusWeeks(long weeksToSubtract) {
        return super.minusWeeks(weeksToSubtract);
    }

    @Override
    JapaneseDate minusDays(long daysToSubtract) {
        return super.minusDays(daysToSubtract);
    }

    private JapaneseDate with(LocalDate newDate) {
        return (newDate.equals(isoDate) ? this : new JapaneseDate(newDate));
    }

    @Override        // for javadoc and covariant return type
    public final ChronoLocalDateTime<JapaneseDate> atTime(LocalTime localTime) {
        return super.atTime(localTime);
    }

    @Override
    public Period periodUntil(ChronoLocalDate<?> endDate) {
        return isoDate.periodUntil(endDate);
    }

    @Override  // override for performance
    public long toEpochDay() {
        return isoDate.toEpochDay();
    }

    //-------------------------------------------------------------------------
    @Override  // override for performance
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof JapaneseDate) {
            JapaneseDate otherDate = (JapaneseDate) obj;
            return this.isoDate.equals(otherDate.isoDate);
        }
        return false;
    }

    @Override  // override for performance
    public int hashCode() {
        return getChronology().getId().hashCode() ^ isoDate.hashCode();
    }

    @Override
    public String toString() {
        if (era == JapaneseEra.SEIREKI) {
            return getChronology().getId() + " " + isoDate.toString();
        }
        return super.toString();
    }

    //-----------------------------------------------------------------------
    private Object writeReplace() {
        return new Ser(Ser.JAPANESE_DATE_TYPE, this);
    }

    void writeExternal(DataOutput out) throws IOException {
        // JapaneseChronology is implicit in the JAPANESE_DATE_TYPE
        out.writeInt(get(YEAR));
        out.writeByte(get(MONTH_OF_YEAR));
        out.writeByte(get(DAY_OF_MONTH));
    }

    static JapaneseDate readExternal(DataInput in) throws IOException {
        int year = in.readInt();
        int month = in.readByte();
        int dayOfMonth = in.readByte();
        return JapaneseChronology.INSTANCE.date(year, month, dayOfMonth);
    }

}
