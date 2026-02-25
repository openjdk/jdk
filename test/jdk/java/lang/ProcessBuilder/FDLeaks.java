/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;

/*
 * @test id=FORK
 * @summary Check that we don't accumulate leaked FDs in the parent process
 * @requires os.family == "linux"
 * @library /test/lib
 * @run main/othervm -Djdk.lang.Process.launchMechanism=fork FDLeaks
 */

/*
 * @test id=VFORK
 * @summary Check that we don't accumulate leaked FDs in the parent process
 * @requires os.family == "linux"
 * @library /test/lib
 * @run main/othervm -Djdk.lang.Process.launchMechanism=vfork FDLeaks
 */

/*
 * @test id=POSIX_SPAWN
 * @summary Check that we don't accumulate leaked FDs in the parent process
 * @requires os.family == "linux"
 * @library /test/lib
 * @run main/othervm -Djdk.lang.Process.launchMechanism=posix_spawn FDLeaks
 */

public class FDLeaks {

    final static int repeatCount = 50;

    // How this works:
    // We execute a mix of failing and succeeding process spawns with various
    // flavors of IO redirections many times; we observe the open file descriptors
    // before and afterwards. Test fails if we have significantly more file descriptors
    // open afterwards than before.

    static int countNumberOfOpenFileDescriptors() {
        return new File("/proc/self/fd").list().length;
    }

    static void printOpenFileDescriptors() {
        long mypid =  ProcessHandle.current().pid();
        try(Process p = new ProcessBuilder("lsof", "-p", Long.toString(mypid))
                .inheritIO().start()) {
            p.waitFor();
        } catch (InterruptedException | IOException ignored) {
            // Quietly swallow; it was just an attempt.
        }
    }

    static String readFirstLineOf(File f) {
        String result;
        try (BufferedReader b = new BufferedReader(new FileReader(f))){
            result = b.readLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    static void runThisExpectSuccess(ProcessBuilder bld) {
        try(Process p = bld.start()) {
            p.waitFor();
            if (p.exitValue() != 0) {
                throw new RuntimeException("Unexpected exitcode");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static void runThisExpectError(ProcessBuilder bld) {
        boolean failed = false;
        try(Process p = bld.start()) {
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            failed = true;
        }
        if (!failed) {
            throw new RuntimeException("Expected Error");
        }
    }

    static void runPosWithPipes() {
        ProcessBuilder bld = new ProcessBuilder("sh", "-c", "echo hallo");
        runThisExpectSuccess(bld);
    }

    static void runPosWithInheritIO() {
        ProcessBuilder bld = new ProcessBuilder("sh", "-c", "echo hallo").inheritIO();
        runThisExpectSuccess(bld);
    }

    static void runPosWithRedirectToFile() {
        File fo = new File("test.out");
        ProcessBuilder bld = new ProcessBuilder("sh", "-c", "echo hallo");
        bld.redirectOutput(ProcessBuilder.Redirect.to(fo));
        runThisExpectSuccess(bld);
        if (!readFirstLineOf(fo).equals("hallo")) {
            throw new RuntimeException("mismatch");
        }
    }

    static void runNegWithPipes() {
        ProcessBuilder bld = new ProcessBuilder("doesnotexist");
        runThisExpectError(bld);
    }

    static void runNegWithInheritIO() {
        ProcessBuilder bld = new ProcessBuilder("doesnotexist").inheritIO();
        runThisExpectError(bld);
    }

    static void runNegWithRedirectToFile() {
        File fo = new File("test.out");
        ProcessBuilder bld = new ProcessBuilder("doesnotexist");
        bld.redirectOutput(ProcessBuilder.Redirect.to(fo));
        runThisExpectError(bld);
    }

    static void doTestNTimesAndCountFDs(Runnable runnable, String name) {
        System.out.println(name);
        int c1 = countNumberOfOpenFileDescriptors();
        for (int i = 0; i < repeatCount; i++) {
            runnable.run();
        }
        int c2 = countNumberOfOpenFileDescriptors();
        System.out.printf("%d->%d", c1, c2);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("jdk.lang.Process.launchMechanism=" +
                System.getProperty("jdk.lang.Process.launchMechanism"));
        int c1 = countNumberOfOpenFileDescriptors();
        doTestNTimesAndCountFDs(FDLeaks::runPosWithPipes, "runPosWithPipes");
        doTestNTimesAndCountFDs(FDLeaks::runPosWithInheritIO, "runPosWithInheritIO");
        doTestNTimesAndCountFDs(FDLeaks::runPosWithRedirectToFile, "runPosWithRedirectToFile");
        doTestNTimesAndCountFDs(FDLeaks::runNegWithPipes, "runNegWithPipes");
        doTestNTimesAndCountFDs(FDLeaks::runNegWithInheritIO, "runNegWithInheritIO");
        doTestNTimesAndCountFDs(FDLeaks::runNegWithRedirectToFile, "runNegWithRedirectToFile");
        int c2 = countNumberOfOpenFileDescriptors();

        System.out.printf("All tests: %d->%d", c1, c2);
        printOpenFileDescriptors();

        final int fudge = 10;
        if (c2 > (c1 + fudge)) {
            throw new RuntimeException(
                    String.format("Leak suspected (%d->%d) - see lsof output", c1, c2));
        }
    }
}
