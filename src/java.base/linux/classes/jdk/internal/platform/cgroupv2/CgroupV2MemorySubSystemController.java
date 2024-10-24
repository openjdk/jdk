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

import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.CgroupSubsystemController;
import jdk.internal.platform.CgroupSubsystemMemoryController;

public class CgroupV2MemorySubSystemController extends CgroupV2SubsystemController implements CgroupSubsystemMemoryController {

    private static final String ROOT_PATH = "/";
    private static final long NO_SWAP = 0;

    CgroupV2MemorySubSystemController(String mountPath, String cgroupPath) {
        super(mountPath, cgroupPath);
    }

    @Override
    public long getMemoryLimit(long physicalMemory) {
        String strVal = CgroupSubsystemController.getStringValue(this, "memory.max");
        long limit = CgroupSubsystem.limitFromString(strVal);
        // Return unlimited if we already determined it as unlimited or if
        // the limit exceeds the host physical memory limit
        if (limit == CgroupSubsystem.LONG_RETVAL_UNLIMITED ||
            limit >= physicalMemory) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        return limit;
    }

    @Override
    public long getMemoryUsage() {
        return CgroupV2Subsystem.getLongVal(this, "memory.current");
    }

    /**
     * Note that for cgroups v2 the actual limits set for swap and
     * memory live in two different files, memory.swap.max and memory.max
     * respectively. In order to properly report a cgroup v1 like
     * compound value we need to sum the two values. Setting a swap limit
     * without also setting a memory limit is not allowed.
     */
    @Override
    public long getMemoryAndSwapLimit(long hostMemory, long hostSwap) {
        String strVal = CgroupSubsystemController.getStringValue(this, "memory.swap.max");
        // We only get a null string when file memory.swap.max doesn't exist.
        // In that case we return the memory limit without any swap.
        if (strVal == null) {
            return getMemoryLimit(hostMemory);
        }
        long swapLimit = CgroupSubsystem.limitFromString(strVal);
        if (swapLimit >= 0) {
            long memoryLimit = getMemoryLimit(hostMemory);
            if (memoryLimit < 0) {
                // We have the case where the memory limit is larger than the
                // host memory size: swapLimit >= 0 && memoryLimit < 0
                // In that case reset the memory to the host memory for this
                // calculation
                memoryLimit = hostMemory;
            }
            long memSwapLimit = memoryLimit + swapLimit;
            long hostBound = hostMemory + hostSwap;
            // The limit must not exceed host values
            if (memSwapLimit >= hostBound) {
                return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
            }
            return memSwapLimit;
        }
        return CgroupSubsystem.LONG_RETVAL_UNLIMITED; // unlimited swap case
    }

    /**
     * Note that for cgroups v2 the actual values set for swap usage and
     * memory usage live in two different files, memory.current and memory.swap.current
     * respectively. In order to properly report a cgroup v1 like
     * compound value we need to sum the two values. Setting a swap limit
     * without also setting a memory limit is not allowed.
     */
    @Override
    public long getMemoryAndSwapUsage() {
        long memoryUsage = getMemoryUsage();
        if (memoryUsage >= 0) {
            // If file memory.swap.current doesn't exist, only return the regular
            // memory usage (without swap). Thus, use default value of NO_SWAP.
            long swapUsage = CgroupV2Subsystem.getLongVal(this, "memory.swap.current", NO_SWAP);
            return memoryUsage + swapUsage;
        }
        return memoryUsage; // case of no memory limits
    }

    @Override
    public long getMemorySoftLimit(long hostMemory) {
        String softLimitStr = CgroupSubsystemController.getStringValue(this, "memory.low");
        long softLimit = CgroupSubsystem.limitFromString(softLimitStr);
        // Avoid for the soft limit exceeding physical memory
        if (softLimit == CgroupSubsystem.LONG_RETVAL_UNLIMITED ||
            softLimit >= hostMemory) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        return softLimit;
    }

    @Override
    public long getMemoryFailCount() {
        return CgroupV2SubsystemController.getLongEntry(this, "memory.events", "max");
    }

    @Override
    public long getTcpMemoryUsage() {
        return CgroupV2SubsystemController.getLongEntry(this, "memory.stat", "sock");
    }

    @Override
    public boolean needsAdjustment() {
        return !ROOT_PATH.equals(getCgroupPath());
    }

}
