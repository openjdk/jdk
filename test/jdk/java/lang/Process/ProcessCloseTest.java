/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.java.lang.Process;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/*
 * @test
 * @bug 8336479
 * @summary Tests for Process.close
 * @run junit/othervm -Djava.util.logging.config.file=${test.src}/ProcessLogging-FINE.properties
 *       jdk.java.lang.Process.ProcessCloseTest
 */

public class ProcessCloseTest {

    private static List<String> JAVA_ARGS;

    private static List<String> setupJavaEXE() {
        String JAVA_HOME = System.getProperty("test.jdk");
        if (JAVA_HOME == null)
            JAVA_HOME = System.getProperty("JAVA_HOME");
        String classPath = System.getProperty("test.class.path");
        return  List.of(JAVA_HOME + "/bin/java", "-cp", classPath, ProcessCloseTest.class.getName());
    }

    private static List<String> javaArgs(ChildCommand... moreArgs) {

        List<String> javaArgs = JAVA_ARGS;
        if (javaArgs == null) {
            JAVA_ARGS = javaArgs = setupJavaEXE();
        }
        List<String> args = new ArrayList<>(javaArgs);
        for (ChildCommand arg : moreArgs) {
            args.add(arg.toString());
        }
        return args;
    }

    // Permutations
    // Parent single thread vs concurrent close
    // child waiting on stdin
    // child sending on stdout and on stderr
    // child exited and not exited
    // parent abandoning stdin and stderr and either-or,
    // parent waiting for exit before closing vs not waiting

    static Stream<Arguments> singleThreadTestCases() {
        return Stream.of(
                Arguments.of(List.of("echo", "xyz0"),
                        List.of(ProcessCommand.STDOUT_PRINT_ALL_LINES,
                                ProcessCommand.STDERR_EXPECT_EMPTY,
                                ProcessCommand.PROCESS_EXPECT_EXIT_NORMAL),
                        ExitStatus.NORMAL),
                Arguments.of(List.of("echo", "xyz1"),
                        List.of(ProcessCommand.STDOUT_PRINT_ALL_LINES),
                        ExitStatus.RACY),
                Arguments.of(List.of("cat", "-"),
                        List.of(ProcessCommand.WRITER_WRITE,
                                ProcessCommand.WRITER_CLOSE,
                                ProcessCommand.STDOUT_PRINT_ALL_LINES),
                        ExitStatus.RACY),
                Arguments.of(List.of("cat", "-"),
                        List.of(ProcessCommand.STDOUT_WRITE,
                                ProcessCommand.STDOUT_CLOSE,
                                ProcessCommand.STDOUT_PRINT_ALL_LINES),
                        ExitStatus.RACY),
                Arguments.of(List.of("cat", "-"),
                        List.of(ProcessCommand.STDOUT_WRITE,
                                ProcessCommand.STDOUT_CLOSE,
                                ProcessCommand.PROCESS_EXPECT_EXIT_NORMAL),
                        ExitStatus.NORMAL),
                Arguments.of(List.of("cat", "NoSuchFile.txt"),
                        List.of(ProcessCommand.STDERR_PRINT_ALL_LINES,
                                ProcessCommand.STDOUT_EXPECT_EMPTY),
                        ExitStatus.RACY),
                Arguments.of(javaArgs(ChildCommand.STDOUT_MARCO),
                        List.of(ProcessCommand.STDOUT_EXPECT_POLO,
                                ProcessCommand.STDERR_EXPECT_EMPTY),
                        ExitStatus.RACY),
                Arguments.of(javaArgs(ChildCommand.STDERR_MARCO),
                        List.of(ProcessCommand.STDERR_EXPECT_POLO,
                                ProcessCommand.STDOUT_EXPECT_EMPTY),
                        ExitStatus.NORMAL),
                Arguments.of(javaArgs(ChildCommand.PROCESS_EXIT1),
                        List.of(ProcessCommand.PROCESS_EXPECT_EXIT_FAIL),
                        ExitStatus.FAIL),       // Got expected status == 1
                Arguments.of(List.of("echo", "abc"),
                        List.of(ProcessCommand.PROCESS_CLOSE),
                        ExitStatus.RACY), // Racy, not deterministic
                Arguments.of(List.of("cat", "-"),
                        List.of(ProcessCommand.STDOUT_WRITE,
                                ProcessCommand.PROCESS_CLOSE),
                        ExitStatus.RACY),  // Racy, not deterministic
                Arguments.of(List.of("echo", "abc"),
                        List.of(ProcessCommand.PROCESS_DESTROY),
                        ExitStatus.RACY), // Racy, not deterministic
                Arguments.of(List.of("cat", "-"),
                        List.of(ProcessCommand.STDOUT_WRITE,
                                ProcessCommand.PROCESS_DESTROY),
                        ExitStatus.RACY), // Racy, not deterministic
                Arguments.of(List.of("echo"),
                        List.of(ProcessCommand.PROCESS_EXPECT_EXIT_NORMAL),
                        ExitStatus.RACY)
        );
    }


    @ParameterizedTest
    @MethodSource("singleThreadTestCases")
    void simple(List<String> args, List<ProcessCommand> commands, ExitStatus exitStatus) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(args);
        Process p = pb.start();
        System.err.printf("Program: %s; pid: %d\n", args, p.pid());
        commands.forEach(c -> {
            System.err.printf("    %s\n", c);
            c.command.accept(p);
        });
        p.close();
        ProcessCommand.processExpectExit(p, exitStatus);
    }

    @ParameterizedTest
    @MethodSource("singleThreadTestCases")
    void autoCloseable(List<String> args, List<ProcessCommand> commands, ExitStatus exitStatus) {
        ProcessBuilder pb = new ProcessBuilder(args);
        Process proc = null;
        try (Process p = pb.start()) {
            proc = p;
            System.err.printf("Program: %s; pid: %d\n", args, p.pid());
            commands.forEach(c -> {
                System.err.printf("    %s\n", c);
                c.command.accept(p);
            });
        }  catch (IOException ioe) {
            Assertions.fail(ioe);
        }
        ProcessCommand.processExpectExit(proc, exitStatus);
    }


    /**
     * Test AutoCloseable for the process and out, in, and err streams.
     * @param args The command line arguments
     * @param commands the commands to the process
     * @param exitStatus The expected final exit status
     */
    @ParameterizedTest
    @MethodSource("singleThreadTestCases")
    void autoCloseableAll(List<String> args, List<ProcessCommand> commands, ExitStatus exitStatus) {
        ProcessBuilder pb = new ProcessBuilder(args);
        Process proc = null;
        try (Process p = pb.start(); var out = p.getOutputStream();
             var in = p.getInputStream();
             var err = p.getErrorStream()) {
            proc = p;
            System.err.printf("Program: %s; pid: %d\n", args, p.pid());
            commands.forEach(c -> {
                System.err.printf("    %s\n", c);
                c.command.accept(p);
            });
        } catch (IOException ioe) {
            Assertions.fail(ioe);
        }
        ProcessCommand.processExpectExit(proc, exitStatus);
    }


        // ExitStatus named values and assertions
    enum ExitStatus {
        NORMAL(0),
        FAIL(1),
        PIPE(1, 141),
        KILLED(0, 143),
        RACY(0, 1, 143),
        ;
        private final int[] allowedStatus;

        ExitStatus(int... status) {
            this.allowedStatus = status;
        }

        // Check a status matches one of the allowed exit status values
        void assertEquals(int actual) {
            for (int status : allowedStatus) {
                if (status == actual) {
                    return;     // status is expected
                }
            }
            if (this == RACY) {
                // Not an error but report the actual status
                System.err.printf("Racy exit status: %d\n", actual);
            } else {
                Assertions.fail("Status: " + actual + ", expected one of: " + Arrays.toString(allowedStatus));
            }
        }
    }
    /**
     * Commands on a Process that can be sequenced in the parent.
     *
     * See ChildCommands for commands that can be sent to the child process.
     */
    enum ProcessCommand {
        PROCESS_EXPECT_EXIT_NORMAL(ProcessCommand::processExpectExitNormal),
        PROCESS_EXPECT_EXIT_FAIL(ProcessCommand::processExpectExitFail),
        PROCESS_CLOSE(ProcessCommand::processClose),
        PROCESS_DESTROY(ProcessCommand::processDestroy),
        WRITER_WRITE(ProcessCommand::writerWrite),
        WRITER_CLOSE(ProcessCommand::writerClose),
        STDOUT_PRINT_ALL_LINES(ProcessCommand::stdoutPrintAllLines),
        STDERR_PRINT_ALL_LINES(ProcessCommand::stderrPrintAllLines),
        STDOUT_WRITE(ProcessCommand::stdoutWrite),
        STDOUT_CLOSE(ProcessCommand::stdoutClose),
        STDOUT_EXPECT_POLO(ProcessCommand::stdoutExpectPolo),
        STDERR_EXPECT_POLO(ProcessCommand::stderrExpectPolo),
        STDOUT_EXPECT_EMPTY(ProcessCommand::stdoutExpectEmpty),
        STDERR_EXPECT_EMPTY(ProcessCommand::stderrExpectEmpty),
        ;
        private final Consumer<Process> command;

        ProcessCommand(Consumer<Process> command) {
            this.command = command;
        }

        private static void stdoutPrintAllLines(Process p) {
            try {
                var lines = p.inputReader().readAllLines();
                Assertions.assertNotEquals(0, lines.size(), "stdout should not be empty");
                System.err.printf("        %d lines\n", lines.size());
                System.err.println(lines.toString().indent(8));
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        private static void stderrPrintAllLines(Process p) {
            try {
                var lines = p.errorReader().readAllLines();
                Assertions.assertNotEquals(0, lines.size(), "stderr should not be empty");
                System.err.printf("        %d lines\n", lines.size());
                System.err.println(lines.toString().indent(8));
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        private static void writerWrite(Process p) {
            try {
                p.outputWriter().write("Now is the time.");
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        private static void writerClose(Process p) {
            try {
                p.outputWriter().close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        private static void stdoutExpectPolo(Process p) {
            String line = readLine(p.getInputStream());
            Assertions.assertEquals("Polo", line, "Stdout Expected Polo");
        }

        private static void stderrExpectPolo(Process p) {
            String line = readLine(p.getErrorStream());
            Assertions.assertEquals("Polo", line, "Stderr Expected Empty");        }

        private static void stdoutExpectEmpty(Process p) {
            String line = readLine(p.getInputStream());
            Assertions.assertEquals("", line, "Stdout Expected Empty");
        }

        private static void stderrExpectEmpty(Process p) {
            String line = readLine(p.getErrorStream());
            Assertions.assertEquals("", line, "Stderr Expected Polo");        }

        private static String readLine(InputStream in) {
            StringBuilder sb = new StringBuilder();
            byte[] bytes = new byte[1];
            try {
                int ch;
                while ((ch = in.read()) != -1) {
                    if (ch == '\n') {
                        // end of line
                        return sb.toString();
                    }
                    if (ch != '\r') {       // ignore cr - Windows defense
                        sb.append((char) ch);
                    }
                }
                // EOF - return string if no LF found
                return sb.toString();
            } catch (IOException ioe) {
                return ioe.getMessage();
            }
        }

        private static void stdoutWrite(Process p) {
            try {
                var out = p.getOutputStream();
                out.write("stdout-write".getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        private static void stdoutClose(Process p) {
            try {
                p.getOutputStream().close();
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        // Expect a normal exit
        private static void processExpectExitNormal(Process p) {
            processExpectExit(p, ExitStatus.NORMAL);
        }

        // expect an error (1) status
        private static void processExpectExitFail(Process p) {
            processExpectExit(p, ExitStatus.FAIL);
        }

        // Process.processExpectExitNormal an expected status
        private static void processExpectExit(Process p, ExitStatus expected) {
            while (true) {
                try {
                    int st = p.waitFor();
                    expected.assertEquals(st);
                    break;
                } catch (InterruptedException ie) {
                    // retry above
                }
            }
        }

        private static void processClose(Process p) {
            p.close();
        }

        private static void processDestroy(Process p) {
            p.destroy();
        }
    }

    // Commands to Java child sent as command line arguments
    enum ChildCommand {
        STDOUT_ECHO(ChildCommand::stdoutEchoBytes),
        STDERR_ECHO(ChildCommand::stderrEchoBytes),
        SLEEP5(ChildCommand::sleep5),
        STDOUT_MARCO(ChildCommand::stdoutMarco),
        STDERR_MARCO(ChildCommand::stderrMarco),
        PROCESS_EXIT1(ChildCommand::processExit1),
        ;
        private final Runnable command;
        ChildCommand(Runnable cmd) {
            this.command = cmd;
        }

        private static void sleep5() {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException ie) {
                // Interrupted sleep, re-assert interrupt
                Thread.currentThread().interrupt();
            }
        }

        private static void stdoutEchoBytes() {
            echoBytes(System.in, System.out);
        }

        private static void stderrEchoBytes() {
            echoBytes(System.in, System.err);
        }

        private static void echoBytes(InputStream in, PrintStream out) {
            try {
                byte[] bytes = in.readAllBytes();
                out.write(bytes);
            } catch (IOException ioe) {
                out.println(ioe);
            }
        }

        private static void stdoutMarco() {
            System.out.println("Polo");
        }

        private static void stderrMarco() {
            System.err.println("Polo");
        }

        private static void processExit1() {
            System.exit(1);
        }
    }

    // Copy of ProcessExamples in java/lang/snippet-files/ProcessExamples.java
    @Test
    void example() {
        String haiku = """
                Oh, the sunrise glow;
                Paddling with the river flow;
                Chilling still, go slow.
                """;
        try (Process p = new ProcessBuilder("cat").start();
             Writer writer = p.outputWriter();
             Reader reader = p.inputReader()) {
            writer.write(haiku);
            writer.close();
            // Read all lines and print each
            reader.readAllLines()
                    .forEach(System.err::println);
            var status = p.waitFor();
            if (status != 0)
                throw new RuntimeException("unexpected process status: " + status);
        } catch (Exception e) {
            System.err.println("Process failed: " + e);
        }
    }

    /**
     * Child program that executes child actions as named by command line args.
     * @param childCommands a sequence of ChildCommand names.
     */
    public static void main(String... childCommands)  {
        List<String> args = List.of(childCommands);
        List<ChildCommand> cmds = args.stream().map(ChildCommand::valueOf).toList();
        cmds.forEach(c -> c.command.run());
    }

}
