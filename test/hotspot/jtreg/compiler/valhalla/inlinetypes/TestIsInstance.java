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

package compiler.valhalla.inlinetypes;

/*
 * @test
 * @summary Test correctness of the isInstance intrinsic with deep class hierarchies.
 * @run main/othervm -Xbatch compiler.valhalla.inlinetypes.TestIsInstance
 */

public class TestIsInstance {
    static interface MyInterface {
    }

    static class MyClass0 implements MyInterface {
    }

    static class MyClass1 extends MyClass0 {
    }

    static class MyClass2 extends MyClass1 {
    }

    static class MyClass3 extends MyClass2 {
    }

    static class MyClass4 extends MyClass3 {
    }

    static class MyClass5 extends MyClass4 {
    }

    static class MyClass6 extends MyClass5 {
    }

    static class MyClass7 extends MyClass6 {
    }

    static class MyClass8 extends MyClass7 {
    }

    static class MyClass9 extends MyClass8 {
    }

    static class MyClass10 extends MyClass9 {
    }

    public static void check(Object obj, Class<?> clazz, boolean expected) {
        if (expected != clazz.isInstance(obj)) {
            throw new RuntimeException("Unexpected result: " + clazz + ".isInstance(" + obj.getClass() + ") should return " + expected);
        }
        // Also try a cast
        try {
            clazz.cast(obj);
        } catch (ClassCastException e) {
            if (expected) {
                throw new RuntimeException("Unexpected ClassCastException", e);
            }
        }
    }

    public static void main(String[] args) {
        Class<?>[] classes = new Class<?>[] {
            Object.class, MyInterface.class, MyClass0.class, MyClass1.class, MyClass2.class, MyClass3.class, MyClass4.class,
            MyClass5.class, MyClass6.class, MyClass7.class, MyClass8.class, MyClass9.class, MyClass10.class, Integer.class };

        Class<?>[] arrayClasses = new Class<?>[] {
            java.lang.Cloneable.class, java.io.Serializable.class, Object.class, Object[].class,
            MyInterface[].class, MyClass0[].class, MyClass1[].class, MyClass2[].class, MyClass3[].class, MyClass4[].class,
            MyClass5[].class, MyClass6[].class, MyClass7[].class, MyClass8[].class, MyClass9[].class, MyClass10[].class, Integer[].class };

        Class<?>[] multiDimArrayClasses = new Class<?>[] {
            java.lang.Cloneable.class, java.lang.Cloneable[].class, java.io.Serializable.class, java.io.Serializable[].class, Object.class, Object[].class, Object[].class,
            MyInterface[][].class, MyClass0[][].class, MyClass1[][].class, MyClass2[][].class, MyClass3[][].class, MyClass4[][].class,
            MyClass5[][].class, MyClass6[][].class, MyClass7[][].class, MyClass8[][].class, MyClass9[][].class, MyClass10[][].class, Integer[][].class };

        for (int i = 0; i < 1000; ++i) {
            for (int j = 0; j < classes.length; ++j) {
                check(new MyClass0(), classes[j], j <= 2);
                check(new MyClass1(), classes[j], j <= 3);
                check(new MyClass2(), classes[j], j <= 4);
                check(new MyClass3(), classes[j], j <= 5);
                check(new MyClass4(), classes[j], j <= 6);
                check(new MyClass5(), classes[j], j <= 7);
                check(new MyClass6(), classes[j], j <= 8);
                check(new MyClass7(), classes[j], j <= 9);
                check(new MyClass8(), classes[j], j <= 10);
                check(new MyClass9(), classes[j], j <= 11);
                check(new MyClass10(), classes[j], j <= 12);

                check(new MyInterface[0], arrayClasses[j], j <= 4);
                check(new MyClass0[0], arrayClasses[j], j <= 5);
                check(new MyClass1[0], arrayClasses[j], j <= 6);
                check(new MyClass2[0], arrayClasses[j], j <= 7);
                check(new MyClass3[0], arrayClasses[j], j <= 8);
                check(new MyClass4[0], arrayClasses[j], j <= 9);
                check(new MyClass5[0], arrayClasses[j], j <= 10);
                check(new MyClass6[0], arrayClasses[j], j <= 11);
                check(new MyClass7[0], arrayClasses[j], j <= 12);
                check(new MyClass8[0], arrayClasses[j], j <= 13);
                check(new MyClass9[0], arrayClasses[j], j <= 14);
                check(new MyClass10[0], arrayClasses[j], j <= 15);

                check(new MyInterface[0][0], multiDimArrayClasses[j], j <= 7);
                check(new MyClass0[0][0], multiDimArrayClasses[j], j <= 8);
                check(new MyClass1[0][0], multiDimArrayClasses[j], j <= 9);
                check(new MyClass2[0][0], multiDimArrayClasses[j], j <= 10);
                check(new MyClass3[0][0], multiDimArrayClasses[j], j <= 11);
                check(new MyClass4[0][0], multiDimArrayClasses[j], j <= 12);
                check(new MyClass5[0][0], multiDimArrayClasses[j], j <= 13);
                check(new MyClass6[0][0], multiDimArrayClasses[j], j <= 14);
                check(new MyClass7[0][0], multiDimArrayClasses[j], j <= 15);
                check(new MyClass8[0][0], multiDimArrayClasses[j], j <= 16);
                check(new MyClass9[0][0], multiDimArrayClasses[j], j <= 17);
                check(new MyClass10[0][0], multiDimArrayClasses[j], j <= 18);
            }
        }
    }
}
