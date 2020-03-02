/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import jdk.internal.platform.CgroupSubsystem;
import jdk.internal.platform.CgroupSubsystemController;
import jdk.internal.platform.CgroupUtil;
import jdk.internal.platform.CgroupV1Metrics;

public class CgroupV1Subsystem implements CgroupSubsystem, CgroupV1Metrics {
    private CgroupV1MemorySubSystemController memory;
    private CgroupV1SubsystemController cpu;
    private CgroupV1SubsystemController cpuacct;
    private CgroupV1SubsystemController cpuset;
    private CgroupV1SubsystemController blkio;
    private boolean activeSubSystems;

    private static final CgroupV1Subsystem INSTANCE = initSubSystem();

    private static final String PROVIDER_NAME = "cgroupv1";

    private CgroupV1Subsystem() {
        activeSubSystems = false;
    }

    public static CgroupV1Subsystem getInstance() {
        return INSTANCE;
    }

    private static CgroupV1Subsystem initSubSystem() {
        CgroupV1Subsystem subsystem = new CgroupV1Subsystem();

        /**
         * Find the cgroup mount points for subsystems
         * by reading /proc/self/mountinfo
         *
         * Example for docker MemorySubSystem subsystem:
         * 219 214 0:29 /docker/7208cebd00fa5f2e342b1094f7bed87fa25661471a4637118e65f1c995be8a34 /sys/fs/cgroup/MemorySubSystem ro,nosuid,nodev,noexec,relatime - cgroup cgroup rw,MemorySubSystem
         *
         * Example for host:
         * 34 28 0:29 / /sys/fs/cgroup/MemorySubSystem rw,nosuid,nodev,noexec,relatime shared:16 - cgroup cgroup rw,MemorySubSystem
         */
        try (Stream<String> lines =
                CgroupUtil.readFilePrivileged(Paths.get("/proc/self/mountinfo"))) {

            lines.filter(line -> line.contains(" - cgroup "))
                 .map(line -> line.split(" "))
                 .forEach(entry -> createSubSystemController(subsystem, entry));

        } catch (IOException e) {
            return null;
        }

        /**
         * Read /proc/self/cgroup and map host mount point to
         * local one via /proc/self/mountinfo content above
         *
         * Docker example:
         * 5:memory:/docker/6558aed8fc662b194323ceab5b964f69cf36b3e8af877a14b80256e93aecb044
         *
         * Host example:
         * 5:memory:/user.slice
         *
         * Construct a path to the process specific memory and cpuset
         * cgroup directory.
         *
         * For a container running under Docker from memory example above
         * the paths would be:
         *
         * /sys/fs/cgroup/memory
         *
         * For a Host from memory example above the path would be:
         *
         * /sys/fs/cgroup/memory/user.slice
         *
         */
        try (Stream<String> lines =
                CgroupUtil.readFilePrivileged(Paths.get("/proc/self/cgroup"))) {

            lines.map(line -> line.split(":"))
                 .filter(line -> (line.length >= 3))
                 .forEach(line -> setSubSystemControllerPath(subsystem, line));

        } catch (IOException e) {
            return null;
        }

        // Return Metrics object if we found any subsystems.
        if (subsystem.activeSubSystems()) {
            return subsystem;
        }

        return null;
    }

    /**
     * createSubSystem objects and initialize mount points
     */
    private static void createSubSystemController(CgroupV1Subsystem subsystem, String[] mountentry) {
        if (mountentry.length < 5) return;

        Path p = Paths.get(mountentry[4]);
        String[] subsystemNames = p.getFileName().toString().split(",");

        for (String subsystemName: subsystemNames) {
            switch (subsystemName) {
                case "memory":
                    subsystem.setMemorySubSystem(new CgroupV1MemorySubSystemController(mountentry[3], mountentry[4]));
                    break;
                case "cpuset":
                    subsystem.setCpuSetController(new CgroupV1SubsystemController(mountentry[3], mountentry[4]));
                    break;
                case "cpuacct":
                    subsystem.setCpuAcctController(new CgroupV1SubsystemController(mountentry[3], mountentry[4]));
                    break;
                case "cpu":
                    subsystem.setCpuController(new CgroupV1SubsystemController(mountentry[3], mountentry[4]));
                    break;
                case "blkio":
                    subsystem.setBlkIOController(new CgroupV1SubsystemController(mountentry[3], mountentry[4]));
                    break;
                default:
                    // Ignore subsystems that we don't support
                    break;
            }
        }
    }

    /**
     * setSubSystemPath based on the contents of /proc/self/cgroup
     */
    private static void setSubSystemControllerPath(CgroupV1Subsystem subsystem, String[] entry) {
        String controllerName;
        String base;
        CgroupV1SubsystemController controller = null;
        CgroupV1SubsystemController controller2 = null;

        controllerName = entry[1];
        base = entry[2];
        if (controllerName != null && base != null) {
            switch (controllerName) {
                case "memory":
                    controller = subsystem.memoryController();
                    break;
                case "cpuset":
                    controller = subsystem.cpuSetController();
                    break;
                case "cpu,cpuacct":
                case "cpuacct,cpu":
                    controller = subsystem.cpuController();
                    controller2 = subsystem.cpuAcctController();
                    break;
                case "cpuacct":
                    controller = subsystem.cpuAcctController();
                    break;
                case "cpu":
                    controller = subsystem.cpuController();
                    break;
                case "blkio":
                    controller = subsystem.blkIOController();
                    break;
                // Ignore subsystems that we don't support
                default:
                    break;
            }
        }

        if (controller != null) {
            controller.setPath(base);
            if (controller instanceof CgroupV1MemorySubSystemController) {
                CgroupV1MemorySubSystemController memorySubSystem = (CgroupV1MemorySubSystemController)controller;
                boolean isHierarchial = getHierarchical(memorySubSystem);
                memorySubSystem.setHierarchical(isHierarchial);
            }
            subsystem.setActiveSubSystems();
        }
        if (controller2 != null) {
            controller2.setPath(base);
        }
    }


    private static boolean getHierarchical(CgroupV1MemorySubSystemController controller) {
        long hierarchical = getLongValue(controller, "memory.use_hierarchy");
        return hierarchical > 0;
    }

    private void setActiveSubSystems() {
        activeSubSystems = true;
    }

    private boolean activeSubSystems() {
        return activeSubSystems;
    }

    private void setMemorySubSystem(CgroupV1MemorySubSystemController memory) {
        this.memory = memory;
    }

    private void setCpuController(CgroupV1SubsystemController cpu) {
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

    private CgroupV1SubsystemController memoryController() {
        return memory;
    }

    private CgroupV1SubsystemController cpuController() {
        return cpu;
    }

    private CgroupV1SubsystemController cpuAcctController() {
        return cpuacct;
    }

    private CgroupV1SubsystemController cpuSetController() {
        return cpuset;
    }

    private CgroupV1SubsystemController blkIOController() {
        return blkio;
    }

    private static long getLongValue(CgroupSubsystemController controller,
                              String parm) {
        return CgroupSubsystemController.getLongValue(controller,
                                                      parm,
                                                      CgroupV1SubsystemController::convertStringToLong,
                                                      CgroupSubsystem.LONG_RETVAL_UNLIMITED);
    }

    public String getProvider() {
        return PROVIDER_NAME;
    }

    /*****************************************************************
     * CPU Accounting Subsystem
     ****************************************************************/


    public long getCpuUsage() {
        return getLongValue(cpuacct, "cpuacct.usage");
    }

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

    public long getCpuUserUsage() {
        return CgroupV1SubsystemController.getLongEntry(cpuacct, "cpuacct.stat", "user");
    }

    public long getCpuSystemUsage() {
        return CgroupV1SubsystemController.getLongEntry(cpuacct, "cpuacct.stat", "system");
    }


    /*****************************************************************
     * CPU Subsystem
     ****************************************************************/


    public long getCpuPeriod() {
        return getLongValue(cpu, "cpu.cfs_period_us");
    }

    public long getCpuQuota() {
        return getLongValue(cpu, "cpu.cfs_quota_us");
    }

    public long getCpuShares() {
        long retval = getLongValue(cpu, "cpu.shares");
        if (retval == 0 || retval == 1024)
            return CgroupSubsystem.LONG_RETVAL_UNLIMITED;
        else
            return retval;
    }

    public long getCpuNumPeriods() {
        return CgroupV1SubsystemController.getLongEntry(cpu, "cpu.stat", "nr_periods");
    }

    public long getCpuNumThrottled() {
        return CgroupV1SubsystemController.getLongEntry(cpu, "cpu.stat", "nr_throttled");
    }

    public long getCpuThrottledTime() {
        return CgroupV1SubsystemController.getLongEntry(cpu, "cpu.stat", "throttled_time");
    }

    public long getEffectiveCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }


    /*****************************************************************
     * CPUSet Subsystem
     ****************************************************************/

    public int[] getCpuSetCpus() {
        return CgroupSubsystemController.stringRangeToIntArray(CgroupSubsystemController.getStringValue(cpuset, "cpuset.cpus"));
    }

    public int[] getEffectiveCpuSetCpus() {
        return CgroupSubsystemController.stringRangeToIntArray(CgroupSubsystemController.getStringValue(cpuset, "cpuset.effective_cpus"));
    }

    public int[] getCpuSetMems() {
        return CgroupSubsystemController.stringRangeToIntArray(CgroupSubsystemController.getStringValue(cpuset, "cpuset.mems"));
    }

    public int[] getEffectiveCpuSetMems() {
        return CgroupSubsystemController.stringRangeToIntArray(CgroupSubsystemController.getStringValue(cpuset, "cpuset.effective_mems"));
    }

    public double getCpuSetMemoryPressure() {
        return CgroupV1SubsystemController.getDoubleValue(cpuset, "cpuset.memory_pressure");
    }

    public Boolean isCpuSetMemoryPressureEnabled() {
        long val = getLongValue(cpuset, "cpuset.memory_pressure_enabled");
        return (val == 1);
    }


    /*****************************************************************
     * Memory Subsystem
     ****************************************************************/


    public long getMemoryFailCount() {
        return getLongValue(memory, "memory.failcnt");
    }

    public long getMemoryLimit() {
        long retval = getLongValue(memory, "memory.limit_in_bytes");
        if (retval > CgroupV1SubsystemController.UNLIMITED_MIN) {
            if (memory.isHierarchical()) {
                // memory.limit_in_bytes returned unlimited, attempt
                // hierarchical memory limit
                String match = "hierarchical_memory_limit";
                retval = CgroupV1SubsystemController.getLongValueMatchingLine(memory,
                                                            "memory.stat",
                                                            match);
            }
        }
        return CgroupV1SubsystemController.longValOrUnlimited(retval);
    }

    public long getMemoryMaxUsage() {
        return getLongValue(memory, "memory.max_usage_in_bytes");
    }

    public long getMemoryUsage() {
        return getLongValue(memory, "memory.usage_in_bytes");
    }

    public long getKernelMemoryFailCount() {
        return getLongValue(memory, "memory.kmem.failcnt");
    }

    public long getKernelMemoryLimit() {
        return CgroupV1SubsystemController.longValOrUnlimited(getLongValue(memory, "memory.kmem.limit_in_bytes"));
    }

    public long getKernelMemoryMaxUsage() {
        return getLongValue(memory, "memory.kmem.max_usage_in_bytes");
    }

    public long getKernelMemoryUsage() {
        return getLongValue(memory, "memory.kmem.usage_in_bytes");
    }

    public long getTcpMemoryFailCount() {
        return getLongValue(memory, "memory.kmem.tcp.failcnt");
    }

    public long getTcpMemoryLimit() {
        return CgroupV1SubsystemController.longValOrUnlimited(getLongValue(memory, "memory.kmem.tcp.limit_in_bytes"));
    }

    public long getTcpMemoryMaxUsage() {
        return getLongValue(memory, "memory.kmem.tcp.max_usage_in_bytes");
    }

    public long getTcpMemoryUsage() {
        return getLongValue(memory, "memory.kmem.tcp.usage_in_bytes");
    }

    public long getMemoryAndSwapFailCount() {
        return getLongValue(memory, "memory.memsw.failcnt");
    }

    public long getMemoryAndSwapLimit() {
        long retval = getLongValue(memory, "memory.memsw.limit_in_bytes");
        if (retval > CgroupV1SubsystemController.UNLIMITED_MIN) {
            if (memory.isHierarchical()) {
                // memory.memsw.limit_in_bytes returned unlimited, attempt
                // hierarchical memory limit
                String match = "hierarchical_memsw_limit";
                retval = CgroupV1SubsystemController.getLongValueMatchingLine(memory,
                                                            "memory.stat",
                                                            match);
            }
        }
        return CgroupV1SubsystemController.longValOrUnlimited(retval);
    }

    public long getMemoryAndSwapMaxUsage() {
        return getLongValue(memory, "memory.memsw.max_usage_in_bytes");
    }

    public long getMemoryAndSwapUsage() {
        return getLongValue(memory, "memory.memsw.usage_in_bytes");
    }

    public Boolean isMemoryOOMKillEnabled() {
        long val = CgroupV1SubsystemController.getLongEntry(memory, "memory.oom_control", "oom_kill_disable");
        return (val == 0);
    }

    public long getMemorySoftLimit() {
        return CgroupV1SubsystemController.longValOrUnlimited(getLongValue(memory, "memory.soft_limit_in_bytes"));
    }


    /*****************************************************************
     * BlKIO Subsystem
     ****************************************************************/


    public long getBlkIOServiceCount() {
        return CgroupV1SubsystemController.getLongEntry(blkio, "blkio.throttle.io_service_bytes", "Total");
    }

    public long getBlkIOServiced() {
        return CgroupV1SubsystemController.getLongEntry(blkio, "blkio.throttle.io_serviced", "Total");
    }

}
