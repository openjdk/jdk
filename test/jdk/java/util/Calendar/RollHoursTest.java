/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8367901
 * @summary Ensure hour rolling is correct. Particularly, when the HOUR/HOUR_OF_DAY
 *          amount rolled would cause the calendar to originate on the same hour as before
 *          the call.
 * @run junit RollHoursTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RollHoursTest {

    // Should trigger multiple full HOUR/HOUR_OF_DAY cycles
    private static final List<Integer> hours =
            IntStream.rangeClosed(-55, 55).boxed().toList();
    // Sample AM/PM Hours
    private static final int AM_HOUR = 3;
    private static final int PM_HOUR = 15;

    // Roll the HOUR_OF_DAY field. AM/PM specific calendar does not matter.
    @ParameterizedTest
    @FieldSource("hours")
    void HourOfDayTest(int hour) {
        var cal = new GregorianCalendar(2005, 8, 12, PM_HOUR, 30, 45);
        // E.g. For hour +50 -> (50 + 15) % 24 = 17
        //      For hour -50 -> (24 + (15 - 50) % 24) % 24
        //                   -> (24 + - 11) % 24 = 13
        var expectedHourOfDay = (hour >= 0 ? (PM_HOUR + hour) : (24 + (PM_HOUR + hour) % 24)) % 24 ;
        cal.roll(Calendar.HOUR_OF_DAY, hour);
        assertEquals(expectedHourOfDay, cal.get(Calendar.HOUR_OF_DAY),
                cal.getTime() + " incorrectly rolled " + hour);
    }

    // Roll the HOUR field for an AM calendar.
    @ParameterizedTest
    @FieldSource("hours")
    void AMHourTest(int hour) {
        var cal = new GregorianCalendar(2005, 8, 12, AM_HOUR, 30, 45);
        var expectedAMHour = (hour >= 0 ? (AM_HOUR + hour) : (12 + (AM_HOUR + hour) % 12)) % 12;
        cal.roll(Calendar.HOUR, hour);
        assertEquals(expectedAMHour, cal.get(Calendar.HOUR),
                cal.getTime() + " incorrectly rolled " + hour);
    }

    // Roll the HOUR field for a PM calendar.
    @ParameterizedTest
    @FieldSource("hours")
    void PMHourTest(int hour) {
        var cal = new GregorianCalendar(2005, 8, 12, PM_HOUR, 30, 45);
        var expectedPMHour = (hour >= 0 ? (PM_HOUR + hour) : (12 + (PM_HOUR + hour) % 12)) % 12;
        cal.roll(Calendar.HOUR, hour);
        assertEquals(expectedPMHour, cal.get(Calendar.HOUR),
                cal.getTime() + " incorrectly rolled " + hour);
    }
}
