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
 * @bug 8331851
 * @summary confirm that Calendar.roll() works correctly with leap year calculations
 * @run junit CalendarLeapYearRollTest
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

public class CalendarLeapYearRollTest {

    /**
     * 8331851 Calendar roll for leap year
     */
    @ParameterizedTest
    @MethodSource("calendarRollSource")
    public void testRollLeapYear(String testName, int calendarDate, int calendarMonth, int calendarYear,
                                 int value, int calendarField, int expectedDate, int expectedMonth,
                                 int expectedYear) {
        Calendar calendar = new GregorianCalendar(calendarYear, calendarMonth, calendarDate);
        calendar.roll(calendarField, value);
        assertEquals(expectedDate, calendar.get(DATE), testName
                + " Expected " + expectedDate + " of " + expectedMonth + expectedYear + " but got " + calendar.getTime());
        assertEquals(expectedMonth, calendar.get(MONTH), testName
                + " Expected " + expectedMonth + " but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
        assertEquals(expectedYear, calendar.get(YEAR), testName
                + " Expected " + expectedYear + " but got " + calendar.get(YEAR));
    }

    /**
     * 8331851 Calendar roll up/down for leap year
     */
    @ParameterizedTest
    @MethodSource("calendarRollUpDownSource")
    public void testRollUpDownLeapYear(String testName, int calendarDate, int calendarMonth, int calendarYear,
                                       int firstValue, int secondValue, int calendarField, int expectedDate,
                                       int expectedMonth, int expectedYear) {
        Calendar calendar = new GregorianCalendar(calendarYear, calendarMonth, calendarDate);
        calendar.roll(calendarField, firstValue);
        calendar.roll(calendarField, secondValue);
        assertEquals(expectedDate, calendar.get(DATE), testName
                + " Expected " + expectedDate + " of " + expectedMonth + expectedYear + " but got " + calendar.getTime());
        assertEquals(expectedMonth, calendar.get(MONTH), testName
                + " Expected " + expectedMonth + " but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
        assertEquals(expectedYear, calendar.get(YEAR), testName
                + " Expected " + expectedYear + " but got " + calendar.get(YEAR));
    }

    /**
     * 8331851 Calendar roll boolean for leap year
     */
    @ParameterizedTest
    @MethodSource("calendarBooleanRollSource")
    public void testBooleanRollLeapYear(String testName, int calendarDate, int calendarMonth, int calendarYear,
                                        boolean value, int calendarField, int expectedDate,
                                        int expectedMonth, int expectedYear) {
        Calendar calendar = new GregorianCalendar(calendarYear, calendarMonth, calendarDate);
        calendar.roll(calendarField, value);
        assertEquals(expectedDate, calendar.get(DATE), testName
                + " Expected " + expectedDate + " of " + expectedMonth + expectedYear + " but got " + calendar.getTime());
        assertEquals(expectedMonth, calendar.get(MONTH), testName
                + " Expected " + expectedMonth + " but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
        assertEquals(expectedYear, calendar.get(YEAR), testName
                + " Expected " + expectedYear + " but got " + calendar.get(YEAR));
    }

    /**
     * 8331851 Calendar month and year roll for leap/non-leap year
     */
    @Test
    public void testMonthYearRollUpDownNonLeapYear() {
        Calendar calendar = new GregorianCalendar(2024, FEBRUARY, 29);
        calendar.roll(MONTH, 1);
        calendar.roll(YEAR, -1);
        calendar.roll(MONTH, -1);
        assertEquals(28, calendar.get(DATE),
                "testMonthYearRollUpDownNonLeapYear Expected 28th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH),
                "testMonthYearRollUpDownNonLeapYear Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
        assertEquals(2023, calendar.get(YEAR),
                "testMonthYearRollUpDownNonLeapYear Expected 2023 but got " + calendar.get(YEAR));
    }

    private static Stream<Arguments> calendarRollUpDownSource() {
        return Stream.of(
                Arguments.of("testMonthRollDownUpLeapYearReversed", 31, MARCH, 2024, -1, 1, MONTH, 29, MARCH, 2024),
                Arguments.of("testMonthRollUpDownLeapYearReversed", 29, FEBRUARY, 2024, 1, -1, MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testYearRollUpDownLeapYear", 29, FEBRUARY, 2024, 1, -1, YEAR, 1, MARCH, 2024),
                Arguments.of("testFourYearRollUpDownLeapYear", 29, FEBRUARY, 2024, 4, -4, YEAR, 29, FEBRUARY, 2024),
                Arguments.of("testDayOfYearRollUpDownLeapYear", 29, FEBRUARY, 2024, 365, -365, DAY_OF_YEAR, 29, FEBRUARY, 2024),
                Arguments.of("testDateRollUpDownLeapYear", 29, FEBRUARY, 2024, 365, -365, DATE, 29, FEBRUARY, 2024),
                Arguments.of("testWeekOfYearRollUpDownLeapYear", 29, FEBRUARY, 2024, 52, -52, WEEK_OF_YEAR, 29, FEBRUARY, 2024),
                Arguments.of("testDayOfMonthRollUpDownLeapYear", 29, FEBRUARY, 2024, 31, -31, DAY_OF_MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testDayOfWeekInMonthRollUpDownLeapYear", 29, FEBRUARY, 2024, 6, -6, DAY_OF_WEEK_IN_MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testDayOfWeekRollUpDownLeapYear", 29, FEBRUARY, 2024, 6, -6, DAY_OF_WEEK, 29, FEBRUARY, 2024)
        );
    }

    private static Stream<Arguments> calendarRollSource() {
        return Stream.of(
                Arguments.of("testMonthRollUpLeapYear", 29, FEBRUARY, 2024, 1, MONTH, 29, MARCH, 2024),
                Arguments.of("testOneMonthRollDownLeapYear", 31, MARCH, 2024, -1, MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testTwoMonthDownEndOfMonthLeapYear", 30, APRIL, 2024, -2, MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testTwoMonthDownSameDateLeapYear", 29, APRIL, 2024, -2, MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testFourYearRollUpLeapYear", 29, FEBRUARY, 2024, 4, YEAR, 29, FEBRUARY, 2028),
                Arguments.of("testTwelveMonthRollDownLeapYear", 29, FEBRUARY, 2024, 12, MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testYearRollUpLeapYear", 29, FEBRUARY, 2024, 1, YEAR, 1, MARCH, 2025),
                Arguments.of("testYearRollDownLeapYear", 29, FEBRUARY, 2024, -1, YEAR, 1, MARCH, 2023),
                Arguments.of("testDayOfYearRollDownLeapYear", 29, FEBRUARY, 2024, -1, DAY_OF_YEAR, 28, FEBRUARY, 2024),
                Arguments.of("testDayOfYearRollUpLeapYear", 29, FEBRUARY, 2024, 1, DAY_OF_YEAR, 1, MARCH, 2024),
                Arguments.of("testDateRollDownLeapYear", 29, FEBRUARY, 2024, -1, DATE, 28, FEBRUARY, 2024),
                Arguments.of("testDateRollUpLeapYear", 29, FEBRUARY, 2024, 1, DATE, 1, FEBRUARY, 2024),
                Arguments.of("testWeekOfYearRollUpLeapYear", 29, FEBRUARY, 2024, 1, WEEK_OF_YEAR, 7, MARCH, 2024),
                Arguments.of("testWeekOfYearRollDownLeapYear", 29, FEBRUARY, 2024, -1, WEEK_OF_YEAR, 22, FEBRUARY, 2024),
                Arguments.of("testDayOfMonthRollUpLeapYear", 29, FEBRUARY, 2024, 1, DAY_OF_MONTH, 1, FEBRUARY, 2024),
                Arguments.of("testDayOfMonthRollDownLeapYear", 29, FEBRUARY, 2024, -1, DAY_OF_MONTH, 28, FEBRUARY, 2024),
                Arguments.of("testDayOfWeekInMonthRollUpLeapYear", 29, FEBRUARY, 2024, 1, DAY_OF_WEEK_IN_MONTH, 1, FEBRUARY, 2024),
                Arguments.of("testDayOfWeekInMonthRollDownLeapYear", 29, FEBRUARY, 2024, -1, DAY_OF_WEEK_IN_MONTH, 22, FEBRUARY, 2024),
                Arguments.of("testDayOfWeekRollUpLeapYear", 29, FEBRUARY, 2024, 1, DAY_OF_WEEK, 1, MARCH, 2024),
                Arguments.of("testDayOfWeekRollDownLeapYear", 29, FEBRUARY, 2024, -1, DAY_OF_WEEK, 28, FEBRUARY, 2024)
        );
    }

    private static Stream<Arguments> calendarBooleanRollSource() {
        return Stream.of(
                Arguments.of("testBooleanMonthRollDownLeapYear", 31, MARCH, 2024, false, MONTH, 29, FEBRUARY, 2024),
                Arguments.of("testBooleanMonthRollUpLeapYear", 29, FEBRUARY, 2024, true, MONTH, 29, MARCH, 2024),
                Arguments.of("testBooleanYearRollUpLeapYear", 29, FEBRUARY, 2024, true, YEAR, 1, MARCH, 2025),
                Arguments.of("testBooleanYearRollDownLeapYear", 29, FEBRUARY, 2024, false, YEAR, 1, MARCH, 2023),
                Arguments.of("testBooleanDayOfYearRollDownLeapYear", 29, FEBRUARY, 2024, false, DAY_OF_YEAR, 28, FEBRUARY, 2024),
                Arguments.of("testBooleanDayOfYearRollUpLeapYear", 29, FEBRUARY, 2024, true, DAY_OF_YEAR, 1, MARCH, 2024),
                Arguments.of("testBooleanDateRollDownLeapYear", 29, FEBRUARY, 2024, false, DATE, 28, FEBRUARY, 2024),
                Arguments.of("testBooleanDateRollUpLeapYear", 29, FEBRUARY, 2024, true, DATE, 1, FEBRUARY, 2024),
                Arguments.of("testBooleanWeekOfYearRollUpLeapYear", 29, FEBRUARY, 2024, true, WEEK_OF_YEAR, 7, MARCH, 2024),
                Arguments.of("testBooleanWeekOfYearRollDownLeapYear", 29, FEBRUARY, 2024, false, WEEK_OF_YEAR, 22, FEBRUARY, 2024),
                Arguments.of("testBooleanDayOfMonthRollUpLeapYear", 29, FEBRUARY, 2024, true, DAY_OF_MONTH, 1, FEBRUARY, 2024),
                Arguments.of("testBooleanDayOfMonthRollDownLeapYear", 29, FEBRUARY, 2024, false, DAY_OF_MONTH, 28, FEBRUARY, 2024),
                Arguments.of("testBooleanDayOfWeekInMonthRollUpLeapYear", 29, FEBRUARY, 2024, true, DAY_OF_WEEK_IN_MONTH, 1, FEBRUARY, 2024),
                Arguments.of("testBooleanDayOfWeekInMonthRollDownLeapYear", 29, FEBRUARY, 2024, false, DAY_OF_WEEK_IN_MONTH, 22, FEBRUARY, 2024),
                Arguments.of("testBooleanDayOfWeekRollUpLeapYear", 29, FEBRUARY, 2024, true, DAY_OF_WEEK, 1, MARCH, 2024),
                Arguments.of("testBooleanDayOfWeekRollDownLeapYear", 29, FEBRUARY, 2024, false, DAY_OF_WEEK, 28, FEBRUARY, 2024)
        );
    }

}
