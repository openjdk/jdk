/*
 * Copyright (c) 2025, BELLSOFT. All rights reserved.
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

import jdk.internal.platform.Metrics;
import jdk.test.lib.Container;
import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerfileConfig;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.containers.docker.ContainerRuntimeVersionTestUtils;

import java.util.ArrayList;

import jtreg.SkippedException;

/*
 * @test
 * @bug 8343191
 * @key cgroups
 * @summary Cgroup v1 subsystem fails to set subsystem path
 * @requires container.support
 * @requires !vm.asan
 * @library /test/lib
 * @modules java.base/jdk.internal.platform
 * @build MetricsMemoryTester
 * @run main/timeout=480 TestDockerMemoryMetricsSubgroup
 */

public class TestDockerMemoryMetricsSubgroup {
    private static final String imageName =
            DockerfileConfig.getBaseImageName() + ":" +
            DockerfileConfig.getBaseImageVersion();

    public static void main(String[] args) throws Exception {
        Metrics metrics = Metrics.systemMetrics();
        if (metrics == null) {
            System.out.println("Cgroup not configured.");
            return;
        }
        DockerTestUtils.checkCanTestDocker();

        ContainerRuntimeVersionTestUtils.checkContainerVersionSupported();

        if (DockerTestUtils.isRootless()) {
            throw new SkippedException("Test skipped in rootless mode");
        }

        if ("cgroupv1".equals(metrics.getProvider())) {
            testMemoryLimitSubgroupV1("200m", "400m", false);
            testMemoryLimitSubgroupV1("500m", "1G", false);
            testMemoryLimitSubgroupV1("200m", "400m", true);
            testMemoryLimitSubgroupV1("500m", "1G", true);
        } else if ("cgroupv2".equals(metrics.getProvider())) {
            testMemoryLimitSubgroupV2("200m", "400m", false);
            testMemoryLimitSubgroupV2("500m", "1G", false);
            testMemoryLimitSubgroupV2("200m", "400m", true);
            testMemoryLimitSubgroupV2("500m", "1G", true);
        } else {
            throw new SkippedException("Metrics are from neither cgroup v1 nor v2, skipped for now.");
        }
    }

    private static void testMemoryLimitSubgroupV1(String innerSize, String outerGroupMemorySize, boolean privateNamespace) throws Exception {
        Common.logNewTestCase("testMemoryLimitSubgroup, innerSize = " + innerSize);
        DockerRunOptions opts =
            new DockerRunOptions(imageName, "sh", "-c");
        opts.javaOpts = new ArrayList<>();
        opts.appendTestJavaOptions = false;
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
            .addDockerOpts("--volume", Utils.TEST_JDK + ":/jdk")
            .addDockerOpts("--privileged")
            .addDockerOpts("--cgroupns=" + (privateNamespace ? "private" : "host"))
            .addDockerOpts("--memory", outerGroupMemorySize)
            .addDockerOpts("-e", "LANG=C.UTF-8");
        opts.addClassOptions("mkdir -p /sys/fs/cgroup/memory/test ; " +
            "echo " + innerSize + " > /sys/fs/cgroup/memory/test/memory.limit_in_bytes ; " +
            "echo $$ > /sys/fs/cgroup/memory/test/cgroup.procs ; " +
            "/jdk/bin/java -cp /test-classes/ " +
            "--add-exports java.base/jdk.internal.platform=ALL-UNNAMED " +
            "MetricsMemoryTester memory " + innerSize);

        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }

    private static void testMemoryLimitSubgroupV2(String innerSize, String outerGroupMemorySize, boolean privateNamespace) throws Exception {
        Common.logNewTestCase("testMemoryLimitSubgroup, innerSize = " + innerSize);
        DockerRunOptions opts =
            new DockerRunOptions(imageName, "sh", "-c");
        opts.javaOpts = new ArrayList<>();
        opts.appendTestJavaOptions = false;
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
            .addDockerOpts("--volume", Utils.TEST_JDK + ":/jdk")
            .addDockerOpts("--privileged")
            .addDockerOpts("--cgroupns=" + (privateNamespace ? "private" : "host"))
            .addDockerOpts("-e", "LANG=C.UTF-8")
            .addDockerOpts("--memory", outerGroupMemorySize);
        opts.addClassOptions("mkdir -p /sys/fs/cgroup/memory/test ; " +
            "echo $$ > /sys/fs/cgroup/memory/test/cgroup.procs ; " +
            "echo '+memory' > /sys/fs/cgroup/cgroup.subtree_control ; " +
            "echo '+memory' > /sys/fs/cgroup/memory/cgroup.subtree_control ; " +
            "echo " + innerSize + " > /sys/fs/cgroup/memory/test/memory.max ; " +
            "/jdk/bin/java -cp /test-classes/ " +
            "--add-exports java.base/jdk.internal.platform=ALL-UNNAMED " +
            "MetricsMemoryTester memory " + innerSize);

        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }
}
