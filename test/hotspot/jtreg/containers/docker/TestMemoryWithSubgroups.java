/*
 * Copyright (C) 2025, BELLSOFT. All rights reserved.
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

import jdk.test.lib.Container;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.containers.docker.ContainerRuntimeVersionTestUtils;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.internal.platform.Metrics;

import java.util.ArrayList;

import jtreg.SkippedException;

/*
 * @test
 * @bug 8343191
 * @requires os.family == "linux"
 * @requires !vm.asan
 * @modules java.base/jdk.internal.platform
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main TestMemoryWithSubgroups
 */
public class TestMemoryWithSubgroups {
    private static final String imageName = Common.imageName("subgroup");

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
        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        String provider = metrics.getProvider();
        if (!"cgroupv1".equals(provider) && !"cgroupv2".equals(provider)) {
            throw new SkippedException("Metrics are from neither cgroup v1 nor v2, skipped for now.");
        }

        try {
            testMemoryLimitSubgroup(provider, "200m", "100m", "104857600", false);
            testMemoryLimitSubgroup(provider, "1g", "500m", "524288000", false);
            testMemoryLimitSubgroup(provider, "200m", "100m", "104857600", true);
            testMemoryLimitSubgroup(provider, "1g", "500m", "524288000", true);
        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }

    private static void testMemoryLimitSubgroup(String cgroupVersion, String containerMemorySize,
                                                String valueToSet, String expectedValue, boolean privateNamespace)
            throws Exception {

        final String upperVersion = "cgroupv1".equals(cgroupVersion) ? "V1" : "V2";
        Common.logNewTestCase("Cgroup " + upperVersion + " subgroup memory limit: " + valueToSet);

        DockerRunOptions opts = new DockerRunOptions(imageName, "sh", "-c");
        opts.javaOpts = new ArrayList<>();
        opts.appendTestJavaOptions = false;
        opts.addDockerOpts("--privileged")
            .addDockerOpts("--user", "root")
            .addDockerOpts("--cgroupns=" + (privateNamespace ? "private" : "host"))
            .addDockerOpts("--memory", containerMemorySize);
        if ("cgroupv1".equals(cgroupVersion)) {
            opts.addClassOptions("mkdir -p /sys/fs/cgroup/memory/test ; " +
                "echo " + valueToSet + " > /sys/fs/cgroup/memory/test/memory.limit_in_bytes ; " +
                "echo $$ > /sys/fs/cgroup/memory/test/cgroup.procs ; " +
                "/jdk/bin/java -Xlog:os+container=trace -version");
        } else {
            opts.addClassOptions("mkdir -p /sys/fs/cgroup/memory/test ; " +
                "echo $$ > /sys/fs/cgroup/memory/test/cgroup.procs ; " +
                "echo '+memory' > /sys/fs/cgroup/cgroup.subtree_control ; " +
                "echo '+memory' > /sys/fs/cgroup/memory/cgroup.subtree_control ; " +
                "echo " + valueToSet + " > /sys/fs/cgroup/memory/test/memory.max ; " +
                "/jdk/bin/java -Xlog:os+container=trace -version");
        }

        Common.run(opts)
            .shouldMatch("Lowest limit was:.*" + expectedValue);
    }
}
