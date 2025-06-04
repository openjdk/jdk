/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4243802
 * @summary confirm that Calendar.setTimeInMillis() and
 *          getTimeInMillis() can be called from a user program. (They used to
 *          be protected methods.)
 * @run junit bug4243802
 */

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class bug4243802 {

    // Save JVM default Locale and TimeZone
    private static final TimeZone savedTz = TimeZone.getDefault();
    private static final Locale savedLocale = Locale.getDefault();

    // Set custom JVM default Locale and TimeZone
    @BeforeAll
    static void initAll() {
        Locale.setDefault(Locale.US);
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
    }

    // Restore JVM default Locale and TimeZone
    @AfterAll
    static void tearDownAll() {
        Locale.setDefault(savedLocale);
        TimeZone.setDefault(savedTz);
    }

    /*
     * Test getTimeInMillis() and setTimeInMillis(). Compare a Calendar
     * set with a traditional date to one set using setTimeInMillis(),
     * where both Calendars should be of equal times.
     */
    @Test
    public void setCalendarWithoutDateTest() {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        cal1.clear();
        cal2.clear();
        cal1.set(2001, Calendar.JANUARY, 25, 1, 23, 45);
        // Build the second calendar using the getTimeInMillis of the first
        cal2.setTimeInMillis(cal1.getTimeInMillis());

        assertEquals(2001, cal2.get(Calendar.YEAR), getErrMsg(cal1));
        assertEquals(Calendar.JANUARY, cal2.get(Calendar.MONTH), getErrMsg(cal1));
        assertEquals(25, cal2.get(Calendar.DAY_OF_MONTH), getErrMsg(cal1));
        assertEquals(1, cal2.get(Calendar.HOUR_OF_DAY), getErrMsg(cal1));
        assertEquals(23, cal2.get(Calendar.MINUTE), getErrMsg(cal1));
        assertEquals(45, cal2.get(Calendar.SECOND), getErrMsg(cal1));
        assertEquals(0, cal2.get(Calendar.MILLISECOND), getErrMsg(cal1));
    }

    // Utility to build a long error message
    private static String getErrMsg(Calendar cal) {
        return "Failed: expected 1/25/2001 1:23:45.000" +
                ", got " + (cal.get(Calendar.MONTH)+1) + "/" +
                cal.get(Calendar.DAY_OF_MONTH) +"/" +
                cal.get(Calendar.YEAR) + " " +
                cal.get(Calendar.HOUR_OF_DAY) + ":" +
                cal.get(Calendar.MINUTE) + ":" +
                cal.get(Calendar.SECOND) + "." +
                toMillis(cal.get(Calendar.MILLISECOND));
    }

    // Utility to convert value to format of expected milisecond value
    private static String toMillis(int m) {
        StringBuilder sb = new StringBuilder();
        if (m < 100) {
            sb.append('0');
        }
        if (m < 10) {
            sb.append('0');
        }
        return sb.append(m).toString();
    }
}
