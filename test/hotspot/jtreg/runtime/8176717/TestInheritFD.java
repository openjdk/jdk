import static java.io.File.createTempFile;
import static java.lang.Long.parseLong;
import static java.lang.System.getProperty;
import static java.lang.management.ManagementFactory.getOperatingSystemMXBean;
import static java.nio.file.Files.readAllBytes;
import static jdk.test.lib.process.ProcessTools.createJavaProcessBuilder;

import java.io.File;
import java.io.IOException;

import com.sun.management.UnixOperatingSystemMXBean;

/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestInheritFD
 * @bug 8176717 8176809
 * @summary a new process should not inherit open file descriptors
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 */

/**
 * Test that HotSpot does not leak logging file descriptors.
 *
 * This test is performed in three steps. The first VM starts a second VM with
 * gc logging enabled. The second VM starts a third VM and redirects the third
 * VMs output to the first VM, it then exits and hopefully closes its log file.
 *
 * The third VM waits for the second to exit and close its log file. After that,
 * the third VM tries to rename the log file of the second VM. If it succeeds in
 * doing so it means that the third VM did not inherit the open log file
 * (windows can not rename opened files easily)
 *
 * The third VM communicates the success to rename the file by printing "CLOSED
 * FD". The first VM checks that the string was printed by the third VM.
 *
 * On unix like systems, UnixOperatingSystemMXBean is used to check open file
 * descriptors.
 */

public class TestInheritFD {

    public static final String LEAKS_FD = "VM RESULT => LEAKS FD";
    public static final String RETAINS_FD = "VM RESULT => RETAINS FD";
    public static final String EXIT = "VM RESULT => VM EXIT";

    // first VM
    public static void main(String[] args) throws Exception {
        String logPath = createTempFile("logging", ".log").getName();
        File commFile = createTempFile("communication", ".txt");

        ProcessBuilder pb = createJavaProcessBuilder(
            "-Xlog:gc:\"" + logPath + "\"",
            "-Dtest.jdk=" + getProperty("test.jdk"),
            VMStartedWithLogging.class.getName(),
            logPath);

        pb.redirectOutput(commFile); // use temp file to communicate between processes
        pb.start();

        String out = "";
        do {
            out = new String(readAllBytes(commFile.toPath()));
            Thread.sleep(100);
            System.out.println("SLEEP 100 millis");
        } while (!out.contains(EXIT));

        System.out.println(out);
        if (out.contains(RETAINS_FD)) {
            System.out.println("Log file was not inherited by third VM");
        } else {
            throw new RuntimeException("could not match: " + RETAINS_FD);
        }
    }

    static class VMStartedWithLogging {
        // second VM
        public static void main(String[] args) throws IOException, InterruptedException {
            ProcessBuilder pb = createJavaProcessBuilder(
                "-Dtest.jdk=" + getProperty("test.jdk"),
                VMShouldNotInheritFileDescriptors.class.getName(),
                args[0],
                "" + ProcessHandle.current().pid(),
                "" + (supportsUnixMXBean()?+unixNrFD():-1));
            pb.inheritIO(); // in future, redirect information from third VM to first VM
            pb.start();
        }
    }

    static class VMShouldNotInheritFileDescriptors {
        // third VM
        public static void main(String[] args) throws InterruptedException {
            File logFile = new File(args[0]);
            long parentPid = parseLong(args[1]);
            long parentFDCount = parseLong(args[2]);

            if(supportsUnixMXBean()){
                long thisFDCount = unixNrFD();
                System.out.println("This VM FD-count (" + thisFDCount + ") should be strictly less than parent VM FD-count (" + parentFDCount + ") as log file should have been closed");
                System.out.println(thisFDCount<parentFDCount?RETAINS_FD:LEAKS_FD);
            } else if (getProperty("os.name").toLowerCase().contains("win")) {
                windows(logFile, parentPid);
            } else {
                System.out.println(LEAKS_FD); // default fail on unknown configuration
            }
            System.out.println(EXIT);
        }
    }

    static boolean supportsUnixMXBean() {
        return getOperatingSystemMXBean() instanceof UnixOperatingSystemMXBean;
    }

    static long unixNrFD() {
        UnixOperatingSystemMXBean osBean = (UnixOperatingSystemMXBean) getOperatingSystemMXBean();
        return osBean.getOpenFileDescriptorCount();
    }

    static void windows(File f, long parentPid) throws InterruptedException {
        System.out.println("waiting for pid: " + parentPid);
        ProcessHandle.of(parentPid).ifPresent(handle -> handle.onExit().join());
        System.out.println("trying to rename file to the same name: " + f);
        System.out.println(f.renameTo(f)?RETAINS_FD:LEAKS_FD); // this parts communicates a closed file descriptor by printing "CLOSED FD"
    }
}