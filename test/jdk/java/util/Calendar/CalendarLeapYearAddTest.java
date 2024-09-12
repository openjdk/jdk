/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331646
 * @summary confirm that Calendar.add() works correctly with leap year calculations
 * @run junit CalendarLeapYearAddTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.stream.Stream;

import static java.util.Calendar.APRIL;
import static java.util.Calendar.DATE;
import static java.util.Calendar.DAY_OF_MONTH;
import static java.util.Calendar.DAY_OF_WEEK;
import static java.util.Calendar.DAY_OF_WEEK_IN_MONTH;
import static java.util.Calendar.DAY_OF_YEAR;
import static java.util.Calendar.FEBRUARY;
import static java.util.Calendar.LONG;
import static java.util.Calendar.MARCH;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.WEEK_OF_YEAR;
import static java.util.Calendar.YEAR;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CalendarLeapYearAddTest {

    /**
     * 8331646 Calendar add for leap year
     */
    @ParameterizedTest
    @MethodSource("calendarAddSource")
    public void testAddLeapYear(String testName, int calendarDate, int calendarMonth, int calendarYear,
                                int value, int calendarField, int expectedDate, int expectedMonth,
                                int expectedYear) {
        Calendar calendar = new GregorianCalendar(calendarYear, calendarMonth, calendarDate);
        calendar.add(calendarField, value);
        assertEquals(expectedDate, calendar.get(DATE), testName
                + " Expected " + expectedDate + " of " + expectedMonth + expectedYear + " but got " + calendar.getTime());
        assertEquals(expectedMonth, calendar.get(MONTH), testName
                + " Expected " + expectedMonth + " but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar month and year add/subtract for leap/non-leap year
     */
    @Test
    public void testMonthYearAddSubtractNonLeapYear() {
        Calendar calendar = new GregorianCalendar(2024, FEBRUARY, 29);
        calendar.add(MONTH, 1);
        calendar.add(YEAR, -1);
        calendar.add(MONTH, -1);
        /* When month added date jumps to 29th of March 2024, after year subtracted date jumps to 29th of March 2023
           after month subtracted date jumps to 28th of Feb 2023 as non leap year
         */
        assertEquals(28, calendar.get(DATE),
                "testMonthYearAddSubtractNonLeapYear Expected 28th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH),
                " testMonthYearAddSubtractNonLeapYear Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar add/subtract for leap year
     */
    @ParameterizedTest
    @MethodSource("calendarAddSubtractSource")
    public void testAddSubtractLeapYear(String testName, int calendarDate, int calendarMonth, int calendarYear,
                                        int firstValue, int secondValue, int calendarField, int expectedDate,
                                        int expectedMonth, int expectedYear) {
        Calendar calendar = new GregorianCalendar(calendarYear, calendarMonth, calendarDate);
        calendar.add(calendarField, firstValue);
        calendar.add(calendarField, secondValue);
        assertEquals(expectedDate, calendar.get(DATE), testName
                + " Expected " + expectedDate + " of " + expectedMonth + expectedYear + " but got " + calendar.getTime());
        assertEquals(expectedMonth, calendar.get(MONTH), testName
                + " Expected " + expectedMonth + " but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    private static Stream<Arguments> calendarAddSubtractSource() {
        return Stream.of(
                Arguments.of("testMonthAddSubtractLeapYearReversed", 31, MARCH, 2024, -1, 1, MONTH, 29, MARCH, 2024),
                Arguments.of("testMonthAddSubtractLeapYear", 29, FEBRUARY, 2024, 1, -1, MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testYearAddSubtractLeapYear", 29, FEBRUARY, 2024, 1, -1, YEAR, 28, FEBRUARY, 2024),
                Arguments.of("testDayOfYearAddSubtractLeapYear", 29, FEBRUARY, 2024, 365, -365, DAY_OF_YEAR, 29, FEBRUARY, 2024),
                Arguments.of("testDateAddSubtractLeapYear", 29, FEBRUARY, 2024, 365, -365, DATE, 29, FEBRUARY, 2024),
                Arguments.of("testWeekOfYearAddSubtractLeapYear", 29, FEBRUARY, 2024, 52, -52, WEEK_OF_YEAR, 29, FEBRUARY, 2024),
                Arguments.of("testDayOfMonthAddSubtractLeapYear", 29, FEBRUARY, 2024, 31, -31, DAY_OF_MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testDayOfWeekInMonthAddSubtractLeapYear", 29, FEBRUARY, 2024, 6, -6, DAY_OF_WEEK_IN_MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testDayOfWeekAddSubtractLeapYear", 29, FEBRUARY, 2024, 6, -6, DAY_OF_WEEK, 29, FEBRUARY, 2024)
        );
    }

    private static Stream<Arguments> calendarAddSource() {
        return Stream.of(
                Arguments.of("testMonthAddLeapYear", 29, FEBRUARY, 2024, 1, MONTH, 29, MARCH, 2024),
                Arguments.of("testOneMonthSubtractLeapYear", 31, MARCH, 2024, -1, MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testTwoMonthSubtractLeapYear", 30, APRIL, 2024, -2, MONTH, 29, FEBRUARY, 2024)
        );
    }
}
