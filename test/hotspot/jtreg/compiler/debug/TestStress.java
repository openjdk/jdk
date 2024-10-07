/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @key stress randomness
 * @bug 8252219 8256535 8317349
 * @requires vm.debug == true & vm.compiler2.enabled
 * @summary Tests that stress compilations with the same seed yield the same
 *          IGVN, CCP, and macro expansion traces.
 * @library /test/lib /
 * @run driver compiler.debug.TestStress
 */

public class TestStress {

    static String phaseTrace(String stressOption, String traceOption,
                             int stressSeed) throws Exception {
        String className = TestStress.class.getName();
        String[] procArgs = {
            "-Xcomp", "-XX:-TieredCompilation", "-XX:-Inline", "-XX:+CICountNative",
            "-XX:CompileOnly=" + className + "::sum", "-XX:" + traceOption,
            "-XX:+" + stressOption, "-XX:StressSeed=" + stressSeed,
            className, "10"};
        ProcessBuilder pb  = ProcessTools.createLimitedTestJavaProcessBuilder(procArgs);
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

    static void sum(int n) {
        int acc = 0;
        for (int i = 0; i < n; i++) acc += i;
        System.out.println(acc);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            for (int s = 0; s < 10; s++) {
                Asserts.assertEQ(igvnTrace(s), igvnTrace(s),
                    "got different IGVN traces for the same seed");
                Asserts.assertEQ(ccpTrace(s), ccpTrace(s),
                    "got different CCP traces for the same seed");
                Asserts.assertEQ(macroExpansionTrace(s), macroExpansionTrace(s),
                    "got different macro expansion traces for the same seed");
            }
        } else if (args.length > 0) {
            sum(Integer.parseInt(args[0]));
        }
    }
}
