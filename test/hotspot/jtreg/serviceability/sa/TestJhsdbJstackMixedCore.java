/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA
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

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.CoreUtils;

/**
 * @test
 * @bug 8374482
 * @requires (os.family == "linux") & (vm.hasSA)
 * @requires os.arch == "amd64"
 * @library /test/lib
 * @run driver TestJhsdbJstackMixedCore
 */
public class TestJhsdbJstackMixedCore {

    private static void runJstackMixed(String coreFileName) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addVMArgs(Utils.getTestJavaOpts());
        launcher.addToolArg("jstack");
        launcher.addToolArg("--mixed");
        launcher.addToolArg("--exe");
        launcher.addToolArg(JDKToolFinder.getTestJDKTool("java"));
        launcher.addToolArg("--core");
        launcher.addToolArg(coreFileName);

        ProcessBuilder pb = SATestUtils.createProcessBuilder(launcher);
        Process jhsdb = pb.start();
        OutputAnalyzer out = new OutputAnalyzer(jhsdb);

        jhsdb.waitFor();

        System.out.println(out.getStdout());
        System.err.println(out.getStderr());

        out.shouldContain("<signal handler called>");
        out.shouldContain("Java_jdk_test_lib_apps_LingeredApp_crash");
    }

    public static void main(String... args) throws Throwable {
        LingeredApp app = new LingeredApp();
        app.setForceCrash(true);
        LingeredApp.startApp(app, CoreUtils.getAlwaysPretouchArg(true));
        app.waitAppTerminate();

        String crashOutput = app.getOutput().getStdout();
        String coreFileName = CoreUtils.getCoreFileLocation(crashOutput, app.getPid());
        runJstackMixed(coreFileName);
    }
}
