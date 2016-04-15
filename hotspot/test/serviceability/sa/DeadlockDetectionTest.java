/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.Collectors;


import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.apps.LingeredAppWithDeadlock;

import jdk.test.lib.Utils;
import jdk.test.lib.Platform;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.OutputAnalyzer;
import jdk.test.lib.ProcessTools;

/*
 * @test
 * @summary Test deadlock detection
 * @library /test/lib/share/classes
 * @library /testlibrary
 * @modules java.management
 * @build jdk.test.lib.*
 * @build jdk.test.lib.apps.*
 * @build DeadlockDetectionTest
 * @run main DeadlockDetectionTest
 */

public class DeadlockDetectionTest {

    private static LingeredAppWithDeadlock theApp = null;
    private static ProcessBuilder processBuilder = new ProcessBuilder();

    private static OutputAnalyzer jstack(String... toolArgs) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addToolArg("jstack");
        if (toolArgs != null) {
            for (String toolArg : toolArgs) {
                launcher.addToolArg(toolArg);
            }
        }

        processBuilder.command(launcher.getCommand());
        System.out.println(processBuilder.command().stream().collect(Collectors.joining(" ")));
        OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
        System.out.println(output.getOutput());

        return output;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting DeadlockDetectionTest");

        if (!Platform.shouldSAAttach()) {
            // Silently skip the test if we don't have enough permissions to attach
            // Not all conditions checked by function is relevant to SA but it's worth
            // to check
            System.err.println("Error! Insufficient permissions to attach.");
            return;
        }

        if (Platform.isOSX()) {
            // Coredump stackwalking is not implemented for Darwin
            System.out.println("This test is not expected to work on OS X. Skipping");
            return;
        }


        if (!LingeredApp.isLastModifiedWorking()) {
            // Exact behaviour of the test depends on operating system and the test nature,
            // so just print the warning and continue
            System.err.println("Warning! Last modified time doesn't work.");
        }

        try {
            List<String> vmArgs = new ArrayList<String>();
            vmArgs.add("-XX:+UsePerfData");
            vmArgs.addAll(Utils.getVmOptions());

            theApp = new LingeredAppWithDeadlock();
            LingeredApp.startApp(vmArgs, theApp);
            OutputAnalyzer output = jstack("--pid", Long.toString(theApp.getPid()));
            System.out.println(output.getOutput());

            if (output.getExitValue() == 3) {
                System.out.println("Test can't run for some reason. Skipping");
            }
            else {
                output.shouldHaveExitValue(0);
                output.shouldContain("Found a total of 1 deadlock.");
            }

        } finally {
            LingeredApp.stopApp(theApp);
        }
    }
}
