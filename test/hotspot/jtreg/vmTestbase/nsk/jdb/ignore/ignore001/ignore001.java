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
 * @summary converted from VM Testbase nsk/jdb/ignore/ignore001.
 * VM Testbase keywords: [jpda, jdb]
 * VM Testbase readme:
 * DECSRIPTION
 * A positive test case for the 'ignore <class_id>' command.
 * The tests sets catch hooks for two different exception
 * which are thrown in the debuggee. Then these hooks are
 * removed by 'ignore' command. The test checks the jdb does
 * not halt execution on removed catch hooks.
 * COMMENTS
 *  The test was updated due fix of the bug 4683795.
 *
 * @library /vmTestbase
 *          /test/lib
 * @build nsk.jdb.ignore.ignore001.ignore001a
 * @run main/othervm
 *      nsk.jdb.ignore.ignore001.ignore001
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -jdb=${test.jdk}/bin/jdb
 *      -java.options="${test.vm.opts} ${test.java.opts}"
 *      -workdir=.
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}"
 */

package nsk.jdb.ignore.ignore001;

import nsk.share.Paragrep;
import nsk.share.jdb.JdbCommand;
import nsk.share.jdb.JdbTest;

import java.io.PrintStream;
import java.util.Vector;

public class ignore001 extends JdbTest {

    public static void main(String[] argv) {
        System.exit(run(argv, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String[] argv, PrintStream out) {
        debuggeeClass = DEBUGGEE_CLASS;
        firstBreak = FIRST_BREAK;
        lastBreak = LAST_BREAK;
        return new ignore001().runTest(argv, out);
    }

    static final String PACKAGE_NAME = "nsk.jdb.ignore.ignore001";
    static final String TEST_CLASS = PACKAGE_NAME + ".ignore001";
    static final String DEBUGGEE_CLASS = TEST_CLASS + "a";
    static final String EXCEPTION_SAMPLE = "Exception occurred:";
    static final String REMOVED_SAMPLE = "Removed:";
    static final String FIRST_BREAK = DEBUGGEE_CLASS + ".main";
    static final String LAST_BREAK = DEBUGGEE_CLASS + ".lastBreak";

    protected void runCases() {
//        jdb.setBreakpointInMethod(LAST_BREAK);

        log.display("Setting catch for: " + nsk.jdb.ignore.ignore001.ignore001a.JAVA_EXCEPTION);
        jdb.receiveReplyFor(JdbCommand._catch + " caught " + nsk.jdb.ignore.ignore001.ignore001a.JAVA_EXCEPTION);

        log.display("Setting catch for: " + nsk.jdb.ignore.ignore001.ignore001a.USER_EXCEPTION1);
        jdb.receiveReplyFor(JdbCommand._catch + " caught " + nsk.jdb.ignore.ignore001.ignore001a.USER_EXCEPTION1);

        log.display("Setting catch for: " + nsk.jdb.ignore.ignore001.ignore001a.USER_EXCEPTION2);
        jdb.receiveReplyFor(JdbCommand._catch + " caught " + nsk.jdb.ignore.ignore001.ignore001a.USER_EXCEPTION2);

        while (true) {
            if (!checkCatch(ignore001a.JAVA_EXCEPTION)) {
                success = false;
            }
            if (!checkCatch(ignore001a.USER_EXCEPTION1)) {
                success = false;
            }
            if (!checkCatch(ignore001a.USER_EXCEPTION2)) {
                success = false;
            }

            if (!checkIgnore(ignore001a.JAVA_EXCEPTION)) {
                success = false;
            }
            if (!checkIgnore(ignore001a.USER_EXCEPTION1)) {
                success = false;
            }
            if (!checkIgnore(ignore001a.USER_EXCEPTION2)) {
                success = false;
            }

            jdb.contToExit(6);

            String[] reply = jdb.getTotalReply();
            var grep = new Paragrep(reply);
            int count = grep.find(EXCEPTION_SAMPLE);
            if (count != 3) {
                success = false;
                log.complain("Should report 3 catched exceptions.");
                log.complain("Reported catched exception count is " + count);
            }
            break;
        }
    }

    private boolean checkCatch(String exceptionName) {
        boolean result = true;

//        log.display("Resuming debuggee.");
        String[] reply = jdb.receiveReplyFor(JdbCommand.cont);

        var v = new Vector<String>();
        v.add(EXCEPTION_SAMPLE);
        v.add(exceptionName);

        var grep = new Paragrep(reply);
        String found = grep.findFirst(v);
        if (found.length() == 0) {
            log.complain("Failed to catch " + exceptionName);
            result = false;
        }
        return result;
    }

    private boolean checkIgnore(String exceptionName) {
        boolean result = true;

        log.display("Unsetting catch for: " + exceptionName);
        String[] reply = jdb.receiveReplyFor(JdbCommand.ignore + " caught " + exceptionName);

        var v = new Vector<String>();
        v.add(REMOVED_SAMPLE);
        v.add(exceptionName);

        var grep = new Paragrep(reply);
        String found = grep.findFirst(v);
        if (found.length() == 0) {
            log.complain("Failed to remove catch for " + exceptionName);
            result = false;
        }
        return result;
    }
}
