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
 * @run main ${test.main.class}
 */

package compiler.valhalla.inlinetypes;
import compiler.lib.ir_framework.*;
import jdk.internal.value.ValueClass;
public class TestArrayLoadProfiling {
    public static void main(String[] args) {
        TestFramework.runWithFlags("--enable-preview", "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED");
    }

    static MyValue1[] array1 = { new MyValue1(42) };
    static MyValue2[] array2 = { new MyValue2(42) };
    static MyValue1[] array3 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, new MyValue1(42));
    static MyValue2[] array4 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, new MyValue2(42));
    static Object[] array5 = { new Object() };
    
    @Test
    public static void test1(Object[] array) {
        return array[0];
    }

    @Run(test = "test1")
    public static void test1Runner() {
        test1(array1);
    }

    @Test
    public static void test2(Object[] array) {
        return array[0];
    }

    @Run(test = "test2")
    public static void test2Runner() {
        test2(array5);
    }

    @Test
    public static void test3(Object[] array) {
        return array[0];
    }

    @Run(test = "test3")
    public static void test3Runner() {
        test3(array1);
        test3(array2);
        test3(array5);
    }
    
    @Test
    public static void test4(Object[] array) {
        return array[0];
    }

    @Run(test = "test4")
    public static void test3Runner() {
        test4(array1);
        test4(array2);
        test4(array3);
        test4(array4);
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
}
