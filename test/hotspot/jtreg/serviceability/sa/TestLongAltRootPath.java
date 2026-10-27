/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.List;

import jtreg.SkippedException;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.CoreUtils;

/**
 * @test
 * @bug 8391991
 * @summary Test using a very long SA_ALTROOT
 * @requires vm.hasSA
 * @requires vm.gc != "Z"
 * @requires os.family != "windows"
 * @library /test/lib
 * @run main/othervm TestLongAltRootPath
 */

/*
 * Attempt to crash SA with a very long SA_ALTROOT.
 */

public class TestLongAltRootPath {

    public static void main(String[] args) throws Exception {
        LingeredApp theApp = null;
        String coreFileName = null;
        try {
            theApp = new LingeredApp();
            theApp.setForceCrash(true);
            LingeredApp.startApp(theApp, CoreUtils.getAlwaysPretouchArg(true));
            System.out.println("Started LingeredApp with pid " + theApp.getPid());

            String crashOutput = theApp.getOutput().getStdout();
            coreFileName = CoreUtils.getCoreFileLocation(crashOutput, theApp.getPid());

            JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
            launcher.addVMArgs(Utils.getTestJavaOpts());
            launcher.addToolArg("jstack");
            launcher.addToolArg("--exe");
            launcher.addToolArg(JDKToolFinder.getTestJDKTool("java"));
            launcher.addToolArg("--core");
            launcher.addToolArg(coreFileName);

            List<String> cmdStringList = new ArrayList<String>();
            cmdStringList.addAll(Arrays.asList(launcher.getCommand()));

            // Build a long path name to use for SA_ALTROOT that will cause
            // a crash if proper length checks are not made.
            int saAltRootMaxLen = 2 * 10_000;
            String dirPattern = "/abcdefghij01234567890123456789";
            StringBuilder saAltRoot = new StringBuilder(saAltRootMaxLen);
            while (saAltRoot.length() < saAltRootMaxLen) {
                saAltRoot.append(dirPattern);
            }

            // Launch "jhsdb jstack" on the core file.
            ProcessBuilder pb = new ProcessBuilder(cmdStringList);
            SATestUtils.addPrivilegesIfNeeded(pb);
            pb.environment().put("SA_ALTROOT", saAltRoot.toString());
            pb.environment().put("LIBSAPROC_DEBUG", "1");
            Process jhsdb = pb.start();
            OutputAnalyzer out = new OutputAnalyzer(jhsdb);

            jhsdb.waitFor();

            System.out.println(out.getStdout());
            System.err.println(out.getStderr());
            System.out.println("exitValue: " + out.getExitValue());

            // jstack should produce an error message and exception due to the
            // bad SA_ALTROOT. This results in an exitValue of 1.
            out.shouldMatch("is bigger than PATH_MAX");
            out.shouldHaveExitValue(1);
        } catch (SkippedException se) {
            throw se;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        }
        System.out.println("Test PASSED");
    }
}
