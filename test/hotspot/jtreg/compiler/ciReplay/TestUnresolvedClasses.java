/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.ciReplay.TestUnresolvedClasses
 */

package compiler.ciReplay;

import jdk.test.lib.Asserts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public class TestUnresolvedClasses extends CiReplayBase {
    private static final String LOG_FILE = "hotspot.log";
    private static final String[] COMMAND_LINE = new String[] {"-XX:LogFile='" + LOG_FILE + "'", "-XX:+LogCompilation", "-XX:+PrintIdeal",
                                                               "-XX:CompileCommand=dontinline,compiler.ciReplay.Test::dontInline"};
    public static void main(String[] args) {
        new TestUnresolvedClasses().runTest(false, TIERED_DISABLED_VM_OPTION);
    }

    @Override
    public String getTestClass() {
        return Test.class.getName();
    }

    @Override
    public void testAction() {
        positiveTest(COMMAND_LINE);
        // Should find CallStaticJava node for dontInline() as f.bar() is resolved and parsing completes.
        checkLogFile(true);

        // Remove ciInstanceKlass entry for Foo in replay file.
        try {
            Path replayFilePath = Paths.get(REPLAY_FILE_NAME);
            List<String> replayContent = Files.readAllLines(replayFilePath);
            List<String> newReplayContent = new ArrayList<>();
            boolean foundFoo = false;
            for (String line : replayContent) {
                if (!line.startsWith("ciInstanceKlass compiler/ciReplay/Foo")) {
                    newReplayContent.add(line);
                } else {
                    foundFoo = true;
                }
            }
            Asserts.assertTrue(foundFoo, "Did not find ciInstanceKlass compiler/ciReplay/Foo entry");
            Files.write(replayFilePath, newReplayContent, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ioe) {
            throw new Error("Failed to read/write replay data: " + ioe, ioe);
        }

        positiveTest(COMMAND_LINE);
        // No ciInstanceKlass entry for Foo is found in the replay. Replay compilation simulates that Foo is unresolved.
        // Therefore, C2 cannot resolve f.bar() at parsing time. It emits an UCT to resolve Foo and stops parsing.
        // The call to dontInline() will not be parsed and thus we should not find a CallStaticJava node for it.
        checkLogFile(false);
        remove(LOG_FILE);
    }

    // Parse <ideal> entry in hotspot.log file and try to find the call for dontInline().
    private void checkLogFile(boolean shouldMatch) {
        String toMatch = "Test::dontInline";
        try (var br = Files.newBufferedReader(Paths.get(LOG_FILE))) {
            String line;
            boolean printIdealLine = false;
            while ((line = br.readLine()) != null) {
                if (printIdealLine) {
                    if (line.startsWith("</ideal")) {
                        break;
                    }
                    if (line.contains(toMatch)) {
                        Asserts.assertTrue(line.contains("CallStaticJava"), "must be CallStaticJava node");
                        Asserts.assertTrue(shouldMatch, "Should not have found " + toMatch);
                        return;
                    }
                } else {
                    printIdealLine = line.startsWith("<ideal");
                }
            }
        } catch (IOException e) {
            throw new Error("Failed to read " + LOG_FILE + " data: " + e, e);
        }
        Asserts.assertFalse(shouldMatch, "Should have found " + toMatch);
    }
}

class Test {
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

class Foo {
    public int bar() {
        return 3;
    }
}
