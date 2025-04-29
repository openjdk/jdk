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
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.internal.platform.Metrics;

import java.util.ArrayList;

import jtreg.SkippedException;

/*
 * @test
 * @bug 8343191
 * @requires os.family == "linux"
 * @modules java.base/jdk.internal.platform
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main TestMemoryWithSubgroups
 */
public class TestMemoryWithSubgroups {

    private static final String imageName = Common.imageName("subgroup");
    private static final boolean IS_DOCKER = Container.ENGINE_COMMAND.contains("docker");

    static String getEngineInfo(String format) throws Exception {
        return DockerTestUtils.execute(Container.ENGINE_COMMAND, "info", "-f", format)
            .getStdout();
    }

    static boolean isRootless() throws Exception {
        // Docker and Podman have different INFO structures.
        // The node path for Podman is .Host.Security.Rootless, that also holds for
        // Podman emulating Docker CLI. The node path for Docker is .SecurityOptions.
        return (getEngineInfo("{{.Host.Security.Rootless}}").contains("true") ||
                getEngineInfo("{{.SecurityOptions}}").contains("name=rootless"));
    }

    public static void main(String[] args) throws Exception {
        Metrics metrics = Metrics.systemMetrics();
        if (metrics == null) {
            System.out.println("Cgroup not configured.");
            return;
        }
        if (!DockerTestUtils.canTestDocker()) {
            System.out.println("Unable to run docker tests.");
            return;
        }
        if (IS_DOCKER && TestDockerMemoryMetricsSubgroup.DockerVersion.VERSION_20_10_0.compareTo(getDockerVersion()) > 0) {
            throw new SkippedException("Docker version too old for this test. Expected >= 20.10.0");
        }
        if (isRootless()) {
            throw new SkippedException("Test skipped in rootless mode");
        }
        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        if ("cgroupv1".equals(metrics.getProvider())) {
            try {
                testMemoryLimitSubgroupV1("200m", "100m", "104857600", false);
                testMemoryLimitSubgroupV1("1g", "500m", "524288000", false);
                testMemoryLimitSubgroupV1("200m", "100m", "104857600", true);
                testMemoryLimitSubgroupV1("1g", "500m", "524288000", true);
            } finally {
                DockerTestUtils.removeDockerImage(imageName);
            }
        } else if ("cgroupv2".equals(metrics.getProvider())) {
            try {
                testMemoryLimitSubgroupV2("200m", "100m", "104857600", false);
                testMemoryLimitSubgroupV2("1g", "500m", "524288000", false);
                testMemoryLimitSubgroupV2("200m", "100m", "104857600", true);
                testMemoryLimitSubgroupV2("1g", "500m", "524288000", true);
            } finally {
                DockerTestUtils.removeDockerImage(imageName);
            }
        } else {
            throw new SkippedException("Metrics are from neither cgroup v1 nor v2, skipped for now.");
        }
    }

    private static void testMemoryLimitSubgroupV1(String containerMemorySize, String valueToSet, String expectedValue, boolean privateNamespace)
            throws Exception {

        Common.logNewTestCase("Cgroup V1 subgroup memory limit: " + valueToSet);

        DockerRunOptions opts = new DockerRunOptions(imageName, "sh", "-c");
        opts.javaOpts = new ArrayList<>();
        opts.appendTestJavaOptions = false;
        opts.addDockerOpts("--privileged")
            .addDockerOpts("--cgroupns=" + (privateNamespace ? "private" : "host"))
            .addDockerOpts("--memory", containerMemorySize);
        opts.addClassOptions("mkdir -p /sys/fs/cgroup/memory/test ; " +
            "echo " + valueToSet + " > /sys/fs/cgroup/memory/test/memory.limit_in_bytes ; " +
            "echo $$ > /sys/fs/cgroup/memory/test/cgroup.procs ; " +
            "/jdk/bin/java -Xlog:os+container=trace -version");

        Common.run(opts)
            .shouldMatch("Lowest limit was:.*" + expectedValue);
    }

    private static void testMemoryLimitSubgroupV2(String containerMemorySize, String valueToSet, String expectedValue, boolean privateNamespace)
            throws Exception {

        Common.logNewTestCase("Cgroup V2 subgroup memory limit: " + valueToSet);

        DockerRunOptions opts = new DockerRunOptions(imageName, "sh", "-c");
        opts.javaOpts = new ArrayList<>();
        opts.appendTestJavaOptions = false;
        opts.addDockerOpts("--privileged")
            .addDockerOpts("--cgroupns=" + (privateNamespace ? "private" : "host"))
            .addDockerOpts("--memory", containerMemorySize);
        opts.addClassOptions("mkdir -p /sys/fs/cgroup/memory/test ; " +
            "echo $$ > /sys/fs/cgroup/memory/test/cgroup.procs ; " +
            "echo '+memory' > /sys/fs/cgroup/cgroup.subtree_control ; " +
            "echo '+memory' > /sys/fs/cgroup/memory/cgroup.subtree_control ; " +
            "echo " + valueToSet + " > /sys/fs/cgroup/memory/test/memory.max ; " +
            "/jdk/bin/java -Xlog:os+container=trace -version");

        Common.run(opts)
            .shouldMatch("Lowest limit was:.*" + expectedValue);
    }
    // pre: IS_DOCKER == true
    private static String getDockerVersionStr() {
        if (!IS_DOCKER) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(Container.ENGINE_COMMAND, "--version");
            OutputAnalyzer out = new OutputAnalyzer(pb.start())
                    .shouldHaveExitValue(0);
            String result = out.asLines().get(0);
            System.out.println(Container.ENGINE_COMMAND + " --version returning: " + result);
            return result;
        } catch (Exception e) {
            System.out.println(Container.ENGINE_COMMAND + " --version command failed. Returning null");
            return null;
        }
    }

    private static TestDockerMemoryMetricsSubgroup.DockerVersion getDockerVersion() {
        return TestDockerMemoryMetricsSubgroup.DockerVersion.fromVersionString(getDockerVersionStr());
    }

    private static class DockerVersion implements Comparable<TestDockerMemoryMetricsSubgroup.DockerVersion> {
        private static final TestDockerMemoryMetricsSubgroup.DockerVersion DEFAULT = new TestDockerMemoryMetricsSubgroup.DockerVersion(0, 0, 0);
        private static final TestDockerMemoryMetricsSubgroup.DockerVersion VERSION_20_10_0 = new TestDockerMemoryMetricsSubgroup.DockerVersion(20, 10, 0);
        private final int major;
        private final int minor;
        private final int micro;

        private DockerVersion(int major, int minor, int micro) {
            this.major = major;
            this.minor = minor;
            this.micro = micro;
        }

        @Override
        public int compareTo(TestDockerMemoryMetricsSubgroup.DockerVersion other) {
            if (this.major > other.major) {
                return 1;
            } else if (this.major < other.major) {
                return -1;
            } else { // equal major
                if (this.minor > other.minor) {
                    return 1;
                } else if (this.minor < other.minor) {
                    return -1;
                } else { // equal majors and minors
                    if (this.micro > other.micro) {
                        return 1;
                    } else if (this.micro < other.micro) {
                        return -1;
                    } else {
                        // equal majors, minors, micro
                        return 0;
                    }
                }
            }
        }

        private static TestDockerMemoryMetricsSubgroup.DockerVersion fromVersionString(String version) {
            try {
                // Example 'docker version 3.2.1'
                String versNums = version.split("\\s+", 3)[2];
                String[] numbers = versNums.split("\\.", 3);
                return new TestDockerMemoryMetricsSubgroup.DockerVersion(Integer.parseInt(numbers[0]),
                        Integer.parseInt(numbers[1]),
                        Integer.parseInt(numbers[2]));
            } catch (Exception e) {
                System.out.println("Failed to parse docker version: " + version);
                return DEFAULT;
            }
        }
    }
}
