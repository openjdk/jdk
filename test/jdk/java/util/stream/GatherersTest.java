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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.*;
import java.util.stream.Gatherer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * @test
 * @summary Testing the built-in Gatherer implementations and their contracts
 * @enablePreview
 * @run junit GatherersTest
 */

public class GatherersTest {

    record Config(int streamSize, boolean parallel) {
        Stream<Integer> stream() {
            return wrapStream(Stream.iterate(1, i -> i + 1).limit(streamSize));
        }

        <R> Stream<R> wrapStream(Stream<R> stream) {
            stream = parallel ? stream.parallel() : stream.sequential();
            return stream;
        }
    }

    final static Stream<Config> configurations() {
        return Stream.of(0,1,10,33,99,9999)
                     .flatMap(size ->
                             Stream.of(false, true)
                                   .map(parallel ->
                                               new Config(size, parallel))
                     );
    }

    final class TestException extends RuntimeException {
        TestException(String message) {
            super(message);
        }
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testFixedWindowAPIandContract(Config config) {
        // Groups must be greater than 0
        assertThrows(IllegalArgumentException.class, () -> Gatherers.windowFixed(0));

        final var streamSize = config.streamSize();

        // We're already covering less-than-one scenarios above
        if (streamSize > 0) {
            //Test creating a window of the same size as the stream
            {
                final var result = config.stream()
                        .gather(Gatherers.windowFixed(streamSize))
                        .toList();
                assertEquals(1, result.size());
                assertEquals(config.stream().toList(), result.get(0));
            }

            //Test nulls as elements
            {
                assertEquals(
                      config.stream()
                            .map(n -> Arrays.asList(null, null))
                            .toList(),
                      config.stream()
                            .flatMap(n -> Stream.of(null, null))
                            .gather(Gatherers.windowFixed(2))
                            .toList());
            }

            // Test unmodifiability of windows
            {
                var window = config.stream()
                                   .gather(Gatherers.windowFixed(1))
                                   .findFirst()
                                   .get();
                assertThrows(UnsupportedOperationException.class,
                        () -> window.add(2));
            }
        }


        // Tests that the layout of the returned data is as expected
        for (var windowSize : List.of(1, 2, 3, 10)) {
            final var expectLastWindowSize = streamSize % windowSize == 0 ? windowSize : streamSize % windowSize;
            final var expectedSize = (streamSize / windowSize) + ((streamSize % windowSize == 0) ? 0 : 1);

            final var expected = config.stream().toList().iterator();

            final var result = config.stream()
                                     .gather(Gatherers.windowFixed(windowSize))
                                     .toList();

            int currentWindow = 0;
            for (var window : result) {
                ++currentWindow;
                assertEquals(currentWindow < expectedSize ? windowSize : expectLastWindowSize, window.size());
                for (var element : window)
                    assertEquals(expected.next(), element);
            }

            assertEquals(expectedSize, currentWindow);
        }
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testSlidingAPIandContract(Config config) {
        // Groups must be greater than 0
        assertThrows(IllegalArgumentException.class, () -> Gatherers.windowSliding(0));

        final var streamSize = config.streamSize();

        // We're already covering less-than-one scenarios above
        if (streamSize > 0) {
            //Test greating a window larger than the size of the stream
            {
                final var result = config.stream()
                                         .gather(Gatherers.windowSliding(streamSize + 1))
                                         .toList();
                assertEquals(1, result.size());
                assertEquals(config.stream().toList(), result.get(0));
            }

            //Test nulls as elements
            {
                assertEquals(
                        List.of(
                                Arrays.asList(null, null),
                                Arrays.asList(null, null)
                        ),
                        config.wrapStream(Stream.of(null, null, null))
                            .gather(Gatherers.windowSliding(2))
                            .toList());
            }

            // Test unmodifiability of windows
            {
                var window = config.stream()
                        .gather(Gatherers.windowSliding(1))
                        .findFirst()
                        .get();
                assertThrows(UnsupportedOperationException.class,
                        () -> window.add(2));
            }
        }

        // Tests that the layout of the returned data is as expected
        for (var windowSize : List.of(1, 2, 3, 10)) {
            final var expectLastWindowSize = streamSize < windowSize ? streamSize : windowSize;
            final var expectedNumberOfWindows = streamSize == 0 ? 0 : Math.max(1, 1 + streamSize - windowSize);

            int expectedElement = 0;
            int currentWindow = 0;

            final var result = config.stream()
                                     .gather(Gatherers.windowSliding(windowSize))
                                     .toList();

            for (var window : result) {
                ++currentWindow;
                assertEquals(currentWindow < expectedNumberOfWindows ? windowSize : expectLastWindowSize, window.size());
                for (var element : window) {
                    assertEquals(++expectedElement, element.intValue());
                }
                // rewind for the sliding motion
                expectedElement -= (window.size() - 1);
            }

            assertEquals(expectedNumberOfWindows, currentWindow);
        }
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testFoldAPIandContract(Config config) {
        // Verify prereqs
        assertThrows(NullPointerException.class, () -> Gatherers.<String,String>fold(null, (state, next) -> state));
        assertThrows(NullPointerException.class, () -> Gatherers.<String,String>fold(() -> "", null));

        final var expectedResult = List.of(
                                       config.stream()
                                             .sequential()
                                             .reduce("", (acc, next) -> acc + next, (l,r) -> { throw new IllegalStateException(); })
                                   );

        final var result = config.stream()
                                 .gather(Gatherers.fold(() -> "", (acc, next) -> acc + next))
                                 .toList();

        assertEquals(expectedResult, result);
    }

    @ParameterizedTest
    @MethodSource("configurations")
    public void testMapConcurrentAPIandContract(Config config) throws InterruptedException {
        // Verify prereqs
        assertThrows(IllegalArgumentException.class, () -> Gatherers.<String, String>mapConcurrent(0, s -> s));
        assertThrows(NullPointerException.class, () -> Gatherers.<String, String>mapConcurrent(2, null));

        // Test exception during processing
        {
            final var stream = config.parallel() ? Stream.of(1).parallel() : Stream.of(1);

            assertThrows(RuntimeException.class,
                    () -> stream.gather(Gatherers.<Integer, Integer>mapConcurrent(2, x -> {
                        throw new RuntimeException();
                    })).toList());
        }

        // Test cancellation after exception during processing
        if (config.streamSize > 2) { // We need streams of a minimum size to test this
            final var firstLatch = new CountDownLatch(1);
            final var secondLatch = new CountDownLatch(1);
            final var cancellationLatch = new CountDownLatch(config.streamSize - 2); // all but two will get cancelled

            try {
                config.stream()
                      .gather(
                            Gatherers.mapConcurrent(config.streamSize(), i -> {
                                switch (i) {
                                    case 1 -> {
                                        try {
                                            firstLatch.await(); // the first waits for the last element to start
                                        } catch (InterruptedException ie) {
                                            throw new IllegalStateException(ie);
                                        }
                                        throw new TestException("expected");
                                    }

                                    case Integer n when n == config.streamSize - 1 -> { // last element
                                        firstLatch.countDown(); // ensure that the first element can now proceed
                                    }

                                    default -> {
                                        try {
                                            secondLatch.await(); // These should all get interrupted
                                        } catch (InterruptedException ie) {
                                            cancellationLatch.countDown(); // used to ensure that they all were interrupted
                                        }
                                    }
                                }

                                return i;
                            })
                      )
                      .toList();
                fail("This should not be reached");
            } catch (Throwable e) {
                while (e.getCause() != null)
                    e = e.getCause();
                assertSame(TestException.class, e.getClass());
                assertEquals("expected", e.getMessage());
                cancellationLatch.await();
                return;
            }

            fail("This should not be reached");
        }

        // Test cancellation during short-circuiting
        if (config.streamSize > 2) {
            final var firstLatch = new CountDownLatch(1);
            final var secondLatch = new CountDownLatch(1);
            final var cancellationLatch = new CountDownLatch(config.streamSize - 2); // all but two will get cancelled

            final var result =
                config.stream()
                      .gather(
                            Gatherers.mapConcurrent(config.streamSize(), i -> {
                                switch (i) {
                                    case 1 -> {
                                        try {
                                            firstLatch.await(); // the first waits for the last element to start
                                        } catch (InterruptedException ie) {
                                            throw new IllegalStateException(ie);
                                        }
                                    }

                                    case Integer n when n == config.streamSize - 1 -> { // last element
                                        firstLatch.countDown(); // ensure that the first element can now proceed
                                    }

                                    default -> {
                                        try {
                                            secondLatch.await(); // These should all get interrupted
                                        } catch (InterruptedException ie) {
                                            cancellationLatch.countDown(); // used to ensure that they all were interrupted
                                        }
                                    }
                                }

                                return i;
                            })
                      )
                      .limit(2)
                      .toList();
            cancellationLatch.await(); // If this hangs, then we didn't cancel and interrupt the tasks
            assertEquals(List.of(1,2), result);
        }

        for (var concurrency : List.of(1, 2, 3, 10, 1000)) {
            // Test normal operation
            {
                final var expectedResult = config.stream()
                                                 .map(x -> x * x)
                                                 .toList();

                final var result = config.stream()
                                         .gather(Gatherers.mapConcurrent(concurrency, x -> x * x))
                                         .toList();

                assertEquals(expectedResult, result);
            }

            // Test short-circuiting
            {
                final var limitTo = Math.max(config.streamSize() / 2, 1);

                final var expectedResult = config.stream()
                                                 .map(x -> x * x)
                                                 .limit(limitTo)
                                                 .toList();

                final var result = config.stream()
                                         .gather(Gatherers.mapConcurrent(concurrency, x -> x * x))
                                         .limit(limitTo)
                                         .toList();

                assertEquals(expectedResult, result);
            }
        }
    }
}
