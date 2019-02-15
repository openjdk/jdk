/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test JVM's CPU resource awareness when running inside docker container
 * @requires docker.support
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @run driver TestCPUAwareness
 */
import java.util.List;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.containers.cgroup.CPUSetsReader;

public class TestCPUAwareness {
    private static final String imageName = Common.imageName("cpu");
    private static final int availableCPUs = Runtime.getRuntime().availableProcessors();

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        System.out.println("Test Environment: detected availableCPUs = " + availableCPUs);
        DockerTestUtils.buildJdkDockerImage(imageName, "Dockerfile-BasicTest", "jdk-docker");

        try {
            // cpuset, period, shares, expected Active Processor Count
            testComboWithCpuSets();

            // cpu shares - it should be safe to use CPU shares exceeding available CPUs
            testCpuShares(256, 1);
            testCpuShares(2048, 2);
            testCpuShares(4096, 4);

            // leave one CPU for system and tools, otherwise this test may be unstable
            int maxNrOfAvailableCpus =  availableCPUs - 1;
            for (int i=1; i < maxNrOfAvailableCpus; i = i * 2) {
                testCpus(i, i);
            }

            // If ActiveProcessorCount is set, the VM should use it, regardless of other
            // container settings, host settings or available CPUs on the host.
            testActiveProcessorCount(1, 1);
            testActiveProcessorCount(2, 2);

            // cpu quota and period
            testCpuQuotaAndPeriod(50*1000, 100*1000);
            testCpuQuotaAndPeriod(100*1000, 100*1000);
            testCpuQuotaAndPeriod(150*1000, 100*1000);
            testCpuQuotaAndPeriod(400*1000, 100*1000);

        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }


    private static void testComboWithCpuSets() throws Exception {
        String cpuSetStr = CPUSetsReader.readFromProcStatus("Cpus_allowed_list");
        System.out.println("cpuSetStr = " + cpuSetStr);

        if (cpuSetStr == null) {
            System.out.printf("The cpuset test cases are skipped");
        } else {
            List<Integer> cpuSet = CPUSetsReader.parseCpuSet(cpuSetStr);

            // Test subset of cpuset with one element
            if (cpuSet.size() >= 1) {
                String testCpuSet = CPUSetsReader.listToString(cpuSet, 1);
                testAPCCombo(testCpuSet, 200*1000, 100*1000,   4*1024, true, 1);
            }

            // Test subset of cpuset with two elements
            if (cpuSet.size() >= 2) {
                String testCpuSet = CPUSetsReader.listToString(cpuSet, 2);
                testAPCCombo(testCpuSet, 200*1000, 100*1000, 4*1024, true, 2);
                testAPCCombo(testCpuSet, 200*1000, 100*1000, 1023,   true, 2);
                testAPCCombo(testCpuSet, 200*1000, 100*1000, 1023,   false,  1);
            }

            // Test subset of cpuset with three elements
            if (cpuSet.size() >= 3) {
                String testCpuSet = CPUSetsReader.listToString(cpuSet, 3);
                testAPCCombo(testCpuSet, 100*1000, 100*1000, 2*1024, true, 1);
                testAPCCombo(testCpuSet, 200*1000, 100*1000, 1023,   true, 2);
                testAPCCombo(testCpuSet, 200*1000, 100*1000, 1023,   false,  1);
            }
        }
    }


    private static void testActiveProcessorCount(int valueToSet, int expectedValue) throws Exception {
        Common.logNewTestCase("Test ActiveProcessorCount: valueToSet = " + valueToSet);

        DockerRunOptions opts = Common.newOpts(imageName)
            .addJavaOpts("-XX:ActiveProcessorCount=" + valueToSet, "-Xlog:os=trace");
        Common.run(opts)
            .shouldMatch("active processor count set by user.*" + expectedValue);
    }


    private static void testCpus(int valueToSet, int expectedTraceValue) throws Exception {
        Common.logNewTestCase("test cpus: " + valueToSet);
        DockerRunOptions opts = Common.newOpts(imageName)
            .addDockerOpts("--cpu-period=" + 10000)
            .addDockerOpts("--cpu-quota=" + valueToSet * 10000);
        Common.run(opts)
            .shouldMatch("active_processor_count.*" + expectedTraceValue);
    }


    // Expected active processor count can not exceed available CPU count
    private static int adjustExpectedAPCForAvailableCPUs(int expectedAPC) {
        if (expectedAPC > availableCPUs) {
            expectedAPC = availableCPUs;
            System.out.println("Adjusted expectedAPC = " + expectedAPC);
        }
        return expectedAPC;
    }


    private static void testCpuQuotaAndPeriod(int quota, int period)
        throws Exception {
        Common.logNewTestCase("test cpu quota and period: ");
        System.out.println("quota = " + quota);
        System.out.println("period = " + period);

        int expectedAPC = (int) Math.ceil((float) quota / (float) period);
        System.out.println("expectedAPC = " + expectedAPC);
        expectedAPC = adjustExpectedAPCForAvailableCPUs(expectedAPC);

        DockerRunOptions opts = Common.newOpts(imageName)
            .addDockerOpts("--cpu-period=" + period)
            .addDockerOpts("--cpu-quota=" + quota);

        Common.run(opts)
            .shouldMatch("CPU Period is.*" + period)
            .shouldMatch("CPU Quota is.*" + quota)
            .shouldMatch("active_processor_count.*" + expectedAPC);
    }


    // Test correctess of automatically selected active processor cound
    private static void testAPCCombo(String cpuset, int quota, int period, int shares,
                                     boolean usePreferContainerQuotaForCPUCount,
                                     int expectedAPC) throws Exception {
        Common.logNewTestCase("test APC Combo");
        System.out.println("cpuset = " + cpuset);
        System.out.println("quota = " + quota);
        System.out.println("period = " + period);
        System.out.println("shares = " + shares);
        System.out.println("usePreferContainerQuotaForCPUCount = " + usePreferContainerQuotaForCPUCount);
        System.out.println("expectedAPC = " + expectedAPC);

        expectedAPC = adjustExpectedAPCForAvailableCPUs(expectedAPC);

        DockerRunOptions opts = Common.newOpts(imageName)
            .addDockerOpts("--cpuset-cpus", "" + cpuset)
            .addDockerOpts("--cpu-period=" + period)
            .addDockerOpts("--cpu-quota=" + quota)
            .addDockerOpts("--cpu-shares=" + shares);

        if (!usePreferContainerQuotaForCPUCount) opts.addJavaOpts("-XX:-PreferContainerQuotaForCPUCount");

        Common.run(opts)
            .shouldMatch("active_processor_count.*" + expectedAPC);
    }


    private static void testCpuShares(int shares, int expectedAPC) throws Exception {
        Common.logNewTestCase("test cpu shares, shares = " + shares);
        System.out.println("expectedAPC = " + expectedAPC);

        expectedAPC = adjustExpectedAPCForAvailableCPUs(expectedAPC);

        DockerRunOptions opts = Common.newOpts(imageName)
            .addDockerOpts("--cpu-shares=" + shares);
        Common.run(opts)
            .shouldMatch("CPU Shares is.*" + shares)
            .shouldMatch("active_processor_count.*" + expectedAPC);
    }
}
