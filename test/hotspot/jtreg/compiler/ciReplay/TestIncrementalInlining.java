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
 * @bug 8254108
 * @library / /test/lib
 * @summary Testing of ciReplay with incremental inlining.
 * @requires vm.flightRecorder != true & vm.compMode != "Xint" & vm.compMode != "Xcomp" & vm.debug == true & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *      compiler.ciReplay.TestIncrementalInlining
 */

package compiler.ciReplay;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestIncrementalInlining extends InliningBase {

    private List<InlineEntry> inlineesNormal;
    private List<InlineEntry> inlineesReplay;
    public static void main(String[] args) {
        new TestIncrementalInlining();
    }

    TestIncrementalInlining() {
        super(IncrementalInliningTest.class);
        // Enable Whitebox access for test VM.
        commandLineNormal.add("-Dtest.jdk=" + Utils.TEST_JDK);
        commandLineNormal.add("-cp");
        commandLineNormal.add(Utils.TEST_CLASS_PATH);
        commandLineNormal.add("-Xbootclasspath/a:.");
        commandLineNormal.add("-XX:+UnlockDiagnosticVMOptions");
        commandLineNormal.add("-XX:+WhiteBoxAPI");
        commandLineNormal.add("-XX:MaxInlineLevel=2");
        commandLineNormal.add("-XX:-AlwaysIncrementalInline");
        commandLineNormal.add("-XX:-StressIncrementalInlining");
        runTest();
    }

    @Override
    public void testAction() {
        positiveTest(commandLineReplay);
        inlineesNormal = parseLogFile(LOG_FILE_NORMAL, getTestClass() + " " + "test", "compile_id='" + getCompileIdFromFile(getReplayFileName()), 5);
        verify(true);

        // Incremental inlining is supported in version 2+
        // Test replay file version 1.
        removeIncrementalInlineInfo();
        setNewVersionInReplayFile(1);
        positiveTest(commandLineReplay);
        verify(false);

        // Test replay file without version.
        removeVersionFromReplayFile();
        positiveTest(commandLineReplay);
        verify(false);
    }

    private void verify(boolean isNewFormat) {
        inlineesReplay = parseLogFile(LOG_FILE_REPLAY, getTestClass() + " " + "test", "test ()V", 5);
        verifyLists(inlineesNormal, inlineesReplay, 5);
        checkInlining(isNewFormat);
    }

    // Check if inlining is done correctly in ciReplay.
    private void checkInlining(boolean isNewFormat) {
        String klass = getTestClass();
        Asserts.assertTrue(inlineesNormal.get(0).compare(klass, "level0", inlineesNormal.get(0).isForcedInline()));
        Asserts.assertTrue(inlineesReplay.get(0).compare(klass, "level0", inlineesReplay.get(0).isForcedByReplay()));
        Asserts.assertTrue(inlineesNormal.get(1).compare(klass, "level1", inlineesNormal.get(1).isNormalInline()));
        Asserts.assertTrue(inlineesReplay.get(1).compare(klass, "level1", inlineesReplay.get(1).isForcedByReplay()));
        Asserts.assertTrue(inlineesNormal.get(2).compare(klass, "level2", inlineesNormal.get(2).isForcedInline()));
        Asserts.assertTrue(inlineesReplay.get(2).compare(klass, "level2", inlineesReplay.get(2).isForcedByReplay()));
        Asserts.assertTrue(inlineesNormal.get(3).compare(klass, "late", inlineesNormal.get(3).isForcedInline()));
        Asserts.assertTrue(inlineesReplay.get(3).compare(klass, "late", isNewFormat ?
                inlineesReplay.get(3).isForcedIncrementalInlineByReplay()
                : inlineesReplay.get(3).isForcedByReplay()));
        Asserts.assertTrue(inlineesNormal.get(4).compare(klass, "level4", inlineesNormal.get(4).isTooDeep()));
        Asserts.assertTrue(inlineesReplay.get(4).compare(klass, "level4", inlineesReplay.get(4).isDisallowedByReplay()));
    }

    private void removeIncrementalInlineInfo() {
        try {
            Path replayFilePath = Paths.get(getReplayFileName());
            List<String> replayContent = Files.readAllLines(replayFilePath);
            for (int i = 0; i < replayContent.size(); i++) {
                String line = replayContent.get(i);
                if (line.startsWith("compile ")) {
                    int lastIndex = 0;
                    StringBuilder newLine = new StringBuilder();
                    Pattern p = Pattern.compile("(\\d (-?\\d)) \\d compiler");
                    Matcher m = p.matcher(line);
                    boolean firstMatch = true;
                    while (m.find()) {
                        newLine.append(line, lastIndex, m.start())
                              .append(m.group(1))
                              .append(" compiler");
                        lastIndex = m.end();
                        String bci = m.group(2);
                        Asserts.assertTrue(firstMatch ? bci.equals("-1") : bci.equals("0"), "only root has -1");
                        firstMatch = false;
                    }
                    Asserts.assertLessThan(lastIndex, line.length(), "not reached end of line, yet");
                    newLine.append(line, lastIndex, line.length());
                    replayContent.set(i, newLine.toString());
                }
            }
            Files.write(replayFilePath, replayContent, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ioe) {
            throw new Error("Failed to read/write replay data: " + ioe, ioe);
        }
    }
}

class IncrementalInliningTest {
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private static String s;

    public static void main(String[] args) throws NoSuchMethodException {
        WB.testSetForceInlineMethod(IncrementalInliningTest.class.getDeclaredMethod("level0"), true);
        WB.testSetForceInlineMethod(IncrementalInliningTest.class.getDeclaredMethod("level2"), true);
        WB.testSetForceInlineMethod(IncrementalInliningTest.class.getDeclaredMethod("late"), true);
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    private static void test() {
        level0();
    }

    public static void level0() {
        level1();
    }

    public static void level1() {
        level2();
    }

    public static void level2() {
        late();
    }

    // Reached max inline level but forced to be inlined -> inline late.
    public static void late() {
        level4();
    }

    // Reached max inline level and not forced to be inlined -> no inline.
    public static void level4() {
        s = "HelloWorld";
    }

}
