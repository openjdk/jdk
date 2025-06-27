/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Methods and definitions related to container runtime version to test container in this directory
 */

package jdk.test.lib.containers.docker;

import jdk.test.lib.Container;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

public class ContainerRuntimeVersionTestUtils implements Comparable<ContainerRuntimeVersionTestUtils> {
    private final int major;
    private final int minor;
    private final int micro;
    private static final boolean IS_DOCKER = Container.ENGINE_COMMAND.contains("docker");
    private static final boolean IS_PODMAN = Container.ENGINE_COMMAND.contains("podman");
    public static final ContainerRuntimeVersionTestUtils DOCKER_MINIMAL_SUPPORTED_VERSION_CGROUPNS = new ContainerRuntimeVersionTestUtils(20, 10, 0);
    public static final ContainerRuntimeVersionTestUtils PODMAN_MINIMAL_SUPPORTED_VERSION_CGROUPNS = new ContainerRuntimeVersionTestUtils(1, 5, 0);

    private ContainerRuntimeVersionTestUtils(int major, int minor, int micro) {
        this.major = major;
        this.minor = minor;
        this.micro = micro;
    }

    public static void checkContainerVersionSupported() {
        if (IS_DOCKER && ContainerRuntimeVersionTestUtils.DOCKER_MINIMAL_SUPPORTED_VERSION_CGROUPNS.compareTo(ContainerRuntimeVersionTestUtils.getContainerRuntimeVersion()) > 0) {
            throw new SkippedException("Docker version too old for this test. Expected >= 20.10.0");
        }
        if (IS_PODMAN && ContainerRuntimeVersionTestUtils.PODMAN_MINIMAL_SUPPORTED_VERSION_CGROUPNS.compareTo(ContainerRuntimeVersionTestUtils.getContainerRuntimeVersion()) > 0) {
            throw new SkippedException("Podman version too old for this test. Expected >= 1.5.0");
        }
    }

    @Override
    public int compareTo(ContainerRuntimeVersionTestUtils other) {
        if (this.major > other.major) {
            return 1;
        } else if (this.major < other.major) {
            return -1;
        } else if (this.minor > other.minor) {
            return 1;
        } else if (this.minor < other.minor) {
            return -1;
        } else if (this.micro > other.micro) {
            return 1;
        } else if (this.micro < other.micro) {
            return -1;
        } else {
            // equal majors, minors, micro
            return 0;
        }
    }

    public static ContainerRuntimeVersionTestUtils fromVersionString(String version) {
        try {
            // Example 'docker version 20.10.0 or podman version 4.9.4-rhel'
            String versNums = version.split("\\s+", 3)[2];
            // On some docker implementations e.g. RHEL8 ppc64le we have the following version output:
            //    Docker version v25.0.3, build 4debf41
            // Trim potentially leading 'v' and trailing ','
            if (versNums.startsWith("v")) {
                versNums = versNums.substring(1);
            }
            int cidx = versNums.indexOf(',');
            versNums = (cidx != -1) ? versNums.substring(0, cidx) : versNums;

            String[] numbers = versNums.split("-")[0].split("\\.", 3);
            return new ContainerRuntimeVersionTestUtils(Integer.parseInt(numbers[0]),
                    Integer.parseInt(numbers[1]),
                    Integer.parseInt(numbers[2]));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse container runtime version: " + version, e);
        }
    }

    public static String getContainerRuntimeVersionStr() {
        try {
            ProcessBuilder pb = new ProcessBuilder(Container.ENGINE_COMMAND, "--version");
            OutputAnalyzer out = new OutputAnalyzer(pb.start())
                    .shouldHaveExitValue(0);
            String result = out.asLines().get(0);
            System.out.println(Container.ENGINE_COMMAND + " --version returning: " + result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(Container.ENGINE_COMMAND + " --version command failed.");
        }
    }

    public static ContainerRuntimeVersionTestUtils getContainerRuntimeVersion() {
        return ContainerRuntimeVersionTestUtils.fromVersionString(getContainerRuntimeVersionStr());
    }
}
