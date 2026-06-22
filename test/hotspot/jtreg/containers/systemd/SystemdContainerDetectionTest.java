/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import jdk.internal.platform.Metrics;
import jdk.test.lib.containers.systemd.SystemdRunOptions;
import jdk.test.lib.containers.systemd.SystemdTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

/*
 * @test
 * @summary Container detection test for a JDK inside a systemd slice.
 * @requires systemd.support
 * @library /test/lib
 * @modules java.base/jdk.internal.platform
 * @build HelloSystemd jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm SystemdContainerDetectionTest
 */
public class SystemdContainerDetectionTest {

    private static final int MB = 1024 * 1024;
    private static final int EXPECTED_LIMIT_MB = 512;

    public static void main(String[] args) throws Exception {
        Metrics metrics = Metrics.systemMetrics();

        testWithoutLimits();
        testCpuLimit();
        testMemoryLimit();
        if ("cgroupv2".equals(metrics.getProvider())) {
            // MemoryHigh/MemoryLow based detection requires cgroup v2
            testMemoryLow();
            testMemoryHigh();
        }
    }

    private static void testWithoutLimits() throws Exception {
        OutputAnalyzer out = runWithLimits("infinity", null, null, null);
        assertContainerized(out, false);
    }

    private static void testCpuLimit() throws Exception {
        OutputAnalyzer out = runWithLimits("infinity", null, null, "50%");
        assertContainerized(out, true);
        out.shouldContain("OSContainer::active_processor_count:");
    }

    private static void testMemoryLimit() throws Exception {
        OutputAnalyzer out = runWithLimits(String.format("%dM", EXPECTED_LIMIT_MB), null, null, null);
        assertContainerized(out, true);
        out.shouldContain(String.format("Memory Limit is: %d", EXPECTED_LIMIT_MB * MB));
    }

    private static void testMemoryLow() throws Exception {
        OutputAnalyzer out = runWithLimits("infinity", String.format("%dM", EXPECTED_LIMIT_MB), null, null);
        assertContainerized(out, true);
        out.shouldContain(String.format("Memory Soft Limit is: %d", EXPECTED_LIMIT_MB * MB));
    }

    private static void testMemoryHigh() throws Exception {
        OutputAnalyzer out = runWithLimits("infinity", null, String.format("%dM", EXPECTED_LIMIT_MB), null);
        assertContainerized(out, true);
        out.shouldContain(String.format("Memory Throttle Limit is: %d", EXPECTED_LIMIT_MB * MB));
    }

    private static void assertContainerized(OutputAnalyzer out, boolean expected) {
        try {
            out.shouldHaveExitValue(0)
               .shouldContain("Hello Systemd")
               .shouldContain(String.format("OSContainer::init: is_containerized() = %b", expected));
        } catch (RuntimeException e) {
            // CPU/memory delegation needs to be enabled when run as user on cg v2
            if (SystemdTestUtils.RUN_AS_USER) {
                String hint = "When run as user on cg v2 cpu/memory delegation needs to be configured!";
                throw new SkippedException(hint);
            }
            throw e;
        }
    }

    private static OutputAnalyzer runWithLimits(String memoryLimit,
                                                String memoryLow,
                                                String memoryHigh,
                                                String cpuLimit) throws Exception {
        SystemdRunOptions opts = SystemdTestUtils.newOpts("HelloSystemd");
        opts.memoryLimit(memoryLimit);
        if (memoryHigh != null) {
            opts.memoryHigh(memoryHigh);
        }
        if (memoryLow != null) {
            opts.memoryLow(memoryLow);
        }
        if (cpuLimit != null) {
            opts.cpuLimit(cpuLimit);
        }
        return SystemdTestUtils.buildAndRunSystemdJava(opts);
    }

}
