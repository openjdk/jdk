/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static java.lang.Character.isDigit;
import static java.lang.Long.parseLong;
import static java.lang.System.getProperty;
import static java.nio.file.Files.readAllBytes;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static jdk.test.lib.process.ProcessTools.createLimitedTestJavaProcessBuilder;
import static jdk.test.lib.Platform.isWindows;
import jdk.test.lib.Utils;
import jdk.test.lib.Platform;
import jtreg.SkippedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.stream.Stream;

/*
 * @test TestInheritFD
 * @bug 8176717 8176809 8222500
 * @summary a new process should not inherit open file descriptors
 * @comment On Aix lsof requires root privileges.
 * @requires os.family != "aix"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestInheritFD
 */

/**
 * Test that HotSpot does not leak logging file descriptors.
 *
 * This test is performed in three steps. The first VM starts a second VM with
 * gc logging enabled. The second VM starts a third VM and redirects the third
 * VMs output to the first VM. The second VM then exits and hopefully closes
 * its log file.
 *
 * The third VM waits for the second to exit and close its log file.
 * On Windows, the third VM tries to rename the log file of the second VM.
 * If it succeeds in doing so it means that the third VM did not inherit
 * the open log file (windows cannot rename opened files easily).
 * On unix like systems, the third VM uses "lsof" for verification.
 *
 * The third VM communicates success by printing "RETAINS FD". The first VM
 * waits for the third VM to exit and checks that the string was printed by
 * the third VM.
 */

public class TestInheritFD {

    public static final String LEAKS_FD = "VM RESULT => LEAKS FD";
    public static final String RETAINS_FD = "VM RESULT => RETAINS FD";
    public static final String EXIT = "VM RESULT => VM EXIT";
    public static final String LOG_SUFFIX = ".strangelogsuffixthatcanbecheckedfor";
    public static final String USER_DIR = System.getProperty("user.dir");
    public static final String LSOF_PID_PREFIX = " VM lsof pid=";
    public static final String SECOND_VM_PID_PREFIX = "Second VM pid=";
    public static final String THIRD_VM_PID_PREFIX = "Third VM pid=";
    public static final String THIRD_VM_WAITING_PREFIX = "Third VM waiting for second VM pid=";

    public static float timeoutFactor = Float.parseFloat(System.getProperty("test.timeout.factor", "1.0"));
    public static long subProcessTimeout = (long)(15L * timeoutFactor);

    // Extract a pid from the specified String at the specified start offset.
    private static long extractPidFromStringOffset(String str, int start) {
        int end;
        for (end = start; end < str.length(); end++) {
            if (!isDigit(str.charAt(end))) {
                break;
            }
        }
        if (start == end) {  // no digits at all
            return -1;
        }
        return parseLong(str.substring(start, end));
    }

    // Wait for the sub-process pids identified in commFile to finish executing.
    // Returns true if RETAINS_FD was found in the commFile and false otherwise.
    enum Result {
        FOUND_LEAKS_FD,
        FOUND_RETAINS_FD,
        FOUND_NONE // Unexpected.
    };
    private static Result waitForSubPids(File commFile) throws Exception {
        String out = "";
        int sleepCnt = 0;
        long secondVMPID = -1;
        long secondVMlsofPID = -1;
        long thirdVMPID = -1;
        long thirdVMlsofPID = -1;
        // Only have to gather info until the doneWithPattern shows up in the output:
        String doneWithPattern;
        if (isWindows()) {
            doneWithPattern = THIRD_VM_PID_PREFIX;
        } else {
            doneWithPattern = "Third" + LSOF_PID_PREFIX;
        }
        do {
            out = new String(readAllBytes(commFile.toPath()));
            if (secondVMPID == -1) {
                int ind = out.indexOf(SECOND_VM_PID_PREFIX);
                if (ind != -1) {
                    int startPid = ind + SECOND_VM_PID_PREFIX.length();
                    secondVMPID = extractPidFromStringOffset(out, startPid);
                    System.out.println("secondVMPID=" + secondVMPID);
                }
            }
            if (!isWindows() && secondVMlsofPID == -1) {
                String prefix = "Second" + LSOF_PID_PREFIX;
                int ind = out.indexOf(prefix);
                if (ind != -1) {
                    int startPid = ind + prefix.length();
                    secondVMlsofPID = extractPidFromStringOffset(out, startPid);
                    System.out.println("secondVMlsofPID=" + secondVMlsofPID);
                }
            }
            if (thirdVMPID == -1) {
                int ind = out.indexOf(THIRD_VM_PID_PREFIX);
                if (ind != -1) {
                    int startPid = ind + THIRD_VM_PID_PREFIX.length();
                    thirdVMPID = extractPidFromStringOffset(out, startPid);
                    System.out.println("thirdVMPID=" + thirdVMPID);
                }
            }
            if (!isWindows() && thirdVMlsofPID == -1) {
                String prefix = "Third" + LSOF_PID_PREFIX;
                int ind = out.indexOf(prefix);
                if (ind != -1) {
                    int startPid = ind + prefix.length();
                    thirdVMlsofPID = extractPidFromStringOffset(out, startPid);
                    System.out.println("thirdVMlsofPID=" + thirdVMlsofPID);
                }
            }
            Thread.sleep(100);
            sleepCnt++;
        } while (!out.contains(doneWithPattern) && !out.contains(EXIT));

        System.out.println("Called Thread.sleep(100) " + sleepCnt + " times.");

        long subPids[] = new long[4];       // At most 4 pids to check.
        String subNames[] = new String[4];  // At most 4 names for those pids.
        int ind = 0;
        if (!isWindows() && secondVMlsofPID != -1) {
            // The second VM's lsof cmd should be the first non-windows sub-process to finish:
            subPids[ind] = secondVMlsofPID;
            subNames[ind] = "second VM lsof";
            ind++;
        }
        // The second VM should the second non-windows or first windows sub-process to finish:
        subPids[ind] = secondVMPID;
        subNames[ind] = "second VM";
        ind++;
        if (!isWindows() && thirdVMlsofPID != -1) {
            // The third VM's lsof cmd should be the third non-windows sub-process to finish:
            subPids[ind] = thirdVMlsofPID;
            subNames[ind] = "third VM lsof";
            ind++;
        }
        // The third VM should the last sub-process to finish:
        subPids[ind] = thirdVMPID;
        subNames[ind] = "third VM";
        ind++;
        if (isWindows()) {
            // No lsof pids on windows so we use fewer array slots.
            // Make sure they are marked as not used.
            for (; ind < subPids.length; ind++) {
                subPids[ind] = -1;
            }
        }

        try {
            for (ind = 0; ind < subPids.length; ind++) {
                if (subPids[ind] == -1) {
                    continue;
                }
                System.out.print("subs[" + ind + "]={pid=" + subPids[ind] + ", name=" + subNames[ind] + "}");
                ProcessHandle.of(subPids[ind]).ifPresent(handle -> handle.onExit().orTimeout(subProcessTimeout, TimeUnit.SECONDS).join());
                System.out.println(" finished.");
            }
        } catch (Exception e) {
            // Terminate the "subs" line from above:
            System.out.println(" Exception was thrown while trying to join() subPids: " + e.toString());
            throw e;
        } finally {
            // Reread to get everything in the commFile:
            out = new String(readAllBytes(commFile.toPath()));
            System.out.println("<BEGIN commFile contents>");
            System.out.println(out);
            System.out.println("<END commFile contents>");
        }
        if (out.contains(RETAINS_FD)) {
            return Result.FOUND_RETAINS_FD;
        } else if (out.contains(LEAKS_FD)) {
            return Result.FOUND_LEAKS_FD;
        } else {
            return Result.FOUND_NONE;
        }
    }

    // first VM
    public static void main(String[] args) throws Exception {
        System.out.println("subProcessTimeout=" + subProcessTimeout + " seconds.");
        System.out.println("First VM starts.");
        String logPath = Utils.createTempFile("logging", LOG_SUFFIX).toFile().getName();
        File commFile = Utils.createTempFile("communication", ".txt").toFile();

        if (!isWindows() && !lsofCommand().isPresent()) {
            throw new SkippedException("Could not find lsof like command");
        }

        ProcessBuilder pb = createLimitedTestJavaProcessBuilder(
            "-Xlog:gc:\"" + logPath + "\"",
            "-Dtest.jdk=" + getProperty("test.jdk"),
            VMStartedWithLogging.class.getName(),
            logPath);

        pb.redirectOutput(commFile); // use temp file to communicate between processes
        pb.start();

        Result result = waitForSubPids(commFile);
        if (result == Result.FOUND_RETAINS_FD) {
            System.out.println("Log file was not inherited by third VM.");
        } else if (result == Result.FOUND_LEAKS_FD) {
            throw new RuntimeException("Log file was leaked to the third VM.");
        } else {
            throw new RuntimeException("Found neither message, test failed to run correctly");
        }
        System.out.println("First VM ends.");
    }

    static class VMStartedWithLogging {
        // second VM
        public static void main(String[] args) throws IOException, InterruptedException {
            System.out.println(SECOND_VM_PID_PREFIX + ProcessHandle.current().pid());
            ProcessBuilder pb = createLimitedTestJavaProcessBuilder(
                "-Dtest.jdk=" + getProperty("test.jdk"),
                VMShouldNotInheritFileDescriptors.class.getName(),
                args[0],
                "" + ProcessHandle.current().pid());
            pb.inheritIO(); // in future, redirect information from third VM to first VM
            pb.start();

            if (!isWindows()) {
                System.out.println("(Second VM) Open file descriptors:\n" + outputContainingFilenames("Second").stream().collect(joining("\n")));
            }
            if (false) {  // Enable to simulate a timeout in the second VM.
                Thread.sleep(300 * 1000);
            }
            System.out.println("Second VM ends.");
        }
    }

    static class VMShouldNotInheritFileDescriptors {
        // third VM
        public static void main(String[] args) throws InterruptedException {
            System.out.println(THIRD_VM_PID_PREFIX + ProcessHandle.current().pid());
            try {
                File logFile = new File(args[0]);
                long parentPid = parseLong(args[1]);
                fakeLeakyJVM(false); // for debugging of test case

                System.out.println(THIRD_VM_WAITING_PREFIX + parentPid);
                ProcessHandle.of(parentPid).ifPresent(handle -> handle.onExit().orTimeout(subProcessTimeout, TimeUnit.SECONDS).join());

                if (isWindows()) {
                    windows(logFile);
                } else {
                    Collection<String> output = outputContainingFilenames("Third");
                    System.out.println("(Third VM) Open file descriptors:\n" + output.stream().collect(joining("\n")));
                    System.out.println(findOpenLogFile(output) ? LEAKS_FD : RETAINS_FD);
                }
                if (false) {  // Enable to simulate a timeout in the third VM.
                    Thread.sleep(300 * 1000);
                }
            } catch (CompletionException e) {
                if (e.getCause() instanceof TimeoutException) {
                    System.out.println("(Third VM) Timed out waiting for second VM: " + e.toString());
                } else {
                    System.out.println("(Third VM) Exception was thrown: " + e.toString());
                }
                throw e;
            } catch (Exception e) {
                System.out.println("(Third VM) Exception was thrown: " + e.toString());
                throw e;
            } finally {
                System.out.println(EXIT);
                System.out.println("Third VM ends.");
            }
        }
    }

    // for debugging of test case
    @SuppressWarnings("resource")
    static void fakeLeakyJVM(boolean fake) {
        if (fake) {
            try {
                new FileOutputStream("fakeLeakyJVM" + LOG_SUFFIX, false);
            } catch (FileNotFoundException e) {
            }
        }
    }

    static Stream<String> runLsof(String whichVM, String... args){
        try {
            Process lsof = new ProcessBuilder(args).start();
            System.out.println(whichVM + LSOF_PID_PREFIX + lsof.pid());
            return new BufferedReader(new InputStreamReader(lsof.getInputStream())).lines();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Optional<String[]> lsofCommandCache = stream(new String[][]{
            {"/usr/bin/lsof", "-p"},
            {"/usr/sbin/lsof", "-p"},
            {"/bin/lsof", "-p"},
            {"/sbin/lsof", "-p"},
            {"/usr/local/bin/lsof", "-p"}})
        .filter(args -> new File(args[0]).exists())
        .findFirst();

    static Optional<String[]> lsofCommand() {
        return lsofCommandCache;
    }

    static Collection<String> outputContainingFilenames(String whichVM) {
        long pid = ProcessHandle.current().pid();
        String[] command = lsofCommand().orElseThrow(() -> new RuntimeException("lsof like command not found"));
        // Only search the directory in which the VM is running (user.dir property).
        System.out.println("using command: " + command[0] + " -a +d " + USER_DIR + " " + command[1] + " " + pid);
        return runLsof(whichVM, command[0], "-a", "+d", USER_DIR, command[1], "" + pid).collect(toList());
    }

    static boolean findOpenLogFile(Collection<String> fileNames) {
        String pid = Long.toString(ProcessHandle.current().pid());
        String[] command = lsofCommand().orElseThrow(() ->
                new RuntimeException("lsof like command not found"));
        String lsof = command[0];
        boolean isBusybox = Platform.isBusybox(lsof);
        return fileNames.stream()
            // lsof from busybox does not support "-p" option
            .filter(fileName -> !isBusybox || fileName.contains(pid))
            .filter(fileName -> fileName.contains(LOG_SUFFIX))
            .findAny()
            .isPresent();
    }

    static void windows(File f) throws InterruptedException {
        System.out.println("trying to rename file to the same name: " + f);
        System.out.println(f.renameTo(f) ? RETAINS_FD : LEAKS_FD); // this parts communicates a closed file descriptor by printing "VM RESULT => RETAINS FD"
    }
}
