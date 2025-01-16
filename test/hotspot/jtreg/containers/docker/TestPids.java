/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 SAP SE. All rights reserved.
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
 * @summary Test JVM's awareness of pids controller
 * @requires container.support
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox PrintContainerInfo
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar whitebox.jar jdk.test.whitebox.WhiteBox
 * @run driver TestPids
 */
import java.util.List;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.Asserts;
import jdk.test.lib.Container;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;

public class TestPids {
    private static final String imageName = Common.imageName("pids");
    private static final boolean IS_PODMAN = Container.ENGINE_COMMAND.contains("podman");
    private static final int UNLIMITED_PIDS_PODMAN = 0;
    private static final int UNLIMITED_PIDS_DOCKER = -1;

    static final String warning_kernel_no_pids_support = "WARNING: Your kernel does not support pids limit capabilities";

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        Common.prepareWhiteBox();
        DockerTestUtils.buildJdkContainerImage(imageName);

        try {
            testPids();
        } finally {
            if (!DockerTestUtils.RETAIN_IMAGE_AFTER_TEST) {
                DockerTestUtils.removeDockerImage(imageName);
            }
        }
    }

    private static void testPids() throws Exception {
        System.out.println("Testing pids controller ...");
        testPids("400");
        testPids("800");
        testPids("2000");
        testPids("Unlimited");
    }

    private static DockerRunOptions commonOpts() {
        DockerRunOptions opts = new DockerRunOptions(imageName, "/jdk/bin/java", "PrintContainerInfo");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/");
        opts.addJavaOpts("-Xlog:os+container=trace", "-cp", "/test-classes/");
        Common.addWhiteBoxOpts(opts);
        return opts;
    }

    private static void checkResult(List<String> lines, String lineMarker, String expectedValue) {
        boolean lineMarkerFound = false;

        for (String line : lines) {
            if (line.contains(warning_kernel_no_pids_support)) {
                System.out.println("Docker pids limitation seems not to work, avoiding check");
                return;
            }

            if (line.contains(lineMarker)) {
                lineMarkerFound = true;
                String[] parts = line.split(":");
                System.out.println("DEBUG: line = " + line);
                System.out.println("DEBUG: parts.length = " + parts.length);
                if (expectedValue.equals("any_integer")) {
                    Asserts.assertEquals(parts.length, 2);
                    String ivalue = parts[1].replaceAll("\\s","");
                    try {
                        int ai = Integer.parseInt(ivalue);
                        System.out.println("Found " + lineMarker + " with value: " + ai + ". PASS.");
                    } catch (NumberFormatException ex) {
                        throw new RuntimeException("Could not convert " + ivalue + " to an integer, log line was " + line);
                    }
                    break;
                }

                Asserts.assertEquals(parts.length, 2);
                String actual = parts[1].replaceAll("\\s","");
                if (expectedValue.equals("max")) {
                    // Unlimited pids accept max or -1
                    if (actual.equals("max") || actual.equals("-1")) {
                        System.out.println("Found expected " + actual + " for unlimited pids value.");
                    } else {
                        try {
                            // Unlimited pids leads on some setups not to "max" in the output, but to a high number
                            int ai = Integer.parseInt(actual);
                            if (ai > 20000) {
                                System.out.println("Limit value " + ai + " got accepted as unlimited, log line was " + line);
                            } else {
                                throw new RuntimeException("Limit value " + ai + " is not accepted as unlimited, log line was " + line);
                            }
                        } catch (NumberFormatException ex) {
                            throw new RuntimeException("Could not convert " + actual + " to an integer, log line was " + line);
                        }
                    }
                } else {
                    Asserts.assertEquals(actual, expectedValue);
                }
                break;
            }
        }
        Asserts.assertTrue(lineMarkerFound);
    }

    private static void testPids(String value) throws Exception {
        Common.logNewTestCase("pids controller test, limiting value = " + value);

        DockerRunOptions opts = commonOpts();
        if (value.equals("Unlimited")) {
            int unlimited = IS_PODMAN ? UNLIMITED_PIDS_PODMAN : UNLIMITED_PIDS_DOCKER;
            opts.addDockerOpts("--pids-limit=" + unlimited);
        } else {
            opts.addDockerOpts("--pids-limit="+value);
        }

        List<String> lines = Common.run(opts).asLines();
        if (value.equals("Unlimited")) {
            checkResult(lines, "Maximum number of tasks is: ", "max");
        } else {
            checkResult(lines, "Maximum number of tasks is: ", value);
        }
        // current number of tasks value is hard to predict, so better expect no value
        checkResult(lines, "Current number of tasks is: ", "any_integer");
    }

}
