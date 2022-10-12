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

package ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.TestVMException;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @requires vm.debug == true & vm.flagless
 * @summary Test -DIgnoreCompilerControls property flag.
 * @library /test/lib /
 * @run driver ir_framework.tests.TestDIgnoreCompilerControls
 */

public class TestDIgnoreCompilerControls {
    public static void main(String[] args) {
        // Ignore Compiler Control
        TestFramework.runWithFlags("-XX:CompileCommand=option,ir_framework.tests.TestDIgnoreCompilerControls::test2,bool,PrintInlining,true",
                                   "-DIgnoreCompilerControls=true");
        Asserts.assertFalse(TestFramework.getLastTestVMOutput().contains("don't inline by annotation"), "should have inlined: "
                                                                                                        + TestFramework.getLastTestVMOutput());
        // Don't ignore compiler control, sanity check
        try {
            TestFramework.runWithFlags("-XX:CompileCommand=option,ir_framework.tests.TestDIgnoreCompilerControls::test2,bool,PrintInlining,true",
                                       "-DIgnoreCompilerControls=false");
            throw new RuntimeException("should throw exception");
        } catch (TestVMException e) {
            Asserts.assertTrue(e.getExceptionInfo().contains("fail run"), "did not find exception with msg \"fail run\"");
            Asserts.assertTrue(TestFramework.getLastTestVMOutput().contains("don't inline by annotation"), "should not have inlined: " + TestFramework.getLastTestVMOutput());
        }
    }

    @Test
    public void test() {}

    @ForceCompile
    public void ignoredForceCompile() {}

    @Run(test = "test")
    @Warmup(10000)
    public void run(RunInfo info) throws NoSuchMethodException {
        if (!info.isWarmUp()) {
            Asserts.assertFalse(WhiteBox.getWhiteBox().isMethodCompiled(getClass().getDeclaredMethod("ignoredForceCompile")), "fail run");
        }
    }

    @DontInline
    public void ignoreDontInline() {}

    @Test
    @Warmup(10000)
    public void test2() {
        ignoreDontInline(); // Is inlined and therefore not compiled separately.
    }
}
