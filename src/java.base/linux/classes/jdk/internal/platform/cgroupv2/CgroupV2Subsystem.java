/*
 * Copyright (c) 2020, 2022, Red Hat Inc.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.platform.cgroupv2;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.platform.CgroupInfo;
import jdk.internal.platform.CgroupMetrics;
import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.CgroupSubsystemController;

public class CgroupV2Subsystem implements CgroupSubsystem {

    private static volatile CgroupV2Subsystem INSTANCE;
    private static final long[] LONG_ARRAY_NOT_SUPPORTED = null;
    private static final int[] INT_ARRAY_UNAVAILABLE = null;
    private static final String PROVIDER_NAME = "cgroupv2";
    private static final Object EMPTY_STR = "";
    private final CgroupSubsystemController unified;
    private final CgroupV2MemorySubSystemController memory;
    private final CgroupV2CpuSubSystemController cpu;

    private CgroupV2Subsystem(CgroupSubsystemController unified,
                              CgroupV2MemorySubSystemController memory,
                              CgroupV2CpuSubSystemController cpu) {
        this.unified = unified;
        this.memory = memory;
        this.cpu = cpu;
    }

    static long getLongVal(CgroupSubsystemController controller, String file, long defaultValue) {
        return CgroupSubsystemController.getLongValue(controller,
                                                      file,
                                                      CgroupV2SubsystemController::convertStringToLong,
                                                      defaultValue);
    }

    static long getLongVal(CgroupSubsystemController controller, String file) {
        return getLongVal(controller, file, CgroupSubsystem.LONG_RETVAL_UNLIMITED);
    }

    /**
     * Get the singleton instance of a cgroups v2 subsystem. On initialization,
     * a new object from the given cgroup information 'anyController' is being
     * created. Note that the cgroup information has been parsed from cgroup
     * interface files ahead of time.
     *
     * See CgroupSubsystemFactory.determineType() for the cgroup interface
     * files parsing logic.
     *
     * @return A singleton CgroupSubsystem instance, never null.
     */
    public static CgroupSubsystem getInstance(Map<String, CgroupInfo> infos) {
        if (INSTANCE == null) {
            CgroupV2Subsystem tmpCgroupSystem = initSubSystem(infos);
            synchronized (CgroupV2Subsystem.class) {
                if (INSTANCE == null) {
                    INSTANCE = tmpCgroupSystem;
                }
            }
        }
        return INSTANCE;
    }

    private static CgroupV2Subsystem initSubSystem(Map<String, CgroupInfo> infos) {
        // For unified it doesn't matter which controller we pick.
        CgroupInfo anyController = infos.values().iterator().next();
        Objects.requireNonNull(anyController);
        // Memory and cpu limits might not be at the leaf, so we ought to
        // iterate the path up the hierarchy and chose the one with the lowest
        // limit. Therefore, we have specific instances for memory and cpu.
        // Other controllers ought to be separate too, but it hasn't yet come up.
        CgroupSubsystemController unified = new CgroupV2SubsystemController(
                anyController.getMountPoint(),
                anyController.getCgroupPath());
        CgroupV2MemorySubSystemController memory = new CgroupV2MemorySubSystemController(
                anyController.getMountPoint(),
                anyController.getCgroupPath());
        CgroupUtil.adjustController(memory);
        CgroupV2CpuSubSystemController cpu = new CgroupV2CpuSubSystemController(
                anyController.getMountPoint(),
                anyController.getCgroupPath());
        CgroupUtil.adjustController(cpu);
        return new CgroupV2Subsystem(unified, memory, cpu);
    }

    @Override
    public String getProvider() {
        return PROVIDER_NAME;
    }

    @Override
    public long getCpuUsage() {
        return cpu.getCpuUsage();
    }

    @Override
    public long[] getPerCpuUsage() {
        return LONG_ARRAY_NOT_SUPPORTED;
    }

    @Override
    public long getCpuUserUsage() {
        return cpu.getCpuUserUsage();
    }

    @Override
    public long getCpuSystemUsage() {
        return cpu.getCpuSystemUsage();
    }

    @Override
    public long getCpuPeriod() {
        return cpu.getCpuPeriod();
    }

    @Override
    public long getCpuQuota() {
        return cpu.getCpuQuota();
    }

    @Override
    public long getCpuShares() {
        return cpu.getCpuShares();
    }

    @Override
    public long getCpuNumPeriods() {
        return cpu.getCpuNumPeriods();
    }

    @Override
    public long getCpuNumThrottled() {
        return cpu.getCpuNumThrottled();
    }

    @Override
    public long getCpuThrottledTime() {
        return cpu.getCpuThrottledTime();
    }

    @Override
    public long getEffectiveCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    @Override
    public int[] getCpuSetCpus() {
        String cpuSetVal = CgroupSubsystemController.getStringValue(unified, "cpuset.cpus");
        return getCpuSet(cpuSetVal);
    }

    @Override
    public int[] getEffectiveCpuSetCpus() {
        String effCpuSetVal = CgroupSubsystemController.getStringValue(unified, "cpuset.cpus.effective");
        return getCpuSet(effCpuSetVal);
    }

    @Override
    public int[] getCpuSetMems() {
        String cpuSetMems = CgroupSubsystemController.getStringValue(unified, "cpuset.mems");
        return getCpuSet(cpuSetMems);
    }

    @Override
    public int[] getEffectiveCpuSetMems() {
        String effCpuSetMems = CgroupSubsystemController.getStringValue(unified, "cpuset.mems.effective");
        return getCpuSet(effCpuSetMems);
    }

    private int[] getCpuSet(String cpuSetVal) {
        if (cpuSetVal == null || EMPTY_STR.equals(cpuSetVal)) {
            return INT_ARRAY_UNAVAILABLE;
        }
        return CgroupSubsystemController.stringRangeToIntArray(cpuSetVal);
    }

    @Override
    public long getMemoryFailCount() {
        return memory.getMemoryFailCount();
    }

    @Override
    public long getMemoryLimit() {
        return memory.getMemoryLimit(CgroupMetrics.getTotalMemorySize0());
    }

    @Override
    public long getMemoryUsage() {
        return memory.getMemoryUsage();
    }

    @Override
    public long getTcpMemoryUsage() {
        return memory.getTcpMemoryUsage();
    }


    @Override
    public long getMemoryAndSwapLimit() {
        return memory.getMemoryAndSwapLimit(CgroupMetrics.getTotalMemorySize0(),
                                            CgroupMetrics.getTotalSwapSize0());
    }


    @Override
    public long getMemoryAndSwapUsage() {
        return memory.getMemoryAndSwapUsage();
    }

    @Override
    public long getMemorySoftLimit() {
        return memory.getMemorySoftLimit(CgroupMetrics.getTotalMemorySize0());
    }

    @Override
    public long getPidsMax() {
        String pidsMaxStr = CgroupSubsystemController.getStringValue(unified, "pids.max");
        return CgroupSubsystem.limitFromString(pidsMaxStr);
    }

    @Override
    public long getPidsCurrent() {
        return getLongVal(unified, "pids.current");
    }

    @Override
    public long getBlkIOServiceCount() {
        return sumTokensIOStat(CgroupV2Subsystem::lineToRandWIOs);
    }


    @Override
    public long getBlkIOServiced() {
        return sumTokensIOStat(CgroupV2Subsystem::lineToRBytesAndWBytesIO);
    }

    private long sumTokensIOStat(Function<String, Long> mapFunc) {
        try (Stream<String> lines = Files.lines(Path.of(unified.path(), "io.stat"))) {
            return lines.map(mapFunc)
                        .collect(Collectors.summingLong(e -> e));
        } catch (UncheckedIOException | IOException e) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
    }

    private static String[] getRWIOMatchTokenNames() {
        return new String[] { "rios", "wios" };
    }

    private static String[] getRWBytesIOMatchTokenNames() {
        return new String[] { "rbytes", "wbytes" };
    }

    public static Long lineToRandWIOs(String line) {
        String[] matchNames = getRWIOMatchTokenNames();
        return ioStatLineToLong(line, matchNames);
    }

    public static Long lineToRBytesAndWBytesIO(String line) {
        String[] matchNames = getRWBytesIOMatchTokenNames();
        return ioStatLineToLong(line, matchNames);
    }

    private static Long ioStatLineToLong(String line, String[] matchNames) {
        if (line == null || EMPTY_STR.equals(line)) {
            return Long.valueOf(0);
        }
        String[] tokens = line.split("\\s+");
        long retval = 0;
        for (String t: tokens) {
            String[] valKeys = t.split("=");
            if (valKeys.length != 2) {
                // ignore device ids $MAJ:$MIN
                continue;
            }
            for (String match: matchNames) {
                if (match.equals(valKeys[0])) {
                    retval += longOrZero(valKeys[1]);
                }
            }
        }
        return Long.valueOf(retval);
    }

    private static long longOrZero(String val) {
        long lVal = 0;
        try {
            lVal = Long.parseLong(val);
        } catch (NumberFormatException e) {
            // keep at 0
        }
        return lVal;
    }
}
