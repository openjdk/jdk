/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

/*
 * @test
 * @key cgroups
 * @summary Test JDK Metrics class when running inside docker container
 * @requires container.support
 * @requires !vm.asan
 * @library /test/lib
 * @modules java.base/jdk.internal.platform
 * @build MetricsMemoryTester
 * @run main/timeout=360 TestDockerMemoryMetrics
 */

public class TestDockerMemoryMetrics {
    private static final String imageName = Common.imageName("metrics-memory");

    public static void main(String[] args) throws Exception {
        DockerTestUtils.checkCanTestDocker();
        DockerTestUtils.checkCanUseResourceLimits();

        // These tests create a docker image and run this image with
        // varying docker memory options.  The arguments passed to the docker
        // container include the Java test class to be run along with the
        // resource to be examined and expected result.

        DockerTestUtils.buildJdkContainerImage(imageName);
        try {
            testMemoryLimit("200m");
            testMemoryLimit("1g");
            // Memory limit test with additional cgroup fs mounted
            testMemoryLimit("500m", true /* cgroup fs mount */);

            testMemoryAndSwapLimit("200m", "1g");
            testMemoryAndSwapLimit("100m", "200m");

            Metrics m = Metrics.systemMetrics();
            // OOM killer disable, '--oom-kill-disable' switch, test not supported
            // by cgroupv2
            if (m != null) {
                if ("cgroupv1".equals(m.getProvider())) {
                    testOomKillFlag("100m", false);
                } else {
                    System.out.println("OOM kill disable test not " +
                                       "supported with cgroupv2.");
                }
            }
            testOomKillFlag("100m", true);

            testMemoryFailCount("128m" /*memory*/, "768m" /*max_heap*/, "1024m" /*memory_n_swap*/);

            testMemorySoftLimit("500m","200m");

        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }

    private static void testMemoryLimit(String value) throws Exception {
        testMemoryLimit(value, false);
    }

    private static void testMemoryLimit(String value, boolean addCgroupMount) throws Exception {
        Common.logNewTestCase("testMemoryLimit, value = " + value);
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "MetricsMemoryTester");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--memory=" + value)
                .addJavaOpts("-cp", "/test-classes/")
                .addJavaOpts("--add-exports", "java.base/jdk.internal.platform=ALL-UNNAMED")
                .addClassOptions("memory", value);
        if (addCgroupMount) {
            // Extra cgroup mount should be ignored by product code
            opts.addDockerOpts("--volume", "/sys/fs/cgroup:/cgroup-in:ro");
        }
        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
    }

    private static void testMemoryFailCount(String memory, String heap, String memoryAndSwap) throws Exception {
        Common.logNewTestCase("testMemoryFailCount, memory = " + memory
                + ", heap = " + heap
                + ", memory + swap = " + memoryAndSwap);

        // Check whether swapping really works for this test
        // On some systems there is no swap space enabled. And running
        // 'java -Xms{heap} -Xmx{heap} -XX:+AlwaysPreTouch -version'
        // would fail due to swap space size being 0. Note that when swap is
        // properly enabled, the explicit memory-and-swap limit gives the JVM
        // enough headroom to exceed the physical memory limit without being
        // killed by the OOM killer.
        DockerRunOptions preOpts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "-version");
        preOpts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--memory=" + memory)
                .addDockerOpts("--memory-swap=" + memoryAndSwap)
                .addJavaOpts("-XX:+AlwaysPreTouch")
                .addJavaOptsAppended("-XX:InitialHeapSize=" + heap)
                .addJavaOptsAppended("-XX:MaxHeapSize=" + heap);
        OutputAnalyzer oa = DockerTestUtils.dockerRunJava(preOpts);
        String output = oa.getOutput();
        if (!output.contains("version")) {
            throw new SkippedException("Swapping doesn't work for this test.");
        }

        //  0                   128                                                       1024
        //  |---o----------------|---------------------------X--------------)-------------|
        //      START            memory.max                  growth target  MaxHeapSize   memory+swap limit
        //      o~~~~~>~>~>~>~>~>~>~>~>~>~>~>~>~>~>~>~>  (growth)                          OOM
        //  failcount: 0          1 2 3 . . . N
        //
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "MetricsMemoryTester");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
                .addDockerOpts("--memory=" + memory)
                .addDockerOpts("--memory-swap=" + memoryAndSwap)
                .addJavaOpts("-cp", "/test-classes/")
                .addJavaOpts("--add-exports", "java.base/jdk.internal.platform=ALL-UNNAMED")
                // set the required heap size *after* inherited jtreg options
                .addJavaOptsAppended("-XX:MaxHeapSize=" + heap)
                .addClassOptions("failcount");
        oa = DockerTestUtils.dockerRunJava(opts);
        output = oa.getOutput();
        if (output.contains("Ignoring test")) {
            throw new SkippedException("Ignored by the tester");
        }
        oa.shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
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
        OutputAnalyzer oa = DockerTestUtils.dockerRunJava(opts);
        oa.shouldHaveExitValue(0).shouldContain("TEST PASSED!!!");
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
