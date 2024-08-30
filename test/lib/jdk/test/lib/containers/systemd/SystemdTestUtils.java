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
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.internal.platform.Metrics;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.FileUtils;
import jtreg.SkippedException;

public class SystemdTestUtils {

    private static final String CGROUPS_PROVIDER = Metrics.systemMetrics().getProvider();
    private static boolean CGROUPS_V2 = "cgroupv2".equals(CGROUPS_PROVIDER);
    private static boolean RUN_AS_USER = !Platform.isRoot() && CGROUPS_V2;
    private static final String SLICE_NAMESPACE_PREFIX = "jdk_internal";
    private static final String SLICE_D_MEM_CONFIG_FILE = "memory-limit.conf";
    private static final String SLICE_D_CPU_CONFIG_FILE = "cpu-limit.conf";
    private static final String USER_HOME = System.getProperty("user.home");
    private static final Path SYSTEMD_CONFIG_HOME_ROOT = Path.of("/", "etc", "systemd", "system");
    private static final Path SYSTEMD_CONFIG_HOME_USER = Path.of(USER_HOME, ".config", "systemd", "user");
    private static final Path SYSTEMD_CONFIG_HOME = Platform.isRoot() ? SYSTEMD_CONFIG_HOME_ROOT : SYSTEMD_CONFIG_HOME_USER;

    // Specifies how many lines to copy from child STDOUT to main test output.
    // Having too many lines in the main test output will result
    // in JT harness trimming the output, and can lead to loss of useful
    // diagnostic information.
    private static final int MAX_LINES_TO_COPY_FOR_CHILD_STDOUT = 100;

    public record ResultFiles(Path memory, Path cpu, Path sliceDotDDir) {}

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
     * Run Java inside a systemd slice with specified parameters and options.
     *
     * @param opts The systemd slice options when running java
     * @return An OutputAnalyzer of the output of the command than ran.
     * @throws Exception If something went wrong.
     * @throws SkippedException If the test cannot be run (i.e. non-root user
     *         on cgroups v1).
     */
    public static OutputAnalyzer buildAndRunSystemdJava(SystemdRunOptions opts) throws Exception, SkippedException {
        if (!Platform.isRoot() && !CGROUPS_V2) {
            throw new SkippedException("Systemd tests require root on cgroup v1. Test skipped!");
        }
        ResultFiles files = SystemdTestUtils.buildSystemdSlices(opts);

        try {
            return SystemdTestUtils.systemdRunJava(opts);
        } finally {
            try {
                if (files.memory() != null) {
                    Files.delete(files.memory());
                }
                if (files.cpu() != null) {
                    Files.delete(files.cpu());
                }
                if (files.sliceDotDDir() != null) {
                    FileUtils.deleteFileTreeUnchecked(files.sliceDotDDir());
                }
            } catch (NoSuchFileException e) {
                // ignore
            }
        }
    }

    private static OutputAnalyzer systemdRunJava(SystemdRunOptions opts) throws Exception {
        return execute(buildJavaCommand(opts));
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
    private static ResultFiles buildSystemdSlices(SystemdRunOptions runOpts) throws Exception {
        String sliceName = sliceName(runOpts);
        String sliceNameCpu = sliceNameCpu(runOpts);

        // Generate systemd slices for cpu/memory
        String memorySliceContent = getMemorySlice(runOpts, sliceName);
        String cpuSliceContent = getCpuSlice(runOpts, sliceName);

        // Ensure base directory exists
        Files.createDirectories(SYSTEMD_CONFIG_HOME);
        Path sliceDotDDir = null;
        if (runOpts.hasSliceDLimit()) {
            String dirName = String.format("%s.slice.d", SLICE_NAMESPACE_PREFIX);
            sliceDotDDir = SYSTEMD_CONFIG_HOME.resolve(Path.of(dirName));
            Files.createDirectory(sliceDotDDir);

            if (runOpts.sliceDMemoryLimit != null) {
                Path memoryConfig = sliceDotDDir.resolve(Path.of(SLICE_D_MEM_CONFIG_FILE));
                Files.writeString(memoryConfig, getMemoryDSliceContent(runOpts));
            }
            if (runOpts.sliceDCpuLimit != null) {
                Path cpuConfig = sliceDotDDir.resolve(Path.of(SLICE_D_CPU_CONFIG_FILE));
                Files.writeString(cpuConfig, getCPUDSliceContent(runOpts));
            }
        }

        Path memory, cpu;
        try {
            // memory slice
            memory = SYSTEMD_CONFIG_HOME.resolve(Path.of(sliceFileName(sliceName)));
            // cpu slice nested in memory
            cpu = SYSTEMD_CONFIG_HOME.resolve(Path.of(sliceFileName(sliceNameCpu)));
            Files.writeString(memory, memorySliceContent);
            Files.writeString(cpu, cpuSliceContent);
        } catch (IOException e) {
            throw new AssertionError("Failed to write systemd slice files");
        }

        systemdDaemonReload(cpu);

        return new ResultFiles(memory, cpu, sliceDotDDir);
    }

    private static String sliceName(SystemdRunOptions runOpts) {
        // Slice name may include '-' which is a hierarchical slice indicator.
        // Replace '-' with '_' to avoid side-effects.
        return SLICE_NAMESPACE_PREFIX + "-" + runOpts.sliceName.replace("-", "_");
    }

    private static String sliceNameCpu(SystemdRunOptions runOpts) {
        String slice = sliceName(runOpts);
        return String.format("%s-cpu", slice);
    }

    private static void systemdDaemonReload(Path cpu) throws Exception {
        List<String> daemonReload = systemCtl();
        daemonReload.add("daemon-reload");

        if (execute(daemonReload).getExitValue() != 0) {
            throw new AssertionError("Failed to reload systemd daemon");
        }
    }

    private static List<String> systemCtl() {
        return commandWithUser("systemctl");
    }

    /**
     * 'baseCommand' or 'baseCommand --user' as list, depending on the cgroups
     * version and running user.
     *
     * @return 'baseCommand' if we are the root user, 'systemctl --user' if
     *         the current user is non-root and we are on cgroups v2. Note:
     *         Cgroups v1 and non-root is not possible as tests are skipped then.
     */
    private static List<String> commandWithUser(String baseCommand) {
        List<String> command = new ArrayList<>();
        command.add(baseCommand);
        if (RUN_AS_USER) {
            command.add("--user");
        }
        return command;
    }

    private static String getCpuSlice(SystemdRunOptions runOpts, String sliceName) {
        String basicSliceFormat = getBasicSliceFormat();
        return String.format(basicSliceFormat, sliceName, getCPUSliceContent(runOpts));
    }

    private static String getCPUSliceContent(SystemdRunOptions runOpts) {
        String format = basicCPUContentFormat();
         return String.format(format, runOpts.cpuLimit);
    }

    private static String getMemorySlice(SystemdRunOptions runOpts, String sliceName) {
        String basicSliceFormat = getBasicSliceFormat();
        return String.format(basicSliceFormat, sliceName, getMemorySliceContent(runOpts));
    }

    private static String getMemoryDSliceContent(SystemdRunOptions runOpts) {
        String format = "[Slice]\n" + basicMemoryContentFormat();
        return String.format(format, runOpts.sliceDMemoryLimit);
    }

    private static String getCPUDSliceContent(SystemdRunOptions runOpts) {
        String format = "[Slice]\n" + basicCPUContentFormat();
        return String.format(format, runOpts.sliceDCpuLimit);
    }

    private static String basicCPUContentFormat() {
        return """
                CPUAccounting=true
                CPUQuota=%s
                """;
    }

    private static String basicMemoryContentFormat() {
        return """
                MemoryAccounting=true
                MemoryLimit=%s
                """;
    }

    private static String getMemorySliceContent(SystemdRunOptions runOpts) {
        String format = basicMemoryContentFormat();

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
        // systemd-run [--user] --slice <slice-name>.slice --scope <java>
        List<String> javaCmd = systemdRun();
        javaCmd.add("--slice");
        javaCmd.add(sliceFileName(sliceNameCpu(opts)));
        javaCmd.add("--scope");
        javaCmd.add(Path.of(Utils.TEST_JDK, "bin", "java").toString());
        javaCmd.addAll(opts.javaOpts);
        javaCmd.add(opts.classToRun);
        javaCmd.addAll(opts.classParams);
        return javaCmd;
    }

    private static List<String> systemdRun() {
        return commandWithUser("systemd-run");
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
