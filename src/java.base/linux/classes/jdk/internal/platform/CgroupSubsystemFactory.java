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

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import jdk.internal.platform.cgroupv1.CgroupV1Subsystem;
import jdk.internal.platform.cgroupv2.CgroupV2Subsystem;

public class CgroupSubsystemFactory {

    private static final String CPU_CTRL = "cpu";
    private static final String CPUACCT_CTRL = "cpuacct";
    private static final String CPUSET_CTRL = "cpuset";
    private static final String BLKIO_CTRL = "blkio";
    private static final String MEMORY_CTRL = "memory";

    static CgroupMetrics create() {
        Optional<CgroupTypeResult> optResult = null;
        try {
            optResult = determineType("/proc/self/mountinfo", "/proc/cgroups");
        } catch (IOException e) {
            return null;
        }

        if (optResult.isEmpty()) {
            return null;
        }
        CgroupTypeResult result = optResult.get();

        // If no controller is enabled, return no metrics.
        if (!result.isAnyControllersEnabled()) {
            return null;
        }

        // Warn about mixed cgroups v1 and cgroups v2 controllers. The code is
        // not ready to deal with that on a per-controller basis. Return no metrics
        // in that case
        if (result.isAnyCgroupV1Controllers() && result.isAnyCgroupV2Controllers()) {
            Logger logger = System.getLogger("jdk.internal.platform");
            logger.log(Level.DEBUG, "Mixed cgroupv1 and cgroupv2 not supported. Metrics disabled.");
            return null;
        }

        if (result.isCgroupV2()) {
            CgroupSubsystem subsystem = CgroupV2Subsystem.getInstance();
            return subsystem != null ? new CgroupMetrics(subsystem) : null;
        } else {
            CgroupV1Subsystem subsystem = CgroupV1Subsystem.getInstance();
            return subsystem != null ? new CgroupV1MetricsImpl(subsystem) : null;
        }
    }

    public static Optional<CgroupTypeResult> determineType(String mountInfo, String cgroups) throws IOException {
        Map<String, CgroupInfo> infos = new HashMap<>();
        List<String> lines = CgroupUtil.readAllLinesPrivileged(Paths.get(cgroups));
        for (String line : lines) {
            if (line.startsWith("#")) {
                continue;
            }
            CgroupInfo info = CgroupInfo.fromCgroupsLine(line);
            switch (info.getName()) {
            case CPU_CTRL:      infos.put(CPU_CTRL, info); break;
            case CPUACCT_CTRL:  infos.put(CPUACCT_CTRL, info); break;
            case CPUSET_CTRL:   infos.put(CPUSET_CTRL, info); break;
            case MEMORY_CTRL:   infos.put(MEMORY_CTRL, info); break;
            case BLKIO_CTRL:    infos.put(BLKIO_CTRL, info); break;
            }
        }

        // For cgroups v2 all controllers need to have zero hierarchy id
        // and /proc/self/mountinfo needs to have at least one cgroup filesystem
        // mounted. Note that hybrid hierarchy has controllers mounted via
        // cgroup v1. In that case hierarchy id's will be non-zero.
        boolean isCgroupsV2 = true;
        boolean anyControllersEnabled = false;
        boolean anyCgroupsV2Controller = false;
        boolean anyCgroupsV1Controller = false;
        for (CgroupInfo info: infos.values()) {
            anyCgroupsV1Controller = anyCgroupsV1Controller || info.getHierarchyId() != 0;
            anyCgroupsV2Controller = anyCgroupsV2Controller || info.getHierarchyId() == 0;
            isCgroupsV2 = isCgroupsV2 && info.getHierarchyId() == 0;
            anyControllersEnabled = anyControllersEnabled || info.isEnabled();
        }

        // If there are no mounted controllers in mountinfo, but we've only
        // seen 0 hierarchy IDs in /proc/cgroups, we are on a cgroups v1 system.
        // However, continuing in that case does not make sense as we'd need
        // information from mountinfo for the mounted controller paths anyway.
        try (Stream<String> mntInfo = CgroupUtil.readFilePrivileged(Paths.get(mountInfo))) {
            boolean anyCgroupMounted = mntInfo.anyMatch(line -> line.contains("cgroup"));
            if (!anyCgroupMounted && isCgroupsV2) {
                return Optional.empty();
            }
        }
        CgroupTypeResult result = new CgroupTypeResult(isCgroupsV2, anyControllersEnabled, anyCgroupsV2Controller, anyCgroupsV1Controller);
        return Optional.of(result);
    }

    public static final class CgroupTypeResult {
        private final boolean isCgroupV2;
        private final boolean anyControllersEnabled;
        private final boolean anyCgroupV2Controllers;
        private final boolean anyCgroupV1Controllers;

        private CgroupTypeResult(boolean isCgroupV2,
                                 boolean anyControllersEnabled,
                                 boolean anyCgroupV2Controllers,
                                 boolean anyCgroupV1Controllers) {
            this.isCgroupV2 = isCgroupV2;
            this.anyControllersEnabled = anyControllersEnabled;
            this.anyCgroupV1Controllers = anyCgroupV1Controllers;
            this.anyCgroupV2Controllers = anyCgroupV2Controllers;
        }

        public boolean isCgroupV2() {
            return isCgroupV2;
        }

        public boolean isAnyControllersEnabled() {
            return anyControllersEnabled;
        }

        public boolean isAnyCgroupV2Controllers() {
            return anyCgroupV2Controllers;
        }

        public boolean isAnyCgroupV1Controllers() {
            return anyCgroupV1Controllers;
        }
    }
}
