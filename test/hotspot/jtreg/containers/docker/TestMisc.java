/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test miscellanous functionality related to JVM running in docker container
 * @requires container.support
 * @requires !vm.asan
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.platform
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build CheckContainerized jdk.test.whitebox.WhiteBox PrintContainerInfo
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run driver TestMisc
 */
import jdk.internal.platform.Metrics;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;


public class TestMisc {
    private static final String imageName = Common.imageName("misc");

    public static void main(String[] args) throws Exception {
        DockerTestUtils.checkCanTestDocker();
        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testMinusContainerSupport();
            testIsContainerized();
            testPrintContainerInfo();
            testPrintContainerInfoActiveProcessorCount();
            testPrintContainerInfoCPUShares();
        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }


    private static void testMinusContainerSupport() throws Exception {
        Common.logNewTestCase("Test related flags: '-UseContainerSupport'");
        DockerRunOptions opts = new DockerRunOptions(imageName, "/jdk/bin/java", "-version");
        opts.addJavaOpts("-XX:-UseContainerSupport", "-Xlog:os+container=trace");

        Common.run(opts)
            .shouldContain("Container Support not enabled");
    }


    private static void testIsContainerized() throws Exception {
        Common.logNewTestCase("Test is_containerized() inside a docker container");

        DockerRunOptions opts = Common.newOpts(imageName, "CheckContainerized");
        Common.addWhiteBoxOpts(opts);

        Common.run(opts)
            .shouldContain(CheckContainerized.INSIDE_A_CONTAINER);
    }


    private static void testPrintContainerInfo() throws Exception {
        Common.logNewTestCase("Test print_container_info()");

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo");
        Common.addWhiteBoxOpts(opts);

        checkContainerInfo(Common.run(opts));
    }

    // Test the mapping function on cgroups v2. Should also pass on cgroups v1 as it's
    // a direct mapping there.
    private static void testPrintContainerInfoCPUShares() throws Exception {
        // Test won't work on cgv1 rootless since resource limits don't work there.
        DockerTestUtils.checkCanUseResourceLimits();
        // Anything less than 1024 should return the back-mapped cpu-shares value without
        // rounding to next multiple of 1024 (on cg v2). Only ensure that we get
        // 'cpu_shares: <back-mapped-value>' over 'cpu_shares: no shares'.
        printContainerInfo(512, 1024, false);
        // Don't use 1024 exactly so as to avoid mapping to the unlimited/uset case.
        // Use a value > 100 post-mapping so as to hit the non-default branch: 1052 => 103
        printContainerInfo(1052, 1024, true);
        // need at least 2 CPU cores for this test to work
        if (Runtime.getRuntime().availableProcessors() >= 2) {
            printContainerInfo(2048, 2048, true);
        }
    }

    private static void printContainerInfo(int cpuShares, int expected, boolean numberMatch) throws Exception {
        Common.logNewTestCase("Test print_container_info() - cpu shares - given: " + cpuShares + ", expected: " + expected);

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo");
        Common.addWhiteBoxOpts(opts);
        opts.addDockerOpts("--cpu-shares", Integer.valueOf(cpuShares).toString());

        OutputAnalyzer out = Common.run(opts);
        String str = out.getOutput();
        boolean isCgroupV2 = str.contains("cgroupv2");
        // cg v1 maps cpu shares values verbatim. Only cg v2 uses the
        // mapping function.
        if (numberMatch) {
          int valueExpected = isCgroupV2 ? expected : cpuShares;
          out.shouldContain("cpu_shares: " + valueExpected);
        } else {
          // must not print "no shares"
          out.shouldNotContain("cpu_shares: no shares");
        }
    }

    private static void testPrintContainerInfoActiveProcessorCount() throws Exception {
        Common.logNewTestCase("Test print_container_info() - ActiveProcessorCount");

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo").addJavaOpts("-XX:ActiveProcessorCount=2");
        Common.addWhiteBoxOpts(opts);

        OutputAnalyzer out = Common.run(opts);
        out.shouldContain("but overridden by -XX:ActiveProcessorCount 2");
    }

    private static void checkContainerInfo(OutputAnalyzer out) throws Exception {
        String[] expectedToContain = new String[] {
            "cpuset.cpus",
            "cpuset.mems",
            "CPU Shares",
            "CPU Quota",
            "CPU Period",
            "CPU Usage",
            "OSContainer::active_processor_count",
            "Memory Limit",
            "Memory Soft Limit",
            "Memory Throttle Limit",
            "Memory Usage",
            "Maximum Memory Usage",
            "memory_max_usage_in_bytes",
            "maximum number of tasks",
            "current number of tasks",
            "rss_usage_in_bytes",
            "cache_usage_in_bytes"
        };

        for (String s : expectedToContain) {
            out.shouldContain(s);
        }
        String str = out.getOutput();
        if (str.contains("cgroupv1")) {
            out.shouldContain("kernel_memory_usage_in_bytes");
            out.shouldContain("kernel_memory_max_usage_in_bytes");
            out.shouldContain("kernel_memory_limit_in_bytes");
        } else {
            if (str.contains("cgroupv2")) {
                out.shouldContain("memory_swap_current_in_bytes");
                out.shouldContain("memory_swap_max_limit_in_bytes");
            } else {
                throw new RuntimeException("Output has to contain information about cgroupv1 or cgroupv2");
            }
        }
    }

}
