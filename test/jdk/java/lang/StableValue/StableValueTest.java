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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StableValueTest {

    private static final int VALUE = 42;
    private static final int VALUE2 = 13;

    @Test
    void trySet() {
        trySet(VALUE);
        trySet(null);
    }

    @Test
    void preSet() {
        StableValue<Integer> stable = StableValue.of(VALUE);
        assertTrue(stable.isSet());
        assertEquals(VALUE, stable.orElseThrow());
        assertEquals(VALUE, stable.orElse(VALUE2));
        assertEquals(VALUE, stable.orElseSet(() -> VALUE2));
        assertFalse(stable.trySet(VALUE2));
        var e = assertThrows(IllegalStateException.class, () -> stable.setOrThrow(VALUE2));
        assertEquals(
                "The contents is already set",
                e.getMessage());
    }

    void trySet(Integer initial) {
        StableValue<Integer> stable = StableValue.of();
        assertTrue(stable.trySet(initial));
        assertFalse(stable.trySet(null));
        assertFalse(stable.trySet(VALUE));
        assertFalse(stable.trySet(VALUE2));
        assertEquals(initial, stable.orElseThrow());
    }

    @Test
    void setOrThrowValue() {
        StableValue<Integer> stable = StableValue.of();
        stable.setOrThrow(VALUE);
        var e = assertThrows(IllegalStateException.class, () -> stable.setOrThrow(VALUE2));
        assertEquals("The contents is already set", e.getMessage());
    }

    @Test
    void setOrThrowNull() {
        StableValue<Integer> stable = StableValue.of();
        stable.setOrThrow(null);
        var e = assertThrows(IllegalStateException.class, () -> stable.setOrThrow(null));
        assertEquals("The contents is already set", e.getMessage());
    }

    @Test
    void orElse() {
        StableValue<Integer> stable = StableValue.of();
        assertEquals(VALUE, stable.orElse(VALUE));
        assertNull(stable.orElse(null));
        stable.trySet(VALUE);
        assertEquals(VALUE, stable.orElse(VALUE2));
    }

    @Test
    void orElseThrow() {
        StableValue<Integer> stable = StableValue.of();
        var e = assertThrows(NoSuchElementException.class, stable::orElseThrow);
        assertEquals("No contents set", e.getMessage());
        stable.trySet(VALUE);
        assertEquals(VALUE, stable.orElseThrow());
    }

    @Test
    void isSet() {
        isSet(VALUE);
        isSet(null);
   }

    void isSet(Integer initial) {
        StableValue<Integer> stable = StableValue.of();
        assertFalse(stable.isSet());
        stable.trySet(initial);
        assertTrue(stable.isSet());
    }

   @Test
   void testOrElseSetSupplier() {
       StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> VALUE);
       StableValue<Integer> stable = StableValue.of();
       assertThrows(NullPointerException.class, () -> stable.orElseSet(null));
       assertEquals(VALUE, stable.orElseSet(cs));
       assertEquals(1, cs.cnt());
       assertEquals(VALUE, stable.orElseSet(cs));
       assertEquals(1, cs.cnt());
   }

    @Test
    void testHashCode() {
        StableValue<Integer> stableValue = StableValue.of();
        // Should be Object::hashCode
        assertEquals(System.identityHashCode(stableValue), stableValue.hashCode());
    }

    @Test
    void testEquals() {
        StableValue<Integer> s0 = StableValue.of();
        assertNotEquals(null, s0);
        StableValue<Integer> s1 = StableValue.of();
        assertNotEquals(s0, s1); // Identity based
        s0.setOrThrow(42);
        s1.setOrThrow(42);
        assertNotEquals(s0, s1);
        assertNotEquals("a", s0);
        StableValue<Integer> null0 = StableValue.of();
        StableValue<Integer> null1 = StableValue.of();
        null0.setOrThrow(null);
        null1.setOrThrow(null);
        assertNotEquals(null0, null1);
    }

    @Test
    void toStringUnset() {
        StableValue<Integer> stable = StableValue.of();
        assertEquals(".unset", stable.toString());
    }

    @Test
    void toStringNull() {
        StableValue<Integer> stable = StableValue.of();
        assertTrue(stable.trySet(null));
        assertEquals("null", stable.toString());
    }

    @Test
    void toStringNonNull() {
        StableValue<Integer> stable = StableValue.of();
        assertTrue(stable.trySet(VALUE));
        assertEquals(Objects.toString(VALUE), stable.toString());
    }

    @Test
    void toStringCircular() {
        StableValue<StableValue<?>> stable = StableValue.of();
        stable.trySet(stable);
        String toString = assertDoesNotThrow(stable::toString);
        assertEquals("(this StableValue)", toString);
        assertDoesNotThrow(stable::hashCode);
        assertDoesNotThrow((() -> stable.equals(stable)));
    }

    @Test
    void recursiveCall() {
        StableValue<Integer> stable = StableValue.of();
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

    @Test
    void intFunctionExample() {
        final class SqrtUtil {

            private SqrtUtil() {}

            private static final int CACHED_SIZE = 10;

            private static final IntFunction<Double> SQRT =
                    // @link substring="intFunction" target="#intFunction(int,IntFunction)" :
                    StableValue.intFunction(CACHED_SIZE, StrictMath::sqrt);

            public static double sqrt(int a) {
                return SQRT.apply(a);
            }
        }

        double sqrt9 = SqrtUtil.sqrt(9);   // May eventually constant fold to 3.0 at runtime

        assertEquals(3, sqrt9);
        assertThrows(IllegalArgumentException.class, () -> SqrtUtil.sqrt(16));
    }

    @Test
    void intFunctionExample2() {
        final class PowerOf2Util {

            private PowerOf2Util() {}

            private static final int SIZE = 6;
            private static final IntFunction<Integer> ORIGINAL_POWER_OF_TWO =
                    v -> 1 << v;

            private static final IntFunction<Integer> POWER_OF_TWO =
                    // @link substring="intFunction" target="#intFunction(int,IntFunction)" :
                    StableValue.intFunction(SIZE, ORIGINAL_POWER_OF_TWO);

            public static int powerOfTwo(int a) {
                return POWER_OF_TWO.apply(a);
            }
        }

        int pwr4 = PowerOf2Util.powerOfTwo(4);   // May eventually constant fold to 16 at runtime

        assertEquals(16, pwr4);
        assertEquals(1, PowerOf2Util.powerOfTwo(0));
        assertEquals(8, PowerOf2Util.powerOfTwo(3));
        assertEquals(32, PowerOf2Util.powerOfTwo(5));
        assertThrows(IllegalArgumentException.class, () -> PowerOf2Util.powerOfTwo(10));
    }

    @Test
    void functionExample() {

        class Log2Util {

            private Log2Util() {}

            private static final Set<Integer> CACHED_KEYS =
                    Set.of(1, 2, 4, 8, 16, 32);
            private static final UnaryOperator<Integer> LOG2_ORIGINAL =
                    i -> 31 - Integer.numberOfLeadingZeros(i);

            private static final Function<Integer, Integer> LOG2_CACHED =
                    // @link substring="function" target="#function(Set,Function)" :
                    StableValue.function(CACHED_KEYS, LOG2_ORIGINAL);

            public static double log2(int a) {
                if (CACHED_KEYS.contains(a)) {
                    return LOG2_CACHED.apply(a);
                } else {
                    return LOG2_ORIGINAL.apply(a);
                }
            }

        }

        double log16 = Log2Util.log2(16); // May eventually constant fold to 4.0 at runtime
        double log256 = Log2Util.log2(256); // Will not constant fold

        assertEquals(4, log16);
        assertEquals(8, log256);
    }

    @Test
    void functionExample2() {

        class Log2Util {

            private Log2Util() {}

            private static final Set<Integer> KEYS =
                    Set.of(1, 2, 4, 8);
            private static final UnaryOperator<Integer> LOG2_ORIGINAL =
                    i -> 31 - Integer.numberOfLeadingZeros(i);

            private static final Function<Integer, Integer> LOG2 =
                    // @link substring="function" target="#function(Set,Function)" :
                    StableValue.function(KEYS, LOG2_ORIGINAL);

            public static double log2(int a) {
                return LOG2.apply(a);
            }

        }

        double log16 = Log2Util.log2(8); // May eventually constant fold to 3.0 at runtime

        assertEquals(3, log16);
        assertThrows(IllegalArgumentException.class, () -> Log2Util.log2(3));
    }

    private static final BiPredicate<StableValue<Integer>, Integer> TRY_SET = StableValue::trySet;
    private static final BiPredicate<StableValue<Integer>, Integer> SET_OR_THROW = (s, i) -> {
        try {
            s.setOrThrow(i);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    };

    @Test
    void raceTrySet() {
        race(TRY_SET);
    }

    @Test
    void raceSetOrThrow() {
        race(SET_OR_THROW);
    }

    @Test
    void raceMixed() {
        race((s, i) -> switch (i % 2) {
            case 0 -> TRY_SET.test(s, i);
            case 1 -> SET_OR_THROW.test(s, i);
            default -> fail("should not reach here");
        });
    }

    void race(BiPredicate<StableValue<Integer>, Integer> winnerPredicate) {
        int noThreads = 10;
        CountDownLatch starter = new CountDownLatch(noThreads);
        StableValue<Integer> stable = StableValue.of();
        Map<Integer, Boolean> winners = new ConcurrentHashMap<>();
        List<Thread> threads = IntStream.range(0, noThreads).mapToObj(i -> new Thread(() -> {
                    try {
                        // Ready ...
                        starter.countDown();
                        // ... set ...
                        starter.await();
                        // Here we go!
                        winners.put(i, winnerPredicate.test(stable, i));
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

}
