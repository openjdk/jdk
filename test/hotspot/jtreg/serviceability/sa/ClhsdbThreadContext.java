/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.Platform;
import jtreg.SkippedException;

/**
 * @test
 * @summary Test clhsdb where command
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/othervm ClhsdbThreadContext
 */

public class ClhsdbThreadContext {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting ClhsdbThreadContext test");

        LingeredApp theApp = null;
        try {
            ClhsdbLauncher test = new ClhsdbLauncher();
            theApp = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            // Run threadcontext on all threads
            String cmdStr = "threadcontext -a";
            List<String> cmds = List.of(cmdStr);
            Map<String, List<String>> expStrMap = new HashMap<>();
            expStrMap.put(cmdStr, List.of(
                    "Thread \"Common-Cleaner\"",
                    "Thread \"Service Thread\"",
                    "Thread \"Finalizer\"",
                    "Thread \"SteadyStateThread\"",
                    "In java stack for thread \"SteadyStateThread\""));
            String cmdOutput = test.run(theApp.getPid(), cmds, expStrMap, null);

            // Run threadcontext on all threads in verbose mode
            cmdStr = "threadcontext -v -a";
            cmds = List.of(cmdStr);
            expStrMap = new HashMap<>();
            expStrMap.put(cmdStr, List.of(
                    "Thread \"Common-Cleaner\"",
                    "Thread \"Service Thread\"",
                    "Thread \"Finalizer\"",
                    "Thread \"SteadyStateThread\""));
            Map<String, List<String>> unexpStrMap = new HashMap<>();
            unexpStrMap.put(cmdStr, List.of(
                    "In java stack for thread \"SteadyStateThread\""));
            test.run(theApp.getPid(), cmds, expStrMap, unexpStrMap);

            // Look for a line like the following and parse the threadID out of it.
            //    Thread "SteadyStateThread" id=18010 Address=0x000014bf103eaf50
            String[] parts = cmdOutput.split("Thread \"SteadyStateThread\" id=");
            String[] tokens = parts[1].split(" ");
            String threadID = tokens[0];

            // Run threadcontext on the SteadyStateThread in verbose mode
            cmdStr = "threadcontext -v " + threadID;
            cmds = List.of(cmdStr);
            expStrMap = new HashMap<>();
            unexpStrMap = new HashMap<>();
            if (Platform.isWindows()) {
                // On windows thread IDs are not guaranteed to be the same each time you attach,
                // so the ID we gleaned above for SteadyStateThread may not actually be for
                // SteadyStateThread when we attach for the next threadcontext command, so we
                // choose not to check the result on Windows.
            } else {
                expStrMap.put(cmdStr, List.of(
                        "Thread \"SteadyStateThread\"",
                        "java.lang.Thread.State: BLOCKED",
                        "In java stack \\[0x\\p{XDigit}+,0x\\p{XDigit}+,0x\\p{XDigit}+\\] for thread"));
                unexpStrMap.put(cmdStr, List.of(
                        "Thread \"Common-Cleaner\"",
                        "Thread \"Service Thread\"",
                        "Thread \"Finalizer\""));
            }
            test.run(theApp.getPid(), cmds, expStrMap, unexpStrMap);

            // Run threadcontext on all threads in verbose mode

        } catch (SkippedException se) {
            throw se;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            LingeredApp.stopApp(theApp);
        }

        System.out.println("Test PASSED");
    }
}
