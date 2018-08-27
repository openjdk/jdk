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

package lib.jdb;

import jdk.test.lib.process.OutputAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

public abstract class JdbTest {

    public JdbTest(Jdb.LaunchOptions jdbOptions) {
        this.jdbOptions= jdbOptions;
        debuggeeClass = jdbOptions.debuggeeClass;
    }
    public JdbTest(String debuggeeClass) {
        this(new Jdb.LaunchOptions(debuggeeClass));
    }

    private final Jdb.LaunchOptions jdbOptions;
    protected Jdb jdb;
    protected final String debuggeeClass;   // shortland for jdbOptions.debuggeeClass

    public void run() {
        try {
            setup();
            runCases();
        } finally {
            shutdown();
        }
    }

    protected void setup() {
        jdb = Jdb.launchLocal(jdbOptions);
        // wait while jdb is initialized
        jdb.waitForPrompt(1, false);

    }

    protected abstract void runCases();

    protected void shutdown() {
        if (jdb != null) {
            jdb.shutdown();
        }
    }

    protected static final String lineSeparator = System.getProperty("line.separator");


    // Parses the specified source file for "@{id} breakpoint" tags and returns
    // list of the line numbers containing the tag.
    // Example:
    //   System.out.println("BP is here");  // @1 breakpoint
    public static List<Integer> parseBreakpoints(String filePath, int id) {
        final String pattern = "@" + id + " breakpoint";
        int lineNum = 1;
        List<Integer> result = new LinkedList<>();
        try {
            for (String line: Files.readAllLines(Paths.get(filePath))) {
                if (line.contains(pattern)) {
                    result.add(lineNum);
                }
                lineNum++;
            }
        } catch (IOException ex) {
            throw new RuntimeException("failed to parse " + filePath, ex);
        }
        return result;
    }

    // sets breakpoints to the lines parsed by {@code parseBreakpoints}
    // returns number of the breakpoints set.
    public static int setBreakpoints(Jdb jdb, String debuggeeClass, String sourcePath, int id) {
        List<Integer> bps = parseBreakpoints(sourcePath, id);
        for (int bp : bps) {
            String reply = jdb.command(JdbCommand.stopAt(debuggeeClass, bp)).stream()
                    .collect(Collectors.joining("\n"));
            if (reply.contains("Unable to set")) {
                throw new RuntimeException("jdb failed to set breakpoint at " + debuggeeClass + ":" + bp);
            }

        }
        return bps.size();
    }

    protected int setBreakpoints(String debuggeeSourcePath, int id) {
        return setBreakpoints(jdb, debuggeeClass, debuggeeSourcePath, id);
    }

    protected OutputAnalyzer execCommand(JdbCommand cmd) {
        List<String> reply = jdb.command(cmd);
        return new OutputAnalyzer(reply.stream().collect(Collectors.joining(lineSeparator)));
    }
}
