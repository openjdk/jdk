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

/*
 * @test
 * @summary Test the duration of selection operations performed by virtual threads
 *     when the expected duration of the select op is known
 * @library /test/lib
 * @run junit/othervm/timeout=600 ${test.main.class}
 */

import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.stream.Stream;

import jdk.test.lib.thread.VThreadRunner;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class SelectDuration {

    // used for scheduling thread tasks
    private static ScheduledExecutorService scheduler;

    @BeforeAll
    static void setup() {
        ThreadFactory factory = Executors.defaultThreadFactory();
        scheduler = Executors.newSingleThreadScheduledExecutor(factory);
    }

    @AfterAll
    static void finish() {
        scheduler.shutdown();
    }

    /**
     * Durations to test. Include at least one >10s to test the Windows implementation.
     */
    static Stream<Integer> durations() {
        if (ThreadLocalRandom.current().nextBoolean()) {
            return Stream.of(1000, 10_500);
        } else {
            return Stream.of(1000, 20_500);
        }
    }

    /**
     * Test select timeout.
     */
    @ParameterizedTest
    @MethodSource("durations")
    void testTimeout(long timeout) throws Exception {
        VThreadRunner.run(() -> {
            try (var sel = Selector.open()) {
                long start = millisTime();
                int n = sel.select(timeout);
                assertDuration(timeout, millisTime() - start);
                assertEquals(0, n);
            }
        });
    }

    /**
     * Test selecting the key for a channel that is ready for I/O after a delay.
     */
    @ParameterizedTest
    @MethodSource("durations")
    void testSelected(long delay) throws Exception {
        VThreadRunner.run(() -> {
            Pipe p = Pipe.open();
            Pipe.SinkChannel sink = p.sink();
            Pipe.SourceChannel source = p.source();
            try (var sel = Selector.open()) {
                source.configureBlocking(false);
                SelectionKey key = source.register(sel, SelectionKey.OP_READ);
                ByteBuffer buf = ByteBuffer.wrap("hello".getBytes(StandardCharsets.UTF_8));
                // start time before scheduling write
                long start = millisTime();
                scheduler.schedule(() -> sink.write(buf), delay, TimeUnit.MILLISECONDS);
                int n = sel.select();
                assertDuration(delay, millisTime() - start);
                assertEquals(1, n);
                assertTrue(key.isReadable());
            } finally {
                sink.close();
                source.close();
            }
        });
    }

    /**
     * Test causing select to wakeup after a delay.
     */
    @ParameterizedTest
    @MethodSource("durations")
    void testWakeup(long delay) throws Exception {
        VThreadRunner.run(() -> {
            try (var sel = Selector.open()) {
                // start time before scheduling wakeup
                long start = millisTime();
                scheduler.schedule(sel::wakeup, delay, TimeUnit.MILLISECONDS);
                int n = sel.select();
                assertDuration(delay, millisTime() - start);
                assertEquals(0, n);
            }
        });
    }

    /**
     * Test interrupting a thread blocked in select after a delay.
     */
    @ParameterizedTest
    @MethodSource("durations")
    void testInterrupt(long delay) throws Exception {
        VThreadRunner.run(() -> {
            try (var sel = Selector.open()) {
                Thread vthread = Thread.currentThread();
                // start time before scheduling interrupt
                long start = millisTime();
                scheduler.schedule(vthread::interrupt, delay, TimeUnit.MILLISECONDS);
                int n = sel.select();
                assertDuration(delay, millisTime() - start);
                assertEquals(0, n);
            }
        });
    }

    /**
     * Returns the current time in milliseconds.
     */
    private static long millisTime() {
        long now = System.nanoTime();
        return TimeUnit.MILLISECONDS.convert(now, TimeUnit.NANOSECONDS);
    }

    /**
     * Check the duration of a task.
     * @param expectedDuration expected duration
     * @param actualDuration actual duration
     */
    private static void assertDuration(long expectedDuration, long actualDuration) {
        long min = expectedDuration - 100;
        long max = expectedDuration + 15_000;  // allow for debug builds or slow machines
        assertTrue(actualDuration >= min,
                "Duration " + actualDuration + "ms, expected >= " + min + "ms");
        assertTrue(actualDuration <= max,
                "Duration " + actualDuration + "ms, expected <= " + max + "ms");
    }
}
