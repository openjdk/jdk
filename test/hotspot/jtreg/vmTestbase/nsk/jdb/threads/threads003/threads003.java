/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 * @summary converted from VM Testbase nsk/jdb/threads/threads003.
 * VM Testbase keywords: [jpda, jdb]
 * VM Testbase readme:
 * DECSRIPTION
 *  This is a test for jdb 'threads' command in conjunction with jdb's
 *  ability to start tracking a vthread when the first event arrives on it.
 *  It also tests that vthreads where no event is received are not tracked.
 *  The debugee starts 5 'MyThreads'. The odd numbered threads call a
 *  method that has been setup as a breakpoint. The others do not. All
 *  5 are then suspended on a lock that the main thread posseses. The
 *  'threads' command is issued at this point. The test passes if
 *  only 'MyThreads-1' and 'MyThreads-3' are reported.
 *
 * @library /vmTestbase
 *          /test/lib
 * @build nsk.jdb.threads.threads003.threads003a
 * @run main/othervm
 *      nsk.jdb.threads.threads003.threads003
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -verbose
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -jdb=${test.jdk}/bin/jdb
 *      -java.options="${test.vm.opts} ${test.java.opts}"
 *      -workdir=.
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}"
 */

package nsk.jdb.threads.threads003;

import nsk.share.*;
import nsk.share.jdb.*;

import java.io.*;
import java.util.*;

public class threads003 extends JdbTest {

    public static void main (String argv[]) {
        System.exit(run(argv, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        debuggeeClass =  DEBUGGEE_CLASS;
        firstBreak = FIRST_BREAK;
        lastBreak = LAST_BREAK;
        return new threads003().runTest(argv, out);
    }

    static final String PACKAGE_NAME     = "nsk.jdb.threads.threads003";
    static final String TEST_CLASS       = PACKAGE_NAME + ".threads003";
    static final String DEBUGGEE_CLASS   = TEST_CLASS + "a";
    static final String FIRST_BREAK      = DEBUGGEE_CLASS + ".main";
    static final String LAST_BREAK       = DEBUGGEE_CLASS + ".breakpoint";
    static final String THREAD_NAME      = "MyThread";
    static final int    NUM_THREADS      = 5;
    static final int    KNOWN_THREADS    = NUM_THREADS / 2; // only odd nubmered threads will be known threads

    protected void runCases() {
        String[] reply;
        Paragrep grep;
        int count;
        Vector v;
        String[] threads;
        boolean vthreadMode = "Virtual".equals(System.getProperty("test.thread.factory"));

        if (!vthreadMode) {
            // This test is only meant to be run in vthread mode.
            log.display("Test not run in vthread mode. Exiting early.");
            jdb.contToExit(1);
            return;
        }

        jdb.setBreakpointInMethod(LAST_BREAK);
        jdb.receiveReplyFor(JdbCommand.cont); // This is to get the test going
        jdb.receiveReplyFor(JdbCommand.cont); // This is to continue after MYTHREAD-1 breakpoint
        jdb.receiveReplyFor(JdbCommand.cont); // This is to continue after MYTHREAD-3 breakpoint

        // At this point we are at the breakpoint done,after creating all the threads.
        // Get the list of debuggee threads that jdb knows about
        threads = jdb.getThreadIdsByName(THREAD_NAME);

        // There were NUM_THREADS threads created, but only KNOWN_THREADS hit the breakpoint,
        // so these should be the only threads found.
        if (threads.length != KNOWN_THREADS) {
            failure("Unexpected number of " + THREAD_NAME + " was listed: " + threads.length +
                    "\n\texpected value: " + KNOWN_THREADS);
        }

        // Now make sure the correct threads were reported.
        for (int i = 0; i < threads.length; i++) {
            // Switch to the thread. The reply should be the new prompt with the thread's name.
            reply = jdb.receiveReplyFor(JdbCommand.thread + " " + threads[i]);
            String prompt = THREAD_NAME + "-" + (i * 2 + 1) + "[1] ";
            if (!reply[0].equals(prompt)) {
                failure("Expect to find prompt \"" + prompt + "\" but found \"" + reply[0] + "\"");
            }
        }

        jdb.contToExit(1);
    }
}
