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
 * @run main/othervm -XX:-BackgroundCompilation ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;
import jdk.internal.value.ValueClass;

public class CompareAndSetFlatArrayField {
    static public value class MyValue {
        int field;
        MyValue(int v) {
            field = v;
        }
    }

    static final Unsafe U = Unsafe.getUnsafe();
    private static final long BASE_OFFSET;
    private static final int INDEX_SCALE;
    private static MyValue[] array;
    private static final boolean FLATTENED_ARRAY;
    private static final int LAYOUT;
    static {
        try {
            array = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, 1, new MyValue(0));
            BASE_OFFSET = U.arrayInstanceBaseOffset(array);
            INDEX_SCALE = U.arrayInstanceIndexScale(array);
            FLATTENED_ARRAY = ValueClass.isFlatArray(array);
            LAYOUT = U.arrayLayout(array);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static public boolean test(int oldVal, int newVal) {
        return U.compareAndSetInt(array, BASE_OFFSET, oldVal, newVal);
    }

    static public void main(String args[]) {
        if (!FLATTENED_ARRAY) {
            throw new RuntimeException("flattened array expected");
        }
        if (INDEX_SCALE != 4) {
            throw new RuntimeException("unexpected layout");
        }
        MyValue[] array = new MyValue[1];
        for (int i = 0; i < 20_000; i++) {
            boolean res = test(i, i+1);
            if (!res) {
                throw new RuntimeException("CAS failed");
            }
        }
    }
}
