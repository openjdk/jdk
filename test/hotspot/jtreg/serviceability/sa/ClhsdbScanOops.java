/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8192985
 * @summary Test the clhsdb 'scanoops' command
 * @requires vm.gc.Parallel
 * @requires vm.hasSA
 * @requires (os.arch != "riscv64" | !(vm.cpu.features ~= ".*qemu.*"))
 * @library /test/lib
 * @run main/othervm/timeout=1200 ClhsdbScanOops UseParallelGC
 */

/**
 * @test
 * @bug 8192985
 * @summary Test the clhsdb 'scanoops' command
 * @requires vm.gc.Serial
 * @requires vm.hasSA
 * @requires (os.arch != "riscv64" | !(vm.cpu.features ~= ".*qemu.*"))
 * @library /test/lib
 * @run main/othervm/timeout=1200 ClhsdbScanOops UseSerialGC
 */

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

public class ClhsdbScanOops {

    private static void testWithGcType(String gc) throws Exception {

        LingeredApp theApp = null;

        try {
            ClhsdbLauncher test = new ClhsdbLauncher();
            theApp = LingeredApp.startApp(gc);

            System.out.println ("Started LingeredApp with the GC option " + gc +
                                " and pid " + theApp.getPid());

            // Run the 'universe' command to get the address ranges
            List<String> cmds = List.of("universe");

            String universeOutput = test.run(theApp.getPid(), cmds, null, null);

            cmds = new ArrayList<String>();
            Map<String, List<String>> expStrMap = new HashMap<>();
            Map<String, List<String>> unExpStrMap = new HashMap<>();

            String startAddress;
            String endAddress;
            String[] snippets;
            String[] words;
            String cmd;

            // Run scanoops on the old gen
            if (gc.contains("UseParallelGC")) {
                snippets = universeOutput.split("PSOldGen \\[  ");
            } else {
                snippets = universeOutput.split("old  \\[");
            }
            words = snippets[1].split(",");
            // Get the addresses for Old gen
            startAddress = words[0].replace("[", "");
            endAddress = words[1];
            cmd = "scanoops " + startAddress + " " + endAddress;
            String output1 = test.run(theApp.getPid(), List.of(cmd), null, null);

            // Run scanoops on the eden gen
            if (gc.contains("UseParallelGC")) {
                snippets = universeOutput.split("eden =  ");
            } else {
                snippets = universeOutput.split("eden \\[");
            }
            words = snippets[1].split(",");
            // Get the addresses for Eden gen
            startAddress = words[0].replace("[", "");
            endAddress = words[1];
            cmd = "scanoops " + startAddress + " " + endAddress;
            String output2 = test.run(theApp.getPid(), List.of(cmd), null, null);

            // Look for expected types in the combined eden and old gens
            OutputAnalyzer out = new OutputAnalyzer(output1 + output2);
            List<String> expectStrs = List.of(
                    "java/lang/Object", "java/lang/Class", "java/lang/Thread",
                    "java/lang/String", "\\[B", "\\[I");
            for (String expectStr : expectStrs) {
                out.shouldMatch(expectStr);
            }

            // Test the 'type' option also:
            //   scanoops <start addr> <end addr> java/lang/String
            // Ensure that only the java/lang/String oops are printed.
            cmd = cmd + " java/lang/String";
            expStrMap.put(cmd, List.of("java/lang/String"));
            unExpStrMap.put(cmd, List.of("java/lang/Thread", "java/lang/Class", "java/lang/Object"));
            test.run(theApp.getPid(), List.of(cmd), expStrMap, unExpStrMap);
        } catch (SkippedException e) {
            throw e;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }
    }

    public static void main(String[] args) throws Exception {
        String gc = args[0];
        System.out.println("Starting the ClhsdbScanOops test");
        testWithGcType("-XX:+" + gc);
        System.out.println("Test PASSED");
    }
}
