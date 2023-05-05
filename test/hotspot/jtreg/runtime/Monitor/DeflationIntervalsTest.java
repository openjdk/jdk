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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/*
 * @test id=defaults
 * @bug 8305994 8306825
 * @summary Test the deflation intervals options
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest defaults
 */

/*
 * @test id=allIntervalsZero
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest allIntervalsZero
 */

/*
 * @test id=allThresholdsZero
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest allThresholdsZero
 */

/*
 * @test id=guaranteed_noThresholdMUDT_noSafepoint
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest guaranteed_noThresholdMUDT_noSafepoint
 */

/*
 * @test id=guaranteed_noThresholdMUDT_safepoint
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest guaranteed_noThresholdMUDT_safepoint
 */

/*
 * @test id=guaranteed_noThresholdADI_noSafepoint
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest guaranteed_noThresholdADI_noSafepoint
 */

/*
 * @test id=guaranteed_noThresholdADI_safepoint
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest guaranteed_noThresholdADI_safepoint
 */

/*
 * @test id=noGuaranteedGADT_threshold_noSafepoint
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest noGuaranteedGADT_threshold_noSafepoint
 */

/*
 * @test id=noGuaranteedGADT_threshold_safepoint
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest noGuaranteedGADT_threshold_safepoint
 */

/*
 * @test id=guaranteed_threshold_noSafepoint
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest guaranteed_threshold_noSafepoint
 */

/*
 * @test id=guaranteed_threshold_safepoint
 * @requires vm.flagless
 * @library /test/lib
 * @run driver DeflationIntervalsTest guaranteed_threshold_safepoint
 */

public class DeflationIntervalsTest {

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
            case "defaults":
                // Try with all defaults
                test(Disabled.NO, Guaranteed.MAYBE, Threshold.MAYBE);
                break;

            case "allIntervalsZero":
                // Try with all deflation intervals at zero
                test(Disabled.YES, Guaranteed.NO, Threshold.NO,
                    "-XX:GuaranteedAsyncDeflationInterval=0",
                    "-XX:AsyncDeflationInterval=0",
                    "-XX:GuaranteedSafepointInterval=0"
                );
                break;

            case "allThresholdsZero":
                // Try with all heuristics thresholds at zero
                test(Disabled.NO, Guaranteed.MAYBE, Threshold.NO,
                    "-XX:MonitorUsedDeflationThreshold=0"
                );
                break;

            // Try with guaranteed interval only enabled, threshold heuristics disabled via MUDT,
            // with and without guaranteed safepoints

            case "guaranteed_noThresholdMUDT_noSafepoint":
                test(Disabled.NO, Guaranteed.YES, Threshold.NO,
                    "-XX:GuaranteedAsyncDeflationInterval=100",
                    "-XX:MonitorUsedDeflationThreshold=0",
                    "-XX:GuaranteedSafepointInterval=0"
                );
                break;

            case "guaranteed_noThresholdMUDT_safepoint":
                test(Disabled.NO, Guaranteed.YES, Threshold.NO,
                    "-XX:GuaranteedAsyncDeflationInterval=100",
                    "-XX:MonitorUsedDeflationThreshold=0"
                );
                break;

            // Try with guaranteed interval only enabled, threshold heuristics disabled via ADI
            // with and without guaranteed safepoints

            case "guaranteed_noThresholdADI_noSafepoint":
                test(Disabled.NO, Guaranteed.YES, Threshold.NO,
                    "-XX:GuaranteedAsyncDeflationInterval=100",
                    "-XX:AsyncDeflationInterval=0",
                    "-XX:GuaranteedSafepointInterval=0"
                );
                break;

            case "guaranteed_noThresholdADI_safepoint":
                test(Disabled.NO, Guaranteed.YES, Threshold.NO,
                    "-XX:GuaranteedAsyncDeflationInterval=100",
                    "-XX:AsyncDeflationInterval=0"
                );
                break;

            // Try with only threshold heuristics, guaranteed is disabled with GADT
            // with and without guaranteed safepoints

            case "noGuaranteedGADT_threshold_noSafepoint":
                test(Disabled.NO, Guaranteed.NO, Threshold.YES,
                    "-XX:GuaranteedAsyncDeflationInterval=0",
                    "-XX:MonitorUsedDeflationThreshold=1",
                    "-XX:GuaranteedSafepointInterval=0"
                );
                break;

            case "noGuaranteedGADT_threshold_safepoint":
                test(Disabled.NO, Guaranteed.NO, Threshold.YES,
                    "-XX:GuaranteedAsyncDeflationInterval=0",
                    "-XX:MonitorUsedDeflationThreshold=1"
                );
                break;

            // Try with both threshold heuristics and guaranteed interval enabled
            // with and without guaranteed safepoints

            case "guaranteed_threshold_noSafepoint":
                test(Disabled.NO, Guaranteed.YES, Threshold.YES,
                    "-XX:GuaranteedAsyncDeflationInterval=5000",
                    "-XX:MonitorUsedDeflationThreshold=1",
                    "-XX:GuaranteedSafepointInterval=0"
                );
                break;

            case "guaranteed_threshold_safepoint":
                // Try with both threshold heuristics and guaranteed interval enabled
                test(Disabled.NO, Guaranteed.YES, Threshold.YES,
                    "-XX:GuaranteedAsyncDeflationInterval=5000",
                    "-XX:MonitorUsedDeflationThreshold=1"
                );
                break;

            default:
                throw new IllegalArgumentException("Unknown test: " + test);
        }
    }

    static final String MSG_THRESHOLD  = "Async deflation needed: monitors used are above the threshold";
    static final String MSG_GUARANTEED = "Async deflation needed: guaranteed interval";
    static final String MSG_DISABLED   = "Async deflation is disabled";

    public static void test(Disabled disabled, Guaranteed guaranteed, Threshold threshold, String... args) throws Exception {
        List<String> opts = new ArrayList<>();
        opts.add("-Xmx128M");
        opts.add("-XX:+UnlockDiagnosticVMOptions");
        opts.add("-Xlog:monitorinflation=info");
        opts.addAll(Arrays.asList(args));
        opts.add("DeflationIntervalsTest$Test");

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(opts);
        OutputAnalyzer oa = new OutputAnalyzer(pb.start());
        oa.shouldHaveExitValue(0);

        switch (disabled) {
            case YES: oa.shouldContain(MSG_DISABLED);    break;
            case NO:  oa.shouldNotContain(MSG_DISABLED); break;
            case MAYBE:                                  break;
        }

        switch (threshold) {
            case YES: oa.shouldContain(MSG_THRESHOLD);    break;
            case NO:  oa.shouldNotContain(MSG_THRESHOLD); break;
            case MAYBE:                                   break;
        }

        switch (guaranteed) {
            case YES: oa.shouldContain(MSG_GUARANTEED);    break;
            case NO:  oa.shouldNotContain(MSG_GUARANTEED); break;
            case MAYBE:                                    break;
        }

        if (threshold == Threshold.YES || guaranteed == Guaranteed.YES) {
            assertDeflations(oa);
        } else if (threshold == Threshold.NO && guaranteed == Guaranteed.NO) {
            assertNoDeflations(oa);
        } else {
            // Don't know
        }
    }

    static final String MSG_FINAL_AUDIT = "Starting the final audit";
    static final String MSG_BEGIN_DEFLATING = "begin deflating";

    private static void assertNoDeflations(OutputAnalyzer oa) {
        for (String line : oa.asLines()) {
            if (line.contains(MSG_FINAL_AUDIT)) {
                // Final deflations started, with no prior deflations, good.
                return;
            }
            if (line.contains(MSG_BEGIN_DEFLATING)) {
                // Deflations detected before final ones, bad
                oa.reportDiagnosticSummary();
                throw new IllegalStateException("FAILED");
            }
        }
    }

    private static void assertDeflations(OutputAnalyzer oa) {
        for (String line : oa.asLines()) {
            if (line.contains(MSG_FINAL_AUDIT)) {
                // Final deflations started, with no prior deflations, bad.
                oa.reportDiagnosticSummary();
                throw new IllegalStateException("FAILED");
            }
            if (line.contains(MSG_BEGIN_DEFLATING)) {
                // Deflations detected before final ones, good
                return;
            }
        }
    }

    enum Disabled {
        YES,
        NO,
        MAYBE,
    }

    enum Threshold {
        YES,
        NO,
        MAYBE,
    }

    enum Guaranteed {
        YES,
        NO,
        MAYBE,
    }

}
