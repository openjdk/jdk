/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

package ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8276546
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compMode != "Xcomp" & vm.compiler1.enabled & vm.compiler2.enabled & vm.flagless
 * @summary Test that CompileThreshold flag is ignored when passed as Java/VM option to the framework.
 *          Normally, the framework should be called with driver.
 * @library /test/lib /testlibrary_tests /
 * @run main/othervm -XX:CompileThreshold=3000 -XX:+UseG1GC ir_framework.tests.TestCompileThreshold
 */

public class TestCompileThreshold {
    public int iFld = 0;

    public static void main(String[] args) throws Exception {
        try {
            // CompileThreshold=3000 passed to the JTreg test is ignored even though we prefer command line flags.
            // CompileThreshold=2000 is user defined and passed directly to the framework and thus not ignored.
            // InterpreterProfilePercentage=0 ensures that we compile exactly after 2000 invocations.
            TestFramework.runWithFlags("-XX:CompileThreshold=2000", "-XX:InterpreterProfilePercentage=0",
                                       "-XX:-TieredCompilation", "-DTest=testWithCompileThreshold",
                                       "-DPreferCommandLineFlags=true");
        } catch (IRViolationException e) {
            Asserts.assertTrue(e.getExceptionInfo().contains("Failed IR Rules (1)"), "exactly one rule failed");
            Asserts.assertTrue(e.getExceptionInfo().contains("testWithCompileThreshold"),
                               "testWithCompileThreshold() failed");
        }

        try {
            TestFramework.runWithFlags("-XX:InterpreterProfilePercentage=0", "-XX:-TieredCompilation",
                                       "-DTest=testWithoutCompileThreshold");
        } catch (IRViolationException e) {
            Asserts.assertTrue(e.getExceptionInfo().contains("Failed IR Rules (1)"), "exactly one rule failed");
            Asserts.assertTrue(e.getExceptionInfo().contains("testWithoutCompileThreshold"),
                               "testWithoutCompileThreshold() failed");
        }
    }

    @Test
    @IR(counts = {IRNode.CALL, "1"}) // fails
    public void testWithCompileThreshold() {
        iFld++;
    }

    @Run(test = "testWithCompileThreshold")
    @Warmup(2010)
    public void runTestWithCompileThreshold(RunInfo info) {
        if (iFld == 2000) {
            TestFramework.assertNotCompiled(info.getTest());
        } else if (iFld == 2001) {
            // CompileThreshold=2000 is passed directly as a flag to the framework.
            // Therefore, testWithCompileThreshold() must be compiled by now.
            TestFramework.assertCompiled(info.getTest());
        }
        testWithCompileThreshold();
    }


    @Test
    @IR(counts = {IRNode.CALL, "1"}) // fails
    public void testWithoutCompileThreshold() {
        iFld++;
    }

    @Run(test = "testWithoutCompileThreshold")
    @Warmup(2010)
    public void runTestWithoutCompileThreshold(RunInfo info) {
        testWithCompileThreshold();
        if (info.isWarmUp()) {
            // CompileThreshold=3000 is passed to the JTreg test but not directly to the framework.
            // Therefore, it is ignored, and we do not trigger a compilation until the framework does.
            TestFramework.assertNotCompiled(info.getTest());
        }
    }
}
