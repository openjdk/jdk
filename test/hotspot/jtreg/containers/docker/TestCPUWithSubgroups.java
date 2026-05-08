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
 */

/*
 * @test
 * @key cgroups
 * @summary Test that a stricter leaf CPU quota is not replaced by a looser parent quota
 * @requires container.support
 * @requires !vm.asan
 * @library /test/lib
 * @modules java.base/jdk.internal.platform
 * @run driver TestCPUWithSubgroups
 */

import jdk.internal.platform.Metrics;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;

import jtreg.SkippedException;

public class TestCPUWithSubgroups {
    private static final String imageName = Common.imageName("cpu-subgroup");

    public static void main(String[] args) throws Exception {
        Metrics metrics = Metrics.systemMetrics();
        if (metrics == null) {
            System.out.println("Cgroup not configured.");
            return;
        }

        DockerTestUtils.checkCanTestDocker();
        String provider = metrics.getProvider();
        if (!"cgroupv1".equals(provider) && !"cgroupv2".equals(provider)) {
            throw new SkippedException("Metrics are from neither cgroup v1 nor v2, skipped for now.");
        }
        if ("cgroupv1".equals(provider) && DockerTestUtils.isRootless()) {
            throw new SkippedException("Test skipped in cgroup v1 rootless mode");
        }
        if (Runtime.getRuntime().availableProcessors() < 2) {
            throw new SkippedException("Test requires at least two CPUs.");
        }

        DockerTestUtils.buildJdkContainerImage(imageName);
        try {
            testCpuLimitSubgroup(provider);
        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }

    private static void testCpuLimitSubgroup(String cgroupVersion) throws Exception {
        final String upperVersion = "cgroupv1".equals(cgroupVersion) ? "V1" : "V2";
        Common.logNewTestCase("Cgroup " + upperVersion + " subgroup CPU limit");

        DockerRunOptions opts = new DockerRunOptions(imageName, "sh", "-c");
        opts.javaOpts.clear();
        opts.appendTestJavaOptions = false;
        opts.addDockerOpts("--privileged")
            .addDockerOpts("--user", "root");
        if ("cgroupv1".equals(cgroupVersion)) {
            opts.addClassOptions(
                "CG=/sys/fs/cgroup/cpu; if [ ! -d $CG ]; then CG=/sys/fs/cgroup/cpu,cpuacct; fi; " +
                "CG=$CG/jdk-cpu-hierarchy-test; mkdir -p $CG/leaf; " +
                "echo 100000 > $CG/cpu.cfs_period_us; " +
                "echo 200000 > $CG/cpu.cfs_quota_us; " +
                "echo 100000 > $CG/leaf/cpu.cfs_period_us; " +
                "echo 100000 > $CG/leaf/cpu.cfs_quota_us; " +
                "echo $$ > $CG/leaf/cgroup.procs; " +
                "/jdk/bin/java -Xlog:os+container=trace -Xlog:os=trace -version");
        } else {
            opts.addClassOptions(
                "CG=/sys/fs/cgroup/jdk-cpu-hierarchy-test; mkdir -p $CG/leaf; " +
                "echo $$ > $CG/leaf/cgroup.procs; " +
                "echo '+cpu' > /sys/fs/cgroup/cgroup.subtree_control; " +
                "echo '+cpu' > $CG/cgroup.subtree_control; " +
                "echo '200000 100000' > $CG/cpu.max; " +
                "echo '100000 100000' > $CG/leaf/cpu.max; " +
                "/jdk/bin/java -Xlog:os+container=trace -Xlog:os=trace -version");
        }

        Common.run(opts)
            .shouldContain("active_processor_count: determined by OSContainer: 1");
    }
}
