/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8226416
 * @summary Test the MonitorUsedDeflationThreshold and NoAsyncDeflationProgressMax options.
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver MonitorUsedDeflationThresholdTest
 */

public class MonitorUsedDeflationThresholdTest {
    public static final int DELAY_SECS = 10;
    public static int inflate_count = 0;
    public static Object[] monitors;

    public static void do_work(int count) {
        System.out.println("Recursion count=" + count);
        if (count > inflate_count) {
            System.out.println("Exceeded inflate_count=" + inflate_count);

            System.out.println("Delaying for " + DELAY_SECS + " secs.");
            try {
                Thread.sleep(DELAY_SECS * 1000);
            } catch (InterruptedException ie) {
                // ignore InterruptedException
            }
            System.out.println("Done delaying for " + DELAY_SECS + " secs.");
            return;
        }

        synchronized(monitors[count]) {
            try {
                monitors[count].wait(1);  // force inflation
            } catch (InterruptedException ie) {
                // ignore InterruptedException
            }
            do_work(count + 1);
        }
    }

    public static void usage() {
        System.err.println("Usage: java " +
                           "MonitorUsedDeflationThresholdTest inflate_count");
    }


    private static ProcessBuilder processCommand(String loggingLevel) {
        return ProcessTools.createLimitedTestJavaProcessBuilder(
            // Test doesn't need much Java heap:
            "-Xmx100M",
            // AvgMonitorsPerThreadEstimate == 1 means we'll start with
            // an in_use_list_ceiling of <n-threads> plus a couple of
            // of monitors for threads that call Object.wait().
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:AvgMonitorsPerThreadEstimate=1",
            // MonitorUsedDeflationThreshold == 10 means we'll request
            // deflations when 10% of monitors are used rather than the
            // default 90%. This should allow the test to tolerate a burst
            // of used monitors by threads not under this test's control.
            "-XX:MonitorUsedDeflationThreshold=10",
            // Enable monitorinflation logging so we can see that
            // MonitorUsedDeflationThreshold and
            // NoAsyncDeflationProgressMaxoption are working.
            "-Xlog:monitorinflation=" + loggingLevel,
            // Run the test with inflate_count == 33 since that
            // reproduced the bug with JDK13. With inflate_count == 33, an
            // initial ceiling == 12 and MonitorUsedDeflationThreshold == 10,
            // we should hit NoAsyncDeflationProgressMax at least 3 times.
            "MonitorUsedDeflationThresholdTest", "33");
    }

    private static void testProcess1() throws Exception {
        ProcessBuilder pb = processCommand("info");

        OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());
        output_detail.shouldHaveExitValue(0);

        // This mesg means:
        // - AvgMonitorsPerThreadEstimate == 1 reduced in_use_list_ceiling
        //   to a small number.
        // - and we crossed MonitorUsedDeflationThreshold:
        output_detail.shouldMatch("begin deflating: .*");
        System.out.println("Found beginning of a deflation cycle.");

        // This mesg means we hit NoAsyncDeflationProgressMax and
        // had to adjust the in_use_list_ceiling:
        String too_many = output_detail.firstMatch("Too many deflations without progress; .*", 0);
        if (too_many == null) {
            output_detail.reportDiagnosticSummary();
            throw new RuntimeException("Did not find too_many string in output.\n");
        }
        System.out.println("too_many='" + too_many + "'");
        // Uncomment the following line for dumping test output in passing runs:
        // output_detail.reportDiagnosticSummary();

        System.out.println("PASSED.");
    }

    private static void testProcess2() throws Exception {
        ProcessBuilder pb = processCommand("debug");
        OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());
        output_detail.shouldHaveExitValue(0);

        // Test that logging reports in_use_list with each iteration of the deflation thread in debug mode.
        // but not monitor details with each report
        output_detail.shouldMatch(   ".debug..monitorinflation. Checking in_use_list:");
        output_detail.shouldNotMatch(".debug..monitorinflation. .*is_busy");

        // Reporting stats at exit is in Info mode, and contains monitor details
        output_detail.shouldMatch(".info ..monitorinflation. Checking in_use_list:");
        output_detail.shouldMatch(".info ..monitorinflation. .*is_busy");

        System.out.println("PASSED.");
    }

    private static void testProcess3() throws Exception {
        ProcessBuilder pb = processCommand("trace");
        OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());
        output_detail.shouldHaveExitValue(0);

        // Test that logging reports in_use_list with each iteration of the deflation thread in debug mode.
        // and monitor details with each report
        output_detail.shouldMatch(".debug..monitorinflation. Checking in_use_list:");
        output_detail.shouldMatch(".trace..monitorinflation. .*is_busy");

        // Reporting stats at exit is in Info mode, and contains monitor details
        output_detail.shouldMatch(".info ..monitorinflation. Checking in_use_list:");
        output_detail.shouldMatch(".info ..monitorinflation. .*is_busy");

        System.out.println("PASSED.");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // Without args we invoke the tests in a java sub-process.
            testProcess1();
            testProcess2();
            testProcess3();
            return;
        }
        // else we are the exec'd java subprocess, so run the actual test:

        try {
            inflate_count = Integer.decode(args[0]);
        } catch (NumberFormatException nfe) {
            usage();
            throw new RuntimeException("ERROR: '" + args[0] +
                                       "': bad inflate_count.");
        }

        System.out.println("Hello from MonitorUsedDeflationThresholdTest!");
        System.out.println("inflate_count=" + inflate_count);

        monitors = new Object[inflate_count + 1];
        for (int i = 1; i <= inflate_count; i++) {
            monitors[i] = new Object();
        }
        do_work(1);
    }
}
