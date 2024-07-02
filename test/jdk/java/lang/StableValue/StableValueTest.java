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
 * @modules java.base/jdk.internal.lang
 * @modules java.base/jdk.internal.lang.stable
 * @compile --enable-preview -source ${jdk.version} StableValueTest.java
 * @run junit/othervm --enable-preview StableValueTest
 */

import jdk.internal.lang.StableValue;
import jdk.internal.lang.stable.StableValueImpl;
import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class StableValueTest {

    @Test
    void unset() {
        StableValue<Integer> stable = StableValue.newInstance();
        assertNull(stable.orElse(null));
        assertThrows(NoSuchElementException.class, stable::orElseThrow);
        assertEquals("StableValue.unset", stable.toString());
        assertTrue(stable.trySet(42));
        assertFalse(stable.trySet(null));
        assertFalse(stable.trySet(42));
        assertFalse(stable.trySet(2));
    }

    @Test
    void setNull() {
        StableValue<Integer> stable = StableValue.newInstance();
        assertTrue(stable.trySet(null));
        assertEquals("StableValue[null]", stable.toString());
        assertNull(stable.orElse(13));
        assertFalse(stable.trySet(null));
        assertFalse(stable.trySet(1));
    }

    @Test
    void setNonNull() {
        StableValue<Integer> stable = StableValue.newInstance();
        assertTrue(stable.trySet(42));
        assertEquals("StableValue[42]", stable.toString());
        assertEquals(42, stable.orElse(null));
        assertFalse(stable.trySet(null));
        assertFalse(stable.trySet(1));
        assertThrows(IllegalStateException.class, () -> stable.setOrThrow(1));
        assertEquals(42, stable.orElseThrow());
    }

    @Test
    void testHashCode() {
        StableValue<Integer> s0 = StableValue.newInstance();
        StableValue<Integer> s1 = StableValue.newInstance();
        assertEquals(s0.hashCode(), s1.hashCode());
        s0.setOrThrow(42);
        s1.setOrThrow(42);
        assertEquals(s0.hashCode(), s1.hashCode());
    }

    @Test
    void testEquals() {
        StableValue<Integer> s0 = StableValue.newInstance();
        StableValue<Integer> s1 = StableValue.newInstance();
        assertEquals(s0, s1);
        s0.setOrThrow(42);
        s1.setOrThrow(42);
        assertEquals(s0, s1);
        StableValue<Integer> other = StableValue.newInstance();
        other.setOrThrow(13);
        assertNotEquals(s0, other);
        assertNotEquals(s0, "a");
    }

    @Test
    void ofList() {
        List<StableValueImpl<Integer>> list = StableValueImpl.ofList(13);
        assertEquals(13, list.size());
        // Check, every StableValue is distinct
        Map<StableValue<Integer>, Boolean> idMap = new IdentityHashMap<>();
        list.forEach(e -> idMap.put(e, true));
        assertEquals(13, idMap.size());
    }

    @Test
    void ofMap() {
        Map<Integer, StableValueImpl<Integer>> map = StableValueImpl.ofMap(Set.of(1, 2, 3));
        assertEquals(3, map.size());
        // Check, every StableValue is distinct
        Map<StableValue<Integer>, Boolean> idMap = new IdentityHashMap<>();
        map.forEach((k, v) -> idMap.put(v, true));
        assertEquals(3, idMap.size());
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
        StableValue<Integer> stable = StableValue.newInstance();
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
