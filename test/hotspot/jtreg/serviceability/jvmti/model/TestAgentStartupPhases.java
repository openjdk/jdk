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
 * @summary Command-line startup and repeated attach share native lookup and one unload notification
 * @requires vm.flagless & vm.jvmti & !jdk.static
 * @library /test/lib
 * @modules jdk.attach
 * @run main/othervm/native/timeout=300 TestAgentStartupPhases
 */

import java.util.List;

import com.sun.tools.attach.VirtualMachine;

import jdk.test.lib.Asserts;

public class TestAgentStartupPhases {
    private static final String LIBRARY = AgentTestSupport.library("OnLoadAttachAgent");

    // The library takes no options and creates no JVMTI environments.
    // Each fresh native lookup returns the total number of startup invocations:
    // one Agent_OnLoad, followed by two Agent_OnAttach invocations.
    private static native int nativeLookupAfterOnLoad();
    private static native int nativeLookupAfterFirstAttach();
    private static native int nativeLookupAfterRepeatedAttach();

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testNativeLookupAndSingleUnloadAcrossStartupPhases();
    }

    // Command-line startup and two attaches preserve native lookup and produce one Agent_OnUnload invocation.
    private static void testNativeLookupAndSingleUnloadAcrossStartupPhases() throws Exception {
        var output = AgentTestSupport.launch(TestAgentStartupPhases.class,
                                             List.of("-agentpath:" + LIBRARY),
                                             "on-load-then-repeated-attach");
        output.shouldHaveExitValue(0).shouldContain("startup phases operation complete");
        AgentTestSupport.unloadedOnce(output, "OnLoadAttachAgent");
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "on-load-then-repeated-attach" -> verifyOnLoadThenRepeatedAttach();
            default -> throw new AssertionError("Unknown startup phases operation: " + operation);
        }
        System.out.println("startup phases operation complete");
    }

    private static void verifyOnLoadThenRepeatedAttach() throws Exception {
        Asserts.assertEquals(nativeLookupAfterOnLoad(), 1, "Agent_OnLoad must run before main");
        attach();
        Asserts.assertEquals(nativeLookupAfterFirstAttach(), 2,
                "The first attach must use the library initialized by Agent_OnLoad");
        attach();
        // Resolve a new method at each step; a cached JNI binding would not test lookup eligibility.
        Asserts.assertEquals(nativeLookupAfterRepeatedAttach(), 3,
                "The repeated attach must preserve native lookup and the library's startup count");
    }

    private static void attach() throws Exception {
        VirtualMachine vm = VirtualMachine.attach(Long.toString(ProcessHandle.current().pid()));
        try {
            vm.loadAgentPath(LIBRARY);
        } finally {
            vm.detach();
        }
    }
}
