/* Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.debug;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Asserts;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/*
 * @test
 * @key stress randomness
 * @requires vm.debug == true & vm.compiler2.enabled & vm.flagless
 * @summary Tests that stress compilations with the N different seeds yield different
 *          IGVN, CCP, macro elimination, and macro expansion traces.
 * @library /test/lib /
 * @run driver compiler.debug.TestStressDistinctSeed
 */

public class TestStressDistinctSeed {

    private static int counter = 0;

    static String phaseTrace(String stressOption, String traceOption,
            int stressSeed) throws Exception {
        String className = TestStressDistinctSeed.class.getName();
        String[] procArgs = {
                "-Xcomp", "-XX:-TieredCompilation", "-XX:-Inline", "-XX:+CICountNative",
                "-XX:CompileOnly=" + className + "::sum", "-XX:" + traceOption,
                "-XX:+" + stressOption, "-XX:StressSeed=" + stressSeed,
                className, "5" };
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);
        return out.getStdout();
    }

    static String igvnTrace(int stressSeed) throws Exception {
        return phaseTrace("StressIGVN", "+TraceIterativeGVN", stressSeed);
    }

    static String ccpTrace(int stressSeed) throws Exception {
        return phaseTrace("StressCCP", "+TracePhaseCCP", stressSeed);
    }

    static String macroExpansionTrace(int stressSeed) throws Exception {
        return phaseTrace("StressMacroExpansion",
                "CompileCommand=PrintIdealPhase,*::*,AFTER_MACRO_EXPANSION_STEP",
                stressSeed);
    }

    static String macroEliminationTrace(int stressSeed) throws Exception {
        return phaseTrace("StressMacroElimination",
                "CompileCommand=PrintIdealPhase,*::*,AFTER_MACRO_ELIMINATION_STEP",
                stressSeed);
    }

    static void sum(int n) {
        int[] arr1 = new int[n];
        for (int i = 0; i < n; i++) {
            synchronized (TestStressDistinctSeed.class) {
                counter += i;
                arr1[i] = counter;
            }
        }
        System.out.println(counter);
    }

    public static void main(String[] args) throws Exception {
        Set<String> igvnTraceSet = new HashSet<>();
        Set<String> ccpTraceSet = new HashSet<>();
        Set<String> macroExpansionTraceSet = new HashSet<>();
        Set<String> macroEliminationTraceSet = new HashSet<>();
        String igvnTraceOutput, ccpTraceOutput, macroExpansionTraceOutput, macroEliminationTraceOutput;
        if (args.length == 0) {
            for (int s = 0; s < 5; s++) {
                igvnTraceOutput = igvnTrace(s);
                ccpTraceOutput = ccpTrace(s);
                macroExpansionTraceOutput = macroExpansionTrace(s);
                macroEliminationTraceOutput = macroEliminationTrace(s);
                // Test same seed produce same result to test that different traces come from different seed and
                // not indeterminism with the test.
                Asserts.assertEQ(igvnTraceOutput, igvnTrace(s),
                        "got different IGVN traces for the same seed");
                Asserts.assertEQ(ccpTraceOutput, ccpTrace(s),
                        "got different CCP traces for the same seed");
                Asserts.assertEQ(macroExpansionTraceOutput, macroExpansionTrace(s),
                        "got different macro expansion traces for the same seed");
                Asserts.assertEQ(macroEliminationTraceOutput, macroEliminationTrace(s),
                        "got different macro elimination traces for the same seed");

                igvnTraceSet.add(igvnTraceOutput);
                ccpTraceSet.add(ccpTraceOutput);
                macroExpansionTraceSet.add(macroExpansionTraceOutput);
                macroEliminationTraceSet.add(macroEliminationTraceOutput);
            }
            Asserts.assertGT(igvnTraceSet.size(), 1,
                    "got same IGVN traces for 5 different seeds");
            Asserts.assertGT(ccpTraceSet.size(), 1,
                    "got same CCP traces for 5 different seeds");
            Asserts.assertGT(macroExpansionTraceSet.size(), 1,
                    "got same macro expansion traces for 5 different seeds");
            Asserts.assertGT(macroEliminationTraceSet.size(), 1,
                    "got same macro elimination traces for 5 different seeds");
        } else if (args.length > 0) {
            sum(Integer.parseInt(args[0]));
        }
    }
}