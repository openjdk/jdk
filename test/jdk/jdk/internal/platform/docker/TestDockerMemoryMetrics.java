/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;

/*
 * @test
 * @summary Test JDK Metrics class when running inside docker container
 * @requires docker.support
 * @library /test/lib
 * @modules java.base/jdk.internal.platform
 * @build MetricsMemoryTester
 * @run main/timeout=360 TestDockerMemoryMetrics
 */

public class TestDockerMemoryMetrics {
    private static final String imageName = Common.imageName("metrics-memory");

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        // These tests create a docker image and run this image with
        // varying docker memory options.  The arguments passed to the docker
        // container include the Java test class to be run along with the
        // resource to be examined and expected result.

        DockerTestUtils.buildJdkDockerImage(imageName, "Dockerfile-BasicTest", "jdk-docker");
        try {
            testMemoryLimit("200m");
            testMemoryLimit("1g");

            testMemoryAndSwapLimit("200m", "1g");
            testMemoryAndSwapLimit("100m", "200m");

            testKernelMemoryLimit("100m");
            testKernelMemoryLimit("1g");

            testOomKillFlag("100m", false);
            testOomKillFlag("100m", true);

            testMemoryFailCount("64m");

            testMemorySoftLimit("500m","200m");

        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }

    private static void testMemoryLimit(String value) throws Exception {
        Common.logNewTestCase("testMemoryLimit, value = " + value);
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "MetricsMemoryTester");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--memory=" + value)
                .addJavaOpts("-cp", "/test-classes/")
                .addJavaOpts("--add-exports", "java.base/jdk.internal.platform=ALL-UNNAMED")
                .addClassOptions("memory", value);
        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }

    private static void testMemoryFailCount(String value) throws Exception {
        Common.logNewTestCase("testMemoryFailCount" + value);
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "MetricsMemoryTester");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--memory=" + value)
                .addJavaOpts("-Xmx" + value)
                .addJavaOpts("-cp", "/test-classes/")
                .addJavaOpts("--add-exports", "java.base/jdk.internal.platform=ALL-UNNAMED")
                .addClassOptions("failcount");
        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }

    private static void testMemoryAndSwapLimit(String memory, String memandswap) throws Exception {
        Common.logNewTestCase("testMemoryAndSwapLimit, memory = " + memory + ", memory and swap = " + memandswap);
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "MetricsMemoryTester");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--memory=" + memory)
                .addDockerOpts("--memory-swap=" + memandswap)
                .addJavaOpts("-cp", "/test-classes/")
                .addJavaOpts("--add-exports", "java.base/jdk.internal.platform=ALL-UNNAMED")
                .addClassOptions("memoryswap", memory, memandswap);
        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }

    private static void testKernelMemoryLimit(String value) throws Exception {
        Common.logNewTestCase("testKernelMemoryLimit, value = " + value);
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "MetricsMemoryTester");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--kernel-memory=" + value)
                .addJavaOpts("-cp", "/test-classes/")
                .addJavaOpts("--add-exports", "java.base/jdk.internal.platform=ALL-UNNAMED")
                .addClassOptions("kernelmem", value);
        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }

    private static void testOomKillFlag(String value, boolean oomKillFlag) throws Exception {
        Common.logNewTestCase("testOomKillFlag, oomKillFlag = " + oomKillFlag);
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "MetricsMemoryTester");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--memory=" + value);
        if (!oomKillFlag) {
            opts.addDockerOpts("--oom-kill-disable");
        }
        opts.addJavaOpts("-cp", "/test-classes/")
                .addJavaOpts("--add-exports", "java.base/jdk.internal.platform=ALL-UNNAMED")
                .addClassOptions("memory", value, oomKillFlag + "");
        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }

    private static void testMemorySoftLimit(String mem, String softLimit) throws Exception {
        Common.logNewTestCase("testMemorySoftLimit, memory = " + mem + ", soft limit = " + softLimit);
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "MetricsMemoryTester");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--memory=" + mem)
                .addDockerOpts("--memory-reservation=" + softLimit);
        opts.addJavaOpts("-cp", "/test-classes/")
                .addJavaOpts("--add-exports", "java.base/jdk.internal.platform=ALL-UNNAMED")
                .addClassOptions("softlimit", softLimit);
        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }
}
