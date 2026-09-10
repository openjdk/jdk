/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2017, 2023, Red Hat, Inc. All rights reserved.
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
 * @test id=default
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.valuetypes.TestUnloadedValueTypeArray::test*
 *                   compiler.valhalla.valuetypes.TestUnloadedValueTypeArray
 */

/*
 * @test id=no-flattening
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:-UseArrayFlattening
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.valuetypes.TestUnloadedValueTypeArray::test*
 *                   compiler.valhalla.valuetypes.TestUnloadedValueTypeArray
 */

/*
 * @test id=xcomp
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp compiler.valhalla.valuetypes.TestUnloadedValueTypeArray
 */

/*
 * @test id=xcomp-no-flattening
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:-UseArrayFlattening
 *                   compiler.valhalla.valuetypes.TestUnloadedValueTypeArray
 */

/*
 * @test id=c2
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.valuetypes.TestUnloadedValueTypeArray::test*
 *                   compiler.valhalla.valuetypes.TestUnloadedValueTypeArray
 */

/*
 * @test id=c2-no-flattening
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -XX:-UseArrayFlattening
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.valuetypes.TestUnloadedValueTypeArray::test*
 *                   compiler.valhalla.valuetypes.TestUnloadedValueTypeArray
 */

/*
 * @test id=xcomp-c2
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   compiler.valhalla.valuetypes.TestUnloadedValueTypeArray
 */

/*
 * @test id=xcomp-c2-no-flattening
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -XX:-UseArrayFlattening
 *                   compiler.valhalla.valuetypes.TestUnloadedValueTypeArray
 */

package compiler.valhalla.valuetypes;

import jdk.test.lib.Asserts;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

@LooselyConsistentValue
value class MyValue1UnloadedValueTypeArray {
    int foo;

    private MyValue1UnloadedValueTypeArray() {
        foo = 0x42;
    }
}

@LooselyConsistentValue
value class MyValue2UnloadedValueTypeArray {
    int foo;

    public MyValue2UnloadedValueTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue3UnloadedValueTypeArray {
    int foo;

    public MyValue3UnloadedValueTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue4UnloadedValueTypeArray {
    int foo;

    public MyValue4UnloadedValueTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue5UnloadedValueTypeArray {
    int foo;

    public MyValue5UnloadedValueTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue6UnloadedValueTypeArray {
    int foo;

    public MyValue6UnloadedValueTypeArray(int n) {
        foo = n;
    }

    public MyValue6UnloadedValueTypeArray(MyValue6UnloadedValueTypeArray v, MyValue6UnloadedValueTypeArray[] dummy) {
        foo = v.foo + 1;
    }
}

@LooselyConsistentValue
value class MyValue7UnloadedValueTypeArray {
    int foo;

    public MyValue7UnloadedValueTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue8UnloadedValueTypeArray {
    int foo = 123;
    static {
        compiler.valhalla.valuetypes.TestUnloadedValueTypeArray.MyValue8_inited = true;
    }
}

@LooselyConsistentValue
value class MyValue9UnloadedValueTypeArray {
    int foo = 123;
    static {
        compiler.valhalla.valuetypes.TestUnloadedValueTypeArray.MyValue9_inited = true;
    }
}

@LooselyConsistentValue
value class MyValue10UnloadedValueTypeArray {
    int foo = 42;
}

@LooselyConsistentValue
value class MyValue11UnloadedValueTypeArray {
    int foo = 42;
}

public class TestUnloadedValueTypeArray {
    static boolean MyValue8_inited = false;
    static boolean MyValue9_inited = false;

    static MyValue1UnloadedValueTypeArray[] target1() {
        return (MyValue1UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue1UnloadedValueTypeArray.class, 10);
    }

    static void test1() {
        target1();
    }

    static MyValue1UnloadedValueTypeArray[] target1Nullable() {
        return new MyValue1UnloadedValueTypeArray[10];
    }

    static void test1Nullable() {
        target1Nullable();
    }

    static int test2(MyValue2UnloadedValueTypeArray[] arr) {
        if (arr != null) {
            return arr[1].foo;
        } else {
            return 1234;
        }
    }

    static void verifyTest2() {
        int n = 50000;

        int m = 9999;
        for (int i = 0; i < n; i++) {
            m = test2(null);
        }
        Asserts.assertEQ(m, 1234);

        MyValue2UnloadedValueTypeArray[] arr = (MyValue2UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue2UnloadedValueTypeArray.class, 2);
        arr[1] = new MyValue2UnloadedValueTypeArray(5678);
        m = 9999;
        for (int i = 0; i < n; i++) {
            m = test2(arr);
        }
        Asserts.assertEQ(m, 5678);
    }

    static int test2Nullable(MyValue2UnloadedValueTypeArray[] arr) {
        if (arr != null) {
            return arr[1].foo;
        } else {
            return 1234;
        }
    }

    static void verifyTest2Nullable() {
        int n = 50000;

        int m = 9999;
        for (int i = 0; i < n; i++) {
            m = test2Nullable(null);
        }
        Asserts.assertEQ(m, 1234);

        MyValue2UnloadedValueTypeArray[] arr = new MyValue2UnloadedValueTypeArray[2];
        arr[1] = new MyValue2UnloadedValueTypeArray(5678);
        m = 9999;
        for (int i = 0; i < n; i++) {
            m = test2Nullable(arr);
        }
        Asserts.assertEQ(m, 5678);
    }

    static void test3(MyValue3UnloadedValueTypeArray[] arr) {
        if (arr != null) {
            arr[1] = new MyValue3UnloadedValueTypeArray(2345);
        }
    }

    static void verifyTest3() {
        int n = 50000;

        for (int i = 0; i < n; i++) {
            test3(null);
        }

        MyValue3UnloadedValueTypeArray[] arr = (MyValue3UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue3UnloadedValueTypeArray.class, 2);
        for (int i = 0; i < n; i++) {
            test3(arr);
        }
        Asserts.assertEQ(arr[1].foo, 2345);
    }

    static void test3Nullable(MyValue3UnloadedValueTypeArray[] arr) {
        if (arr != null) {
            arr[0] = null;
            arr[1] = new MyValue3UnloadedValueTypeArray(2345);
        }
    }

    static void verifyTest3Nullable() {
        int n = 50000;

        for (int i = 0; i < n; i++) {
            test3Nullable(null);
        }

        MyValue3UnloadedValueTypeArray[] arr = new MyValue3UnloadedValueTypeArray[2];
        for (int i = 0; i < n; i++) {
            test3Nullable(arr);
        }
        Asserts.assertEQ(arr[0], null);
        Asserts.assertEQ(arr[1].foo, 2345);
    }

    static MyValue4UnloadedValueTypeArray[] test4(boolean b) {
        // range check elimination
        if (b) {
            MyValue4UnloadedValueTypeArray[] arr = (MyValue4UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue4UnloadedValueTypeArray.class, 10);
            arr[1] = new MyValue4UnloadedValueTypeArray(2345);
            return arr;
        } else {
            return null;
        }
    }

    static void verifyTest4() {
        int n = 50000;

        for (int i = 0; i < n; i++) {
            test4(false);
        }

        MyValue4UnloadedValueTypeArray[] arr = null;
        for (int i = 0; i < n; i++) {
            arr = test4(true);
        }
        Asserts.assertEQ(arr[1].foo, 2345);
    }

    static MyValue4UnloadedValueTypeArray[] test4Nullable(boolean b) {
        // range check elimination
        if (b) {
            MyValue4UnloadedValueTypeArray[] arr = new MyValue4UnloadedValueTypeArray[10];
            arr[0] = null;
            arr[1] = new MyValue4UnloadedValueTypeArray(2345);
            return arr;
        } else {
            return null;
        }
    }

    static void verifyTest4Nullable() {
        int n = 50000;

        for (int i = 0; i < n; i++) {
            test4Nullable(false);
        }

        MyValue4UnloadedValueTypeArray[] arr = null;
        for (int i = 0; i < n; i++) {
            arr = test4Nullable(true);
        }
        Asserts.assertEQ(arr[0], null);
        Asserts.assertEQ(arr[1].foo, 2345);
        arr[3] = null;
    }

    static Object[] test5(int n) {
        if (n == 0) {
            return null;
        } else if (n == 1) {
            MyValue5UnloadedValueTypeArray[] arr = (MyValue5UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue5UnloadedValueTypeArray.class, 10);
            arr[1] = new MyValue5UnloadedValueTypeArray(12345);
            return arr;
        } else {
            MyValue5UnloadedValueTypeArray[] arr = new MyValue5UnloadedValueTypeArray[10];
            arr[1] = new MyValue5UnloadedValueTypeArray(22345);
            return arr;
        }
    }

    static void verifyTest5() {
        int n = 50000;

        for (int i = 0; i < n; i++) {
            test5(0);
        }

        {
            MyValue5UnloadedValueTypeArray[] arr = null;
            for (int i = 0; i < n; i++) {
                arr = (MyValue5UnloadedValueTypeArray[])test5(1);
            }
            Asserts.assertEQ(arr[1].foo, 12345);
        }
        {
            MyValue5UnloadedValueTypeArray[] arr = null;
            for (int i = 0; i < n; i++) {
                arr = (MyValue5UnloadedValueTypeArray[])test5(2);
            }
            Asserts.assertEQ(arr[1].foo, 22345);
        }
    }

    static Object test6() {
        return new MyValue6UnloadedValueTypeArray(new MyValue6UnloadedValueTypeArray(123), null);
    }

    static void verifyTest6() {
        Object n = test6();
        Asserts.assertEQ(n.toString(), "compiler.valhalla.valuetypes.MyValue6UnloadedValueTypeArray@" + Integer.toHexString(n.hashCode()));
    }

    static int test7(MyValue7UnloadedValueTypeArray[][] arr) {
        if (arr != null) {
            return arr[0][1].foo;
        } else {
            return 1234;
        }
    }

    static void verifyTest7() {
        int n = 50000;

        int m = 9999;
        for (int i = 0; i < n; i++) {
            m = test7(null);
        }
        Asserts.assertEQ(m, 1234);

        MyValue7UnloadedValueTypeArray[][] arr = { (MyValue7UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue7UnloadedValueTypeArray.class, 2),
                             (MyValue7UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue7UnloadedValueTypeArray.class, 2) };
        Object[] oa = arr[1];
        Asserts.assertEQ(oa[0], null);

        arr[0][1] = new MyValue7UnloadedValueTypeArray(5678);
        m = 9999;
        for (int i = 0; i < n; i++) {
            m = test7(arr);
        }
        Asserts.assertEQ(m, 5678);
    }

    static int test7Nullable(MyValue7UnloadedValueTypeArray[][] arr) {
        if (arr != null) {
            arr[0][0] = null;
            return arr[0][1].foo;
        } else {
            return 1234;
        }
    }

    static void verifyTest7Nullable() {
        int n = 50000;

        int m = 9999;
        for (int i = 0; i < n; i++) {
            m = test7Nullable(null);
        }
        Asserts.assertEQ(m, 1234);

        MyValue7UnloadedValueTypeArray[][] arr = new MyValue7UnloadedValueTypeArray[2][2];
        Object[] oa = arr[1];
        Asserts.assertEQ(oa[0], null);

        arr[0][1] = new MyValue7UnloadedValueTypeArray(5678);
        m = 9999;
        for (int i = 0; i < n; i++) {
            m = test7Nullable(arr);
        }
        Asserts.assertEQ(m, 5678);
        Asserts.assertEQ(arr[0][0], null);
    }

    static void test8() {
        MyValue8UnloadedValueTypeArray a[] = new MyValue8UnloadedValueTypeArray[0];
        Asserts.assertEQ(MyValue8_inited, false);

        MyValue8UnloadedValueTypeArray b[] = (MyValue8UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue8UnloadedValueTypeArray.class, 0);
        Asserts.assertEQ(MyValue8_inited, false); // creation of a nullable array doesn't trigger the initialization
                                                  // of the element's class (same behavior as anewarray and Array.newInstance())
    }

    static void test9() {
        MyValue9UnloadedValueTypeArray a[][] = new MyValue9UnloadedValueTypeArray[10][0];
        Asserts.assertEQ(MyValue9_inited, false);

        a[0] = (MyValue9UnloadedValueTypeArray[])ValueClass.newNullableAtomicArray(MyValue9UnloadedValueTypeArray.class, 0);
        Asserts.assertEQ(MyValue9_inited, false); // creation of a nullable array doesn't trigger the initialization
                                                  // of the element's class (same behavior as anewarray and Array.newInstance())
    }

    static void test10(MyValue10UnloadedValueTypeArray dummy) {
        MyValue10UnloadedValueTypeArray[][] a = { (MyValue10UnloadedValueTypeArray[])ValueClass.newNullRestrictedNonAtomicArray(MyValue10UnloadedValueTypeArray.class, 1, new MyValue10UnloadedValueTypeArray()) };
        if (a[0][0].equals(null)) throw new RuntimeException("test10 failed");
        Asserts.assertNE(a[0][0], null);
    }

    static void test11(MyValue10UnloadedValueTypeArray dummy) {
        MyValue11UnloadedValueTypeArray[][] a = new MyValue11UnloadedValueTypeArray[1][1];
        Asserts.assertEQ(a[0][0], null);
    }

    static public void main(String[] args) {
        test1();
        test1Nullable();
        verifyTest2();
        verifyTest2Nullable();
        verifyTest3();
        verifyTest3Nullable();
        verifyTest4();
        verifyTest4Nullable();
        verifyTest5();
        verifyTest6();
        verifyTest7();
        verifyTest7Nullable();
        test8();
        test9();
        test10(null);
        test11(null);
    }
}
