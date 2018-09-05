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

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class JdbTest {

    public static class LaunchOptions {
        public final String debuggeeClass;
        public final List<String> debuggeeOptions = new LinkedList<>();

        public LaunchOptions(String debuggeeClass) {
            this.debuggeeClass = debuggeeClass;
        }
        public LaunchOptions addDebuggeeOption(String option) {
            debuggeeOptions.add(option);
            return this;
        }
        public LaunchOptions addDebuggeeOptions(String[] options) {
            debuggeeOptions.addAll(Arrays.asList(options));
            return this;
        }
    }

    public JdbTest(LaunchOptions launchOptions) {
        this.launchOptions= launchOptions;
        debuggeeClass = launchOptions.debuggeeClass;
    }
    public JdbTest(String debuggeeClass) {
        this(new LaunchOptions(debuggeeClass));
    }

    protected Jdb jdb;
    protected Process debuggee;
    private final List<String> debuggeeOutput = new LinkedList<>();
    private final LaunchOptions launchOptions;
    protected final String debuggeeClass;   // shortland for launchOptions.debuggeeClass

    // returns the whole jdb output as a string
    public String getJdbOutput() {
        return jdb == null ? "" : jdb.getJdbOutput();
    }

    // returns the whole debuggee output as a string
    public String getDebuggeeOutput() {
        return debuggeeOutput.stream().collect(Collectors.joining(lineSeparator));
    }

    public void run() {
        try {
            setup();
            runCases();
        } finally {
            shutdown();
        }
    }

    protected void setup() {
        /* run debuggee as:
            java -agentlib:jdwp=transport=dt_socket,address=0,server=n,suspend=y <debuggeeClass>
        it reports something like : Listening for transport dt_socket at address: 60810
        after that connect jdb by:
            jdb -connect com.sun.jdi.SocketAttach:port=60810
        */
        // launch debuggee
        List<String> debuggeeArgs = new LinkedList<>();
        // specify address=0 to automatically select free port
        debuggeeArgs.add("-agentlib:jdwp=transport=dt_socket,address=0,server=y,suspend=y");
        debuggeeArgs.addAll(launchOptions.debuggeeOptions);
        debuggeeArgs.add(launchOptions.debuggeeClass);
        ProcessBuilder pbDebuggee = ProcessTools.createJavaProcessBuilder(true, debuggeeArgs.toArray(new String[0]));

        // debuggeeListen[0] - transport, debuggeeListen[1] - address
        String[] debuggeeListen = new String[2];
        Pattern listenRegexp = Pattern.compile("Listening for transport \\b(.+)\\b at address: \\b(\\d+)\\b");
        try {
            debuggee = ProcessTools.startProcess("debuggee", pbDebuggee,
                    s -> debuggeeOutput.add(s),  // output consumer
                    s -> {  // warm-up predicate
                        Matcher m = listenRegexp.matcher(s);
                        if (!m.matches()) {
                            return false;
                        }
                        debuggeeListen[0] = m.group(1);
                        debuggeeListen[1] = m.group(2);
                        return true;
                    },
                    30, TimeUnit.SECONDS);
        } catch (IOException | InterruptedException | TimeoutException ex) {
            throw new RuntimeException("failed to launch debuggee", ex);
        }

        // launch jdb
        try {
            jdb = new Jdb("-connect", "com.sun.jdi.SocketAttach:port=" + debuggeeListen[1]);
        } catch (Throwable ex) {
            // terminate debuggee if something went wrong
            debuggee.destroy();
            throw ex;
        }
        // wait while jdb is initialized
        jdb.waitForPrompt(1, false);
    }

    protected abstract void runCases();

    protected void shutdown() {
        if (jdb != null) {
            jdb.shutdown();
        }
        // shutdown debuggee
        if (debuggee != null && debuggee.isAlive()) {
            try {
                debuggee.waitFor(Utils.adjustTimeout(10), TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // ignore
            } finally {
                if (debuggee.isAlive()) {
                    debuggee.destroy();
                }
            }
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

    // sets breakpoints to the lines parsed by {@code parseBreakpoints}
    // from the file from test source directory.
    // returns number of the breakpoints set.
    protected int setBreakpointsFromTestSource(String debuggeeFileName, int id) {
        return setBreakpoints(jdb, debuggeeClass, System.getProperty("test.src") + "/" + debuggeeFileName, id);
    }

    protected OutputAnalyzer execCommand(JdbCommand cmd) {
        List<String> reply = jdb.command(cmd);
        return new OutputAnalyzer(reply.stream().collect(Collectors.joining(lineSeparator)));
    }

    // helpers for "eval" jdb command.
    // executes "eval <expr>" and verifies output contains the specified text
    protected void evalShouldContain(String expr, String expectedString) {
        execCommand(JdbCommand.eval(expr))
                .shouldContain(expectedString);
    }
    // executes "eval <expr>" and verifies output does not contain the specified text
    protected void evalShouldNotContain(String expr, String unexpectedString) {
        execCommand(JdbCommand.eval(expr))
                .shouldNotContain(unexpectedString);
    }
}
