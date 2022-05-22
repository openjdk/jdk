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
 * @bug 8276044
 * @library / /test/lib
 * @summary Testing that a replay file is dumped for C1 and C2 when using the DumpReplay compile command option.
 * @requires vm.flightRecorder != true & vm.compMode != "Xint" & vm.compMode != "Xcomp" & vm.debug == true
 *           & vm.compiler1.enabled & vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @build sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+TieredCompilation
 *      compiler.ciReplay.TestDumpReplayCommandLine
 */

package compiler.ciReplay;

import jdk.test.lib.Asserts;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestDumpReplayCommandLine extends DumpReplayBase {

    public static void main(String[] args) {
        new TestDumpReplayCommandLine().runTest(TIERED_ENABLED_VM_OPTION);
    }

    @Override
    public void testAction() {
        List<File> replayFiles = getReplayFiles();
        Asserts.assertEQ(2, replayFiles.size(), "should find a C1 and a C2 replay file");
        String replayFile1 = replayFiles.get(0).getName();
        String replayFile2 = replayFiles.get(1).getName();
        int compileId1 = getCompileIdFromFile(replayFile1);
        int compileId2 = getCompileIdFromFile(replayFile2);
        int compLevel1 = getCompLevelFromReplay(replayFile1);
        int compLevel2 = getCompLevelFromReplay(replayFile2);
        Asserts.assertEQ(compileId1 < compileId2 ? compLevel1 : compLevel2, 3, "Must be C1 replay file");
        Asserts.assertEQ(compileId1 < compileId2 ? compLevel2 : compLevel1, 4, "Must be C2 replay file");
    }

    @Override
    public String getTestClass() {
        return TestDumpReplayCommandFoo.class.getName();
    }
}

class TestDumpReplayCommandFoo {
    public static int iFld;

    public static void main(String[] args) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }

    public static void test() {
        for (int i = 0; i < 1; i++) {
            iFld = 3;
        }
    }
}
