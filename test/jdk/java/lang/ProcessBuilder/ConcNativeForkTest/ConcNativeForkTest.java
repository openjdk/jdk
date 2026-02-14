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
 * @test id=POSIX_SPAWN
 * @requires os.family != "windows"
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm/manual -Djdk.lang.Process.launchMechanism=POSIX_SPAWN ConcNativeForkTest
 */

/*
 * @test id=FORK
 * @requires os.family != "windows"
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm/manual -Djdk.lang.Process.launchMechanism=FORK ConcNativeForkTest
 */

/*
 * @test id=VFORK
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /test/lib
 * @run main/othervm/manual -Djdk.lang.Process.launchMechanism=VFORK ConcNativeForkTest
 */

public class ConcNativeForkTest {

    // How this works:
    // - We start a child process via ProcessBuilder. Does not matter what, we just call "/bin/true".
    // - Concurrently, we continuously (up to a limit) fork natively; these forks will all exec "sleep 30".
    // - If the natively forked child process forks off at the right (wrong) moment, it will catch the open pipe from
    //   the "/bin/true" child process, and forcing the parent process (this test) to wait in ProcessBuilder.start()
    //   (inside forkAndExec()) until the natively forked child releases the pipe file descriptors it inherited.

    // Note: obviously, this is racy and depends on scheduler timings of the underlying OS. The test succeeding is
    // no proof the bug does not exist (see PipesCloseOnExecTest as a complimentary test that is more reliable, but
    // only works on Linux).
    // It seems to reliably reproduce the bug on Linux x64, though.

    native static boolean prepareNativeForkerThread(int numForks);
    native static void releaseNativeForkerThread();
    native static void stopNativeForkerThread();

    private static final int numIterations = 20;

    public static void main(String[] args) throws Exception {

        System.out.println("jdk.lang.Process.launchMechanism=" +
                System.getProperty("jdk.lang.Process.launchMechanism"));

        System.loadLibrary("ConcNativeFork");

        // A very simple program returning immediately (/bin/true)
        ProcessBuilder pb = new ProcessBuilder("true").inheritIO();
        final int numJavaProcesses = 10;
        final int numNativeProcesses = 250;
        Process[] processes = new Process[numJavaProcesses];

        for (int iteration = 0; iteration < numIterations; iteration ++) {

            if (!prepareNativeForkerThread(numNativeProcesses)) {
                throw new RuntimeException("Failed to start native forker thread (see stdout)");
            }

            long[] durations = new long[numJavaProcesses];

            releaseNativeForkerThread();

            for (int np = 0; np < numJavaProcesses; np ++) {
                long t1 = System.currentTimeMillis();
                try (Process p = pb.start()) {
                    durations[np] = System.currentTimeMillis() - t1;
                    processes[np] = p;
                }
            }

            stopNativeForkerThread();

            long longestDuration = 0;
            for (int np = 0; np < numJavaProcesses; np ++) {
                processes[np].waitFor();
                System.out.printf("Duration: %dms%n", durations[np]);
                longestDuration = Math.max(durations[np], longestDuration);
            }

            System.out.printf("Longest startup time: %dms%n", longestDuration);

            if (longestDuration >= 30000) {
                throw new RuntimeException("Looks like we blocked on native fork");
            }
        }

    }

}
