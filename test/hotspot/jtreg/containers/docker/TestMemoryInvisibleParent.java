/*
 * Copyright (C) 2025, IBM Corporation. All rights reserved.
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
import jdk.test.lib.process.OutputAnalyzer;
import jdk.internal.platform.Metrics;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;

import jdk.test.lib.Platform;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8370966
 * @requires os.family == "linux"
 * @requires !vm.asan
 * @modules java.base/jdk.internal.platform
 * @library /test/lib
 * @run main TestMemoryInvisibleParent
 */
public class TestMemoryInvisibleParent {
    private static final String UNLIMITED = "-1";
    private static final String imageName = Common.imageName("invisible-parent");

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
        if (!Platform.isRoot()) {
            throw new SkippedException("Test should be run as root");
        }
        DockerTestUtils.buildJdkContainerImage(imageName);

        if ("cgroupv1".equals(metrics.getProvider())) {
            try {
                testMemoryLimitHiddenParent("104857600", "104857600");
                testMemoryLimitHiddenParent("209715200", "209715200");
            } finally {
                DockerTestUtils.removeDockerImage(imageName);
            }
        } else {
            throw new SkippedException("cgroup v1 - only test! This is " + metrics.getProvider());
        }
    }

    private static void testMemoryLimitHiddenParent(String valueToSet, String expectedValue)
            throws Exception {

        Common.logNewTestCase("Cgroup V1 hidden parent memory limit: " + valueToSet);

        try {
            String cgroupParent = setParentWithLimit(valueToSet);
            DockerRunOptions opts = new DockerRunOptions(imageName, "/jdk/bin/java", "-version", "-Xlog:os+container=trace");
            opts.appendTestJavaOptions = false;
            if (DockerTestUtils.isPodman()) {
                // Podman needs to run this test with engine option --cgroup-manager=cgroupfs
                opts.addEngineOpts("--cgroup-manager", "cgroupfs");
            }
            opts.addDockerOpts("--cgroup-parent=/" + cgroupParent);
            Common.run(opts)
                  .shouldContain("Hierarchical Memory Limit is: " + expectedValue);
        } finally {
            // Reset the parent memory limit to unlimited (-1)
            setParentWithLimit(UNLIMITED);
        }
    }

    private static String setParentWithLimit(String memLimit) throws Exception {
        String cgroupParent = "hidden-parent-" + TestMemoryInvisibleParent.class.getSimpleName() + Runtime.version().feature();
        Path sysFsMemory = Path.of("/", "sys", "fs", "cgroup", "memory");
        Path cgroupParentPath = sysFsMemory.resolve(cgroupParent);
        ProcessBuilder pb = new ProcessBuilder("mkdir", "-p", cgroupParentPath.toString());
        OutputAnalyzer out = new OutputAnalyzer(pb.start())
            .shouldHaveExitValue(0);
        Path memoryLimitsFile = cgroupParentPath.resolve("memory.limit_in_bytes");
        Files.writeString(memoryLimitsFile, memLimit);
        System.out.println("Cgroup parent is: /" + cgroupParentPath.getFileName() +
                           " at " + sysFsMemory.toString());
        return cgroupParent;
    }

}
