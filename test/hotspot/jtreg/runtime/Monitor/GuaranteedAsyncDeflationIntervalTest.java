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
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @bug 8305994
 * @summary Test the GuaranteedAsyncDeflationInterval option
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver GuaranteedAsyncDeflationIntervalTest
 */

public class GuaranteedAsyncDeflationIntervalTest {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
          driver();
        } else {
          test();
        }
    }

    // Inflate a lot of monitors, so that threshold heuristics definitely fires
    public static final int MONITORS = 10_000;

    public static Object[] monitors;

    public static void test() throws Exception {
        monitors = new Object[MONITORS];
        for (int i = 0; i < MONITORS; i++) {
            Object o = new Object();
            synchronized (o) {
                try {
                    o.wait(1); // Inflate!
                } catch (InterruptedException ie) {
                }
            }
            monitors[i] = o;
        }

        try {
            Thread.sleep(10_000);
        } catch (InterruptedException ie) {
        }
    }

    public static void driver() throws Exception {
        final String MSG_THRESHOLD  = "Async deflation needed: monitors used are above the threshold";
        final String MSG_GUARANTEED = "Async deflation needed: guaranteed interval reached";

        // Try with all heuristics disabled
        {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-Xmx100M",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:GuaranteedAsyncDeflationInterval=0",
                "-XX:MonitorUsedDeflationThreshold=0",
                "-Xlog:monitorinflation=info",
                "GuaranteedAsyncDeflationIntervalTest",
                "test");

            OutputAnalyzer oa = new OutputAnalyzer(pb.start());
            oa.shouldHaveExitValue(0);

            oa.shouldNotContain(MSG_THRESHOLD);
            oa.shouldNotContain(MSG_GUARANTEED);
            assertNoDeflations(oa);
        }

        // Try with guaranteed interval only enabled
        {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-Xmx100M",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:GuaranteedAsyncDeflationInterval=100",
                "-XX:MonitorUsedDeflationThreshold=0",
                "-Xlog:monitorinflation=info",
                "GuaranteedAsyncDeflationIntervalTest",
                "test");

            OutputAnalyzer oa = new OutputAnalyzer(pb.start());
            oa.shouldHaveExitValue(0);

            oa.shouldNotContain(MSG_THRESHOLD);
            oa.shouldContain(MSG_GUARANTEED);
            assertDeflations(oa);
        }

        // Try with both threshold heuristics and guaranteed interval enabled
        {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                "-Xmx100M",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:GuaranteedAsyncDeflationInterval=5000",
                "-XX:MonitorUsedDeflationThreshold=10",
                "-Xlog:monitorinflation=info",
                "GuaranteedAsyncDeflationIntervalTest",
                "test");

            OutputAnalyzer oa = new OutputAnalyzer(pb.start());
            oa.shouldHaveExitValue(0);

            oa.shouldContain(MSG_THRESHOLD);
            oa.shouldContain(MSG_GUARANTEED);
            assertDeflations(oa);
        }
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
