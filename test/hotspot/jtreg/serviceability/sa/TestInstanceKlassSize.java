/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.HotSpotAgent;
import sun.jvm.hotspot.utilities.SystemDictionaryHelper;
import sun.jvm.hotspot.oops.InstanceKlass;
import sun.jvm.hotspot.debugger.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.Asserts;

import java.io.*;
import java.util.*;

/**
 * @test
 * @library /test/lib
 * @requires vm.hasSAandCanAttach
 * @modules java.base/jdk.internal.misc
 *          jdk.hotspot.agent/sun.jvm.hotspot
 *          jdk.hotspot.agent/sun.jvm.hotspot.utilities
 *          jdk.hotspot.agent/sun.jvm.hotspot.oops
 *          jdk.hotspot.agent/sun.jvm.hotspot.debugger
 * @run main/othervm TestInstanceKlassSize
 */

public class TestInstanceKlassSize {

    private static String getJcmdInstanceKlassSize(OutputAnalyzer output,
                                                   String instanceKlassName) {
        for (String s : output.asLines()) {
            if (s.contains(instanceKlassName)) {
                String tokens[];
                System.out.println(s);
                tokens = s.split("\\s+");
                return tokens[3];
            }
        }
        return null;
    }

    private static OutputAnalyzer jcmd(Long pid,
                                       String... toolArgs) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder();
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jcmd");
        launcher.addToolArg(Long.toString(pid));
        if (toolArgs != null) {
            for (String toolArg : toolArgs) {
                launcher.addToolArg(toolArg);
            }
        }

        processBuilder.command(launcher.getCommand());
        System.out.println(
            processBuilder.command().stream().collect(Collectors.joining(" ")));
        return ProcessTools.executeProcess(processBuilder);
    }

    private static void startMeWithArgs() throws Exception {

        LingeredApp app = null;
        OutputAnalyzer output = null;
        try {
            List<String> vmArgs = new ArrayList<String>();
            vmArgs.add("-XX:+UsePerfData");
            vmArgs.addAll(Utils.getVmOptions());
            app = LingeredApp.startApp(vmArgs);
            System.out.println ("Started LingeredApp with pid " + app.getPid());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        try {
            String[] instanceKlassNames = new String[] {
                                              " java.lang.Object",
                                              " java.util.Vector",
                                              " java.lang.String",
                                              " java.lang.Thread",
                                              " java.lang.Byte",
                                          };
            String[] toolArgs = {
                "--add-modules=jdk.hotspot.agent",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.utilities=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.oops=ALL-UNNAMED",
                "--add-exports=jdk.hotspot.agent/sun.jvm.hotspot.debugger=ALL-UNNAMED",
                "TestInstanceKlassSize",
                Long.toString(app.getPid())
            };

            OutputAnalyzer jcmdOutput = jcmd(
                           app.getPid(),
                           "GC.class_stats", "VTab,ITab,OopMap,KlassBytes");
            ProcessBuilder processBuilder = ProcessTools
                                            .createJavaProcessBuilder(toolArgs);
            output = ProcessTools.executeProcess(processBuilder);
            System.out.println(output.getOutput());
            output.shouldHaveExitValue(0);

            // Check whether the size matches that which jcmd outputs
            for (String instanceKlassName : instanceKlassNames) {
                System.out.println ("Trying to match for" + instanceKlassName);
                String jcmdInstanceKlassSize = getJcmdInstanceKlassSize(
                                                      jcmdOutput,
                                                      instanceKlassName);
                Asserts.assertNotNull(jcmdInstanceKlassSize,
                    "Could not get the instance klass size from the jcmd output");
                for (String s : output.asLines()) {
                    if (s.contains(instanceKlassName)) {
                       Asserts.assertTrue(
                          s.contains(jcmdInstanceKlassSize),
                          "The size computed by SA for" +
                          instanceKlassName + " does not match.");
                    }
                }
            }
        } finally {
            LingeredApp.stopApp(app);
        }
    }

    private static void SAInstanceKlassSize(int pid,
                                            String[] SAInstanceKlassNames) {
        HotSpotAgent agent = new HotSpotAgent();
        try {
            agent.attach(pid);
        }
        catch (DebuggerException e) {
            System.out.println(e.getMessage());
            System.err.println("Unable to connect to process ID: " + pid);

            agent.detach();
            e.printStackTrace();
        }

        for (String SAInstanceKlassName : SAInstanceKlassNames) {
            InstanceKlass ik = SystemDictionaryHelper.findInstanceKlass(
                               SAInstanceKlassName);
            Asserts.assertNotNull(ik,
                String.format("Unable to find instance klass for %s", SAInstanceKlassName));
            System.out.println("SA: The size of " + SAInstanceKlassName +
                               " is " + ik.getSize());
        }
        agent.detach();
    }

    public static void main(String[] args) throws Exception {

        if (args == null || args.length == 0) {
            System.out.println ("No args run. Starting with args now.");
            startMeWithArgs();
        } else {
            String[] SAInstanceKlassNames = new String[] {
                                                "java.lang.Object",
                                                "java.util.Vector",
                                                "java.lang.String",
                                                "java.lang.Thread",
                                                "java.lang.Byte"
                                             };
            SAInstanceKlassSize(Integer.parseInt(args[0]), SAInstanceKlassNames);
        }
    }
}

