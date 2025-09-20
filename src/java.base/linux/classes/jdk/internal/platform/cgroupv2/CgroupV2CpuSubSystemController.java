/*
 * Copyright (c) 2024, Red Hat Inc.
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

import java.util.concurrent.TimeUnit;

import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.CgroupSubsystemController;
import jdk.internal.platform.CgroupSubsystemCpuController;

public class CgroupV2CpuSubSystemController extends CgroupV2SubsystemController
        implements CgroupSubsystemCpuController {

    private static final String ROOT_PATH = "/";
    private static final int PER_CPU_SHARES = 1024;

    public CgroupV2CpuSubSystemController(String mountPath, String cgroupPath) {
        super(mountPath, cgroupPath);
    }

    @Override
    public long getCpuPeriod() {
        return getFromCpuMax(1 /* $PERIOD index */);
    }

    @Override
    public long getCpuQuota() {
        return getFromCpuMax(0 /* $MAX index */);
    }

    @Override
    public long getCpuShares() {
        long sharesRaw = CgroupV2Subsystem.getLongVal(this, "cpu.weight");
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
        return CgroupV2SubsystemController.getLongEntry(this, "cpu.stat", "nr_periods");
    }

    @Override
    public long getCpuNumThrottled() {
        return CgroupV2SubsystemController.getLongEntry(this, "cpu.stat", "nr_throttled");
    }

    @Override
    public long getCpuThrottledTime() {
        long micros = CgroupV2SubsystemController.getLongEntry(this, "cpu.stat", "throttled_usec");
        if (micros < 0) {
            return micros;
        }
        return TimeUnit.MICROSECONDS.toNanos(micros);
    }

    private long getFromCpuMax(int tokenIdx) {
        String cpuMaxRaw = CgroupSubsystemController.getStringValue(this, "cpu.max");
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
        return CgroupSubsystem.limitFromString(quota);
    }

    /**
     * Implementations which use the cpu controller, but are cpuacct on cg v1
     */

    public long getCpuUsage() {
        long micros = CgroupV2SubsystemController.getLongEntry(this, "cpu.stat", "usage_usec");
        if (micros < 0) {
            return micros;
        }
        return TimeUnit.MICROSECONDS.toNanos(micros);
    }

    public long getCpuUserUsage() {
        long micros = CgroupV2SubsystemController.getLongEntry(this, "cpu.stat", "user_usec");
        if (micros < 0) {
            return micros;
        }
        return TimeUnit.MICROSECONDS.toNanos(micros);
    }

    public long getCpuSystemUsage() {
        long micros = CgroupV2SubsystemController.getLongEntry(this, "cpu.stat", "system_usec");
        if (micros < 0) {
            return micros;
        }
        return TimeUnit.MICROSECONDS.toNanos(micros);
    }

    @Override
    public boolean needsAdjustment() {
        return !ROOT_PATH.equals(getCgroupPath());
    }

}
