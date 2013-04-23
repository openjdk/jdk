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
package java.time.temporal;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.function.UnaryOperator;

/**
 * Strategy for adjusting a temporal object.
 * <p>
 * Adjusters are a key tool for modifying temporal objects.
 * They exist to externalize the process of adjustment, permitting different
 * approaches, as per the strategy design pattern.
 * Examples might be an adjuster that sets the date avoiding weekends, or one that
 * sets the date to the last day of the month.
 * <p>
 * There are two equivalent ways of using a {@code TemporalAdjuster}.
 * The first is to invoke the method on this interface directly.
 * The second is to use {@link Temporal#with(TemporalAdjuster)}:
 * <pre>
 *   // these two lines are equivalent, but the second approach is recommended
 *   temporal = thisAdjuster.adjustInto(temporal);
 *   temporal = temporal.with(thisAdjuster);
 * </pre>
 * It is recommended to use the second approach, {@code with(TemporalAdjuster)},
 * as it is a lot clearer to read in code.
 * <p>
 * This class also contains a standard set of adjusters, available as static methods.
 * These include:
 * <ul>
 * <li>finding the first or last day of the month
 * <li>finding the first day of next month
 * <li>finding the first or last day of the year
 * <li>finding the first day of next year
 * <li>finding the first or last day-of-week within a month, such as "first Wednesday in June"
 * <li>finding the next or previous day-of-week, such as "next Thursday"
 * </ul>
 *
 * <h3>Specification for implementors</h3>
 * This interface places no restrictions on the mutability of implementations,
 * however immutability is strongly recommended.
 * <p>
 * All the implementations supplied by the static methods on this interface are immutable.
 *
 * @since 1.8
 */
@FunctionalInterface
public interface TemporalAdjuster {

    /**
     * Adjusts the specified temporal object.
     * <p>
     * This adjusts the specified temporal object using the logic
     * encapsulated in the implementing class.
     * Examples might be an adjuster that sets the date avoiding weekends, or one that
     * sets the date to the last day of the month.
     * <p>
     * There are two equivalent ways of using this method.
     * The first is to invoke this method directly.
     * The second is to use {@link Temporal#with(TemporalAdjuster)}:
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   temporal = thisAdjuster.adjustInto(temporal);
     *   temporal = temporal.with(thisAdjuster);
     * </pre>
     * It is recommended to use the second approach, {@code with(TemporalAdjuster)},
     * as it is a lot clearer to read in code.
     *
     * <h3>Specification for implementors</h3>
     * The implementation must take the input object and adjust it.
     * The implementation defines the logic of the adjustment and is responsible for
     * documenting that logic. It may use any method on {@code Temporal} to
     * query the temporal object and perform the adjustment.
     * The returned object must have the same observable type as the input object
     * <p>
     * The input object must not be altered.
     * Instead, an adjusted copy of the original must be returned.
     * This provides equivalent, safe behavior for immutable and mutable temporal objects.
     * <p>
     * The input temporal object may be in a calendar system other than ISO.
     * Implementations may choose to document compatibility with other calendar systems,
     * or reject non-ISO temporal objects by {@link TemporalQuery#chronology() querying the chronology}.
     * <p>
     * This method may be called from multiple threads in parallel.
     * It must be thread-safe when invoked.
     *
     * @param temporal  the temporal object to adjust, not null
     * @return an object of the same observable type with the adjustment made, not null
     * @throws DateTimeException if unable to make the adjustment
     * @throws ArithmeticException if numeric overflow occurs
     */
    Temporal adjustInto(Temporal temporal);

    //-----------------------------------------------------------------------
    /**
     * Obtains a {@code TemporalAdjuster} that wraps a date adjuster.
     * <p>
     * The {@code TemporalAdjuster} is based on the low level {@code Temporal} interface.
     * This method allows an adjustment from {@code LocalDate} to {@code LocalDate}
     * to be wrapped to match the temporal-based interface.
     * This is provided for convenience to make user-written adjusters simpler.
     * <p>
     * In general, user-written adjusters should be static constants:
     * <pre>
     *  static TemporalAdjuster TWO_DAYS_LATER = TemporalAdjuster.ofDateAdjuster(
     *    date -> date.plusDays(2));
     * </pre>
     *
     * @param dateBasedAdjuster  the date-based adjuster, not null
     * @return the temporal adjuster wrapping on the date adjuster, not null
     */
    static TemporalAdjuster ofDateAdjuster(UnaryOperator<LocalDate> dateBasedAdjuster) {
        return TemporalAdjusters.ofDateAdjuster(dateBasedAdjuster);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the "first day of month" adjuster, which returns a new date set to
     * the first day of the current month.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 will return 2011-01-01.<br>
     * The input 2011-02-15 will return 2011-02-01.
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It is equivalent to:
     * <pre>
     *  temporal.with(DAY_OF_MONTH, 1);
     * </pre>
     *
     * @return the first day-of-month adjuster, not null
     */
    static TemporalAdjuster firstDayOfMonth() {
        return TemporalAdjusters.firstDayOfMonth();
    }

    /**
     * Returns the "last day of month" adjuster, which returns a new date set to
     * the last day of the current month.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 will return 2011-01-31.<br>
     * The input 2011-02-15 will return 2011-02-28.<br>
     * The input 2012-02-15 will return 2012-02-29 (leap year).<br>
     * The input 2011-04-15 will return 2011-04-30.
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It is equivalent to:
     * <pre>
     *  long lastDay = temporal.range(DAY_OF_MONTH).getMaximum();
     *  temporal.with(DAY_OF_MONTH, lastDay);
     * </pre>
     *
     * @return the last day-of-month adjuster, not null
     */
    static TemporalAdjuster lastDayOfMonth() {
        return TemporalAdjusters.lastDayOfMonth();
    }

    /**
     * Returns the "first day of next month" adjuster, which returns a new date set to
     * the first day of the next month.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 will return 2011-02-01.<br>
     * The input 2011-02-15 will return 2011-03-01.
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It is equivalent to:
     * <pre>
     *  temporal.with(DAY_OF_MONTH, 1).plus(1, MONTHS);
     * </pre>
     *
     * @return the first day of next month adjuster, not null
     */
    static TemporalAdjuster firstDayOfNextMonth() {
        return TemporalAdjusters.firstDayOfNextMonth();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the "first day of year" adjuster, which returns a new date set to
     * the first day of the current year.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 will return 2011-01-01.<br>
     * The input 2011-02-15 will return 2011-01-01.<br>
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It is equivalent to:
     * <pre>
     *  temporal.with(DAY_OF_YEAR, 1);
     * </pre>
     *
     * @return the first day-of-year adjuster, not null
     */
    static TemporalAdjuster firstDayOfYear() {
        return TemporalAdjusters.firstDayOfYear();
    }

    /**
     * Returns the "last day of year" adjuster, which returns a new date set to
     * the last day of the current year.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 will return 2011-12-31.<br>
     * The input 2011-02-15 will return 2011-12-31.<br>
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It is equivalent to:
     * <pre>
     *  long lastDay = temporal.range(DAY_OF_YEAR).getMaximum();
     *  temporal.with(DAY_OF_YEAR, lastDay);
     * </pre>
     *
     * @return the last day-of-year adjuster, not null
     */
    static TemporalAdjuster lastDayOfYear() {
        return TemporalAdjusters.lastDayOfYear();
    }

    /**
     * Returns the "first day of next year" adjuster, which returns a new date set to
     * the first day of the next year.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 will return 2012-01-01.
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It is equivalent to:
     * <pre>
     *  temporal.with(DAY_OF_YEAR, 1).plus(1, YEARS);
     * </pre>
     *
     * @return the first day of next month adjuster, not null
     */
    static TemporalAdjuster firstDayOfNextYear() {
        return TemporalAdjusters.firstDayOfNextYear();
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the first in month adjuster, which returns a new date
     * in the same month with the first matching day-of-week.
     * This is used for expressions like 'first Tuesday in March'.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-12-15 for (MONDAY) will return 2011-12-05.<br>
     * The input 2011-12-15 for (FRIDAY) will return 2011-12-02.<br>
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It uses the {@code DAY_OF_WEEK} and {@code DAY_OF_MONTH} fields
     * and the {@code DAYS} unit, and assumes a seven day week.
     *
     * @param dayOfWeek  the day-of-week, not null
     * @return the first in month adjuster, not null
     */
    static TemporalAdjuster firstInMonth(DayOfWeek dayOfWeek) {
        return TemporalAdjuster.dayOfWeekInMonth(1, dayOfWeek);
    }

    /**
     * Returns the last in month adjuster, which returns a new date
     * in the same month with the last matching day-of-week.
     * This is used for expressions like 'last Tuesday in March'.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-12-15 for (MONDAY) will return 2011-12-26.<br>
     * The input 2011-12-15 for (FRIDAY) will return 2011-12-30.<br>
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It uses the {@code DAY_OF_WEEK} and {@code DAY_OF_MONTH} fields
     * and the {@code DAYS} unit, and assumes a seven day week.
     *
     * @param dayOfWeek  the day-of-week, not null
     * @return the first in month adjuster, not null
     */
    static TemporalAdjuster lastInMonth(DayOfWeek dayOfWeek) {
        return TemporalAdjuster.dayOfWeekInMonth(-1, dayOfWeek);
    }

    /**
     * Returns the day-of-week in month adjuster, which returns a new date
     * in the same month with the ordinal day-of-week.
     * This is used for expressions like the 'second Tuesday in March'.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-12-15 for (1,TUESDAY) will return 2011-12-06.<br>
     * The input 2011-12-15 for (2,TUESDAY) will return 2011-12-13.<br>
     * The input 2011-12-15 for (3,TUESDAY) will return 2011-12-20.<br>
     * The input 2011-12-15 for (4,TUESDAY) will return 2011-12-27.<br>
     * The input 2011-12-15 for (5,TUESDAY) will return 2012-01-03.<br>
     * The input 2011-12-15 for (-1,TUESDAY) will return 2011-12-27 (last in month).<br>
     * The input 2011-12-15 for (-4,TUESDAY) will return 2011-12-06 (3 weeks before last in month).<br>
     * The input 2011-12-15 for (-5,TUESDAY) will return 2011-11-29 (4 weeks before last in month).<br>
     * The input 2011-12-15 for (0,TUESDAY) will return 2011-11-29 (last in previous month).<br>
     * <p>
     * For a positive or zero ordinal, the algorithm is equivalent to finding the first
     * day-of-week that matches within the month and then adding a number of weeks to it.
     * For a negative ordinal, the algorithm is equivalent to finding the last
     * day-of-week that matches within the month and then subtracting a number of weeks to it.
     * The ordinal number of weeks is not validated and is interpreted leniently
     * according to this algorithm. This definition means that an ordinal of zero finds
     * the last matching day-of-week in the previous month.
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It uses the {@code DAY_OF_WEEK} and {@code DAY_OF_MONTH} fields
     * and the {@code DAYS} unit, and assumes a seven day week.
     *
     * @param ordinal  the week within the month, unbounded but typically from -5 to 5
     * @param dayOfWeek  the day-of-week, not null
     * @return the day-of-week in month adjuster, not null
     */
    static TemporalAdjuster dayOfWeekInMonth(final int ordinal, DayOfWeek dayOfWeek) {
        return TemporalAdjusters.dayOfWeekInMonth(ordinal, dayOfWeek);
    }

    //-----------------------------------------------------------------------
    /**
     * Returns the next day-of-week adjuster, which adjusts the date to the
     * first occurrence of the specified day-of-week after the date being adjusted.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 (a Saturday) for parameter (MONDAY) will return 2011-01-17 (two days later).<br>
     * The input 2011-01-15 (a Saturday) for parameter (WEDNESDAY) will return 2011-01-19 (four days later).<br>
     * The input 2011-01-15 (a Saturday) for parameter (SATURDAY) will return 2011-01-22 (seven days later).
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It uses the {@code DAY_OF_WEEK} field and the {@code DAYS} unit,
     * and assumes a seven day week.
     *
     * @param dayOfWeek  the day-of-week to move the date to, not null
     * @return the next day-of-week adjuster, not null
     */
    static TemporalAdjuster next(DayOfWeek dayOfWeek) {
        return TemporalAdjusters.next(dayOfWeek);
    }

    /**
     * Returns the next-or-same day-of-week adjuster, which adjusts the date to the
     * first occurrence of the specified day-of-week after the date being adjusted
     * unless it is already on that day in which case the same object is returned.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 (a Saturday) for parameter (MONDAY) will return 2011-01-17 (two days later).<br>
     * The input 2011-01-15 (a Saturday) for parameter (WEDNESDAY) will return 2011-01-19 (four days later).<br>
     * The input 2011-01-15 (a Saturday) for parameter (SATURDAY) will return 2011-01-15 (same as input).
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It uses the {@code DAY_OF_WEEK} field and the {@code DAYS} unit,
     * and assumes a seven day week.
     *
     * @param dayOfWeek  the day-of-week to check for or move the date to, not null
     * @return the next-or-same day-of-week adjuster, not null
     */
    static TemporalAdjuster nextOrSame(DayOfWeek dayOfWeek) {
        return TemporalAdjusters.nextOrSame(dayOfWeek);
    }

    /**
     * Returns the previous day-of-week adjuster, which adjusts the date to the
     * first occurrence of the specified day-of-week before the date being adjusted.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 (a Saturday) for parameter (MONDAY) will return 2011-01-10 (five days earlier).<br>
     * The input 2011-01-15 (a Saturday) for parameter (WEDNESDAY) will return 2011-01-12 (three days earlier).<br>
     * The input 2011-01-15 (a Saturday) for parameter (SATURDAY) will return 2011-01-08 (seven days earlier).
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It uses the {@code DAY_OF_WEEK} field and the {@code DAYS} unit,
     * and assumes a seven day week.
     *
     * @param dayOfWeek  the day-of-week to move the date to, not null
     * @return the previous day-of-week adjuster, not null
     */
    static TemporalAdjuster previous(DayOfWeek dayOfWeek) {
        return TemporalAdjusters.previous(dayOfWeek);
    }

    /**
     * Returns the previous-or-same day-of-week adjuster, which adjusts the date to the
     * first occurrence of the specified day-of-week before the date being adjusted
     * unless it is already on that day in which case the same object is returned.
     * <p>
     * The ISO calendar system behaves as follows:<br>
     * The input 2011-01-15 (a Saturday) for parameter (MONDAY) will return 2011-01-10 (five days earlier).<br>
     * The input 2011-01-15 (a Saturday) for parameter (WEDNESDAY) will return 2011-01-12 (three days earlier).<br>
     * The input 2011-01-15 (a Saturday) for parameter (SATURDAY) will return 2011-01-15 (same as input).
     * <p>
     * The behavior is suitable for use with most calendar systems.
     * It uses the {@code DAY_OF_WEEK} field and the {@code DAYS} unit,
     * and assumes a seven day week.
     *
     * @param dayOfWeek  the day-of-week to check for or move the date to, not null
     * @return the previous-or-same day-of-week adjuster, not null
     */
    static TemporalAdjuster previousOrSame(DayOfWeek dayOfWeek) {
        return TemporalAdjusters.previousOrSame(dayOfWeek);
    }

}
