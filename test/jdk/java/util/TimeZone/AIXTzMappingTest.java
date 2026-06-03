/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8380993
 * @library /test/lib
 * @summary Validates AIX timezone mapping behavior where POSIX TZ strings
 * with comma-separated DST rules are truncated and mapped through tzmappings
 * to IANA timezone IDs, ensuring proper DST transitions.
 * @requires os.family == "aix"
 * @run main/othervm AIXTzMappingTest
 */

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class AIXTzMappingTest {
    // POSIX TZ strings that should be mapped via tzmappings
    // CET-1CEST,M3.5.0,M10.5.0 should map to Europe/Paris
    private static String TZ_CET = "CET-1CEST,M3.5.0,M10.5.0";
    // MEZ-1MESZ,M3.5.0,M10.5.0/3 should map to Europe/Berlin
    private static String TZ_MEZ = "MEZ-1MESZ,M3.5.0,M10.5.0/3";
    public static void main(String args[]) throws Throwable {
        if (args.length == 0) {
            // Test 1: CET-1CEST with comma suffix
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder("AIXTzMappingTest", "runTZTest");
            pb.environment().put("TZ", TZ_CET);
            OutputAnalyzer output = ProcessTools.executeProcess(pb);
            output.shouldHaveExitValue(0);
            // Test 2: MEZ-1MESZ with comma suffix
            pb.environment().put("TZ", TZ_MEZ);
            output = ProcessTools.executeProcess(pb);
            output.shouldHaveExitValue(0);
        } else {
            runTZTest();
        }
    }

    /* On AIX, POSIX TZ strings like "CET-1CEST,M3.5.0,M10.5.0" are truncated
     * at the comma and mapped through tzmappings to IANA timezone IDs.
     * This test verifies:
     * 1. The timezone is mapped to a proper IANA ID (not a GMT offset)
     * 2. DST transitions work correctly with the mapped timezone
     */
    private static void runTZTest() {
        Date time = new Date();
        String tzStr = System.getenv("TZ");
        if (tzStr == null)
            throw new RuntimeException("Got unexpected timezone information: TZ is null");
        // Get the default timezone set by the JVM
        TimeZone tz = TimeZone.getDefault();
        String tzId = tz.getID();
        // Verify we got a proper IANA timezone ID, not a GMT offset
        // AIX should map to Europe/Paris or Europe/Berlin, not GMT+01:00
        if (tzId.startsWith("GMT")) {
            throw new RuntimeException(
                "Expected IANA timezone ID but got GMT offset: " + tzId +
                " for TZ=" + tzStr);
        }
        // Verify DST transitions work correctly
        // For CET/MEZ: Standard time = GMT+1, DST = GMT+2
        if (tz.inDaylightTime(time)) {
            // We are in Daylight savings period - expect GMT+02:00
            if (time.toString().contains("GMT+02:00") ||
                time.toString().contains("CEST") ||
                time.toString().contains("MESZ")) {
                System.out.println("AIX timezone mapping test passed: " +
                    tzId + " (DST active) for TZ=" + tzStr);
                return;
            }
        } else {
            // Standard time - expect GMT+01:00
            if (time.toString().contains("GMT+01:00") ||
                time.toString().contains("CET") ||
                time.toString().contains("MEZ")) {
                System.out.println("AIX timezone mapping test passed: " +
                    tzId + " (standard time) for TZ=" + tzStr);
                return;
            }
        }
        // Reaching here means time zone did not match up as expected
        throw new RuntimeException(
            "Got unexpected timezone information: TZ=" + tzStr +
            ", mapped to: " + tzId + ", time: " + time);
    }
}

