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
 * @summary Converted and unconverted -Xrun agents follow their reporting and lifecycle contracts
 * @requires vm.flagless & vm.hasJFR & vm.jvmti & !jdk.static
 * @library /test/lib ../model
 * @modules jdk.attach jdk.jfr
 * @run main/othervm/native TestXRunAgentEvents
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestXRunAgentEvents {
    private static final String UNCONVERTED_AGENT = "VMInfoAgent";
    private static final String ONLOAD_AGENT = "XRunAgent";
    // JVM_OnLoad prints this marker and returns without obtaining a JVMTI environment.
    private static final String NO_ENVIRONMENT = "no-environment";
    // XRunAgent exports Agent_OnLoad but not JVM_OnLoad; this operation retains an environment without callbacks.
    private static final String CREATE_ENVIRONMENT = "create-environment-without-callback";

    // No System.loadLibrary or RegisterNatives: these distinct symbols require fresh JVMTI native lookups.
    private static native int nativeLookupBeforeDisposal();
    private static native void disposeEnvironment();
    private static native int nativeLookupAfterDisposal();

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testXRunAgentWithoutEnvironment();
        testCommandLineAgentWithEnvironment();
        testConvertedXRunAgentWithEnvironment();
    }

    // An unconverted JVM_OnLoad agent reports its identity and
    // fallback timing without a registered association.
    private static void testXRunAgentWithoutEnvironment() throws Exception {
        launch("record-xrun-agent", "-Xrun" + UNCONVERTED_AGENT + ":" + NO_ENVIRONMENT)
            .shouldHaveExitValue(0)
            .shouldContain("JVM_OnLoad:" + NO_ENVIRONMENT)
            .shouldNotContain("Agent_OnLoad:")
            .shouldContain("xrun agent events verified");
    }

    // Ordinary -agentlib startup provides the baseline for reporting, native lookup, and one unload notification.
    private static void testCommandLineAgentWithEnvironment() throws Exception {
        launchWithEnvironment("-agentlib:" + ONLOAD_AGENT + "=" + CREATE_ENVIRONMENT);
    }

    // The same library loaded through -Xrun must satisfy exactly the same checks after conversion to Agent_OnLoad.
    private static void testConvertedXRunAgentWithEnvironment() throws Exception {
        launchWithEnvironment("-Xrun" + ONLOAD_AGENT + ":" + CREATE_ENVIRONMENT);
    }

    private static void launchWithEnvironment(String startup) throws Exception {
        OutputAnalyzer output = launch("record-onload-agent", startup);
        output.shouldHaveExitValue(0).shouldContain("xrun agent events verified");
        JfrAgentTestSupport.unloadedOnce(output, ONLOAD_AGENT);
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "record-xrun-agent" -> verifyXRunAgentEvents();
            case "record-onload-agent" -> verifyOnLoadAgentEvents();
            default -> throw new AssertionError("Unknown xrun agent operation: " + operation);
        }
        System.out.println("xrun agent events verified");
    }

    private static void verifyXRunAgentEvents() throws Exception {
        try (AgentEventRecording recording = new AgentEventRecording()) {
            assertAgentEvents(recording.stop(), UNCONVERTED_AGENT, NO_ENVIRONMENT).assertLive();
        }
    }

    private static void verifyOnLoadAgentEvents() throws Exception {
        // Only Agent_OnLoad increments this counter; the native probe also checks that its environment is live.
        Asserts.assertEquals(nativeLookupBeforeDisposal(), 1, "Agent_OnLoad invocation count");
        try (AgentEventRecording recording = new AgentEventRecording()) {
            var events = assertAgentEvents(recording.stop(), ONLOAD_AGENT, CREATE_ENVIRONMENT).assertLive();
            assertOncePerBatch(events);
        }
        try (AgentEventRecording recording = new AgentEventRecording()) {
            disposeEnvironment();
            assertAgentEvents(recording.stop(), ONLOAD_AGENT, CREATE_ENVIRONMENT).assertDisposed();
        }
        // Resolve a different symbol after disposal: native lookup outlives the environment.
        Asserts.assertEquals(nativeLookupAfterDisposal(), 1, "Agent_OnLoad invocation count after disposal");
        // A converted agent must not survive disposal as a retained-xrun entry.
        AgentEventRecording.assertNoEventsInNewRecording(ONLOAD_AGENT);
    }

    private static AgentEventRecording.FilteredList assertAgentEvents(AgentEventRecording.Results results,
                                                                      String agentName,
                                                                      String options) throws Exception {
        var events = results.forLibrary(agentName).assertOptions(options).assertDynamic(false).assertInitializationTiming();
        Path library = Path.of(JfrAgentTestSupport.library(agentName));
        for (var event : events.events()) {
            String description = event.toString();
            Asserts.assertTrue(Files.isSameFile(Path.of(event.getString("path")), library), description);
            Asserts.assertEquals(event.getLong("initializationDuration"), 0L, description);
        }
        return events;
    }

    private static void assertOncePerBatch(AgentEventRecording.FilteredList events) {
        // Both iterator passes share the batch timestamp. Repeated batches are valid, duplicate membership is not.
        Set<Instant> timestamps = new HashSet<>();
        for (var event : events.events()) {
            Asserts.assertTrue(timestamps.add(event.getStartTime()), "Duplicate agent event in one batch\n" + events);
        }
    }

    private static OutputAnalyzer launch(String operation, String startup) throws Exception {
        ProcessBuilder process = ProcessTools.createTestJavaProcessBuilder("-XX:+UnlockExperimentalVMOptions",
                                                                           "-XX:-UseFastUnorderedTimeStamps",
                                                                           startup,
                                                                           "-Dtest.nativepath=" + Utils.TEST_NATIVE_PATH,
                                                                           "-Dtest.timeout.factor=" + System.getProperty("test.timeout.factor", "1.0"),
                                                                           TestXRunAgentEvents.class.getName(), operation);
        JfrAgentTestSupport.addNativeLibraryPath(process);
        return ProcessTools.executeProcess(process);
    }
}
