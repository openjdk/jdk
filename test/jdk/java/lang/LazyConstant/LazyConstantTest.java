/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for the LazyConstant implementation
 * @enablePreview
 * @library /test/lib
 * @modules java.base/jdk.internal.lang
 * @run junit/othervm --add-opens java.base/jdk.internal.lang=ALL-UNNAMED LazyConstantTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.LazyConstant;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import jdk.test.lib.Utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LazyConstantTest {

    private static final int VALUE = 42;
    private static final Supplier<Integer> SUPPLIER = () -> VALUE;
    private static final long TIME_OUT_S = Utils.adjustTimeout(5);
    private static final long OVERLAP_TIME_MS = 100;

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> LazyConstant.of(null));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void basic(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(SUPPLIER);
        var lazy = factory.apply(cs);
        assertEquals(SUPPLIER.get(), lazy.get());
        assertEquals(1, cs.cnt());
        assertEquals(SUPPLIER.get(), lazy.get());
        assertEquals(1, cs.cnt());
        assertTrue(lazy.toString().contains(Integer.toString(SUPPLIER.get())));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void exceptionInComputingFunction(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        // Test different Throwable categories
        for (LazyConstantTestUtil.Thrower thrower : LazyConstantTestUtil.throwers()) {
            AtomicReference<Throwable> exceptionThrown = new AtomicReference<>();
            LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(() -> {
                Throwable t = thrower.supplier().get();
                exceptionThrown.set(t);
                LazyConstantTestUtil.sneakyThrow(t);
                return 42; // Unreachable
            });
            exceptionInComputingFunction(factory, cs, () -> exceptionThrown.get().getClass(), thrower.message());
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void nullInComputingFunction(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(() -> {
            return null;
        });
        exceptionInComputingFunction(factory, cs, () -> NullPointerException.class, null);
    }

    void exceptionInComputingFunction(Function<Supplier<Integer>, LazyConstant<Integer>> factory,
                                      LazyConstantTestUtil.CountingSupplier<Integer> cs,
                                      Supplier<Class<? extends Throwable>> causeTypeSupplier,
                                      String message) {
        var lazy = factory.apply(cs);
        var ix = assertThrows(NoSuchElementException.class, lazy::get);
        // Now we can look at the throwable
        var causeType = causeTypeSupplier.get();
        assertEquals(causeType, ix.getCause().getClass());
        if (message != null) {
            assertEquals(message, ix.getCause().getMessage());
        }
        assertEquals(1, cs.cnt());
        var x = assertThrows(NoSuchElementException.class, lazy::get);
        assertEquals("Unable to access the constant because "+causeType.getName()+" was thrown at initial computation", x.getMessage());
        assertEquals(1, cs.cnt());
        var toString = lazy.toString();
        assertTrue(toString.contains("failed with="+causeType.getName()), toString);
        assertNull(x.getCause());
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void get(LazyConstant<Integer> constant) {
        assertEquals(VALUE, constant.get());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testHashCode(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(SUPPLIER);
        var lazy = factory.apply(cs);
        assertEquals(System.identityHashCode(lazy), lazy.hashCode());
        assertEquals(System.identityHashCode(lazy), lazy.hashCode());
        // The supplier should never be invoked
        assertEquals(0, cs.cnt());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void testEquals(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(SUPPLIER);
        var lazy = factory.apply(cs);
        assertNotEquals(null, lazy);
        LazyConstant<Integer> different = LazyConstant.of(SUPPLIER);
        assertNotEquals(different, lazy);
        assertNotEquals(lazy, different);
        assertNotEquals("a", lazy);
        // The supplier should never be invoked
        assertEquals(0, cs.cnt());
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void testLazyConstantAsComputingFunction(LazyConstant<Integer> constant) {
        LazyConstant<Integer> c1 = LazyConstant.of(constant);
        assertNotSame(constant, c1);
    }

    @Test
    void toStringTest() {
        Supplier<String> supplier = () -> "str";
        LazyConstant<String> lazy = LazyConstant.of(supplier);
        var expectedSubstring = "computing function=" + supplier;
        assertTrue(lazy.toString().contains(expectedSubstring));
        lazy.get();
        assertTrue(lazy.toString().contains("str"));
    }

    @ParameterizedTest
    @MethodSource("lazyConstants")
    void toStringUnset(LazyConstant<Integer> constant) {
        String unInitializedToString = constant.toString();
        int suffixEnd = unInitializedToString.indexOf("[");
        String suffix = unInitializedToString.substring(0, suffixEnd);
        String expectedUninitialized = suffix+"[computing function=";
        assertTrue(unInitializedToString.startsWith(expectedUninitialized));
        constant.get();
        String expectedInitialized = suffix + "[" + VALUE + "]";
        assertEquals(expectedInitialized, constant.toString());
    }

    @Test
    void toStringCircular() {
        AtomicReference<LazyConstant<?>> ref = new AtomicReference<>();
        LazyConstant<LazyConstant<?>> constant = LazyConstant.of(ref::get);
        ref.set(constant);
        constant.get();
        String toString = assertDoesNotThrow(constant::toString);
        assertTrue(constant.toString().contains("(this LazyConstant)"), toString);
    }

    @Test
    void recursiveCall() {
        AtomicReference<LazyConstant<Integer>> ref = new AtomicReference<>();
        LazyConstant<Integer> constant = LazyConstant.of(() -> ref.get().get());
        ref.set(constant);
        var x = assertThrows(NoSuchElementException.class, constant::get);
        assertEquals(IllegalStateException.class, x.getCause().getClass());
    }

    @Test
    void recursiveCallWithComputingFunctionsToStringThrowing() {
        AtomicReference<LazyConstant<Integer>> ref = new AtomicReference<>();
        AtomicInteger cnt = new AtomicInteger();

        final class NaughtySupplier implements Supplier<Integer> {
            @Override
            public Integer get() {
                return ref.get().get();
            }

            @Override
            public String toString() {
                cnt.incrementAndGet();
                throw new UnsupportedOperationException("I should never be seen");
            }
        }

        LazyConstant<Integer> constant = LazyConstant.of(new NaughtySupplier());

        ref.set(constant);
        var x = assertThrows(NoSuchElementException.class, constant::get);
        assertEquals(IllegalStateException.class, x.getCause().getClass());
        assertEquals(1, cnt.get());
        assertEquals("Unable to access the constant because java.lang.IllegalStateException was thrown at initial computation", x.getMessage());
        assertTrue(x.getCause().getMessage().contains(NaughtySupplier.class.getName()),  x.getCause().getMessage());
    }

    @Test
    void atMostOnceComputationUnderContention() throws Exception {
        // Mitigate thread starvation via a dedicated thread pool != FJP
        try (var testExecutor = Executors.newFixedThreadPool(3)) {
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch competing = new CountDownLatch(2);

            LazyConstant<Integer> constant = LazyConstant.of(() -> {
                calls.incrementAndGet();
                entered.countDown();
                try {
                    assertTrue(release.await(TIME_OUT_S, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                return VALUE;
            });

            var f1 = CompletableFuture.supplyAsync(constant::get, testExecutor);
            assertTrue(entered.await(TIME_OUT_S, TimeUnit.SECONDS));

            var f2 = CompletableFuture.supplyAsync(() -> {
                competing.countDown();
                return constant.get();
            }, testExecutor);
            var f3 = CompletableFuture.supplyAsync(() -> {
                competing.countDown();
                return constant.get();
            }, testExecutor);

            assertTrue(competing.await(TIME_OUT_S, TimeUnit.SECONDS));
            // While computation is blocked, only one thread should have entered supplier
            assertEquals(1, calls.get());

            release.countDown();

            assertEquals(VALUE, f1.get(TIME_OUT_S, TimeUnit.SECONDS));
            assertEquals(VALUE, f2.get(TIME_OUT_S, TimeUnit.SECONDS));
            assertEquals(VALUE, f3.get(TIME_OUT_S, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void competingThreadsBlockUntilInitializationCompletes() throws Exception {
        // Mitigate thread starvation via a dedicated thread pool != FJP
        try (var testExecutor = Executors.newFixedThreadPool(2)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch waiting = new CountDownLatch(1);

            LazyConstant<Integer> constant = LazyConstant.of(() -> {
                entered.countDown();
                try {
                    assertTrue(release.await(TIME_OUT_S, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                return VALUE;
            });

            var computingThread = CompletableFuture.supplyAsync(constant::get, testExecutor);
            assertTrue(entered.await(TIME_OUT_S, TimeUnit.SECONDS));

            var waitingThread = CompletableFuture.supplyAsync(() -> {
                waiting.countDown();
                return constant.get();
            }, testExecutor);

            assertTrue(waiting.await(TIME_OUT_S, TimeUnit.SECONDS));
            Thread.sleep(OVERLAP_TIME_MS);
            assertFalse(waitingThread.isDone(), "contending thread should be be blocked");

            release.countDown();

            assertEquals(VALUE, computingThread.get(TIME_OUT_S, TimeUnit.SECONDS));
            assertEquals(VALUE, waitingThread.get(TIME_OUT_S, TimeUnit.SECONDS));
        }
    }

    @Test
    void interruptStatusIsPreservedForComputingThread() throws Exception {
        int unset = -1;
        int notInterrupted = 0;
        int interrupted = 1;
        AtomicInteger observedInterrupted = new AtomicInteger(unset);
        CountDownLatch supplierRunning = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        LazyConstant<Integer> constant = LazyConstant.of(() -> {
            supplierRunning.countDown();
            try {
                assertTrue(release.await(TIME_OUT_S, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                observedInterrupted.set(Thread.currentThread().isInterrupted() ? interrupted : notInterrupted);
                Thread.currentThread().interrupt(); // restore if await cleared it
            }
            return VALUE;
        });

        AtomicInteger interruptedAfterGet = new AtomicInteger(unset);

        Thread t = Thread.ofPlatform().start(() -> {
            assertEquals(VALUE, constant.get());
            interruptedAfterGet.set(Thread.currentThread().isInterrupted() ? interrupted : notInterrupted);
        });

        assertTrue(supplierRunning.await(TIME_OUT_S, TimeUnit.SECONDS));
        Thread.sleep(OVERLAP_TIME_MS);
        t.interrupt();
        release.countDown();
        t.join();

        assertEquals(notInterrupted, observedInterrupted.get()); // Observed before restoration of the status
        assertEquals(interrupted, interruptedAfterGet.get(), "get() cleared interrupt status");
    }

    @ParameterizedTest
    @MethodSource("factories")
    void underlying(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(SUPPLIER);
        var f1 = factory.apply(cs);

        Object underlyingBefore = LazyConstantTestUtil.computingFunction(f1);
        assertSame(cs, underlyingBefore);
        int v = f1.get();
        Object underlyingAfter = LazyConstantTestUtil.computingFunction(f1);
        assertNull(underlyingAfter);
    }

    @ParameterizedTest
    @MethodSource("factories")
    void functionHolderException(Function<Supplier<Integer>, LazyConstant<Integer>> factory) {
        LazyConstantTestUtil.CountingSupplier<Integer> cs = new LazyConstantTestUtil.CountingSupplier<>(() -> {
            throw new UnsupportedOperationException();
        });
        var f1 = factory.apply(cs);

        Object underlyingBefore = LazyConstantTestUtil.computingFunction(f1);
        assertSame(cs, underlyingBefore);

        var x = assertThrows(NoSuchElementException.class, f1::get);
        assertEquals(UnsupportedOperationException.class, x.getCause().getClass());

        Object underlyingAfter = LazyConstantTestUtil.computingFunction(f1);
        assertEquals(UnsupportedOperationException.class.getName(), underlyingAfter);
    }

    private static Stream<LazyConstant<Integer>> lazyConstants() {
        return factories()
                .map(f -> f.apply(() -> VALUE));
    }

    private static Stream<Function<Supplier<Integer>, LazyConstant<Integer>>> factories() {
        return Stream.of(
                supplier("ComputedConstant.of(<lambda>)", LazyConstant::of)
        );
    }

    private static Function<Supplier<Integer>, LazyConstant<Integer>> supplier(String name,
                                                                               Function<Supplier<Integer>, LazyConstant<Integer>> underlying) {
        return new Function<Supplier<Integer>, LazyConstant<Integer>>() {
            @Override
            public LazyConstant<Integer> apply(Supplier<Integer> supplier) {
                return underlying.apply(supplier);
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

}
