/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @library /test/lib
 * @bug 8171084
 * @requires vm.hasSAandCanAttach & (vm.bits == "64" & os.maxMemory > 8g)
 * @requires vm.gc != "Shenandoah"
 * @modules java.base/jdk.internal.misc
 *          jdk.hotspot.agent/sun.jvm.hotspot
 *          jdk.hotspot.agent/sun.jvm.hotspot.utilities
 *          jdk.hotspot.agent/sun.jvm.hotspot.oops
 *          jdk.hotspot.agent/sun.jvm.hotspot.debugger
 * @run main/timeout=1800/othervm -Xmx8g TestHeapDumpForLargeArray
 */

public class TestHeapDumpForLargeArray {

    private static LingeredAppWithLargeArray theApp = null;

    private static void attachAndDump(String heapDumpFileName,
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
        SAOutput.shouldNotContain("Heap segment size overflow");
        SAOutput.shouldContain("truncating to");
        SAOutput.shouldContain("heap written to");
        SAOutput.shouldContain(heapDumpFileName);
        System.out.println(SAOutput.getOutput());

    }

    public static void main (String... args) throws Exception {

        String heapDumpFileName = "LargeArrayHeapDump.bin";

        File heapDumpFile = new File(heapDumpFileName);
        if (heapDumpFile.exists()) {
            heapDumpFile.delete();
        }

        try {
            // Need to add the default arguments first to have explicit
            // -Xmx8g last, otherwise test will fail if default
            // arguments contain a smaller -Xmx.
            List<String> vmArgs = new ArrayList<String>();
            vmArgs.addAll(Utils.getVmOptions());
            vmArgs.add("-XX:+UsePerfData");
            vmArgs.add("-Xmx8g");

            theApp = new LingeredAppWithLargeArray();
            LingeredApp.startApp(vmArgs, theApp);
            attachAndDump(heapDumpFileName, theApp.getPid());
        } finally {
            LingeredApp.stopApp(theApp);
            heapDumpFile.delete();
        }
    }
}
