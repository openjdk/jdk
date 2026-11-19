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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RollHoursTest {

    // Should trigger multiple full HOUR/HOUR_OF_DAY cycles
    private static final List<Integer> hours =
            IntStream.rangeClosed(-55, 55).boxed().toList();
    // Various calendars to test against
    private static final List<Calendar> calendars = List.of(
            // GMT, independent of daylight saving time transitions
            new GregorianCalendar(TimeZone.getTimeZone("GMT")),
            // Daylight saving observing calendars
            new GregorianCalendar(TimeZone.getTimeZone("America/Chicago")),
            new GregorianCalendar(TimeZone.getTimeZone("America/Chicago")),
            new GregorianCalendar(TimeZone.getTimeZone("America/Los_Angeles")),
            new GregorianCalendar(TimeZone.getTimeZone("America/Los_Angeles"))
    );

    // Reset the times of each calendar. These calendars provide testing under
    // daylight saving transitions (or the lack thereof) and different AM/PM hours.
    @BeforeEach
    void clear() {
        // Reset all calendars each iteration for clean slate
        calendars.forEach(Calendar::clear);

        // Basic test, independent of daylight saving transitions
        calendars.get(0).set(2005, 8, 20, 12, 10, 25);

        // Transition to daylight saving time (CST/CDT) ---
        // Day of transition: 03/13/2016 (Sunday)
        //      Most interesting test case due to 2 AM skip
        calendars.get(1).set(2016, 2, 13, 3, 30, 55);
        // Day before transition: 03/12/2016 (Saturday)
        calendars.get(2).set(2016, 2, 12, 15, 20, 45);

        // Transition back to standard time (PST/PDT) ----
        // Day of transition: 11/06/2016 (Sunday)
        calendars.get(3).set(2016, 10, 6, 4, 15, 20);
        // Day before transition: 11/05/2016 (Saturday)
        calendars.get(4).set(2016, 10, 5, 12, 25, 30);
    }

    // Rolling the HOUR_OF_DAY field.
    @ParameterizedTest
    @FieldSource("hours")
    void HourOfDayTest(int hoursToRoll) {
        for (var cal : calendars) {
            var savedTime = cal.getTime();
            var savedHour = cal.get(Calendar.HOUR_OF_DAY);
            cal.roll(Calendar.HOUR_OF_DAY, hoursToRoll);
            assertEquals(getExpectedHour(hoursToRoll, savedHour, 24, cal, savedTime),
                    cal.get(Calendar.HOUR_OF_DAY),
                    getMessage(cal.getTimeZone(), savedTime, hoursToRoll));
        }
    }

    // Rolling the HOUR field.
    @ParameterizedTest
    @FieldSource("hours")
    void HourTest(int hoursToRoll) {
        for (var cal : calendars) {
            var savedTime = cal.getTime();
            var savedHour = cal.get(Calendar.HOUR_OF_DAY);
            cal.roll(Calendar.HOUR, hoursToRoll);
            assertEquals(getExpectedHour(hoursToRoll, savedHour, 12, cal, savedTime),
                    cal.get(Calendar.HOUR),
                    getMessage(cal.getTimeZone(), savedTime, hoursToRoll));
        }
    }

    // Gets the expected hour after rolling by X hours. Supports 12/24-hour cycle.
    // Special handling for non-existent 2 AM case on transition day.
    private static int getExpectedHour(int roll, int hour, int hourCycle, Calendar cal, Date oldDate) {
        // For example with HOUR_OF_DAY at 15 and a 24-hour cycle
        // For rolling forwards 50 hours -> (50 + 15) % 24 = 17
        // For hour backwards 50 hours -> (24 + (15 - 50) % 24) % 24
        //                             -> (24 - 11) % 24 = 13
        var result = (roll >= 0 ? (hour + roll) : (hourCycle + (hour + roll) % hourCycle)) % hourCycle;

        // 2 AM does not exist on transition day. Calculate normalized value accordingly
        if (cal.getTimeZone().inDaylightTime(oldDate) && cal.get(Calendar.MONTH) == Calendar.MARCH && result == 2) {
            return roll > 0 ? result + 1 : result - 1;
        } else {
            // Normal return value
            return result;
        }
    }

    // Get a TimeZone adapted error message
    private static String getMessage(TimeZone tz, Date date, int hoursToRoll) {
        var fmt = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL);
        fmt.setTimeZone(tz);
        return fmt.format(date) + " incorrectly rolled " + hoursToRoll;
    }
}
