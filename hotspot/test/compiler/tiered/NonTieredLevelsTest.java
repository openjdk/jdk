/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.IntPredicate;

/**
 * @test NonTieredLevelsTest
 * @library /testlibrary /testlibrary/whitebox /compiler/whitebox
 * @build NonTieredLevelsTest
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:-TieredCompilation
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:CompileCommand=compileonly,TestCase$Helper::*
 *                   NonTieredLevelsTest
 * @summary Verify that only one level can be used
 * @author igor.ignatyev@oracle.com
 */
public class NonTieredLevelsTest extends CompLevelsTest {
    private static final int AVAILABLE_COMP_LEVEL;
    private static final IntPredicate IS_AVAILABLE_COMPLEVEL;
    static {
        String vmName = System.getProperty("java.vm.name");
        if (vmName.endsWith(" Server VM")) {
            AVAILABLE_COMP_LEVEL = COMP_LEVEL_FULL_OPTIMIZATION;
            IS_AVAILABLE_COMPLEVEL = x -> x == COMP_LEVEL_FULL_OPTIMIZATION;
        } else if (vmName.endsWith(" Client VM")
                || vmName.endsWith(" Minimal VM")) {
            AVAILABLE_COMP_LEVEL = COMP_LEVEL_SIMPLE;
            IS_AVAILABLE_COMPLEVEL = x -> x >= COMP_LEVEL_SIMPLE
                    && x <= COMP_LEVEL_FULL_PROFILE;
        } else {
            throw new RuntimeException("Unknown VM: " + vmName);
        }

    }
    public static void main(String[] args) throws Exception {
        if (TIERED_COMPILATION) {
            System.err.println("Test isn't applicable w/ enabled "
                    + "TieredCompilation. Skip test.");
            return;
        }
        for (TestCase test : TestCase.values()) {
            new NonTieredLevelsTest(test).runTest();
        }
    }

    private NonTieredLevelsTest(TestCase testCase) {
        super(testCase);
        // to prevent inlining of #method
        WHITE_BOX.testSetDontInlineMethod(method, true);
    }

    @Override
    protected void test() throws Exception {
        checkNotCompiled();
        compile();
        checkCompiled();

        int compLevel = getCompLevel();
        checkLevel(AVAILABLE_COMP_LEVEL, compLevel);
        int bci = WHITE_BOX.getMethodEntryBci(method);
        deoptimize();
        if (!testCase.isOsr) {
            for (int level = 1; level <= COMP_LEVEL_MAX; ++level) {
                if (IS_AVAILABLE_COMPLEVEL.test(level)) {
                    testAvailableLevel(level, bci);
                } else {
                    testUnavailableLevel(level, bci);
                }
            }
        } else {
            System.out.println("skip other levels testing in OSR");
            testAvailableLevel(AVAILABLE_COMP_LEVEL, bci);
        }
    }
}
