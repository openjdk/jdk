/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary VM.info preserves agent identity and reports completed initialization independently of environment lifetime
 * @requires vm.flagless & vm.jvmti & !jdk.static
 * @library /test/lib
 * @modules jdk.attach java.instrument
 * @build VMInfoJavaAgent
 * @run driver jdk.test.lib.util.JavaAgentBuilder VMInfoJavaAgent VMInfoJavaAgent.jar
 * @run main/othervm/native/timeout=300 TestAgentVMInfo
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.sun.tools.attach.VirtualMachine;

import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.dcmd.PidJcmdExecutor;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestAgentVMInfo {
    private static final String STARTUP_AGENT = AgentTestSupport.library("VMInfoAgent");
    private static final String ATTACH_AGENT = AgentTestSupport.library("OnAttachAgent");
    private static final String JAVA_AGENT = Path.of("VMInfoJavaAgent.jar").toAbsolutePath().toString();
    private static final String CREATE = "create-environment-without-callback";
    private static final String DISPOSE_BEFORE_INIT = "dispose-environment-in-vm-start";
    private static final String ATTACH_OPTIONS = "create-environment-success, 0, 0";
    private static final String PREMAIN_OPTIONS = "premain=value=one";
    private static final String AGENTMAIN_OPTIONS = "agentmain=value=two";

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testNoAgents();
        testLegacyAgentWithoutEnvironment();
        testCommandLineAgentWithFallbackTiming();
        testDisposedAgentWithoutInitializationTiming();
        testLiveAndDisposedAttachedAgent();
        testJavaAgentIdentityAndOptions();
        testJavaAgentWithoutOptions();
        testJavaAgentWithEmptyOptions();
    }

    // A fresh VM reports an explicitly empty agent section.
    private static void testNoAgents() throws Exception {
        launch("no-agents");
    }

    // An unconverted JVM_OnLoad agent is listed once and initialized without creating a JVMTI environment.
    private static void testLegacyAgentWithoutEnvironment() throws Exception {
        launch("legacy-agent", "-XrunVMInfoAgent").shouldContain("JVM_OnLoad:none").shouldNotContain("Agent_OnLoad:");
    }

    // A registered command-line agent without VMInit callbacks receives fallback initialization timing.
    private static void testCommandLineAgentWithFallbackTiming() throws Exception {
        launch("command-line-agent", "-agentpath:" + STARTUP_AGENT + "=" + CREATE).shouldContain("Agent_OnLoad:" + CREATE);
    }

    // Disposal in VMStart publishes unavailable timing, which must not be reported as initialized.
    private static void testDisposedAgentWithoutInitializationTiming() throws Exception {
        launch("disposed-before-init", "-agentpath:" + STARTUP_AGENT + "=" + DISPOSE_BEFORE_INIT).shouldContain("disposed-before-vm-init");
    }

    // A registered agent remains visible, with its completed timing and original options, after disposal.
    private static void testLiveAndDisposedAttachedAgent() throws Exception {
        launch("attached-agent");
    }

    // Both Java startup phases report the JAR name and user options, preserving '=' in option values.
    private static void testJavaAgentIdentityAndOptions() throws Exception {
        launch("java-agents", "-javaagent:" + JAVA_AGENT + "=" + PREMAIN_OPTIONS);
    }

    // Absent options reach both Java entry points as null and appear as options:none.
    private static void testJavaAgentWithoutOptions() throws Exception {
        launch("java-agents-without-options", "-javaagent:" + JAVA_AGENT);
    }

    // Explicitly empty options remain empty in both Java entry points and appear as options:.
    private static void testJavaAgentWithEmptyOptions() throws Exception {
        launch("java-agents-with-empty-options", "-javaagent:" + JAVA_AGENT + "=");
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "no-agents" -> agentInfo(0);
            case "legacy-agent" ->
                assertAgent(agentInfo(1), "VMInfoAgent", STARTUP_AGENT, "none", false, false, true);
            case "command-line-agent" -> assertAgent(agentInfo(1), STARTUP_AGENT, STARTUP_AGENT, CREATE, false, false, true);
            case "disposed-before-init" -> assertAgent(agentInfo(1), STARTUP_AGENT, STARTUP_AGENT, DISPOSE_BEFORE_INIT, false, false, false);
            case "attached-agent" -> verifyLiveAndDisposedAttachedAgent();
            case "java-agents" -> verifyJavaAgentIdentityAndOptions(PREMAIN_OPTIONS, AGENTMAIN_OPTIONS);
            case "java-agents-without-options" -> verifyJavaAgentIdentityAndOptions(null, null);
            case "java-agents-with-empty-options" -> verifyJavaAgentIdentityAndOptions("", "");
            default -> throw new AssertionError("Unknown VM.info operation: " + operation);
        }
        System.out.println("VM.info operation complete: " + operation);
    }

    private static void verifyLiveAndDisposedAttachedAgent() throws Exception {
        AgentTestSupport.attach(ATTACH_AGENT, ATTACH_OPTIONS);
        assertAgent(agentInfo(1), ATTACH_AGENT, ATTACH_AGENT, ATTACH_OPTIONS, true, false, true);
        AgentTestSupport.attach(ATTACH_AGENT, "dispose-environment-success, 0, 0");
        AgentTestSupport.attach(ATTACH_AGENT, "check-no-environments-success, 0, 0");
        assertAgent(agentInfo(1), ATTACH_AGENT, ATTACH_AGENT, ATTACH_OPTIONS, true, false, true);
    }

    private static void verifyJavaAgentIdentityAndOptions(String premainOptions, String agentmainOptions) throws Exception {
        String instrument = Platform.libDir().resolve(System.mapLibraryName("instrument")).toString();
        String premainOptionsText = premainOptions == null ? "none" : premainOptions;
        String agentmainOptionsText = agentmainOptions == null ? "none" : agentmainOptions;
        Asserts.assertEquals(System.getProperty("VMInfoJavaAgent.premain"), String.valueOf(premainOptions));
        assertAgent(agentInfo(1), JAVA_AGENT, instrument, premainOptionsText, false, true, true);
        VirtualMachine vm = VirtualMachine.attach(Long.toString(ProcessHandle.current().pid()));
        try {
            vm.loadAgent(JAVA_AGENT, agentmainOptions);
        } finally {
            vm.detach();
        }
        Asserts.assertEquals(System.getProperty("VMInfoJavaAgent.agentmain"), String.valueOf(agentmainOptions));
        List<String> agents = agentInfo(2);
        assertAgent(agents, JAVA_AGENT, instrument, premainOptionsText, false, true, true);
        assertAgent(agents, JAVA_AGENT, instrument, agentmainOptionsText, true, true, true);
    }

    private static OutputAnalyzer launch(String operation, String... startupOptions) throws Exception {
        List<String> command = new ArrayList<>(List.of(startupOptions));
        command.add("-XX:+EnableDynamicAgentLoading");
        command.add("-Djdk.attach.allowAttachSelf=true");
        command.add("-Dtest.jdk=" + Utils.TEST_JDK);
        command.add("-Dtest.nativepath=" + Utils.TEST_NATIVE_PATH);
        command.add("-Dtest.timeout.factor=" + System.getProperty("test.timeout.factor", "1.0"));
        command.add(TestAgentVMInfo.class.getName());
        command.add(operation);
        ProcessBuilder process = ProcessTools.createTestJavaProcessBuilder(command);
        AgentTestSupport.addNativeLibraryPath(process);
        return ProcessTools.executeProcess(process).shouldHaveExitValue(0).shouldContain("VM.info operation complete: " + operation);
    }

    private static List<String> agentInfo(int expectedCount) {
        OutputAnalyzer output = new PidJcmdExecutor().execute("VM.info", true);
        output.shouldHaveExitValue(0);
        List<String> lines = output.getStdout().lines().toList();
        List<String> headers = lines.stream().filter(line -> line.equals("JVMTI agents:") || line.equals("JVMTI agents: none")).toList();
        Asserts.assertEquals(headers.size(), 1, "Expected one JVMTI agent section\n" + output.getOutput());
        List<String> agents = List.of();
        if (headers.getFirst().equals("JVMTI agents:")) {
            agents = lines.stream().skip(lines.indexOf("JVMTI agents:") + 1) .takeWhile(line -> !line.isBlank()).toList();
            Asserts.assertFalse(agents.isEmpty(), "A nonempty agent section must contain entries");
        }
        Asserts.assertEquals(agents.size(), expectedCount, "Unexpected agent entries: " + agents);
        System.out.println("JVMTI agent entries: " + agents);
        return agents;
    }

    private static void assertAgent(List<String> agents, String name, String library, String options,
                                    boolean dynamic, boolean instrument, boolean initialized) throws Exception {
        String prefix = name + " path:";
        String suffix = ", loaded, " + (initialized ? "initialized" : "not initialized") +
                        ", " + (dynamic ? "dynamic " : "") + (instrument ? "instrumentlib " : "") + "options:" + options;
        List<String> matches = agents.stream().filter(line -> line.startsWith(prefix) && line.endsWith(suffix)).toList();
        Asserts.assertEquals(matches.size(), 1, "Expected exactly one " + prefix + "<library>" + suffix + "\n" + agents);
        String entry = matches.getFirst();
        Path actualPath = Path.of(entry.substring(prefix.length(), entry.length() - suffix.length()));
        Asserts.assertTrue(Files.isSameFile(actualPath, Path.of(library)), "Unexpected library path: " + entry);
    }
}
