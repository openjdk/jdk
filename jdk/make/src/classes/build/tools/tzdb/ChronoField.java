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

package build.tools.tzdb;

/**
 * A standard set of date/time fields.
 *
 * @since 1.8
 */
enum ChronoField {

    /**
     * The second-of-minute.
     * <p>
     * This counts the second within the minute, from 0 to 59.
     * This field has the same meaning for all calendar systems.
     */
    SECOND_OF_MINUTE("SecondOfMinute", 0, 59),

    /**
     * The second-of-day.
     * <p>
     * This counts the second within the day, from 0 to (24 * 60 * 60) - 1.
     * This field has the same meaning for all calendar systems.
     */
    SECOND_OF_DAY("SecondOfDay", 0, 86400 - 1),

    /**
     * The minute-of-hour.
     * <p>
     * This counts the minute within the hour, from 0 to 59.
     * This field has the same meaning for all calendar systems.
     */
    MINUTE_OF_HOUR("MinuteOfHour", 0, 59),

    /**
     * The hour-of-day.
     * <p>
     * This counts the hour within the day, from 0 to 23.
     * This is the hour that would be observed on a standard 24-hour digital clock.
     * This field has the same meaning for all calendar systems.
     */
    HOUR_OF_DAY("HourOfDay", 0, 23),


    /**
     * The day-of-month.
     * <p>
     * This represents the concept of the day within the month.
     * In the default ISO calendar system, this has values from 1 to 31 in most months.
     * April, June, September, November have days from 1 to 30, while February has days
     * from 1 to 28, or 29 in a leap year.
     * <p>
     * Non-ISO calendar systems should implement this field using the most recognized
     * day-of-month values for users of the calendar system.
     * Normally, this is a count of days from 1 to the length of the month.
     */
    DAY_OF_MONTH("DayOfMonth", 1, 31),

    /**
     * The month-of-year, such as March.
     * <p>
     * This represents the concept of the month within the year.
     * In the default ISO calendar system, this has values from January (1) to December (12).
     * <p>
     * Non-ISO calendar systems should implement this field using the most recognized
     * month-of-year values for users of the calendar system.
     * Normally, this is a count of months starting from 1.
     */
    MONTH_OF_YEAR("MonthOfYear", 1, 12),

    /**
     * The proleptic year, such as 2012.
     * <p>
     * This represents the concept of the year, counting sequentially and using negative numbers.
     * The proleptic year is not interpreted in terms of the era.
     * See {@link #YEAR_OF_ERA} for an example showing the mapping from proleptic year to year-of-era.
     * <p>
     * The standard mental model for a date is based on three concepts - year, month and day.
     * These map onto the {@code YEAR}, {@code MONTH_OF_YEAR} and {@code DAY_OF_MONTH} fields.
     * Note that there is no reference to eras.
     * The full model for a date requires four concepts - era, year, month and day. These map onto
     * the {@code ERA}, {@code YEAR_OF_ERA}, {@code MONTH_OF_YEAR} and {@code DAY_OF_MONTH} fields.
     * Whether this field or {@code YEAR_OF_ERA} is used depends on which mental model is being used.
     * See {@link ChronoLocalDate} for more discussion on this topic.
     * <p>
     * Non-ISO calendar systems should implement this field as follows.
     * If the calendar system has only two eras, before and after a fixed date, then the
     * proleptic-year value must be the same as the year-of-era value for the later era,
     * and increasingly negative for the earlier era.
     * If the calendar system has more than two eras, then the proleptic-year value may be
     * defined with any appropriate value, although defining it to be the same as ISO may be
     * the best option.
     */
    YEAR("Year", -999_999_999, 999_999_999);

    private final String name;
    private final int min;
    private final int max;

    private ChronoField(String name, int min, int max) {
        this.name = name;
        this.min= min;
        this.max= max;
    }

    /**
     * Checks that the specified value is valid for this field.
     * <p>
     *
     * @param value  the value to check
     * @return the value that was passed in
     */
    public int checkValidValue(int value) {
        if (value >= min && value <= max) {
            return value;
        }
        throw new DateTimeException("Invalid value for " + name + " value: " + value);
    }

    public String toString() {
        return name;
    }

}
