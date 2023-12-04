/*
 * Copyright (C) 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.internal.platform.Metrics;

/*
 * @test
 * @key cgroups
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox PrintContainerInfo CheckOperatingSystemMXBean
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main TestMemoryWithCgroupV1
 */
public class TestMemoryWithCgroupV1 {

    private static final String imageName = Common.imageName("memory");

    public static void main(String[] args) throws Exception {
        // If cgroups is not configured, report success.
        Metrics metrics = Metrics.systemMetrics();
        if (metrics == null) {
            System.out.println("TEST PASSED!!!");
            return;
        }
        if ("cgroupv1".equals(metrics.getProvider())) {
            if (!DockerTestUtils.canTestDocker()) {
                return;
            }

            Common.prepareWhiteBox();
            DockerTestUtils.buildJdkContainerImage(imageName);

            try {
                testMemoryLimitWithSwappiness("100M", "150M", "100.00M",
                        Integer.toString(((int) Math.pow(2, 20)) * 150),
                        Integer.toString(((int) Math.pow(2, 20)) * 100));
                testOSBeanSwappinessMemory("200m", "250m", "0", "0");
            } finally {
                DockerTestUtils.removeDockerImage(imageName);
            }
        } else {
            System.out.println("Memory swappiness not supported with cgroups v2. Test skipped.");
        }
        System.out.println("TEST PASSED!!!");
    }

    private static void testMemoryLimitWithSwappiness(String dockerMemLimit, String dockerSwapMemLimit,
            String expectedLimit, String expectedReadLimit, String expectedResetLimit)
            throws Exception {
        Common.logNewTestCase("Test print_container_info()");

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo").addJavaOpts("-XshowSettings:system");
        opts.addDockerOpts("--cpus", "4"); // Avoid OOM kill on many-core systems
        opts.addDockerOpts("--memory", dockerMemLimit, "--memory-swappiness", "0", "--memory-swap", dockerSwapMemLimit);
        Common.addWhiteBoxOpts(opts);

        OutputAnalyzer out = Common.run(opts);
        // in case of warnings like : "Your kernel does not support swap limit
        // capabilities or the cgroup is not mounted. Memory limited without swap."
        // we only have Memory and Swap Limit is: <huge integer> in the output
        try {
            if (out.getOutput().contains("memory_and_swap_limit_in_bytes: not supported")) {
                System.out.println("memory_and_swap_limit_in_bytes not supported, avoiding Memory and Swap Limit check");
            } else {
                out.shouldContain("Memory and Swap Limit is: " + expectedReadLimit)
                    .shouldContain(
                        "Memory and Swap Limit has been reset to " + expectedResetLimit + " because swappiness is 0")
                    .shouldContain("Memory & Swap Limit: " + expectedLimit);
            }
        } catch (RuntimeException ex) {
            System.out.println("Expected Memory and Swap Limit output missing.");
            System.out.println("You may need to add 'cgroup_enable=memory swapaccount=1' to the Linux kernel boot parameters.");
            throw ex;
        }
    }

    private static void testOSBeanSwappinessMemory(String memoryAllocation, String swapAllocation,
            String swappiness, String expectedSwap) throws Exception {
        Common.logNewTestCase("Check OperatingSystemMXBean");
        DockerRunOptions opts = Common.newOpts(imageName, "CheckOperatingSystemMXBean")
                .addDockerOpts("--cpus", "4") // Avoid OOM kill on many-core systems
                .addDockerOpts(
                        "--memory", memoryAllocation,
                        "--memory-swappiness", swappiness,
                        "--memory-swap", swapAllocation)
                // CheckOperatingSystemMXBean uses Metrics (jdk.internal.platform) for
                // diagnostics
                .addJavaOpts("--add-exports")
                .addJavaOpts("java.base/jdk.internal.platform=ALL-UNNAMED");
        OutputAnalyzer out = DockerTestUtils.dockerRunJava(opts);
        // in case of warnings like : "Your kernel does not support swap limit
        // capabilities or the cgroup is not mounted. Memory limited without swap."
        // the getTotalSwapSpaceSize and getFreeSwapSpaceSize return the system
        // values as the container setup isn't supported in that case.
        try {
            out.shouldContain("OperatingSystemMXBean.getTotalSwapSpaceSize: " + expectedSwap);
        } catch (RuntimeException ex) {
            out.shouldMatch("OperatingSystemMXBean.getTotalSwapSpaceSize: [0-9]+");
        }
    }

}
