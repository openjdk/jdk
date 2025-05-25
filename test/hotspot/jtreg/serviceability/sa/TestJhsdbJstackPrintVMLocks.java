/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

/**
 * @test
 * @summary Test verifies that jstack --mixed prints information about VM locks
 * @requires vm.hasSA
 * @requires (os.arch != "riscv64" | !(vm.cpu.features ~= ".*qemu.*"))
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestJhsdbJstackPrintVMLocks
 */

public class TestJhsdbJstackPrintVMLocks {

    final static int MAX_ATTEMPTS = 5;
    public static void main(String[] args) throws Exception {
        SATestUtils.skipIfCannotAttach(); // throws SkippedException if attach not expected to work.

        LingeredApp theApp = null;
        try {
            theApp = new LingeredAppWithLockInVM();
            LingeredApp.startApp(theApp,
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+WhiteBoxAPI",
                    "-Xbootclasspath/a:.");

            System.out.println("Started LingeredApp with pid " + theApp.getPid());
            theApp.waitAppReadyOrCrashed();

            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                JDKToolLauncher launcher = JDKToolLauncher
                        .createUsingTestJDK("jhsdb");
                launcher.addToolArg("jstack");
                launcher.addToolArg("--mixed");
                launcher.addToolArg("--pid");
                launcher.addToolArg(Long.toString(theApp.getPid()));

                ProcessBuilder pb = SATestUtils.createProcessBuilder(launcher);
                Process jhsdb = pb.start();
                OutputAnalyzer out = new OutputAnalyzer(jhsdb);

                jhsdb.waitFor();

                System.out.println(out.getStdout());
                System.err.println(out.getStderr());

                if (out.contains("Mutex VMStatistic_lock is owned by LockerThread")) {
                    System.out.println("Test PASSED");
                    return;
                }
                Thread.sleep(attempt * 2000);
            }
            throw new RuntimeException("Not able to find lock");
        } finally {
            if (theApp.getProcess() != null) {
                theApp.deleteLock();
                theApp.getProcess().destroyForcibly();
            }
        }
    }
}
