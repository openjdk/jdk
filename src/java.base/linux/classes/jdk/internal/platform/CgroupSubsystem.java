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

/**
 * Marker interface for cgroup-based metrics
 *
 */
public interface CgroupSubsystem extends Metrics {

    /**
     * Returned for metrics of type long if the underlying implementation
     * has determined that no limit is being imposed.
     */
    public static final long OSCONTAINER_ERROR = -2;
    public static final String MAX_VAL = "max";

    public static long limitFromString(String strVal) {
        if (strVal == null) {
            return CgroupSubsystem.OSCONTAINER_ERROR;
        }
        if (MAX_VAL.equals(strVal)) {
            return Long.MAX_VALUE;
        }
        return Long.parseLong(strVal);
    }

    public default void initializeHierarchy(CgroupSubsystemController memory) {
        int bestLevel = 0;
        long memoryLimitMin = Long.MAX_VALUE;
        long memorySwapLimitMin = Long.MAX_VALUE;

        for (int dirCount = 0; memory.trimPath(dirCount); ++dirCount) {
            long memoryLimit = getMemoryLimit();
            if (memoryLimit != Long.MAX_VALUE && memoryLimit != CgroupSubsystem.OSCONTAINER_ERROR && memoryLimit < memoryLimitMin) {
                memoryLimitMin = memoryLimit;
                bestLevel = dirCount;
            }
            long memorySwapLimit = getMemoryAndSwapLimit();
            if (memorySwapLimit != Long.MAX_VALUE && memorySwapLimit != CgroupSubsystem.OSCONTAINER_ERROR && memorySwapLimit < memorySwapLimitMin) {
                memorySwapLimitMin = memorySwapLimit;
                bestLevel = dirCount;
            }
            // Never use a directory without controller files (disabled by "../cgroup.subtree_control").
            if (memoryLimit == CgroupSubsystem.OSCONTAINER_ERROR && memorySwapLimit == CgroupSubsystem.OSCONTAINER_ERROR && bestLevel == dirCount) {
                ++bestLevel;
            }
        }

        memory.trimPath(bestLevel);
    }
}
