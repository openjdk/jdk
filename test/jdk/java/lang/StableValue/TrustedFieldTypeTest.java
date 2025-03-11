/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @compile --enable-preview -source ${jdk.version} TrustedFieldTypeTest.java
 * @run junit/othervm --enable-preview --add-opens java.base/jdk.internal.lang.stable=ALL-UNNAMED -Dopens=true TrustedFieldTypeTest
 * @run junit/othervm --enable-preview -Dopens=false TrustedFieldTypeTest
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
    void reflection() throws NoSuchFieldException, IllegalAccessException {
        final class Holder {
            private final StableValue<Integer> value = StableValue.of();
        }
        final class HolderNonFinal {
            private StableValue<Integer> value = StableValue.of();
        }
        final class ArrayHolder {
            private final StableValue<Integer>[] array = (StableValue<Integer>[]) new StableValue[]{};
        }

        Field valueField = Holder.class.getDeclaredField("value");
        valueField.setAccessible(true);
        Holder holder = new Holder();
        // We should be able to read the StableValue field
        Object read = valueField.get(holder);
        // We should NOT be able to write to the StableValue field
        assertThrows(IllegalAccessException.class, () ->
                valueField.set(holder, StableValue.of())
        );

        Field valueNonFinal = HolderNonFinal.class.getDeclaredField("value");
        valueNonFinal.setAccessible(true);
        HolderNonFinal holderNonFinal = new HolderNonFinal();
        // As the field is not final, both read and write should be ok (not trusted)
        Object readNonFinal = valueNonFinal.get(holderNonFinal);
        valueNonFinal.set(holderNonFinal, StableValue.of());

        Field arrayField = ArrayHolder.class.getDeclaredField("array");
        arrayField.setAccessible(true);
        ArrayHolder arrayHolder = new ArrayHolder();
        // We should be able to read the StableValue array
        read = arrayField.get(arrayHolder);
        // We should be able to write to the StableValue array
        assertDoesNotThrow(() -> arrayField.set(arrayHolder, new StableValue[1]));
    }

    @SuppressWarnings("removal")
    @Test
    void sunMiscUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        assertTrue(unsafeField.trySetAccessible());
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)unsafeField.get(null);

        final class Holder {
            private final StableValue<Integer> value = StableValue.of();
        }
        final class ArrayHolder {
            @SuppressWarnings("unchecked")
            private final StableValue<Integer>[] array = (StableValue<Integer>[]) new StableValue[]{};
        }

        Field valueField = Holder.class.getDeclaredField("value");
        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(valueField)
        );

        Field arrayField = ArrayHolder.class.getDeclaredField("array");

        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(arrayField)
        );

        // Test direct access
        StableValue<?> stableValue = StableValue.of();
        Class<?> clazz = stableValue.getClass();
        System.out.println("clazz = " + clazz);
        assertThrows(NoSuchFieldException.class, () -> clazz.getField("value"));
    }

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

        long offset = unsafe.objectFieldOffset(stableValue.getClass(), "value");
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
            Field field = StableValueImpl.class.getDeclaredField("value");
            field.setAccessible(true);

            StableValue<Integer> stableValue = StableValue.of();
            stableValue.trySet(42);

//            assertThrows(IllegalAccessException.class, () -> {
            Object oldData = field.get(stableValue);
            assertEquals(42, oldData);
//            });

//            assertThrows(IllegalAccessException.class, () -> {
            field.set(stableValue, 13);
//            });
            assertEquals(13, stableValue.orElseThrow());
        } else {
            Field field = StableValueImpl.class.getDeclaredField("value");
            assertThrows(InaccessibleObjectException.class, ()-> field.setAccessible(true));
        }
    }


}
