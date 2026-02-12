/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, NTT DATA
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

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test id=xcomp
 * @bug 8370176
 * @requires vm.hasSA
 * @requires os.family == "linux"
 * @requires os.arch == "amd64"
 * @library /test/lib
 * @run driver TestJhsdbJstackMixedWithXComp
 */

/**
 * @test id=xcomp-preserve-frame-pointer
 * @bug 8370176
 * @requires vm.hasSA
 * @requires os.family == "linux"
 * @requires os.arch == "amd64"
 * @library /test/lib
 * @run driver TestJhsdbJstackMixedWithXComp -XX:+PreserveFramePointer
 */

/**
 * @test id=xcomp-disable-tiered-compilation
 * @bug 8370176
 * @requires vm.hasSA
 * @requires os.family == "linux"
 * @requires os.arch == "amd64"
 * @library /test/lib
 * @run driver TestJhsdbJstackMixedWithXComp -XX:-TieredCompilation
 */


public class TestJhsdbJstackMixedWithXComp {

    private static void runJstack(LingeredApp app) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addVMArgs(Utils.getFilteredTestJavaOpts("-showversion"));
        launcher.addToolArg("jstack");
        launcher.addToolArg("--mixed");
        launcher.addToolArg("--pid");
        launcher.addToolArg(Long.toString(app.getPid()));

        ProcessBuilder pb = SATestUtils.createProcessBuilder(launcher);
        Process jhsdb = pb.start();
        OutputAnalyzer out = new OutputAnalyzer(jhsdb);

        jhsdb.waitFor();

        String stdout = out.getStdout();
        System.out.println(stdout);
        System.err.println(out.getStderr());

        out.stderrShouldBeEmptyIgnoreVMWarnings();

        List<String> targetStackTrace = new ArrayList<>();
        boolean inStack = false;
        for (String line : stdout.split("\n")) {
            if (line.contains("<nep_invoker_blob>")) {
                inStack = true;
            } else if (inStack && line.contains("-----------------")) {
                inStack = false;
                break;
            }

            if (inStack) {
                targetStackTrace.add(line);
            }
        }

        boolean found = targetStackTrace.stream()
                                        .anyMatch(l -> l.contains("thread_native_entry"));
        if (!found) {
            throw new RuntimeException("Test failed!");
        }
    }

    public static void main(String... args) throws Exception {
        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.
        LingeredApp app = null;

        try {
            List<String> jvmOpts = new ArrayList<>();
            jvmOpts.add("-Xcomp");
            jvmOpts.addAll(Arrays.asList(args));

            app = new LingeredAppWithVirtualThread();
            LingeredApp.startApp(app, jvmOpts.toArray(new String[0]));
            System.out.println("Started LingeredApp with pid " + app.getPid());
            runJstack(app);
            System.out.println("Test Completed");
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        } finally {
            LingeredApp.stopApp(app);
        }
    }
}
