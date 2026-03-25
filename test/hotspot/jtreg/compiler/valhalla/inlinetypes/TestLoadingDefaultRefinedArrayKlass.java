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

import java.lang.reflect.Array;
import java.util.Arrays;

import jdk.internal.value.ValueClass;

import jdk.test.lib.Asserts;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @summary Make sure that the correct default refined klass is loaded by intrinsics.
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   compiler.valhalla.inlinetypes.TestLoadingDefaultRefinedArrayKlass
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:CompileCommand=dontinline,*TestLoadingDefaultRefinedArrayKlass::test*
 *                   -XX:+UseArrayFlattening -XX:+UseNullableValueFlattening -XX:+UseAtomicValueFlattening
 *                   compiler.valhalla.inlinetypes.TestLoadingDefaultRefinedArrayKlass
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch -XX:CompileCommand=dontinline,*TestLoadingDefaultRefinedArrayKlass::test*
 *                   -XX:-UseArrayFlattening
 *                   compiler.valhalla.inlinetypes.TestLoadingDefaultRefinedArrayKlass
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xcomp -XX:CompileCommand=compileonly,*TestLoadingDefaultRefinedArrayKlass::test* -XX:-TieredCompilation
 *                   compiler.valhalla.inlinetypes.TestLoadingDefaultRefinedArrayKlass
 */
public class TestLoadingDefaultRefinedArrayKlass {

    private static final WhiteBox WHITEBOX = WhiteBox.getWhiteBox();
    private static final boolean UseArrayFlattening = WHITEBOX.getBooleanVMFlag("UseArrayFlattening");

    static value class MyValue1 {
        int x = 42;
    }

    static value class MyValue2 {
        int x = 42;
    }

    static value class MyValue3 {
        int x = 42;
    }

    public static Object[] test1(Object[] array, Class<? extends Object[]> arrayType) {
        return Arrays.copyOf(array, 1, arrayType);
    }

    public static Object[] test2(Class<?> componentType) {
        return (Object[])Array.newInstance(componentType, 1);
    }

    public static Object[] test3(Class<?> componentType) {
        return (Object[])Array.newInstance(componentType, 1);
    }

    public static void main(String[] args) {
        // Make sure that a non-initialized refined array klass is handled

        // Make sure stuff is loaded
        Arrays.copyOf(new Object[0], 1, Object[].class);
        Array.newInstance(Integer.class, 1);

        Object[] res1 = test1(new Object[1], MyValue1[].class);
        Asserts.assertEquals(ValueClass.isFlatArray(res1), UseArrayFlattening);
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(res1));
        Asserts.assertTrue(ValueClass.isAtomicArray(res1));

        Class c = MyValue2[].class; // Make sure the array klass mirror is created
        Object[] res2 = test2(MyValue2.class);
        Asserts.assertEquals(ValueClass.isFlatArray(res2), UseArrayFlattening);
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(res2));
        Asserts.assertTrue(ValueClass.isAtomicArray(res2));

        // Pollute the system with non-default refined array klasses
        MyValue3[] tmp1 = (MyValue3[])ValueClass.newNullRestrictedNonAtomicArray(MyValue3.class, 1, new MyValue3());
        MyValue3[] tmp2 = (MyValue3[])ValueClass.newNullRestrictedAtomicArray(MyValue3.class, 1, new MyValue3());
        MyValue3[] tmp3 = (MyValue3[])ValueClass.newNullableAtomicArray(MyValue3.class, 1);

        // Now assert that the default refined array klass is loaded by the intrinsics
        MyValue3[] array = new MyValue3[1];
        for (int i = 0; i < 50_000; ++i) {
            res1 = test1(array, MyValue3[].class);
            Asserts.assertEquals(ValueClass.isFlatArray(res1), UseArrayFlattening);
            Asserts.assertFalse(ValueClass.isNullRestrictedArray(res1));
            Asserts.assertTrue(ValueClass.isAtomicArray(res1));

            res2 = test2(MyValue3.class);
            Asserts.assertEquals(ValueClass.isFlatArray(res2), UseArrayFlattening);
            Asserts.assertFalse(ValueClass.isNullRestrictedArray(res2));
            Asserts.assertTrue(ValueClass.isAtomicArray(res2));
        }
    }
}
