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

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;

import jtreg.SkippedException;

/**
 * @test
 * @bug 8396859
 * @requires vm.hasSA
 * @requires vm.gc != "Z"
 * @requires os.family == "linux"
 * @requires (os.arch != "riscv64" | !(vm.cpu.features ~= ".*qemu.*"))
 * @library /test/lib
 * @run driver TestELFDebuginfo
 */
public class TestELFDebuginfo {

    private static void runJstack(LingeredApp app) throws Exception {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
        launcher.addVMArgs(Utils.getTestJavaOpts());
        launcher.addToolArg("jstack");
        launcher.addToolArg("--mixed");
        launcher.addToolArg("--pid");
        launcher.addToolArg(Long.toString(app.getPid()));

        ProcessBuilder pb = SATestUtils.createProcessBuilder(launcher);
        Process jhsdb = pb.start();
        OutputAnalyzer out = new OutputAnalyzer(jhsdb);

        jhsdb.waitFor();

        System.out.println(out.getStdout());
        System.err.println(out.getStderr());

        out.stderrShouldBeEmptyIgnoreVMWarnings();
        out.shouldContain("LingeredAppForDebuginfoTest.callSleep");
        out.shouldContain("sleep_internal");
    }

    public static void main(String... args) throws Exception {
        if (Platform.isMusl()) {
            if (!Platform.isX64()) {
                throw new SkippedException("This platform is not supported.");
            }

            var libc = SATestUtils.getLibCPath();
            if (SATestUtils.getDebugInfo(libc) == null) {
                throw new SkippedException("libc debug info is not available.");
            }
        }

        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.
        LingeredApp app = null;

        try {
            // Needed for LingeredApp to be able to resolve native library.
            String libPath = System.getProperty("java.library.path");
            String[] vmArgs = (libPath != null)
                ? Utils.prependTestJavaOpts("-Djava.library.path=" + libPath)
                : Utils.getTestJavaOpts();

            app = new LingeredAppForDebuginfoTest();
            LingeredApp.startAppExactJvmOpts(app, vmArgs);
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
