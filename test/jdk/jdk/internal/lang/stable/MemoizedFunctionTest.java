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
 * @compile --enable-preview -source ${jdk.version} MemoizedFunctionTest.java
 * @run junit/othervm --enable-preview MemoizedFunctionTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class MemoizedFunctionTest {

    private static final int SIZE = 3;
    private static final int INDEX = 1;
    private static final Set<Integer> INPUTS = IntStream.range(0, SIZE).boxed().collect(Collectors.toSet());
    private static final Function<Integer, Integer> FUNCTION = Function.identity();

    @Test
    void basic() {
        StableTestUtil.CountingFunction<Integer, Integer> original = new StableTestUtil.CountingFunction<>(FUNCTION);
        Function<Integer, Integer> function = StableValue.memoizedFunction(INPUTS, original);
        assertEquals(INDEX, function.apply(INDEX));
        assertEquals(1, original.cnt());
        assertEquals(INDEX, function.apply(INDEX));
        assertEquals(1, original.cnt());
        assertThrows(IllegalArgumentException.class, () -> function.apply(SIZE));
    }

    @Test
    void empty() {
        StableTestUtil.CountingFunction<Integer, Integer> original = new StableTestUtil.CountingFunction<>(FUNCTION);
        Function<Integer, Integer> function = StableValue.memoizedFunction(Set.of(), original);
        assertThrows(IllegalArgumentException.class, () -> function.apply(INDEX));
    }

/*    @Test
    void toStringTest() {
        Function<Integer, Integer> function = StableValue.memoizedFunction(INPUTS, FUNCTION);
        String expectedEmpty = "MemoizedFunction[original=" + FUNCTION;
        assertTrue(function.toString().startsWith(expectedEmpty), function.toString());
        function.apply(INDEX);
        assertTrue(function.toString().contains("[" + INDEX + "]"), function.toString());
    }*/

    @Test
    void shakedownSingleThread() {
        for (int size = 0; size < 128; size++) {
            Function<Integer, Integer> memo = StableValue.memoizedFunction(IntStream.range(0, size).boxed().collect(Collectors.toSet()), Function.identity());
            for (int i = 0; i < size; i++) {
                assertEquals(i, memo.apply(i));
            }
        }
    }

    @Test
    void shakedownMultiThread() {
        int threadCount = 8;
        var startGate = new CountDownLatch(1);
        for (int size = 0; size < 128; size++) {
            Function<Integer, Integer> memo = StableValue.memoizedFunction(IntStream.range(0, size).boxed().collect(Collectors.toSet()), Function.identity());
            final int fSize = size;
            List<Thread> threads = Stream.generate(() -> new Thread(() -> {
                        while (startGate.getCount() != 0) {
                            Thread.onSpinWait();
                        }
                        for (int i = 0; i < fSize; i++) {
                            int value = memo.apply(i);
                            assertEquals(i, value);
                        }
                    }))
                    .limit(threadCount)
                    .toList();
            threads.forEach(Thread::start);
            // Give some time for the threads to start
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            // Release them as close as possible
            startGate.countDown();
            threads.forEach(MemoizedFunctionTest::join);
        }
    }

    static void join(Thread t) {
        try {
            t.join();
        } catch (InterruptedException e) {
            fail(e);
        }
    }

}