/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic test for JFR jdk.VirtualThreadXXX events
 * @requires vm.continuations
 * @modules jdk.jfr java.base/java.lang:+open
 * @library /test/lib
 * @run junit/othervm --enable-native-access=ALL-UNNAMED JfrEvents
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import jdk.test.lib.thread.VThreadPinner;
import jdk.test.lib.thread.VThreadRunner;
import jdk.test.lib.thread.VThreadScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JfrEvents {

    @BeforeAll
    static void setup() {
        int minParallelism = 2;
        if (Thread.currentThread().isVirtual()) {
            minParallelism++;
        }
        VThreadRunner.ensureParallelism(minParallelism);
    }

    /**
     * Test jdk.VirtualThreadStart and jdk.VirtualThreadEnd events.
     */
    @Test
    void testVirtualThreadStartAndEnd() throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadStart");
            recording.enable("jdk.VirtualThreadEnd");

            // execute 100 tasks, each in their own virtual thread
            recording.start();
            ThreadFactory factory = Thread.ofVirtual().factory();
            try (var executor = Executors.newThreadPerTaskExecutor(factory)) {
                for (int i = 0; i < 100; i++) {
                    executor.submit(() -> { });
                }
                Thread.sleep(1000); // give time for thread end events to be recorded
            } finally {
                recording.stop();
            }

            Map<String, Integer> events = sumEvents(recording);
            System.err.println(events);

            int startCount = events.getOrDefault("jdk.VirtualThreadStart", 0);
            int endCount = events.getOrDefault("jdk.VirtualThreadEnd", 0);
            assertEquals(100, startCount);
            assertEquals(100, endCount);
        }
    }

    /**
     * Test jdk.VirtualThreadPinned event when parking while pinned.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testParkWhenPinned(boolean timed) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned");
            recording.start();

            var started = new AtomicBoolean();
            var done = new AtomicBoolean();
            var vthread = Thread.startVirtualThread(() -> {
                VThreadPinner.runPinned(() -> {
                    started.set(true);
                    while (!done.get()) {
                        if (timed) {
                            LockSupport.parkNanos(Long.MAX_VALUE);
                        } else {
                            LockSupport.park();
                        }
                    }
                });
            });

            try {
                // wait for thread to start and park
                awaitTrue(started);
                await(vthread, timed ? Thread.State.TIMED_WAITING : Thread.State.WAITING);
            } finally {
                done.set(true);
                LockSupport.unpark(vthread);
                vthread.join();
                recording.stop();
            }

            assertContainsPinnedEvent(recording, vthread);
        }
    }

    /**
     * Test jdk.VirtualThreadSubmitFailed event.
     */
    @Test
    void testVirtualThreadSubmitFailed() throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadSubmitFailed");

            recording.start();
            try (ExecutorService pool = Executors.newCachedThreadPool()) {
                Executor scheduler = task -> pool.execute(task);

                // create virtual thread that uses custom scheduler
                ThreadFactory factory = VThreadScheduler.virtualThreadFactory(scheduler);

                // start a thread
                Thread thread = factory.newThread(LockSupport::park);
                thread.start();

                // wait for thread to park
                await(thread, Thread.State.WAITING);

                // shutdown scheduler
                pool.shutdown();

                // unpark, the submit should fail
                try {
                    LockSupport.unpark(thread);
                    fail();
                } catch (RejectedExecutionException expected) { }

                // start another thread, it should fail and an event should be recorded
                try {
                    factory.newThread(LockSupport::park).start();
                    throw new RuntimeException("RejectedExecutionException expected");
                } catch (RejectedExecutionException expected) { }
            } finally {
                recording.stop();
            }

            List<RecordedEvent> submitFailedEvents = find(recording, "jdk.VirtualThreadSubmitFailed");
            System.err.println(submitFailedEvents);
            assertTrue(submitFailedEvents.size() == 2, "Expected two events");
        }
    }

    /**
     * Returns the list of events in the given recording with the given name.
     */
    private static List<RecordedEvent> find(Recording recording, String name) throws IOException {
        Path recordingFile = recordingFile(recording);
        return RecordingFile.readAllEvents(recordingFile)
                .stream()
                .filter(e -> e.getEventType().getName().equals(name))
                .toList();
    }

    /**
     * Read the events from the recording and return a map of event name to count.
     */
    private static Map<String, Integer> sumEvents(Recording recording) throws IOException {
        Path recordingFile = recordingFile(recording);
        List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
        return events.stream()
                .map(RecordedEvent::getEventType)
                .collect(Collectors.groupingBy(EventType::getName,
                                               Collectors.summingInt(x -> 1)));
    }

    /**
     * Return the file path to the recording file.
     */
    private static Path recordingFile(Recording recording) throws IOException {
        Path recordingFile = recording.getDestination();
        if (recordingFile == null) {
            ProcessHandle h = ProcessHandle.current();
            recordingFile = Path.of("recording-" + recording.getId() + "-pid" + h.pid() + ".jfr");
            recording.dump(recordingFile);
        }
        return recordingFile;
    }

    /**
     * Assert that a recording contains a jdk.VirtualThreadPinned event on the given thread.
     */
    private void assertContainsPinnedEvent(Recording recording, Thread thread) throws IOException {
        List<RecordedEvent> pinnedEvents = find(recording, "jdk.VirtualThreadPinned");
        assertTrue(pinnedEvents.size() > 0, "No jdk.VirtualThreadPinned events in recording");
        System.err.println(pinnedEvents);

        long tid = thread.threadId();
        assertTrue(pinnedEvents.stream()
                        .anyMatch(e -> e.getThread().getJavaThreadId() == tid),
                "jdk.VirtualThreadPinned for javaThreadId = " + tid + " not found");
    }

    /**
     * Waits for the given boolean to be set to true.
     */
    private void awaitTrue(AtomicBoolean b) throws InterruptedException {
        while (!b.get()) {
            Thread.sleep(10);
        }
    }

    /**
     * Waits for the given thread to reach a given state.
     */
    private static void await(Thread thread, Thread.State expectedState) throws InterruptedException {
        Thread.State state = thread.getState();
        while (state != expectedState) {
            Thread.sleep(10);
            state = thread.getState();
        }
    }
}
