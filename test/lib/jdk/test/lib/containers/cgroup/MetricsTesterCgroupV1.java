/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import jdk.internal.platform.Metrics;
import jdk.internal.platform.CgroupV1Metrics;

public class MetricsTesterCgroupV1 implements CgroupMetricsTester {

    private static long unlimited_minimum = 0x7FFFFFFFFF000000L;
    long startSysVal;
    long startUserVal;
    long startUsage;
    long startPerCpu[];

    enum Controller {
        MEMORY("memory"),
        CPUSET("cpuset"),
        CPU("cpu"),
        CPUACCT("cpuacct"),
        BLKIO("blkio");

        private String value;

        Controller(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static final Set<String> allowedSubSystems =
            Stream.of(Controller.values()).map(Controller::value).collect(Collectors.toSet());

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
        if (startPerCpu == null) {
            startPerCpu = new long[0];
        }

        try {
            Stream<String> lines = Files.lines(Paths.get("/proc/self/mountinfo"));
            lines.filter(line -> line.contains(" - cgroup cgroup "))
                    .map(line -> line.split(" "))
                    .forEach(MetricsTesterCgroupV1::createSubsystems);
            lines.close();

            lines = Files.lines(Paths.get("/proc/self/cgroup"));
            lines.map(line -> line.split(":"))
                    .filter(line -> (line.length >= 3))
                    .forEach(MetricsTesterCgroupV1::setPath);
            lines.close();
        } catch (IOException e) {
        }
    }

    private static String getFileContents(Controller subSystem, String fileName) {
        String fname = subSystemPaths.get(subSystem.value())[0] + File.separator + fileName;
        try {
            return new Scanner(new File(fname)).useDelimiter("\\Z").next();
        } catch (FileNotFoundException e) {
            System.err.println("Unable to open : " + fname);
            return null;
        }
    }

    private static long getLongValueFromFile(Controller subSystem, String fileName) {
        String data = getFileContents(subSystem, fileName);
        return (data == null || data.isEmpty()) ? 0L : convertStringToLong(data);
    }

    private static long convertStringToLong(String strval) {
        return CgroupMetricsTester.convertStringToLong(strval, Long.MAX_VALUE);
    }

    private static long getLongValueFromFile(Controller subSystem, String metric, String subMetric) {
        String stats = getFileContents(subSystem, metric);
        String[] tokens = stats.split("[\\r\\n]+");
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].startsWith(subMetric)) {
                String strval = tokens[i].split("\\s+")[1];
                return convertStringToLong(strval);
            }
        }
        return 0L;
    }

    private static double getDoubleValueFromFile(Controller subSystem, String fileName) {
        String data = getFileContents(subSystem, fileName);
        return data.isEmpty() ? 0.0 : Double.parseDouble(data);
    }

    private static void fail(Controller system, String metric, long oldVal, long testVal) {
        CgroupMetricsTester.fail(system.value, metric, oldVal, testVal);
    }

    private static void fail(Controller system, String metric, String oldVal, String testVal) {
        CgroupMetricsTester.fail(system.value, metric, oldVal, testVal);
    }

    private static void fail(Controller system, String metric, double oldVal, double testVal) {
        CgroupMetricsTester.fail(system.value, metric, oldVal, testVal);
    }

    private static void fail(Controller system, String metric, boolean oldVal, boolean testVal) {
        CgroupMetricsTester.fail(system.value, metric, oldVal, testVal);
    }

    private static void warn(Controller system, String metric, long oldVal, long testVal) {
        CgroupMetricsTester.warn(system.value, metric, oldVal, testVal);
    }

    public void testMemorySubsystem() {
        CgroupV1Metrics metrics = (CgroupV1Metrics)Metrics.systemMetrics();

        // User Memory
        long oldVal = metrics.getMemoryFailCount();
        long newVal = getLongValueFromFile(Controller.MEMORY, "memory.failcnt");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.failcnt", oldVal, newVal);
        }

        oldVal = metrics.getMemoryLimit();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.limit_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemoryMaxUsage();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.max_usage_in_bytes");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.max_usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemoryUsage();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.usage_in_bytes");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.usage_in_bytes", oldVal, newVal);
        }

        // Kernel memory
        oldVal = metrics.getKernelMemoryFailCount();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.kmem.failcnt");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.kmem.failcnt", oldVal, newVal);
        }

        oldVal = metrics.getKernelMemoryLimit();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.kmem.limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.kmem.limit_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getKernelMemoryMaxUsage();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.kmem.max_usage_in_bytes");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.kmem.max_usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getKernelMemoryUsage();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.kmem.usage_in_bytes");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.kmem.usage_in_bytes", oldVal, newVal);
        }

        //TCP Memory
        oldVal = metrics.getTcpMemoryFailCount();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.kmem.tcp.failcnt");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.kmem.tcp.failcnt", oldVal, newVal);
        }

        oldVal = metrics.getTcpMemoryLimit();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.kmem.tcp.limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.kmem.tcp.limit_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getTcpMemoryMaxUsage();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.kmem.tcp.max_usage_in_bytes");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.kmem.tcp.max_usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getTcpMemoryUsage();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.kmem.tcp.usage_in_bytes");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.kmem.tcp.usage_in_bytes", oldVal, newVal);
        }

        //  Memory and Swap
        oldVal = metrics.getMemoryAndSwapFailCount();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.memsw.failcnt");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.memsw.failcnt", oldVal, newVal);
        }

        oldVal = metrics.getMemoryAndSwapLimit();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.memsw.limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.memsw.limit_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemoryAndSwapMaxUsage();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.memsw.max_usage_in_bytes");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.memsw.max_usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemoryAndSwapUsage();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.memsw.usage_in_bytes");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.memsw.usage_in_bytes", oldVal, newVal);
        }

        oldVal = metrics.getMemorySoftLimit();
        newVal = getLongValueFromFile(Controller.MEMORY, "memory.soft_limit_in_bytes");
        newVal = newVal > unlimited_minimum ? -1L : newVal;
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.MEMORY, "memory.soft_limit_in_bytes", oldVal, newVal);
        }

        boolean oomKillEnabled = metrics.isMemoryOOMKillEnabled();
        boolean newOomKillEnabled = getLongValueFromFile(Controller.MEMORY,
                "memory.oom_control", "oom_kill_disable") == 0L ? true : false;
        if (oomKillEnabled != newOomKillEnabled) {
            throw new RuntimeException("Test failed for - " + Controller.MEMORY.value + ":"
                    + "memory.oom_control:oom_kill_disable" + ", expected ["
                    + oomKillEnabled + "], got [" + newOomKillEnabled + "]");
        }
    }

    public void testCpuAccounting() {
        CgroupV1Metrics metrics = (CgroupV1Metrics)Metrics.systemMetrics();
        long oldVal = metrics.getCpuUsage();
        long newVal = getLongValueFromFile(Controller.CPUACCT, "cpuacct.usage");

        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            warn(Controller.CPUACCT, "cpuacct.usage", oldVal, newVal);
        }

        String newValsStr = getFileContents(Controller.CPUACCT, "cpuacct.usage_percpu");
        Long[] newVals = new Long[0];
        if (newValsStr != null) {
            newVals = Stream.of(newValsStr
                .split("\\s+"))
                .map(Long::parseLong)
                .toArray(Long[]::new);
        }
        long[] oldValsPrim = metrics.getPerCpuUsage();
        Long[] oldVals = LongStream.of(oldValsPrim == null ? new long[0] : oldValsPrim)
                                    .boxed().toArray(Long[]::new);
        for (int i = 0; i < oldVals.length; i++) {
            if (!CgroupMetricsTester.compareWithErrorMargin(oldVals[i], newVals[i])) {
                warn(Controller.CPUACCT, "cpuacct.usage_percpu", oldVals[i], newVals[i]);
            }
        }

        oldVal = metrics.getCpuUserUsage();
        newVal = getLongValueFromFile(Controller.CPUACCT, "cpuacct.stat", "user");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            warn(Controller.CPUACCT, "cpuacct.usage - user", oldVal, newVal);
        }

        oldVal = metrics.getCpuSystemUsage();
        newVal = getLongValueFromFile(Controller.CPUACCT, "cpuacct.stat", "system");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            warn(Controller.CPUACCT, "cpuacct.usage - system", oldVal, newVal);
        }
    }

    public void testCpuSchedulingMetrics() {
        CgroupV1Metrics metrics = (CgroupV1Metrics)Metrics.systemMetrics();
        long oldVal = metrics.getCpuPeriod();
        long newVal = getLongValueFromFile(Controller.CPUACCT, "cpu.cfs_period_us");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.CPUACCT, "cpu.cfs_period_us", oldVal, newVal);
        }

        oldVal = metrics.getCpuQuota();
        newVal = getLongValueFromFile(Controller.CPUACCT, "cpu.cfs_quota_us");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.CPUACCT, "cpu.cfs_quota_us", oldVal, newVal);
        }

        oldVal = metrics.getCpuShares();
        newVal = getLongValueFromFile(Controller.CPUACCT, "cpu.shares");
        if (newVal == 0 || newVal == 1024) newVal = -1;
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.CPUACCT, "cpu.shares", oldVal, newVal);
        }

        oldVal = metrics.getCpuNumPeriods();
        newVal = getLongValueFromFile(Controller.CPUACCT, "cpu.stat", "nr_periods");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.CPUACCT, "cpu.stat - nr_periods", oldVal, newVal);
        }

        oldVal = metrics.getCpuNumThrottled();
        newVal = getLongValueFromFile(Controller.CPUACCT, "cpu.stat", "nr_throttled");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.CPUACCT, "cpu.stat - nr_throttled", oldVal, newVal);
        }

        oldVal = metrics.getCpuThrottledTime();
        newVal = getLongValueFromFile(Controller.CPUACCT, "cpu.stat", "throttled_time");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.CPUACCT, "cpu.stat - throttled_time", oldVal, newVal);
        }
    }

    public void testCpuSets() {
        CgroupV1Metrics metrics = (CgroupV1Metrics)Metrics.systemMetrics();
        Integer[] oldVal = Arrays.stream(metrics.getCpuSetCpus()).boxed().toArray(Integer[]::new);
        Arrays.sort(oldVal);

        String cpusstr = getFileContents(Controller.CPUSET, "cpuset.cpus");
        // Parse range string in the format 1,2-6,7
        Integer[] newVal = CgroupMetricsTester.convertCpuSetsToArray(cpusstr);
        Arrays.sort(newVal);
        if (Arrays.compare(oldVal, newVal) != 0) {
            fail(Controller.CPUSET, "cpuset.cpus", Arrays.toString(oldVal),
                Arrays.toString(newVal));
        }

        int [] cpuSets = metrics.getEffectiveCpuSetCpus();

        // Skip this test if this metric is not supported on this platform
        if (cpuSets.length != 0) {
            oldVal = Arrays.stream(cpuSets).boxed().toArray(Integer[]::new);
            Arrays.sort(oldVal);
            cpusstr = getFileContents(Controller.CPUSET, "cpuset.effective_cpus");
            newVal = CgroupMetricsTester.convertCpuSetsToArray(cpusstr);
            Arrays.sort(newVal);
            if (Arrays.compare(oldVal, newVal) != 0) {
                fail(Controller.CPUSET, "cpuset.effective_cpus", Arrays.toString(oldVal),
                        Arrays.toString(newVal));
            }
        }

        oldVal = Arrays.stream(metrics.getCpuSetMems()).boxed().toArray(Integer[]::new);
        Arrays.sort(oldVal);
        cpusstr = getFileContents(Controller.CPUSET, "cpuset.mems");
        newVal = CgroupMetricsTester.convertCpuSetsToArray(cpusstr);
        Arrays.sort(newVal);
        if (Arrays.compare(oldVal, newVal) != 0) {
            fail(Controller.CPUSET, "cpuset.mems", Arrays.toString(oldVal),
                    Arrays.toString(newVal));
        }

        int [] cpuSetMems = metrics.getEffectiveCpuSetMems();

        // Skip this test if this metric is not supported on this platform
        if (cpuSetMems.length != 0) {
            oldVal = Arrays.stream(cpuSetMems).boxed().toArray(Integer[]::new);
            Arrays.sort(oldVal);
            cpusstr = getFileContents(Controller.CPUSET, "cpuset.effective_mems");
            newVal = CgroupMetricsTester.convertCpuSetsToArray(cpusstr);
            Arrays.sort(newVal);
            if (Arrays.compare(oldVal, newVal) != 0) {
                fail(Controller.CPUSET, "cpuset.effective_mems", Arrays.toString(oldVal),
                        Arrays.toString(newVal));
            }
        }

        double oldValue = metrics.getCpuSetMemoryPressure();
        double newValue = getDoubleValueFromFile(Controller.CPUSET, "cpuset.memory_pressure");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldValue, newValue)) {
            fail(Controller.CPUSET, "cpuset.memory_pressure", oldValue, newValue);
        }

        boolean oldV = metrics.isCpuSetMemoryPressureEnabled();
        boolean newV = getLongValueFromFile(Controller.CPUSET,
                "cpuset.memory_pressure_enabled") == 1 ? true : false;
        if (oldV != newV) {
            fail(Controller.CPUSET, "cpuset.memory_pressure_enabled", oldV, newV);
        }
    }

    private void testBlkIO() {
        CgroupV1Metrics metrics = (CgroupV1Metrics)Metrics.systemMetrics();
            long oldVal = metrics.getBlkIOServiceCount();
        long newVal = getLongValueFromFile(Controller.BLKIO,
                "blkio.throttle.io_service_bytes", "Total");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.BLKIO, "blkio.throttle.io_service_bytes - Total",
                    oldVal, newVal);
        }

        oldVal = metrics.getBlkIOServiced();
        newVal = getLongValueFromFile(Controller.BLKIO, "blkio.throttle.io_serviced", "Total");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail(Controller.BLKIO, "blkio.throttle.io_serviced - Total", oldVal, newVal);
        }
    }

    public void testCpuConsumption() throws IOException, InterruptedException {
        CgroupV1Metrics metrics = (CgroupV1Metrics)Metrics.systemMetrics();
        // make system call
        long newSysVal = metrics.getCpuSystemUsage();
        long newUserVal = metrics.getCpuUserUsage();
        long newUsage = metrics.getCpuUsage();
        long[] newPerCpu = metrics.getPerCpuUsage();
        if (newPerCpu == null) {
            newPerCpu = new long[0];
        }

        // system/user CPU usage counters may be slowly increasing.
        // allow for equal values for a pass
        if (newSysVal < startSysVal) {
            fail(Controller.CPU, "getCpuSystemUsage", newSysVal, startSysVal);
        }

        // system/user CPU usage counters may be slowly increasing.
        // allow for equal values for a pass
        if (newUserVal < startUserVal) {
            fail(Controller.CPU, "getCpuUserUsage", newUserVal, startUserVal);
        }

        if (newUsage <= startUsage) {
            fail(Controller.CPU, "getCpuUsage", newUsage, startUsage);
        }

        boolean success = false;
        for (int i = 0; i < startPerCpu.length; i++) {
            if (newPerCpu[i] > startPerCpu[i]) {
                success = true;
                break;
            }
        }

        if(!success) fail(Controller.CPU, "getPerCpuUsage", Arrays.toString(newPerCpu),
                Arrays.toString(startPerCpu));
    }

    public void testMemoryUsage() throws Exception {
        CgroupV1Metrics metrics = (CgroupV1Metrics)Metrics.systemMetrics();
        long memoryMaxUsage = metrics.getMemoryMaxUsage();
        long memoryUsage = metrics.getMemoryUsage();
        long newMemoryMaxUsage = 0, newMemoryUsage = 0;

        // allocate memory in a loop and check more than once for new values
        // otherwise we might see seldom the effect of decreasing new memory values
        // e.g. because the system could free up memory
        byte[][] bytes = new byte[32][];
        for (int i = 0; i < 32; i++) {
            bytes[i] = new byte[8*1024*1024];
            newMemoryUsage = metrics.getMemoryUsage();
            if (newMemoryUsage > memoryUsage) {
                break;
            }
        }
        newMemoryMaxUsage = metrics.getMemoryMaxUsage();

        if (newMemoryMaxUsage < memoryMaxUsage) {
            fail(Controller.MEMORY, "getMemoryMaxUsage", memoryMaxUsage,
                    newMemoryMaxUsage);
        }

        if (newMemoryUsage < memoryUsage) {
            fail(Controller.MEMORY, "getMemoryUsage", memoryUsage, newMemoryUsage);
        }
    }

    @Override
    public void testMisc() {
        testBlkIO();
    }
}
