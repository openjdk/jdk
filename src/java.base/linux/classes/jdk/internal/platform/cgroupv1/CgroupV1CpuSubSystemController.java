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

package jdk.internal.platform.cgroupv1;

import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.CgroupSubsystemCpuController;

public class CgroupV1CpuSubSystemController extends CgroupV1SubsystemController
        implements CgroupSubsystemCpuController {

    public CgroupV1CpuSubSystemController(String root, String mountPoint) {
        super(root, mountPoint);
    }

    @Override
    public long getCpuPeriod() {
        return CgroupV1Subsystem.getLongValue(this, "cpu.cfs_period_us");
    }

    @Override
    public long getCpuQuota() {
        return CgroupV1Subsystem.getLongValue(this, "cpu.cfs_quota_us");
    }

    @Override
    public long getCpuShares() {
        long retval = CgroupV1Subsystem.getLongValue(this, "cpu.shares");
        if (retval == 0 || retval == 1024) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        } else {
            return retval;
        }
    }

    @Override
    public long getCpuNumPeriods() {
        return CgroupV1SubsystemController.getLongEntry(this, "cpu.stat", "nr_periods");
    }

    @Override
    public long getCpuNumThrottled() {
        return CgroupV1SubsystemController.getLongEntry(this, "cpu.stat", "nr_throttled");
    }

    @Override
    public long getCpuThrottledTime() {
        return CgroupV1SubsystemController.getLongEntry(this, "cpu.stat", "throttled_time");
    }

    @Override
    public boolean needsAdjustment() {
        // Container frameworks have them equal; we skip adjustment for them
        return !getRoot().equals(getCgroupPath());
    }

}
