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
    private static final long BASE_OFFSET1;
    private static final int INDEX_SCALE1;
    private static final boolean FLATTENED_ARRAY1;
    private static final int LAYOUT1;
    private static final long VALUE_HEADER_SIZE1;
    private static final long FIELD_OFFSET1;
    
    private static final MyValue2[] array2;
    private static final long BASE_OFFSET2;
    private static final int INDEX_SCALE2;
    private static final boolean FLATTENED_ARRAY2;
    private static final int LAYOUT2;
    private static final long VALUE_HEADER_SIZE2;
    private static final long FIELD_OFFSET2;

    private static Object o1 = new Object();
    private static Object o2 = new Object();
    static {
        try {
            array1 = (MyValue1[])ValueClass.newNullRestrictedNonAtomicArray(MyValue1.class, 1, new MyValue1(o1));
            BASE_OFFSET1 = U.arrayInstanceBaseOffset(array1);
            INDEX_SCALE1 = U.arrayInstanceIndexScale(array1);
            FLATTENED_ARRAY1 = ValueClass.isFlatArray(array1);
            LAYOUT1 = U.arrayLayout(array1);
            VALUE_HEADER_SIZE1 = U.valueHeaderSize(MyValue1.class);
            Field f = MyValue1.class.getDeclaredField("field");
            FIELD_OFFSET1 = U.objectFieldOffset(f);

            array2 = (MyValue2[])ValueClass.newNullRestrictedNonAtomicArray(MyValue2.class, 1, new MyValue2(42));
            BASE_OFFSET2 = U.arrayInstanceBaseOffset(array2);
            INDEX_SCALE2 = U.arrayInstanceIndexScale(array2);
            FLATTENED_ARRAY2 = ValueClass.isFlatArray(array2);
            LAYOUT2 = U.arrayLayout(array2);
            VALUE_HEADER_SIZE2 = U.valueHeaderSize(MyValue2.class);
            f = MyValue2.class.getDeclaredField("field");
            FIELD_OFFSET2 = U.objectFieldOffset(f);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static public boolean test1(Object oldVal, Object newVal) {
        array1[0] = new MyValue1(oldVal);
        return U.compareAndSetReference(array1, BASE_OFFSET1, oldVal, newVal);
    }

    static public boolean test2(int oldVal, int newVal) {
        array2[0] = new MyValue2(oldVal);
        return U.compareAndSetFlatValue(array2, BASE_OFFSET2, LAYOUT2, MyValue2.class, new MyValue2(oldVal), new MyValue2(newVal));
    }

    static public void main(String args[]) {
        if (!FLATTENED_ARRAY1) {
            throw new RuntimeException("flattened array expected");
        }
        System.out.println("XXX " + FIELD_OFFSET1 + " " + VALUE_HEADER_SIZE1);
        if (FIELD_OFFSET1 != VALUE_HEADER_SIZE1) {
            throw new RuntimeException("bad field offset");
        }
        if (INDEX_SCALE1 != 4) {
            throw new RuntimeException("unexpected layout");
        }
        for (int i = 0; i < 20_000; i++) {
            // boolean res = test1(o1, o2);
            // if (!res) {
            //     throw new RuntimeException("CAS failed");
            // }
            boolean res = test2(42, 0x42);
            if (!res) {
                throw new RuntimeException("CAS failed");
            }
        }
    }
}
