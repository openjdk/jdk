/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary
 * VM Testbase keywords: [jpda, jdb]
 * VM Testbase readme:
 * DECSRIPTION
 * Tests the operation of the `repeat' commands, which print and change the status of GDB-style command repetition and
 * list auto-advance.  The particular behavior of `list' when repitition is on is tested in the `list' tests.
 * The test consists of two program:
 *   repeat001.java - launches jdb and debuggee and executes test cases
 *   repeat001a.java - the debugged application
 * COMMENTS
 *
 * @library /vmTestbase
 *          /test/lib
 * @build nsk.jdb.repeat.repeat001.repeat001a
 * @run main/othervm
 *      nsk.jdb.repeat.repeat001.repeat001
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -jdb=${test.jdk}/bin/jdb
 *      -jdb.option="-J-Duser.language=en -J-Duser.country=US"
 *      -java.options="${test.vm.opts} ${test.java.opts}"
 *      -workdir=.
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}"
 */

package nsk.jdb.repeat.repeat001;

import java.io.PrintStream;
import java.util.Arrays;

import nsk.share.jdb.JdbTest;
import nsk.share.jdb.JdbCommand;
import jdk.test.lib.Utils;


public class repeat001 extends JdbTest {
    static final String PACKAGE_NAME = "nsk.jdb.repeat.repeat001";
    static final String TEST_CLASS = PACKAGE_NAME + ".repeat001";
    static final String DEBUGGEE_CLASS = TEST_CLASS + "a";
    static final String FIRST_BREAK = DEBUGGEE_CLASS + ".main";

    public static void main(String[] args) {
        System.exit(run(args, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String[] args, PrintStream out) {
        debuggeeClass = DEBUGGEE_CLASS;
        firstBreak = FIRST_BREAK;
        return new repeat001().runTest(args, out);
    }

    protected static boolean isPrompt(String line) {
        return line.trim().equals("main[1]");
    }

    @Override
    protected void runCases() {
        try {
            runCasesNoCleanup();
        } finally {
            jdb.contToExit(1);
        }
    }

    protected void runCasesNoCleanup() {
        // Verify that repeat is off initially
        String[] reply = jdb.receiveReplyFor(JdbCommand.repeat);
        if (reply.length != 2 || !isPrompt(reply[1])) {
            failure("Unexpected output");
        }
        if (!reply[0].equals("Repeat is off")) {
            failure("Incorrect initial repeat setting");
        }

        // Verify that list auto-advance is disabled
        String[] firstList = jdb.receiveReplyFor(JdbCommand.list);
        String[] secondList = jdb.receiveReplyFor(JdbCommand.list);
        if (!Arrays.equals(firstList, secondList)) {
            failure("Listing inconsistent with repeat off");
        }

        // Verify that command repetition doesn't happen when disabled
        reply = jdb.receiveReplyFor("");
        if (reply.length != 1 || !isPrompt(reply[0])) {
            failure("Unexpected output");
        }

        reply = jdb.receiveReplyFor(JdbCommand.repeat + "on");
        if (reply.length != 1 || !isPrompt(reply[0])) {
            failure("Unexpected output");
        }

        // Verify that repeat is reported on
        reply = jdb.receiveReplyFor(JdbCommand.repeat);
        if (reply.length != 2 || !isPrompt(reply[1])) {
            failure("Unexpected output");
        }
        if (!reply[0].equals("Repeat is on")) {
            failure("Incorrect repeat status reported");
        }

        // Verify that non-repeatable commands still don't repeat
        if (jdb.receiveReplyFor(JdbCommand.print + "0").length != 2) {
            failure("Unexpected output");
        }
        if (jdb.receiveReplyFor("").length != 1) {
            failure("Unexpected output");
        }

        // Verify that repeated commands are repeatable
        // (`up' just prints `End of stack.' since we're stopped in `main')
        reply = jdb.receiveReplyFor("2 2 " + JdbCommand.up, true, 4);
        if (reply.length != 5 || !isPrompt(reply[4])) {
            failure("Unexpected output");
        }
        if (!Arrays.equals(reply, jdb.receiveReplyFor("", true, 4))) {
            failure("Repeated command didn't repeat correctly");
        }
    }
}
