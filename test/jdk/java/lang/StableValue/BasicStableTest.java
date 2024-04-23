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
 * @summary Basic tests for StableValue implementations
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} BasicStableTest.java
 * @compile Util.java
 * @run junit/othervm --enable-preview BasicStableTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class BasicStableTest {

    private static final int FIRST = 42;
    private static final int SECOND = 13;

    private StableValue<Integer> stable;

    @BeforeEach
    void setup() {
        stable = StableValue.of();
    }

    @Test
    void unset() {
        assertFalse(stable.isSet());
        assertThrows(NoSuchElementException.class, stable::orThrow);
    }

    @Test
    void setOrThrow() {
        stable.setOrThrow(FIRST);
        assertTrue(stable.isSet());
        assertEquals(FIRST, stable.orThrow());
        assertThrows(IllegalStateException.class, () -> stable.setOrThrow(SECOND));
        assertTrue(stable.isSet());
        assertEquals(FIRST, stable.orThrow());
    }

    @Test
    void setIfUnset() {
        Integer i = stable.setIfUnset(FIRST);
        assertTrue(stable.isSet());
        assertEquals(FIRST, i);
        assertEquals(FIRST, stable.orThrow());

        assertEquals(FIRST, stable.setIfUnset(FIRST));
        assertEquals(FIRST, stable.setIfUnset(SECOND));
        assertEquals(FIRST, stable.setIfUnset(null));
    }

    @Test
    void setIfUnsetNull() {
        Integer i = stable.setIfUnset(null);
        assertTrue(stable.isSet());
        assertNull(i);
        assertNull(stable.orThrow());

        assertNull(stable.setIfUnset(null));
        assertNull(stable.setIfUnset(FIRST));
        assertNull(stable.setIfUnset(SECOND));
    }

    @Test
    void computeIfUnset() {
        stable.computeIfUnset(() -> FIRST);
        assertEquals(FIRST, stable.orThrow());

        Supplier<Integer> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };
        assertDoesNotThrow(() -> stable.computeIfUnset(throwingSupplier));

        var m2 = StableValue.of();
        m2.computeIfUnset(() -> FIRST);
        assertEquals(FIRST, m2.orThrow());
    }

    @Test
    void computeIfUnsetNull() {
        Util.CountingSupplier<Integer> c = new Util.CountingSupplier<>(() -> null);
        stable.computeIfUnset(c);
        assertNull(stable.orThrow());
        assertEquals(1, c.cnt());
        stable.computeIfUnset(c);
        assertEquals(1, c.cnt());
    }

    @Test
    void memoized() {
        Util.CountingSupplier<Integer> cSup = new Util.CountingSupplier<>(() -> FIRST);
        StableValue<Integer> m3 = StableValue.of();
        Supplier<Integer> memoized = () -> m3.computeIfUnset(cSup);
        assertEquals(FIRST, memoized.get());
        // Make sure the original supplier is not invoked more than once
        assertEquals(FIRST, memoized.get());
        assertEquals(1, cSup.cnt());
    }

    @Test
    void testToString() {
        assertEquals("StableValue.unset", stable.toString());
        stable.setOrThrow(1);
        assertEquals("StableValue[1]", stable.toString());
    }

    @Test
    void reflection() throws NoSuchFieldException {
        final class Holder {
            private final StableValue<Integer> stable = StableValue.of();
        }
        final class HolderNonFinal {
            private StableValue<Integer> stable = StableValue.of();
        }

        Field field = Holder.class.getDeclaredField("stable");
        assertThrows(InaccessibleObjectException.class, () ->
                        field.setAccessible(true)
                );

        Field fieldNonFinal = HolderNonFinal.class.getDeclaredField("stable");
        assertDoesNotThrow(() -> fieldNonFinal.setAccessible(true));
    }

    @Test
    void sunMiscUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)unsafeField.get(null);

        final class Holder {
            private final StableValue<Integer> stable = StableValue.of();
        }
        Field field = Holder.class.getDeclaredField("stable");
        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(field)
        );

    }

    @Test
    void varHandle() throws NoSuchFieldException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        StableValue<Integer> original = StableValue.of();

        final class Holder {
            private final StableValue<Integer> stable = original;
        }

        VarHandle varHandle = lookup.findVarHandle(Holder.class, "stable", StableValue.class);
        Holder holder = new Holder();

        assertThrows(UnsupportedOperationException.class, () ->
                varHandle.set(holder, StableValue.of())
        );

        assertThrows(UnsupportedOperationException.class, () ->
                varHandle.compareAndSet(holder, original, StableValue.of())
        );

    }

}
