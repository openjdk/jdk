/*
 * Copyright (c) 2025 Red Hat, Inc.
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
 * @summary Verify MaxRAMPercentage setting in a containerized system
 * @bug 8350596
 * @key cgroups
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /testlibrary /test/lib
 * @run driver MaxRAMPercentage
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class MaxRAMPercentage {
    private static final String imageName = Common.imageName("ram-percentage");

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testMaxRAMPercentage();
        } finally {
            DockerTestUtils.removeDockerImage(imageName);
        }
    }

    private static String getFlagValue(String flag, String where) {
        Matcher m = Pattern.compile(flag + "\\s+:?= (\\d+\\.\\d+)").matcher(where);
        if (!m.find()) {
            throw new RuntimeException("Could not find value for flag " + flag + " in output string");
        }
        return m.group(1);
    }

    private static void testMaxRAMPercentage() throws Exception {
        Common.logNewTestCase("Test MaxRAMPercentage");
        DockerRunOptions opts =
                new DockerRunOptions(imageName, "/jdk/bin/java", "-version");
        opts.addJavaOpts("-XX:+PrintFlagsFinal");

        // We are interested in the default option when run in a container, so
        // don't append test java options
        opts.appendTestJavaOptions = false;
        OutputAnalyzer out = DockerTestUtils.dockerRunJava(opts);
        out.shouldHaveExitValue(0);

        String maxRamPercentage = getFlagValue("MaxRAMPercentage", out.getStdout());
        // expect a default of 75%
        if (!maxRamPercentage.startsWith("75")) {
            throw new RuntimeException("Test failed! Expected MaxRAMPercentage to be 75% but got: " + maxRamPercentage);
        }
        System.out.println("PASS. Got expected MaxRAMPercentage=" + maxRamPercentage);
    }

}
