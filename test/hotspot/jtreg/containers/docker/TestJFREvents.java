/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Ensure that certain JFR events return correct results for resource values
 *          when run inside Docker container, such as available CPU and memory.
 *          Also make sure that PIDs are based on value provided by container,
 *          not by the host system.
 * @requires (container.support & os.maxMemory >= 2g)
 * @requires !vm.asan
 * @modules java.base/jdk.internal.platform
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build JfrReporter
 * @run driver TestJFREvents
 */
import java.util.List;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Utils;
import jdk.internal.platform.Metrics;


public class TestJFREvents {
    private static final String imageName = Common.imageName("jfr-events");
    private static final String TEST_ENV_VARIABLE = "UNIQUE_VARIABLE_ABC592903XYZ";
    private static final String TEST_ENV_VALUE = "unique_value_abc592903xyz";
    private static final int availableCPUs = Runtime.getRuntime().availableProcessors();
    private static final int UNKNOWN = -100;
    private static boolean isCgroupV1 = false;

    public static void main(String[] args) throws Exception {
        System.out.println("Test Environment: detected availableCPUs = " + availableCPUs);
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        // If cgroups is not configured, report success.
        Metrics metrics = Metrics.systemMetrics();
        if (metrics == null) {
            System.out.println("TEST PASSED!!!");
            return;
        }
        isCgroupV1 = "cgroupv1".equals(metrics.getProvider());

        DockerTestUtils.buildJdkContainerImage(imageName);

        try {

            long MB = 1024*1024;
            testMemory("200m", "" + 200*MB);
            testMemory("500m", "" + 500*MB);
            testMemory("1g", "" + 1024*MB);

            // see https://docs.docker.com/config/containers/resource_constraints/
            testSwapMemory("200m", "200m", "" + 0*MB, "" + 0*MB);
            testSwapMemory("200m", "300m", "" + 100*MB, "" + 100*MB);

            testProcessInfo();

            testEnvironmentVariables();

            containerInfoTestCase();
            testCpuUsage();
            testCpuThrottling();
            testMemoryUsage();
            testIOUsage();
        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }

    private static void containerInfoTestCase() throws Exception {
        long hostTotalMemory = getHostTotalMemory();
        System.out.println("Debug: Host total memory is " + hostTotalMemory);
        // Leave one CPU for system and tools, otherwise this test may be unstable.
        // Try the memory sizes that were verified by testMemory tests before.
        int maxNrOfAvailableCpus = availableCPUs - 1;
        for (int cpus = 1; cpus < maxNrOfAvailableCpus; cpus *= 2) {
            for (int mem : new int[]{ 200, 500, 1024 }) {
                testContainerInfo(cpus, mem, hostTotalMemory);
            }
        }
    }

    private static long getHostTotalMemory() throws Exception {
        DockerRunOptions opts = Common.newOpts(imageName);

        String hostMem = Common.run(opts).firstMatch("total physical memory: (\\d+)", 1);
        try {
            return Long.parseLong(hostMem);
        } catch (NumberFormatException e) {
            System.out.println("Could not parse total physical memory '" + hostMem + "' returning " + UNKNOWN);
            return UNKNOWN;
        }
    }

    private static void testContainerInfo(int expectedCPUs, int expectedMemoryMB, long hostTotalMemory) throws Exception {
        Common.logNewTestCase("ContainerInfo: --cpus=" + expectedCPUs + " --memory=" + expectedMemoryMB + "m");
        String eventName = "jdk.ContainerConfiguration";
        long expectedSlicePeriod = 100000; // default slice period
        long expectedMemoryLimit = expectedMemoryMB * 1024 * 1024;

        String cpuCountFld = "effectiveCpuCount";
        String cpuQuotaFld = "cpuQuota";
        String cpuSlicePeriodFld = "cpuSlicePeriod";
        String memoryLimitFld = "memoryLimit";
        String totalMem = "hostTotalMemory";

        DockerTestUtils.dockerRunJava(
                                      commonDockerOpts()
                                      .addDockerOpts("--cpus=" + expectedCPUs)
                                      .addDockerOpts("--memory=" + expectedMemoryMB + "m")
                                      .addClassOptions(eventName))
            .shouldHaveExitValue(0)
            .shouldContain(cpuCountFld + " = " + expectedCPUs)
            .shouldContain(cpuSlicePeriodFld + " = " + expectedSlicePeriod)
            .shouldContain(cpuQuotaFld + " = " + expectedCPUs * expectedSlicePeriod)
            .shouldContain(memoryLimitFld + " = " + expectedMemoryLimit)
            .shouldContain(totalMem + " = " + hostTotalMemory)
            .shouldContain("hostTotalSwapMemory");
    }

    private static void testCpuUsage() throws Exception {
        Common.logNewTestCase("CPU Usage");
        String eventName = "jdk.ContainerCPUUsage";

        String cpuTimeFld = "cpuTime";
        String cpuUserTimeFld = "cpuUserTime";
        String cpuSystemTimeFld = "cpuSystemTime";

        DockerTestUtils.dockerRunJava(
                                      commonDockerOpts()
                                      .addClassOptions(eventName, "period=endChunk"))
            .shouldHaveExitValue(0)
            .shouldNotContain(cpuTimeFld + " = " + 0)
            .shouldNotContain(cpuUserTimeFld + " = " + 0)
            .shouldNotContain(cpuSystemTimeFld + " = " + 0);
    }

    private static void testMemoryUsage() throws Exception {
        Common.logNewTestCase("Memory Usage");
        String eventName = "jdk.ContainerMemoryUsage";

        String memoryFailCountFld = "memoryFailCount";
        String memoryUsageFld = "memoryUsage";
        String swapMemoryUsageFld = "swapMemoryUsage";

        DockerTestUtils.dockerRunJava(
                                      commonDockerOpts()
                                      .addClassOptions(eventName, "period=endChunk"))
            .shouldHaveExitValue(0)
            .shouldContain(memoryFailCountFld)
            .shouldContain(memoryUsageFld)
            .shouldContain(swapMemoryUsageFld);
    }

    private static void testIOUsage() throws Exception {
        Common.logNewTestCase("I/O Usage");
        String eventName = "jdk.ContainerIOUsage";

        String serviceRequestsFld = "serviceRequests";
        String dataTransferredFld = "dataTransferred";

        DockerTestUtils.dockerRunJava(
                                      commonDockerOpts()
                                      .addClassOptions(eventName, "period=endChunk"))
            .shouldHaveExitValue(0)
            .shouldContain(serviceRequestsFld)
            .shouldContain(dataTransferredFld);
    }

    private static void testCpuThrottling() throws Exception {
        Common.logNewTestCase("CPU Throttling");
        String eventName = "jdk.ContainerCPUThrottling";

        String cpuElapsedSlicesFld = "cpuElapsedSlices";
        String cpuThrottledSlicesFld = "cpuThrottledSlices";
        String cpuThrottledTimeFld = "cpuThrottledTime";

        DockerTestUtils.dockerRunJava(
                                      commonDockerOpts()
                                      .addClassOptions(eventName, "period=endChunk"))
            .shouldHaveExitValue(0)
            .shouldContain(cpuElapsedSlicesFld)
            .shouldContain(cpuThrottledSlicesFld)
            .shouldContain(cpuThrottledTimeFld);
    }


    private static void testMemory(String valueToSet, String expectedValue) throws Exception {
        Common.logNewTestCase("Memory: --memory = " + valueToSet);
        DockerTestUtils.dockerRunJava(
                                      commonDockerOpts()
                                      .addDockerOpts("--memory=" + valueToSet)
                                      .addClassOptions("jdk.PhysicalMemory"))
            .shouldHaveExitValue(0)
            .shouldContain("totalSize = " + expectedValue);
    }


    private static void testSwapMemory(String memValueToSet, String swapValueToSet, String expectedTotalValue, String expectedFreeValue) throws Exception {
        Common.logNewTestCase("Memory: --memory = " + memValueToSet + " --memory-swap = " + swapValueToSet);
        DockerRunOptions opts = commonDockerOpts();
        opts.addDockerOpts("--memory=" + memValueToSet)
            .addDockerOpts("--memory-swap=" + swapValueToSet)
            .addClassOptions("jdk.SwapSpace");
        if (isCgroupV1) {
            // With Cgroupv1, The default memory-swappiness vaule is inherited from the host machine, which maybe 0
            opts.addDockerOpts("--memory-swappiness=60");
        }
        OutputAnalyzer out = DockerTestUtils.dockerRunJava(opts);
        out.shouldHaveExitValue(0)
            .shouldContain("totalSize = " + expectedTotalValue)
            .shouldContain("freeSize = ");
        List<String> ls = out.asLinesWithoutVMWarnings();
        for (String cur : ls) {
            int idx = cur.indexOf("freeSize = ");
            if (idx != -1) {
                int startNbr = idx+11;
                int endNbr = cur.indexOf(' ', startNbr);
                if (endNbr == -1) endNbr = cur.length();
                String freeSizeStr = cur.substring(startNbr, endNbr);
                long freeval = Long.parseLong(freeSizeStr);
                long totalval = Long.parseLong(expectedTotalValue);
                if (0 <= freeval && freeval <= totalval) {
                    System.out.println("Found freeSize value " + freeval + " is fine");
                } else {
                    System.out.println("Found freeSize value " + freeval + " is bad");
                    throw new Exception("Found free size value is bad");
                }
            }
        }
    }


    private static void testProcessInfo() throws Exception {
        Common.logNewTestCase("ProcessInfo");
        DockerTestUtils.dockerRunJava(
                                      commonDockerOpts()
                                      .addClassOptions("jdk.SystemProcess"))
            .shouldHaveExitValue(0)
            .shouldContain("pid = 1");
    }

    private static DockerRunOptions commonDockerOpts() {
        return new DockerRunOptions(imageName, "/jdk/bin/java", "JfrReporter")
            .addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
            .addJavaOpts("-cp", "/test-classes/");
    }


    private static void testEnvironmentVariables() throws Exception {
        Common.logNewTestCase("EnvironmentVariables");

        List<String> cmd = DockerTestUtils.buildJavaCommand(
                                      commonDockerOpts()
                                      .addClassOptions("jdk.InitialEnvironmentVariable"));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        // Container has JAVA_HOME defined via the Dockerfile; make sure
        // it is reported by JFR event.
        // Environment variable set in host system should not be visible inside a container,
        // and should not be reported by JFR.
        pb.environment().put(TEST_ENV_VARIABLE, TEST_ENV_VALUE);

        System.out.println("[COMMAND]\n" + Utils.getCommandLine(pb));
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        System.out.println("[STDERR]\n" + out.getStderr());
        System.out.println("[STDOUT]\n" + out.getStdout());

        out.shouldHaveExitValue(0)
            .shouldContain("key = JAVA_HOME")
            .shouldContain("value = /jdk")
            .shouldNotContain(TEST_ENV_VARIABLE)
            .shouldNotContain(TEST_ENV_VALUE);
    }
}
