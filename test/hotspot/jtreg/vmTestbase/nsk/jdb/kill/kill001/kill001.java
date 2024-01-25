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
 * @summary converted from VM Testbase nsk/jdb/kill/kill001.
 * VM Testbase keywords: [jpda, jdb]
 * VM Testbase readme:
 * DECSRIPTION
 * A positive test case for the 'kill <thread id> <expr>' command.
 * The debuggee program (kill001a.java) creates a number of additional
 * threads with name like "MyThread-<number>" and starts them. The jdb
 * suspends debuggee at moment when additional threads try to obtain
 * lock on synchronized object (previously locked in main thread)
 * and then tries to kill them. If these threads are killed then
 * the value of the special "killed" variable should be set
 * to the number of additional threads created. The value of
 * the "killed" variable is checked by "eval <expr>" command.
 * The test passes if the value is equal to the number of
 * additional threads created and fails otherwise.
 * COMMENTS
 *  Modified due to fix of the test bug:
 *  4940902 TEST_BUG: race in nsk/jdb/kill/kill001
 *  Modified due to fix of the bug:
 *  5024081 TEST_BUG: nsk/jdb/kill/kill001 is slow
 *
 * @library /vmTestbase
 *          /test/lib
 * @build nsk.jdb.kill.kill001.kill001a
 * @run main/othervm
 *      nsk.jdb.kill.kill001.kill001
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -verbose
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -jdb=${test.jdk}/bin/jdb
 *      -java.options="${test.vm.opts} ${test.java.opts}"
 *      -workdir=.
 *      -jdb.option="-trackallthreads"
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}"
 */

package nsk.jdb.kill.kill001;

import nsk.share.*;
import nsk.share.jdb.*;

import java.io.*;
import java.util.*;

public class kill001 extends JdbTest {

    public static void main (String argv[]) {
        System.exit(run(argv, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        debuggeeClass =  DEBUGGEE_CLASS;
        firstBreak = FIRST_BREAK;
        return new kill001().runTest(argv, out);
    }

    static final String PACKAGE_NAME    = "nsk.jdb.kill.kill001";
    static final String TEST_CLASS      = PACKAGE_NAME + ".kill001";
    static final String DEBUGGEE_CLASS  = TEST_CLASS + "a";
    static final String FIRST_BREAK     = DEBUGGEE_CLASS + ".main";
    static final String LAST_BREAK      = DEBUGGEE_CLASS + ".breakHere";
    static final String MYTHREAD        = "MyThread";
    static final String DEBUGGEE_THREAD = PACKAGE_NAME + "." + MYTHREAD;
    static final String DEBUGGEE_RESULT = DEBUGGEE_CLASS + ".killed";
    static final String DEBUGGEE_EXCEPTIONS = DEBUGGEE_CLASS + ".exceptions";

    static int numThreads = nsk.jdb.kill.kill001.kill001a.numThreads;

    protected void runCases() {
        String[] reply;
        String[] threads;

        jdb.setBreakpointInMethod(LAST_BREAK);
        reply = jdb.receiveReplyFor(JdbCommand.cont);

        // Issue a jdb "catch all" command for each exception type that will be thrown.
        // This will force jdb to stop when the exception is thrown.
        for (int i = 0; i < kill001a.exceptions.length; i++) {
            String className = kill001a.exceptions[i].getClass().getName();
            reply = jdb.receiveReplyFor(JdbCommand._catch + "all " + className);
        }

        // At this point we are at the breakpoint triggered by the breakHere() call done
        // after creating all the threads. Get the list of debuggee threads.
        threads = jdb.getThreadIdsByName(MYTHREAD);

        if (threads.length != numThreads) {
            log.complain("jdb should report " + numThreads + " instance of " + DEBUGGEE_THREAD);
            log.complain("Found: " + threads.length);
            success = false;
        }

        // Kill each debuggee thread. This will cause each thread to stop in the debugger,
        // indicating that an exception was thrown.
        for (int i = 0; i < threads.length; i++) {
            reply = jdb.receiveReplyForWithMessageWait(JdbCommand.kill + threads[i] + " " +
                                                       DEBUGGEE_EXCEPTIONS + "[" + i + "]",
                                                       "killed");
        }

        // Continue the main thread, which is still at the breakpoint. This will resume
        // all the debuggee threads, allowing the kill to take place.
        reply = jdb.receiveReplyFor(JdbCommand.cont);

        // Continue each of the threads that received the "kill" exception.
        for (int i = 0; i < numThreads; i++) {
            reply = jdb.receiveReplyFor(JdbCommand.cont);
        }

        // make sure the debugger is at a breakpoint
        if (!jdb.isAtBreakpoint(reply, LAST_BREAK)) {
            log.display("Expected breakpoint has not been hit yet");
            jdb.waitForMessage(0, LAST_BREAK);
        }
        log.display("Breakpoint has been hit");

        reply = jdb.receiveReplyForWithMessageWait(JdbCommand.eval + DEBUGGEE_RESULT,
                                                   DEBUGGEE_RESULT + " =");
        Paragrep grep = new Paragrep(reply);
        String killed = grep.findFirst(DEBUGGEE_RESULT + " =" );
        if (killed.length() > 0) {
            if (killed.indexOf(DEBUGGEE_RESULT + " = " + numThreads) < 0) {
                log.complain("Only " + killed + " out of " + numThreads + " " + MYTHREAD + "s were killed.");
                success = false;
            }
        } else {
            log.complain("Value for " + DEBUGGEE_RESULT + " is not found.");
            success = false;
        }

        reply = jdb.receiveReplyFor(JdbCommand.threads);
        jdb.contToExit(1);
    }
}
