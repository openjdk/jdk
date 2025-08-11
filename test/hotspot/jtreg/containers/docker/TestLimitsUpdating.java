/*
 * Copyright (c) 2023, Red Hat, Inc.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 *
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
 * @bug 8308090
 * @key cgroups
 * @summary Test container limits updating as they get updated at runtime without restart
 * @requires container.support
 * @requires !vm.asan
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox LimitUpdateChecker
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run driver TestLimitsUpdating
 */

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class TestLimitsUpdating {
    private static final String TARGET_CONTAINER = "limitsUpdatingHS_" + Runtime.getRuntime().version().major();
    private static final String imageName = Common.imageName("limitsUpdating");

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testLimitUpdates();
        } finally {
            if (!DockerTestUtils.RETAIN_IMAGE_AFTER_TEST) {
                DockerTestUtils.removeDockerImage(imageName);
            }
        }
    }

    private static void testLimitUpdates() throws Exception {
        File sharedtmpdir = new File("test-sharedtmp");
        File flag = new File(sharedtmpdir, "limitsUpdated"); // shared with LimitUpdateChecker
        File started = new File(sharedtmpdir, "started"); // shared with LimitUpdateChecker
        sharedtmpdir.mkdir();
        flag.delete();
        started.delete();
        DockerRunOptions opts = new DockerRunOptions(imageName, "/jdk/bin/java", "LimitUpdateChecker");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/");
        opts.addDockerOpts("--volume", sharedtmpdir.getAbsolutePath() + ":/tmp");
        opts.addDockerOpts("--cpu-period", "100000");
        opts.addDockerOpts("--cpu-quota", "200000");
        opts.addDockerOpts("--memory", "500m");
        opts.addDockerOpts("--memory-swap", "500m");
        opts.addDockerOpts("--name", TARGET_CONTAINER);
        opts.addJavaOpts("-cp", "/test-classes/");
        Common.addWhiteBoxOpts(opts);
        final OutputAnalyzer out[] = new OutputAnalyzer[1];
        Thread t1 = new Thread() {
                public void run() {
                    try {
                        out[0] = DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
        t1.start();

       // Wait for target container (that we later update) to complete its
       // initial starting-up phase. Prints initial container limits.
        while (!started.exists()) {
            System.out.println("Wait for target container to start");
            Thread.sleep(100);
        }

        final List<String> containerCommand = getContainerUpdate(300_000, 100_000, "300m");
        // Run the update command so as to increase resources once the container signaled it has started.
        Thread t2 = new Thread() {
                public void run() {
                    try {
                        DockerTestUtils.execute(containerCommand).shouldHaveExitValue(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            };
        t2.start();
        t2.join();

        // Set the flag for the to-get updated container, indicating the update
        // has completed.
        FileOutputStream fout = new FileOutputStream(flag);
        fout.close();

        t1.join();

        // Do assertions based on the output in target container
        OutputAnalyzer targetOut = out[0];
        targetOut.shouldContain("active_processor_count: 2"); // initial value
        targetOut.shouldContain("active_processor_count: 3"); // updated value
        targetOut.shouldContain("memory_limit_in_bytes: 512000 k"); // initial value
        targetOut.shouldContain("memory_and_swap_limit_in_bytes: 512000 k"); // initial value
        targetOut.shouldContain("memory_limit_in_bytes: 307200 k"); // updated value
        targetOut.shouldContain("memory_and_swap_limit_in_bytes: 307200 k"); // updated value
    }

    private static List<String> getContainerUpdate(int cpuQuota, int cpuPeriod, String memory) {
        List<String> cmd = DockerTestUtils.buildContainerCommand();
        cmd.add("update");
        cmd.add("--cpu-period=" + cpuPeriod);
        cmd.add("--cpu-quota=" + cpuQuota);
        cmd.add("--memory=" + memory);
        cmd.add("--memory-swap=" + memory); // no swap
        cmd.add(TARGET_CONTAINER);
        return cmd;
    }
}
