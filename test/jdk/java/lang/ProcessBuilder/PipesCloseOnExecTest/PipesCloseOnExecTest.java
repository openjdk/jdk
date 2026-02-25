/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, IBM Corp.
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

/*
 * @test id=FORK
 * @bug 8377907
 * @summary Check that we don't open pipes without CLOEXCEC
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=FORK PipesCloseOnExecTest
 */

/*
 * @test id=VFORK
 * @bug 8377907
 * @summary Check that we don't open pipes without CLOEXCEC
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=VFORK PipesCloseOnExecTest
 */

/*
 * @test id=POSIX_SPAWN
 * @bug 8377907
 * @summary Check that we don't open pipes without CLOEXCEC
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm/native -Djdk.lang.Process.launchMechanism=POSIX_SPAWN PipesCloseOnExecTest
 */

import jdk.test.lib.process.OutputAnalyzer;

import java.time.LocalTime;

public class PipesCloseOnExecTest {

    // How this works:
    // - We start a child process A. Does not matter what, we just call "/bin/date".
    // - Concurrently, we (natively, continuously) iterate all pipe file descriptors in
    //   the process and count all those that are not tagged with CLOEXEC. Finding one
    //   counts as error.

    // Note that this test can only reliably succeed with Linux and the xxxBSDs, where
    // we have pipe2(2).
    //
    // On MacOs and AIX, we emulate pipe2(2) with pipe(2) and fcntl(2); therefore we
    // have a tiny time window in which a concurrent thread can can observe pipe
    // filedescriptors without CLOEXEC. Furthermore, on MacOS, we also have to employ
    // the double-dup-trick to workaround a the buggy MacOS implementation of posix_spawn.
    // Therefore, on these platforms, the test would (correctly) spot "bad" file descriptors.

    native static boolean startTester();
    native static boolean stopTester();

    static final int num_tries = 100;

    public static void main(String[] args) throws Exception {

        System.out.println("jdk.lang.Process.launchMechanism=" +
                           System.getProperty("jdk.lang.Process.launchMechanism"));

        System.loadLibrary("PipesCloseOnExec");

        if (!startTester()) {
            throw new RuntimeException("Failed to start testers (see stdout)");
        }

        System.out.println(LocalTime.now() + ": Call ProcessBuilder.start...");

        // Start /bin/true, repeatedly, over and over; the hope is that one of the starts will caused FDs without
        // CLOEXEC long enough for the concurrent testers to pick them up
        for (int i = 0; i < num_tries; i ++) {
            ProcessBuilder pb = new ProcessBuilder("true").inheritIO();
            new OutputAnalyzer(pb.start()).shouldHaveExitValue(0);
        }

        if (!stopTester()) {
              // uncomment to get lsof
              // ProcessBuilder pb = new ProcessBuilder("lsof").inheritIO();
              // try (Process p = pb.start()) { p.waitFor(); }
            throw new RuntimeException("Catched FDs without CLOEXEC? Check output.");
        }
    }
}
