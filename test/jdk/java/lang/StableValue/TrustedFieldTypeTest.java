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
 * @modules java.base/jdk.internal.lang
 * @modules java.base/jdk.internal.lang.stable
 * @compile --enable-preview -source ${jdk.version} TrustedFieldTypeTest.java
 * @run junit/othervm --enable-preview TrustedFieldTypeTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;

import static org.junit.jupiter.api.Assertions.*;

final class TrustedFieldTypeTest {

    @Test
    void reflection() throws NoSuchFieldException {
        final class Holder {
            private final StableValue<Integer> value = StableValue.newInstance();
        }
        final class HolderNonFinal {
            private StableValue<Integer> value = StableValue.newInstance();
        }

        Field valueField = Holder.class.getDeclaredField("value");
        assertThrows(InaccessibleObjectException.class, () ->
                valueField.setAccessible(true)
        );
        Field valueNonFinal = HolderNonFinal.class.getDeclaredField("value");
        assertDoesNotThrow(() -> valueNonFinal.setAccessible(true));
    }

    @SuppressWarnings("removal")
    @Test
    void sunMiscUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        assertTrue(unsafeField.trySetAccessible());
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)unsafeField.get(null);

        final class Holder {
            private final StableValue<Integer> value = StableValue.newInstance();
        }

        Field valueField = Holder.class.getDeclaredField("value");
        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(valueField)
        );

    }

    @Test
    void varHandle() throws NoSuchFieldException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        StableValue<Integer> originalValue = StableValue.newInstance();

        final class Holder {
            private final StableValue<Integer> value = originalValue;
        }

        VarHandle valueVarHandle = lookup.findVarHandle(Holder.class, "value", StableValue.class);
        Holder holder = new Holder();

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.set(holder, StableValue.newInstance())
        );

        assertThrows(UnsupportedOperationException.class, () ->
                valueVarHandle.compareAndSet(holder, originalValue, StableValue.newInstance())
        );

    }

}
