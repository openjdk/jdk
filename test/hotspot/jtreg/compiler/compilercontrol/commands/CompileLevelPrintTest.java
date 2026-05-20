/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @bug 8313713
 * @summary Test -XX:CompileCommand=exclude and compileonly with different compilation levels,
 *          monitoring compilation events in VM -XX:+PrintCompilation and -XX:+PrintTieredEvents output
 * @requires vm.compMode != "Xint" & vm.flavor == "server"
 *          & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 *          & (vm.opt.CompilationMode == "normal" | vm.opt.CompilationMode == null)
 * @library /test/lib
 * @run main ${test.main.class} runner
 */

package compiler.compilercontrol.commands;

import jdk.test.lib.Asserts;
import jdk.test.lib.management.InputArguments;
import jdk.test.lib.process.ProcessTools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileLevelPrintTest {
    static final Method TEST_METHOD;

    static {
        try {
            TEST_METHOD = Testee.class.getDeclaredMethod("compiledMethod", new Class[] {int.class});
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    static final String TEST_METHOD_NAME_DOT = TEST_METHOD.getDeclaringClass().getName().replace('.', '/')
            + "." + TEST_METHOD.getName();
    static final String TEST_METHOD_NAME_DBL_COLON = TEST_METHOD.getDeclaringClass().getName()
            + "::" + TEST_METHOD.getName();
    static final String TEST_METHOD_SIGNATURE = TEST_METHOD_NAME_DBL_COLON + "(";

    static final String TESTEE_WAITING_FOR_START_CMD = "==> waiting for start command";

    static final String START_CMD = "start";
    static final String STOP_CMD = "stop";

    static final boolean DEBUG_OUTPUT = false;

    static int TIMEOUT_SEC = 30;

    static class TesteeState {
        final CountDownLatch waitingForStartTest = new CountDownLatch(1);
        final AtomicInteger compiler1QueueSize = new AtomicInteger();
        final AtomicInteger compiler2QueueSize = new AtomicInteger(0);
        final Set<String> compileCommandsReported = Collections.synchronizedSet(new HashSet<>());
        volatile Set<String> testMethodCompiledAtLevel = Collections.synchronizedSet(new HashSet<>());
        final Set<String> testMethodExcludedAtLevel = Collections.synchronizedSet(new HashSet<>());
        volatile Set<String> testMethodPrintedAtLevel = Collections.synchronizedSet(new HashSet<>());
        volatile boolean testMethodMDOPrinted = false;

        @Override
        public String toString() {
            return "TesteeState{" +
                    "\n  compileCommandsReported=" + compileCommandsReported +
                    "\n  testMethodCompiledAtLevel=" + testMethodCompiledAtLevel +
                    "\n  testMethodExcludedAtLevel=" + testMethodExcludedAtLevel +
                    "\n  testMethodPrintedAtLevel=" + testMethodPrintedAtLevel +
                    "\n  testMethodMDOPrinted=" + testMethodMDOPrinted +
                    "\n}";
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        // Use the same launcher to avoid double launch cost compared to multiple jtreg @test annotations
        if (args.length > 0 && "runner".equals(args[0])) {
            if (Arrays.asList(InputArguments.getVmInputArgs()).contains("-Xcomp")) {
                TIMEOUT_SEC *= 3;
            }

            if (Arrays.asList(InputArguments.getVmInputArgs()).contains("-XX:-TieredCompilation")) {
                // If we have -XX:-TieredCompilation, we check only for C2 compilation
                // A space is printed instead of compile level
                Runner.run("compileonly",    1,    1, 1, Set.of(), Set.of("4"), true, false);
                Runner.run("compileonly",   10,    1, 1, Set.of(), Set.of("4"), true, false);
                Runner.run("compileonly",  100,    1, 1, Set.of(), Set.of("4"), true, false);
                Runner.run("compileonly", 1000, 1000, 4, Set.of(" "), Set.of(), true, false);
                Runner.run("compileonly", 1100, 1100, 4, Set.of(" "), Set.of(), true, false);

                Runner.run("exclude", 1110,    1, 1, Set.of(), Set.of("4"), true, false);
                Runner.run("exclude", 1101,   10, 2, Set.of(), Set.of("4"), true, false);
                Runner.run("exclude", 1011,  100, 3, Set.of(), Set.of("4"), true, false);
                Runner.run("exclude",  111, 1000, 4, Set.of(" "), Set.of(), true, false);
            } else {
                // -XX:+TieredCompilation
                Runner.run("compileonly",    1,    1, 1, Set.of("1"), Set.of(), false, true);
                Runner.run("compileonly",   10,   10, 2, Set.of("2"), Set.of(), true, true);
                Runner.run("compileonly",  100,  100, 3, Set.of("3"), Set.of(), true, true);
                Runner.run("compileonly", 1000, 1000, 4, Set.of("4"), Set.of("3"), true, true);
                Runner.run("compileonly", 1100, 1100, 4, Set.of("3", "4"), Set.of(), true, true);

                Runner.run("exclude", 1110,    1, 1, Set.of("1"), Set.of(), false, true);
                Runner.run("exclude", 1101,   10, 2, Set.of("2"), Set.of(), true, true);
                Runner.run("exclude", 1011,  100, 3, Set.of("3"), Set.of(), true, true);
                Runner.run("exclude",  111, 1000, 4, Set.of("4"), Set.of("3"), true, true);
            }
        } else if (args.length > 1 && "parse-logs".equals(args[0])) {
            // For test troubleshooting: if test has failed due to regexp matching,
            // try parsing testee-<pid>.out and hotspot_pid<pid>.log and adjust the patterns
            TesteeState testeeState = new TesteeState();
            for (int i = 1; i < args.length; i++) {
                Runner.matchMessagesInHotspotLog(args[i], testeeState);
            }
            IO.println(testeeState.toString());
        } else {
            Testee.run();
        }
    }

    static class Runner {
        private static final int LAST_N_LINES_COUNT = 5;

        private static final Pattern reCompileCommand = Pattern.compile(
                "CompileCommand: (.*)");
        private static final Pattern reTieredEvent = Pattern.compile(
                "[0-9.]+: \\[(call|loop|compile|force-compile|remove-from-queue|update-in-queue|reprofile|make-not-entrant) "
                      + "level=\\d \\[([^]]+)] @-?\\d+ queues=(\\d+),(\\d+).*]");
        private static final Pattern reCompilation = Pattern.compile(
                "(\\d+) (C1|C2|no compiler): *(\\d+) ([ %][ s][ !][ b][ n]) ([-0-4 ]) +([^ ]+).*");
        private static final Pattern reExcludeCompile = Pattern.compile(
                ".*made not compilable on level (\\d) +([^ ]+) .* excluded by CompileCommand");
        private static final Pattern reCompiledMethod = Pattern.compile(
                ".*-{35} Assembly -{35}\\n(?:\\[[0-9.]+s]\\[warning]\\[os] Loading hsdis library failed\\n)?\\nCompiled method \\((?:c1|c2)\\) (\\d+) ([Cc][12]): *"
                      + "(\\d+) ([ %][ s][ !][ b][ n]) ([-0-4 ]) +([^ ]+) +(@ -?[0-9]+ +)?\\(\\d+ bytes\\)", Pattern.DOTALL);
        private static final Pattern reMethodData = Pattern.compile(
                ".*-{72}\\nstatic ([^\\n]+)\\n *interpreter_invocation_count: *\\d+\\n *invocation_counter: *\\d+", Pattern.DOTALL);
        private static final Pattern reEndOfLog = Pattern.compile("<hotspot_log_done .*/>");

        public static void run(String compileCmd,
                               int cmdCompLevel,
                               int printCmdCompLevel,
                               int tieredStopAtLevel,
                               Set<String> expectedCompLevel,
                               Set<String> expectExcludedAtLevels,
                               boolean expectMDOPrinted,
                               boolean tieredCompilation)
                throws IOException, InterruptedException {

            IO.println("\n########> Testing " + compileCmd + " " + cmdCompLevel);

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+PrintCompilation",
                    "-XX:+CIPrintCompilerName",
                    "-XX:+PrintTieredEvents",
                    "-XX:+LogVMOutput",
                    "-XX:+LogCompilation",
                    "-XX:" + (tieredCompilation ? "+" : "-") + "TieredCompilation",
                    "-XX:TieredStopAtLevel=" + tieredStopAtLevel,
                    "-XX:CompileCommand=" + compileCmd + "," + TEST_METHOD_NAME_DBL_COLON + "," + cmdCompLevel,
                    "-XX:CompileCommand=print," + TEST_METHOD_NAME_DBL_COLON + "," + printCmdCompLevel,
                    CompileLevelPrintTest.class.getName());

            try (Process process = pb.start();
                    BufferedWriter processInput = process.outputWriter();
                    BufferedReader processOutput = process.inputReader();
                    BufferedReader processErrOut = process.errorReader()) {
                long startNanos = System.nanoTime();
                try {
                    IO.println("##> Testee PID: " + process.pid());
                    TesteeState testeeState = new TesteeState();

                    Thread stdoutParser = startDaemonThread(() ->
                            matchVmMessages(processOutput, testeeState,  "", "testee-" + process.pid() + ".out"));
                    Thread stderrParser = startDaemonThread(() ->
                            matchTesteeMessages(processErrOut, testeeState, "testee-" + process.pid() + ".err"));

                    IO.println("##> Waiting for testee to get ready for the start command");
                    if (!testeeState.waitingForStartTest.await(TIMEOUT_SEC, TimeUnit.SECONDS)) {
                        throw new RuntimeException("No start signal from testee");
                    }

                    Asserts.assertTrue(waitUntil(() -> !process.isAlive()
                            || (testeeState.compiler1QueueSize.get() < 5
                                    && testeeState.compiler2QueueSize.get() < 5)),
                            "Compiler queue is still not empty");
                    Asserts.assertTrue(testeeState.compileCommandsReported.contains(
                            compileCmd + " " + TEST_METHOD_NAME_DOT + " intx " + compileCmd + " = " + cmdCompLevel),
                            "'CompileCommand: " + compileCmd + "...' was not printed");
                    Asserts.assertTrue(testeeState.compileCommandsReported.contains(
                            "print " + TEST_METHOD_NAME_DOT + " intx print = " + printCmdCompLevel),
                            "'CompileCommand: print ...' was not printed");

                    IO.println("##> Order testee to start");
                    processInput.write(START_CMD); processInput.newLine(); processInput.flush();

                    waitUntil(() -> !process.isAlive()
                            || (!expectedCompLevel.isEmpty() && !expectExcludedAtLevels.isEmpty()
                                    && expectedCompLevel.equals(testeeState.testMethodCompiledAtLevel)
                                    && expectedCompLevel.equals(testeeState.testMethodPrintedAtLevel)
                                    && expectExcludedAtLevels.equals(testeeState.testMethodExcludedAtLevel)));

                    if (process.isAlive()) {
                        IO.println("##> Required messages have been found in testee output, now stop it");
                        processInput.write(STOP_CMD + "\n");
                        processInput.flush();
                        processInput.close();
                    }

                    Asserts.assertEquals(0, process.waitFor());
                    stdoutParser.join();
                    stderrParser.join();

                    // Process stdout can be garbled: pieces of different messages can be intertwined and regexps may
                    // intermittently fail to match the messages.
                    // Now parse Hotspot log file to re-match them. Duplicates are OK.
                    matchMessagesInHotspotLog("hotspot_pid" + process.pid() + ".log", testeeState);

                    Asserts.assertEquals(expectedCompLevel, testeeState.testMethodCompiledAtLevel,
                            "Test method was not compiled at required level (" + expectedCompLevel + ")");
                    Asserts.assertEquals(expectedCompLevel, testeeState.testMethodPrintedAtLevel,
                            "Test method assembly was not printed at required level (" + expectedCompLevel + ")");
                    Asserts.assertEquals(expectExcludedAtLevels, testeeState.testMethodExcludedAtLevel,
                            "Test method compilation was not excluded at required levels (" + expectExcludedAtLevels + ")");
                    Asserts.assertEquals(expectMDOPrinted, testeeState.testMethodMDOPrinted,
                            "Test method MDO was" + (expectMDOPrinted ? " NOT" : "") + " printed");

                    IO.println("########> Test passed");
                } catch (Exception ex) {
                    IO.println("########> Test failed");
                    ex.printStackTrace();
                    process.destroyForcibly();
                    throw ex;
                } finally {
                    IO.println("########> Elapsed " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos) + " ms");
                }
            }
        }

        static void matchMessagesInHotspotLog(String logFileName, TesteeState testeeState) throws IOException {
            IO.println("##> Parsing " + logFileName + " to match possibly missed messages");
            try (BufferedReader reader = new BufferedReader(new FileReader(logFileName))) {
                matchVmMessages(reader, testeeState, "Log: ", null);
            }
        }

        private static void matchVmMessages(BufferedReader testeeOutput, TesteeState testeeState, String logPrefix, String fileName) {
            try (Writer outWriter = fileName != null ? new BufferedWriter(new FileWriter(fileName)) : null) {
                String line;
                LinkedList<String> lastNLines = new LinkedList<>();
                boolean endOfLog = false;

                while (!endOfLog && (line = testeeOutput.readLine()) != null) {
                    if (outWriter != null) {
                        outWriter.write(line);
                        outWriter.write('\n');
                    }

                    line = line.trim();

                    lastNLines.addLast(line);
                    while (lastNLines.size() > LAST_N_LINES_COUNT) {
                        lastNLines.removeFirst();
                    }
                    String lastNLinesStr = String.join("\n", lastNLines);

                    Matcher matcher;
                    String msg = "";

                    if ((matcher = reCompileCommand.matcher(line)).matches()) {
                        testeeState.compileCommandsReported.add(matcher.group(1));

                        msg = "Compile command reported: " + matcher.group(1);

                    } else if ((matcher = reTieredEvent.matcher(line)).matches()) {
                        testeeState.compiler1QueueSize.set(Integer.parseInt(matcher.group(3)));
                        testeeState.compiler2QueueSize.set(Integer.parseInt(matcher.group(4)));

                    } else if ((matcher = reCompilation.matcher(line)).matches()) {
                        if ("C1".equalsIgnoreCase(matcher.group(2))) {
                            testeeState.compiler1QueueSize.decrementAndGet();
                        } else {
                            testeeState.compiler2QueueSize.decrementAndGet();
                        }

                        if (matcher.group(6).contains(TEST_METHOD_NAME_DBL_COLON)) {
                            testeeState.testMethodCompiledAtLevel.add(matcher.group(5));

                            msg = "Test method compiled:"
                                    + " compiler=" + matcher.group(2)
                                    + " level=" + matcher.group(5)
                                    + " compilation#=" + matcher.group(3)
                                    + " flags=" + matcher.group(4).trim()
                                    + " name=" + matcher.group(6);
                        }
                    } else if ((matcher = reCompiledMethod.matcher(lastNLinesStr)).matches()) {
                        if (matcher.group(6).contains(TEST_METHOD_NAME_DBL_COLON)) {
                            testeeState.testMethodPrintedAtLevel.add(matcher.group(5));

                            msg = "Test method assembly printed:"
                                    + " compiler=" + matcher.group(2)
                                    + " level=" + matcher.group(5)
                                    + " compilation#=" + matcher.group(3)
                                    + " flags=" + matcher.group(4).trim()
                                    + " name=" + matcher.group(6)
                                    + " bci=" + (matcher.group(7) != null ? matcher.group(7).trim() : "");
                        }
                    } else if ((matcher = reExcludeCompile.matcher(line)).matches()) {
                        if (matcher.group(2).contains(TEST_METHOD_NAME_DBL_COLON)) {
                            testeeState.testMethodExcludedAtLevel.add(matcher.group(1));

                            msg = "Test method not compilable:"
                                    + " level=" + matcher.group(1)
                                    + " name=" + matcher.group(2);
                        }
                    } else if ((matcher = reMethodData.matcher(lastNLinesStr)).matches()) {
                        if (matcher.group(1).contains(TEST_METHOD_SIGNATURE)) {
                            testeeState.testMethodMDOPrinted = true;

                            msg = "Test method data:"
                                    + " name=" + matcher.group(1);
                        }
                    } else if (reEndOfLog.matcher(line).matches()) {
                        endOfLog = true;

                        msg = "End of log";
                    }

                    if (!msg.isEmpty()) {
                        msg = "##> " + logPrefix + msg;
                        IO.println(msg);
                        if (outWriter != null) {
                            outWriter.write(msg);
                            outWriter.write('\n');
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private static void matchTesteeMessages(BufferedReader testeeErrorOutput, TesteeState testeeState, String fileName) {
            try (BufferedWriter outWriter = new BufferedWriter(new FileWriter(fileName))) {
                String line;
                while ((line = testeeErrorOutput.readLine()) != null) {
                    outWriter.write(line);
                    outWriter.newLine();

                    line = line.trim();

                    if (TESTEE_WAITING_FOR_START_CMD.equals(line)) {
                        IO.println("##> Testee is waiting for start command");
                        testeeState.waitingForStartTest.countDown();
                    } else if (line.startsWith("==>")) {
                        IO.println(line);
                    } else if (line.startsWith("Exception in thread ") || line.startsWith("at ")) {
                        IO.println("==>" + line);
                    } else if (DEBUG_OUTPUT) {
                        IO.println("Did not parse stderr: " + line);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    static class Testee {
        private static final CountDownLatch startCmd = new CountDownLatch(1);
        private static final CountDownLatch stopCmd = new CountDownLatch(1);

        static void run() throws IOException, InterruptedException {
            System.err.println("==> entering testee()");

            try {
                startDaemonThread(Testee::inputMonitor);

                if (stopCmd.getCount() == 0) {
                    return;
                }

                // Print 3 times, since the output can be intermixed with
                System.err.println(TESTEE_WAITING_FOR_START_CMD);
                if (!startCmd.await(TIMEOUT_SEC + 1, TimeUnit.SECONDS)) {
                    System.err.println("==> 'start' command was not given in stdin");
                    return;
                }

                if (stopCmd.getCount() == 0) {
                    return;
                }

                System.err.println("==> starting test");
                runTestCode();
            } finally {
                System.err.println("==> exiting testee()");
            }
        }

        private static void inputMonitor() {
            try (BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in))) {
                String line;
                while ((line = stdin.readLine()) != null) {
                    line = line.trim();
                    System.err.println("==> STDIN: " + line);

                    switch (line) {
                        case START_CMD:
                            startCmd.countDown();
                            break;

                        case STOP_CMD:
                            stopCmd.countDown();
                            break;

                        default:
                            System.err.println("==> ERROR: unknown command");
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        // For Tier4 600 invocation of this method with avg 25 loops for each should be enough
        // to trigger Tier4CompilationThreshold=15000
        private static void compiledMethod(final int a) {
            int r = 0;
            for (int i = 0; i < a % 50; i++) {
                r ^= i;
            }
            if (r == 42) {
                System.err.println("MAGIC!");
            }
        }

        private static boolean longLoop() {
            // To trigger compilation, 100-200 should be enough for C1 and 600-700 for C2
            for (int i = 0; i < 10000; i++) {
                compiledMethod(i);
                if ((i & 0xf) == 0 && stopCmd.getCount() == 0) {
                    System.err.println("==> Bailing out of compiledMethod() at iteration " + i);
                    return true;
                }
            }
            return false;
        }

        private static void runTestCode() {
            for (int i = 0; i < 100; i++) {
                if (longLoop()) {
                    return;
                }
            }
        }
    }

    static Thread startDaemonThread(Runnable code) {
        Thread t = new Thread(code);
        t.setDaemon(true);
        t.start();
        return t;
    }

    static boolean waitUntil(BooleanSupplier condition) throws InterruptedException {
        for (int maxWait = TIMEOUT_SEC * 5; maxWait > 0; --maxWait) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }
}
