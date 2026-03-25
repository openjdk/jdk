/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray::test*
 *                   compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray
 */

/*
 * @test id=no-flattening
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-UseArrayFlattening
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray::test*
 *                   compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray
 */

/*
 * @test id=xcomp
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray
 */

/*
 * @test id=xcomp-no-flattening
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-UseArrayFlattening
 *                   compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray
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
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray::test*
 *                   compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray
 */

/*
 * @test id=c2-no-flattening
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:-UseArrayFlattening
 *                   -XX:CompileCommand=compileonly,compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray::test*
 *                   compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray
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
 *                   compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray
 */

/*
 * @test id=xcomp-c2-no-flattening
 * @bug 8182997 8214898
 * @library /test/lib
 * @summary Test the handling of arrays of unloaded value classes.
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:-UseArrayFlattening
 *                   compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray
 */

package compiler.valhalla.inlinetypes;

import jdk.test.lib.Asserts;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;

@LooselyConsistentValue
value class MyValue1UnloadedInlineTypeArray {
    int foo;

    private MyValue1UnloadedInlineTypeArray() {
        foo = 0x42;
    }
}

@LooselyConsistentValue
value class MyValue2UnloadedInlineTypeArray {
    int foo;

    public MyValue2UnloadedInlineTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue3UnloadedInlineTypeArray {
    int foo;

    public MyValue3UnloadedInlineTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue4UnloadedInlineTypeArray {
    int foo;

    public MyValue4UnloadedInlineTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue5UnloadedInlineTypeArray {
    int foo;

    public MyValue5UnloadedInlineTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue6UnloadedInlineTypeArray {
    int foo;

    public MyValue6UnloadedInlineTypeArray(int n) {
        foo = n;
    }

    public MyValue6UnloadedInlineTypeArray(MyValue6UnloadedInlineTypeArray v, MyValue6UnloadedInlineTypeArray[] dummy) {
        foo = v.foo + 1;
    }
}

@LooselyConsistentValue
value class MyValue7UnloadedInlineTypeArray {
    int foo;

    public MyValue7UnloadedInlineTypeArray(int n) {
        foo = n;
    }
}

@LooselyConsistentValue
value class MyValue8UnloadedInlineTypeArray {
    int foo = 123;
    static {
        compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray.MyValue8_inited = true;
    }
}

@LooselyConsistentValue
value class MyValue9UnloadedInlineTypeArray {
    int foo = 123;
    static {
        compiler.valhalla.inlinetypes.TestUnloadedInlineTypeArray.MyValue9_inited = true;
    }
}

@LooselyConsistentValue
value class MyValue10UnloadedInlineTypeArray {
    int foo = 42;
}

@LooselyConsistentValue
value class MyValue11UnloadedInlineTypeArray {
    int foo = 42;
}

public class TestUnloadedInlineTypeArray {
    static boolean MyValue8_inited = false;
    static boolean MyValue9_inited = false;

    static MyValue1UnloadedInlineTypeArray[] target1() {
        return (MyValue1UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue1UnloadedInlineTypeArray.class, 10);
    }

    static void test1() {
        target1();
    }

    static MyValue1UnloadedInlineTypeArray[] target1Nullable() {
        return new MyValue1UnloadedInlineTypeArray[10];
    }

    static void test1Nullable() {
        target1Nullable();
    }

    static int test2(MyValue2UnloadedInlineTypeArray[] arr) {
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

        MyValue2UnloadedInlineTypeArray[] arr = (MyValue2UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue2UnloadedInlineTypeArray.class, 2);
        arr[1] = new MyValue2UnloadedInlineTypeArray(5678);
        m = 9999;
        for (int i = 0; i < n; i++) {
            m = test2(arr);
        }
        Asserts.assertEQ(m, 5678);
    }

    static int test2Nullable(MyValue2UnloadedInlineTypeArray[] arr) {
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

        MyValue2UnloadedInlineTypeArray[] arr = new MyValue2UnloadedInlineTypeArray[2];
        arr[1] = new MyValue2UnloadedInlineTypeArray(5678);
        m = 9999;
        for (int i = 0; i < n; i++) {
            m = test2Nullable(arr);
        }
        Asserts.assertEQ(m, 5678);
    }

    static void test3(MyValue3UnloadedInlineTypeArray[] arr) {
        if (arr != null) {
            arr[1] = new MyValue3UnloadedInlineTypeArray(2345);
        }
    }

    static void verifyTest3() {
        int n = 50000;

        for (int i = 0; i < n; i++) {
            test3(null);
        }

        MyValue3UnloadedInlineTypeArray[] arr = (MyValue3UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue3UnloadedInlineTypeArray.class, 2);
        for (int i = 0; i < n; i++) {
            test3(arr);
        }
        Asserts.assertEQ(arr[1].foo, 2345);
    }

    static void test3Nullable(MyValue3UnloadedInlineTypeArray[] arr) {
        if (arr != null) {
            arr[0] = null;
            arr[1] = new MyValue3UnloadedInlineTypeArray(2345);
        }
    }

    static void verifyTest3Nullable() {
        int n = 50000;

        for (int i = 0; i < n; i++) {
            test3Nullable(null);
        }

        MyValue3UnloadedInlineTypeArray[] arr = new MyValue3UnloadedInlineTypeArray[2];
        for (int i = 0; i < n; i++) {
            test3Nullable(arr);
        }
        Asserts.assertEQ(arr[0], null);
        Asserts.assertEQ(arr[1].foo, 2345);
    }

    static MyValue4UnloadedInlineTypeArray[] test4(boolean b) {
        // range check elimination
        if (b) {
            MyValue4UnloadedInlineTypeArray[] arr = (MyValue4UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue4UnloadedInlineTypeArray.class, 10);
            arr[1] = new MyValue4UnloadedInlineTypeArray(2345);
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

        MyValue4UnloadedInlineTypeArray[] arr = null;
        for (int i = 0; i < n; i++) {
            arr = test4(true);
        }
        Asserts.assertEQ(arr[1].foo, 2345);
    }

    static MyValue4UnloadedInlineTypeArray[] test4Nullable(boolean b) {
        // range check elimination
        if (b) {
            MyValue4UnloadedInlineTypeArray[] arr = new MyValue4UnloadedInlineTypeArray[10];
            arr[0] = null;
            arr[1] = new MyValue4UnloadedInlineTypeArray(2345);
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

        MyValue4UnloadedInlineTypeArray[] arr = null;
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
            MyValue5UnloadedInlineTypeArray[] arr = (MyValue5UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue5UnloadedInlineTypeArray.class, 10);
            arr[1] = new MyValue5UnloadedInlineTypeArray(12345);
            return arr;
        } else {
            MyValue5UnloadedInlineTypeArray[] arr = new MyValue5UnloadedInlineTypeArray[10];
            arr[1] = new MyValue5UnloadedInlineTypeArray(22345);
            return arr;
        }
    }

    static void verifyTest5() {
        int n = 50000;

        for (int i = 0; i < n; i++) {
            test5(0);
        }

        {
            MyValue5UnloadedInlineTypeArray[] arr = null;
            for (int i = 0; i < n; i++) {
                arr = (MyValue5UnloadedInlineTypeArray[])test5(1);
            }
            Asserts.assertEQ(arr[1].foo, 12345);
        }
        {
            MyValue5UnloadedInlineTypeArray[] arr = null;
            for (int i = 0; i < n; i++) {
                arr = (MyValue5UnloadedInlineTypeArray[])test5(2);
            }
            Asserts.assertEQ(arr[1].foo, 22345);
        }
    }

    static Object test6() {
        return new MyValue6UnloadedInlineTypeArray(new MyValue6UnloadedInlineTypeArray(123), null);
    }

    static void verifyTest6() {
        Object n = test6();
        Asserts.assertEQ(n.toString(), "compiler.valhalla.inlinetypes.MyValue6UnloadedInlineTypeArray@" + Integer.toHexString(n.hashCode()));
    }

    static int test7(MyValue7UnloadedInlineTypeArray[][] arr) {
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

        MyValue7UnloadedInlineTypeArray[][] arr = { (MyValue7UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue7UnloadedInlineTypeArray.class, 2),
                             (MyValue7UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue7UnloadedInlineTypeArray.class, 2) };
        Object[] oa = arr[1];
        Asserts.assertEQ(oa[0], null);

        arr[0][1] = new MyValue7UnloadedInlineTypeArray(5678);
        m = 9999;
        for (int i = 0; i < n; i++) {
            m = test7(arr);
        }
        Asserts.assertEQ(m, 5678);
    }

    static int test7Nullable(MyValue7UnloadedInlineTypeArray[][] arr) {
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

        MyValue7UnloadedInlineTypeArray[][] arr = new MyValue7UnloadedInlineTypeArray[2][2];
        Object[] oa = arr[1];
        Asserts.assertEQ(oa[0], null);

        arr[0][1] = new MyValue7UnloadedInlineTypeArray(5678);
        m = 9999;
        for (int i = 0; i < n; i++) {
            m = test7Nullable(arr);
        }
        Asserts.assertEQ(m, 5678);
        Asserts.assertEQ(arr[0][0], null);
    }

    static void test8() {
        MyValue8UnloadedInlineTypeArray a[] = new MyValue8UnloadedInlineTypeArray[0];
        Asserts.assertEQ(MyValue8_inited, false);

        MyValue8UnloadedInlineTypeArray b[] = (MyValue8UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue8UnloadedInlineTypeArray.class, 0);
        Asserts.assertEQ(MyValue8_inited, true);
    }

    static void test9() {
        MyValue9UnloadedInlineTypeArray a[][] = new MyValue9UnloadedInlineTypeArray[10][0];
        Asserts.assertEQ(MyValue9_inited, false);

        a[0] = (MyValue9UnloadedInlineTypeArray[])ValueClass.newNullableAtomicArray(MyValue9UnloadedInlineTypeArray.class, 0);
        Asserts.assertEQ(MyValue9_inited, true);
    }

    static void test10(MyValue10UnloadedInlineTypeArray dummy) {
        MyValue10UnloadedInlineTypeArray[][] a = { (MyValue10UnloadedInlineTypeArray[])ValueClass.newNullRestrictedNonAtomicArray(MyValue10UnloadedInlineTypeArray.class, 1, new MyValue10UnloadedInlineTypeArray()) };
        if (a[0][0].equals(null)) throw new RuntimeException("test10 failed");
        Asserts.assertNE(a[0][0], null);
    }

    static void test11(MyValue10UnloadedInlineTypeArray dummy) {
        MyValue11UnloadedInlineTypeArray[][] a = new MyValue11UnloadedInlineTypeArray[1][1];
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
