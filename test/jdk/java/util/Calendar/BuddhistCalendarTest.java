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
 * @summary Confirm that BuddhistCalendar's add(), roll() and toString()
 *          work correctly with Buddhist Era years.
 * @run junit BuddhistCalendarTest
 */

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class BuddhistCalendarTest {

    private static final Locale THAI_LOCALE = Locale.of("th", "TH");

    // Test some add values for the BuddhistCalendar
    @Test
    public void buddhistAddTest() {
        Calendar cal;
        int base, year;
        // Test: BuddhistCalendar.add(YEAR)
        cal = getBuddhistCalendar();
        base = cal.get(YEAR);
        cal.add(YEAR, 1);
        year = cal.get(YEAR);
        assertEquals(year, base+1, "add(+YEAR)");

        cal = getBuddhistCalendar();
        base = cal.get(YEAR);
        cal.add(YEAR, -3);
        year = cal.get(YEAR);
        assertEquals(year, base-3, "add(-YEAR)");

        // Test BuddhistCalendar.add(MONTH)
        cal = getBuddhistCalendar();
        base = cal.get(YEAR);
        cal.set(MONTH, DECEMBER);
        cal.add(MONTH, 2);
        year = cal.get(YEAR);
        assertEquals(year, base+1, "add(+MONTH)");

        cal = getBuddhistCalendar();
        base = cal.get(YEAR);
        cal.set(MONTH, FEBRUARY);
        cal.add(MONTH, -4);
        year = cal.get(YEAR);
        assertEquals(year, base-1, "add(-MONTH)");
    }

    // Test some roll values for the BuddhistCalendar
    @Test
    public void buddhistRollTest() {
        Calendar cal;
        int base, year;
        // Test BuddhistCalendar.roll(YEAR)
        cal = getBuddhistCalendar();
        base = cal.get(YEAR);
        cal.roll(YEAR, 2);
        year = cal.get(YEAR);
        assertEquals(year, base+2, "roll(+YEAR)");

        cal = getBuddhistCalendar();
        base = cal.get(YEAR);
        cal.roll(YEAR, -4);
        year = cal.get(YEAR);
        assertEquals(year, base-4, "roll(-YEAR)");

        // Test BuddhistCalendar.roll(WEEK_OF_YEAR)
        cal = getBuddhistCalendar();
        cal.set(YEAR, 2543);   // A.D.2000
        cal.set(MONTH, DECEMBER);
        cal.set(DATE, 31);
        base = cal.get(YEAR);
        assertEquals(base, 2543, "roll(+WEEK_OF_YEAR)");
        cal.roll(WEEK_OF_YEAR, 10);
        year = cal.get(YEAR);
        assertEquals(year, base, "roll(+WEEK_OF_YEAR)");

        cal = getBuddhistCalendar();
        cal.set(YEAR, 2543);   // A.D.2000
        cal.set(MONTH, JANUARY);
        cal.set(DATE, 1);
        base = cal.get(YEAR);
        assertEquals(base, 2543, "roll(+WEEK_OF_YEAR)");
        cal.roll(WEEK_OF_YEAR, -10);
        year = cal.get(YEAR);
        assertEquals(year, base, "roll(-WEEK_OF_YEAR)");

    }

    // Test some set values for the BuddhistCalendar
    @Test
    public void buddhistSetTest() {
        Calendar cal;
        int year;
        // Test Calendar.set(year, month, date)
        cal = getBuddhistCalendar();
        cal.set(3001, APRIL, 10);
        year = cal.get(YEAR);
        assertEquals(year, 3001, "set(year, month, date)");

        // Test Calendar.set(year, month, date, hour, minute)
        cal = getBuddhistCalendar();
        cal.set(3020, MAY, 20, 9, 10);
        year = cal.get(YEAR);
        assertEquals(year, 3020, "set(year, month, date, hour, minute)");

        // Test Calendar.set(year, month, date, hour, minute, second)
        cal = getBuddhistCalendar();
        cal.set(3120, MAY, 20, 9, 10, 52);
        year = cal.get(YEAR);
        assertEquals(year, 3120, "set(year, month, date, hour, minute, second)");
    }

    /*
     * Test BuddhistCalendar.getActualMaximum(YEAR);
     * set(YEAR)/get(YEAR) in this method doesn't affect the real
     * YEAR value because a clone is used with set() and get().
     */
    @Test
    public void buddhistActualMaximumTest() {
        Calendar cal;
        int base, year;
        cal = getBuddhistCalendar();
        base = cal.get(YEAR);
        year = cal.get(YEAR);
        assertEquals(year, base, "BuddhistCalendar.getActualMaximum(YEAR)");
    }

    // Test BuddhistCalendar.getActualMinimum(YEAR), doesn't call set(YEAR) nor get(YEAR).
    @Test
    public void buddhistActualMinimumTest() {
        Calendar cal;
        int base, year;
        cal = getBuddhistCalendar();
        base = cal.get(YEAR);
        year = cal.get(YEAR);
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

    // returns a BuddhistCalendar
    private static Calendar getBuddhistCalendar() {
        return Calendar.getInstance(THAI_LOCALE);
    }
}
