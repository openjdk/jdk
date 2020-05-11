/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited. All rights reserved.
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
 * @bug 8244164
 * @requires vm.aot & vm.bits == 64
 * @library /test/lib /testlibrary /
 * @modules java.base/jdk.internal.misc
 * @build compiler.aot.TestHeapBase
 *        sun.hotspot.WhiteBox
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 * @run driver compiler.aot.AotCompiler -libname libTestHeapBase.so
 *     -class compiler.aot.TestHeapBase
 *     -compile compiler.aot.TestHeapBase.test()V
 *     -extraopt -XX:+UseCompressedOops -extraopt -Xmx1g
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseAOT
 *     -XX:+UseCompressedOops -XX:HeapBaseMinAddress=32g
 *     -XX:AOTLibrary=./libTestHeapBase.so -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xlog:aot+class+load=trace
 *     compiler.aot.TestHeapBase
 * @summary check for crash when jaotc is run with zero-based compressed oops then
 *          generated code is loaded in vm with non-zero-based compressed oops.
 */

package compiler.aot;

import java.lang.reflect.Method;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.hotspot.WhiteBox;
import compiler.whitebox.CompilerWhiteBoxTest;

public final class TestHeapBase {
    private static final String TEST_METHOD = "test";
    private static final WhiteBox WB = WhiteBox.getWhiteBox();
    private final Method testMethod;

    private TestHeapBase() {
        try {
            testMethod = getClass().getDeclaredMethod(TEST_METHOD);
        } catch (NoSuchMethodException e) {
            throw new Error("TEST BUG: no test method found", e);
        }
    }

    public static void main(String args[]) {
        new TestHeapBase().test();
    }

    private void test() {
        System.out.println("Hello, World!");

        Asserts.assertTrue(WB.isMethodCompiled(testMethod),
                "Method expected to be compiled");
        Asserts.assertEQ(WB.getMethodCompilationLevel(testMethod),
                CompilerWhiteBoxTest.COMP_LEVEL_AOT,
                "Expected method to be AOT compiled");
    }
}
