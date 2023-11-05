/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm JfrEvents
 */

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import jdk.jfr.EventType;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JfrEvents {
    private static final Object lock = new Object();

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
     * Test jdk.VirtualThreadPinned event.
     */
    @Test
    void testVirtualThreadPinned() throws Exception {
        Runnable[] parkers = new Runnable[] {
            () -> LockSupport.park(),
            () -> LockSupport.parkNanos(Duration.ofDays(1).toNanos())
        };

        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned");

            recording.start();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                for (Runnable parker : parkers) {
                    // execute parking task in virtual thread
                    var threadRef = new AtomicReference<Thread>();
                    executor.submit(() -> {
                        threadRef.set(Thread.currentThread());
                        synchronized (lock) {
                            parker.run();   // should pin carrier
                        }
                    });

                    // wait for the task to start and the virtual thread to park
                    Thread thread;
                    while ((thread = threadRef.get()) == null) {
                        Thread.sleep(10);
                    }
                    try {
                        Thread.State state = thread.getState();
                        while (state != Thread.State.WAITING && state != Thread.State.TIMED_WAITING) {
                            Thread.sleep(10);
                            state = thread.getState();
                        }
                    } finally {
                        LockSupport.unpark(thread);
                    }
                }
            } finally {
                recording.stop();
            }

            Map<String, Integer> events = sumEvents(recording);
            System.err.println(events);

            // should have a pinned event for each park
            int pinnedCount = events.getOrDefault("jdk.VirtualThreadPinned", 0);
            assertEquals(parkers.length, pinnedCount);
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
                ThreadFactory factory = ThreadBuilders.virtualThreadBuilder(scheduler).factory();

                // start a thread
                Thread thread = factory.newThread(LockSupport::park);
                thread.start();

                // wait for thread to park
                while (thread.getState() != Thread.State.WAITING) {
                    Thread.sleep(10);
                }

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

            Map<String, Integer> events = sumEvents(recording);
            System.err.println(events);

            int count = events.getOrDefault("jdk.VirtualThreadSubmitFailed", 0);
            assertEquals(2, count);
        }
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
}
