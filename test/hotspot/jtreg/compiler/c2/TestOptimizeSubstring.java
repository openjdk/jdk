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
 * @summary verify that -XX:+OptimizeSubstring removes String.substring()
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

    public static void main (String args[]) {
        if (args.length == 0) {
            check(true);  // check generated code when c2 enables OptimizeSubstring
            check(false); // ... and disabled
        }
        else {
            boolean val1 = false;
            boolean val2 = false;
            boolean caughtEx;

            for (int i = 0; i < 20_000; ++i) {
                val1 |= TestOptimizeSubstring.useStartsWith("abcdef");
                val2 |= TestOptimizeSubstring.useStartsWith("efgdedf");

                caughtEx = false;
                try {
                    TestOptimizeSubstring.useStartsWith("");
                } catch(StringIndexOutOfBoundsException e) {
                    caughtEx = true;
                }
                Asserts.assertTrue(caughtEx, "useStartsWith(\"\") should throw StringIndexOutOfBoundsException");
            }

            Asserts.assertTrue (val1, "val1 should be true");
            Asserts.assertFalse(val2, "val2 should be false");
        }
    }

    private static void check(boolean enabled) {
        OutputAnalyzer oa;
        String newStringAlloc = "call,static  wrapper for: _new_array_nozero_Java";

        try {
            oa = ProcessTools.executeTestJvm("-XX:+UnlockDiagnosticVMOptions", "-Xbootclasspath/a:.",
                    "-XX:" + (enabled ? "+" : "-") + "OptimizeSubstring",
                    "-XX:+PrintOptoAssembly", "-XX:-TieredCompilation",
                    "-XX:CompileOnly=" + TestOptimizeSubstring.class.getName() + "::useStartsWith",
                    TestOptimizeSubstring.class.getName(),
                    "runtest");
        } catch (Exception e) {
            throw new Error("Exception launching child for case enabled=" + enabled + " : " + e, e);
        }
        oa.shouldHaveExitValue(0);

        if (enabled) {
            oa.shouldNotContain(newStringAlloc);
        } else {
            oa.shouldContain(newStringAlloc);
        }
   }

    private static boolean useStartsWith(String s) {
        String x = s.substring(1);
        return x.startsWith("a") | x.startsWith("b") | x.startsWith("c");
    }
}
