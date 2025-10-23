/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test that Thread.sleep emits a JFR jdk.ThreadSleep event
 * @requires vm.hasJFR
 * @modules jdk.jfr
 * @run junit ThreadSleepEvent
 */

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import static java.util.concurrent.TimeUnit.*;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class ThreadSleepEvent {
    private static final String THREAD_SLEEP_EVENT_NAME = "jdk.ThreadSleep";

    static Stream<ThreadFactory> threadFactories() {
        return Stream.of(
                Thread.ofPlatform().factory(),
                Thread.ofVirtual().factory()
        );
    }

    // Arguments.of(ThreadFactory, millis)
    static Stream<Arguments> threadFactoryAndMillis() {
        long[] millis = {0, 1000, Long.MAX_VALUE};
        return threadFactories()
                .flatMap(f -> LongStream.of(millis)
                        .mapToObj(ms -> Arguments.of(f, ms)));
    }

    // Arguments.of(ThreadFactory, millis, nanos)
    static Stream<Arguments> threadFactoryAndMillisAndNanos() {
        int[] nanos = {0, 1000, 999_999};
        return threadFactoryAndMillis().flatMap(a -> IntStream.of(nanos)
                .mapToObj(ns -> Arguments.of(a.get()[0], a.get()[1], ns)));
    }

    // Arguments.of(ThreadFactory, Duration)
    static Stream<Arguments> threadFactoryAndDuration() {
        Duration[] durations = {
                Duration.ofNanos(0),
                Duration.ofSeconds(1),
                Duration.ofSeconds(Long.MAX_VALUE, 999_999_999)
        };
        return threadFactories()
                .flatMap(f -> Stream.of(durations)
                        .map(d -> Arguments.of(f, d)));
    }

    /**
     * Test Thread.sleep(long) emits event.
     */
    @ParameterizedTest
    @MethodSource("threadFactoryAndMillis")
    void testSleep(ThreadFactory factory, long millis) throws Exception {
        long expectedTime = MILLISECONDS.toNanos(millis);
        long recordedTime = testSleep(factory, () -> Thread.sleep(millis));
        assertEquals(expectedTime, recordedTime);
    }

    /**
     * Test Thread.sleep(long, int) emits event.
     */
    @ParameterizedTest
    @MethodSource("threadFactoryAndMillisAndNanos")
    void testSleep(ThreadFactory factory, long millis, int nanos) throws Exception {
        long expectedTime = MILLISECONDS.toNanos(millis);
        expectedTime += Math.min(Long.MAX_VALUE - expectedTime, nanos);
        long recordedTime = testSleep(factory, () -> Thread.sleep(millis, nanos));
        assertEquals(expectedTime, recordedTime);
    }

    /**
     * Test Thread.sleep(Duration) emits event.
     */
    @ParameterizedTest
    @MethodSource("threadFactoryAndDuration")
    void testSleep(ThreadFactory factory, Duration duration) throws Exception {
        long expectedTime = NANOSECONDS.convert(duration);
        long recordedTime = testSleep(factory, () -> Thread.sleep(duration));
        assertEquals(expectedTime, recordedTime);
    }

    /**
     * Test that sleeper emits event, returning the recorded sleep time.
     */
    long testSleep(ThreadFactory factory, Sleeper sleeper) throws Exception {
        try (Recording recording = new Recording()) {
            long tid;

            recording.enable(THREAD_SLEEP_EVENT_NAME);
            recording.start();
            try {

                // start thread to run sleeper task
                var latch = new CountDownLatch(1);
                Thread thread = factory.newThread(() -> {
                    latch.countDown();
                    try {
                        sleeper.run();
                    } catch (InterruptedException e) {
                        // ignore;
                    }
                });
                thread.start();

                // wait for thread to execute
                latch.await();

                // don't wait for the sleep time
                thread.interrupt();

                thread.join();
                tid = thread.threadId();
            } finally {
                recording.stop();
            }

            // find the ThreadSleep event recorded by the thread
            RecordedEvent event = find(recording, THREAD_SLEEP_EVENT_NAME, tid).orElseThrow();
            return event.getLong("time");
        }
    }

    private interface Sleeper {
        void run() throws InterruptedException;
    }

    /**
     * Find a recorded event with the given name and recorded by a thread with the
     * given thread ID.
     */
    private static Optional<RecordedEvent> find(Recording recording,
                                                String name,
                                                long tid) throws Exception {
        Path recordingFile = recordingFile(recording);
        List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
        return events.stream()
                .filter(e -> e.getEventType().getName().equals(name)
                        && e.getThread().getJavaThreadId() == tid)
                .findAny();
    }

    /**
     * Return the file path to the recording file.
     */
    private static Path recordingFile(Recording recording) throws Exception {
        Path recordingFile = recording.getDestination();
        if (recordingFile == null) {
            ProcessHandle h = ProcessHandle.current();
            recordingFile = Path.of("recording-" + recording.getId() + "-pid" + h.pid() + ".jfr");
            recording.dump(recordingFile);
        }
        return recordingFile;
    }
}
