/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat, Inc.
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
 * @summary Test container info for cgroup v2
 * @key cgroups
 * @requires container.support
 * @requires !vm.asan
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build CheckContainerized jdk.test.whitebox.WhiteBox PrintContainerInfo
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run driver TestContainerInfo
 */
import jtreg.SkippedException;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class TestContainerInfo {
    private static final String imageName = Common.imageName("container-info");

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testPrintContainerInfoWithoutSwap();
        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }

    private static void testPrintContainerInfoWithoutSwap() throws Exception {
        Common.logNewTestCase("Test print_container_info() - without swap");

        DockerRunOptions opts = Common.newOpts(imageName, "PrintContainerInfo")
                      .addDockerOpts("--memory=500m")
                      .addDockerOpts("--memory-swap=500m"); // no swap
        Common.addWhiteBoxOpts(opts);

        OutputAnalyzer out = Common.run(opts);
        checkContainerInfo(out);
    }

    private static void shouldMatchWithValue(OutputAnalyzer output, String match, String value) {
        output.shouldContain(match);
        String str = output.getOutput();
        for (String s : str.split(System.lineSeparator())) {
            if (s.contains(match)) {
                if (!s.contains(value)) {
                    throw new RuntimeException("memory_swap_current_in_bytes NOT " + value + "! Line was : " + s);
                }
            }
        }
    }

    private static void checkContainerInfo(OutputAnalyzer out) throws Exception {
        String str = out.getOutput();
        if (str.contains("cgroupv2")) {
            shouldMatchWithValue(out, "memory_swap_max_limit_in_bytes", "0");
            shouldMatchWithValue(out, "memory_swap_current_in_bytes", "0");
        } else {
            throw new SkippedException("This test is cgroups v2 specific, skipped on cgroups v1");
        }
    }
}
