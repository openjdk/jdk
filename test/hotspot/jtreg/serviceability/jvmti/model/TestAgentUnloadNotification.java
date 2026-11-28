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
 * @summary Agent_OnUnload is invoked once per library after startup, regardless of its result or JVMTI environment lifetime
 * @requires vm.flagless & vm.jvmti & !jdk.static
 * @library /test/lib
 * @modules jdk.attach
 * @run main/othervm/native/timeout=300 TestAgentUnloadNotification
 */

import java.util.List;

public class TestAgentUnloadNotification {
    // Agent_OnAttach options format: <operation>, <slot>, <value>.
    //
    // Operation selects the action. The -success/-fail suffix describes the expected startup outcome:
    //
    // Library  Operation                          JVMTI environment action
    // A, B     idle-success                       No change
    // A        idle-fail                          No change
    // A        create-environment-success         Create and retain
    // A        create-environment-fail            Create and retain
    // A        dispose-environment-success        Dispose the retained environment
    // B        create-dispose-environment-success Create and dispose during this attach
    //
    // Slot must be 0; each library has its own retained-environment slot.
    // Idle and create-dispose operations do not use the retained slot, but still require 0.
    //
    // Value supplies the exact Agent_OnAttach return code.
    // Unexpected operation failures return JNI_ERR.
    private static final String AGENT_A = AgentTestSupport.library("OnAttachLookupAgentA");
    private static final String AGENT_B = AgentTestSupport.library("OnAttachLookupAgentB");

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testOnUnloadOnceAfterStartupSuccessWithoutEnvironment();
        testOnUnloadOnceAfterStartupFailureWithoutEnvironment();
        testOnUnloadOnceAfterStartupFailureWithEnvironment();
        testOnUnloadOnceAfterTwoAttachesFailureThenSuccess();
        testOnUnloadOnceAfterTwoAttachesSuccessThenFailure();
        testOnUnloadOnceAfterEnvironmentDisposalAndRepeatedAttach();
    }

    // Successful startup without a JVMTI environment still requires one unload notification.
    private static void testOnUnloadOnceAfterStartupSuccessWithoutEnvironment() throws Exception {
        launchAndCheckUnload("success-without-environment", "OnAttachLookupAgentA");
    }

    // Invoking startup is enough for unload notification even when it returns an error.
    private static void testOnUnloadOnceAfterStartupFailureWithoutEnvironment() throws Exception {
        launchAndCheckUnload("failure-without-environment", "OnAttachLookupAgentA");
    }

    // Failed startup with a retained JVMTI environment still requires one unload notification.
    private static void testOnUnloadOnceAfterStartupFailureWithEnvironment() throws Exception {
        launchAndCheckUnload("failure-with-environment", "OnAttachLookupAgentA");
    }

    // Two attaches to the same library, failing then succeeding, yield one Agent_OnUnload invocation at shutdown.
    private static void testOnUnloadOnceAfterTwoAttachesFailureThenSuccess() throws Exception {
        launchAndCheckUnload("failure-then-success", "OnAttachLookupAgentA");
    }

    // Two attaches to the same library, succeeding then failing, yield one Agent_OnUnload invocation at shutdown.
    private static void testOnUnloadOnceAfterTwoAttachesSuccessThenFailure() throws Exception {
        launchAndCheckUnload("success-then-failure", "OnAttachLookupAgentA");
    }

    // Disposal and repeated attaches leave exactly one unload notification for each library.
    private static void testOnUnloadOnceAfterEnvironmentDisposalAndRepeatedAttach() throws Exception {
        launchAndCheckUnload("environment-disposal", "OnAttachLookupAgentA", "OnAttachLookupAgentB");
    }

    // Check shutdown notifications only after the child has exited.
    private static void launchAndCheckUnload(String operation, String... libraries) throws Exception {
        var output = AgentTestSupport.launch(TestAgentUnloadNotification.class, List.of(), operation);
        output.shouldHaveExitValue(0).shouldContain("unload operation complete");
        for (String library : libraries) {
            AgentTestSupport.unloadedOnce(output, library);
        }
        if (!List.of(libraries).contains("OnAttachLookupAgentB")) {
            output.shouldNotContain("unloaded:OnAttachLookupAgentB");
        }
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "success-without-environment" -> runSuccessWithoutEnvironment();
            case "failure-without-environment" -> runFailureWithoutEnvironment();
            case "failure-with-environment" -> runFailureWithEnvironment();
            case "failure-then-success" -> runFailureThenSuccess();
            case "success-then-failure" -> runSuccessThenFailure();
            case "environment-disposal" -> runEnvironmentDisposal();
            default -> throw new AssertionError("Unknown unload operation: " + operation);
        }
        System.out.println("unload operation complete");
    }

    private static void runSuccessWithoutEnvironment() throws Exception {
        attach(AGENT_A, "idle-success, 0, 0");
    }

    private static void runFailureWithoutEnvironment() throws Exception {
        attach(AGENT_A, "idle-fail, 0, 71");
    }

    private static void runFailureWithEnvironment() throws Exception {
        // Leave the environment retained, with no later successful startup.
        attach(AGENT_A, "create-environment-fail, 0, 71");
    }

    private static void runFailureThenSuccess() throws Exception {
        attach(AGENT_A, "idle-fail, 0, 71");
        attach(AGENT_A, "idle-success, 0, 0");
    }

    private static void runSuccessThenFailure() throws Exception {
        attach(AGENT_A, "idle-success, 0, 0");
        attach(AGENT_A, "idle-fail, 0, 71");
    }

    private static void runEnvironmentDisposal() throws Exception {
        attach(AGENT_A, "create-environment-success, 0, 0");
        attach(AGENT_B, "create-dispose-environment-success, 0, 0");
        attach(AGENT_A, "idle-success, 0, 0");
        attach(AGENT_B, "idle-success, 0, 0");
        attach(AGENT_B, "idle-success, 0, 0");
        attach(AGENT_A, "dispose-environment-success, 0, 0");
    }

    private static void attach(String library, String options) throws Exception {
        AgentTestSupport.attach(library, options);
    }
}
