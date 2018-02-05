/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @build compiler.aot.DeoptimizationTest
 *        compiler.aot.AotCompiler
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *     sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run driver compiler.aot.AotCompiler -libname libDeoptimizationTest.so
 *     -class compiler.aot.DeoptimizationTest
 *     -compile compiler.aot.DeoptimizationTest.testMethod()D
 *     -extraopt -XX:-UseCompressedOops
 * @run main/othervm -Xmixed -XX:+UseAOT -XX:+TieredCompilation
 *     -XX:-UseCompressedOops
 *     -XX:CompileCommand=dontinline,compiler.aot.DeoptimizationTest::*
 *     -XX:AOTLibrary=./libDeoptimizationTest.so -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     compiler.aot.DeoptimizationTest
 * @summary check if aot code can be deoptimized
 */

package compiler.aot;

import java.lang.reflect.Method;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;
import compiler.whitebox.CompilerWhiteBoxTest;

public final class DeoptimizationTest {
    private static final String TEST_METHOD = "testMethod";
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private final Method testMethod;

    private DeoptimizationTest() {
        try {
            testMethod = getClass().getDeclaredMethod(TEST_METHOD);
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG: no test method found", e);
        }
    }

    public static void main(String args[]) {
        new DeoptimizationTest().test();
    }

    private double testMethod() {
        return 42 / 0;
    }

    private void test() {
        Asserts.assertTrue(WB.isMethodCompiled(testMethod),
                "Method expected to be compiled");
        Asserts.assertEQ(WB.getMethodCompilationLevel(testMethod),
                CompilerWhiteBoxTest.COMP_LEVEL_AOT,
                "Unexpected compilation level at start");
        Utils.runAndCheckException(() -> testMethod(), ArithmeticException.class);
        Asserts.assertFalse(WB.isMethodCompiled(testMethod),
                "Method is unexpectedly compiled after deoptimization");
        Asserts.assertEQ(WB.getMethodCompilationLevel(testMethod), 0,
                "Unexpected compilation level after deoptimization");
    }
}
