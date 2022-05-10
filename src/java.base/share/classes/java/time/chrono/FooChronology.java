/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.time.chrono;

import java.time.*;
import java.time.temporal.*;

/**
 * Super interface for experimentation.
 */
public interface FooChronology {
    /**
     * Obtains adate from the era, year-of-era, month-of-year
     * and day-of-month fields.
     *
     * @param era  the ISO era, not null
     * @param yearOfEra  the ISO year-of-era
     * @param month  the ISO month-of-year
     * @param dayOfMonth  the ISO day-of-month
     * @return the ISO local date, not null
     * @throws DateTimeException if unable to create the date
     * @throws ClassCastException if conditions
     */
    public FooDate date(Era era, int yearOfEra, int month, int dayOfMonth);

    /**
     * Obtains adate from the proleptic-year, month-of-year
     * and day-of-month fields.
     *
     * @param prolepticYear  the ISO proleptic-year
     * @param month  the ISO month-of-year
     * @param dayOfMonth  the ISO day-of-month
     * @return the ISO local date, not null
     * @throws DateTimeException if unable to create the date
     */
    public FooDate date(int prolepticYear, int month, int dayOfMonth);

    /**
     * Obtains an date from another date-time object.
     *
     * @param temporal  the date-time object to convert, not null
     * @return the ISO local date, not null
     * @throws DateTimeException if unable to create the date
     */
    public FooDate date(TemporalAccessor temporal);

    /**
     * Obtains the current date from the system clock in the default time-zone.
     *
     * @return the current date using the system clock and default time-zone, not null
     * @throws DateTimeException if unable to create the date
     */
    public FooDate dateNow();

    /**
     * Obtains the current date from the system clock in the specified time-zone.
     * @return the current date using the system clock, not null
     * @throws DateTimeException if unable to create the date
     * @param zone the zone ID to use, not null
     */
    public FooDate dateNow(ZoneId zone);

    /**
     * Obtains the currentdate from the specified clock.
     *
     * @param clock  the clock to use, not null
     * @return the current ISO local date, not null
     * @throws DateTimeException if unable to create the date
     */
    public FooDate dateNow(Clock clock);


    /**
     * Obtains a date from the era, year-of-era and day-of-year fields.
     *
     * @param era  the era, not null
     * @param yearOfEra  the year-of-era
     * @param dayOfYear  the  day-of-year
     * @return the ISO local date, not null
     * @throws DateTimeException if unable to create the date
     */
    public FooDate dateYearDay(Era era, int yearOfEra, int dayOfYear);

    /**
     * Obtains a date from the proleptic-year and day-of-year fields.
     *
     * @param prolepticYear  the proleptic-year
     * @param dayOfYear  the day-of-year
     * @return the  local date, not null
     * @throws DateTimeException if unable to create the date
     */
    public FooDate dateYearDay(int prolepticYear, int dayOfYear);

    /**
     * {@return the era}
     * @param eraValue the value of the era
     */
    Era eraOf(int eraValue);

    
    /**
     * {@return the eras}
     */
    java.util.List<Era> eras();

    /**
     * {@return the calendar type of the underlying calendar system}
     */
    String getCalendarType();

    /**
     * {@return the id of the chronology}
     */
    public String getId();

    /**
     * {@return true if this year is a leap year, false otherwise}
     * @param prolepticYear  the proleptic year to check
     */
    boolean isLeapYear(long prolepticYear);

    /**
     * {@return the proleptic year}
     * @param era the era
     * @param yearOfEra the year of the era
     */
    int prolepticYear(Era era, int yearOfEra);

    /**
     * {@return the range of valid values for the specified field}
     * @param field the field
     */
    ValueRange range(ChronoField field);

    /**
     * {@return the date after parsing the field values}
     * @param fieldValues the field values
     * @param resolverStyle the resolverStyle
     */
    FooDate resolveDate(java.util.Map<TemporalField,Long> fieldValues,
                        java.time.format.ResolverStyle resolverStyle);
}
