/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8074292
 * @summary Reproduce the bb->is_reachable() assert with GetSetLocals call after async exception.
 * @library /vmTestbase
 *          /test/lib
 * @compile -g kill003a.java
 * @run driver
 *      nsk.jdb.kill.kill003.kill003
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

package nsk.jdb.kill.kill003;

import nsk.share.*;
import nsk.share.jdb.*;

import java.io.*;
import java.util.*;

public class kill003 extends JdbTest {

    public static void main (String argv[]) {
        debuggeeClass =  DEBUGGEE_CLASS;
        firstBreak = FIRST_BREAK;
        new kill003().runTest(argv);
    }

    static final String PACKAGE_NAME    = "nsk.jdb.kill.kill003";
    static final String TEST_CLASS      = PACKAGE_NAME + ".kill003";
    static final String DEBUGGEE_CLASS  = TEST_CLASS + "a";
    static final String FIRST_BREAK     = DEBUGGEE_CLASS + ".main";
    static final String MYTHREAD        = "MyThread";
    static final String DEBUGGEE_THREAD = PACKAGE_NAME + "." + MYTHREAD;
    static final String DEBUGGEE_EXCEPTION = DEBUGGEE_CLASS + ".exception";

    protected void runCases() {
        String[] reply;
        String[] threads;

        // At this point we are at the breakpoint triggered by the firstBreak in main
        // after creating all the threads. Get the list of debuggee threads.
        threads = jdb.getThreadIdsByName("main");

        // Stopped at kill.main, so step into synchronized block
        reply = jdb.receiveReplyFor(JdbCommand.next);

        if (threads.length != 1) {
            log.complain("jdb should report " + 1 + " instance of " + DEBUGGEE_THREAD);
            log.complain("Found: " + threads.length);
            success = false;
        }

        // Execution is at a bytecode that is not expected to handle an async exception.  Throw one here
        // to make sure it gets handled without crashing.  The exception will be delivered at the next
        // bytecode that can handle the async exception.
        reply = jdb.receiveReplyForWithMessageWait(JdbCommand.kill + threads[0] + " " + DEBUGGEE_EXCEPTION,
                                                   "killed");

        // Continue the debuggee - the async exception will be delivered to the debuggee.
        reply = jdb.receiveReplyFor(JdbCommand.cont);

        // Ask the debuggee for its local variables at the bytecode where the async exception was delivered, which
        // should be reachable.
        reply = jdb.receiveReplyForWithMessageWait(JdbCommand.locals, "Local variables");

        if (jdb.terminated()) {
            throw new Failure("Debuggee exited");
        }

        // The lack of exception handler in the debuggee should cause it to exit when continued.
        jdb.contToExit(1);
    }
}
