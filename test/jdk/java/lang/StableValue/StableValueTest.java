/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @run junit StableValueTest
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.StableValue;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StableValueTest {

    private static final int VALUE = 42;
    private static final int VALUE2 = 13;

    @ParameterizedTest
    @MethodSource("stableValues")
    void trySet(StableValue<Integer> stable) {
        assertTrue(stable.trySet(VALUE));
        assertFalse(stable.trySet(VALUE));
        assertFalse(stable.trySet(VALUE2));
        assertEquals(VALUE, stable.get());
    }

    @ParameterizedTest
    @MethodSource("presetStableValues")
    void preSet(StableValue<Integer> stable) {
        assertTrue(stable.isSet());
        assertEquals(VALUE, stable.get());
        assertEquals(VALUE, stable.toOptional().orElseThrow());
        assertEquals(VALUE, stable.orElseSet(() -> VALUE2));
        assertFalse(stable.trySet(VALUE2));
    }

    @ParameterizedTest
    @MethodSource("stableValues")
    void toOptional(StableValue<Integer> stable) {
        assertTrue(stable.toOptional().isEmpty());
        stable.trySet(VALUE);
        assertEquals(VALUE, stable.toOptional().orElseThrow());
    }

    @ParameterizedTest
    @MethodSource("stableValues")
    void get(StableValue<Integer> stable) {
        var e = assertThrows(NoSuchElementException.class, stable::get);
        assertEquals("No contents set", e.getMessage());
        stable.trySet(VALUE);
        assertEquals(VALUE, stable.get());
    }

    @ParameterizedTest
    @MethodSource("stableValues")
    void isSet(StableValue<Integer> stable) {
        assertFalse(stable.isSet());
        stable.trySet(VALUE);
        assertTrue(stable.isSet());
   }

    @ParameterizedTest
    @MethodSource("stableValues")
   void testOrElseSetSupplier(StableValue<Integer> stable) {
       StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> VALUE);
       assertThrows(NullPointerException.class, () -> stable.orElseSet(null));
       assertEquals(VALUE, stable.orElseSet(cs));
       assertEquals(1, cs.cnt());
       assertEquals(VALUE, stable.orElseSet(cs));
       assertEquals(1, cs.cnt());
   }

    @ParameterizedTest
    @MethodSource("stableValues")
    void testHashCode(StableValue<Integer> stable) {
        StableValue<Integer> stableValue = StableValue.of();
        // Should be Object::hashCode
        assertEquals(System.identityHashCode(stableValue), stableValue.hashCode());
    }

    @ParameterizedTest
    @MethodSource("stableValues")
    void testEquals(StableValue<Integer> stable) {
        StableValue<Integer> s0 = StableValue.of();
        assertNotEquals(null, s0);
        StableValue<Integer> s1 = StableValue.of();
        assertNotEquals(s0, s1); // Identity based
        s0.trySet(42);
        s1.trySet(42);
        assertNotEquals(s0, s1);
        assertNotEquals("a", s0);
        StableValue<Integer> null0 = StableValue.of();
        StableValue<Integer> null1 = StableValue.of();
    }

    @ParameterizedTest
    @MethodSource("stableValues")
    void toStringUnset(StableValue<Integer> stable) {
        assertEquals(".unset", stable.toString());
    }

    @ParameterizedTest
    @MethodSource("stableValues")
    void toStringNonNull(StableValue<Integer> stable) {
        assertTrue(stable.trySet(VALUE));
        assertEquals(Objects.toString(VALUE), stable.toString());
    }

    @ParameterizedTest
    @MethodSource("circleStableValues")
    void toStringCircular(StableValue<StableValue<?>> stable) {
        stable.trySet(stable);
        String toString = assertDoesNotThrow(stable::toString);
        assertEquals("(this StableValue)", toString);
        assertDoesNotThrow(stable::hashCode);
        assertDoesNotThrow((() -> stable.equals(stable)));
    }

    @ParameterizedTest
    @MethodSource("stableValues")
    void recursiveCall(StableValue<Integer> stable) {
        AtomicReference<StableValue<Integer>> ref = new AtomicReference<>(stable);
        assertThrows(IllegalStateException.class, () ->
                stable.orElseSet(() -> {
                    ref.get().trySet(1);
                    return 1;
                })
        );
        assertThrows(IllegalStateException.class, () ->
                stable.orElseSet(() -> {
                    ref.get().orElseSet(() -> 1);
                    return 1;
                })
        );
    }

    @ParameterizedTest
    @MethodSource("aliasStableValues")
    void aliasSet(Pair<StableValue<Integer>, StableValue<Integer>> pair) {
        assertTrue(pair.l().trySet(VALUE));
        assertEquals(pair.l().get(), pair.r().get());
    }

    private static final BiPredicate<StableValue<Integer>, Integer> TRY_SET = StableValue::trySet;

    @ParameterizedTest
    @MethodSource("factories")
    void race(Supplier<StableValue<Integer>> factory) {
        int noThreads = 10;
        CountDownLatch starter = new CountDownLatch(noThreads);
        StableValue<Integer> stable = factory.get();
        Map<Integer, Boolean> winners = new ConcurrentHashMap<>();
        List<Thread> threads = IntStream.range(0, noThreads).mapToObj(i -> new Thread(() -> {
                    try {
                        // Ready ...
                        starter.countDown();
                        // ... set ...
                        starter.await();
                        // Here we go!
                        winners.put(i, TRY_SET.test(stable, i));
                    } catch (Throwable t) {
                        fail(t);
                    }
                }))
                .toList();
        threads.forEach(Thread::start);
        threads.forEach(StableValueTest::join);
        // There can only be one winner
        assertEquals(1, winners.values().stream().filter(b -> b).count());
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            fail(e);
        }
    }

    private static final int LIST_SIZE = 8;
    private static final int LIST_MID = 3;

    private static Stream<StableValue<Integer>> stableValues() {
        return factories()
                .map(Supplier::get);
    }

    private static Stream<StableValue<Integer>> presetStableValues() {
        final List<StableValue<Integer>> list = StableValue.ofList(VALUE, 1, 2, VALUE, 4, 5, 6, VALUE);
        return Stream.of(
                StableValue.of(VALUE),
                list.getFirst(),
                list.get(LIST_MID),
                list.getLast()
        );
    }

    private static Stream<StableValue<StableValue<?>>> circleStableValues() {
        final List<StableValue<StableValue<?>>> list = StableValue.ofList(LIST_SIZE);
        return Stream.of(
                StableValue.of(),
                list.getFirst(),
                list.get(LIST_MID),
                list.getLast()
        );
    }

    private static Stream<Supplier<StableValue<Integer>>> factories() {
        final List<StableValue<Integer>> list = StableValue.ofList(LIST_SIZE);
        return Stream.of(
                supplier("StableValue.of()", StableValue::of),
                supplier("list::getFirst", list::getFirst),
                supplier("() -> list.get(LIST_MID)", () -> list.get(LIST_MID)),
                supplier("list::getLast", list::getLast)
        );
    }

    private static <T> Supplier<T> supplier(String name, Supplier<? extends T> underlying) {
        return new Supplier<T>() {
            @Override
            public T get() {
                return underlying.get();
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    record Pair<L, R>(L l, R r) {}

    private static Stream<Pair<StableValue<Integer>, StableValue<Integer>>> aliasStableValues() {
        final StableValue<Integer> stable = StableValue.of();
        final List<StableValue<Integer>> list = StableValue.ofList(LIST_SIZE);
        return Stream.of(
                new Pair<>(stable, stable),
                new Pair<>(list.getFirst(), list.getFirst())
        );
    }

}
