/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test id=allDisabled
 * @bug 8305994
 * @summary Test the GuaranteedAsyncDeflationInterval option
 * @requires vm.flagless
 * @library /test/lib
 * @run driver GuaranteedAsyncDeflationIntervalTest allDisabled
 */

/*
 * @test id=guaranteedNoMUDT
 * @requires vm.flagless
 * @library /test/lib
 * @run driver GuaranteedAsyncDeflationIntervalTest guaranteedNoMUDT
 */

/*
 * @test id=guaranteedNoADI
 * @requires vm.flagless
 * @library /test/lib
 * @run driver GuaranteedAsyncDeflationIntervalTest guaranteedNoADI
 */

/*
 * @test id=allEnabled
 * @requires vm.flagless
 * @library /test/lib
 * @run driver GuaranteedAsyncDeflationIntervalTest allEnabled
 */

public class GuaranteedAsyncDeflationIntervalTest {

    public static class Test {
        // Inflate a lot of monitors, so that threshold heuristics definitely fires
        private static final int MONITORS = 10_000;

        // Use a handful of threads to inflate the monitors, to eat the cost of
        // wait(1) calls. This can be larger than available parallelism, since threads
        // would be time-waiting.
        private static final int THREADS = 16;

        private static Thread[] threads;
        private static Object[] monitors;

        public static void main(String... args) throws Exception {
            monitors = new Object[MONITORS];
            threads = new Thread[THREADS];

            for (int t = 0; t < THREADS; t++) {
                int monStart = t * MONITORS / THREADS;
                int monEnd = (t + 1) * MONITORS / THREADS;
                threads[t] = new Thread(() -> {
                    for (int m = monStart; m < monEnd; m++) {
                        Object o = new Object();
                        synchronized (o) {
                            try {
                                o.wait(1);
                            } catch (InterruptedException e) {
                            }
                        }
                        monitors[m] = o;
                    }
                });
                threads[t].start();
            }

            for (Thread t : threads) {
                t.join();
            }

            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ie) {
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expect the test label");
        }

        String test = args[0];
        switch (test) {
            case "allDisabled":
                testAllDisabled();
                break;
            case "guaranteedNoMUDT":
                testGuaranteedNoMUDT();
                break;
            case "guaranteedNoADI":
                testGuaranteedNoADI();
                break;
            case "allEnabled":
                testAllEnabled();
                break;
            default:
                throw new IllegalArgumentException("Unknown test: " + test);
        }
    }

    static final String MSG_THRESHOLD  = "Async deflation needed: monitors used are above the threshold";
    static final String MSG_GUARANTEED = "Async deflation needed: guaranteed interval";

    // Try with all heuristics disabled
    public static void testAllDisabled() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx100M",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:GuaranteedAsyncDeflationInterval=0",
            "-XX:AsyncDeflationInterval=0",
            "-XX:MonitorUsedDeflationThreshold=0",
            "-Xlog:monitorinflation=info",
            "GuaranteedAsyncDeflationIntervalTest$Test");

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);

        oa.shouldNotContain(MSG_THRESHOLD);
        oa.shouldNotContain(MSG_GUARANTEED);
        assertNoDeflations(oa);
    }

    // Try with guaranteed interval only enabled, threshold heuristics disabled via MUDT
    public static void testGuaranteedNoMUDT() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx100M",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:GuaranteedAsyncDeflationInterval=100",
            "-XX:MonitorUsedDeflationThreshold=0",
            "-Xlog:monitorinflation=info",
            "GuaranteedAsyncDeflationIntervalTest$Test");

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);

        oa.shouldNotContain(MSG_THRESHOLD);
        oa.shouldContain(MSG_GUARANTEED);
        assertDeflations(oa);
    }

    // Try with guaranteed interval only enabled, threshold heuristics disabled via ADI
    public static void testGuaranteedNoADI() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx100M",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:GuaranteedAsyncDeflationInterval=100",
            "-XX:AsyncDeflationInterval=0",
            "-Xlog:monitorinflation=info",
            "GuaranteedAsyncDeflationIntervalTest$Test");

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);

        oa.shouldNotContain(MSG_THRESHOLD);
        oa.shouldContain(MSG_GUARANTEED);
        assertDeflations(oa);
    }

    // Try with both threshold heuristics and guaranteed interval enabled
    public static void testAllEnabled() throws Exception {
        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
            "-Xmx100M",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:GuaranteedAsyncDeflationInterval=5000",
            "-XX:MonitorUsedDeflationThreshold=1",
            "-Xlog:monitorinflation=info",
            "GuaranteedAsyncDeflationIntervalTest$Test");

        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);

        oa.shouldContain(MSG_THRESHOLD);
        oa.shouldContain(MSG_GUARANTEED);
        assertDeflations(oa);
    }

    private static void assertNoDeflations(OutputAnalyzer oa) {
        for (String line : oa.asLines()) {
            if (line.contains("Starting the final audit")) {
                // Final deflations started, with no prior deflations, good.
                return;
            }
            if (line.contains("begin deflating")) {
                // Deflations detected before final ones, bad
                oa.reportDiagnosticSummary();
                throw new IllegalStateException("FAILED");
            }
        }
    }

    private static void assertDeflations(OutputAnalyzer oa) {
        for (String line : oa.asLines()) {
            if (line.contains("Starting the final audit")) {
                // Final deflations started, with no prior deflations, bad.
                oa.reportDiagnosticSummary();
                throw new IllegalStateException("FAILED");
            }
            if (line.contains("begin deflating")) {
                // Deflations detected before final ones, good
                return;
            }
        }
    }
}
