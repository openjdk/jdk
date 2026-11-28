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

package runtime.valhalla.valuetypes;

import java.lang.reflect.Field;
import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Reading an uninitialized flat field must not initialize an uninitialized value
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @run main/othervm/native --enable-native-access=ALL-UNNAMED -Xint
 *                         runtime.valhalla.valuetypes.UninitializedFlatField
 */

public class UninitializedFlatField {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static boolean valueInitialized;

    static {
        System.loadLibrary("UninitializedFlatField");
    }

    static value class Value {
        int x;

        static {
            valueInitialized = true;
        }

        Value(int x) {
            this.x = x;
        }
    }

    static class Holder {
        @NullRestricted
        Value value;

        Holder() {
            this.value = new Value(1);
            super();
        }
    }

    private static native Object allocObject(Class<?> target);
    private static native Object getObjectField(Holder holder);

    public static void main(String[] args) throws Exception {
        Holder holder = (Holder) allocObject(Holder.class);
        Field field = Holder.class.getDeclaredField("value");

        Asserts.assertTrue(U.isFlatField(field), "Test needs a flat field");
        Asserts.assertFalse(U.hasNullMarker(field), "Test needs a null-restricted field");
        Asserts.assertFalse(valueInitialized, "AllocObject must not initialize Value");

        Object result_getfield = holder.value;
        Asserts.assertTrue(result_getfield == null, "Read got an instance of an uninitialized value class");

        Object result_jni = getObjectField(holder);
        Asserts.assertTrue(result_jni == null, "Read got an instance of an uninitialized value class");

        Object result_unsafe = U.getFlatValue(holder, U.objectFieldOffset(field), U.fieldLayout(field), Value.class);
        Asserts.assertTrue(result_unsafe == null, "Read got an instance of an uninitialized value class");

        Asserts.assertFalse(valueInitialized, "Reading the field must not initialize Value");
    }
}
