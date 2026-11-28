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
 * @summary Native lookup eligibility follows successful startup, not JVMTI environment lifetime
 * @requires vm.flagless & vm.jvmti & !jdk.static
 * @library /test/lib
 * @modules jdk.attach
 * @run main/othervm/native/timeout=300 TestNativeLookupLifetime
 */

import java.util.List;

import jdk.test.lib.Asserts;

public class TestNativeLookupLifetime {
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
    // Value supplies the exact Agent_OnAttach return code, not a native lookup result.
    // Unexpected operation failures return JNI_ERR.
    private static final String AGENT_A = AgentTestSupport.library("OnAttachLookupAgentA");
    private static final String AGENT_B = AgentTestSupport.library("OnAttachLookupAgentB");

    private static native int firstResolutionAgentA();
    private static native int nextResolutionAgentA();
    private static native int afterDisposalAgentB();
    private static native int afterRepeatedAttachesAgentB();

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testSuccessWithoutEnvironmentEnablesNativeLookup();
        testFailureWithoutEnvironmentDoesNotEnableNativeLookup();
        testFailureWithEnvironmentDoesNotEnableNativeLookup();
        testLaterSuccessEnablesNativeLookup();
        testLaterFailurePreservesNativeLookup();
        testEnvironmentDisposalPreservesNativeLookup();
    }

    // Successful startup enables native lookup even without creating a JVMTI environment.
    private static void testSuccessWithoutEnvironmentEnablesNativeLookup() throws Exception {
        launch("success-without-environment");
    }

    // Failed startup without a JVMTI environment leaves native lookup disabled.
    private static void testFailureWithoutEnvironmentDoesNotEnableNativeLookup() throws Exception {
        launch("failure-without-environment");
    }

    // Retaining a JVMTI environment does not enable native lookup after failed startup.
    private static void testFailureWithEnvironmentDoesNotEnableNativeLookup() throws Exception {
        launch("failure-with-environment");
    }

    // A later successful attach enables native lookup for the library whose earlier startup failed.
    private static void testLaterSuccessEnablesNativeLookup() throws Exception {
        launch("failure-then-success");
    }

    // A later failed attach does not revoke native lookup enabled by an earlier success.
    private static void testLaterFailurePreservesNativeLookup() throws Exception {
        launch("success-then-failure");
    }

    // Both libraries remain searchable after JVMTI environment disposal and repeated attaches.
    private static void testEnvironmentDisposalPreservesNativeLookup() throws Exception {
        launch("environment-disposal");
    }

    // Isolate each operation so an earlier success cannot invalidate a negative lookup check.
    private static void launch(String operation) throws Exception {
        var output = AgentTestSupport.launch(TestNativeLookupLifetime.class, List.of(), operation);
        output.shouldHaveExitValue(0).shouldContain("native lookup operation complete");
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "success-without-environment" -> verifySuccessWithoutEnvironment();
            case "failure-without-environment" -> verifyFailureWithoutEnvironment();
            case "failure-with-environment" -> verifyFailureWithEnvironment();
            case "failure-then-success" -> verifyFailureThenSuccess();
            case "success-then-failure" -> verifySuccessThenFailure();
            case "environment-disposal" -> verifyEnvironmentDisposal();
            default -> throw new AssertionError("Unknown native lookup operation: " + operation);
        }
        System.out.println("native lookup operation complete");
    }

    private static void verifySuccessWithoutEnvironment() throws Exception {
        attach(AGENT_A, "idle-success, 0, 0");
        Asserts.assertEquals(firstResolutionAgentA(), 101);
    }

    private static void verifyFailureWithoutEnvironment() throws Exception {
        attach(AGENT_A, "idle-fail, 0, 71");
        assertNativeLookupUnavailable();
    }

    private static void verifyFailureWithEnvironment() throws Exception {
        attach(AGENT_A, "create-environment-fail, 0, 71");
        assertNativeLookupUnavailable();
        // Successful disposal proves that the failed startup left its environment alive.
        // Do this after the negative lookup, since this attach itself succeeds.
        attach(AGENT_A, "dispose-environment-success, 0, 0");
    }

    private static void verifyFailureThenSuccess() throws Exception {
        attach(AGENT_A, "idle-fail, 0, 71");
        assertNativeLookupUnavailable();
        attach(AGENT_A, "idle-success, 0, 0");
        // Use a different method from the failed resolution above.
        Asserts.assertEquals(nextResolutionAgentA(), 102, "A later success must enable the existing resolver");
    }

    private static void verifySuccessThenFailure() throws Exception {
        attach(AGENT_A, "idle-success, 0, 0");
        Asserts.assertEquals(firstResolutionAgentA(), 101);
        attach(AGENT_A, "idle-fail, 0, 71");
        // Resolve a fresh method rather than reuse the earlier binding.
        Asserts.assertEquals(nextResolutionAgentA(), 102, "A later failure must not revoke lookup eligibility");
    }

    private static void verifyEnvironmentDisposal() throws Exception {
        attach(AGENT_A, "create-environment-success, 0, 0");
        attach(AGENT_B, "create-dispose-environment-success, 0, 0");
        // Agent B has disposed its environment and has not been retried.
        Asserts.assertEquals(afterDisposalAgentB(), 201);

        attach(AGENT_A, "idle-success, 0, 0");
        attach(AGENT_B, "idle-success, 0, 0");
        attach(AGENT_B, "idle-success, 0, 0");
        attach(AGENT_A, "dispose-environment-success, 0, 0");
        // Neither method has been resolved before this point.
        Asserts.assertEquals(nextResolutionAgentA(), 102);
        Asserts.assertEquals(afterRepeatedAttachesAgentB(), 202);
    }

    private static void assertNativeLookupUnavailable() {
        try {
            firstResolutionAgentA();
            throw new AssertionError("Failed startup made agent A searchable");
        } catch (UnsatisfiedLinkError expected) {
        }
    }

    private static void attach(String library, String options) throws Exception {
        AgentTestSupport.attach(library, options);
    }
}
