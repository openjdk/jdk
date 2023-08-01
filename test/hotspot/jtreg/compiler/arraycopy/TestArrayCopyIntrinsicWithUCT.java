/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297730
 * @summary Test taking UCT between array allocation and array copy to report correct exception.
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.arraycopy.TestArrayCopyIntrinsicWithUCT::test*
 *                   compiler.arraycopy.TestArrayCopyIntrinsicWithUCT
 */

package compiler.arraycopy;

import jdk.test.lib.Asserts;

import java.util.function.Function;
import java.util.function.Supplier;

public class TestArrayCopyIntrinsicWithUCT {
    static int zero = 0;
    static int zero2 = 0;
    static int minusOne = -1;
    static int iFld;
    static int iFld2;
    static boolean flag;
    static byte[] byArrNull = null;
    static A aFld = null;

    static public void main(String[] args) {
        System.out.println("Start"); // Ensure loaded.
        new A(); // Ensure loaded
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSize);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSize2);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeFldSize);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeFldSize2);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeStore);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeStore2);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZero);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZero2);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZero3);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZero4);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZero5);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZero6);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZero7);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZeroFld);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZeroFld2);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZeroFld3);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZeroFld4);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZeroFld5);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZeroFld6);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZeroFld7);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeDivZeroNullPointer);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeSizeComplex);
        runNegativeSize(TestArrayCopyIntrinsicWithUCT::testNegativeControlFlowNotAllowed);
        flag = false;
        runNegativeSizeHalf();
        runNegativeSizeHalf();
    }

    static void runNegativeSize(Supplier<byte[]> testMethod) {
        try {
            testMethod.get();
            Asserts.fail("should throw exception");
        } catch (NegativeArraySizeException e) {
            // Expected
        }
    }

    static void runNegativeSize(Function<byte[], byte[]> testMethod) {
        try {
            testMethod.apply(null);
            Asserts.fail("should throw exception");
        } catch (NegativeArraySizeException e) {
            // Expected
        }
    }

    static byte[] testNegativeSize(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = byArr.length; // null check trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeSize2() {
        byte[] byArr = new byte[8];
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = byArrNull.length; // null check trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeFldSize(byte[] byArr) {
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        int len = byArr.length; // null check trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }
    static byte[] testNegativeFldSize2() {
        byte[] byArr = new byte[8];
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        int len = byArrNull.length; // null check trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeSizeStore(byte[] byArr) {
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        iFld++; // Since we have a store here, we do not move the allocation down
        int len = byArr.length; // null check trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeSizeStore2() {
        byte[] byArr = new byte[8];
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        iFld++; // Since we have a store here, we do not move the allocation down
        int len = byArrNull.length; // null check trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeSizeDivZero(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = 8 / zero; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeSizeDivZero2() {
        byte[] byArr = new byte[8];
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = 8 / zero; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeSizeDivZero3(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = 8 / zero; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, iFld2);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZero4(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = 8 / zero / zero2; // 2 div by zero traps would fail
        System.arraycopy(byArr, 0, b, 0, iFld2);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZero5(byte[] byArr) {
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        int len = 8 / zero / zero2; // 2 div by zero traps would fail
        System.arraycopy(byArr, 0, b, 0, iFld2);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZero6(byte[] byArr) {
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        int len = 8 / zero / zero2; // 2 div by zero traps would fail
        System.arraycopy(byArr, 0, b, 0, 8);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZero7(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = 8 / zero / zero2; // 2 div by zero traps would fail
        System.arraycopy(byArr, 0, b, 0, 8);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZeroFld(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = minusOne / zero; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeSizeDivZeroFld2() {
        byte[] byArr = new byte[8];
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = minusOne / zero; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }

    static byte[] testNegativeSizeDivZeroFld3(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = minusOne / zero; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, iFld2);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZeroFld4(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = minusOne / zero / zero2; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, iFld2);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZeroFld5(byte[] byArr) {
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        int len = minusOne / zero / zero2; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, iFld2);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZeroFld6(byte[] byArr) {
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        int len = minusOne / zero / zero2; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, 8);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZeroFld7(byte[] byArr) {
        byte[] b = new byte[-1]; // throws NegativeArraySizeException
        int len = minusOne / zero / zero2; // div by zero trap would fail
        System.arraycopy(byArr, 0, b, 0, 8);
        iFld = len;
        return b;
    }

    static byte[] testNegativeSizeDivZeroNullPointer(byte[] byArr) {
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        int x = minusOne / zero / zero2; // div by zero trap would fail
        int len = byArr.length;
        System.arraycopy(byArr, 0, b, 0, len);
        iFld = x;
        return b;
    }

    static byte[] testNegativeSizeComplex(byte[] byArr) {
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        int x = minusOne / zero; // div by zero trap would fail
        int y = aFld.i;
        int len = byArr.length;
        x = x + aFld.i2 / zero2;
        System.arraycopy(byArr, 0, b, 0, x);
        iFld = x + y;
        return b;
    }

    // Optimization not applied because of additional control flow that is not considered safe.
    static byte[] testNegativeControlFlowNotAllowed(byte[] byArr) {
        int x = 23;
        byte[] b = new byte[minusOne]; // throws NegativeArraySizeException
        if (flag) {
            x = 34;
        }
        int len = x / zero;
        System.arraycopy(byArr, 0, b, 0, 8);
        iFld = len;
        return b;
    }

    static void runNegativeSizeHalf() {
        try {
            testNegativeSizeHalf(null);
            Asserts.fail("should throw exception");
        } catch (NegativeArraySizeException e) {
            Asserts.assertTrue(flag, "wrongly caught NegativeArraySizeException");
        } catch (NullPointerException e) {
            Asserts.assertFalse(flag, "wrongly caught NullPointerException");
        }
        flag = !flag;
    }

    static byte[] testNegativeSizeHalf(byte[] byArr) {
        int size = flag ? -1 : 1;
        byte[] b = new byte[size]; // throws NegativeArraySizeException if size == -1
        int len = byArr.length; // throws NullPointerException if size == 1
        System.arraycopy(byArr, 0, b, 0, len);
        return b;
    }
}

class A {
    int i, i2;
}
