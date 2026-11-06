/*
 * Copyright (c) 2023, Alibaba Group Holding Limited. All Rights Reserved.
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;

import jdk.test.lib.hprof.HprofParser;

/*
 * @test id=Serial
 * @bug 8306441 8319053 8384612 8392050
 * @requires vm.gc.Serial
 * @summary Verify the integrity of generated heap dump and that parallel dump requests run serially (Serial GC)
 * @library /test/lib
 * @run driver HeapDumpParallelTest -XX:+UseSerialGC
 */

/*
 * @test id=Parallel
 * @bug 8306441 8319053 8384612 8392050
 * @requires vm.gc.Parallel
 * @summary Verify the integrity of generated heap dump and capability of parallel dump (Parallel GC)
 * @library /test/lib
 * @run driver HeapDumpParallelTest -XX:+UseParallelGC
 */

/*
 * @test id=G1
 * @bug 8306441 8319053 8384612 8392050
 * @requires vm.gc.G1
 * @summary Verify the integrity of generated heap dump and capability of parallel dump (G1 GC)
 * @library /test/lib
 * @run driver HeapDumpParallelTest -XX:+UseG1GC
 */

/*
 * @test id=Shenandoah
 * @bug 8306441 8319053 8384612 8392050
 * @requires vm.gc.Shenandoah
 * @summary Verify the integrity of generated heap dump and capability of parallel dump (Shenandoah GC)
 * @library /test/lib
 * @run driver HeapDumpParallelTest -XX:+UseShenandoahGC
 */

/*
 * @test id=ShenandoahGenerational
 * @bug 8306441 8319053 8384612 8392050
 * @requires vm.gc.Shenandoah
 * @summary Verify the integrity of generated heap dump and capability of parallel dump (Generational Shenandoah GC)
 * @library /test/lib
 * @run driver HeapDumpParallelTest -XX:+UseShenandoahGC -XX:ShenandoahGCMode=generational
 */

/*
 * @test id=Z
 * @bug 8306441 8319053 8384612 8392050
 * @requires vm.gc.Z
 * @summary Verify the integrity of generated heap dump and capability of parallel dump (Z GC)
 * @library /test/lib
 * @run driver HeapDumpParallelTest -XX:+UseZGC
 */

/*
 * @test id=Epsilon
 * @bug 8306441 8319053 8384612 8392050
 * @requires vm.gc.Epsilon
 * @summary Verify the integrity of generated heap dump and that parallel dump requests run serially (Epsilon GC)
 * @library /test/lib
 * @run driver HeapDumpParallelTest -XX:+UnlockExperimentalVMOptions -XX:+UseEpsilonGC
 */

public class HeapDumpParallelTest {

    private static final String heapDumpFileName = "parallelHeapDump.bin";
    private static final int ACTIVE_PROCESSOR_COUNT = 8;
    private static final int DEFAULT_WORKERS = Math.max(1, ACTIVE_PROCESSOR_COUNT * 3 / 8);

    private static void checkAndVerify(OutputAnalyzer dcmdOut, LingeredApp app, File heapDumpFile,
                                       int expectedDiagWorkers) throws Exception {
        dcmdOut.shouldHaveExitValue(0);
        dcmdOut.shouldContain("Heap dump file created");
        OutputAnalyzer appOut = new OutputAnalyzer(app.getProcessStdout());
        appOut.shouldMatch("\\[heapdump *\\]");
        // Check the target VM's selected GC, including ergonomic selection.
        appOut.shouldMatch("\\[gc *\\] Using ");
        if (appOut.getStdout().contains("Using Serial") || appOut.getStdout().contains("Using Epsilon")) {
            System.out.println("Serial or Epsilon GC detected.");
            expectedDiagWorkers = 0;
        }
        if (expectedDiagWorkers > 1) {
            appOut.shouldContain("Created diagnostic worker pool (max " + ACTIVE_PROCESSOR_COUNT + " workers)");
            appOut.shouldContain("Dump heap objects in parallel");
            appOut.shouldContain("Merge heap files complete");
            appOut.shouldContain("active dump threads " + expectedDiagWorkers + ",");
        } else {
            appOut.shouldNotContain("Created diagnostic worker pool");
            appOut.shouldNotContain("Dump heap objects in parallel");
            appOut.shouldContain("active dump threads 0");
        }
        HprofParser.parseAndVerify(heapDumpFile);

        List<String> files
            = Stream.of(heapDumpFile.getAbsoluteFile().getParentFile().listFiles())
                .filter(file -> !file.isDirectory())
                .map(File::getName)
                .filter(name -> name.startsWith(heapDumpFileName) && !name.equals(heapDumpFileName))
                .collect(Collectors.toList());
        if (!files.isEmpty()) {
            throw new RuntimeException("Unexpected files left: " + files);
        }
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }
    }

    private static LingeredApp launchApp(String[] gcArgs) throws IOException {
        LingeredApp theApp = new LingeredApp();
        List<String> jvmArgs = new ArrayList<>(List.of("-Xlog:gc,heapdump,gc+task", "-Xmx512m",
                                                       "-XX:ActiveProcessorCount=" + ACTIVE_PROCESSOR_COUNT));
        jvmArgs.addAll(Arrays.asList(gcArgs));
        LingeredApp.startApp(theApp, jvmArgs.toArray(new String[0]));
        return theApp;
    }

    public static void main(String[] args) throws Exception {
        File heapDumpFile = new File(heapDumpFileName);
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }

        LingeredApp theApp = launchApp(args);
        try {
            // Expect error message
            OutputAnalyzer out = attachJcmdHeapDump(heapDumpFile, theApp.getPid(), "-parallel=" + -1);
            out.shouldContain("Invalid number of parallel dump threads.");

            // Expect error message on value above 32 bit unsigned range
            out = attachJcmdHeapDump(heapDumpFile, theApp.getPid(), "-parallel=" + 4294967297L);
            out.shouldContain("Invalid number of parallel dump threads.");

            // Expect serial dump because 0 implies to disable parallel dump
            test(heapDumpFile, "-parallel=" + 0, 0, args);

            // Expect serial dump
            test(heapDumpFile,  "-parallel=" + 1, 0, args);

            // Expect parallel dump
            test(heapDumpFile, "-parallel=" + 2, 2, args);

            // Expect parallel dump
            test(heapDumpFile, "-parallel=" + Integer.MAX_VALUE, ACTIVE_PROCESSOR_COUNT, args);

            // Expect parallel dump
            test(heapDumpFile, "-gz=9 -overwrite -parallel=" + ACTIVE_PROCESSOR_COUNT, ACTIVE_PROCESSOR_COUNT, args);

            // If no parallel threads are passed in, the default number of parallel threads
            // to use is based on CPUs: max(1, ACTIVE_PROCESSOR_COUNT * 3 / 8).
            test(heapDumpFile, "", DEFAULT_WORKERS, args);
        } finally {
            theApp.stopApp();
        }
    }

    private static void test(File heapDumpFile, String arg, int expectedDiagWorkers, String[] gcArgs) throws Exception {
        LingeredApp theApp = launchApp(gcArgs);
        try {
            OutputAnalyzer dcmdOut = attachJcmdHeapDump(heapDumpFile, theApp.getPid(), arg);
            theApp.stopApp();
            checkAndVerify(dcmdOut, theApp, heapDumpFile, expectedDiagWorkers);
        } finally {
            theApp.stopApp();
        }
    }

    private static OutputAnalyzer attachJcmdHeapDump(File heapDumpFile, long lingeredAppPid, String arg) throws Exception {
        // e.g. jcmd <pid> GC.heap_dump -parallel=cpucount <file_path>
        System.out.println("Testing pid " + lingeredAppPid);
        PidJcmdExecutor executor = new PidJcmdExecutor("" + lingeredAppPid);
        return executor.execute("GC.heap_dump " + arg + " " + heapDumpFile.getAbsolutePath());
    }
}
