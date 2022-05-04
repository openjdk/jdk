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
 * @build sun.hotspot.WhiteBox PrintContainerInfo
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar sun.hotspot.WhiteBox
 * @run main TestMemoryWithCgroupV1
 */
public class TestMemoryWithCgroupV1 {

    private static final String imageName = Common.imageName("misc");

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
                testMemoryLimitWithSwappiness("100M", "150M");
            } finally {
                DockerTestUtils.removeDockerImage(imageName);
            }
        } else {
            System.out.println("Memory swappiness not supported with cgroups v2. Test skipped.");
        }
        System.out.println("TEST PASSED!!!");
    }

    private static void testMemoryLimitWithSwappiness(String dockerMemLimit, String dockerSwapMemLimit)
            throws Exception {
        Common.logNewTestCase("Test print_container_info()");

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo").addJavaOpts("-XshowSettings:system");
        opts.addDockerOpts("--memory", dockerMemLimit, "--memory-swappiness", "0", "--memory-swap", dockerSwapMemLimit);
        Common.addWhiteBoxOpts(opts);

        OutputAnalyzer out = Common.run(opts);
        out.shouldContain("Memory and Swap Limit is: 157286400")
           .shouldContain("Memory and Swap Limit has been reset to 104857600 because swappiness is 0")
           .shouldContain("Memory & Swap Limit: 100.00M");
    }

}
