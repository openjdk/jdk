/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;
import java.util.stream.Collectors;
import java.io.OutputStream;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;

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

        Process p;
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addToolArg("clhsdb");
        launcher.addToolArg("--pid");
        launcher.addToolArg(Long.toString(lingeredAppPid));

        ProcessBuilder pb = new ProcessBuilder();
        pb.command(launcher.getCommand());
        System.out.println(
            pb.command().stream().collect(Collectors.joining(" ")));

        try {
            p = pb.start();
        } catch (Exception attachE) {
            throw new Error("Couldn't start jhsdb or attach to LingeredApp : " + attachE);
        }

        // Issue the 'universe' command at the clhsdb prompt.
        OutputStream input = p.getOutputStream();
        try {
            input.write("universe\n".getBytes());
            input.write("quit\n".getBytes());
            input.flush();
        } catch (IOException ioe) {
            throw new Error("Problem issuing the 'universe' command ", ioe);
        }

        OutputAnalyzer output = new OutputAnalyzer(p);

        try {
            p.waitFor();
        } catch (InterruptedException ie) {
            p.destroyForcibly();
            throw new Error("Problem awaiting the child process: " + ie, ie);
        }
        if (gc.contains("UseZGC")) {
            output.shouldContain("ZHeap");
        }

        output.shouldHaveExitValue(0);
        System.out.println(output.getOutput());

        output.shouldContain("Heap Parameters");
        if (gc.contains("G1GC")) {
            output.shouldContain("garbage-first heap");
            output.shouldContain("region size");
            output.shouldContain("G1 Young Generation:");
            output.shouldContain("regions  =");
        }
        if (gc.contains("UseConcMarkSweepGC")) {
            output.shouldContain("Gen 1: concurrent mark-sweep generation");
        }
        if (gc.contains("UseSerialGC")) {
            output.shouldContain("Gen 1:   old");
        }
        if (gc.contains("UseParallelGC")) {
            output.shouldContain("ParallelScavengeHeap");
            output.shouldContain("PSYoungGen");
            output.shouldContain("eden");
        }
        if (gc.contains("UseEpsilonGC")) {
            output.shouldContain("Epsilon heap");
            output.shouldContain("reserved");
            output.shouldContain("committed");
            output.shouldContain("used");
        }
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
        } catch (Exception e) {
            throw new Error("Test failed with " + e);
        }
    }
}
