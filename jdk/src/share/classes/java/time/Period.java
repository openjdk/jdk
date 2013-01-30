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
package java.time;

import static java.time.LocalTime.NANOS_PER_DAY;
import static java.time.LocalTime.NANOS_PER_HOUR;
import static java.time.LocalTime.NANOS_PER_MINUTE;
import static java.time.LocalTime.NANOS_PER_SECOND;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.EPOCH_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_DAY;
import static java.time.temporal.ChronoField.YEAR;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.NANOS;
import static java.time.temporal.ChronoUnit.YEARS;

import java.io.Serializable;
import java.time.format.DateTimeParseException;
import java.time.temporal.Chrono;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdder;
import java.time.temporal.TemporalSubtractor;
import java.time.temporal.TemporalUnit;
import java.time.temporal.ValueRange;
import java.util.Objects;

/**
 * A period of time, measured using the most common units, such as '3 Months, 4 Days and 7 Hours'.
 * <p>
 * A {@code Period} represents an amount of time measured in terms of the most commonly used units:
 * <p><ul>
 * <li>{@link ChronoUnit#YEARS YEARS}</li>
 * <li>{@link ChronoUnit#MONTHS MONTHS}</li>
 * <li>{@link ChronoUnit#DAYS DAYS}</li>
 * <li>time units with an {@linkplain TemporalUnit#isDurationEstimated() exact duration}</li>
 * </ul><p>
 * The period may be used with any calendar system with the exception is methods with an "ISO" suffix.
 * The meaning of a "year" or a "month" is only applied when the object is added to a date.
 * <p>
 * The period is modeled as a directed amount of time, meaning that individual parts of the
 * period may be negative.
 *
 * <h3>Specification for implementors</h3>
 * This class is immutable and thread-safe.
 * The maximum number of hours that can be stored is about 2.5 million, limited by storing
 * a single {@code long} nanoseconds for all time units internally.
 *
 * @since 1.8
 */
public final class Period
        implements TemporalAdder, TemporalSubtractor, Serializable {
    // maximum hours is 2,562,047

    /**
     * A constant for a period of zero.
     */
    public static final Period ZERO = new Period(0, 0, 0, 0);
    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -8290556941213247973L;

    /**
     * The number of years.
     */
    private final int years;
    /**
     * The number of months.
     */
    private final int months;
    /**
     * The number of days.
     */
    private final int days;
    /**
     * The number of nanoseconds.
     */
    private final long nanos;

    //-----------------------------------------------------------------------
    /**
     * Obtains a {@code Period} from date-based and time-based fields.
     * <p>
     * This creates an instance based on years, months, days, hours, minutes and seconds.
     * Within a period, the time fields are always normalized.
     *
     * @param years  the amount of years, may be negative
     * @param months  the amount of months, may be negative
     * @param days  the amount of days, may be negative
     * @param hours  the amount of hours, may be negative
     * @param minutes  the amount of minutes, may be negative
     * @param seconds  the amount of seconds, may be negative
     * @return the period, not null
     */
    public static Period of(int years, int months, int days, int hours, int minutes, int seconds) {
        return of(years, months, days, hours, minutes, seconds, 0);
    }

    /**
     * Obtains a {@code Period} from date-based and time-based fields.
     * <p>
     * This creates an instance based on years, months, days, hours, minutes, seconds and nanoseconds.
     * Within a period, the time fields are always normalized.
     *
     * @param years  the amount of years, may be negative
     * @param months  the amount of months, may be negative
     * @param days  the amount of days, may be negative
     * @param hours  the amount of hours, may be negative
     * @param minutes  the amount of minutes, may be negative
     * @param seconds  the amount of seconds, may be negative
     * @param nanos  the amount of nanos, may be negative
     * @return the period, not null
     */
    public static Period of(int years, int months, int days, int hours, int minutes, int seconds, long nanos) {
        if ((years | months | days | hours | minutes | seconds | nanos) == 0) {
            return ZERO;
        }
        long totSecs = Math.addExact(hours * 3600L, minutes * 60L) + seconds;
        long totNanos = Math.addExact(Math.multiplyExact(totSecs, 1_000_000_000L), nanos);
        return create(years, months, days, totNanos);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains a {@code Period} from date-based fields.
     * <p>
     * This creates an instance based on years, months and days.
     *
     * @param years  the amount of years, may be negative
     * @param months  the amount of months, may be negative
     * @param days  the amount of days, may be negative
     * @return the period, not null
     */
    public static Period ofDate(int years, int months, int days) {
        return of(years, months, days, 0, 0, 0, 0);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains a {@code Period} from time-based fields.
     * <p>
     * This creates an instance based on hours, minutes and seconds.
     * Within a period, the time fields are always normalized.
     *
     * @param hours  the amount of hours, may be negative
     * @param minutes  the amount of minutes, may be negative
     * @param seconds  the amount of seconds, may be negative
     * @return the period, not null
     */
    public static Period ofTime(int hours, int minutes, int seconds) {
        return of(0, 0, 0, hours, minutes, seconds, 0);
    }

    /**
     * Obtains a {@code Period} from time-based fields.
     * <p>
     * This creates an instance based on hours, minutes, seconds and nanoseconds.
     * Within a period, the time fields are always normalized.
     *
     * @param hours  the amount of hours, may be negative
     * @param minutes  the amount of minutes, may be negative
     * @param seconds  the amount of seconds, may be negative
     * @param nanos  the amount of nanos, may be negative
     * @return the period, not null
     */
    public static Period ofTime(int hours, int minutes, int seconds, long nanos) {
        return of(0, 0, 0, hours, minutes, seconds, nanos);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains an instance of {@code Period} from a period in the specified unit.
     * <p>
     * The parameters represent the two parts of a phrase like '6 Days'. For example:
     * <pre>
     *  Period.of(3, SECONDS);
     *  Period.of(5, YEARS);
     * </pre>
     * The specified unit must be one of the supported units from {@link ChronoUnit},
     * {@code YEARS}, {@code MONTHS} or {@code DAYS} or be a time unit with an
     * {@linkplain TemporalUnit#isDurationEstimated() exact duration}.
     * Other units throw an exception.
     *
     * @param amount  the amount of the period, measured in terms of the unit, positive or negative
     * @param unit  the unit that the period is measured in, must have an exact duration, not null
     * @return the period, not null
     * @throws DateTimeException if the period unit is invalid
     * @throws ArithmeticException if a numeric overflow occurs
     */
    public static Period of(long amount, TemporalUnit unit) {
        return ZERO.plus(amount, unit);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains a {@code Period} from a {@code Duration}.
     * <p>
     * This converts the duration to a period.
     * Within a period, the time fields are always normalized.
     * The years, months and days fields will be zero.
     * <p>
     * To populate the days field, call {@link #normalizedHoursToDays()} on the created period.
     *
     * @param duration  the duration to convert, not null
     * @return the period, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public static Period of(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isZero()) {
            return ZERO;
        }
        return new Period(0, 0, 0, duration.toNanos());
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a {@code Period} consisting of the number of years, months, days,
     * hours, minutes, seconds, and nanoseconds between two {@code TemporalAccessor} instances.
     * <p>
     * The start date is included, but the end date is not. Only whole years count.
     * For example, from {@code 2010-01-15} to {@code 2011-03-18} is one year, two months and three days.
     * <p>
     * This method examines the {@link ChronoField fields} {@code YEAR}, {@code MONTH_OF_YEAR},
     * {@code DAY_OF_MONTH} and {@code NANO_OF_DAY}
     * The difference between each of the fields is calculated independently from the others.
     * At least one of the four fields must be present.
     * <p>
     * The four units are typically retained without normalization.
     * However, years and months are normalized if the range of months is fixed, as it is with ISO.
     * <p>
     * The result of this method can be a negative period if the end is before the start.
     * The negative sign can be different in each of the four major units.
     *
     * @param start  the start date, inclusive, not null
     * @param end  the end date, exclusive, not null
     * @return the period between the date-times, not null
     * @throws DateTimeException if the two date-times do have similar available fields
     * @throws ArithmeticException if numeric overflow occurs
     */
    public static Period between(TemporalAccessor start, TemporalAccessor end) {
        if (Chrono.from(start).equals(Chrono.from(end)) == false) {
            throw new DateTimeException("Unable to calculate period as date-times have different chronologies");
        }
        int years = 0;
        int months = 0;
        int days = 0;
        long nanos = 0;
        boolean valid = false;
        if (start.isSupported(YEAR)) {
            years = Math.toIntExact(Math.subtractExact(end.getLong(YEAR), start.getLong(YEAR)));
            valid = true;
        }
        if (start.isSupported(MONTH_OF_YEAR)) {
            months = Math.toIntExact(Math.subtractExact(end.getLong(MONTH_OF_YEAR), start.getLong(MONTH_OF_YEAR)));
            ValueRange startRange = Chrono.from(start).range(MONTH_OF_YEAR);
            ValueRange endRange = Chrono.from(end).range(MONTH_OF_YEAR);
            if (startRange.isFixed() && startRange.isIntValue() && startRange.equals(endRange)) {
                int monthCount = (int) (startRange.getMaximum() - startRange.getMinimum() + 1);
                long totMonths = ((long) months) + years * monthCount;
                months = (int) (totMonths % monthCount);
                years = Math.toIntExact(totMonths / monthCount);
            }
            valid = true;
        }
        if (start.isSupported(DAY_OF_MONTH)) {
            days = Math.toIntExact(Math.subtractExact(end.getLong(DAY_OF_MONTH), start.getLong(DAY_OF_MONTH)));
            valid = true;
        }
        if (start.isSupported(NANO_OF_DAY)) {
            nanos = Math.subtractExact(end.getLong(NANO_OF_DAY), start.getLong(NANO_OF_DAY));
            valid = true;
        }
        if (valid == false) {
            throw new DateTimeException("Unable to calculate period as date-times do not have any valid fields");
        }
        return create(years, months, days, nanos);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains a {@code Period} consisting of the number of years, months,
     * and days between two dates.
     * <p>
     * The start date is included, but the end date is not.
     * The period is calculated by removing complete months, then calculating
     * the remaining number of days, adjusting to ensure that both have the same sign.
     * The number of months is then split into years and months based on a 12 month year.
     * A month is considered if the end day-of-month is greater than or equal to the start day-of-month.
     * For example, from {@code 2010-01-15} to {@code 2011-03-18} is one year, two months and three days.
     * <p>
     * The result of this method can be a negative period if the end is before the start.
     * The negative sign will be the same in each of year, month and day.
     *
     * @param startDate  the start date, inclusive, not null
     * @param endDate  the end date, exclusive, not null
     * @return the period between the dates, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public static Period betweenISO(LocalDate startDate, LocalDate endDate) {
        long startMonth = startDate.getLong(EPOCH_MONTH);
        long endMonth = endDate.getLong(EPOCH_MONTH);
        long totalMonths = endMonth - startMonth;  // safe
        int days = endDate.getDayOfMonth() - startDate.getDayOfMonth();
        if (totalMonths > 0 && days < 0) {
            totalMonths--;
            LocalDate calcDate = startDate.plusMonths(totalMonths);
            days = (int) (endDate.toEpochDay() - calcDate.toEpochDay());  // safe
        } else if (totalMonths < 0 && days > 0) {
            totalMonths++;
            days -= endDate.lengthOfMonth();
        }
        long years = totalMonths / 12;  // safe
        int months = (int) (totalMonths % 12);  // safe
        return ofDate(Math.toIntExact(years), months, days);
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains a {@code Period} consisting of the number of hours, minutes,
     * seconds and nanoseconds between two times.
     * <p>
     * The start time is included, but the end time is not.
     * The period is calculated from the difference between the nano-of-day values
     * of the two times. For example, from {@code 13:45:00} to {@code 14:50:30.123456789}
     * is {@code P1H5M30.123456789S}.
     * <p>
     * The result of this method can be a negative period if the end is before the start.
     *
     * @param startTime  the start time, inclusive, not null
     * @param endTime  the end time, exclusive, not null
     * @return the period between the times, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public static Period betweenISO(LocalTime startTime, LocalTime endTime) {
        return create(0, 0, 0, endTime.toNanoOfDay() - startTime.toNanoOfDay());
    }

    //-----------------------------------------------------------------------
    /**
     * Obtains a {@code Period} from a text string such as {@code PnYnMnDTnHnMn.nS}.
     * <p>
     * This will parse the string produced by {@code toString()} which is
     * a subset of the ISO-8601 period format {@code PnYnMnDTnHnMn.nS}.
     * <p>
     * The string consists of a series of numbers with a suffix identifying their meaning.
     * The values, and suffixes, must be in the sequence year, month, day, hour, minute, second.
     * Any of the number/suffix pairs may be omitted providing at least one is present.
     * If the period is zero, the value is normally represented as {@code PT0S}.
     * The numbers must consist of ASCII digits.
     * Any of the numbers may be negative. Negative zero is not accepted.
     * The number of nanoseconds is expressed as an optional fraction of the seconds.
     * There must be at least one digit before any decimal point.
     * There must be between 1 and 9 inclusive digits after any decimal point.
     * The letters will all be accepted in upper or lower case.
     * The decimal point may be either a dot or a comma.
     *
     * @param text  the text to parse, not null
     * @return the parsed period, not null
     * @throws DateTimeParseException if the text cannot be parsed to a period
     */
    public static Period parse(final CharSequence text) {
        Objects.requireNonNull(text, "text");
        return new PeriodParser(text).parse();
    }

    //-----------------------------------------------------------------------
    /**
     * Creates an instance.
     *
     * @param years  the amount
     * @param months  the amount
     * @param days  the amount
     * @param nanos  the amount
     */
    private static Period create(int years, int months, int days, long nanos) {
        if ((years | months | days | nanos) == 0) {
            return ZERO;
        }
        return new Period(years, months, days, nanos);
    }

    /**
     * Constructor.
     *
     * @param years  the amount
     * @param months  the amount
     * @param days  the amount
     * @param nanos  the amount
     */
    private Period(int years, int months, int days, long nanos) {
        this.years = years;
        this.months = months;
        this.days = days;
        this.nanos = nanos;
    }

    /**
     * Resolves singletons.
     *
     * @return the resolved instance
     */
    private Object readResolve() {
        if ((years | months | days | nanos) == 0) {
            return ZERO;
        }
        return this;
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if this period is zero-length.
     *
     * @return true if this period is zero-length
     */
    public boolean isZero() {
        return (this == ZERO);
    }

    /**
     * Checks if this period is fully positive, excluding zero.
     * <p>
     * This checks whether all the amounts in the period are positive,
     * defined as greater than zero.
     *
     * @return true if this period is fully positive excluding zero
     */
    public boolean isPositive() {
        return ((years | months | days | nanos) > 0);
    }

    //-----------------------------------------------------------------------
    /**
     * Gets the amount of years of this period.
     *
     * @return the amount of years of this period
     */
    public int getYears() {
        return years;
    }

    /**
     * Gets the amount of months of this period.
     *
     * @return the amount of months of this period
     */
    public int getMonths() {
        return months;
    }

    /**
     * Gets the amount of days of this period.
     *
     * @return the amount of days of this period
     */
    public int getDays() {
        return days;
    }

    /**
     * Gets the amount of hours of this period.
     * <p>
     * Within a period, the time fields are always normalized.
     *
     * @return the amount of hours of this period
     */
    public int getHours() {
        return (int) (nanos / NANOS_PER_HOUR);
    }

    /**
     * Gets the amount of minutes within an hour of this period.
     * <p>
     * Within a period, the time fields are always normalized.
     *
     * @return the amount of minutes within an hour of this period
     */
    public int getMinutes() {
        return (int) ((nanos / NANOS_PER_MINUTE) % 60);
    }

    /**
     * Gets the amount of seconds within a minute of this period.
     * <p>
     * Within a period, the time fields are always normalized.
     *
     * @return the amount of seconds within a minute of this period
     */
    public int getSeconds() {
        return (int) ((nanos / NANOS_PER_SECOND) % 60);
    }

    /**
     * Gets the amount of nanoseconds within a second of this period.
     * <p>
     * Within a period, the time fields are always normalized.
     *
     * @return the amount of nanoseconds within a second of this period
     */
    public int getNanos() {
        return (int) (nanos % NANOS_PER_SECOND);  // safe from overflow
    }

    /**
     * Gets the total amount of the time units of this period, measured in nanoseconds.
     * <p>
     * Within a period, the time fields are always normalized.
     *
     * @return the total amount of time unit nanoseconds of this period
     */
    public long getTimeNanos() {
        return nanos;
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this period with the specified amount of years.
     * <p>
     * This method will only affect the years field.
     * All other units are unaffected.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param years  the years to represent
     * @return a {@code Period} based on this period with the requested years, not null
     */
    public Period withYears(int years) {
        if (years == this.years) {
            return this;
        }
        return create(years, months, days, nanos);
    }

    /**
     * Returns a copy of this period with the specified amount of months.
     * <p>
     * This method will only affect the months field.
     * All other units are unaffected.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param months  the months to represent
     * @return a {@code Period} based on this period with the requested months, not null
     */
    public Period withMonths(int months) {
        if (months == this.months) {
            return this;
        }
        return create(years, months, days, nanos);
    }

    /**
     * Returns a copy of this period with the specified amount of days.
     * <p>
     * This method will only affect the days field.
     * All other units are unaffected.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param days  the days to represent
     * @return a {@code Period} based on this period with the requested days, not null
     */
    public Period withDays(int days) {
        if (days == this.days) {
            return this;
        }
        return create(years, months, days, nanos);
    }

    /**
     * Returns a copy of this period with the specified total amount of time units
     * expressed in nanoseconds.
     * <p>
     * Within a period, the time fields are always normalized.
     * This method will affect all the time units - hours, minutes, seconds and nanos.
     * The date units are unaffected.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param nanos  the nanoseconds to represent
     * @return a {@code Period} based on this period with the requested nanoseconds, not null
     */
    public Period withTimeNanos(long nanos) {
        if (nanos == this.nanos) {
            return this;
        }
        return create(years, months, days, nanos);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this period with the specified period added.
     * <p>
     * This operates separately on the years, months, days and the normalized time.
     * There is no further normalization beyond the normalized time.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param other  the period to add, not null
     * @return a {@code Period} based on this period with the requested period added, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period plus(Period other) {
        return create(
                Math.addExact(years, other.years),
                Math.addExact(months, other.months),
                Math.addExact(days, other.days),
                Math.addExact(nanos, other.nanos));
    }

    /**
     * Returns a copy of this period with the specified period added.
     * <p>
     * The specified unit must be one of the supported units from {@link ChronoUnit},
     * {@code YEARS}, {@code MONTHS} or {@code DAYS} or be a time unit with an
     * {@linkplain TemporalUnit#isDurationEstimated() exact duration}.
     * Other units throw an exception.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param amount  the amount to add, positive or negative
     * @param unit  the unit that the amount is expressed in, not null
     * @return a {@code Period} based on this period with the requested amount added, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period plus(long amount, TemporalUnit unit) {
        Objects.requireNonNull(unit, "unit");
        if (unit instanceof ChronoUnit) {
            if (unit == YEARS || unit == MONTHS || unit == DAYS || unit.isDurationEstimated() == false) {
                if (amount == 0) {
                    return this;
                }
                switch((ChronoUnit) unit) {
                    case NANOS: return plusNanos(amount);
                    case MICROS: return plusNanos(Math.multiplyExact(amount, 1000L));
                    case MILLIS: return plusNanos(Math.multiplyExact(amount, 1000_000L));
                    case SECONDS: return plusSeconds(amount);
                    case MINUTES: return plusMinutes(amount);
                    case HOURS: return plusHours(amount);
                    case HALF_DAYS: return plusNanos(Math.multiplyExact(amount, 12 * NANOS_PER_HOUR));
                    case DAYS: return plusDays(amount);
                    case MONTHS: return plusMonths(amount);
                    case YEARS: return plusYears(amount);
                    default: throw new DateTimeException("Unsupported unit: " + unit.getName());
                }
            }
        }
        if (unit.isDurationEstimated()) {
            throw new DateTimeException("Unsupported unit: " + unit.getName());
        }
        return plusNanos(Duration.of(amount, unit).toNanos());
    }

    public Period plusYears(long amount) {
        return create(Math.toIntExact(Math.addExact(years, amount)), months, days, nanos);
    }

    public Period plusMonths(long amount) {
        return create(years, Math.toIntExact(Math.addExact(months, amount)), days, nanos);
    }

    public Period plusDays(long amount) {
        return create(years, months, Math.toIntExact(Math.addExact(days, amount)), nanos);
    }

    public Period plusHours(long amount) {
        return plusNanos(Math.multiplyExact(amount, NANOS_PER_HOUR));
    }

    public Period plusMinutes(long amount) {
        return plusNanos(Math.multiplyExact(amount, NANOS_PER_MINUTE));
    }

    public Period plusSeconds(long amount) {
        return plusNanos(Math.multiplyExact(amount, NANOS_PER_SECOND));
    }

    public Period plusNanos(long amount) {
        return create(years, months, days, Math.addExact(nanos,  amount));
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this period with the specified period subtracted.
     * <p>
     * This operates separately on the years, months, days and the normalized time.
     * There is no further normalization beyond the normalized time.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param other  the period to subtract, not null
     * @return a {@code Period} based on this period with the requested period subtracted, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period minus(Period other) {
        return create(
                Math.subtractExact(years, other.years),
                Math.subtractExact(months, other.months),
                Math.subtractExact(days, other.days),
                Math.subtractExact(nanos, other.nanos));
    }

    /**
     * Returns a copy of this period with the specified period subtracted.
     * <p>
     * The specified unit must be one of the supported units from {@link ChronoUnit},
     * {@code YEARS}, {@code MONTHS} or {@code DAYS} or be a time unit with an
     * {@linkplain TemporalUnit#isDurationEstimated() exact duration}.
     * Other units throw an exception.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param amount  the amount to subtract, positive or negative
     * @param unit  the unit that the amount is expressed in, not null
     * @return a {@code Period} based on this period with the requested amount subtracted, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period minus(long amount, TemporalUnit unit) {
        return (amount == Long.MIN_VALUE ? plus(Long.MAX_VALUE, unit).plus(1, unit) : plus(-amount, unit));
    }

    public Period minusYears(long amount) {
        return (amount == Long.MIN_VALUE ? plusYears(Long.MAX_VALUE).plusYears(1) : plusYears(-amount));
    }

    public Period minusMonths(long amount) {
        return (amount == Long.MIN_VALUE ? plusMonths(Long.MAX_VALUE).plusMonths(1) : plusMonths(-amount));
    }

    public Period minusDays(long amount) {
        return (amount == Long.MIN_VALUE ? plusDays(Long.MAX_VALUE).plusDays(1) : plusDays(-amount));
    }

    public Period minusHours(long amount) {
        return (amount == Long.MIN_VALUE ? plusHours(Long.MAX_VALUE).plusHours(1) : plusHours(-amount));
    }

    public Period minusMinutes(long amount) {
        return (amount == Long.MIN_VALUE ? plusMinutes(Long.MAX_VALUE).plusMinutes(1) : plusMinutes(-amount));
    }

    public Period minusSeconds(long amount) {
        return (amount == Long.MIN_VALUE ? plusSeconds(Long.MAX_VALUE).plusSeconds(1) : plusSeconds(-amount));
    }

    public Period minusNanos(long amount) {
        return (amount == Long.MIN_VALUE ? plusNanos(Long.MAX_VALUE).plusNanos(1) : plusNanos(-amount));
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a new instance with each element in this period multiplied
     * by the specified scalar.
     * <p>
     * This simply multiplies each field, years, months, days and normalized time,
     * by the scalar. No normalization is performed.
     *
     * @param scalar  the scalar to multiply by, not null
     * @return a {@code Period} based on this period with the amounts multiplied by the scalar, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period multipliedBy(int scalar) {
        if (this == ZERO || scalar == 1) {
            return this;
        }
        return create(
                Math.multiplyExact(years, scalar),
                Math.multiplyExact(months, scalar),
                Math.multiplyExact(days, scalar),
                Math.multiplyExact(nanos, scalar));
    }

    /**
     * Returns a new instance with each amount in this period negated.
     *
     * @return a {@code Period} based on this period with the amounts negated, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period negated() {
        return multipliedBy(-1);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns a copy of this period with the days and hours normalized using a 24 hour day.
     * <p>
     * This normalizes the days and hours units, leaving years and months unchanged.
     * The hours unit is adjusted to have an absolute value less than 23,
     * with the days unit being adjusted to compensate.
     * For example, a period of {@code P1DT27H} will be normalized to {@code P2DT3H}.
     * <p>
     * The sign of the days and hours units will be the same after normalization.
     * For example, a period of {@code P1DT-51H} will be normalized to {@code P-1DT-3H}.
     * Since all time units are always normalized, if the hours units changes sign then
     * other time units will also be affected.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @return a {@code Period} based on this period with excess hours normalized to days, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period normalizedHoursToDays() {
        // logic uses if statements to normalize signs to avoid unnecessary overflows
        long totalDays = (nanos / NANOS_PER_DAY) + days;  // no overflow
        long splitNanos = nanos % NANOS_PER_DAY;
        if (totalDays > 0 && splitNanos < 0) {
            splitNanos += NANOS_PER_DAY;
            totalDays--;
        } else if (totalDays < 0 && splitNanos > 0) {
            splitNanos -= NANOS_PER_DAY;
            totalDays++;
        }
        if (totalDays == days && splitNanos == nanos) {
            return this;
        }
        return create(years, months, Math.toIntExact(totalDays), splitNanos);
    }

    /**
     * Returns a copy of this period with any days converted to hours using a 24 hour day.
     * <p>
     * The days unit is reduced to zero, with the hours unit increased by 24 times the
     * days unit to compensate. Other units are unaffected.
     * For example, a period of {@code P2DT4H} will be normalized to {@code PT52H}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @return a {@code Period} based on this period with days normalized to hours, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period normalizedDaysToHours() {
        if (days == 0) {
            return this;
        }
        return create(years, months, 0, Math.addExact(Math.multiplyExact(days, NANOS_PER_DAY), nanos));
    }

    /**
     * Returns a copy of this period with the years and months normalized using a 12 month year.
     * <p>
     * This normalizes the years and months units, leaving other units unchanged.
     * The months unit is adjusted to have an absolute value less than 11,
     * with the years unit being adjusted to compensate.
     * For example, a period of {@code P1Y15M} will be normalized to {@code P2Y3M}.
     * <p>
     * The sign of the years and months units will be the same after normalization.
     * For example, a period of {@code P1Y-25M} will be normalized to {@code P-1Y-1M}.
     * <p>
     * This normalization uses a 12 month year it is not valid for all calendar systems.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @return a {@code Period} based on this period with years and months normalized, not null
     * @throws ArithmeticException if numeric overflow occurs
     */
    public Period normalizedMonthsISO() {
        long totalMonths = years * 12L + months;  // no overflow
        long splitYears = totalMonths / 12;
        int splitMonths = (int) (totalMonths % 12);  // no overflow
        if (splitYears == years && splitMonths == months) {
            return this;
        }
        return create(Math.toIntExact(splitYears), splitMonths, days, nanos);
    }

    //-------------------------------------------------------------------------
    /**
     * Converts this period to one that only has date units.
     * <p>
     * The resulting period will have the same years, months and days as this period
     * but the time units will all be zero. No normalization occurs in the calculation.
     * For example, a period of {@code P1Y3MT12H} will be converted to {@code P1Y3M}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @return a {@code Period} based on this period with the time units set to zero, not null
     */
    public Period toDateOnly() {
        if (nanos == 0) {
            return this;
        }
        return create(years, months, days, 0);
    }

    //-------------------------------------------------------------------------
    /**
     * Adds this period to the specified temporal object.
     * <p>
     * This returns a temporal object of the same observable type as the input
     * with this period added.
     * <p>
     * In most cases, it is clearer to reverse the calling pattern by using
     * {@link Temporal#plus(TemporalAdder)}.
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   dateTime = thisPeriod.addTo(dateTime);
     *   dateTime = dateTime.plus(thisPeriod);
     * </pre>
     * <p>
     * The calculation will add the years, then months, then days, then nanos.
     * Only non-zero amounts will be added.
     * If the date-time has a calendar system with a fixed number of months in a
     * year, then the years and months will be combined before being added.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param temporal  the temporal object to adjust, not null
     * @return an object of the same type with the adjustment made, not null
     * @throws DateTimeException if unable to add
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public Temporal addTo(Temporal temporal) {
        Objects.requireNonNull(temporal, "temporal");
        if ((years | months) != 0) {
            ValueRange startRange = Chrono.from(temporal).range(MONTH_OF_YEAR);
            if (startRange.isFixed() && startRange.isIntValue()) {
                long monthCount = startRange.getMaximum() - startRange.getMinimum() + 1;
                temporal = temporal.plus(years * monthCount + months, MONTHS);
            } else {
                if (years != 0) {
                    temporal = temporal.plus(years, YEARS);
                }
                if (months != 0) {
                    temporal = temporal.plus(months, MONTHS);
                }
            }
        }
        if (days != 0) {
            temporal = temporal.plus(days, DAYS);
        }
        if (nanos != 0) {
            temporal = temporal.plus(nanos, NANOS);
        }
        return temporal;
    }

    /**
     * Subtracts this period from the specified temporal object.
     * <p>
     * This returns a temporal object of the same observable type as the input
     * with this period subtracted.
     * <p>
     * In most cases, it is clearer to reverse the calling pattern by using
     * {@link Temporal#minus(TemporalSubtractor)}.
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   dateTime = thisPeriod.subtractFrom(dateTime);
     *   dateTime = dateTime.minus(thisPeriod);
     * </pre>
     * <p>
     * The calculation will subtract the years, then months, then days, then nanos.
     * Only non-zero amounts will be subtracted.
     * If the date-time has a calendar system with a fixed number of months in a
     * year, then the years and months will be combined before being subtracted.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @param temporal  the temporal object to adjust, not null
     * @return an object of the same type with the adjustment made, not null
     * @throws DateTimeException if unable to subtract
     * @throws ArithmeticException if numeric overflow occurs
     */
    @Override
    public Temporal subtractFrom(Temporal temporal) {
        Objects.requireNonNull(temporal, "temporal");
        if ((years | months) != 0) {
            ValueRange startRange = Chrono.from(temporal).range(MONTH_OF_YEAR);
            if (startRange.isFixed() && startRange.isIntValue()) {
                long monthCount = startRange.getMaximum() - startRange.getMinimum() + 1;
                temporal = temporal.minus(years * monthCount + months, MONTHS);
            } else {
                if (years != 0) {
                    temporal = temporal.minus(years, YEARS);
                }
                if (months != 0) {
                    temporal = temporal.minus(months, MONTHS);
                }
            }
        }
        if (days != 0) {
            temporal = temporal.minus(days, DAYS);
        }
        if (nanos != 0) {
            temporal = temporal.minus(nanos, NANOS);
        }
        return temporal;
    }

    //-----------------------------------------------------------------------
    /**
     * Converts this period to one that only has time units.
     * <p>
     * The resulting period will have the same time units as this period
     * but the date units will all be zero. No normalization occurs in the calculation.
     * For example, a period of {@code P1Y3MT12H} will be converted to {@code PT12H}.
     * <p>
     * This instance is immutable and unaffected by this method call.
     *
     * @return a {@code Period} based on this period with the date units set to zero, not null
     */
    public Period toTimeOnly() {
        if ((years | months | days) == 0) {
            return this;
        }
        return create(0, 0, 0, nanos);
    }

    //-------------------------------------------------------------------------
    /**
     * Calculates the duration of this period.
     * <p>
     * The calculation uses the hours, minutes, seconds and nanoseconds fields.
     * If years, months or days are present an exception is thrown.
     * See {@link #toTimeOnly()} for a way to remove the date units and
     * {@link #normalizedDaysToHours()} for a way to convert days to hours.
     *
     * @return a {@code Duration} equivalent to this period, not null
     * @throws DateTimeException if the period cannot be converted as it contains years, months or days
     */
    public Duration toDuration() {
        if ((years | months | days) != 0) {
            throw new DateTimeException("Unable to convert period to duration as years/months/days are present: " + this);
        }
        return Duration.ofNanos(nanos);
    }

    //-----------------------------------------------------------------------
    /**
     * Checks if this period is equal to another period.
     * <p>
     * The comparison is based on the amounts held in the period.
     * To be equal, the years, months, days and normalized time fields must be equal.
     *
     * @param obj  the object to check, null returns false
     * @return true if this is equal to the other period
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Period) {
            Period other = (Period) obj;
            return years == other.years && months == other.months &&
                    days == other.days && nanos == other.nanos;
        }
        return false;
    }

    /**
     * A hash code for this period.
     *
     * @return a suitable hash code
     */
    @Override
    public int hashCode() {
        // ordered such that overflow from one field doesn't immediately affect the next field
        return ((years << 27) | (years >>> 5)) ^
                ((days << 21) | (days >>> 11)) ^
                ((months << 17) | (months >>> 15)) ^
                ((int) (nanos ^ (nanos >>> 32)));
    }

    //-----------------------------------------------------------------------
    /**
     * Outputs this period as a {@code String}, such as {@code P6Y3M1DT12H}.
     * <p>
     * The output will be in the ISO-8601 period format.
     *
     * @return a string representation of this period, not null
     */
    @Override
    public String toString() {
        if (this == ZERO) {
            return "PT0S";
        } else {
            StringBuilder buf = new StringBuilder();
            buf.append('P');
            if (years != 0) {
                buf.append(years).append('Y');
            }
            if (months != 0) {
                buf.append(months).append('M');
            }
            if (days != 0) {
                buf.append(days).append('D');
            }
            if (nanos != 0) {
                buf.append('T');
                if (getHours() != 0) {
                    buf.append(getHours()).append('H');
                }
                if (getMinutes() != 0) {
                    buf.append(getMinutes()).append('M');
                }
                int secondPart = getSeconds();
                int nanoPart = getNanos();
                int secsNanosOr = secondPart | nanoPart;
                if (secsNanosOr != 0) {  // if either non-zero
                    if ((secsNanosOr & Integer.MIN_VALUE) != 0) {  // if either less than zero
                        buf.append('-');
                        secondPart = Math.abs(secondPart);
                        nanoPart = Math.abs(nanoPart);
                    }
                    buf.append(secondPart);
                    if (nanoPart != 0) {
                        int dotPos = buf.length();
                        nanoPart += 1000_000_000;
                        while (nanoPart % 10 == 0) {
                            nanoPart /= 10;
                        }
                        buf.append(nanoPart);
                        buf.setCharAt(dotPos, '.');
                    }
                    buf.append('S');
                }
            }
            return buf.toString();
        }
    }

}
