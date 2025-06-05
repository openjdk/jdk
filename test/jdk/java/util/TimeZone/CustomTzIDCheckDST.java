/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8285838
 * @library /test/lib
 * @summary This test will ensure that daylight savings rules are followed
 * appropriately when setting a custom timezone ID via the TZ env variable.
 * @requires os.family != "windows"
 * @run main/othervm CustomTzIDCheckDST
 */

import java.util.Calendar;
import java.util.Date;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class CustomTzIDCheckDST {
    // Northern Hemisphere
    private static String CUSTOM_TZ = "MEZ-1MESZ,M3.5.0,M10.5.0/3";
    // Simulate Southern Hemisphere
    private static String CUSTOM_TZ2 = "MEZ-1MESZ,M10.5.0,M3.5.0/3";
    public static void main(String args[]) throws Throwable {
        if (args.length == 0) {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("CustomTzIDCheckDST", "runTZTest");
            pb.environment().put("TZ", CUSTOM_TZ);
            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            output.shouldHaveExitValue(0);
            pb.environment().put("TZ", CUSTOM_TZ2);
            output = ProcessTools.executeProcess(pb);
            output.shouldHaveExitValue(0);
        } else {
            runTZTest();
        }
    }

    /* TZ is set to "MEZ-1MESZ,M3.5.0,M10.5.0/3", it will be the northern hemisphere.
     * This ensures the transition periods for Daylights Savings should be at March's last
     * Sunday and October's last Sunday.
     */
    private static void runTZTest() {
        Date time = new Date();
        String tzStr = System.getenv("TZ");
        if (tzStr == null)
            throw new RuntimeException("Got unexpected timezone information: TZ is null");
        boolean nor = CUSTOM_TZ.equals(tzStr);
        TimeZone tz = new SimpleTimeZone(3600000, tzStr,
            nor ? Calendar.MARCH : Calendar.OCTOBER, -1,
            Calendar.SUNDAY, 3600000, SimpleTimeZone.UTC_TIME,
            nor ? Calendar.OCTOBER : Calendar.MARCH, -1,
            Calendar.SUNDAY, 3600000, SimpleTimeZone.UTC_TIME,
            3600000);
        if (tz.inDaylightTime(time)) {
            // We are in Daylight savings period.
            if (time.toString().endsWith("GMT+02:00 " + Integer.toString(time.getYear() + 1900)))
                return;
        } else {
            if (time.toString().endsWith("GMT+01:00 " + Integer.toString(time.getYear() + 1900)))
                return;
        }

        // Reaching here means time zone did not match up as expected.
        throw new RuntimeException("Got unexpected timezone information: " + tzStr + " " + time);
    }

    private static ZonedDateTime getLastSundayOfMonth(ZonedDateTime date) {
        return date.with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY));
    }
}
