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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

/*
 * @test id=Serial
 * @bug 8392050
 * @requires vm.gc.Serial
 * @summary Verify serial heap inspection doesn't create and use the diagnostic worker pool (Serial GC)
 * @library /test/lib
 * @run driver ClassHistogramParallelTest -XX:+UseSerialGC
 */

/*
 * @test id=Parallel
 * @bug 8392050
 * @requires vm.gc.Parallel
 * @summary Verify parallel heap inspection uses the diagnostic worker pool (Parallel GC)
 * @library /test/lib
 * @run driver ClassHistogramParallelTest -XX:+UseParallelGC
 */

/*
 * @test id=G1
 * @bug 8392050
 * @requires vm.gc.G1
 * @summary Verify parallel heap inspection uses the diagnostic worker pool (G1 GC)
 * @library /test/lib
 * @run driver ClassHistogramParallelTest -XX:+UseG1GC
 */

/*
 * @test id=Shenandoah
 * @bug 8392050
 * @requires vm.gc.Shenandoah
 * @summary Verify parallel heap inspection uses the diagnostic worker pool (Shenandoah GC)
 * @library /test/lib
 * @run driver ClassHistogramParallelTest -XX:+UseShenandoahGC
 */

/*
 * @test id=ShenandoahGenerational
 * @bug 8392050
 * @requires vm.gc.Shenandoah
 * @summary Verify parallel heap inspection uses the diagnostic worker pool (Generational Shenandoah GC)
 * @library /test/lib
 * @run driver ClassHistogramParallelTest -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 */

/*
 * @test id=Z
 * @bug 8392050
 * @requires vm.gc.Z
 * @summary Verify parallel heap inspection uses the diagnostic worker pool (Z GC)
 * @library /test/lib
 * @run driver ClassHistogramParallelTest -XX:+UseZGC
 */

/*
 * @test id=Epsilon
 * @bug 8392050
 * @requires vm.gc.Epsilon
 * @summary Verify serial heap inspection doesn't create and use the diagnostic worker pool (Epsilon GC)
 * @library /test/lib
 * @run driver ClassHistogramParallelTest -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 */

public class ClassHistogramParallelTest {
    private static final int ACTIVE_PROCESSOR_COUNT = 8;
    private static final int DEFAULT_WORKERS = Math.max(1, ACTIVE_PROCESSOR_COUNT * 3 / 8);
    private static final String POOL_CREATION_MSG =
        "Created diagnostic worker pool (max " + ACTIVE_PROCESSOR_COUNT + " workers)";

    private static void checkAndVerify(OutputAnalyzer threadPrintOut, LingeredApp app,
                                       int expectedDiagWorkers, String[] gcArgs) throws Exception {
        threadPrintOut.shouldHaveExitValue(0);
        OutputAnalyzer appOut = new OutputAnalyzer(app.getProcessStdout());

        List<String> gcFlags = Arrays.asList(gcArgs);
        if (gcFlags.contains("-XX:+UseSerialGC") || gcFlags.contains("-XX:+UseEpsilonGC")) {
            System.out.println("Serial or Epsilon GC detected.");
            expectedDiagWorkers = 0;
        }

        if (expectedDiagWorkers > 1) {
            appOut.shouldContain(POOL_CREATION_MSG);

            // Verify that the worker pool was only created once.
            int count = (int)appOut.stdoutAsLines().stream()
                                   .filter(line -> line.contains(POOL_CREATION_MSG))
                                   .count();
            Asserts.assertEquals(1, count);

            threadPrintOut.shouldContain("\"DiagWorker#" + (expectedDiagWorkers - 1) + "\"");
            threadPrintOut.shouldNotContain("\"DiagWorker#" + expectedDiagWorkers + "\"");
        } else {
            appOut.shouldNotContain("Created diagnostic worker pool");
            threadPrintOut.shouldNotContain("DiagWorker");
        }
    }

    private static LingeredApp launchApp(String[] gcArgs) throws IOException {
        LingeredApp theApp = new LingeredApp();
        List<String> jvmArgs = new ArrayList<>(List.of("-Xlog:gc+task",
                                                       "-Xmx512m",
                                                       "-XX:ActiveProcessorCount=" + ACTIVE_PROCESSOR_COUNT));
        jvmArgs.addAll(Arrays.asList(gcArgs));
        LingeredApp.startApp(theApp, jvmArgs.toArray(new String[0]));
        return theApp;
    }

    public static void main(String[] args) throws Exception {
        LingeredApp theApp = launchApp(args);
        try {
            // Expect error message
            OutputAnalyzer out = attachJcmd(theApp.getPid(), "GC.class_histogram -parallel=-1");
            out.shouldContain("Parallel thread number out of range (>=0): -1");

            // Expect serial inspection.
            test("-parallel=1", 0, args, 1);

            // If no parallel threads are passed in or -parallel=0, the default number of parallel
            // threads to use is based on CPUs: max(1, ACTIVE_PROCESSOR_COUNT * 3 / 8).
            test("", DEFAULT_WORKERS, args, 1);
            test("-parallel=0", DEFAULT_WORKERS, args, 1);

            // Expect parallel heap inspection.
            test("-parallel=2", 2, args, 1);
            test("-parallel=" + ACTIVE_PROCESSOR_COUNT, ACTIVE_PROCESSOR_COUNT, args, 1);
            test("-parallel=" + Integer.MAX_VALUE, ACTIVE_PROCESSOR_COUNT, args, 1);

            // Expect that the diagnostic worker pool is created once for multiple requests.
            test("-parallel=2", 2, args, 5);
        } finally {
            theApp.stopApp();
        }
    }

    private static void test(String parallelThreads, int expectedDiagWorkers, String[] gcArgs, int attempts) throws Exception {
        LingeredApp theApp = launchApp(gcArgs);
        try {
            OutputAnalyzer classHistogramOut = attachJcmd(theApp.getPid(), "GC.class_histogram " + parallelThreads);
            verifyHistogram(classHistogramOut);

            for (int i = 1; i < attempts; i++) {
                classHistogramOut = attachJcmd(theApp.getPid(), "GC.class_histogram " + parallelThreads);
                verifyHistogram(classHistogramOut);
            }

            OutputAnalyzer threadPrintOut = attachJcmd(theApp.getPid(), "Thread.print");
            theApp.stopApp();
            checkAndVerify(threadPrintOut, theApp, expectedDiagWorkers, gcArgs);
        } finally {
            theApp.stopApp();
        }
    }

    private static OutputAnalyzer attachJcmd(long lingeredAppPid, String cmd) throws Exception {
        System.out.println("Testing pid " + lingeredAppPid);
        PidJcmdExecutor executor = new PidJcmdExecutor("" + lingeredAppPid);
        return executor.execute(cmd, true);
    }

    private static void verifyHistogram(OutputAnalyzer out) throws Exception {
        out.shouldHaveExitValue(0);
        // Verify that the histogram was printed.
        out.shouldMatch("^Total\\s+\\d+\\s+\\d+");
    }
}
