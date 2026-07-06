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
 * to the expected IANA timezone IDs.
 * @requires os.family == "aix"
 * @run main/othervm AIXTzMappingTest
 */

import java.util.TimeZone;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class AIXTzMappingTest {

    // POSIX TZ strings that should be mapped via tzmappings
    private static final String TZ_CET = "CET-1CEST,M3.5.0,M10.5.0";
    private static final String TZ_MEZ = "MEZ-1MESZ,M3.5.0,M10.5.0/3";

    private static final String ID_PARIS = "Europe/Paris";
    private static final String ID_BERLIN = "Europe/Berlin";

    public static void main(String[] args) throws Throwable {
        if (args.length == 0) {
            runWithTZ(TZ_CET, ID_PARIS);
            runWithTZ(TZ_MEZ, ID_BERLIN);
        } else if (args.length == 1) {
            runTZTest(args[0]);
        } else {
            throw new RuntimeException(
                    "Expected 0 or 1 arguments, got " + args.length);
        }
    }

    private static void runWithTZ(String tz, String expectedId)
            throws Throwable {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                "AIXTzMappingTest", expectedId);

        pb.environment().put("TZ", tz);

        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        output.shouldHaveExitValue(0);
    }

    /*
     * On AIX, POSIX TZ strings such as:
     *     CET-1CEST,M3.5.0,M10.5.0
     *     MEZ-1MESZ,M3.5.0,M10.5.0/3
     * are truncated at the comma and mapped through tzmappings to
     * IANA timezone IDs.
     *
     * This test verifies that the expected IANA timezone ID is selected.
     */
    private static void runTZTest(String expectedId) {
        String tzStr = System.getenv("TZ");

        if (tzStr == null) {
            throw new RuntimeException(
                    "Got unexpected timezone information: TZ is null");
        }

        TimeZone tz = TimeZone.getDefault();
        String tzId = tz.getID();

        if (!expectedId.equals(tzId)) {
            throw new RuntimeException(
                    "Expected timezone ID " + expectedId
                    + " but got " + tzId
                    + " for TZ=" + tzStr);
        }

        System.out.println(
                "AIX timezone mapping test passed: "
                + tzId + " for TZ=" + tzStr);
    }
}
