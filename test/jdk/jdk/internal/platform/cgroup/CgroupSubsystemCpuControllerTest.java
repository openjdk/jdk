/*
 * Copyright (c) 2024, Red Hat, Inc.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import jdk.internal.platform.CgroupMetrics;
import jdk.internal.platform.CgroupSubsystemCpuController;
import jdk.internal.platform.CgroupUtil;

/*
 * @test
 * @summary Unit test for controller adjustment for cpu controllers via CgroupUtil.
 * @key cgroups
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform
 * @run junit/othervm CgroupSubsystemCpuControllerTest
 */
public class CgroupSubsystemCpuControllerTest {

    private static final CpuLimit UNLIMITED = new CpuLimit(10_000, -1);
    @Test
    public void noAdjustementNeededIsNoop() {
        MockCgroupSubsystemCpuController cpu = new MockCgroupSubsystemCpuController(false, null);
        CgroupUtil.adjustController(cpu);

        assertNull(cpu.getCgroupPath());
    }

    @Test
    public void adjustmentWithNoLowerLimit() {
        String cgroupPath = "/a/b/c/d";
        Map<String, CpuLimit> limits = Map.of(cgroupPath, UNLIMITED,
                                              "/a/b/c", UNLIMITED,
                                              "/a/b", UNLIMITED,
                                              "/a", UNLIMITED,
                                              "/", UNLIMITED);
        MockCgroupSubsystemCpuController cpu = new MockCgroupSubsystemCpuController(true, cgroupPath, limits);
        CgroupUtil.adjustController(cpu);

        assertNotNull(cpu.getCgroupPath());
        assertEquals(cgroupPath, cpu.getCgroupPath());
        assertEquals(5, cpu.setCgroupPaths.size());
        List<String> expectedList = List.of( "/a/b/c", "/a/b", "/a", "/", cgroupPath);
        assertEquals(expectedList, cpu.setCgroupPaths);
    }

    @Test
    public void adjustedLimitLowerInHierarchy() {
        String cgroupPath = "/a/b/c/d";
        String expectedPath = "/a/b";
        long hostCpus = CgroupMetrics.getTotalCpuCount0();
        assumeTrue(hostCpus > 2); // Skip on systems < 3 host cpus.
        Map<String, CpuLimit> limits = Map.of(cgroupPath, UNLIMITED,
                                              "/a/b/c", UNLIMITED,
                                              expectedPath, new CpuLimit(10_000, 20_000) /* two cores */,
                                              "/a", UNLIMITED,
                                              "/", UNLIMITED);
        MockCgroupSubsystemCpuController cpu = new MockCgroupSubsystemCpuController(true, cgroupPath, limits);
        CgroupUtil.adjustController(cpu);

        assertNotNull(cpu.getCgroupPath());
        assertEquals(expectedPath, cpu.getCgroupPath());
        // All paths below /a/b/c/d => /, /a, /a/b, /a/b/c and /a/b
        assertEquals(5, cpu.setCgroupPaths.size());
        // The full hierarchy walk, plus one setPath() call to the expected path
        // due to the lowest limit
        List<String> expectedList = List.of("/a/b/c",
                                            expectedPath,
                                            "/a",
                                            "/",
                                            expectedPath);
        assertEquals(expectedList, cpu.setCgroupPaths);
    }

    @Test
    public void adjustedLimitTwoLowerLimits() {
        String cgroupPath = "/a/b/c/d";
        String pathWithLimit = "/a/b/c";
        String expectedPath = "/a";
        long hostCpus = CgroupMetrics.getTotalCpuCount0();
        assumeTrue(hostCpus > 2); // Skip on systems < 3 host cpus.
        Map<String, CpuLimit> limits = Map.of(cgroupPath, UNLIMITED,
                                              pathWithLimit, new CpuLimit(10_000, 20_000), /* two cores */
                                              "/a/b", UNLIMITED,
                                              expectedPath, new CpuLimit(10_000, 10_000), /* one core */
                                              "/", UNLIMITED);
        MockCgroupSubsystemCpuController cpu = new MockCgroupSubsystemCpuController(true, cgroupPath, limits);
        CgroupUtil.adjustController(cpu);

        assertNotNull(cpu.getCgroupPath());
        assertEquals(expectedPath, cpu.getCgroupPath());
        // All paths below /a/b/c/d => /, /a, /a/b, /a/b/c and /a
        assertEquals(5, cpu.setCgroupPaths.size());
        // The full hierarchy walk, plus one setPath() call to the expected path
        // due to the lowest limit
        List<String> expectedList = List.of(pathWithLimit,
                                            "/a/b",
                                            expectedPath,
                                            "/",
                                            expectedPath);
        assertEquals(expectedList, cpu.setCgroupPaths);
    }

    private static class MockCgroupSubsystemCpuController implements CgroupSubsystemCpuController {

        private String cgroupPath;
        private final boolean needsAdjustment;
        private final List<String> setCgroupPaths = new ArrayList<>();
        private final Map<String, CpuLimit> limits;

        private MockCgroupSubsystemCpuController(boolean needsAdjustment, String cgroupPath) {
            this(needsAdjustment, cgroupPath, null);
        }

        private MockCgroupSubsystemCpuController(boolean needsAdjustment, String cgroupPath, Map<String, CpuLimit> limits) {
            this.needsAdjustment = needsAdjustment;
            this.cgroupPath = cgroupPath;
            this.limits = limits;
        }


        @Override
        public String path() {
            return null; // doesn't matter
        }

        @Override
        public void setPath(String cgroupPath) {
            this.setCgroupPaths.add(cgroupPath);
            this.cgroupPath = cgroupPath;
        }

        @Override
        public String getCgroupPath() {
            return cgroupPath;
        }

        @Override
        public long getCpuPeriod() {
            CpuLimit l = limits.get(cgroupPath);
            return l.getPeriod();
        }

        @Override
        public long getCpuQuota() {
            CpuLimit l = limits.get(cgroupPath);
            return l.getQuota();
        }

        @Override
        public long getCpuShares() {
            // Doesn't matter
            return 0;
        }

        @Override
        public long getCpuNumPeriods() {
            // Doesn't matter
            return 0;
        }

        @Override
        public long getCpuNumThrottled() {
            // Doesn't matter
            return 0;
        }

        @Override
        public long getCpuThrottledTime() {
            // Doesn't matter
            return 0;
        }

        @Override
        public boolean needsAdjustment() {
            return needsAdjustment;
        }

    }

    private static record CpuLimit(long period, long quota) {
        long getPeriod() {
            return period;
        }

        long getQuota() {
            return quota;
        }
    }
}
