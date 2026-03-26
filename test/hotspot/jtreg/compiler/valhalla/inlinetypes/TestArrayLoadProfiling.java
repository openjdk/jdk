/*
 * Copyright (c) 2026 IBM Corporation. All rights reserved.
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

/**
 * @test
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import compiler.lib.ir_framework.*;
import jdk.internal.value.ValueClass;
import jdk.test.whitebox.WhiteBox;
import compiler.whitebox.CompilerWhiteBoxTest;
import java.lang.reflect.Method;

public class TestArrayLoadProfiling {
    private final static WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) {
        TestFramework.runWithFlags("--enable-preview", "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED", "-Xbootclasspath/a:.", "-XX:+UnlockDiagnosticVMOptions", "-XX:+WhiteBoxAPI");
    }

    static MyValue1[] array1 = { new MyValue1(42) };
    static MyValue2[] array2 = { new MyValue2(42) };
    static MyValue1[] array3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, new MyValue1(42));
    static MyValue2[] array4 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, new MyValue2(42));
    static A[] array5 = { new A() };
    
    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.CALL, "4", IRNode.IF, "4" })
    public static void test1(I[] array) {
        test1Inline(array[0]);
    }

    @Run(test = "test1")
    public static void test1Runner() {
        test1(array1);
        test1Inline(array2[0]);
        test1Inline(array3[0]);
        test1Inline(array4[0]);
        test1Inline(array5[0]);
    }

    @ForceInline
    static void test1Inline(I i) {
        i.m();
    }

    // @Test
    // @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.CALL, "5", IRNode.IF, "5" })
    // @IR(failOn = { IRNode.CLASS_CHECK_TRAP })
    // public static void test2(I[] array) {
    //     test2Inline(array[0]);
    // }

    // @Run(test = "test2")
    // public static void test2Runner(RunInfo info)  throws Exception {
    //     if (info.isWarmUp()) {
    //         test2(array1);
    //         test2Inline(array2[0]);
    //         test2Inline(array3[0]);
    //         test2Inline(array4[0]);
    //         test2Inline(array5[0]);
    //     } else {
    //         Method m = TestArrayLoadProfiling.class.getDeclaredMethod("test2", I[].class);
    //         if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
    //             throw new RuntimeException("should be compiled");
    //         }
    //         int i = 0;
    //         do {
    //             test2(array2);
    //             i++;
    //             if (i > 10) {
    //                 throw new RuntimeException("should not be compiled anymore");
    //             }
    //         } while (WHITE_BOX.isMethodCompiled(m));
    //         WHITE_BOX.enqueueMethodForCompilation(m, CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION);
    //         if (!WHITE_BOX.isMethodCompiled(m) || WHITE_BOX.getMethodCompilationLevel(m) != CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION) {
    //             throw new RuntimeException("should be compiled");
    //         }
    //     }
    // }

    // @ForceInline
    // static void test2Inline(I i) {
    //     i.m();
    // }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "1", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.CALL, "3", IRNode.IF, "4" })
    public static void test3(I[] array) {
        test3Inline(array[0]);
    }

    @Run(test = "test3")
    public static void test3Runner() {
        test3(array3);
        test3(array4);
    }

    @ForceInline
    static void test3Inline(I i) {
        i.m();
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.CALL, "4", IRNode.IF, "6" })
    public static void test5(I[] array) {
        test5Inline(array[0]);
    }

    @Run(test = "test5")
    public static void test5Runner() {
        test5(array1);
        test5(array2);
    }

    @ForceInline
    static void test5Inline(I i) {
        i.m();
    }
    
    // @Test
    // public static Object test2(Object[] array) {
    //     return array[0];
    // }

    // @Run(test = "test2")
    // public static void test2Runner() {
    //     test2(array5);
    // }

    // @Test
    // public static Object test3(Object[] array) {
    //     return array[0];
    // }

    // @Run(test = "test3")
    // public static void test3Runner() {
    //     test3(array1);
    //     test3(array2);
    //     test3(array5);
    // }
    
    // @Test
    // public static Object test4(Object[] array) {
    //     return array[0];
    // }

    // @Run(test = "test4")
    // public static void test4Runner() {
    //     test4(array1);
    //     test4(array2);
    //     test4(array3);
    //     test4(array4);
    // }
    
    interface I {
        void m();
    }
    
    static value class MyValue1 implements I {
        int intField;

        MyValue1(int intField) {
            this.intField = intField;
        }
        
        public void m() {
        }
    }

    static value class MyValue2 implements I {
        int intField;

        MyValue2(int intField) {
            this.intField = intField;
        }

        public void m() {
        }
    }

    static class A implements I {
        public void m() {
        }
    }
}
