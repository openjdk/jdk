/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8225641
 * @summary Test the behavior of Calendar.roll(WEEK_OF_YEAR) when the week
 * is rolled into a minimal week 1
 * @run junit RollToMinWeek
 */

import java.util.Calendar;
import java.util.Locale;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

public class RollToMinWeek {

    /**
     * Test to validate the behavior of Calendar.roll(WEEK_OF_YEAR)
     * when rolling into a minimal week 1. Since other methods
     * will call complete(), and rolling WEEK_OF_YEAR will cause field mask
     * to compute the new time using WEEK_OF_YEAR and DAY_OF_WEEK, these values
     * must represent a valid date. If the DAY_OF_WEEK does not exist in the
     * minimum first week, WEEK_OF_YEAR should be incremented by 1 to provide a
     * valid combination.
     *
     * For example, (Locale.US) rolling December 29th, 2019 by 1 week will
     * produce WEEK_OF_YEAR = 1, and DAY_OF_WEEK = 1. However, there is no
     * Sunday in the first week of 2019, and WEEK_OF_YEAR should be
     * incremented by 1 to a value of 2.
     */
    @ParameterizedTest
    @MethodSource("calendarProvider")
    public void testRollToMinWeek1(Calendar calendar, String expectedDate) {
        String originalDate = longDateString(calendar);
        calendar.roll(Calendar.WEEK_OF_YEAR, +1);
        String rolledDate = longDateString(calendar);
        if (!rolledDate.equals(expectedDate)) {
            fail(String.format("""
            {$$$ Failed: Rolled: "%s" by 1 week, expecting: "%s", but got: "%s"},
            """, originalDate, expectedDate, rolledDate));
        } else {
            System.out.printf("""
            {$$$ Passed: Rolled: "%s" by 1 week and successfully got: "%s"},
            """, originalDate, rolledDate);
        }
    }

    /**
     * Data provider for testing firstWeek().
     */
    private static Stream<Arguments> calendarProvider() {
        return Stream.of(
                // Test a variety of rolls that previously produced incorrect results
                Arguments.of(buildCalendar(27, 11, 2020, Locale.ENGLISH),
                        "Sunday, 5 January 2020"),
                Arguments.of(buildCalendar(28, 11, 2020, Locale.ENGLISH),
                        "Monday, 6 January 2020"),
                Arguments.of(buildCalendar(29, 11, 2020, Locale.ENGLISH),
                        "Tuesday, 7 January 2020"),
                Arguments.of(buildCalendar(29, 11, 2019, Locale.ENGLISH),
                        "Sunday, 6 January 2019"),
                Arguments.of(buildCalendar(30, 11, 2019, Locale.ENGLISH),
                        "Monday, 7 January 2019"),
                Arguments.of(buildCalendar(30, 11, 2019, Locale.FRANCE),
                        "Monday, 7 January 2019")
        );
    }

    private static Calendar buildCalendar(int day, int month, int year, Locale locale) {
        Calendar calendar = Calendar.getInstance(locale);
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar;
    }

    private String longDateString(Calendar calendar) {
        return String.format("%s, %s %s %s",
                calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH),
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH),
                calendar.get(Calendar.YEAR));
    }
}
