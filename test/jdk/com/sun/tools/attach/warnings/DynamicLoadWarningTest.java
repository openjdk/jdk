/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8307478
 * @summary Test that a warning is printed when an agent is dynamically loaded
 * @requires vm.jvmti
 * @modules jdk.attach jdk.jcmd
 * @library /test/lib /test/jdk
 * @build Application JavaAgent
 * @run junit/othervm/native DynamicLoadWarningTest
 */

import java.io.DataInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.tools.attach.VirtualMachine;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.JarUtils;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class DynamicLoadWarningTest {
    private static final String JVMTI_AGENT_WARNING = "WARNING: A JVM TI agent has been loaded dynamically";
    private static final String JAVA_AGENT_WARNING  = "WARNING: A Java agent has been loaded dynamically";

    // JVM TI agents
    private static final String JVMTI_AGENT1_LIB = "JvmtiAgent1";
    private static final String JVMTI_AGENT2_LIB = "JvmtiAgent2";
    private static String jvmtiAgentPath1;
    private static String jvmtiAgentPath2;

    // Java agent
    private static final String TEST_CLASSES = System.getProperty("test.classes");
    private static String javaAgent;

    @BeforeAll
    static void setup() throws Exception {
        // get absolute path to JVM TI agents
        String libname1 = Platform.buildSharedLibraryName(JVMTI_AGENT1_LIB);
        String libname2 = Platform.buildSharedLibraryName(JVMTI_AGENT2_LIB);
        jvmtiAgentPath1 = Path.of(Utils.TEST_NATIVE_PATH, libname1).toAbsolutePath().toString();
        jvmtiAgentPath2 = Path.of(Utils.TEST_NATIVE_PATH, libname2).toAbsolutePath().toString();

        // create JAR file with Java agent
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(new Attributes.Name("Agent-Class"), "JavaAgent");
        Path jarfile = Path.of("javaagent.jar");
        Path classes = Path.of(TEST_CLASSES);
        JarUtils.createJarFile(jarfile, man, classes, Path.of("JavaAgent.class"));
        javaAgent = jarfile.toString();
    }

    /**
     * Actions to load JvmtiAgent1 into a running VM.
     */
    private static Stream<OnAttachAction> loadJvmtiAgent1() {
        // load agent with the attach API
        OnAttachAction loadJvmtiAgent = (pid, vm) -> vm.loadAgentLibrary(JVMTI_AGENT1_LIB);

        // jcmd <pid> JVMTI.agent_load <agent>
        OnAttachAction jcmdAgentLoad = jcmdAgentLoad(jvmtiAgentPath1);

        return Stream.of(loadJvmtiAgent, jcmdAgentLoad);
    }

    /**
     * Test loading JvmtiAgent1 into a running VM.
     */
    @ParameterizedTest
    @MethodSource("loadJvmtiAgent1")
    void testLoadOneJvmtiAgent(OnAttachAction loadJvmtiAgent1) throws Exception {
        // dynamically load loadJvmtiAgent1
        test().whenRunning(loadJvmtiAgent1)
                .stderrShouldContain(JVMTI_AGENT_WARNING);

        // opt-in via command line option to allow dynamic loading of agents
        test().withOpts("-XX:+EnableDynamicAgentLoading")
                .whenRunning(loadJvmtiAgent1)
                .stderrShouldNotContain(JVMTI_AGENT_WARNING);

        // start loadJvmtiAgent1 via the command line, then dynamically load loadJvmtiAgent1
        test().withOpts("-agentpath:" + jvmtiAgentPath1)
                .whenRunning(loadJvmtiAgent1)
                .stderrShouldNotContain(JVMTI_AGENT_WARNING);

        // dynamically load loadJvmtiAgent1 twice, should be one warning
        test().whenRunning(loadJvmtiAgent1)
                .whenRunning(loadJvmtiAgent1)
                .stderrShouldContain(JVMTI_AGENT_WARNING, 1);
    }

    /**
     * Test loading JvmtiAgent1 and JvmtiAgent2 into a running VM.
     */
    @ParameterizedTest
    @MethodSource("loadJvmtiAgent1")
    void testLoadTwoJvmtiAgents(OnAttachAction loadJvmtiAgent1) throws Exception {
        OnAttachAction loadJvmtiAgent2 = (pid, vm) -> vm.loadAgentLibrary(JVMTI_AGENT2_LIB);
        OnAttachAction jcmdAgentLoad2 = jcmdAgentLoad(jvmtiAgentPath2);

        // dynamically load loadJvmtiAgent1, then dynamically load loadJvmtiAgent2 with attach API
        test().whenRunning(loadJvmtiAgent1)
                .whenRunning(loadJvmtiAgent2)
                .stderrShouldContain(JVMTI_AGENT_WARNING, 2);

        // dynamically load loadJvmtiAgent1, then dynamically load loadJvmtiAgent2 with jcmd
        test().whenRunning(loadJvmtiAgent1)
                .whenRunning(jcmdAgentLoad2)
                .stderrShouldContain(JVMTI_AGENT_WARNING, 2);

        // start loadJvmtiAgent2 via the command line, then dynamically load loadJvmtiAgent1
        test().withOpts("-agentpath:" + jvmtiAgentPath2)
                .whenRunning(loadJvmtiAgent1)
                .stderrShouldContain(JVMTI_AGENT_WARNING);
    }

    /**
     * Test loading Java agent into a running VM.
     */
    @Test
    void testLoadJavaAgent() throws Exception {
        OnAttachAction loadJavaAgent = (pid, vm) -> vm.loadAgent(javaAgent);

        // agent dynamically loaded
        test().whenRunning(loadJavaAgent)
                .stderrShouldContain(JAVA_AGENT_WARNING);

        // opt-in via command line option to allow dynamic loading of agents
        test().withOpts("-XX:+EnableDynamicAgentLoading")
                .whenRunning(loadJavaAgent)
                .stderrShouldNotContain(JAVA_AGENT_WARNING);
    }

    /**
     * Represents an operation that accepts a process identifier and a VirtualMachine
     * that the current JVM is attached to.
     */
    private interface OnAttachAction {
        void accept(long pid, VirtualMachine vm) throws Exception;
    }

    /**
     * Returns an operation that invokes "jcmd <pid> JVMTI.agent_load <agentpath>" to
     * load the given agent library into the JVM that the current JVM is attached to.
     */
    private static OnAttachAction jcmdAgentLoad(String agentPath) {
        return (pid, vm) -> {
            String[] jcmd = JDKToolLauncher.createUsingTestJDK("jcmd")
                    .addToolArg(Long.toString(pid))
                    .addToolArg("JVMTI.agent_load")
                    .addToolArg(agentPath)
                    .getCommand();
            System.out.println(Arrays.stream(jcmd).collect(Collectors.joining(" ")));
            Process p = new ProcessBuilder(jcmd).inheritIO().start();
            assertEquals(0, p.waitFor());
        };
    }

    /**
     * Returns a new app runner.
     */
    private static AppRunner test() {
        return new AppRunner();
    }

    /**
     * Runs an application in its own VM. Once started, it attachs to the VM, runs a set
     * of actions, then checks that the output contains, or does not contain, a string.
     */
    private static class AppRunner {
        private String[] vmopts = new String[0];
        private List<OnAttachAction> actions = new ArrayList<>();

        /**
         * Specifies VM options to run the application.
         */
        AppRunner withOpts(String... vmopts) {
            this.vmopts = vmopts;
            return this;
        }

        /**
         * Specifies an action to run when the attached to the running application.
         */
        AppRunner whenRunning(OnAttachAction action) {
            actions.add(action);
            return this;
        }

        OutputAnalyzer run() throws Exception {
            // start a listener socket that the application will connect to
            try (ServerSocket listener = new ServerSocket()) {
                InetAddress lh = InetAddress.getLoopbackAddress();
                listener.bind(new InetSocketAddress(lh, 0));

                var done = new AtomicBoolean();

                // start a thread to wait for the application to phone home
                Thread.ofPlatform().daemon().start(() -> {
                    try (Socket s = listener.accept();
                         DataInputStream in = new DataInputStream(s.getInputStream())) {

                        // read pid
                        long pid = in.readLong();

                        // attach and run the actions with the vm object
                        VirtualMachine vm = VirtualMachine.attach(Long.toString(pid));
                        try {
                            for (OnAttachAction action : actions) {
                                action.accept(pid, vm);
                            }
                        } finally {
                            vm.detach();
                        }
                        done.set(true);

                        // shutdown
                        s.getOutputStream().write(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                // launch application with the given VM options, waiting for it to terminate
                Stream<String> s1 = Stream.of(vmopts);
                Stream<String> s2 = Stream.of("Application", Integer.toString(listener.getLocalPort()));
                String[] opts = Stream.concat(s1, s2).toArray(String[]::new);
                OutputAnalyzer outputAnalyzer = ProcessTools
                        .executeTestJava(opts)
                        .outputTo(System.out)
                        .errorTo(System.out);
                assertEquals(0, outputAnalyzer.getExitValue());
                assertTrue(done.get(), "Attach or action failed, see log for details");
                return outputAnalyzer;
            }
        }

        /**
         * Run the application, checking that standard error contains a string.
         */
        void stderrShouldContain(String s) throws Exception {
            run().stderrShouldContain(s);
        }

        /**
         * Run the application, checking that standard error contains the given number of
         * occurrences of a string.
         */
        void stderrShouldContain(String s, int occurrences) throws Exception {
            List<String> lines = run().asLines();
            int count = (int) lines.stream().filter(line -> line.indexOf(s) >= 0).count();
            assertEquals(occurrences, count);
        }

        /**
         * Run the application, checking that standard error does not contain a string.
         */
        void stderrShouldNotContain(String s) throws Exception {
            run().stderrShouldNotContain(s);
        }
    }
}
