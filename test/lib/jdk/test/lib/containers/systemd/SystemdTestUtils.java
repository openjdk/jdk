/*
 * Copyright (c) 2024, Red Hat, Inc.
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

package jdk.test.lib.containers.systemd;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;

public class SystemdTestUtils {

    private static final Path SYSTEMD_CONFIG_HOME = Path.of("/", "etc", "systemd", "system");

    // Specifies how many lines to copy from child STDOUT to main test output.
    // Having too many lines in the main test output will result
    // in JT harness trimming the output, and can lead to loss of useful
    // diagnostic information.
    private static final int MAX_LINES_TO_COPY_FOR_CHILD_STDOUT = 100;

    public record ResultFiles(Path memory, Path cpu) {}

    /**
     * Run Java inside a systemd slice with specified parameters and options.
     *
     * @param opts The systemd slice options when running java
     * @return
     * @throws Exception
     */
    public static OutputAnalyzer systemdRunJava(SystemdRunOptions opts) throws Exception {
        return execute(buildJavaCommand(opts));
    }

    /**
     * Create commonly used options with the class to be launched inside the
     * systemd slice
     *
     * @param testClass The test class or {@code -version}
     * @return The basic options.
     */
    public static SystemdRunOptions newOpts(String testClass) {
        return new SystemdRunOptions(testClass,
                                     "-Xlog:os+container=trace",
                                     "-cp",
                                     Utils.TEST_CLASSES);
    }

    /**
     * Create systemd slice files under /etc/systemd/system.
     *
     * The JDK will then run within that slice as provided by the SystemdRunOptions.
     *
     * @param runOpts The systemd slice options to use when running the test.
     * @return The systemd slice files (for cleanup-purposes later).
     * @throws Exception
     */
    public static ResultFiles buildSystemdSlices(SystemdRunOptions runOpts) throws Exception {
        String sliceName = sliceName(runOpts);
        String sliceNameCpu = sliceNameCpu(runOpts);

        // Generate systemd slices for cpu/memory
        String memorySliceContent = getMemorySlice(runOpts, sliceName);
        String cpuSliceContent = getCpuSlice(runOpts, sliceName);

        Path memory, cpu;
        try {
            // memory slice
            memory = SYSTEMD_CONFIG_HOME.resolve(Path.of(sliceFileName(sliceName)));
            cpu = SYSTEMD_CONFIG_HOME.resolve(Path.of(sliceFileName(sliceNameCpu)));
            Files.writeString(memory, memorySliceContent);
            Files.writeString(cpu, cpuSliceContent);
        } catch (IOException e) {
            throw new AssertionError("Failed to write systemd slice files");
        }

        systemdDaemonReload(cpu);

        return new ResultFiles(memory, cpu);
    }

    private static String sliceName(SystemdRunOptions runOpts) {
        // Slice name may include '-' which is a hierarchical slice indicator.
        // Replace '-' with '_' to avoid side-effects.
        return "jdk_internal_" + runOpts.sliceName.replace("-", "_");
    }

    private static String sliceNameCpu(SystemdRunOptions runOpts) {
        String slice = sliceName(runOpts);
        return String.format("%s-cpu", slice);
    }

    private static void systemdDaemonReload(Path cpu) throws Exception {
        List<String> daemonReload = List.of("systemctl", "daemon-reload");
        List<String> restartSlice = List.of("systemctl", "restart", cpu.getFileName().toString());

        if (execute(daemonReload).getExitValue() != 0) {
            throw new AssertionError("Failed to reload systemd daemon");
        }
        if (execute(restartSlice).getExitValue() != 0) {
            throw new AssertionError("Failed to restart the systemd slice");
        }
    }

    private static String getCpuSlice(SystemdRunOptions runOpts, String sliceName) {
        String basicSliceFormat = getBasicSliceFormat();
        return String.format(basicSliceFormat, sliceName, getMemoryCPUSliceContent(runOpts));
    }

    private static Object getMemoryCPUSliceContent(SystemdRunOptions runOpts) {
        String format =
                """
                CPUAccounting=true
                CPUQuota=%s
                """;
         return String.format(format, runOpts.cpuLimit);
    }

    private static String getMemorySlice(SystemdRunOptions runOpts, String sliceName) {
        String basicSliceFormat = getBasicSliceFormat();
        return String.format(basicSliceFormat, sliceName, getMemorySliceContent(runOpts));
    }

    private static Object getMemorySliceContent(SystemdRunOptions runOpts) {
        String format =
               """
               MemoryAccounting=true
               MemoryLimit=%s
               """;
        return String.format(format, runOpts.memoryLimit);
    }

    private static String getBasicSliceFormat() {
        return """
               [Unit]
               Description=OpenJDK Tests Slice for %s
               Before=slices.target

               [Slice]
               %s
               """;
    }

    private static String sliceFileName(String sliceName) {
        return String.format("%s.slice", sliceName);
    }

    /**
     * Build the java command to run inside a systemd slice
     *
     * @param SystemdRunOptions options for running the systemd slice test
     *
     * @return command
     * @throws Exception
     */
    private static List<String> buildJavaCommand(SystemdRunOptions opts) throws Exception {
        List<String> javaCmd = new ArrayList<>();
        // systemd-run --slice <slice-name>.slice --scope <java>
        javaCmd.add("systemd-run");
        javaCmd.add("--slice");
        javaCmd.add(sliceFileName(sliceNameCpu(opts)));
        javaCmd.add("--scope");
        javaCmd.add(Path.of(Utils.TEST_JDK, "bin", "java").toString());
        javaCmd.addAll(opts.javaOpts);
        javaCmd.add(opts.classToRun);
        javaCmd.addAll(opts.classParams);
        return javaCmd;
    }

    /**
     * Execute a specified command in a process, report diagnostic info.
     *
     * @param command to be executed
     * @return The output from the process
     * @throws Exception
     */
    private static OutputAnalyzer execute(List<String> command) throws Exception {
        return execute(command.toArray(String[]::new));
    }

    /**
     * Execute a specified command in a process, report diagnostic info.
     *
     * @param command to be executed
     * @return The output from the process
     * @throws Exception
     */
    private static OutputAnalyzer execute(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        System.out.println("[COMMAND]\n" + Utils.getCommandLine(pb));

        Process p = pb.start();
        long pid = p.pid();
        OutputAnalyzer output = new OutputAnalyzer(p);

        int max = MAX_LINES_TO_COPY_FOR_CHILD_STDOUT;
        String stdout = output.getStdout();
        String stdoutLimited = limitLines(stdout, max);
        System.out.println("[STDERR]\n" + output.getStderr());
        System.out.println("[STDOUT]\n" + stdoutLimited);
        if (stdout != stdoutLimited) {
            System.out.printf("Child process STDOUT is limited to %d lines\n",
                              max);
        }

        String stdoutLogFile = String.format("systemd-stdout-%d.log", pid);
        writeOutputToFile(stdout, stdoutLogFile);
        System.out.println("Full child process STDOUT was saved to " + stdoutLogFile);

        return output;
    }

    private static void writeOutputToFile(String output, String fileName) throws Exception {
        try (FileWriter fw = new FileWriter(fileName)) {
            fw.write(output, 0, output.length());
        }
    }

    private static String limitLines(String buffer, int nrOfLines) {
        List<String> l = Arrays.asList(buffer.split("\\R"));
        if (l.size() < nrOfLines) {
            return buffer;
        }

        return String.join("\n", l.subList(0, nrOfLines));
    }
}
