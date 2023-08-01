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
import jdk.test.lib.SA.SATestUtils;

import jdk.test.lib.hprof.HprofParser;

/**
 * @test
 * @bug 8306441
 * @summary Verify the generated heap dump is valid and complete after parallel heap dump
 * @library /test/lib
 * @run driver IntegrityHeapDumpTest
 */

class IntegrityTest extends LingeredApp {
    public static void main(String[] args) {
        System.out.println("Hello world");
        LingeredApp.main(args);
    }
}

public class IntegrityHeapDumpTest {
    static IntegrityTest theApp;

    public static void main(String[] args) throws Exception {
        String heapDumpFileName = "parallelHeapDump.bin";

        File heapDumpFile = new File(heapDumpFileName);
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }

        try {
            theApp = new IntegrityTest();
            LingeredApp.startApp(theApp, "-Xlog:heapdump", "-Xmx512m");
            attachDumpAndVerify(heapDumpFile, theApp.getPid());
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    private static void attachDumpAndVerify(File heapDumpFile,
                                            long lingeredAppPid) throws Exception {

        //jcmd <pid> GC.heap_dump -parallel=cpucount <file_path>
        JDKToolLauncher launcher = JDKToolLauncher
                .createUsingTestJDK("jcmd")
                .addToolArg(Long.toString(lingeredAppPid))
                .addToolArg("GC.heap_dump")
                .addToolArg("-parallel=" + Runtime.getRuntime().availableProcessors())
                .addToolArg(heapDumpFile.getAbsolutePath());

        ProcessBuilder processBuilder = new ProcessBuilder(launcher.getCommand());
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        String stdoutStr = output.getStdout();
        String stderrStr = output.getStderr();
        System.out.println("stdout:");
        System.out.println(stdoutStr);
        System.out.println("stderr:");
        System.out.println(stderrStr);
        output.shouldHaveExitValue(0);
        Asserts.assertTrue(stdoutStr.contains("Heap dump file created"));
        Asserts.assertTrue(stderrStr.equals(""));

        verifyHeapDump(heapDumpFile);
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