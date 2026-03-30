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
    static MyValue1[] array6 = (MyValue1[])ValueClass.newReferenceArray(MyValue1.class, 1);
    static MyValue2[] array7 = (MyValue2[])ValueClass.newReferenceArray(MyValue2.class, 1);
    { // doesn't run!?
        array6[0] = new MyValue1(42);
        array7[0] = new MyValue2(42);
    }
    
    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.TRAP, "4", IRNode.CALL, "4", IRNode.IF, "4" })
    @IR(failOn = IRNode.ALLOC)
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
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "1", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.TRAP, "3", IRNode.CALL, "3", IRNode.IF, "4" })
    @IR(failOn = IRNode.ALLOC)
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
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.TRAP, "4", IRNode.CALL, "4", IRNode.IF, "6" })
    @IR(failOn = IRNode.ALLOC)
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

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "2", IRNode.TRAP, "5", IRNode.CALL, "5", IRNode.IF, "6" })
    @IR(failOn = IRNode.ALLOC)
    public static void test7(I[] array) {
        test7Inline(array[0]);
    }

    @Run(test = "test7")
    public static void test7Runner() {
        test7Inline(array1[0]);
        test7Inline(array2[0]);
        test7Inline(array3[0]);
        test7Inline(array4[0]);
        test7(array5);
    }

    @ForceInline
    static void test7Inline(I i) {
        i.m();
    }

    // if (array == null) {
    //   trap1;
    // }
    // if (0 not in range of array) {
    //   trap2;
    // }
    // if (array flat) {
    //   if (array.klass == MyValue1[]) {
    //      if (array[0] == null) {
    //        trap3;
    //      }
    //      // inlined call
    //   } else if (array.klass == MyValue2[]) {
    //      if (array[0] == null) {
    //        trap3;
    //      }
    //      // inlined call
    //   } else {
    //     trap4;
    //   }
    // } else {
    //   if (array.klass == MyValue1[]) {
    //      if (array[0] == null) {
    //        trap3;
    //      }
    //      // inlined call
    //   } else if (array.klass == MyValue2[]) {
    //      if (array[0] == null) {
    //        trap3;
    //      }
    //      // inlined call
    //   } else {
    //     trap5;
    //   }
    // }
    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.BIMORPHIC_OR_OPTIMIZED_TYPE_CHECK_TRAP, "1", IRNode.TRAP, "5", IRNode.CALL, "5", IRNode.IF, "11" })
    @IR(failOn = IRNode.ALLOC)
    public static void test9(I[] array) {
        test9Inline(array[0]);
    }

    @Run(test = "test9")
    public static void test9Runner() {
        if (array6[0] == null) {
            array6[0] = new MyValue1(42);
            array7[0] = new MyValue2(42);
        }
        test9(array1);
        test9(array2);
        test9(array6);
        test9(array7);
    }

    @ForceInline
    static void test9Inline(I i) {
        i.m();
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.BIMORPHIC_OR_OPTIMIZED_TYPE_CHECK_TRAP, "1", IRNode.TRAP, "5", IRNode.CALL, "5", IRNode.IF, "11" })
    @IR(failOn = IRNode.ALLOC)
    public static void test11(I[] array) {
        test11Inline(array[0]);
    }

    @Run(test = "test11")
    public static void test11Runner() {
        for (int i = 0; i < 50; i++) {
            test11(array1);
        }
        test11(array2);
        test11(array3);
        test11(array4);
    }

    @ForceInline
    static void test11Inline(I i) {
        i.m();
    }

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
