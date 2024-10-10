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
 * @summary Basic tests for CachingFunction methods
 * @compile --enable-preview -source ${jdk.version} CachingFunctionTest.java
 * @run junit/othervm --enable-preview CachingFunctionTest
 */

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

final class CachingFunctionTest {

    private static final int VALUE = 42;
    private static final int VALUE2 = 13;
    private static final Set<Integer> INPUTS = Set.of(VALUE, VALUE2);
    private static final Function<Integer, Integer> MAPPER = Function.identity();

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> StableValue.newCachingFunction(null, MAPPER));
        assertThrows(NullPointerException.class, () -> StableValue.newCachingFunction(INPUTS, null));
        assertThrows(NullPointerException.class, () -> StableValue.newCachingFunction(null, MAPPER, Thread.ofVirtual().factory()));
        assertThrows(NullPointerException.class, () -> StableValue.newCachingFunction(INPUTS, null, Thread.ofVirtual().factory()));
        assertThrows(NullPointerException.class, () -> StableValue.newCachingFunction(INPUTS, MAPPER, null));
    }

    @Test
    void basic() {
        basic(MAPPER);
        basic(_ -> null);
    }

    void basic(Function<Integer, Integer> mapper) {
        StableTestUtil.CountingFunction<Integer, Integer> cif = new StableTestUtil.CountingFunction<>(mapper);
        var cached = StableValue.newCachingFunction(INPUTS, cif);
        assertEquals(mapper.apply(VALUE), cached.apply(VALUE));
        assertEquals(1, cif.cnt());
        assertEquals(mapper.apply(VALUE), cached.apply(VALUE));
        assertEquals(1, cif.cnt());
        assertTrue(cached.toString().startsWith("CachingFunction[values={"));
        // Key order is unspecified
        assertTrue(cached.toString().contains(VALUE2 + "=.unset"));
        assertTrue(cached.toString().contains(VALUE + "=[" + mapper.apply(VALUE) + "]"));
        assertTrue(cached.toString().endsWith(", original=" + cif + "]"));
        // One between the values and one just before "original"
        assertEquals(2L, cached.toString().chars().filter(ch -> ch == ',').count());
        var x = assertThrows(IllegalArgumentException.class, () -> cached.apply(-1));
        assertTrue(x.getMessage().contains("-1"));
    }

    @Test
    void background() {
        final AtomicInteger cnt = new AtomicInteger(0);
        ThreadFactory factory = r -> new Thread(() -> {
            r.run();
            cnt.incrementAndGet();
        });
        var cached = StableValue.newCachingFunction(INPUTS, MAPPER, factory);
        while (cnt.get() < 2) {
            Thread.onSpinWait();
        }
        assertEquals(VALUE, cached.apply(VALUE));
        assertEquals(VALUE2, cached.apply(VALUE2));
    }

    @Test
    void exception() {
        StableTestUtil.CountingFunction<Integer, Integer> cif = new StableTestUtil.CountingFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var cached = StableValue.newCachingFunction(INPUTS, cif);
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(VALUE));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> cached.apply(VALUE));
        assertEquals(2, cif.cnt());
        assertTrue(cached.toString().startsWith("CachingFunction[values={"));
        // Key order is unspecified
        assertTrue(cached.toString().contains(VALUE2 + "=.unset"));
        assertTrue(cached.toString().contains(VALUE + "=.unset"));
        assertTrue(cached.toString().endsWith(", original=" + cif + "]"));
    }

    @Test
    void circular() {
        final AtomicReference<Function<?, ?>> ref = new AtomicReference<>();
        Function<Integer, Function<?, ?>> cached = StableValue.newCachingFunction(INPUTS, _ -> ref.get());
        ref.set(cached);
        cached.apply(VALUE);
        String toString = cached.toString();
        assertTrue(toString.contains("(this CachingFunction)"));
        assertDoesNotThrow(cached::hashCode);
        assertDoesNotThrow((() -> cached.equals(cached)));
    }

    @Test
    void equality() {
        Function<Integer, Integer> mapper = Function.identity();
        Function<Integer, Integer> f0 = StableValue.newCachingFunction(INPUTS, mapper);
        Function<Integer, Integer> f1 = StableValue.newCachingFunction(INPUTS, mapper);
        // No function is equal to another function
        assertNotEquals(f0, f1);
    }

    @Test
    void hashCodeStable() {
        Function<Integer, Integer> f0 = StableValue.newCachingFunction(INPUTS, Function.identity());
        assertEquals(System.identityHashCode(f0), f0.hashCode());
        f0.apply(VALUE);
        assertEquals(System.identityHashCode(f0), f0.hashCode());
    }

}
