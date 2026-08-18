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
 */

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Array;
import java.util.Arrays;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.whitebox.WhiteBox;

/*
 * @test
 * @key randomness
 * @summary Test loading of the default refined array klass from C2 intrinsics.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=dontinline,*::test*
 *                   ${test.main.class}
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+StressReflectiveCode
 *                   -XX:CompileCommand=dontinline,*::test*
 *                   ${test.main.class}
 */

public class TestLoadRefinedArrayKlass {
    private static final WhiteBox WHITEBOX = WhiteBox.getWhiteBox();
    private static final boolean UseArrayFlattening = WHITEBOX.getBooleanVMFlag("UseArrayFlattening");
    private static final boolean UseNullableAtomicValueFlattening = WHITEBOX.getBooleanVMFlag("UseNullableAtomicValueFlattening");
    private static final boolean UseNullFreeAtomicValueFlattening = WHITEBOX.getBooleanVMFlag("UseNullFreeAtomicValueFlattening");
    private static final boolean ForceNonTearable = !WHITEBOX.getStringVMFlag("ForceNonTearable").equals("");

    @LooselyConsistentValue
    static value class MySimpleValue {
        int i;
        byte b;

        public MySimpleValue(int i) {
            this.i = i;
            this.b = (byte)i;
        }
    }

    public static void main(String[] args) {
        // Randomly trigger creation of a refined array klass of MySimpleValue[] to make
        // sure that the order in which they are created does not make a difference.
        switch (Utils.getRandomInstance().nextInt(5)) {
            case 0:
                Object tmp = new MySimpleValue[10];
                break;
            case 1:
                ValueClass.newReferenceArray(MySimpleValue.class, 10);
                break;
            case 2:
                ValueClass.newNullableAtomicArray(MySimpleValue.class, 10);
                break;
            case 3:
                ValueClass.newNullRestrictedNonAtomicArray(MySimpleValue.class, 10, new MySimpleValue(0));
                break;
            case 4:
                ValueClass.newNullRestrictedAtomicArray(MySimpleValue.class, 10, new MySimpleValue(0));
                break;
        }

        Object[] objArray = new Object[10];
        MySimpleValue[] defaultArray = new MySimpleValue[10];
        MySimpleValue[] refArray = (MySimpleValue[])ValueClass.newReferenceArray(MySimpleValue.class, 10);
        MySimpleValue[] nullableAtomicArray = (MySimpleValue[])ValueClass.newNullableAtomicArray(MySimpleValue.class, 10);
        MySimpleValue[] nullFreeNonAtomicArray = (MySimpleValue[])ValueClass.newNullRestrictedNonAtomicArray(MySimpleValue.class, 10, new MySimpleValue(0));
        MySimpleValue[] nullFreeAtomicArray = (MySimpleValue[])ValueClass.newNullRestrictedAtomicArray(MySimpleValue.class, 10, new MySimpleValue(0));

        Asserts.assertEquals(ValueClass.isFlatArray(defaultArray), UseArrayFlattening && UseNullableAtomicValueFlattening);
        Asserts.assertFalse(ValueClass.isFlatArray(refArray));
        Asserts.assertEquals(ValueClass.isFlatArray(nullableAtomicArray), UseArrayFlattening && UseNullableAtomicValueFlattening);
        Asserts.assertEquals(ValueClass.isFlatArray(nullFreeNonAtomicArray), UseArrayFlattening && UseNullFreeAtomicValueFlattening);
        Asserts.assertEquals(ValueClass.isFlatArray(nullFreeAtomicArray), UseArrayFlattening && UseNullFreeAtomicValueFlattening);

        Asserts.assertFalse(ValueClass.isNullRestrictedArray(defaultArray));
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(refArray));
        Asserts.assertFalse(ValueClass.isNullRestrictedArray(nullableAtomicArray));
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeNonAtomicArray));
        Asserts.assertTrue(ValueClass.isNullRestrictedArray(nullFreeAtomicArray));

        Asserts.assertTrue(ValueClass.isAtomicArray(defaultArray));
        Asserts.assertTrue(ValueClass.isAtomicArray(refArray));
        Asserts.assertTrue(ValueClass.isAtomicArray(nullableAtomicArray));
        Asserts.assertEquals(ValueClass.isAtomicArray(nullFreeNonAtomicArray), ForceNonTearable || !UseArrayFlattening);
        Asserts.assertTrue(ValueClass.isAtomicArray(nullFreeAtomicArray));

        for (int i = 0; i < objArray.length; i++) {
            objArray[i] = new MySimpleValue(i);
            defaultArray[i] = new MySimpleValue(i);
            refArray[i] = new MySimpleValue(i);
            nullableAtomicArray[i] = new MySimpleValue(i);
            nullFreeNonAtomicArray[i] = new MySimpleValue(i);
            nullFreeAtomicArray[i] = new MySimpleValue(i);
        }

        for (int i = 0; i < 50_000; i++) {
            // Test _copyOf intrinsic
            Object[] res = testCopyOf(objArray, MySimpleValue[].class);
            checkArray(res, true, false, true);

            res = testCopyOf(defaultArray, MySimpleValue[].class);
            checkArray(res, true, false, true);

            res = testCopyOf(refArray, MySimpleValue[].class);
            checkArray(res, true, false, true);

            res = testCopyOf(nullableAtomicArray, MySimpleValue[].class);
            checkArray(res, true, false, true);

            res = testCopyOf(nullFreeNonAtomicArray, MySimpleValue[].class);
            checkArray(res, true, true, ForceNonTearable || !UseArrayFlattening);

            res = testCopyOf(nullFreeAtomicArray, MySimpleValue[].class);
            checkArray(res, true, true, true);

            res = testCopyOf(objArray, Object[].class);
            checkArray(res, false, false, true);

            res = testCopyOf(defaultArray, Object[].class);
            checkArray(res, false, false, true);

            res = testCopyOf(refArray, Object[].class);
            checkArray(res, false, false, true);

            res = testCopyOf(nullableAtomicArray, Object[].class);
            checkArray(res, false, false, true);

            res = testCopyOf(nullFreeNonAtomicArray, Object[].class);
            checkArray(res, false, false, true);

            res = testCopyOf(nullFreeAtomicArray, Object[].class);
            checkArray(res, false, false, true);

            // Test _newArray intrinsic
            res = testNewInstance(MySimpleValue.class, 10);
            Asserts.assertEquals(ValueClass.isFlatArray(res), UseArrayFlattening && UseNullableAtomicValueFlattening);
            Asserts.assertFalse(ValueClass.isNullRestrictedArray(res));
            Asserts.assertTrue(ValueClass.isAtomicArray(res));
        }
    }

    static <T> T[] testCopyOf(Object[] array, Class<? extends T[]> c) {
        return Arrays.copyOf(array, array.length, c);
    }

    static void checkArray(Object[] array, boolean isValue, boolean nullRestricted, boolean atomic) {
        Asserts.assertEquals(ValueClass.isFlatArray(array), isValue && UseArrayFlattening && (nullRestricted ? UseNullFreeAtomicValueFlattening : UseNullableAtomicValueFlattening));
        Asserts.assertEquals(ValueClass.isNullRestrictedArray(array), nullRestricted);
        Asserts.assertEquals(ValueClass.isAtomicArray(array), atomic);
        for (int i = 0; i < array.length; i++) {
            MySimpleValue value = (MySimpleValue)array[i];
            Asserts.assertEquals(value.i, i);
            Asserts.assertEquals(value.b, (byte)i);
        }
    }

    static Object[] testNewInstance(Class<?> c, int len) {
        return (Object[])Array.newInstance(c, len);
    }
}

