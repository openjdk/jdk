/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.oracle;

import java.util.ArrayList;
import java.util.List;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import compiler.lib.ir_framework.CompilePhase;

/**
 * @test
 * @bug 8372097
 * @summary Checks that -XX:CompileCommand=PhasePrintLevel,... interacts with
 *          -XX:PrintPhaseLevel as expected.
 * @library /test/lib /
 * @requires vm.debug & vm.compiler2.enabled & vm.flagless
 * @run driver compiler.oracle.TestPhasePrintLevel
 */

public class TestPhasePrintLevel {

    static final String level1Phase = CompilePhase.FINAL_CODE.getName();
    static final String level2Phase = CompilePhase.GLOBAL_CODE_MOTION.getName();

    public static void main(String[] args) throws Exception {
        // Test flag level < 0: nothing should be printed regardless of the compile command level.
        test(-1, -1, null,        level1Phase);
        test(-1,  0, null,        level1Phase);
        test(-1,  1, null,        level1Phase);

        // Test flag level = 0: the compile command level should determine what is printed.
        test(0,  -1, null,        level1Phase);
        test(0,   0, null,        level1Phase);
        test(0,   1, level1Phase, null);
        test(0,   2, level2Phase, null);

        // Test flag level > 0: the compile command level should take precedence.
        test(1,  -1, null,        level1Phase);
        test(1,   0, null,        level1Phase);
        test(1,   1, level1Phase, null);
        test(2,   1, level1Phase, level2Phase);
        test(1,   2, level2Phase, null);
    }

    static void test(int flagLevel, int compileCommandLevel, String expectedPhase, String unexpectedPhase) throws Exception {
        List<String> options = new ArrayList<String>();
        options.add("-Xbatch");
        options.add("-XX:CompileOnly=" + getTestName());
        options.add("-XX:PrintPhaseLevel=" + flagLevel);
        options.add("-XX:CompileCommand=PhasePrintLevel," + getTestName() + "," + compileCommandLevel);
        options.add(getTestClass());
        OutputAnalyzer oa = ProcessTools.executeTestJava(options);
        oa.shouldHaveExitValue(0)
            .shouldContain("CompileCommand: PhasePrintLevel compiler/oracle/TestPhasePrintLevel$TestMain.test intx PhasePrintLevel = " + compileCommandLevel)
            .shouldNotContain("CompileCommand: An error occurred during parsing")
            .shouldNotContain("# A fatal error has been detected by the Java Runtime Environment");
        if (expectedPhase != null) {
            oa.shouldContain(expectedPhase);
        }
        if (unexpectedPhase != null) {
            oa.shouldNotContain(unexpectedPhase);
        }
    }

    static String getTestClass() {
        return TestMain.class.getName();
    }

    static String getTestName() {
        return getTestClass() + "::test";
    }

    static class TestMain {
        public static void main(String[] args) {
            for (int i = 0; i < 10_000; i++) {
                test(i);
            }
        }

        static void test(int i) {
            if ((i % 1000) == 0) {
                System.out.println("Hello World!");
            }
        }
    }
}
