/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8146115 8292083
 * @key cgroups
 * @summary Test JVM's memory resource awareness when running inside docker container
 * @requires container.support
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.platform
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build AttemptOOM jdk.test.whitebox.WhiteBox PrintContainerInfo CheckOperatingSystemMXBean
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:whitebox.jar -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI TestMemoryAwareness
 */
import java.util.function.Consumer;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.process.OutputAnalyzer;

import static jdk.test.lib.Asserts.assertNotNull;

public class TestMemoryAwareness {
    private static final String imageName = Common.imageName("memory");
    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    private static String getHostMaxMemory() {
        return Long.valueOf(wb.hostPhysicalMemory()).toString();
    }

    private static String getHostSwap() {
        return Long.valueOf(wb.hostPhysicalSwap()).toString();
    }

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testMemoryLimit("100m", "104857600", false);
            testMemoryLimit("500m", "524288000", false);
            testMemoryLimit("1g", "1073741824", false);
            testMemoryLimit("4g", "4294967296", false);
            testMemoryLimit("100m", "104857600", true /* additional cgroup mount */);

            testMemorySoftLimit("500m", "524288000");
            testMemorySoftLimit("1g", "1073741824");
            testMemorySwapLimitSanity();

            testMemorySwapNotSupported("500m", "520m", "512000 k", "532480 k");

            // Add extra 10 Mb to allocator limit, to be sure to cause OOM
            testOOM("256m", 256 + 10);

            testOperatingSystemMXBeanAwareness(
                "100M", Integer.toString(((int) Math.pow(2, 20)) * 100),
                "150M", Integer.toString(((int) Math.pow(2, 20)) * (150 - 100))
            );
            testOperatingSystemMXBeanAwareness(
                "128M", Integer.toString(((int) Math.pow(2, 20)) * 128),
                "256M", Integer.toString(((int) Math.pow(2, 20)) * (256 - 128))
            );
            testOperatingSystemMXBeanAwareness(
                "1G", Integer.toString(((int) Math.pow(2, 20)) * 1024),
                "1500M", Integer.toString(((int) Math.pow(2, 20)) * (1500 - 1024))
            );
            testOperatingSystemMXBeanAwareness(
                "100M", Integer.toString(((int) Math.pow(2, 20)) * 100),
                "200M", Integer.toString(((int) Math.pow(2, 20)) * (200 - 100)),
                true /* additional cgroup fs mounts */
            );
            testOSMXBeanIgnoresMemLimitExceedingPhysicalMemory();
            testOSMXBeanIgnoresSwapLimitExceedingPhysical();
            testMetricsExceedingPhysicalMemory();
            testMetricsSwapExceedingPhysical();
            testContainerMemExceedsPhysical();
        } finally {
            if (!DockerTestUtils.RETAIN_IMAGE_AFTER_TEST) {
                DockerTestUtils.removeDockerImage(imageName);
            }
        }
    }


    private static void testMemoryLimit(String valueToSet, String expectedTraceValue, boolean addCgmounts)
            throws Exception {

        Common.logNewTestCase("memory limit: " + valueToSet);

        DockerRunOptions opts = Common.newOpts(imageName)
            .addDockerOpts("--memory", valueToSet);

        if (addCgmounts) {
            opts = opts.addDockerOpts("--volume", "/sys/fs/cgroup:/cgroups-in:ro");
        }

        Common.run(opts)
            .shouldMatch("Memory Limit is:.*" + expectedTraceValue);
    }

    // JDK-8292083
    // Ensure that Java ignores container memory limit values above the host's physical memory.
    private static void testContainerMemExceedsPhysical()
            throws Exception {
        Common.logNewTestCase("container memory limit exceeds physical memory");
        String hostMaxMem = getHostMaxMemory();
        String badMem = hostMaxMem + "0";
        // set a container memory limit to the bad value
        DockerRunOptions opts = Common.newOpts(imageName)
            .addDockerOpts("--memory", badMem);

        Common.run(opts)
            .shouldMatch("container memory limit (ignored: " + badMem + "|unlimited: -1), using host value " + hostMaxMem);
    }


    private static void testMemorySoftLimit(String valueToSet, String expectedTraceValue)
            throws Exception {
        Common.logNewTestCase("memory soft limit: " + valueToSet);

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo");
        Common.addWhiteBoxOpts(opts);
        opts.addDockerOpts("--memory-reservation=" + valueToSet);

        Common.run(opts)
            .shouldMatch("Memory Soft Limit.*" + expectedTraceValue);
    }

    /*
     * Verifies that PrintContainerInfo prints the memory
     * limit - without swap - iff swap is disabled (e.g. via swapaccount=0). It must
     * not print 'not supported' for that value in that case. It'll always pass
     * on systems with swap accounting enabled.
     */
    private static void testMemorySwapNotSupported(String valueToSet, String swapToSet, String expectedMem, String expectedSwap)
            throws Exception {
        Common.logNewTestCase("memory swap not supported: " + valueToSet);

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo");
        Common.addWhiteBoxOpts(opts);
        opts.addDockerOpts("--memory=" + valueToSet);
        opts.addDockerOpts("--memory-swap=" + swapToSet);

        Common.run(opts)
            .shouldMatch("memory_limit_in_bytes:.*" + expectedMem)
            .shouldNotMatch("memory_and_swap_limit_in_bytes:.*not supported")
            // On systems with swapaccount=0 this returns the memory limit.
            // On systems with swapaccount=1 this returns the set memory+swap value.
            .shouldMatch("memory_and_swap_limit_in_bytes:.*(" + expectedMem + "|" + expectedSwap + ")");
    }

    /*
     * This test verifies that no confusingly large positive numbers get printed on
     * systems with swapaccount=0 kernel option. On some systems -2 were converted
     * to unsigned long and printed that way. Ensure this oddity doesn't occur.
     */
    private static void testMemorySwapLimitSanity() throws Exception {
        String valueToSet = "500m";
        String expectedTraceValue = "524288000";
        Common.logNewTestCase("memory swap sanity: " + valueToSet);

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo");
        Common.addWhiteBoxOpts(opts);
        opts.addDockerOpts("--memory=" + valueToSet);
        opts.addDockerOpts("--memory-swap=" + valueToSet);

        String neg2InUnsignedLong = "18446744073709551614";

        Common.run(opts)
            .shouldMatch("Memory Limit is:.*" + expectedTraceValue)
            // Either for cgroup v1: a_1) same as memory limit, or b_1) -2 on systems with swapaccount=0
            // Either for cgroup v2: a_2) 0, or b_2) -2 on systems with swapaccount=0
            .shouldMatch("(Memory and )?Swap Limit is:.*(" + expectedTraceValue + "|-2|0)")
            .shouldNotMatch("(Memory and )?Swap Limit is:.*" + neg2InUnsignedLong);
    }


    // provoke OOM inside the container, see how VM reacts
    private static void testOOM(String dockerMemLimit, int sizeToAllocInMb) throws Exception {
        Common.logNewTestCase("OOM");

        DockerRunOptions opts = Common.newOpts(imageName, "AttemptOOM")
            .addDockerOpts("--memory", dockerMemLimit, "--memory-swap", dockerMemLimit);
        opts.classParams.add("" + sizeToAllocInMb);

        // make sure we avoid inherited Xmx settings from the jtreg vmoptions
        // set Xmx ourselves instead
        System.out.println("sizeToAllocInMb is:" + sizeToAllocInMb + " sizeToAllocInMb/2 is:" + sizeToAllocInMb/2);
        String javaHeapSize = sizeToAllocInMb/2 + "m";
        opts.addJavaOptsAppended("-Xmx" + javaHeapSize);

        OutputAnalyzer out = DockerTestUtils.dockerRunJava(opts);

        if (out.getExitValue() == 0) {
            throw new RuntimeException("We exited successfully, but we wanted to provoke an OOM inside the container");
        }

        out.shouldContain("Entering AttemptOOM main")
           .shouldNotContain("AttemptOOM allocation successful")
           .shouldContain("java.lang.OutOfMemoryError");
    }

    private static void testOperatingSystemMXBeanAwareness(String memoryAllocation, String expectedMemory,
            String swapAllocation, String expectedSwap) throws Exception {
        testOperatingSystemMXBeanAwareness(memoryAllocation, expectedMemory, swapAllocation, expectedSwap, false);
    }

    private static void testOperatingSystemMXBeanAwareness(String memoryAllocation, String expectedMemory,
            String swapAllocation, String expectedSwap, boolean addCgroupMounts) throws Exception {
        Consumer<OutputAnalyzer> noOp = o -> {};
        testOperatingSystemMXBeanAwareness(memoryAllocation, expectedMemory, swapAllocation, expectedSwap, false, noOp);
    }

    private static void testOperatingSystemMXBeanAwareness(String memoryAllocation, String expectedMemory,
            String swapAllocation, String expectedSwap, boolean addCgroupMounts,
            Consumer<OutputAnalyzer> additionalMatch) throws Exception {

        Common.logNewTestCase("Check OperatingSystemMXBean");

        DockerRunOptions opts = Common.newOpts(imageName, "CheckOperatingSystemMXBean")
            .addDockerOpts(
                "--memory", memoryAllocation,
                "--memory-swap", swapAllocation
            )
            .addJavaOpts("-esa")
            // CheckOperatingSystemMXBean uses Metrics (jdk.internal.platform) for
            // diagnostics
            .addJavaOpts("--add-exports")
            .addJavaOpts("java.base/jdk.internal.platform=ALL-UNNAMED");
        if (addCgroupMounts) {
            // Extra cgroup mount should be ignored by product code
            opts.addDockerOpts("--volume", "/sys/fs/cgroup:/cgroup-in:ro");
        }

        OutputAnalyzer out = DockerTestUtils.dockerRunJava(opts);
        out.shouldHaveExitValue(0)
           .shouldContain("Checking OperatingSystemMXBean")
           .shouldContain("OperatingSystemMXBean.getTotalPhysicalMemorySize: " + expectedMemory)
           .shouldContain("OperatingSystemMXBean.getTotalMemorySize: " + expectedMemory)
           .shouldMatch("OperatingSystemMXBean\\.getFreeMemorySize: [1-9][0-9]+")
           .shouldMatch("OperatingSystemMXBean\\.getFreePhysicalMemorySize: [1-9][0-9]+");

        // in case of warnings like : "Your kernel does not support swap limit capabilities
        // or the cgroup is not mounted. Memory limited without swap."
        // the getTotalSwapSpaceSize either returns the system (or host) values, or 0
        // if a container memory limit is in place and gets detected. A value of 0 is because,
        // Metrics.getMemoryLimit() returns the same value as Metrics.getMemoryAndSwapLimit().
        //
        // getFreeSwapSpaceSize() are a function of what getTotalSwapSpaceSize() returns. Either
        // a number > 0, or 0 if getTotalSwapSpaceSize() == 0.
        try {
            out.shouldContain("OperatingSystemMXBean.getTotalSwapSpaceSize: " + expectedSwap);
        } catch(RuntimeException ex) {
            String hostSwap = getHostSwap();
            out.shouldMatch("OperatingSystemMXBean.getTotalSwapSpaceSize: (0|" + hostSwap + ")");
        }

        try {
            out.shouldMatch("OperatingSystemMXBean\\.getFreeSwapSpaceSize: [1-9][0-9]+");
        } catch(RuntimeException ex) {
            out.shouldMatch("OperatingSystemMXBean\\.getFreeSwapSpaceSize: 0");
        }
        additionalMatch.accept(out);
    }

    // JDK-8292541: Ensure OperatingSystemMXBean ignores container memory limits above the host's physical memory.
    private static void testOSMXBeanIgnoresMemLimitExceedingPhysicalMemory()
            throws Exception {
        String hostMaxMem = getHostMaxMemory();
        String badMem = hostMaxMem + "0";
        testOperatingSystemMXBeanAwareness(badMem, hostMaxMem, badMem, hostMaxMem);
    }

    private static void testOSMXBeanIgnoresSwapLimitExceedingPhysical()
            throws Exception {
        long totalSwap = wb.hostPhysicalSwap() + wb.hostPhysicalMemory();
        String expectedSwap = Long.valueOf(totalSwap).toString();
        String hostMaxMem = getHostMaxMemory();
        String badMem = hostMaxMem + "0";
        final String badSwap = expectedSwap + "0";
        testOperatingSystemMXBeanAwareness(badMem, hostMaxMem, badSwap, expectedSwap, false, o -> {
            o.shouldNotContain("Metrics.getMemoryAndSwapLimit() == " + badSwap);
        });
    }

    private static void testMetricsSwapExceedingPhysical()
            throws Exception {
        Common.logNewTestCase("Metrics ignore container swap memory limit exceeding physical");
        long totalSwap = wb.hostPhysicalSwap() + wb.hostPhysicalMemory();
        String expectedSwap = Long.valueOf(totalSwap).toString();
        final String badSwap = expectedSwap + "0";
        String badMem = getHostMaxMemory() + "0";
        DockerRunOptions opts = Common.newOpts(imageName)
            .addJavaOpts("-XshowSettings:system")
            .addDockerOpts("--memory", badMem)
            .addDockerOpts("--memory-swap", badSwap);

        OutputAnalyzer out = DockerTestUtils.dockerRunJava(opts);
        out.shouldContain("Memory Limit: Unlimited");
        out.shouldContain("Memory & Swap Limit: Unlimited");
    }

    // JDK-8292541: Ensure Metrics ignores container memory limits above the host's physical memory.
    private static void testMetricsExceedingPhysicalMemory()
            throws Exception {
        Common.logNewTestCase("Metrics ignore container memory limit exceeding physical memory");
        String badMem = getHostMaxMemory() + "0";
        DockerRunOptions opts = Common.newOpts(imageName)
            .addJavaOpts("-XshowSettings:system")
            .addDockerOpts("--memory", badMem);

        DockerTestUtils.dockerRunJava(opts).shouldMatch("Memory Limit: Unlimited");
    }
}
