/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug     8036823
 * @summary Creates two threads contending for the same lock and checks
 *      whether jstack reports "locked" by more than one thread.
 *
 * @library /testlibrary
 * @run main/othervm TestThreadDumpMonitorContention
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.java.testlibrary.*;

public class TestThreadDumpMonitorContention {
    // jstack tends to be closely bound to the VM that we are running
    // so use getTestJDKTool() instead of getCompileJDKTool() or even
    // getJDKTool() which can fall back to "compile.jdk".
    final static String JSTACK = JDKToolFinder.getTestJDKTool("jstack");
    final static String PID = getPid();

    // looking for header lines with these patterns:
    // "ContendingThread-1" #19 prio=5 os_prio=64 tid=0x000000000079c000 nid=0x23 runnable [0xffff80ffb8b87000]
    // "ContendingThread-2" #21 prio=5 os_prio=64 tid=0x0000000000780000 nid=0x2f waiting for monitor entry [0xfffffd7fc1111000]
    final static Pattern HEADER_PREFIX_PATTERN = Pattern.compile(
        "^\"ContendingThread-.*");
    final static Pattern HEADER_WAITING_PATTERN = Pattern.compile(
        "^\"ContendingThread-.* waiting for monitor entry .*");
    final static Pattern HEADER_RUNNABLE_PATTERN = Pattern.compile(
        "^\"ContendingThread-.* runnable .*");

    // looking for thread state lines with these patterns:
    // java.lang.Thread.State: RUNNABLE
    // java.lang.Thread.State: BLOCKED (on object monitor)
    final static Pattern THREAD_STATE_PREFIX_PATTERN = Pattern.compile(
        " *java\\.lang\\.Thread\\.State: .*");
    final static Pattern THREAD_STATE_BLOCKED_PATTERN = Pattern.compile(
        " *java\\.lang\\.Thread\\.State: BLOCKED \\(on object monitor\\)");
    final static Pattern THREAD_STATE_RUNNABLE_PATTERN = Pattern.compile(
        " *java\\.lang\\.Thread\\.State: RUNNABLE");

    // looking for duplicates of this pattern:
    // - locked <0x000000076ac59e20> (a TestThreadDumpMonitorContention$1)
    final static Pattern LOCK_PATTERN = Pattern.compile(
        ".* locked \\<.*\\(a TestThreadDumpMonitorContention.*");

    // sanity checking header and thread state lines associated
    // with this pattern:
    // - waiting to lock <0x000000076ac59e20> (a TestThreadDumpMonitorContention$1)
    final static Pattern WAITING_PATTERN = Pattern.compile(
        ".* waiting to lock \\<.*\\(a TestThreadDumpMonitorContention.*");

    volatile static boolean done = false;

    static int error_cnt = 0;
    static String header_line = null;
    static boolean have_header_line = false;
    static boolean have_thread_state_line = false;
    static int match_cnt = 0;
    static String[] match_list = new String[2];
    static int n_samples = 15;
    static String thread_state_line = null;
    static boolean verbose = false;

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            int arg_i = 0;
            if (args[arg_i].equals("-v")) {
                verbose = true;
                arg_i++;
            }

            try {
                n_samples = Integer.parseInt(args[arg_i]);
            } catch (NumberFormatException nfe) {
                System.err.println(nfe);
                usage();
            }
        }

        Runnable runnable = new Runnable() {
            public void run() {
                while (!done) {
                    synchronized (this) { }
                }
            }
        };
        Thread[] thread_list = new Thread[2];
        thread_list[0] = new Thread(runnable, "ContendingThread-1");
        thread_list[1] = new Thread(runnable, "ContendingThread-2");
        thread_list[0].start();
        thread_list[1].start();

        doSamples();

        done = true;

        thread_list[0].join();
        thread_list[1].join();

        if (error_cnt == 0) {
            System.out.println("Test PASSED.");
        } else {
            System.out.println("Test FAILED.");
            throw new AssertionError("error_cnt=" + error_cnt);
        }
    }

    // Reached a blank line which is the end of the
    // stack trace without matching either LOCK_PATTERN
    // or WAITING_PATTERN. Rare, but it's not an error.
    //
    // Example:
    // "ContendingThread-1" #21 prio=5 os_prio=64 tid=0x00000000007b9000 nid=0x2f runnable [0xfffffd7fc1111000]
    //    java.lang.Thread.State: RUNNABLE
    //         at TestThreadDumpMonitorContention$1.run(TestThreadDumpMonitorContention.java:67)
    //         at java.lang.Thread.run(Thread.java:745)
    //
    static boolean checkBlankLine(String line) {
        if (line.length() == 0) {
            have_header_line = false;
            have_thread_state_line = false;
            return true;
        }

        return false;
    }

    // Process the locked line here if we found one.
    //
    // Example 1:
    // "ContendingThread-1" #21 prio=5 os_prio=64 tid=0x00000000007b9000 nid=0x2f runnable [0xfffffd7fc1111000]
    //    java.lang.Thread.State: RUNNABLE
    //         at TestThreadDumpMonitorContention$1.run(TestThreadDumpMonitorContention.java:67)
    //         - locked <0xfffffd7e6a2912f8> (a TestThreadDumpMonitorContention$1)
    //         at java.lang.Thread.run(Thread.java:745)
    //
    // Example 2:
    // "ContendingThread-1" #21 prio=5 os_prio=64 tid=0x00000000007b9000 nid=0x2f waiting for monitor entry [0xfffffd7fc1111000]
    //    java.lang.Thread.State: BLOCKED (on object monitor)
    //         at TestThreadDumpMonitorContention$1.run(TestThreadDumpMonitorContention.java:67)
    //         - locked <0xfffffd7e6a2912f8> (a TestThreadDumpMonitorContention$1)
    //         at java.lang.Thread.run(Thread.java:745)
    //
    static boolean checkLockedLine(String line) {
        Matcher matcher = LOCK_PATTERN.matcher(line);
        if (matcher.matches()) {
            if (verbose) {
                System.out.println("locked_line='" + line + "'");
            }
            match_list[match_cnt] = new String(line);
            match_cnt++;

            matcher = HEADER_RUNNABLE_PATTERN.matcher(header_line);
            if (!matcher.matches()) {
                // It's strange, but a locked line can also
                // match the HEADER_WAITING_PATTERN.
                matcher = HEADER_WAITING_PATTERN.matcher(header_line);
                if (!matcher.matches()) {
                    System.err.println();
                    System.err.println("ERROR: header line does " +
                        "not match runnable or waiting patterns.");
                    System.err.println("ERROR: header_line='" +
                        header_line + "'");
                    System.err.println("ERROR: locked_line='" + line + "'");
                    error_cnt++;
                }
            }

            matcher = THREAD_STATE_RUNNABLE_PATTERN.matcher(thread_state_line);
            if (!matcher.matches()) {
                // It's strange, but a locked line can also
                // match the THREAD_STATE_BLOCKED_PATTERN.
                matcher = THREAD_STATE_BLOCKED_PATTERN.matcher(
                              thread_state_line);
                if (!matcher.matches()) {
                    System.err.println();
                    System.err.println("ERROR: thread state line does not " +
                        "match runnable or waiting patterns.");
                    System.err.println("ERROR: " + "thread_state_line='" +
                        thread_state_line + "'");
                    System.err.println("ERROR: locked_line='" + line + "'");
                    error_cnt++;
                }
            }

            // Have everything we need from this thread stack
            // that matches the LOCK_PATTERN.
            have_header_line = false;
            have_thread_state_line = false;
            return true;
        }

        return false;
    }

    // Process the waiting line here if we found one.
    //
    // Example:
    // "ContendingThread-2" #22 prio=5 os_prio=64 tid=0x00000000007b9800 nid=0x30 waiting for monitor entry [0xfffffd7fc1010000]
    //    java.lang.Thread.State: BLOCKED (on object monitor)
    //         at TestThreadDumpMonitorContention$1.run(TestThreadDumpMonitorContention.java:67)
    //         - waiting to lock <0xfffffd7e6a2912f8> (a TestThreadDumpMonitorContention$1)
    //         at java.lang.Thread.run(Thread.java:745)
    //
    static boolean checkWaitingLine(String line) {
        Matcher matcher = WAITING_PATTERN.matcher(line);
        if (matcher.matches()) {
            if (verbose) {
                System.out.println("waiting_line='" + line + "'");
            }

            matcher = HEADER_WAITING_PATTERN.matcher(header_line);
            if (!matcher.matches()) {
                System.err.println();
                System.err.println("ERROR: header line does " +
                    "not match a waiting pattern.");
                System.err.println("ERROR: header_line='" + header_line + "'");
                System.err.println("ERROR: waiting_line='" + line + "'");
                error_cnt++;
            }

            matcher = THREAD_STATE_BLOCKED_PATTERN.matcher(thread_state_line);
            if (!matcher.matches()) {
                System.err.println();
                System.err.println("ERROR: thread state line " +
                    "does not match a waiting pattern.");
                System.err.println("ERROR: thread_state_line='" +
                    thread_state_line + "'");
                System.err.println("ERROR: waiting_line='" + line + "'");
                error_cnt++;
            }

            // Have everything we need from this thread stack
            // that matches the WAITING_PATTERN.
            have_header_line = false;
            have_thread_state_line = false;
            return true;
        }

        return false;
    }

    static void doSamples() throws Exception {
        for (int count = 0; count < n_samples; count++) {
            match_cnt = 0;
            // verbose mode or an error has a lot of output so add more space
            if (verbose || error_cnt > 0) System.out.println();
            System.out.println("Sample #" + count);

            // We don't use the ProcessTools, OutputBuffer or
            // OutputAnalyzer classes from the testlibrary because
            // we have a complicated multi-line parse to perform
            // on a narrow subset of the JSTACK output.
            //
            // - we only care about stack traces that match
            //   HEADER_PREFIX_PATTERN; only two should match
            // - we care about at most three lines from each stack trace
            // - if both stack traces match LOCKED_PATTERN, then that's
            //   a failure and we report it
            // - for a stack trace that matches LOCKED_PATTERN, we verify:
            //   - the header line matches HEADER_RUNNABLE_PATTERN
            //     or HEADER_WAITING_PATTERN
            //   - the thread state line matches THREAD_STATE_BLOCKED_PATTERN
            //     or THREAD_STATE_RUNNABLE_PATTERN
            //   - we report any mismatches as failures
            // - for a stack trace that matches WAITING_PATTERN, we verify:
            //   - the header line matches HEADER_WAITING_PATTERN
            //   - the thread state line matches THREAD_STATE_BLOCKED_PATTERN
            //   - we report any mismatches as failures
            // - the stack traces that match HEADER_PREFIX_PATTERN may
            //   not match either LOCKED_PATTERN or WAITING_PATTERN
            //   because we might observe the thread outside of
            //   monitor operations; this is not considered a failure
            //
            // When we do observe LOCKED_PATTERN or WAITING_PATTERN,
            // then we are checking the header and thread state patterns
            // that occurred earlier in the current stack trace that
            // matched HEADER_PREFIX_PATTERN. We don't use data from
            // stack traces that don't match HEADER_PREFIX_PATTERN and
            // we don't mix data between the two stack traces that do
            // match HEADER_PREFIX_PATTERN.
            //
            Process process = new ProcessBuilder(JSTACK, PID)
                .redirectErrorStream(true).start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(
                                        process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = null;

                // process the header line here
                if (!have_header_line) {
                    matcher = HEADER_PREFIX_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        if (verbose) {
                            System.out.println();
                            System.out.println("header='" + line + "'");
                        }
                        header_line = new String(line);
                        have_header_line = true;
                        continue;
                    }
                    continue;  // skip until have a header line
                }

                // process the thread state line here
                if (!have_thread_state_line) {
                    matcher = THREAD_STATE_PREFIX_PATTERN.matcher(line);
                    if (matcher.matches()) {
                        if (verbose) {
                            System.out.println("thread_state='" + line + "'");
                        }
                        thread_state_line = new String(line);
                        have_thread_state_line = true;
                        continue;
                    }
                    continue;  // skip until we have a thread state line
                }

                // process the locked line here if we find one
                if (checkLockedLine(line)) {
                    continue;
                }

                // process the waiting line here if we find one
                if (checkWaitingLine(line)) {
                    continue;
                }

                // process the blank line here if we find one
                if (checkBlankLine(line)) {
                    continue;
                }
            }
            process.waitFor();

           if (match_cnt == 2) {
               if (match_list[0].equals(match_list[1])) {
                   System.err.println();
                   System.err.println("ERROR: matching lock lines:");
                   System.err.println("ERROR: line[0]'" + match_list[0] + "'");
                   System.err.println("ERROR: line[1]'" + match_list[1] + "'");
                   error_cnt++;
               }
           }

            // slight delay between jstack launches
            Thread.sleep(500);
        }
    }

    // This helper relies on RuntimeMXBean.getName() returning a string
    // that looks like this: 5436@mt-haku
    //
    // The testlibrary has tryFindJvmPid(), but that uses a separate
    // process which is much more expensive for finding out your own PID.
    //
    static String getPid() {
        RuntimeMXBean runtimebean = ManagementFactory.getRuntimeMXBean();
        String vmname = runtimebean.getName();
        int i = vmname.indexOf('@');
        if (i != -1) {
            vmname = vmname.substring(0, i);
        }
        return vmname;
    }

    static void usage() {
        System.err.println("Usage: " +
            "java TestThreadDumpMonitorContention [-v] [n_samples]");
        System.exit(1);
    }
}
