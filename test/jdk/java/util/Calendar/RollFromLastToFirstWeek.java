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
 * @summary Test the behavior of GregorianCalendar.roll(WEEK_OF_YEAR)
 * when the last week is rolled into the first week of the same year
 * @run junit RollFromLastToFirstWeek
 */


import java.util.*;
import java.util.stream.Stream;
import static java.util.Calendar.*;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Test to validate the behavior of GregorianCalendar.roll(WEEK_OF_YEAR, +1)
 * when rolling from the last week of a year into the first week of the same year.
 * This only test the implementation of the Gregorian Calendar roll.
 *
 * Rolling from the last week of a year into the first week of the same year
 * could cause a WEEK_OF_YEAR with a non-existent DAY_OF_WEEK combination.
 * The associated fix ensures that a final check is made, so that the first
 * week is incremented to prevent this.
 */
public class RollFromLastToFirstWeek {
    private static final Builder GREGORIAN_BUILDER = new Builder()
            .setCalendarType("gregory");

    @ParameterizedTest
    @MethodSource("rollUpCalProvider")
    public void rollUpTest(Calendar calendar, String[] validDates){
        if (calendar instanceof GregorianCalendar) {
            testRoll(calendar, validDates);
        } else {
            fail(String.format("Calendar is not Gregorian: %s", calendar));
        }
    }

    private void testRoll(Calendar calendar, String[] validDates) {
        String originalDate = longDateString(calendar);
        calendar.roll(Calendar.WEEK_OF_YEAR, 1);
        String rolledDate = longDateString(calendar);
        if (!Arrays.asList(validDates).contains(rolledDate)) {
            fail(String.format("""
            {$$$ Failed: Rolled: "%s" by 1 week, where the first day of the week
            is: %s with a minimum week length of: %s and was expecting one of: "%s", but got: "%s"},
            """, originalDate, calendar.getFirstDayOfWeek(),
                    calendar.getMinimalDaysInFirstWeek(), Arrays.toString(validDates), rolledDate));
        } else {
            System.out.printf("""
            {$$$ Passed: Rolled: "%s" by 1 week where the first day of the week
            is: %s with a minimum week length of: %s and successfully got: "%s"},
            """, originalDate, calendar.getFirstDayOfWeek(),
                    calendar.getMinimalDaysInFirstWeek(), rolledDate);
        }
    }

    // This implicitly tests the Iso8601 calendar as
    // MinWeek = 4 and FirstDayOfWeek = Monday is included in the provider
    private static Stream<Arguments> rollUpCalProvider() {
        ArrayList<Arguments> calList = new ArrayList<Arguments>();
        // Week 1, Week 2 are all potential dates to roll into
        // Depends on first day of week / min days in week
        String[][] validDates = {
                {"Wednesday, 2 January 2019", "Wednesday, 9 January 2019"},
                {"Thursday, 3 January 2019" , "Thursday, 10 January 2019"},
                {"Friday, 4 January 2019"   , "Friday, 11 January 2019"},
                {"Saturday, 5 January 2019" , "Saturday, 12 January 2019"},
                {"Sunday, 6 January 2019"   , "Sunday, 13 January 2019"},
                {"Monday, 7 January 2019"   , "Monday, 14 January 2019"},
                {"Tuesday, 1 January 2019"  , "Tuesday, 8 January 2019"}
        };
        int date = 0;
        // Test all days at the end of the year that roll into week 1
        for (int dayOfMonth = 25; dayOfMonth <= 31; dayOfMonth++) {
            for (int weekLength = 1; weekLength <= 7; weekLength++) {
                // Sunday .. Monday -> Saturday
                for (int firstDay = SUNDAY; firstDay <= SATURDAY; firstDay++) {
                    calList.add(Arguments.of(buildCalendar(firstDay, weekLength,
                                    dayOfMonth, DECEMBER, 2019), validDates[date]));
                }
            }
            date++;
        }
        return calList.stream();
    }

    private static Calendar buildCalendar(int firstDayOfWeek,
                                          int minimumWeekLength, int dayOfMonth,
                                          int month, int year) {
        return GREGORIAN_BUILDER
                .setWeekDefinition(firstDayOfWeek, minimumWeekLength)
                .setDate(year, month, dayOfMonth)
                .build();
    }

    private static String longDateString(Calendar calendar) {
        return String.format("%s, %s %s %s",
                calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH),
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH),
                calendar.get(YEAR));
    }
}
