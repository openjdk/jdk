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
 * @modules java.base/jdk.internal.lang
 * @modules java.base/jdk.internal.misc
 * @enablePreview
 * @run junit/othervm --add-opens java.base/jdk.internal.lang=ALL-UNNAMED -Dopens=true TrustedFieldTypeTest
 * @run junit/othervm -Dopens=false TrustedFieldTypeTest
 */

import jdk.internal.lang.LazyConstantImpl;
import jdk.internal.misc.Unsafe;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.LazyConstant;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class TrustedFieldTypeTest {

    private static final int VALUE = 42;
    private static final Supplier<Integer> SUPPLIER = () -> VALUE;

    @Test
    void varHandle() throws NoSuchFieldException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        LazyConstant<Integer> originalValue = LazyConstant.of(SUPPLIER);
        @SuppressWarnings("unchecked")
        LazyConstant<Integer>[] originalArrayValue = new LazyConstant[10];

        final class Holder {
            private final LazyConstant<Integer> value = originalValue;
        }
        final class ArrayHolder {
            private final LazyConstant<Integer>[] array = originalArrayValue;
        }


        VarHandle valueVarHandle = lookup.findVarHandle(Holder.class, "value", LazyConstant.class);
        Holder holder = new Holder();

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.set(holder, LazyConstant.of(SUPPLIER))
        );

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.compareAndSet(holder, originalValue, LazyConstant.of(SUPPLIER))
        );

        VarHandle arrayVarHandle = lookup.findVarHandle(ArrayHolder.class, "array", LazyConstant[].class);
        ArrayHolder arrayHolder = new ArrayHolder();

        assertThrows(UnsupportedOperationException.class, () ->
                arrayVarHandle.set(arrayHolder, new LazyConstant[1])
        );

        assertThrows(UnsupportedOperationException.class, () ->
                arrayVarHandle.compareAndSet(arrayHolder, originalArrayValue, new LazyConstant[1])
        );

    }

    @Test
    void updateComputedConstantContentVia_j_i_m_Unsafe() {
        LazyConstant<Integer> lazyConstant = LazyConstant.of(SUPPLIER);
        lazyConstant.get();
        jdk.internal.misc.Unsafe unsafe = Unsafe.getUnsafe();

        long offset = unsafe.objectFieldOffset(lazyConstant.getClass(), "constant");
        assertTrue(offset > 0);

        // Unfortunately, it is possible to update the underlying data via jdk.internal.misc.Unsafe
        Object oldData = unsafe.getAndSetReference(lazyConstant, offset, 13);
        assertEquals(VALUE, oldData);
        assertEquals(13, lazyConstant.get());
    }

    @Test
    void updateComputedConstantContentViaSetAccessible() throws NoSuchFieldException, IllegalAccessException {

        if (Boolean.getBoolean("opens")) {
            // Unfortunately, add-opens allows direct access to the `value` field
            Field field = LazyConstantImpl.class.getDeclaredField("constant");
            field.setAccessible(true);

            LazyConstant<Integer> lazyConstant = LazyConstant.of(SUPPLIER);
            lazyConstant.get();

            Object oldData = field.get(lazyConstant);
            assertEquals(VALUE, oldData);

            field.set(lazyConstant, 13);
            assertEquals(13, lazyConstant.get());
        } else {
            Field field = LazyConstantImpl.class.getDeclaredField("constant");
            assertThrows(InaccessibleObjectException.class, ()-> field.setAccessible(true));
        }
    }

}
