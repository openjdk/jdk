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

/* @test
 * @summary Basic tests for TrustedFieldType implementations
 * @modules jdk.unsupported/sun.misc
 * @modules java.base/jdk.internal.lang.stable
 * @modules java.base/jdk.internal.misc
 * @enablePreview
 * @run junit/othervm --add-opens java.base/jdk.internal.lang.stable=ALL-UNNAMED -Dopens=true TrustedFieldTypeTest
 * @run junit/othervm -Dopens=false TrustedFieldTypeTest
 */

import jdk.internal.lang.stable.StableValueImpl;
import jdk.internal.misc.Unsafe;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;

import static org.junit.jupiter.api.Assertions.*;

final class TrustedFieldTypeTest {

    @Test
    void varHandle() throws NoSuchFieldException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        StableValue<Integer> originalValue = StableValue.of();
        @SuppressWarnings("unchecked")
        StableValue<Integer>[] originalArrayValue = new StableValue[10];

        final class Holder {
            private final StableValue<Integer> value = originalValue;
        }
        final class ArrayHolder {
            private final StableValue<Integer>[] array = originalArrayValue;
        }


        VarHandle valueVarHandle = lookup.findVarHandle(Holder.class, "value", StableValue.class);
        Holder holder = new Holder();

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.set(holder, StableValue.of())
        );

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.compareAndSet(holder, originalValue, StableValue.of())
        );

        VarHandle arrayVarHandle = lookup.findVarHandle(ArrayHolder.class, "array", StableValue[].class);
        ArrayHolder arrayHolder = new ArrayHolder();

        assertThrows(UnsupportedOperationException.class, () ->
                arrayVarHandle.set(arrayHolder, new StableValue[1])
        );

        assertThrows(UnsupportedOperationException.class, () ->
                arrayVarHandle.compareAndSet(arrayHolder, originalArrayValue, new StableValue[1])
        );

    }

    @Test
    void updateStableValueContentVia_j_i_m_Unsafe() {
        StableValue<Integer> stableValue = StableValue.of();
        stableValue.trySet(42);
        jdk.internal.misc.Unsafe unsafe = Unsafe.getUnsafe();

        long offset = unsafe.objectFieldOffset(stableValue.getClass(), "contents");
        assertTrue(offset > 0);

        // Unfortunately, it is possible to update the underlying data via jdk.internal.misc.Unsafe
        Object oldData = unsafe.getAndSetReference(stableValue, offset, 13);
        assertEquals(42, oldData);
        assertEquals(13, stableValue.orElseThrow());
    }

    @Test
    void updateStableValueContentViaSetAccessible() throws NoSuchFieldException, IllegalAccessException {

        if (Boolean.getBoolean("opens")) {
            // Unfortunately, add-opens allows direct access to the `value` field
            Field field = StableValueImpl.class.getDeclaredField("contents");
            field.setAccessible(true);

            StableValue<Integer> stableValue = StableValue.of();
            stableValue.trySet(42);

            Object oldData = field.get(stableValue);
            assertEquals(42, oldData);

            field.set(stableValue, 13);
            assertEquals(13, stableValue.orElseThrow());
        } else {
            Field field = StableValueImpl.class.getDeclaredField("contents");
            assertThrows(InaccessibleObjectException.class, ()-> field.setAccessible(true));
        }
    }

}
