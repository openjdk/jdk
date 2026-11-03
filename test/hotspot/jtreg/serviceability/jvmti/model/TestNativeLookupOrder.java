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
 * @summary Native lookup follows first successful startup order, not library loading or repeated requests
 * @requires vm.flagless & vm.jvmti & !jdk.static
 * @library /test/lib
 * @modules jdk.attach
 * @run main/othervm/native/timeout=300 TestNativeLookupOrder
 */

import java.util.List;

import jdk.test.lib.Asserts;

public class TestNativeLookupOrder {
    // Agent_OnAttach options format: <operation>, <slot>, <value>.
    //
    // Operation selects the action. The -success/-fail suffix describes the expected startup outcome:
    //
    // Library  Operation      JVMTI environment action
    // A, B     idle-success   No change
    // A        idle-fail      No change
    //
    // Slot is 0 and is unused by these idle operations.
    //
    // Value supplies the exact Agent_OnAttach return code, not a native lookup result.
    // Unexpected operation failures return JNI_ERR.
    private static final String AGENT_A = AgentTestSupport.library("OnAttachLookupAgentA");
    private static final String AGENT_B = AgentTestSupport.library("OnAttachLookupAgentB");

    // Both libraries export all three native methods:
    // OnAttachLookupAgentA returns 101; OnAttachLookupAgentB returns 201.
    // The result identifies which library supplied the resolved implementation.
    private static native int afterAgentBSuccess();
    private static native int afterAgentASuccess();
    private static native int afterRepeatedAttach();

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testFirstSuccessfulStartupDeterminesNativeLookupOrder();
    }

    // First successful startup determines native lookup order; later startups preserve it.
    private static void testFirstSuccessfulStartupDeterminesNativeLookupOrder() throws Exception {
        var output = AgentTestSupport.launch(TestNativeLookupOrder.class, List.of(), "first-successful-startup-order");
        output.shouldHaveExitValue(0).shouldContain("native lookup order operation complete");
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "first-successful-startup-order" -> verifyFirstSuccessfulStartupOrder();
            default -> throw new AssertionError("Unknown native lookup order operation: " + operation);
        }
        System.out.println("native lookup order operation complete");
    }

    // Keep the sequence in one JVM and resolve a fresh native method at each step.
    private static void verifyFirstSuccessfulStartupOrder() throws Exception {
        attach(AGENT_A, "idle-fail, 0, 71");
        attach(AGENT_B, "idle-success, 0, 0");
        Asserts.assertEquals(afterAgentBSuccess(), 201, "Failed startup must not take precedence");
        attach(AGENT_A, "idle-success, 0, 0");
        Asserts.assertEquals(afterAgentASuccess(), 201, "Successful startup order must determine precedence");
        attach(AGENT_B, "idle-success, 0, 0");
        Asserts.assertEquals(afterRepeatedAttach(), 201, "Repeated startup must not reorder an eligible resolver");
    }

    private static void attach(String library, String options) throws Exception {
        AgentTestSupport.attach(library, options);
    }
}
