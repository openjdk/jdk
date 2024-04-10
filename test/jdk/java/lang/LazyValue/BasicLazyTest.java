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
 * @summary Basic tests for LazyValue implementations
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

    private LazyValue<Integer> lazy;

    @BeforeEach
    void setup() {
        lazy = LazyValue.of();
    }

    @Test
    void unset() {
        assertFalse(lazy.isSet());
        assertThrows(NoSuchElementException.class, lazy::orThrow);
    }

    @Test
    void setOrThrow() {
        lazy.setOrThrow(FIRST);
        assertTrue(lazy.isSet());
        assertEquals(FIRST, lazy.orThrow());
        assertThrows(IllegalStateException.class, () -> lazy.setOrThrow(SECOND));
        assertTrue(lazy.isSet());
        assertEquals(FIRST, lazy.orThrow());
    }

    @Test
    void setIfUnset() {
        Integer i = lazy.setIfUnset(FIRST);
        assertTrue(lazy.isSet());
        assertEquals(FIRST, i);
        assertEquals(FIRST, lazy.orThrow());

        assertEquals(FIRST, lazy.setIfUnset(FIRST));
        assertEquals(FIRST, lazy.setIfUnset(SECOND));
        assertEquals(FIRST, lazy.setIfUnset(null));
    }

    @Test
    void setIfUnsetNull() {
        Integer i = lazy.setIfUnset(null);
        assertTrue(lazy.isSet());
        assertNull(i);
        assertNull(lazy.orThrow());

        assertNull(lazy.setIfUnset(null));
        assertNull(lazy.setIfUnset(FIRST));
        assertNull(lazy.setIfUnset(SECOND));
    }

    @Test
    void computeIfUnset() {
        lazy.computeIfUnset(() -> FIRST);
        assertEquals(FIRST, lazy.orThrow());

        Supplier<Integer> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };
        assertDoesNotThrow(() -> lazy.computeIfUnset(throwingSupplier));

        var m2 = LazyValue.of();
        m2.computeIfUnset(() -> FIRST);
        assertEquals(FIRST, m2.orThrow());
    }

    @Test
    void computeIfUnsetNull() {
        CountingSupplier<Integer> c = new CountingSupplier<>(() -> null);
        lazy.computeIfUnset(c);
        assertNull(lazy.orThrow());
        assertEquals(1, c.cnt());
        lazy.computeIfUnset(c);
        assertEquals(1, c.cnt());
    }

    @Test
    void memoized() {
        CountingSupplier<Integer> cSup = new CountingSupplier<>(() -> FIRST);
        LazyValue<Integer> m3 = LazyValue.of();
        Supplier<Integer> memoized = () -> m3.computeIfUnset(cSup);
        assertEquals(FIRST, memoized.get());
        // Make sure the original supplier is not invoked more than once
        assertEquals(FIRST, memoized.get());
        assertEquals(1, cSup.cnt());
    }

    @Test
    void testToString() {
        assertEquals("LazyValue.unset", lazy.toString());
        lazy.setOrThrow(1);
        assertEquals("LazyValue[1]", lazy.toString());
    }

    @Test
    void reflection() throws NoSuchFieldException {
        final class Holder {
            private final LazyValue<Integer> lazy = LazyValue.of();
        }
        final class HolderNonFinal {
            private LazyValue<Integer> lazy = LazyValue.of();
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
            private final LazyValue<Integer> lazy = LazyValue.of();
        }
        Field field = Holder.class.getDeclaredField("lazy");
        assertThrows(UnsupportedOperationException.class, () ->
                unsafe.objectFieldOffset(field)
        );

    }

    @Test
    void varHandle() throws NoSuchFieldException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        LazyValue<Integer> original = LazyValue.of();

        final class Holder {
            private final LazyValue<Integer> monotonic = original;
        }

        VarHandle varHandle = lookup.findVarHandle(Holder.class, "monotonic", LazyValue.class);
        Holder holder = new Holder();

        assertThrows(UnsupportedOperationException.class, () ->
                varHandle.set(holder, LazyValue.of())
        );

        assertThrows(UnsupportedOperationException.class, () ->
                varHandle.compareAndSet(holder, original, LazyValue.of())
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
