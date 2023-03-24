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
 * @summary Test the behavior of Calendar.roll(WEEK_OF_YEAR) when the last week
 * is rolled up into a minimal week 1 of the same year
 * @run junit RollToMinWeek
 */


import java.util.*;
import java.util.stream.Stream;
import static java.util.Calendar.*;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Test to validate the behavior of Calendar.roll(WEEK_OF_YEAR, +1)
 * when rolling into a minimal week 1 from the max week. WEEK_OF_YEAR can
 * not be rolled to a week with a non-existent DAY_OF_WEEK. This only
 * test the implementation of the Gregorian Calendar roll
 */
public class RollToMinWeek {
    @ParameterizedTest
    @MethodSource("rollUpCalProvider")
    public void rollUpTest(Calendar calendar, String[] validDates){
        if (calendar instanceof GregorianCalendar) {
            testRoll(calendar, validDates, +1);
        } else {
            fail(String.format("Calendar is not Gregorian: %s", calendar));
        }
    }

    private void testRoll(Calendar calendar, String[] validDates, int amount) {
        String originalDate = longDateString(calendar);
        calendar.roll(Calendar.WEEK_OF_YEAR, amount);
        String rolledDate = longDateString(calendar);
        if (!Arrays.asList(validDates).contains(rolledDate)) {
            fail(String.format("""
            {$$$ Failed: Rolled: "%s" by %s week, where the first day of the week
            is: %s with a minimum week length of: %s and was expecting one of: "%s", but got: "%s"},
            """, originalDate, amount, calendar.getFirstDayOfWeek(),
                    calendar.getMinimalDaysInFirstWeek(), Arrays.toString(validDates), rolledDate));
        } else {
            System.out.printf("""
            {$$$ Passed: Rolled: "%s" by %s week where the first day of the week
            is: %s with a minimum week length of: %s and successfully got: "%s"},
            """, originalDate, amount, calendar.getFirstDayOfWeek(),
                    calendar.getMinimalDaysInFirstWeek(), rolledDate);
        }
    }

    // This implicitly tests the Iso8601 calendar as
    // MinWeek = 4 and FirstDayOfWeek = Monday is included in the provider
    private static Stream<Arguments> rollUpCalProvider() {
        ArrayList<Arguments> calList = new ArrayList<Arguments>();
        for (int weekLength = 1; weekLength <= 7; weekLength++) {
            for (int firstDay = 1; firstDay <= 7; firstDay++) {
                // Week 1, Week 2 are all potential dates to roll into
                String[] validDates = {"Sunday, 6 January 2019", "Sunday, 13 January 2019"};
                calList.add(Arguments.of(buildCalendar("gregory", firstDay, weekLength,
                                29, 11, 2019), validDates)
                        // Roll from week Max to week 1 with non-existent day of week
                );
            }
        }
        return calList.stream();
    }

    private static Calendar buildCalendar(String type, int firstDayOfWeek,
                                 int minimumWeekLength, int dayOfMonth,
                                 int month, int year) {
        Calendar.Builder calBuilder = new Builder();
        calBuilder.setCalendarType(type);
        calBuilder.setWeekDefinition(firstDayOfWeek, minimumWeekLength);
        calBuilder.setDate(year, month, dayOfMonth);
        return calBuilder.build();
    }

    private static String longDateString(Calendar calendar) {
        return String.format("%s, %s %s %s",
                calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH),
                calendar.get(Calendar.DAY_OF_MONTH),
                calendar.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH),
                calendar.get(YEAR));
    }
}
