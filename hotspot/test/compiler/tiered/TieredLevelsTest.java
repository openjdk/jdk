/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import compiler.whitebox.CompilerWhiteBoxTest;

/**
 * @test TieredLevelsTest
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @modules java.management
 * @build TieredLevelsTest
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:-UseCounterDecay
 *                   -XX:CompileCommand=compileonly,compiler.whitebox.SimpleTestCaseHelper::*
 *                   TieredLevelsTest
 * @summary Verify that all levels &lt; 'TieredStopAtLevel' can be used
 * @author igor.ignatyev@oracle.com
 */
public class TieredLevelsTest extends CompLevelsTest {
    public static void main(String[] args) throws Exception, Throwable {
        if (CompilerWhiteBoxTest.skipOnTieredCompilation(false)) {
            return;
        }
        CompilerWhiteBoxTest.main(TieredLevelsTest::new, args);
    }

    protected TieredLevelsTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }

    @Override
    protected void test() throws Exception {
        if (skipXcompOSR()) {
          return;
        }
        checkNotCompiled();
        compile();
        checkCompiled();

        int compLevel = getCompLevel();
        if (compLevel > TIERED_STOP_AT_LEVEL) {
            throw new RuntimeException("method.compLevel[" + compLevel
                    + "] > TieredStopAtLevel [" + TIERED_STOP_AT_LEVEL + "]");
        }
        int bci = WHITE_BOX.getMethodEntryBci(method);
        deoptimize();

        for (int testedTier = 1; testedTier <= TIERED_STOP_AT_LEVEL;
                ++testedTier) {
            testAvailableLevel(testedTier, bci);
        }
        for (int testedTier = TIERED_STOP_AT_LEVEL + 1;
                testedTier <= COMP_LEVEL_MAX; ++testedTier) {
            testUnavailableLevel(testedTier, bci);
        }
    }

    @Override
    protected void checkLevel(int expected, int actual) {
        if (expected == COMP_LEVEL_FULL_PROFILE
                && actual == COMP_LEVEL_LIMITED_PROFILE) {
            // for simple method full_profile may be replaced by limited_profile
            if (IS_VERBOSE) {
                System.out.printf("Level check: full profiling was replaced "
                        + "by limited profiling. Expected: %d, actual:%d",
                        expected, actual);
            }
            return;
        }
        super.checkLevel(expected, actual);
    }
}
