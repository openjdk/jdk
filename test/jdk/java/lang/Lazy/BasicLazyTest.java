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
 * @summary Basic tests for Lazy implementations
 * @compile --enable-preview -source ${jdk.version} BasicLazyTest.java
 * @run junit/othervm --enable-preview BasicLazyTest
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyTest {

    private static final int FIRST = 42;
    private static final int SECOND = 13;

    private Lazy<Integer> m;

    @BeforeEach
    void setup() {
        m = Lazy.of();
    }

    @Test
    void unbound() {
        assertFalse(m.isSet());
        assertThrows(NoSuchElementException.class, m::orThrow);
    }

    void bind() {
        m.setOrThrow(FIRST);
        assertTrue(m.isSet());
        assertEquals(FIRST, m.orThrow());
        assertThrows(IllegalStateException.class, () -> m.setOrThrow(SECOND));
        assertTrue(m.isSet());
        assertEquals(FIRST, m.orThrow());
    }

    @Test
    void bindIfAbsent() {
        Integer i = m.setIfUnset(FIRST);
        assertTrue(m.isSet());
        assertEquals(FIRST, i);
        assertEquals(FIRST, m.orThrow());

        assertEquals(FIRST, m.setIfUnset(FIRST));
        assertEquals(FIRST, m.setIfUnset(SECOND));
        assertEquals(FIRST, m.setIfUnset(null));
    }

    @Test
    void bindIfAbsentNull() {
        Integer i = m.setIfUnset(null);
        assertTrue(m.isSet());
        assertNull(i);
        assertNull(m.orThrow());

        assertNull(m.setIfUnset(null));
        assertNull(m.setIfUnset(FIRST));
        assertNull(m.setIfUnset(SECOND));
    }

    @Test
    void computeIfAbsent() {
        m.computeIfUnset(() -> FIRST);
        assertEquals(FIRST, m.orThrow());

        Supplier<Integer> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };
        assertDoesNotThrow(() -> m.computeIfUnset(throwingSupplier));

        var m2 = Lazy.of();
        m2.computeIfUnset(() -> FIRST);
        assertEquals(FIRST, m2.orThrow());
    }

    @Test
    void computeIfAbsentNull() {
        CountingSupplier<Integer> c = new CountingSupplier<>(() -> null);
        m.computeIfUnset(c);
        assertNull(m.orThrow());
        assertEquals(1, c.cnt());
        m.computeIfUnset(c);
        assertEquals(1, c.cnt());
    }

    @Test
    void memoized() {
        CountingSupplier<Integer> cSup = new CountingSupplier<>(() -> FIRST);
        Lazy<Integer> m3 = Lazy.of();
        Supplier<Integer> memoized = () -> m3.computeIfUnset(cSup);
        assertEquals(FIRST, memoized.get());
        // Make sure the original supplier is not invoked more than once
        assertEquals(FIRST, memoized.get());
        assertEquals(1, cSup.cnt());
    }

    @Test
    void reflection() throws NoSuchFieldException {
        final class Holder {
            private final Lazy<Integer> lazy = Lazy.of();
        }
        final class HolderNonFinal {
            private Lazy<Integer> lazy = Lazy.of();
        }

        Field field = Holder.class.getDeclaredField("lazy");
        assertThrows(InaccessibleObjectException.class, () ->
                        field.setAccessible(true)
                );

        Field fieldNonFinal = HolderNonFinal.class.getDeclaredField("lazy");
        assertDoesNotThrow(() -> fieldNonFinal.setAccessible(true));
    }

    @Test
    void sunMiscUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe)unsafeField.get(null);

        final class Holder {
            private final Lazy<Integer> lazy = Lazy.of();
        }
        Field field = Holder.class.getDeclaredField("lazy");
        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(field)
        );

    }

    @Test
    void varHandle() throws NoSuchFieldException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        Lazy<Integer> original = Lazy.of();

        final class Holder {
            private final Lazy<Integer> monotonic = original;
        }

        VarHandle varHandle = lookup.findVarHandle(Holder.class, "monotonic", Lazy.class);
        Holder holder = new Holder();

        assertThrows(UnsupportedOperationException.class, () ->
                varHandle.set(holder, Lazy.of())
        );

        assertThrows(UnsupportedOperationException.class, () ->
                varHandle.compareAndSet(holder, original, Lazy.of())
        );

    }


    static final class CountingSupplier<T> implements Supplier<T> {

        private final AtomicInteger cnt = new AtomicInteger();
        private final Supplier<T> delegate;

        public CountingSupplier(Supplier<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T get() {
            cnt.incrementAndGet();
            return delegate.get();
        }

        public int cnt() {
            return cnt.get();
        }

        @Override
        public String toString() {
            return cnt.toString();
        }
    }
}
