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
 * @summary Agents sharing a native library retain distinct identities and independent JVMTI environment lifetimes
 * @requires vm.flagless & vm.hasJFR & vm.jvmti & !jdk.static
 * @library /test/lib ../model
 * @modules jdk.attach jdk.jfr
 * @run main/othervm/native -Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading
 *      -XX:+UnlockExperimentalVMOptions -XX:-UseFastUnorderedTimeStamps TestAgents
 */

public class TestAgents {
    // Agent_OnAttach options format: <operation>, <slot>, <value>.
    //
    // Operation selects the action. The -success/-fail suffix describes the expected startup outcome:
    //
    // check-no-environments-success / check-no-environments-fail:
    //     Check that neither slot holds a JVMTI environment.
    // create-environment-success / create-environment-fail:
    //     Create a JVMTI environment in an empty slot and reset its visit count.
    // visit-environment-success:
    //     Check the next visit number and live phase, then advance the count.
    // dispose-environment-success / dispose-environment-fail:
    //     Dispose the JVMTI environment in the selected slot and clear the slot.
    // create-dispose-environment-success / create-dispose-environment-fail:
    //     Create and dispose a JVMTI environment within this attach call.
    //
    // Slot selects retained JVMTI environment 0 or 1.
    // The slot is unused by check-no-environments and create-dispose-environment,
    // but both operations still require a valid slot number.
    //
    // Value supplies the exact return code, except for visit-environment-success,
    // where it is the expected next visit number.
    // Unexpected operation failures return JNI_ERR.
    //
    // All events verified by this test are jdk.NativeAgent events.
    //
    private static final String LIBRARY = JfrAgentTestSupport.library("OnAttachAgent");

    public static void main(String[] args) throws Exception {
        testNoEventWithoutEnvironment();
        testIndependentEventsForRetainedEnvironments();
        testFirstDisposalPreservesSecondAgentEvents();
        testDisposalEventAfterFailedStartup();
        testDisposalEventAfterSuccessfulStartup();
    }

    // Failed startup without a JVMTI environment produces no event.
    private static void testNoEventWithoutEnvironment() throws Exception {
        recordEvents("check-no-environments-fail, 0, 23").assertNoEvents(LIBRARY);
    }

    // Events retain each instance's original options and distinguish its JVMTI environment lifetime from the other's.
    private static void testIndependentEventsForRetainedEnvironments() throws Exception {
        String first = "create-environment-fail, 0, 23";
        var afterFirstCreation = recordEvents(first);
        assertAgentEvents(afterFirstCreation, first).assertLive();

        // Each visit checks the first environment and the next sequence number.
        var afterFirstVisits = recordEvents("visit-environment-success, 0, 1",
                                            "visit-environment-success, 0, 2",
                                            "visit-environment-success, 0, 3");
        assertAgentEvents(afterFirstVisits, first).assertLive();
        String second = "create-environment-success, 1, 0";
        var afterSecondCreation = recordEvents(second);
        assertAgentEvents(afterSecondCreation, first, second).assertLive();
        // Dispose in reverse order. The failed first attach's environment outlives
        // the successful second attach's environment, despite sharing a library.
        var afterSecondDisposal = recordEvents("dispose-environment-success, 1, 0");
        var eventsAfterSecondDisposal = assertAgentEvents(afterSecondDisposal, first, second);
        eventsAfterSecondDisposal.forOptions(first).assertLive();
        eventsAfterSecondDisposal.forOptions(second).assertDisposed();
        var firstRemaining = recordEvents();
        assertAgentEvents(firstRemaining, first).assertLive();
        var afterFirstDisposal = recordEvents("dispose-environment-success, 0, 0");
        assertAgentEvents(afterFirstDisposal, first).assertDisposed();
        recordEvents("check-no-environments-success, 0, 0").assertNoEvents(LIBRARY);
    }

    // Disposing the older environment leaves the newer one usable and reported under its original options.
    private static void testFirstDisposalPreservesSecondAgentEvents() throws Exception {
        String first = "create-environment-success, 0, 0";
        String second = "create-environment-success, 1, 0";
        assertAgentEvents(recordEvents(first, second), first, second).assertLive();
        var afterFirstDisposal = recordEvents("dispose-environment-success, 0, 0");
        var eventsAfterFirstDisposal = assertAgentEvents(afterFirstDisposal, first, second);
        eventsAfterFirstDisposal.forOptions(first).assertDisposed();
        eventsAfterFirstDisposal.forOptions(second).assertLive();
        var secondRemaining = recordEvents("visit-environment-success, 1, 1");
        assertAgentEvents(secondRemaining, second).assertLive();
        var afterSecondDisposal = recordEvents("dispose-environment-success, 1, 0");
        assertAgentEvents(afterSecondDisposal, second).assertDisposed();
        recordEvents("check-no-environments-success, 0, 0").assertNoEvents(LIBRARY);
    }

    // Disposal during failed startup still produces one event with completed initialization timing.
    private static void testDisposalEventAfterFailedStartup() throws Exception {
        verifyCreateDisposeEnvironment("create-dispose-environment-fail, 0, 23");
    }

    // Disposal during successful startup produces one event and leaves no retained environment.
    private static void testDisposalEventAfterSuccessfulStartup() throws Exception {
        verifyCreateDisposeEnvironment("create-dispose-environment-success, 0, 0");
    }

    private static void verifyCreateDisposeEnvironment(String options) throws Exception {
        assertAgentEvents(recordEvents(options), options).assertDisposed();
        recordEvents("check-no-environments-success, 0, 0").assertNoEvents(LIBRARY);
    }

    private static AgentEventRecording.FilteredList assertAgentEvents(AgentEventRecording.Results results, String... options) {
        return results.forLibrary(LIBRARY).assertOptions(options).assertDynamic(true).assertInitializationTiming();
    }

    private static AgentEventRecording.Results recordEvents(String... options) throws Exception {
        try (AgentEventRecording recording = new AgentEventRecording()) {
            for (String option : options) {
                attach(option);
            }
            return recording.stop();
        }
    }

    private static void attach(String options) throws Exception {
        JfrAgentTestSupport.attach(LIBRARY, options);
    }
}
