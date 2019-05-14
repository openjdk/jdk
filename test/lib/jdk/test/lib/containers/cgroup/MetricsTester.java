/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.containers.cgroup;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import jdk.internal.platform.Metrics;

public class MetricsTester {

    private static final double ERROR_MARGIN = 0.1;
    private static long unlimited_minimum = 0x7FFFFFFFFF000000L;
    long startSysVal;
    long startUserVal;
    long startUsage;
    long startPerCpu[];

    enum SubSystem {
        MEMORY("memory"),
        CPUSET("cpuset"),
        CPU("cpu"),
        CPUACCT("cpuacct"),
        BLKIO("blkio");

        private String value;

        SubSystem(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static final Set<String> allowedSubSystems =
            Stream.of(SubSystem.values()).map(SubSystem::value).collect(Collectors.toSet());

    private static final Map<String, String[]> subSystemPaths = new HashMap<>();

    private static void setPath(String[] line) {
        String cgroupPath = line[2];
        String[] subSystems = line[1].split(",");

        for (String subSystem : subSystems) {
            if (allowedSubSystems.contains(subSystem)) {
                String[] paths = subSystemPaths.get(subSystem);
                String finalPath = "";
                String root = paths[0];
                String mountPoint = paths[1];
                if (root != null && cgroupPath != null) {
                    if (root.equals("/")) {
                        if (!cgroupPath.equals("/")) {
                            finalPath = mountPoint + cgroupPath;
                        } else {
                            finalPath = mountPoint;
                        }
                    } else {
                        if (root.equals(cgroupPath)) {
                            finalPath = mountPoint;
                        } else {
                            if (cgroupPath.startsWith(root)) {
                                if (cgroupPath.length() > root.length()) {
                                    String cgroupSubstr = cgroupPath.substring(root.length());
                                    finalPath = mountPoint + cgroupSubstr;
                                }
                            }
                        }
                    }
                }
                subSystemPaths.put(subSystem, new String[]{finalPath, mountPoint});
            }
        }
    }

    private static void createSubsystems(String[] line) {
        if (line.length < 5) return;
        Path p = Paths.get(line[4]);
        String subsystemName = p.getFileName().toString();
        if (subsystemName != null) {
            for (String subSystem : subsystemName.split(",")) {
                if (allowedSubSystems.contains(subSystem)) {
                    subSystemPaths.put(subSystem, new String[]{line[3], line[4]});
                }
            }
        }
    }

    public void setup() {
        Metrics metrics = Metrics.systemMetrics();
        // Initialize CPU usage metrics before we do any testing.
        startSysVal = metrics.getCpuSystemUsage();
        startUserVal = metrics.getCpuUserUsage();
        startUsage = metrics.getCpuUsage();
        startPerCpu = metrics.getPerCpuUsage();

        try {
            Stream<String> lines = Files.lines(Paths.get("/proc/self/mountinfo"));
            lines.filter(line -> line.contains(" - cgroup cgroup "))
                    .map(line -> line.split(" "))
                    .forEach(MetricsTester::createSubsystems);
            lines.close();

            lines = Files.lines(Paths.get("/proc/self/cgroup"));
            lines.map(line -> line.split(":"))
                    .filter(line -> (line.length >= 3))
                    .forEach(MetricsTester::setPath);
            lines.close();
        } catch (IOException e) {
        }
    }

    private static String getFileContents(SubSystem subSystem, String fileName) {
        String fname = subSystemPaths.get(subSystem.value())[0] + File.separator + fileName;
        try {
            return new Scanner(new File(fname)).useDelimiter("\\Z").next();
        } catch (FileNotFoundException e) {
            System.err.println("Unable to open : " + fname);
            return "";
        }
    }

    private static long getLongValueFromFile(SubSystem subSystem, String fileName) {
        String data = getFileContents(subSystem, fileName);
        return data.isEmpty() ? 0L : Long.parseLong(data);
    }

    private static long getLongValueFromFile(SubSystem subSystem, String metric, String subMetric) {
        String stats = getFileContents(subSystem, metric);
        String[] tokens = stats.split("[\\r\\n]+");
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].startsWith(subMetric)) {
                return Long.parseLong(tokens[i].split("\\s+")[1]);
            }
        }
        return 0L;
    }

    private static double getDoubleValueFromFile(SubSystem subSystem, String fileName) {
        String data = getFileContents(subSystem, fileName);
        return data.isEmpty() ? 0.0 : Double.parseDouble(data);
    }

    private boolean compareWithErrorMargin(long oldVal, long newVal) {
        return Math.abs(oldVal - newVal) <= Math.abs(oldVal * ERROR_MARGIN);
    }

    private boolean compareWithErrorMargin(double oldVal, double newVal) {
        return Math.abs(oldVal - newVal) <= Math.abs(oldVal * ERROR_MARGIN);
    }

    private static void fail(SubSystem system, String metric, long oldVal, long testVal) {
        throw new RuntimeException("Test failed for - " + system.value + ":"
                + metric + ", expected [" + oldVal + "], got [" + testVal + "]");
    }

    private static void fail(SubSystem system, String metric, String oldVal, String testVal) {
        throw new RuntimeException("Test failed for - " + system.value + ":"
                + metric + ", expected [" + oldVal + "], got [" + testVal + "]");
    }

    private static void fail(SubSystem system, String metric, double oldVal, double testVal) {
        throw new RuntimeException("Test failed for - " + system.value + ":"
                + metric + ", expected [" + oldVal + "], got [" + testVal + "]");
    }

    private static void fail(SubSystem system, String metric, boolean oldVal, boolean testVal) {
        throw new RuntimeException("Test failed for - " + system.value + ":"
                + metric + ", expected [" + oldVal + "], got [" + testVal + "]");
    }

    private static void warn(SubSystem system, String metric, long oldVal, long testVal) {
        System.err.println("Warning - " + system.value + ":" + metric
                + ", expected [" + oldVal + "], got [" + testVal + "]");
    }

    public void testMemorySubsystem() {
        Metrics metrics = Metrics.systemMetrics();

        // User Memory
        long oldVal = metrics.getMemoryFailCount();
        long newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.failcnt");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.failcnt", oldVal, newVal);
        }

        oldVal = metrics.getMemoryLimit();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.limit_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemoryMaxUsage();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.max_usage_in_bytes");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.max_usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemoryUsage();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.usage_in_bytes");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.usage_in_bytes", oldVal, newVal);
        }

        // Kernel memory
        oldVal = metrics.getKernelMemoryFailCount();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.kmem.failcnt");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.kmem.failcnt", oldVal, newVal);
        }

        oldVal = metrics.getKernelMemoryLimit();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.kmem.limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.kmem.limit_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getKernelMemoryMaxUsage();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.kmem.max_usage_in_bytes");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.kmem.max_usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getKernelMemoryUsage();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.kmem.usage_in_bytes");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.kmem.usage_in_bytes", oldVal, newVal);
        }

        //TCP Memory
        oldVal = metrics.getTcpMemoryFailCount();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.kmem.tcp.failcnt");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.kmem.tcp.failcnt", oldVal, newVal);
        }

        oldVal = metrics.getTcpMemoryLimit();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.kmem.tcp.limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.kmem.tcp.limit_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getTcpMemoryMaxUsage();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.kmem.tcp.max_usage_in_bytes");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.kmem.tcp.max_usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getTcpMemoryUsage();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.kmem.tcp.usage_in_bytes");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.kmem.tcp.usage_in_bytes", oldVal, newVal);
        }

        //  Memory and Swap
        oldVal = metrics.getMemoryAndSwapFailCount();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.memsw.failcnt");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.memsw.failcnt", oldVal, newVal);
        }

        oldVal = metrics.getMemoryAndSwapLimit();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.memsw.limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.memsw.limit_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemoryAndSwapMaxUsage();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.memsw.max_usage_in_bytes");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.memsw.max_usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemoryAndSwapUsage();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.memsw.usage_in_bytes");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.memsw.usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemorySoftLimit();
        newVal = getLongValueFromFile(SubSystem.MEMORY, "memory.soft_limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.MEMORY, "memory.soft_limit_in_bytes", oldVal, newVal);
        }

        boolean oomKillEnabled = metrics.isMemoryOOMKillEnabled();
        boolean newOomKillEnabled = getLongValueFromFile(SubSystem.MEMORY,
                "memory.oom_control", "oom_kill_disable") == 0L ? true : false;
        if (oomKillEnabled != newOomKillEnabled) {
            throw new RuntimeException("Test failed for - " + SubSystem.MEMORY.value + ":"
                    + "memory.oom_control:oom_kill_disable" + ", expected ["
                    + oomKillEnabled + "], got [" + newOomKillEnabled + "]");
        }
    }

    public void testCpuAccounting() {
        Metrics metrics = Metrics.systemMetrics();
        long oldVal = metrics.getCpuUsage();
        long newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpuacct.usage");

        if (!compareWithErrorMargin(oldVal, newVal)) {
            warn(SubSystem.CPUACCT, "cpuacct.usage", oldVal, newVal);
        }

        Long[] newVals = Stream.of(getFileContents(SubSystem.CPUACCT, "cpuacct.usage_percpu")
                .split("\\s+"))
                .map(Long::parseLong)
                .toArray(Long[]::new);
        Long[] oldVals = LongStream.of(metrics.getPerCpuUsage()).boxed().toArray(Long[]::new);
        for (int i = 0; i < oldVals.length; i++) {
            if (!compareWithErrorMargin(oldVals[i], newVals[i])) {
                warn(SubSystem.CPUACCT, "cpuacct.usage_percpu", oldVals[i], newVals[i]);
            }
        }

        oldVal = metrics.getCpuUserUsage();
        newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpuacct.stat", "user");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            warn(SubSystem.CPUACCT, "cpuacct.usage - user", oldVal, newVal);
        }

        oldVal = metrics.getCpuSystemUsage();
        newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpuacct.stat", "system");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            warn(SubSystem.CPUACCT, "cpuacct.usage - system", oldVal, newVal);
        }
    }

    public void testCpuSchedulingMetrics() {
        Metrics metrics = Metrics.systemMetrics();
        long oldVal = metrics.getCpuPeriod();
        long newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpu.cfs_period_us");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.CPUACCT, "cpu.cfs_period_us", oldVal, newVal);
        }

        oldVal = metrics.getCpuQuota();
        newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpu.cfs_quota_us");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.CPUACCT, "cpu.cfs_quota_us", oldVal, newVal);
        }

        oldVal = metrics.getCpuShares();
        newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpu.shares");
        if (newVal == 0 || newVal == 1024) newVal = -1;
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.CPUACCT, "cpu.shares", oldVal, newVal);
        }

        oldVal = metrics.getCpuNumPeriods();
        newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpu.stat", "nr_periods");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.CPUACCT, "cpu.stat - nr_periods", oldVal, newVal);
        }

        oldVal = metrics.getCpuNumThrottled();
        newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpu.stat", "nr_throttled");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.CPUACCT, "cpu.stat - nr_throttled", oldVal, newVal);
        }

        oldVal = metrics.getCpuThrottledTime();
        newVal = getLongValueFromFile(SubSystem.CPUACCT, "cpu.stat", "throttled_time");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.CPUACCT, "cpu.stat - throttled_time", oldVal, newVal);
        }
    }

    public void testCpuSets() {
        Metrics metrics = Metrics.systemMetrics();
        Integer[] oldVal = Arrays.stream(metrics.getCpuSetCpus()).boxed().toArray(Integer[]::new);
        Arrays.sort(oldVal);

        String cpusstr = getFileContents(SubSystem.CPUSET, "cpuset.cpus");
        // Parse range string in the format 1,2-6,7
        Integer[] newVal = Stream.of(cpusstr.split(",")).flatMap(a -> {
            if (a.contains("-")) {
                String[] range = a.split("-");
                return IntStream.rangeClosed(Integer.parseInt(range[0]),
                        Integer.parseInt(range[1])).boxed();
            } else {
                return Stream.of(Integer.parseInt(a));
            }
        }).toArray(Integer[]::new);
        Arrays.sort(newVal);
        if (Arrays.compare(oldVal, newVal) != 0) {
            fail(SubSystem.CPUSET, "cpuset.cpus", Arrays.toString(oldVal),
                Arrays.toString(newVal));
        }

        int [] cpuSets = metrics.getEffectiveCpuSetCpus();

        // Skip this test if this metric is supported on this platform
        if (cpuSets.length != 0) {
            oldVal = Arrays.stream(cpuSets).boxed().toArray(Integer[]::new);
            Arrays.sort(oldVal);
            cpusstr = getFileContents(SubSystem.CPUSET, "cpuset.effective_cpus");
            newVal = Stream.of(cpusstr.split(",")).flatMap(a -> {
                if (a.contains("-")) {
                    String[] range = a.split("-");
                    return IntStream.rangeClosed(Integer.parseInt(range[0]),
                            Integer.parseInt(range[1])).boxed();
                } else {
                    return Stream.of(Integer.parseInt(a));
                }
            }).toArray(Integer[]::new);
            Arrays.sort(newVal);
            if (Arrays.compare(oldVal, newVal) != 0) {
                fail(SubSystem.CPUSET, "cpuset.effective_cpus", Arrays.toString(oldVal),
                        Arrays.toString(newVal));
            }
        }

        oldVal = Arrays.stream(metrics.getCpuSetMems()).boxed().toArray(Integer[]::new);
        Arrays.sort(oldVal);
        cpusstr = getFileContents(SubSystem.CPUSET, "cpuset.mems");
        newVal = Stream.of(cpusstr.split(",")).flatMap(a -> {
            if (a.contains("-")) {
                String[] range = a.split("-");
                return IntStream.rangeClosed(Integer.parseInt(range[0]),
                        Integer.parseInt(range[1])).boxed();
            } else {
                return Stream.of(Integer.parseInt(a));
            }
        }).toArray(Integer[]::new);
        Arrays.sort(newVal);
        if (Arrays.compare(oldVal, newVal) != 0) {
            fail(SubSystem.CPUSET, "cpuset.mems", Arrays.toString(oldVal),
                    Arrays.toString(newVal));
        }

        int [] cpuSetMems = metrics.getEffectiveCpuSetMems();

        // Skip this test if this metric is supported on this platform
        if (cpuSetMems.length != 0) {
            oldVal = Arrays.stream(cpuSetMems).boxed().toArray(Integer[]::new);
            Arrays.sort(oldVal);
            cpusstr = getFileContents(SubSystem.CPUSET, "cpuset.effective_mems");
            newVal = Stream.of(cpusstr.split(",")).flatMap(a -> {
                if (a.contains("-")) {
                    String[] range = a.split("-");
                    return IntStream.rangeClosed(Integer.parseInt(range[0]),
                            Integer.parseInt(range[1])).boxed();
                } else {
                    return Stream.of(Integer.parseInt(a));
                }
            }).toArray(Integer[]::new);
            Arrays.sort(newVal);
            if (Arrays.compare(oldVal, newVal) != 0) {
                fail(SubSystem.CPUSET, "cpuset.effective_mems", Arrays.toString(oldVal),
                        Arrays.toString(newVal));
            }
        }

        double oldValue = metrics.getCpuSetMemoryPressure();
        double newValue = getDoubleValueFromFile(SubSystem.CPUSET, "cpuset.memory_pressure");
        if (!compareWithErrorMargin(oldValue, newValue)) {
            fail(SubSystem.CPUSET, "cpuset.memory_pressure", oldValue, newValue);
        }

        boolean oldV = metrics.isCpuSetMemoryPressureEnabled();
        boolean newV = getLongValueFromFile(SubSystem.CPUSET,
                "cpuset.memory_pressure_enabled") == 1 ? true : false;
        if (oldV != newV) {
            fail(SubSystem.CPUSET, "cpuset.memory_pressure_enabled", oldV, newV);
        }
    }

    public void testBlkIO() {
        Metrics metrics = Metrics.systemMetrics();
            long oldVal = metrics.getBlkIOServiceCount();
        long newVal = getLongValueFromFile(SubSystem.BLKIO,
                "blkio.throttle.io_service_bytes", "Total");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.BLKIO, "blkio.throttle.io_service_bytes - Total",
                    oldVal, newVal);
        }

        oldVal = metrics.getBlkIOServiced();
        newVal = getLongValueFromFile(SubSystem.BLKIO, "blkio.throttle.io_serviced", "Total");
        if (!compareWithErrorMargin(oldVal, newVal)) {
            fail(SubSystem.BLKIO, "blkio.throttle.io_serviced - Total", oldVal, newVal);
        }
    }

    public void testCpuConsumption() throws IOException, InterruptedException {
        Metrics metrics = Metrics.systemMetrics();
        // make system call
        long newSysVal = metrics.getCpuSystemUsage();
        long newUserVal = metrics.getCpuUserUsage();
        long newUsage = metrics.getCpuUsage();
        long[] newPerCpu = metrics.getPerCpuUsage();

        if (newSysVal <= startSysVal) {
            fail(SubSystem.CPU, "getCpuSystemUsage", newSysVal, startSysVal);
        }

        if (newUserVal <= startUserVal) {
            fail(SubSystem.CPU, "getCpuUserUsage", newUserVal, startUserVal);
        }

        if (newUsage <= startUsage) {
            fail(SubSystem.CPU, "getCpuUserUsage", newUsage, startUsage);
        }

        boolean success = false;
        for (int i = 0; i < startPerCpu.length; i++) {
            if (newPerCpu[i] > startPerCpu[i]) {
                success = true;
                break;
            }
        }

        if(!success) fail(SubSystem.CPU, "getPerCpuUsage", Arrays.toString(newPerCpu),
                Arrays.toString(startPerCpu));
    }

    public void testMemoryUsage() throws Exception {
        Metrics metrics = Metrics.systemMetrics();
        long memoryMaxUsage = metrics.getMemoryMaxUsage();
        long memoryUsage = metrics.getMemoryUsage();

        long[] ll = new long[64*1024*1024]; // 64M

        long newMemoryMaxUsage = metrics.getMemoryMaxUsage();
        long newMemoryUsage = metrics.getMemoryUsage();

        if(newMemoryMaxUsage < memoryMaxUsage) {
            fail(SubSystem.MEMORY, "getMemoryMaxUsage", newMemoryMaxUsage,
                    memoryMaxUsage);
        }

        if(newMemoryUsage < memoryUsage) {
            fail(SubSystem.MEMORY, "getMemoryUsage", newMemoryUsage, memoryUsage);
        }
    }

    public static void main(String[] args) throws Exception {
        // If cgroups is not configured, report success
        Metrics metrics = Metrics.systemMetrics();
        if (metrics == null) {
            System.out.println("TEST PASSED!!!");
            return;
        }

        MetricsTester metricsTester = new MetricsTester();
        metricsTester.setup();
        metricsTester.testCpuAccounting();
        metricsTester.testCpuSchedulingMetrics();
        metricsTester.testCpuSets();
        metricsTester.testMemorySubsystem();
        metricsTester.testBlkIO();
        metricsTester.testCpuConsumption();
        metricsTester.testMemoryUsage();
        System.out.println("TEST PASSED!!!");
    }
}
