/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary converted from VM Testbase nsk/jdb/stop_at/stop_at002.
 * VM Testbase keywords: [jpda, jdb]
 * VM Testbase readme:
 * DESCRIPTION
 *    Regression test for:
 *    Bug ID: 4299394
 *    Synopsis: TTY: Deferred breakpoints can't be set on inner classes
 * COMMENTS
 *
 * @library /vmTestbase
 *          /test/lib
 * @build nsk.jdb.stop_at.stop_at002.stop_at002a
 * @run main/othervm
 *      nsk.jdb.stop_at.stop_at002.stop_at002
 *      -arch=${os.family}-${os.simpleArch}
 *      -waittime=5
 *      -debugee.vmkind=java
 *      -transport.address=dynamic
 *      -jdb=${test.jdk}/bin/jdb
 *      -java.options="${test.vm.opts} ${test.java.opts}"
 *      -workdir=.
 *      -debugee.vmkeys="${test.vm.opts} ${test.java.opts}"
 */

package nsk.jdb.stop_at.stop_at002;

import nsk.share.*;
import nsk.share.jdb.*;

import java.io.*;
import java.util.*;

/*
 * Regression test for:
 * Bug ID: 4299394
 * Synopsis: TTY: Deferred breakpoints can't be set on inner classes
 *
 */

public class stop_at002 extends JdbTest {

    public static void main (String argv[]) {
        System.exit(run(argv, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        debuggeeClass =  DEBUGGEE_CLASS;
        firstBreak = FIRST_BREAK;
        lastBreak = LAST_BREAK;
        return new stop_at002().runTest(argv, out);
    }

    static final String PACKAGE_NAME = "nsk.jdb.stop_at.stop_at002";
    static final String TEST_CLASS = PACKAGE_NAME + ".stop_at002";
    static final String DEBUGGEE_CLASS = TEST_CLASS + "a";
    static final String FIRST_BREAK        = DEBUGGEE_CLASS + ".main";
    static final String LAST_BREAK         = DEBUGGEE_CLASS + ".lastBreak";
    static final String DEBUGGEE_LOCATION1 = DEBUGGEE_CLASS + "$Nested$DeeperNested$DeepestNested:64";
    static final String DEBUGGEE_LOCATION2 = DEBUGGEE_CLASS + "$Inner$MoreInner:78";

    protected void runCases() {
        if (!checkStop(DEBUGGEE_LOCATION1)) {
            success = false;
        }

        if (!checkStop(DEBUGGEE_LOCATION2)) {
            success = false;
        }

        if (!checkBreakpointHit(DEBUGGEE_LOCATION1)) {
            success = false;
        }

        if (!checkBreakpointHit(DEBUGGEE_LOCATION2)) {
            success = false;
        }

        jdb.contToExit(1);
    }

    private boolean checkStop(String location) {
        Paragrep grep;
        String[] reply;
        String found;

        log.display("Trying to set breakpoint at line: " + location);
        reply = jdb.receiveReplyFor(JdbCommand.stop_at + location);

        grep = new Paragrep(reply);
        found = grep.findFirst("Deferring breakpoint " + location);
        if (found.length() == 0) {
            log.complain("jdb failed to setup deferred breakpoint at line: " + location);
            return false;
        }

        return true;
    }

    private boolean checkBreakpointHit(String location) {
        Paragrep grep;
        String[] reply;
        String found;

        log.display("continuing to breakpoint at line: " + location);
        reply = jdb.receiveReplyFor(JdbCommand.cont);
        grep = new Paragrep(reply);

        found = grep.findFirst("Unable to set deferred breakpoint");
        if (found.length() > 0) {
            log.complain("jdb failed to set deferred breakpoint at line: " + location);
            return false;
        }

        found = grep.findFirst("Set deferred breakpoint " + location);
        if (found.length() == 0) {
            log.complain("jdb failed to set deferred breakpoint at line: " + location);
            return false;
        }

        found = grep.findFirst("Breakpoint hit: \"thread=main\", ");
        if (found.length() == 0) {
            log.complain("jdb failed to hit breakpoint at line: " + location);
            return false;
        }

        return true;
    }
}
