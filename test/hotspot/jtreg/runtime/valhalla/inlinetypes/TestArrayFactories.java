/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package runtime.valhalla.inlinetypes;

import java.lang.reflect.Method;
import java.util.ArrayList;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test argument validation and behavior of special arrays factories
 * @enablePreview
 * @library /test/lib /
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main runtime.valhalla.inlinetypes.TestArrayFactories
 */

public class TestArrayFactories {

    @LooselyConsistentValue
    static abstract value class MyAbsVal {
        short s = (short)0;
    }

    @LooselyConsistentValue
    static value class MyVal extends MyAbsVal {
        boolean b = false;
    }

    // Testing negative length

    static void test_0() {
        try {
            var a = ValueClass.newNullRestrictedAtomicArray(MyVal.class, -1, new MyVal());
            throw new RuntimeException("Missing NegativeArraySizeException");
        } catch (NegativeArraySizeException e) { }
    }

    static void test_1() {
        try {
            var a = ValueClass.newNullRestrictedNonAtomicArray(MyVal.class, Integer.MIN_VALUE , new MyVal());
            throw new RuntimeException("Missing NegativeArraySizeException");
        } catch (NegativeArraySizeException e) { }
    }

    static void test_2() {
        try {
            var a = ValueClass.newNullableAtomicArray(MyVal.class, -100000);
            throw new RuntimeException("Missing NegativeArraySizeException");
        } catch (NegativeArraySizeException e) { }
    }

    static void test_3() {
        try {
            var a = ValueClass.newReferenceArray(MyVal.class, -2);
            throw new RuntimeException("Missing NegativeArraySizeException");
        } catch (NegativeArraySizeException e) { }
    }

    // Testing null class argument

    static void test_4() {
        try {
            var a = ValueClass.newNullRestrictedAtomicArray(null, 1, new MyVal());
            throw new RuntimeException("Missing NullPointerException");
        } catch (NullPointerException e) { }
    }

    static void test_5() {
        try {
            var a = ValueClass.newNullRestrictedNonAtomicArray(null, 1, new MyVal());
            throw new RuntimeException("Missing NullPointerException");
        } catch (NullPointerException e) { }
    }

    static void test_6() {
        try {
            var a = ValueClass.newNullableAtomicArray(null, 1);
            throw new RuntimeException("Missing NullPointerException");
        } catch (NullPointerException e) { }
    }

    static void test_7() {
        try {
            var a = ValueClass.newReferenceArray(null, 1);
            throw new RuntimeException("Missing NullPointerException");
        } catch (NullPointerException e) { }
    }

    // Abstract value class as element type

    static void test_8() {
        try {
            var a = ValueClass.newNullRestrictedAtomicArray(MyAbsVal.class, 1, new MyVal());
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    static void test_9() {
        try {
            var a = ValueClass.newNullRestrictedNonAtomicArray(MyAbsVal.class, 1, new MyVal());
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    static void test_10() {
        try {
            var a = ValueClass.newNullableAtomicArray(MyAbsVal.class, 1);
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    static void test_11() {
        try {
            var a = ValueClass.newReferenceArray(MyAbsVal.class, 1);
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    // Identity class as element type

    static void test_12() {
        try {
            var a = ValueClass.newNullRestrictedAtomicArray(String.class, 1, new String(""));
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    static void test_13() {
        try {
            var a = ValueClass.newNullRestrictedNonAtomicArray(String.class, 1, new String(""));
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    static void test_14() {
        try {
            var a = ValueClass.newNullableAtomicArray(String.class, 1);
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    static void test_15() {
        try {
            var a = ValueClass.newReferenceArray(String.class, 1);
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    // Element type vs initial value mismatch

    static value class MyVal1 {
      char c = '0';
    }

    static void test_16() {
      try {
          var a = ValueClass.newNullRestrictedAtomicArray(MyVal.class, 1, new MyVal1());
          throw new RuntimeException("Missing IllegalArgumentException");
      } catch (IllegalArgumentException e) { }
    }

    static void test_17() {
      try {
            var a = ValueClass.newNullRestrictedNonAtomicArray(MyVal.class, 1, new MyVal1());
            throw new RuntimeException("Missing IllegalArgumentException");
        } catch (IllegalArgumentException e) { }
    }

    // Testing element type initialization

    static boolean initFlag0 = false;

    static value class MyVal2 {

        static {
            TestArrayFactories.initFlag0 = true;
        }

        int i = 0;
    }

    static void test_18() {
        Asserts.assertFalse(TestArrayFactories.initFlag0);
        var a = ValueClass.newNullableAtomicArray(MyVal2.class, 1);
        Asserts.assertFalse(TestArrayFactories.initFlag0);
    }

    static boolean initFlag1 = false;

    static value class MyVal3 {

        static {
            TestArrayFactories.initFlag1 = true;
        }

        int i = 0;
    }

    static void test_19() {
        Asserts.assertFalse(TestArrayFactories.initFlag1);
        var a = ValueClass.newReferenceArray(MyVal3.class, 1);
        Asserts.assertFalse(TestArrayFactories.initFlag1);
    }

    // Array creation with an element type in an initialization error state

    static MyVal4 val4;
    static boolean fail = true;

    static value class MyVal4 {
      int i = 0;

      static {
          TestArrayFactories.val4 = new MyVal4();
          if (TestArrayFactories.fail) throw new RuntimeException();
      }
    }

    static void test_20() {
        try {
            var v = new MyVal4();
            throw new RuntimeException("Missing class initialization failure");
        } catch (ExceptionInInitializerError|NoClassDefFoundError e) { }
        var a = ValueClass.newNullRestrictedAtomicArray(MyVal4.class, 1, val4);
        Asserts.assertNotNull(a);
    }

    static void test_21() {
        try {
            var v = new MyVal4();
            throw new RuntimeException("Missing class initialization failure");
        } catch (ExceptionInInitializerError|NoClassDefFoundError e) { }
        var a = ValueClass.newNullRestrictedNonAtomicArray(MyVal4.class, 1, val4);
        Asserts.assertNotNull(a);
    }

    static void test_22() {
        try {
            var v = new MyVal4();
            throw new RuntimeException("Missing class initialization failure");
        } catch (ExceptionInInitializerError|NoClassDefFoundError e) { }
        var a = ValueClass.newNullableAtomicArray(MyVal4.class, 1);
        Asserts.assertNotNull(a);
    }

    static void test_23() {
        try {
            var v = new MyVal4();
            throw new RuntimeException("Missing class initialization failure");
        } catch (ExceptionInInitializerError|NoClassDefFoundError e) { }
        var a = ValueClass.newReferenceArray(MyVal4.class, 1);
        Asserts.assertNotNull(a);
    }

    // Test array creation with a null initial value

    static void test_24() {
        try {
            var a = ValueClass.newNullRestrictedAtomicArray(MyVal.class, 1, null);
            throw new RuntimeException("Missing NullPointerException");
        } catch (NullPointerException e) { }
    }

    static void test_25() {
        try {
            var a = ValueClass.newNullRestrictedNonAtomicArray(MyVal.class, 1, null);
            throw new RuntimeException("Missing NullPointerException");
        } catch (NullPointerException e) { }
    }

    public static void main(String[] args) {
        var test = new TestArrayFactories();
        Class c = test.getClass();
        ArrayList<String> failures = new ArrayList<>();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().startsWith("test_")) {
                try {
                    System.out.println("Running " + m.getName());
                    m.invoke(null);
                } catch (Throwable t) {
                    t.printStackTrace();
                    failures.add(m.getName());
                }
            }
        }
        if (!failures.isEmpty()) {
            System.out.print("Failed tests: ");
            for (String s: failures) {
                System.out.print(s + " ");
            }
            System.out.println("");
            throw new RuntimeException("Some tests failed");
        }
    }
}
