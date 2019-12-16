/*
 * Copyright (c) 2020, Red Hat Inc.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.Metrics;

public class MetricsTesterCgroupV2 implements CgroupMetricsTester {

    private static final long UNLIMITED = -1;
    private static final UnifiedController UNIFIED = new UnifiedController();
    private static final String MAX = "max";
    private static final int PER_CPU_SHARES = 1024;

    private final long startSysVal;
    private final long startUserVal;
    private final long startUsage;

    static class UnifiedController {

        private static final String NAME = "unified";
        private final String path;

        UnifiedController() {
            path = constructPath();
        }

        String getPath() {
            return path;
        }

        private static String constructPath() {
            String mountPath;
            String cgroupPath;
            try {
                List<String> fifthTokens = Files.lines(Paths.get("/proc/self/mountinfo"))
                        .filter( l -> l.contains("- cgroup2"))
                        .map(UnifiedController::splitAndMountPath)
                        .collect(Collectors.toList());
                if (fifthTokens.size() != 1) {
                    throw new AssertionError("Expected only one cgroup2 line");
                }
                mountPath = fifthTokens.get(0);

                List<String> cgroupPaths = Files.lines(Paths.get("/proc/self/cgroup"))
                        .filter( l -> l.startsWith("0:"))
                        .map(UnifiedController::splitAndCgroupPath)
                        .collect(Collectors.toList());
                if (cgroupPaths.size() != 1) {
                    throw new AssertionError("Expected only one unified controller line");
                }
                cgroupPath = cgroupPaths.get(0);
                return Paths.get(mountPath, cgroupPath).toString();
            } catch (IOException e) {
                return null;
            }
        }

        public static String splitAndMountPath(String input) {
            String[] tokens = input.split("\\s+");
            return tokens[4]; // fifth entry is the mount path
        }

        public static String splitAndCgroupPath(String input) {
            String[] tokens = input.split(":");
            return tokens[2];
        }
    }

    private long getLongLimitValueFromFile(String file) {
        String strVal = getStringVal(file);
        if (MAX.equals(strVal)) {
            return UNLIMITED;
        }
        return convertStringToLong(strVal);
    }

    public MetricsTesterCgroupV2() {
        Metrics metrics = Metrics.systemMetrics();
        // Initialize CPU usage metrics before we do any testing.
        startSysVal = metrics.getCpuSystemUsage();
        startUserVal = metrics.getCpuUserUsage();
        startUsage = metrics.getCpuUsage();
    }

    private long getLongValueFromFile(String file) {
        return convertStringToLong(getStringVal(file));
    }

    private long getLongValueEntryFromFile(String file, String metric) {
        Path filePath = Paths.get(UNIFIED.getPath(), file);
        try {
            String strVal = Files.lines(filePath).filter(l -> l.startsWith(metric)).collect(Collectors.joining());
            String[] keyValues = strVal.split("\\s+");
            String value = keyValues[1];
            return convertStringToLong(value);
        } catch (IOException e) {
            return 0;
        }
    }

    private String getStringVal(String file) {
        Path filePath = Paths.get(UNIFIED.getPath(), file);
        try {
            return Files.lines(filePath).collect(Collectors.joining());
        } catch (IOException e) {
            return null;
        }
    }

    private void fail(String metric, long oldVal, long newVal) {
        CgroupMetricsTester.fail(UnifiedController.NAME, metric, oldVal, newVal);
    }

    private void fail(String metric, String oldVal, String newVal) {
        CgroupMetricsTester.fail(UnifiedController.NAME, metric, oldVal, newVal);
    }

    private void warn(String metric, long oldVal, long newVal) {
        CgroupMetricsTester.warn(UnifiedController.NAME, metric, oldVal, newVal);
    }

    private long getCpuShares(String file) {
        long rawVal = getLongValueFromFile(file);
        if (rawVal == 0 || rawVal == 100) {
            return UNLIMITED;
        }
        int shares = (int)rawVal;
        // CPU shares (OCI) value needs to get translated into
        // a proper Cgroups v2 value. See:
        // https://github.com/containers/crun/blob/master/crun.1.md#cpu-controller
        //
        // Use the inverse of (x == OCI value, y == cgroupsv2 value):
        // ((262142 * y - 1)/9999) + 2 = x
        //
        int x = 262142 * shares - 1;
        double frac = x/9999.0;
        x = ((int)frac) + 2;
        if ( x <= PER_CPU_SHARES ) {
            return PER_CPU_SHARES; // mimic cgroups v1
        }
        int f = x/PER_CPU_SHARES;
        int lower_multiple = f * PER_CPU_SHARES;
        int upper_multiple = (f + 1) * PER_CPU_SHARES;
        int distance_lower = Math.max(lower_multiple, x) - Math.min(lower_multiple, x);
        int distance_upper = Math.max(upper_multiple, x) - Math.min(upper_multiple, x);
        x = distance_lower <= distance_upper ? lower_multiple : upper_multiple;
        return x;
    }

    private long getCpuMaxValueFromFile(String file) {
        return getCpuValueFromFile(file, 0 /* $MAX index */);
    }

    private long getCpuPeriodValueFromFile(String file) {
        return getCpuValueFromFile(file, 1 /* $PERIOD index */);
    }

    private long getCpuValueFromFile(String file, int index) {
        String maxPeriod = getStringVal(file);
        if (maxPeriod == null) {
            return UNLIMITED;
        }
        String[] tokens = maxPeriod.split("\\s+");
        String val = tokens[index];
        if (MAX.equals(val)) {
            return UNLIMITED;
        }
        return convertStringToLong(val);
    }

    private long convertStringToLong(String val) {
        return CgroupMetricsTester.convertStringToLong(val, UNLIMITED);
    }

    @Override
    public void testMemorySubsystem() {
        Metrics metrics = Metrics.systemMetrics();

        // User Memory
        long oldVal = metrics.getMemoryFailCount();
        long newVal = getLongValueEntryFromFile("memory.events", "max");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("memory.events[max]", oldVal, newVal);
        }

        oldVal = metrics.getMemoryLimit();
        newVal = getLongLimitValueFromFile("memory.max");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("memory.max", oldVal, newVal);
        }

        oldVal = metrics.getMemoryUsage();
        newVal = getLongValueFromFile("memory.current");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("memory.current", oldVal, newVal);
        }

        oldVal = metrics.getTcpMemoryUsage();
        newVal = getLongValueEntryFromFile("memory.stat", "sock");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("memory.stat[sock]", oldVal, newVal);
        }

        oldVal = metrics.getMemoryAndSwapLimit();
        newVal = getLongLimitValueFromFile("memory.swap.max");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("memory.swap.max", oldVal, newVal);
        }

        oldVal = metrics.getMemoryAndSwapUsage();
        newVal = getLongValueFromFile("memory.swap.current");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("memory.swap.current", oldVal, newVal);
        }

        oldVal = metrics.getMemorySoftLimit();
        newVal = getLongLimitValueFromFile("memory.high");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("memory.high", oldVal, newVal);
        }

    }

    @Override
    public void testCpuAccounting() {
        Metrics metrics = Metrics.systemMetrics();
        long oldVal = metrics.getCpuUsage();
        long newVal = TimeUnit.MICROSECONDS.toNanos(getLongValueEntryFromFile("cpu.stat", "usage_usec"));

        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            warn("cpu.stat[usage_usec]", oldVal, newVal);
        }

        oldVal = metrics.getCpuUserUsage();
        newVal = TimeUnit.MICROSECONDS.toNanos(getLongValueEntryFromFile("cpu.stat", "user_usec"));
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            warn("cpu.stat[user_usec]", oldVal, newVal);
        }

        oldVal = metrics.getCpuSystemUsage();
        newVal = TimeUnit.MICROSECONDS.toNanos(getLongValueEntryFromFile("cpu.stat", "system_usec"));
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            warn("cpu.stat[system_usec]", oldVal, newVal);
        }
    }

    @Override
    public void testCpuSchedulingMetrics() {
        Metrics metrics = Metrics.systemMetrics();
        long oldVal = metrics.getCpuPeriod();
        long newVal = getCpuPeriodValueFromFile("cpu.max");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("cpu.max[$PERIOD]", oldVal, newVal);
        }

        oldVal = metrics.getCpuQuota();
        newVal = getCpuMaxValueFromFile("cpu.max");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("cpu.max[$MAX]", oldVal, newVal);
        }

        oldVal = metrics.getCpuShares();
        newVal = getCpuShares("cpu.weight");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("cpu.weight", oldVal, newVal);
        }

        oldVal = metrics.getCpuNumPeriods();
        newVal = getLongValueEntryFromFile("cpu.stat", "nr_periods");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("cpu.stat[nr_periods]", oldVal, newVal);
        }

        oldVal = metrics.getCpuNumThrottled();
        newVal = getLongValueEntryFromFile("cpu.stat", "nr_throttled");
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("cpu.stat[nr_throttled]", oldVal, newVal);
        }

        oldVal = metrics.getCpuThrottledTime();
        newVal = TimeUnit.MICROSECONDS.toNanos(getLongValueEntryFromFile("cpu.stat", "throttled_usec"));
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("cpu.stat[throttled_usec]", oldVal, newVal);
        }
    }

    @Override
    public void testCpuSets() {
        Metrics metrics = Metrics.systemMetrics();
        int[] cpus = mapNullToEmpty(metrics.getCpuSetCpus());
        Integer[] oldVal = Arrays.stream(cpus).boxed().toArray(Integer[]::new);
        Arrays.sort(oldVal);

        String cpusstr = getStringVal("cpuset.cpus");
        // Parse range string in the format 1,2-6,7
        Integer[] newVal = CgroupMetricsTester.convertCpuSetsToArray(cpusstr);
        Arrays.sort(newVal);
        if (Arrays.compare(oldVal, newVal) != 0) {
            fail("cpuset.cpus", Arrays.toString(oldVal),
                                Arrays.toString(newVal));
        }

        cpus = mapNullToEmpty(metrics.getEffectiveCpuSetCpus());
        oldVal = Arrays.stream(cpus).boxed().toArray(Integer[]::new);
        Arrays.sort(oldVal);
        cpusstr = getStringVal("cpuset.cpus.effective");
        newVal = CgroupMetricsTester.convertCpuSetsToArray(cpusstr);
        Arrays.sort(newVal);
        if (Arrays.compare(oldVal, newVal) != 0) {
            fail("cpuset.cpus.effective", Arrays.toString(oldVal),
                                          Arrays.toString(newVal));
        }

        cpus = mapNullToEmpty(metrics.getCpuSetMems());
        oldVal = Arrays.stream(cpus).boxed().toArray(Integer[]::new);
        Arrays.sort(oldVal);
        cpusstr = getStringVal("cpuset.mems");
        newVal = CgroupMetricsTester.convertCpuSetsToArray(cpusstr);
        Arrays.sort(newVal);
        if (Arrays.compare(oldVal, newVal) != 0) {
            fail("cpuset.mems", Arrays.toString(oldVal),
                                Arrays.toString(newVal));
        }

        cpus = mapNullToEmpty(metrics.getEffectiveCpuSetMems());
        oldVal = Arrays.stream(cpus).boxed().toArray(Integer[]::new);
        Arrays.sort(oldVal);
        cpusstr = getStringVal("cpuset.mems.effective");
        newVal = CgroupMetricsTester.convertCpuSetsToArray(cpusstr);
        Arrays.sort(newVal);
        if (Arrays.compare(oldVal, newVal) != 0) {
            fail("cpuset.mems.effective", Arrays.toString(oldVal),
                                          Arrays.toString(newVal));
        }
    }

    private int[] mapNullToEmpty(int[] cpus) {
        if (cpus == null) {
            // Not available. For sake of testing continue with an
            // empty array.
            cpus = new int[0];
        }
        return cpus;
    }

    @Override
    public void testCpuConsumption() {
        Metrics metrics = Metrics.systemMetrics();
        // make system call
        long newSysVal = metrics.getCpuSystemUsage();
        long newUserVal = metrics.getCpuUserUsage();
        long newUsage = metrics.getCpuUsage();

        // system/user CPU usage counters may be slowly increasing.
        // allow for equal values for a pass
        if (newSysVal < startSysVal) {
            fail("getCpuSystemUsage", newSysVal, startSysVal);
        }

        // system/user CPU usage counters may be slowly increasing.
        // allow for equal values for a pass
        if (newUserVal < startUserVal) {
            fail("getCpuUserUsage", newUserVal, startUserVal);
        }

        if (newUsage <= startUsage) {
            fail("getCpuUsage", newUsage, startUsage);
        }
    }

    @Override
    public void testMemoryUsage() {
        Metrics metrics = Metrics.systemMetrics();
        long memoryUsage = metrics.getMemoryUsage();
        long newMemoryUsage = 0;

        // allocate memory in a loop and check more than once for new values
        // otherwise we might occasionally see the effect of decreasing new memory
        // values. For example because the system could free up memory
        byte[][] bytes = new byte[32][];
        for (int i = 0; i < 32; i++) {
            bytes[i] = new byte[8*1024*1024];
            newMemoryUsage = metrics.getMemoryUsage();
            if (newMemoryUsage > memoryUsage) {
                break;
            }
        }

        if (newMemoryUsage < memoryUsage) {
            fail("getMemoryUsage", memoryUsage, newMemoryUsage);
        }
    }

    @Override
    public void testMisc() {
        testIOStat();
    }

    private void testIOStat() {
        Metrics metrics = Metrics.systemMetrics();
        long oldVal = metrics.getBlkIOServiceCount();
        long newVal = getIoStatAccumulate(new String[] { "rios", "wios" });
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("io.stat->rios/wios: ", oldVal, newVal);
        }

        oldVal = metrics.getBlkIOServiced();
        newVal = getIoStatAccumulate(new String[] { "rbytes", "wbytes" });
        if (!CgroupMetricsTester.compareWithErrorMargin(oldVal, newVal)) {
            fail("io.stat->rbytes/wbytes: ", oldVal, newVal);
        }
    }

    private long getIoStatAccumulate(String[] matchNames) {
        try {
            return Files.lines(Paths.get(UNIFIED.getPath(), "io.stat"))
                    .map(line -> {
                        long accumulator = 0;
                        String[] tokens = line.split("\\s+");
                        for (String t: tokens) {
                            String[] keyVal = t.split("=");
                            if (keyVal.length != 2) {
                                continue;
                            }
                            for (String match: matchNames) {
                                if (match.equals(keyVal[0])) {
                                    accumulator += Long.parseLong(keyVal[1]);
                                }
                            }
                        }
                        return accumulator;
                    }).collect(Collectors.summingLong(e -> e));
        } catch (IOException e) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
    }
}
