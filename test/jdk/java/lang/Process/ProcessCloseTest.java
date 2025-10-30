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

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/*
 * @test
 * @bug 8364361
 * @summary Tests for Process.close
 * @modules java.base/java.lang:+open java.base/java.io:+open
 * @run junit jdk.java.lang.Process.ProcessCloseTest
 */
public class ProcessCloseTest {

    private final static boolean OS_WINDOWS = System.getProperty("os.name").startsWith("Windows");
    private final static String CAT_PROGRAM = "cat";
    public static final String FORCED_CLOSE_MSG = "Forced close";
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

    /**
     * {@return A Stream of Arguments}
     * Each Argument consists of three lists.
     * - A List of command line arguments to start a process.
     *   `javaArgs can be used to launch a Java child with ChildCommands
     * - A List of ProcessCommand actions to be invoked on that process
     * - A List of commands to be invoked on the process after the close or T-W-R exit.
     */
    static Stream<Arguments> singleThreadTestCases() {
        return Stream.of(
                Arguments.of(List.of("echo", "xyz0"),
                        List.of(ProcessCommand.STDOUT_PRINT_ALL_LINES,
                                ProcessCommand.STDERR_EXPECT_EMPTY,
                                ExitStatus.NORMAL),
                        List.of(ExitStatus.NORMAL)),
                Arguments.of(List.of("echo", "xyz1"),
                        List.of(ProcessCommand.STDOUT_PRINT_ALL_LINES),
                        List.of(ExitStatus.NORMAL)),
                Arguments.of(javaArgs(ChildCommand.STDOUT_ECHO),
                        List.of(ProcessCommand.WRITER_WRITE,
                                ProcessCommand.WRITER_CLOSE,
                                ProcessCommand.STDOUT_PRINT_ALL_LINES),
                        List.of(ExitStatus.NORMAL)),
                Arguments.of(javaArgs(ChildCommand.STDOUT_ECHO),
                        List.of(ProcessCommand.STDOUT_WRITE,
                                ProcessCommand.STDOUT_CLOSE,
                                ProcessCommand.STDOUT_PRINT_ALL_LINES),
                        List.of(ExitStatus.NORMAL)),
                Arguments.of(javaArgs(ChildCommand.STDOUT_ECHO),
                        List.of(ProcessCommand.STDOUT_WRITE,
                                ProcessCommand.STDOUT_CLOSE,
                                ExitStatus.NORMAL),
                        List.of(ExitStatus.NORMAL)),
                Arguments.of(List.of(CAT_PROGRAM, "NoSuchFile.txt"),
                        List.of(ProcessCommand.STDERR_PRINT_ALL_LINES,
                                ProcessCommand.STDOUT_EXPECT_EMPTY),
                        List.of(ExitStatus.FAIL)),
                Arguments.of(javaArgs(ChildCommand.STDOUT_MARCO),
                        List.of(ProcessCommand.STDOUT_EXPECT_POLO,
                                ProcessCommand.STDERR_EXPECT_EMPTY),
                        List.of(ExitStatus.NORMAL)),
                Arguments.of(javaArgs(ChildCommand.STDERR_MARCO),
                        List.of(ProcessCommand.STDERR_EXPECT_POLO,
                                ProcessCommand.STDOUT_EXPECT_EMPTY),
                        List.of(ExitStatus.NORMAL)),
                Arguments.of(javaArgs(ChildCommand.PROCESS_EXIT1),
                        List.of(ExitStatus.FAIL),
                        List.of(ExitStatus.FAIL)),       // Got expected status == 1
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_INTERRUPT, // schedule an interrupt (in .2 sec)
                                ProcessCommand.PROCESS_CLOSE,
                                ProcessCommand.PROCESS_CHECK_INTERRUPTED), // Verify interrupted status
                        List.of(ExitStatus.KILLED)), // And process was destroyed
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_INTERRUPT), // interrupts the TWR/close
                        List.of(ProcessCommand.PROCESS_CHECK_INTERRUPTED, ExitStatus.KILLED)),
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ExitStatus.NORMAL), // waitFor before T-W-R exit
                        List.of(ExitStatus.NORMAL)),
                Arguments.of(List.of("echo", "abc"),
                        List.of(ProcessCommand.PROCESS_CLOSE),
                        List.of(ExitStatus.RACY)),
                Arguments.of(javaArgs(ChildCommand.STDOUT_ECHO),
                        List.of(ProcessCommand.STDOUT_WRITE,
                                ProcessCommand.PROCESS_CLOSE),
                        List.of(ExitStatus.PIPE)),
                Arguments.of(List.of("echo", "def"),
                        List.of(ProcessCommand.PROCESS_DESTROY),
                        List.of(ExitStatus.RACY)), // Racy, not deterministic
                Arguments.of(javaArgs(ChildCommand.STDOUT_ECHO),
                        List.of(ProcessCommand.STDOUT_WRITE,
                                ProcessCommand.PROCESS_DESTROY),
                        List.of(ExitStatus.RACY)), // Racy, not deterministic
                Arguments.of(List.of("echo"),
                        List.of(ExitStatus.NORMAL),
                        List.of(ExitStatus.NORMAL))
        );
    }

    // Utility to process each command on the process
    private static void doCommands(Process proc, List<Consumer<Process>> commands) {
        commands.forEach(c -> {
            Log.printf("    %s\n", c);
            c.accept(proc);
        });
    }

    @ParameterizedTest
    @MethodSource("singleThreadTestCases")
    void simple(List<String> args, List<Consumer<Process>> commands,
                List<Consumer<Process>> postCommands) throws IOException {
        var log = Log.get();
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            Process p = pb.start(); // Buffer any debug output
            Log.printf("Program: %s; pid: %d\n", args, p.pid());
            doCommands(p, commands);
            p.close();
            doCommands(p, postCommands);
        } catch (Throwable ex) {
            System.err.print(log);
            throw ex;
        }
    }

    @ParameterizedTest
    @MethodSource("singleThreadTestCases")
    void autoCloseable(List<String> args, List<Consumer<Process>> commands,
                       List<Consumer<Process>> postCommands) {
        var log = Log.get();
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            Process proc = null;
            try (Process p = pb.start()) {
                proc = p;
                Log.printf("Program: %s; pid: %d\n", args, p.pid());
                doCommands(p, commands);
            } catch (IOException ioe) {
                Assertions.fail(ioe);
            }
            doCommands(proc, postCommands);
        } catch (Throwable ex) {
            System.err.println(log);
            throw ex;
        }
    }

    /**
     * Test AutoCloseable for the process and out, in, and err streams.
     * @param args The command line arguments
     * @param commands the commands to the process
     * @param postCommands The expected final exit status
     */
    @ParameterizedTest
    @MethodSource("singleThreadTestCases")
    void autoCloseableAll(List<String> args, List<Consumer<Process>> commands,
                          List<Consumer<Process>> postCommands) {
        var log = Log.get();
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            Process proc = null;
            try (Process p = pb.start(); var out = p.getOutputStream();
                 var in = p.getInputStream();
                 var err = p.getErrorStream()) {
                proc = p;
                Log.printf("Program: %s; pid: %d\n", args, p.pid());
                doCommands(p, commands);
            } catch (IOException ioe) {
                Assertions.fail(ioe);
            }
            doCommands(proc, postCommands);
        }  catch (Throwable ex) {
            System.err.println(log);
            throw ex;
        }
    }

    /**
     * ExitStatus named values and assertions
     */
    enum ExitStatus implements Consumer<Process> {
        NORMAL(0),
            FAIL(1),
            PIPE(0, 1, 141),   // SIGPIPE
            KILLED(1, 137), // SIGKILL
            TERMINATED(0, 143), // SIGTERM
            RACY(0, 1, 137, 143),
        ;
        private final int[] allowedStatus;

        ExitStatus(int... status) {
            this.allowedStatus = status;
        }

        // If used as a process command, checks the exit status
        public void accept(Process p) {
            try {
                Instant begin = Instant.now();
                final int exitStatus = p.waitFor();
                Duration latency = begin.until(Instant.now());
                Log.printf("    ExitStatus: %d, sig#: %d, waitFor latency: %s%n",
                        exitStatus, exitStatus & 0x7f, latency);
                assertEquals(exitStatus);
            } catch (InterruptedException ie) {
                Assertions.fail("Unexpected InterruptedException checking status: " + this);
            }
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
                Log.printf("Racy exit status: %d\n", actual);
            } else {
                Assertions.fail("Status: " + actual + ", sig#: " + (actual & 0x7f) +
                        ", expected one of: " + Arrays.toString(allowedStatus));
            }
        }
    }

    /**
     * Commands on a Process that can be sequenced in the parent.
     * See ChildCommands for commands that can be sent to the child process.
     */
    enum ProcessCommand implements Consumer<Process> {
        PROCESS_CLOSE(ProcessCommand::processClose),
        PROCESS_DESTROY(ProcessCommand::processDestroy),
        PROCESS_FORCE_OUT_CLOSE_EXCEPTION(ProcessCommand::processForceOutCloseException),
        PROCESS_FORCE_IN_CLOSE_EXCEPTION(ProcessCommand::processForceInCloseException),
        PROCESS_FORCE_ERROR_CLOSE_EXCEPTION(ProcessCommand::processForceErrorCloseException),
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
        PROCESS_INTERRUPT(ProcessCommand::processInterruptThread),
        PROCESS_CHECK_INTERRUPTED(ProcessCommand::processAssertInterrupted),
        ;
        private final Consumer<Process> command;

        ProcessCommand(Consumer<Process> command) {
            this.command = command;
        }

        public void accept(Process p) {
            command.accept(p);
        }

        private static void stdoutPrintAllLines(Process p) {
            try {
                var lines = p.inputReader().readAllLines();
                Assertions.assertNotEquals(0, lines.size(), "stdout should not be empty");
                Log.printf("        %d lines\n", lines.size());
                Log.printf("%s%n", lines.toString().indent(8));
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }

        private static void stderrPrintAllLines(Process p) {
            try {
                var lines = p.errorReader().readAllLines();
                Assertions.assertNotEquals(0, lines.size(), "stderr should not be empty");
                Log.printf("        %d lines\n", lines.size());
                Log.printf("%s%n", lines.toString().indent(8));
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
            Assertions.assertEquals("Polo", line, "Stderr Expected Polo");
        }

        private static void stdoutExpectEmpty(Process p) {
            String line = readLine(p.getInputStream());
            Assertions.assertEquals("", line, "Stdout Expected Empty");
        }

        private static void stderrExpectEmpty(Process p) {
            String line = readLine(p.getErrorStream());
            Assertions.assertEquals("", line, "Stderr Expected Empty");
        }

        private static String readLine(InputStream in) {
            StringBuilder sb = new StringBuilder();
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

        private static void processClose(Process p) {
            try {
                p.close();
            } catch (IOException ioe) {
                Assertions.fail(ioe);
            }
        }

        private static void processDestroy(Process p) {
            p.destroy();
        }

        // Interpose an input stream that throws on close()
        private static void processForceInCloseException(Process p) {
            try {
                synchronized (p) {
                    Field stdinField = p.getClass().getDeclaredField(OS_WINDOWS ? "stdin_stream" : "stdin");
                    stdinField.setAccessible(true);
                    stdinField.set(p, new ThrowingOutputStream((OutputStream) stdinField.get(p)));
                }
            } catch (Exception ex) {
                Assertions.fail("Failed to setup InputStream for throwing close", ex);
            }
        }

        // Interpose an output stream that throws on close()
        private static void processForceOutCloseException(Process p) {
            try {
                synchronized (p) {
                    Field stdoutField = p.getClass().getDeclaredField(OS_WINDOWS ? "stdout_stream" : "stdout");
                    stdoutField.setAccessible(true);
                    stdoutField.set(p, new ThrowingInputStream((InputStream) stdoutField.get(p)));
                }
            } catch (Exception ex) {
                Assertions.fail("Failed to setup OutputStream throwing close", ex);
            }
        }

        // Interpose an error stream that throws on close()
        private static void processForceErrorCloseException(Process p) {
            try {
                synchronized (p) {
                    Field stderrField = p.getClass().getDeclaredField(OS_WINDOWS ? "stderr_stream" : "stderr");
                    stderrField.setAccessible(true);
                    stderrField.set(p, new ThrowingInputStream((InputStream) stderrField.get(p)));
                }
            } catch (Exception ex) {
                Assertions.fail("Failed to setup OutputStream for throwing close", ex);
            }
        }

        // Hard coded to interrupt the invoking thread at a fixed rate of .2 second, if process is alive
        private static void processInterruptThread(Process p) {
            if (p.isAlive()) {
                int delay = 200;
                final Thread targetThread = Thread.currentThread();
                ForkJoinPool common = ForkJoinPool.commonPool();
                final ThreadInterruptor interrupter = new ThreadInterruptor(p, targetThread);
                common.scheduleAtFixedRate(interrupter, delay, delay, TimeUnit.MILLISECONDS);
            }
        }

        // Verify that an interrupt is pending and reset it
        private static void processAssertInterrupted(Process p) {
            Assertions.assertTrue(Thread.interrupted(), "Expected an interrupt");
        }
    }

    // Runnable scheduled at a fixed rate to interrupt a thread if a process is alive.
    private static class ThreadInterruptor implements Runnable {
        private final Process process;
        private final Thread targetThread;
        private int count;

        ThreadInterruptor(Process process, Thread targetThread) {
            this.process = process;
            this.targetThread = targetThread;
            this.count = 0;
        }

        public void run() {
            if (process.isAlive()) {
                count++;
                Log.printf("Interrupting thread, count: %d%n", count);
                targetThread.interrupt();
            } else {
                throw new RuntimeException("process not alive");
            }
        }
    }

    // Commands to Java child sent as command line arguments
    enum ChildCommand {
        STDOUT_ECHO(ChildCommand::stdoutEchoBytes),
        STDERR_ECHO(ChildCommand::stderrEchoBytes),
        SLEEP(ChildCommand::SLEEP),
        STDOUT_MARCO(ChildCommand::stdoutMarco),
        STDERR_MARCO(ChildCommand::stderrMarco),
        PROCESS_EXIT1(ChildCommand::processExit1),
        ;
        private final Runnable command;
        ChildCommand(Runnable cmd) {
            this.command = cmd;
        }

        // The child sleeps before continuing with next ChildCommand
        private static void SLEEP() {
            final int sleepMS = 2_000;
            try {
                Thread.sleep(sleepMS);
            } catch (InterruptedException ie) {
                // Interrupted sleep, re-assert interrupted status
                System.err.println("Sleep interrupted");  // Note the interruption in the log
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

    static Stream<Arguments> closeExceptions() {
        return Stream.of(
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_FORCE_OUT_CLOSE_EXCEPTION),
                        List.of(ExitStatus.RACY), List.of(FORCED_CLOSE_MSG)),
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_FORCE_IN_CLOSE_EXCEPTION),
                        List.of(ExitStatus.RACY), List.of(FORCED_CLOSE_MSG)),
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_FORCE_ERROR_CLOSE_EXCEPTION),
                        List.of(ExitStatus.RACY), List.of(FORCED_CLOSE_MSG)),
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_FORCE_OUT_CLOSE_EXCEPTION,
                                ProcessCommand.PROCESS_FORCE_IN_CLOSE_EXCEPTION,
                                ProcessCommand.PROCESS_FORCE_ERROR_CLOSE_EXCEPTION),
                        List.of(ExitStatus.RACY), List.of(FORCED_CLOSE_MSG, FORCED_CLOSE_MSG, FORCED_CLOSE_MSG)),
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_FORCE_OUT_CLOSE_EXCEPTION,
                                ProcessCommand.PROCESS_FORCE_IN_CLOSE_EXCEPTION),
                        List.of(ExitStatus.RACY), List.of(FORCED_CLOSE_MSG, FORCED_CLOSE_MSG)),
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_FORCE_OUT_CLOSE_EXCEPTION,
                                ProcessCommand.PROCESS_FORCE_ERROR_CLOSE_EXCEPTION),
                        List.of(ExitStatus.RACY), List.of(FORCED_CLOSE_MSG, FORCED_CLOSE_MSG)),
                Arguments.of(javaArgs(ChildCommand.SLEEP),
                        List.of(ProcessCommand.PROCESS_FORCE_IN_CLOSE_EXCEPTION,
                                ProcessCommand.PROCESS_FORCE_ERROR_CLOSE_EXCEPTION),
                        List.of(ExitStatus.RACY), List.of(FORCED_CLOSE_MSG, FORCED_CLOSE_MSG)),
                Arguments.of(List.of("echo", "xyz1"),
                        List.of(ProcessCommand.PROCESS_FORCE_OUT_CLOSE_EXCEPTION),
                        List.of(ExitStatus.RACY), List.of(FORCED_CLOSE_MSG))
        );
    }
    /**
     * Test AutoCloseable for cases that are expected to throw exceptions.
     * The list of ProcessCommands controls what is sent to the process and closing of streams.
     * The command line arguments control the sequence of actions taken by the child.
     * @param args The command line arguments for the child process
     * @param commands the commands to this process controlling the child
     * @param postCommands The expected final exit status
     * @param expectedMessages the list of exception messages expected by close()
     */
    @ParameterizedTest
    @MethodSource("closeExceptions")
    void testStreamsCloseThrowing(List<String> args, List<Consumer<Process>> commands,
                                  List<Consumer<Process>> postCommands, List<String> expectedMessages) {
        var log = Log.get();
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            Process proc = null;
            IOException expectedIOE = null;
            try (Process p = pb.start()) {
                proc = p;
                Log.printf("Program: %s; pid: %d\n",args, p.pid());
                doCommands(p, commands);
            } catch (IOException ioe) {
                expectedIOE = ioe;
            }
            // Check the exceptions thrown, if any
            if (expectedIOE != null) {
                // Check each exception that it is expected
                Assertions.assertEquals(expectedMessages.getFirst(), expectedIOE.getMessage(),
                        "Unexpected exception message");
                var suppressedEx = expectedIOE.getSuppressed();
                Assertions.assertEquals(expectedMessages.size() - 1, suppressedEx.length,
                        "Number of suppressed exceptions");
                for (int i = 1; i < expectedMessages.size(); i++) {
                    Assertions.assertEquals(expectedMessages.get(i),
                            suppressedEx[i - 1].getMessage(),
                            "Unexpected suppressed exception message");
                }
            }
            Assertions.assertNotNull(proc, "Process is null");
            doCommands(proc, postCommands);
        }  catch (Exception ex) {
            System.err.println(log);
            throw ex;
        }
    }

    /**
     * An OutputStream that delegates to another stream and always throws IOException on close().
     */
    private static class ThrowingOutputStream extends FilterOutputStream {
        public ThrowingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            try {
                out.close();
            } catch (IOException ioe) {
                // ignore except to log the exception; may be useful to debug
                ioe.printStackTrace(System.err);
            }
            throw new IOException(FORCED_CLOSE_MSG);
        }
    }

    /**
     * An InputStream that delegates to another stream and always throws IOException on close().
     */
    private static class ThrowingInputStream extends FilterInputStream {
        public ThrowingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() throws IOException {
            try {
                in.close();
            } catch (IOException ioe) {
                // ignore except to log the exception; may be useful to debug
                ioe.printStackTrace(System.err);
            }
            throw new IOException(FORCED_CLOSE_MSG);
        }
    }

    // Copy of ProcessExamples in java/lang/snippet-files/ProcessExamples.java
    @Test
    void example() {
        try (Process p = new ProcessBuilder(CAT_PROGRAM).start();
             Writer writer = p.outputWriter();
             Reader reader = p.inputReader()) {
            writer.write(haiku);
            writer.close();
            // Read all lines and print each
            reader.readAllLines()
                    .forEach(System.out::println);
            var status = p.waitFor();
            if (status != 0)
                throw new RuntimeException("unexpected process status: " + status);
        } catch (Exception e) {
            System.err.println("Process failed: " + e);
        }
    }

    String haiku = """
                Oh, the sunrise glow;
                Paddling with the river flow;
                Chilling still, go slow.
                """;

    /**
     * Child program that executes child actions as named by command line args.
     * @param childCommands a sequence of ChildCommand names.
     */
    public static void main(String... childCommands)  {
        List<String> args = List.of(childCommands);
        List<ChildCommand> commands = args.stream().map(ChildCommand::valueOf).toList();
        commands.forEach(c -> c.command.run());
    }

    /**
     * Log of output produced on a thread during a test.
     * Normally, the output is buffered and only output to stderr if the test fails.
     * Set -DDEBUG=true to send all output to stderr as it occurs.
     */
    private static class Log {

        private static final boolean DEBUG = Boolean.getBoolean("DEBUG");
        private final static ScopedValue<Appendable> OUT = ScopedValue.newInstance();
        private final static ScopedValue.Carrier LOG = setupLog();

        private static ScopedValue.Carrier setupLog() {
            if (DEBUG) {
                return ScopedValue.where(OUT, System.err);
            } else {
                return ScopedValue.where(OUT, new StringBuffer());
            }
        }

        // Return the log for this thread and clear the buffer.
        private static Appendable get() {
            var log = LOG.get(OUT);
            if (log instanceof StringBuffer sb)
                sb.setLength(0);
            return log;
        }

        // Printf to the log for this thread.
        private static void printf(String format, Object... args) {
            try {
                var log = LOG.get(OUT);
                log.append(format.formatted(args));
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            }
        }
    }
}
