/*
 * Copyright (c) 2022, Red Hat, Inc.
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8293540
 * @summary Verify that -XshowSettings:system works
 * @key cgroups
 * @requires container.support
 * @requires !vm.asan
 * @library /test/lib
 * @run main/timeout=360 TestDockerBasic
 */

import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;

public class TestDockerBasic {
    private static final String imageName = Common.imageName("javaDockerBasic");

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testXshowSettingsSystem(true);
            testXshowSettingsSystem(false);
        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }

    private static void testXshowSettingsSystem(boolean addCgroupMounts) throws Exception {
        String testMsg = (addCgroupMounts ? " with " : " without ") + " additional cgroup FS mounts in /cgroup-in";
        Common.logNewTestCase("Test TestDockerBasic " + testMsg);
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "-version");
        opts.addJavaOpts("-esa");
        opts.addJavaOpts("-XshowSettings:system");
        opts.addDockerOpts("--memory", "300m");
        if (addCgroupMounts) {
            // Extra cgroup mount should be ignored by product code
            opts.addDockerOpts("--volume", "/sys/fs/cgroup:/cgroup-in:ro");
        }
        DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0)
            .shouldNotContain("AssertionError")
            .shouldContain("Memory Limit: 300.00M");
    }
}
