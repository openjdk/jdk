/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @deprecated This class is deprecated. Use the one from
 *             {@code <root>/test/lib/share/classes/jdk/test/lib/process}
 */
@Deprecated
public final class ProcessTools {

  private ProcessTools() {
  }

  /**
   * Pumps stdout and stderr from running the process into a String.
   *
   * @param processBuilder ProcessBuilder to run.
   * @return Output from process.
   * @throws IOException If an I/O error occurs.
   */
  public static OutputBuffer getOutput(ProcessBuilder processBuilder) throws IOException {
    return getOutput(processBuilder.start());
  }

  /**
   * Pumps stdout and stderr the running process into a String.
   *
   * @param process Process to pump.
   * @return Output from process.
   * @throws IOException If an I/O error occurs.
   */
  public static OutputBuffer getOutput(Process process) throws IOException {
    ByteArrayOutputStream stderrBuffer = new ByteArrayOutputStream();
    ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();
    StreamPumper outPumper = new StreamPumper(process.getInputStream(), stdoutBuffer);
    StreamPumper errPumper = new StreamPumper(process.getErrorStream(), stderrBuffer);
    Thread outPumperThread = new Thread(outPumper);
    Thread errPumperThread = new Thread(errPumper);

    outPumperThread.setDaemon(true);
    errPumperThread.setDaemon(true);

    outPumperThread.start();
    errPumperThread.start();

    try {
      process.waitFor();
      outPumperThread.join();
      errPumperThread.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }

    return new OutputBuffer(stdoutBuffer.toString(), stderrBuffer.toString());
  }

  /**
   * Get the process id of the current running Java process
   *
   * @return Process id
   */
  public static int getProcessId() throws Exception {
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    int pid = Integer.parseInt(runtime.getName().split("@")[0]);

    return pid;
  }

  /**
   * Get the string containing input arguments passed to the VM
   *
   * @return arguments
   */
  public static String getVmInputArguments() {
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

    List<String> args = runtime.getInputArguments();
    StringBuilder result = new StringBuilder();
    for (String arg : args)
        result.append(arg).append(' ');

    return result.toString();
  }

  /**
   * Gets the array of strings containing input arguments passed to the VM
   *
   * @return arguments
   */
  public static String[] getVmInputArgs() {
    RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
    List<String> args = runtime.getInputArguments();
    return args.toArray(new String[args.size()]);
  }

  /**
   * Get platform specific VM arguments (e.g. -d64 on 64bit Solaris)
   *
   * @return String[] with platform specific arguments, empty if there are none
   */
  public static String[] getPlatformSpecificVMArgs() {

    if (Platform.is64bit() && Platform.isSolaris()) {
      return new String[] { "-d64" };
    }

    return new String[] {};
  }

  /**
   * Create ProcessBuilder using the java launcher from the jdk to be tested and
   * with any platform specific arguments prepended
   */
  public static ProcessBuilder createJavaProcessBuilder(String... command) throws Exception {
    return createJavaProcessBuilder(false, command);
  }

  public static ProcessBuilder createJavaProcessBuilder(boolean addTestVmAndJavaOptions, String... command) throws Exception {
    String javapath = JDKToolFinder.getJDKTool("java");

    ArrayList<String> args = new ArrayList<>();
    args.add(javapath);
    Collections.addAll(args, getPlatformSpecificVMArgs());

    args.add("-cp");
    args.add(System.getProperty("java.class.path"));

    if (addTestVmAndJavaOptions) {
      Collections.addAll(args, Utils.getTestJavaOpts());
    }

    Collections.addAll(args, command);

    // Reporting
    StringBuilder cmdLine = new StringBuilder();
    for (String cmd : args) {
      cmdLine.append(cmd).append(' ');
    }
    System.out.println("Command line: [" + cmdLine.toString() + "]");

    return new ProcessBuilder(args.toArray(new String[args.size()]));
  }

  /**
   * Executes a test jvm process, waits for it to finish and returns the process output.
   * The default jvm options from jtreg, test.vm.opts and test.java.opts, are added.
   * The java from the test.jdk is used to execute the command.
   *
   * The command line will be like:
   * {test.jdk}/bin/java {test.vm.opts} {test.java.opts} cmds
   *
   * @param cmds User specifed arguments.
   * @return The output from the process.
   */
  public static OutputAnalyzer executeTestJvm(String... cmds) throws Throwable {
    ProcessBuilder pb = createJavaProcessBuilder(Utils.addTestJavaOpts(cmds));
    return executeProcess(pb);
  }

  /**
   * Executes a test jvm process, waits for it to finish and returns the process output.
   * The default jvm options from the test's run command, jtreg, test.vm.opts and test.java.opts, are added.
   * The java from the test.jdk is used to execute the command.
   *
   * The command line will be like:
   * {test.jdk}/bin/java {test.fromRun.opts} {test.vm.opts} {test.java.opts} cmds
   *
   * @param cmds User specifed arguments.
   * @return The output from the process.
   */
  public static OutputAnalyzer executeTestJvmAllArgs(String... cmds) throws Throwable {
    List<String> argsList = new ArrayList<>();
    String[] testArgs = getVmInputArgs();
    Collections.addAll(argsList, testArgs);
    Collections.addAll(argsList, Utils.addTestJavaOpts(cmds));
    ProcessBuilder pb = createJavaProcessBuilder(argsList.toArray(new String[argsList.size()]));
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
