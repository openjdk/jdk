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
 * @summary Basic tests for Monotonic implementations
 * @run junit BasicMonotonicTest
 */

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class BasicMonotonicTest {

    @Test
    void testTypes() {
        Monotonic<Integer> m1 = Monotonic.of(Integer.class);
        Monotonic<Integer> m2 = Monotonic.of(int.class);
        Monotonic<Number> m3 = Monotonic.of(Integer.class);
        Monotonic<Object> m4 = Monotonic.of(Integer.class);
        Monotonic<Object> m5 = Monotonic.of(int.class);
    }

    @Test
    void testIntegerOld() {
        Monotonic<Integer> m = Monotonic.of(Integer.class);
        assertFalse(m.isPresent());
        assertNull(m.get());

        m.put(42);
        assertTrue(m.isPresent());
        assertEquals(42, m.get());
        assertThrows(IllegalStateException.class, () -> m.put(13));
        assertTrue(m.isPresent());
        assertEquals(42, m.get());

/*        MethodHandle handle = m.getter();
        assertEquals(Object.class, handle.type().returnType());
        assertEquals(1, handle.type().parameterCount());
        assertEquals(Monotonic.class, handle.type().parameterType(0));
        try {
            Integer i = (Integer) handle.invoke(m);
            assertEquals(42, i);
        } catch (Throwable t) {
            fail(t);
        }*/
    }

    @Test
    void testIntOld() {
        Monotonic<Integer> m = Monotonic.of(int.class);
        assertFalse(m.isPresent());
        assertNull(m.get());

        m.put(42);
        assertTrue(m.isPresent());
        assertEquals(42, m.get());
        assertThrows(IllegalStateException.class, () -> m.put(13));
        assertTrue(m.isPresent());
        assertEquals(42, m.get());
        Supplier<Integer> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };
        assertDoesNotThrow(() -> m.computeIfAbsent(throwingSupplier));

/*        MethodHandle handle = m.getter();
        assertEquals(int.class, handle.type().returnType());
        assertEquals(1, handle.type().parameterCount());

        assertEquals(Monotonic.class, handle.type().parameterType(0));
        try {
            Integer i = (int) handle.invoke(m);
            assertEquals(42, i);
        } catch (Throwable t) {
            fail(t);
        }*/
    }

    @Test
    void testString() {
        testMonotonic(String.class, "A", "B");
    }

    @Test
    void testInteger() {
        testMonotonic(Integer.class, 42, 13);
    }

    @Test
    void testInt() {
        testMonotonic(int.class, 42, 13);
    }

    @Test
    void testLong() {
        testMonotonic(long.class, 42L, 13L);
    }

    interface MethodHandleInvoker<T> {
        T apply(Monotonic<T> monotonic) throws Throwable;
    }

    <T> void testMonotonic(Class<T> type,
                           T first,
                           T second) {

        // unbound
        Monotonic<T> m = Monotonic.of(type);
        assertFalse(m.isPresent());
        assertNull(m.get());

        // bind()
        m.put(first);
        assertTrue(m.isPresent());
        assertEquals(first, m.get());
        assertThrows(IllegalStateException.class, () -> m.put(second));
        assertTrue(m.isPresent());
        assertEquals(first, m.get());

        // getter()
/*        MethodHandle handle = m.getter();
        if (type.isPrimitive()) {
            assertEquals(type, handle.type().returnType());
        } else {
            assertEquals(Object.class, handle.type().returnType());
        }
        assertEquals(1, handle.type().parameterCount());

        assertEquals(Monotonic.class, handle.type().parameterType(0));
        try {
            T t = invoker.apply(m);
            assertEquals(first, t);
        } catch (Throwable t) {
            fail(t);
        }*/

        // computeIfAbsent()
        Supplier<T> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };
        assertDoesNotThrow(() -> m.computeIfAbsent(throwingSupplier));

        var m2 = Monotonic.of(type);
        m2.computeIfAbsent(() -> first);
        assertEquals(first, m2.get());


        // Memoized
        CountingSupplier<T> cSup = new CountingSupplier<>(() -> first);
        Monotonic<T> m3 = Monotonic.of(type);
        Supplier<T> memoized = () -> m3.computeIfAbsent(cSup);
        assertEquals(first, memoized.get());
        // Make sure the original supplier is not invoked more than once
        assertEquals(first, memoized.get());
        assertEquals(1, cSup.cnt());
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
