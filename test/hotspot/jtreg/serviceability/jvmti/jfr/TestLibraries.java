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
 *
 */

/*
 * @test
 * @summary Independent reporting of native libraries' agents across repeated attaches and overlapping recordings
 * @requires vm.flagless & vm.hasJFR & vm.jvmti & !jdk.static
 * @library /test/lib ../model
 * @modules jdk.attach jdk.jfr
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading
 *      -XX:+UnlockExperimentalVMOptions -XX:-UseFastUnorderedTimeStamps TestLibraries
 */

import jdk.test.lib.Asserts;

public class TestLibraries {
    // Agent_OnAttach options format: <operation>, <slot>, <value>.
    // The library argument selects A or B; the options are passed to its Agent_OnAttach.
    //
    // Operation selects the action. The -success/-fail suffix describes the expected startup outcome:
    //
    // Library  Operation                          JVMTI environment action
    // A, B     idle-success                       Leave the retained environment unchanged
    // A        idle-fail                          Leave the retained environment unchanged
    // A, B     create-environment-success         Create and retain in the selected slot
    // A        create-environment-fail            Create and retain in the selected slot
    // A, B     dispose-environment-success        Dispose the retained environment and clear the slot
    // B        create-dispose-environment-success Create and dispose during this attach, without retaining
    //
    // Slot must be 0. Each library has its own independent retained-environment slot,
    // which persists across attaches. Creation requires an empty slot; disposal requires
    // a retained environment. Idle and create-dispose operations do not use the retained
    // slot, but still require 0 in the options.
    //
    // Value supplies the exact return code after the operation completes: 0 for -success,
    // nonzero for -fail. A failure return does not undo the operation's environment action.
    // The attach helper checks the Attach API outcome against this value.
    // Unexpected operation failures return JNI_ERR.
    //
    // All events verified by this test are jdk.NativeAgent events.
    //
    private static final String AGENT_A = JfrAgentTestSupport.library("OnAttachLookupAgentA");
    private static final String AGENT_B = JfrAgentTestSupport.library("OnAttachLookupAgentB");

    public static void main(String[] args) throws Exception {
        testNoEventWithoutEnvironment();
        testEventsAfterStartupFailureWithEnvironment();
        testIndependentEventsAcrossLibraries();
        testDisposalEventsInOverlappingRecordings();
        testEventsAfterInnerRecordingStops();
    }

    // Neither successful nor failed startup without a JVMTI environment produces an event.
    private static void testNoEventWithoutEnvironment() throws Exception {
        try (AgentEventRecording recording = new AgentEventRecording()) {
            attach(AGENT_A, "idle-success, 0, 0");
            attach(AGENT_A, "idle-fail, 0, 71");
            recording.stop().assertNoEvents(AGENT_A);
        }
    }

    // Events after failed startup retain the original options through JVMTI environment disposal, then cease.
    private static void testEventsAfterStartupFailureWithEnvironment() throws Exception {
        try (AgentEventRecording recording = new AgentEventRecording()) {
            attach(AGENT_A, "create-environment-fail, 0, 71");
            assertAgentEvents(recording.stop(), AGENT_A, "create-environment-fail, 0, 71").assertLive();
        }
        try (AgentEventRecording recording = new AgentEventRecording()) {
            attach(AGENT_A, "dispose-environment-success, 0, 0");
            assertAgentEvents(recording.stop(), AGENT_A, "create-environment-fail, 0, 71").assertDisposed();
        }
        AgentEventRecording.assertNoEventsInNewRecording(AGENT_A);
    }

    // Events for each library's instance retain its original options and independent disposal time across repeated attaches.
    private static void testIndependentEventsAcrossLibraries() throws Exception {
        try (AgentEventRecording recording = new AgentEventRecording()) {
            attach(AGENT_A, "create-environment-success, 0, 0");
            attach(AGENT_B, "create-dispose-environment-success, 0, 0");
            var afterInitialAttaches = recording.snapshot();
            assertAgentEvents(afterInitialAttaches, AGENT_A, "create-environment-success, 0, 0").assertLive();
            assertAgentEvents(afterInitialAttaches, AGENT_B, "create-dispose-environment-success, 0, 0").assertDisposed();
            attach(AGENT_A, "idle-success, 0, 0");
            attach(AGENT_B, "idle-success, 0, 0");
            attach(AGENT_B, "idle-success, 0, 0");
            attach(AGENT_A, "dispose-environment-success, 0, 0");
            var afterDisposal = recording.stop();
            assertAgentEvents(afterDisposal, AGENT_A, "create-environment-success, 0, 0").assertDisposed();
            assertAgentEvents(afterDisposal, AGENT_B, "create-dispose-environment-success, 0, 0").assertDisposed();
        }
        AgentEventRecording.assertNoEventsInNewRecording(AGENT_A, AGENT_B);
    }

    // Both overlapping recordings contain one disposal event; a later recording contains no event for the agent.
    private static void testDisposalEventsInOverlappingRecordings() throws Exception {
        try (AgentEventRecording outerRecording = new AgentEventRecording()) {
            attach(AGENT_A, "create-environment-success, 0, 0");
            assertAgentEvents(outerRecording.snapshot(), AGENT_A, "create-environment-success, 0, 0").assertLive();
            try (AgentEventRecording innerRecording = new AgentEventRecording()) {
                assertAgentEvents(innerRecording.snapshot(), AGENT_A, "create-environment-success, 0, 0").assertLive();
                attach(AGENT_A, "dispose-environment-success, 0, 0");
                assertAgentEvents(innerRecording.stop(), AGENT_A, "create-environment-success, 0, 0").assertDisposed();
            }
            assertAgentEvents(outerRecording.stop(), AGENT_A, "create-environment-success, 0, 0").assertDisposed();
        }
        AgentEventRecording.assertNoEventsInNewRecording(AGENT_A);
    }

    // An agent created during the inner recording continues producing events in the outer recording after the inner stops.
    private static void testEventsAfterInnerRecordingStops() throws Exception {
        try (AgentEventRecording outerRecording = new AgentEventRecording()) {
            attach(AGENT_A, "create-environment-success, 0, 0");
            var initial = outerRecording.snapshot();
            assertAgentEvents(initial, AGENT_A, "create-environment-success, 0, 0").assertLive();
            initial.assertNoEvents(AGENT_B);
            int innerEvents;
            try (AgentEventRecording innerRecording = new AgentEventRecording()) {
                attach(AGENT_B, "create-environment-success, 0, 0");
                var innerResults = innerRecording.stop();
                assertAgentEvents(innerResults, AGENT_A, "create-environment-success, 0, 0").assertLive();
                innerEvents = assertAgentEvents(innerResults, AGENT_B, "create-environment-success, 0, 0").assertLive().events().size();
            }
            var live = outerRecording.snapshot();
            assertAgentEvents(live, AGENT_A, "create-environment-success, 0, 0").assertLive();
            int outerEvents = assertAgentEvents(live, AGENT_B, "create-environment-success, 0, 0").assertLive().events().size();
            // B did not exist before the inner recording, so additional events must be from after it stopped.
            Asserts.assertGT(outerEvents, innerEvents, "No new event for B after the inner recording stopped");
            attach(AGENT_B, "dispose-environment-success, 0, 0");
            attach(AGENT_A, "dispose-environment-success, 0, 0");
            var results = outerRecording.stop();
            assertAgentEvents(results, AGENT_A, "create-environment-success, 0, 0").assertDisposed();
            assertAgentEvents(results, AGENT_B, "create-environment-success, 0, 0").assertDisposed();
        }
        AgentEventRecording.assertNoEventsInNewRecording(AGENT_A, AGENT_B);
    }

    private static AgentEventRecording.FilteredList assertAgentEvents(AgentEventRecording.Results results, String library, String options) {
        return results.forLibrary(library).assertOptions(options).assertDynamic(true).assertInitializationTiming();
    }

    private static void attach(String library, String options) throws Exception {
        JfrAgentTestSupport.attach(library, options);
    }
}
