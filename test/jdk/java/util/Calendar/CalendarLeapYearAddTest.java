/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7631503
 * @summary confirm that Calendar.add() works correctly with leap year calculations
 * @run junit CalendarLeapYearAddTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.Calendar;
import java.util.Locale;

import static java.util.Calendar.*;
import static java.util.Calendar.MONTH;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CalendarLeapYearAddTest {

    /**
     * 7631503 Calendar month add for leap year
     */
    @Test
    public void testMonthAddLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(Calendar.MONTH, 1);
        /* when added a month date jumps to 29th of March 2024 */
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of March 2024 but got " + calendar.getTime());
        assertEquals(MARCH, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected March but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar 1 month subtract for leap year
     */
    @Test
    public void testOneMonthSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 2, 31, 15, 0, 0);
        calendar.add(Calendar.MONTH, -1);
        /* when added a month date jumps to 29th of March 2024 */
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar 2 month subtract for leap year
     */
    @Test
    public void testTwoMonthSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 3, 30, 15, 0, 0);
        calendar.add(Calendar.MONTH, -2);
        /* when added a month date jumps to 29th of March 2024 */
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar month add/subtract for leap year
     */
    @Test
    public void testMonthAddSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(Calendar.MONTH, 1);
        /* when added a month date jumps to 29th of March 2024,
           subtracting month in a leap year returns 29th of February 2024  */
        calendar.add(Calendar.MONTH, -1);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar month and year add/subtract for leap/non-leap year
     */
    @Test
    public void testMonthYearAddSubtractNonLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(Calendar.MONTH, 1);
        calendar.add(YEAR, -1);
        calendar.add(Calendar.MONTH, -1);
        /* When month added date jumps to 29th of March 2024, after year subtracted date jumps to 29th of March 2023
           after month subtracted date jumps to 28th of Feb 2023 as non leap year
         */
        assertEquals(28, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 28th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar month add/subtract for leap year jumping from March 31st to February 29th and back to March 29th
     */
    @Test
    public void testMonthAddSubtractLeapYearReversed(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 2, 31, 15, 0, 0);
        calendar.add(MONTH, -1);
        calendar.add(MONTH, 1);
        /* when month removed date jumps to 29th of February,
           adding a month results in 29th of March 2024 as it's leap year
        */
        assertEquals(29,calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of March 2024 but got " + calendar.getTime());
        assertEquals(MARCH, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected March but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar year add/subtract for leap year
     */
    @Test
    public void testYearAddSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(YEAR, 1);
        calendar.add(YEAR, -1);
        /* when evaluated to no leap year date jumps to 28 of Feb 2023, removing year results in 28th of Feb 2024 */
        assertEquals(28, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 28th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar year add/subtract for leap year
     */
    @Test
    public void testYearDayAddSubtractLeapYearReversed(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, 1, 28, 15, 0, 0);
        calendar.add(YEAR, 1);
        calendar.add(DATE, 1);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        calendar.add(YEAR, -1);
        /* when evaluated to leap year + 1 day the date jumps to 29th of Feb 2024,
         removing year results in 28th of Feb 2023 */
        assertEquals(28, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 28th of February 2023 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar day of year add/subtract for leap year
     */
    @Test
    public void testDayOfYearAddSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DAY_OF_YEAR, 365);
        calendar.add(DAY_OF_YEAR, -365);
        /* adding/subtracting same amount of days should land on the same day in leap year*/
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar date add/subtract for leap year
     */
    @Test
    public void testDateAddSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DATE, 365);
        calendar.add(DATE, -365);
        /* DATE behaves as date DAY_OF_YEAR */
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar week of the year add/subtract for leap year
     */
    @Test
    public void testWeekOfYearAddSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(WEEK_OF_YEAR, 52);
        calendar.add(WEEK_OF_YEAR, -52);
        /* adding year in weeks should not mess the date*/
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar day of month add/subtract for leap year
     */
    @Test
    public void testDateOfMonthAddSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DAY_OF_MONTH, 31);
        calendar.add(DAY_OF_MONTH, -31);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar day of week add/subtract for leap year
     */
    @Test
    public void testDayOfWeekAddSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DAY_OF_WEEK, 6);
        calendar.add(DAY_OF_WEEK, -6);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 7631503 Calendar day of week in month add/subtract for leap year
     */
    @Test
    public void testDayOfWeekInMonthAddSubtractLeapYear(TestInfo testInfo) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DAY_OF_WEEK_IN_MONTH, 6);
        calendar.add(DAY_OF_WEEK_IN_MONTH, -6);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }
}
