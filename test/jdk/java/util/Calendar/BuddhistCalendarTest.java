/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4817812 4847186 4956227 4956479
 * @summary Confirm that BuddhistCalendar's add(), roll(), set(), and toString()
 *          work correctly with Buddhist Era years.
 * @run junit BuddhistCalendarTest
 */

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.stream.Stream;

import static java.util.Calendar.APRIL;
import static java.util.Calendar.DATE;
import static java.util.Calendar.DECEMBER;
import static java.util.Calendar.ERA;
import static java.util.Calendar.FEBRUARY;
import static java.util.Calendar.JANUARY;
import static java.util.Calendar.MAY;
import static java.util.Calendar.MONTH;
import static java.util.Calendar.WEEK_OF_YEAR;
import static java.util.Calendar.YEAR;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BuddhistCalendarTest {

    private static final Locale THAI_LOCALE = Locale.of("th", "TH");

    /*
     * Test some add values for the BuddhistCalendar. This test compares the same field
     * as the one added.
     */
    @ParameterizedTest
    @MethodSource("addDataProvider")
    public void buddhistAddTest(Calendar cal, int amount, int fieldToAdd) {
        int base = cal.get(YEAR);
        cal.add(fieldToAdd, amount);
        int yearAfterRoll = cal.get(YEAR);
        assertEquals(yearAfterRoll, base+amount, String.format(
                "Added: %s to field: %s", amount, fieldToAdd));
    }

    /*
     * Given in the format: Calendar, amount to add, and field to add.
     * Test adding of positive and negative year values.
     */
    private static Stream<Arguments> addDataProvider() {
        return Stream.of(
                Arguments.of(getBuddhistCalendar(), 1, YEAR),
                Arguments.of(getBuddhistCalendar(), -3, YEAR)
        );
    }

    /*
     * Test some add values for the BuddhistCalendar. Compare a bigger field
     * (year) than the one added (month). Larger field should roll over.
     */
    @ParameterizedTest
    @MethodSource("alternateAddDataProvider")
    public void buddhistAlternateAddTest(Calendar cal, int amount, int fieldToAdd) {
        int base = cal.get(YEAR);
        cal.add(fieldToAdd, amount);
        int yearAfterRoll = cal.get(YEAR);
        assertEquals(yearAfterRoll, (amount>0) ? (base+1): (base-1), String.format(
                "Added: %s to field: %s", amount, fieldToAdd));
    }

    /*
     * Given in the format: Calendar, amount to add, and field to add.
     * Test adding of positive and negative month values.
     */
    private static Stream<Arguments> alternateAddDataProvider() {
        return Stream.of(
                Arguments.of(getBuddhistCalendarBuilder().set(MONTH, DECEMBER).build(), 2, MONTH),
                Arguments.of(getBuddhistCalendarBuilder().set(MONTH, FEBRUARY).build(), -4, MONTH)
                );
    }

    /*
     * Test some roll values for the BuddhistCalendar. Compare same field
     * that was rolled, value should change.
     */
    @ParameterizedTest
    @MethodSource("rollProvider")
    public void buddhistRollTest(Calendar cal, int amount, int fieldToRoll) {
        int base = cal.get(YEAR);
        cal.roll(fieldToRoll, amount);
        int year = cal.get(YEAR);
        assertEquals(year, base+amount, "Rolling field should change value");
    }

    /*
     * Given in the format: Calendar, amount to roll, and field to roll.
     * Test rolling of positive and negative year values.
     */
    private static Stream<Arguments> rollProvider() {
        return Stream.of(
                Arguments.of(getBuddhistCalendar(), 2, YEAR),
                Arguments.of(getBuddhistCalendar(), -4, YEAR)
        );
    }

    /*
     * Set some calendar values and roll, however, measure a different
     * field than the field that was rolled. Rolling should not change the
     * larger field.
     */
    @ParameterizedTest
    @MethodSource("alternateRollProvider")
    public void buddhistAlternateRollTest(Calendar cal, int amount, int fieldToRoll) {
        int base = cal.get(YEAR);
        cal.roll(fieldToRoll, amount);
        int year = cal.get(YEAR);
        assertEquals(year, base, "Rolling smaller field should not change bigger field");
    }

    /*
     * Given in the format: Calendar, amount to roll, and field to roll.
     * Test rolling of positive and negative week_of_year values.
     */
    private static Stream<Arguments> alternateRollProvider() {
        return Stream.of(
                Arguments.of(getBuddhistCalendarBuilder().set(YEAR, 2543)
                        .set(MONTH, DECEMBER).set(DATE, 31).build(), 10, WEEK_OF_YEAR),
                Arguments.of(getBuddhistCalendarBuilder().set(YEAR, 2543)
                        .set(MONTH, JANUARY).set(DATE, 1).build(), -10, WEEK_OF_YEAR)
        );
    }

    // Test the overloaded set() methods. Check year value.
    @Test
    public void buddhistSetTest() {
        Calendar cal = getBuddhistCalendar();
        cal.set(3001, APRIL, 10);
        assertEquals(cal.get(YEAR), 3001);
        cal.set(3020, MAY, 20, 9, 10);
        assertEquals(cal.get(YEAR), 3020);
        cal.set(3120, MAY, 20, 9, 10, 52 );
        assertEquals(cal.get(YEAR), 3120);
    }

    /*
     * Test BuddhistCalendar.getActualMaximum(YEAR);
     * set(YEAR)/get(YEAR) in this method doesn't affect the real
     * YEAR value because a clone is used with set() and get().
     */
    @Test
    public void buddhistActualMaximumTest() {
        Calendar cal = getBuddhistCalendar();
        int base = cal.get(YEAR);
        int ignored = cal.getActualMaximum(YEAR);
        int year = cal.get(YEAR);
        assertEquals(year, base, "BuddhistCalendar.getActualMaximum(YEAR)");
    }

    // Test BuddhistCalendar.getActualMinimum(YEAR), doesn't call set(YEAR) nor get(YEAR).
    @Test
    public void buddhistActualMinimumTest() {
        Calendar cal = getBuddhistCalendar();
        int base = cal.get(YEAR);
        int ignored = cal.getActualMinimum(YEAR);
        int year = cal.get(YEAR);
        assertEquals(year, base, "BuddhistCalendar.getActualMinimum(YEAR)");
    }

    // 4847186: BuddhistCalendar: toString() returns Gregorian year
    @Test
    public void buddhistToStringTest() {
        Calendar cal = getBuddhistCalendar();
        int year = cal.get(YEAR);
        String s = cal.toString();
        String y = s.replaceAll(".+,YEAR=(\\d+),.+", "$1");
        assertEquals(year, Integer.parseInt(y), "Wrong year value");
    }

    // 4956479: BuddhistCalendar methods may return wrong values after exception
    @Test
    public void buddhistValuesAfterExceptionTest() {
        Calendar cal = getBuddhistCalendar();
        int year = cal.get(YEAR);
        assertThrows(IllegalArgumentException.class, ()-> cal.add(100, +1));
        int year2 = cal.get(YEAR);
        assertEquals(year2, year, "Wrong year value after exception thrown");
    }

    // 4956227: getLeastMaximum(WEEK_OF_MONTH) return diff. val. for Greg. and Buddhist Calendar
    @Test
    public void buddhistLeastMaximumTest() {
        Calendar bc = getBuddhistCalendar();
        // Specify THAI_LOCALE to get the same params for WEEK
        // calculations (6904680).
        Calendar gc = new GregorianCalendar(THAI_LOCALE);
        for (int f = 0; f < Calendar.FIELD_COUNT; f++) {
            if (f == ERA || f == YEAR) {
                continue;
            }
            int bn = bc.getLeastMaximum(f);
            int gn = gc.getLeastMaximum(f);
            assertEquals(bn, gn, "Inconsistent Least Max value for " + Koyomi.getFieldName(f));
        }
    }

    // Utility to get a new Buddhist Calendar Builder (to allow setting of other values)
    private static Calendar.Builder getBuddhistCalendarBuilder() {
        return new Calendar.Builder().setLocale(THAI_LOCALE);
    }

    // Utility to get a new Buddhist calendar
    private static Calendar getBuddhistCalendar() {
        return Calendar.getInstance(THAI_LOCALE);
    }
}
