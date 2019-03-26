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

import sun.hotspot.code.Compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import jdk.test.lib.apps.LingeredApp;
import jtreg.SkippedException;

/**
 * @test
 * @summary Test the 'universe' command of jhsdb clhsdb.
 * @requires vm.hasSAandCanAttach & vm.gc != "Z"
 * @bug 8190307
 * @library /test/lib
 * @build jdk.test.lib.apps.*
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. TestUniverse withoutZ
 */

/**
 * @test
 * @summary Test the 'universe' command of jhsdb clhsdb.
 * @requires vm.hasSAandCanAttach & vm.gc == "Z"
 * @bug 8190307
 * @library /test/lib
 * @build jdk.test.lib.apps.*
 * @build sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. TestUniverse withZ
 */

public class TestUniverse {

    private static void testClhsdbForUniverse(long lingeredAppPid,
                                              String gc) throws Exception {

        ClhsdbLauncher launcher = new ClhsdbLauncher();
        List<String> cmds = List.of("universe");
        Map<String, List<String>> expStrMap = new HashMap<>();
        List<String> expStrings = new ArrayList<String>();
        expStrings.add("Heap Parameters");

        if (gc.contains("UseZGC")) {
            expStrings.add("ZHeap");
        }
        if (gc.contains("G1GC")) {
            expStrings.add("garbage-first heap");
            expStrings.add("region size");
            expStrings.add("G1 Young Generation:");
            expStrings.add("regions  =");
        }
        if (gc.contains("UseConcMarkSweepGC")) {
            expStrings.add("Gen 1: concurrent mark-sweep generation");
        }
        if (gc.contains("UseSerialGC")) {
            expStrings.add("Gen 1:   old");
        }
        if (gc.contains("UseParallelGC")) {
            expStrings.add("ParallelScavengeHeap");
            expStrings.add("PSYoungGen");
            expStrings.add("eden");
        }
        if (gc.contains("UseEpsilonGC")) {
            expStrings.add("Epsilon heap");
            expStrings.add("reserved");
            expStrings.add("committed");
            expStrings.add("used");
        }
        expStrMap.put("universe", expStrings);
        launcher.run(lingeredAppPid, cmds, expStrMap, null);
    }

    public static void test(String gc) throws Exception {
        LingeredApp app = null;
        try {
            List<String> vmArgs = new ArrayList<String>();
            vmArgs.add("-XX:+UnlockExperimentalVMOptions"); // unlock experimental GCs
            vmArgs.add(gc);
            app = LingeredApp.startApp(vmArgs);
            System.out.println ("Started LingeredApp with the GC option " + gc +
                                " and pid " + app.getPid());
            testClhsdbForUniverse(app.getPid(), gc);
        } finally {
            LingeredApp.stopApp(app);
        }
    }

    public static void main (String... args) throws Exception {
        System.out.println("Starting TestUniverse test");
        try {
            test("-XX:+UseG1GC");
            test("-XX:+UseParallelGC");
            test("-XX:+UseSerialGC");
            if (!Compiler.isGraalEnabled()) { // Graal does not support all GCs
                test("-XX:+UseConcMarkSweepGC");
                if (args[0].equals("withZ")) {
                    test("-XX:+UseZGC");
                }
                test("-XX:+UseEpsilonGC");
            }
        } catch (SkippedException se) {
            throw se;
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
            throw new Error("Test failed with " + e);
        }
    }
}
