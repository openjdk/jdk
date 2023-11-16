/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.process;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ProcessTools {
    private static final class LineForwarder extends StreamPumper.LinePump {
        private final PrintStream ps;
        private final String prefix;

        LineForwarder(String prefix, PrintStream os) {
            this.ps = os;
            this.prefix = prefix;
        }

        @Override
        protected void processLine(String line) {
            ps.println("[" + prefix + "] " + line);
        }
    }
    private ProcessTools() {
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * Same as
     * {@linkplain #startProcess(String, ProcessBuilder, Consumer, Predicate, long, TimeUnit) startProcess}
     * {@code (name, processBuilder, null, null, -1, TimeUnit.NANOSECONDS)}
     * </p>
     * @param name           The process name
     * @param processBuilder The process builder
     * @return Returns the initialized process
     * @throws IOException
     */
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder)
            throws IOException {
        return startProcess(name, processBuilder, (Consumer<String>) null);
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * Same as
     * {@linkplain #startProcess(String, ProcessBuilder, Consumer, Predicate, long, TimeUnit) startProcess}
     * {@code (name, processBuilder, consumer, null, -1, TimeUnit.NANOSECONDS)}
     * </p>
     *
     * @param name           The process name
     * @param processBuilder The process builder
     * @param consumer       {@linkplain Consumer} instance to process the in-streams
     * @return Returns the initialized process
     * @throws IOException
     */
    @SuppressWarnings("overloads")
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       Consumer<String> consumer)
            throws IOException {
        try {
            return startProcess(name, processBuilder, consumer, null, -1, TimeUnit.NANOSECONDS);
        } catch (InterruptedException | TimeoutException | CancellationException e) {
            // will never happen
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * Same as
     * {@linkplain #startProcess(String, ProcessBuilder, Consumer, Predicate, long, TimeUnit) startProcess}
     * {@code (name, processBuilder, null, linePredicate, timeout, unit)}
     * </p>
     *
     * @param name           The process name
     * @param processBuilder The process builder
     * @param linePredicate  The {@linkplain Predicate} to use on the STDOUT and STDERR.
     *                       Used to determine the moment the target app is
     *                       properly warmed-up.
     *                       It can be null - in that case the warmup is skipped.
     * @param timeout        The timeout for the warmup waiting; -1 = no wait; 0 = wait forever
     * @param unit           The timeout {@linkplain TimeUnit}
     * @return Returns the initialized {@linkplain Process}
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     */
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       final Predicate<String> linePredicate,
                                       long timeout,
                                       TimeUnit unit)
            throws IOException, InterruptedException, TimeoutException {
        return startProcess(name, processBuilder, null, linePredicate, timeout, unit);
    }


    /*
        BufferOutputStream and BufferInputStream allow to re-use p.getInputStream() amd p.getOutputStream() of
        processes started with ProcessTools.startProcess(...).
        Implementation cashes ALL process output and allow to read it through InputStream.
        The stream uses  Future<Void> task from StreamPumper.process() to check if output is complete.
     */
    private static class BufferOutputStream extends ByteArrayOutputStream {
        private int current = 0;
        final private Process p;

        private Future<Void> task;

        public BufferOutputStream(Process p) {
            this.p = p;
        }

        synchronized void setTask(Future<Void> task) {
            this.task = task;
        }
        synchronized int readNext() {
            if (current > count) {
                throw new RuntimeException("Shouldn't ever happen.  start: "
                        + current + " count: " + count + " buffer: " + this);
            }
            while (current == count) {
                if (!p.isAlive() && (task != null)) {
                    try {
                        task.get(10, TimeUnit.MILLISECONDS);
                        if (current == count) {
                            return -1;
                        }
                    } catch (TimeoutException e) {
                        // continue execution, so wait() give a chance to write
                    } catch (InterruptedException | ExecutionException e) {
                        return -1;
                    }
                }
                try {
                    wait(1);
                } catch (InterruptedException ie) {
                    return -1;
                }
            }
            return this.buf[current++];
        }
    }

    private static class BufferInputStream extends InputStream {

        private final BufferOutputStream buffer;

        public BufferInputStream(Process p) {
            buffer = new BufferOutputStream(p);
        }

        BufferOutputStream getOutputStream() {
            return buffer;
        }

        @Override
        public int read() throws IOException {
            return buffer.readNext();
        }
    }


    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * It is possible to wait for the process to get to a warmed-up state
     * via {@linkplain Predicate} condition on the STDOUT/STDERR and monitor the
     * in-streams via the provided {@linkplain Consumer}
     * </p>
     *
     * @param name           The process name
     * @param processBuilder The process builder
     * @param lineConsumer   The {@linkplain Consumer} the lines will be forwarded to
     * @param linePredicate  The {@linkplain Predicate} to use on the STDOUT and STDERR.
     *                       Used to determine the moment the target app is
     *                       properly warmed-up.
     *                       It can be null - in that case the warmup is skipped.
     * @param timeout        The timeout for the warmup waiting; -1 = no wait; 0 = wait forever
     * @param unit           The timeout {@linkplain TimeUnit}
     * @return Returns the initialized {@linkplain Process}
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     */
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       final Consumer<String> lineConsumer,
                                       final Predicate<String> linePredicate,
                                       long timeout,
                                       TimeUnit unit)
            throws IOException, InterruptedException, TimeoutException {
        System.out.println("[" + name + "]:" + String.join(" ", processBuilder.command()));
        Process p = privilegedStart(processBuilder);
        StreamPumper stdout = new StreamPumper(p.getInputStream());
        StreamPumper stderr = new StreamPumper(p.getErrorStream());

        stdout.addPump(new LineForwarder(name, System.out));
        stderr.addPump(new LineForwarder(name, System.err));


        BufferInputStream stdOut = new BufferInputStream(p);
        BufferInputStream stdErr = new BufferInputStream(p);

        stdout.addOutputStream(stdOut.getOutputStream());
        stderr.addOutputStream(stdErr.getOutputStream());

        if (lineConsumer != null) {
            StreamPumper.LinePump pump = new StreamPumper.LinePump() {
                @Override
                protected void processLine(String line) {
                    lineConsumer.accept(line);
                }
            };
            stdout.addPump(pump);
            stderr.addPump(pump);
        }

        CountDownLatch latch = new CountDownLatch(1);
        if (linePredicate != null) {
            StreamPumper.LinePump pump = new StreamPumper.LinePump() {
                // synchronization between stdout and stderr pumps
                private final Object sync = new Object();
                @Override
                protected void processLine(String line) {
                    synchronized (sync) {
                        if (latch.getCount() > 0 && linePredicate.test(line)) {
                            latch.countDown();
                        }
                    }
                }
            };
            stdout.addPump(pump);
            stderr.addPump(pump);
        } else {
            latch.countDown();
        }
        final Future<Void> stdoutTask = stdout.process();
        final Future<Void> stderrTask = stderr.process();

        stdOut.getOutputStream().setTask(stdoutTask);
        stdErr.getOutputStream().setTask(stderrTask);

        try {
            if (timeout > -1) {

                long timeoutMs = timeout == 0 ? -1: unit.toMillis(Utils.adjustTimeout(timeout));
                // Every second check if line is printed and if process is still alive
                Utils.waitForCondition(() -> latch.getCount() == 0 || !p.isAlive(),
                       timeoutMs , 1000);

                if (latch.getCount() > 0) {
                    if (!p.isAlive()) {
                        // Give some extra time for the StreamPumper to run after the process completed
                        Thread.sleep(1000);
                        if (latch.getCount() > 0) {
                            throw new RuntimeException("Started process " + name + " terminated before producing the expected output.");
                        }
                    } else {
                        throw new TimeoutException();
                    }
                }
            }
        } catch (TimeoutException | RuntimeException | InterruptedException e) {
            System.err.println("Failed to start a process (thread dump follows)");
            for (Map.Entry<Thread, StackTraceElement[]> s : Thread.getAllStackTraces().entrySet()) {
                printStack(s.getKey(), s.getValue());
            }

            if (p.isAlive()) {
                p.destroyForcibly();
            }

            stdoutTask.cancel(true);
            stderrTask.cancel(true);
            throw e;
        }

        return new ProcessImpl(p, stdoutTask, stderrTask, stdOut, stdErr);
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * It is possible to wait for the process to get to a warmed-up state
     * via {@linkplain Predicate} condition on the STDOUT/STDERR.
     * The warm-up will wait indefinitely.
     * </p>
     *
     * @param name           The process name
     * @param processBuilder The process builder
     * @param linePredicate  The {@linkplain Predicate} to use on the STDOUT and STDERR.
     *                       Used to determine the moment the target app is
     *                       properly warmed-up.
     *                       It can be null - in that case the warmup is skipped.
     * @return Returns the initialized {@linkplain Process}
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     */
    @SuppressWarnings("overloads")
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       final Predicate<String> linePredicate)
            throws IOException, InterruptedException, TimeoutException {
        return startProcess(name, processBuilder, linePredicate, 0, TimeUnit.SECONDS);
    }

    /**
     * Get the process id of the current running Java process
     *
     * @return Process id
     */
    public static long getProcessId() throws Exception {
        return ProcessHandle.current().pid();
    }

    /*
      Convert arguments for tests running with virtual threads test thread factory.
      When test is executed with test thread factory the line is changed from
      java <jvm-args> <test-class> <test-args>
      to
      java <jvm-args> -Dtest.thread.factory=<test-thread-factory-name> jdk.test.lib.process.ProcessTools <test-thread-factory-name> <test-class> <test-args>
     */

    private static List<String> addTestThreadFactoryArgs(String testThreadFactoryName, List<String> command) {

        final List<String> unsupportedArgs = List.of(
                "-jar", "-cp", "-classpath", "--class-path", "--describe-module", "-d",
                "--dry-run", "--list-modules","--validate-modules", "-m", "--module", "-version");

        final List<String> doubleWordArgs = List.of(
                "--add-opens", "--upgrade-module-path", "--add-modules", "--add-exports",
                "--limit-modules", "--add-reads", "--patch-module", "--module-path", "-p");

        ArrayList<String> args = new ArrayList<>();

        boolean expectSecondArg = false;
        boolean isTestThreadFactoryAdded = false;
        for (String cmd : command) {
            if (isTestThreadFactoryAdded) {
                args.add(cmd);
                continue;
            }

            if (expectSecondArg) {
                expectSecondArg = false;
                args.add(cmd);
                continue;
            }
            if (unsupportedArgs.contains(cmd)) {
                return command;
            }
            if (doubleWordArgs.contains(cmd)) {
                expectSecondArg = true;
                args.add(cmd);
                continue;
            }
            if (expectSecondArg) {
                continue;
            }
            // command-line or name command-line file
            if (cmd.startsWith("-") || cmd.startsWith("@")) {
                args.add(cmd);
                continue;
            }

            // if command is like 'java source.java' then return
            if (cmd.endsWith(".java")) {
                return command;
            }
            // Some tests might check property to understand
            // if virtual threads are tested
            args.add("-Dtest.thread.factory=" + testThreadFactoryName);
            args.add("jdk.test.lib.process.ProcessTools");
            args.add(testThreadFactoryName);
            isTestThreadFactoryAdded = true;
            args.add(cmd);
        }
        return args;
    }

    /**
     * Create ProcessBuilder using the java launcher from the jdk to be tested.
     *
     * @param command Arguments to pass to the java command.
     * @return The ProcessBuilder instance representing the java command.
     */
    private static ProcessBuilder createJavaProcessBuilder(String... command) {
        String javapath = JDKToolFinder.getJDKTool("java");

        ArrayList<String> args = new ArrayList<>();
        args.add(javapath);

        String noCPString = System.getProperty("test.noclasspath", "false");
        boolean noCP = Boolean.valueOf(noCPString);
        if (!noCP) {
            args.add("-cp");
            args.add(System.getProperty("java.class.path"));
        }

        String testThreadFactoryName = System.getProperty("test.thread.factory");
        if (testThreadFactoryName != null) {
            args.addAll(addTestThreadFactoryArgs(testThreadFactoryName, Arrays.asList(command)));
        } else {
            Collections.addAll(args, command);
        }

        // Reporting
        StringBuilder cmdLine = new StringBuilder();
        for (String cmd : args)
            cmdLine.append(cmd).append(' ');
        System.out.println("Command line: [" + cmdLine.toString() + "]");

        ProcessBuilder pb = new ProcessBuilder(args);
        if (noCP) {
            // clear CLASSPATH from the env
            pb.environment().remove("CLASSPATH");
        }
        return pb;
    }

    private static void printStack(Thread t, StackTraceElement[] stack) {
        System.out.println("\t" + t + " stack: (length = " + stack.length + ")");
        if (t != null) {
            for (StackTraceElement stack1 : stack) {
                System.out.println("\t" + stack1);
            }
            System.out.println();
        }
    }

    /**
     * Create ProcessBuilder using the java launcher from the jdk to
     * be tested. The default jvm options from jtreg, test.vm.opts and
     * test.java.opts, are added.
     *
     * <p>Unless the "test.noclasspath" property is "true" the
     * classpath property "java.class.path" is appended to the command
     * line and the environment of the ProcessBuilder is modified to
     * remove "CLASSPATH". If the property "test.thread.factory" is
     * provided the command args are updated and appended to invoke
     * ProcessTools main() and provide the name of the thread factory.
     *
     * <p>The "-Dtest.thread.factory" is appended to the arguments
     * with the thread factory value. The remaining command args are
     * scanned for unsupported options and are appended to the
     * ProcessBuilder.
     *
     * @param command Arguments to pass to the java command.
     * @return The ProcessBuilder instance representing the java command.
     */
    public static ProcessBuilder createTestJavaProcessBuilder(List<String> command) {
        return createTestJavaProcessBuilder(command.toArray(String[]::new));
    }

    /**
     * Create ProcessBuilder using the java launcher from the jdk to
     * be tested. The default jvm options from jtreg, test.vm.opts and
     * test.java.opts, are added.
     *
     * <p>Unless the "test.noclasspath" property is "true" the
     * classpath property "java.class.path" is appended to the command
     * line and the environment of the ProcessBuilder is modified to
     * remove "CLASSPATH". If the property "test.thread.factory" is
     * provided the command args are updated and appended to invoke
     * ProcessTools main() and provide the name of the thread factory.
     *
     * <p>The "-Dtest.thread.factory" is appended to the arguments
     * with the thread factory value. The remaining command args are
     * scanned for unsupported options and are appended to the
     * ProcessBuilder.
     *
     * @param command Arguments to pass to the java command.
     * @return The ProcessBuilder instance representing the java command.
     */
    public static ProcessBuilder createTestJavaProcessBuilder(String... command) {
        return createJavaProcessBuilder(Utils.prependTestJavaOpts(command));
    }

    /**
     * Create ProcessBuilder using the java launcher from the jdk to
     * be tested.
     *
     * <p><b>Please observe that you likely should use
     * createTestJavaProcessBuilder() instead of this method because
     * createTestJavaProcessBuilder() will add JVM options from
     * "test.vm.opts" and "test.java.opts"</b> and this method will
     * not do that.
     *
     * <p>If you still chose to use
     * createLimitedTestJavaProcessBuilder() you should probably use
     * it in combination with <b>@requires vm.flagless</b> JTREG
     * anotation as to not waste energy and test resources.
     *
     * <p>Unless the "test.noclasspath" property is "true" the
     * classpath property "java.class.path" is appended to the command
     * line and the environment of the ProcessBuilder is modified to
     * remove "CLASSPATH". If the property "test.thread.factory" is
     * provided the command args are updated and appended to invoke
     * ProcessTools main() and provide the name of the thread factory.
     *
     * <p>The "-Dtest.thread.factory" is appended to the arguments
     * with the thread factory value. The remaining command args are
     * scanned for unsupported options and are appended to the
     * ProcessBuilder.
     *
     * @param command Arguments to pass to the java command.
     * @return The ProcessBuilder instance representing the java command.
     */
    public static ProcessBuilder createLimitedTestJavaProcessBuilder(List<String> command) {
        return createLimitedTestJavaProcessBuilder(command.toArray(String[]::new));
    }

   /**
     * Create ProcessBuilder using the java launcher from the jdk to
     * be tested.
     *
     * <p><b>Please observe that you likely should use
     * createTestJavaProcessBuilder() instead of this method because
     * createTestJavaProcessBuilder() will add JVM options from
     * "test.vm.opts" and "test.java.opts"</b> and this method will
     * not do that.
     *
     * <p>If you still chose to use
     * createLimitedTestJavaProcessBuilder() you should probably use
     * it in combination with <b>@requires vm.flagless</b> JTREG
     * anotation as to not waste energy and test resources.
     *
     * <p>Unless the "test.noclasspath" property is "true" the
     * classpath property "java.class.path" is appended to the command
     * line and the environment of the ProcessBuilder is modified to
     * remove "CLASSPATH". If the property "test.thread.factory" is
     * provided the command args are updated and appended to invoke
     * ProcessTools main() and provide the name of the thread factory.
     *
     * <p>The "-Dtest.thread.factory" is appended to the arguments
     * with the thread factory value. The remaining command args are
     * scanned for unsupported options and are appended to the
     * ProcessBuilder.
     *
     * @param command Arguments to pass to the java command.
     * @return The ProcessBuilder instance representing the java command.
     */
    public static ProcessBuilder createLimitedTestJavaProcessBuilder(String... command) {
        return createJavaProcessBuilder(command);
    }

    /**
     * Executes a test jvm process, waits for it to finish and returns
     * the process output.
     *
     * <p>The process is created using runtime flags set up by:
     * {@link #createTestJavaProcessBuilder(String...)}. The
     * jvm process will have exited before this method returns.
     *
     * @param cmds User specified arguments.
     * @return The output from the process.
     */
    public static OutputAnalyzer executeTestJvm(List<String> cmds) throws Exception {
        return executeTestJvm(cmds.toArray(String[]::new));
    }

    /**
     * Executes a test jvm process, waits for it to finish and returns
     * the process output.
     *
     * <p>The process is created using runtime flags set up by:
     * {@link #createTestJavaProcessBuilder(String...)}. The
     * jvm process will have exited before this method returns.
     *
     * @param cmds User specified arguments.
     * @return The output from the process.
     */
    public static OutputAnalyzer executeTestJvm(String... cmds) throws Exception {
        ProcessBuilder pb = createTestJavaProcessBuilder(cmds);
        return executeProcess(pb);
    }

    /**
     * @param cmds User specified arguments.
     * @return The output from the process.
     * @see #executeTestJvm(String...)
     */
    public static OutputAnalyzer executeTestJava(String... cmds) throws Exception {
        return executeTestJvm(cmds);
    }

    /**
     * Executes a process, waits for it to finish and returns the process output.
     * The process will have exited before this method returns.
     *
     * @param pb The ProcessBuilder to execute.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    public static OutputAnalyzer executeProcess(ProcessBuilder pb) throws Exception {
        return executeProcess(pb, null);
    }

    /**
     * Executes a process, pipe some text into its STDIN, waits for it
     * to finish and returns the process output. The process will have exited
     * before this method returns.
     *
     * @param pb    The ProcessBuilder to execute.
     * @param input The text to pipe into STDIN. Can be null.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    public static OutputAnalyzer executeProcess(ProcessBuilder pb, String input) throws Exception {
        return executeProcess(pb, input, null);
    }

    /**
     * Executes a process, pipe some text into its STDIN, waits for it
     * to finish and returns the process output. The process will have exited
     * before this method returns.
     *
     * @param pb    The ProcessBuilder to execute.
     * @param input The text to pipe into STDIN. Can be null.
     * @param cs    The charset used to convert from bytes to chars or null for
     *              the default charset.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    @SuppressWarnings("removal")
    public static OutputAnalyzer executeProcess(ProcessBuilder pb, String input,
                                                Charset cs) throws Exception {
        OutputAnalyzer output = null;
        Process p = null;
        boolean failed = false;
        try {
            p = privilegedStart(pb);
            if (input != null) {
                try (PrintStream ps = new PrintStream(p.getOutputStream())) {
                    ps.print(input);
                }
            }

            output = new OutputAnalyzer(p, cs);
            p.waitFor();

            {   // Dumping the process output to a separate file
                var fileName = String.format("pid-%d-output.log", p.pid());
                var processOutput = getProcessLog(pb, output);
                AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
                    Files.writeString(Path.of(fileName), processOutput);
                    return null;
                });
                System.out.printf(
                        "Output and diagnostic info for process %d " +
                                "was saved into '%s'%n", p.pid(), fileName);
            }

            return output;
        } catch (Throwable t) {
            if (p != null) {
                p.destroyForcibly().waitFor();
            }

            failed = true;
            System.out.println("executeProcess() failed: " + t);
            throw t;
        } finally {
            if (failed) {
                System.err.println(getProcessLog(pb, output));
            }
        }
    }

    /**
     * Executes a process, waits for it to finish and returns the process output.
     * <p>
     * The process will have exited before this method returns.
     *
     * @param cmds The command line to execute.
     * @return The output from the process.
     */
    public static OutputAnalyzer executeProcess(String... cmds) throws Throwable {
        return executeProcess(new ProcessBuilder(cmds));
    }

    /**
     * Used to log command line, stdout, stderr and exit code from an executed process.
     *
     * @param pb     The executed process.
     * @param output The output from the process.
     */
    public static String getProcessLog(ProcessBuilder pb, OutputAnalyzer output) {
        String stderr = output == null ? "null" : output.getStderr();
        String stdout = output == null ? "null" : output.getStdout();
        String exitValue = output == null ? "null" : Integer.toString(output.getExitValue());
        return String.format("--- ProcessLog ---%n" +
                             "cmd: %s%n" +
                             "exitvalue: %s%n" +
                             "stderr: %s%n" +
                             "stdout: %s%n",
                             getCommandLine(pb), exitValue, stderr, stdout);
    }

    /**
     * @return The full command line for the ProcessBuilder.
     */
    public static String getCommandLine(ProcessBuilder pb) {
        if (pb == null) {
            return "null";
        }
        StringBuilder cmd = new StringBuilder();
        for (String s : pb.command()) {
            cmd.append(s).append(" ");
        }
        return cmd.toString().trim();
    }

    /**
     * Executes a process, waits for it to finish, prints the process output
     * to stdout, and returns the process output.
     * <p>
     * The process will have exited before this method returns.
     *
     * @param cmds The command line to execute.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    public static OutputAnalyzer executeCommand(String... cmds)
            throws Throwable {
        String cmdLine = String.join(" ", cmds);
        System.out.println("Command line: [" + cmdLine + "]");
        OutputAnalyzer analyzer = ProcessTools.executeProcess(cmds);
        System.out.println(analyzer.getOutput());
        return analyzer;
    }

    /**
     * Executes a process, waits for it to finish, prints the process output
     * to stdout and returns the process output.
     * <p>
     * The process will have exited before this method returns.
     *
     * @param pb The ProcessBuilder to execute.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    public static OutputAnalyzer executeCommand(ProcessBuilder pb)
            throws Throwable {
        String cmdLine = pb.command().stream()
                .map(x -> (x.contains(" ") || x.contains("$"))
                        ? ("'" + x + "'") : x)
                .collect(Collectors.joining(" "));
        System.out.println("Command line: [" + cmdLine + "]");
        OutputAnalyzer analyzer = ProcessTools.executeProcess(pb);
        System.out.println(analyzer.getOutput());
        return analyzer;
    }

    /**
     * Helper method to create a process builder for launching native executable
     * test that uses/loads JVM.
     *
     * @param executableName The name of an executable to be launched.
     * @param args           Arguments for the executable.
     * @return New ProcessBuilder instance representing the command.
     */
    public static ProcessBuilder createNativeTestProcessBuilder(String executableName,
                                                                String... args) throws Exception {
        executableName = Platform.isWindows() ? executableName + ".exe" : executableName;
        String executable = Paths.get(Utils.TEST_NATIVE_PATH, executableName)
                                 .toAbsolutePath()
                                 .toString();

        ProcessBuilder pb = new ProcessBuilder(executable);
        pb.command().addAll(Arrays.asList(args));
        return addJvmLib(pb);
    }

    /**
     * Adds JVM library path to the native library path.
     *
     * @param pb ProcessBuilder to be updated with JVM library path.
     * @return pb Update ProcessBuilder instance.
     */
    public static ProcessBuilder addJvmLib(ProcessBuilder pb) throws Exception {
        String jvmLibDir = Platform.jvmLibDir().toString();
        String libPathVar = Platform.sharedLibraryPathVariableName();
        String currentLibPath = pb.environment().get(libPathVar);

        String newLibPath = jvmLibDir;
        if (Platform.isWindows()) {
            String libDir = Platform.libDir().toString();
            newLibPath = newLibPath + File.pathSeparator + libDir;
        }
        if ((currentLibPath != null) && !currentLibPath.isEmpty()) {
            newLibPath = newLibPath + File.pathSeparator + currentLibPath;
        }

        pb.environment().put(libPathVar, newLibPath);

        return pb;
    }

    @SuppressWarnings("removal")
    private static Process privilegedStart(ProcessBuilder pb) throws IOException {
        try {
            return AccessController.doPrivileged(
                    (PrivilegedExceptionAction<Process>) pb::start);
        } catch (PrivilegedActionException e) {
            throw (IOException) e.getException();
        }
    }

    private static class ProcessImpl extends Process {

        private final InputStream stdOut;
        private final InputStream stdErr;
        private final Process p;
        private final Future<Void> stdoutTask;
        private final Future<Void> stderrTask;

        public ProcessImpl(Process p, Future<Void> stdoutTask, Future<Void> stderrTask,
                           InputStream stdOut, InputStream etdErr) {
            this.p = p;
            this.stdoutTask = stdoutTask;
            this.stderrTask = stderrTask;
            this.stdOut = stdOut;
            this.stdErr = etdErr;
        }

        @Override
        public OutputStream getOutputStream() {
            return p.getOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdOut;
        }

        @Override
        public InputStream getErrorStream() {
            return stdErr;
        }

        @Override
        public int waitFor() throws InterruptedException {
            int rslt = p.waitFor();
            waitForStreams();
            return rslt;
        }

        @Override
        public int exitValue() {
            return p.exitValue();
        }

        @Override
        public void destroy() {
            p.destroy();
        }

        @Override
        public long pid() {
            return p.pid();
        }

        @Override
        public boolean isAlive() {
            return p.isAlive();
        }

        @Override
        public Process destroyForcibly() {
            return p.destroyForcibly();
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            boolean rslt = p.waitFor(timeout, unit);
            if (rslt) {
                waitForStreams();
            }
            return rslt;
        }

        private void waitForStreams() throws InterruptedException {
            try {
                stdoutTask.get();
            } catch (ExecutionException e) {
            }
            try {
                stderrTask.get();
            } catch (ExecutionException e) {
            }
        }
    }

    public static final String OLD_MAIN_THREAD_NAME = "old-m-a-i-n";

    // ProcessTools as a wrapper for test execution
    // It executes method main in a separate virtual or platform thread
    public static void main(String[] args) throws Throwable {
        String testThreadFactoryName = args[0];
        String className = args[1];
        String[] classArgs = new String[args.length - 2];
        System.arraycopy(args, 2, classArgs, 0, args.length - 2);
        Class<?> c = Class.forName(className);
        Method mainMethod = c.getMethod("main", new Class[] { String[].class });
        mainMethod.setAccessible(true);

        if (testThreadFactoryName.equals("Virtual")) {
            // MainThreadGroup used just as a container for exceptions
            // when main is executed in virtual thread
            MainThreadGroup tg = new MainThreadGroup();
            Thread vthread = Thread.ofVirtual().unstarted(() -> {
                    try {
                        mainMethod.invoke(null, new Object[] { classArgs });
                    } catch (InvocationTargetException e) {
                        tg.uncaughtThrowable = e.getCause();
                    } catch (Throwable error) {
                        tg.uncaughtThrowable = error;
                    }
                });
            Thread.currentThread().setName(OLD_MAIN_THREAD_NAME);
            vthread.setName("main");
            vthread.start();
            vthread.join();
            if (tg.uncaughtThrowable != null) {
                throw tg.uncaughtThrowable;
            }
        } else if (testThreadFactoryName.equals("Kernel")) {
            MainThreadGroup tg = new MainThreadGroup();
            Thread t = new Thread(tg, () -> {
                    try {
                        mainMethod.invoke(null, new Object[] { classArgs });
                    } catch (InvocationTargetException e) {
                        tg.uncaughtThrowable = e.getCause();
                    } catch (Throwable error) {
                        tg.uncaughtThrowable = error;
                    }
                });
            t.start();
            t.join();
            if (tg.uncaughtThrowable != null) {
                throw tg.uncaughtThrowable;
            }
        } else {
            mainMethod.invoke(null, new Object[] { classArgs });
        }
    }

    static class MainThreadGroup extends ThreadGroup {
        MainThreadGroup() {
            super("MainThreadGroup");
        }

        public void uncaughtException(Thread t, Throwable e) {
            e.printStackTrace(System.err);
            uncaughtThrowable = e;
        }
        Throwable uncaughtThrowable = null;
    }
}
