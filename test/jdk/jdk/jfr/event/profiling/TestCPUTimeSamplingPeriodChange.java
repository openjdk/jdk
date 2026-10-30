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

package jdk.jfr.event.profiling;

import jdk.jfr.EventSettings;
import jdk.jfr.Recording;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import static jdk.test.lib.jfr.EventNames.CPUTimeSample;

/*
 * @test
 * @bug 8396013
 * @summary Repeatedly change CPU-time sampling period while threads are starting and exiting
 * @requires vm.hasJFR & vm.flagless & os.family == "linux"
 * @library /test/lib
 * @run driver jdk.jfr.event.profiling.TestCPUTimeSamplingPeriodChange --check-output
 */
public class TestCPUTimeSamplingPeriodChange {

    private static final int ITERATIONS = 5000;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "--check-output".equals(args[0])) {
            checkOutput();
        } else {
            runTest();
        }
    }

    // Run the test in a forked process and verify that it does not log
    // any warnings or errors about failed timer operations
    private static void checkOutput() throws Exception {
        OutputAnalyzer output = ProcessTools.executeTestJava(
                TestCPUTimeSamplingPeriodChange.class.getName());
        output.shouldHaveExitValue(0);
        output.shouldNotContain("warning");
        output.shouldNotContain("error");
    }

    private static void runTest() {
        int threads = Math.clamp(Runtime.getRuntime().availableProcessors() - 1, 2, 15);
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(TestCPUTimeSamplingPeriodChange::thrash, "Thread thrasher " + i);
            t.setDaemon(true);
            t.start();
        }

        try (Recording r = new Recording()) {
            r.start();
            EventSettings setting = r.enable(CPUTimeSample);
            for (int i = 0; i < ITERATIONS; i++) {
                // Each change of the sampling period updates CPU timers of all threads
                setting.with("throttle", "1ms").with("throttle", "2ms");
            }
            r.stop();
        }
    }

    // Start and immediately terminate threads in a loop
    private static void thrash() {
        try {
            while (true) {
                Thread t = new Thread();
                t.start();
                t.join();
            }
        } catch (InterruptedException e) {
            // ignore
        }
    }
}
