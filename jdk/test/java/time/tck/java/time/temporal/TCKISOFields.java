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
package tck.java.time.temporal;

import java.time.format.DateTimeBuilder;
import java.time.temporal.*;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.YEAR;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Test.
 */
@Test(groups={"tck"})
public class TCKISOFields {

    @DataProvider(name="quarter")
    Object[][] data_quarter() {
        return new Object[][] {
                {LocalDate.of(1969, 12, 29), 90, 4},
                {LocalDate.of(1969, 12, 30), 91, 4},
                {LocalDate.of(1969, 12, 31), 92, 4},

                {LocalDate.of(1970, 1, 1), 1, 1},
                {LocalDate.of(1970, 1, 2), 2, 1},
                {LocalDate.of(1970, 2, 28), 59, 1},
                {LocalDate.of(1970, 3, 1), 60, 1},
                {LocalDate.of(1970, 3, 31), 90, 1},

                {LocalDate.of(1970, 4, 1), 1, 2},
                {LocalDate.of(1970, 6, 30), 91, 2},

                {LocalDate.of(1970, 7, 1), 1, 3},
                {LocalDate.of(1970, 9, 30), 92, 3},

                {LocalDate.of(1970, 10, 1), 1, 4},
                {LocalDate.of(1970, 12, 31), 92, 4},

                {LocalDate.of(1972, 2, 28), 59, 1},
                {LocalDate.of(1972, 2, 29), 60, 1},
                {LocalDate.of(1972, 3, 1), 61, 1},
                {LocalDate.of(1972, 3, 31), 91, 1},
        };
    }

    //-----------------------------------------------------------------------
    // DAY_OF_QUARTER
    //-----------------------------------------------------------------------
    @Test(dataProvider="quarter")
    public void test_DOQ(LocalDate date, int doq, int qoy) {
        assertEquals(ISOFields.DAY_OF_QUARTER.doGet(date), doq);
        assertEquals(date.get(ISOFields.DAY_OF_QUARTER), doq);
    }

    //-----------------------------------------------------------------------
    // QUARTER_OF_YEAR
    //-----------------------------------------------------------------------
    @Test(dataProvider="quarter")
    public void test_QOY(LocalDate date, int doq, int qoy) {
        assertEquals(ISOFields.QUARTER_OF_YEAR.doGet(date), qoy);
        assertEquals(date.get(ISOFields.QUARTER_OF_YEAR), qoy);
    }

    //-----------------------------------------------------------------------
    // builder
    //-----------------------------------------------------------------------
    @Test(dataProvider="quarter")
    public void test_builder_quarters(LocalDate date, int doq, int qoy) {
        DateTimeBuilder builder = new DateTimeBuilder();
        builder.addFieldValue(ISOFields.DAY_OF_QUARTER, doq);
        builder.addFieldValue(ISOFields.QUARTER_OF_YEAR, qoy);
        builder.addFieldValue(YEAR, date.getYear());
        builder.resolve();
        assertEquals(builder.query(LocalDate::from), date);
    }

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    @DataProvider(name="week")
    Object[][] data_week() {
        return new Object[][] {
                {LocalDate.of(1969, 12, 29), MONDAY, 1, 1970},
                {LocalDate.of(2012, 12, 23), SUNDAY, 51, 2012},
                {LocalDate.of(2012, 12, 24), MONDAY, 52, 2012},
                {LocalDate.of(2012, 12, 27), THURSDAY, 52, 2012},
                {LocalDate.of(2012, 12, 28), FRIDAY, 52, 2012},
                {LocalDate.of(2012, 12, 29), SATURDAY, 52, 2012},
                {LocalDate.of(2012, 12, 30), SUNDAY, 52, 2012},
                {LocalDate.of(2012, 12, 31), MONDAY, 1, 2013},
                {LocalDate.of(2013, 1, 1), TUESDAY, 1, 2013},
                {LocalDate.of(2013, 1, 2), WEDNESDAY, 1, 2013},
                {LocalDate.of(2013, 1, 6), SUNDAY, 1, 2013},
                {LocalDate.of(2013, 1, 7), MONDAY, 2, 2013},
        };
    }

    //-----------------------------------------------------------------------
    // WEEK_OF_WEEK_BASED_YEAR
    //-----------------------------------------------------------------------
    @Test(dataProvider="week")
    public void test_WOWBY(LocalDate date, DayOfWeek dow, int week, int wby) {
        assertEquals(date.getDayOfWeek(), dow);
        assertEquals(ISOFields.WEEK_OF_WEEK_BASED_YEAR.doGet(date), week);
        assertEquals(date.get(ISOFields.WEEK_OF_WEEK_BASED_YEAR), week);
    }

    //-----------------------------------------------------------------------
    // WEEK_BASED_YEAR
    //-----------------------------------------------------------------------
    @Test(dataProvider="week")
    public void test_WBY(LocalDate date, DayOfWeek dow, int week, int wby) {
        assertEquals(date.getDayOfWeek(), dow);
        assertEquals(ISOFields.WEEK_BASED_YEAR.doGet(date), wby);
        assertEquals(date.get(ISOFields.WEEK_BASED_YEAR), wby);
    }

    //-----------------------------------------------------------------------
    // builder
    //-----------------------------------------------------------------------
    @Test(dataProvider="week")
    public void test_builder_weeks(LocalDate date, DayOfWeek dow, int week, int wby) {
        DateTimeBuilder builder = new DateTimeBuilder();
        builder.addFieldValue(ISOFields.WEEK_BASED_YEAR, wby);
        builder.addFieldValue(ISOFields.WEEK_OF_WEEK_BASED_YEAR, week);
        builder.addFieldValue(DAY_OF_WEEK, dow.getValue());
        builder.resolve();
        assertEquals(builder.query(LocalDate::from), date);
    }

    //-----------------------------------------------------------------------
    public void test_loop() {
        // loop round at least one 400 year cycle, including before 1970
        LocalDate date = LocalDate.of(1960, 1, 5);  // Tuseday of week 1 1960
        int year = 1960;
        int wby = 1960;
        int weekLen = 52;
        int week = 1;
        while (date.getYear() < 2400) {
            DayOfWeek loopDow = date.getDayOfWeek();
            if (date.getYear() != year) {
                year = date.getYear();
            }
            if (loopDow == MONDAY) {
                week++;
                if ((week == 53 && weekLen == 52) || week == 54) {
                    week = 1;
                    LocalDate firstDayOfWeekBasedYear = date.plusDays(14).withDayOfYear(1);
                    DayOfWeek firstDay = firstDayOfWeekBasedYear.getDayOfWeek();
                    weekLen = (firstDay == THURSDAY || (firstDay == WEDNESDAY && firstDayOfWeekBasedYear.isLeapYear()) ? 53 : 52);
                    wby++;
                }
            }
            assertEquals(ISOFields.WEEK_OF_WEEK_BASED_YEAR.doRange(date), ValueRange.of(1, weekLen), "Failed on " + date + " " + date.getDayOfWeek());
            assertEquals(ISOFields.WEEK_OF_WEEK_BASED_YEAR.doGet(date), week, "Failed on " + date + " " + date.getDayOfWeek());
            assertEquals(date.get(ISOFields.WEEK_OF_WEEK_BASED_YEAR), week, "Failed on " + date + " " + date.getDayOfWeek());
            assertEquals(ISOFields.WEEK_BASED_YEAR.doGet(date), wby, "Failed on " + date + " " + date.getDayOfWeek());
            assertEquals(date.get(ISOFields.WEEK_BASED_YEAR), wby, "Failed on " + date + " " + date.getDayOfWeek());
            date = date.plusDays(1);
        }
    }

    // TODO: more tests
}
