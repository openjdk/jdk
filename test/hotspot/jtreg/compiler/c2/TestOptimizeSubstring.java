/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/**
 * @test
 * @summary verify that -XX:+OptimizeTempArray removes String.substring()
 * @library /test/lib /
 * @requires vm.compMode != "Xint" & vm.flavor == "server" & (vm.opt.TieredStopAtLevel == null | vm.opt.TieredStopAtLevel == 4) & vm.debug == true
 * @requires !vm.emulatedClient & !vm.graal.enabled
 * @run driver compiler.c2.TestOptimizeSubstring
 */

package compiler.c2;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Asserts;
import jdk.test.lib.Platform;

public class TestOptimizeSubstring {
    // ideally, we should check the opcode 'call' as well, but x86_32.ad uses all CAPITAL opcodes.
    // the feature is 'CALL, static  wrapper for: _new_array_nozero_Java' on i686.
    static final String newStringAlloc = /*call ,*/"static  wrapper for: _new_array_nozero_Java";

    public static void main (String args[]) {
        if (args.length == 0) {
            check(true);  // check generated code when c2 enables OptimizeSubstring
            check(false); // ... and disabled
            check_nontrivial();
            check_deoptimization();
        } else if (args[0].equals("nontrivial")) {
            boolean val1 = false;

            for (int i = 0; i < 20_000; ++i) {
                val1 |= TestOptimizeSubstring.useStartsWith_NonTrivial();
            }

            Asserts.assertFalse(val1, "val1 should be false");
        } else if (args[0].equals("deoptimization")) {
            int CNT = 20_000;
            String s = "abcd";
            boolean val1 = true;

             for (int i = 0; i < CNT; ++i) {
                boolean f = i <= (CNT - 10) ? true : false;
                val1 &= TestOptimizeSubstring.mayHaveDeoptimization(s, f);
            }

            Asserts.assertTrue(val1, "val1 should be true");
        } else {
            boolean val1 = false;
            boolean val2 = false;

            for (int i = 0; i < 20_000; ++i) {
                val1 |= TestOptimizeSubstring.useStartsWith("abcdefghijklmnop");
                val2 |= TestOptimizeSubstring.useStartsWith("efgdedfghijklmnop");
            }
            Asserts.assertTrue (val1, "val1 should be true");
            Asserts.assertFalse(val2, "val2 should be false");

            boolean caughtEx = false;
            try {
                TestOptimizeSubstring.useStartsWith("");
            } catch(StringIndexOutOfBoundsException e) {
                caughtEx = true;
            }
            Asserts.assertTrue(caughtEx, "useStartsWith(\"\") should throw StringIndexOutOfBoundsException");

        }
    }

    private static void check(boolean enabled) {
        OutputAnalyzer oa;

        try {
            oa = ProcessTools.executeTestJvm("-XX:+UnlockDiagnosticVMOptions", "-Xbootclasspath/a:.",
                    "-XX:" + (enabled ? "+" : "-") + "OptimizeTempArray",
                    "-XX:+PrintOptoAssembly", "-XX:-TieredCompilation", "-XX:ArrayCopyLoadStoreMaxElem=0",
                    "-XX:CompileOnly=" + TestOptimizeSubstring.class.getName() + "::useStartsWith",
                    TestOptimizeSubstring.class.getName(),
                    "runtest");
        } catch (Exception e) {
            throw new Error("Exception launching child for case enabled=" + enabled + " : " + e, e);
        }
        oa.shouldHaveExitValue(0);

        if (enabled) {
            oa.shouldNotContain(TestOptimizeSubstring.newStringAlloc);
        } else {
            oa.shouldContain(TestOptimizeSubstring.newStringAlloc);
        }
   }

    private static void check_nontrivial() {
        OutputAnalyzer oa;
        try {
            oa = ProcessTools.executeTestJvm("-XX:+UnlockDiagnosticVMOptions", "-Xbootclasspath/a:.",
                    "-XX:+OptimizeTempArray", "-XX:-UseOnStackReplacement",
                    "-XX:+PrintOptoAssembly", "-XX:-TieredCompilation", "-XX:ArrayCopyLoadStoreMaxElem=0",
                    "-XX:CompileOnly=" + TestOptimizeSubstring.class.getName() + "::useStartsWith_NonTrivial",
                    TestOptimizeSubstring.class.getName(),
                    "nontrivial");
        } catch (Exception e) {
            throw new Error("Exception launching child for check_nontrivial");
        }
        oa.shouldHaveExitValue(0);
    }

    private static void check_deoptimization() {
        OutputAnalyzer oa;
        try {
            oa = ProcessTools.executeTestJvm("-XX:+UnlockDiagnosticVMOptions", "-Xbootclasspath/a:.",
                    "-XX:+OptimizeTempArray", "-XX:-UseOnStackReplacement",
                    "-XX:+PrintOptoAssembly", "-XX:-TieredCompilation", "-XX:ArrayCopyLoadStoreMaxElem=0",
                    "-XX:CompileOnly=" + TestOptimizeSubstring.class.getName() + "::mayHaveDeoptimization",
                    "-XX:+TraceDeoptimization", "-XX:+PrintDeoptimizationDetails",
                    TestOptimizeSubstring.class.getName(),
                    "deoptimization");
        } catch (Exception e) {
            throw new Error("Exception launching child for mayHaveDeoptimization");
        }
        oa.shouldHaveExitValue(0);
        oa.shouldNotContain(TestOptimizeSubstring.newStringAlloc);
        oa.shouldContain("ScObj0 java/lang/String={ [hash :0]=#0, [coder :1]=#0, [hashIsZero :2]=#0, [value :3]=#ScObj1 }");
    }


    private static boolean useStartsWith(String s) {
        String x = s.substring(1);
        return x.startsWith("a") | x.startsWith("b") | x.startsWith("c");
    }

    // courtesy of John Rose's comment
    // https://github.com/openjdk/jdk/pull/974#pullrequestreview-551773771
    private static boolean useStartsWith_NonTrivial() {
        String s = "abcd";
        String x = s.substring(1, 2);
        return x.startsWith("bc");
    }

    private static boolean mayHaveDeoptimization(String s, boolean flag) {
        String p = s.substring(1, 3);

        boolean result = true;
        result &= p.length() == 2;
        result &= p.charAt(0) == 'b';
        result &= p.charAt(1) == 'c';

        if (!flag) { // unlikely, should trigger deoptimization of unstable_if
            result &= p.length() > 0;
            result &= p.charAt(0) == 'b';
            result &= p.charAt(1) == 'c';
        }
        return result;
    }
}
