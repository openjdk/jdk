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
 * @summary Basic tests for StableValue implementations
 * @compile --enable-preview -source ${jdk.version} StableValueTest.java
 * @run junit/othervm --enable-preview StableValueTest
 */

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiPredicate;
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
        assertEquals(VALUE, stable.computeIfUnset(() -> VALUE2));
        assertFalse(stable.trySet(VALUE2));
        assertThrows(IllegalStateException.class, () -> stable.setOrThrow(VALUE2));
    }

    void trySet(Integer initial) {
        StableValue<Integer> stable = StableValue.unset();
        assertTrue(stable.trySet(initial));
        assertFalse(stable.trySet(null));
        assertFalse(stable.trySet(VALUE));
        assertFalse(stable.trySet(VALUE2));
        assertEquals(initial, stable.orElseThrow());
    }

    @Test
    void orElse() {
        StableValue<Integer> stable = StableValue.unset();
        assertEquals(VALUE, stable.orElse(VALUE));
        stable.trySet(VALUE);
        assertEquals(VALUE, stable.orElse(VALUE2));
    }

    @Test
    void orElseThrow() {
        StableValue<Integer> stable = StableValue.unset();
        var e = assertThrows(NoSuchElementException.class, stable::orElseThrow);
        assertEquals("No underlying data set", e.getMessage());
        stable.trySet(VALUE);
        assertEquals(VALUE, stable.orElseThrow());
    }

    @Test
    void isSet() {
        isSet(VALUE);
        isSet(null);
   }

    void isSet(Integer initial) {
        StableValue<Integer> stable = StableValue.unset();
        assertFalse(stable.isSet());
        stable.trySet(initial);
        assertTrue(stable.isSet());
    }

   @Test
   void testComputeIfUnsetSupplier() {
       StableTestUtil.CountingSupplier<Integer> cs = new StableTestUtil.CountingSupplier<>(() -> VALUE);
       StableValue<Integer> stable = StableValue.unset();
       assertEquals(VALUE, stable.computeIfUnset(cs));
       assertEquals(1, cs.cnt());
       assertEquals(VALUE, stable.computeIfUnset(cs));
       assertEquals(1, cs.cnt());
   }

    @Test
    void testHashCode() {
        StableValue<Integer> stableValue = StableValue.unset();
        // Should be Object::hashCode
        assertEquals(System.identityHashCode(stableValue), stableValue.hashCode());
    }

    @Test
    void testEquals() {
        StableValue<Integer> s0 = StableValue.unset();
        StableValue<Integer> s1 = StableValue.unset();
        assertNotEquals(s0, s1); // Identity based
        s0.setOrThrow(42);
        s1.setOrThrow(42);
        assertNotEquals(s0, s1);
        assertNotEquals(s0, "a");
        StableValue<Integer> null0 = StableValue.unset();
        StableValue<Integer> null1 = StableValue.unset();
        null0.setOrThrow(null);
        null1.setOrThrow(null);
        assertNotEquals(null0, null1);
    }

    @Test
    void toStringUnset() {
        StableValue<Integer> stable = StableValue.unset();
        assertEquals("StableValue.unset", stable.toString());
    }

    @Test
    void toStringNull() {
        StableValue<Integer> stable = StableValue.unset();
        assertTrue(stable.trySet(null));
        assertEquals("StableValue[null]", stable.toString());
    }

    @Test
    void toStringNonNull() {
        StableValue<Integer> stable = StableValue.unset();
        assertTrue(stable.trySet(VALUE));
        assertEquals("StableValue[" + VALUE + "]", stable.toString());
    }

    @Test
    void toStringCircular() {
        StableValue<StableValue<?>> stable = StableValue.unset();
        stable.trySet(stable);
        String toString = stable.toString();
        assertEquals("(this StableValue)", toString);
        assertDoesNotThrow(stable::hashCode);
        assertDoesNotThrow((() -> stable.equals(stable)));
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
        CountDownLatch starter = new CountDownLatch(1);
        StableValue<Integer> stable = StableValue.unset();
        BitSet winner = new BitSet(noThreads);
        List<Thread> threads = IntStream.range(0, noThreads).mapToObj(i -> new Thread(() -> {
                    try {
                        // Ready, set ...
                        starter.await();
                        // Here we go!
                        winner.set(i, winnerPredicate.test(stable, i));
                    } catch (Throwable t) {
                        fail(t);
                    }
                }))
                .toList();
        threads.forEach(Thread::start);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        // Start the race
        starter.countDown();
        threads.forEach(StableValueTest::join);
        // There can only be one winner
        assertEquals(1, winner.cardinality());
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            fail(e);
        }
    }

}
