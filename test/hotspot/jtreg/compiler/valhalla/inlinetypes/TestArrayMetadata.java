/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress test the VM internal metadata for arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/timeout=300 compiler.valhalla.inlinetypes.TestArrayMetadata
 */

/*
 * @test id=stress-reflective-code
 * @summary Stress test the VM internal metadata for arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm/timeout=300 -XX:+IgnoreUnrecognizedVMOptions -XX:+StressReflectiveCode
 *                               compiler.valhalla.inlinetypes.TestArrayMetadata
 */

/*
 * @test id=no-monomorphic
 * @summary Stress test the VM internal metadata for arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm/timeout=300 -XX:+IgnoreUnrecognizedVMOptions
 *                               -XX:-MonomorphicArrayCheck -XX:-OmitStackTraceInFastThrow
 *                               compiler.valhalla.inlinetypes.TestArrayMetadata
 */

/*
 * @test id=xcomp
 * @summary Stress test the VM internal metadata for arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm/timeout=300 -Xcomp compiler.valhalla.inlinetypes.TestArrayMetadata
 */

/*
 * @test id=expand-zero
 * @summary Stress test the VM internal metadata for arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm/timeout=300 -XX:MultiArrayExpandLimit=0 compiler.valhalla.inlinetypes.TestArrayMetadata
 */

/*
 * @test id=co-di-test
 * @summary Stress test the VM internal metadata for arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm/timeout=300 -Xbatch -XX:CompileCommand=compileonly,*TestArrayMetadata::*
 *                               -XX:CompileCommand=dontinline,*TestArrayMetadata::test*
 *                               compiler.valhalla.inlinetypes.TestArrayMetadata
 */

/*
 * @test id=co-di
 * @summary Stress test the VM internal metadata for arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm/timeout=300 -Xbatch -XX:CompileCommand=compileonly,*TestArrayMetadata::*
 *                               -XX:CompileCommand=dontinline,*TestArrayMetadata::*
 *                               compiler.valhalla.inlinetypes.TestArrayMetadata
 */

/*
 * @test id=co-main-di-test
 * @summary Stress test the VM internal metadata for arrays.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm/timeout=300 -Xbatch -XX:CompileCommand=compileonly,*TestArrayMetadata::main
 *                               -XX:CompileCommand=dontinline,*TestArrayMetadata::test*
 *                                compiler.valhalla.inlinetypes.TestArrayMetadata
 */

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Array;
import java.util.Arrays;

import jdk.internal.value.ValueClass;
import jdk.test.lib.Asserts;

public class TestArrayMetadata {

    static interface MyInterface {

    }

    public static Object[] testArrayAllocation1() {
        return new Object[1];
    }

    public static Object[][] testArrayAllocation2() {
        return new Object[1][1];
    }

    public static Class getClass1() {
        return Object.class;
    }

    public static Object[] testArrayAllocation3() {
        return (Object[])Array.newInstance(getClass1(), 1);
    }

    public static Class getClass2() {
        return TestArrayMetadata.class;
    }

    public static Object[] testArrayAllocation4() {
        return (TestArrayMetadata[])Array.newInstance(getClass2(), 1);
    }

    public static Object[] testArrayAllocation5() {
        return new MyInterface[1];
    }

    public static Object[] testArrayAllocation6() {
        return new Integer[1];
    }

    public static Object[] testCheckcast1(Object arg) {
        return (Object[])arg;
    }

    public static Object[] testCheckcast2(Object arg) {
        return (TestArrayMetadata[])arg;
    }

    public static Cloneable testCheckcast3(Object arg) {
        return (Cloneable)arg;
    }

    public static Object testCheckcast4(Object arg) {
        return (Object)arg;
    }

    public static Object[][] testCheckcast5(Object arg) {
        return (Object[][])arg;
    }

    public static MyInterface[] testCheckcast6(Object arg) {
        return (MyInterface[])arg;
    }

    public static Integer[] testCheckcast7(Object arg) {
        return (Integer[])arg;
    }

    public static Class getArrayClass1() {
        return Object[].class;
    }

    public static boolean testIsInstance1(Object arg) {
        return getArrayClass1().isInstance(arg);
    }

    public static Class getArrayClass2() {
        return TestArrayMetadata[].class;
    }

    public static boolean testIsInstance2(Object arg) {
        return getArrayClass2().isInstance(arg);
    }

    public static Class getArrayClass3() {
        return Cloneable.class;
    }

    public static boolean testIsInstance3(Object arg) {
        return getArrayClass3().isInstance(arg);
    }

    public static Class getArrayClass4() {
        return Object.class;
    }

    public static boolean testIsInstance4(Object arg) {
        return getArrayClass4().isInstance(arg);
    }

    public static Class getArrayClass5() {
        return Object[][].class;
    }

    public static boolean testIsInstance5(Object arg) {
        return getArrayClass5().isInstance(arg);
    }

    static value class MyIntegerValue {
        int x = 42;
    }

    public static Class getArrayClass6() {
        return MyIntegerValue[].class;
    }

    public static boolean testIsInstance6(Object arg) {
        return getArrayClass6().isInstance(arg);
    }

    public static Object[] testCopyOf1(Object[] array, Class<? extends Object[]> clazz) {
        return Arrays.copyOf(array, 1, clazz);
    }

    public static Object[] testCopyOf2(Object[] array) {
        return Arrays.copyOf(array, array.length, array.getClass());
    }

    public static Class testGetSuperclass1(Object[] array) {
        return array.getClass().getSuperclass();
    }

    public static Class testGetSuperclass2() {
        return Object[].class.getSuperclass();
    }

    public static Class testGetSuperclass3() {
        return TestArrayMetadata[].class.getSuperclass();
    }

    public static Object[] testClassCast1(Object array) {
        return Object[].class.cast(array);
    }

    public static Object[] testClassCast2(Object array) {
        return TestArrayMetadata[].class.cast(array);
    }

    public static Object testClassCast3(Class c, Object array) {
        return c.cast(array);
    }

    public static void test5(Object[] array, Object obj) {
        array[0] = obj;
    }

    public static void test6(Object[][] array, Object[] obj) {
        array[0] = obj;
    }

    public static void test7(Object[][][] array, Object[][] obj) {
        array[0] = obj;
    }

    public static void test8(Object[][][][] array, Object[][][] obj) {
        array[0] = obj;
    }

    public static void test9(Object[][] array) {
        array[0] = (Object[]) new Object[0];
    }

    public static void test10(Object[][] array) {
        array[0] = new String[0];
    }

    public static boolean testIsAssignableFrom1(Class clazz1, Class clazz2) {
        return clazz1.isAssignableFrom(clazz2);
    }

    public static boolean testIsAssignableFrom2(Object obj, Class clazz) {
        return obj.getClass().isAssignableFrom(clazz);
    }

    public static boolean testIsAssignableFrom3(Class clazz, Object obj) {
        return clazz.isAssignableFrom(obj.getClass());
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100_000; ++i) {
            Object[] array1 = testArrayAllocation1();
            Object[][] array2 = testArrayAllocation2();
            Object[] array3 = testArrayAllocation3();
            Object[] array4 = testArrayAllocation4();
            Object[] array5 = testArrayAllocation5();
            Object[] array6 = testArrayAllocation6();

            testCheckcast1(new Object[0]);
            testCheckcast1(new TestArrayMetadata[0]);
            testCheckcast1(array1);
            testCheckcast1(array3);
            testCheckcast1(array4);
            testCheckcast1(array5);
            testCheckcast1(array6);
            try {
                testCheckcast1(42);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }
            try {
                testCheckcast2(new Object[0]);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }
            try {
                testCheckcast2(array1);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }
            testCheckcast2(new TestArrayMetadata[0]);
            testCheckcast2(array4);
            try {
                testCheckcast2(array5);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }
            try {
                testCheckcast2(42);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }
            testCheckcast3(new Object[0]);
            testCheckcast3(new TestArrayMetadata[0]);
            testCheckcast3(array1);
            testCheckcast3(array3);
            testCheckcast3(array4);
            testCheckcast3(array5);
            testCheckcast3(array6);
            try {
                testCheckcast3(42);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }

            testCheckcast4(new Object[0]);
            testCheckcast4(new TestArrayMetadata[0]);
            testCheckcast4(array1);
            testCheckcast4(array3);
            testCheckcast4(array4);
            testCheckcast4(array5);
            testCheckcast4(array6);
            testCheckcast4(42);

            testCheckcast5(new Object[0][0]);
            testCheckcast5(new TestArrayMetadata[0][0]);
            testCheckcast5(array2);
            try {
                testCheckcast5(42);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }

            testCheckcast6(array5);

            testCheckcast7(array6);

            testCopyOf1(new Object[1], Object[].class);
            testCopyOf1(new TestArrayMetadata[1], Object[].class);
            testCopyOf1(new Object[1], TestArrayMetadata[].class);
            testCopyOf1(new TestArrayMetadata[1], TestArrayMetadata[].class);
            try {
                testCopyOf1(new TestArrayMetadata[]{new TestArrayMetadata()}, Integer[].class);
                throw new RuntimeException("No exception thrown");
            } catch (ArrayStoreException e) {
                // Expected
            }

            testCopyOf2(new Object[1]);
            testCopyOf2(new TestArrayMetadata[1]);

            testClassCast1(new Object[0]);
            testClassCast1(new TestArrayMetadata[0]);
            try {
                testClassCast1(new int[0]);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }

            testClassCast2(new TestArrayMetadata[0]);
            try {
                testClassCast2(new Object[0]);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }
            try {
                testClassCast2(new int[0]);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }

            testClassCast3(TestArrayMetadata[].class, new TestArrayMetadata[0]);
            testClassCast3(Object[].class, new TestArrayMetadata[0]);
            testClassCast3(Object[].class, new Object[0]);
            testClassCast3(int[].class, new int[0]);
            try {
                testClassCast3(TestArrayMetadata[].class, new int[0]);
                throw new RuntimeException("No exception thrown");
            } catch (ClassCastException e) {
                // Expected
            }

            Asserts.assertEQ(testGetSuperclass1(new Object[1]), Object.class);
            Asserts.assertEQ(testGetSuperclass1(new TestArrayMetadata[1]), Object.class);
            Asserts.assertEQ(testGetSuperclass2(), Object.class);
            Asserts.assertEQ(testGetSuperclass3(), Object.class);

            MyIntegerValue[] nullFreeArray6 = (MyIntegerValue[])ValueClass.newNullRestrictedNonAtomicArray(MyIntegerValue.class, 3, new MyIntegerValue());
            MyIntegerValue[] nullFreeAtomicArray6 = (MyIntegerValue[])ValueClass.newNullRestrictedAtomicArray(MyIntegerValue.class, 3, new MyIntegerValue());
            MyIntegerValue[] nullableArray6 = new MyIntegerValue[3];
            MyIntegerValue[] nullableAtomicArray6 = (MyIntegerValue[])ValueClass.newNullableAtomicArray(MyIntegerValue.class, 3);

            Asserts.assertTrue(testIsInstance1(new Object[0]));
            Asserts.assertTrue(testIsInstance1(new TestArrayMetadata[0]));
            Asserts.assertTrue(testIsInstance1(nullFreeArray6));
            Asserts.assertTrue(testIsInstance1(nullFreeAtomicArray6));
            Asserts.assertTrue(testIsInstance1(nullableArray6));
            Asserts.assertTrue(testIsInstance1(nullableAtomicArray6));
            Asserts.assertFalse(testIsInstance1(42));
            Asserts.assertTrue(testIsInstance1(array1));
            Asserts.assertTrue(testIsInstance1(array3));
            Asserts.assertTrue(testIsInstance1(array4));
            Asserts.assertTrue(testIsInstance1(array5));
            Asserts.assertTrue(testIsInstance1(array6));

            Asserts.assertFalse(testIsInstance2(new Object[0]));
            Asserts.assertTrue(testIsInstance2(new TestArrayMetadata[0]));
            Asserts.assertFalse(testIsInstance2(42));
            Asserts.assertFalse(testIsInstance2(array1));
            Asserts.assertFalse(testIsInstance2(array3));
            Asserts.assertTrue(testIsInstance2(array4));
            Asserts.assertFalse(testIsInstance2(array5));
            Asserts.assertFalse(testIsInstance2(array6));

            Asserts.assertTrue(testIsInstance3(new Object[0]));
            Asserts.assertTrue(testIsInstance3(new TestArrayMetadata[0]));
            Asserts.assertTrue(testIsInstance3(nullFreeArray6));
            Asserts.assertTrue(testIsInstance3(nullFreeAtomicArray6));
            Asserts.assertTrue(testIsInstance3(nullableArray6));
            Asserts.assertTrue(testIsInstance3(nullableAtomicArray6));
            Asserts.assertFalse(testIsInstance3(42));
            Asserts.assertTrue(testIsInstance3(array1));
            Asserts.assertTrue(testIsInstance3(array3));
            Asserts.assertTrue(testIsInstance3(array4));
            Asserts.assertTrue(testIsInstance3(array5));
            Asserts.assertTrue(testIsInstance3(array6));

            Asserts.assertTrue(testIsInstance4(new Object[0]));
            Asserts.assertTrue(testIsInstance4(new TestArrayMetadata[0]));
            Asserts.assertTrue(testIsInstance4(nullFreeArray6));
            Asserts.assertTrue(testIsInstance4(nullFreeAtomicArray6));
            Asserts.assertTrue(testIsInstance4(nullableArray6));
            Asserts.assertTrue(testIsInstance4(nullableAtomicArray6));
            Asserts.assertTrue(testIsInstance4(42));
            Asserts.assertTrue(testIsInstance4(array1));
            Asserts.assertTrue(testIsInstance4(array3));
            Asserts.assertTrue(testIsInstance4(array4));
            Asserts.assertTrue(testIsInstance4(array5));
            Asserts.assertTrue(testIsInstance4(array6));

            Asserts.assertTrue(testIsInstance5(new Object[0][0]));
            Asserts.assertTrue(testIsInstance5(new TestArrayMetadata[0][0]));
            Asserts.assertTrue(testIsInstance5(array2));
            Asserts.assertFalse(testIsInstance5(42));

            Asserts.assertFalse(testIsInstance6(new Object[0]));
            Asserts.assertFalse(testIsInstance6(42));
            Asserts.assertTrue(testIsInstance6(nullFreeArray6));
            Asserts.assertTrue(testIsInstance6(nullFreeAtomicArray6));
            Asserts.assertTrue(testIsInstance6(nullableArray6));
            Asserts.assertTrue(testIsInstance6(nullableAtomicArray6));

            test5(new Object[1], new TestArrayMetadata());
            test5((new Object[1][1])[0], (new TestArrayMetadata[1])[0]);
            test5(new String[1], "42");
            test5((new String[1][1])[0], (new String[1])[0]);
            test5(array1, new TestArrayMetadata());
            test5(array3, new TestArrayMetadata());
            test5(array4, new TestArrayMetadata());
            try {
                test5(array5, new TestArrayMetadata());
                throw new RuntimeException("No exception thrown");
            } catch (ArrayStoreException e) {
                // Expected
            }

            test6(new Object[1][1], new TestArrayMetadata[0]);
            test6((new Object[1][1][1])[0], (new TestArrayMetadata[1][0])[0]);
            test6(new String[1][1], new String[0]);
            test6((new String[1][1][1])[0], (new String[1][0])[0]);
            test6(array2, new TestArrayMetadata[0]);

            test7(new Object[1][1][1], new TestArrayMetadata[0][0]);
            test7((new Object[1][1][1][1])[0], (new TestArrayMetadata[1][0][0])[0]);
            test7(new String[1][1][1], new String[0][0]);
            test7((new String[1][1][1][1])[0], (new String[1][0][0])[0]);

            test8(new Object[1][1][1][1], new TestArrayMetadata[0][0][0]);
            test8((new Object[1][1][1][1][1])[0], (new TestArrayMetadata[1][0][0][0])[0]);
            test8(new String[1][1][1][1], new String[0][0][0]);
            test8((new String[1][1][1][1][1])[0], (new String[1][0][0][0])[0]);

            test9(new Object[1][1]);
            test9(array2);

            test10(new String[1][1]);
            test10(array2);

            Asserts.assertTrue(testIsAssignableFrom1(Object[].class, Object[].class));
            Asserts.assertTrue(testIsAssignableFrom1(Object[].class, TestArrayMetadata[].class));
            Asserts.assertTrue(testIsAssignableFrom1(int[].class, int[].class));
            Asserts.assertFalse(testIsAssignableFrom1(Object[].class, int[].class));
            Asserts.assertFalse(testIsAssignableFrom1(Object[].class, TestArrayMetadata.class));

            Asserts.assertTrue(testIsAssignableFrom2(new Object[0], Object[].class));
            Asserts.assertTrue(testIsAssignableFrom2(new Object[0], TestArrayMetadata[].class));
            Asserts.assertTrue(testIsAssignableFrom2(new int[0], int[].class));
            Asserts.assertFalse(testIsAssignableFrom2(new Object[0], int[].class));
            Asserts.assertFalse(testIsAssignableFrom2(new Object[0], TestArrayMetadata.class));

            Asserts.assertTrue(testIsAssignableFrom3(Object[].class, new Object[0]));
            Asserts.assertTrue(testIsAssignableFrom3(Object[].class, new TestArrayMetadata[0]));
            Asserts.assertTrue(testIsAssignableFrom3(int[].class, new int[0]));
            Asserts.assertFalse(testIsAssignableFrom3(Object[].class, new int[0]));
            Asserts.assertFalse(testIsAssignableFrom3(Object[].class, new TestArrayMetadata()));
        }
    }
}
