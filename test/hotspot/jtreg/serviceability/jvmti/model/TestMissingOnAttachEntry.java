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
 * @summary Missing Agent_OnAttach preserves load warnings without enabling native lookup or unload notification
 * @requires vm.flagless & vm.jvmti & !jdk.static
 * @library /test/lib
 * @modules jdk.attach
 * @run main/othervm/native/timeout=300 TestMissingOnAttachEntry
 */

import java.util.List;

import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.VirtualMachine;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;

public class TestMissingOnAttachEntry {
    private static final String LIBRARY = AgentTestSupport.library("OnLoadAgent");
    private static final String NATIVE_AGENT_WARNING = "WARNING: A JVM TI agent has been loaded dynamically";

    private static native int nativeLookupProbe();

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            runChild(args[0]);
            return;
        }
        testMissingOnAttachDisablesNativeLookupAndUnload();
        testMissingOnAttachWarnsOnce();
        testExplicitOptInSuppressesWarning();
        testOnLoadEnablesNativeLookupAndUnload();
    }

    // Missing Agent_OnAttach enables neither native lookup nor an Agent_OnUnload notification.
    private static void testMissingOnAttachDisablesNativeLookupAndUnload() throws Exception {
        var output = AgentTestSupport.launch(TestMissingOnAttachEntry.class, List.of(), "native-lookup-disabled-after-missing-on-attach");
        output.shouldHaveExitValue(0).shouldContain("attach entry operation complete").shouldNotContain("unloaded:OnLoadAgent");
    }

    // The first acquisition warns even without Agent_OnAttach; repeating the failed attach does not warn again.
    private static void testMissingOnAttachWarnsOnce() throws Exception {
        var output = AgentTestSupport.launchWithoutAgentLoadingOptIn(TestMissingOnAttachEntry.class, List.of(), "repeat-missing-on-attach");
        assertWarningCount(output, 1);
        output.stderrShouldContain(NATIVE_AGENT_WARNING + " (" + LIBRARY + ")");
    }

    // Explicit opt-in suppresses the warning for both failed attach attempts.
    private static void testExplicitOptInSuppressesWarning() throws Exception {
        var output = AgentTestSupport.launch(TestMissingOnAttachEntry.class, List.of(), "repeat-missing-on-attach");
        assertWarningCount(output, 0);
    }

    // Command-line startup proves that the same library supports native lookup and unload notification.
    private static void testOnLoadEnablesNativeLookupAndUnload() throws Exception {
        var output = AgentTestSupport.launch(TestMissingOnAttachEntry.class, List.of("-agentpath:" + LIBRARY), "native-lookup-enabled-after-on-load");
        output.shouldHaveExitValue(0).shouldContain("attach entry operation complete");
        AgentTestSupport.unloadedOnce(output, "OnLoadAgent");
    }

    private static void assertWarningCount(OutputAnalyzer output, long expected) {
        output.shouldHaveExitValue(0).shouldContain("attach entry operation complete").shouldNotContain("unloaded:OnLoadAgent");
        long count = output.getStderr().lines().filter(line -> line.startsWith(NATIVE_AGENT_WARNING)).count();
        Asserts.assertEquals(count, expected, "Native-agent warning count\n" + output.getOutput());
    }

    private static void runChild(String operation) throws Exception {
        switch (operation) {
            case "native-lookup-disabled-after-missing-on-attach" -> verifyNativeLookupDisabledAfterMissingOnAttach();
            case "repeat-missing-on-attach" -> {
                verifyNativeLookupDisabledAfterMissingOnAttach();
                verifyNativeLookupDisabledAfterMissingOnAttach();
            }
            case "native-lookup-enabled-after-on-load" -> verifyNativeLookupEnabledAfterOnLoad();
            default -> throw new AssertionError("Unknown attach entry operation: " + operation);
        }
        System.out.println("attach entry operation complete");
    }

    private static void verifyNativeLookupDisabledAfterMissingOnAttach() throws Exception {
        VirtualMachine vm = VirtualMachine.attach(Long.toString(ProcessHandle.current().pid()));
        try {
            vm.loadAgentPath(LIBRARY);
            throw new AssertionError("Library without Agent_OnAttach was accepted");
        } catch (AgentLoadException expected) {
            Asserts.assertTrue(expected.getMessage().contains("Agent_OnAttach is not available in " + LIBRARY),
                    expected.toString());
        } finally {
            vm.detach();
        }
        try {
            nativeLookupProbe();
            throw new AssertionError("Library became searchable without invoking startup");
        } catch (UnsatisfiedLinkError expected) {
        }
    }

    private static void verifyNativeLookupEnabledAfterOnLoad() {
        Asserts.assertEquals(nativeLookupProbe(), 301);
    }
}
