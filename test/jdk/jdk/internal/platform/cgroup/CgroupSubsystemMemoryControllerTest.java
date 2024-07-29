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

/*
 * @test
 * @summary Unit test for controller adjustment for memory controllers via CgroupUtil.
 * @key cgroups
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform
 * @run junit/othervm CgroupSubsystemMemoryControllerTest
 */

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import jdk.internal.platform.CgroupSubsystemMemoryController;
import jdk.internal.platform.CgroupUtil;

public class CgroupSubsystemMemoryControllerTest {

    private static final Long UNLIMITED = -1L;
    private static final Long FIVE_HUNDRED_MB = (long)500 * 1024 * 1024 * 1024;

    @Test
    public void noAdjustementNeededIsNoop() {
        MockCgroupSubsystemMemoryController memory = new MockCgroupSubsystemMemoryController(false, null);
        CgroupUtil.adjustController(memory);

        assertNull(memory.getCgroupPath());
    }

    @Test
    public void adjustmentWithNoLowerLimit() {
        String cgroupPath = "/a/b/c/d";
        Map<String, Long> limits = Map.of(cgroupPath, UNLIMITED,
                                          "/a/b/c", UNLIMITED,
                                          "/a/b", UNLIMITED,
                                          "/a", UNLIMITED,
                                          "/", UNLIMITED);
        MockCgroupSubsystemMemoryController memory = new MockCgroupSubsystemMemoryController(true, cgroupPath, limits);
        CgroupUtil.adjustController(memory);

        assertNotNull(memory.getCgroupPath());
        assertEquals(cgroupPath, memory.getCgroupPath());
        assertEquals(5, memory.setCgroupPaths.size());
        List<String> expectedList = List.of( "/a/b/c", "/a/b", "/a", "/", cgroupPath);
        assertEquals(expectedList, memory.setCgroupPaths);
    }

    @Test
    public void adjustedLimitAtRoot() {
        String cgroupPath = "/a/b/c/d";
        String expectedPath = "/";
        Map<String, Long> limits = Map.of(cgroupPath, UNLIMITED,
                                              "/a/b/c", UNLIMITED,
                                              "/a/b", UNLIMITED,
                                              "/a", UNLIMITED,
                                              expectedPath, FIVE_HUNDRED_MB);
        MockCgroupSubsystemMemoryController cpu = new MockCgroupSubsystemMemoryController(true, cgroupPath, limits);
        CgroupUtil.adjustController(cpu);

        assertNotNull(cpu.getCgroupPath());
        assertEquals(expectedPath, cpu.getCgroupPath());
        assertEquals(4, cpu.setCgroupPaths.size());
        List<String> expectedList = List.of("/a/b/c", "/a/b", "/a", "/");
        assertEquals(expectedList, cpu.setCgroupPaths);
    }

    private static class MockCgroupSubsystemMemoryController implements CgroupSubsystemMemoryController {

        private String cgroupPath;
        private final boolean needsAdjustment;
        private final List<String> setCgroupPaths = new ArrayList<>();
        private final Map<String, Long> limits;

        private MockCgroupSubsystemMemoryController(boolean needsAdjustment, String cgroupPath) {
            this(needsAdjustment, cgroupPath, null);
        }

        private MockCgroupSubsystemMemoryController(boolean needsAdjustment, String cgroupPath, Map<String, Long> limits) {
            this.needsAdjustment = needsAdjustment;
            this.limits = limits;
            this.cgroupPath = cgroupPath;
        }

        @Override
        public String path() {
            // doesn't matter
            return null;
        }

        @Override
        public void setPath(String cgroupPath) {
            setCgroupPaths.add(cgroupPath);
            this.cgroupPath = cgroupPath;
        }

        @Override
        public String getCgroupPath() {
            return cgroupPath;
        }

        @Override
        public long getMemoryLimit(long physicalMemory) {
            return limits.get(cgroupPath);
        }

        @Override
        public long getMemoryUsage() {
            // doesn't matter
            return 0;
        }

        @Override
        public long getTcpMemoryUsage() {
            // doesn't matter
            return 0;
        }

        @Override
        public long getMemoryAndSwapLimit(long hostMemory, long hostSwap) {
            // doesn't matter
            return 0;
        }

        @Override
        public long getMemoryAndSwapUsage() {
            // doesn't matter
            return 0;
        }

        @Override
        public long getMemorySoftLimit(long hostMemory) {
            // doesn't matter
            return 0;
        }

        @Override
        public long getMemoryFailCount() {
            // doesn't matter
            return 0;
        }

        @Override
        public boolean needsAdjustment() {
            return needsAdjustment;
        }


    }

}
