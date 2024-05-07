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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Calendar;
import java.util.Locale;

import static java.util.Calendar.*;
import static java.util.Calendar.MONTH;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CalendarLeapYearAddTest {

    private TestInfo testInfo;

    @BeforeEach
    void init(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    /**
     * 8331646 Calendar month add for leap year
     */
    @ParameterizedTest(name = "testMonthAddLeapYear")
    @ValueSource(ints = {1})
    public void testMonthAddLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(Calendar.MONTH, value);
        /* when added a month date jumps to 29th of March 2024 */
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of March 2024 but got " + calendar.getTime());
        assertEquals(MARCH, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected March but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar 1 month subtract for leap year
     */
    @ParameterizedTest(name = "testOneMonthSubtractLeapYear")
    @ValueSource(ints = {-1})
    public void testOneMonthSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 2, 31, 15, 0, 0);
        calendar.add(Calendar.MONTH, value);
        /* when added a month date jumps to 29th of March 2024 */
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar 2 month subtract for leap year
     */
    @ParameterizedTest(name = "testTwoMonthSubtractLeapYear")
    @ValueSource(ints = {-2})
    public void testTwoMonthSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 3, 30, 15, 0, 0);
        calendar.add(Calendar.MONTH, value);
        /* when added a month date jumps to 29th of March 2024 */
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar month add/subtract for leap year
     */
    @ParameterizedTest(name = "testMonthAddSubtractLeapYear")
    @ValueSource(ints = {1})
    public void testMonthAddSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(Calendar.MONTH, value);
        /* when added a month date jumps to 29th of March 2024,
           subtracting month in a leap year returns 29th of February 2024  */
        calendar.add(Calendar.MONTH, -value);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar month and year add/subtract for leap/non-leap year
     */
    @ParameterizedTest(name = "testMonthYearAddSubtractNonLeapYear")
    @ValueSource(ints = {1})
    public void testMonthYearAddSubtractNonLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(Calendar.MONTH, value);
        calendar.add(YEAR, -value);
        calendar.add(Calendar.MONTH, -value);
        /* When month added date jumps to 29th of March 2024, after year subtracted date jumps to 29th of March 2023
           after month subtracted date jumps to 28th of Feb 2023 as non leap year
         */
        assertEquals(28, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 28th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar month add/subtract for leap year jumping from March 31st to February 29th and back to March 29th
     */
    @ParameterizedTest(name = "testMonthAddSubtractLeapYearReversed")
    @ValueSource(ints = {1})
    public void testMonthAddSubtractLeapYearReversed(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 2, 31, 15, 0, 0);
        calendar.add(MONTH, -value);
        calendar.add(MONTH, value);
        /* when month removed date jumps to 29th of February,
           adding a month results in 29th of March 2024 as it's leap year
        */
        assertEquals(29,calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of March 2024 but got " + calendar.getTime());
        assertEquals(MARCH, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected March but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar year add/subtract for leap year
     */
    @ParameterizedTest(name = "testYearAddSubtractLeapYear")
    @ValueSource(ints = {1})
    public void testYearAddSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(YEAR, value);
        calendar.add(YEAR, -value);
        /* when evaluated to no leap year date jumps to 28 of Feb 2023, removing year results in 28th of Feb 2024 */
        assertEquals(28, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 28th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar year add/subtract for leap year
     */
    @ParameterizedTest(name = "testYearDayAddSubtractLeapYearReversed")
    @ValueSource(ints = {1})
    public void testYearDayAddSubtractLeapYearReversed(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2023, 1, 28, 15, 0, 0);
        calendar.add(YEAR, value);
        calendar.add(DATE, value);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        calendar.add(YEAR, -value);
        /* when evaluated to leap year + 1 day the date jumps to 29th of Feb 2024,
         removing year results in 28th of Feb 2023 */
        assertEquals(28, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 28th of February 2023 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar day of year add/subtract for leap year
     */
    @ParameterizedTest(name = "testDayOfYearAddSubtractLeapYear")
    @ValueSource(ints = {365})
    public void testDayOfYearAddSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DAY_OF_YEAR, value);
        calendar.add(DAY_OF_YEAR, -value);
        /* adding/subtracting same amount of days should land on the same day in leap year*/
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar date add/subtract for leap year
     */
    @ParameterizedTest(name = "testDateAddSubtractLeapYear")
    @ValueSource(ints = {365})
    public void testDateAddSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DATE, value);
        calendar.add(DATE, -value);
        /* DATE behaves as date DAY_OF_YEAR */
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar week of the year add/subtract for leap year
     */
    @ParameterizedTest(name = "testWeekOfYearAddSubtractLeapYear")
    @ValueSource(ints = {52})
    public void testWeekOfYearAddSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(WEEK_OF_YEAR, value);
        calendar.add(WEEK_OF_YEAR, -value);
        /* adding year in weeks should not mess the date*/
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar day of month add/subtract for leap year
     */
    @ParameterizedTest(name = "testDateOfMonthAddSubtractLeapYear")
    @ValueSource(ints = {31})
    public void testDateOfMonthAddSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DAY_OF_MONTH, value);
        calendar.add(DAY_OF_MONTH, -value);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar day of week add/subtract for leap year
     */
    @ParameterizedTest(name = "testDayOfWeekAddSubtractLeapYear")
    @ValueSource(ints = {6})
    public void testDayOfWeekAddSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DAY_OF_WEEK, value);
        calendar.add(DAY_OF_WEEK, -value);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }

    /**
     * 8331646 Calendar day of week in month add/subtract for leap year
     */
    @ParameterizedTest(name = "testDayOfWeekInMonthAddSubtractLeapYear")
    @ValueSource(ints = {6})
    public void testDayOfWeekInMonthAddSubtractLeapYear(int value) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2024, 1, 29, 15, 0, 0);
        calendar.add(DAY_OF_WEEK_IN_MONTH, value);
        calendar.add(DAY_OF_WEEK_IN_MONTH, -value);
        assertEquals(29, calendar.get(DATE), testInfo.getDisplayName()
                + " Expected 29th of February 2024 but got " + calendar.getTime());
        assertEquals(FEBRUARY, calendar.get(MONTH), testInfo.getDisplayName()
                + " Expected February but got " + calendar.getDisplayName(MONTH, LONG, Locale.getDefault()));
    }
}
