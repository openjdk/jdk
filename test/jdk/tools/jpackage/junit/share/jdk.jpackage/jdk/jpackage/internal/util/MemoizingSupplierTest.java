/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static jdk.jpackage.internal.util.function.ThrowingRunnable.toRunnable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;


class MemoizingSupplierTest {

    @Test
    void test() {
        var supplier = count(() -> "foo");
        var runOnceSupplier = MemoizingSupplier.runOnce(supplier);

        assertEquals(0, supplier.counter());

        assertEquals("foo", runOnceSupplier.get());
        assertEquals("foo", runOnceSupplier.get());

        assertEquals(1, supplier.counter());
    }

    @Test
    void test_null() {
        CountingSupplier<String> supplier = count(() -> null);
        var runOnceSupplier = MemoizingSupplier.runOnce(supplier);

        assertEquals(0, supplier.counter());

        assertEquals(null, runOnceSupplier.get());
        assertEquals(null, runOnceSupplier.get());

        assertEquals(1, supplier.counter());
    }

    @Test
    void test_throws_Exception() {
        CountingSupplier<String> supplier = count(() -> {
            throw new IllegalStateException("Kaput!");
        });
        var runOnceSupplier = MemoizingSupplier.runOnce(supplier);

        assertEquals(0, supplier.counter());

        assertThrowsExactly(IllegalStateException.class, () -> {
            runOnceSupplier.get();
        });

        assertThrowsExactly(IllegalStateException.class, () -> {
            runOnceSupplier.get();
        });

        assertEquals(1, supplier.counter());
    }

    @Test
    void test_throws_Error() {
        CountingSupplier<String> supplier = count(() -> {
            throw new Error("Grand kaput!");
        });
        var runOnceSupplier = MemoizingSupplier.runOnce(supplier);

        assertEquals(0, supplier.counter());

        assertThrowsExactly(Error.class, () -> {
            runOnceSupplier.get();
        });

        assertThrowsExactly(Error.class, () -> {
            runOnceSupplier.get();
        });

        assertEquals(1, supplier.counter());
    }

    @Test
    void testAsync() throws InterruptedException {
        var supplier = count(() -> "foo");
        var runOnceSupplier = MemoizingSupplier.runOnce(supplier);

        final var supplierCount = 100;
        final var supplierExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Schedule invoking "runOnceSupplier.get()" in a separate virtual threads.
        // Start and suspend threads, waiting until all scheduled threads have started.
        // After all scheduled threads start, resume them.
        // This should result in multiple simultaneous "runOnceSupplier.get()" calls.

        var readyLatch = new CountDownLatch(supplierCount);
        var startLatch = new CountDownLatch(1);

        var futures = IntStream.range(0, supplierCount).mapToObj(_ -> {
            return CompletableFuture.runAsync(toRunnable(() -> {
                readyLatch.countDown();
                startLatch.await();
                runOnceSupplier.get();

            }), supplierExecutor);
        }).toList();

        readyLatch.await();
        startLatch.countDown();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        assertEquals(1, supplier.counter());
    }

    private static <T> CountingSupplier<T> count(Supplier<T> supplier) {
        return new CountingSupplier<>(supplier);
    }

    private static final class CountingSupplier<T> implements Supplier<T> {

        CountingSupplier(Supplier<T> impl) {
            this.impl = Objects.requireNonNull(impl);
        }

        @Override
        public T get() {
            counter++;
            return impl.get();
        }

        int counter() {
            return counter;
        }

        private int counter;
        private final Supplier<T> impl;
    }
}
