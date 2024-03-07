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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

final class BasicMonotonicTest {

    private static final int FIRST = 42;
    private static final int SECOND = 13;

    private Monotonic<Integer> m;

    @BeforeEach
    void setup() {
        m = Monotonic.of();
    }

    @Test
    void unbound() {
        assertFalse(m.isPresent());
        assertThrows(NoSuchElementException.class, m::get);
    }

    void bind() {
        m.bind(FIRST);
        assertTrue(m.isPresent());
        assertEquals(FIRST, m.get());
        assertThrows(IllegalStateException.class, () -> m.bind(SECOND));
        assertTrue(m.isPresent());
        assertEquals(FIRST, m.get());
    }

    @Test
    void computeIfAbsent() {
        m.computeIfAbsent(() -> FIRST);
        assertEquals(FIRST, m.get());

        Supplier<Integer> throwingSupplier = () -> {
            throw new UnsupportedOperationException();
        };
        assertDoesNotThrow(() -> m.computeIfAbsent(throwingSupplier));

        var m2 = Monotonic.of();
        m2.computeIfAbsent(() -> FIRST);
        assertEquals(FIRST, m2.get());
    }

    @Test
    void computeIfAbsentNull() {
        CountingSupplier<Integer> c = new CountingSupplier<>(() -> null);
        m.computeIfAbsent(c);
        assertNull(m.get());
        assertEquals(1, c.cnt());
        m.computeIfAbsent(c);
        assertEquals(1, c.cnt());
    }

    @Test
    void memoized() {
        CountingSupplier<Integer> cSup = new CountingSupplier<>(() -> FIRST);
        Monotonic<Integer> m3 = Monotonic.of();
        Supplier<Integer> memoized = () -> m3.computeIfAbsent(cSup);
        assertEquals(FIRST, memoized.get());
        // Make sure the original supplier is not invoked more than once
        assertEquals(FIRST, memoized.get());
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
