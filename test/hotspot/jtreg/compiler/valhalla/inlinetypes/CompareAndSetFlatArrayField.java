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

/*
 * @test
 * @bug 8389390
 * @summary [Valhalla] Compile::adjust_flat_array_access_aliases asserts due SCMemProj
 * @enablePreview
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -XX:-BackgroundCompilation ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;

public class CompareAndSetFlatArrayField {
    static public value class MyValue1 {
        Object field;
        MyValue1(Object v) {
            field = v;
        }
    }

    static public value class MyValue2 {
        int field;
        MyValue2(int v) {
            field = v;
        }
    }

    private static final Unsafe U = Unsafe.getUnsafe();

    private static final MyValue1[] array1;
    private static final long ARRAY1_BASE_OFFSET;
    private static final int ARRAY1_INDEX_SCALE;
    private static final boolean ARRAY1_FLATTENED;
    private static final int ARRAY_LAYOUT1;
    private static final long VALUE1_HEADER_SIZE;
    private static final long VALUE1_FIELD_OFFSET;

    private static final MyValue2[] array2;
    private static final long ARRAY2_BASE_OFFSET;
    private static final int ARRAY2_INDEX_SCALE;
    private static final boolean ARRAY2_FLATTENED;
    private static final int ARRAY2_LAYOUT;
    private static final long VALUE2_HEADER_SIZE;
    private static final long FIELD_OFFSET2;

    private MyValue1 field1;
    private static final boolean FLAT_FIELD1;
    private static final long FIELD1_OFFSET;
    private static final int FIELD1_LAYOUT;

    private MyValue2 field2;
    private static final boolean FLAT_FIELD2;
    private static final long FIELD2_OFFSET;
    private static final int FIELD2_LAYOUT;

    private static Object o1 = new Object();
    private static Object o2 = new Object();

    private static CompareAndSetFlatArrayField testObject = new CompareAndSetFlatArrayField();

    static {
        try {
            array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, new MyValue1(o1));
            ARRAY1_BASE_OFFSET = U.arrayInstanceBaseOffset(array1);
            ARRAY1_INDEX_SCALE = U.arrayInstanceIndexScale(array1);
            ARRAY1_FLATTENED = ValueClass.isFlatArray(array1);
            ARRAY_LAYOUT1 = U.arrayLayout(array1);
            VALUE1_HEADER_SIZE = U.valueHeaderSize(MyValue1.class);
            Field f = MyValue1.class.getDeclaredField("field");
            VALUE1_FIELD_OFFSET = U.objectFieldOffset(f);

            array2 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, new MyValue2(42));
            ARRAY2_BASE_OFFSET = U.arrayInstanceBaseOffset(array2);
            ARRAY2_INDEX_SCALE = U.arrayInstanceIndexScale(array2);
            ARRAY2_FLATTENED = ValueClass.isFlatArray(array2);
            ARRAY2_LAYOUT = U.arrayLayout(array2);
            VALUE2_HEADER_SIZE = U.valueHeaderSize(MyValue2.class);
            f = MyValue2.class.getDeclaredField("field");
            FIELD_OFFSET2 = U.objectFieldOffset(f);

            f = CompareAndSetFlatArrayField.class.getDeclaredField("field1");
            FIELD1_OFFSET = U.objectFieldOffset(f);
            FLAT_FIELD1 = U.isFlatField(f);
            FIELD1_LAYOUT = U.fieldLayout(f);

            f = CompareAndSetFlatArrayField.class.getDeclaredField("field2");
            FIELD2_OFFSET = U.objectFieldOffset(f);
            FLAT_FIELD2 = U.isFlatField(f);
            FIELD2_LAYOUT = U.fieldLayout(f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static public boolean test1(Object oldVal, Object newVal) {
        array1[0] = new MyValue1(oldVal);
        return U.compareAndSetReference(array1, ARRAY1_BASE_OFFSET, oldVal, newVal);
    }

    static public boolean test2(int oldVal, int newVal) {
        array2[0] = new MyValue2(oldVal);
        return U.compareAndSetFlatValue(array2, ARRAY2_BASE_OFFSET, ARRAY2_LAYOUT, MyValue2.class, new MyValue2(oldVal), new MyValue2(newVal));
    }

    static public boolean test3(Object oldVal, Object newVal) {
        testObject.field1 = new MyValue1(oldVal);
        return U.compareAndSetReference(testObject, FIELD1_OFFSET, oldVal, newVal);
    }

    static public boolean test4(int oldVal, int newVal) {
        testObject.field2 = new MyValue2(oldVal);
        return U.compareAndSetFlatValue(testObject, FIELD2_OFFSET, FIELD2_LAYOUT, MyValue2.class, new MyValue2(oldVal), new MyValue2(newVal));
    }

    static public void main(String args[]) {
        if (!ARRAY1_FLATTENED || !ARRAY2_FLATTENED || !FLAT_FIELD1 || !FLAT_FIELD2) {
            return;
        }
        if (VALUE1_FIELD_OFFSET != VALUE1_HEADER_SIZE) {
            throw new RuntimeException("fix test: test assumes MyValue1[0].f is at offset 0 in MyValue1[0]");
        }
        for (int i = 0; i < 20_000; i++) {
            boolean res = test1(o1, o2);
            if (!res) {
                throw new RuntimeException("CAS failed");
            }
            res = test2(42, 0x42);
            if (!res) {
                throw new RuntimeException("CAS failed");
            }
            res = test3(o1, o2);
            if (!res) {
                throw new RuntimeException("CAS failed");
            }
            res = test4(42, 0x42);
            if (!res) {
                throw new RuntimeException("CAS failed");
            }
        }
    }
}
