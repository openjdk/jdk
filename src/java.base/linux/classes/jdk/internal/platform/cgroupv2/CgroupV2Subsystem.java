/*
 * Copyright (c) 2020, Red Hat Inc.
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
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.CgroupSubsystemController;
import jdk.internal.platform.CgroupUtil;

public class CgroupV2Subsystem implements CgroupSubsystem {

    private static final CgroupV2Subsystem INSTANCE = initSubsystem();
    private static final long[] LONG_ARRAY_NOT_SUPPORTED = null;
    private static final int[] INT_ARRAY_UNAVAILABLE = null;
    private final CgroupSubsystemController unified;
    private static final String PROVIDER_NAME = "cgroupv2";
    private static final int PER_CPU_SHARES = 1024;
    private static final String MAX_VAL = "max";
    private static final Object EMPTY_STR = "";

    private CgroupV2Subsystem(CgroupSubsystemController unified) {
        this.unified = unified;
    }

    private long getLongVal(String file) {
        return CgroupSubsystemController.getLongValue(unified,
                                                      file,
                                                      CgroupV2SubsystemController::convertStringToLong,
                                                      CgroupSubsystem.LONG_RETVAL_UNLIMITED);
    }

    private static CgroupV2Subsystem initSubsystem() {
        // read mountinfo so as to determine root mount path
        String mountPath = null;
        try (Stream<String> lines =
                CgroupUtil.readFilePrivileged(Paths.get("/proc/self/mountinfo"))) {

            String l = lines.filter(line -> line.contains(" - cgroup2 "))
                            .collect(Collectors.joining());
            String[] tokens = l.split(" ");
            mountPath = tokens[4];
        } catch (IOException e) {
            return null;
        }
        String cgroupPath = null;
        try {
            List<String> lines = CgroupUtil.readAllLinesPrivileged(Paths.get("/proc/self/cgroup"));
            for (String line: lines) {
                String[] tokens = line.split(":");
                if (tokens.length != 3) {
                    return null; // something is not right.
                }
                if (!"0".equals(tokens[0])) {
                    // hierarchy must be zero for cgroups v2
                    return null;
                }
                cgroupPath = tokens[2];
                break;
            }
        } catch (IOException e) {
            return null;
        }
        CgroupSubsystemController unified = new CgroupV2SubsystemController(
                mountPath,
                cgroupPath);
        return new CgroupV2Subsystem(unified);
    }

    public static CgroupSubsystem getInstance() {
        return INSTANCE;
    }

    @Override
    public String getProvider() {
        return PROVIDER_NAME;
    }

    @Override
    public long getCpuUsage() {
        long micros = CgroupV2SubsystemController.getLongEntry(unified, "cpu.stat", "usage_usec");
        if (micros < 0) {
            return micros;
        }
        return TimeUnit.MICROSECONDS.toNanos(micros);
    }

    @Override
    public long[] getPerCpuUsage() {
        return LONG_ARRAY_NOT_SUPPORTED;
    }

    @Override
    public long getCpuUserUsage() {
        long micros = CgroupV2SubsystemController.getLongEntry(unified, "cpu.stat", "user_usec");
        if (micros < 0) {
            return micros;
        }
        return TimeUnit.MICROSECONDS.toNanos(micros);
    }

    @Override
    public long getCpuSystemUsage() {
        long micros = CgroupV2SubsystemController.getLongEntry(unified, "cpu.stat", "system_usec");
        if (micros < 0) {
            return micros;
        }
        return TimeUnit.MICROSECONDS.toNanos(micros);
    }

    @Override
    public long getCpuPeriod() {
        return getFromCpuMax(1 /* $PERIOD index */);
    }

    @Override
    public long getCpuQuota() {
        return getFromCpuMax(0 /* $MAX index */);
    }

    private long getFromCpuMax(int tokenIdx) {
        String cpuMaxRaw = CgroupSubsystemController.getStringValue(unified, "cpu.max");
        if (cpuMaxRaw == null) {
            // likely file not found
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        // $MAX $PERIOD
        String[] tokens = cpuMaxRaw.split("\\s+");
        if (tokens.length != 2) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        String quota = tokens[tokenIdx];
        return limitFromString(quota);
    }

    private long limitFromString(String strVal) {
        if (strVal == null || MAX_VAL.equals(strVal)) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        return Long.parseLong(strVal);
    }

    @Override
    public long getCpuShares() {
        long sharesRaw = getLongVal("cpu.weight");
        if (sharesRaw == 100 || sharesRaw <= 0) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        int shares = (int)sharesRaw;
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

    @Override
    public long getCpuNumPeriods() {
        return CgroupV2SubsystemController.getLongEntry(unified, "cpu.stat", "nr_periods");
    }

    @Override
    public long getCpuNumThrottled() {
        return CgroupV2SubsystemController.getLongEntry(unified, "cpu.stat", "nr_throttled");
    }

    @Override
    public long getCpuThrottledTime() {
        long micros = CgroupV2SubsystemController.getLongEntry(unified, "cpu.stat", "throttled_usec");
        if (micros < 0) {
            return micros;
        }
        return TimeUnit.MICROSECONDS.toNanos(micros);
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
        return CgroupV2SubsystemController.getLongEntry(unified, "memory.events", "max");
    }

    @Override
    public long getMemoryLimit() {
        String strVal = CgroupSubsystemController.getStringValue(unified, "memory.max");
        return limitFromString(strVal);
    }

    @Override
    public long getMemoryUsage() {
        return getLongVal("memory.current");
    }

    @Override
    public long getTcpMemoryUsage() {
        return CgroupV2SubsystemController.getLongEntry(unified, "memory.stat", "sock");
    }

    @Override
    public long getMemoryAndSwapLimit() {
        String strVal = CgroupSubsystemController.getStringValue(unified, "memory.swap.max");
        return limitFromString(strVal);
    }

    @Override
    public long getMemoryAndSwapUsage() {
        return getLongVal("memory.swap.current");
    }

    @Override
    public long getMemorySoftLimit() {
        String softLimitStr = CgroupSubsystemController.getStringValue(unified, "memory.high");
        return limitFromString(softLimitStr);
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
        try {
            return CgroupUtil.readFilePrivileged(Paths.get(unified.path(), "io.stat"))
                                .map(mapFunc)
                                .collect(Collectors.summingLong(e -> e));
        } catch (IOException e) {
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
