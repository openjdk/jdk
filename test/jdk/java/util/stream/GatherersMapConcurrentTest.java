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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.stream.Gatherer;
import java.util.stream.Gatherers;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * @test
 * @summary Tests the API and contract of Gatherers.mapConcurrent
 * @enablePreview
 * @run junit GatherersMapConcurrentTest
 */

public class GatherersMapConcurrentTest {

    record Config(int streamSize, boolean parallel) {
        Stream<Integer> stream() {
            var stream = Stream.iterate(1, i -> i + 1).limit(streamSize);
            stream = parallel ? stream.parallel() : stream.sequential();
            return stream;
        }
    }

    record ConcurrencyConfig(Config config, int concurrencyLevel) {}

    static final Stream<Integer> sizes(){
        return Stream.of(0,1,10,33,99,9999);
    }

    static final Stream<Integer> concurrencyLevels() { return Stream.of(1, 2, 3, 10,
            1000); }

    static final Stream<Config> sequentialAndParallel(int size) {
        return Stream.of(false, true)
                .map(parallel ->
                        new Config(size, parallel));
    }

    static final Stream<Config> configurations() {
        return sizes().flatMap(i -> sequentialAndParallel(i));
    }

    static final Stream<ConcurrencyConfig> concurrencyConfigurations() {
        return configurations().flatMap( c -> concurrencyLevels().map( l -> new ConcurrencyConfig(c, l)) );
    }

    static final Stream<Config> small_atleast3_configurations() {
        return sizes().filter(i -> i > 2 && i < 100).flatMap(i -> sequentialAndParallel(i));
    }

    static final class TestException extends RuntimeException {
        TestException(String message) {
            super(message);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { Integer.MIN_VALUE, -999, -1, 0})
    public void throwsIAEWhenConcurrencyLevelIsLowerThanOne(int level) {
        assertThrows(IllegalArgumentException.class,
                () -> Gatherers.<String, String>mapConcurrent(level, s -> s));
    }

    @Test
    public void throwsNPEWhenMapperFunctionIsNull() {
        assertThrows(NullPointerException.class, () -> Gatherers.<String, String>mapConcurrent(2, null));
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false } )
    public void rethrowsRuntimeExceptionsUnwrapped(boolean parallel) {
        final var stream = parallel ? Stream.of(1).parallel() : Stream.of(1);

        var exception =
            assertThrows(
                RuntimeException.class,
                     () -> stream.gather(
                             Gatherers.<Integer, Integer>mapConcurrent(2, x -> {
                                 throw new RuntimeException("expected");
                             })
                           ).toList()
            );
        assertEquals("expected", exception.getMessage());
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false } )
    public void rethrowsSubtypesOfRuntimeExceptionsUnwrapped(boolean parallel) {
        final var stream = parallel ? Stream.of(1).parallel() : Stream.of(1);

        var exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> stream.gather(
                                Gatherers.<Integer, Integer>mapConcurrent(2, x -> {
                                    throw new IllegalStateException("expected");
                                })
                        ).toList()
                );
        assertEquals("expected", exception.getMessage());
        assertNull(exception.getCause());
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false } )
    public void rethrowsErrorsWrappedAsRuntimeExceptions(boolean parallel) {
        final var stream = parallel ? Stream.of(1).parallel() : Stream.of(1);

        var exception =
                assertThrows(
                        RuntimeException.class,
                        () -> stream.gather(
                                Gatherers.<Integer, Integer>mapConcurrent(2, x -> {
                                    throw new Error("expected");
                                })
                        ).toList()
                );
        assertEquals("expected", exception.getCause().getMessage());
        assertEquals(Error.class, exception.getCause().getClass());
    }

    @ParameterizedTest
    @MethodSource("small_atleast3_configurations")
    public void cancelsStartedTasksIfExceptionDuringProcessingIsThrown(Config config) {
        final var streamSize = config.streamSize();

        assertTrue(streamSize > 2, "This test case won't work with tiny streams!");

        final var tasksToCancel = streamSize - 2;
        final var throwerReady = new CountDownLatch(1);
        final var initiateThrow = new CountDownLatch(1);
        final var tasksCancelled = new CountDownLatch(tasksToCancel);

        final var tasksWaiting = new Semaphore(0);

        try {
            config.stream()
                    .gather(
                            Gatherers.mapConcurrent(streamSize, i -> {
                                switch (i) {
                                    case 1 -> {
                                        throwerReady.countDown();
                                        try { initiateThrow.await(); }
                                        catch (InterruptedException ie) {
                                            fail("Unexpected");
                                        }
                                        throw new TestException("expected");
                                    }

                                    case Integer n when n == streamSize -> {
                                        try { throwerReady.await(); }
                                        catch (InterruptedException ie) {
                                            fail("Unexpected");
                                        }
                                        while(tasksWaiting.getQueueLength() < tasksToCancel) {
                                            try {
                                                Thread.sleep(10);
                                            } catch (InterruptedException ie) {
                                                // Ignore
                                            }
                                        }
                                        initiateThrow.countDown();
                                    }

                                    default -> {
                                        try {
                                            tasksWaiting.acquire();
                                        } catch (InterruptedException ie) {
                                            tasksCancelled.countDown(); // used to ensure that they all were interrupted
                                        }
                                    }
                                }

                                return i;
                            })
                    )
                    .toList();
            fail("This should not be reached");
        } catch (TestException te) {
            assertEquals("expected", te.getMessage());
            try { tasksCancelled.await(); }
            catch (InterruptedException ie) {
                fail("Unexpected");
            }
            return;
        }

        fail("This should not be reached");
    }

    @ParameterizedTest
    @MethodSource("small_atleast3_configurations")
    public void cancelsStartedTasksIfShortCircuited(Config config) {
        final var streamSize = config.streamSize();

        assertTrue(streamSize > 2, "This test case won't work with tiny streams!");

        final var tasksToCancel = streamSize - 2;
        final var firstReady = new CountDownLatch(1);
        final var lastDone = new CountDownLatch(1);
        final var tasksCancelled = new CountDownLatch(tasksToCancel);

        final var tasksWaiting = new Semaphore(0);

        final var result =
                config.stream().gather(
                    Gatherers.mapConcurrent(streamSize, i -> {
                        switch (i) {
                            case 1 -> {
                                firstReady.countDown();
                                try { lastDone.await(); }
                                catch (InterruptedException ie) {
                                    fail("Unexpected!");
                                }
                            }

                            case Integer n when n == streamSize -> {
                                try { firstReady.await(); }
                                catch (InterruptedException ie) {
                                    fail("Unexpected!");
                                }
                                while(tasksWaiting.getQueueLength() < tasksToCancel) {
                                    try {
                                        Thread.sleep(10);
                                    } catch (InterruptedException ie) {
                                        // Ignore
                                    }
                                }
                                lastDone.countDown();
                            }

                            default -> {
                                try {
                                    tasksWaiting.acquire();
                                } catch (InterruptedException ie) {
                                    tasksCancelled.countDown(); // used to ensure that they all were interrupted
                                }
                            }
                        }

                        return i;
                    })
            )
            .gather(Gatherer.of((unused, state, downstream) -> downstream.push(state) && false)) // emulate limit(1)
            .toList();
        assertEquals(List.of(1), result);
        try {
            tasksCancelled.await();
        } catch (InterruptedException ie) {
            fail("Unexpected");
        }
    }

    @ParameterizedTest
    @MethodSource("concurrencyConfigurations")
    public void behavesAsExpected(ConcurrencyConfig cc) {
        final var expectedResult = cc.config().stream()
                .map(x -> x * x)
                .toList();

        final var result = cc.config().stream()
                .gather(Gatherers.mapConcurrent(cc.concurrencyLevel(), x -> x * x))
                .toList();

        assertEquals(expectedResult, result);
    }

    @ParameterizedTest
    @MethodSource("concurrencyConfigurations")
    public void behavesAsExpectedWhenShortCircuited(ConcurrencyConfig cc) {
        final var limitTo = Math.max(cc.config().streamSize() / 2, 1);

        final var expectedResult = cc.config().stream()
                .map(x -> x * x)
                .limit(limitTo)
                .toList();

        final var result = cc.config().stream()
                .gather(Gatherers.mapConcurrent(cc.concurrencyLevel(), x -> x * x))
                .limit(limitTo)
                .toList();

        assertEquals(expectedResult, result);
    }
}
