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
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
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
import static org.junit.jupiter.api.Assertions.*;

class DynamicLoadWarningTest {

    // JVM TI agent
    private static final String JVMTI_AGENT_LIB = "JvmtiAgent";
    private static String jvmtiAgentPath;

    // Java agent
    private static final String TEST_CLASSES = System.getProperty("test.classes");
    private static String javaAgent;

    @BeforeAll
    static void createJavaAgent() throws Exception {
        // create JAR file with Java agent
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(new Attributes.Name("Agent-Class"), "JavaAgent");
        Path jarfile = Path.of("javaagent.jar");
        Path classes = Path.of(TEST_CLASSES);
        JarUtils.createJarFile(jarfile, man, classes, Path.of("JavaAgent.class"));
        javaAgent = jarfile.toString();

        // get absolute path to JVM TI agent
        String libname;
        if (Platform.isWindows()) {
            libname = JVMTI_AGENT_LIB + ".dll";
        } else if (Platform.isOSX()) {
            libname = "lib" + JVMTI_AGENT_LIB + ".dylib";
        } else {
            libname = "lib" + JVMTI_AGENT_LIB + ".so";
        }
        jvmtiAgentPath = Path.of(Utils.TEST_NATIVE_PATH, libname)
                .toAbsolutePath()
                .toString();
    }

    /**
     * Test loading JVM TI agent into a running VM with the Attach API.
     */
    @Test
    void testLoadJvmtiAgent() throws Exception {
        String message = "WARNING: A JVM TI agent has been dynamically loaded";

        // warning should be printed
        test((pid, vm) -> vm.loadAgentLibrary(JVMTI_AGENT_LIB)).shouldContain(message);

        // no warning should be printed
        test((pid, vm) -> vm.loadAgentLibrary(JVMTI_AGENT_LIB), "-XX:+EnableDynamicAgentLoading")
                .shouldNotContain(message);
    }

    /**
     * Test loading JVM TI agent into a running VM with jcmd VMTI.agent_load command.
     */
    @Test
    void testJCmdJvmtiAgentLoad() throws Exception {
        String message = "WARNING: A JVM TI agent has been dynamically loaded";

        // jcmd <pid> JVMTI.agent_load <agent>
        Op op = (pid, vm) -> {
            var jcmd = JDKToolLauncher.createUsingTestJDK("jcmd")
                    .addToolArg(""+pid)
                    .addToolArg("JVMTI.agent_load")
                    .addToolArg(jvmtiAgentPath);
            var pb = new ProcessBuilder(jcmd.getCommand());
            int exitValue = ProcessTools.executeProcess(pb)
                    .outputTo(System.out)
                    .errorTo(System.out)
                    .getExitValue();
            assertEquals(0, exitValue);
        };

        // warning should be printed
        test(op).shouldContain(message);

        // no warning should be printed
        test(op, "-XX:+EnableDynamicAgentLoading").shouldNotContain(message);
    }

    /**
     * Test loading Java agent into a running VM.
     */
    @Test
    void testLoadJavaAgent() throws Exception {
        String message = "WARNING: A Java agent has been loaded dynamically";

        // warning should be printed
        test((pid, vm) -> vm.loadAgent(javaAgent)).shouldContain(message);

        // no warning should be printed
        test((pid, vm) -> vm.loadAgent(javaAgent), "-XX:+EnableDynamicAgentLoading")
                .shouldNotContain(message);
    }

    private interface Op {
        void accept(long pid, VirtualMachine vm) throws Exception;
    }

    /**
     * Starts a new VM to run an application, attaches to the VM, runs a given action
     * with the VirtualMachine object, and returns the output (both stdout and stderr)
     * for analysis.
     */
    private OutputAnalyzer test(Op action, String... vmopts) throws Exception {
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

                    // attach and run the action with the vm object
                    VirtualMachine vm = VirtualMachine.attach(""+pid);
                    try {
                        action.accept(pid, vm);
                        done.set(true);
                    } finally {
                        vm.detach();
                    }

                    // shutdown
                    s.getOutputStream().write(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // launch application with the given VM options, waiting for it to terminate
            Stream<String> s1 = Stream.of(vmopts);
            Stream<String> s2 = Stream.of("Application", ""+listener.getLocalPort());
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
}

