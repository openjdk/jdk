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
import java.util.Arrays;

import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @bug 8392050
 * @summary Verify parallel heap inspection uses the diagnostic worker pool.
 * @library /test/lib
 * @run main ClassHistogramParallelTest
 */

public class ClassHistogramParallelTest {
    private static final int ACTIVE_PROCESSOR_COUNT = 2;
    private static final int PARALLEL_GC_THREADS = 4;

    private static void checkAndVerify(OutputAnalyzer classHistogramOut, OutputAnalyzer threadPrintOut,
                                       LingeredApp app, int expectedDiagWorkers) throws Exception {
        classHistogramOut.shouldHaveExitValue(0);
        threadPrintOut.shouldHaveExitValue(0);
        OutputAnalyzer appOut = new OutputAnalyzer(app.getProcessStdout());
        String opts = Arrays.asList(Utils.getTestJavaOpts()).toString();

        if (opts.contains("-XX:+UseSerialGC")) {
            System.out.println("UseSerialGC detected.");
            expectedDiagWorkers = 0;
        } else if (opts.contains("-XX:+UseEpsilonGC")) {
            System.out.println("UseEpsilonGC detected.");
            expectedDiagWorkers = 0;
        }

        // Verify that the histogram was printed
        classHistogramOut.shouldMatch("^Total\\s+\\d+\\s+\\d+");

        if (expectedDiagWorkers > 1) {
            appOut.shouldContain("Created diagnostic worker pool (max " + PARALLEL_GC_THREADS + " workers)");
            threadPrintOut.shouldContain("\"DiagWorker#" + (expectedDiagWorkers - 1) + "\"");
            threadPrintOut.shouldNotContain("\"DiagWorker#" + expectedDiagWorkers + "\"");
        } else {
            appOut.shouldNotContain("Created diagnostic worker pool");
            threadPrintOut.shouldNotContain("DiagWorker");
        }
    }

    private static LingeredApp launchApp() throws IOException {
        LingeredApp theApp = new LingeredApp();
        LingeredApp.startApp(theApp, "-Xlog:gc+task", "-Xmx512m",
                             "-XX:ActiveProcessorCount=" + ACTIVE_PROCESSOR_COUNT,
                             "-XX:ParallelGCThreads=" + PARALLEL_GC_THREADS);
        return theApp;
    }

    public static void main(String[] args) throws Exception {
        LingeredApp theApp = launchApp();
        try {
            // Expect error message
            OutputAnalyzer out = attachJcmd(theApp.getPid(), "GC.class_histogram -parallel=-1");
            out.shouldContain("Parallel thread number out of range (>=0): -1");

            // If no parallel threads are passed in or -parallel=0, the default number of parallel
            // threads to use is based on CPUs: max(1, (ACTIVE_PROCESSOR_COUNT * 3 / 8)). If the
            // result is <= 1, then heap inspection will run serially.
            test("", 0);
            test("-parallel=0", 0);
            test("-parallel=1", 0);

            // Expect parallel inspection
            test("-parallel=2", 2);
            test("-parallel=" + PARALLEL_GC_THREADS, PARALLEL_GC_THREADS);
            test("-parallel=" + Integer.MAX_VALUE, PARALLEL_GC_THREADS);
        } finally {
            theApp.stopApp();
        }
    }

    private static void test(String parallelThreads, int expectedDiagWorkers) throws Exception {
        LingeredApp theApp = launchApp();
        try {
            OutputAnalyzer classHistogramOut = attachJcmd(theApp.getPid(), "GC.class_histogram " + parallelThreads);
            OutputAnalyzer threadPrintOut = attachJcmd(theApp.getPid(), "Thread.print");
            theApp.stopApp();
            checkAndVerify(classHistogramOut, threadPrintOut, theApp, expectedDiagWorkers);
        } finally {
            theApp.stopApp();
        }
    }

    private static OutputAnalyzer attachJcmd(long lingeredAppPid, String args) throws Exception {
        // e.g. jcmd <pid> args
        System.out.println("Testing pid " + lingeredAppPid);
        PidJcmdExecutor executor = new PidJcmdExecutor("" + lingeredAppPid);
        return executor.execute(args);
    }
}
