/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.aot
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 * @build compiler.aot.RecompilationTest
 *        compiler.aot.AotCompiler
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *     sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run driver compiler.aot.AotCompiler -libname libRecompilationTest1.so
 *     -class compiler.whitebox.SimpleTestCaseHelper
 *     -extraopt -Dgraal.TieredAOT=true -extraopt -Dgraal.ProfileSimpleMethods=true
 *     -extraopt -Dgraal.ProbabilisticProfiling=false
 *     -extraopt -XX:+UnlockDiagnosticVMOptions -extraopt -XX:+WhiteBoxAPI -extraopt -Xbootclasspath/a:.
 *     -extraopt -XX:-UseCompressedOops
 *     -extraopt -XX:CompileCommand=dontinline,compiler.whitebox.SimpleTestCaseHelper::*
 * @run driver compiler.aot.AotCompiler -libname libRecompilationTest2.so
 *     -class compiler.whitebox.SimpleTestCaseHelper
 *     -extraopt -Dgraal.TieredAOT=false
 *     -extraopt -XX:+UnlockDiagnosticVMOptions -extraopt -XX:+WhiteBoxAPI -extraopt -Xbootclasspath/a:.
 *     -extraopt -XX:-UseCompressedOops
 *     -extraopt -XX:CompileCommand=dontinline,compiler.whitebox.SimpleTestCaseHelper::*
 * @run main/othervm -Xmixed -Xbatch -XX:+UnlockExperimentalVMOptions -XX:+UseAOT -XX:-TieredCompilation
 *     -XX:-UseCounterDecay -XX:-UseCompressedOops
 *     -XX:-Inline
 *     -XX:AOTLibrary=./libRecompilationTest2.so -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -Dcompiler.aot.RecompilationTest.check_level=-1
 *     compiler.aot.RecompilationTest
 * @summary check if recompilation after aot goes fine
 */

 /* having whitebox-related options for aot compiler is a temporary solution,
    because of JDK-8146201
 */

package compiler.aot;

import compiler.whitebox.CompilerWhiteBoxTest;
import java.lang.reflect.Executable;
import jdk.test.lib.Asserts;

public final class RecompilationTest extends CompilerWhiteBoxTest {
    private static final int CHECK_LEVEL = Integer.getInteger(
                "compiler.aot.RecompilationTest.check_level");

    public static void main(String args[]) {
        CompilerWhiteBoxTest.main(RecompilationTest::new, args);
    }

    private RecompilationTest(TestCase testCase) {
        super(testCase);
    }

    @Override
    protected void test() throws Exception {
        if (testCase.isOsr()) {
            /* aot compiler is not using osr compilation */
            System.out.println("Skipping OSR case");
            return;
        }
        Executable e = testCase.getExecutable();
        Asserts.assertTrue(WHITE_BOX.isMethodCompiled(e),
                testCase.name() +  ": an executable expected to be compiled");
        Asserts.assertEQ(WHITE_BOX.getMethodCompilationLevel(e),
                COMP_LEVEL_AOT,
                String.format("%s: unexpected compilation level at start",
                        testCase.name()));
        compile();
        Asserts.assertTrue(WHITE_BOX.isMethodCompiled(e), testCase.name()
                + ": method expected to be compiled");
        /* a case with AOT'ed code checks exact compilation level equality
           while another case checks minimum level and if method compiled
           because there might be different compilation level transitions */
        if (CHECK_LEVEL != COMP_LEVEL_AOT) {
            Asserts.assertGTE(WHITE_BOX.getMethodCompilationLevel(e),
                CHECK_LEVEL,
                String.format("%s: expected compilation level"
                        + " after compilation to be no less than %d for %s",
                        testCase.name(), CHECK_LEVEL, testCase.name()));
        } else {
            Asserts.assertEQ(WHITE_BOX.getMethodCompilationLevel(e),
                COMP_LEVEL_AOT, String.format("%s: expected compilation"
                        + " level after compilation to be equal to %d for %s",
                        testCase.name(), COMP_LEVEL_AOT, testCase.name()));
        }
    }
}
