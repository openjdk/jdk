/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;

/**
 * @test
 * @requires vm.hasSAandCanAttach
 * @library /test/lib
 * @run main JhsdbThreadInfoTest
 */
public class JhsdbThreadInfoTest {

    public static void main(String[] args) throws Exception {

        LingeredApp app = null;

        try {
            app = LingeredApp.startApp();
            System.out.println("Started LingeredApp with pid " + app.getPid());

            JDKToolLauncher jhsdbLauncher = JDKToolLauncher.createUsingTestJDK("jhsdb");

            jhsdbLauncher.addToolArg("jstack");
            jhsdbLauncher.addToolArg("--pid");
            jhsdbLauncher.addToolArg(Long.toString(app.getPid()));

            ProcessBuilder pb = new ProcessBuilder();
            pb.command(jhsdbLauncher.getCommand());
            Process jhsdb = pb.start();

            OutputAnalyzer out = new OutputAnalyzer(jhsdb);

            jhsdb.waitFor();

            System.out.println(out.getStdout());
            System.err.println(out.getStderr());

            out.shouldMatch("\".+\" #\\d+ daemon prio=\\d+ tid=0x[0-9a-f]+ nid=0x[0-9a-f]+ .+ \\[0x[0-9a-f]+]");
            out.shouldMatch("\"main\" #\\d+ prio=\\d+ tid=0x[0-9a-f]+ nid=0x[0-9a-f]+ .+ \\[0x[0-9a-f]+]");
            out.shouldMatch("   java.lang.Thread.State: .+");
            out.shouldMatch("   JavaThread state: _thread_.+");

            out.shouldNotContain("   java.lang.Thread.State: UNKNOWN");

            // stderr should be empty except for VM warnings.
            if (!out.getStderr().isEmpty()) {
                List<String> lines = Arrays.asList(out.getStderr().split("(\\r\\n|\\n|\\r)"));
                Pattern p = Pattern.compile(".*VM warning.*");
                for (String line : lines) {
                    Matcher m = p.matcher(line);
                    if (!m.matches()) {
                        throw new RuntimeException("Stderr has output other than VM warnings");
                    }
                }
            }

            System.out.println("Test Completed");
        } catch (InterruptedException ie) {
            throw new Error("Problem awaiting the child process: " + ie, ie);
        } catch (Exception attachE) {
            throw new Error("Couldn't start jhsdb, attach to LingeredApp or match ThreadName: " + attachE);
        } finally {
            LingeredApp.stopApp(app);
        }
    }
}
