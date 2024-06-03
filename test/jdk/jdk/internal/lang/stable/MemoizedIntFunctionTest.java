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
 * @compile --enable-preview -source ${jdk.version} MemoizedIntFunctionTest.java
 * @run junit/othervm --enable-preview MemoizedIntFunctionTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class MemoizedIntFunctionTest {

    private static final int SIZE = 3;
    private static final int INDEX = 1;
    private static final IntFunction<Integer> FUNCTION = i -> i;

    @Test
    void basic() {
        StableTestUtil.CountingIntFunction<Integer> original = new StableTestUtil.CountingIntFunction<>(FUNCTION);
        IntFunction<Integer> function = StableValue.memoizedIntFunction(SIZE, original);
        assertEquals(INDEX, function.apply(INDEX));
        assertEquals(1, original.cnt());
        assertEquals(INDEX, function.apply(INDEX));
        assertEquals(1, original.cnt());
        assertThrows(IndexOutOfBoundsException.class, () -> function.apply(SIZE));
    }

    @Test
    void empty() {
        StableTestUtil.CountingIntFunction<Integer> original = new StableTestUtil.CountingIntFunction<>(FUNCTION);
        IntFunction<Integer> function = StableValue.memoizedIntFunction(0, original);
        assertThrows(IndexOutOfBoundsException.class, () -> function.apply(INDEX));
    }

/*    @Test
    void toStringTest() {
        IntFunction<Integer> function = StableValue.memoizedIntFunction(SIZE, FUNCTION);
        String expectedEmpty = "MemoizedIntFunction[original=" + FUNCTION + ", delegate=StableArray[.unset, .unset, .unset]";
        assertTrue(function.toString().startsWith(expectedEmpty), function.toString());
        function.apply(INDEX);
        assertTrue(function.toString().contains("delegate=StableArray[.unset, [" + INDEX + "], .unset]"), function.toString());
    }*/

    @Test
    void shakedownSingleThread() {
        for (int size = 0; size < 128; size++) {
            IntFunction<Integer> memo = StableValue.memoizedIntFunction(size, i -> i);
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
            IntFunction<Integer> memo = StableValue.memoizedIntFunction(size, i -> i);
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
            threads.forEach(MemoizedIntFunctionTest::join);
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