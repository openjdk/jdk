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
import java.util.List;

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import jdk.test.lib.hprof.HprofParser;

/**
 * @test
 * @bug 8306441
 * @summary Verify the generated heap dump is valid and complete after parallel heap dump
 * @library /test/lib
 * @run driver HeapDumpParallelTest
 */

class HeapDumpParallel extends LingeredApp {
    public static void main(String[] args) {
        System.out.println("Hello world");
        LingeredApp.main(args);
    }
}

public class HeapDumpParallelTest {
    static HeapDumpParallel theApp;

    private static void checkAndVerify(OutputAnalyzer out, LingeredApp app, File heapDumpFile, boolean expectSerial) {
        out.shouldHaveExitValue(0);
        Asserts.assertTrue(out.getStdout().contains("Heap dump file created"));
        if (!expectSerial && Runtime.getRuntime().availableProcessors() > 1) {
            Asserts.assertTrue(app.getProcessStdout().contains("Dump heap objects in parallel"));
            Asserts.assertTrue(app.getProcessStdout().contains("Merge heap files complete"));
        } else {
            Asserts.assertFalse(app.getProcessStdout().contains("Dump heap objects in parallel"));
            Asserts.assertFalse(app.getProcessStdout().contains("Merge heap files complete"));
        }
        verifyHeapDump(heapDumpFile);
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }
    }

    public static void main(String[] args) throws Exception {
        String heapDumpFileName = "parallelHeapDump.bin";

        File heapDumpFile = new File(heapDumpFileName);
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }

        try {
            theApp = new HeapDumpParallel();
            LingeredApp.startApp(theApp, "-Xlog:heapdump", "-Xmx512m",
                                "-XX:-UseDynamicNumberOfGCThreads",
                                "-XX:ParallelGCThreads=2");
            // Expect error message
            OutputAnalyzer out = attachWith(heapDumpFile, theApp.getPid(), "-parallel=" + -1);
            Asserts.assertTrue(out.getStdout().contains("Invalid number of parallel dump threads."));

            // Expect serial dump because 0 implies to disable parallel dump
            out = attachWith(heapDumpFile, theApp.getPid(), "-parallel=" + 0);
            checkAndVerify(out, theApp, heapDumpFile, true);

            // Expect serial dump
            out = attachWith(heapDumpFile, theApp.getPid(), "-parallel=" + 1);
            checkAndVerify(out, theApp, heapDumpFile, true);

            // Expect parallel dump
            out = attachWith(heapDumpFile, theApp.getPid(), "-parallel=" + Integer.MAX_VALUE);
            checkAndVerify(out, theApp, heapDumpFile, false);

            // Expect parallel dump
            out = attachWith(heapDumpFile, theApp.getPid(), "-gz=9 -overwrite -parallel=" + Runtime.getRuntime().availableProcessors());
            checkAndVerify(out, theApp, heapDumpFile, false);
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    private static OutputAnalyzer attachWith(File heapDumpFile, long lingeredAppPid, String arg) throws Exception {
        //jcmd <pid> GC.heap_dump -parallel=cpucount <file_path>
        JDKToolLauncher launcher = JDKToolLauncher
                .createUsingTestJDK("jcmd")
                .addToolArg(Long.toString(lingeredAppPid))
                .addToolArg("GC.heap_dump")
                .addToolArg(arg)
                .addToolArg(heapDumpFile.getAbsolutePath());

        ProcessBuilder processBuilder = new ProcessBuilder(launcher.getCommand());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        return output;
    }

    private static void verifyHeapDump(File dump) {
        Asserts.assertTrue(dump.exists() && dump.isFile(), "Could not create dump file " + dump.getAbsolutePath());
        try {
            File out = HprofParser.parse(dump);

            Asserts.assertTrue(out != null && out.exists() && out.isFile(), "Could not find hprof parser output file");
            List<String> lines = Files.readAllLines(out.toPath());
            Asserts.assertTrue(lines.size() > 0, "hprof parser output file is empty");
            for (String line : lines) {
                Asserts.assertFalse(line.matches(".*WARNING(?!.*Failed to resolve object.*constantPoolOop.*).*"));
            }

            out.delete();
        } catch (Exception e) {
            e.printStackTrace();
            Asserts.fail("Could not parse dump file " + dump.getAbsolutePath());
        }
    }
}