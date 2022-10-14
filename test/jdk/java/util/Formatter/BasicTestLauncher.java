/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/* @test
 * @summary Unit test for formatter
 * @library /test/lib
 * @compile Basic.java
 * @bug 4906370 4962433 4973103 4989961 5005818 5031150 4970931 4989491 5002937
 *      5005104 5007745 5061412 5055180 5066788 5088703 6317248 6318369 6320122
 *      6344623 6369500 6534606 6282094 6286592 6476425 5063507 6469160 6476168
 *      8059175 8204229
 *
 * @run main/othervm BasicTestLauncher
 */
public class BasicTestLauncher {
    // US/Pacific time zone
    private static final String TZ_UP = "US/Pacific";
    // Asia/Novosibirsk time zone
    private static final String TZ_AN = "Asia/Novosibirsk";


    public static void main(String[] args){
        runFormatterTests(TZ_UP);
        runFormatterTests(TZ_AN);
    }

    /**
     * Test to validate whether the desired time zone
     * passes the Formatter Basic unit tests
     * @param timeZone the time zone to be set in the sub process environment
     */
    private static void runFormatterTests(String timeZone){
        try {
            System.out.printf("$$$ Testing against %s!%n", timeZone);

            // Build and run Basic class with correct configuration
            ProcessBuilder pb = ProcessTools.createTestJvm("-Djava.locale.providers=CLDR", "Basic");
            pb.environment().put("TZ", timeZone);
            Process process = pb.start();

            // Ensure process ran successfully and passed all tests
            OutputAnalyzer output = new OutputAnalyzer(process);
            output.shouldNotContain("failure(s)")
                    .shouldHaveExitValue(0)
                    .reportDiagnosticSummary();

            System.out.printf("$$$ %s passed as expected!%n", timeZone);
        }catch (Exception err) {
            throw new RuntimeException(String.format("$$$ Error(s) found within %s subprocess: " +
                    "%s%n", timeZone, err.getMessage()));
        }
    }
}
