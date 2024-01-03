/*
 * Copyright (c) 2023, Alibaba Group Holding Limited. All Rights Reserved.
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
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import jdk.test.lib.hprof.HprofParser;

/**
 * @test
 * @bug 8306441 8319053
 * @summary Verify the integrity of generated heap dump and capability of parallel dump
 * @library /test/lib
 * @run main HeapDumpParallelTest
 */

public class HeapDumpParallelTest {

    private static final String heapDumpFileName = "parallelHeapDump.bin";

    private static void checkAndVerify(OutputAnalyzer dcmdOut, LingeredApp app, File heapDumpFile, boolean expectSerial) throws Exception {
        dcmdOut.shouldHaveExitValue(0);
        dcmdOut.shouldContain("Heap dump file created");
        OutputAnalyzer appOut = new OutputAnalyzer(app.getProcessStdout());
        appOut.shouldContain("[heapdump]");
        String opts = Arrays.asList(Utils.getTestJavaOpts()).toString();
        if (opts.contains("-XX:+UseSerialGC") || opts.contains("-XX:+UseEpsilonGC")) {
            System.out.println("UseSerialGC detected.");
            expectSerial = true;
        }
        if (!expectSerial && Runtime.getRuntime().availableProcessors() > 1) {
            appOut.shouldContain("Dump heap objects in parallel");
            appOut.shouldContain("Merge heap files complete");
        } else {
            appOut.shouldNotContain("Dump heap objects in parallel");
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

    private static LingeredApp launchApp() throws IOException {
        LingeredApp theApp = new LingeredApp();
        LingeredApp.startApp(theApp, "-Xlog:heapdump", "-Xmx512m",
                             "-XX:-UseDynamicNumberOfGCThreads",
                             "-XX:ParallelGCThreads=2");
        return theApp;
    }

    public static void main(String[] args) throws Exception {
        File heapDumpFile = new File(heapDumpFileName);
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }

        LingeredApp theApp = launchApp();
        try {
            // Expect error message
            OutputAnalyzer out = attachJcmdHeapDump(heapDumpFile, theApp.getPid(), "-parallel=" + -1);
            out.shouldContain("Invalid number of parallel dump threads.");

            // Expect serial dump because 0 implies to disable parallel dump
            test(heapDumpFile, "-parallel=" + 0, true);

            // Expect serial dump
            test(heapDumpFile,  "-parallel=" + 1, true);

            // Expect parallel dump
            test(heapDumpFile, "-parallel=" + Integer.MAX_VALUE, false);

            // Expect parallel dump
            test(heapDumpFile, "-gz=9 -overwrite -parallel=" + Runtime.getRuntime().availableProcessors(), false);
        } finally {
            theApp.stopApp();
        }
    }

    private static void test(File heapDumpFile, String arg, boolean expectSerial) throws Exception {
        LingeredApp theApp = launchApp();
        try {
            OutputAnalyzer dcmdOut = attachJcmdHeapDump(heapDumpFile, theApp.getPid(), arg);
            theApp.stopApp();
            checkAndVerify(dcmdOut, theApp, heapDumpFile, expectSerial);
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