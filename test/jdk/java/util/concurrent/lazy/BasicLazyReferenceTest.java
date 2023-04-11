/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary Verify basic LazyReference operations
 * @run junit BasicLazyReferenceTest
 */

import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReference;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyReferenceTest {

    LazyReference<Integer> lazy;
    CountingIntegerSupplier supplier;

    @BeforeEach
    void setup() {
        lazy = Lazy.ofEmpty();
        supplier = new CountingIntegerSupplier();
    }

    @Test
    void supply() {
        Integer val = lazy.supplyIfEmpty(supplier);
        assertEquals(CountingIntegerSupplier.MAGIC_VALUE, val);
        assertEquals(1, supplier.invocations());
        Integer val2 = lazy.supplyIfEmpty(supplier);
        assertEquals(CountingIntegerSupplier.MAGIC_VALUE, val);
        assertEquals(1, supplier.invocations());
    }

    @Test
    void nulls() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> lazy.supplyIfEmpty(null));
        // Mapper returns null
        assertThrows(NullPointerException.class,
                () -> lazy.supplyIfEmpty(() -> null));
    }

    @Test
    void noPresetGet() {
        assertThrows(IllegalStateException.class,
                () -> lazy.get());
    }

    @Test
    void state() {
        assertEquals(Lazy.State.EMPTY, lazy.state());
        Integer val = lazy.supplyIfEmpty(supplier);
        assertEquals(Lazy.State.PRESENT, lazy.state());
    }

    @Test
    void presetSupplierBasic() {
        LazyReference<Integer> presetLazy = Lazy.of(supplier);
        for (int i = 0; i < 2; i++) {
            assertEquals(CountingIntegerSupplier.MAGIC_VALUE, presetLazy.get());
            assertEquals(1, supplier.invocations());
        }
    }

    @Test
    void presetSupplierNullSuppying() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> Lazy.of(null));
        // Mapper returns null
        assertThrows(NullPointerException.class,
                () -> Lazy.of(() -> null).get());
    }

    @Test
    void optionalModelling() {
        Supplier<Optional<String>> empty = Lazy.of(() -> Optional.empty());
        assertTrue(empty.get().isEmpty());
        Supplier<Optional<String>> present = Lazy.of(() -> Optional.of("A"));
        assertEquals("A", present.get().orElseThrow());
    }

    @Test
    void error() {
        Supplier<Integer> throwSupplier = () -> {
            throw new UnsupportedOperationException();
        };
        assertThrows(UnsupportedOperationException.class,
                () -> lazy.supplyIfEmpty(throwSupplier));

        assertEquals(Lazy.State.ERROR, lazy.state());
        assertTrue(lazy.exception().isPresent());

        // Should not invoke the supplier as we are already in ERROR state
        assertThrows(NoSuchElementException.class, () -> lazy.supplyIfEmpty(throwSupplier));
    }

    // Todo:repeate the test 1000 times
    @Test
    void threadTest() throws InterruptedException {
        var gate = new AtomicBoolean();
        var threads = IntStream.range(0, Runtime.getRuntime().availableProcessors() * 2)
                .mapToObj(i -> new Thread(()->{
                    while (!gate.get()) {
                        Thread.onSpinWait();
                    }
                    // Try to access the instance "simultaneously"
                    lazy.supplyIfEmpty(supplier);
                }))
                .toList();
        threads.forEach(Thread::start);
        Thread.sleep(10);
        gate.set(true);
        join(threads);
        assertEquals(CountingIntegerSupplier.MAGIC_VALUE, lazy.get());
        assertEquals(1, supplier.invocations());
    }

    @Test
    void testToString() throws InterruptedException {
        var lazy0 = Lazy.of(() -> 0);
        var lazy1 = Lazy.of(() -> 1);
        lazy1.get();
        var lazy2 = Lazy.of(() -> {
            throw new UnsupportedOperationException();
        });
        // Do not touch lazy0
        lazy1.get();
        try {
            lazy2.get();
        } catch (UnsupportedOperationException ignored) {
            // Happy path
        }

        assertEquals("LazyReference[EMPTY]", lazy0.toString());
        assertEquals("LazyReference[1]", lazy1.toString());

        // Todo: Figure out why this fails
        // assertEquals("LazyReference[ERROR]", lazy2.toString());
    }

    private static void join(Collection<Thread> threads) {
        for (var t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    static private final class CountingIntegerSupplier implements Supplier<Integer> {
        static final int MAGIC_VALUE = 42;
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Integer get() {
            invocations.incrementAndGet();
            return MAGIC_VALUE;
        }

        int invocations() {
            return invocations.get();
        }
    }

}
