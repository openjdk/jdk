/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

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
        Process p = null;
        try {
            p = startProcess(name, processBuilder, null, -1, TimeUnit.NANOSECONDS);
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
     * @param timeout The timeout for the warmup waiting
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
        Process p = processBuilder.start();
        StreamPumper stdout = new StreamPumper(p.getInputStream());
        StreamPumper stderr = new StreamPumper(p.getErrorStream());

        stdout.addPump(new LineForwarder(name, System.out));
        stderr.addPump(new LineForwarder(name, System.err));
        final Phaser phs = new Phaser(1);
        if (linePredicate != null) {
            stdout.addPump(new StreamPumper.LinePump() {
                @Override
                protected void processLine(String line) {
                    if (linePredicate.test(line)) {
                        if (phs.getRegisteredParties() > 0) {
                            phs.arriveAndDeregister();
                        }
                    }
                }
            });
        }
        Future<Void> stdoutTask = stdout.process();
        Future<Void> stderrTask = stderr.process();

        try {
            if (timeout > -1) {
                phs.awaitAdvanceInterruptibly(0, timeout, unit);
            }
        } catch (TimeoutException | InterruptedException e) {
            stdoutTask.cancel(true);
            stderrTask.cancel(true);
            throw e;
        }

        return p;
    }

    /**
     * Pumps stdout and stderr from running the process into a String.
     *
     * @param processBuilder
     *            ProcessHandler to run.
     * @return Output from process.
     * @throws IOException
     *             If an I/O error occurs.
     */
    public static OutputBuffer getOutput(ProcessBuilder processBuilder)
            throws IOException {
        return getOutput(processBuilder.start());
    }

    /**
     * Pumps stdout and stderr the running process into a String.
     *
     * @param process
     *            Process to pump.
     * @return Output from process.
     * @throws IOException
     *             If an I/O error occurs.
     */
    public static OutputBuffer getOutput(Process process) throws IOException {
        ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
        StreamPumper outPumper = new StreamPumper(process.getInputStream(),
                stdoutBuffer);
        StreamPumper errPumper = new StreamPumper(process.getErrorStream(),
                stderrBuffer);

        Future<Void> outTask = outPumper.process();
        Future<Void> errTask = errPumper.process();

        try {
            process.waitFor();
            outTask.get();
            errTask.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            throw new IOException(e);
        }

        return new OutputBuffer(stdoutBuffer.toString(),
                stderrBuffer.toString());
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

}
