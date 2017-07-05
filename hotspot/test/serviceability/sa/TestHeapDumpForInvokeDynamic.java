/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.util.stream.Collectors;
import java.io.FileInputStream;

import sun.jvm.hotspot.HotSpotAgent;
import sun.jvm.hotspot.debugger.*;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jdk.test.lib.Asserts;
import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.hprof.parser.HprofReader;
import jdk.test.lib.hprof.parser.PositionDataInputStream;
import jdk.test.lib.hprof.model.Snapshot;

/*
 * @test
 * @library /test/lib
 * @requires os.family != "mac"
 * @modules java.base/jdk.internal.misc
 *          jdk.hotspot.agent/sun.jvm.hotspot
 *          jdk.hotspot.agent/sun.jvm.hotspot.utilities
 *          jdk.hotspot.agent/sun.jvm.hotspot.oops
 *          jdk.hotspot.agent/sun.jvm.hotspot.debugger
 * @run main/othervm TestHeapDumpForInvokeDynamic
 */

public class TestHeapDumpForInvokeDynamic {

    private static LingeredAppWithInvokeDynamic theApp = null;

    private static void verifyHeapDump(String heapFile) {

        File heapDumpFile = new File(heapFile);
        Asserts.assertTrue(heapDumpFile.exists() && heapDumpFile.isFile(),
                          "Could not create dump file " + heapDumpFile.getAbsolutePath());
        try (PositionDataInputStream in = new PositionDataInputStream(
                new BufferedInputStream(new FileInputStream(heapFile)))) {
            int i = in.readInt();
            if (HprofReader.verifyMagicNumber(i)) {
                Snapshot sshot;
                HprofReader r = new HprofReader(heapFile, in, 0,
                                                false, 0);
                sshot = r.read();
            } else {
                throw new IOException("Unrecognized magic number: " + i);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Asserts.fail("Could not read dump file " + heapFile);
        } finally {
            heapDumpFile.delete();
        }
    }

    private static void attachDumpAndVerify(String heapDumpFileName,
                                            long lingeredAppPid) throws Exception {

        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addToolArg("jmap");
        launcher.addToolArg("--binaryheap");
        launcher.addToolArg("--dumpfile");
        launcher.addToolArg(heapDumpFileName);
        launcher.addToolArg("--pid");
        launcher.addToolArg(Long.toString(lingeredAppPid));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(launcher.getCommand());
        System.out.println(
            processBuilder.command().stream().collect(Collectors.joining(" ")));

        OutputAnalyzer SAOutput = ProcessTools.executeProcess(processBuilder);
        SAOutput.shouldHaveExitValue(0);
        SAOutput.shouldContain("heap written to");
        SAOutput.shouldContain(heapDumpFileName);
        System.out.println(SAOutput.getOutput());

        verifyHeapDump(heapDumpFileName);
    }

    public static void main (String... args) throws Exception {

        String heapDumpFileName = "lambdaHeapDump.bin";

        if (!Platform.shouldSAAttach()) {
            System.out.println(
               "SA attach not expected to work - test skipped.");
            return;
        }

        File heapDumpFile = new File(heapDumpFileName);
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }

        try {
            List<String> vmArgs = new ArrayList<String>();
            vmArgs.add("-XX:+UsePerfData");
            vmArgs.addAll(Utils.getVmOptions());

            theApp = new LingeredAppWithInvokeDynamic();
            LingeredApp.startApp(vmArgs, theApp);
            attachDumpAndVerify(heapDumpFileName, theApp.getPid());
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }
}
