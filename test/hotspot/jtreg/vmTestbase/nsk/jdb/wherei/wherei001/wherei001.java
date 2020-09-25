/*
 * Copyright (c) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase nsk/jdb/wherei/wherei001.
 * VM Testbase keywords: [jpda, jdb]
 * VM Testbase readme:
 * DECSRIPTION
 * A positive test case for the 'wherei <thread id>' command.
 * The test checks if jdb correctly reports stack trace for
 * every checked thread id.
 * COMMENTS
 *
 * @library /vmTestbase
 *          /test/lib
 * @build nsk.jdb.wherei.wherei001.wherei001a
 * @run main/othervm
 *      nsk.jdb.wherei.wherei001.wherei001
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -jdb=${test.jdk}/bin/jdb
 *      -java.options="${test.vm.opts} ${test.java.opts}"
 *      -workdir=.
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}"
 */

package nsk.jdb.wherei.wherei001;

import nsk.share.Paragrep;
import nsk.share.jdb.JdbCommand;
import nsk.share.jdb.JdbTest;

import java.io.PrintStream;

public class wherei001 extends JdbTest {

    public static void main(String[] argv) {
        System.exit(run(argv, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String[] argv, PrintStream out) {
        debuggeeClass = DEBUGGEE_CLASS;
        firstBreak = FIRST_BREAK;
        lastBreak = LAST_BREAK;
        return new wherei001().runTest(argv, out);
    }

    static final String PACKAGE_NAME = "nsk.jdb.wherei.wherei001";
    static final String TEST_CLASS = PACKAGE_NAME + ".wherei001";
    static final String DEBUGGEE_CLASS = TEST_CLASS + "a";
    static final String FIRST_BREAK = DEBUGGEE_CLASS + ".main";
    static final String LAST_BREAK = DEBUGGEE_CLASS + ".lastBreak";
    static final String DEBUGGEE_THREAD = PACKAGE_NAME + ".MyThread";

    protected void runCases() {
        jdb.setBreakpointInMethod(LAST_BREAK);
        jdb.receiveReplyFor(JdbCommand.cont);

        String[] threads = jdb.getThreadIds(DEBUGGEE_THREAD);

        if (threads.length != 5) {
            log.complain("jdb should report 5 instance of " + DEBUGGEE_THREAD);
            log.complain("Found: " + threads.length);
            success = false;
        }

        for (String thread : threads) {
            if (!checkStack(thread)) {
                success = false;
            }
        }

        jdb.contToExit(1);
    }

    private boolean checkStack(String threadId) {
        boolean result = true;
        String[] func = {"func5", "func4", "func3", "func2", "func1", "run"};
        String[] reply = jdb.receiveReplyFor(JdbCommand.wherei + threadId);

        var grep = new Paragrep(reply);
        for (String s : func) {
            int count = grep.find(DEBUGGEE_THREAD + "." + s);
            if (count != 1) {
                log.complain("Contents of stack trace is incorrect for thread " + threadId);
                log.complain("Searched for: " + DEBUGGEE_THREAD + "." + s);
                log.complain("Count : " + count);
                result = false;
            }
        }
        return result;
    }
}
