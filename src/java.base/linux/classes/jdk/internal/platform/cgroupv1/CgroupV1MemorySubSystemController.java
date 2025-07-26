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

package jdk.internal.platform.cgroupv1;

import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.CgroupSubsystemMemoryController;

public class CgroupV1MemorySubSystemController extends CgroupV1SubsystemController
        implements CgroupSubsystemMemoryController {

    private boolean swapenabled;

    public CgroupV1MemorySubSystemController(String root, String mountPoint) {
        super(root, mountPoint);
    }

    private boolean isSwapEnabled() {
        return swapenabled;
    }

    private void setSwapEnabled() {
        long memswBytes = CgroupV1Subsystem.getLongValue(this, "memory.memsw.limit_in_bytes");
        long swappiness = CgroupV1Subsystem.getLongValue(this, "memory.swappiness");
        this.swapenabled = (memswBytes > 0 && swappiness > 0);
    }

    @Override
    public void setPath(String cgroupPath) {
        super.setPath(cgroupPath);
        setSwapEnabled();
    }

    @Override
    public long getMemoryLimit(long physicalMemory) {
        long limit = CgroupV1Subsystem.getLongValue(this, "memory.limit_in_bytes");
        // Limits on cg v1 might return large numbers. Bound above by the
        // physical limit of the host. If so, treat it as unlimited.
        if (limit >= physicalMemory) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        return limit;
    }

    @Override
    public long getMemoryUsage() {
        return CgroupV1Subsystem.getLongValue(this, "memory.usage_in_bytes");
    }

    @Override
    public long getTcpMemoryUsage() {
        return CgroupV1Subsystem.getLongValue(this, "memory.kmem.tcp.usage_in_bytes");
    }

    @Override
    public long getMemoryAndSwapLimit(long hostMemory, long hostSwap) {
        if (!isSwapEnabled()) {
            return getMemoryLimit(hostMemory);
        }
        long retval = CgroupV1Subsystem.getLongValue(this, "memory.memsw.limit_in_bytes");
        long upperBound = hostMemory + hostSwap;
        // The limit value for cg v1 might be a large number when there is no
        // limit. Ensure the limit doesn't exceed the physical bounds.
        if (retval >= upperBound) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        return retval;
    }

    @Override
    public long getMemoryAndSwapUsage() {
        if (!isSwapEnabled()) {
            return getMemoryUsage();
        }
        return CgroupV1Subsystem.getLongValue(this, "memory.memsw.usage_in_bytes");
    }

    @Override
    public long getMemorySoftLimit(long hostMemory) {
        long softLimit = CgroupV1Subsystem.getLongValue(this, "memory.soft_limit_in_bytes");
        if (softLimit >= hostMemory) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        return softLimit;
    }

    @Override
    public long getMemoryFailCount() {
        return CgroupV1Subsystem.getLongValue(this, "memory.failcnt");
    }

    public long getMemoryMaxUsage() {
        return CgroupV1Subsystem.getLongValue(this, "memory.max_usage_in_bytes");
    }

    public long getMemoryAndSwapMaxUsage() {
        if (!isSwapEnabled()) {
            return getMemoryMaxUsage();
        }
        return CgroupV1Subsystem.getLongValue(this, "memory.memsw.max_usage_in_bytes");
    }

    public long getMemoryAndSwapFailCount() {
        if (!isSwapEnabled()) {
            return getMemoryFailCount();
        }
        return CgroupV1Subsystem.getLongValue(this, "memory.memsw.failcnt");
    }

    public long getKernelMemoryFailCount() {
        return CgroupV1Subsystem.getLongValue(this, "memory.kmem.failcnt");
    }

    public long getKernelMemoryMaxUsage() {
        return CgroupV1Subsystem.getLongValue(this, "memory.kmem.max_usage_in_bytes");
    }

    public long getKernelMemoryUsage() {
        return CgroupV1Subsystem.getLongValue(this, "memory.kmem.usage_in_bytes");
    }

    public long getTcpMemoryFailCount() {
        return CgroupV1Subsystem.getLongValue(this, "memory.kmem.tcp.failcnt");
    }

    public long getTcpMemoryMaxUsage() {
        return CgroupV1Subsystem.getLongValue(this, "memory.kmem.tcp.max_usage_in_bytes");
    }

    public Boolean isMemoryOOMKillEnabled() {
        long val = CgroupV1SubsystemController.getLongEntry(this, "memory.oom_control", "oom_kill_disable");
        return (val == 0);
    }

    @Override
    public boolean needsAdjustment() {
        // Container frameworks have them equal; we skip adjustment for them
        return !getRoot().equals(getCgroupPath());
    }
}
