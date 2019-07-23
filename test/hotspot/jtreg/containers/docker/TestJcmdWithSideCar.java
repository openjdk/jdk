/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @requires docker.support
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @build EventGeneratorLoop
 * @run driver TestJcmdWithSideCar
 */
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import jdk.test.lib.Container;
import jdk.test.lib.Utils;
import jdk.test.lib.containers.docker.Common;
import jdk.test.lib.containers.docker.DockerRunOptions;
import jdk.test.lib.containers.docker.DockerTestUtils;
import jdk.test.lib.process.OutputAnalyzer;


public class TestJcmdWithSideCar {
    private static final String IMAGE_NAME = Common.imageName("jfr-jcmd");
    private static final int TIME_TO_RUN_MAIN_PROCESS = (int) (30 * Utils.TIMEOUT_FACTOR); // seconds
    private static final String MAIN_CONTAINER_NAME = "test-container-main";

    public static void main(String[] args) throws Exception {
        if (!DockerTestUtils.canTestDocker()) {
            return;
        }

        DockerTestUtils.buildJdkDockerImage(IMAGE_NAME, "Dockerfile-BasicTest", "jdk-docker");

        try {
            // Start the loop process in the "main" container, then run test cases
            // using a sidecar container.
            DockerThread t = startMainContainer();

            waitForMainContainerToStart(500, 10);
            t.checkForErrors();

            OutputAnalyzer jcmdOut = testCase01();
            long mainProcPid = findProcess(jcmdOut, "EventGeneratorLoop");

            t.assertIsAlive();
            testCase02(mainProcPid);

            // JCMD does not work in sidecar configuration, except for "jcmd -l".
            // Including this test case to assist in reproduction of the problem.
            // t.assertIsAlive();
            // testCase03(mainProcPid);

            t.join(TIME_TO_RUN_MAIN_PROCESS * 1000);
            t.checkForErrors();
        } finally {
            DockerTestUtils.removeDockerImage(IMAGE_NAME);
        }
    }


    // Run "jcmd -l" in a sidecar container and find a process that runs EventGeneratorLoop
    private static OutputAnalyzer testCase01() throws Exception {
        return runSideCar(MAIN_CONTAINER_NAME, "/jdk/bin/jcmd", "-l")
            .shouldHaveExitValue(0)
            .shouldContain("sun.tools.jcmd.JCmd")
            .shouldContain("EventGeneratorLoop");
    }

    // run jhsdb jinfo <PID> (jhsdb uses PTRACE)
    private static void testCase02(long pid) throws Exception {
        runSideCar(MAIN_CONTAINER_NAME, "/jdk/bin/jhsdb", "jinfo", "--pid", "" + pid)
            .shouldHaveExitValue(0)
            .shouldContain("Java System Properties")
            .shouldContain("VM Flags");
    }

    // test jcmd with some commands (help, start JFR recording)
    // JCMD will use signal mechanism and Unix Socket
    private static void testCase03(long pid) throws Exception {
        runSideCar(MAIN_CONTAINER_NAME, "/jdk/bin/jcmd", "" + pid, "help")
            .shouldHaveExitValue(0)
            .shouldContain("VM.version");
        runSideCar(MAIN_CONTAINER_NAME, "/jdk/bin/jcmd", "" + pid, "JFR.start")
            .shouldHaveExitValue(0)
            .shouldContain("Started recording");
    }

    private static DockerThread startMainContainer() throws Exception {
        // start "main" container (the observee)
        DockerRunOptions opts = commonDockerOpts("EventGeneratorLoop");
        opts.addDockerOpts("--cap-add=SYS_PTRACE")
            .addDockerOpts("--name", MAIN_CONTAINER_NAME)
            .addDockerOpts("-v", "/tmp")
            .addJavaOpts("-XX:+UsePerfData")
            .addClassOptions("" + TIME_TO_RUN_MAIN_PROCESS);
        DockerThread t = new DockerThread(opts);
        t.start();

        return t;
    }

    private static void waitForMainContainerToStart(int delayMillis, int count) throws Exception {
        boolean started = false;
        for(int i=0; i < count; i++) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {}
            if (isMainContainerRunning()) {
                started = true;
                break;
            }
        }
        if (!started) {
            throw new RuntimeException("Main container did not start");
        }
    }

    private static boolean isMainContainerRunning() throws Exception {
        OutputAnalyzer out =
            DockerTestUtils.execute(Container.ENGINE_COMMAND,
                                    "ps", "--no-trunc",
                                    "--filter", "name=" + MAIN_CONTAINER_NAME);
        return out.getStdout().contains(MAIN_CONTAINER_NAME);
    }

    // JCMD relies on the attach mechanism (com.sun.tools.attach),
    // which in turn relies on JVMSTAT mechanism, which puts its mapped
    // buffers in /tmp directory (hsperfdata_<user>). Thus, in sidecar
    // we mount /tmp via --volumes-from from the main container.
    private static OutputAnalyzer runSideCar(String MAIN_CONTAINER_NAME, String whatToRun,
                                             String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        String[] command = new String[] {
            Container.ENGINE_COMMAND, "run",
            "--tty=true", "--rm",
            "--cap-add=SYS_PTRACE", "--sig-proxy=true",
            "--pid=container:" + MAIN_CONTAINER_NAME,
            "--volumes-from", MAIN_CONTAINER_NAME,
            IMAGE_NAME, whatToRun
        };

        cmd.addAll(Arrays.asList(command));
        cmd.addAll(Arrays.asList(args));
        return DockerTestUtils.execute(cmd);
    }

    private static long findProcess(OutputAnalyzer out, String name) throws Exception {
        List<String> l = out.asLines()
            .stream()
            .filter(s -> s.contains(name))
            .collect(Collectors.toList());
        if (l.isEmpty()) {
            throw new RuntimeException("Could not find matching process");
        }
        String psInfo = l.get(0);
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


    static class DockerThread extends Thread {
        DockerRunOptions runOpts;
        Exception exception;

        DockerThread(DockerRunOptions opts) {
            runOpts = opts;
        }

        public void run() {
            try {
                DockerTestUtils.dockerRunJava(runOpts);
            } catch (Exception e) {
                exception = e;
            }
        }

        public void assertIsAlive() throws Exception {
            if (!isAlive()) {
                throw new RuntimeException("DockerThread stopped unexpectedly");
            }
        }

        public void checkForErrors() throws Exception {
            if (exception != null) {
                throw new RuntimeException("DockerThread threw exception"
                                           + exception.getMessage());
            }
        }
    }

}
