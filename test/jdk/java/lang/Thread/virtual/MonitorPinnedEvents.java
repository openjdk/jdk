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

/*
 * @test
 * @summary Test JFR jdk.VirtualThreadPinned event recorded for contended monitor enter
 *     and Object.wait when pinned
 * @requires vm.continuations
 * @modules jdk.jfr jdk.management
 * @library /test/lib
 * @run junit/othervm --enable-native-access=ALL-UNNAMED MonitorPinnedEvents
 */

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;

import jdk.test.lib.thread.VThreadPinner;
import jdk.test.lib.thread.VThreadRunner;   // ensureParallelism requires jdk.management
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MonitorPinnedEvents {
    // log to System.err so inlined with JUnit output
    private static final PrintStream log = System.err;

    // expected values for "blockingOperation" field in event
    private static final String CONTENDED_MONITOR_ENTER = "Contended monitor enter";
    private static final String OBJECT_WAIT = "Object.wait";

    // expected value for "pinnedReason" field in event
    private static final String NATIVE_FRAME = "Native or VM frame on stack";

    // block or wait for 2s to allow minimum event duration be tested
    private static final int DELAY = 2000;

    @BeforeAll
    static void setup() {
        // need at least two carriers to test pinning
        VThreadRunner.ensureParallelism(2);
    }

    /**
     * Test jdk.VirtualThreadPinned recorded for a contended monitor enter.
     */
    @Test
    void testContentedMonitorEnter() throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned");
            recording.start();

            Thread vthread;
            try {
                Object lock = new Object();

                synchronized (lock) {
                    // start virtual thread that blocks trying to acquire monitor while pinned
                    var ready = new AtomicBoolean();
                    vthread = Thread.ofVirtual().start(() -> {
                        VThreadPinner.runPinned(() -> {
                            ready.set(true);
                            synchronized (lock) {   // <--- blocks here while pinned
                            }
                        });
                    });
                    await(ready, vthread, Thread.State.BLOCKED);

                    // sleep before releasing to ensure virtual thread is blocked for >= 2s
                    Thread.sleep(DELAY);
                }
                vthread.join();
            } finally {
                recording.stop();
            }

            // jdk.VirtualThreadPinned event should be recorded
            RecordedEvent event = findPinnedEvent(recording);
            testEvent(event, vthread, CONTENDED_MONITOR_ENTER, NATIVE_FRAME, DELAY);
        }
    }

    /**
     * Test jdk.VirtualThreadPinned recorded for a contended monitor enter where
     * another thread may acquire the monitor when the main thread releases it. If
     * the other thread acquires the monitor before the virtual thread then the event
     * duration should include the time blocked until the other thread releases it.
     */
    @Test
    void testContentedMonitorEnter2() throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned");
            recording.start();

            // the thread that acquires the monitor after the main thread releases it
            var nextOwner = new AtomicReference<Thread>();

            Thread vthread;
            try {
                Object lock = new Object();
                Thread thread2;
                synchronized (lock) {

                    // start virtual thread that blocks trying to acquire monitor while pinned
                    var ready1 = new AtomicBoolean();
                    vthread = Thread.ofVirtual().start(() -> {
                        VThreadPinner.runPinned(() -> {
                            ready1.set(true);
                            synchronized (lock) {   // <--- blocks here while pinned
                                nextOwner.compareAndSet(null, Thread.currentThread());
                            }
                        });
                    });
                    await(ready1, vthread, Thread.State.BLOCKED);

                    // start platform thread that blocks trying to acquire monitor
                    thread2 = Thread.ofPlatform().start(() -> {
                        synchronized (lock) {
                            if (nextOwner.compareAndSet(null, Thread.currentThread())) {
                                // delayed release of monitor if acquired before virtual thread
                                try {
                                    Thread.sleep(DELAY);
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                    });

                }  // release lock, vthread or thread2 will acquire

                vthread.join();
                thread2.join();
            } finally {
                recording.stop();
            }

            // jdk.VirtualThreadPinned event should be recorded. If the platform thread
            // acquired the monitor before the virtual thread then the event duration
            // should be >= DELAY.
            RecordedEvent event = findPinnedEvent(recording);
            Thread winner = nextOwner.get();
            assertNotNull(winner);
            int minDuration = winner.isVirtual() ? 0 : DELAY;
            testEvent(event, vthread, CONTENDED_MONITOR_ENTER, NATIVE_FRAME, minDuration);
        }
    }

    /**
     * Test jdk.VirtualThreadPinned recorded for Object.wait when notified.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testObjectWaitNotify(boolean timed) throws Exception {
        testObjectWait(timed, false);
    }

    /**
     * Test jdk.VirtualThreadPinned recorded for Object.wait when interrupted.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testObjectWaitInterrupt(boolean timed) throws Exception {
        testObjectWait(timed, true);
    }

    /**
     * Test jdk.VirtualThreadPinned recorded for Object.wait.
     * @param timed true for a timed-wait, false for an untimed-wait
     * @param interrupt true to interrupt the thread, false to notify
     */
    private void testObjectWait(boolean timed, boolean interrupt) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned");
            recording.start();

            Thread vthread;
            try {
                Object lock = new Object();

                // start virtual thread that waits in Object.wait while pinned
                var ready = new AtomicBoolean();
                vthread = Thread.ofVirtual().start(() -> {
                    VThreadPinner.runPinned(() -> {
                        synchronized (lock) {
                            ready.set(true);
                            try {
                                // wait while pinned
                                if (timed) {
                                    lock.wait(Long.MAX_VALUE);
                                } else {
                                    lock.wait();
                                }
                            } catch (InterruptedException e) {
                            }
                        }
                    });
                });
                await(ready, vthread, timed ? Thread.State.TIMED_WAITING : Thread.State.WAITING);

                // sleep to ensure virtual thread waits for >= 2s
                Thread.sleep(DELAY);

                // interrupt or notify thread so it resumes execution
                if (interrupt) {
                    vthread.interrupt();
                } else {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
                vthread.join();
            } finally {
                recording.stop();
            }

            // jdk.VirtualThreadPinned event should be recorded
            RecordedEvent event = findPinnedEvent(recording);
            testEvent(event, vthread, OBJECT_WAIT, NATIVE_FRAME, DELAY);
        }
    }

    /**
     * Test jdk.VirtualThreadPinned recorded for Object.wait where the virtual thread
     * is notified but may block when attempting to reenter. One event should be
     * recorded. The event duration should include both the waiting time and the time
     * blocked to reenter.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testObjectWaitNotify2(boolean timed) throws Exception {
        testObjectWait2(timed, false);
    }

    /**
     * Test jdk.VirtualThreadPinned recorded for Object.wait where the virtual thread
     * is interrupted but may block when attempting to reenter. One event should be
     * recorded. The event duration should include both the waiting time and the time
     * blocked to reenter.
     */
    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void testObjectWaitInterrupt2(boolean timed) throws Exception {
        testObjectWait2(timed, true);
    }

    /**
     * Test jdk.VirtualThreadPinned recorded for Object.wait where the virtual thread
     * blocks when attempting to reenter.
     * @param timed true for a timed-wait, false for an untimed-wait
     * @param interrupt true to interrupt the thread, false to notify
     */
    private void testObjectWait2(boolean timed, boolean interrupt) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.VirtualThreadPinned");
            recording.start();

            // the thread that acquires the monitor after the main thread releases it
            var nextOwner = new AtomicReference<Thread>();

            Thread vthread;
            try {
                Object lock = new Object();

                // start virtual thread that waits in Object.wait while pinned
                var ready1 = new AtomicBoolean();
                vthread = Thread.ofVirtual().start(() -> {
                    VThreadPinner.runPinned(() -> {
                        boolean notify = !interrupt;
                        synchronized (lock) {
                            ready1.set(true);
                            try {
                                // wait while pinned
                                if (timed) {
                                    lock.wait(Long.MAX_VALUE);
                                } else {
                                    lock.wait();
                                }
                                if (notify) {
                                    nextOwner.compareAndSet(null, Thread.currentThread());
                                }
                            } catch (InterruptedException e) {
                                if (interrupt) {
                                    nextOwner.compareAndSet(null, Thread.currentThread());
                                }
                            }
                        }
                    });
                });
                await(ready1, vthread, timed ? Thread.State.TIMED_WAITING : Thread.State.WAITING);

                // platform thread that blocks on monitor enter
                Thread thread2;
                synchronized (lock) {
                    thread2 = Thread.ofPlatform().start(() -> {
                        synchronized (lock) {   // <--- blocks here
                            if (nextOwner.compareAndSet(null, Thread.currentThread())) {
                                // delayed release of monitor if acquired before virtual thread
                                try {
                                    Thread.sleep(DELAY);
                                } catch (InterruptedException e) {
                                }
                            }
                        }
                    });

                    // interrupt/notify and release monitor to allow one of the threads to acquire
                    if (interrupt) {
                        vthread.interrupt();
                    } else {
                        lock.notify();
                    }
                }

                vthread.join();
                thread2.join();

            } finally {
                recording.stop();
            }

            // jdk.VirtualThreadPinned event should be recorded. If the platform thread
            // acquired the monitor before the virtual thread re-entered then the event
            // duration should be >= DELAY.
            RecordedEvent event = findPinnedEvent(recording);
            Thread winner = nextOwner.get();
            assertNotNull(winner);
            int minDuration = winner.isVirtual() ? 0 : DELAY;
            testEvent(event, vthread, OBJECT_WAIT, NATIVE_FRAME, minDuration);
        }
    }

    /**
     * Test that the event was recorded by the expected virtual thread, the event has a
     * "carrierThread", the "blockingOperation", and "pinnedReason" fields have the
     * expected values, and the event duration is >= minDuration.
     */
    private void testEvent(RecordedEvent event,
                           Thread vthread,
                           String expectedBlockingOp,
                           String expectedPinnedReason,
                           int minDuration) {
        assertTrue(vthread.isVirtual());
        assertEquals(vthread.threadId(), event.getThread().getId());

        RecordedThread carrier = event.getValue("carrierThread");
        assertFalse(carrier.isVirtual());

        assertEquals(expectedBlockingOp, event.getString("blockingOperation"));
        assertEquals(expectedPinnedReason, event.getString("pinnedReason"));

        long duration = event.getDuration().toMillis();
        assertTrue(duration >= (minDuration - 100),
                "Duration " + duration + "ms, expected >= " + minDuration + "ms");
    }

    /**
     * Find the expected jdk.VirtualThreadPinned event in the recording. There may be
     * several pinned events recorded by other parts of the system that need to be
     * filtered out.
     */
    private RecordedEvent findPinnedEvent(Recording recording) throws IOException {
        Map<Boolean, List<RecordedEvent>> events = find(recording, "jdk.VirtualThreadPinned")
                .collect(Collectors.partitioningBy(e -> filtered(topFrameMethod(e)), Collectors.toList()));
        List<RecordedEvent> filteredEvents = events.get(Boolean.TRUE);
        List<RecordedEvent> pinnedEvents = events.get(Boolean.FALSE);
        log.format("%d event(s) recorded%n", filteredEvents.size() + pinnedEvents.size());
        log.println("-- filtered events --");
        log.println(filteredEvents);
        log.println("-- remaining pinned events --");
        log.println(pinnedEvents);
        assertEquals(1, pinnedEvents.size());
        return pinnedEvents.get(0);
    }

    /**
     * Pinned events recorded at the following classes/methods should be ignored.
     */
    private final Set<String> FILTERED = Set.of(
            "java.lang.VirtualThread.*",
            "java.lang.ref.ReferenceQueue.poll",
            "java.lang.invoke.*",
            "jdk.internal.ref.*"
    );

    /**
     * Returns true if the given top-frame {@code class.method} should be ignored.
     */
    private boolean filtered(String topFrameMethod) {
        for (String s : FILTERED) {
            if (s.endsWith("*")) {
                String prefix = s.substring(0, s.length()-1);
                if (topFrameMethod.startsWith(prefix)) {
                    return true;
                }
            } else if (s.equals(topFrameMethod)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the {@code class.method} of the top frame in the event's stack trace.
     */
    private String topFrameMethod(RecordedEvent event) {
        RecordedFrame topFrame = event.getStackTrace().getFrames().get(0);
        RecordedMethod method = topFrame.getMethod();
        return method.getType().getName() + "." + method.getName();
    }

    /**
     * Returns the events with the given name in the recording.
     */
    private Stream<RecordedEvent> find(Recording recording, String name) throws IOException {
        Path recordingFile = recording.getDestination();
        if (recordingFile == null) {
            ProcessHandle h = ProcessHandle.current();
            recordingFile = Path.of("recording-" + recording.getId() + "-pid" + h.pid() + ".jfr");
            recording.dump(recordingFile);
        }
        return RecordingFile.readAllEvents(recordingFile)
                .stream()
                .filter(e -> e.getEventType().getName().equals(name));
    }

    /**
     * Waits for ready to become true, then waits for the thread to get to the given state.
     */
    private void await(AtomicBoolean ready,
                       Thread thread,
                       Thread.State expectedState) throws InterruptedException {
        while (!ready.get()) {
            Thread.sleep(10);
        }
        Thread.State state = thread.getState();
        while (state != expectedState) {
            Thread.sleep(10);
            state = thread.getState();
        }
    }
}
