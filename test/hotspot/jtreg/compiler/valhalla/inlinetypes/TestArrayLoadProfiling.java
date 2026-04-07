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

    static final MyValue1[] array1 = { new MyValue1((byte)42) };
    static MyValue2[] array2 = { new MyValue2((byte)42) };
    static MyValue1[] array3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, new MyValue1((byte)42));
    static MyValue2[] array4 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, new MyValue2((byte)42));
    static A[] array5 = { new A() };
    static MyValue1[] array6 = (MyValue1[])ValueClass.newReferenceArray(MyValue1.class, 1);
    static MyValue2[] array7 = (MyValue2[])ValueClass.newReferenceArray(MyValue2.class, 1);
    static MyValue1[] array8 = (MyValue1[])ValueClass.newNullRestrictedAtomicArray(MyValue1.class, 1, new MyValue1((byte)42));
    static MyValue1[] array9 = (MyValue1[])ValueClass.newNullableAtomicArray(MyValue1.class, 1);
    static MyValue3[] array10 = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, new MyValue3(42));
    static {
        array6[0] = new MyValue1((byte)42);
        array7[0] = new MyValue2((byte)42);
        array8[0] = new MyValue1((byte)42);
        array9[0] = new MyValue1((byte)42);
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
    //   if (array[0] == null) {
    //        trap3;
    //   }
    //   if (array[0].klass == MyValue1) {
    //      // inlined call
    //   } else if (array[0].klass == MyValue2) {
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
        test9(array1);
        test9(array2);
        test9(array6);
        test9(array7);
    }

    @ForceInline
    static void test9Inline(I i) {
        i.m();
    }

    // if (array == null) {
    //   trap1;
    // }
    // if (0 not in range of array) {
    //   trap2;
    // }
    // if (array.klass == MyValue1[]) {
    //    if (array[0] == null) {
    //      trap3;
    //    } 
    //    // inlined call
    // } else {
    //   if (array flat) {
    //     elt = load_unknown_inline();
    //     if (elt == null) {
    //       trap3;
    //     } 
    //     if (elt.klass == MyValue1) {
    //       // inlined call
    //     } else if (elt.klass == MyValue2) {
    //       // inlined call
    //     } else {
    //       trap4;
    //     }
    //   } else {
    //     trap5;
    //   }
    // }
    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.BIMORPHIC_OR_OPTIMIZED_TYPE_CHECK_TRAP, "1", IRNode.TRAP, "5", IRNode.CALL, "6", IRNode.IF, "9" })
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

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.BIMORPHIC_OR_OPTIMIZED_TYPE_CHECK_TRAP, "1", IRNode.TRAP, "5", IRNode.CALL, "6", IRNode.IF, "7" })
    @IR(failOn = IRNode.ALLOC)
    public static void test13(I[] array) {
        test13Inline(array[0]);
    }

    @Run(test = "test13")
    public static void test13Runner() {
        test13(array1);
        test13(array2);
        test13(array3);
        test13(array4);
    }

    @ForceInline
    static void test13Inline(I i) {
        i.m();
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.BIMORPHIC_OR_OPTIMIZED_TYPE_CHECK_TRAP, "1", IRNode.TRAP, "4", IRNode.CALL, "5", IRNode.IF, "7" })
    @IR(failOn = IRNode.ALLOC)
    public static void test15(I[] array) {
        test15Inline(array[0]);
    }

    @Run(test = "test15")
    public static void test15Runner() {
        test15(array1);
        test15(array2);
        test15(array3);
        test15(array4);
        test15(array6);
        test15(array7);
    }

    @ForceInline
    static void test15Inline(I i) {
        i.m();
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "1", IRNode.RANGE_CHECK_TRAP, "1", IRNode.TRAP, "2", IRNode.CALL, "4", IRNode.IF, "4" })
    @IR(failOn = IRNode.ALLOC)
    public static void test17(I[] array) {
        test17Inline(array[0]);
    }

    @Run(test = "test17")
    @Warmup(0)
    public static void test17Runner() {
        test17(array1);
    }

    @ForceInline
    static void test17Inline(I i) {
        i.m();
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "1", IRNode.TRAP, "1", IRNode.CALL, "1", IRNode.IF, "1" })
    @IR(failOn = IRNode.ALLOC)
    public static void test18() {
        array1[0].m();
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.TRAP, "3", IRNode.CALL, "3", IRNode.IF, "3" })
    @IR(failOn = IRNode.ALLOC)
    public static void test19(A[] array) {
        array[0].m();
    }

    @Run(test = "test19")
    public static void test19Runner() {
        test19(array5);
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.TRAP, "3", IRNode.CALL, "3", IRNode.IF, "8" })
    @IR(failOn = IRNode.ALLOC)
    public static void test20(MyValue1[] array) {
        array[0].m();
    }

    @Run(test = "test20")
    @Warmup(0)
    public static void test20Runner() {
        test20(array1);
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.TRAP, "3", IRNode.CALL, "3", IRNode.IF, "8" })
    @IR(failOn = IRNode.ALLOC)
    public static void test21() {
        I[] array = array3;
        test21Inline(array);
    }

    @Run(test = "test21")
    public static void test21Runner() {
        test21();
        test21Inline(array2);
        test21Inline(array4);
        test21Inline(array5);
        test21Inline(array7);
    }

    @ForceInline
    static void test21Inline(I[] array) {
        array[0].m();
    }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.TRAP, "4", IRNode.CALL, "4", IRNode.IF, "7" })
    @IR(failOn = IRNode.ALLOC)
    public static void test22() {
        I[] array = array3;
        test22Inline(array);
    }

    @Run(test = "test22")
    public static void test22Runner() {
        test22();
        test22Inline(array2);
        test22Inline(array4);
    }

    @ForceInline
    static void test22Inline(I[] array) {
        array[0].m();
    }

    // @Test
    // @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "1", IRNode.TRAP, "4", IRNode.CALL, "4", IRNode.IF, "9" })
    // @IR(failOn = IRNode.ALLOC)
    // public static void test22(I[] array) {
    //     test22Inline(array[0]);
    // }

    // @Run(test = "test22")
    // public static void test22Runner() {
    //     test22(array1);
    //     test22(array3);
    //     test22(array6);
    //     test22(array8);
    //     test22(array9);
    //     test22Inline(array2[0]);
    //     test22Inline(array4[0]);
    //     test22Inline(array5[0]);
    // }

    // @ForceInline
    // static void test22Inline(I i) {
    //     i.m();
    // }

    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "2", IRNode.TRAP, "5", IRNode.CALL, "5", IRNode.IF, "9" })
    @IR(failOn = IRNode.ALLOC)
    public static void test23(I[] array) {
        test23Inline(array[0]);
    }

    @Run(test = "test23")
    public static void test23Runner() {
        test23(array1);
        test23(array3);
        test23(array6);
        test23(array8);
        test23(array9);
        test23Inline(array2[0]);
        test23Inline(array4[0]);
        test23Inline(array5[0]);
    }

    @ForceInline
    static void test23Inline(I i) {
        i.m();
    }
    
    @Test
    @IR(counts = { IRNode.NULL_CHECK_TRAP, "2", IRNode.RANGE_CHECK_TRAP, "1", IRNode.CLASS_CHECK_TRAP, "2", IRNode.TRAP, "5", IRNode.CALL, "5", IRNode.IF, "9" })
    @IR(failOn = IRNode.ALLOC)
    public static void test24(I[] array) {
        array[0].m();
    }

    @Run(test = "test24")
    public static void test24Runner() {
        test24(array3);
        test24(array10);
    }

    interface I {
        void m();
    }
    

    static value class MyValue1 implements I {
        byte byteField;
        byte byteField2;
        int byteField3;

        MyValue1(byte byteField) {
            this.byteField = byteField;
            this.byteField2 = byteField;
            this.byteField3 = byteField;
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

    static value class MyValue3 implements I {
        int intField1;
        int intField2;
        int intField3;
        int intField4;

        MyValue3(int intField) {
            this.intField1 = intField;
            this.intField2 = intField;
            this.intField3 = intField;
            this.intField4 = intField;
        }

        public void m() {
        }
    }

    static class A implements I {
        public void m() {
        }
    }
}
