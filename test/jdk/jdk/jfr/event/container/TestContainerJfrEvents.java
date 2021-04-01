/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.jfr
 * @library /test/lib
 * @build ContainerJfrEventsRunner
 * @run main/timeout=360 TestContainerJfrEvents
 */

import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.Utils;

import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;

public class TestContainerJfrEvents {
    private static final String imageName = Common.imageName("jfrContainerEvents");

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
          return;
        }

        Path output = Utils.createTempDirectory("jfrevents-");

        try {
            DockerTestUtils.buildJdkDockerImage(imageName, "Dockerfile-BasicTest", "jdk-docker");
            
            Common.logNewTestCase("JFR container events");
            DockerRunOptions opts =
                    new DockerRunOptions(imageName, "/jdk/bin/java", "ContainerJfrEvetnsRunner");
            opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/");
            opts.addDockerOpts("--volume", output + ":/test-output/");
            opts.addJavaOpts("-cp", "/test-classes/");
            // opts.addClassOptions("cpumems", value);
            DockerTestUtils.dockerRunJava(opts).shouldHaveExitValue(0);
        } finally {
            Files.deleteIfExists(output);
        }
    }

}