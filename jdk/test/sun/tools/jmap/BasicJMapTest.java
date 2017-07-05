/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;

import static jdk.testlibrary.Asserts.*;
import jdk.testlibrary.JDKToolLauncher;
import jdk.testlibrary.OutputAnalyzer;
import jdk.testlibrary.ProcessTools;

/*
 * @test
 * @bug 6321286
 * @summary Unit test for jmap utility
 * @library /lib/testlibrary
 * @build jdk.testlibrary.*
 * @run main BasicJMapTest
 */
public class BasicJMapTest {

    private static ProcessBuilder processBuilder = new ProcessBuilder();

    public static void main(String[] args) throws Exception {
        testHisto();
        testHistoLive();
        testDump();
        testDumpLive();
    }

    private static void testHisto() throws Exception {
        OutputAnalyzer output = jmap("-histo");
        output.shouldHaveExitValue(0);
    }

    private static void testHistoLive() throws Exception {
        OutputAnalyzer output = jmap("-histo:live");
        output.shouldHaveExitValue(0);
    }

    private static void testDump() throws Exception {
        File dump = new File("java_pid$" + ProcessTools.getProcessId() + ".hprof");
        OutputAnalyzer output = jmap("-dump:format=b,file=" + dump.getName());
        output.shouldHaveExitValue(0);
        output.shouldContain("Heap dump file created");
        verifyDumpFile(dump);
    }

    private static void testDumpLive() throws Exception {
        File dump = new File("java_pid$" + ProcessTools.getProcessId() + ".hprof");
        OutputAnalyzer output = jmap("-dump:live,format=b,file=" + dump.getName());
        output.shouldHaveExitValue(0);
        output.shouldContain("Heap dump file created");
        verifyDumpFile(dump);
    }

    private static void verifyDumpFile(File dump) {
        assertTrue(dump.exists() && dump.isFile(), "Could not create dump file");
        dump.delete();
    }

    private static OutputAnalyzer jmap(String... toolArgs) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jmap");
        launcher.addVMArg("-XX:+UsePerfData");
        if (toolArgs != null) {
            for (String toolArg : toolArgs) {
                launcher.addToolArg(toolArg);
            }
        }
        launcher.addToolArg(Integer.toString(ProcessTools.getProcessId()));

        processBuilder.command(launcher.getCommand());
        System.out.println(Arrays.toString(processBuilder.command().toArray()).replace(",", ""));
        OutputAnalyzer output = new OutputAnalyzer(processBuilder.start());
        System.out.println(output.getOutput());

        return output;
    }

}
