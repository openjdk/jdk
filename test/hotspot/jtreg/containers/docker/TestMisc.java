/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @requires docker.support
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build CheckContainerized jdk.test.whitebox.WhiteBox PrintContainerInfo
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run driver TestMisc
 */
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class TestMisc {
    private static final String imageName = Common.imageName("misc");

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testMinusContainerSupport();
            testIsContainerized();
            testPrintContainerInfo();
            testPrintContainerInfoActiveProcessorCount();
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

    private static void testPrintContainerInfoActiveProcessorCount() throws Exception {
        Common.logNewTestCase("Test print_container_info()");

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
            "OSContainer::active_processor_count",
            "Memory Limit",
            "Memory Soft Limit",
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
