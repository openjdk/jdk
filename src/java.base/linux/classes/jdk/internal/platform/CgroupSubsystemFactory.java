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

import jdk.internal.platform.cgroupv1.CgroupV1Subsystem;
import jdk.internal.platform.cgroupv2.CgroupV2Subsystem;

class CgroupSubsystemFactory {

    private static final String CPU_CTRL = "cpu";
    private static final String CPUACCT_CTRL = "cpuacct";
    private static final String CPUSET_CTRL = "cpuset";
    private static final String BLKIO_CTRL = "blkio";
    private static final String MEMORY_CTRL = "memory";

    static CgroupMetrics create() {
        Map<String, CgroupInfo> infos = new HashMap<>();
        try {
            List<String> lines = CgroupUtil.readAllLinesPrivileged(Paths.get("/proc/cgroups"));
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
        } catch (IOException e) {
            return null;
        }

        // For cgroups v1 all controllers need to have non-zero hierarchy id
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

        // If no controller is enabled, return no metrics.
        if (!anyControllersEnabled) {
            return null;
        }
        // Warn about mixed cgroups v1 and cgroups v2 controllers. The code is
        // not ready to deal with that on a per-controller basis. Return no metrics
        // in that case
        if (anyCgroupsV1Controller && anyCgroupsV2Controller) {
            Logger logger = System.getLogger("jdk.internal.platform");
            logger.log(Level.DEBUG, "Mixed cgroupv1 and cgroupv2 not supported. Metrics disabled.");
            return null;
        }

        if (isCgroupsV2) {
            CgroupSubsystem subsystem = CgroupV2Subsystem.getInstance();
            return subsystem != null ? new CgroupMetrics(subsystem) : null;
        } else {
            CgroupV1Subsystem subsystem = CgroupV1Subsystem.getInstance();
            return subsystem != null ? new CgroupV1MetricsImpl(subsystem) : null;
        }
    }
}
