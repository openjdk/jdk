/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import sun.management.VMManagement;

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
     * @param name The process name
     * @param processBuilder The process builder
     * @return Returns the initialized process
     * @throws IOException
     */
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder)
    throws IOException {
        return startProcess(name, processBuilder, (Consumer)null);
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>It is possible to monitor the in-streams via the provided {@code consumer}
     * @param name The process name
     * @param consumer {@linkplain Consumer} instance to process the in-streams
     * @param processBuilder The process builder
     * @return Returns the initialized process
     * @throws IOException
     */
    public static Process startProcess(String name,
                                       ProcessBuilder processBuilder,
                                       Consumer<String> consumer)
    throws IOException {
        Process p = null;
        try {
            p = startProcess(
                name,
                processBuilder,
                line -> {
                    if (consumer != null) {
                        consumer.accept(line);
                    }
                    return false;
                },
                -1,
                TimeUnit.NANOSECONDS
            );
        } catch (InterruptedException | TimeoutException e) {
            // can't ever happen
        }
        return p;
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * It is possible to wait for the process to get to a warmed-up state
     * via {@linkplain Predicate} condition on the STDOUT
     * </p>
     * @param name The process name
     * @param processBuilder The process builder
     * @param linePredicate The {@linkplain Predicate} to use on the STDOUT
     *                      Used to determine the moment the target app is
     *                      properly warmed-up.
     *                      It can be null - in that case the warmup is skipped.
     * @param timeout The timeout for the warmup waiting; -1 = no wait; 0 = wait forever
     * @param unit The timeout {@linkplain TimeUnit}
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
        System.out.println("["+name+"]:" + processBuilder.command().stream().collect(Collectors.joining(" ")));
        Process p = processBuilder.start();
        StreamPumper stdout = new StreamPumper(p.getInputStream());
        StreamPumper stderr = new StreamPumper(p.getErrorStream());

        stdout.addPump(new LineForwarder(name, System.out));
        stderr.addPump(new LineForwarder(name, System.err));
        CountDownLatch latch = new CountDownLatch(1);
        if (linePredicate != null) {
            StreamPumper.LinePump pump = new StreamPumper.LinePump() {
                @Override
                protected void processLine(String line) {
                    if (latch.getCount() > 0 && linePredicate.test(line)) {
                        latch.countDown();
                    }
                }
            };
            stdout.addPump(pump);
            stderr.addPump(pump);
        } else {
            latch.countDown();
        }
        Future<Void> stdoutTask = stdout.process();
        Future<Void> stderrTask = stderr.process();

        try {
            if (timeout > -1) {
                if (timeout == 0) {
                    latch.await();
                } else {
                    if (!latch.await(Utils.adjustTimeout(timeout), unit)) {
                        throw new TimeoutException();
                    }
                }
            }
        } catch (TimeoutException | InterruptedException e) {
            System.err.println("Failed to start a process (thread dump follows)");
            for(Map.Entry<Thread, StackTraceElement[]> s : Thread.getAllStackTraces().entrySet()) {
                printStack(s.getKey(), s.getValue());
            }

            if (p.isAlive()) {
                p.destroyForcibly();
            }

            stdoutTask.cancel(true);
            stderrTask.cancel(true);
            throw e;
        }

        return p;
    }

    /**
     * <p>Starts a process from its builder.</p>
     * <span>The default redirects of STDOUT and STDERR are started</span>
     * <p>
     * It is possible to wait for the process to get to a warmed-up state
     * via {@linkplain Predicate} condition on the STDOUT
     * </p>
     * @param name The process name
     * @param processBuilder The process builder
     * @param linePredicate The {@linkplain Predicate} to use on the STDOUT
     *                      Used to determine the moment the target app is
     *                      properly warmed-up.
     *                      It can be null - in that case the warmup is skipped.
     * @return Returns the initialized {@linkplain Process}
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     */
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
    public static int getProcessId() throws Exception {

        // Get the current process id using a reflection hack
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        Field jvm = runtime.getClass().getDeclaredField("jvm");

        jvm.setAccessible(true);
        VMManagement mgmt = (sun.management.VMManagement) jvm.get(runtime);

        Method pid_method = mgmt.getClass().getDeclaredMethod("getProcessId");

        pid_method.setAccessible(true);

        int pid = (Integer) pid_method.invoke(mgmt);

        return pid;
    }

    /**
     * Get platform specific VM arguments (e.g. -d64 on 64bit Solaris)
     *
     * @return String[] with platform specific arguments, empty if there are
     *         none
     */
    public static String[] getPlatformSpecificVMArgs() {
        String osName = System.getProperty("os.name");
        String dataModel = System.getProperty("sun.arch.data.model");

        if (osName.equals("SunOS") && dataModel.equals("64")) {
            return new String[] { "-d64" };
        }

        return new String[] {};
    }

    /**
     * Create ProcessBuilder using the java launcher from the jdk to be tested
     * and with any platform specific arguments prepended
     */
    public static ProcessBuilder createJavaProcessBuilder(String... command)
            throws Exception {
        String javapath = JDKToolFinder.getJDKTool("java");

        ArrayList<String> args = new ArrayList<>();
        args.add(javapath);
        Collections.addAll(args, getPlatformSpecificVMArgs());
        Collections.addAll(args, command);

        // Reporting
        StringBuilder cmdLine = new StringBuilder();
        for (String cmd : args)
            cmdLine.append(cmd).append(' ');
        System.out.println("Command line: [" + cmdLine.toString() + "]");

        return new ProcessBuilder(args.toArray(new String[args.size()]));
    }

    private static void printStack(Thread t, StackTraceElement[] stack) {
        System.out.println("\t" +  t +
                           " stack: (length = " + stack.length + ")");
        if (t != null) {
            for (StackTraceElement stack1 : stack) {
                System.out.println("\t" + stack1);
            }
            System.out.println();
        }
    }

    /**
     * Executes a test jvm process, waits for it to finish and returns the process output.
     * The default jvm options from jtreg, test.vm.opts and test.java.opts, are added.
     * The java from the test.jdk is used to execute the command.
     *
     * The command line will be like:
     * {test.jdk}/bin/java {test.vm.opts} {test.java.opts} cmds
     *
     * The jvm process will have exited before this method returns.
     *
     * @param cmds User specifed arguments.
     * @return The output from the process.
     */
    public static OutputAnalyzer executeTestJvm(String... cmds) throws Exception {
        ProcessBuilder pb = createJavaProcessBuilder(Utils.addTestJavaOpts(cmds));
        return executeProcess(pb);
    }

    /**
     * Executes a process, waits for it to finish and returns the process output.
     * The process will have exited before this method returns.
     * @param pb The ProcessBuilder to execute.
     * @return The {@linkplain OutputAnalyzer} instance wrapping the process.
     */
    public static OutputAnalyzer executeProcess(ProcessBuilder pb) throws Exception {
        OutputAnalyzer output = null;
        Process p = null;
        boolean failed = false;
        try {
            p = pb.start();
            output = new OutputAnalyzer(p);
            p.waitFor();

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
     *
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
     * @param pb The executed process.
     * @param output The output from the process.
     */
    public static String getProcessLog(ProcessBuilder pb, OutputAnalyzer output) {
        String stderr = output == null ? "null" : output.getStderr();
        String stdout = output == null ? "null" : output.getStdout();
        String exitValue = output == null ? "null": Integer.toString(output.getExitValue());
        StringBuilder logMsg = new StringBuilder();
        final String nl = System.getProperty("line.separator");
        logMsg.append("--- ProcessLog ---" + nl);
        logMsg.append("cmd: " + getCommandLine(pb) + nl);
        logMsg.append("exitvalue: " + exitValue + nl);
        logMsg.append("stderr: " + stderr + nl);
        logMsg.append("stdout: " + stdout + nl);

        return logMsg.toString();
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
}
