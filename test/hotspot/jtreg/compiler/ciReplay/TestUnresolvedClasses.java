/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8262912
 * @library / /test/lib
 * @summary Test class resolution based on whitelist created by ciInstanceKlass entries in replay file.
 * @requires vm.flightRecorder != true & vm.compMode != "Xint" & vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.ciReplay.TestUnresolvedClasses
 */

package compiler.ciReplay;

import jdk.test.lib.Asserts;

import java.util.List;

public class TestUnresolvedClasses extends CiReplayBase {
    private static final String LOG_FILE = "hotspot.log";
    private final PrintIdeal printIdeal = new PrintIdeal(LOG_FILE);

    public static void main(String[] args) {
        new TestUnresolvedClasses().runTest(false, TIERED_DISABLED_VM_OPTION);
    }

    @Override
    public String getTestClass() {
        return Test.class.getName();
    }

    @Override
    public void testAction() {
        List<String> vmFlags = printIdeal.vmFlags();
        vmFlags.add("-XX:CompileCommand=dontinline,*Test::dontInline");
        String[] commandLine = vmFlags.toArray(new String[0]);
        positiveTest(commandLine);
        printIdeal.parse();
        // Should find CallStaticJava node for dontInline() as f.bar() is resolved and parsing completes.
        checkLogFile(true);

        ReplayFile replayFile = new ReplayFile(getReplayFileName());
        // Remove ciInstanceKlass entry for Foo in replay file.
        replayFile.removeLineStartingWith("ciInstanceKlass compiler/ciReplay/TestUnresolvedClasses$Foo");

        positiveTest(commandLine);
        printIdeal.parse();
        // No ciInstanceKlass entry for Foo is found in the replay. Replay compilation simulates that Foo is unresolved.
        // Therefore, C2 cannot resolve f.bar() at parsing time. It emits an UCT to resolve Foo and stops parsing.
        // The call to dontInline() will not be parsed and thus we should not find a CallStaticJava node for it.
        checkLogFile(false);
        remove(LOG_FILE);
    }

    // Parse <ideal> entry in hotspot.log file and try to find the call for dontInline().
    private void checkLogFile(boolean shouldMatch) {
        String toMatch = "Test::dontInline";
        String line = printIdeal.find(toMatch);
        if (shouldMatch) {
            Asserts.assertTrue(line.contains("CallStaticJava"), "must be CallStaticJava node");
        } else {
            Asserts.assertTrue(line.isEmpty(), "Should not have found " + toMatch);
        }
    }

    private static class Test {
        static Foo f = new Foo();

        public static void main(String[] args) {
            for (int i = 0; i < 10000; i++) {
                test();
            }
        }

        public static void test() {
            f.bar();
            // At replay compilation: Should emit UCT for f.bar() because class Foo is unloaded. Parsing stops here.
            // dontInline() is not parsed anymore.
            dontInline();
        }

        // Not inlined
        public static void dontInline() {
        }
    }

    private static class Foo {
        public int bar() {
            return 3;
        }
    }
}
