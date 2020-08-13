/*
 * Copyright (c) 2005, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.Asserts.fail;

import java.io.File;
import java.util.Arrays;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @summary Unit test for jmap utility
 * @key intermittent
 * @library /test/lib
 * @build jdk.test.lib.hprof.*
 * @build jdk.test.lib.hprof.model.*
 * @build jdk.test.lib.hprof.parser.*
 * @build jdk.test.lib.hprof.util.*
 * @run main/timeout=240 BasicJMapTest
 */
public class BasicJMapTest {

    private static ProcessBuilder processBuilder = new ProcessBuilder();

    public static void main(String[] args) throws Exception {
        testHisto();
        testHistoLive();
        testHistoAll();
        testHistoToFile();
        testHistoLiveToFile();
        testHistoAllToFile();
        testFinalizerInfo();
        testClstats();
        testDump();
        testDumpLive();
        testDumpAll();
    }

    private static void testHisto() throws Exception {
        OutputAnalyzer output = jmap("-histo:");
        output.shouldHaveExitValue(0);
        OutputAnalyzer output1 = jmap("-histo");
        output1.shouldHaveExitValue(0);
    }

    private static void testHistoLive() throws Exception {
        OutputAnalyzer output = jmap("-histo:live");
        output.shouldHaveExitValue(0);
    }

    private static void testHistoAll() throws Exception {
        OutputAnalyzer output = jmap("-histo:all");
        output.shouldHaveExitValue(0);
    }

    private static void testHistoParallelZero() throws Exception {
        OutputAnalyzer output = jmap("-histo:parallel=0");
        output.shouldHaveExitValue(0);
    }

    private static void testHistoParallel() throws Exception {
        OutputAnalyzer output = jmap("-histo:parallel=2");
        output.shouldHaveExitValue(0);
    }

    private static void testHistoNonParallel() throws Exception {
        OutputAnalyzer output = jmap("-histo:parallel=1");
        output.shouldHaveExitValue(0);
    }

    private static void testHistoToFile() throws Exception {
        histoToFile(false, false, 1);
    }

    private static void testHistoLiveToFile() throws Exception {
        histoToFile(true, false, 1);
    }

    private static void testHistoAllToFile() throws Exception {
        histoToFile(false, true, 1);
    }

    private static void testHistoFileParallelZero() throws Exception {
        histoToFile(false, false, 0);
    }

    private static void testHistoFileParallel() throws Exception {
        histoToFile(false, false, 2);
    }

    private static void histoToFile(boolean live,
                                    boolean explicitAll,
                                    int parallelThreadNum) throws Exception {
        String liveArg = "";
        String fileArg = "";
        String parArg = "parallel=" + parallelThreadNum;
        String allArgs = "-histo:";

        if (live && explicitAll) {
            fail("Illegal argument setting for jmap -histo");
        }
        if (live) {
            liveArg = "live,";
        }
        if (explicitAll) {
            liveArg = "all,";
        }

        File file = new File("jmap.histo.file" + System.currentTimeMillis() + ".histo");
        if (file.exists()) {
            file.delete();
        }
        fileArg = "file=" + file.getName();

        OutputAnalyzer output;
        allArgs = allArgs + liveArg + fileArg + ',' + parArg;
        output = jmap(allArgs);
        output.shouldHaveExitValue(0);
        output.shouldContain("Heap inspection file created");
        file.delete();
    }

    private static void testFinalizerInfo() throws Exception {
        OutputAnalyzer output = jmap("-finalizerinfo");
        output.shouldHaveExitValue(0);
    }

    private static void testClstats() throws Exception {
        OutputAnalyzer output = jmap("-clstats");
        output.shouldHaveExitValue(0);
    }

    private static void testDump() throws Exception {
        dump(false, false);
    }

    private static void testDumpLive() throws Exception {
        dump(true, false);
    }

    private static void testDumpAll() throws Exception {
        dump(false, true);
    }

    private static void dump(boolean live, boolean explicitAll) throws Exception {
        String liveArg = "";
        String fileArg = "";
        String allArgs = "-dump:";

        if (live && explicitAll) {
            fail("Illegal argument setting for jmap -dump");
        }
        if (live) {
            liveArg = "live,";
        }
        if (explicitAll) {
            liveArg = "all,";
        }

        File file = new File("jmap.dump" + System.currentTimeMillis() + ".hprof");
        if (file.exists()) {
            file.delete();
        }
        fileArg = "file=" + file.getName();

        OutputAnalyzer output;
        allArgs = allArgs + liveArg + "format=b," + fileArg;
        output = jmap(allArgs);
        output.shouldHaveExitValue(0);
        output.shouldContain("Heap dump file created");
        verifyDumpFile(file);
        file.delete();
    }

    private static void verifyDumpFile(File dump) {
        assertTrue(dump.exists() && dump.isFile(), "Could not create dump file " + dump.getAbsolutePath());
        try {
            HprofParser.parse(dump);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Could not parse dump file " + dump.getAbsolutePath());
        }
    }

    private static OutputAnalyzer jmap(String... toolArgs) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jmap");
        launcher.addVMArgs(Utils.getTestJavaOpts());
        if (toolArgs != null) {
            for (String toolArg : toolArgs) {
                launcher.addToolArg(toolArg);
            }
        }
        launcher.addToolArg(Long.toString(ProcessTools.getProcessId()));

        processBuilder.command(launcher.getCommand());
        System.out.println(Arrays.toString(processBuilder.command().toArray()));
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        System.out.println(output.getOutput());

        return output;
    }
}
