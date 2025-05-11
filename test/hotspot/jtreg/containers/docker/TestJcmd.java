/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test JCMD across container boundary. The JCMD runs on a host system,
 *          while sending commands to a JVM that runs inside a container.
 * @requires container.support
 * @requires vm.flagless
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @library /test/lib
 * @build EventGeneratorLoop
 * @run driver TestJcmd
 */
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import jdk.test.lib.Container;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerfileConfig;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;


public class TestJcmd {
    private static final String IMAGE_NAME = Common.imageName("jcmd");
    private static final int TIME_TO_RUN_CONTAINER_PROCESS = (int) (10 * Utils.TIMEOUT_FACTOR); // seconds
    private static final String CONTAINER_NAME = "test-container";
    private static final boolean IS_PODMAN = Container.ENGINE_COMMAND.contains("podman");
    private static final String ROOT_UID = "0";


    public static void main(String[] args) throws Exception {
        DockerTestUtils.canTestDocker();

        // podman versions below 3.3.1 hava a bug where cross-container testing with correct
        // permissions fails. See JDK-8273216
        if (IS_PODMAN && PodmanVersion.VERSION_3_3_1.compareTo(getPodmanVersion()) > 0) {
            throw new SkippedException("Podman version too old for this test. Expected >= 3.3.1");
        }

        // Need to create a custom dockerfile where user name and id, as well as group name and id
        // of the JVM running in container must match the ones from the inspecting JCMD process.
        String content = generateCustomDockerfile();
        DockerTestUtils.buildJdkContainerImage(IMAGE_NAME, content);

        try {
            Process p = startObservedContainer();
            long pid = testJcmdGetPid("EventGeneratorLoop");

            assertIsAlive(p);
            testJcmdHelp(pid);

            assertIsAlive(p);
            testVmInfo(pid);

            p.waitFor();
        } finally {
            DockerTestUtils.removeDockerImage(IMAGE_NAME);
        }
    }


    // Run "jcmd -l", find the target process.
    private static long testJcmdGetPid(String className) throws Exception {
        System.out.println("TestCase: testJcmdGetPid()");
        ProcessBuilder pb = new ProcessBuilder(JDKToolFinder.getJDKTool("jcmd"), "-l");
        OutputAnalyzer out = new OutputAnalyzer(pb.start())
            .shouldHaveExitValue(0);

        System.out.println("------------------ jcmd -l output: ");
        System.out.println(out.getOutput());
        System.out.println("-----------------------------------");

        List<String> l = out.asLines()
            .stream()
            .filter(s -> s.contains(className))
            .collect(Collectors.toList());
        if (l.isEmpty()) {
            throw new RuntimeException("Could not find specified process");
        }

        String pid = l.get(0).split("\\s+", 3)[0];
        return Long.parseLong(pid);
    }


    private static void testJcmdHelp(long pid) throws Exception {
        System.out.println("TestCase: testJcmdHelp()");
        ProcessBuilder pb = new ProcessBuilder(JDKToolFinder.getJDKTool("jcmd"), "" + pid, "help");
        OutputAnalyzer out = new OutputAnalyzer(pb.start())
            .shouldHaveExitValue(0)
            .shouldContain("JFR.start")
            .shouldContain("VM.version");
    }

    private static void testVmInfo(long pid) throws Exception {
        System.out.println("TestCase: testVmInfo()");
        ProcessBuilder pb = new ProcessBuilder(JDKToolFinder.getJDKTool("jcmd"), "" + pid, "VM.info");
        OutputAnalyzer out = new OutputAnalyzer(pb.start())
            .shouldHaveExitValue(0)
            .shouldContain("vm_info")
            .shouldContain("VM Arguments");
    }

    // Need to make sure that user name+id and group name+id are created for the image, and
    // match the host system. This is necessary to allow proper permission/access for JCMD.
    // For podman --userns=keep-id is sufficient.
    private static String generateCustomDockerfile() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("FROM %s:%s\n", DockerfileConfig.getBaseImageName(),
                                DockerfileConfig.getBaseImageVersion()));
        sb.append("COPY /jdk /jdk\n");
        sb.append("ENV JAVA_HOME=/jdk\n");

        if (!IS_PODMAN) { // only needed for docker
            String uid = getId("-u");
            String gid = getId("-g");
            String userName = getId("-un");
            String groupName = getId("-gn");
            // Only needed when run as regular user. UID == 0 should already exist
            if (!ROOT_UID.equals(uid)) {
                sb.append(String.format("RUN groupadd --gid %s %s \n", gid, groupName));
                sb.append(String.format("RUN useradd  --uid %s --gid %s %s \n", uid, gid, userName));
                sb.append(String.format("USER %s \n", userName));
            }
        }

        sb.append("CMD [\"/bin/bash\"]\n");

        return sb.toString();
    }


    private static Process startObservedContainer() throws Exception {
        DockerRunOptions opts = new DockerRunOptions(IMAGE_NAME, "/jdk/bin/java", "EventGeneratorLoop");
        opts.addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/:z")
            .addJavaOpts("-cp", "/test-classes/")
            .addDockerOpts("--cap-add=SYS_PTRACE")
            .addDockerOpts("--name", CONTAINER_NAME)
            .addClassOptions("" + TIME_TO_RUN_CONTAINER_PROCESS);

        if (IS_PODMAN && !ROOT_UID.equals(getId("-u"))) {
            // map the current userid to the one in the target namespace
            opts.addDockerOpts("--userns=keep-id");
        }

        // avoid large Xmx
        opts.appendTestJavaOptions = false;

        List<String> cmd = DockerTestUtils.buildJavaCommand(opts);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        return ProcessTools.startProcess("main-container-process",
                                      pb,
                                      line -> line.contains(EventGeneratorLoop.MAIN_METHOD_STARTED),
                                      5, TimeUnit.SECONDS);
    }


    private static void assertIsAlive(Process p) throws Exception {
        if (!p.isAlive()) {
            throw new RuntimeException("Main container process stopped unexpectedly, exit value: "
                                       + p.exitValue());
        }
    }


    // -u for userId, -g for groupId
    private static String getId(String param) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("id", param);
        OutputAnalyzer out = new OutputAnalyzer(pb.start())
            .shouldHaveExitValue(0);
        String result = out.asLines().get(0);
        System.out.println("getId() " + param + " returning: " + result);
        return result;
    }

    // pre: IS_PODMAN == true
    private static String getPodmanVersionStr() {
        if (!IS_PODMAN) {
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(Container.ENGINE_COMMAND, "--version");
            OutputAnalyzer out = new OutputAnalyzer(pb.start())
                .shouldHaveExitValue(0);
            String result = out.asLines().get(0);
            System.out.println(Container.ENGINE_COMMAND + " --version returning: " + result);
            return result;
        } catch (Exception e) {
            System.out.println(Container.ENGINE_COMMAND + " --version command failed. Returning null");
            return null;
        }
    }

    private static PodmanVersion getPodmanVersion() {
        return PodmanVersion.fromVersionString(getPodmanVersionStr());
    }

    private static class PodmanVersion implements Comparable<PodmanVersion> {
        private static final PodmanVersion DEFAULT = new PodmanVersion(0, 0, 0);
        private static final PodmanVersion VERSION_3_3_1 = new PodmanVersion(3, 3, 1);
        private final int major;
        private final int minor;
        private final int micro;

        private PodmanVersion(int major, int minor, int micro) {
            this.major = major;
            this.minor = minor;
            this.micro = micro;
        }

        @Override
        public int compareTo(PodmanVersion other) {
            if (this.major > other.major) {
                return 1;
            } else if (this.major < other.major) {
                return -1;
            } else { // equal major
                if (this.minor > other.minor) {
                    return 1;
                } else if (this.minor < other.minor) {
                    return -1;
                } else { // equal majors and minors
                    if (this.micro > other.micro) {
                        return 1;
                    } else if (this.micro < other.micro) {
                        return -1;
                    } else {
                        // equal majors, minors, micro
                        return 0;
                    }
                }
            }
        }

        private static PodmanVersion fromVersionString(String version) {
            // Examples: 'podman version 3.2.1', 'podman version 4.9.4-rhel'
            final Pattern pattern = Pattern.compile("podman version (\\d+)\\.(\\d+)\\.(\\d+).*");
            final Matcher matcher = pattern.matcher(version);

            if (matcher.matches()) {
                return new PodmanVersion(
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2)),
                        Integer.parseInt(matcher.group(3)));
            } else {
                System.out.println("Failed to parse podman version: " + version);
                return DEFAULT;
            }
        }
    }
}
