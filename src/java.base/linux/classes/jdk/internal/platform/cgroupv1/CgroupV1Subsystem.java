/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.function.Supplier;

import jdk.internal.platform.CgroupInfo;
import jdk.internal.platform.CgroupMetrics;
import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.CgroupSubsystemController;
import jdk.internal.platform.CgroupUtil;
import jdk.internal.platform.CgroupV1Metrics;

public class CgroupV1Subsystem implements CgroupSubsystem, CgroupV1Metrics {
    private CgroupV1MemorySubSystemController memory;
    private CgroupV1CpuSubSystemController cpu;
    private CgroupV1SubsystemController cpuacct;
    private CgroupV1SubsystemController cpuset;
    private CgroupV1SubsystemController blkio;
    private CgroupV1SubsystemController pids;

    private static volatile CgroupV1Subsystem INSTANCE;

    private static final String PROVIDER_NAME = "cgroupv1";

    private CgroupV1Subsystem() {}

    /**
     * Get a singleton instance of CgroupV1Subsystem. Initially, it creates a new
     * object by retrieving the pre-parsed information from cgroup interface
     * files from the provided 'infos' map.
     *
     * See CgroupSubsystemFactory.determineType() where the actual parsing of
     * cgroup interface files happens.
     *
     * @return A singleton CgroupV1Subsystem instance, never null
     */
    public static CgroupV1Subsystem getInstance(Map<String, CgroupInfo> infos) {
        if (INSTANCE == null) {
            CgroupV1Subsystem tmpSubsystem = initSubSystem(infos);
            synchronized (CgroupV1Subsystem.class) {
                if (INSTANCE == null) {
                    INSTANCE = tmpSubsystem;
                }
            }
        }
        return INSTANCE;
    }

    private static CgroupV1Subsystem initSubSystem(Map<String, CgroupInfo> infos) {
        CgroupV1Subsystem subsystem = new CgroupV1Subsystem();

        boolean anyActiveControllers = false;
        /*
         * Find the cgroup mount points for subsystem controllers
         * by looking up relevant data in the infos map
         */
        for (CgroupInfo info: infos.values()) {
            switch (info.getName()) {
            case "memory": {
                if (info.getMountRoot() != null && info.getMountPoint() != null) {
                    CgroupV1MemorySubSystemController controller = new CgroupV1MemorySubSystemController(info.getMountRoot(), info.getMountPoint());
                    controller.setPath(info.getCgroupPath());
                    CgroupUtil.adjustController(controller);
                    subsystem.setMemorySubSystem(controller);
                    anyActiveControllers = true;
                }
                break;
            }
            case "cpuset": {
                if (info.getMountRoot() != null && info.getMountPoint() != null) {
                    CgroupV1SubsystemController controller = new CgroupV1SubsystemController(info.getMountRoot(), info.getMountPoint());
                    controller.setPath(info.getCgroupPath());
                    subsystem.setCpuSetController(controller);
                    anyActiveControllers = true;
                }
                break;
            }
            case "cpuacct": {
                if (info.getMountRoot() != null && info.getMountPoint() != null) {
                    CgroupV1SubsystemController controller = new CgroupV1SubsystemController(info.getMountRoot(), info.getMountPoint());
                    controller.setPath(info.getCgroupPath());
                    subsystem.setCpuAcctController(controller);
                    anyActiveControllers = true;
                }
                break;
            }
            case "cpu": {
                if (info.getMountRoot() != null && info.getMountPoint() != null) {
                    CgroupV1CpuSubSystemController controller = new CgroupV1CpuSubSystemController(info.getMountRoot(), info.getMountPoint());
                    controller.setPath(info.getCgroupPath());
                    CgroupUtil.adjustController(controller);
                    subsystem.setCpuController(controller);
                    anyActiveControllers = true;
                }
                break;
            }
            case "blkio": {
                if (info.getMountRoot() != null && info.getMountPoint() != null) {
                    CgroupV1SubsystemController controller = new CgroupV1SubsystemController(info.getMountRoot(), info.getMountPoint());
                    controller.setPath(info.getCgroupPath());
                    subsystem.setBlkIOController(controller);
                    anyActiveControllers = true;
                }
                break;
            }
            case "pids": {
                if (info.getMountRoot() != null && info.getMountPoint() != null) {
                    CgroupV1SubsystemController controller = new CgroupV1SubsystemController(info.getMountRoot(), info.getMountPoint());
                    controller.setPath(info.getCgroupPath());
                    subsystem.setPidsController(controller);
                    anyActiveControllers = true;
                }
                break;
            }
            default:
                throw new AssertionError("Unrecognized controller in infos: " + info.getName());
            }
        }

        // Return Metrics object if we found any subsystems.
        if (anyActiveControllers) {
            return subsystem;
        }

        return null;
    }

    private void setMemorySubSystem(CgroupV1MemorySubSystemController memory) {
        this.memory = memory;
    }

    private void setCpuController(CgroupV1CpuSubSystemController cpu) {
        this.cpu = cpu;
    }

    private void setCpuAcctController(CgroupV1SubsystemController cpuacct) {
        this.cpuacct = cpuacct;
    }

    private void setCpuSetController(CgroupV1SubsystemController cpuset) {
        this.cpuset = cpuset;
    }

    private void setBlkIOController(CgroupV1SubsystemController blkio) {
        this.blkio = blkio;
    }

    private void setPidsController(CgroupV1SubsystemController pids) {
        this.pids = pids;
    }

    static long getLongValue(CgroupSubsystemController controller,
                              String param) {
        return CgroupSubsystemController.getLongValue(controller,
                                                      param,
                                                      CgroupV1SubsystemController::convertStringToLong,
                                                      CgroupSubsystem.LONG_RETVAL_UNLIMITED);
    }

    /**
     * Accounts for optional controllers. If the controller is null the provided
     * supplier will never be called.
     *
     * @param controller The controller to check for null
     * @param supplier The supplier using the controller
     * @return -1 (unlimited) when the controller is null, otherwise the supplier
     *         value.
     */
    private static long valueOrUnlimited(CgroupSubsystemController controller,
                                         Supplier<Long> supplier) {
        if (controller == null) {
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        }
        return supplier.get();
    }

    @Override
    public String getProvider() {
        return PROVIDER_NAME;
    }

    /*****************************************************************
     * CPU Accounting Subsystem
     ****************************************************************/


    @Override
    public long getCpuUsage() {
        return getLongValue(cpuacct, "cpuacct.usage");
    }

    @Override
    public long[] getPerCpuUsage() {
        String usagelist = CgroupSubsystemController.getStringValue(cpuacct, "cpuacct.usage_percpu");
        if (usagelist == null) {
            return null;
        }

        String list[] = usagelist.split(" ");
        long percpu[] = new long[list.length];
        for (int i = 0; i < list.length; i++) {
            percpu[i] = Long.parseLong(list[i]);
        }
        return percpu;
    }

    @Override
    public long getCpuUserUsage() {
        return CgroupV1SubsystemController.getLongEntry(cpuacct, "cpuacct.stat", "user");
    }

    @Override
    public long getCpuSystemUsage() {
        return CgroupV1SubsystemController.getLongEntry(cpuacct, "cpuacct.stat", "system");
    }


    /*****************************************************************
     * CPU Subsystem
     ****************************************************************/


    @Override
    public long getCpuPeriod() {
        return valueOrUnlimited(cpu, () -> cpu.getCpuPeriod());
    }

    @Override
    public long getCpuQuota() {
        return valueOrUnlimited(cpu, () -> cpu.getCpuQuota());
    }

    @Override
    public long getCpuShares() {
        return valueOrUnlimited(cpu, () -> cpu.getCpuShares());
    }

    @Override
    public long getCpuNumPeriods() {
        return valueOrUnlimited(cpu, () -> cpu.getCpuNumPeriods());
    }

    @Override
    public long getCpuNumThrottled() {
        return valueOrUnlimited(cpu, () -> cpu.getCpuNumThrottled());
    }

    @Override
    public long getCpuThrottledTime() {
        return valueOrUnlimited(cpu, () -> cpu.getCpuThrottledTime());
    }

    @Override
    public long getEffectiveCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }


    /*****************************************************************
     * CPUSet Subsystem
     ****************************************************************/

    @Override
    public int[] getCpuSetCpus() {
        return CgroupSubsystemController.stringRangeToIntArray(CgroupSubsystemController.getStringValue(cpuset, "cpuset.cpus"));
    }

    @Override
    public int[] getEffectiveCpuSetCpus() {
        return CgroupSubsystemController.stringRangeToIntArray(CgroupSubsystemController.getStringValue(cpuset, "cpuset.effective_cpus"));
    }

    @Override
    public int[] getCpuSetMems() {
        return CgroupSubsystemController.stringRangeToIntArray(CgroupSubsystemController.getStringValue(cpuset, "cpuset.mems"));
    }

    @Override
    public int[] getEffectiveCpuSetMems() {
        return CgroupSubsystemController.stringRangeToIntArray(CgroupSubsystemController.getStringValue(cpuset, "cpuset.effective_mems"));
    }

    @Override
    public double getCpuSetMemoryPressure() {
        return CgroupV1SubsystemController.getDoubleValue(cpuset, "cpuset.memory_pressure");
    }

    @Override
    public Boolean isCpuSetMemoryPressureEnabled() {
        long val = getLongValue(cpuset, "cpuset.memory_pressure_enabled");
        return (val == 1);
    }


    /*****************************************************************
     * Memory Subsystem
     ****************************************************************/

    @Override
    public long getMemoryFailCount() {
        return valueOrUnlimited(memory, () -> memory.getMemoryFailCount());
    }

    @Override
    public long getMemoryLimit() {
        Supplier<Long> limitSupplier = () -> memory.getMemoryLimit(CgroupMetrics.getTotalMemorySize0());
        return valueOrUnlimited(memory, limitSupplier);
    }

    @Override
    public long getMemoryMaxUsage() {
        return valueOrUnlimited(memory, () -> memory.getMemoryMaxUsage());
    }

    @Override
    public long getMemoryUsage() {
        return valueOrUnlimited(memory, () -> memory.getMemoryUsage());
    }

    @Override
    public long getTcpMemoryUsage() {
        return valueOrUnlimited(memory, () -> memory.getTcpMemoryUsage());
    }

    @Override
    public long getMemoryAndSwapFailCount() {
        return valueOrUnlimited(memory, () -> memory.getMemoryAndSwapFailCount());
    }

    @Override
    public long getMemoryAndSwapLimit() {
        Supplier<Long> limitSupplier = () -> {
            long hostMem = CgroupMetrics.getTotalMemorySize0();
            long hostSwap = CgroupMetrics.getTotalSwapSize0();
            return memory.getMemoryAndSwapLimit(hostMem, hostSwap);
        };
        return valueOrUnlimited(memory, limitSupplier);
    }

    @Override
    public long getMemoryAndSwapMaxUsage() {
        return valueOrUnlimited(memory, () -> memory.getMemoryAndSwapMaxUsage());
    }

    @Override
    public long getMemoryAndSwapUsage() {
        return valueOrUnlimited(memory, () -> memory.getMemoryAndSwapUsage());
    }

    @Override
    public long getKernelMemoryFailCount() {
        return valueOrUnlimited(memory, () -> memory.getKernelMemoryFailCount());
    }

    @Override
    public long getKernelMemoryMaxUsage() {
        return valueOrUnlimited(memory, () -> memory.getKernelMemoryMaxUsage());
    }

    @Override
    public long getKernelMemoryUsage() {
        return valueOrUnlimited(memory, () -> memory.getKernelMemoryUsage());
    }

    @Override
    public long getTcpMemoryFailCount() {
        return valueOrUnlimited(memory, () -> memory.getTcpMemoryFailCount());
    }

    @Override
    public long getTcpMemoryMaxUsage() {
        return valueOrUnlimited(memory, () -> memory.getTcpMemoryMaxUsage());
    }

    @Override
    public Boolean isMemoryOOMKillEnabled() {
        return memory == null ? false : memory.isMemoryOOMKillEnabled();
    }

    @Override
    public long getMemorySoftLimit() {
        return valueOrUnlimited(memory, () -> memory.getMemorySoftLimit(CgroupMetrics.getTotalMemorySize0()));
    }

    /*****************************************************************
     *  pids subsystem
     ****************************************************************/
    @Override
    public long getPidsMax() {
        String pidsMaxStr = CgroupSubsystemController.getStringValue(pids, "pids.max");
        return CgroupSubsystem.limitFromString(pidsMaxStr);
    }

    @Override
    public long getPidsCurrent() {
        return getLongValue(pids, "pids.current");
    }

    /*****************************************************************
     * BlKIO Subsystem
     ****************************************************************/


    @Override
    public long getBlkIOServiceCount() {
        return CgroupV1SubsystemController.getLongEntry(blkio, "blkio.throttle.io_service_bytes", "Total");
    }

    @Override
    public long getBlkIOServiced() {
        return CgroupV1SubsystemController.getLongEntry(blkio, "blkio.throttle.io_serviced", "Total");
    }

}
