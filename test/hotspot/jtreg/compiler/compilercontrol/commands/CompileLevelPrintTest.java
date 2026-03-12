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
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == 4 | vm.opt.TieredStopAtLevel == null)
 * @library /test/lib
 * @build compiler.compilercontrol.commands.CompileLevelPrintTest
 * @run driver compiler.compilercontrol.commands.CompileLevelPrintTest runner
 */

package compiler.compilercontrol.commands;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.ProcessTools;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompileLevelPrintTest {
    static final Method TEST_METHOD;
    static {
        try {
            TEST_METHOD = Testee.class.getDeclaredMethod("compiledMethod",
                    new Class[] {int.class});
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
    static final String TEST_METHOD_NAME_DOT =
            TEST_METHOD.getDeclaringClass().getName().replace('.', '/')
            + "." + TEST_METHOD.getName();
    static final String TEST_METHOD_NAME_DBL_COLON =
            TEST_METHOD.getDeclaringClass().getName()
            + "::" + TEST_METHOD.getName();
    static final String TEST_METHOD_SIGNATURE = TEST_METHOD_NAME_DBL_COLON + "(";

    static final String TESTEE_WAITING_FOR_START_CMD = "==> waiting for start command <==";

    static final Path START_CMD_FILE = Path.of(".start");
    static final Path STOP_TEST_METHOD_CMD = Path.of(".stop");

    static final boolean DEBUG_OUTPUT = false;

    static class TesteeState {
        final CountDownLatch waitingForStartTest = new CountDownLatch(1);
        volatile boolean areCompilerQueuesEmpty = false;
        final Set<String> compileCommandsReported = Collections.synchronizedSet(new HashSet<>());
        volatile Set<String> testMethodCompiledAtLevel = Collections.synchronizedSet(new HashSet<>());
        final Set<String> testMethodExcludedAtLevel = Collections.synchronizedSet(new HashSet<>());
        volatile Set<String> testMethodPrintedAtLevel = Collections.synchronizedSet(new HashSet<>());
        volatile boolean testMethodMDOPrinted = false;
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        if (args.length > 0 && "runner".equals(args[0])) {
            // Use the same launcher to avoid double launch cost compared to multiple jtreg @test annotations
            Runner.run("compileonly", "1", "1", "1", Set.of("1"), Set.of(), false);
            Runner.run("compileonly", "2", "2", "2", Set.of("2"), Set.of(), true);
            Runner.run("compileonly", "4", "4", "3", Set.of("3"), Set.of(), true);
            Runner.run("compileonly", "8", "8", "4", Set.of("4"), Set.of("3"), true);
            Runner.run("compileonly", "12", "12", "4", Set.of("3", "4"), Set.of(), true);

            Runner.run("exclude", "14", "1", "1", Set.of("1"), Set.of(), false);
            Runner.run("exclude", "13", "2", "2", Set.of("2"), Set.of(), true);
            Runner.run("exclude", "11", "4", "3", Set.of("3"), Set.of(), true);
            Runner.run("exclude", "7", "8", "4", Set.of("4"), Set.of("3"), true);
            Runner.run("exclude", "3", "12", "4", Set.of("3", "4"), Set.of(), true);
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
                "(\\d+) (C1|C2|no compiler): +(\\d+) ([ %][ s][ !][ b][ n]) ([-0-4]) +([^ ]+).*");
        private static final Pattern reExcludeCompile = Pattern.compile(
                "made not compilable on level (\\d) +([^ ]+) .* excluded by CompileCommand");
        private static final Pattern reCompiledMethod = Pattern.compile(
                ".*\\n-{35} Assembly -{35}\\n\\nCompiled method \\((?:c1|c2)\\) (\\d+) (C1|C2): +"
                      + "(\\d+) ([ %][ s][ !][ b][ n]) ([-0-4]) +([^ ]+) \\(\\d+ bytes\\)", Pattern.DOTALL);
        private static final Pattern reMethodData = Pattern.compile(
                ".*\\n-{72}\\nstatic ([^\\n]+)\\ninterpreter_invocation_count: *\\d+", Pattern.DOTALL);

        public static void run(String compileCmd,
                               String cmdCompLevel,
                               String printCmdCompLevel,
                               String tieredStopAtLevel,
                               Set<String> expectedCompLevel,
                               Set<String> expectExcludedAtLevels,
                               boolean expectMDOPrinted)
                throws IOException, InterruptedException {

            System.out.println("\n########> Testing " + compileCmd + " " + cmdCompLevel);

            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+PrintCompilation",
                    "-XX:+CIPrintCompilerName",
                    "-XX:+PrintTieredEvents",
                    "-XX:+TieredCompilation",
                    "-XX:TieredStopAtLevel=" + tieredStopAtLevel,
                    "-XX:CompileCommand=" + compileCmd + "," + TEST_METHOD_NAME_DBL_COLON + "," + cmdCompLevel,
                    "-XX:CompileCommand=print," + TEST_METHOD_NAME_DBL_COLON + "," + printCmdCompLevel,
                    CompileLevelPrintTest.class.getName());

            Files.deleteIfExists(START_CMD_FILE);
            Files.deleteIfExists(STOP_TEST_METHOD_CMD);

            try (Process process = pb.start();
                 BufferedReader processOutput = process.inputReader();
                 BufferedReader processErrOut = process.errorReader()) {
                try {
                    TesteeState testeeState = new TesteeState();

                    Thread stdoutParser = startDaemonThread(() ->
                            testeeOutputMonitor(processOutput, testeeState, "testee-" + process.pid() + ".out"));
                    Thread stderrParser = startDaemonThread(() ->
                            testeeErrorOutputMonitor(processErrOut, testeeState, "testee-" + process.pid() + ".err"));

                    if (!testeeState.waitingForStartTest.await(30, TimeUnit.SECONDS)) {
                        throw new RuntimeException("No start signal from testee");
                    }

                    Asserts.assertTrue(waitUntil(() -> testeeState.areCompilerQueuesEmpty),
                            "Compiler queue is still not empty");
                    Asserts.assertTrue(testeeState.compileCommandsReported.contains(
                            compileCmd + " " + TEST_METHOD_NAME_DOT + " intx " + compileCmd + " = " + cmdCompLevel),
                            "'CompileCommand: " + compileCmd + "...' was not printed");
                    Asserts.assertTrue(testeeState.compileCommandsReported.contains(
                            "print " + TEST_METHOD_NAME_DOT + " intx print = " + printCmdCompLevel),
                            "'CompileCommand: print ...' was not printed");

                    System.out.println("##> Order testee to start");
                    Files.createFile(START_CMD_FILE);
                    Asserts.assertTrue(waitUntil(() ->
                            expectedCompLevel.equals(testeeState.testMethodCompiledAtLevel)
                            && expectedCompLevel.equals(testeeState.testMethodPrintedAtLevel)
                            && expectExcludedAtLevels.equals(testeeState.testMethodExcludedAtLevel)),
                            "Test method was not compiled at required level (" + expectedCompLevel + "), "
                               + " or its method assembly was not printed, "
                               + " or method was compiled at incorrect level (" + expectExcludedAtLevels + ")");

                    System.out.println("##> Test method compiled, now stop");
                    Files.createFile(STOP_TEST_METHOD_CMD);

                    Asserts.assertEquals(process.waitFor(), 0);
                    stdoutParser.join();
                    stderrParser.join();

                    Asserts.assertEquals(expectMDOPrinted, testeeState.testMethodMDOPrinted,
                            "Test method MDO was" + (expectMDOPrinted ? " NOT" : "") + " printed");

                    System.out.println("########> Test passed");
                } catch (Exception ex) {
                    ex.printStackTrace();
                    touchFile(STOP_TEST_METHOD_CMD);
                    process.destroyForcibly();
                    throw ex;
                }
            }
        }

        private static void testeeOutputMonitor(BufferedReader testeeOutput, TesteeState testeeState, String fileName) {
            try (BufferedWriter outWriter = new BufferedWriter(new FileWriter(fileName))) {
                String line;
                LinkedList<String> lastNLines = new LinkedList<>();

                while ((line = testeeOutput.readLine()) != null) {
                    outWriter.write(line);
                    outWriter.newLine();

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
                        int compilerQueuesSize = Integer.parseInt(matcher.group(3))
                                + Integer.parseInt(matcher.group(4));

                        testeeState.areCompilerQueuesEmpty = compilerQueuesSize == 0;

                    } else if ((matcher = reCompilation.matcher(line)).matches()) {
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
                                    + " name=" + matcher.group(6);
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
                    } else if (DEBUG_OUTPUT) {
                        System.out.println("Did not parse stdout: " + line);
                    }

                    if (!msg.isEmpty()) {
                        msg = "##> " + msg;
                        System.out.println(msg);
                        outWriter.write(msg);
                        outWriter.newLine();
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private static void testeeErrorOutputMonitor(BufferedReader testeeErrorOutput, TesteeState testeeState, String fileName) {
            try (BufferedWriter outWriter = new BufferedWriter(new FileWriter(fileName))) {
                String line;
                while ((line = testeeErrorOutput.readLine()) != null) {
                    outWriter.write(line);
                    outWriter.newLine();

                    line = line.trim();

                    if (TESTEE_WAITING_FOR_START_CMD.equals(line)) {
                        System.out.println("##> Testee is waiting for start command");
                        testeeState.waitingForStartTest.countDown();
                    } else if (line.startsWith("==>")) {
                        System.out.println(line);
                    } else if (line.startsWith("Exception in thread ") || line.startsWith("at ")) {
                        System.out.println("==>" + line);
                    } else if (DEBUG_OUTPUT) {
                        System.out.println("Did not parse stderr: " + line);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    static class Testee {
        static void run() throws IOException {
            System.err.println("==> entering testee() <==");
            try {
                for (;;) {
                    System.err.println(TESTEE_WAITING_FOR_START_CMD);
                    Asserts.assertTrue(waitUntil(() -> Files.exists(START_CMD_FILE) || Files.exists(STOP_TEST_METHOD_CMD)));
                    if (Files.exists(STOP_TEST_METHOD_CMD)) {
                        break;
                    }
                    Files.deleteIfExists(START_CMD_FILE);

                    System.err.println("==> starting test <==");
                    runTestCode();
                }
                Files.deleteIfExists(STOP_TEST_METHOD_CMD);
            } finally {
                System.err.println("==> exiting testee() <==");
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
            for (int i = 0; i < 1500; i++) {
                compiledMethod(i);
                if ((i & 0xf) == 0) {
                    if (Files.exists(STOP_TEST_METHOD_CMD)) {
                        System.err.println("==> compiledMethod() has been compiled at iteration " + i + " <==");
                        return true;
                    }
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

    static void touchFile(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
    }

    static boolean waitUntil(BooleanSupplier condition) {
        for (int maxWait = 300; maxWait > 0; --maxWait) {
            if (condition.getAsBoolean()) {
                return true;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
        }
        return false;
    }
}
