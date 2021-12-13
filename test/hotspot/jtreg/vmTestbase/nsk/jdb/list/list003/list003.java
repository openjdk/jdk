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
 * This tests the GDB-style auto-advance feature of `list', which is enabled and disabled through the `repeat' command.
 * The test consists of two program:
 *   list003.java - launches jdb and debuggee and executes test cases
 *   list003a.java - the debugged application
 * COMMENTS
 *
 * @library /vmTestbase
 *          /test/lib
 * @build nsk.jdb.list.list003.list003a
 * @run main/othervm
 *      nsk.jdb.list.list003.list003
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

package nsk.jdb.list.list003;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static java.util.stream.Collectors.toList;

import nsk.share.jdb.JdbTest;
import nsk.share.jdb.JdbCommand;


public class list003 extends JdbTest {
    static final String PACKAGE_NAME = "nsk.jdb.list.list003";
    static final String TEST_CLASS = PACKAGE_NAME + ".list003";
    static final String DEBUGGEE_CLASS = TEST_CLASS + "a";
    static final String FIRST_BREAK = DEBUGGEE_CLASS + ".main";

    /**
     * Represents a line output by the {@code list} command.
     */
    protected static record ListLine(int number, boolean active) {
        public static ListLine parse(String line) {
            String[] tokens = line.split("\\s+");
            return new ListLine(
                Integer.parseInt(tokens[0]),
                tokens.length >= 2 && tokens[1].equals("=>")
            );
        }
    }

    protected static boolean isPrompt(String line) {
        return line.trim().equals("main[1]");
    }

    protected List<ListLine> parseListOutput(String[] lines) {
        List<String> lineList = new ArrayList<>(Arrays.asList(lines));
        if (!isPrompt(lineList.remove(lineList.size() - 1))) {
            failure("Expected trailing prompt");
            return null;
        } else if (lineList.size() == 1 && lineList.get(0).equals("EOF")) {
            return new ArrayList<>();
        } else {
            return lineList.stream().map(ListLine::parse).collect(toList());
        }
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out) + JCK_STATUS_BASE);
    }

    public static int run(String[] args, PrintStream out) {
        debuggeeClass = DEBUGGEE_CLASS;
        firstBreak = FIRST_BREAK;
        return new list003().runTest(args, out);
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
        if (jdb.receiveReplyFor(JdbCommand.repeat + "on").length != 1) {
            failure("Missing or unexpected output");
        }

        List<ListLine> autoList = parseListOutput(jdb.receiveReplyFor(JdbCommand.list));
        int lineNo = autoList.stream().filter(ListLine::active).findFirst().get().number();
        List<ListLine> manualList = parseListOutput(jdb.receiveReplyFor(JdbCommand.list + (lineNo - 1)));
        if (manualList.stream().filter(ListLine::active).findFirst().get().number() != lineNo) {
            failure("Manual listing didn't mark the active source line");
        }

        // Verify that we can correctly list by auto-advance all the way to EOF
        List<ListLine> prevList = manualList;
        int reps;
        for (reps = 0; !prevList.isEmpty(); reps += 1) {
            // Exercise both explicit `list' and auto-repeat
            var command = reps % 2 == 0 ? JdbCommand.list : "";

            List<ListLine> currList = parseListOutput(jdb.receiveReplyFor(command));
            if (currList.equals(prevList)) {
                // This guards against infinite looping
                failure("Consecutive listings were identical");
            }
            int prevEnd = prevList.get(prevList.size() - 1).number();
            if (!currList.isEmpty() && currList.get(0).number() != prevEnd + 1) {
                failure("Consecutive listings weren't for consecutive source chunks");
            }
            prevList = currList;
        }
        if (reps < 2) {
            failure("Didn't get enough consecutive list reps");
        }

        String[] lines = jdb.receiveReplyFor(JdbCommand.up);
        if (!lines[0].equals("End of stack.") || !isPrompt(lines[1])) {
            failure("Unexpected output from `up'");
        }
        List<ListLine> resetList = parseListOutput(jdb.receiveReplyFor(JdbCommand.list));
        if (!resetList.stream().anyMatch(ListLine::active)) {
            failure("List target didn't reset to active line");
        }

        List<ListLine> listing = parseListOutput(jdb.receiveReplyFor(JdbCommand.list + "1"));
        if (!listing.stream().anyMatch(l -> l.number() == 1)) {
            failure("Manual listing displayed the wrong lines");
        }

        List<ListLine> targetedList = parseListOutput(jdb.receiveReplyFor(JdbCommand.list + "1"));
        autoList = parseListOutput(jdb.receiveReplyFor(JdbCommand.list));
        if (autoList.get(0).number() != targetedList.get(targetedList.size() - 1).number() + 1) {
            failure("Auto-advance didn't work after targeted list");
        }
    }
}
