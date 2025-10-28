/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test JCMD with side car pattern.
 *          Sidecar is a common pattern used in the cloud environments for monitoring
 *          and other uses. In side car pattern the main application/service container
 *          is paired with a sidecar container by sharing certain aspects of container
 *          namespace such as PID namespace, specific sub-directories, IPC and more.
 * @requires container.support
 * @requires vm.flagless
 * @requires !vm.asan
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @library /test/lib
 * @build EventGeneratorLoop
 * @run driver TestJcmdWithSideCar
 */
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import jdk.test.lib.Container;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;


public class TestJcmdWithSideCar {
    private static final String IMAGE_NAME = Common.imageName("jfr-jcmd");
    private static final int TIME_TO_RUN_MAIN_PROCESS = (int) (30 * Utils.TIMEOUT_FACTOR); // seconds
    private static final long TIME_TO_WAIT_FOR_MAIN_METHOD_START = 50 * 1000; // milliseconds

    private static final String UID = "uid";
    private static final String GID = "gid";

    private static final Pattern ID_PATTERN = Pattern.compile("uid=(?<" + UID + ">\\d+)\\([^\\)]+\\)\\s+gid=(?<" + GID + ">\\d+).*");

    private static final Optional<String> USER = ProcessHandle.current().info().user().map(
            user -> {
                try (var br = new BufferedReader(new InputStreamReader(new ProcessBuilder("id", user).start().getInputStream()))) {
                    for (final var line : br.lines().toList()) {
                        final var m = ID_PATTERN.matcher(line);

                        if (m.matches()) {
                            return "--user=" + m.group(UID) + ":" + m.group(GID);
                        }
                    }
                } catch (IOException e) {
                    // do nothing...
                }

                return null;
            }
    );

    private static final String NET_BIND_SERVICE = "--cap-add=NET_BIND_SERVICE";

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        DockerTestUtils.buildJdkContainerImage(IMAGE_NAME);

        try {
            for (final boolean elevated : USER.isPresent() ? new Boolean[] { false, true } : new Boolean[] { false }) {
                // Start the loop process in the "main" container, then run test cases
                // using a sidecar container.
                MainContainer mainContainer = new MainContainer();
                mainContainer.start(elevated);
                mainContainer.waitForMainMethodStart(TIME_TO_WAIT_FOR_MAIN_METHOD_START);

                for (AttachStrategy attachStrategy : EnumSet.allOf(AttachStrategy.class)) {
                    if (attachStrategy == AttachStrategy.ACCESS_TMP_VIA_PROC_ROOT &&
                        elevated && !Platform.isRoot()) {
                        // Elevated attach via proc/root not yet supported.
                        continue;
                    }
                    long mainProcPid = testCase01(mainContainer, attachStrategy, elevated);

                    // Excluding the test case below until JDK-8228850 is fixed
                    // JDK-8228850: jhsdb jinfo fails with ClassCastException:
                    // s.j.h.oops.TypeArray cannot be cast to s.j.h.oops.Instance
                    // mainContainer.assertIsAlive();
                    // testCase02(mainContainer, mainProcPid, attachStrategy, elevated);

                    mainContainer.assertIsAlive();
                    testCase03(mainContainer, mainProcPid, attachStrategy, elevated);
                }

                mainContainer.stop();
            }
        } finally {
            DockerTestUtils.removeDockerImage(IMAGE_NAME);
        }
    }


    // Run "jcmd -l" in a sidecar container, find a target process.
    private static long testCase01(MainContainer mainContainer, AttachStrategy attachStrategy, boolean elevated) throws Exception {
        OutputAnalyzer out = runSideCar(mainContainer, attachStrategy, elevated, "/jdk/bin/jcmd", "-l")
            .shouldHaveExitValue(0)
            .shouldContain("sun.tools.jcmd.JCmd");
        long pid = findProcess(out, "EventGeneratorLoop");
        if (pid == -1) {
            throw new RuntimeException(attachStrategy + ": Could not find specified process");
        }

        return pid;
    }

    // run jhsdb jinfo <PID> (jhsdb uses PTRACE)
    private static void testCase02(MainContainer mainContainer, long pid, AttachStrategy attachStrategy, boolean elevated) throws Exception {
        runSideCar(mainContainer, attachStrategy, elevated, "/jdk/bin/jhsdb", "jinfo", "--pid", "" + pid)
            .shouldHaveExitValue(0)
            .shouldContain("Java System Properties")
            .shouldContain("VM Flags");
    }

    // test jcmd with some commands (help, start JFR recording)
    // JCMD will use signal mechanism and Unix Socket
    private static void testCase03(MainContainer mainContainer, long pid, AttachStrategy attachStrategy, boolean elevated) throws Exception {
        runSideCar(mainContainer, attachStrategy, elevated, "/jdk/bin/jcmd", "" + pid, "help")
            .shouldHaveExitValue(0)
            .shouldContain("VM.version");
        runSideCar(mainContainer, attachStrategy, elevated, "/jdk/bin/jcmd", "" + pid, "JFR.start")
            .shouldHaveExitValue(0)
            .shouldContain("Started recording");
    }


    // JCMD relies on the attach mechanism (com.sun.tools.attach),
    // which in turn relies on JVMSTAT mechanism, which puts its mapped
    // buffers in /tmp directory (hsperfdata_<user>). Thus, in the sidecar
    // we have two options:
    // 1. mount /tmp from the main container using --volumes-from.
    // 2. access /tmp from the main container via /proc/<pid>/root/tmp.
    private static OutputAnalyzer runSideCar(MainContainer mainContainer, AttachStrategy attachStrategy, boolean elevated,  String whatToRun, String... args) throws Exception {
        System.out.println("Attach strategy " + attachStrategy);

        List<String> initialCommands = List.of(
            Container.ENGINE_COMMAND, "run",
            "--tty=true", "--rm",
            "--cap-add=SYS_PTRACE", "--sig-proxy=true",
            "--pid=container:" + mainContainer.name()
        );

        List<String> attachStrategyCommands = switch (attachStrategy) {
            case TMP_MOUNTED_INTO_SIDECAR -> List.of("--volumes-from", mainContainer.name());
            case ACCESS_TMP_VIA_PROC_ROOT -> List.of();
        };

        List<String> elevatedOpts = elevated && USER.isPresent() ? List.of(NET_BIND_SERVICE, USER.get()) : Collections.emptyList();

        List<String> imageAndCommand = List.of(
            IMAGE_NAME, whatToRun
        );

        List<String> cmd = new ArrayList<>();
        cmd.addAll(initialCommands);
        cmd.addAll(elevatedOpts);
        cmd.addAll(attachStrategyCommands);
        cmd.addAll(imageAndCommand);
        cmd.addAll(Arrays.asList(args));
        return DockerTestUtils.execute(cmd);
    }

    // Returns PID of a matching process, or -1 if not found.
    private static long findProcess(OutputAnalyzer out, String name) throws Exception {
        List<String> l = out.asLines()
            .stream()
            .filter(s -> s.contains(name))
            .toList();
        if (l.isEmpty()) {
            return -1;
        }
        String psInfo = l.getFirst();
        System.out.println("findProcess(): psInfo: " + psInfo);
        String pid = psInfo.substring(0, psInfo.indexOf(' '));
        System.out.println("findProcess(): pid: " + pid);
        return Long.parseLong(pid);
    }

    private static DockerRunOptions commonDockerOpts(String className) {
        return new DockerRunOptions(IMAGE_NAME, "/jdk/bin/java", className)
            .addDockerOpts("--volume", Utils.TEST_CLASSES + ":/test-classes/")
            .addJavaOpts("-cp", "/test-classes/");
    }

    private static void sleep(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            System.out.println("InterruptedException" + e.getMessage());
        }
    }


    static class MainContainer {
        private static final String MAIN_CONTAINER_NAME_PREFIX = "test-container-main";
        private static final Random RANDOM = Utils.getRandomInstance();

        String name;
        boolean mainMethodStarted;
        Process p;

        private Consumer<String> outputConsumer = s -> {
            if (!mainMethodStarted && s.contains(EventGeneratorLoop.MAIN_METHOD_STARTED)) {
                System.out.println("MainContainer: setting mainMethodStarted");
                mainMethodStarted = true;
            }
        };

        public Process start(final boolean elevated) throws Exception {
            // start "main" container (the observee)
            DockerRunOptions opts = commonDockerOpts("EventGeneratorLoop");

            if (elevated && USER.isPresent()) {
                opts.addDockerOpts(USER.get());
                opts.addDockerOpts(NET_BIND_SERVICE);
            }

            name = MAIN_CONTAINER_NAME_PREFIX + "-elevated-" + elevated + "-" + RANDOM.nextInt();

            opts.addDockerOpts("--cap-add=SYS_PTRACE")
                .addDockerOpts("--init")
                .addDockerOpts("--name", name)
                .addDockerOpts("--volume", "/tmp")
                .addDockerOpts("--volume", Paths.get(".").toAbsolutePath() + ":/workdir/")
                .addJavaOpts("-XX:+UsePerfData")
                .addClassOptions("" + TIME_TO_RUN_MAIN_PROCESS);
            // avoid large Xmx
            opts.appendTestJavaOptions = false;

            List<String> cmd = DockerTestUtils.buildJavaCommand(opts);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            p = ProcessTools.startProcess("main-container-process",
                                          pb,
                                          outputConsumer);
            return p;
        }

        public void waitForMainMethodStart(long howLong) {
            long expiration = System.currentTimeMillis() + howLong;

            do {
                if (mainMethodStarted) {
                    return;
                }
                sleep(200);
            } while (System.currentTimeMillis() < expiration);

            throw new RuntimeException("Timed out while waiting for main() to start");
        }

        public void assertIsAlive() throws Exception {
            if (!p.isAlive()) {
                throw new RuntimeException("Main container process stopped unexpectedly, exit value: "
                                           + p.exitValue());
            }
        }

        public void waitFor(long timeout) throws Exception {
            p.waitFor(timeout, TimeUnit.MILLISECONDS);
        }

        public void stop() throws Exception {
            OutputAnalyzer out = DockerTestUtils.execute(Container.ENGINE_COMMAND, "ps")
                    .shouldHaveExitValue(0);
            if (out.contains(name)) {
                DockerTestUtils.execute(Container.ENGINE_COMMAND, "stop", name)
                        .shouldHaveExitValue(0);
            }
        }

        public String name() {
            return name;
        }
    }

    private enum AttachStrategy {
        TMP_MOUNTED_INTO_SIDECAR,
        ACCESS_TMP_VIA_PROC_ROOT
    }
}
