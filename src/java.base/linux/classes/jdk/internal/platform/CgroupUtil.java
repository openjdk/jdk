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

package jdk.internal.platform;

import java.nio.file.Path;

public final class CgroupUtil {

    /**
     * Calculate the processor count based on the host CPUs and set cpu quota.
     *
     * @param cpu      The cpu controller to read the quota values from.
     * @param hostCpus The physical host CPUs
     * @return The minimum of host CPUs and the configured cpu quota, never
     *         negative.
     */
    static int processorCount(CgroupSubsystemCpuController cpu, int hostCpus) {
        int limit = hostCpus;
        long quota = cpu.getCpuQuota();
        long period = cpu.getCpuPeriod();
        int quotaCount = 0;

        if (quota > CgroupSubsystem.LONG_RETVAL_UNLIMITED && period > 0) {
            quotaCount = (int) Math.ceilDiv(quota, period);
        }
        if (quotaCount != 0) {
            limit = quotaCount;
        }
        return Math.min(hostCpus, limit);
    }

    public static void adjustController(CgroupSubsystemCpuController cpu) {
        if (!cpu.needsAdjustment()) {
            return;
        }
        String origCgroupPath = cpu.getCgroupPath();
        Path workingPath = Path.of(origCgroupPath);
        int hostCpus = CgroupMetrics.getTotalCpuCount0();
        int lowestLimit = hostCpus;

        int limit = CgroupUtil.processorCount(cpu, hostCpus);
        String lowestPath = origCgroupPath;
        while ((workingPath = workingPath.getParent()) != null) {
            cpu.setPath(workingPath.toString()); // adjust path
            limit = CgroupUtil.processorCount(cpu, hostCpus);
            if (limit < lowestLimit) {
                lowestLimit = limit;
                lowestPath = workingPath.toString();
            }
        }
        if (lowestLimit == hostCpus) {
            // No lower limit found adjust to original path
            cpu.setPath(origCgroupPath);
        } else {
            // Adjust controller to lowest observed limit path
            cpu.setPath(lowestPath);
        }
    }

    public static void adjustController(CgroupSubsystemMemoryController memory) {
        if (!memory.needsAdjustment()) {
            return;
        }
        long physicalMemory = CgroupMetrics.getTotalMemorySize0();
        String origCgroupPath = memory.getCgroupPath();
        Path workingPath = Path.of(origCgroupPath);
        long limit = memory.getMemoryLimit(physicalMemory);
        long lowestLimit = limit < 0 ? physicalMemory : limit;
        String lowestPath = origCgroupPath;
        while ((workingPath = workingPath.getParent()) != null) {
            memory.setPath(workingPath.toString()); // adjust path
            limit = memory.getMemoryLimit(physicalMemory);
            if (limit > 0 && limit < lowestLimit) {
                lowestLimit = limit;
                lowestPath = workingPath.toString();
            }
        }
        if (lowestLimit < physicalMemory) {
            // Found a lower limit, adjust controller to that path
            memory.setPath(lowestPath);
        } else {
            // No lower limit found adjust to original path
            memory.setPath(origCgroupPath);
        }
    }
}
