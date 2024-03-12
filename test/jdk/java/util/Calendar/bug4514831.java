/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @test
 * @bug 4514831
 * @summary Confirm that GregorianCalendar.roll() works properly during
 *          transition from Daylight Saving Time to Standard Time.
 * @run junit bug4514831
 */

import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import static java.util.GregorianCalendar.DAY_OF_MONTH;
import static java.util.GregorianCalendar.DAY_OF_WEEK;
import static java.util.GregorianCalendar.DAY_OF_WEEK_IN_MONTH;
import static java.util.GregorianCalendar.DAY_OF_YEAR;
import static java.util.GregorianCalendar.OCTOBER;
import static java.util.GregorianCalendar.THURSDAY;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class bug4514831 {
    // Data of 7 rolls in the form of a string for the respective field
    private static final String expectedDayOfYearData = "27-28 28-29 29-30 30-31 31-1 1-2 2-3 ";
    private static final String expectedDayOfWeekData = "27-28 28-29 29-30 30-31 31-25 25-26 26-27 ";
    private static final String expectedDayOfWeekInMonthData = "1-8 8-15 15-22 22-29 29-1 1-8 8-15 ";
    private static final TimeZone savedTz = TimeZone.getDefault();
    private static final Locale savedLocale = Locale.getDefault();

    // Save JVM default Locale and TimeZone
    @BeforeAll
    void initAll() {
        Locale.setDefault(Locale.US);
        TimeZone.setDefault(TimeZone.getTimeZone("US/Pacific"));
    }

    // Restore JVM default Locale and TimeZone
    @AfterAll
    void tearDownAll() {
        Locale.setDefault(savedLocale);
        TimeZone.setDefault(savedTz);
    }

    /*
     * Test some roll values during transition (DAY_OF_YEAR field). Uses
     * the boolean roll method. Roll multiple times and attach the returned
     * values to a long string which is then compared to the expected data.
     */
    public void rollDayOfYearTest() {
        StringBuilder actualRollData = new StringBuilder();
        GregorianCalendar cal = new GregorianCalendar(2001, OCTOBER, 27);
        for (int i = 0; i < 7; i++) {
            actualRollData.append(cal.get(DAY_OF_MONTH)).append("-");
            cal.roll(DAY_OF_YEAR, true);
            actualRollData.append(cal.get(DAY_OF_MONTH)).append(" ");
        }
        assertEquals(expectedDayOfYearData, actualRollData.toString(),
                "Wrong roll(DAY_OF_YEAR) transition");
    }

    /*
     * Test some roll values during transition (DAY_OF_WEEK field). Uses
     * the boolean roll method. Roll multiple times and attach the returned
     * values to a long string which is then compared to the expected data.
     */
    public void rollDayOfWeekTest() {
        StringBuilder actualRollData = new StringBuilder();
        GregorianCalendar cal = new GregorianCalendar(2001, OCTOBER, 27);
        cal.setFirstDayOfWeek(THURSDAY);
        for (int i = 0; i < 7; i++) {
            actualRollData.append(cal.get(DAY_OF_MONTH)).append("-");
            cal.roll(DAY_OF_WEEK, true);
            actualRollData.append(cal.get(DAY_OF_MONTH)).append(" ");
        }
        assertEquals(expectedDayOfWeekData, actualRollData.toString(),
                "Wrong roll(DAY_OF_WEEK) transition");
    }

    /*
     * Test some roll values during transition (DAY_OF_WEEK_IN_MONTH field). Uses
     * the boolean roll method. Roll multiple times and attach the returned
     * values to a long string which is then compared to the expected data.
     */
    public void rollDayOfWeekInMonthTest() {
        StringBuilder actualRollData = new StringBuilder();
        GregorianCalendar cal = new GregorianCalendar(2001, OCTOBER, 1);
        for (int i = 0; i < 7; i++) {
            actualRollData.append(cal.get(DAY_OF_MONTH)).append("-");
            cal.roll(DAY_OF_WEEK_IN_MONTH, true);
            actualRollData.append(cal.get(DAY_OF_MONTH)).append(" ");
        }
        assertEquals(expectedDayOfWeekInMonthData, actualRollData.toString(),
                "Wrong roll(DAY_OF_WEEK_IN_MONTH) transition");
    }
}
